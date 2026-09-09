/**
 * 라운드4 1단계 — 후보 풀을 **상가정보 ∪ OSM 합집합**으로 만든다.
 *
 * 라운드2·3 의 풀은 상가정보 "음식" 53,716곳 **뿐**이었다. OSM 은 태그(전화·영업시간
 * 기재 여부)를 얹는 데만 쓰고, **상가정보에 안 붙은 OSM 2,303곳은 버렸다.**
 *
 * 버리면 안 되는 이유는 라운드1 이 이미 증명했다 — **후보에 없는 정답은 어떤 순위
 * 로직으로도 절대 못 맞힌다.** 상가정보는 사업자 등록을 기준으로 하는 표라
 * 등록이 다르게 돼 있거나 최근에 바뀐 가게가 빠질 수 있고, OSM 은 사람이 손으로
 * 적는 지도라 그 빈칸을 일부 메운다. 둘의 빈칸이 서로 다르므로 **합집합**이 맞다.
 *
 * 🔴 합치되 **같은 가게가 둘로 세어지면 안 된다.** 두 줄로 세어지면
 *    ① 후보 수가 부풀어 백분위가 좋아 보이고 ② 정답 하나가 두 줄 중 한 줄에만
 *    붙어서 나머지 한 줄이 "오답" 으로 학습된다. 둘 다 성적을 거짓으로 만든다.
 *
 * 붙이는 규칙 (🔴 채점 점수를 보기 **전에** 정했다. 라운드2 규칙 + 구·군 강제):
 *   반경 120m 안 · 정규화 이름이 같거나 서로 포함하거나 앞 3글자 일치
 *   · 🔴 **구·군이 같아야 한다** — OSM 은 주소가 없으므로 가장 가까운 상가정보
 *     행의 구·군을 그 POI 의 구·군으로 본다 (120m 안이면 사실상 같은 동네다)
 *   한 OSM POI 는 한 상가정보 행에만 붙는다 (가장 가까운 쪽)
 *
 * 왜 구·군을 강제하나: 앞선 라운드에서 **상호만 같은 남의 가게**가 정답으로 잘못
 * 붙은 일이 5건 있었다 (미쉐린 파인다이닝 "팔레트" 에 동래구 일반 유흥 주점이
 * 붙는 식). 조용히 틀린 정답은 못 맞힌 정답보다 나쁘다 — 모델이 그것을 배운다.
 *
 * 산출: data/r4-pool.json (약 31MB — .gitignore 로 막는다)
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { loadSbizFood } from "./lib/sbiz.mjs";
import { loadFoodPois, buildGrid, neighbors } from "./lib/pool.mjs";

const HERE = path.dirname(fileURLToPath(import.meta.url));

/** 공백·괄호·기호를 지운다. 라운드1·2 의 norm() 과 같은 규칙이다. */
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

/**
 * OSM 태그 → 상가정보 소분류(`상권업종소분류명`) 대응표.
 *
 * 🔴 이것은 **추정이 아니라 대응**이다. OSM 의 `amenity=cafe` 와 상가정보의 "카페"
 *    는 같은 것을 가리키는 서로 다른 이름표라, 옮겨 적는 것뿐이다. 대응하는 이름이
 *    없는 것(`pub`·`bar`)은 상가정보에 실제로 있는 이름을 골랐다.
 *
 * 🔴 여기 없는 것은 **비워 둔다.** 지어내지 않는다 — 소분류는 라운드3 모델에서
 *    가장 무게가 큰 칸이라, 틀리게 채우면 그 칸이 통째로 거짓이 된다.
 */
const OSM_KIND_TO_SO = {
  restaurant: null, // 🔴 무슨 음식인지 모른다. cuisine 태그로도 소분류를 못 고른다
  cafe: "카페",
  fast_food: "패스트푸드",
  pub: "일반 유흥 주점",
  bar: "일반 유흥 주점",
  ice_cream: "아이스크림",
  food_court: null,
  biergarten: "일반 유흥 주점",
  "shop:bakery": "제과점",
  "shop:confectionery": "제과점",
  "shop:pastry": "제과점",
  "shop:deli": null,
  "shop:coffee": "카페",
};

