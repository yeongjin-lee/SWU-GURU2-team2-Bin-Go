const express = require('express');
const axios = require('axios');
const fs = require('fs');
const path = require('path');
require('dotenv').config();

/**
 * ✅ 버전
 */
const VERSION = 'BIN-SOFT-v8-storeSlot-servicearea-toiletRequired-2026-01-25';
console.log('[BOOT] running:', __filename);
console.log('[VERSION]', VERSION);

const app = express();
const PORT = 3000;

app.use(express.json());

// ===============================
// ✅ ORS KEY 안전 처리 (개행/공백/따옴표 방지)
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
// ✅ destination point (meters, bearing degrees)
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

  const lng2 =
    lng1 +
    Math.atan2(
      Math.sin(brng) * Math.sin(dr) * Math.cos(lat1),
      Math.cos(dr) - Math.sin(lat1) * Math.sin(lat2)
    );

  return { lat: toDeg(lat2), lng: toDeg(lng2) };
}

// ===============================
// ✅ 후보 선택: target 근처에서 type 1개 고르기 (+ 타겟 최대거리 제한)
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
// ✅ ORS 호출: loop (start + waypoints + start) + 429 백오프
// ===============================
async function orsLoopGeojson(startPt, waypoints, apiKey) {
  const coordinates = [
    [startPt.lng, startPt.lat],
    ...waypoints.map((p) => [p.lng, p.lat]),
    [startPt.lng, startPt.lat],
  ];

  const url =
    'https://api.openrouteservice.org/v2/directions/foot-walking/geojson';
  const body = { coordinates };
  const headers = { Authorization: apiKey, 'Content-Type': 'application/json' };

  // ✅ 호출 간격(429 완화)
  await sleep(180);

  const retries = 4;
  let backoff = 700;

  for (let i = 0; i <= retries; i++) {
    try {
      const orsResp = await axios.post(url, body, { headers, timeout: 15000 });
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
// ✅ 서비스 지역(bbox) 자동 구성
//   - POI 전체의 bbox를 만들고, marginDeg 만큼 여유를 둠
//   - start가 bbox 밖이면 서비스 불가 처리
// ===============================
function computeBBox(points) {
  let minLat = Infinity,
    maxLat = -Infinity,
    minLng = Infinity,
    maxLng = -Infinity;

  for (const p of points) {
    if (!Number.isFinite(p.lat) || !Number.isFinite(p.lng)) continue;
    minLat = Math.min(minLat, p.lat);
    maxLat = Math.max(maxLat, p.lat);
    minLng = Math.min(minLng, p.lng);
    maxLng = Math.max(maxLng, p.lng);
  }

  if (!Number.isFinite(minLat)) {
    return null;
  }
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
  const marginDeg = 0.03; // 약 3km 내외(위도 기준) 여유
  const bbox0 = computeBBox(POIS);
  if (!bbox0) return null;
  const bbox = expandBBox(bbox0, marginDeg);
  console.log('[SERVICE_AREA] bbox:', bbox, 'marginDeg:', marginDeg);
  return { bbox, marginDeg };
})();

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

  return res.json({
    ok: true,
    version: VERSION,
    start,
    radiusM,
    types,
    counts: {
      totalPoisLoaded: POIS.length,
      candidates: candidates.length,
    },
    sample: candidates.slice(0, 20).map((p) => ({
      id: p.id,
      type: p.type,
      name: p.name,
      lat: p.lat,
      lng: p.lng,
      distanceM: p.distanceM,
    })),
  });
});

// ===============================
// ✅ 서버 헬스체크
// ===============================
app.get('/', (req, res) => {
  const k = getOrsKey();
  res.json({
    ok: true,
    version: VERSION,
    message: 'SERVER OK - NO DB',
    running_file: __filename,
    has_ors_key: !!k,
    ors_key_length: k?.length || 0,
    poi_counts: {
      bins: bins.length,
      toilets: toilets.length,
      total: POIS.length,
    },
    service_area: SERVICE_AREA || null,
  });
});

