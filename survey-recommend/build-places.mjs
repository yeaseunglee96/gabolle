/* 검색 색인 만드는 프로그램 (S15P21E201-754)
 *
 * 소상공인시장진흥공단 상가(상권)정보 CSV(부산, 87MB)에서 "음식" 계열
 * (음식점 · 카페 · 술집)만 뽑아 서버가 메모리에 올릴 작은 파일을 만든다.
 *
 * 🔴 이 스크립트가 하지 않는 것
 *   - 87MB CSV 자체를 저장소에 넣지 않는다 (커밋 대상이 아니다).
 *   - 나온 places.json 만 커밋한다 — 서버(server.mjs)가 시작할 때 이 파일을
 *     읽는다. CSV 는 배포 서버에도 없다.
 *
 * 쓰는 법
 *   node build-places.mjs [입력 CSV 경로] [출력 JSON 경로]
 *   기본값: 입력 = ../bigData/data/sbiz/부산_202606.csv (저장소 루트 기준)
 *           출력 = ./places.json
 */

import { createReadStream, statSync, writeFileSync } from "node:fs";
import readline from "node:readline";
import { fileURLToPath } from "node:url";
import { dirname, join, resolve } from "node:path";

const HERE = dirname(fileURLToPath(import.meta.url));
const inPath  = resolve(process.argv[2] || join(HERE, "..", "bigData", "data", "sbiz", "부산_202606.csv"));
const outPath = resolve(process.argv[3] || join(HERE, "places.json"));

/* ── 부산 경계 상자 (여유 있게) ─────────────────────────────────────
 * 다른 시·도의 좌표 오류가 섞여 들어와도 이 상자 밖이면 버린다.
 * 실제로는(2026-09-09 실측) 53,716건 전부가 이 안에 들어왔다 — 그래도
 * 다음에 CSV 가 갱신됐을 때 안전장치로 남겨 둔다. */
const BUSAN_BBOX = { latMin: 34.8, latMax: 35.45, lngMin: 128.7, lngMax: 129.35 };

/* CSV 한 줄을 필드 배열로. 큰따옴표로 감싼 칸 안의 쉼표·줄바꿈·"" 를 다룬다.
 * (이 CSV 는 필드 안에 실제 줄바꿈은 없다 — 한 레코드 = 한 줄로 실측 확인함) */
function parseCsvLine(line) {
  const fields = [];
  let cur = "";
  let inQuotes = false;
  for (let i = 0; i < line.length; i++) {
    const c = line[i];
    if (inQuotes) {
      if (c === '"') {
        if (line[i + 1] === '"') { cur += '"'; i++; }
        else inQuotes = false;
      } else cur += c;
    } else {
      if (c === '"') inQuotes = true;
      else if (c === ",") { fields.push(cur); cur = ""; }
      else cur += c;
    }
  }
  fields.push(cur);
  return fields;
}

/* 좌표는 소수 6자리(약 11cm 정밀도)면 충분하다 — 원본은 15자리까지 있는데
 * 그건 GPS 오차보다 훨씬 세밀해서 낭비다. 파일 크기를 눈에 띄게 줄인다. */
const round6 = n => Math.round(n * 1e6) / 1e6;

/* 출력 한 줄(rows[i])의 각 자리가 무엇인지 — server.mjs 가 이 순서로 읽는다 */
const FIELDS = ["id", "name", "branch", "gu", "dong", "road", "lat", "lng", "cat"];
const ROAD_PREFIX = "부산광역시 ";
let roadPrefixMismatch = 0;

