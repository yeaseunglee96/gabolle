/**
 * 라운드3 7단계 — 로지스틱 회귀를 **조정용 안에서만** 맞춘다. 판정용은 안 연다.
 *
 * 라운드2 와 다른 것 둘:
 *   ① 입력에 **해안선까지의 거리**가 들어왔다 (2칸)
 *   ② 🔴 **정답의 강도**를 시험한다 — 여러 출처에 실린 가게에 더 무거운 무게를 준다.
 *      과제가 물은 것이 이것이다: 미쉐린 가격대(₩~₩₩₩₩)와 블루리본 리본수(1·2)를
 *      정답의 강도로 쓸 수 있는가.
 *
 *      🔴 **누출(leakage) 경계선을 여기 못 박는다.** 가격대·리본수·출처 수는
 *         **정답에만 붙어 있는 값**이다. 후보 53,716곳 중 정답이 아닌 곳에는 이 값이
 *         없다. 그래서 **입력(신호)으로는 절대 못 쓴다** — 쓰는 순간 "정답인 것만
 *         아는 값" 으로 정답을 맞히는 부정행위가 된다.
 *         무게로 쓰는 것은 다르다. 무게는 *"이 정답을 얼마나 중요하게 배울 것인가"*
 *         를 정할 뿐이고, 점수를 매길 때는 쓰이지 않는다. 판정용 정답의 무게는
 *         한 번도 보지 않는다 (Recall 은 맞힌 **개수**를 세지 무게를 세지 않는다).
 *
 * 산출: data/r3-model.json
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
console.log(`입력 칸 ${enc.dim}개 (수치 ${enc.nNumeric} + 업종·구군 원핫 ${enc.dim - enc.nNumeric})`);
console.log(`조정용 정답 ${posIds.size}곳 · 기저율 ${((posIds.size / N) * 100).toFixed(3)}%`);
const XallFull = rows.map((r) => enc.encode(r));
const scaler = fitScaler(XallFull, enc.nNumeric);
const XsFull = XallFull.map((x) => applyScaler(x, scaler));

/**
 * 🔴 r3-07b-sweep.mjs 가 **조정용 교차검증만으로** 고른 칸 자르기를 그대로 따른다.
 * 자르는 지점은 항상 앞에서부터라 `subarray` 한 번이면 된다
 * (수치 → 업종 원핫 → 구군 원핫 순서로 붙여 두었다).
 */
const nSo = new Set(rows.map((r) => r.soCode)).size;
const CUT_DIM = { 전부: enc.dim, 구군빼기: NUMERIC_NAMES.length + nSo, 수치만: NUMERIC_NAMES.length };
let cutName = "전부";
const sweepPath = path.join(HERE, "data", "r3-sweep.json");
if (fs.existsSync(sweepPath)) {
  const sw = JSON.parse(fs.readFileSync(sweepPath, "utf8"));
  const best = [...sw.results].sort((a, b) => b.r200 - a.r200 || a.medPct - b.medPct)[0];
  cutName = best.cut;
  console.log(`쓸기(r3-07b)가 고른 칸: **${cutName}** (그때 CV R@200 ${best.r200.toFixed(1)}%)`);
}
const DIM = CUT_DIM[cutName];
const Xs = DIM === enc.dim ? XsFull : XsFull.map((x) => x.subarray(0, DIM));
const NAMES = enc.names.slice(0, DIM);
console.log(`실제로 쓰는 칸 ${DIM}개 (${cutName})`);
const yAll = rows.map((r) => (posIds.has(r.id) ? 1 : 0));

function mulberry32(a) {
  return function () { a |= 0; a = (a + 0x6d2b79f5) | 0; let t = Math.imul(a ^ (a >>> 15), 1 | a); t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t; return ((t ^ (t >>> 14)) >>> 0) / 4294967296; };
}
const NEG_SAMPLE = 15000; // 라운드2 와 같은 값. 순위에는 영향이 없다
const rnd = mulberry32(7053026);
const negIdx = [];
for (let i = 0; i < N; i++) if (!yAll[i]) negIdx.push(i);
for (let i = negIdx.length - 1; i > 0; i--) { const j = Math.floor(rnd() * (i + 1)); [negIdx[i], negIdx[j]] = [negIdx[j], negIdx[i]]; }
const negUse = negIdx.slice(0, NEG_SAMPLE);

