// 이동 수단 — 경로 위를 실제로 움직이는 보행자·버스·승용차.
//
// 왜 필요한가 — 경로를 선 하나로만 그리면 "여기로 간다" 는 알아도 "얼마나 걸린다" 는 안 보인다.
// 여행 앱의 경로 요약은 시간이 핵심이라, 도보 15분과 버스 15분이 화면에서 같아 보이면 안 된다.
// 크기·속도가 다른 표식이 실제로 지나가면 그 차이가 설명 없이 읽힌다.
//
// ── 왜 3D 상자인가 (심볼 아이콘·캔버스가 아니라) ─────────────────
// 고른 방식: **fill-extrusion(진짜 입체 상자)을 매 프레임 GeoJSON 으로 다시 넣는다.**
// 후보가 셋이었고 이렇게 갈랐다.
//
//   1) 심볼 레이어 + 아이콘 회전
//      가장 싸다. 그런데 **높이를 못 준다.** 이 화면의 경로는 광안대교(상판 34 m)·부산항대교(63 m)
//      위를 지나는데, 심볼은 지면에 붙으므로 다리 위를 걷는 사람이 **바다에 떠 있게** 그려진다.
//      그리고 이 화면은 건물·다리·그림자가 전부 입체다 — 납작한 스프라이트 하나만 평면이면
//      그것만 붙여 넣은 그림처럼 보인다.
//   2) three.js 커스텀 레이어
//      모양은 가장 좋다. 그런데 이 저장소는 이미 three 를 뺀 경로를 골랐고(three-test.html 은 실험 잔재),
//      지형·그림자와 깊이(depth)를 맞추는 문제를 다시 떠안는다. 파도(sea.mjs)를 뺀 이유가 정확히 그것이다.
//   3) **fill-extrusion 상자 (고른 것)**
//      다리 상판 높이에 그대로 올릴 수 있고(base/height 가 지형 위 높이다), 건물·다리와 같은
//      깊이·조명·그림자 체계 안에 있다. 표식이 3개뿐이라 도형은 8개 남짓이고,
//      매 프레임 setData 를 해도 1 KB 미만이다 — 9,000개를 다루는 그림자보다 세 자릿수 가볍다.
//
// 상자를 쓰는 대가는 모양이 거칠다는 것이다. 그래서 수단마다 상자를 2~3개 겹쳐
// 실루엣이 읽히게 한다 (승용차는 몸체+객실, 버스는 긴 몸체+지붕, 사람은 몸통+머리).

const R = 6378137;
const rad = (d) => (d * Math.PI) / 180;
const deg = (r) => (r * 180) / Math.PI;
const mPerLon = (lat) => (Math.PI / 180) * R * Math.cos(rad(lat));
const M_PER_LAT = (Math.PI / 180) * R;

// ── 수단별 제원 ──────────────────────────────────────────────────
// 속도는 **부산의 실제 통행 속도**에 맞춘 값이다. 최고 속도가 아니라 평균이다 —
// 버스는 정류장에 서고 승용차는 신호에 걸리므로, 최고 속도로 움직이면 화면에서 즉시 거짓말이 된다.
//   도보 4.5 km/h  성인 평균 보행 속도. 보행자 신호 설계에 쓰는 1.0~1.2 m/s 와 같은 범위
//   버스 22 km/h   시내버스 표정속도(정차 시간을 포함한 평균). 출처 없이 정한 값이라 **추정값**이다
//   승용차 38 km/h 도심 평균 주행 속도. 이것도 **추정값**이다
// 🔴 두 값 모두 공식 통계로 확인하지 않았다. 화면의 "빠르기 차이" 를 만드는 것이 목적이고,
//    통행 시간을 계산에 쓰려면 실제 대중교통 API 의 소요 시간으로 바꿔야 한다.
export const MODES = {
  walk: {
    label: '도보', speedKmh: 4.5, estimatedSpeed: false,
    // 사람 — 몸통 + 머리. 어깨 폭 0.45 m, 키 1.75 m 는 실제 성인 치수다.
    lenM: 0.5, widM: 0.45, topM: 1.75,
    color: '#e2574c',
    parts: [
      { len: 1.0, wid: 1.0, base: 0.00, top: 0.72, fwd: 0 },   // 다리
      { len: 1.1, wid: 1.15, base: 0.72, top: 0.90, fwd: 0 },  // 어깨 — 위에서 볼 때 사람으로 읽히게 살짝 넓다
      { len: 0.62, wid: 0.62, base: 0.90, top: 1.00, fwd: 0.05 }, // 머리
    ],
    // 이 확대 아래에서는 사람이 1픽셀도 안 된다. 그리면 지글거리기만 하고 안 보인다.
    minZoom: 15.5,
  },
  bus: {
    label: '버스', speedKmh: 22, estimatedSpeed: true,
    // 시내버스 대형 — 길이 11 m · 폭 2.5 m · 높이 3.2 m (도로교통법상 대형버스 한도 안)
    lenM: 11, widM: 2.5, topM: 3.2,
    color: '#2f6fb0',
    parts: [
      { len: 1.0, wid: 1.0, base: 0.00, top: 0.30, fwd: 0 },    // 차체 아래(바퀴칸)
      { len: 1.0, wid: 1.0, base: 0.30, top: 0.94, fwd: 0 },    // 객실
      { len: 0.80, wid: 0.86, base: 0.94, top: 1.00, fwd: -0.03 }, // 지붕 — 뒤로 조금 물러 앞유리 경사를 흉내낸다
    ],
    minZoom: 13.5,
  },
  car: {
    label: '승용차', speedKmh: 38, estimatedSpeed: true,
    // 중형 승용차 — 길이 4.6 m · 폭 1.85 m · 높이 1.5 m
    lenM: 4.6, widM: 1.85, topM: 1.5,
    color: '#e8b23a',
    parts: [
      { len: 1.0, wid: 1.0, base: 0.00, top: 0.62, fwd: 0 },     // 몸체
      // 객실은 **뒤쪽으로 치우쳐** 있다. 이게 앞뒤를 만든다 — 앞이 어디인지 모르면
      // 회전이 맞아도 차가 뒤로 가는 것처럼 보인다.
      { len: 0.52, wid: 0.80, base: 0.62, top: 1.00, fwd: -0.12 },
    ],
    minZoom: 14.5,
  },
};

