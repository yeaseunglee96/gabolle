// 부산의 다리를 3D 로 세운다.
//
// 왜 따로 만드나 — 건물 데이터(국토교통부 GIS건물통합정보)에는 다리가 한 동도 없다.
// 그건 건축물대장이고 교량은 건축물이 아니기 때문이다. 용도 33종을 세어 봐도 교량은 없다.
// 그래서 광안대교는 "덜 그려진" 것이 아니라 한 번도 그려진 적이 없었다.
//
// 재료는 OpenStreetMap 에서 한 번만 받아 온다. 부산의 다리는 자주 안 변하므로
// 받은 것을 work/ 에 저장해 두고 다시 안 받는다. 다시 받으려면 그 파일을 지운다.
//
// 🔴 다리를 찾는 순서가 중요하다 — 실측으로 확인한 것이다.
//    부산항대교·남항대교·영도대교는 **도로에 이름이 없다.** 이름은 다리 외곽선에만 있다.
//    그래서 "이름으로 도로를 찾는다" 는 안 되고, 이렇게 해야 한다:
//      외곽선에서 이름과 위치를 얻는다 → 그 위치 근처의 다리 도로를 줍는다 → 이어 붙인다
//
// 만드는 것 셋:
//   1) 상판 — 중심선을 폭만큼 부풀린 띠. 조각마다 높이가 다르다
//   2) 주탑 — OSM 에 실측이 있다. 점이 아니라 **면**으로 들어 있어서 가운데를 구해 쓴다
//   3) 주케이블 — 주탑 사이로 늘어지는 곡선. 이게 있어야 현수교로 보인다

import fs from 'node:fs';
import path from 'node:path';

const HERE = import.meta.dirname;
const WORK = path.join(HERE, 'work');
const CACHE = path.join(WORK, '_osm-bridges.json');
const OUT = path.join(HERE, 'public/bridges.geojson');

const BBOX = { s: 34.95, w: 128.80, n: 35.35, e: 129.30 };

// ── 세울 다리 ────────────────────────────────────────────────────
//   area    OSM 다리 외곽선의 이름. 여기서 위치를 얻는다
//   road    외곽선이 없는 다리는 도로 이름으로 찾는다
//   reachM  그 위치에서 몇 m 까지를 이 다리로 볼 것인가. 실제 다리 길이의 절반쯤
//   deckM   상판 폭(m)
//   cruise  바다 위를 지날 때 상판 윗면 높이(m). 배가 지나가야 해서 높다
//   thick   상판 두께(m). 광안대교는 복층이라 두껍다 — OSM 에도 layer 1·2 가 둘 다 있다
const BRIDGES = [
  { show: '광안대교', area: '광안대교', reachM: 3900, deckM: 25, cruise: 34, thick: 12 },
  { show: '부산항대교', area: '부산항대교', reachM: 1800, deckM: 22, cruise: 60, thick: 7 },
  { show: '남항대교', area: '남항대교', reachM: 1200, deckM: 20, cruise: 35, thick: 6 },
  { show: '을숙도대교', road: '을숙도대로', reachM: 2700, deckM: 22, cruise: 22, thick: 5 },
  { show: '영도대교', area: '영도대교', reachM: 220, deckM: 18, cruise: 12, thick: 4 },
  { show: '부산대교', area: '부산대교', reachM: 260, deckM: 18, cruise: 14, thick: 4 },
];

// 육지에서 순항 높이까지 올라가는 기울기. 실제 고속 교량이 3~4 % 다.
const GRADE = 0.035;
const MAIN_ROAD = /^(motorway|trunk|primary|secondary)$/;

// ── 좌표 계산 ────────────────────────────────────────────────────
const R = 6378137;
const rad = (x) => (x * Math.PI) / 180;
// 이 위도에서 경도 1도가 몇 m 인지. 부산이면 약 91 km.
const mPerLon = (lat) => (Math.PI / 180) * R * Math.cos(rad(lat));
const M_PER_LAT = (Math.PI / 180) * R;

function distM(a, b) {
  const x = (b[0] - a[0]) * mPerLon((a[1] + b[1]) / 2);
  const y = (b[1] - a[1]) * M_PER_LAT;
  return Math.hypot(x, y);
}

