/**
 * 음식점 가격 전수 조사 — agy 헤드리스, 재개 가능.
 *
 * 🔴 **대상은 2,000곳이다, 2,355곳이 아니다.** 인수인계 문서의 "2,355곳"은 그때 운영 DB에
 *    이미 적재돼 있던 수였고, 지금 이 스크립트가 읽는 `data/staged/selected-2000.ndjson`
 *    (select-2000.mjs 산출, FOOD 규칙만 — tourapi-nonfood 제외)은 **2,000곳**이다. 다르면
 *    다르다고 적는다 — 조용히 아무 숫자나 골라 쓰지 않는다.
 *
 * 재개 가능한 이유 — **결과 파일 하나가 곧 체크포인트다.**
 *   `research/data/price-results/<id>.json` 이 이미 있으면 그 곳은 건너뛴다.
 *   중간에 죽어도(피시가 꺼져도, agy 가 막혀도) 다시 이 스크립트를 그대로 돌리면
 *   끝난 곳은 다시 안 돌고 남은 곳부터 이어진다.
 *
 * 사용
 *   node research/price-queue.mjs             다음 배치를 돈다 (기본 한 번에 최대 60곳)
 *   node research/price-queue.mjs --limit 200  이번 실행에서 최대 200곳까지
 *   node research/price-queue.mjs --status     몇 곳 끝났는지만 보고 안 돈다
 *   node research/price-queue.mjs --aggregate  끝난 결과를 data/staged/place-price.ndjson 로 모은다
 */
import { execFile } from "node:child_process";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(HERE, "..");
const SELECTED = path.join(ROOT, "data/staged/selected-2000.ndjson");
const RESULTS_DIR = path.join(HERE, "data/price-results");
const STAGED_OUT = path.join(ROOT, "data/staged/place-price.ndjson");
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

function statusReport(targets) {
  const done = targets.filter((t) => fs.existsSync(path.join(RESULTS_DIR, `${t.id}.json`)));
  const found = done.filter((t) => {
    try { return JSON.parse(fs.readFileSync(path.join(RESULTS_DIR, `${t.id}.json`), "utf8")).found; }
    catch { return false; }
  });
  console.log(`대상 ${targets.length}곳 중 완료 ${done.length}곳 (${((done.length / targets.length) * 100).toFixed(1)}%)`);
  if (done.length) console.log(`완료분 중 적중 ${found.length}/${done.length} (${((found.length / done.length) * 100).toFixed(1)}%)`);
  console.log(`남은 곳 ${targets.length - done.length}곳`);
  return { done, targets };
}

async function callAgy(t, timeoutMs) {
  const prompt = `${t.road} 에 있는 '${t.name}'(${t.cat} 업종)의 대표 메뉴 1개 가격을 웹에서 실제로 검색해서 원 단위 숫자로 찾아라. 메뉴판이나 배달앱·블로그 후기의 가격 정보를 우선한다. 못 찾으면 found:false 로 정직하게 답하라. 지어내지 마라.`;
  return new Promise((resolve) => {
    execFile(
      "agy",
      ["--model", "gemini-3.8-flash-low", "--dangerously-skip-permissions", `-p=${prompt}`, "--json-schema", SCHEMA, "--output-format", "json"],
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

async function runOne(t) {
  let r = await callAgy(t, 150000);
  let attempts = 1;
  if (!r.ok) {
    r = await callAgy(t, 180000); // 한 번 더 — 타임아웃·일시 오류 재시도
    attempts = 2;
  }
  const out = r.ok
    ? { id: t.id, name: t.name, cat: t.cat, found: !!r.structured?.found, priceWon: r.structured?.priceWon ?? null,
        priceMenu: r.structured?.priceMenu ?? null, sources: r.structured?.sources ?? [], attempts,
        geminiTokens: r.geminiTokens, researchedAt: new Date().toISOString() }
    : { id: t.id, name: t.name, cat: t.cat, found: null, error: r.err, attempts,
        researchedAt: new Date().toISOString() }; // found:null = 조사 실패(못 찾음이 아니라 확인 못 함)
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
  const lines = [];
  for (const t of targets) {
    const f = path.join(RESULTS_DIR, `${t.id}.json`);
    if (!fs.existsSync(f)) continue;
    const r = JSON.parse(fs.readFileSync(f, "utf8"));
    if (!r.found) continue;
    lines.push(JSON.stringify({ placeId: r.id, priceWon: r.priceWon, priceMenu: r.priceMenu, sources: r.sources }));
  }
  fs.mkdirSync(path.dirname(STAGED_OUT), { recursive: true });
  fs.writeFileSync(STAGED_OUT, lines.join("\n") + (lines.length ? "\n" : ""));
  console.log(`→ ${path.relative(ROOT, STAGED_OUT)} (${lines.length}줄)`);
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
