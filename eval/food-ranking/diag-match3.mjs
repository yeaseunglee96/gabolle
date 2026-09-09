/**
 * 매칭 규칙을 한 번 더 느슨하게 했을 때 **무엇이 더 붙는지** 눈으로 본다.
 * 🔴 채점 점수를 보고 규칙을 고르는 것이 아니다. 점수를 재기 전에, 붙은 쌍이
 *    같은 가게인지만 사람이 보고 판단한다.
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { loadFoodPois, buildGrid, neighbors } from "./lib/pool.mjs";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const truth = JSON.parse(fs.readFileSync(path.join(HERE, "data", "truth-tourapi.json"), "utf8"));
const matched = JSON.parse(fs.readFileSync(path.join(HERE, "data", "matched.json"), "utf8"));
const already = new Set(matched.items.map((m) => m.contentid));
const pois = loadFoodPois();
const grid = buildGrid(pois);
const norm = (s) => String(s).replace(/\(.*?\)/g, "").replace(/[\s\-·.,'"’`]/g, "").toLowerCase();

/** 두 이름이 앞에서 몇 글자나 같은가 */
function commonPrefix(a, b) {
  let i = 0;
  while (i < a.length && i < b.length && a[i] === b[i]) i++;
  return i;
}

const extra = [];
for (const t of truth.items) {
  if (already.has(t.contentid)) continue;
  const near = neighbors(grid, t.lat, t.lng, 150);
  const tn = norm(t.title);
  if (tn.length < 3) continue;
  for (const { i, d } of near) {
    const p = pois[i];
    const n = p.tags["name:ko"] ?? p.tags.name;
    if (!n) continue;
    const pn = norm(n);
    if (pn.length < 3) continue;
    const cp = commonPrefix(tn, pn);
    if (cp >= 3) extra.push({ t: t.title, p: n, d: Math.round(d), cp });
  }
}
extra.sort((a, b) => b.cp - a.cp || a.d - b.d);
console.log(`앞 3글자 이상 같고 150m 안 — 추가로 붙을 수 있는 쌍 ${extra.length}건:`);
for (const e of extra) console.log(`  ${e.t}  ↔  ${e.p}   (${e.d}m, 앞 ${e.cp}자 일치)`);
