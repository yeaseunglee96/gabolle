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

/* ── 받아들이는 값 — 화면의 목록과 같아야 한다 ───────────────────── */
const AGE_BANDS   = new Set(["UNDER_20", "AGE_20_24", "AGE_25_29", "AGE_30_34", "AGE_35_PLUS"]);
const BUSAN_YEARS = new Set(["BORN_HERE", "OVER_10Y", "Y_3_10", "Y_1_3", "UNDER_1Y", "NEVER"]);
const PLACE_TYPES = new Set(["FOOD", "CAFE", "NATURE", "CULTURE", "MARKET", "ACTIVITY", "BAR"]);
const WHEN_GOOD   = new Set(["DAY", "NIGHT", "ANY"]);
const NEED = 5;

/* 들어온 응답이 쓸 수 있는 모양인가. 아니면 왜 아닌지를 사람이 읽을 말로 돌려준다 */
function check(b) {
  if (!b || typeof b !== "object") return "보내신 내용을 읽지 못했어요.";
  if (b.consented !== true) return "동의 표시가 없어요.";
  if (!AGE_BANDS.has(b.ageBand)) return "나이대를 골라 주세요.";
  if (!BUSAN_YEARS.has(b.busanYears)) return "부산에 얼마나 사셨는지 골라 주세요.";

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
  }
  return null;
}

async function insert(b) {
  const client = await pool.connect();
  try {
    await client.query("BEGIN");
    const { rows } = await client.query(
      `INSERT INTO response (age_band, busan_years, consented, nonce)
       VALUES ($1, $2, TRUE, $3) RETURNING id`,
      [b.ageBand, b.busanYears, randomBytes(9).toString("base64url")]
    );
    const id = rows[0].id;
    let slot = 0;
    for (const r of b.recommendations) {
      slot += 1;
      await client.query(
        `INSERT INTO recommendation
           (response_id, slot, place_type, place_name, when_good, limited_time, reason)
         VALUES ($1, $2, $3, $4, $5, $6, $7)`,
        [id, slot, r.placeType, r.placeName.trim(), r.whenGood, r.limitedTime, r.reason.trim()]
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
  let path = (req.url || "/").split("?")[0];

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
