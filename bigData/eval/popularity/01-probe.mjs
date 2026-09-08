/**
 * 1단계 — **가져와도 되는가**를 먼저 묻는다.
 *
 *   node bigData/eval/popularity/01-probe.mjs
 *
 * 인기 신호(평점·방문자 리뷰 수·블로그 리뷰 수·가격대)를 어디서 가져올지 정하기 전에,
 * 그 집이 **오지 말라고 써 붙였는지**부터 읽는다. 그게 `robots.txt`(사이트가 자동
 * 프로그램에게 "여기는 긁지 마라"를 적어 두는 파일)다.
 *
 * 이 스크립트가 하는 일은 셋뿐이다.
 *
 *  1. 후보 출처들의 `robots.txt` 를 **원문 그대로** 받아 적는다
 *  2. 공식 API 가 평점·리뷰수 칸을 주는지 **실제로 한 번 불러서** 확인한다
 *  3. 그 둘을 합쳐 출처별 판정(`허용` / `금지` / `칸없음`)을 낸다
 *
 * 🔴 **막고 있으면 거기서 멈춘다.** 우회는 이 작업의 범위가 아니다. 이 스크립트에는
 *    사람인 척하는 코드도, 프록시도, 재시도 우회도 없다 — 없는 것이 결과다.
 *
 * 요청은 **1초 이상 간격, 한 번에 하나**다. robots.txt 를 읽는 것조차 남의 서버를
 * 두드리는 일이라 같은 예의를 지킨다.
 */
import fs from "node:fs";
import path from "node:path";
import { OUT_DIR } from "./paths.mjs";

// 🔴 HTTP 헤더는 Latin-1 만 담을 수 있다. 한글을 넣으면 fetch 가 통째로 실패하고
//    그 실패가 "차단당했다" 처럼 보인다 — 실제로는 우리 쪽 오류다.
const UA = "S15P21E201-research/1.0 (SSAFY team project; sampling study)";
const GAP_MS = 3000;

/**
 * 429(**Too Many Requests** — "너무 자주 왔다") 를 받으면 **한 번만** 더, 훨씬 길게
 * 쉬었다 묻는다. 두 번째도 429 면 그대로 적고 넘어간다.
 * 이건 우회가 아니라 예의다 — 상대가 말한 속도로 늦추는 것이다.
 */
const RETRY_AFTER_MS = 20_000;

const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

/** 후보 출처 — 부산 동네 식당의 평점·리뷰 수가 실제로 보이는 곳들 */
const HOSTS = [
  { key: "naver-map", url: "https://map.naver.com/robots.txt", why: "네이버 지도 — 플레이스로 들어가는 문" },
  { key: "naver-place-m", url: "https://m.place.naver.com/robots.txt", why: "🔴 방문자 리뷰 수·블로그 리뷰 수가 실제로 적혀 있는 화면" },
  { key: "naver-place-pc", url: "https://pcmap.place.naver.com/robots.txt", why: "같은 화면의 PC 판" },
  { key: "naver-openapi", url: "https://openapi.naver.com/robots.txt", why: "네이버 공식 검색 API 호스트" },
  { key: "kakao-map", url: "https://map.kakao.com/robots.txt", why: "카카오맵 — 평점·후기 수가 보이는 곳" },
  { key: "kakao-place", url: "https://place.map.kakao.com/robots.txt", why: "카카오 장소 상세" },
  { key: "diningcode", url: "https://www.diningcode.com/robots.txt", why: "다이닝코드 — 맛집 점수를 따로 매기는 곳" },
];

/**
 * `User-agent: *` 와 우리에게 해당하는 이름(ClaudeBot 등)에 걸린 규칙만 추려 본다.
 * 판정은 사람이 원문을 보고 하되, 눈에 띄게는 해 준다.
 */
function verdictOf(body) {
  if (body == null) return { verdict: "확인실패", note: "robots.txt 를 받지 못했다" };
  const lines = body.split(/\r?\n/).map((l) => l.trim());
  const groups = [];
  let cur = null;
  for (const l of lines) {
    if (/^user-agent:/i.test(l)) {
      const agent = l.split(":").slice(1).join(":").trim();
      if (cur && cur.rules.length === 0) cur.agents.push(agent);
      else { cur = { agents: [agent], rules: [] }; groups.push(cur); }
    } else if (/^(allow|disallow):/i.test(l) && cur) {
      cur.rules.push(l);
    }
  }
  const named = groups.filter((g) => g.agents.some((a) => /claude|anthropic/i.test(a)));
  const star = groups.find((g) => g.agents.includes("*"));
  const blanketDeny = (g) => g && g.rules.some((r) => /^disallow:\s*\/\s*$/i.test(r));

  if (blanketDeny(named[0])) return { verdict: "금지", note: "우리 이름(ClaudeBot 계열)을 콕 집어 전면 금지" };
  if (blanketDeny(star)) {
    const allows = star.rules.filter((r) => /^allow:/i.test(r));
    return {
      verdict: "금지",
      note: allows.length
        ? `모두에게 전면 금지. 예외는 ${allows.join(" / ")} 뿐 — 첫 화면만 열려 있고 가게 상세는 닫혀 있다`
        : "모두에게 전면 금지",
    };
  }
  return { verdict: "일부허용", note: "전면 금지는 아니다. 경로별 규칙을 사람이 읽어야 한다" };
}

