// index.js
const express = require('express');
const axios = require('axios');
const fs = require('fs');
const path = require('path');
require('dotenv').config();

/**
 * ✅ 버전: v10
 * 특징:
 * 1. Sector Route (부채꼴) 적용
 * 2. Slot 1(화장실) 필수
 * 3. Slot 2(쓰레기통) 우선 검색하되, 없으면 화장실로 대체 (Soft Fallback)
 * 4. Slot 3(편의점) 선택 시 필수, 미선택 시 화장실
 */
const VERSION = 'BIN-SOFT-v10-binFallback-storeFixed-2026-01-26';
console.log('[BOOT] running:', __filename);
console.log('[VERSION]', VERSION);

const app = express();
const PORT = 3000;

app.use(express.json());

// ===============================
// ✅ ORS KEY 안전 처리
// ===============================
function getOrsKey() {
  const raw = process.env.ORS_API_KEY;
  if (!raw) return null;
  const cleaned = String(raw).replace(/[\r\n\t]/g, '').trim();
  const unquoted = cleaned.replace(/^"(.*)"$/, '$1').replace(/^'(.*)'$/, '$1');
  return unquoted;
}

// ===============================
// ✅ sleep (rate-limit 완화)
// ===============================
function sleep(ms) {
  return new Promise((r) => setTimeout(r, ms));
}

// ===============================
// ✅ POI 데이터 로딩
// ===============================
let bins = [];
let toilets = [];
let storesFile = [];

try {
  const dataDir = path.resolve(__dirname, 'data');

  bins = JSON.parse(
    fs.readFileSync(path.join(dataDir, 'bins.normalized.json'), 'utf-8')
  );
  toilets = JSON.parse(
    fs.readFileSync(path.join(dataDir, 'toilets.normalized.json'), 'utf-8')
  );
  storesFile = JSON.parse(
    fs.readFileSync(path.join(dataDir, 'stores.normalized.json'), 'utf-8')
  );

  console.log('bins:', bins.length);
  console.log('toilets:', toilets.length);
  console.log('stores(file):', storesFile.length);
} catch (e) {
  console.error('[POI] Failed to load data files. Check server/data/*.json');
  console.error(e.message);
}

const POIS = [...bins, ...toilets, ...storesFile];

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
  return String(typesStr).split(',').map((s) => s.trim()).filter(Boolean);
}

// ===============================
// ✅ destination point
// ===============================
function destinationPoint(start, bearingDeg, distanceM) {
  const R = 6371000;
  const toRad = (d) => (d * Math.PI) / 180;
  const toDeg = (r) => (r * 180) / Math.PI;

  const lat1 = toRad(start.lat);
  const lng1 = toRad(start.lng);
  const brng = toRad(bearingDeg);
  const dr = distanceM / R;

  const lat2 = Math.asin(
    Math.sin(lat1) * Math.cos(dr) +
    Math.cos(lat1) * Math.sin(dr) * Math.cos(brng)
  );

  const lng2 = lng1 + Math.atan2(
    Math.sin(brng) * Math.sin(dr) * Math.cos(lat1),
    Math.cos(dr) - Math.sin(lat1) * Math.sin(lat2)
  );

  return { lat: toDeg(lat2), lng: toDeg(lng2) };
}

// ===============================
// ✅ 후보 선택
// ===============================
function pickNearestByType(
  pool,
  targetPt,
  type,
  usedIds,
  maxCandidates = 240,
  maxTargetDistM = Infinity
) {
  const cands = pool
    .filter((p) => p.type === type)
    .filter((p) => !usedIds.has(p.id))
    .map((p) => ({ ...p, d: haversineM(targetPt, p) }))
    .filter((p) => p.d <= maxTargetDistM)
    .sort((a, b) => a.d - b.d)
    .slice(0, maxCandidates);

  return cands.length ? cands[0] : null;
}

