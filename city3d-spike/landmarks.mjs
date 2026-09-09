// 랜드마크를 실사에 가깝게 — 층단 있는 다층 상자와 색 분리 (S15P21E201-685)
//
// 왜 상자로 보이나 — 높이는 이미 정확하다 (두산위브더제니스 300/284/268 m, 해운대아이파크 293/273/206 m,
// BIFC 289 m·63층, 파크하얏트 170 m). 가짜로 보이는 이유는 **지붕이 평평하고 층단이 없어서**다.
// 부산 전역 OSM 에 지붕 모양(roof:shape)이 167개, 건물 층단(building:part)이 209개뿐이고 마린시티
// 102동은 둘 다 0개다 (2026-09-07 조사). 브이월드 3D 는 공개제한으로 막혔고 Sketchfab 에도 없다.
//
// 그래서 이렇게 한다:
//   1) 건물 타일에서 그 건물의 발자국(바닥 다각형)을 꺼낸다. 이름은 없으니 **높이와 바닥 면적**으로 찾는다 —
//      타일에 든 속성이 h(높이)·a(면적)·use(용도)·lv(층수)뿐이라서다. 그 둘의 조합은 부산 안에서 사실상 유일하다
//   2) 발자국을 가운데로 오므린 다각형을 여러 층 쌓는다. 위로 갈수록 좁아지는 층단이 된다.
//      화면은 fill-extrusion(평면 다각형을 위로 뽑아 입체로 만드는 레이어) 그대로 쓴다. three.js 는 필요 없다
//   3) 화면 쪽(index.html)은 같은 높이·면적 조합을 가진 건물을 기본 건물 레이어에서 빼고 이 파일의 층단을 그린다.
//      그래서 **여기 목록과 index.html 의 LANDMARK_KEYS 는 같은 값이어야 한다** — 이 파일이 그 값을 같이 써 준다
//
//   node landmarks.mjs     → public/landmarks.geojson
//
// 🔴 발자국은 화면이 쓰는 것과 같은 타일에서 꺼낸다. 로컬에 구운 타일(public/tiles-busan)이 있으면 그것을,
//    없으면 AWS 에 올린 것을 받는다. 한 번 꺼낸 발자국은 work/ 에 저장해 두고 다시 안 받는다.

import fs from 'node:fs';
import path from 'node:path';
// vt-pbf(타일을 굽는 라이브러리)가 끌고 오는 것이라 package.json 에 따로 안 적었다. 타일을 읽는 쪽이다.
import { VectorTile } from '@mapbox/vector-tile';
import Pbf from 'pbf';

const HERE = import.meta.dirname;
const WORK = path.join(HERE, 'work');
const CACHE = path.join(WORK, '_landmark-footprints.json');
const OUT = path.join(HERE, 'public/landmarks.geojson');
const TILES_LOCAL = path.join(HERE, 'public/tiles-busan');
const TILES_AWS = 'https://j15e201.p.ssafy.io/city3d/tiles-busan';
// night.mjs 가 타일을 구울 때 쓴 값과 같아야 한다. 잘린 사본을 가려내는 데 쓴다
const EXTENT = 4096, BUFFER = 64;

