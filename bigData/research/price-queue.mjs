/**
 * 음식점 통합 조사 — 가격 · "왜 가는지" narrative · 원문 발췌를 한 번에. agy 헤드리스, 재개 가능.
 *
 * 🔴 **처음엔 가격만 조사했다.** 사용자가 "원문도 저장하고 필드도 넓히자"로 다시 설계해서,
 *    지금은 narrative 조사(`PROMPT.md` 의 whyPeopleGo·descriptors 7칸)와 정량 신호(가격·
 *    영업시간·혼잡·현지인비중·메뉴다양성)를 **한 번의 fetch로 같이** 받는다. 따로 두 번
 *    돌리는 것(가격 88k + narrative 140k = 228k 토큰/곳)보다 **합친 쪽이 3곳 검증에서
 *    76k 토큰/곳으로 더 쌌다.** `reviewCount`(평점·후기수)는 기존 규칙(「다른 앱 별점·
 *    평가수 적지 마라」, `PROMPT.md` 「하지 말 것」)과 충돌해서 뺐다.
 *
 * 🔴 **대상은 2,000곳이다, 2,355곳이 아니다.** 인수인계 문서의 "2,355곳"은 그때 운영 DB에
 *    이미 적재돼 있던 수였고, 지금 이 스크립트가 읽는 `data/staged/selected-2000.ndjson`
 *    (select-2000.mjs 산출, FOOD 규칙만 — tourapi-nonfood 제외)은 **2,000곳**이다.
 *
 * 재개 가능한 이유 — **결과 파일 하나가 곧 체크포인트다.**
 *   `research/data/combined-results/<id>.json` 이 있고 `found` 가 `null` 이 아니면 건너뛴다.
 *   중간에 끊겨도 다시 이 스크립트를 그대로 돌리면 끝난 곳은 다시 안 돌고 이어간다.
 *
 * 🔴 sourceExcerpts(원문 발췌)는 **내부 분석용으로만 쓰고 재배포하지 않는다.** 일부 출처는
 *    원문 저장을 금지하는 약관을 걸어 두므로(구글식), 이 필드를 외부에 공개하는 화면·API 에
 *    그대로 노출하지 않는다 — 분석·품질 확인 용도로만 연다.
 *
 * 사용
 *   node research/price-queue.mjs             다음 배치를 돈다 (기본 한 번에 최대 60곳)
 *   node research/price-queue.mjs --limit 200  이번 실행에서 최대 200곳까지
 *   node research/price-queue.mjs --status     몇 곳 끝났는지만 보고 안 돈다
 *   node research/price-queue.mjs --aggregate  끝난 결과를 data/staged/ 로 모은다
 */
import { execFile } from "node:child_process";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(HERE, "..");
const SELECTED = path.join(ROOT, "data/staged/selected-2000.ndjson");
const RESULTS_DIR = path.join(HERE, "data/combined-results");
const STAGED_PRICE = path.join(ROOT, "data/staged/place-price.ndjson");
const STAGED_COMBINED = path.join(ROOT, "data/staged/place-research-combined.ndjson");
const SCHEMA = path.join(HERE, "price-schema.json");

const argv = process.argv.slice(2);
const flag = (n) => argv.includes(n);
const num = (n, d) => { const i = argv.indexOf(n); return i < 0 ? d : Number(argv[i + 1]); };
const LIMIT = num("--limit", 60);
const CONCURRENCY = num("--concurrency", 6);

fs.mkdirSync(RESULTS_DIR, { recursive: true });

function loadTargets() {
  const lines = fs.readFileSync(SELECTED, "utf8").trim().split("\n");
  return lines
    .map((l) => JSON.parse(l))
    .filter((r) => r.pick.rule !== "tourapi-nonfood")
    .map((r) => ({ id: r.id, name: r.name, road: r.roadAddr, cat: r.category?.name ?? "" }));
}

/**
 * 🔴 "결과 파일이 있다" 와 "끝났다" 는 다르다. `found: null` 은 조사 자체가 실패한 것
 * (타임아웃·스키마 파일이 없어졌다 같은 우리 쪽 사고 포함)이라 **다시 돈다.**
 * `found: true/false` 만 진짜 완료다 — 이게 체크포인트의 판정 기준이다.
 */
function isDone(t) {
  const f = path.join(RESULTS_DIR, `${t.id}.json`);
  if (!fs.existsSync(f)) return false;
  try { return JSON.parse(fs.readFileSync(f, "utf8")).found !== null; }
  catch { return false; }
}

function statusReport(targets) {
  const done = targets.filter(isDone);
  const found = done.filter((t) => JSON.parse(fs.readFileSync(path.join(RESULTS_DIR, `${t.id}.json`), "utf8")).found);
  console.log(`대상 ${targets.length}곳 중 완료 ${done.length}곳 (${((done.length / targets.length) * 100).toFixed(1)}%)`);
  if (done.length) console.log(`완료분 중 적중 ${found.length}/${done.length} (${((found.length / done.length) * 100).toFixed(1)}%)`);
  console.log(`남은 곳 ${targets.length - done.length}곳 (오류로 재시도 대기 포함)`);
  return { done, targets };
}

