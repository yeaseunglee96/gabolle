// "이 자리는 지금 그늘인가 볕인가" 를 판정한다.
//
// 왜 필요한가 — 이 서비스의 핵심은 **그림자 우선 경로**다. 여행자가 경로 요약을 볼 때
// "이 구간은 14시까지 볕, 그 뒤로 그늘" 을 읽을 수 있어야 한다. 그러려면 그림자를
// 화면에 그리는 것만으로는 모자라고, **점 하나를 물어볼 수 있어야** 한다.
//
// ── 판정 방법 ────────────────────────────────────────────────────
// 그 지점에서 **해 쪽으로 광선을 쏜다.** 광선을 따라가다가 그 높이보다 높은 건물을 만나면 그늘이다.
//
//   해 고도가 alt 일 때, 지점에서 수평으로 d m 떨어진 곳의 광선 높이는 d × tan(alt) 다.
//   그 자리 건물의 높이가 그보다 크면 해를 가린다.
//
// 🔴 화면에 **그려지는** 그림자는 이것과 계산법이 다르다. 그리는 쪽(index.html 의 rebuildShadows)은
//    건물 바닥과 그 그림자를 합쳐 **볼록 껍질**로 덮는다 — 빠르지만 ㄷ자·ㄱ자 건물에서는
//    실제보다 넓게 칠한다. 여기 판정은 **실제 바닥 모양**을 쓰므로 오목한 건물에서 둘이 어긋난다.
//    같게 만들 수도 있었지만, 화면은 빨라야 하고 판정은 맞아야 해서 갈랐다.
//
// ── 🔴 이 판정이 못 보는 것 (반드시 읽어라) ──────────────────────
//   1) **건물만 본다.** 가로수·파라솔·담장·육교·고가도로 밑·버스정류장 지붕은 안 들어간다.
//      여름 부산의 실제 그늘에서 가로수가 차지하는 몫은 작지 않다. 이 판정은 그만큼 볕을 과대평가한다
//   2) **지형이 없다.** 산이 해를 가리는 것을 못 본다. 부산은 산이 많아 이게 크다 —
//      황령산 동쪽 사면은 오후 늦게 산 그늘에 드는데 여기서는 볕으로 나온다
//   3) **화면에 로드된 타일의 건물만 본다.** 지도를 안 가 본 곳은 건물이 0개라 전부 볕으로 나온다.
//      경로 전체를 판정하려면 그 경로가 화면에 한 번은 들어와야 한다
//   4) **건물 높이는 데이터의 값**이다 (국토교통부 GIS건물통합정보). 옥탑·안테나는 안 들어 있다
//   5) 구름·미세먼지는 당연히 없다. 맑은 날 기준이다
//
// 이 다섯을 모르고 쓰면 "그늘 경로" 가 여름 한낮에 사람을 볕으로 보낸다.

const R = 6378137;
const rad = (d) => (d * Math.PI) / 180;
const mPerLon = (lat) => (Math.PI / 180) * R * Math.cos(rad(lat));
const M_PER_LAT = (Math.PI / 180) * R;

// 광선을 따라가며 재는 간격(m). 4 m 는 건물 하나를 건너뛰지 않을 만큼 촘촘하다
// (가장 작은 건물의 한 변이 대개 6 m 넘는다).
const RAY_STEP_M = 4;
// 아무리 멀어도 여기서 끊는다. 해가 낮으면 그림자가 수 km 가 되는데 그때는 도시 전체가
// 그늘이라 판정할 의미가 없다. 화면의 SHADOW_MAX_M 과 같은 값으로 맞춰 둔다.
const RAY_MAX_M = 600;
// 이보다 낮은 건물은 넣지 않는다. 화면의 SHADOW_MIN_H 와 같다 — 두 곳이 다르면
// "그림자는 안 그려졌는데 그늘이라고 나온다" 가 된다.
const MIN_BUILDING_H = 20;
// 해가 이보다 낮으면 그늘/볕을 가르지 않는다. 그림자가 끝없이 길어져 전부 그늘이 되고,
// 실제로도 그 시각의 도시는 그늘이라기보다 어스름이다.
const MIN_ALT_DEG = 6;