// ── 무엇을 어떻게 세우나 ──────────────────────────────────────────
//   key    [높이 m, 바닥 면적 m²] — 타일에서 이 건물을 찾는 열쇠. 화면도 같은 열쇠로 기본 레이어에서 뺀다
//   at     대략의 위치. 열쇠가 같은 건물이 멀리 또 있어도 이 근처 것만 잡는다
//   tiers  층단. [아래 높이 비율, 위 높이 비율, 바닥을 오므리는 비율] 의 목록. 1 이면 원래 발자국 그대로
//   lm     화면에서 색을 고르는 이름
//
// 🔴 "상위 6동" 은 BIFC·마린시티에서 높은 순서 여섯이다 — 두산 3동(300·284·268), 아이파크 2동(293·273), BIFC(289).
//    아이파크 206 m 와 파크하얏트 170 m 는 층단 없이 색만 바꾼다(tiers 가 한 층). 영화의전당도 색만.
const TIER = {
  // 두산위브더제니스 — 위에서 두 번 오므라들며 왕관처럼 끝난다
  zenith: [[0, 0.80, 1], [0.80, 0.92, 0.86], [0.92, 1, 0.70]],
  // 해운대아이파크 — 돛 모양. 꼭대기가 더 가늘다
  ipark: [[0, 0.76, 1], [0.76, 0.90, 0.85], [0.90, 1, 0.62]],
  // BIFC — 꼭대기가 비스듬히 깎였다. 기울기는 못 세우니 두 번 오므린다
  bifc: [[0, 0.78, 1], [0.78, 0.92, 0.84], [0.92, 1, 0.58]],
  flat: [[0, 1, 1]],
};
const LANDMARKS = [
  { lm: 'zenith', show: '두산위브더제니스 101동', key: [299.9, 2383], at: [129.14602, 35.15684], tiers: TIER.zenith },
  { lm: 'zenith', show: '두산위브더제니스 102동', key: [283.9, 2365], at: [129.14484, 35.15723], tiers: TIER.zenith },
  { lm: 'zenith', show: '두산위브더제니스 103동', key: [267.9, 2381], at: [129.14529, 35.15609], tiers: TIER.zenith },
  { lm: 'ipark', show: '해운대아이파크 292 m 동', key: [292.7, 2138], at: [129.14209, 35.15605], tiers: TIER.ipark },
  { lm: 'ipark', show: '해운대아이파크 273 m 동', key: [273.5, 2129], at: [129.14259, 35.15708], tiers: TIER.ipark },
  { lm: 'ipark', show: '해운대아이파크 206 m 동', key: [206.3, 2132], at: [129.14242, 35.15521], tiers: TIER.flat },
  { lm: 'hyatt', show: '파크하얏트 부산', key: [169.8, 1636], at: [129.14185, 35.15656], tiers: TIER.flat },
  // 🔴 BIFC 는 건물 데이터에 **바닥 328 m² 짜리 조각**으로만 들어 있다 (63층 289 m 기록이 이 조각에 붙어 있다).
  //    그대로 세우면 바늘이 된다 — 지금 화면이 그렇다. 실제 탑은 한 변 50 m 남짓이라 발자국을 손으로 그린다:
  //    그 조각의 가운데에, 조각의 긴 변 방향으로 56×46 m 직사각형. 실측 발자국이 생기면 이 줄을 지운다
  { lm: 'bifc', show: 'BIFC (부산국제금융센터)', key: [289, 328], at: [129.06647, 35.14689], tiers: TIER.bifc,
    footprint: { rect: [56, 46] } },
  { lm: 'cinema', show: '영화의전당', key: [43.2, 17890], at: [129.12736, 35.17087], tiers: TIER.flat },
];

// 🔴 부산타워는 건물 데이터에 **없다.** 건축물이 아니라 공작물이라 건축물대장에 안 오른다.
//    용두산공원 150 m 안을 다 뒤져도 탑은 없었다 (2026-09-07 실측). 그래서 손으로 세운다.
//    좌표는 **기억값(추정)** 이다 — 용두산공원 정상 광장. 실측이 나오면 바꾼다. 높이 120 m 는 문헌값.
const BUSAN_TOWER = {
  lm: 'tower', show: '부산타워 (좌표 추정)', at: [129.0325, 35.1010],
  parts: [
    { size: 9, hb: 0, h: 100 },      // 기둥
    { size: 22, hb: 100, h: 112 },   // 전망대
    { size: 4, hb: 112, h: 120 },    // 첨탑
  ],
};