async function get(url) {
  const t0 = Date.now();
  try {
    const res = await fetch(url, { headers: { "User-Agent": UA }, redirect: "follow" });
    const body = await res.text();
    return { status: res.status, body, ms: Date.now() - t0 };
  } catch (e) {
    return { status: null, body: null, error: String(e), ms: Date.now() - t0 };
  }
}

const log = [];
const robots = [];

for (const h of HOSTS) {
  let r = await get(h.url);
  if (r.status === 429) {
    console.log(`  429 — ${RETRY_AFTER_MS / 1000}초 쉬고 한 번만 더 묻는다: ${h.key}`);
    log.push({ at: new Date().toISOString(), what: "robots.txt(429)", url: h.url, status: 429, bytes: 0 });
    await sleep(RETRY_AFTER_MS);
    r = await get(h.url);
  }
  const v = verdictOf(r.status === 200 ? r.body : null);
  robots.push({ ...h, status: r.status, ...v, body: r.body });
  log.push({ at: new Date().toISOString(), what: "robots.txt", url: h.url, status: r.status, bytes: r.body?.length ?? 0 });
  console.log(`${v.verdict.padEnd(6)} ${h.key.padEnd(16)} HTTP ${r.status}  ${v.note}`);
  await sleep(GAP_MS);
}

/**
 * 공식 API 가 **평점·리뷰수 칸 자체를 주는지**를 본다.
 *
 * 네이버 지역 검색 API 는 자격증명이 있어야 답을 준다. 없으면 401 이 오는데,
 * 그것과 별개로 **문서에 적힌 응답 칸 목록**이 이 질문의 답이다 — 칸이 없으면
 * 열쇠가 있어도 평점은 안 나온다.
 */
const NAVER_LOCAL_FIELDS = [
  "title", "link", "category", "description",
  "telephone", "address", "roadAddress", "mapx", "mapy",
];
const WANTED = ["평점", "방문자 리뷰 수", "블로그 리뷰 수", "가격대"];

const apiProbe = { at: new Date().toISOString(), calls: [] };
{
  const id = process.env.NAVER_CLIENT_ID;
  const secret = process.env.NAVER_CLIENT_SECRET;
  const url = "https://openapi.naver.com/v1/search/local.json?query=" + encodeURIComponent("부산 돼지국밥") + "&display=5";
  const t0 = Date.now();
  let status = null, note = "";
  if (!id || !secret) {
    note = "NAVER_CLIENT_ID / NAVER_CLIENT_SECRET 이 없어 부르지 않았다";
  } else {
    try {
      const res = await fetch(url, {
        headers: { "X-Naver-Client-Id": id, "X-Naver-Client-Secret": secret, "User-Agent": UA },
      });
      status = res.status;
      const body = await res.json().catch(() => ({}));
      const item = body?.items?.[0] ?? {};
      note = status === 200
        ? `응답이 실제로 준 칸: ${Object.keys(item).join(", ") || "(항목 없음)"}`
        : `호출 실패: ${body?.errorCode ?? ""} ${body?.errorMessage ?? ""}`.trim();
    } catch (e) {
      note = String(e);
    }
  }
  apiProbe.calls.push({ api: "네이버 지역 검색", url, status, ms: Date.now() - t0, note });
  log.push({ at: new Date().toISOString(), what: "공식 API 호출", url, status, bytes: 0 });
  console.log(`\n네이버 지역 검색 API — HTTP ${status ?? "부르지 않음"}  ${note}`);
}

const verdict = {
  ranAt: new Date().toISOString(),
  질문: "인기 신호(평점·방문자 리뷰 수·블로그 리뷰 수·가격대)를 합법적으로 얻을 경로가 있는가",
  /**
   * 🔴 **본문은 HTTP 200 일 때만 남긴다.**
   * 차단 안내 페이지(429)에는 **우리 공인 IP 가 찍혀 나온다.** 그건 robots.txt 가
   * 아니라 우리 쪽 식별자라, 저장소에 남길 것이 아니다.
   */
  robots: robots.map(({ body, status, ...rest }) => ({
    ...rest,
    status,
    원문: status === 200 ? body : null,
    막힘: status === 200 ? null : `HTTP ${status} — robots.txt 대신 차단 안내 화면이 왔다 (본문은 우리 IP 가 찍혀 있어 남기지 않는다)`,
  })),
  공식API: {
    "네이버 지역 검색": {
      문서: "https://developers.naver.com/docs/serviceapi/search/local/local.md",
      주는칸: NAVER_LOCAL_FIELDS,
      안주는칸: WANTED,
      한계: "display 최댓값 5, start 최댓값 1 — 한 질의에 5곳까지, 페이지 넘기기 없음",
      비고: "sort=comment 는 '카페·블로그 리뷰 개수순 정렬'이라 순서는 주지만 **개수는 안 준다**",
    },
    실측: apiProbe,
  },
  취득기록: log,
};

fs.mkdirSync(OUT_DIR, { recursive: true });
fs.writeFileSync(path.join(OUT_DIR, "01-source-verdict.json"), JSON.stringify(verdict, null, 1), "utf8");

const blocked = robots.filter((r) => r.verdict === "금지").map((r) => r.key);
console.log(`\n전면 금지: ${blocked.length}/${robots.length} — ${blocked.join(", ")}`);
console.log(`공식 API 가 평점·리뷰수 칸을 주는가: 아니오`);
console.log(`\n적었다: ${path.join(OUT_DIR, "01-source-verdict.json")}`);
