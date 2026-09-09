// 좌수영교 → 신세계 센텀시티 보행 경로 두 개를 우리 OSM 그래프에서 직접 찾는다.
//
// 🔴 길찾기 API 를 쓰지 않는다. 두 가지 이유다.
//   1. 공개된 보행자 경로 API 가 없다.
//   2. 더 중요한 것 — 우리 온톨로지로 판정하려면 경로가 **우리 그래프**에서 나와야
//      한다. 남의 API 가 준 선에는 way id 가 없고, way id 가 없으면 어떤 구간이
//      왜 불가인지 말할 수 없다. 데모의 핵심이 바로 그 "왜" 다.
//
// 내는 경로는 둘이다.
//   (a) 최단거리   — 판정을 무시하고 거리만 본다. 사람이 보통 받는 경로다.
//   (b) 휠체어 최선 — 불가 구간을 뺀 그래프에서 가장 짧은 길.
// 둘을 겹쳐 봐야 "무엇을 피하느라 얼마를 더 가는가" 가 보인다.
//
// 쓰는 법: node bigData/process/wheelchair-route.mjs

import fs from 'node:fs';
import path from 'node:path';
import readline from 'node:readline';
import {
  REPO_ROOT, VERDICT, loadOntology, buildRuleset, judge, loadSegments,
} from './wheelchair-verdict.mjs';

const PBF_DIR = path.join(REPO_ROOT, 'bigData', 'data', 'raw', 'pbf');
const OUT_GEOJSON = path.join(REPO_ROOT, 'bigData', 'data', 'staged', 'wheelchair-demo.geojson');
// 화면은 정적 서버의 뿌리를 demo 폴더로 잡는다. 그래서 읽을 수 있는 자리에 사본을 둔다.
const OUT_DEMO = path.join(REPO_ROOT, 'bigData', 'demo', 'wheelchair-route', 'route.geojson');

// 좌표를 붙이는 격자. 소수 7자리 ≈ 1.1cm — OSM 이 같은 노드로 쓰는 점은 값이 같다.
const COORD_PRECISION = 7;
const key = (lat, lon) => `${lat.toFixed(COORD_PRECISION)},${lon.toFixed(COORD_PRECISION)}`;

const R_EARTH = 6371008.8;
function haversine(a, b) {
  const toRad = Math.PI / 180;
  const dLat = (b.lat - a.lat) * toRad, dLon = (b.lon - a.lon) * toRad;
  const la1 = a.lat * toRad, la2 = b.lat * toRad;
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(la1) * Math.cos(la2) * Math.sin(dLon / 2) ** 2;
  return 2 * R_EARTH * Math.asin(Math.sqrt(h));
}

async function* readNdjson(file) {
  const rl = readline.createInterface({ input: fs.createReadStream(file), crlfDelay: Infinity });
  for await (const line of rl) { if (line.trim()) yield JSON.parse(line); }
}

// ── 출발지·도착지를 데이터에서 확정한다 ──────────────────────────────────
//
// 🔴 좌표를 손으로 적지 않는다. 무엇으로 확정했는지가 결과에 남아야 한다.

/** 좌수영교 — bridge:name 태그가 이름을 직접 들고 있다. */
async function findJwasuyeongBridge() {
  const hits = [];
  for (const file of ['road.ndjson', 'walk.ndjson']) {
    for await (const w of readNdjson(path.join(PBF_DIR, file))) {
      const t = w.tags ?? {};
      const named = t['bridge:name'] ?? t['bridge:name:ko'] ?? t.name ?? '';
      if (t.bridge && named.includes('좌수영교')) {
        hits.push({ id: w.id, file, tags: t, geometry: w.geometry });
      }
    }
  }
  if (!hits.length) return null;
  // 서쪽 끝을 출발점으로 쓴다 — 센텀시티가 동쪽이라 다리를 건너는 방향이 된다.
  let best = null;
  for (const h of hits) for (const p of h.geometry) {
    if (!best || p.lon < best.point.lon) best = { point: p, way: h };
  }
  return {
    point: best.point,
    evidence: {
      what: '좌수영교',
      how: `road/walk ndjson 에서 bridge=yes 이고 bridge:name 에 '좌수영교' 가 든 way ${hits.length}개를 찾아, 그 중 가장 서쪽 끝점을 썼다`,
      wayIds: hits.map((h) => h.id),
      tagUsed: 'bridge:name',
      sampleTags: { 'bridge:name': best.way.tags['bridge:name'], foot: best.way.tags.foot, highway: best.way.tags.highway },
    },
  };
}

