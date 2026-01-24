const express = require('express');
const axios = require('axios');
const fs = require('fs');
const path = require('path');
require('dotenv').config();

// ✅ 지금 실행된 파일 확인용 (가장 중요)
console.log('[BOOT] running:', __filename);

const app = express();
const PORT = 3000;

app.use(express.json());

// ===============================
// ✅ POI 데이터 로딩
// ===============================
let bins = [];
let toilets = [];

try {
  const dataDir = path.resolve(__dirname, 'data');

  bins = JSON.parse(
    fs.readFileSync(path.join(dataDir, 'bins.normalized.json'), 'utf-8')
  );
  toilets = JSON.parse(
    fs.readFileSync(path.join(dataDir, 'toilets.normalized.json'), 'utf-8')
  );

  console.log('bins:', bins.length);
  console.log('toilets:', toilets.length);
} catch (e) {
  console.error('[POI] Failed to load data files. Check server/data/*.json');
  console.error(e.message);
}

// 통합 POI 풀
const POIS = [...bins, ...toilets];

// ===============================
// ✅ 유틸: haversine (meters)
// ===============================
function haversineM(a, b) {
  const R = 6371000;
  const toRad = (d) => (d * Math.PI) / 180;

  const dLat = toRad(b.lat - a.lat);
  const dLng = toRad(b.lng - a.lng);
  const lat1 = toRad(a.lat);
  const lat2 = toRad(b.lat);

  const x =
    Math.sin(dLat / 2) ** 2 +
    Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLng / 2) ** 2;

  return 2 * R * Math.asin(Math.sqrt(x));
}

function parseTypes(typesStr) {
  if (!typesStr) return ['toilet', 'bin'];
  return String(typesStr)
    .split(',')
    .map((s) => s.trim())
    .filter(Boolean);
}

// ===============================
// ✅ 주변 POI 후보 확인용 엔드포인트
// ===============================
app.get('/api/poi/nearby', (req, res) => {
  const lat = Number(req.query.lat);
  const lng = Number(req.query.lng);
  const radiusM = Number(req.query.radiusM || 2000);
  const types = parseTypes(req.query.types);

  if (!Number.isFinite(lat) || !Number.isFinite(lng)) {
    return res.status(400).json({
      error: 'INVALID_REQUEST',
      message:
        'query로 lat,lng가 필요합니다. 예) /api/poi/nearby?lat=...&lng=...',
    });
  }

  if (!Number.isFinite(radiusM) || radiusM <= 0) {
    return res.status(400).json({
      error: 'INVALID_REQUEST',
      message: 'radiusM은 양수 숫자여야 합니다.',
    });
  }

  const start = { lat, lng };

  const candidates = POIS.filter((p) => types.includes(p.type))
    .map((p) => ({
      ...p,
      distanceM: Math.round(haversineM(start, p)),
    }))
    .filter((p) => p.distanceM <= radiusM)
    .sort((a, b) => a.distanceM - b.distanceM);

  const sample = candidates.slice(0, 20).map((p) => ({
    id: p.id,
    type: p.type,
    name: p.name,
    lat: p.lat,
    lng: p.lng,
    distanceM: p.distanceM,
  }));

  return res.json({
    ok: true,
    start,
    radiusM,
    types,
    counts: {
      totalPoisLoaded: POIS.length,
      candidates: candidates.length,
    },
    sample,
  });
});

/**
 * 서버 헬스체크
 * ✅ 여기서도 어떤 index.js가 떠있는지 확실히 확인 가능하게 추가
 */
app.get('/', (req, res) => {
  res.json({
    ok: true,
    message: 'SERVER OK - NO DB',
    running_file: __filename, // ✅ 추가
    has_ors_key: !!process.env.ORS_API_KEY,
    ors_key_length: process.env.ORS_API_KEY?.length || 0,
    poi_counts: {
      bins: bins.length,
      toilets: toilets.length,
      total: POIS.length,
    },
  });
});

/**
 * ORS 보행자 경로 (GeoJSON)
 */
app.post('/api/ors/walking', async (req, res) => {
  try {
    if (!process.env.ORS_API_KEY) {
      return res.status(500).json({
        error: 'MISSING_ORS_API_KEY',
        message: '.env에 ORS_API_KEY가 없습니다',
      });
    }

    const { start, end, waypoints } = req.body || {};

    if (
      !start ||
      !end ||
      start.lat == null ||
      start.lng == null ||
      end.lat == null ||
      end.lng == null
    ) {
      return res.status(400).json({
        error: 'INVALID_REQUEST',
        message: 'start/end (lat,lng)가 필요합니다',
      });
    }

    const coordinates = [
      [Number(start.lng), Number(start.lat)],
      ...(Array.isArray(waypoints)
        ? waypoints
            .filter((p) => p && p.lat != null && p.lng != null)
            .map((p) => [Number(p.lng), Number(p.lat)])
        : []),
      [Number(end.lng), Number(end.lat)],
    ];

    const orsResp = await axios.post(
      'https://api.openrouteservice.org/v2/directions/foot-walking/geojson',
      { coordinates },
      {
        headers: {
          Authorization: process.env.ORS_API_KEY,
          'Content-Type': 'application/json',
        },
        timeout: 15000,
      }
    );

    return res.status(orsResp.status).json(orsResp.data);
  } catch (err) {
    const status = err.response?.status || 500;
    const data = err.response?.data || null;

    return res.status(status).json({
      error: 'ORS_API_FAILED',
      status,
      data,
      message: err.message,
    });
  }
});