// ===============================
// ✅ ORS 호출
// ===============================
async function orsLoopGeojson(startPt, waypoints, apiKey) {
  const coordinates = [
    [startPt.lng, startPt.lat],
    ...waypoints.map((p) => [p.lng, p.lat]),
    [startPt.lng, startPt.lat],
  ];

  const url = 'https://api.openrouteservice.org/v2/directions/foot-walking/geojson';
  const headers = { Authorization: apiKey, 'Content-Type': 'application/json' };

  await sleep(180); // rate limit buffer

  const retries = 4;
  let backoff = 700;

  for (let i = 0; i <= retries; i++) {
    try {
      const orsResp = await axios.post(url, { coordinates }, { headers, timeout: 15000 });
      return orsResp.data;
    } catch (err) {
      const status = err.response?.status;
      if (status === 429 && i < retries) {
        await sleep(backoff);
        backoff = Math.min(backoff * 2, 5000);
        continue;
      }
      throw err;
    }
  }
}

function getOrsDistanceM(geojson) {
  let d = geojson?.features?.[0]?.properties?.summary?.distance;
  if (Number.isFinite(d)) return d;
  d = geojson?.features?.[0]?.properties?.segments?.[0]?.distance;
  if (Number.isFinite(d)) return d;
  return null;
}

// ===============================
// ✅ 서비스 지역(bbox)
// ===============================
function computeBBox(points) {
  let minLat = Infinity, maxLat = -Infinity, minLng = Infinity, maxLng = -Infinity;
  for (const p of points) {
    if (!Number.isFinite(p.lat) || !Number.isFinite(p.lng)) continue;
    minLat = Math.min(minLat, p.lat);
    maxLat = Math.max(maxLat, p.lat);
    minLng = Math.min(minLng, p.lng);
    maxLng = Math.max(maxLng, p.lng);
  }
  if (!Number.isFinite(minLat)) return null;
  return { minLat, maxLat, minLng, maxLng };
}

function expandBBox(bbox, marginDeg) {
  return {
    minLat: bbox.minLat - marginDeg,
    maxLat: bbox.maxLat + marginDeg,
    minLng: bbox.minLng - marginDeg,
    maxLng: bbox.maxLng + marginDeg,
  };
}

function inBBox(pt, bbox) {
  return (
    pt.lat >= bbox.minLat &&
    pt.lat <= bbox.maxLat &&
    pt.lng >= bbox.minLng &&
    pt.lng <= bbox.maxLng
  );
}

const SERVICE_AREA = (() => {
  const marginDeg = 0.03;
  const bbox0 = computeBBox([...bins, ...toilets]);
  if (!bbox0) return null;
  const bbox = expandBBox(bbox0, marginDeg);
  console.log('[SERVICE_AREA] bbox:', bbox);
  return { bbox, marginDeg };
})();

// ===============================
// ✅ 서버 헬스체크
// ===============================
app.get('/', (req, res) => {
  res.json({
    ok: true,
    version: VERSION,
    message: 'SERVER OK - Bin Fallback Applied',
  });
});