// ── 경로 ─────────────────────────────────────────────────────────
/**
 * 좌표 배열을 "t(0~1) 를 주면 그 시점의 위치·방향" 을 돌려주는 것으로 바꾼다.
 *
 * 🔴 t 는 **거리 비율이지 배열 인덱스가 아니다.** 인덱스로 나누면 꼭짓점이 촘촘한 구간에서
 *    표식이 느려지고 성긴 구간에서 순간이동한다 — 그게 가장 먼저 가짜로 보이는 지점이다.
 *    그래서 누적 거리를 미리 재 두고 그 위에서 찾는다.
 *
 * @param {Array<[number,number]|[number,number,number]>} coords
 *   [경도, 위도] 또는 [경도, 위도, 높이m]. 높이는 **지형 위 높이**다 —
 *   다리 위를 지나는 구간은 상판 높이를 적어 준다. 없으면 0(지면)으로 본다.
 * @param {string} modeName 'walk' | 'bus' | 'car'
 * @param {{loop?: boolean}} [opt] loop 면 끝에서 처음으로 돌아온다(왕복이 아니라 순환)
 */
export function makeRoute(coords, modeName, opt = {}) {
  const mode = MODES[modeName];
  if (!mode) throw new Error('모르는 이동수단: ' + modeName);
  if (!Array.isArray(coords) || coords.length < 2) throw new Error('경로에 점이 둘 이상 필요하다');

  const pts = coords.map((c) => [c[0], c[1], c.length > 2 ? c[2] : 0]);
  // 누적 거리 — cum[i] 는 시작점에서 pts[i] 까지 몇 m 인가
  const cum = [0];
  for (let i = 1; i < pts.length; i++) {
    const lat = (pts[i][1] + pts[i - 1][1]) / 2;
    const dx = (pts[i][0] - pts[i - 1][0]) * mPerLon(lat);
    const dy = (pts[i][1] - pts[i - 1][1]) * M_PER_LAT;
    cum.push(cum[i - 1] + Math.hypot(dx, dy));
  }
  const totalM = cum[cum.length - 1];
  if (!(totalM > 0)) throw new Error('경로 길이가 0 이다');

  const speedMs = (mode.speedKmh * 1000) / 3600;

  /** 시작점에서 s m 지점의 위치·높이·방향 */
  function atMeters(s) {
    const clamped = Math.max(0, Math.min(totalM, s));
    // 이분 탐색 — 점이 수백 개여도 로그 시간이다
    let lo = 0, hi = cum.length - 1;
    while (hi - lo > 1) {
      const mid = (lo + hi) >> 1;
      if (cum[mid] <= clamped) lo = mid; else hi = mid;
    }
    const segLen = cum[hi] - cum[lo] || 1;
    const f = (clamped - cum[lo]) / segLen;
    const a = pts[lo], b = pts[hi];
    const lon = a[0] + (b[0] - a[0]) * f;
    const lat = a[1] + (b[1] - a[1]) * f;
    const z = a[2] + (b[2] - a[2]) * f;

    // 방향 — 지금 있는 선분의 방향. 북쪽 0, 시계 방향(도).
    // 🔴 위도로 나눌 때 경도 1도의 실제 길이가 위도마다 다르다는 것을 반드시 넣어야 한다.
    //    안 넣으면 부산(위도 35°)에서 방향이 최대 10도 넘게 틀어진다.
    const dx = (b[0] - a[0]) * mPerLon(lat);
    const dy = (b[1] - a[1]) * M_PER_LAT;
    const bearing = (deg(Math.atan2(dx, dy)) + 360) % 360;
    return { lon, lat, z, bearing, distM: clamped, segIndex: lo };
  }

  return {
    mode: modeName,
    spec: mode,
    totalM,
    /** 이 경로를 이 수단으로 갈 때 걸리는 시간(초) */
    durationSec: totalM / speedMs,
    coords: pts,
    /**
     * 🔴 이 함수가 이 파일의 계약이다.
     * t(0~1) → { lon, lat, z, bearing, distM }
     * 시간 슬라이더든 애니메이션 루프든 경로 요약이든, 붙이는 쪽은 t 만 주면 된다.
     */
    at(t) {
      let u = Number.isFinite(t) ? t : 0;
      // 🔴 loop 라도 **t = 1 은 끝점 그대로** 둔다. 그냥 u % 1 로 감으면 1 이 0 이 되어
      //    at(1) 이 출발점을 준다 — 경로 요약에서 "도착 지점" 을 물었는데 출발 지점이 온다.
      //    범위를 벗어난 값만 감는다.
      if (opt.loop) { if (u < 0 || u > 1) u = ((u % 1) + 1) % 1; }
      else u = Math.max(0, Math.min(1, u));
      return atMeters(u * totalM);
    },
    atMeters,
  };
}