const sbiz = await loadSbizFood();
console.log(`상가정보 "음식" 대분류: ${sbiz.length}곳`);

const osm = loadFoodPois();
const osmNamed = osm.filter((p) => p.tags["name:ko"] ?? p.tags.name);
console.log(`OSM 부산 식음료 POI: ${osm.length}곳 (이름 있는 것 ${osmNamed.length})`);

const JOIN_RADIUS_M = 120;
const JOIN_PREFIX_MIN = 3;
/** 구·군을 알아내려고 훑는 반경. 이 안에 상가정보가 하나도 없으면 구·군을 비워 둔다 */
const GU_LOOKUP_M = 2000;

const gSbiz = buildGrid(sbiz.map((s) => ({ lat: s.lat, lon: s.lon })));
const TAGS_OF_INTEREST = [
  "name", "name:en", "cuisine", "opening_hours", "phone", "website",
  "wheelchair", "brand", "takeaway", "outdoor_seating", "addr:street",
  "internet_access", "smoking",
];

/** OSM 태그에서 우리가 쓰는 칸만 뽑는다 — 상가정보 행에도, OSM 단독 행에도 같은 모양으로 붙인다 */
function osmSummary(t) {
  return {
    hasPhone: Boolean(t.phone),
    hasWebsite: Boolean(t.website),
    hasOpeningHours: Boolean(t.opening_hours),
    hasCuisine: Boolean(t.cuisine),
    hasBrand: Boolean(t.brand),
    hasNameEn: Boolean(t["name:en"]),
    tagRichness: TAGS_OF_INTEREST.filter((k) => t[k]).length,
  };
}

/** 가장 가까운 상가정보 행의 구·군. 못 찾으면 null */
function guOf(lat, lon) {
  const near = neighbors(gSbiz, lat, lon, GU_LOOKUP_M);
  if (!near.length) return null;
  let best = near[0];
  for (const n of near) if (n.d < best.d) best = n;
  return sbiz[best.i].sigungu ?? null;
}

// ── OSM → 상가정보 붙이기 ─────────────────────────────────────────────────────
const osmAttach = new Map(); // sbiz index → osm tags
const osmOnly = []; // 어느 상가정보 행에도 안 붙은 OSM POI
let joined = 0;
let rejectedByGu = 0;

