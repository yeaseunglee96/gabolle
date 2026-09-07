// 부산의 횡단보도를 땅 위의 흰 줄무늬로 만든다.
//
// 왜 필요한가 — 도보 시점에서 길이 "걸어다니는 곳" 으로 읽히려면 바닥에 사람이 아는 표시가
// 있어야 한다. 확대 18 부터는 항공사진을 걷어내므로(S15P21E201-725) 그전까지 사진에 찍혀
// 있던 흰 줄무늬도 같이 사라진다. 그 자리를 우리가 그린다.
//
// 재료는 OpenStreetMap 이다. 🔴 **건물과 반대로, 거리 표시는 OSM 말고 쓸 것이 없다** —
// 건물은 우리가 가진 국토교통부 자료가 47만 동이고 OSM 은 5만 동뿐이라 안 쓰지만,
// 횡단보도는 전국 단위로 좌표가 공개된 다른 자료를 못 찾았다.
//
//   node crossings.mjs        → public/crossings.geojson 이 생긴다
//
// 받은 것은 work/_osm-crossings.json 에 두고 다시 안 받는다. 다시 받으려면 그 파일을 지운다.
//
// ── 🔴 무엇이 자료이고 무엇이 우리가 정한 것인가 ────────────────────
//
// 자료에서 오는 것 (OSM way 의 실제 좌표):
//   · 횡단보도가 **있는 자리**
//   · **방향** — 사람이 건너가는 쪽. 그래서 줄무늬가 도로와 어긋나지 않는다
//   · **길이** — 건너는 거리. 곧 그 도로의 폭이다 (부산 중앙값 15.8 m, 실측)
//
// 자료에 **없어서 우리가 정한 것** (전부 그림 규격이지 실측이 아니다):
//   · 횡단보도의 **폭**(도로를 따라가는 방향의 크기) — 4 m 로 잡았다.
//     🔴 부산의 횡단보도 way 2,180 개 중 `width` 태그가 있는 것은 **0 개다** (2026-09-07 직접 셈).
//     한 개도 없으므로 이건 추정이 아니라 **우리가 고른 그림 크기**다. 실제로는 다 다르다
//   · 흰 줄 하나의 폭 0.45 m 와 줄 사이 간격 0.45 m — 도색 규격 원문을 확인하지 못했다
//
// 🔴 안 그리는 것:
//   · `crossing:markings=no` — **도색이 없다고 자료가 말한 곳**이다 (부산에 7개).
//     없는 흰 줄을 그리는 것은 창작이다
//   · `highway=crossing` **점**(부산 4,129개) — 점에는 방향이 없다. 방향을 지어내면
//     도로와 90도 어긋난 횡단보도가 도시 곳곳에 생긴다. 자료가 없으면 안 그린다
//   · 길이 60 m 를 넘는 way — 횡단보도가 아니라 다른 것이 잘못 태그된 것으로 본다
//     (부산 최대 264 m). 이건 **버리는 기준**이지 만들어 내는 값이 아니다

import fs from 'node:fs';
import path from 'node:path';

const HERE = import.meta.dirname;
const WORK = path.join(HERE, 'work');
const CACHE = path.join(WORK, '_osm-crossings.json');
const OUT = path.join(HERE, 'public/crossings.geojson');

const BBOX = { s: 34.95, w: 128.80, n: 35.35, e: 129.30 };

const CROSS_WIDTH_M = 4.0;    // 도로를 따라가는 방향의 크기. 자료에 없다 (위 설명)
const BAR_M = 0.45;           // 흰 줄 하나의 폭
const GAP_M = 0.45;           // 줄 사이
const MAX_LEN_M = 60;         // 이보다 길면 횡단보도로 안 본다

const R_EARTH = 6378137;
const M_PER_LAT = (Math.PI / 180) * R_EARTH;
const mPerLon = (lat) => (Math.PI / 180) * R_EARTH * Math.cos((lat * Math.PI) / 180);

async function fetchOsm() {
  if (fs.existsSync(CACHE)) {
    console.log('받아 둔 것을 씁니다 (다시 받으려면 work/_osm-crossings.json 을 지우세요)');
    return JSON.parse(fs.readFileSync(CACHE, 'utf8'));
  }
  const q = `[out:json][timeout:180];
(
  way["footway"="crossing"](${BBOX.s},${BBOX.w},${BBOX.n},${BBOX.e});
  way["highway"="crossing"](${BBOX.s},${BBOX.w},${BBOX.n},${BBOX.e});
);
out geom;`;
  const MIRRORS = [
    'https://overpass-api.de/api/interpreter',
    'https://overpass.kumi.systems/api/interpreter',
    'https://overpass.osm.jp/api/interpreter',
  ];
  console.log('OpenStreetMap 에서 횡단보도를 받는 중…');
  const fails = [];
  for (const url of MIRRORS) {
    try {
      const res = await fetch(url, {
        method: 'POST',
        body: 'data=' + encodeURIComponent(q),
        headers: {
          'Content-Type': 'application/x-www-form-urlencoded',
          'User-Agent': 'busan-city3d-spike/1.0',
          Accept: 'application/json',
        },
      });
      if (!res.ok) { fails.push(`${new URL(url).host} → ${res.status}`); continue; }
      const json = await res.json();
      fs.mkdirSync(WORK, { recursive: true });
      fs.writeFileSync(CACHE, JSON.stringify(json), 'utf8');
      console.log(`  ${new URL(url).host} 에서 받았습니다 — ${json.elements.length.toLocaleString()} 개`);
      return json;
    } catch (e) {
      fails.push(`${new URL(url).host} → ${e.message}`);
    }
  }
  throw new Error('모든 서버가 실패했습니다 — ' + fails.join(' · '));
}

