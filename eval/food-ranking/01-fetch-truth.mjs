/**
 * 1단계 — 정답표를 합법적으로 받아온다.
 *
 * 출처: 한국관광공사 TourAPI 4.0 (KorService2) `areaBasedList2`
 *       areaCode=6 (부산광역시) · contentTypeId=39 (음식점)
 * 라이선스: 공공누리 (data.go.kr 이용약관)
 * 인증키: 팀이 이미 발급받은 DATA_GO_KR_KEY (.env). 🔴 키는 절대 커밋하지 않는다.
 *
 * 크롤링이 아니다 — 공식 오픈 API 의 정해진 오퍼레이션만 부른다.
 *
 * 산출: data/truth-tourapi.json  (키가 들어가지 않는다)
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const OUT = path.join(HERE, "data", "truth-tourapi.json");

// .env 는 이 저장소가 아니라 bigData 클론에 있다. 경로를 인자로 덮어쓸 수 있다.
const ENV_PATH =
  process.argv[2] ?? "C:/Users/SSAFY/Desktop/S15P21E201-bigdata/bigData/.env";

function readKey() {
  if (process.env.DATA_GO_KR_KEY) return process.env.DATA_GO_KR_KEY;
  if (!fs.existsSync(ENV_PATH)) {
    throw new Error(
      `인증키가 없다. 환경변수 DATA_GO_KR_KEY 를 넣거나 .env 경로를 인자로 주어라: ${ENV_PATH}`,
    );
  }
  const line = fs
    .readFileSync(ENV_PATH, "utf8")
    .split(/\r?\n/)
    .find((l) => l.startsWith("DATA_GO_KR_KEY="));
  if (!line) throw new Error(`DATA_GO_KR_KEY 가 ${ENV_PATH} 에 없다.`);
  const key = line.slice("DATA_GO_KR_KEY=".length).trim();
  if (!key) throw new Error("DATA_GO_KR_KEY 가 비어 있다.");
  return key;
}

const BASE = "https://apis.data.go.kr/B551011/KorService2";

async function page(key, pageNo, numOfRows) {
  const qs = new URLSearchParams({
    serviceKey: key,
    MobileOS: "ETC",
    MobileApp: "LocalRouteEval",
    _type: "json",
    areaCode: "6", // 부산광역시
    contentTypeId: "39", // 음식점
    numOfRows: String(numOfRows),
    pageNo: String(pageNo),
    arrange: "C", // 수정일순 — 결과 집합은 같고 순서만 정해진다
  });
  const res = await fetch(`${BASE}/areaBasedList2?${qs}`, {
    signal: AbortSignal.timeout(30000),
  });
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  const json = await res.json();
  const header = json?.response?.header;
  if (header?.resultCode !== "0000") {
    throw new Error(`TourAPI ${header?.resultCode} ${header?.resultMsg}`);
  }
  const body = json.response.body;
  const items = body?.items === "" || !body?.items ? [] : body.items.item;
  return {
    total: Number(body.totalCount),
    items: Array.isArray(items) ? items : items ? [items] : [],
  };
}

const key = readKey();
const ROWS = 100;
const first = await page(key, 1, ROWS);
const pages = Math.ceil(first.total / ROWS);
let all = [...first.items];
for (let p = 2; p <= pages; p++) {
  const r = await page(key, p, ROWS);
  all = all.concat(r.items);
  await new Promise((r2) => setTimeout(r2, 300)); // 공용 API 에 대한 예의
}

// 우리가 쓰는 칸만 남긴다. 이미지 URL·전화 등은 채점에 안 쓰므로 버린다.
const rows = all
  .map((it) => ({
    contentid: String(it.contentid),
    title: String(it.title ?? "").trim(),
    addr1: String(it.addr1 ?? "").trim(),
    sigungucode: String(it.sigungucode ?? ""),
    cat3: String(it.cat3 ?? ""),
    lclsSystm3: String(it.lclsSystm3 ?? ""),
    lng: Number(it.mapx),
    lat: Number(it.mapy),
  }))
  .filter((r) => Number.isFinite(r.lat) && Number.isFinite(r.lng) && r.title);

// contentid 중복 제거
const byId = new Map();
for (const r of rows) if (!byId.has(r.contentid)) byId.set(r.contentid, r);
const out = [...byId.values()];

fs.writeFileSync(
  OUT,
  JSON.stringify(
    {
      source: "한국관광공사 TourAPI 4.0 KorService2/areaBasedList2",
      license: "공공누리 (data.go.kr)",
      query: { areaCode: 6, contentTypeId: 39 },
      fetchedAt: new Date().toISOString(),
      totalCountReported: first.total,
      rows: out.length,
      items: out,
    },
    null,
    1,
  ),
);
console.log(`정답표 원본: TourAPI 가 알려준 총건수 ${first.total} → 저장 ${out.length}건`);
console.log(`→ ${OUT}`);