// ── 건물 색인 ────────────────────────────────────────────────────
// 광선이 지나는 자리마다 "여기 건물 있나" 를 물어야 한다. 전부 훑으면 한 번에 수천 번이라
// 격자에 미리 나눠 담는다. 칸 하나는 약 60 m — 건물 하나가 대개 한두 칸에 들어간다.
const CELL_M = 60;

export function buildIndex(buildings, opt = {}) {
  const minH = opt.minH ?? MIN_BUILDING_H;
  const cells = new Map();
  let lat0 = null;
  let kept = 0;
  for (const b of buildings) {
    if (!(b.h >= minH) || !b.ring || b.ring.length < 4) continue;
    if (lat0 === null) lat0 = b.ring[0][1];
    kept++;
    let x0 = Infinity, y0 = Infinity, x1 = -Infinity, y1 = -Infinity;
    for (const p of b.ring) {
      if (p[0] < x0) x0 = p[0];
      if (p[0] > x1) x1 = p[0];
      if (p[1] < y0) y0 = p[1];
      if (p[1] > y1) y1 = p[1];
    }
    const item = { ring: b.ring, h: b.h, x0, y0, x1, y1 };
    const dLon = CELL_M / mPerLon(lat0), dLat = CELL_M / M_PER_LAT;
    for (let ix = Math.floor(x0 / dLon); ix <= Math.floor(x1 / dLon); ix++) {
      for (let iy = Math.floor(y0 / dLat); iy <= Math.floor(y1 / dLat); iy++) {
        const k = ix + ':' + iy;
        let arr = cells.get(k);
        if (!arr) cells.set(k, (arr = []));
        arr.push(item);
      }
    }
  }
  const lat = lat0 ?? 35.16;
  return {
    cells,
    count: kept,
    dLon: CELL_M / mPerLon(lat),
    dLat: CELL_M / M_PER_LAT,
    at(lon, latQ) {
      return cells.get(Math.floor(lon / this.dLon) + ':' + Math.floor(latQ / this.dLat));
    },
  };
}

// 점이 다각형 안에 있나 (ray casting). ring 은 닫혀 있어도 되고 아니어도 된다.
function inRing(lon, lat, ring) {
  let inside = false;
  for (let i = 0, j = ring.length - 1; i < ring.length; j = i++) {
    const xi = ring[i][0], yi = ring[i][1];
    const xj = ring[j][0], yj = ring[j][1];
    if ((yi > lat) !== (yj > lat) && lon < ((xj - xi) * (lat - yi)) / (yj - yi) + xi) inside = !inside;
  }
  return inside;
}

/**
 * 이 점이 그늘인가.
 * @param {number} lon
 * @param {number} lat
 * @param {{altitude:number, azimuth:number}} sun  sun.mjs 의 sunPosition 결과
 * @param {object} index buildIndex 의 결과
 * @returns {{shaded:boolean, by:number|null, reason:string}}
 *   by 는 가린 건물의 높이(m). reason 은 왜 그렇게 판정했는지 — 화면에 그대로 보여줄 수 있다
 */
export function shadeAt(lon, lat, sun, index) {
  if (!sun || sun.altitude <= 0) return { shaded: true, by: null, reason: '해가 져 있음' };
  if (sun.altitude < MIN_ALT_DEG) return { shaded: true, by: null, reason: `해가 낮음 (${sun.altitude.toFixed(1)}°)` };
  if (!index || index.count === 0) return { shaded: false, by: null, reason: '주변 건물 데이터가 없음' };

  // 해 쪽으로 가는 방향 (동, 북)
  const az = rad(sun.azimuth);
  const ex = Math.sin(az), ny = Math.cos(az);
  const tanAlt = Math.tan(rad(sun.altitude));
  const kx = mPerLon(lat);

  for (let d = RAY_STEP_M; d <= RAY_MAX_M; d += RAY_STEP_M) {
    const rayH = d * tanAlt;      // 이 거리에서 광선의 높이
    if (rayH > 300) break;         // 부산에서 가장 높은 건물(엘시티 411 m)만 남는 높이. 사실상 여기서 끝난다
    const qLon = lon + (ex * d) / kx;
    const qLat = lat + (ny * d) / M_PER_LAT;
    const cand = index.at(qLon, qLat);
    if (!cand) continue;
    for (const b of cand) {
      if (b.h <= rayH) continue;                       // 광선보다 낮으면 못 가린다
      if (qLon < b.x0 || qLon > b.x1 || qLat < b.y0 || qLat > b.y1) continue;
      if (inRing(qLon, qLat, b.ring)) {
        return { shaded: true, by: b.h, reason: `${Math.round(d)} m 앞 ${Math.round(b.h)} m 건물` };
      }
    }
  }
  return { shaded: false, by: null, reason: '해를 가리는 건물 없음' };
}

