/**
 * 부산 음식점 53,716곳에서 **2,000곳을 고른다.**
 *
 * 설계는 `MODEL-1-SELECTION.md` 3.6절이 권고한 **⑦ 동 바닥 5 + 업종 바닥 5 + 나머지 점수**
 * 를 그대로 구현한 것이다. 세 규칙이 순서대로 자리를 채운다.
 *
 *   1) 행정동 206개마다 그 동에서 점수 상위 k곳            (동 바닥, 기본 k=5)
 *   2) 업종 소분류 43종마다 아직 안 뽑힌 것 중 상위 k곳      (업종 바닥, 기본 k=5)
 *   3) 남은 자리를 전체 점수 상위로
 *
 * 🔴 **왜 점수 하나로 자르면 안 되나.** 실제로 잘라 보면 수영구 + 해운대구가 74.7%가 되고
 *    강서·북·사상·영도가 **0곳**, 행정동 206개 중 145개가 통째로 사라진다. 모델의 편향이
 *    그대로 부산 지도가 된다. 이 스크립트는 그 실패를 **종료 코드로 막는다** — 아래 불변식.
 *
 * ── 불변식 (하나라도 깨지면 종료 코드 1) ────────────────────────────────────────
 *   · 소멸 검사 — 뽑힌 2,000곳 안에 **행정동 206개 · 업종 43종 · 구군 16개**가
 *     전부 한 곳 이상 있어야 한다
 *   · 쏠림 검사 — **상위 두 구가 절반(50%)을 넘으면 실패**
 *
 * ── 점수는 어디서 오나 ──────────────────────────────────────────────────────────
 * 라운드3 로지스틱 회귀 모델(`r3-model.json`, 가중치 88칸)을 그대로 쓴다. 이 저장소의
 * 이 브랜치에는 OSM 추출본(PBF)이 없어서 **88칸 중 81칸만** 만든다. 못 만드는 7칸
 * (OSM 등재·태그·전화·영업시간 · 지하철거리 · 정류장거리 · 관광지거리)은 **모든 후보에
 * 같은 값(0)** 이 들어가므로 점수를 상수만큼 밀 뿐 **순위를 바꾸지 않는다.**
 * MODEL-1-SELECTION.md 3.3절이 쓴 근사와 같은 것이고, 아래 `--baseline` 출력이
 * 그 문서의 표와 맞는지 스스로 대조한다.
 *
 * ── 유령 가게 ───────────────────────────────────────────────────────────────────
 * 인허가에 붙었는데 상태가 **폐업**인 곳은 후보에서 뺀다. 이미 없어진 가게를 사람이
 * 조사하는 것은 통째로 낭비다. 🔴 인허가에 **안 붙은** 곳은 폐업인지 아닌지 모르므로
 * 건드리지 않는다. `--keep-ghosts` 로 끌 수 있다.
 *
 * 입력
 *   data/raw/poi/sbiz-poi-busan-202606.csv   상가정보 부산 2026-06 (159,689행)
 *   data/staged/place-link.ndjson            상가정보 × 인허가 연결 (유령 판정용)
 *   data/staged/r3-model.json                라운드3 모델 — 없으면 git 에서 꺼낸다
 *   data/staged/r3-coastline.json            해안선 꼭짓점 — 없으면 git 에서 꺼낸다
 * 출력
 *   data/staged/selected-2000.ndjson         한 줄에 한 곳
 *
 * 사용
 *   node process/select-2000.mjs
 *   node process/select-2000.mjs --k-dong 3 --k-cat 5      바닥 두께를 바꾼다
 *   node process/select-2000.mjs --baseline                점수만 자르기도 같이 찍는다
 */
import fs from "node:fs";
import path from "node:path";
import readline from "node:readline";
import { execFileSync } from "node:child_process";
import { fileURLToPath } from "node:url";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(HERE, "..");
const CSV = process.env.SBIZ_CSV ?? path.join(ROOT, "data/raw/poi/sbiz-poi-busan-202606.csv");
const LINK = path.join(ROOT, "data/staged/place-link.ndjson");
const STAGED = path.join(ROOT, "data/staged");
const OUT = path.join(STAGED, "selected-2000.ndjson");

// ── 인자 ─────────────────────────────────────────────────────────────────────
const argv = process.argv.slice(2);
const flag = (name) => argv.includes(name);
const num = (name, dflt) => {
  const i = argv.indexOf(name);
  if (i < 0) return dflt;
  const v = Number(argv[i + 1]);
  if (!Number.isFinite(v)) throw new Error(`${name} 뒤에 숫자가 필요합니다`);
  return v;
};
const K_DONG = num("--k-dong", 5);
const K_CAT = num("--k-cat", 5);
const TARGET = num("--n", 2000);
const KEEP_GHOSTS = flag("--keep-ghosts");
const SHOW_BASELINE = flag("--baseline");

