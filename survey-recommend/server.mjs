/* 부산 추천 장소 설문 — 아주 작은 서버 (S15P21E201-754)
 *
 * 하는 일은 둘뿐이다.
 *   1. index.html 을 내준다
 *   2. POST /api/submit 로 온 응답을 표 두 개에 넣는다
 *
 * 🔴 이 서버가 일부러 안 하는 것
 *   - 접속 기록(로그)을 남기지 않는다. IP · User-Agent · Referer 를
 *     읽지도, 적지도, 데이터베이스에 넣지도 않는다.
 *     오류가 나도 요청 내용을 찍지 않는다 — 요청 안에 사람이 적은 글이 들어 있다.
 *   - 쿠키 · 세션 · 방문자 구분을 안 만든다.
 *   - 시:분:초를 안 남긴다 (표가 DATE 로 받는다 — schema.sql).
 */

import { createServer } from "node:http";
import { readFileSync } from "node:fs";
import { randomBytes } from "node:crypto";
import { fileURLToPath } from "node:url";
import { dirname, join } from "node:path";
import pg from "pg";

const HERE = dirname(fileURLToPath(import.meta.url));
const PORT = Number(process.env.PORT || 3100);
const HOST = process.env.HOST || "0.0.0.0";   /* 컨테이너 안. 바깥 노출은 compose 가 127.0.0.1 로 묶는다 */

/* nginx 가 /survey 아래에 붙여도, 3100 번을 직접 열어도 같이 돌게 한다 */
const MOUNT = process.env.MOUNT_PATH || "/survey";

const PAGE = readFileSync(join(HERE, "index.html"));

/* ── 🔴 정적 파일(사진·글꼴)을 허용 목록으로만 내준다 ────────────────
 * 예전엔 index.html 딱 한 파일만 내줬다 — 그래서 앞선 작업이 사진·글꼴을
 * base64 로 index.html 안에 통째로 넣었고, 화면 파일이 33KB 에서 3.2MB 로
 * 불었다. 이제 이 서버가 파일을 더 내주지만, 요청 경로를 그대로 파일
 * 시스템에 넘기면 "../" 로 서버의 아무 파일이나 읽어가는 공격(path
 * traversal)이 된다. 그래서 요청 경로 문자열이 아니라 "내줄 파일 이름"을
 * 코드에 미리 박아 두고, 그 목록에 있는 요청만 실제 파일로 연결한다 —
 * 목록에 없으면 무엇을 요청했든 그냥 404 다.
 *
 * 사진·글꼴은 index.html 처럼 시작할 때 한 번 읽어 메모리에 둔다 —
 * 요청마다 디스크를 다시 읽지 않는다. 안 바뀌는 파일이라
 * Cache-Control 로 브라우저·CDN 에 일주일(604800초) 캐시를 허락한다.
 * ────────────────────────────────────────────────────────────────── */
const STATIC_FILES = {
  /* 🔴 사진마다 두 판이다 (S15P21E201-882) — 좁은 창은 위(1080px), 넓은 창은
        아래(2560px). 화면이 시작할 때 한 쪽을 고른다. 열 줄 다 있어야 한다:
        빠지면 그 화면만 404 로 까맣게 뜨고, 아래 시작 검사가 그걸 잡는다. */
  "/assets/gwangan-bridge.jpg":           { file: "assets/gwangan-bridge.jpg",           type: "image/jpeg" },
  "/assets/gamcheon-village.jpg":         { file: "assets/gamcheon-village.jpg",         type: "image/jpeg" },
  "/assets/gwangalli-beach.jpg":          { file: "assets/gwangalli-beach.jpg",          type: "image/jpeg" },
  "/assets/huinnyeoul.jpg":               { file: "assets/huinnyeoul.jpg",               type: "image/jpeg" },
  "/assets/busan-night-panorama.jpg":     { file: "assets/busan-night-panorama.jpg",     type: "image/jpeg" },
  "/assets/gwangan-bridge-big.jpg":       { file: "assets/gwangan-bridge-big.jpg",       type: "image/jpeg" },
  "/assets/gamcheon-village-big.jpg":     { file: "assets/gamcheon-village-big.jpg",     type: "image/jpeg" },
  "/assets/gwangalli-beach-big.jpg":      { file: "assets/gwangalli-beach-big.jpg",      type: "image/jpeg" },
  "/assets/huinnyeoul-big.jpg":           { file: "assets/huinnyeoul-big.jpg",           type: "image/jpeg" },
  "/assets/busan-night-panorama-big.jpg": { file: "assets/busan-night-panorama-big.jpg", type: "image/jpeg" },
  "/assets/CREDITS.md":               { file: "assets/CREDITS.md",               type: "text/markdown; charset=utf-8" },
  "/fonts/PretendardVariable.woff2":  { file: "fonts/PretendardVariable.woff2",  type: "font/woff2" },
  "/fonts/LICENSE-Pretendard.txt":    { file: "fonts/LICENSE-Pretendard.txt",    type: "text/plain; charset=utf-8" }
};
/* 목록에 적힌 파일만 이때 읽는다 — 목록에 없는 파일은 이 서버가 존재조차 모른다.
   🔴 목록에 있는데 파일이 없으면 여기서 죽는다. 그게 맞다 — 502 가 뜨고
      docker logs 에 어느 파일인지 적힌다. 조용히 빈 사진을 내주는 것보다 낫다. */
for (const entry of Object.values(STATIC_FILES)) {
  entry.body = readFileSync(join(HERE, entry.file));
}