// 🔴 점에서 **선까지**의 거리. 꼭짓점까지가 아니다.
//    OSM 은 곧게 뻗은 구간에 꼭짓점을 수백 m 마다만 찍는다. 광안대교 주경간이 그렇다.
//    꼭짓점으로만 재면 선이 주탑을 관통하는데도 424 m 떨어졌다고 나온다 — 실제로 그랬다.
function distToSegM(p, a, b) {
  const kx = mPerLon(p[1]);
  const px = (p[0] - a[0]) * kx, py = (p[1] - a[1]) * M_PER_LAT;
  const bx = (b[0] - a[0]) * kx, by = (b[1] - a[1]) * M_PER_LAT;
  const L2 = bx * bx + by * by;
  const t = L2 ? Math.max(0, Math.min(1, (px * bx + py * by) / L2)) : 0;
  return Math.hypot(px - bx * t, py - by * t);
}
function distToLineM(line, p) {
  if (line.length < 2) return distM(line[0], p);
  let best = Infinity;
  for (let i = 1; i < line.length; i++) best = Math.min(best, distToSegM(p, line[i - 1], line[i]));
  return best;
}

const centroid = (pts) => {
  const lon = pts.reduce((s, p) => s + p[0], 0) / pts.length;
  const lat = pts.reduce((s, p) => s + p[1], 0) / pts.length;
  return [lon, lat];
};
const asXY = (el) => el.geometry.map((p) => [p.lon, p.lat]);

// ── OSM 에서 받기 (한 번만) ───────────────────────────────────────
async function fetchOsm() {
  if (fs.existsSync(CACHE)) {
    console.log('받아 둔 것을 씁니다:', path.relative(HERE, CACHE));
    return JSON.parse(fs.readFileSync(CACHE, 'utf8'));
  }
  const box = `${BBOX.s},${BBOX.w},${BBOX.n},${BBOX.e}`;
  // 셋을 한 번에 받는다: 다리 도로 · 다리 외곽선 · 교각과 주탑(점·면 둘 다)
  const q = `[out:json][timeout:180];
    ( way["bridge"]["highway"](${box});
      way["man_made"="bridge"](${box});
      node["bridge:support"](${box});
      way["bridge:support"](${box}); );
    out geom;`;
  // 공개 서버라 자주 바쁘다 (504). 같은 데이터를 주는 거울 서버를 차례로 시도한다.
  // 한 번만 받으면 되는 일이라 여기서 포기하면 처음부터 다시 해야 한다.
  const MIRRORS = [
    'https://overpass-api.de/api/interpreter',
    'https://overpass.kumi.systems/api/interpreter',
    'https://overpass.osm.jp/api/interpreter',
  ];
  console.log('OpenStreetMap 에서 다리를 받는 중…');
  let json = null;
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
      json = await res.json();
      console.log(`  ${new URL(url).host} 에서 받았습니다`);
      break;
    } catch (e) {
      fails.push(`${new URL(url).host} → ${e.message}`);
    }
  }
  if (!json) throw new Error('모든 서버가 실패했습니다 — ' + fails.join(' · '));
  fs.mkdirSync(WORK, { recursive: true });
  fs.writeFileSync(CACHE, JSON.stringify(json), 'utf8');
  console.log(`받았습니다 — ${json.elements.length.toLocaleString()} 개`);
  return json;
}

// ── 토막난 길을 이어 붙인다 ──────────────────────────────────────
// OSM 은 하나의 다리를 여러 조각으로 쪼개 둔다. 끝점이 맞는 것끼리 이어야
// 다리 전체 길이를 알 수 있고, 그래야 "양 끝에서 올라간다" 는 높이 규칙이 성립한다.
function stitch(lines) {
  const key = (p) => p[0].toFixed(6) + ',' + p[1].toFixed(6);
  const pool = lines.map((l) => l.slice());
  const out = [];

  while (pool.length) {
    let cur = pool.pop();
    let grew = true;
    while (grew) {
      grew = false;
      for (let i = 0; i < pool.length; i++) {
        const c = pool[i];
        const head = key(cur[0]);
        const tail = key(cur[cur.length - 1]);
        const cHead = key(c[0]);
        const cTail = key(c[c.length - 1]);
        if (tail === cHead) cur = cur.concat(c.slice(1));
        else if (tail === cTail) cur = cur.concat(c.slice(0, -1).reverse());
        else if (head === cTail) cur = c.slice(0, -1).concat(cur);
        else if (head === cHead) cur = c.slice(1).reverse().concat(cur);
        else continue;
        pool.splice(i, 1);
        grew = true;
        break;
      }
    }
    out.push(cur);
  }
  return out;
}

