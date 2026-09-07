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
// 만드는 것 넷:
//   1) 상판 — 중심선을 폭만큼 부풀린 띠. 조각마다 높이가 다르다
//   2) 주탑 — 광안대교는 OSM 에 실측이 있다. 점이 아니라 **면**으로 들어 있어서 가운데를 구해 쓴다.
//      🔴 부산항대교·남항대교의 주탑은 OSM 에 **없다.** 부산 전역에 주탑 요소가 2개뿐이고 둘 다
//      광안대교다 (2026-09-07 실측). 그래서 이 둘은 아래 설정에 문헌값을 손으로 박는다.
//   3) 케이블 — 다리 종류에 따라 모양이 다르다.
//      현수교(광안대교): 주탑 사이로 **늘어지는 곡선** 하나.
//      사장교(부산항대교·남항대교): 주탑 꼭대기에서 상판으로 **곧게 뻗는 다발.**
//      같은 곡선을 쓰면 틀린 그림이 된다. 갈라서 그린다.
//   4) 고가도로 — OSM 에 bridge=yes 인 도로 전부. 이름난 다리 여섯 말고 나머지 수천 개다.
//      전에는 평면 지도 위의 선으로만 있었다. 상판 함수(deckChunks)를 그대로 써서 세운다.

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
//   type    'suspension'(현수교) 또는 'cable-stayed'(사장교). 케이블 모양이 갈린다
//   pylons  OSM 에 주탑이 없는 다리에 손으로 넣는 주탑.
//             h     주탑 꼭대기 높이(m, 해수면 기준)
//             span  주경간(m) — 두 주탑 사이 거리. 외곽선 가운데에서 양쪽으로 span/2 에 세운다
//
// 🔴 남항대교 주탑 높이는 **확인된 문헌값이 없다** (2026-09-07 조사). 아래 105 m 는 **추정**이다.
//    근거: 상판 40 m + 주경간의 1/5 (사장교 주탑은 대개 상판 위 높이가 주경간의 1/5 안팎이다).
//    주경간 320 m 도 기억에 의존한 값이라 미확인이다. 부산항대교의 실측 비율(190−63 = 127 m,
//    127/540 = 0.235)을 그대로 쓰면 40 + 320×0.235 ≈ 115 m 가 나온다. 실측이 나오면 이 숫자를 바꾼다.
const BRIDGES = [
  { show: '광안대교', area: '광안대교', reachM: 3900, deckM: 25, cruise: 34, thick: 12, type: 'suspension' },
  // 부산항대교 — 문헌값: 주탑 190 m · 상판 63 m · 주경간 540 m · 전체 3,331 m · 다이아몬드형 주탑 사장교.
  // OSM 외곽선(1,120 m)이 사장교 구간(1,114 m)과 맞아서 외곽선 가운데 = 주경간 가운데로 본다.
  { show: '부산항대교', area: '부산항대교', reachM: 1800, deckM: 22, cruise: 63, thick: 7, type: 'cable-stayed',
    pylons: { h: 190, span: 540 } },
  // 남항대교 — 문헌값: 상판 40 m · 전체 1,941 m · 사장교. 주탑 높이는 위 설명대로 **추정 105 m**.
  { show: '남항대교', area: '남항대교', reachM: 1200, deckM: 20, cruise: 40, thick: 6, type: 'cable-stayed',
    pylons: { h: 105, span: 320, estimated: true } },
  { show: '을숙도대교', road: '을숙도대로', reachM: 2700, deckM: 22, cruise: 22, thick: 5 },
  { show: '영도대교', area: '영도대교', reachM: 220, deckM: 18, cruise: 12, thick: 4 },
  { show: '부산대교', area: '부산대교', reachM: 260, deckM: 18, cruise: 14, thick: 4 },
];

// 육지에서 순항 높이까지 올라가는 기울기. 실제 고속 교량이 3~4 % 다.
const GRADE = 0.035;
const MAIN_ROAD = /^(motorway|trunk|primary|secondary)$/;