/* ── 🔴 화면이 부르는 파일과 이 목록이 어긋나 있지 않은가 (S15P21E201-881) ──
 * 2026-09-12 에 실제로 어긋났다. 웰컴 사진 한 장을 감천문화마을 사진으로
 * 바꾸면서 index.html 만 고치고 **이 목록을 안 고쳤다.** 그러면 둘이 터진다:
 *   · 지운 사진(haeundae-beach.jpg)이 목록에 남아 → 위 readFileSync 가
 *     ENOENT 로 죽고, 컨테이너가 재시작을 반복하며 **502**
 *   · 새 사진이 목록에 없어 → 브라우저가 **404**, 화면은 까맣게 뜬다
 * 배포 전에 잡은 건 Dockerfile 주석을 읽다가였다. **운이었다.**
 *
 * 그래서 시작할 때 index.html 이 부르는 assets/ · fonts/ 를 전부 뽑아
 * 이 목록과 맞춰 본다. 어긋나면 **죽지는 않고 크게 적는다** — 사진 한 장
 * 때문에 설문 전체를 못 열게 만들 이유는 없고, 로그 첫 줄에 뜨면 배포한
 * 사람이 바로 본다.
 * ─────────────────────────────────────────────────────────────────── */
const wanted = new Set(
  (PAGE.toString("utf8").match(/(?:assets|fonts)\/[A-Za-z0-9_-]+(?:\.[A-Za-z0-9]+)+/g) || [])
    .map(p => "/" + p)
);
const missing = [...wanted].filter(p => !Object.prototype.hasOwnProperty.call(STATIC_FILES, p));
const unused  = Object.keys(STATIC_FILES).filter(p => !wanted.has(p));
if (missing.length) console.error("🔴 화면이 부르는데 서버 목록에 없다 (404 가 난다): " + missing.join(" "));
if (unused.length)  console.error("🔴 서버 목록에 있는데 화면이 안 부른다 (지운 사진이 남아 있나): " + unused.join(" "));
if (!missing.length && !unused.length) console.log("정적 파일 목록과 화면이 맞는다 (" + wanted.size + "개)");

/* ── 🔴 가게 이름 검색 색인 — 시작할 때 한 번만 메모리에 올린다 (S15P21E201-754) ──
 * places.json 은 build-places.mjs 가 부산 음식점 CSV(87MB, 이 저장소 밖)에서
 * 미리 뽑아 만든 작은 파일이다(커밋됨). 여기서 다시 87MB CSV 를 읽지 않는다.
 * 배열의 배열(rows) 로 저장돼 있는 걸 검색하기 편한 객체 배열로 한 번 바꿔 둔다.
 * ────────────────────────────────────────────────────────────────── */
const placesRaw = JSON.parse(readFileSync(join(HERE, "places.json"), "utf8"));
const PLACES = placesRaw.rows.map(row => {
  const rec = {};
  placesRaw.fields.forEach((field, i) => { rec[field] = row[i]; });
  rec.road = placesRaw.roadPrefix + (rec.road || "");
  rec.branch = rec.branch || "";
  rec.nameLower = String(rec.name).toLowerCase();
  return rec;
});
const PLACE_BY_ID = new Map(PLACES.map(p => [p.id, p]));
console.log(`가게 색인 ${PLACES.length}곳 로드`);

/* 상호명에 검색어가 포함되면 맞음. 앞에서부터(접두어) 맞는 것을 먼저, 그다음 포함.
 * 🔴 사람이 보낸 글자를 정규식으로 만들지 않는다 — indexOf 만 쓴다
 *   (정규식으로 만들면 값에 따라 서버를 오래 멈추게 하는 ReDoS 위험이 있다). */
function searchPlaces(qRaw, guRaw) {
  const q = String(qRaw || "").trim().slice(0, 60);
  if (q.length < 2) return [];   /* 🔴 2자 미만이면 5만 줄을 매번 안 돈다 */
  const qLower = q.toLowerCase();
  const gu = guRaw ? String(guRaw).trim().slice(0, 20) : null;

  const starts = [];
  const contains = [];
  for (const p of PLACES) {
    if (gu && p.gu !== gu) continue;
    const at = p.nameLower.indexOf(qLower);
    if (at === -1) continue;
    (at === 0 ? starts : contains).push(p);
  }
  const byLen = (a, b) => a.name.length - b.name.length || (a.id < b.id ? -1 : 1);
  starts.sort(byLen);
  contains.sort(byLen);

  return starts.concat(contains).slice(0, 8).map(p => ({
    id: p.id, name: p.name, branch: p.branch, gu: p.gu, dong: p.dong, road: p.road, cat: p.cat
    /* 🔴 좌표(lat·lng)는 일부러 안 넣는다 — 화면은 필요 없고, 넣으면 우리 데이터가 그대로 밖으로 나간다 */
  }));
}

/* ── 짝 비교 문항 정의 — 🔴 여기에 다시 적지 않는다 (S15P21E201-754) ───
 * index.html 안의 <script id="design"> 블록을 그대로 읽어 쓴다. 그 블록을
 * 만드는 것은 design-v4.mjs 다 (손으로 고치지 않는다).
 *
 * 왜 이렇게 하나: 문항 목록을 서버에도 한 벌 적으면 두 벌이 되고, 두 벌은
 * 반드시 어긋난다. 어긋나면 사람은 화면에서 다 답하고 서버에서 거절당한다.
 * 파일이 하나라서 그 위험 자체가 없다.
 * ────────────────────────────────────────────────────────────────── */
function readDesign(html) {
  const open = '<script id="design" type="application/json">';
  const i = html.indexOf(open);
  if (i < 0) return null;
  const j = html.indexOf("<" + "/script>", i);
  if (j < 0) return null;
  try { return JSON.parse(html.slice(i + open.length, j)); } catch { return null; }
}

const DESIGN = readDesign(PAGE.toString("utf8"));
/* 🔴 문항이 없으면 뜨지 않는다. 그냥 뜨면 짝 비교 답이 전부 거절당하는데,
      화면에는 400 만 보이고 왜인지는 아무도 모른다. */
if (!DESIGN || !Array.isArray(DESIGN.variants) || DESIGN.variants.length === 0) {
  console.error("index.html 에 짝 비교 문항이 없다 — node design-v4.mjs 를 먼저 돌려 주세요.");
  process.exit(1);
}