function buildPrompt(t) {
  return `너는 부산 음식점을 웹에서 실제로 검색해서 조사하는 조사원이다.

대상: ${t.road} 에 있는 '${t.name}'(${t.cat} 업종)

## 할 일

1. 이 가게를 웹에서 검색해서 "사람들이 왜 이 집에 가는지"를 찾는다.
2. 아래 정량 정보도 같이 찾는다 — 가격, 영업시간, 혼잡/줄서기, 현지인/관광객 언급,
   메뉴 다양성. **각 항목은 따로 찾는다** — 하나를 못 찾아도 나머지는 계속 찾는다.
3. **실제로 읽은 페이지에서, 이 가게에 대해 적힌 문장을 그대로 옮겨 적는다**
   (sourceExcerpts). 요약하지 말고 원문 그대로 — 나중에 사람이 다시 판단할 수 있게
   남겨두는 것이다. 내부 분석용으로만 쓰고 재배포하지 않는다.

## 규칙 (반드시 지킨다)

- **없는 가게를 지어내지 마라.** 못 찾으면 found:false.
- **각 정량 항목도 못 찾으면 그 항목만 found:false 로 하고 나머지는 계속 찾는다** —
  하나 못 찾았다고 전체를 포기하지 않는다.
- **whyPeopleGo.note 는 원문을 그대로 옮기지 마라** — 한 줄 요약. 단 sourceExcerpts
  는 반대로 **원문을 그대로** 옮긴다(용도가 다르다).
- **네이버·카카오 지도 응답을 저장하지 마라.** 로드뷰 이미지도 안 된다.
- **다른 앱의 별점 숫자·평가 수는 적지 마라.** 이 스키마엔 그 칸이 아예 없다.
- sources 에는 **원문 주소**를 적어라. 중계 주소(구글 리다이렉트)는 출처가 아니다.

## whyPeopleGo 유형 (아홉 가지 중 골라라, 안 맞으면 newTypes 에 우리말로)

원조·최초 · 오랜 역사 · 대물림 · 맛 자체 · 혼잡·수요 · 현지인 지지 · 투표·독자 선정 ·
플랫폼 순위·평점 · 가격·가성비 · 운영·접근 편의 · 서비스·사람

## descriptors — 정확히 이 일곱 칸만

yearsClaimed(숫자) · generations(숫자) · queueing(불리언) · parking(불리언) ·
audience(문자) · signatureDishes(배열) · priceBand(cheap/mid/high)

## 🔴 검색은 최대 3~4번 안에서 끝낸다

찾다가 안 나오는 항목에 검색을 계속 쏟지 마라. 3~4번 검색해도 안 나오면 그 항목만
found:false 로 넘기고 다음으로 간다 — 완벽하게 채우는 것보다 **적당히 찾고 빨리
끝내는 게 낫다.**`;
}

async function callAgy(t, timeoutMs) {
  const prompt = buildPrompt(t);
  return new Promise((resolve) => {
    execFile(
      "agy",
      // 🔴 effort low — 1부(narrative 조사) 실측에서 토큰 -22%·시간 -41%, 되찾음률은
      // 거의 그대로였다(인수인계 문서 1.6절). 지금까지는 이 옵션을 빼먹고 돌리고 있었다.
      ["--model", "gemini-3.8-flash-low", "--effort", "low", "--dangerously-skip-permissions", `-p=${prompt}`, "--json-schema", SCHEMA, "--output-format", "json"],
      { maxBuffer: 16 << 20, timeout: timeoutMs },
      (err, stdout) => {
        if (err) return resolve({ ok: false, err: String(err.message || err).slice(0, 300) });
        try {
          const parsed = JSON.parse(stdout.trim().split("\n").pop());
          resolve({ ok: true, structured: parsed.structured_output, geminiTokens: parsed.usage?.total_tokens ?? null });
        } catch (e) {
          resolve({ ok: false, err: "PARSE_FAIL: " + String(e).slice(0, 200) });
        }
      },
    );
  });
}

/**
 * 🔴 재시도를 이 함수 안에서 바로 하지 않는다 — 예전엔 타임아웃(150s) 나면 그 자리에서
 * 180s 로 한 번 더 돌렸는데, agy 는 이어하기가 아니라 **처음부터 다시 검색**하므로
 * 실패한 곳이 통째로 2배 비용을 문다(실측: 재시도로 살아난 9곳 평균 190k토큰, 전체
 * 평균 135k토큰). 체크포인트가 이미 `found:null` 을 "안 끝난 것"으로 보고 **다음
 * 실행에서 자동으로 다시 줍는다** — 그러니 여기서 즉시 재시도할 필요가 없다. 시간을
 * 넉넉히(220s) 주고 한 번만 시도한다.
 */