/**
 * 경로 위를 일정 간격으로 찍어 볼 점들. 경로 요약은 꼭짓점이 아니라
 * **일정 거리마다**로 봐야 한다 — 긴 직선 구간이 점 두 개로 요약되면 안 되기 때문이다.
 * @param {Array<[number,number]>} coords
 * @param {number} stepM 몇 m 마다 (기본 25)
 */
export function sampleRoute(coords, stepM = 25) {
  const cum = [0];
  for (let i = 1; i < coords.length; i++) {
    const lat = (coords[i][1] + coords[i - 1][1]) / 2;
    const dx = (coords[i][0] - coords[i - 1][0]) * mPerLon(lat);
    const dy = (coords[i][1] - coords[i - 1][1]) * M_PER_LAT;
    cum.push(cum[i - 1] + Math.hypot(dx, dy));
  }
  const total = cum[cum.length - 1];
  const out = [];
  for (let s = 0; s <= total; s += stepM) {
    let i = 1;
    while (i < cum.length - 1 && cum[i] < s) i++;
    const f = (s - cum[i - 1]) / (cum[i] - cum[i - 1] || 1);
    out.push([
      coords[i - 1][0] + (coords[i][0] - coords[i - 1][0]) * f,
      coords[i - 1][1] + (coords[i][1] - coords[i - 1][1]) * f,
      s,
    ]);
  }
  return { points: out, totalM: total };
}

/**
 * 하루 전체에 대해 "이 경로가 몇 시에 얼마나 그늘인가".
 * 시간축 위의 그늘/볕 띠가 이 결과로 그려진다.
 *
 * @param {Array<[number,number]>} coords 경로
 * @param {object} index buildIndex 결과
 * @param {(hour:number) => {altitude:number,azimuth:number}} sunAtHour
 *        그 시각의 해. 날짜·좌표를 아는 쪽(화면)이 넘겨준다 — 이 파일은 날짜를 모른다
 * @param {{fromHour?:number, toHour?:number, stepMin?:number, sampleM?:number}} [opt]
 * @returns {{bins: Array<{hour:number, shadedRatio:number, alt:number}>,
 *            perPoint: Array<{distM:number, spans:Array<[number,number]>}>,
 *            samples:number}}
 *   bins      시각마다 경로의 몇 %가 그늘인가
 *   perPoint  점마다 "몇 시부터 몇 시까지 그늘" 구간 목록
 */
export function shadeTimeline(coords, index, sunAtHour, opt = {}) {
  const from = opt.fromHour ?? 5;
  const to = opt.toHour ?? 21;
  const stepMin = opt.stepMin ?? 20;
  const { points } = sampleRoute(coords, opt.sampleM ?? 40);
  const bins = [];
  // 점마다 시간축 위의 그늘 여부를 모아 두었다가 구간으로 접는다
  const perPointFlags = points.map(() => []);

  for (let h = from; h <= to + 1e-9; h += stepMin / 60) {
    const sun = sunAtHour(h);
    let shaded = 0;
    for (let i = 0; i < points.length; i++) {
      const r = shadeAt(points[i][0], points[i][1], sun, index);
      if (r.shaded) shaded++;
      perPointFlags[i].push(r.shaded);
    }
    bins.push({ hour: +h.toFixed(3), shadedRatio: points.length ? shaded / points.length : 0, alt: sun.altitude });
  }

  const perPoint = points.map((p, i) => {
    const spans = [];
    let start = null;
    for (let k = 0; k < perPointFlags[i].length; k++) {
      if (perPointFlags[i][k] && start === null) start = bins[k].hour;
      if (!perPointFlags[i][k] && start !== null) { spans.push([start, bins[k].hour]); start = null; }
    }
    if (start !== null) spans.push([start, bins[bins.length - 1].hour]);
    return { distM: p[2], spans };
  });

  return { bins, perPoint, samples: points.length };
}
