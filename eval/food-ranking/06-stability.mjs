/**
 * 6단계 — 판정용을 열기 전에, 조정용 안에서만 **얼마나 흔들리는지** 본다.
 *
 * 두 가지를 한다.
 *  (가) 순열 검정(permutation test — *정답 자리를 무작위로 바꿔 놓고 같은 값을 몇 번이나
 *       얻는지 세는 것*). 우리 숫자가 그냥 운인지 아닌지를 본다.
 *  (나) 반쪽 나누기 — 조정용 26곳을 13/13 으로 갈라 양쪽에서 같은 값이 나오는지 본다.
 *
 * 🔴 여기서도 판정용은 열지 않는다.
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { VARIANTS } from "./lib/variants.mjs";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const feat = JSON.parse(fs.readFileSync(path.join(HERE, "data", "features.json"), "utf8"));
const tune = JSON.parse(fs.readFileSync(path.join(HERE, "data", "split-tune.json"), "utf8"));
const rows = feat.rows;
const N = rows.length;
const variantKey = process.argv[2] ?? "v13";
const fn = VARIANTS[variantKey].fn;

function hash01(s) {
  let h = 2166136261;
  for (let i = 0; i < s.length; i++) { h ^= s.charCodeAt(i); h = Math.imul(h, 16777619); }
  return (h >>> 0) / 4294967296;
}
const ranked = rows
  .map((r) => ({ id: r.id, s: fn(r), tb: hash01(r.id) }))
  .sort((a, b) => b.s - a.s || a.tb - b.tb);
const rankOf = new Map(ranked.map((r, i) => [r.id, i + 1]));

function stats(ids) {
  const rs = ids.map((id) => rankOf.get(id)).filter(Boolean).sort((a, b) => a - b);
  return {
    n: rs.length,
    medPct: (rs[Math.floor(rs.length / 2)] / N) * 100,
    r200: rs.filter((x) => x <= 200).length / rs.length * 100,
  };
}

const posIds = tune.items.map((i) => i.poiId);
const real = stats(posIds);
console.log(`${variantKey} · 조정용 ${real.n}곳 → 순위 백분위 중앙값 ${real.medPct.toFixed(1)}% · Recall@200 ${real.r200.toFixed(1)}%\n`);

// (가) 순열 검정 — 정답 26곳을 후보 중에서 무작위로 다시 뽑아 같은 값을 몇 번 넘나
const TRIALS = 20000;
let asGoodMed = 0, asGoodR200 = 0;
let seed = 12345;
const rnd = () => {
  seed |= 0; seed = (seed + 0x6d2b79f5) | 0;
  let t = Math.imul(seed ^ (seed >>> 15), 1 | seed);
  t = (t + Math.imul(t ^ (t >>> 7), 61 | t)) ^ t;
  return ((t ^ (t >>> 14)) >>> 0) / 4294967296;
};
for (let t = 0; t < TRIALS; t++) {
  const picked = [];
  const used = new Set();
  while (picked.length < real.n) {
    const i = Math.floor(rnd() * N);
    if (used.has(i)) continue;
    used.add(i);
    picked.push(rows[i].id);
  }
  const s = stats(picked);
  if (s.medPct <= real.medPct) asGoodMed++;
  if (s.r200 >= real.r200) asGoodR200++;
}
console.log(`(가) 순열 검정 ${TRIALS}회 — 무작위로 뽑은 ${real.n}곳이 우리만큼 잘 나온 비율`);
console.log(`     중앙 백분위 : p = ${(asGoodMed / TRIALS).toFixed(4)}`);
console.log(`     Recall@200  : p = ${(asGoodR200 / TRIALS).toFixed(4)}`);
console.log(`     🔴 이 p 값은 '이 변형 하나만 시험했을 때' 의 값이다. 우리는 조정용 정답을`);
console.log(`        보면서 신호와 변형을 여러 개 골랐으므로 실제 위험은 이보다 크다.\n`);

// (나) 반쪽 나누기
const half = Math.floor(posIds.length / 2);
const a = stats(posIds.slice(0, half));
const b = stats(posIds.slice(half));
console.log(`(나) 조정용을 반으로 — 같은 결론이 양쪽에서 나오나`);
console.log(`     앞 ${a.n}곳: 중앙 ${a.medPct.toFixed(1)}% · Recall@200 ${a.r200.toFixed(1)}%`);
console.log(`     뒤 ${b.n}곳: 중앙 ${b.medPct.toFixed(1)}% · Recall@200 ${b.r200.toFixed(1)}%`);
