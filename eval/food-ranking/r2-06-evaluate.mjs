/**
 * 라운드2 6단계 — 조정용 채점.
 *   node r2-06-evaluate.mjs            모든 변형을 한 표로
 *   node r2-06-evaluate.mjs --variant w10   하나만 자세히
 *
 * 🔴 판정용은 여기서 절대 안 연다. 판정은 r2-07-final.mjs 하나뿐이다.
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { buildSoPrior, makeVariants } from "./lib/r2-variants.mjs";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const feat = JSON.parse(fs.readFileSync(path.join(HERE, "data", "r2-features.json"), "utf8"));
const tune = JSON.parse(fs.readFileSync(path.join(HERE, "data", "r2-split-tune.json"), "utf8"));
const rows = feat.rows;
const N = rows.length;
const posIds = new Set(tune.items.map((i) => i.storeId));
const prior = buildSoPrior(rows, posIds);
const VARIANTS = makeVariants(prior);

function hash01(s) {
  let h = 2166136261;
  for (let i = 0; i < s.length; i++) { h ^= s.charCodeAt(i); h = Math.imul(h, 16777619); }
  return (h >>> 0) / 4294967296;
}
const KS = [50, 100, 200, 500, 1000];

function measure(fn) {
  const ranked = rows.map((r) => ({ id: r.id, s: fn(r), tb: hash01(r.id) }))
    .sort((a, b) => b.s - a.s || a.tb - b.tb);
  const ranks = [];
  ranked.forEach((r, i) => { if (posIds.has(r.id)) ranks.push(i + 1); });
  ranks.sort((a, b) => a - b);
  const n = ranks.length;
  const rec = {};
  for (const K of KS) rec[K] = (ranks.filter((x) => x <= K).length / n) * 100;
  return { n, rec, medPct: (ranks[Math.floor(n / 2)] / N) * 100 };
}

console.log(`후보 ${N}곳 · 조정용 정답 ${posIds.size}곳`);
console.log(`무작위 기대: ${KS.map((K) => `R@${K} ${((K / N) * 100).toFixed(2)}%`).join(" · ")} · 중앙 백분위 50%\n`);
console.log("변형\t" + KS.map((K) => `R@${K}`).join("\t") + "\t중앙%\t설명");
for (const [k, v] of Object.entries(VARIANTS)) {
  const m = measure(v.fn);
  console.log(`${k}\t` + KS.map((K) => m.rec[K].toFixed(1) + "%").join("\t") + `\t${m.medPct.toFixed(1)}%\t${v.label}`);
}
console.log(`\n업종 사전확률 매끄럽게 하기: 가짜 관측 ${prior.pseudo}건 · 기저율 ${(prior.base * 100).toFixed(3)}%`);