async function runOne(t) {
  const r = await callAgy(t, 220000);
  const out = r.ok
    ? { id: t.id, name: t.name, cat: t.cat, attempts: 1, geminiTokens: r.geminiTokens,
        researchedAt: new Date().toISOString(), ...r.structured }
    : { id: t.id, name: t.name, cat: t.cat, found: null, error: r.err, attempts: 1,
        researchedAt: new Date().toISOString() }; // found:null = 조사 실패(못 찾음이 아니라 확인 못 함) — 다음 실행에서 재시도
  fs.writeFileSync(path.join(RESULTS_DIR, `${t.id}.json`), JSON.stringify(out, null, 2));
  return out;
}

async function runPool(items, n) {
  let i = 0;
  let doneCount = 0;
  let foundCount = 0;
  let errCount = 0;
  async function worker() {
    while (i < items.length) {
      const t = items[i++];
      const r = await runOne(t);
      doneCount++;
      if (r.found) foundCount++;
      if (r.found === null) errCount++;
      console.log(`[${doneCount}/${items.length}] ${r.name} — found=${r.found} attempts=${r.attempts}`);
    }
  }
  await Promise.all(Array.from({ length: n }, worker));
  return { doneCount, foundCount, errCount };
}

function aggregate(targets) {
  const priceLines = [];
  const combinedLines = [];
  for (const t of targets) {
    const f = path.join(RESULTS_DIR, `${t.id}.json`);
    if (!fs.existsSync(f)) continue;
    const r = JSON.parse(fs.readFileSync(f, "utf8"));
    if (!r.found) continue;
    if (r.price?.found) {
      priceLines.push(JSON.stringify({ placeId: r.id, priceWon: r.price.priceWon, priceMenu: r.price.priceMenu, sources: r.sources }));
    }
    combinedLines.push(JSON.stringify({
      placeId: r.id,
      confidence: r.confidence,
      whyPeopleGo: r.whyPeopleGo ?? [],
      descriptors: r.descriptors ?? {},
      price: r.price ?? { found: false },
      hours: r.hours ?? { found: false },
      crowding: r.crowding ?? { found: false },
      localVsTourist: r.localVsTourist ?? { found: false },
      menuDiversity: r.menuDiversity ?? { found: false },
      sources: r.sources ?? [],
      sourceExcerpts: r.sourceExcerpts ?? [], // 🔴 내부 분석용 — 외부 노출 금지
    }));
  }
  fs.mkdirSync(path.dirname(STAGED_PRICE), { recursive: true });
  fs.writeFileSync(STAGED_PRICE, priceLines.join("\n") + (priceLines.length ? "\n" : ""));
  fs.writeFileSync(STAGED_COMBINED, combinedLines.join("\n") + (combinedLines.length ? "\n" : ""));
  console.log(`→ ${path.relative(ROOT, STAGED_PRICE)} (${priceLines.length}줄, 가격만)`);
  console.log(`→ ${path.relative(ROOT, STAGED_COMBINED)} (${combinedLines.length}줄, 전체 — 내부 분석용)`);
}

// 🔴 실측으로 한 번 걸렸다 — 스키마 파일이 (다른 브랜치로 전환되며) 사라진 채로 282곳이
// 조용히 다 실패했다. 시작하기 전에 확인하고, 없으면 그 자리에서 죽는다(fail fast).
if (!fs.existsSync(SCHEMA)) {
  console.error(`🔴 스키마 파일이 없다: ${SCHEMA}\n   (다른 브랜치로 체크아웃하면 이 파일이 사라질 수 있다 — 실제로 한 번 그랬다)`);
  process.exit(1);
}

const targets = loadTargets();
console.log(`대상 파일: FOOD ${targets.length}곳 (selected-2000.ndjson 기준 — 문서의 "2,355곳"과 다름, 위 주석 참고)`);

if (flag("--status")) {
  statusReport(targets);
  process.exit(0);
}
if (flag("--aggregate")) {
  aggregate(targets);
  process.exit(0);
}

const { done: alreadyDone } = statusReport(targets);
const doneIds = new Set(alreadyDone.map((t) => t.id));
const remaining = targets.filter((t) => !doneIds.has(t.id)).slice(0, LIMIT);
console.log(`\n이번 실행: ${remaining.length}곳 (동시 ${CONCURRENCY}개)`);

if (remaining.length === 0) {
  console.log("남은 곳이 없다.");
  aggregate(targets);
  process.exit(0);
}

const t0 = Date.now();
const stats = await runPool(remaining, CONCURRENCY);
console.log(`\n이번 배치: ${stats.doneCount}곳 완료 · 적중 ${stats.foundCount} · 오류/타임아웃 ${stats.errCount} · ${((Date.now() - t0) / 1000).toFixed(0)}초`);
statusReport(targets);
aggregate(targets);