function hash01(s) { let h = 2166136261; for (let i = 0; i < s.length; i++) { h ^= s.charCodeAt(i); h = Math.imul(h, 16777619); } return (h >>> 0) / 4294967296; }

function rankMetrics(model, evalPosIds) {
  const ranked = rows.map((r, i) => ({ id: r.id, s: score(model, Xs[i]), tb: hash01(r.id) })).sort((a, b) => b.s - a.s || a.tb - b.tb);
  const ranks = [];
  ranked.forEach((r, i) => { if (evalPosIds.has(r.id)) ranks.push(i + 1); });
  ranks.sort((a, b) => a - b);
  const n = ranks.length;
  const at = (k) => (ranks.filter((x) => x <= k).length / n) * 100;
  return { n, r50: at(50), r100: at(100), r200: at(200), r500: at(500), r1000: at(1000), medPct: (ranks[Math.floor(n / 2)] / N) * 100 };
}

// ── 🔴 정답의 강도 — 세 가지를 조정용 교차검증으로 견준다 ────────────────────
const SCHEMES = {
  A: { label: "무게 없음 — 정답은 다 같다", w: () => 1 },
  B: { label: "출처 수 — 두 곳에 실렸으면 ×2, 셋이면 ×3", w: (t) => t.nTaste },
  C: { label: "출처 수 + 블루리본 리본 2개면 +1", w: (t) => t.nTaste + (t.blueRibbon === 2 ? 1 : 0) },
};

const posIdx = [];
for (let i = 0; i < N; i++) if (yAll[i]) posIdx.push(i);
for (let i = posIdx.length - 1; i > 0; i--) { const j = Math.floor(rnd() * (i + 1)); [posIdx[i], posIdx[j]] = [posIdx[j], posIdx[i]]; }
const FOLDS = 5;
const folds = Array.from({ length: FOLDS }, (_, k) => posIdx.filter((_, i) => i % FOLDS === k));
const LAMBDAS = [0.3, 0.1, 0.03, 0.01, 0.003, 0.001];
// 정답 가중도 쓸기(r3-07b)가 고른 값을 따른다. 없으면 라운드2 와 같은 ×30
let POS_W = 30;
if (fs.existsSync(sweepPath)) {
  const sw = JSON.parse(fs.readFileSync(sweepPath, "utf8"));
  POS_W = [...sw.results].sort((a, b) => b.r200 - a.r200 || a.medPct - b.medPct)[0].posW;
}

function cv(lambda, scheme) {
  const acc = { r200: 0, r500: 0, r1000: 0, medPct: 0 };
  for (let k = 0; k < FOLDS; k++) {
    const heldOut = new Set(folds[k].map((i) => rows[i].id));
    const trainPos = posIdx.filter((i) => !heldOut.has(rows[i].id));
    const idxs = [...trainPos, ...negUse];
    const X = idxs.map((i) => Xs[i]);
    const y = idxs.map((i) => (heldOut.has(rows[i].id) ? 0 : yAll[i]));
    const sw = idxs.map((i) => (yAll[i] && !heldOut.has(rows[i].id) ? scheme.w(byStore.get(rows[i].id)) : 1));
    const model = trainLogistic(X, y, lambda, { iters: 600, lr: 1.0, posWeight: POS_W, sampleWeight: sw });
    const m = rankMetrics(model, heldOut);
    acc.r200 += m.r200; acc.r500 += m.r500; acc.r1000 += m.r1000; acc.medPct += m.medPct;
  }
  return Object.fromEntries(Object.entries(acc).map(([k2, v]) => [k2, v / FOLDS]));
}

