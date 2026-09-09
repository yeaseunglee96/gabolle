/**
 * 라운드2 4단계 — 후보 53,716곳마다 신호를 굽는다.
 *
 * 🔴 정답표(TourAPI)에서 나온 값은 **하나도 넣지 않는다.** 누출(leakage — 정답을
 *    몰래 신호로 쓰는 것. 조정용에서는 잘 맞고 판정용에서 무너진다)을 막기 위해서다.
 *    여기 들어가는 값은 전부 상가정보 CSV 와 OSM 에서만 나온다.
 *
 * 라운드1 에서 죽은 신호와 살릴 신호를 갈랐다 (라운드1 진단, AUC = 어떤 신호가
 * 정답과 오답을 얼마나 잘 가르는지. 0.5면 동전던지기):
 *   · 맛집 골목 밀집 AUC 0.491 · 대중교통 접근성 AUC 0.48  → 사실상 무력. 그래도
 *     후보 풀이 18배로 바뀌었으니 **다시 잰다.** 낡은 진단을 그대로 믿지 않는다
 *   · OSM 기재 상세도 AUC 0.565 · 전화번호 AUC 0.613 → 힘은 있었다. 다만 이건
 *     맛이 아니라 **유명세**를 잰다. 그대로 두되 그 사실을 표시해 둔다
 *
 * 상가정보로 **새로 가능해진** 신호 (라운드1 에는 없던 것):
 *   · soCode/jungCode  업종 소·중분류 (예: "횟집" 대 "일반 유흥 주점")
 *   · nameCount        같은 상호가 부산에 몇 개 — 프랜차이즈 정도
 *   · soDens300/soShare300  같은 업종의 국지 밀집 (자갈치 회 골목 같은 특화 구역)
 *   · floorNum 등      층수·지하 여부 (1층 노출 대 지하·고층)
 *   · bldgFoodCount    같은 건물 안의 음식점 수 (상가건물·푸드코트인가)
 *   · roadFoodCount / bdongFoodCount  같은 도로·법정동의 음식점 수
 *   · hasBldgName/hasHo  건물명·호수 기재 (독립 건물인가 상가 한 칸인가)
 *
 * 산출: data/r2-features.json (약 20MB — 커밋하지 않는다)
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";
import { loadTransit, loadTouristAnchors, buildGrid, neighbors, nearestDistance } from "./lib/pool.mjs";
import { loadSbizPointsByDae } from "./lib/sbiz.mjs";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const pool = JSON.parse(fs.readFileSync(path.join(HERE, "data", "r2-pool.json"), "utf8"));
const rows = pool.rows;
console.log(`후보 ${rows.length}곳`);

function norm(s) {
  return String(s ?? "").replace(/\(.*?\)/g, "").replace(/[\s\-·.,'"’`]/g, "").toLowerCase();
}

// ── 집계표들 ─────────────────────────────────────────────────────────────────
const nameCount = new Map();   // 같은 상호명이 부산에 몇 개인가 (= 프랜차이즈 정도)
const bldgCount = new Map();   // 같은 건물관리번호에 음식점 몇 개
const roadCount = new Map();   // 같은 도로명코드에 음식점 몇 개
const bdongCount = new Map();  // 같은 법정동에 음식점 몇 개
for (const r of rows) {
  const n = norm(r.name);
  if (n) nameCount.set(n, (nameCount.get(n) ?? 0) + 1);
  if (r.bldgMgmt) bldgCount.set(r.bldgMgmt, (bldgCount.get(r.bldgMgmt) ?? 0) + 1);
  if (r.roadCode) roadCount.set(r.roadCode, (roadCount.get(r.roadCode) ?? 0) + 1);
  if (r.bdongCode) bdongCount.set(r.bdongCode, (bdongCount.get(r.bdongCode) ?? 0) + 1);
}

// ── 공간 신호 ────────────────────────────────────────────────────────────────
const { stops, subway } = loadTransit();
const anchors = loadTouristAnchors();
console.log(`대중교통 노드 ${stops.length} · 지하철 노드 ${subway.length} · 관광 기준점 ${anchors.length}`);

// 관광 구역 proxy — 숙박(모텔·호텔·펜션)과 예술·스포츠(관광시설) 상가의 밀집.
// 🔴 정답표에서 나온 값이 아니다. 같은 상가정보 CSV 의 다른 대분류일 뿐이다.
const extra = await loadSbizPointsByDae(new Set(["숙박", "예술·스포츠"]));
const lodging = extra.get("숙박") ?? [];
const leisure = extra.get("예술·스포츠") ?? [];
console.log(`숙박 상가 ${lodging.length} · 예술·스포츠 상가 ${leisure.length}`);
const gLodge = buildGrid(lodging);
const gLeisure = buildGrid(leisure);

const gPoi = buildGrid(rows.map((r) => ({ lat: r.lat, lon: r.lon })));
const gStop = buildGrid(stops);
const gSub = buildGrid(subway, 0.01);
const gAnchor = buildGrid(anchors, 0.01);

/** 층정보를 수로 바꾼다. "지"·"B1" 은 음수, 빈칸은 null */
function parseFloor(s) {
  const t = String(s ?? "").trim();
  if (!t) return null;
  if (/^지/.test(t) || /^b/i.test(t)) {
    const m = t.match(/\d+/);
    return m ? -Number(m[0]) : -1;
  }
  const m = t.match(/^\d+/);
  return m ? Number(m[0]) : null;
}