// ── 고가도로 (4) — 어떤 도로를 세우고 얼마나 높이·넓게 세우나 ─────────
// 차가 다니는 길만 세운다. 보행교(footway·steps·path 등 685개)는 이번엔 뺐다 — 개수가 곧 무게다.
const VIADUCT_ROAD = /^(motorway|trunk|primary|secondary|tertiary|unclassified|residential|living_street|service)(_link)?$/;
// 이보다 짧은 다리는 안 세운다. 수 m 짜리 배수로 위 다리가 수백 개인데 화면에서 안 보인다.
const VIADUCT_MIN_M = 30;
// 조각 길이. 이름난 다리는 80 m 인데 고가도로는 높이가 일정하니 더 길게 잘라도 된다. 개수가 절반이 된다.
const VIADUCT_CHUNK_M = 120;
// 상판 윗면 높이 = 층(layer) × 7 m. 고가 한 층은 밑으로 차가 지나야 해서 4.5 m 틈 + 상판 두께다.
// layer 가 없으면 1층으로 본다. 🔴 이 높이는 **땅에서 잰 것**이다 — 화면이 조각마다 그 자리 땅 높이 위에 올린다.
const VIADUCT_TOP_PER_LAYER = 7;
const VIADUCT_THICK = 1.6;
// 폭. lanes 가 있으면 차로 × 3.5 m, 없으면 도로 등급으로 짐작한다
const VIADUCT_WIDTH = { motorway: 12, trunk: 12, primary: 12, secondary: 10, tertiary: 8 };
const VIADUCT_WIDTH_DEFAULT = 6;
// 이름난 다리와 겹치는 도로는 뺀다. 꼭짓점의 절반 이상이 그 다리 상판에서 이 거리 안이면 같은 다리다.
const NAMED_OVERLAP_M = 20;

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
// 파일 크기를 줄인다. 소수 6자리면 약 10 cm 다 — 다리에 그 이상은 필요 없다.
const r6 = (p) => [+p[0].toFixed(6), +p[1].toFixed(6)];

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

// 선 위 s 미터 지점에서 다리가 향하는 쪽(단위 벡터, m 단위). 앞뒤로 조금 떨어진 두 점으로 잰다
function headingAt(line, cum, s, stepM = 4) {
  const total = cum[cum.length - 1];
  const a = pointAt(line, cum, Math.max(0, s - stepM));
  const b = pointAt(line, cum, Math.min(total, s + stepM));
  const lat = (a[1] + b[1]) / 2;
  const dx = (b[0] - a[0]) * mPerLon(lat);
  const dy = (b[1] - a[1]) * M_PER_LAT;
  const L = Math.hypot(dx, dy) || 1;
  return [dx / L, dy / L];
}

// 선 위 s 미터 지점에서 옆으로 lateralM 만큼(왼쪽 +, 오른쪽 −) 비킨 점.
// 🔴 케이블과 주탑은 전부 이걸로 자리를 잡는다 — 그래야 다리가 휘어도 상판을 따라간다.
function offsetAt(line, cum, s, lateralM) {
  const p = pointAt(line, cum, s);
  const [ux, uy] = headingAt(line, cum, s);
  return [p[0] + ((-uy * lateralM) / mPerLon(p[1])), p[1] + ((ux * lateralM) / M_PER_LAT)];
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
  ].map(r6)];
}

// 중심 c 에서 다리 방향(u)으로 alongM, 그 수직으로 acrossM 인 직사각형. 주탑 다리와 가로보에 쓴다
function box(c, u, alongM, acrossM) {
  const kx = mPerLon(c[1]);
  const pt = (a, b) => r6([c[0] + (u[0] * a - u[1] * b) / kx, c[1] + (u[1] * a + u[0] * b) / M_PER_LAT]);
  const A = alongM / 2, B = acrossM / 2;
  return [[pt(-A, -B), pt(A, -B), pt(A, B), pt(-A, B), pt(-A, -B)]];
}

// 두 점 a→b 를 잇는 폭 widthM 의 띠. 케이블 토막 하나가 이것이다
function ribbon(a, b, widthM) {
  const lat = (a[1] + b[1]) / 2;
  const dx = (b[0] - a[0]) * mPerLon(lat);
  const dy = (b[1] - a[1]) * M_PER_LAT;
  const L = Math.hypot(dx, dy) || 1;
  const nx = (-dy / L) * (widthM / 2), ny = (dx / L) * (widthM / 2);
  const off = (p, sx, sy) => r6([p[0] + sx / mPerLon(lat), p[1] + sy / M_PER_LAT]);
  return [[off(a, nx, ny), off(b, nx, ny), off(b, -nx, -ny), off(a, -nx, -ny), off(a, nx, ny)]];
}

