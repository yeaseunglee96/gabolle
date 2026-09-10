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
  "/assets/gwangan-bridge.jpg":       { file: "assets/gwangan-bridge.jpg",       type: "image/jpeg" },
  "/assets/haeundae-beach.jpg":       { file: "assets/haeundae-beach.jpg",       type: "image/jpeg" },
  "/assets/gwangalli-beach.jpg":      { file: "assets/gwangalli-beach.jpg",      type: "image/jpeg" },
  "/assets/huinnyeoul.jpg":           { file: "assets/huinnyeoul.jpg",           type: "image/jpeg" },
  "/assets/busan-night-panorama.jpg": { file: "assets/busan-night-panorama.jpg", type: "image/jpeg" },
  "/assets/CREDITS.md":               { file: "assets/CREDITS.md",               type: "text/markdown; charset=utf-8" },
  "/fonts/PretendardVariable.woff2":  { file: "fonts/PretendardVariable.woff2",  type: "font/woff2" },
  "/fonts/LICENSE-Pretendard.txt":    { file: "fonts/LICENSE-Pretendard.txt",    type: "text/plain; charset=utf-8" }
};
/* 목록에 적힌 파일만 이때 읽는다 — 목록에 없는 파일은 이 서버가 존재조차 모른다 */
for (const entry of Object.values(STATIC_FILES)) {
  entry.body = readFileSync(join(HERE, entry.file));
}

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
const PLACE_TYPES = new Set(["FOOD", "CAFE", "NATURE", "CULTURE", "MARKET", "ACTIVITY", "BAR"]);
const WHEN_GOOD   = new Set(["DAY", "NIGHT", "ANY"]);
const NEED = 5;

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
      return "전화번호 형식이 올바르지 않아요. (예: 010-1234-5678, 비워 두셔도 됩니다)";
    }
  }

  const rs = b.recommendations;
  if (!Array.isArray(rs) || rs.length !== NEED) return "추천하는 곳 다섯 군데를 채워 주세요.";

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

async function insert(b) {
  const client = await pool.connect();
  try {
    await client.query("BEGIN");
    const { rows } = await client.query(
      `INSERT INTO response (age_band, busan_years, phone, consented, nonce)
       VALUES ($1, $2, $3, TRUE, $4) RETURNING id`,
      [b.ageBand, b.busanYears, b.phone || null, randomBytes(9).toString("base64url")]
    );
    const id = rows[0].id;
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

      const wrong = check(body);
      if (wrong) return json(res, 400, { message: wrong });

      try {
        await insert(body);
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