for (const p of osm) {
  const pn = norm(p.tags["name:ko"] ?? p.tags.name);
  const pGu = guOf(p.lat, p.lon);
  if (pn.length < 2) {
    // 이름이 없거나 한 글자다. 붙일 수단이 없으니 OSM 단독 후보로 남긴다
    osmOnly.push({ p, gu: pGu });
    continue;
  }
  const near = neighbors(gSbiz, p.lat, p.lon, JOIN_RADIUS_M);
  const cands = [];
  for (const { i, d } of near) {
    // 🔴 구·군 강제 — 상호만 같은 남의 가게를 막는 첫 관문
    if (pGu && sbiz[i].sigungu && sbiz[i].sigungu !== pGu) { rejectedByGu++; continue; }
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
  if (!cands.length) { osmOnly.push({ p, gu: pGu }); continue; }
  cands.sort((a, b) => (b.exact ? 1 : 0) - (a.exact ? 1 : 0) || a.d - b.d);
  const best = cands[0];
  if (osmAttach.has(best.i)) { osmOnly.push({ p, gu: pGu }); continue; }
  osmAttach.set(best.i, p.tags);
  joined++;
}

// ── 합집합 만들기 ────────────────────────────────────────────────────────────
const rows = sbiz.map((s, i) => {
  const t = osmAttach.get(i);
  return { ...s, source: "sbiz", osm: t ? osmSummary(t) : null };
});

let osmOnlyNamed = 0;
let osmOnlySoKnown = 0;
let osmOnlyNoGu = 0;
for (const { p, gu } of osmOnly) {
  // 🔴 구·군을 못 정한 것은 **버린다.** 2km 안에 상가정보가 한 곳도 없다는 뜻이고,
  //    실제로 보면 김해·양산 쪽 — OSM 추출본의 경계상자가 부산보다 넓어서 딸려 온
  //    것들이다. 부산 추천의 후보가 아니다.
  //    게다가 주소도 구·군도 없으면 정답표에 **붙을 길이 아예 없다**(주소 단계 T1~T3
  //    도, 구·군을 강제하는 이름 단계 T4 도 못 쓴다). 남겨 두면 건초더미만 키운다.
  //    🔴 이 규칙은 점수를 한 번도 재기 전에 정했다.
  if (!gu) { osmOnlyNoGu++; continue; }
  const name = p.tags["name:ko"] ?? p.tags.name ?? "";
  if (name) osmOnlyNamed++;
  const so = OSM_KIND_TO_SO[p.kind] ?? null;
  if (so) osmOnlySoKnown++;
  rows.push({
    id: `osm:${p.id}`,
    name,
    branch: "",
    // 🔴 상가정보에서 오는 칸들은 **비운다.** OSM 에 그 값이 없다. 지어내지 않는다
    jungCode: null, jung: null,
    soCode: null, so,
    ksicCode: null,
    sigungu: gu, // 🔴 가장 가까운 상가정보 행에서 가져온 값 — 원본이 아니라 유도값이다
    hdongCode: null, hdong: null,
    bdongCode: null, bdong: null,
    daeji: null,
    roadCode: null, road: null,
    bldgBon: null, bldgMgmt: null, bldgName: null,
    roadAddr: null, jibunAddr: null,
    floorInfo: "", hoInfo: "",
    lat: p.lat, lon: p.lon,
    source: "osm",
    osmKind: p.kind,
    guDerived: true, // 이 행의 구·군은 유도값이라는 표시
    osm: osmSummary(p.tags),
  });
}

fs.writeFileSync(
  path.join(HERE, "data", "r4-pool.json"),
  JSON.stringify({
    builtAt: new Date().toISOString(),
    count: rows.length,
    sbizCount: sbiz.length,
    osmCount: osm.length,
    osmJoined: joined,
    osmOnlyCandidates: osmOnly.length - osmOnlyNoGu,
    osmOnlyDroppedNoGu: osmOnlyNoGu,
    osmOnlyNamed,
    rejectedByGu,
    joinRadiusM: JOIN_RADIUS_M,
    rows,
  }),
);

const pctJoin = ((joined / sbiz.length) * 100).toFixed(2);
const pctOsm = ((joined / osmNamed.length) * 100).toFixed(1);
console.log(`\n── 합집합 ──`);
console.log(`OSM 붙임(같은 가게로 판정): ${joined}건 — 상가정보의 ${pctJoin}% · 이름 있는 OSM POI 의 ${pctOsm}%`);
console.log(`🔴 구·군이 달라서 거른 짝: ${rejectedByGu}건`);
console.log(`OSM 단독(상가정보에 없는 것): ${osmOnly.length}곳`);
console.log(`  🔴 그중 구·군을 못 정해 **버린 것**: ${osmOnlyNoGu}곳 — 2km 안에 상가정보가 없다 = 부산 밖(김해·양산)이거나 주소를 못 붙인다`);
console.log(`  후보로 넣은 것: ${osmOnly.length - osmOnlyNoGu}곳 (이름 있는 것 ${osmOnlyNamed} · 업종 소분류를 붙인 것 ${osmOnlySoKnown})`);
console.log(`\n후보 풀: ${sbiz.length} + ${osmOnly.length - osmOnlyNoGu} = **${rows.length}곳**`);
console.log(`→ data/r4-pool.json`);