async function main() {
  const st = statSync(inPath);
  console.log(`입력: ${inPath} (${(st.size / 1024 / 1024).toFixed(1)}MB)`);

  const rl = readline.createInterface({ input: createReadStream(inPath, { encoding: "utf8" }), crlfDelay: Infinity });

  let header = null;
  let idx = {};
  let totalRows = 0;
  let foodRows = 0;
  let droppedMissingCoord = 0;
  let droppedOutOfBbox = 0;
  const places = [];
  const seenIds = new Set();
  let droppedDupId = 0;

  for await (const line of rl) {
    if (!header) {
      header = parseCsvLine(line);
      const need = [
        "상가업소번호", "상호명", "지점명", "상권업종대분류명", "상권업종소분류명",
        "시군구명", "행정동명", "도로명주소", "경도", "위도"
      ];
      for (const col of need) {
        idx[col] = header.indexOf(col);
        if (idx[col] === -1) throw new Error(`CSV 에 칸이 없다: ${col} — 헤더가 바뀐 것 같다`);
      }
      continue;
    }
    if (line.trim() === "") continue;
    totalRows++;
    const f = parseCsvLine(line);

    if (f[idx["상권업종대분류명"]] !== "음식") continue;
    foodRows++;

    const id = f[idx["상가업소번호"]];
    if (seenIds.has(id)) { droppedDupId++; continue; }

    const lngRaw = f[idx["경도"]], latRaw = f[idx["위도"]];
    if (!lngRaw || !latRaw || lngRaw.trim() === "" || latRaw.trim() === "") {
      droppedMissingCoord++;
      continue;
    }
    const lng = Number(lngRaw), lat = Number(latRaw);
    if (!Number.isFinite(lng) || !Number.isFinite(lat)) { droppedMissingCoord++; continue; }
    if (lat < BUSAN_BBOX.latMin || lat > BUSAN_BBOX.latMax || lng < BUSAN_BBOX.lngMin || lng > BUSAN_BBOX.lngMax) {
      droppedOutOfBbox++;
      continue;
    }

    seenIds.add(id);
    const branch = (f[idx["지점명"]] || "").trim();
    /* 🔴 도로명주소는 전부 "부산광역시 " 로 시작한다(부산 CSV 라서 100%) —
       53,716번 반복되는 접두어라 잘라내고 서버가 응답할 때 다시 붙인다.
       접두어가 없는 줄이 하나라도 있으면 다르게 처리해야 하는데, 실측상 없다
       (아래 for 문에서 확인하며 경고를 찍는다). */
    let road = f[idx["도로명주소"]];
    if (road.startsWith(ROAD_PREFIX)) road = road.slice(ROAD_PREFIX.length);
    else roadPrefixMismatch++;

    const rec = [
      id,
      f[idx["상호명"]],
      branch,
      f[idx["시군구명"]],
      f[idx["행정동명"]],
      road,
      round6(lat),
      round6(lng),
      f[idx["상권업종소분류명"]]
    ];
    places.push(rec);
  }

  const kept = places.length;
  /* 🔴 배열의 배열(array-of-arrays)로 낸다. 사람이 보는 자리(응답 JSON)가
     아니라 색인 파일이라, 매 줄마다 "id":"name": 같은 키 문자열을 반복하지
     않는 것만으로 10.6MB → 목표 안으로 줄어든다 (아래 실측 로그 참고).
     fields 배열이 각 자리의 뜻을 말한다 — server.mjs 가 이 순서대로 읽는다. */
  const out = { fields: FIELDS, roadPrefix: ROAD_PREFIX, rows: places };
  const json = JSON.stringify(out);
  writeFileSync(outPath, json, "utf8");
  const outSize = Buffer.byteLength(json, "utf8");

  console.log(`전체 CSV 행: ${totalRows}`);
  console.log(`"음식" 대분류: ${foodRows}`);
  console.log(`버림 — 좌표 없음: ${droppedMissingCoord}`);
  console.log(`버림 — 부산 경계 밖: ${droppedOutOfBbox}`);
  console.log(`버림 — id 중복: ${droppedDupId}`);
  console.log(`도로명주소가 "${ROAD_PREFIX}" 로 안 시작한 줄: ${roadPrefixMismatch}`);
  console.log(`남은 가게 수: ${kept}`);
  console.log(`출력: ${outPath} (${(outSize / 1024 / 1024).toFixed(2)}MB)`);
  if (outSize > 8 * 1024 * 1024) {
    console.log(`🔴 목표(8MB)를 넘었다. 실제 크기를 그대로 보고한다.`);
  }
}

main().catch(e => { console.error(e); process.exit(1); });