/* ── 🔴 문항지가 **두 벌**이다 (S15P21E201-884) ────────────────────────
 * 사람마다 한 벌만 받고, 어느 벌이었는지가 제출에 designId 로 실려 온다.
 * 서버는 **그 벌의 문항 정의로만** 검사한다.
 *
 * 🔴 벌을 안 가르고 문항 이름(set_id)만 보면 정합성이 깨진다. 두 벌의
 *    eat01 은 **이름이 같고 내용이 다른 문항**이라, 섞으면 a벌 사람이 낸
 *    답에 b벌 카드 조건이 적힌다. 그 응답은 겉보기에 멀쩡하고 계수만
 *    조용히 틀어진다 — 표를 아무리 봐도 안 보인다.
 * ─────────────────────────────────────────────────────────────────── */
const VARIANTS = new Map();
for (const v of DESIGN.variants) {
  if (!v || typeof v.designId !== "string" || !Array.isArray(v.sets) || v.sets.length === 0) {
    console.error("짝 비교 문항지 한 벌이 비어 있다 — design-v4.mjs 를 다시 돌려 주세요.");
    process.exit(1);
  }
  /* 🔴 함정이 없는 벌이 있으면 뜨지 않는다. 함정이 없으면 아무거나 찍은
        사람이 "아무거나 좋아하는 사람" 으로 학습되고, 그건 결과만 봐서는
        안 보인다. **벌마다** 확인한다 — 한 벌만 보면 나머지가 샌다. */
  const trap = v.sets.find(x => x.trap) || null;
  if (!trap || (trap.trapCorrect !== 0 && trap.trapCorrect !== 1)) {
    console.error(v.designId + " 에 함정 문항이 없다 — design-v4.mjs 의 TRAP_AT 을 확인해 주세요.");
    process.exit(1);
  }
  if (VARIANTS.has(v.designId)) {
    console.error("문항지 이름이 겹친다: " + v.designId);
    process.exit(1);
  }
  /* set_id → 문항 정의. 🔴 화면이 보낸 조건값을 믿지 않고 여기서 찾아 쓴다 */
  VARIANTS.set(v.designId, { designId: v.designId, sets: v.sets, byId: new Map(v.sets.map(x => [x.setId, x])) });
}
/* 한 장에 몇 문항인가 — 표의 page_no 를 화면과 같은 규칙으로 여기서 센다 */
const PER_PAGE = DESIGN.perPage || 2;
/* 응답 시간 구간의 최댓값. 화면도 표(ms_bucket 제약)도 같은 수에서 나온다 */
const MS_BUCKET_MAX = (DESIGN.msBuckets || []).length;
console.log(`짝 비교 ${VARIANTS.size}벌 × ${DESIGN.variants[0].sets.length}문항 (${DESIGN.family})`);

/* 🔴 이 응답이 어느 판의 설문에 답했나 — schema.sql · migrations/0002 참고.
      1 = 짝 비교도 세 문항도 없던 판 · 2 = 세 문항이 붙은 판 · 3 = 짝 비교까지 ·
      4 = 네 번째 개인화 문항(북적임)까지 (migrations/0004).
      5 = 장소 칸을 열까지 늘릴 수 있는 판 (migrations/0005).
      이 숫자를 안 올리면 옛 응답과 섞여서 "북적임 응답률이 낮다" 로 잘못 읽힌다 —
      사실은 그 문항이 생기기 전에 들어온 응답이라 물어본 적이 없는 것이다.
      🔴 5 도 같다. 안 올리면 "여섯 칸 이상 적은 사람이 3%뿐" 으로 읽는데,
         사실은 나머지가 그 버튼이 생기기 전 응답이라 더 적고 싶어도 못 적었다.
      6 = 장소 추천이 **갈래 여덟 × 갈래마다 여러 곳**이 된 판 (migrations/0006).
      🔴 6 도 같다. 안 올리면 "SIGHT 로 적힌 곳이 하나도 없다 → 사람들이
         관광지를 안 추천한다" 로 읽는데, 사실은 그 갈래가 생기기 전 응답이라
         고를 수가 없었다. */
/*    7 = 짝 비교가 덩어리 셋이 된 판 (migrations/0007).
      8 = 짝 비교 카드가 위·아래에서 **좌·우**로 간 판 (migrations/0008).
          🔴 이 번호가 top_was / left_was 의 뜻을 가른다. 7 이하 행의 0/1 은
             "위에 있던 쪽" 이고 8 이상은 "왼쪽에 있던 쪽" 이다. 안 올리면
             두 판을 합쳐 위치 편향을 뺄 때 방향이 섞인다. */
/* 🔴 9 로 올렸다 (S15P21E201-884). 저장되는 모양은 안 바뀌었지만 **문항이
      통째로 바뀌었다** — 카드가 다섯 칸에서 네 칸이 되었고(비슷한 가게를
      뺐다) 문항지가 두 벌이 됐다. 나중에 응답을 합칠 때 8 이하와 9 이상을
      섞으면 안 된다: 같은 이름의 문항(eat01)이 다른 질문이기 때문이다.
      어느 벌이었는지는 pairwise_design_id 가 따로 말해 준다. */
const FORM_VERSION = 9;

const pool = new pg.Pool({
  connectionString: process.env.DATABASE_URL,
  max: 4,
  idleTimeoutMillis: 30_000,
  connectionTimeoutMillis: 5_000
});

/* ── 🔴 개인정보가 섞였는지 보는 자리 ────────────────────────────────
 * 화면(index.html 의 PII)과 똑같은 검사를 여기서 한 번 더 한다.
 * 화면만 있으면 주소창으로 직접 보내는 것을 못 막는다.
 * 두 벌이 어긋나면 사람은 화면에서 통과하고 서버에서 거절당한다 —
 * 고칠 때 반드시 양쪽을 같이 고친다.
 * ────────────────────────────────────────────────────────────────── */
const PII = [
  { name: "전화번호처럼 보이는 숫자", re: /0\d{1,2}[-.\s]?\d{3,4}[-.\s]?\d{4}/ },
  { name: "이메일 주소(@)", re: /@/ },
  { name: "8자리 넘게 이어진 숫자", re: /\d{8,}/ }
];
const piiHit = s => { for (const p of PII) if (p.re.test(s)) return p.name; return null; };

