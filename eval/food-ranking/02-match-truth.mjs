/**
 * 2단계 — 정답표(TourAPI 음식점)를 후보 풀(OSM 식음료 POI)에 붙인다.
 *
 * 왜 붙여야 하나: 후보 풀에 없는 정답은 **어떤 순위 로직으로도 절대 못 맞힌다.**
 * 그런 것을 정답표에 남겨 두면 채점이 영원히 낮게 나오고, 그 낮음이 로직 탓인지
 * 자료 탓인지 구분이 안 된다. 그래서 **붙은 것만 채점 대상**으로 삼고,
 * 못 붙은 것이 몇 건인지를 그대로 적는다.
 *
 * 붙이는 규칙 (이름 + 거리):
 *   1. TourAPI 한 건마다 반경 400m 안의 식음료 POI 를 모은다
 *   2. 이름을 정규화(공백·괄호·지점표기 제거)해서 같거나 서로 포함하면 후보
 *   3. 남은 후보 중 가장 가까운 것 하나
 * 🔴 이름이 없는 OSM POI(8.6%)에는 절대 안 붙는다 — 그건 한계로 적는다.
 *
 * 산출: data/matched.json
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { loadFoodPois, buildGrid, neighbors } from "./lib/pool.mjs";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const truth = JSON.parse(
  fs.readFileSync(path.join(HERE, "data", "truth-tourapi.json"), "utf8"),
);

const pois = loadFoodPois();
console.log(`후보 풀(OSM 부산 식음료 POI): ${pois.length}곳`);
const named = pois.filter((p) => p.tags.name);
console.log(`  그중 이름이 있는 것: ${named.length}곳`);

/** 공백·괄호·흔한 지점 표기를 지운다. "본점"·"점" 은 지우지 않는다 — 지우면 서로 다른 지점이 뭉친다. */
function norm(s) {
  return String(s)
    .replace(/\(.*?\)/g, "")
    .replace(/[\s\-·.,'"’`]/g, "")
    .toLowerCase();
}

/** 두 이름이 앞에서 몇 글자나 같은가 */
function commonPrefix(a, b) {
  let i = 0;
  while (i < a.length && i < b.length && a[i] === b[i]) i++;
  return i;
}

const grid = buildGrid(pois);
const MATCH_RADIUS_M = 400;
/**
 * 앞 3글자가 같고 150m 안이면 같은 가게로 본다.
 * 🔴 이 규칙은 **채점 점수를 보기 전에** 정했고, 이 규칙으로 새로 붙는 쌍을
 *    전부 눈으로 확인했다 (2건: 용궁해물야채쟁반짜장↔용궁해물쟁반짜장 13m,
 *    이재모피자 본점↔이재모 Pizza 22m). 점수를 보고 고른 규칙이 아니다.
 */
const PREFIX_RADIUS_M = 150;
const PREFIX_MIN = 3;

const matched = [];
const unmatched = [];
const usedPoi = new Set();

for (const t of truth.items) {
  const near = neighbors(grid, t.lat, t.lng, MATCH_RADIUS_M);
  const tn = norm(t.title);
  const cands = [];
  for (const { i, d } of near) {
    const p = pois[i];
    const nameKo = p.tags["name:ko"] ?? p.tags.name;
    if (!nameKo) continue;
    const pn = norm(nameKo);
    if (pn.length < 2 || tn.length < 2) continue;
    const same = pn === tn;
    const contain = pn.includes(tn) || tn.includes(pn);
    const prefix =
      d <= PREFIX_RADIUS_M &&
      pn.length >= PREFIX_MIN &&
      tn.length >= PREFIX_MIN &&
      commonPrefix(tn, pn) >= PREFIX_MIN;
    if (!same && !contain && !prefix) continue;
    cands.push({ i, d, exact: same });
  }
  if (!cands.length) {
    unmatched.push({ contentid: t.contentid, title: t.title, addr1: t.addr1 });
    continue;
  }
  cands.sort((a, b) => (b.exact ? 1 : 0) - (a.exact ? 1 : 0) || a.d - b.d);
  const best = cands[0];
  const poi = pois[best.i];
  if (usedPoi.has(poi.id)) continue; // 같은 가게에 두 건이 붙으면 하나만 센다
  usedPoi.add(poi.id);
  matched.push({
    contentid: t.contentid,
    title: t.title,
    poiId: poi.id,
    poiName: poi.tags["name:ko"] ?? poi.tags.name,
    kind: poi.kind,
    distM: Math.round(best.d),
    exact: best.exact,
  });
}

const out = {
  matchedAt: new Date().toISOString(),
  poolSize: pois.length,
  poolNamed: named.length,
  truthTotal: truth.items.length,
  matched: matched.length,
  matchedExact: matched.filter((m) => m.exact).length,
  unmatched: unmatched.length,
  matchRadiusM: MATCH_RADIUS_M,
  items: matched,
  unmatchedItems: unmatched,
};
fs.writeFileSync(path.join(HERE, "data", "matched.json"), JSON.stringify(out, null, 1));

console.log(
  `정답표 ${truth.items.length}건 중 ${matched.length}건이 후보 풀에 붙었다 ` +
    `(이름 완전일치 ${out.matchedExact}건). 못 붙은 것 ${unmatched.length}건.`,
);
console.log(`기저율(base rate — *아무 데나 찍었을 때 맞을 확률*): ${(matched.length / pois.length * 100).toFixed(2)}%`);
