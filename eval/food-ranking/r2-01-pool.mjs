/**
 * 라운드2 1단계 — 후보 풀을 **상가정보**로 갈아 끼우고, OSM 을 그 위에 덧붙인다.
 *
 * 라운드1 의 벽: 후보 풀이 OSM 부산 식음료 POI 3,039곳뿐이라 정답 329건 중 37건만
 * 붙었다. 표본이 너무 작아 어떤 판정도 무의미했다.
 *
 * 라운드2: 소상공인시장진흥공단 **상가정보**(전국 상가·업소의 상호·업종·주소·좌표를
 * 분기마다 공개하는 공공데이터) 부산 2026-06 판의 "음식" 대분류 53,716곳을 쓴다.
 *
 * OSM 은 **버리지 않는다.** 좌표+이름으로 상가정보 행에 붙여서,
 * OSM 에만 있는 것(영업시간·전화·홈페이지 기재 여부)을 칸으로 가져온다.
 * 붙는 비율이 얼마인지도 같이 적는다.
 *
 * 산출: data/r2-pool.json  (약 30MB — .gitignore 로 막는다)
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { loadSbizFood } from "./lib/sbiz.mjs";
import { loadFoodPois, buildGrid, neighbors } from "./lib/pool.mjs";

const HERE = path.dirname(fileURLToPath(import.meta.url));

/** 공백·괄호·기호를 지운다. 라운드1 의 norm() 과 같은 규칙이다. */
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

const sbiz = await loadSbizFood();
console.log(`상가정보 "음식" 대분류: ${sbiz.length}곳`);

const osm = loadFoodPois();
const osmNamed = osm.filter((p) => p.tags["name:ko"] ?? p.tags.name);
console.log(`OSM 부산 식음료 POI: ${osm.length}곳 (이름 있는 것 ${osmNamed.length})`);

// ── OSM → 상가정보 붙이기 ─────────────────────────────────────────────────────
// 규칙 (🔴 채점 점수를 보기 전에 정했다):
//   반경 120m 안 · 정규화 이름이 같거나 서로 포함하거나 앞 3글자 일치 → 같은 가게
//   한 OSM POI 는 한 상가정보 행에만 붙는다 (가장 가까운 쪽)
const JOIN_RADIUS_M = 120;
const JOIN_PREFIX_MIN = 3;

const gSbiz = buildGrid(sbiz.map((s) => ({ lat: s.lat, lon: s.lon })));
const TAGS_OF_INTEREST = [
  "name", "name:en", "cuisine", "opening_hours", "phone", "website",
  "wheelchair", "brand", "takeaway", "outdoor_seating", "addr:street",
  "internet_access", "smoking",
];

const osmAttach = new Map(); // sbiz index → osm tags
let joined = 0;
for (const p of osm) {
  const pn = norm(p.tags["name:ko"] ?? p.tags.name);
  if (pn.length < 2) continue;
  const near = neighbors(gSbiz, p.lat, p.lon, JOIN_RADIUS_M);
  const cands = [];
  for (const { i, d } of near) {
    const sn = norm(sbiz[i].name);
    const snb = norm(sbiz[i].name + sbiz[i].branch);
    if (sn.length < 2) continue;
    const same = sn === pn || snb === pn;
    const contain = sn.includes(pn) || pn.includes(sn);
    const prefix =
      sn.length >= JOIN_PREFIX_MIN && pn.length >= JOIN_PREFIX_MIN &&
      commonPrefix(sn, pn) >= JOIN_PREFIX_MIN;
    if (!same && !contain && !prefix) continue;
    cands.push({ i, d, exact: same });
  }
  if (!cands.length) continue;
  cands.sort((a, b) => (b.exact ? 1 : 0) - (a.exact ? 1 : 0) || a.d - b.d);
  const best = cands[0];
  if (osmAttach.has(best.i)) continue;
  osmAttach.set(best.i, p.tags);
  joined++;
}

const rows = sbiz.map((s, i) => {
  const t = osmAttach.get(i);
  return {
    ...s,
    osm: t
      ? {
          hasPhone: Boolean(t.phone),
          hasWebsite: Boolean(t.website),
          hasOpeningHours: Boolean(t.opening_hours),
          hasCuisine: Boolean(t.cuisine),
          hasBrand: Boolean(t.brand),
          hasNameEn: Boolean(t["name:en"]),
          tagRichness: TAGS_OF_INTEREST.filter((k) => t[k]).length,
        }
      : null,
  };
});

fs.writeFileSync(
  path.join(HERE, "data", "r2-pool.json"),
  JSON.stringify({ builtAt: new Date().toISOString(), count: rows.length, osmJoined: joined, rows }),
);

const pctJoin = ((joined / sbiz.length) * 100).toFixed(2);
const pctOsm = ((joined / osmNamed.length) * 100).toFixed(1);
console.log(`OSM 붙임: ${joined}건 — 상가정보의 ${pctJoin}% · 이름 있는 OSM POI 의 ${pctOsm}%`);
console.log(`→ data/r2-pool.json`);
