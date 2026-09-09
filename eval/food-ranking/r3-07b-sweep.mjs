/**
 * 라운드3 7-b 단계 — 최종안을 고르기 전에 **조정용 안에서만** 손잡이를 돌려 본다.
 *
 * 🔴 판정용은 열지 않는다. 여기서 고른 값은 그대로 r3-08-final.mjs 가 쓴다.
 *
 * 돌리는 손잡이 셋:
 *   · 입력 칸을 어디까지 쓸 것인가 (전부 / 구군 원핫 빼기 / 수치만)
 *   · 정답 가중 (×10 · ×30 · ×100) — 정답이 0.2% 뿐이라 그냥 두면 전부 "아니오"
 *   · 규제 세기 λ
 *
 * 판정은 전부 **조정용 5겹 교차검증**으로 한다.
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { buildEncoder, fitScaler, applyScaler, trainLogistic, score, NUMERIC_NAMES } from "./lib/r3-model.mjs";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const feat = JSON.parse(fs.readFileSync(path.join(HERE, "data", "r3-features.json"), "utf8"));
const tune = JSON.parse(fs.readFileSync(path.join(HERE, "data", "r3-split-tune.json"), "utf8"));
const rows = feat.rows;
const N = rows.length;
const posIds = new Set(tune.items.map((i) => i.storeId));
const byStore = new Map(tune.items.map((i) => [i.storeId, i]));

const enc = buildEncoder(rows);
const nSo = new Set(rows.map((r) => r.soCode)).size;
const Xfull = rows.map((r) => enc.encode(r));
const scaler = fitScaler(Xfull, enc.nNumeric);
const Xs = Xfull.map((x) => applyScaler(x, scaler));
const yAll = rows.map((r) => (posIds.has(r.id) ? 1 : 0));

/** 칸을 잘라 쓰는 세 가지 */
// 🔴 "수치만"(업종·구군 원핫 없이)은 r3-06 의 손조합이 이미 그 범위를 다 재 봤고
//    가장 나은 것이 R@200 3.5% 였다. 여기서는 원핫이 있는 둘만 견준다 — 한 번 도는 데
//    몇 분씩 걸려서, 값이 낮을 것을 아는 조합에 시간을 쓰지 않는다.
const CUTS = {
  전부: { dim: enc.dim },
  "구군빼기": { dim: NUMERIC_NAMES.length + nSo },
};
function slice(x, cut) {
  if (cut.dim === enc.dim) return x;
  return x.subarray(0, cut.dim);
}

function mulberry32(a) {
  return function () { a |= 0; a = (a + 0x6d2b79f5) | 0; let t = Math.imul(a ^ (a >>> 15), 1 | a); t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t; return ((t ^ (t >>> 14)) >>> 0) / 4294967296; };
}
const rnd = mulberry32(7053026);
const negIdx = [];
for (let i = 0; i < N; i++) if (!yAll[i]) negIdx.push(i);
for (let i = negIdx.length - 1; i > 0; i--) { const j = Math.floor(rnd() * (i + 1)); [negIdx[i], negIdx[j]] = [negIdx[j], negIdx[i]]; }
const negUse = negIdx.slice(0, 15000);
function hash01(s) { let h = 2166136261; for (let i = 0; i < s.length; i++) { h ^= s.charCodeAt(i); h = Math.imul(h, 16777619); } return (h >>> 0) / 4294967296; }
const TB = rows.map((r) => hash01(r.id));

const posIdx = [];
for (let i = 0; i < N; i++) if (yAll[i]) posIdx.push(i);
for (let i = posIdx.length - 1; i > 0; i--) { const j = Math.floor(rnd() * (i + 1)); [posIdx[i], posIdx[j]] = [posIdx[j], posIdx[i]]; }
const FOLDS = 5;
const folds = Array.from({ length: FOLDS }, (_, k) => posIdx.filter((_, i) => i % FOLDS === k));

function rankMetrics(model, evalPosIds, cut) {
  const arr = rows.map((r, i) => ({ id: r.id, s: score(model, slice(Xs[i], cut)), tb: TB[i] }));
  arr.sort((a, b) => b.s - a.s || a.tb - b.tb);
  const ranks = [];
  arr.forEach((r, i) => { if (evalPosIds.has(r.id)) ranks.push(i + 1); });
  ranks.sort((a, b) => a - b);
  const n = ranks.length;
  const at = (k) => (ranks.filter((x) => x <= k).length / n) * 100;
  return { r200: at(200), r500: at(500), r1000: at(1000), medPct: (ranks[Math.floor(n / 2)] / N) * 100 };
}

// 무게방식은 r3-07-fit 이 A·B·C 를 다 재므로 여기서는 출처 수(B) 하나로 고정한다
const SCHEMES = { B: (t) => t.nTaste };
const results = [];
console.log("칸\t무게\t정답가중\tλ\tCV R@200\tCV R@500\tCV R@1000\tCV 중앙%");
for (const [cutName, cut] of Object.entries(CUTS)) {
  for (const [sk, wf] of Object.entries(SCHEMES)) {
    for (const POS_W of [10, 30, 100]) {
      for (const lambda of [0.01, 0.003, 0.001]) {
        const acc = { r200: 0, r500: 0, r1000: 0, medPct: 0 };
        for (let k = 0; k < FOLDS; k++) {
          const heldOut = new Set(folds[k].map((i) => rows[i].id));
          const trainPos = posIdx.filter((i) => !heldOut.has(rows[i].id));
          const idxs = [...trainPos, ...negUse];
          const X = idxs.map((i) => slice(Xs[i], cut));
          const y = idxs.map((i) => (heldOut.has(rows[i].id) ? 0 : yAll[i]));
          const sw = idxs.map((i) => (yAll[i] && !heldOut.has(rows[i].id) ? wf(byStore.get(rows[i].id)) : 1));
          const model = trainLogistic(X, y, lambda, { iters: 600, lr: 1.0, posWeight: POS_W, sampleWeight: sw });
          const m = rankMetrics(model, heldOut, cut);
          acc.r200 += m.r200; acc.r500 += m.r500; acc.r1000 += m.r1000; acc.medPct += m.medPct;
        }
        const avg = Object.fromEntries(Object.entries(acc).map(([k2, v]) => [k2, v / FOLDS]));
        results.push({ cut: cutName, scheme: sk, posW: POS_W, lambda, ...avg });
        console.log(`${cutName}\t${sk}\t${POS_W}\t${lambda}\t${avg.r200.toFixed(1)}%\t${avg.r500.toFixed(1)}%\t${avg.r1000.toFixed(1)}%\t${avg.medPct.toFixed(1)}%`);
      }
    }
  }
}
results.sort((a, b) => b.r200 - a.r200 || a.medPct - b.medPct);
console.log("\n── 조정용 교차검증 상위 6 ──");
for (const r of results.slice(0, 6)) console.log(`  ${r.cut} · 무게${r.scheme} · ×${r.posW} · λ=${r.lambda} → R@200 ${r.r200.toFixed(1)}% · 중앙 ${r.medPct.toFixed(1)}%`);
fs.writeFileSync(path.join(HERE, "data", "r3-sweep.json"), JSON.stringify({ ranAt: new Date().toISOString(), results }, null, 1));
console.log("\n→ data/r3-sweep.json");
