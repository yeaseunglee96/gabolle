/**
 * 라운드3 6단계 — 손으로 만든 변형들을 **조정용 115곳**으로만 재 본다.
 * 🔴 판정용은 열지 않는다.
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { buildSoPrior, makeVariants } from "./lib/r3-variants.mjs";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const feat = JSON.parse(fs.readFileSync(path.join(HERE, "data", "r3-features.json"), "utf8"));
const tune = JSON.parse(fs.readFileSync(path.join(HERE, "data", "r3-split-tune.json"), "utf8"));
const rows = feat.rows;
const N = rows.length;
const posIds = new Set(tune.items.map((i) => i.storeId));
console.log(`후보 ${N}곳 · 조정용 정답 ${posIds.size}곳 · 기저율 ${((posIds.size / N) * 100).toFixed(3)}%`);

const prior = buildSoPrior(rows, posIds);
const variants = makeVariants(prior);

/** 동점을 이름 해시로 갈라, 순위가 입력 순서에 안 흔들리게 한다 */
function hash01(s) {
  let h = 2166136261;
  for (let i = 0; i < s.length; i++) { h ^= s.charCodeAt(i); h = Math.imul(h, 16777619); }
  return (h >>> 0) / 4294967296;
}

function metrics(fn) {
  const ranked = rows.map((r) => ({ id: r.id, s: fn(r), tb: hash01(r.id) })).sort((a, b) => b.s - a.s || a.tb - b.tb);
  const ranks = [];
  ranked.forEach((r, i) => { if (posIds.has(r.id)) ranks.push(i + 1); });
  ranks.sort((a, b) => a - b);
  const n = ranks.length;
  const at = (k) => (ranks.filter((x) => x <= k).length / n) * 100;
  return { r50: at(50), r100: at(100), r200: at(200), r500: at(500), r1000: at(1000), medPct: (ranks[Math.floor(n / 2)] / N) * 100 };
}

const rnd = metrics(() => 0);
console.log("\n변형\tR@50\tR@100\tR@200\tR@500\tR@1000\t중앙%\t설명");
console.log(`무작위\t${((50 / N) * 100).toFixed(2)}%\t${((100 / N) * 100).toFixed(2)}%\t${((200 / N) * 100).toFixed(2)}%\t${((500 / N) * 100).toFixed(2)}%\t${((1000 / N) * 100).toFixed(2)}%\t50.0%\t기준선`);
const out = [];
for (const [k, v] of Object.entries(variants)) {
  const m = metrics(v.fn);
  out.push({ k, ...m, label: v.label });
  console.log(`${k}\t${m.r50.toFixed(1)}%\t${m.r100.toFixed(1)}%\t${m.r200.toFixed(1)}%\t${m.r500.toFixed(1)}%\t${m.r1000.toFixed(1)}%\t${m.medPct.toFixed(1)}%\t${v.label}`);
}
out.sort((a, b) => b.r200 - a.r200);
console.log(`\n조정용에서 가장 나은 손조합: ${out[0].k} — R@200 ${out[0].r200.toFixed(1)}% · 중앙 ${out[0].medPct.toFixed(1)}%`);
console.log(`  ${out[0].label}`);
fs.writeFileSync(path.join(HERE, "data", "r3-variants-tune.json"), JSON.stringify({ ranAt: new Date().toISOString(), tuneCount: posIds.size, results: out }, null, 1));
