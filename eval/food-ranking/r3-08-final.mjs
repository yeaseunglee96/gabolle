/**
 * 라운드3 8단계 — 🔴 **판정용을 연다. 딱 한 번.**
 *
 * 열기 전에 `data/r3-preregistration.md` 에 최종안·예상 성적·기준을 적어 두었다.
 * 이 파일을 돌리면 `data/r3-holdout-access.log` 에 한 줄이 쌓인다.
 * **판정용을 본 뒤에는 모델을 고치지 않는다.** 고칠 것이 생기면 문서에 적기만 한다.
 *
 *   node --max-old-space-size=8192 r3-08-final.mjs --dry   # 조정용으로 예행 (기록 안 남김)
 *   node --max-old-space-size=8192 r3-08-final.mjs         # 🔴 진짜. 판정용을 연다
 *
 * 성공 기준 (라운드1 에서 못 박았고 이후 바꾸지 않았다):
 *   Recall@200 ≥ 20%  **그리고**  순위 백분위 중앙값 ≤ 33%
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { buildEncoder, fitScaler, applyScaler, score } from "./lib/r3-model.mjs";
import { buildEncoder as buildEncoderR2, applyScaler as applyScalerR2, score as scoreR2 } from "./lib/r2-model.mjs";
import { buildSoPrior, makeVariants } from "./lib/r3-variants.mjs";

const DRY = process.argv.includes("--dry");
const HERE = path.dirname(fileURLToPath(import.meta.url));
const D = (f) => JSON.parse(fs.readFileSync(path.join(HERE, "data", f), "utf8"));

const feat = D("r3-features.json");
const rows = feat.rows;
const N = rows.length;
const tune = D("r3-split-tune.json");
const model = D("r3-model.json");

let evalSet;
if (DRY) {
  console.log("── 예행(--dry): **조정용**으로 돌린다. 판정용은 열지 않고 기록도 안 남긴다 ──\n");
  evalSet = tune;
} else {
  const logPath = path.join(HERE, "data", "r3-holdout-access.log");
  const prev = fs.existsSync(logPath) ? fs.readFileSync(logPath, "utf8").split("\n").filter((l) => l.trim()).length : 0;
  console.log(`🔴 판정용을 연다. 이번이 ${prev + 1}번째 열람이다.\n`);
  evalSet = D("r3-split-holdout.json");
  fs.appendFileSync(logPath, `${new Date().toISOString()}\tr3-08-final.mjs\t판정용 ${evalSet.count}곳\t모델 ${model.weightScheme}/λ${model.lambda}\n`);
}
const evalIds = new Set(evalSet.items.map((i) => i.storeId));
console.log(`후보 ${N}곳 · ${evalSet.role} 정답 ${evalIds.size}곳 (씨앗 ${evalSet.seed})`);

function hash01(s) { let h = 2166136261; for (let i = 0; i < s.length; i++) { h ^= s.charCodeAt(i); h = Math.imul(h, 16777619); } return (h >>> 0) / 4294967296; }
const TB = rows.map((r) => hash01(r.id));

function metrics(scoreFn) {
  const arr = rows.map((r, i) => ({ id: r.id, s: scoreFn(r, i), tb: TB[i] }));
  arr.sort((a, b) => b.s - a.s || a.tb - b.tb);
  const ranks = [];
  arr.forEach((r, i) => { if (evalIds.has(r.id)) ranks.push(i + 1); });
  ranks.sort((a, b) => a - b);
  const n = ranks.length;
  const cnt = (k) => ranks.filter((x) => x <= k).length;
  const at = (k) => (cnt(k) / n) * 100;
  return { n, ranks, c50: cnt(50), c100: cnt(100), c200: cnt(200), c500: cnt(500), c1000: cnt(1000),
    r50: at(50), r100: at(100), r200: at(200), r500: at(500), r1000: at(1000), medPct: (ranks[Math.floor(n / 2)] / N) * 100 };
}

// ── 최종안 m3 — 조정용에서만 맞춘 로지스틱 회귀 ──────────────────────────────
const enc = buildEncoder(rows);
const Xall = rows.map((r) => enc.encode(r));
const scaler = { mean: Float64Array.from(model.scaler.mean), sd: Float64Array.from(model.scaler.sd), nNumeric: model.scaler.nNumeric };
// 🔴 r3-07-fit 이 칸을 잘라 썼으면 여기서도 똑같이 자른다. 자르는 지점은 늘 앞에서부터다
const DIM = model.dim ?? enc.dim;
const Xs = Xall.map((x) => applyScaler(x, scaler)).map((x) => (DIM === enc.dim ? x : x.subarray(0, DIM)));
if (DIM !== enc.dim) console.log(`최종안이 쓰는 칸: ${DIM}개 (${model.cut}) — 전체 ${enc.dim}개 중 앞에서부터`);
if (model.w.length !== DIM) throw new Error(`가중치 ${model.w.length}개와 칸 ${DIM}개가 안 맞는다`);
const m3 = { w: Float64Array.from(model.w), b: model.b };

// ── 대조 ① 라운드2 최종안 m1 을 그대로 이식 ─────────────────────────────────
// 🔴 후보 풀이 같아서 업종·구군 원핫 순서가 같다. 가중치를 그대로 쓸 수 있다.
const r2m = D("r2-model.json");
const encR2 = buildEncoderR2(rows);
const okR2 = encR2.names.length === r2m.names.length && encR2.names.every((n, i) => n === r2m.names[i]);
const scalerR2 = { mean: Float64Array.from(r2m.scaler.mean), sd: Float64Array.from(r2m.scaler.sd), nNumeric: r2m.scaler.nNumeric };
const m1 = { w: Float64Array.from(r2m.w), b: r2m.b };
const XsR2 = okR2 ? rows.map((r) => applyScalerR2(encR2.encode(r), scalerR2)) : null;
console.log(`라운드2 모델 이식: 입력 칸 이름이 ${okR2 ? "일치한다 — 그대로 쓴다" : "🔴 안 맞는다 — 건너뛴다"}`);

// ── 대조 ② 손조합들 ─────────────────────────────────────────────────────────
const prior = buildSoPrior(rows, new Set(tune.items.map((i) => i.storeId)));
const variants = makeVariants(prior);

const table = [];
const push = (k, label, fn) => table.push({ k, label, m: metrics(fn) });
push("무작위", "기준선 — 아무 순서나", () => 0);
push("v0", "제품 로직 그대로", variants.v0.fn);
push("v1", "🔴 라운드2 손조합 최고안 w18 이식", variants.v1.fn);
push("v8", "라운드3 최고 손조합 (숙박 많음 + 업종 사전확률)", variants.v8.fn);
push("v2", "🌊 해안선에 가까움 하나만", variants.v2.fn);
if (okR2) push("m1", "🔴 라운드2 최종안(TourAPI 로 배운 로지스틱 회귀) 이식", (r, i) => scoreR2(m1, XsR2[i]));
push("m3", "★ 라운드3 최종안 (맛 정답으로 배운 로지스틱 회귀)", (r, i) => score(m3, Xs[i]));

const rndPct = (k) => ((k / N) * 100).toFixed(2);
console.log(`\n무작위 기준선: R@50 ${rndPct(50)}% · R@100 ${rndPct(100)}% · R@200 ${rndPct(200)}% · R@500 ${rndPct(500)}% · R@1000 ${rndPct(1000)}% · 중앙 50.0%\n`);
console.log("변형\tR@50\tR@100\t**R@200**\tR@500\tR@1000\t**중앙 백분위**\t설명");
for (const t of table) {
  const m = t.m;
  console.log(`${t.k}\t${m.r50.toFixed(1)}%\t${m.r100.toFixed(1)}%\t${m.r200.toFixed(1)}%\t${m.r500.toFixed(1)}%\t${m.r1000.toFixed(1)}%\t${m.medPct.toFixed(1)}%\t${t.label}`);
}

// ── 판정 ─────────────────────────────────────────────────────────────────────
const fin = table.find((t) => t.k === "m3").m;
const passMain = fin.r200 >= 20;
const passSub = fin.medPct <= 33;
console.log(`\n═══ 판정 ═══`);
console.log(`주  기준: Recall@200 ≥ 20%  →  실측 ${fin.r200.toFixed(1)}%  ${passMain ? "통과" : "🔴 못 넘었다"}`);
console.log(`부  기준: 중앙 백분위 ≤ 33% →  실측 ${fin.medPct.toFixed(1)}%  ${passSub ? "통과" : "🔴 못 넘었다"}`);
console.log(`둘 다 넘어야 성공 → ${passMain && passSub ? "🟢 성공" : "🔴 실패"}`);
console.log(`(기준은 라운드1 에서 못 박은 값 그대로다. 결과를 보고 낮추지 않았다)`);

// ── 우연일 확률 (이항검정) ──────────────────────────────────────────────────
function binomTail(k, n, p) { // P(X >= k)
  let logFact = [0];
  for (let i = 1; i <= n; i++) logFact[i] = logFact[i - 1] + Math.log(i);
  let s = 0;
  for (let x = k; x <= n; x++) s += Math.exp(logFact[n] - logFact[x] - logFact[n - x] + x * Math.log(p) + (n - x) * Math.log(1 - p));
  return s;
}
console.log(`\n── 우연일 확률 (무작위로 찍었을 때 이만큼 맞을 확률) — 최종안 m3 ──`);
console.log("지표\t적중 / 정답수\t무작위 기대\t우연일 확률");
for (const k of [50, 100, 200, 500, 1000]) {
  const c = fin[`c${k}`];
  const p = k / N;
  console.log(`R@${k}\t${c} / ${fin.n}\t${(p * fin.n).toFixed(2)}건\t${binomTail(c, fin.n, p).toExponential(1)}`);
}
console.log(`\n최종안이 맞힌 정답의 순위 앞머리: ${fin.ranks.slice(0, 20).join(" · ")}`);

if (!DRY) {
  fs.writeFileSync(path.join(HERE, "data", "r3-final.json"), JSON.stringify({
    ranAt: new Date().toISOString(), holdoutCount: fin.n, seed: evalSet.seed,
    criteria: { r200: 20, medPct: 33 }, pass: passMain && passSub,
    results: table.map((t) => ({ k: t.k, label: t.label, r50: t.m.r50, r100: t.m.r100, r200: t.m.r200, r500: t.m.r500, r1000: t.m.r1000, medPct: t.m.medPct })),
    finalRanks: fin.ranks,
  }, null, 1));
  console.log("\n→ data/r3-final.json");
}