// ── 모델과 해안선을 확보한다 ──────────────────────────────────────────────────
// 두 파일은 팀 저장소의 `bigData/dev` 브랜치에 커밋돼 있다. data/ 는 커밋하지 않으므로
// 없으면 git 에서 꺼내 온다 (사본을 또 커밋하지 않는다).
const FROM_GIT = {
  "r3-model.json": "origin/bigData/dev:eval/food-ranking/data/r3-model.json",
  "r3-coastline.json": "origin/bigData/dev:eval/food-ranking/data/r3-coastline.json",
};
function ensureFromGit(fileName) {
  const dest = path.join(STAGED, fileName);
  if (fs.existsSync(dest)) return dest;
  const ref = FROM_GIT[fileName];
  try {
    const buf = execFileSync("git", ["show", ref], { cwd: ROOT, maxBuffer: 64 << 20 });
    fs.mkdirSync(STAGED, { recursive: true });
    fs.writeFileSync(dest, buf);
    console.log(`  ${fileName} 를 git(${ref})에서 꺼냈습니다`);
    return dest;
  } catch (e) {
    console.error(
      `\n🔴 ${fileName} 이 없고 git 에서도 못 꺼냈습니다.\n` +
        `   먼저:  git fetch origin bigData/dev\n` +
        `   또는:  git show ${ref} > ${dest}\n`,
    );
    throw e;
  }
}

// ── 상가정보 CSV ─────────────────────────────────────────────────────────────
/** 큰따옴표 안에 쉼표가 있어도 안 깨지는 최소 CSV 파서 */
function parseCsvLine(line) {
  const out = [];
  let cur = "";
  let q = false;
  for (let i = 0; i < line.length; i++) {
    const ch = line[i];
    if (q) {
      if (ch === '"') {
        if (line[i + 1] === '"') { cur += '"'; i++; }
        else q = false;
      } else cur += ch;
    } else if (ch === '"') q = true;
    else if (ch === ",") { out.push(cur); cur = ""; }
    else cur += ch;
  }
  out.push(cur);
  return out;
}
/** 칸 번호 — 0부터 센다 */
const COL = {
  storeId: 0, name: 1, branch: 2,
  daeCode: 3, dae: 4, jungCode: 5, jung: 6, soCode: 7, so: 8,
  sigunguCode: 13, sigungu: 14, hdongCode: 15, hdong: 16, bdongCode: 17, bdong: 18,
  daejiCode: 20, daeji: 21,
  roadCode: 25, road: 26, bldgMgmt: 29, bldgName: 30, roadAddr: 31,
  dongInfo: 34, floorInfo: 35, hoInfo: 36, lon: 37, lat: 38,
};