// ── 좌표 도구 ─────────────────────────────────────────────────────
const R = 6378137;
const mPerLon = (lat) => (Math.PI / 180) * R * Math.cos((lat * Math.PI) / 180);
const M_PER_LAT = (Math.PI / 180) * R;
const r6 = (p) => [+p[0].toFixed(6), +p[1].toFixed(6)];
const lonToX = (lon, z) => Math.floor(((lon + 180) / 360) * 2 ** z);
const latToY = (lat, z) => {
  const r = (lat * Math.PI) / 180;
  return Math.floor(((1 - Math.log(Math.tan(r) + 1 / Math.cos(r)) / Math.PI) / 2) * 2 ** z);
};
const distM = (a, b) => Math.hypot((b[0] - a[0]) * mPerLon(a[1]), (b[1] - a[1]) * M_PER_LAT);
const centroid = (ring) => [
  ring.reduce((s, p) => s + p[0], 0) / ring.length,
  ring.reduce((s, p) => s + p[1], 0) / ring.length,
];
// 다각형을 가운데로 s 배 오므린다. 모양은 그대로다
const shrink = (ring, c, s) => ring.map((p) => r6([c[0] + (p[0] - c[0]) * s, c[1] + (p[1] - c[1]) * s]));
// 중심 c, 긴 변 방향 u(단위 벡터, m), 크기 alongM×acrossM 인 직사각형
function rect(c, u, alongM, acrossM) {
  const kx = mPerLon(c[1]);
  const pt = (a, b) => r6([c[0] + (u[0] * a - u[1] * b) / kx, c[1] + (u[1] * a + u[0] * b) / M_PER_LAT]);
  const A = alongM / 2, B = acrossM / 2;
  return [pt(-A, -B), pt(A, -B), pt(A, B), pt(-A, B), pt(-A, -B)];
}
const square = (c, sizeM) => rect(c, [1, 0], sizeM, sizeM);

// ── 타일에서 발자국 꺼내기 ────────────────────────────────────────
const tileCache = new Map();
async function tile(z, x, y) {
  const k = `${z}/${x}/${y}`;
  if (tileCache.has(k)) return tileCache.get(k);
  let buf = null;
  const local = path.join(TILES_LOCAL, String(z), String(x), `${y}.pbf`);
  if (fs.existsSync(local)) buf = fs.readFileSync(local);
  else {
    const res = await fetch(`${TILES_AWS}/${k}.pbf`);
    if (res.ok) buf = Buffer.from(await res.arrayBuffer());
  }
  const vt = buf ? new VectorTile(new Pbf(new Uint8Array(buf))) : null;
  tileCache.set(k, vt);
  return vt;
}

// 🔴 타일 경계에 걸친 건물은 잘린 채로 들어 있다. 잘린 사본으로 층단을 만들면 반쪽 건물이 된다.
//    잘렸는지는 꼭짓점이 버퍼 경계(−64 또는 4096+64)에 닿았는지로 안다. 잘리지 않은 사본을 찾을 때까지
//    이웃 타일과 더 먼 확대 단계(16 → 12)를 차례로 본다. 단계가 낮을수록 타일이 커서 통째로 들어갈 확률이 높다.
async function footprintOf(L) {
  for (let z = 16; z >= 12; z--) {
    const x0 = lonToX(L.at[0], z), y0 = latToY(L.at[1], z);
    for (let dx = -1; dx <= 1; dx++) for (let dy = -1; dy <= 1; dy++) {
      const x = x0 + dx, y = y0 + dy;
      const vt = await tile(z, x, y);
      const layer = vt?.layers?.building;
      if (!layer) continue;
      for (let i = 0; i < layer.length; i++) {
        const f = layer.feature(i);
        if (Math.abs(f.properties.h - L.key[0]) > 0.05 || f.properties.a !== L.key[1]) continue;
        const raw = f.loadGeometry();
        const clipped = raw.some((r) => r.some((q) => q.x <= -BUFFER || q.y <= -BUFFER || q.x >= EXTENT + BUFFER || q.y >= EXTENT + BUFFER));
        if (clipped) continue;
        const g = f.toGeoJSON(x, y, z).geometry;
        const ring = g.type === 'Polygon' ? g.coordinates[0] : g.coordinates[0][0];
        if (distM(centroid(ring), L.at) > 80) continue;
        return { ring: ring.map(r6), use: f.properties.use, lv: f.properties.lv, from: `${z}/${x}/${y}` };
      }
    }
  }
  return null;
}