/* ── 🔴 전화번호 칸만 PII 검사의 예외다 (S15P21E201-754) ─────────────
 * 경품 추첨용으로 일부러 받는 칸이라, 위 PII 검사(자유 입력 칸용)를 여기엔
 * 안 돌린다. 대신 "숫자·하이픈을 받고, 정규화하면 한국 휴대전화 번호 모양인가"
 * 만 본다. index.html 의 PHONE_RE 와 한 글자도 다르지 않게 맞춘다.
 * 비어 있으면(제출 안 하면) 검사를 건너뛴다 — 선택 입력이라서다.
 * ────────────────────────────────────────────────────────────────── */
const PHONE_RE = /^01[016789][0-9]{7,8}$/;
const normalizePhone = s => String(s).replace(/[^0-9]/g, "");

/* ── 받아들이는 값 — 화면의 목록과 같아야 한다 ───────────────────── */
/* 🔴 다섯 칸 → 세 칸 (S15P21E201-754, 팀원 피드백). 옛 값 UNDER_20 ·
      AGE_20_24 · AGE_25_29 · AGE_30_34 · AGE_35_PLUS 는 이제 전부 거절된다.
      🔴 빈 구멍: 20세 미만 · 80세 이상은 이 세 칸에 안 들어간다. 나이대가
      필수 입력이라 그 나이의 응답자는 못 낸다 — index.html AGE 배열 주석 참고. */
const AGE_BANDS   = new Set(["AGE_20_39", "AGE_40_59", "AGE_60_79"]);
/* 🔴 'NEVER' → 'VISITED_ONLY', 구간 경계 10/3/1 → 20/10/5 (S15P21E201-754).
      OVER_10Y→OVER_20Y · Y_3_10→Y_10_20 · Y_1_3→Y_5_10 · UNDER_1Y→UNDER_5Y.
      옛 값은 이제 전부 거절된다 — 값 이관은 필요 없었다 (그때까지 0행). */
const BUSAN_YEARS = new Set(["BORN_HERE", "OVER_20Y", "Y_10_20", "Y_5_10", "UNDER_5Y", "VISITED_ONLY"]);
/* 🔴 일곱 → 여덟 (S15P21E201-754). 새로 생긴 것은 SIGHT 하나뿐이고 나머지
      일곱은 코드값 그대로다 — 화면의 갈래 이름만 바뀌었다.
      🔴 SIGHT = "볼거리 · 명소"(sightseeing 의 그 sight). 「관광 · 명소 · 경치」가
         담는 것이 정확히 그것이다 — 보러 가는 곳. TOUR 는 여행 전체라 너무 넓고,
         VIEW 는 경치만이라 감천문화마을이 안 들어가고, LANDMARK 는 대표 건물만
         가리켜 전망 · 야경이 빠진다. 왜 이 갈래가 필요했는지는
         migrations/0006_add_sight_type.sql 과 README.md 2절에 있다.
      🔴 이 목록은 index.html 의 TYPES · schema.sql · migrations/0006 의
         recommendation_place_type_ok 와 같아야 한다. 넷이 어긋나면 사람은
         화면에서 통과하고 여기서, 또는 여기서 통과하고 DB 에서 거절당한다. */
const PLACE_TYPES = new Set(["SIGHT", "FOOD", "CAFE", "NATURE", "CULTURE", "MARKET", "ACTIVITY", "BAR"]);
const WHEN_GOOD   = new Set(["DAY", "NIGHT", "ANY"]);
/* 🔴 NEED 는 **반드시 채워야 하는 곳 수**(전체 하한), MAX_SLOTS 는 **넣을 수
      있는 상한**이다. 둘 다 **응답 하나 전체**를 세는 숫자다.
      🔴 갈래별 상한은 없다 (S15P21E201-754). 맛집을 다섯 곳 아는 사람이 다섯
         곳 다 「로컬 음식 · 맛집」 갈래에 적을 수 있어야 한다 — 그 답이 우리가
         가장 원하는 답이다. 그래서 여기서도, DB 에서도 유형별로 안 센다.
      🔴 이 두 숫자는 index.html 의 NEED · MAX_SLOTS 와 같아야 하고, MAX_SLOTS 는
         schema.sql · migrations/0005_more_slots.sql 의 recommendation_slot_ok
         상한(10)과도 같아야 한다. 어긋나면 사람은 화면에서 통과하고 여기서,
         또는 여기서 통과하고 DB 에서 거절당한다. */
/* 🔴 하한 다섯 → 셋 (2026-09-12, S15P21E201-870). index.html 의 NEED 와
      반드시 같아야 한다 — 한쪽만 고치면 사람이 화면에서 통과하고 여기서
      거절당한다. DB 제약은 상한만 보므로 마이그레이션이 없다. */
const NEED = 3;
const MAX_SLOTS = 10;

/* ── 맨 앞의 개인화 세 문항 (오는 교통 · 숙소 · 식사) ────────────────
 * 코드값은 docs/COLDSTART-THREE-QUESTIONS.md 3.2 의 "안 B — 눈금형" 이다.
 * 등급형(LOW/MID/HIGH)이 아니라 눈금형을 쓰기로 사람이 정했다.
 *
 * 🔴 여기가 유일한 방벽이다. DB 는 JSONB 안쪽 글자를 안 막는다.
 *    화면(index.html 의 SPEND_QUESTIONS)과 이 목록은 같아야 한다.
 *    한쪽만 고치면 사람은 화면에서 통과하고 서버에서 거절당한다.
 * ────────────────────────────────────────────────────────────────── */
const SPEND_CHOICES = {
  transport: new Set(["PRICE_FIRST", "TIME_AND_PRICE", "COMFORT_FIRST"]),
  stay:      new Set(["KRW_UNDER_50K", "KRW_50K_120K", "KRW_120K_250K", "KRW_OVER_250K"]),
  /* VARIES("그날그날 달라요")는 건너뛴 것이 아니라 답이다 —
     "가격대를 고정하지 않는 사람" 이라는 정보가 있어서 저장은 하고 계산에서만 뺀다. */
  meal:      new Set(["EVERYDAY_LOCAL", "KNOWN_IN_AREA", "SPECIAL_BOOKED", "VARIES"])
};
const SPEND_LABEL = { transport: "오는 교통", stay: "숙소", meal: "식사" };