// ===============================
// ✅ ORS 보행자 경로 (GeoJSON)
// ===============================
app.post('/api/ors/walking', async (req, res) => {
  try {
    const apiKey = getOrsKey();
    if (!apiKey) {
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
          Authorization: apiKey,
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

// ===============================
// ✅ 추천 코스 3개
//  - toilet 1개 이상 필수
//  - storeRequested면 3개 중 1개 슬롯은 "store 우선" 시도
//  - bin은 있으면 좋음(soft)
//  - 핵심: start-distance band 강제해서 폭주 막음
//  - 서비스 지역 밖이면 차단(프론트 팝업용 에러)
// ===============================
app.post('/api/course/recommend', async (req, res) => {
  try {
    const apiKey = getOrsKey();
    if (!apiKey) {
      return res.status(500).json({
        error: 'MISSING_ORS_API_KEY',
        message: '.env에 ORS_API_KEY가 없습니다',
      });
    }

    const {
      start,
      targetKm,
      radiusM,
      toleranceRatio,
      maxOrsCalls,
      include,
      stores,
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
      return res.status(400).json({
        error: 'INVALID_REQUEST',
        message: 'start(lat,lng)가 필요합니다',
      });
    }

    const startPt = { lat: Number(start.lat), lng: Number(start.lng) };

    // ✅ 서비스 지역 체크 (서울 데이터만 있을 때 외부 좌표 막기)
    if (SERVICE_AREA?.bbox && !inBBox(startPt, SERVICE_AREA.bbox)) {
      return res.status(400).json({
        error: 'SERVICE_AREA_OUT',
        version: VERSION,
        message:
          '아직 서비스는 서울/수도권(데이터 범위)에서만 지원합니다. 서울에서 다시 시도해주세요.',
        service_area: SERVICE_AREA,
        start: startPt,
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

    const minL = L * (1 - tol);
    const maxL = L * (1 + tol);

    const maxCalls = Number.isFinite(Number(maxOrsCalls))
      ? Number(maxOrsCalls)
      : 18;

    const r = Number.isFinite(Number(radiusM)) ? Number(radiusM) : 4500;

    const wantToilet = true; // ✅ 최소 1개는 반드시 포함(정책 고정)
    const storeRequested = !!include?.store;

    const storePool = Array.isArray(stores) ? stores : [];
    const POOL = [...POIS, ...storePool];

    // ✅ start에서 너무 가까운 POI는 제외
    const minStartDistM = Math.min(900, Math.max(350, 0.12 * L)); // 5km면 ~600

    // 방향 다양화
    const offsets = [0, 25, 50, 75, 100, 125, 150];

    // ✅ dStar 초기값 과도 방지
    const approxRadius = Math.max(320, L / (2 * Math.PI)); // 5km면 ~795m
    const dStarInit = Math.min(r * 0.75, approxRadius * 1.05);

    const attemptsPerOffset = 6;
    const nearMissMaxRatio = 0.40;

    let orsCallsUsed = 0;
    const usedGlobal = new Set();
    const courses = [];
    const bestNearMiss = [];

    // ✅ (핵심) storeRequested면, 3개 슬롯 중 1개는 store 우선 슬롯으로 지정
    // 코스/시도마다 위치를 바꿔 다양성 확보
    function getStoreSlot(courseIndex, attemptIndex) {
      if (!storeRequested) return -1;
      return (courseIndex + attemptIndex) % 3; // 0/1/2 중 하나
    }

    // 나머지(기본) 선호 순서: toilet 우선, 그 다음 bin, 그 다음 store
    // (store는 “요청 슬롯”에서 우선 시도하므로 기본 슬롯에서는 너무 강제하지 않음)
    function preferenceListDefault() {
      return ['toilet', 'bin', 'store'];
    }

    async function tryBuildCourse(offsetDeg, dStarLocal, courseIndex, attemptIndex) {
      const bearings = [0 + offsetDeg, 120 + offsetDeg, 240 + offsetDeg];

      // ✅ target 근처 제한
      const maxTargetDistHard = Math.max(500, 0.85 * dStarLocal);
      const maxTargetDistSoft = Math.max(450, 0.60 * dStarLocal);

      // ✅ start-distance band 강제
      const bandMin = Math.max(minStartDistM, 0.65 * dStarLocal);
      const bandMax = Math.min(r, 1.45 * dStarLocal);

      const usedLocal = new Set();
      const waypoints = [];
      const waypointTypes = [];

      const storeSlot = getStoreSlot(courseIndex, attemptIndex);

      for (let k = 0; k < 3; k++) {
        const target = destinationPoint(startPt, bearings[k], dStarLocal);

        const candidatePool = POOL.filter((p) => {
          if (usedGlobal.has(p.id) || usedLocal.has(p.id)) return false;
          const ds = haversineM(startPt, p);
          if (ds < bandMin || ds > bandMax) return false;
          return true;
        });

        let picked = null;

        // ✅ store 우선 슬롯: store -> toilet -> bin 순으로 시도
        if (k === storeSlot) {
          picked =
            pickNearestByType(candidatePool, target, 'store', usedLocal, 240, maxTargetDistSoft) ||
            pickNearestByType(candidatePool, target, 'toilet', usedLocal, 240, maxTargetDistSoft) ||
            pickNearestByType(candidatePool, target, 'bin', usedLocal, 240, maxTargetDistHard);
        } else {
          // ✅ 기본 슬롯: toilet 우선, 그 다음 bin, 그 다음 store(있으면)
          const pref = preferenceListDefault();
          for (const t of pref) {
            const maxTarget =
              t === 'toilet' || t === 'store' ? maxTargetDistSoft : maxTargetDistHard;
            picked = pickNearestByType(candidatePool, target, t, usedLocal, 240, maxTarget);
            if (picked) break;
          }
        }

        if (!picked) {
          logDebug({
            stage: 'pick_failed',
            offsetDeg,
            attemptIndex,
            k,
            bearing: bearings[k],
            dStarLocal: Math.round(dStarLocal),
            bandMinM: Math.round(bandMin),
            bandMaxM: Math.round(bandMax),
            maxTargetDistHardM: Math.round(maxTargetDistHard),
            maxTargetDistSoftM: Math.round(maxTargetDistSoft),
            poolSize: candidatePool.length,
            storeSlot,
            hasStoreCandidates: storePool.length > 0,
          });
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

      // ✅ toilet 최소 1개 보장
      if (!waypoints.some((w) => w.type === 'toilet')) {
        logDebug({ stage: 'no_toilet', offsetDeg, attemptIndex, waypointTypes });
        return null;
      }

      const geojson = await orsLoopGeojson(startPt, waypoints, apiKey);
      const distM = getOrsDistanceM(geojson);

      if (!Number.isFinite(distM)) {
        logDebug({ stage: 'dist_parse_failed', offsetDeg, attemptIndex });
        return null;
      }

      return {
        waypoints,
        geojson,
        distM,
        waypointTypes,
        bandMin,
        bandMax,
        maxTargetDistHard,
        maxTargetDistSoft,
        storeSlot,
      };
    }

    for (let oi = 0; oi < offsets.length; oi++) {
      if (courses.length >= 3) break;
      if (orsCallsUsed >= maxCalls) break;

      const offset = offsets[oi];
      let dStarLocal = dStarInit;

      for (let attempt = 0; attempt < attemptsPerOffset; attempt++) {
        if (courses.length >= 3) break;
        if (orsCallsUsed >= maxCalls) break;

        const built = await tryBuildCourse(offset, dStarLocal, oi, attempt);
        orsCallsUsed += 1;

        if (!built) {
          dStarLocal *= 1.06;
          continue;
        }

        const {
          waypoints,
          geojson,
          distM,
          waypointTypes,
          bandMin,
          bandMax,
          maxTargetDistHard,
          maxTargetDistSoft,
          storeSlot,
        } = built;

        const hit = distM >= minL && distM <= maxL;
        const errAbs = Math.abs(distM - L);

        logDebug({
          stage: 'built',
          offsetDeg: offset,
          attempt,
          dStarUsedM: Math.round(dStarLocal),
          distM: Math.round(distM),
          hit,
          minL: Math.round(minL),
          maxL: Math.round(maxL),
          waypointTypes,
          storeSlot,
          bandMinM: Math.round(bandMin),
          bandMaxM: Math.round(bandMax),
          maxTargetDistHardM: Math.round(maxTargetDistHard),
          maxTargetDistSoftM: Math.round(maxTargetDistSoft),
          hasStoreCandidates: storePool.length > 0,
        });

        if (hit) {
          waypoints.forEach((w) => usedGlobal.add(w.id));
          courses.push({
            meta: {
              index: courses.length + 1,
              targetKm: Number(targetKm),
              targetDistanceM: Math.round(L),
              totalDistanceM: Math.round(distM),
              toleranceRatio: tol,
              toleranceHit: true,
              radiusM: r,
              orsCallsUsed,
              offsetDeg: offset,
              attempt,
              dStarUsedM: Math.round(dStarLocal),
              waypointTypes,
              storeRequested,
              storeCandidatesProvided: storePool.length,
              storeSlot,
              toiletRequiredAtLeastOne: true,
              minStartDistM: Math.round(minStartDistM),
              bandMinM: Math.round(bandMin),
              bandMaxM: Math.round(bandMax),
            },
            waypoints,
            geojson,
          });
          break;
        } else {
          bestNearMiss.push({
            errAbs,
            payload: {
              meta: {
                index: -1,
                targetKm: Number(targetKm),
                targetDistanceM: Math.round(L),
                totalDistanceM: Math.round(distM),
                toleranceRatio: tol,
                toleranceHit: false,
                radiusM: r,
                orsCallsUsed,
                offsetDeg: offset,
                attempt,
                dStarUsedM: Math.round(dStarLocal),
                waypointTypes,
                storeRequested,
                storeCandidatesProvided: storePool.length,
                storeSlot,
                toiletRequiredAtLeastOne: true,
                minStartDistM: Math.round(minStartDistM),
                bandMinM: Math.round(bandMin),
                bandMaxM: Math.round(bandMax),
              },
              waypoints,
              geojson,
            },
          });

          // ✅ 거리 보정(안정적으로 수렴)
          const scale = Math.sqrt(L / distM);
          const clamped = Math.max(0.80, Math.min(1.25, scale));
          dStarLocal = dStarLocal * clamped;
        }
      }
    }

    if (courses.length < 3 && bestNearMiss.length) {
      bestNearMiss.sort((a, b) => a.errAbs - b.errAbs);
      for (const item of bestNearMiss) {
        if (courses.length >= 3) break;
        if (item.errAbs > L * nearMissMaxRatio) continue;

        const payload = item.payload;
        const overlap = payload.waypoints.some((w) => usedGlobal.has(w.id));
        if (overlap) continue;

        payload.waypoints.forEach((w) => usedGlobal.add(w.id));
        payload.meta.index = courses.length + 1;
        courses.push(payload);
      }
    }

    return res.json({
      ok: true,
      version: VERSION,
      requested: 3,
      returned: courses.length,
      meta: {
        targetKm: Number(targetKm),
        targetDistanceM: Math.round(L),
        toleranceRatio: tol,
        radiusM: r,
        orsCallsUsed,
        maxOrsCalls: maxCalls,
        include: {
          toiletRequiredAtLeastOne: true,
          storeRequested,
          storeCandidatesProvided: storePool.length,
          storeSlotBehavior: '1 slot tries store first; fallback to toilet/bin',
        },
        minStartDistM: Math.round(minStartDistM),
        nearMissMaxRatio,
        poolCounts: {
          bins: bins.length,
          toilets: toilets.length,
          stores: storePool.length,
          total: POOL.length,
        },
        service_area: SERVICE_AREA || null,
      },
      courses,
      debug: DEBUG ? debugLog : undefined,
    });
  } catch (err) {
    const status = err.response?.status || 500;
    const data = err.response?.data || null;

    return res.status(status).json({
      error: 'COURSE_RECOMMEND_FAILED',
      status,
      data,
      message: err.message,
    });
  }
});

app.listen(PORT, () => {
  console.log(`서버 실행 완료: http://localhost:${PORT}`);
  console.log('[ORS] key length:', getOrsKey()?.length || 0);
});
