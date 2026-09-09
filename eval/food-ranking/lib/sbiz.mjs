/**
 * 소상공인시장진흥공단 **상가(상권)정보** CSV 를 읽는다.
 *
 * 상가정보 = 전국 상가·업소의 상호·업종·주소·좌표를 분기마다 공개하는 공공데이터.
 * 이 저장소에 **커밋하지 않는다** (82.8MB). 경로는 환경변수로 바꿀 수 있다.
 *
 * 🔴 읽기만 한다. 원본을 고치지 않는다.
 */
import fs from "node:fs";
import readline from "node:readline";

export const CSV_PATH =
  process.env.SBIZ_CSV ??
  "C:/Users/SSAFY/Desktop/S15P21E201/bigData/data/sbiz/부산_202606.csv";

/** 큰따옴표로 감싼 칸 안에 쉼표가 들어 있어도 안 깨지는 최소 CSV 파서 */
export function parseCsvLine(line) {
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

/** 칸 번호 — r2-00-audit.mjs 로 실측한 것 (0부터 센다) */
export const COL = {
  storeId: 0,       // 상가업소번호
  name: 1,          // 상호명
  branch: 2,        // 지점명
  daeCode: 3, dae: 4,       // 상권업종대분류
  jungCode: 5, jung: 6,     // 상권업종중분류
  soCode: 7, so: 8,         // 상권업종소분류
  ksicCode: 9, ksic: 10,    // 표준산업분류
  sidoCode: 11, sido: 12,
  sigunguCode: 13, sigungu: 14,
  hdongCode: 15, hdong: 16, // 행정동
  bdongCode: 17, bdong: 18, // 법정동
  jibunCode: 19,
  daejiCode: 20, daeji: 21, // 대지구분 (대지/산)
  bonbun: 22, bubun: 23,
  jibunAddr: 24,
  roadCode: 25, road: 26,
  bldgBon: 27, bldgBu: 28, bldgMgmt: 29,
  bldgName: 30,
  roadAddr: 31,
  zipOld: 32, zipNew: 33,
  dongInfo: 34, floorInfo: 35, hoInfo: 36,
  lon: 37, lat: 38,
};

/**
 * 음식 대분류만 골라 후보 풀로 만든다.
 * @param {(cols:string[])=>boolean} [extra] 추가 필터
 */
export async function loadSbizFood(extra) {
  const rl = readline.createInterface({
    input: fs.createReadStream(CSV_PATH, { encoding: "utf8" }),
    crlfDelay: Infinity,
  });
  const out = [];
  let first = true;
  for await (const line of rl) {
    if (!line.trim()) continue;
    const c = parseCsvLine(line);
    if (first) { first = false; continue; }
    if (c[COL.dae] !== "음식") continue;
    if (extra && !extra(c)) continue;
    const lat = Number(c[COL.lat]);
    const lon = Number(c[COL.lon]);
    if (!Number.isFinite(lat) || !Number.isFinite(lon) || lat === 0 || lon === 0) continue;
    out.push({
      id: c[COL.storeId],
      name: c[COL.name],
      branch: c[COL.branch],
      jungCode: c[COL.jungCode], jung: c[COL.jung],
      soCode: c[COL.soCode], so: c[COL.so],
      ksicCode: c[COL.ksicCode],
      sigungu: c[COL.sigungu],
      hdongCode: c[COL.hdongCode], hdong: c[COL.hdong],
      bdongCode: c[COL.bdongCode], bdong: c[COL.bdong],
      daeji: c[COL.daeji],
      roadCode: c[COL.roadCode], road: c[COL.road],
      bldgBon: c[COL.bldgBon],
      bldgMgmt: c[COL.bldgMgmt],
      bldgName: c[COL.bldgName],
      roadAddr: c[COL.roadAddr],
      jibunAddr: c[COL.jibunAddr],
      floorInfo: c[COL.floorInfo],
      hoInfo: c[COL.hoInfo],
      lat, lon,
    });
  }
  return out;
}

/** 음식이 아닌 대분류(숙박·예술·스포츠 등)의 좌표만 뽑는다 — 관광 구역을 재기 위한 것 */
export async function loadSbizPointsByDae(daeSet) {
  const rl = readline.createInterface({
    input: fs.createReadStream(CSV_PATH, { encoding: "utf8" }),
    crlfDelay: Infinity,
  });
  const out = new Map([...daeSet].map((d) => [d, []]));
  let first = true;
  for await (const line of rl) {
    if (!line.trim()) continue;
    const c = parseCsvLine(line);
    if (first) { first = false; continue; }
    const d = c[COL.dae];
    if (!daeSet.has(d)) continue;
    const lat = Number(c[COL.lat]);
    const lon = Number(c[COL.lon]);
    if (!Number.isFinite(lat) || !Number.isFinite(lon) || lat === 0 || lon === 0) continue;
    out.get(d).push({ lat, lon });
  }
  return out;
}