console.log(`\n── 조정용 안 ${FOLDS}겹 교차검증 · 오답 표본 ${negUse.length}곳 · 정답 가중 ×${POS_W} ──`);
console.log("무게방식\t규제λ\tCV R@200\tCV R@500\tCV R@1000\tCV 중앙%");
let best = null;
for (const [sk, scheme] of Object.entries(SCHEMES)) {
  for (const lambda of LAMBDAS) {
    const avg = cv(lambda, scheme);
    console.log(`${sk}\t${lambda}\t${avg.r200.toFixed(1)}%\t${avg.r500.toFixed(1)}%\t${avg.r1000.toFixed(1)}%\t${avg.medPct.toFixed(1)}%`);
    if (!best || avg.r200 > best.avg.r200 || (avg.r200 === best.avg.r200 && avg.medPct < best.avg.medPct)) best = { sk, scheme, lambda, avg };
  }
}
console.log(`\n교차검증이 고른 것: 무게방식 ${best.sk}(${best.scheme.label}) · λ=${best.lambda}`);
console.log(`  CV Recall@200 ${best.avg.r200.toFixed(1)}% · CV 중앙 백분위 ${best.avg.medPct.toFixed(1)}%`);

// ── 조정용 전체로 다시 맞춘다 ───────────────────────────────────────────────
const idxs = [...posIdx, ...negUse];
const sw = idxs.map((i) => (yAll[i] ? best.scheme.w(byStore.get(rows[i].id)) : 1));
const finalModel = trainLogistic(idxs.map((i) => Xs[i]), idxs.map((i) => yAll[i]), best.lambda,
  { iters: 1200, lr: 1.0, posWeight: POS_W, sampleWeight: sw });
const inSample = rankMetrics(finalModel, posIds);
console.log(`\n조정용 전체 재적합 → 조정용에서 R@200 ${inSample.r200.toFixed(1)}% · 중앙 ${inSample.medPct.toFixed(1)}% (외운 값이라 부풀려져 있다)`);

const wpairs = NAMES.map((n, j) => [n, finalModel.w[j]]);
wpairs.sort((a, b) => Math.abs(b[1]) - Math.abs(a[1]));
console.log("\n가중치 큰 순 22개 (표준화 기준. + 면 정답 쪽, − 면 오답 쪽)");
for (const [n, w] of wpairs.slice(0, 22)) console.log(`  ${w >= 0 ? "+" : ""}${w.toFixed(3)}\t${n}`);
const coastW = wpairs.filter(([n]) => n.startsWith("🌊"));
console.log("\n🌊 해안선 칸의 무게:", coastW.map(([n, w]) => `${n} ${w >= 0 ? "+" : ""}${w.toFixed(3)}`).join(" · "));

// ── 미쉐린 가격대는 정답의 강도인가 — 무게로 쓰지 않고 **진단만** 한다 ────────
console.log("\n── 🔴 미쉐린 가격대별 조정용 정답의 순위 (무게로 쓰지 않았다) ──");
const rankOf = new Map();
rows.map((r, i) => ({ id: r.id, s: score(finalModel, Xs[i]), tb: hash01(r.id) }))
  .sort((a, b) => b.s - a.s || a.tb - b.tb)
  .forEach((r, i) => rankOf.set(r.id, i + 1));
const priceGroups = new Map();
for (const t of tune.items) {
  const k = t.michelinPrice || "(미쉐린 아님)";
  if (!priceGroups.has(k)) priceGroups.set(k, []);
  priceGroups.get(k).push(rankOf.get(t.storeId));
}
console.log("가격대\t정답수\t순위 중앙값\t전체 대비 백분위");
for (const [k, v] of [...priceGroups].sort()) {
  const s = [...v].sort((a, b) => a - b);
  const med = s[Math.floor(s.length / 2)];
  console.log(`${k}\t${v.length}\t${med}\t${((med / N) * 100).toFixed(1)}%`);
}
console.log("🔴 가격대는 **비싼 정도**이지 맛의 등급이 아니다. 무게로 쓰면 비싼 집 쪽으로");
console.log("   모델을 밀게 되므로 쓰지 않았다. 리본수(1·2)는 등급이라 무게방식 C 로 시험했다.");

fs.writeFileSync(path.join(HERE, "data", "r3-model.json"), JSON.stringify({
  fittedAt: new Date().toISOString(),
  weightScheme: best.sk, weightSchemeLabel: best.scheme.label,
  lambda: best.lambda, posWeight: POS_W, negSample: NEG_SAMPLE, iters: 1200, cv: best.avg,
  cut: cutName, dim: DIM,
  names: NAMES, w: [...finalModel.w], b: finalModel.b,
  scaler: { mean: [...scaler.mean], sd: [...scaler.sd], nNumeric: scaler.nNumeric },
}, null, 1));
console.log("\n→ data/r3-model.json");