/* 들어온 세 문항 답을 읽는다. 🔴 셋을 가른다 —
 *   null     : spendProfile 이 아예 없다 = 아직 안 물어봤다 (표에서도 NULL)
 *   SKIPPED  : 물어봤는데 하나도 안 골랐다 ({} 로 온다)
 *   SELECTED : 하나 이상 골랐다
 * 건너뛴 키는 null 이 아니라 아예 없다.
 * 상태는 클라이언트가 보낸 것을 믿지 않고 여기서 정한다. */
function readSpendProfile(v) {
  if (v === undefined) return { status: null, value: null };
  if (v === null || typeof v !== "object" || Array.isArray(v)) {
    return { error: "여행 스타일 세 문항의 답을 읽지 못했어요." };
  }
  const picked = Object.create(null);
  for (const key of Object.keys(v)) {
    /* 🔴 hasOwn 으로 본다. SPEND_CHOICES["__proto__"] 는 목록에 없는데도
          Object.prototype 이 나와서 "있다" 로 읽힌다. 그러면 그 다음 줄이
          터지고 요청이 답 없이 매달린다. */
    const allowed = Object.hasOwn(SPEND_CHOICES, key) ? SPEND_CHOICES[key] : null;
    /* 🔴 모르는 키 이름을 메시지에 그대로 되돌려 주지 않는다.
          이 서버는 사람이 적은 글을 어디에도 다시 내보내지 않는다. */
    if (!allowed) return { error: "여행 스타일 답에 모르는 항목이 있어요." };
    const code = v[key];
    if (typeof code !== "string" || !allowed.has(code)) {
      return { error: `여행 스타일의 “${SPEND_LABEL[key]}” 답이 목록에 없는 값이에요.` };
    }
    picked[key] = code;
  }
  return Object.keys(picked).length === 0
    ? { status: "SKIPPED",  value: null }
    : { status: "SELECTED", value: picked };
}

/* ── 네 번째 개인화 문항: 저녁 먹을 곳의 북적임 ──────────────────────
 * 🔴 화면(index.html 의 CROWD_QUESTION)과 표(schema.sql ·
 *    migrations/0004 의 response_crowd_pref_ok)와 이 목록이 **세 벌**이다.
 *    셋이 어긋나면 사람은 화면에서 통과하고 서버나 DB 에서 거절당한다.
 *    고칠 때 셋을 같이 고친다.
 *
 * 눈금은 "저녁 먹을 곳을 고를 때 어느 쪽으로 가나" 한 축이다 —
 *   CROWD_BUSY(먹자골목 한가운데) · CROWD_EDGE(가장자리) · CROWD_QUIET(조용한 골목).
 * 🔴 CROWD_VARIES("그날그날 달라요")는 눈금 위의 한 점이 아니고 건너뛴 것도
 *    아니다. "밀도를 고정하지 않는 사람" 이라는 답이라 저장은 하고 계산에서만
 *    뺀다 — 식사 문항의 VARIES 와 같은 취급이다.
 * ────────────────────────────────────────────────────────────────── */
const CROWD_CHOICES = new Set(["CROWD_BUSY", "CROWD_EDGE", "CROWD_QUIET", "CROWD_VARIES"]);

/* 들어온 북적임 답을 읽는다. 🔴 셋을 가른다 —
 *   null     : crowdPref 칸이 아예 없다 = 아직 안 물어봤다 (표에서도 NULL)
 *   SKIPPED  : 물어봤는데 안 골랐다 (null 로 온다)
 *   SELECTED : 골랐다
 * 🔴 여기서는 null 이 "건너뜀" 이다. spend_profile 안쪽에서 "stay": null 을
 *    금지한 것과 어긋나 보이지만 자리가 다르다 — 저기는 **객체 안의 키**라
 *    "키가 없는 것" 과 "키가 null 인 것" 두 벌이 생기는 게 문제였고, 여기는
 *    **칸 하나**라 세 상태를 나타낼 다른 방법이 없다(칸 없음 / null / 글자).
 * 상태는 클라이언트가 보낸 것을 믿지 않고 여기서 정한다. */
function readCrowdPref(v) {
  if (v === undefined) return { status: null, value: null };
  if (v === null) return { status: "SKIPPED", value: null };
  /* 🔴 모르는 값을 메시지에 그대로 되돌려 주지 않는다.
        이 서버는 사람이 적은 글을 어디에도 다시 내보내지 않는다. */
  if (typeof v !== "string" || !CROWD_CHOICES.has(v)) {
    return { error: "저녁 먹을 곳 문항의 답이 목록에 없는 값이에요." };
  }
  return { status: "SELECTED", value: v };
}

/* ── 짝 비교 여섯 문항 (화면 3장 × 2문항) ────────────────────────────
 * 🔴 여기가 유일한 방벽이다. 화면만 믿으면 주소창으로 직접 보내는 것을 못 막는다.
 *
 * 🔴 둘을 가른다 —
 *   null     : pairwise 가 아예 없다 = 아직 안 물어봤다 (표에서도 NULL)
 *   ANSWERED : 여섯 문항을 다 답했다
 *   ...SKIPPED 는 없다. 화면에 건너뛰기가 없기 때문이다 — 두 카드 중 하나를
 *   눌러야 다음으로 간다. 강제 선택이 짝 비교의 본체다. 그래서 "일부만
 *   답했다" 도 안 받는다. 반쪽짜리는 나중에 빠진 자리를 지어내게 만든다.
 * ────────────────────────────────────────────────────────────────── */
const isBit = v => v === 0 || v === 1;
const okBucket = v => Number.isInteger(v) && v >= 0 && v <= MS_BUCKET_MAX;

