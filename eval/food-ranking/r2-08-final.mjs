/**
 * 라운드2 8단계 — 🔴 판정. 판정용 정답표를 **딱 한 번** 연다.
 *
 * 이 파일 하나가 판정용을 한 번 읽고 네 가지를 함께 잰다:
 *   w0 제품 로직 · w1 라운드1 최종안 이식 · w18 손으로 만든 최고 · m1 최종안(로지스틱 회귀)
 * 변형마다 따로 돌리면 열람 횟수가 는다. 열람 기록은 data/r2-holdout-access.log 에 쌓인다.
 *
 * 성공 기준 (라운드1 과 같다. 바꾸지 않았다):
 *   주: Recall@200 ≥ 20%
 *   부: 순위 백분위 중앙값 ≤ 33%
 *   둘 다 넘어야 성공.
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { buildSoPrior, makeVariants } from "./lib/r2-variants.mjs";
import { buildEncoder, applyScaler } from "./lib/r2-model.mjs";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const CRITERION = { r200: 20, medPct: 33 };

const feat = JSON.parse(fs.readFileSync(path.join(HERE, "data", "r2-features.json"), "utf8"));
const tune = JSON.parse(fs.readFileSync(path.join(HERE, "data", "r2-split-tune.json"), "utf8"));
const mdl = JSON.parse(fs.readFileSync(path.join(HERE, "data", "r2-model.json"), "utf8"));
const rows = feat.rows;
const N = rows.length;

// 손으로 만든 변형이 쓰는 업종 사전확률은 **조정용**에서 굽는다 (판정용 아님)
const tunePos = new Set(tune.items.map((i) => i.storeId));
const VARIANTS = makeVariants(buildSoPrior(rows, tunePos));

// 로지스틱 회귀 점수
const enc = buildEncoder(rows);
const scaler = { mean: Float64Array.from(mdl.scaler.mean), sd: Float64Array.from(mdl.scaler.sd), nNumeric: mdl.scaler.nNumeric };
const W = Float64Array.from(mdl.w);
const lrScore = rows.map((r) => {
  const x = applyScaler(enc.encode(r), scaler);
  let z = mdl.b;
  for (let j = 0; j < x.length; j++) z += W[j] * x[j];
  return z;
});

// ── 🔴 판정용 열람 ────────────────────────────────────────────────────────────
const log = path.join(HERE, "data", "r2-holdout-access.log");
fs.appendFileSync(log, `${new Date().toISOString()}\tr2-08-final.mjs\t변형=w0,w1,w18,m1\n`);
const opened = fs.readFileSync(log, "utf8").trim().split("\n").filter(Boolean).length;
const hold = JSON.parse(fs.readFileSync(path.join(HERE, "data", "r2-split-holdout.json"), "utf8"));
const posIds = new Set(hold.items.map((i) => i.storeId));
console.log(`🔴 라운드2 판정용 열람 ${opened}번째. 이 실행 하나로 넷을 함께 잰다.\n`);

function hash01(s) { let h = 2166136261; for (let i = 0; i < s.length; i++) { h ^= s.charCodeAt(i); h = Math.imul(h, 16777619); } return (h >>> 0) / 4294967296; }
const KS = [50, 100, 200, 500, 1000];

function measure(key, scoreOf) {
  const ranked = rows.map((r, i) => ({ id: r.id, s: scoreOf(r, i), tb: hash01(r.id) }))
    .sort((a, b) => b.s - a.s || a.tb - b.tb);
  const ranks = [];
  ranked.forEach((r, i) => { if (posIds.has(r.id)) ranks.push(i + 1); });
  ranks.sort((a, b) => a - b);
  const n = ranks.length;
  const rec = {};
  for (const K of KS) rec[K] = (ranks.filter((x) => x <= K).length / n) * 100;
  return { key, n, ranks, rec, medPct: (ranks[Math.floor(n / 2)] / N) * 100 };
}

const results = [
  measure("w0 제품 로직", VARIANTS.w0.fn),
  measure("w1 라운드1 v13", VARIANTS.w1.fn),
  measure("w18 손으로 최고", VARIANTS.w18.fn),
  measure("m1 최종안(회귀)", (_r, i) => lrScore[i]),
];

console.log(`후보 ${N}곳 · 판정용 정답 ${results[0].n}곳 (씨앗 ${hold.seed})\n`);
console.log("변형\t" + KS.map((K) => `R@${K}`).join("\t") + "\t중앙 백분위");
console.log("무작위\t" + KS.map((K) => ((K / N) * 100).toFixed(2) + "%").join("\t") + "\t50.00%");
for (const r of results) {
  console.log(`${r.key}\t` + KS.map((K) => r.rec[K].toFixed(1) + "%").join("\t") + `\t${r.medPct.toFixed(1)}%`);
}

const f = results[3];
console.log(`\n최종안 정답들의 실제 순위(상위 30개만): ${f.ranks.slice(0, 30).join(", ")} … (전체 ${N} 중)`);
const passR = f.rec[200] >= CRITERION.r200;
const passM = f.medPct <= CRITERION.medPct;
console.log(`\n성공 기준 대조 (라운드1 과 같은 값. 바꾸지 않았다)`);
console.log(`  주 Recall@200 ≥ ${CRITERION.r200}%  →  ${f.rec[200].toFixed(1)}%  ${passR ? "통과" : "🔴 못 넘었다"}   (무작위 ${((200 / N) * 100).toFixed(2)}%)`);
console.log(`  부 중앙 백분위 ≤ ${CRITERION.medPct}%  →  ${f.medPct.toFixed(1)}%  ${passM ? "통과" : "🔴 못 넘었다"}   (무작위 50%)`);
console.log(`\n판정: ${passR && passM ? "성공" : "🔴 실패"}`);
console.log(`🔴 판정용은 ${f.n}곳이다. 라운드1 의 11곳보다는 낫지만 이 숫자 하나로 결론을 굳히지 않는다.`);