// ===============================
// ✅ 추천 코스 생성
// ===============================
app.post('/api/course/recommend', async (req, res) => {
  try {
    const apiKey = getOrsKey();
    if (!apiKey) return res.status(500).json({ error: 'MISSING_ORS_API_KEY' });

    const {
      start,
      targetKm,
      radiusM,
      toleranceRatio,
      maxOrsCalls,
      include,
      debug,
    } = req.body || {};
    const DEBUG = !!debug;

    const debugLog = [];
    function logDebug(obj) {
      if (!DEBUG) return;
      debugLog.push(obj);
      if (debugLog.length > 120) debugLog.shift();
    }

    if (!start || start.lat == null || start.lng == null) {
      return res.status(400).json({ error: 'INVALID_REQUEST' });
    }

    const startPt = { lat: Number(start.lat), lng: Number(start.lng) };

    if (SERVICE_AREA?.bbox && !inBBox(startPt, SERVICE_AREA.bbox)) {
      return res.status(400).json({
        error: 'SERVICE_AREA_OUT',
        message: '서비스 지역 밖입니다.',
        service_area: SERVICE_AREA,
      });
    }

    const km = Number.isFinite(Number(targetKm)) ? Number(targetKm) : 5;
    const L = km * 1000;
    const tol = Number.isFinite(Number(toleranceRatio)) ? Number(toleranceRatio) : 0.12;
    const minL = L * (1 - tol);
    const maxL = L * (1 + tol);
    const maxCalls = Number.isFinite(Number(maxOrsCalls)) ? Number(maxOrsCalls) : 18;
    const r = Number.isFinite(Number(radiusM)) ? Number(radiusM) : 4500;

    const storeRequested = !!include?.store;

    // 거리 계산용 상수
    const minStartDistM = Math.min(900, Math.max(350, 0.12 * L));
    const offsets = [0, 25, 50, 75, 100, 125, 150];
    const approxRadius = Math.max(320, L / (2 * Math.PI));
    const dStarInit = Math.min(r * 0.75, approxRadius * 1.05);
    const attemptsPerOffset = 6;
    const nearMissMaxRatio = 0.40;

    let orsCallsUsed = 0;
    async function orsLoopGeojsonCounted(startPt0, waypoints0) {
      if (orsCallsUsed >= maxCalls) {
        const e = new Error('ORS_CALL_LIMIT_REACHED');
        e.code = 'ORS_CALL_LIMIT_REACHED';
        throw e;
      }
      orsCallsUsed += 1;
      return orsLoopGeojson(startPt0, waypoints0, apiKey);
    }

    const usedGlobal = new Set();
    const courses = [];
    const bestNearMiss = [];

    // ==========================================
    // 🚀 코스 생성 로직
    // ==========================================
    async function tryBuildCourse(offsetDeg, dStarLocal, courseIndex, attemptIndex) {
      // 1. 각도: 80도 간격 (부채꼴)
      const spread = 80;
      const bearings = [
        offsetDeg,
        offsetDeg + spread,
        offsetDeg + (spread * 2)
      ];

      const maxTargetDistHard = Math.max(500, 0.85 * dStarLocal);
      const maxTargetDistSoft = Math.max(450, 0.60 * dStarLocal);
      const bandMin = Math.max(minStartDistM, 0.65 * dStarLocal);
      const bandMax = Math.min(r, 1.45 * dStarLocal);

      const usedLocal = new Set();
      const waypoints = [];
      const waypointTypes = [];

      for (let k = 0; k < 3; k++) {
        const target = destinationPoint(startPt, bearings[k], dStarLocal);
        const candidatePool = POIS.filter((p) => {
          if (usedGlobal.has(p.id) || usedLocal.has(p.id)) return false;
          const ds = haversineM(startPt, p);
          if (ds < bandMin || ds > bandMax) return false;
          return true;
        });

        let picked = null;
        let searchTypes = [];

        // 2. 슬롯별 우선순위 결정 (여기가 핵심!)
        if (k === 0) {
          // [Slot 1] 화장실 필수 (없으면 Bin이라도)
          searchTypes = ['toilet', 'bin', 'store'];
        }
        else if (k === 1) {
          // [Slot 2] 쓰레기통 우선!
          // 하지만 없으면 화장실(toilet)로 대체 (Fallback) -> Route 실패 방지
          searchTypes = ['bin', 'toilet', 'store'];
        }
        else if (k === 2) {
          // [Slot 3] 편의점 선택 로직
          if (storeRequested) {
             // 편의점 요청 시: 오직 편의점만 검색 (강제)
             searchTypes = ['store'];
          } else {
             // 미요청 시: 화장실 등 자유롭게
             searchTypes = ['toilet', 'bin', 'store'];
          }
        }

        for (const t of searchTypes) {
          const maxTarget = (t === 'toilet' || t === 'store')
              ? maxTargetDistSoft
              : maxTargetDistHard;

          picked = pickNearestByType(
            candidatePool,
            target,
            t,
            usedLocal,
            240,
            maxTarget
          );
          if (picked) break; // 찾았으면 선택하고 다음 슬롯으로
        }

        if (!picked) {
          logDebug({ stage: 'pick_failed', k, bearing: bearings[k], requested: searchTypes });
          return null;
        }

        usedLocal.add(picked.id);
        waypoints.push({
          id: picked.id,
          type: picked.type,
          name: picked.name,
          lat: picked.lat,
          lng: picked.lng,
        });
        waypointTypes.push(picked.type);
      }

      // 최종 검증: 화장실 1개는 무조건 있어야 함
      if (!waypoints.some((w) => w.type === 'toilet')) {
        return null;
      }

      const geojson = await orsLoopGeojsonCounted(startPt, waypoints);
      const distM = getOrsDistanceM(geojson);
      if (!Number.isFinite(distM)) return null;

      return { waypoints, geojson, distM, waypointTypes };
    }

    // 메인 루프 (offset -> attempt)
    for (let oi = 0; oi < offsets.length; oi++) {
      if (courses.length >= 3 || orsCallsUsed >= maxCalls) break;

      const offset = offsets[oi];
      let dStarLocal = dStarInit;

      for (let attempt = 0; attempt < attemptsPerOffset; attempt++) {
        if (courses.length >= 3 || orsCallsUsed >= maxCalls) break;

        let built = null;
        try {
          built = await tryBuildCourse(offset, dStarLocal, oi, attempt);
        } catch (e) {
          if (e?.code === 'ORS_CALL_LIMIT_REACHED') break;
          throw e;
        }

        if (!built) {
          dStarLocal *= 1.06;
          continue;
        }

        const { waypoints, geojson, distM, waypointTypes } = built;
        const hit = distM >= minL && distM <= maxL;
        const errAbs = Math.abs(distM - L);

        if (hit) {
          waypoints.forEach((w) => usedGlobal.add(w.id));
          courses.push({
            meta: {
              index: courses.length + 1,
              targetKm: km,
              totalDistanceM: Math.round(distM),
              waypointTypes,
            },
            waypoints,
            geojson,
          });
          break; // 해당 offset 성공시 다음 offset으로
        } else {
          bestNearMiss.push({
            errAbs,
            payload: {
              meta: { index: -1, targetKm: km, totalDistanceM: Math.round(distM), waypointTypes },
              waypoints,
              geojson,
            },
          });
          // 거리 보정
          const scale = Math.sqrt(L / distM);
          dStarLocal = dStarLocal * Math.max(0.8, Math.min(1.25, scale));
        }
      }
    }

    // 결과 부족 시 NearMiss 채우기
    if (courses.length < 3 && bestNearMiss.length) {
      bestNearMiss.sort((a, b) => a.errAbs - b.errAbs);
      for (const item of bestNearMiss) {
        if (courses.length >= 3) break;
        if (item.errAbs > L * nearMissMaxRatio) continue;
        if (item.payload.waypoints.some((w) => usedGlobal.has(w.id))) continue;

        item.payload.waypoints.forEach((w) => usedGlobal.add(w.id));
        item.payload.meta.index = courses.length + 1;
        courses.push(item.payload);
      }
    }

    return res.json({
      ok: true,
      version: VERSION,
      returned: courses.length,
      meta: { targetKm: km, orsCallsUsed },
      courses,
      debug: DEBUG ? debugLog : undefined,
    });
  } catch (err) {
    const status = err.response?.status || 500;
    return res.status(status).json({ error: 'COURSE_RECOMMEND_FAILED', message: err.message });
  }
});


app.listen(PORT, '0.0.0.0', () => { // 'localhost' 대신 '0.0.0.0'을 써야 외부 기기 접속 가능
  console.log(`서버 실행 완료: http://localhost:${PORT}`);
  console.log(`서버가 모든 IP에 대해 개방되었습니다: http://0.0.0.0:${PORT}`);
  console.log('[ORS] key length:', getOrsKey()?.length || 0);
});