/** 신세계 센텀시티 — 백화점 건물 자체는 추출본에 없다(poi 는 node 만 담겼다). 이름이 붙은 점으로 확정한다. */
async function findShinsegaeCentum() {
  const inStore = [];   // 건물 안에 있는 이름 붙은 점
  const stops = [];     // 같은 이름의 버스 정류장
  for await (const n of readNdjson(path.join(PBF_DIR, 'poi.ndjson'))) {
    const name = n.tags?.name ?? '';
    if (name.includes('신세계') && name.includes('센텀시티')) inStore.push(n);
  }
  for await (const n of readNdjson(path.join(PBF_DIR, 'transit.ndjson'))) {
    const name = n.tags?.name ?? '';
    if (n.tags?.highway === 'bus_stop' && name.includes('신세계') && name.includes('센텀시티')) stops.push(n);
  }
  const anchor = inStore[0] ?? stops[0];
  if (!anchor) return null;
  return {
    point: { lat: anchor.lat, lon: anchor.lon },
    evidence: {
      what: '신세계 센텀시티',
      how: inStore.length
        ? `poi.ndjson 에서 이름에 '신세계'와 '센텀시티'가 함께 든 node 를 썼다 (백화점 건물 폴리곤은 추출본에 없다 — poi 는 node 만 담겼다)`
        : `poi 에 없어 transit.ndjson 의 highway=bus_stop 중 이름이 '신세계 센텀시티' 인 정류장을 썼다`,
      anchorId: anchor.id,
      anchorName: anchor.tags.name,
      corroboration: stops.map((s) => ({ id: s.id, name: s.tags.name, stop_id: s.tags.stop_id, lat: s.lat, lon: s.lon })),
    },
  };
}

// ── 그래프 ────────────────────────────────────────────────────────────────

/** 두 끝점을 넉넉히 감싸는 상자. 부산 전역 51만 점을 다 올릴 이유가 없다. */
function bboxAround(points, padLat = 0.018, padLon = 0.022) {
  const lats = points.map((p) => p.lat), lons = points.map((p) => p.lon);
  return {
    south: Math.min(...lats) - padLat, north: Math.max(...lats) + padLat,
    west: Math.min(...lons) - padLon, east: Math.max(...lons) + padLon,
  };
}
const inBbox = (p, b) => p.lat >= b.south && p.lat <= b.north && p.lon >= b.west && p.lon <= b.east;

/**
 * 보행 그래프를 만든다. 도로·보도·계단을 전부 넣는다 —
 * 계단을 빼 버리면 "이 경로가 계단에서 막힌다" 를 보여줄 수가 없다.
 */
async function buildGraph(bbox, segIndex, ruleset, onto) {
  const nodes = new Map();        // key → {lat,lon,edges:[]}
  const ways = new Map();         // wayId → {verdict,reason,name,highway,topic,p90Slope,lengthM}
  const topics = { 'road.ndjson': 'road', 'walk.ndjson': 'walk', 'stairs.ndjson': 'stairs' };

  const nodeAt = (p) => {
    const k = key(p.lat, p.lon);
    let n = nodes.get(k);
    if (!n) { n = { lat: p.lat, lon: p.lon, edges: [] }; nodes.set(k, n); }
    return { k, n };
  };

  for (const [file, topic] of Object.entries(topics)) {
    for await (const w of readNdjson(path.join(PBF_DIR, file))) {
      const geom = w.geometry ?? [];
      if (geom.length < 2) continue;
      if (!geom.some((p) => inBbox(p, bbox))) continue;

      // 판정에 쓸 구간 사실. staged 표에 있으면 그것을 쓰고(경사가 거기 있다),
      // 없으면 OSM 태그만으로 만든다 — 경사는 미상이 되지만 계단·자동차전용은 여전히 잡힌다.
      const staged = segIndex.get(w.id);
      const t = w.tags ?? {};
      const seg = staged ?? {
        id: w.id, topic, highway: t.highway ?? null, name: t.name ?? null,
        length: null, p90Slope: undefined,
        stepCount: t.step_count != null ? Number(t.step_count) : null,
      };
      const { verdict, reason } = judge(seg, ruleset, onto);

      ways.set(w.id, {
        verdict, reason,
        name: seg.name ?? t.name ?? null,
        highway: seg.highway ?? t.highway ?? null,
        topic: seg.topic ?? topic,
        p90Slope: typeof seg.p90Slope === 'number' ? seg.p90Slope : null,
        fromStaged: Boolean(staged),
      });

      for (let i = 1; i < geom.length; i++) {
        const a = geom[i - 1], b = geom[i];
        if (!inBbox(a, bbox) && !inBbox(b, bbox)) continue;
        const { k: ka, n: na } = nodeAt(a);
        const { k: kb, n: nb } = nodeAt(b);
        if (ka === kb) continue;
        const len = haversine(a, b);
        na.edges.push({ to: kb, len, wayId: w.id });
        nb.edges.push({ to: ka, len, wayId: w.id });   // 보행자는 일방통행을 따르지 않는다
      }
    }
  }
  return { nodes, ways };
}