function readPairwise(v) {
  if (v === undefined) {
    return { status: null, designId: null, trapPassed: null, rows: [] };
  }
  if (v === null || typeof v !== "object" || Array.isArray(v)) {
    return { error: "두 곳 중 고르기 답을 읽지 못했어요." };
  }
  /* 🔴 화면과 서버가 서로 다른 문항을 들고 있으면 그 응답은 못 쓴다 —
        eat01 이 서로 다른 질문이 되어 버린다. 아래 검사는 전부 **이 벌의**
        문항 정의로만 한다 (S15P21E201-884). */
  const variant = typeof v.designId === "string" ? VARIANTS.get(v.designId) : null;
  if (!variant) {
    return { error: "화면이 오래됐어요. 새로고침한 뒤 다시 해 주세요." };
  }
  if (!Array.isArray(v.choices)) return { error: "두 곳 중 고르기 답을 읽지 못했어요." };

  const rows = [];
  const seen = new Set();
  let trapPassed = null;

  for (const c of v.choices) {
    if (!c || typeof c !== "object" || Array.isArray(c)) {
      return { error: "두 곳 중 고르기 답을 읽지 못했어요." };
    }
    /* 🔴 모르는 set_id 를 메시지에 그대로 되돌려 주지 않는다 */
    const set = typeof c.setId === "string" ? variant.byId.get(c.setId) : null;
    if (!set) return { error: "두 곳 중 고르기에 모르는 문항이 있어요." };
    if (seen.has(set.setId)) return { error: "두 곳 중 고르기 답이 겹쳐서 왔어요." };
    seen.add(set.setId);
    if (!isBit(c.chosen) || !isBit(c.leftWas)) {
      return { error: "두 곳 중 고르기에서 어느 쪽을 고르셨는지 읽지 못했어요." };
    }
    if (!okBucket(c.msBucket)) return { error: "두 곳 중 고르기 답을 읽지 못했어요." };

    /* 🔴 함정을 통과했는지는 **여기서** 정한다. 화면이 보낸 판정을 믿으면
          주소창으로 "통과했다" 를 그냥 보낼 수 있고, 그러면 함정이 함정이 아니다. */
    if (set.trap) trapPassed = c.chosen === set.trapCorrect;

    /* 🔴 보여준 두 카드의 조건은 **화면이 보낸 값을 쓰지 않는다.**
          여기 있는 문항 정의에서 set_id 로 찾아 서버가 적는다. 화면이 보낸
          숫자를 그대로 넣으면 주소창으로 "가격 1원짜리를 봤다" 를 꾸며 넣을
          수 있고, 그건 계수를 조용히 망가뜨린다. */
    const [a0, a1] = set.alternatives;
    /* 🔴 v3 (S15P21E201-851) — 어느 덩어리의 문항이었나. 이것도 화면이 아니라
          문항 정의에서 가져온다. 덩어리가 섞인 문항(set.blocks)은 두 카드의
          덩어리가 다르므로 카드마다 따로 적는다. */
    const mixed = Array.isArray(set.blocks) && set.blocks.length === 2;
    rows.push({
      setId: set.setId,
      isTrap: !!set.trap,
      trapCorrect: set.trap ? set.trapCorrect : null,
      pageNo: Math.floor(variant.sets.indexOf(set) / PER_PAGE) + 1,
      chosen: c.chosen, leftWas: c.leftWas, msBucket: c.msBucket,
      block: mixed ? "mixed" : (set.block || null),
      a0Block: mixed ? set.blocks[0] : null,
      a1Block: mixed ? set.blocks[1] : null,
      a0, a1
    });
  }

  if (rows.length !== variant.sets.length) {
    return { error: `두 곳 중 고르기 ${variant.sets.length}문항을 모두 골라 주세요.` };
  }
  if (trapPassed === null) return { error: "두 곳 중 고르기 문항을 모두 골라 주세요." };

  return { status: "ANSWERED", designId: variant.designId, trapPassed, rows };
}

/* 들어온 응답이 쓸 수 있는 모양인가. 아니면 왜 아닌지를 사람이 읽을 말로 돌려준다 */
function check(b) {
  if (!b || typeof b !== "object") return "보내신 내용을 읽지 못했어요.";
  if (b.consented !== true) return "동의 표시가 없어요.";
  if (!AGE_BANDS.has(b.ageBand)) return "나이대를 골라 주세요.";
  if (!BUSAN_YEARS.has(b.busanYears)) return "부산에 얼마나 사셨는지 골라 주세요.";

  /* 🔴 전화번호는 선택이다 — 비어 있으면(null/undefined/"") 그냥 통과시킨다.
        값이 있을 때만 모양을 본다. b.phone 은 아래 POST 핸들러가 넣기 전에
        이미 숫자만 남도록 정규화해 둔다. */
  if (b.phone != null && b.phone !== "") {
    if (typeof b.phone !== "string" || !PHONE_RE.test(b.phone)) {
      return "전화번호는 숫자만 11자리로 적어 주세요. (예: 01012345678, 비워 두셔도 됩니다)";
    }
  }

  const rs = b.recommendations;
  /* 🔴 다섯은 하한이고 열이 상한이다 (S15P21E201-754). 예전에는 `!== NEED` 라
        여섯 번째 칸을 보내면 그 자리에서 거절당했다.
        🔴 빈 칸은 여기 오기 전에 pruneEmptySlots() 가 이미 걷어냈다 —
           그래서 여기 남은 것은 전부 "적은 칸" 이고, 아래 검사는 전부 통과해야 한다. */
  if (!Array.isArray(rs)) return "추천하는 곳을 읽지 못했어요.";
  if (rs.length < NEED) return "추천하는 곳 " + NEED + "군데를 채워 주세요.";
  if (rs.length > MAX_SLOTS) return `추천하는 곳은 ${MAX_SLOTS}군데까지 받아요.`;

  /* 🔴 같은 유형이 여러 번 와도 받는다. 맛집 다섯 곳은 정상적인 응답이다.
        예전에는 여기서 중복 유형을 거절했는데, 그러면 맛집을 다섯 곳 아는
        사람의 답이 잘려나간다 — 우리가 가장 원하는 응답이 바로 그것이다. */
  for (let i = 0; i < rs.length; i++) {
    const r = rs[i];
    /* 🔴 칸 번호로 말한다. 유형은 겹칠 수 있어서 유형 이름만으로는 어느 칸인지 못 가린다 */
    const where = `${i + 1}번째 추천`;
    if (!r || typeof r !== "object") return `${where}을 읽지 못했어요.`;
    if (!PLACE_TYPES.has(r.placeType)) return `${where}의 장소 유형이 목록에 없는 값이에요.`;
    if (!WHEN_GOOD.has(r.whenGood)) return `${where}의 언제 가면 좋은지를 골라 주세요.`;
    if (typeof r.limitedTime !== "boolean") return `${where}의 기간 한정 표시를 읽지 못했어요.`;

    for (const [key, label, max] of [["placeName", "장소 이름", 60], ["reason", "추천 이유", 500]]) {
      const v = typeof r[key] === "string" ? r[key].trim() : "";
      if (!v) return `${where}의 ${label} 칸이 비어 있어요.`;
      if (v.length > max) return `${where}의 ${label} 이 너무 길어요 (${max}자까지).`;
      const hit = piiHit(v);
      if (hit) return `${where}의 ${label} 칸에 ${hit}가 있어요. 지워 주세요.`;
    }

    /* 🔴 목록에서 고른 경우만 온다 — 직접 적은 경우엔 없거나 빈 값이라 통과.
          값이 있으면 우리 색인에 실제로 있는 id 인지 본다(S15P21E201-754).
          없는 id 를 보내면(다른 값 조작 등) 그 자리에서 거절한다. */
    if (r.placeId != null && r.placeId !== "") {
      if (typeof r.placeId !== "string" || !PLACE_BY_ID.has(r.placeId)) {
        return `${where}에서 고른 장소를 찾을 수 없어요. 목록에서 다시 골라 주세요.`;
      }
    }
  }
  return null;
}