async function footprints() {
  if (fs.existsSync(CACHE)) {
    const c = JSON.parse(fs.readFileSync(CACHE, 'utf8'));
    if (LANDMARKS.every((L) => c[L.key.join('|')])) {
      console.log('꺼내 둔 발자국을 씁니다:', path.relative(HERE, CACHE));
      return c;
    }
  }
  const out = {};
  const src = fs.existsSync(TILES_LOCAL) ? '로컬 타일' : 'AWS 타일';
  console.log(`${src}에서 발자국을 꺼냅니다…`);
  for (const L of LANDMARKS) {
    const fp = await footprintOf(L);
    if (fp) out[L.key.join('|')] = fp;
    console.log(`  ${L.show} → ${fp ? `${fp.from} · 점 ${fp.ring.length} · ${fp.use} ${fp.lv}층` : '못 찾음'}`);
  }
  fs.mkdirSync(WORK, { recursive: true });
  fs.writeFileSync(CACHE, JSON.stringify(out), 'utf8');
  return out;
}

// ── 실행 ─────────────────────────────────────────────────────────
const fps = await footprints();
const features = [];
const report = [];

for (const L of LANDMARKS) {
  const fp = fps[L.key.join('|')];
  if (!fp) { report.push({ 건물: L.show, 상태: '발자국 못 찾음 — 안 세움' }); continue; }
  let ring = fp.ring;
  const c = centroid(ring);
  if (L.footprint?.rect) {
    // 가장 긴 변의 방향을 재서 그 방향으로 직사각형을 놓는다
    let best = [1, 0], bd = 0;
    for (let i = 1; i < ring.length; i++) {
      const d = distM(ring[i - 1], ring[i]);
      if (d > bd) { bd = d; best = [(ring[i][0] - ring[i - 1][0]) * mPerLon(c[1]), (ring[i][1] - ring[i - 1][1]) * M_PER_LAT]; }
    }
    const n = Math.hypot(best[0], best[1]) || 1;
    ring = rect(c, [best[0] / n, best[1] / n], L.footprint.rect[0], L.footprint.rect[1]);
  }
  const H = L.key[0];
  for (const [f0, f1, s] of L.tiers) {
    features.push({
      type: 'Feature',
      properties: { lm: L.lm, kind: 'tier', key: L.key.join('|'), hb: +(H * f0).toFixed(1), h: +(H * f1).toFixed(1) },
      geometry: { type: 'Polygon', coordinates: [s === 1 ? ring : shrink(ring, c, s)] },
    });
  }
  report.push({ 건물: L.show, 발자국: `${fp.from} · ${fp.use} ${fp.lv}층`, 층단: L.tiers.length, 높이: H });
}

for (const p of BUSAN_TOWER.parts) {
  features.push({
    type: 'Feature',
    properties: { lm: BUSAN_TOWER.lm, kind: 'tower', hb: p.hb, h: p.h },
    geometry: { type: 'Polygon', coordinates: [square(BUSAN_TOWER.at, p.size)] },
  });
}
report.push({ 건물: BUSAN_TOWER.show, 층단: BUSAN_TOWER.parts.length, 높이: 120 });

fs.writeFileSync(OUT, JSON.stringify({ type: 'FeatureCollection', features }), 'utf8');
const kb = +(fs.statSync(OUT).size / 1024).toFixed(1);
console.log('\n' + JSON.stringify(report, null, 2));
console.log(`\n도형 ${features.length} 개 · ${kb} KB → ${path.relative(HERE, OUT)}`);
console.log('\nindex.html 의 LANDMARK_KEYS 에 이 값이 있어야 한다:');
console.log('  ' + JSON.stringify(LANDMARKS.map((L) => L.key.join('|'))));
fs.writeFileSync(path.join(WORK, '_landmarks.json'), JSON.stringify({ at: new Date().toISOString(), features: features.length, kb, report }, null, 2), 'utf8');
