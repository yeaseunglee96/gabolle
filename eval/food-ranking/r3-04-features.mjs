/**
 * 라운드3 4단계 — 라운드2 의 신호 위에 **해안선까지의 거리**를 얹는다.
 *
 * 🔴 라운드2 가 못 한 것 ③ 이 이것이다 — *"정답이 바닷가에 몰려 있는데 해안선
 *    레이어가 없다. 가장 아까운 미사용 신호다"* (docs/FOOD-RANKING-EVAL.md 12절).
 *    `r3-coastline.py` 가 OSM `natural=coastline` 에서 꼭짓점을 뽑아 뒀다.
 *
 * 라운드2 의 신호는 **다시 굽지 않고 그대로 읽어 쓴다** (data/r2-features.json).
 * 같은 후보 풀·같은 정의라야 라운드2 와 나란히 놓고 비교할 수 있다.
 *
 * 🔴 정답표에서 나온 값은 여전히 **하나도 넣지 않는다.** 미쉐린 가격대·블루리본
 *    리본수는 정답의 **강도**로만 쓴다 (r3-07-fit.mjs). 신호로 쓰면 누출이다.
 *
 * 산출: data/r3-features.json (약 40MB — 커밋하지 않는다)
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { haversine } from "./lib/pool.mjs";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const feat = JSON.parse(fs.readFileSync(path.join(HERE, "data", "r2-features.json"), "utf8"));
const coast = JSON.parse(fs.readFileSync(path.join(HERE, "data", "r3-coastline.json"), "utf8"));
const rows = feat.rows;
console.log(`후보 ${rows.length}곳 · 해안선 꼭짓점 ${coast.count}개 (way ${coast.ways}개)`);

// ── 해안선 격자 ──────────────────────────────────────────────────────────────
// 칸은 약 1.1 km. 링(ring)을 바깥으로 넓혀 가며 훑고, 이미 찾은 거리가 링의
// 안쪽 반경보다 가까우면 멈춘다 — 더 바깥에는 더 가까운 점이 있을 수 없다.
const CELL = 0.01;
const grid = new Map();
coast.points.forEach((p, i) => {
  const k = `${Math.floor(p[0] / CELL)}|${Math.floor(p[1] / CELL)}`;
  if (!grid.has(k)) grid.set(k, []);
  grid.get(k).push(i);
});

const MAX_RING = 80; // 약 88 km — 부산 안에서는 절대 안 닿는다
function nearestCoastM(lat, lon) {
  const cy = Math.floor(lat / CELL);
  const cx = Math.floor(lon / CELL);
  // 이 위도에서 칸 하나의 실제 폭(m) 중 작은 쪽 — 멈춤 판정을 보수적으로 한다
  const mPerCellLat = CELL * 111320;
  const mPerCellLon = CELL * 111320 * Math.cos((lat * Math.PI) / 180);
  const mPerCell = Math.min(mPerCellLat, mPerCellLon);
  let best = Infinity;
  for (let ring = 0; ring <= MAX_RING; ring++) {
    for (let y = cy - ring; y <= cy + ring; y++) {
      const edgeY = y === cy - ring || y === cy + ring;
      for (let x = cx - ring; x <= cx + ring; x++) {
        if (!edgeY && x !== cx - ring && x !== cx + ring) continue;
        const arr = grid.get(`${y}|${x}`);
        if (!arr) continue;
        for (const i of arr) {
          const p = coast.points[i];
          const d = haversine(lat, lon, p[0], p[1]);
          if (d < best) best = d;
        }
      }
    }
    if (best < Infinity && best <= ring * mPerCell) break;
  }
  return best;
}

const t0 = Date.now();
const out = rows.map((r, i) => {
  const coastM = Math.round(nearestCoastM(r.lat, r.lon));
  if (i && i % 10000 === 0) console.log(`  ${i} / ${rows.length} …`);
  return {
    ...r,
    coastM,
    // 라운드2 의 `touristM` 은 관광 기준점 345개(해수욕장 1건)까지의 거리였다.
    // 이건 바다 자체까지의 거리다 — 서로 다른 것을 잰다.
    isSeaside: coastM <= 300,
    isNearSea: coastM <= 1000,
  };
});
console.log(`해안선 거리 계산 ${((Date.now() - t0) / 1000).toFixed(0)}초`);

const ds = out.map((r) => r.coastM).sort((a, b) => a - b);
const q = (p) => ds[Math.floor(ds.length * p)];
console.log(`coastM 분위: 10% ${q(0.1)}m · 25% ${q(0.25)}m · 중앙 ${q(0.5)}m · 75% ${q(0.75)}m · 90% ${q(0.9)}m · 최대 ${ds[ds.length - 1]}m`);
console.log(`바다 300m 안 ${out.filter((r) => r.isSeaside).length}곳 · 1km 안 ${out.filter((r) => r.isNearSea).length}곳`);

fs.writeFileSync(path.join(HERE, "data", "r3-features.json"),
  JSON.stringify({ builtAt: new Date().toISOString(), count: out.length, rows: out }));
console.log("→ data/r3-features.json");
