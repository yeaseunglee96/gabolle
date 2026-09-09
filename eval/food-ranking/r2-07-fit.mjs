/**
 * 라운드2 7단계 — 로지스틱 회귀를 **조정용 안에서만** 맞추고, 규제 세기를
 * 교차검증으로 고른다. 판정용은 열지 않는다.
 *
 * 산출: data/r2-model.json (가중치) · 표준출력에 교차검증 표
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { buildEncoder, fitScaler, applyScaler, trainLogistic, score } from "./lib/r2-model.mjs";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const feat = JSON.parse(fs.readFileSync(path.join(HERE, "data", "r2-features.json"), "utf8"));
const tune = JSON.parse(fs.readFileSync(path.join(HERE, "data", "r2-split-tune.json"), "utf8"));
const rows = feat.rows;
const N = rows.length;
const posIds = new Set(tune.items.map((i) => i.storeId));

const enc = buildEncoder(rows);
console.log(`입력 칸 ${enc.dim}개 (수치 ${enc.nNumeric} + 업종·구군 원핫 ${enc.dim - enc.nNumeric})`);
const Xall = rows.map((r) => enc.encode(r));
const scaler = fitScaler(Xall, enc.nNumeric);
const Xs = Xall.map((x) => applyScaler(x, scaler));
const yAll = rows.map((r) => (posIds.has(r.id) ? 1 : 0));

/** 학습을 빠르게 하려고 오답을 무작위로 줄인다 (순위에는 영향이 없다) */
function mulberry32(a) {
  return function () { a |= 0; a = (a + 0x6d2b79f5) | 0; let t = Math.imul(a ^ (a >>> 15), 1 | a); t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t; return ((t ^ (t >>> 14)) >>> 0) / 4294967296; };
}
const NEG_SAMPLE = 15000;
const rnd = mulberry32(7052026);
const negIdx = [];
for (let i = 0; i < N; i++) if (!yAll[i]) negIdx.push(i);
for (let i = negIdx.length - 1; i > 0; i--) { const j = Math.floor(rnd() * (i + 1)); [negIdx[i], negIdx[j]] = [negIdx[j], negIdx[i]]; }
const negUse = negIdx.slice(0, NEG_SAMPLE);

function hash01(s) { let h = 2166136261; for (let i = 0; i < s.length; i++) { h ^= s.charCodeAt(i); h = Math.imul(h, 16777619); } return (h >>> 0) / 4294967296; }

function rankMetrics(model, evalPosIds) {
  const ranked = rows.map((r, i) => ({ id: r.id, s: score(model, Xs[i]), tb: hash01(r.id) }))
    .sort((a, b) => b.s - a.s || a.tb - b.tb);
  const ranks = [];
  ranked.forEach((r, i) => { if (evalPosIds.has(r.id)) ranks.push(i + 1); });
  ranks.sort((a, b) => a - b);
  const n = ranks.length;
  return {
    n,
    r200: (ranks.filter((x) => x <= 200).length / n) * 100,
    r500: (ranks.filter((x) => x <= 500).length / n) * 100,
    r1000: (ranks.filter((x) => x <= 1000).length / n) * 100,
    medPct: (ranks[Math.floor(n / 2)] / N) * 100,
  };
}

// ── 조정용 안 5겹 교차검증 ────────────────────────────────────────────────────
const posIdx = [];
for (let i = 0; i < N; i++) if (yAll[i]) posIdx.push(i);
for (let i = posIdx.length - 1; i > 0; i--) { const j = Math.floor(rnd() * (i + 1)); [posIdx[i], posIdx[j]] = [posIdx[j], posIdx[i]]; }
const FOLDS = 5;
const folds = Array.from({ length: FOLDS }, (_, k) => posIdx.filter((_, i) => i % FOLDS === k));

const LAMBDAS = [0.3, 0.1, 0.03, 0.01, 0.003, 0.001];
const POS_W = 30;
console.log(`\n조정용 정답 ${posIdx.length}곳 · 오답 표본 ${negUse.length}곳 · 정답 가중 ×${POS_W}`);
console.log("규제λ\tCV R@200\tCV R@500\tCV R@1000\tCV 중앙%");
let best = null;
for (const lambda of LAMBDAS) {
  const acc = { r200: 0, r500: 0, r1000: 0, medPct: 0 };
  for (let k = 0; k < FOLDS; k++) {
    const heldOut = new Set(folds[k].map((i) => rows[i].id));
    const trainPos = posIdx.filter((i) => !heldOut.has(rows[i].id));
    const idxs = [...trainPos, ...negUse];
    const X = idxs.map((i) => Xs[i]);
    const y = idxs.map((i) => (heldOut.has(rows[i].id) ? 0 : yAll[i]));
    const model = trainLogistic(X, y, lambda, { iters: 600, lr: 1.0, posWeight: POS_W });
    const m = rankMetrics(model, heldOut);
    acc.r200 += m.r200; acc.r500 += m.r500; acc.r1000 += m.r1000; acc.medPct += m.medPct;
  }
  const avg = Object.fromEntries(Object.entries(acc).map(([k2, v]) => [k2, v / FOLDS]));
  console.log(`${lambda}\t${avg.r200.toFixed(1)}%\t${avg.r500.toFixed(1)}%\t${avg.r1000.toFixed(1)}%\t${avg.medPct.toFixed(1)}%`);
  if (!best || avg.r200 > best.avg.r200) best = { lambda, avg };
}

console.log(`\n교차검증이 고른 규제 세기: λ=${best.lambda} (CV Recall@200 ${best.avg.r200.toFixed(1)}%)`);

// ── 조정용 전체로 다시 맞춘다 ────────────────────────────────────────────────
const idxs = [...posIdx, ...negUse];
const finalModel = trainLogistic(idxs.map((i) => Xs[i]), idxs.map((i) => yAll[i]), best.lambda,
  { iters: 1200, lr: 1.0, posWeight: POS_W });
const inSample = rankMetrics(finalModel, posIds);
console.log(`조정용 전체 재적합 → 조정용에서 R@200 ${inSample.r200.toFixed(1)}% · 중앙 ${inSample.medPct.toFixed(1)}% (외운 값이라 부풀려져 있다)`);

const wpairs = enc.names.map((n, j) => [n, finalModel.w[j]]);
wpairs.sort((a, b) => Math.abs(b[1]) - Math.abs(a[1]));
console.log("\n가중치 큰 순 20개 (표준화된 값 기준. + 면 정답 쪽, - 면 오답 쪽)");
for (const [n, w] of wpairs.slice(0, 20)) console.log(`  ${w >= 0 ? "+" : ""}${w.toFixed(3)}\t${n}`);

fs.writeFileSync(path.join(HERE, "data", "r2-model.json"), JSON.stringify({
  fittedAt: new Date().toISOString(), lambda: best.lambda, posWeight: POS_W,
  negSample: NEG_SAMPLE, iters: 1200, cv: best.avg,
  names: enc.names, w: [...finalModel.w], b: finalModel.b,
  scaler: { mean: [...scaler.mean], sd: [...scaler.sd], nNumeric: scaler.nNumeric },
}, null, 1));
console.log("\n→ data/r2-model.json");