/* ── 🔴 늘려 놓고 안 채운 칸을 걷어낸다 (S15P21E201-754) ─────────────
 * 「한 곳 더 적기」로 칸을 늘렸다가 안 채운 사람이 제출에서 막히면 안 된다.
 * 화면도 보내기 전에 같은 일을 하지만, **여기서 한 번 더 한다** — 화면만 믿으면
 * 주소창으로 직접 보낸 요청이나 옛 화면이 통째로 빈 칸을 실어 보낸다.
 *
 * 🔴 "통째로 빈 칸" 만 걷어낸다. 이름만 적고 이유를 안 적은 칸은 **안 걷어낸다** —
 *    그건 사람이 적다 만 것이고, 조용히 버리면 적은 것이 소리 없이 사라진다.
 *    그런 칸은 아래 check() 가 "몇 번째 칸의 무엇이 비었다" 로 알려 준다.
 *
 * 🔴 걷어낸 뒤 칸 번호를 다시 매기는 것은 insert() 다 (거기서 1부터 센다).
 *    화면이 보낸 slot 값은 안 쓴다 — 6·9 만 남은 배열이 와도 표에는 1·2 로 들어간다.
 * ────────────────────────────────────────────────────────────────── */
function pruneEmptySlots(b) {
  if (!b || typeof b !== "object" || !Array.isArray(b.recommendations)) return;
  const blank = r => {
    if (!r || typeof r !== "object") return false;   /* 읽을 수 없는 것은 check() 가 말한다 */
    const txt = k => (typeof r[k] === "string" ? r[k].trim() : "");
    return !r.placeType && !r.whenGood && !txt("placeName") && !txt("reason") && !txt("placeId");
  };
  b.recommendations = b.recommendations.filter(r => !blank(r));
}

async function insert(b, spend, pair, crowd) {
  const client = await pool.connect();
  try {
    await client.query("BEGIN");
    /* 🔴 개인화 세 문항 답과 짝 비교 답이 **같은 줄**에 들어간다. 그래서 이을
          것이 없다 — 애초에 갈라지지 않는다. 새 식별자를 만들지 않는 이유가
          이것이다: 이 줄의 nonce 가 이미 그 응답 하나를 가리키고, 짝 비교
          여섯 줄은 이 줄의 id 를 외래키로 물고 있다. */
    const { rows } = await client.query(
      `INSERT INTO response (age_band, busan_years, phone, consented, nonce, form_version,
                             spend_profile, spend_profile_status,
                             pairwise_status, pairwise_design_id, pairwise_trap_passed,
                             crowd_pref, crowd_pref_status)
       VALUES ($1, $2, $3, TRUE, $4, $5, $6, $7, $8, $9, $10, $11, $12) RETURNING id`,
      [b.ageBand, b.busanYears, b.phone || null, randomBytes(9).toString("base64url"),
       FORM_VERSION,
       spend.value ? JSON.stringify(spend.value) : null, spend.status,
       pair.status, pair.designId, pair.trapPassed,
       crowd.value, crowd.status]
    );
    const id = rows[0].id;
    for (const c of pair.rows) {
      /* 🔴 문항 하나 = 줄 하나. 보여준 두 카드의 조건이 이 줄에 그대로
            들어간다 — 나중에 design-v4.mjs 를 안 열어도 이 표만으로 계수를 낼 수
            있게. 조건값은 화면이 보낸 것이 아니라 서버가 문항 정의에서 찾은 것이다. */
      await client.query(
        /* 🔴 v3 부터 카드 조건은 JSONB 다. 덩어리마다 속성이 달라 칸으로 못 박는다.
              v2 의 alt0_price 같은 칸은 표에 남아 있지만 여기서는 안 채운다 —
              옛 응답을 읽기 위한 자리이지 새 응답이 쓸 자리가 아니다. */
        `INSERT INTO pairwise_choice
           (response_id, design_id, set_id, is_trap, trap_correct, page_no,
            chosen, left_was, ms_bucket, block, alt0_block, alt1_block, alt0, alt1)
         VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10, $11, $12, $13, $14)`,
        [id, pair.designId, c.setId, c.isTrap, c.trapCorrect, c.pageNo,
         c.chosen, c.leftWas, c.msBucket, c.block, c.a0Block, c.a1Block,
         JSON.stringify(c.a0), JSON.stringify(c.a1)]
      );
    }
    let slot = 0;
    for (const r of b.recommendations) {
      slot += 1;
      /* 🔴 gu 는 클라이언트가 보낸 값을 믿지 않는다 — place_id 로 우리 색인을
            다시 찾아 그 가게의 구를 서버가 직접 채운다. check() 가 이미 이
            place_id 가 색인에 있는지 확인했다. */
      const place = r.placeId ? PLACE_BY_ID.get(r.placeId) : null;
      await client.query(
        `INSERT INTO recommendation
           (response_id, slot, place_type, place_name, when_good, limited_time, reason, place_id, gu)
         VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9)`,
        [id, slot, r.placeType, r.placeName.trim(), r.whenGood, r.limitedTime, r.reason.trim(),
         place ? place.id : null, place ? place.gu : null]
      );
    }
    await client.query("COMMIT");
    return id;
  } catch (e) {
    await client.query("ROLLBACK").catch(() => {});
    throw e;
  } finally {
    client.release();
  }
}