// 한 횡단보도를 흰 줄 여러 개로 바꾼다.
//
// 🔴 방향을 헷갈리기 쉬운 자리라 그림으로 적어 둔다.
//    OSM way 는 **사람이 건너가는 선**이다. 도로를 가로지른다.
//    흰 줄은 그 선과 **나란히** 길고, 도로를 따라가며 **되풀이된다** —
//    차가 지나가면서 여러 줄을 밟고, 사람은 한 줄 위를 걷는다.
//    그래서 줄의 길이 = way 의 길이(도로 폭)이고, 줄이 늘어서는 방향 = way 에 수직이다.
//
//        way (사람이 가는 쪽)
//              ↑
//        ┃ ┃ ┃ ┃ ┃      ← 흰 줄. way 와 나란하고 옆으로 되풀이된다
//        ━━━━━━━━━━  도로
function barsFor(geom) {
  const a = geom[0];
  const b = geom[geom.length - 1];
  const mLon = mPerLon(a.lat);
  const dx = (b.lon - a.lon) * mLon;
  const dy = (b.lat - a.lat) * M_PER_LAT;
  const len = Math.hypot(dx, dy);
  if (len < 2 || len > MAX_LEN_M) return null;

  const ux = dx / len, uy = dy / len;          // 건너가는 쪽 (단위 벡터, m 기준)
  const px = -uy, py = ux;                     // 그것에 수직 = 도로를 따라가는 쪽

  // 4 m 안에 0.45 m 줄을 0.45 m 간격으로. 가운데를 0 으로 두고 양쪽 대칭이라 홀수 개가 된다.
  const period = BAR_M + GAP_M;
  const half = Math.round((CROSS_WIDTH_M / 2 - BAR_M / 2) / period);
  const cx = (a.lon + b.lon) / 2, cy = (a.lat + b.lat) / 2;

  const polys = [];
  for (let k = -half; k <= half; k++) {
    const ring = [];
    // 줄 하나의 네 귀퉁이. m 로 계산하고 마지막에 위경도로 되돌린다.
    for (const [s, w] of [[-1, -1], [1, -1], [1, 1], [-1, 1]]) {
      const ex = ux * (s * len / 2) + px * (k * period + w * BAR_M / 2);
      const ey = uy * (s * len / 2) + py * (k * period + w * BAR_M / 2);
      ring.push([+(cx + ex / mLon).toFixed(7), +(cy + ey / M_PER_LAT).toFixed(7)]);
    }
    ring.push(ring[0]);
    polys.push([ring]);
  }
  return polys;
}

const json = await fetchOsm();
const ways = json.elements.filter((e) => e.type === 'way' && e.geometry?.length >= 2);

const features = [];
const skipped = { markingsNo: 0, tooLong: 0, tooShort: 0 };
for (const w of ways) {
  if (w.tags?.['crossing:markings'] === 'no') { skipped.markingsNo++; continue; }
  const polys = barsFor(w.geometry);
  if (!polys) {
    const a = w.geometry[0], b = w.geometry[w.geometry.length - 1];
    const len = Math.hypot((b.lon - a.lon) * mPerLon(a.lat), (b.lat - a.lat) * M_PER_LAT);
    if (len > MAX_LEN_M) skipped.tooLong++; else skipped.tooShort++;
    continue;
  }
  // 줄 여러 개를 도형 하나로 묶는다. 따로 두면 파일이 두 배가 된다 (같은 껍데기를 매번 다시 쓴다).
  features.push({ type: 'Feature', properties: {}, geometry: { type: 'MultiPolygon', coordinates: polys } });
}

fs.writeFileSync(OUT, JSON.stringify({ type: 'FeatureCollection', features }), 'utf8');
const kb = (fs.statSync(OUT).size / 1024).toFixed(0);

console.log(`\n횡단보도 ${features.length.toLocaleString()} 곳 → public/crossings.geojson (${kb} KB)`);
console.log(`  줄무늬 도형 ${features.reduce((s, f) => s + f.geometry.coordinates.length, 0).toLocaleString()} 개`);
console.log(`  안 그린 것: 도색 없음(crossing:markings=no) ${skipped.markingsNo} · 60 m 초과 ${skipped.tooLong} · 2 m 미만 ${skipped.tooShort}`);
console.log(`  🔴 폭 ${CROSS_WIDTH_M} m · 줄 ${BAR_M} m · 간격 ${GAP_M} m 는 자료에 없다. 우리가 고른 그림 크기다`);