/** 공백·괄호·기호를 지운다 — 라운드1~3 의 norm() 과 같은 규칙 */
function norm(s) {
  return String(s ?? "")
    .replace(/\(.*?\)/g, "")
    .replace(/[\s\-·.,'"’`]/g, "")
    .toLowerCase();
}

async function loadSbiz() {
  const rl = readline.createInterface({
    input: fs.createReadStream(CSV, { encoding: "utf8" }),
    crlfDelay: Infinity,
  });
  const food = [];
  const lodging = [];
  const leisure = [];
  let first = true;
  for await (const line of rl) {
    if (!line.trim()) continue;
    const c = parseCsvLine(line);
    if (first) { first = false; continue; }
    const lat = Number(c[COL.lat]);
    const lon = Number(c[COL.lon]);
    if (!Number.isFinite(lat) || !Number.isFinite(lon) || lat === 0 || lon === 0) continue;
    const dae = c[COL.dae];
    if (dae === "숙박") { lodging.push({ lat, lon }); continue; }
    if (dae === "예술·스포츠") { leisure.push({ lat, lon }); continue; }
    if (dae !== "음식") continue;
    food.push({
      id: c[COL.storeId], name: c[COL.name], branch: c[COL.branch],
      jungCode: c[COL.jungCode], jung: c[COL.jung],
      soCode: c[COL.soCode], so: c[COL.so],
      sigungu: c[COL.sigungu],
      hdongCode: c[COL.hdongCode], hdong: c[COL.hdong],
      bdongCode: c[COL.bdongCode], bdong: c[COL.bdong],
      daeji: c[COL.daeji],
      roadCode: c[COL.roadCode], bldgMgmt: c[COL.bldgMgmt], bldgName: c[COL.bldgName],
      roadAddr: c[COL.roadAddr], floorInfo: c[COL.floorInfo], hoInfo: c[COL.hoInfo],
      lat, lon,
    });
  }
  return { food, lodging, leisure };
}

// ── TourAPI 비식품 후보 ──────────────────────────────────────────────────────
// 🔴 음식 선정(점수 모델·불변식)은 위에서 전혀 안 건드린다. 모델이 음식 전용 신호로
// 맞춰져 있어 비식품에 그대로 못 쓴다 — 그래서 별도 규칙으로 뒤에 덧붙이기만 한다.
const TOURAPI_RAW = path.join(ROOT, "data/raw/tourapi/tourapi-busan.ndjson");
const TOURAPI_LABEL = { 12: "관광지", 14: "문화시설", 15: "행사공연축제", 28: "레포츠", 32: "숙박", 38: "쇼핑" };
/**
 * 「부산광역시 해운대구 …」에서 구·군만 뽑는다. TourAPI 가 행정동은 안 주므로 hdong 은 모르는 채로 둔다.
 * 🔴 `\b`(단어 경계) 는 한글에서 안 먹는다 — JS 정규식의 `\w` 는 영문·숫자만 쳐서 한글 뒤에서는
 * 경계로 안 잡힌다. 그래서 공백으로 토큰을 나눠 「구·군으로 끝나는 토큰」을 직접 찾는다.
 */
function guFromAddr(addr) {
  const tok = String(addr ?? "").split(/\s+/).find((s) => /(구|군)$/.test(s));
  return tok ?? null;
}
async function loadTourapiNonFood() {
  if (!fs.existsSync(TOURAPI_RAW)) {
    console.log(`  ⚠ ${TOURAPI_RAW} 이 없어 비식품 후보를 못 붙입니다`);
    return [];
  }
  const rl = readline.createInterface({
    input: fs.createReadStream(TOURAPI_RAW, { encoding: "utf8" }),
    crlfDelay: Infinity,
  });
  const byId = new Map(); // contentid → item. 한 줄 = 장소 하나가 아니라 API 응답 봉투 하나다
  let dup = 0;
  let badCoord = 0;
  for await (const line of rl) {
    if (!line.trim()) continue;
    const env = JSON.parse(line);
    if (env.stage !== "list") continue; // detail 은 같은 곳의 상세 설명이라 건너뛴다 (숙박 66줄=목록1+상세65 문제)
    if (env.contentTypeId === 39) continue; // 음식은 상가 CSV 가 이미 맡는다 — 여기서 안 섞는다
    const raw = JSON.parse(env.raw);
    let items = raw?.response?.body?.items?.item;
    if (!items) continue;
    if (!Array.isArray(items)) items = [items];
    for (const it of items) {
      const lat = Number(it.mapy); // 🔴 Number('') 는 0 이다 — isFinite 로 거르지 않으면 "너무 멀다"로 조용히 버려진다
      const lon = Number(it.mapx);
      if (!Number.isFinite(lat) || !Number.isFinite(lon) || lat === 0 || lon === 0) { badCoord++; continue; }
      if (byId.has(it.contentid)) dup++; // 같은 id 가 두 번째 나오면 로그로 남기고 뒤엣것으로 덮는다
      byId.set(it.contentid, { ...it, lat, lon, contentTypeId: env.contentTypeId });
    }
  }
  console.log(`  TourAPI 비식품 — 중복 id ${dup}건 덮음 · 좌표 없음 ${badCoord}건 뺌`);
  return [...byId.values()];
}

// ── 격자 색인 ────────────────────────────────────────────────────────────────
const R = 6371000;
function haversine(aLat, aLon, bLat, bLon) {
  const dLat = ((bLat - aLat) * Math.PI) / 180;
  const dLon = ((bLon - aLon) * Math.PI) / 180;
  const la1 = (aLat * Math.PI) / 180;
  const la2 = (bLat * Math.PI) / 180;
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(la1) * Math.cos(la2) * Math.sin(dLon / 2) ** 2;
  return 2 * R * Math.asin(Math.min(1, Math.sqrt(h)));
}
function buildGrid(points, cellDeg = 0.005) {
  const g = new Map();
  points.forEach((p, i) => {
    const k = `${Math.floor(p.lat / cellDeg)}|${Math.floor(p.lon / cellDeg)}`;
    if (!g.has(k)) g.set(k, []);
    g.get(k).push(i);
  });
  return { g, cellDeg, points };
}
function neighbors(grid, lat, lon, radiusM) {
  const { g, cellDeg, points } = grid;
  const dLat = radiusM / 111320;
  const dLon = radiusM / (111320 * Math.cos((lat * Math.PI) / 180));
  const span = Math.max(Math.ceil(dLat / cellDeg), Math.ceil(dLon / cellDeg));
  const cy = Math.floor(lat / cellDeg);
  const cx = Math.floor(lon / cellDeg);
  const out = [];
  for (let y = cy - span; y <= cy + span; y++) {
    for (let x = cx - span; x <= cx + span; x++) {
      const arr = g.get(`${y}|${x}`);
      if (!arr) continue;
      for (const i of arr) {
        const d = haversine(lat, lon, points[i].lat, points[i].lon);
        if (d <= radiusM) out.push({ i, d });
      }
    }
  }
  return out;
}
function nearestDistance(grid, lat, lon, startM = 500, maxM = 8000) {
  let r = startM;
  while (r <= maxM) {
    const n = neighbors(grid, lat, lon, r);
    if (n.length) return Math.min(...n.map((x) => x.d));
    r *= 2;
  }
  return maxM;
}

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

// ── 본체 ─────────────────────────────────────────────────────────────────────
const t0 = Date.now();
console.log("상가정보를 읽습니다 …");
const { food, lodging, leisure } = await loadSbiz();
console.log(`음식 ${food.length}곳 · 숙박 ${lodging.length} · 예술·스포츠 ${leisure.length}`);

const model = JSON.parse(fs.readFileSync(ensureFromGit("r3-model.json"), "utf8"));
const coast = JSON.parse(fs.readFileSync(ensureFromGit("r3-coastline.json"), "utf8"));
console.log(`모델 ${model.dim}칸 (${model.fittedAt}) · 해안선 꼭짓점 ${coast.count}개`);

// 집계표
const nameCount = new Map();
const bldgCount = new Map();
const roadCount = new Map();
const bdongCount = new Map();
for (const r of food) {
  const n = norm(r.name);
  if (n) nameCount.set(n, (nameCount.get(n) ?? 0) + 1);
  if (r.bldgMgmt) bldgCount.set(r.bldgMgmt, (bldgCount.get(r.bldgMgmt) ?? 0) + 1);
  if (r.roadCode) roadCount.set(r.roadCode, (roadCount.get(r.roadCode) ?? 0) + 1);
  if (r.bdongCode) bdongCount.set(r.bdongCode, (bdongCount.get(r.bdongCode) ?? 0) + 1);
}

const gFood = buildGrid(food);
const gLodge = buildGrid(lodging);
const gLeisure = buildGrid(leisure);

// 해안선 — 링을 넓혀 가며 훑고, 찾은 거리가 링 안쪽 반경보다 가까우면 멈춘다
const COAST_CELL = 0.01;
const coastGrid = new Map();
coast.points.forEach((p, i) => {
  const k = `${Math.floor(p[0] / COAST_CELL)}|${Math.floor(p[1] / COAST_CELL)}`;
  if (!coastGrid.has(k)) coastGrid.set(k, []);
  coastGrid.get(k).push(i);
});
function nearestCoastM(lat, lon) {
  const cy = Math.floor(lat / COAST_CELL);
  const cx = Math.floor(lon / COAST_CELL);
  const mPerCell = Math.min(
    COAST_CELL * 111320,
    COAST_CELL * 111320 * Math.cos((lat * Math.PI) / 180),
  );
  let best = Infinity;
  for (let ring = 0; ring <= 80; ring++) {
    for (let y = cy - ring; y <= cy + ring; y++) {
      const edgeY = y === cy - ring || y === cy + ring;
      for (let x = cx - ring; x <= cx + ring; x++) {
        if (!edgeY && x !== cx - ring && x !== cx + ring) continue;
        const arr = coastGrid.get(`${y}|${x}`);
        if (!arr) continue;
        for (const i of arr) {
          const p = coast.points[i];
          const d = haversine(lat, lon, p[0], p[1]);
          if (d < best) best = d;
        }
      }
    }
    if (best < Infinity && best <= ring * mPerCell) break;
  }
  return best;
}

console.log("신호를 굽습니다 …");
const feat = food.map((r, idx) => {
  if (idx && idx % 10000 === 0) console.log(`  ${idx} / ${food.length} …`);
  const near300 = neighbors(gFood, r.lat, r.lon, 300);
  const near100 = near300.filter((n) => n.d <= 100);
  let soNear = 0;
  for (const n of near300) if (food[n.i].soCode === r.soCode) soNear++;
  const fl = parseFloor(r.floorInfo);
  const coastM = Math.round(nearestCoastM(r.lat, r.lon));
  return {
    foodDens100: Math.max(0, near100.length - 1),
    foodDens300: Math.max(0, near300.length - 1),
    soDens300: Math.max(0, soNear - 1),
    soShare300: near300.length > 1 ? (soNear - 1) / (near300.length - 1) : 0,
    nameCount: nameCount.get(norm(r.name)) ?? 1,
    hasBranch: Boolean(r.branch),
    nameLen: norm(r.name).length,
    floorMissing: fl === null,
    isFloor1: fl === 1,
    isBasement: fl !== null && fl < 0,
    isUpper: fl !== null && fl >= 2,
    hasBldgName: Boolean(r.bldgName),
    hasHo: Boolean(r.hoInfo),
    bldgFoodCount: r.bldgMgmt ? bldgCount.get(r.bldgMgmt) ?? 1 : 1,
    daejiSan: r.daeji === "산",
    roadFoodCount: r.roadCode ? roadCount.get(r.roadCode) ?? 1 : 1,
    bdongFoodCount: r.bdongCode ? bdongCount.get(r.bdongCode) ?? 1 : 1,
    lodgeDens500: neighbors(gLodge, r.lat, r.lon, 500).length,
    lodgeM: Math.round(nearestDistance(gLodge, r.lat, r.lon, 500)),
    leisureDens500: neighbors(gLeisure, r.lat, r.lon, 500).length,
    coastM,
    isSeaside: coastM <= 300,
  };
});
console.log(`신호 ${((Date.now() - t0) / 1000).toFixed(0)}초`);

// ── 점수 ─────────────────────────────────────────────────────────────────────
// 🔴 라운드3 모델과 **칸 순서가 정확히 같아야** 한다. 모델 파일의 `names` 로 대조한다.
const l = (x) => Math.log1p(Math.max(0, x));
const NUMERIC_NAMES = [
  "밀집100m(log)", "밀집300m(log)", "같은업종밀집300m(log)", "같은업종비중300m",
  "지하철거리(log)", "정류장거리(log)", "관광지거리(log)",
  "같은건물음식점수(log)", "같은도로음식점수(log)", "같은법정동음식점수(log)",
  "같은상호개수(log)", "상호명길이", "지점명있음",
  "1층", "2층이상", "지하", "층정보없음", "건물명있음", "호수있음", "산지번",
  "OSM등재", "OSM태그촘촘함", "OSM전화", "OSM영업시간",
  "숙박밀집500m(log)", "가장가까운숙박(log)", "예술·스포츠밀집500m(log)",
  "🌊해안선거리(log)", "🌊바다300m안",
];
/** 🔴 이 브랜치에 원본이 없어서 못 만드는 칸. 모든 후보에 같은 값이라 순위를 안 바꾼다. */
const MISSING = new Set([
  "지하철거리(log)", "정류장거리(log)", "관광지거리(log)",
  "OSM등재", "OSM태그촘촘함", "OSM전화", "OSM영업시간",
]);

const soCodes = [...new Set(food.map((r) => r.soCode))].sort();
const guCodes = [...new Set(food.map((r) => r.sigungu))].sort();
const myNames = [
  ...NUMERIC_NAMES,
  ...soCodes.map((c) => `업종:${c}`),
  ...guCodes.map((c) => `구군:${c}`),
];
if (myNames.length !== model.dim || myNames.some((n, i) => n !== model.names[i])) {
  const bad = myNames.findIndex((n, i) => n !== model.names[i]);
  console.error(
    `\n🔴 모델의 칸 이름과 지금 만든 칸이 어긋납니다 — 점수가 조용히 틀립니다.\n` +
      `   내 칸 ${myNames.length}개 · 모델 ${model.dim}개 · 처음 어긋난 자리 ${bad}: ` +
      `"${myNames[bad]}" ≠ "${model.names[bad]}"\n`,
  );
  process.exit(1);
}
const soIdx = new Map(soCodes.map((c, i) => [c, i]));
const guIdx = new Map(guCodes.map((c, i) => [c, i]));
const { mean, sd, nNumeric } = model.scaler;

function scoreOf(r, f) {
  const num = [
    l(f.foodDens100), l(f.foodDens300), l(f.soDens300), f.soShare300,
    0, 0, 0,                                  // 지하철·정류장·관광지 (없다)
    l(f.bldgFoodCount), l(f.roadFoodCount), l(f.bdongFoodCount),
    l(f.nameCount), f.nameLen / 10, f.hasBranch ? 1 : 0,
    f.isFloor1 ? 1 : 0, f.isUpper ? 1 : 0, f.isBasement ? 1 : 0, f.floorMissing ? 1 : 0,
    f.hasBldgName ? 1 : 0, f.hasHo ? 1 : 0, f.daejiSan ? 1 : 0,
    0, 0, 0, 0,                               // OSM 넷 (없다)
    l(f.lodgeDens500), l(f.lodgeM), l(f.leisureDens500),
    l(f.coastM), f.isSeaside ? 1 : 0,
  ];
  let z = model.b;
  for (let j = 0; j < nNumeric; j++) {
    if (MISSING.has(NUMERIC_NAMES[j])) continue;   // 상수라 순위에 영향이 없다 — 아예 뺀다
    z += model.w[j] * ((num[j] - mean[j]) / sd[j]);
  }
  const si = soIdx.get(r.soCode);
  if (si !== undefined) z += model.w[nNumeric + si];
  const gi = guIdx.get(r.sigungu);
  if (gi !== undefined) z += model.w[nNumeric + soCodes.length + gi];
  return z;
}

const rows = food.map((r, i) => ({ ...r, f: feat[i], score: scoreOf(r, feat[i]) }));

// ── 분포를 찍는 도구 ─────────────────────────────────────────────────────────
function countBy(list, key) {
  const m = new Map();
  for (const r of list) m.set(key(r), (m.get(key(r)) ?? 0) + 1);
  return m;
}
function guTable(picked, pool) {
  const p = countBy(pool, (r) => r.sigungu);
  const s = countBy(picked, (r) => r.sigungu);
  const gus = [...p.keys()].sort((a, b) => (s.get(b) ?? 0) - (s.get(a) ?? 0));
  const w = Math.max(...gus.map((g) => g.length));
  console.log(`  ${"구·군".padEnd(w)}   모집단     뽑힘    비중`);
  for (const g of gus) {
    const n = s.get(g) ?? 0;
    const mark = n === 0 ? "  🔴 0곳" : "";
    console.log(
      `  ${g.padEnd(w)} ${String(p.get(g)).padStart(7)} ${String(n).padStart(7)}` +
        `  ${((n / picked.length) * 100).toFixed(1).padStart(5)}%${mark}`,
    );
  }
}

// ── ① 점수만 자르기 — 문서 3.3 의 표와 대조하는 자기 점검 ──────────────────────
if (SHOW_BASELINE) {
  const only = [...rows].sort((a, b) => b.score - a.score).slice(0, TARGET);
  console.log(`\n[대조용] ① 점수만 상위 ${TARGET}곳 — MODEL-1-SELECTION.md 3.3 의 표와 맞아야 한다`);
  guTable(only, rows);
  const dongs = countBy(only, (r) => r.hdongCode);
  const cats = countBy(only, (r) => r.soCode);
  const allDongs = new Set(rows.map((r) => r.hdongCode));
  const allCats = new Set(rows.map((r) => r.soCode));
  console.log(
    `  → 0곳 행정동 ${allDongs.size - dongs.size}/${allDongs.size} · ` +
      `0곳 업종 ${allCats.size - cats.size}/${allCats.size} ` +
      `(문서: 145/206 · 16/43)`,
  );
}

// ── 유령 가게를 뺀다 ─────────────────────────────────────────────────────────
let pool = rows;
let ghosts = 0;
if (!KEEP_GHOSTS) {
  const closed = new Map(); // sbizId → permit
  if (fs.existsSync(LINK)) {
    const rl = readline.createInterface({
      input: fs.createReadStream(LINK, { encoding: "utf8" }),
      crlfDelay: Infinity,
    });
    for await (const line of rl) {
      if (!line.trim()) continue;
      const r = JSON.parse(line);
      if (r.permit?.state === "폐업") closed.set(r.sbizId, r.permit);
    }
  } else {
    console.log(`  ⚠ ${LINK} 이 없어 유령 가게를 못 뺍니다 (npm run link 로 만듭니다)`);
  }
  pool = rows.filter((r) => !closed.has(r.id));
  ghosts = rows.length - pool.length;
  console.log(`\n유령 가게(인허가 폐업) ${ghosts}곳을 뺐습니다 → 후보 ${pool.length}곳`);
}

// 인허가 정보를 붙인다 (조사하는 사람에게 "언제 문을 열었나" 를 준다)
const permitOf = new Map();
if (fs.existsSync(LINK)) {
  const rl = readline.createInterface({
    input: fs.createReadStream(LINK, { encoding: "utf8" }),
    crlfDelay: Infinity,
  });
  for await (const line of rl) {
    if (!line.trim()) continue;
    const r = JSON.parse(line);
    if (r.permit) permitOf.set(r.sbizId, r.permit);
  }
}

// ── ⑦ 동 바닥 + 업종 바닥 + 점수 ────────────────────────────────────────────
const byScore = [...pool].sort((a, b) => b.score - a.score);
byScore.forEach((r, i) => { r.globalRank = i + 1; });

const picked = new Map();          // id → { row, rule, rankInGroup }
function take(r, rule, rankInGroup) {
  if (picked.has(r.id)) return false;
  if (picked.size >= TARGET) return false;
  picked.set(r.id, { row: r, rule, rankInGroup });
  return true;
}

// 1) 동 바닥 — 행정동마다 점수 상위 K_DONG
const byDong = new Map();
for (const r of byScore) {
  if (!byDong.has(r.hdongCode)) byDong.set(r.hdongCode, []);
  byDong.get(r.hdongCode).push(r);
}
let nDong = 0;
for (const [, list] of byDong) {
  for (let i = 0; i < Math.min(K_DONG, list.length); i++) {
    if (take(list[i], "dong-floor", i + 1)) nDong++;
  }
}

// 2) 업종 바닥 — 업종 소분류마다 아직 안 뽑힌 것 중 상위 K_CAT
const byCat = new Map();
for (const r of byScore) {
  if (!byCat.has(r.soCode)) byCat.set(r.soCode, []);
  byCat.get(r.soCode).push(r);
}
let nCat = 0;
for (const [, list] of byCat) {
  let got = 0;
  for (const r of list) {
    if (got >= K_CAT) break;
    if (picked.has(r.id)) { got++; continue; }   // 이미 동 바닥이 채운 자리도 한 칸으로 센다
    if (take(r, "category-floor", got + 1)) { got++; nCat++; }
  }
}

// 3) 남은 자리를 전체 점수 상위로
let nScore = 0;
for (const r of byScore) {
  if (picked.size >= TARGET) break;
  if (take(r, "score", null)) nScore++;
}

const selected = [...picked.values()];
console.log(
  `\n뽑은 규칙별 수 — 동 바닥 ${nDong} · 업종 바닥 ${nCat} · 점수 ${nScore} = ${selected.length}곳`,
);
if (selected.length < TARGET) {
  console.log(`  ⚠ 목표 ${TARGET}곳을 못 채웠습니다 (후보 ${pool.length}곳)`);
}

// ── 분포 ─────────────────────────────────────────────────────────────────────
const pickedRows = selected.map((s) => s.row);
console.log(`\n⑦ 동 바닥 ${K_DONG} + 업종 바닥 ${K_CAT} + 점수 — 구·군 분포`);
guTable(pickedRows, pool);

const dongsAll = new Map(pool.map((r) => [r.hdongCode, r.hdong]));
const catsAll = new Map(pool.map((r) => [r.soCode, r.so]));
const dongsPicked = countBy(pickedRows, (r) => r.hdongCode);
const catsPicked = countBy(pickedRows, (r) => r.soCode);
const missDongs = [...dongsAll.keys()].filter((k) => !dongsPicked.has(k));
const missCats = [...catsAll.keys()].filter((k) => !catsPicked.has(k));
const missGus = [...new Set(pool.map((r) => r.sigungu))].filter(
  (g) => !pickedRows.some((r) => r.sigungu === g),
);
console.log(
  `\n소멸 검사 — 행정동 ${dongsPicked.size}/${dongsAll.size} · ` +
    `업종 ${catsPicked.size}/${catsAll.size} · 구군 ${new Set(pickedRows.map((r) => r.sigungu)).size}/${new Set(pool.map((r) => r.sigungu)).size}`,
);

// 업종 분포 (많은 것부터)
console.log(`\n업종 소분류 — 뽑힌 수`);
const catRank = [...catsPicked.entries()].sort((a, b) => b[1] - a[1]);
console.log(
  catRank.map(([c, n]) => `${catsAll.get(c)} ${n}`).join(" · "),
);

// ── 불변식 — 판정은 종료 코드다 ──────────────────────────────────────────────
const problems = [];
if (missDongs.length)
  problems.push(
    `행정동 ${missDongs.length}개가 통째로 사라졌습니다: ` +
      missDongs.slice(0, 10).map((k) => dongsAll.get(k)).join(", ") +
      (missDongs.length > 10 ? " …" : ""),
  );
if (missCats.length)
  problems.push(
    `업종 ${missCats.length}종이 통째로 사라졌습니다: ` +
      missCats.map((k) => catsAll.get(k)).join(", "),
  );
if (missGus.length) problems.push(`구·군 ${missGus.length}개가 0곳입니다: ${missGus.join(", ")}`);

const guCounts = [...countBy(pickedRows, (r) => r.sigungu).entries()].sort((a, b) => b[1] - a[1]);
const top2 = guCounts.slice(0, 2);
const top2Share = (top2.reduce((s, x) => s + x[1], 0) / pickedRows.length) * 100;
console.log(
  `쏠림 검사 — 상위 두 구 ${top2.map((x) => `${x[0]} ${x[1]}`).join(" + ")} = ${top2Share.toFixed(1)}% (통과선 50% 이하)`,
);
if (top2Share > 50)
  problems.push(
    `상위 두 구(${top2.map((x) => x[0]).join("+")})가 ${top2Share.toFixed(1)}% 로 절반을 넘습니다`,
  );

// ── 쓴다 ─────────────────────────────────────────────────────────────────────
const RULE_LABEL = {
  "dong-floor": `행정동마다 상위 ${K_DONG}곳 — 동네가 사라지지 않게`,
  "category-floor": `업종마다 상위 ${K_CAT}곳 — 업종이 사라지지 않게`,
  score: "전체 점수 상위",
};
fs.mkdirSync(STAGED, { recursive: true });
const lines = selected.map(({ row: r, rule, rankInGroup }) => {
  const p = permitOf.get(r.id) ?? null;
  return JSON.stringify({
    id: r.id,
    name: r.name,
    branch: r.branch || null,
    roadAddr: r.roadAddr,
    gu: r.sigungu,
    hdong: r.hdong,
    bdong: r.bdong,
    category: { code: r.soCode, name: r.so, mid: r.jung },
    lon: r.lon,
    lat: r.lat,
    pick: {
      rule,
      why: RULE_LABEL[rule],
      rankInGroup,
      globalRank: r.globalRank,
      score: Number(r.score.toFixed(4)),
    },
    permit: p ? { state: p.state, openedOn: p.openedOn, category: p.category } : null,
  });
});
console.log("\nTourAPI 비식품 후보를 읽습니다 …");
const tourapiItems = await loadTourapiNonFood();
const tourapiLines = tourapiItems.map((it) =>
  JSON.stringify({
    id: `tourapi-${it.contentid}`,
    name: it.title,
    branch: null,
    roadAddr: it.addr1 || null,
    gu: guFromAddr(it.addr1),
    hdong: null, // TourAPI 는 행정동을 안 준다 — 모르는 것을 지어내지 않는다
    bdong: null,
    category: {
      code: `TOURAPI-${it.contentTypeId}`,
      name: TOURAPI_LABEL[it.contentTypeId] ?? String(it.contentTypeId),
      mid: null,
    },
    lon: it.lon,
    lat: it.lat,
    pick: {
      rule: "tourapi-nonfood",
      why: "TourAPI 비식품 갈래 — 음식 쏠림을 줄이려 덧붙인 후보 (점수 모델 밖)",
      rankInGroup: null,
      globalRank: null,
      score: null,
    },
    permit: null,
  }),
);
const tourapiByCat = countBy(tourapiItems, (it) => TOURAPI_LABEL[it.contentTypeId] ?? it.contentTypeId);
console.log(`TourAPI 비식품 후보 — 갈래별 수 (고유 contentid 기준)`);
for (const [k, v] of [...tourapiByCat.entries()].sort((a, b) => b[1] - a[1])) console.log(`  ${k} ${v}`);

const allLines = [...lines, ...tourapiLines];
fs.writeFileSync(OUT, allLines.join("\n") + "\n");
console.log(
  `\n→ ${path.relative(ROOT, OUT)} (식품 ${selected.length}곳 + 비식품 ${tourapiLines.length}곳 = ${allLines.length}줄)`,
);

if (problems.length) {
  console.error("\n🔴 불변식이 깨졌습니다 — 이 목록을 쓰면 안 됩니다");
  for (const p of problems) console.error(`   · ${p}`);
  process.exit(1);
}
console.log(`\n불변식 통과. ${((Date.now() - t0) / 1000).toFixed(0)}초`);
