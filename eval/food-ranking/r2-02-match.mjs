/**
 * 라운드2 2단계 — 정답표(TourAPI 부산 음식점 329건)를 새 후보 풀에 붙인다.
 *
 * 🔴 붙이는 규칙은 **라운드1 과 글자 그대로 같다.** 바꾼 것은 후보 풀뿐이다.
 *    규칙을 같이 바꾸면 "37 → ?" 의 증가가 풀 덕인지 규칙 덕인지 못 가른다.
 *      1. 반경 400m 안의 후보를 모은다
 *      2. 정규화 이름이 같거나 서로 포함하면 후보
 *      3. 150m 안이고 앞 3글자가 같아도 후보
 *      4. 완전일치 우선, 그다음 가장 가까운 것 하나
 *      5. 한 후보에는 정답 한 건만 붙는다
 *    상가정보에만 있는 지점명(예: "설빙범일점")은 상호명+지점명도 같이 본다 —
 *    이건 라운드1 에 없던 칸이라 새로 생긴 것이지 규칙 완화가 아니다.
 *
 * 산출: data/r2-matched.json
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { buildGrid, neighbors } from "./lib/pool.mjs";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const truth = JSON.parse(fs.readFileSync(path.join(HERE, "data", "truth-tourapi.json"), "utf8"));
const pool = JSON.parse(fs.readFileSync(path.join(HERE, "data", "r2-pool.json"), "utf8"));
const rows = pool.rows;
console.log(`후보 풀(상가정보 음식): ${rows.length}곳 · OSM 이 붙은 것 ${pool.osmJoined}곳`);

function norm(s) {
  return String(s ?? "")
    .replace(/\(.*?\)/g, "")
    .replace(/[\s\-·.,'"’`]/g, "")
    .toLowerCase();
}
function commonPrefix(a, b) {
  let i = 0;
  while (i < a.length && i < b.length && a[i] === b[i]) i++;
  return i;
}

const grid = buildGrid(rows.map((r) => ({ lat: r.lat, lon: r.lon })));
const MATCH_RADIUS_M = 400;
const PREFIX_RADIUS_M = 150;
const PREFIX_MIN = 3;

const matched = [];
const unmatched = [];
const used = new Set();

for (const t of truth.items) {
  const near = neighbors(grid, t.lat, t.lng, MATCH_RADIUS_M);
  const tn = norm(t.title);
  if (tn.length < 2) { unmatched.push({ contentid: t.contentid, title: t.title, why: "이름이 너무 짧다" }); continue; }
  const cands = [];
  for (const { i, d } of near) {
    const r = rows[i];
    const sn = norm(r.name);
    const snb = norm(r.name + r.branch);
    if (sn.length < 2) continue;
    const same = sn === tn || snb === tn;
    const contain = sn.includes(tn) || tn.includes(sn) || snb.includes(tn) || tn.includes(snb);
    const prefix =
      d <= PREFIX_RADIUS_M && sn.length >= PREFIX_MIN && tn.length >= PREFIX_MIN &&
      commonPrefix(tn, sn) >= PREFIX_MIN;
    if (!same && !contain && !prefix) continue;
    cands.push({ i, d, exact: same });
  }
  if (!cands.length) {
    unmatched.push({ contentid: t.contentid, title: t.title, addr1: t.addr1, near: near.length });
    continue;
  }
  cands.sort((a, b) => (b.exact ? 1 : 0) - (a.exact ? 1 : 0) || a.d - b.d);
  const best = cands[0];
  const r = rows[best.i];
  if (used.has(r.id)) continue;
  used.add(r.id);
  matched.push({
    contentid: t.contentid,
    title: t.title,
    storeId: r.id,
    storeName: r.name + (r.branch ? ` ${r.branch}` : ""),
    so: r.so,
    distM: Math.round(best.d),
    exact: best.exact,
  });
}

const out = {
  matchedAt: new Date().toISOString(),
  poolSize: rows.length,
  osmJoined: pool.osmJoined,
  truthTotal: truth.items.length,
  matched: matched.length,
  matchedExact: matched.filter((m) => m.exact).length,
  unmatched: unmatched.length,
  matchRadiusM: MATCH_RADIUS_M,
  round1Matched: 37,
  items: matched,
  unmatchedItems: unmatched,
};
fs.writeFileSync(path.join(HERE, "data", "r2-matched.json"), JSON.stringify(out, null, 1));

console.log(
  `정답표 ${truth.items.length}건 중 ${matched.length}건이 붙었다 ` +
    `(이름 완전일치 ${out.matchedExact}건). 못 붙은 것 ${unmatched.length}건. 라운드1 은 37건이었다.`,
);
console.log(`기저율(base rate — 아무 데나 찍었을 때 맞을 확률): ${((matched.length / rows.length) * 100).toFixed(3)}%`);
const bySo = new Map();
for (const m of matched) bySo.set(m.so, (bySo.get(m.so) ?? 0) + 1);
console.log("정답의 업종 소분류 상위:", [...bySo].sort((a,b)=>b[1]-a[1]).slice(0,10).map(([k,v])=>`${k}:${v}`).join(" "));