// ── 중심선을 폭만큼 부풀려 조각 상판으로 ──────────────────────────
// 🔴 조각으로 자르는 이유: 화면을 그리는 방식이 도형 하나에 높이 하나만 받는다.
//    그래서 80 m 마다 잘라 조각마다 높이를 준다. 잘라도 수백 개라 비용은 없다시피 하다.
//    곡선 구간에서 조각 사이에 아주 작은 틈이 생기는데, 폭 25 m 에 길이 80 m 면 안 보인다.
//
// 옵션 (고가도로가 쓴다):
//   flat   true 면 양 끝에서 올라가지 않고 처음부터 cruise 높이다. OSM 의 bridge 구간은 이미
//          떠 있는 부분만이라(흙 쌓은 접속부는 다리가 아니다) 올라가는 구간을 넣으면 오히려 틀린다
//   chunk  조각 길이(m). 기본 80
//   minM   이보다 짧으면 안 만든다. 기본 60
//   kind   도형에 적는 종류. 기본 'deck'
function deckChunks(line, cfg, opt = {}) {
  const cum = [0];
  for (let i = 1; i < line.length; i++) cum.push(cum[i - 1] + distM(line[i - 1], line[i]));
  const total = cum[cum.length - 1];
  if (total < (opt.minM ?? 60)) return null;

  // 양 끝에서 기울기만큼 올라가다 순항 높이에서 멈춘다. 실제 다리가 이렇게 생겼다.
  const topAt = opt.flat
    ? () => cfg.cruise
    : (s) => Math.min(cfg.cruise, 2 + Math.min(s, total - s) * GRADE);

  const CHUNK = opt.chunk ?? 80;
  const feats = [];
  const halfW = cfg.deckM / 2;
  const props = { kind: opt.kind ?? 'deck' };
  if (cfg.show) props.name = cfg.show;

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
    const off = (p, sx, sy) => r6([p[0] + sx / mPerLon(lat), p[1] + sy / M_PER_LAT]);

    const top = Math.max(topAt((s + e) / 2), 3);
    feats.push({
      type: 'Feature',
      properties: { ...props, hb: +Math.max(top - cfg.thick, 0.5).toFixed(1), h: +top.toFixed(1) },
      geometry: {
        type: 'Polygon',
        coordinates: [[off(a, nx, ny), off(b, nx, ny), off(b, -nx, -ny), off(a, -nx, -ny), off(a, nx, ny)]],
      },
    });
  }
  return { feats, total, cum, topAt };
}

// ── 주케이블 (현수교) ────────────────────────────────────────────
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

// ── 주탑 (사장교, 손으로 넣는 것) ────────────────────────────────
// 다이아몬드형 주탑을 상자 여섯 개로 근사한다. 기울어진 면은 못 세우니 층으로 나눈다:
//   아래  상판 양옆 바깥에 다리 둘 (0 → 상판 조금 위)
//   가운데 상판 밑을 받치는 가로보 하나
//   위   상판 위에서 하나로 모이는 기둥 하나, 꼭대기는 더 가늘게
// 멀리서 보면 다리가 상판을 감싸고 위로 모이는 실루엣이 나온다. 그게 사장교 주탑의 특징이다.
function pylonBoxes(line, cum, sP, towerTop, deckTop, cfg) {
  const u = headingAt(line, cum, sP);
  const c = pointAt(line, cum, sP);
  const legOut = cfg.deckM / 2 + 4;           // 상판 가장자리에서 4 m 바깥
  const kneeH = deckTop + (towerTop - deckTop) * 0.22;   // 다리 둘이 하나로 모이는 높이
  const f = (coords, hb, h, part) => ({
    type: 'Feature',
    properties: { kind: 'pylon', name: cfg.show, part, hb: +hb.toFixed(1), h: +h.toFixed(1) },
    geometry: { type: 'Polygon', coordinates: coords },
  });
  const feats = [];
  for (const side of [-1, 1]) {
    feats.push(f(box(offsetAt(line, cum, sP, legOut * side), u, 9, 6), 0, kneeH, 'leg'));
  }
  // 가로보 — 상판 바로 밑에서 두 다리를 잇는다
  feats.push(f(box(c, u, 5, legOut * 2 + 6), Math.max(deckTop - cfg.thick - 5, 1), deckTop - cfg.thick, 'beam'));
  // 다리가 상판 위에서 모이는 무릎 — 두 다리 폭을 다 덮는 넓은 상자
  feats.push(f(box(c, u, 8, legOut * 2 + 6), kneeH - 6, kneeH + 4, 'knee'));
  // 위 기둥 — 하나로 모인 뒤 꼭대기까지
  feats.push(f(box(c, u, 8, 10), kneeH, towerTop - (towerTop - deckTop) * 0.18, 'mast'));
  feats.push(f(box(c, u, 6, 7), towerTop - (towerTop - deckTop) * 0.2, towerTop, 'top'));
  return feats;
}

