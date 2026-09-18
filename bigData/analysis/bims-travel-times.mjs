// BIMS 실시간 관측에서 버스가 실제로 얼마나 빠른지 잰다 — S15P21E201-1123.
//
// 🔴 왜 필요한가. 대중교통 시간 계산이 쓰는 속도·정차시간·배차간격이 전부 "누가 정한 값"
//    이었다. 그런데 이 저장소에는 그것을 실제로 잴 수 있는 자료가 이미 있었다 —
//    data/raw/transit/bims-*.ndjson 이 60초마다 노선 위의 차량을 순번·좌표와 함께 찍어 뒀다.
//
// 무엇을 내나
//   1. 표정속도(정차 포함) — 한 차량이 정류장 a 에서 b 까지 간 거리 ÷ 걸린 시간
//   2. 정류장 한 칸 지나는 시간
//   3. 실측 배차간격 — 같은 정류장에 다른 차가 오기까지의 간격
//
// 🔴 3번이 특히 중요하다. BIMS 의 routes-all.json 에 적힌 headway 는 **계획값**이고,
//    실측은 그보다 1.5~3.2배 길었다. 그 값을 "실측" 으로 믿고 쓰면 기다리는 시간을
//    전 노선에서 낮잡는다.
//
// 한계: 폴링 대상이 다섯 노선(15·23·96·111·126)이고 전부 일반버스다. 급행·마을버스는
//       재지 않았다. 폴링 대상이 늘면 종류별로 갈라야 한다.
//
// 돌리는 법:  node bigData/analysis/bims-travel-times.mjs
//
// 🔴 필요한 원천 넷 중 둘이 저장소에 없다 (2026-09-17 확인).
//      🟢 config/routes-all.json              커밋됨
//      🟢 data/raw/transit/bims-*.ndjson      커밋됨
//      🔴 data/raw/transit/_route-stops.json  커밋 안 됨
//      🔴 data/raw/transit/_stop-coords.json  커밋 안 됨
//    그래서 이 스크립트는 그 둘을 수집한 PC 에서만 돈다. 백엔드가 쓰는 노선망
//    (backend/src/main/resources/transit/busan-bus-network.json)은 커밋돼 있어 제품은
//    돌지만, **다시 만들 수는 없는 상태**다. 둘을 커밋하면 풀린다 (합쳐서 2.7MB).

import fs from "node:fs";
import readline from "node:readline";
import path from "node:path";
import { fileURLToPath } from "node:url";

// 이 파일 기준으로 bigData/ 를 찾는다 — 어느 폴더에서 돌려도 같게 동작한다.
const ROOT = path.join(path.dirname(fileURLToPath(import.meta.url)), "..") + path.sep;
function mustRead(rel) {
  const file = path.join(ROOT, rel);
  if (!fs.existsSync(file)) {
    console.error("원천 파일이 없다: " + rel);
    console.error("이 파일은 아직 저장소에 커밋되지 않았다 — 수집한 PC 에서만 돌릴 수 있다.");
    console.error("자세한 것은 이 파일 맨 위 주석.");
    process.exit(1);
  }
  return JSON.parse(fs.readFileSync(file, "utf8"));
}

const stops = mustRead("data/raw/transit/_stop-coords.json").stops;
const routeStops = mustRead("data/raw/transit/_route-stops.json").routes;
const routesAll = mustRead("config/routes-all.json").routes;
const meta = {};
for (const r of (Array.isArray(routesAll) ? routesAll : Object.values(routesAll))) meta[String(r.lineid)] = r;

const R = 6371000, rad = (d) => d * Math.PI / 180;
const hav = (a, b, c, d) => { const dLat = rad(c - a), dLng = rad(d - b);
  const x = Math.sin(dLat/2)**2 + Math.cos(rad(a))*Math.cos(rad(c))*Math.sin(dLng/2)**2;
  return R * 2 * Math.atan2(Math.sqrt(x), Math.sqrt(1 - x)); };

// 노선별 정류장 순번 → 좌표
const coordsByRoute = {};
for (const id in routeStops) {
  coordsByRoute[id] = routeStops[id].stops.map(s => { const c = stops[s.bstopid];
    return c ? [c.lat, c.lon] : null; });
}

// (노선, 차량) → [{ts, idx}]
const track = new Map();
// (노선, 정류장순번) → [ts, carno] 도착 기록 (배차 재기용)
const arrivals = new Map();

let lines = 0;
for (const file of ["bims-2026-08-26.ndjson", "bims-2026-08-27.ndjson", "bims-2026-08-28.ndjson"]) {
  const rl = readline.createInterface({ input: fs.createReadStream(path.join(ROOT, "data/raw/transit", file)), crlfDelay: Infinity });
  for await (const line of rl) {
    if (!line.trim()) continue;
    lines++;
    let j; try { j = JSON.parse(line); } catch { continue; }
    const raw = j.raw || "";
    for (const m of raw.matchAll(/<item>([\s\S]*?)<\/item>/g)) {
      const t = m[1];
      const car = t.match(/<carno>([^<]*)<\/carno>/); if (!car) continue;
      const idxm = t.match(/<bstopidx>([^<]*)<\/bstopidx>/); if (!idxm) continue;
      const key = j.routeId + "|" + car[1];
      if (!track.has(key)) track.set(key, []);
      track.get(key).push({ ts: j.ts, idx: +idxm[1] });
      const ak = j.routeId + "|" + (+idxm[1]);
      if (!arrivals.has(ak)) arrivals.set(ak, []);
      arrivals.get(ak).push({ ts: j.ts, car: car[1] });
    }
  }
}