// ── 도형 만들기 ──────────────────────────────────────────────────
// 중심 · 방향 · 길이 · 폭 → 회전된 직사각형 하나.
// fwd 는 앞뒤로 밀어 둘 양을 전체 길이 대비 비율로 준 것이다 (승용차 객실이 뒤로 치우치는 데 쓴다).
function rect(lon, lat, bearingDeg, lenM, widM, fwdM) {
  const kx = mPerLon(lat), ky = M_PER_LAT;
  const th = rad(bearingDeg);
  // 진행 방향 단위벡터(동, 북)와 그 왼쪽
  const fx = Math.sin(th), fy = Math.cos(th);
  const sx = Math.cos(th), sy = -Math.sin(th);
  const cx = lon + (fx * fwdM) / kx;
  const cy = lat + (fy * fwdM) / ky;
  const hl = lenM / 2, hw = widM / 2;
  const ring = [];
  for (const [a, b] of [[+1, +1], [+1, -1], [-1, -1], [-1, +1]]) {
    ring.push([
      cx + (fx * hl * a + sx * hw * b) / kx,
      cy + (fy * hl * a + sy * hw * b) / ky,
    ]);
  }
  ring.push(ring[0]);
  return [ring];
}

/**
 * 표식 하나를 상자 몇 개짜리 GeoJSON 도형으로. 회전·높이가 여기서 정해진다.
 * @param {{lon:number,lat:number,z:number,bearing:number}} p  route.at(t) 의 결과
 * @param {string} modeName
 * @param {string} [id] 도형에 붙일 이름표 (색을 수단별로 칠하는 데 쓴다)
 */
export function actorFeatures(p, modeName, id = modeName) {
  const m = MODES[modeName];
  const out = [];
  for (const part of m.parts) {
    out.push({
      type: 'Feature',
      properties: {
        actor: modeName,
        id,
        // 🔴 base/height 는 **지형 위 높이**다. 다리 위면 p.z 가 상판 높이라 그 위에 선다.
        hb: +(p.z + part.base * m.topM).toFixed(2),
        h: +(p.z + part.top * m.topM).toFixed(2),
      },
      geometry: {
        type: 'Polygon',
        coordinates: rect(p.lon, p.lat, p.bearing, m.lenM * part.len, m.widM * part.wid, m.lenM * part.fwd),
      },
    });
  }
  return out;
}

/** 여러 표식을 한 덩어리 GeoJSON 으로. 지도 소스에 그대로 넣는다. */
export function actorCollection(list) {
  const features = [];
  for (const a of list) features.push(...actorFeatures(a.p, a.mode, a.id));
  return { type: 'FeatureCollection', features };
}

/** 수단별 색 — 지도 레이어의 match 식에 그대로 쓴다. */
export function actorColorExpr() {
  const e = ['match', ['get', 'actor']];
  for (const [k, v] of Object.entries(MODES)) e.push(k, v.color);
  e.push('#888888');
  return e;
}