// ── 사장교 케이블 ────────────────────────────────────────────────
// 주탑 위쪽에서 상판 가장자리로 **곧게** 뻗는 다발. 주탑마다 앞뒤 두 방향, 상판 양옆 두 줄.
// 위에서부터 긴 케이블이 먼 곳에, 짧은 케이블이 가까운 곳에 닿는다(부채꼴).
//
// 그리는 방법은 현수교와 같다 — 기울어진 막대를 못 세우니 짧은 토막을 계단으로 잇는다.
// 토막은 정사각형이 아니라 케이블 방향으로 누운 띠라서 계단이 덜 보인다.
// 🔴 여기서도 케이블은 상판 경로를 따라간다 (offsetAt). 직선으로 이으면 휘는 구간에서 빠져나간다.
function stayCables(line, cum, sP, towerTop, deckTopAt, cfg, reachM) {
  // 🔴 처음엔 8가닥·14 m 토막으로 했더니 검은 삼각형 벽이 됐다. 토막 하나가 14 m 가는 동안 7 m 를
  //    올라가서 케이블 한 가닥이 7 m 두께의 띠가 되고, 8가닥이 주탑 근처에서 5 m 간격으로 붙어 버렸다.
  //    그래서 가닥을 줄이고(6), 토막을 짧게(8 m → 두께 4 m), 주탑에 닿는 높이 범위를 넓혔다(0.45).
  const N = 6;          // 한 방향 한 줄에 케이블 6가닥. 실제는 20가닥 넘지만 멀리서는 6이면 다발로 읽힌다
  const STEP_M = 8;     // 토막 길이
  const WIDTH_M = 0.8;  // 토막 폭. 케이블 굵기가 아니라 "멀리서도 보이는 최소 폭" 이다
  const total = cum[cum.length - 1];
  const half = cfg.deckM / 2 - 1;
  const fan = (towerTop - deckTopAt(sP)) * 0.45;   // 케이블이 주탑에 닿는 높이의 범위
  const feats = [];

  for (const dir of [-1, 1]) {
    for (let k = 1; k <= N; k++) {
      const d = (reachM * k) / N;
      const sEnd = sP + dir * d;
      if (sEnd < 0 || sEnd > total) continue;
      const zTop = towerTop - (fan * (N - k)) / N;   // 긴 케이블일수록 위에 닿는다
      const zEnd = deckTopAt(sEnd);
      const M = Math.max(3, Math.round(d / STEP_M));
      for (const side of [-1, 1]) {
        for (let i = 0; i < M; i++) {
          const t0 = i / M, t1 = (i + 1) / M;
          const a = offsetAt(line, cum, sP + dir * d * t0, half * side * t0);
          const b = offsetAt(line, cum, sP + dir * d * t1, half * side * t1);
          const z0 = zTop + (zEnd - zTop) * t0, z1 = zTop + (zEnd - zTop) * t1;
          feats.push({
            type: 'Feature',
            properties: { kind: 'stay', name: cfg.show, hb: +Math.min(z0, z1).toFixed(1), h: +(Math.max(z0, z1) + 0.6).toFixed(1) },
            geometry: { type: 'Polygon', coordinates: ribbon(a, b, WIDTH_M) },
          });
        }
      }
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
const namedParts = [];   // 이름난 다리의 상판 중심선. 고가도로 단계에서 겹치는 도로를 빼는 데 쓴다

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
  namedParts.push(part);

  row.후보줄 = cands.length;
  row.기준점까지 = nearestM + ' m';
  row.길이 = Math.round(built.total) + ' m';
  row.상판조각 = built.feats.length;

  if (cfg.pylons) {
    // 5-a) 손으로 넣는 주탑 — 기준점(외곽선 가운데)에서 양쪽으로 주경간의 절반씩 떨어진 곳.
    //      상판 높이는 그 자리의 상판 조각과 같은 함수(topAt)에서 얻는다. 따로 정하면 어긋난다.
    const sMid = arcLengthAt(part, built.cum, anchor);
    const spots = [sMid - cfg.pylons.span / 2, sMid + cfg.pylons.span / 2];
    let n = 0;
    for (const sP of spots) {
      if (sP < 0 || sP > built.total) continue;
      features.push(...pylonBoxes(part, built.cum, sP, cfg.pylons.h, built.topAt(sP), cfg));
      // 케이블은 주경간 쪽으로 주경간의 절반 조금 못 미치게, 반대쪽(측경간)도 같은 길이로
      features.push(...stayCables(part, built.cum, sP, cfg.pylons.h, built.topAt, cfg, cfg.pylons.span / 2 - 12));
      n++;
    }
    row.주탑 = `${n}개 · 높이 ${cfg.pylons.h} m${cfg.pylons.estimated ? ' (추정)' : ''} · 간격 ${cfg.pylons.span} m · 손으로 넣음`;
    row.케이블 = '사장교 다발';
  } else {
    // 5-b) OSM 에 있는 주탑 — 중심선에서 60 m 안
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
      row.케이블 = '현수교 곡선';
    } else if (mine.length) {
      row.주탑 = `${mine.length}개`;
    }
  }
}
const namedCount = features.length;

// ── 4) 고가도로 — 이름난 다리 밖의 bridge=yes 도로 전부 ───────────
// 도로마다 조각을 내서 세운다. 높이는 layer 로, 폭은 lanes 나 등급으로 짐작한다.
const viaductRows = { 후보: 0, 짧아서뺌: 0, 이름난다리와겹쳐뺌: 0, 세움: 0, 조각: 0 };
for (const w of osm.elements) {
  if (w.type !== 'way' || !w.geometry || !w.tags) continue;
  const t = w.tags;
  if (!t.bridge || t.bridge === 'no' || !VIADUCT_ROAD.test(t.highway ?? '')) continue;
  if (t.tunnel || +(t.layer ?? 1) < 0) continue;
  viaductRows.후보++;
  const line = asXY(w);
  if (lengthM(line) < VIADUCT_MIN_M) { viaductRows.짧아서뺌++; continue; }
  // 이름난 다리와 같은 자리면 뺀다 — 그 다리는 위에서 바다 높이로 이미 세웠다
  const nearNamed = line.filter((p) => namedParts.some((part) => distToLineM(part, p) < NAMED_OVERLAP_M)).length;
  if (nearNamed * 2 >= line.length) { viaductRows.이름난다리와겹쳐뺌++; continue; }

  const layer = Math.max(1, +(t.layer ?? 1) || 1);
  const lanes = +t.lanes || 0;
  const base = t.highway.replace(/_link$/, '');
  const deckM = lanes ? lanes * 3.5 + 1 : (VIADUCT_WIDTH[base] ?? VIADUCT_WIDTH_DEFAULT);
  const built = deckChunks(line, { deckM, cruise: layer * VIADUCT_TOP_PER_LAYER, thick: VIADUCT_THICK },
    { flat: true, chunk: VIADUCT_CHUNK_M, minM: VIADUCT_MIN_M, kind: 'viaduct' });
  if (!built) continue;
  features.push(...built.feats);
  viaductRows.세움++;
  viaductRows.조각 += built.feats.length;
}
report.push({ 고가도로: viaductRows });

fs.writeFileSync(OUT, JSON.stringify({ type: 'FeatureCollection', features }), 'utf8');
const kb = +(fs.statSync(OUT).size / 1024).toFixed(1);

console.log('\n' + JSON.stringify(report, null, 2));
console.log(`\n도형 ${features.length} 개 (이름난 다리 ${namedCount} · 고가도로 ${features.length - namedCount}) · ${kb} KB → ${path.relative(HERE, OUT)}`);
fs.writeFileSync(
  path.join(WORK, '_bridges.json'),
  JSON.stringify({ at: new Date().toISOString(), features: features.length, kb, report }, null, 2),
  'utf8'
);