// 이어 붙인 선에서 "이 다리인 부분" 만 남긴다.
// 기준점에 가장 가까운 점을 찾고, 거기서 양쪽으로 reachM 안에 있는 동안만 따라간다.
// 이렇게 해야 다리가 육지에서 시내 도로로 계속 이어져 나가지 않는다.
function trimAround(line, anchor, reachM) {
  let best = 0;
  let bestD = Infinity;
  for (let i = 1; i < line.length; i++) {
    const d = distToSegM(anchor, line[i - 1], line[i]);
    if (d < bestD) { bestD = d; best = i - 1; }
  }
  let lo = best;
  while (lo > 0 && distM(line[lo - 1], anchor) <= reachM) lo--;
  let hi = best;
  while (hi < line.length - 1 && distM(line[hi + 1], anchor) <= reachM) hi++;
  return { part: line.slice(lo, hi + 1), nearestM: Math.round(bestD) };
}

// 선 위에서 시작점으로부터 s 미터 떨어진 점
function pointAt(line, cum, s) {
  let i = 1;
  while (i < cum.length - 1 && cum[i] < s) i++;
  const t = (s - cum[i - 1]) / Math.max(cum[i] - cum[i - 1], 1e-9);
  return [
    line[i - 1][0] + (line[i][0] - line[i - 1][0]) * t,
    line[i - 1][1] + (line[i][1] - line[i - 1][1]) * t,
  ];
}

const lengthM = (line) => {
  let s = 0;
  for (let i = 1; i < line.length; i++) s += distM(line[i - 1], line[i]);
  return s;
};

// 한 변이 sizeM 인 정사각형
function square(center, sizeM) {
  const h = sizeM / 2;
  const dx = h / mPerLon(center[1]);
  const dy = h / M_PER_LAT;
  return [[
    [center[0] - dx, center[1] - dy], [center[0] + dx, center[1] - dy],
    [center[0] + dx, center[1] + dy], [center[0] - dx, center[1] + dy],
    [center[0] - dx, center[1] - dy],
  ]];
}

// ── 중심선을 폭만큼 부풀려 조각 상판으로 ──────────────────────────
// 🔴 조각으로 자르는 이유: 화면을 그리는 방식이 도형 하나에 높이 하나만 받는다.
//    그래서 80 m 마다 잘라 조각마다 높이를 준다. 잘라도 수백 개라 비용은 없다시피 하다.
//    곡선 구간에서 조각 사이에 아주 작은 틈이 생기는데, 폭 25 m 에 길이 80 m 면 안 보인다.
function deckChunks(line, cfg) {
  const cum = [0];
  for (let i = 1; i < line.length; i++) cum.push(cum[i - 1] + distM(line[i - 1], line[i]));
  const total = cum[cum.length - 1];
  if (total < 60) return null;

  // 양 끝에서 기울기만큼 올라가다 순항 높이에서 멈춘다. 실제 다리가 이렇게 생겼다.
  const topAt = (s) => Math.min(cfg.cruise, 2 + Math.min(s, total - s) * GRADE);

  const CHUNK = 80;
  const feats = [];
  const halfW = cfg.deckM / 2;

  for (let s = 0; s < total; s += CHUNK) {
    const e = Math.min(s + CHUNK, total);
    if (e - s < 8) break;
    const a = pointAt(line, cum, s);
    const b = pointAt(line, cum, e);

    // 진행 방향에 수직인 쪽으로 폭의 절반씩 벌린다.
    const lat = (a[1] + b[1]) / 2;
    const dx = (b[0] - a[0]) * mPerLon(lat);
    const dy = (b[1] - a[1]) * M_PER_LAT;
    const L = Math.hypot(dx, dy) || 1;
    const nx = (-dy / L) * halfW;
    const ny = (dx / L) * halfW;
    const off = (p, sx, sy) => [p[0] + sx / mPerLon(lat), p[1] + sy / M_PER_LAT];

    const top = Math.max(topAt((s + e) / 2), 3);
    feats.push({
      type: 'Feature',
      properties: { kind: 'deck', name: cfg.show, hb: +Math.max(top - cfg.thick, 0.5).toFixed(1), h: +top.toFixed(1) },
      geometry: {
        type: 'Polygon',
        coordinates: [[off(a, nx, ny), off(b, nx, ny), off(b, -nx, -ny), off(a, -nx, -ny), off(a, nx, ny)]],
      },
    });
  }
  return { feats, total, cum };
}