const json = (res, code, obj) => {
  res.writeHead(code, { "content-type": "application/json; charset=utf-8", "cache-control": "no-store" });
  res.end(JSON.stringify(obj));
};

const server = createServer((req, res) => {
  /* 주소만 본다. 어디서 왔는지(IP · Referer · User-Agent)는 읽지 않는다 */
  const url = new URL(req.url || "/", "http://internal");   /* 물음표 뒤 검색어(query)를 안전하게 떼어내려고만 쓴다 */
  let path = url.pathname;

  /* /survey 로 오면 /survey/ 로 보낸다.
     🔴 슬래시가 없으면 화면 안의 "api/submit" 이 /api/submit 으로 풀려서
        운영 백엔드로 날아간다. 이 한 줄이 그걸 막는다. */
  if (path === MOUNT) {
    res.writeHead(301, { location: MOUNT + "/" });
    res.end();
    return;
  }
  if (path.startsWith(MOUNT + "/")) path = path.slice(MOUNT.length);

  if (req.method === "GET" && (path === "/" || path === "/index.html")) {
    res.writeHead(200, { "content-type": "text/html; charset=utf-8", "cache-control": "no-cache" });
    res.end(PAGE);
    return;
  }

  /* 사진 · 글꼴 — 허용 목록에 있는 경로만. 목록에 없으면 여기까지 안 걸리고
     맨 아래 404 로 떨어진다 (path traversal 을 시도해도 마찬가지다) */
  if (req.method === "GET" && Object.prototype.hasOwnProperty.call(STATIC_FILES, path)) {
    const entry = STATIC_FILES[path];
    res.writeHead(200, { "content-type": entry.type, "cache-control": "public, max-age=604800" });
    res.end(entry.body);
    return;
  }

  if (req.method === "GET" && path === "/health") {
    json(res, 200, { ok: true });
    return;
  }

  /* 🔴 가게 이름 자동완성 (S15P21E201-754). 화면은 이 응답에 좌표를 못 본다 —
        searchPlaces() 가 애초에 안 담는다. json() 이 no-store 를 붙여서
        검색어가 캐시에 안 남는다. */
  if (req.method === "GET" && path === "/api/places") {
    const results = searchPlaces(url.searchParams.get("q"), url.searchParams.get("gu"));
    json(res, 200, results);
    return;
  }

  if (req.method === "POST" && path === "/api/submit") {
    let size = 0;
    const chunks = [];
    req.on("data", c => {
      size += c.length;
      if (size > 32 * 1024) { req.destroy(); return; }   /* 32KB 넘으면 이 설문의 응답이 아니다 */
      chunks.push(c);
    });
    req.on("end", async () => {
      let body;
      try { body = JSON.parse(Buffer.concat(chunks).toString("utf8")); }
      catch { return json(res, 400, { message: "보내신 내용을 읽지 못했어요." }); }

      /* 🔴 하이픈이 섞여 와도(주소창으로 직접 보낸 경우 등) 숫자만 남긴다.
            비어 있으면 그대로 두고, check() 가 "선택이라 통과" 를 처리한다. */
      if (body && typeof body === "object" && body.phone) {
        body.phone = normalizePhone(body.phone);
      }

      /* 🔴 늘려 놓고 안 채운 칸을 먼저 걷어낸다. check() 는 남은 것만 본다 */
      pruneEmptySlots(body);

      const wrong = check(body);
      if (wrong) return json(res, 400, { message: wrong });

      /* 🔴 화면만 믿지 않는다. 주소창으로 직접 보내는 것도 여기서 걸러진다 */
      const spend = readSpendProfile(body.spendProfile);
      if (spend.error) return json(res, 400, { message: spend.error });

      const pair = readPairwise(body.pairwise);
      if (pair.error) return json(res, 400, { message: pair.error });

      const crowd = readCrowdPref(body.crowdPref);
      if (crowd.error) return json(res, 400, { message: crowd.error });

      try {
        await insert(body, spend, pair, crowd);
        json(res, 201, { ok: true });
      } catch (e) {
        /* 🔴 오류 이름만 찍는다. 요청 내용은 절대 찍지 않는다 —
              그 안에 사람이 적은 글이 들어 있다. */
        console.error("submit failed:", e.code || e.name || "unknown");
        json(res, 500, { message: "저장하지 못했어요. 잠시 뒤 다시 눌러 주세요." });
      }
    });
    return;
  }

  json(res, 404, { message: "없는 주소예요." });
});

server.listen(PORT, HOST, () => {
  console.log(`survey-recommend listening on ${HOST}:${PORT} (mount ${MOUNT})`);
});

for (const sig of ["SIGTERM", "SIGINT"]) {
  process.on(sig, () => {
    server.close(() => pool.end().then(() => process.exit(0)));
  });
}