/** 그래프에서 주어진 점에 가장 가까운 노드. */
function snap(graph, point) {
  let best = null;
  for (const [k, n] of graph.nodes) {
    const d = haversine(point, n);
    if (!best || d < best.distM) best = { key: k, node: n, distM: d };
  }
  return best;
}

/** Dijkstra. blocked 를 피하라고 하면 불가 구간을 아예 지나지 않는다. */
function dijkstra(graph, startKey, goalKey, { avoidBlocked }) {
  const dist = new Map([[startKey, 0]]);
  const prev = new Map();
  const done = new Set();
  // 데모 규모(수만 노드)에서는 이 정도 우선순위 큐로 충분하다.
  const queue = [{ k: startKey, d: 0 }];

  while (queue.length) {
    let bi = 0;
    for (let i = 1; i < queue.length; i++) if (queue[i].d < queue[bi].d) bi = i;
    const { k, d } = queue.splice(bi, 1)[0];
    if (done.has(k)) continue;
    done.add(k);
    if (k === goalKey) break;

    for (const e of graph.nodes.get(k).edges) {
      if (avoidBlocked && graph.ways.get(e.wayId)?.verdict === VERDICT.BLOCKED) continue;
      const nd = d + e.len;
      if (nd < (dist.get(e.to) ?? Infinity)) {
        dist.set(e.to, nd);
        prev.set(e.to, { from: k, wayId: e.wayId, len: e.len });
        queue.push({ k: e.to, d: nd });
      }
    }
  }
  if (!prev.has(goalKey) && startKey !== goalKey) return null;

  const steps = [];
  for (let k = goalKey; k !== startKey;) {
    const p = prev.get(k);
    if (!p) return null;
    steps.push({ from: p.from, to: k, wayId: p.wayId, len: p.len });
    k = p.from;
  }
  steps.reverse();
  return { steps, totalM: dist.get(goalKey) };
}

/** 같은 way 를 연달아 지나는 구간을 하나의 선으로 묶는다. */
function toFeatures(graph, route, routeId, routeLabel) {
  const feats = [];
  let run = null;
  const push = () => {
    if (!run) return;
    const w = graph.ways.get(run.wayId);
    feats.push({
      type: 'Feature',
      geometry: { type: 'LineString', coordinates: run.coords },
      properties: {
        route: routeId, routeName: routeLabel,
        verdict: w.verdict, reason: w.reason,
        wayId: run.wayId, name: w.name, highway: w.highway, topic: w.topic,
        lengthM: Math.round(run.lengthM * 10) / 10,
        p90Slope: w.p90Slope,
        slopeFromStagedTable: w.fromStaged,
      },
    });
    run = null;
  };

  for (const s of route.steps) {
    const a = graph.nodes.get(s.from), b = graph.nodes.get(s.to);
    if (!run || run.wayId !== s.wayId) {
      push();
      run = { wayId: s.wayId, coords: [[a.lon, a.lat]], lengthM: 0 };
    }
    run.coords.push([b.lon, b.lat]);
    run.lengthM += s.len;
  }
  push();
  return feats;
}

function summarise(feats) {
  const by = {};
  for (const f of feats) {
    const v = f.properties.verdict;
    by[v] ??= { count: 0, metres: 0 };
    by[v].count += 1;
    by[v].metres += f.properties.lengthM;
  }
  for (const v of Object.values(by)) v.metres = Math.round(v.metres);
  return by;
}

// ── 실행 ──────────────────────────────────────────────────────────────────