const out = rows.map((r, idx) => {
  const near300 = neighbors(gPoi, r.lat, r.lon, 300);
  const near100 = near300.filter((n) => n.d <= 100);
  let soNear = 0;
  for (const n of near300) if (rows[n.i].soCode === r.soCode) soNear++;
  const fl = parseFloor(r.floorInfo);
  const o = r.osm;
  return {
    id: r.id,
    idx,
    name: r.name,
    lat: r.lat,
    lon: r.lon,
    sigungu: r.sigungu,
    // 업종
    jungCode: r.jungCode,
    soCode: r.soCode,
    so: r.so,
    // 밀집
    foodDens100: Math.max(0, near100.length - 1),
    foodDens300: Math.max(0, near300.length - 1),
    soDens300: Math.max(0, soNear - 1),
    soShare300: near300.length > 1 ? (soNear - 1) / (near300.length - 1) : 0,
    // 프랜차이즈
    nameCount: nameCount.get(norm(r.name)) ?? 1,
    hasBranch: Boolean(r.branch),
    nameLen: norm(r.name).length,
    // 건물·층
    floorNum: fl,
    floorMissing: fl === null,
    isFloor1: fl === 1,
    isBasement: fl !== null && fl < 0,
    isUpper: fl !== null && fl >= 2,
    hasBldgName: Boolean(r.bldgName),
    hasHo: Boolean(r.hoInfo),
    bldgFoodCount: r.bldgMgmt ? bldgCount.get(r.bldgMgmt) ?? 1 : 1,
    daejiSan: r.daeji === "산",
    // 행정 단위
    roadFoodCount: r.roadCode ? roadCount.get(r.roadCode) ?? 1 : 1,
    bdongFoodCount: r.bdongCode ? bdongCount.get(r.bdongCode) ?? 1 : 1,
    // 공간 (OSM 지도 기반)
    transitM: Math.round(nearestDistance(gStop, r.lat, r.lon, 500)),
    subwayM: Math.round(nearestDistance(gSub, r.lat, r.lon, 1000)),
    touristM: Math.round(nearestDistance(gAnchor, r.lat, r.lon, 1000)),
    // 관광 구역 proxy (상가정보의 다른 대분류)
    lodgeDens500: neighbors(gLodge, r.lat, r.lon, 500).length,
    lodgeM: Math.round(nearestDistance(gLodge, r.lat, r.lon, 500)),
    leisureDens500: neighbors(gLeisure, r.lat, r.lon, 500).length,
    // OSM 이 이 가게를 그렸나 (🔴 맛이 아니라 유명세를 잰다)
    osmListed: Boolean(o),
    osmTagRichness: o ? o.tagRichness : 0,
    osmHasPhone: Boolean(o?.hasPhone),
    osmHasOpeningHours: Boolean(o?.hasOpeningHours),
    osmHasWebsite: Boolean(o?.hasWebsite),
  };
});

fs.writeFileSync(path.join(HERE, "data", "r2-features.json"),
  JSON.stringify({ builtAt: new Date().toISOString(), count: out.length, rows: out }));

const med = (a) => { const s = [...a].sort((x, y) => x - y); return s[Math.floor(s.length / 2)]; };
console.log(`foodDens300 중앙값 ${med(out.map(r=>r.foodDens300))} · soDens300 중앙값 ${med(out.map(r=>r.soDens300))} · ` +
  `nameCount 중앙값 ${med(out.map(r=>r.nameCount))} · transitM 중앙값 ${med(out.map(r=>r.transitM))} · ` +
  `subwayM 중앙값 ${med(out.map(r=>r.subwayM))} · touristM 중앙값 ${med(out.map(r=>r.touristM))}`);
console.log(`1층 ${out.filter(r=>r.isFloor1).length} · 지하 ${out.filter(r=>r.isBasement).length} · 2층이상 ${out.filter(r=>r.isUpper).length} · 층정보없음 ${out.filter(r=>r.floorMissing).length}`);
console.log(`→ data/r2-features.json`);