function getOrsDistanceM(geojson) {
  const d = geojson?.features?.[0]?.properties?.summary?.distance;
  return Number.isFinite(d) ? d : null;
}

/**
 * MVP 코스 생성 (루프) - count 최대 3개
 */
app.post('/api/course/generate', async (req, res) => {
  try {
    if (!process.env.ORS_API_KEY) {
      return res.status(500).json({
        error: 'MISSING_ORS_API_KEY',
        message: '.env에 ORS_API_KEY가 없습니다',
      });
    }

    const {
      start,
      targetKm,
      radiusM,
      types,
      toleranceRatio,
      maxOrsCalls,
      count,
      diversify,
    } = req.body || {};

    if (!start || start.lat == null || start.lng == null) {
      return res.status(400).json({
        error: 'INVALID_REQUEST',
        message: 'start(lat,lng)가 필요합니다',
      });
    }

    const L = Number(targetKm) * 1000;
    if (!Number.isFinite(L) || L <= 0) {
      return res.status(400).json({
        error: 'INVALID_REQUEST',
        message: 'targetKm은 양수 숫자여야 합니다',
      });
    }

    const tol = Number.isFinite(Number(toleranceRatio))
      ? Number(toleranceRatio)
      : 0.12;

    const maxCalls = Number.isFinite(Number(maxOrsCalls))
      ? Number(maxOrsCalls)
      : 12;

    const want = Number.isFinite(Number(count))
      ? Math.max(1, Math.min(3, Number(count)))
      : 1;

    const mode = diversify || 'exclude_used';

    const r = Number.isFinite(Number(radiusM))
      ? Number(radiusM)
      : L <= 3500
      ? 1800
      : L <= 5500
      ? 2500
      : L <= 8000
      ? 3500
      : 5000;

    const includeTypes =
      Array.isArray(types) && types.length ? types : ['toilet', 'bin'];

    const startPt = { lat: Number(start.lat), lng: Number(start.lng) };

    const minL = L * (1 - tol);
    const maxL = L * (1 + tol);

    const k = 1.2;
    const dStar = L / (2 * k);

    const usedIds = new Set();
    const courses = [];

    for (let i = 0; i < want; i++) {
      const candidates = POIS.filter((p) => includeTypes.includes(p.type))
        .filter((p) => (mode === 'exclude_used' ? !usedIds.has(p.id) : true))
        .map((p) => ({ ...p, d: haversineM(startPt, p) }))
        .filter((p) => p.d <= r)
        .sort((a, b) => a.d - b.d);

      if (candidates.length === 0) break;

      const K = Math.min(30, candidates.length);
      const ranked = candidates
        .slice(0, Math.min(800, candidates.length))
        .map((p) => ({ ...p, score: Math.abs(p.d - dStar) }))
        .sort((a, b) => a.score - b.score)
        .slice(0, K);

      let best = null;
      let callsUsed = 0;

      for (const anchor of ranked) {
        if (callsUsed >= maxCalls) break;
        callsUsed += 1;

        const coordinates = [
          [startPt.lng, startPt.lat],
          [anchor.lng, anchor.lat],
          [startPt.lng, startPt.lat],
        ];

        const orsResp = await axios.post(
          'https://api.openrouteservice.org/v2/directions/foot-walking/geojson',
          { coordinates },
          {
            headers: {
              Authorization: process.env.ORS_API_KEY,
              'Content-Type': 'application/json',
            },
            timeout: 15000,
          }
        );

        const geojson = orsResp.data;
        const distM = getOrsDistanceM(geojson);
        if (!Number.isFinite(distM)) continue;

        const err = Math.abs(distM - L);

        if (!best || err < best.err) {
          best = {
            waypoint: {
              id: anchor.id,
              type: anchor.type,
              name: anchor.name,
              lat: anchor.lat,
              lng: anchor.lng,
            },
            geojson,
            distM,
            err,
            orsCallsUsed: callsUsed,
            candidates: candidates.length,
          };
        }

        if (distM >= minL && distM <= maxL) break;
      }

      if (!best) break;

      usedIds.add(best.waypoint.id);
      courses.push({
        meta: {
          index: i + 1,
          targetKm: Number(targetKm),
          targetDistanceM: L,
          totalDistanceM: Math.round(best.distM),
          toleranceRatio: tol,
          toleranceHit: best.distM >= minL && best.distM <= maxL,
          radiusM: r,
          candidates: best.candidates,
          orsCallsUsed: best.orsCallsUsed,
        },
        waypoints: [best.waypoint],
        geojson: best.geojson,
      });
    }

    return res.json({
      ok: true,
      mode,
      requested: want,
      returned: courses.length,
      courses,
    });
  } catch (err) {
    const status = err.response?.status || 500;
    const data = err.response?.data || null;

    return res.status(status).json({
      error: 'COURSE_GENERATE_FAILED',
      status,
      data,
      message: err.message,
    });
  }
});

app.listen(PORT, () => {
  console.log(`서버 실행 완료: http://localhost:${PORT}`);
  console.log('[ORS] key length:', process.env.ORS_API_KEY?.length || 0);
});