// ── 1. 구간 속도 (정차 포함) ─────────────────────────────────────────────
const runs = [];       // {routeId, stops, meters, seconds}
for (const [key, obs] of track) {
  const [routeId] = key.split("|");
  const coords = coordsByRoute[routeId]; if (!coords) continue;
  obs.sort((a, b) => a.ts - b.ts);
  let start = null, prev = null;
  const flush = () => {
    if (start && prev && prev.idx > start.idx) {
      let meters = 0, ok = true;
      for (let i = start.idx - 1; i < prev.idx - 1; i++) {
        const a = coords[i], b = coords[i + 1];
        if (!a || !b) { ok = false; break; }
        meters += hav(a[0], a[1], b[0], b[1]);
      }
      const sec = (prev.ts - start.ts) / 1000;
      if (ok && meters > 300 && sec > 120) runs.push({ routeId, stops: prev.idx - start.idx, meters, seconds: sec });
    }
    start = prev;
  };
  for (const o of obs) {
    if (!start) { start = o; prev = o; continue; }
    // 순번이 줄거나 10분 넘게 끊기면 한 운행이 끝난 것으로 본다
    if (o.idx < prev.idx || o.ts - prev.ts > 10 * 60 * 1000) { flush(); start = o; prev = o; continue; }
    prev = o;
  }
  flush();
}

const med = (a) => { if (!a.length) return null; const s = [...a].sort((x, y) => x - y); return s[Math.floor(s.length / 2)]; };
const speeds = runs.map(r => r.meters / r.seconds * 3.6);
const perHop = runs.map(r => r.seconds / r.stops);

console.log("읽은 응답 줄:", lines, "· 추적한 (노선,차량):", track.size, "· 쓸 수 있는 운행 구간:", runs.length);
console.log();
console.log("=== 실측 1 — 정차 포함 표정속도 ===");
console.log("  중앙값 " + med(speeds).toFixed(1) + " km/h  (내가 쓰던 모델: 차내 22km/h + 정차 20초)");
console.log("  구간 거리 합계 " + (runs.reduce((s, r) => s + r.meters, 0) / 1000).toFixed(0) + " km · 관측 시간 합계 "
  + (runs.reduce((s, r) => s + r.seconds, 0) / 3600).toFixed(1) + " 시간");
console.log();
console.log("=== 실측 2 — 정류장 한 칸 지나는 데 걸리는 시간 ===");
console.log("  중앙값 " + med(perHop).toFixed(0) + " 초/정거장");
console.log();
console.log("=== 노선별 ===");
const byRoute = {};
for (const r of runs) { (byRoute[r.routeId] = byRoute[r.routeId] || []).push(r); }
for (const id in byRoute) {
  const rs = byRoute[id];
  const sp = med(rs.map(r => r.meters / r.seconds * 3.6));
  const hop = med(rs.map(r => r.seconds / r.stops));
  const m = meta[id] || {};
  console.log("  " + (m.num || id).padEnd(6) + " " + (m.type || "?").padEnd(10)
    + " 표정 " + sp.toFixed(1).padStart(5) + " km/h · " + hop.toFixed(0).padStart(3) + " 초/정거장 · 운행구간 " + rs.length
    + " · BIMS 배차 " + (m.headway == null ? "null" : m.headway + "분"));
}

// ── 2. 실측 배차간격 ────────────────────────────────────────────────────
console.log();
console.log("=== 실측 3 — 같은 정류장에 다른 차가 오기까지 (배차) ===");
const gapsByRoute = {};
for (const [k, list] of arrivals) {
  const [routeId] = k.split("|");
  list.sort((a, b) => a.ts - b.ts);
  let lastCar = null, lastTs = null;
  for (const o of list) {
    if (lastCar && o.car !== lastCar) {
      const gapMin = (o.ts - lastTs) / 60000;
      if (gapMin > 1 && gapMin < 120) (gapsByRoute[routeId] = gapsByRoute[routeId] || []).push(gapMin);
    }
    if (o.car !== lastCar) { lastCar = o.car; lastTs = o.ts; }
  }
}
for (const id in gapsByRoute) {
  const m = meta[id] || {};
  console.log("  " + (m.num || id).padEnd(6) + " 실측 배차 중앙값 " + med(gapsByRoute[id]).toFixed(1).padStart(5)
    + "분 · 표본 " + gapsByRoute[id].length + " · BIMS 가 적어 둔 값 " + (m.headway == null ? "null" : m.headway + "분"));
}