async function main() {
  const onto = loadOntology();
  const ruleset = buildRuleset(onto, 'bm:Wheelchair');
  console.log(`온톨로지: ${onto.from}`);
  console.log(`돌릴 수 있는 규칙: ${ruleset.runnable.map((r) => r.prefLabel?.ko).join(' · ')}`);

  console.log('\n끝점을 데이터에서 확정하는 중…');
  const origin = await findJwasuyeongBridge();
  if (!origin) throw new Error('좌수영교를 찾지 못했습니다. 좌표를 지어내지 않고 멈춥니다.');
  const dest = await findShinsegaeCentum();
  if (!dest) throw new Error('신세계 센텀시티를 찾지 못했습니다. 좌표를 지어내지 않고 멈춥니다.');

  for (const e of [origin, dest]) {
    console.log(`  ${e.evidence.what}: ${e.point.lat.toFixed(6)}, ${e.point.lon.toFixed(6)}`);
    console.log(`    확정 근거: ${e.evidence.how}`);
  }

  const bbox = bboxAround([origin.point, dest.point]);
  console.log('\n구간 표를 읽고 그래프를 만드는 중…');
  const segIndex = await loadSegments();
  const graph = await buildGraph(bbox, segIndex, ruleset, onto);
  console.log(`  노드 ${graph.nodes.size.toLocaleString()}개 · way ${graph.ways.size.toLocaleString()}개`);

  const from = snap(graph, origin.point);
  const to = snap(graph, dest.point);
  console.log(`  출발지 스냅 ${from.distM.toFixed(1)}m · 도착지 스냅 ${to.distM.toFixed(1)}m`);

  const plans = [
    { id: 'shortest', label: '최단거리 (판정 무시)', avoidBlocked: false },
    { id: 'wheelchair', label: '휠체어 최선 (불가 구간 제외)', avoidBlocked: true },
  ];

  const features = [];
  const report = [];
  for (const plan of plans) {
    const route = dijkstra(graph, from.key, to.key, { avoidBlocked: plan.avoidBlocked });
    if (!route) {
      console.log(`\n${plan.label}: 경로 없음`);
      report.push({ ...plan, found: false });
      continue;
    }
    const feats = toFeatures(graph, route, plan.id, plan.label);
    features.push(...feats);
    const dist = summarise(feats);
    console.log(`\n${plan.label}`);
    console.log(`  총 길이 ${Math.round(route.totalM).toLocaleString()} m · 구간(선) ${feats.length}개`);
    for (const [v, s] of Object.entries(dist)) console.log(`    ${v}  ${s.count}개  ${s.metres.toLocaleString()} m`);
    report.push({ ...plan, found: true, totalM: Math.round(route.totalM), lineCount: feats.length, verdicts: dist });
  }

  // 끝점은 점으로 같이 낸다. 화면에서 어디서 어디로 가는지 보여야 한다.
  for (const [role, e] of [['origin', origin], ['dest', dest]]) {
    features.push({
      type: 'Feature',
      geometry: { type: 'Point', coordinates: [e.point.lon, e.point.lat] },
      properties: { kind: 'endpoint', role, name: e.evidence.what, how: e.evidence.how },
    });
  }

  const geojson = {
    type: 'FeatureCollection',
    metadata: {
      generatedAt: new Date().toISOString(),
      generatedBy: 'bigData/process/wheelchair-route.mjs',
      ontology: onto.from,
      profile: 'bm:Wheelchair',
      rulesRun: ruleset.runnable.map((r) => ({ id: r['@id'], label: r.prefLabel?.ko })),
      rulesParked: ruleset.parked.map((r) => ({ id: r['@id'], label: r.prefLabel?.ko, why: r.evidenceStatus })),
      endpoints: { origin: origin.evidence, dest: dest.evidence },
      routes: report,
      // 🔴 화면에 그대로 띄운다. 데모의 한계를 숨기지 않는다.
      caveat: '온톨로지에 통과 "가능" 을 선언하는 규칙이 없다 — 선언된 규칙은 전부 불가 조건이다. '
            + '유효폭(bm:widthM)이 수집되면 그때 "가능" 이 나올 수 있다. 그전까지 불가가 아닌 모든 구간은 미상이다.',
    },
    features,
  };

  fs.mkdirSync(path.dirname(OUT_GEOJSON), { recursive: true });
  fs.mkdirSync(path.dirname(OUT_DEMO), { recursive: true });
  const text = JSON.stringify(geojson, null, 2);
  fs.writeFileSync(OUT_GEOJSON, text);
  fs.writeFileSync(OUT_DEMO, text);
  console.log(`\n썼습니다:\n  ${OUT_GEOJSON}\n  ${OUT_DEMO}`);
}

main().catch((err) => { console.error(`\n${err.message}`); process.exit(1); });