// ── 주케이블 ─────────────────────────────────────────────────────
// 주탑 두 개 사이로 늘어지는 곡선. 실제 모양은 현수선인데 포물선과 눈으로
// 구별이 안 되므로 포물선으로 놓는다.
//
// 그리는 방법: 화면을 그리는 방식이 기울어진 막대를 못 세운다. 세로 기둥만 된다.
// 그래서 짧은 토막을 계단처럼 이어 붙인다. 멀리서 보면 곡선으로 읽힌다.
// 🔴 케이블은 두 주탑을 **직선으로 잇지 않는다.** 상판 경로를 따라간다.
//    직선으로 이었더니 다리가 휘는 구간에서 케이블만 옆으로 빠져나갔다 — 실제로 그랬다.
function cables(line, cum, sA, sB, towerTop, deckTop, cfg) {
  const N = 64;   // 500 m 를 64 조각 → 약 8 m 간격. 26 조각이면 점선으로 끊겨 보였다
  const feats = [];
  const sag = deckTop + 6; // 가운데가 가장 낮게 내려오는 높이
  const half = cfg.deckM / 2 - 2;

  for (let i = 0; i <= N; i++) {
    const t = i / N;
    const s = sA + (sB - sA) * t;
    const p = pointAt(line, cum, s);
    // 그 지점에서 다리가 향하는 쪽 — 앞뒤로 조금 떨어진 두 점으로 잰다
    const step = Math.abs(sB - sA) / N / 2 + 1;
    const a = pointAt(line, cum, Math.max(0, s - step));
    const b = pointAt(line, cum, Math.min(cum[cum.length - 1], s + step));
    const dx = (b[0] - a[0]) * mPerLon(p[1]);
    const dy = (b[1] - a[1]) * M_PER_LAT;
    const L = Math.hypot(dx, dy) || 1;

    const y = towerTop + (sag - towerTop) * (1 - (2 * t - 1) ** 2);
    for (const side of [-1, 1]) {
      const c = [
        p[0] + ((-dy / L) * half * side) / mPerLon(p[1]),
        p[1] + ((dx / L) * half * side) / M_PER_LAT,
      ];
      feats.push({
        type: 'Feature',
        properties: { kind: 'cable', name: cfg.show, hb: +(y - 2.5).toFixed(1), h: +y.toFixed(1) },
        geometry: { type: 'Polygon', coordinates: square(c, 5) },
      });
    }
  }
  return feats;
}

// 점이 선의 어디쯤(시작점에서 몇 m)에 있는지
function arcLengthAt(line, cum, p) {
  let bestS = 0;
  let bestD = Infinity;
  for (let i = 1; i < line.length; i++) {
    const d = distToSegM(p, line[i - 1], line[i]);
    if (d < bestD) {
      bestD = d;
      // 그 선분 위에서 얼마나 진행했는지
      const kx = mPerLon(p[1]);
      const px = (p[0] - line[i - 1][0]) * kx, py = (p[1] - line[i - 1][1]) * M_PER_LAT;
      const bx = (line[i][0] - line[i - 1][0]) * kx, by = (line[i][1] - line[i - 1][1]) * M_PER_LAT;
      const L2 = bx * bx + by * by;
      const t = L2 ? Math.max(0, Math.min(1, (px * bx + py * by) / L2)) : 0;
      bestS = cum[i - 1] + (cum[i] - cum[i - 1]) * t;
    }
  }
  return bestS;
}

// ── 실행 ─────────────────────────────────────────────────────────
const osm = await fetchOsm();

const roads = osm.elements.filter(
  (e) => e.type === 'way' && e.geometry && e.tags?.bridge && MAIN_ROAD.test(e.tags.highway ?? '')
);
const areas = osm.elements.filter((e) => e.type === 'way' && e.geometry && e.tags?.man_made === 'bridge');

// 주탑 — 점으로도 면으로도 들어 있다. 면이면 가운데를 쓴다.
const pylons = osm.elements
  .filter((e) => e.tags?.['bridge:support'] === 'pylon' || (e.tags?.man_made === 'tower' && e.tags?.['tower:type'] === 'bridge'))
  .map((e) => ({
    at: e.type === 'node' ? [e.lon, e.lat] : centroid(asXY(e)),
    h: +(e.tags.height ?? 90),
  }));

console.log(`다리 도로 ${roads.length} · 외곽선 ${areas.length} · 주탑 ${pylons.length}`);
console.log(pylons.map((p) => `  주탑 ${p.at[0].toFixed(5)},${p.at[1].toFixed(5)} 높이 ${p.h} m`).join('\n'));

const features = [];
const report = [];

for (const cfg of BRIDGES) {
  const row = { 다리: cfg.show };
  report.push(row);

  // 1) 기준점 — 외곽선이 있으면 거기서, 없으면 도로 이름에서
  let anchor = null;
  const area = cfg.area && areas.find((a) => a.tags.name === cfg.area);
  if (area) {
    anchor = centroid(asXY(area));
    row.기준점 = `외곽선 '${cfg.area}'`;
  } else if (cfg.road) {
    const named = roads.filter((w) => w.tags.name === cfg.road);
    if (named.length) {
      anchor = centroid(named.flatMap(asXY));
      row.기준점 = `도로 이름 '${cfg.road}' ${named.length} 조각`;
    }
  }
  if (!anchor) { row.상태 = 'OSM 에서 못 찾음'; continue; }

  // 2) 그 근처의 다리 도로를 줍는다
  const near = roads.filter((w) => w.geometry.some((p) => distM([p.lon, p.lat], anchor) <= cfg.reachM));
  if (!near.length) { row.상태 = '근처에 다리 도로 없음'; continue; }

  // 3) 층별로 나눠서 이어 붙인다.
  //    층을 섞어 이으면 복층 다리의 위·아래가 한 줄로 붙어 버린다.
  const byLayer = new Map();
  for (const w of near) {
    const L = +(w.tags.layer ?? 0);
    if (!byLayer.has(L)) byLayer.set(L, []);
    byLayer.get(L).push(w);
  }

  // 4) 🔴 기준점에 **가장 가까운 줄**을 고른다. 가장 긴 줄이 아니다.
  //    길이로 고르면 근처의 다른 고가도로가 이긴다 — 실제로 광안대교가 그렇게 424 m 빗나갔다.
  const cands = [];
  for (const [L, ws] of byLayer) {
    for (const l of stitch(ws.map(asXY))) {
      cands.push({ L, l, d: distToLineM(l, anchor) });
    }
  }
  cands.sort((a, b) => a.d - b.d || lengthM(b.l) - lengthM(a.l));
  const pick = cands[0];
  row.층 = byLayer.size > 1 ? `${[...byLayer.keys()].sort().join('·')}층 중 ${pick.L}층` : '단층';

  const { part, nearestM } = trimAround(pick.l, anchor, cfg.reachM);

  const built = deckChunks(part, cfg);
  if (!built) { row.상태 = `너무 짧음 (${Math.round(lengthM(part))} m)`; continue; }
  features.push(...built.feats);

  row.후보줄 = cands.length;
  row.기준점까지 = nearestM + ' m';
  row.길이 = Math.round(built.total) + ' m';
  row.상판조각 = built.feats.length;

  // 5) 이 다리에 속한 주탑 — 중심선에서 150 m 안
  const mine = pylons.filter((p) => distToLineM(part, p.at) < 60);
  for (const p of mine) {
    features.push({
      type: 'Feature',
      properties: { kind: 'pylon', name: cfg.show, hb: 0, h: p.h },
      geometry: { type: 'Polygon', coordinates: square(p.at, 15) },
    });
  }
  if (mine.length === 2) {
    const sA = arcLengthAt(part, built.cum, mine[0].at);
    const sB = arcLengthAt(part, built.cum, mine[1].at);
    features.push(...cables(part, built.cum, sA, sB, mine[0].h, cfg.cruise, cfg));
    row.주탑 = `2개 · 높이 ${mine[0].h} m · 간격 ${Math.round(distM(mine[0].at, mine[1].at))} m`;
    row.주케이블 = '있음';
  } else if (mine.length) {
    row.주탑 = `${mine.length}개`;
  }
}

fs.writeFileSync(OUT, JSON.stringify({ type: 'FeatureCollection', features }), 'utf8');
const kb = +(fs.statSync(OUT).size / 1024).toFixed(1);

console.log('\n' + JSON.stringify(report, null, 2));
console.log(`\n도형 ${features.length} 개 · ${kb} KB → ${path.relative(HERE, OUT)}`);
fs.writeFileSync(
  path.join(WORK, '_bridges.json'),
  JSON.stringify({ at: new Date().toISOString(), features: features.length, kb, report }, null, 2),
  'utf8'
);
