/**
 * 대기열이 **여러 프로세스가 동시에 달려들어도 안 깨지는가**를 시험한다.
 *
 * 이 시험이 잡으려는 사고는 하나다 — 🔴 **두 조사원이 같은 가게를 받는 것.**
 * 그러면 같은 웹 조사를 두 번 하고, 결과 파일을 서로 덮어쓴다.
 *
 * 검사 여섯 (판정은 종료 코드다):
 *   1  동시에 달려든 claim 이 **같은 가게를 두 번 안 내준다**
 *   2  기록 파일(events.ndjson)에 **찢어진 줄이 없다**
 *   3  시간 제한이 지나면 잡혔던 것이 **대기열로 돌아온다**
 *   4  결과 파일 없이 done 하면 **거부한다** (= "못 찾음" 도 적어야 끝난 것이다)
 *   5  남이 잡고 있는 것을 done 하면 **거부한다**
 *   6  전부 done 하면 남은 것이 0 이 된다
 *
 * 실행:  node research/queue.test.mjs
 */
import fs from "node:fs";
import os from "node:os";
import path from "node:path";
import { spawn, spawnSync } from "node:child_process";
import { fileURLToPath } from "node:url";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const QUEUE_MJS = path.join(HERE, "queue.mjs");
const TMP = fs.mkdtempSync(path.join(os.tmpdir(), "queue-test-"));
const ENV = { ...process.env, RESEARCH_DATA: TMP, RESEARCH_MIN_MS: "0" };  // 관문 ① 은 시험 폴더에서만 끌 수 있다

// 더 세게 돌려 보려면 환경변수로 올린다 — 예: Q_WORKERS=8 Q_ROUNDS=20 node research/queue.test.mjs
const ITEMS = Number(process.env.Q_ITEMS ?? 300);      // 대기열 크기
const WORKERS = Number(process.env.Q_WORKERS ?? 3);    // 동시에 도는 조사원 — 지시서가 말한 Haiku 3대
const ROUNDS = Number(process.env.Q_ROUNDS ?? 5);      // 몇 번씩 달려드나
const N = Number(process.env.Q_N ?? 10);               // 한 번에 몇 곳씩 잡나

let failed = 0;
function check(name, ok, detail = "") {
  console.log(`${ok ? "  통과" : "🔴 실패"}  ${name}${detail ? ` — ${detail}` : ""}`);
  if (!ok) failed++;
}
function run(args, opts = {}) {
  const r = spawnSync(process.execPath, [QUEUE_MJS, ...args], {
    env: ENV, encoding: "utf8", ...opts,
  });
  return { status: r.status, out: r.stdout ?? "", err: r.stderr ?? "" };
}
/** 여러 프로세스를 **한꺼번에** 띄운다. 순서대로 돌리면 경합이 안 일어나 시험이 무의미하다. */
function runAllAtOnce(argsList) {
  return Promise.all(argsList.map((args) => new Promise((resolve) => {
    const p = spawn(process.execPath, [QUEUE_MJS, ...args], { env: ENV });
    let out = "", err = "";
    p.stdout.on("data", (d) => { out += d; });
    p.stderr.on("data", (d) => { err += d; });
    p.on("close", (code) => resolve({ args, code, out, err }));
  })));
}

// ── 대기열을 만든다 ──────────────────────────────────────────────────────────
const src = path.join(TMP, "src.ndjson");
fs.writeFileSync(src, Array.from({ length: ITEMS }, (_, i) => JSON.stringify({
  id: `T${String(i).padStart(4, "0")}`,
  name: `시험가게${i}`,
  roadAddr: `부산광역시 시험구 시험로 ${i}`,
  gu: "시험구", hdong: "시험동",
  category: { name: "시험업종" },
  lon: 129, lat: 35,
})).join("\n") + "\n");
const init = run(["init", "--from", src, "--also", "", "--force"]);
if (init.status !== 0) { console.error(init.err || init.out); process.exit(1); }
console.log(`시험 폴더 ${TMP}\n대기열 ${ITEMS}곳 · 조사원 ${WORKERS}명 · ${ROUNDS}번씩 ${N}곳 동시 claim\n`);

// ── 검사 1 — 동시에 달려들어도 같은 가게를 두 번 안 내준다 ────────────────────
const owner = new Map();          // id → worker
const dupes = [];
let totalClaimed = 0;
for (let round = 0; round < ROUNDS; round++) {
  const results = await runAllAtOnce(
    Array.from({ length: WORKERS }, (_, w) => ["claim", "--worker", `w${w}`, "--n", String(N), "--ttl", "10m"]),
  );
  for (const r of results) {
    if (r.code !== 0) { check(`claim 이 실패했다 (${r.args.join(" ")})`, false, r.err.trim()); continue; }
    const worker = r.args[2];
    for (const line of r.out.split("\n")) {
      if (!line.trim()) continue;
      const it = JSON.parse(line);
      totalClaimed++;
      if (owner.has(it.id)) dupes.push({ id: it.id, first: owner.get(it.id), second: worker });
      else owner.set(it.id, worker);
    }
  }
}
check(
  "동시 claim 이 같은 가게를 두 번 안 내준다",
  dupes.length === 0,
  dupes.length
    ? `겹친 것 ${dupes.length}곳 (예: ${dupes[0].id} → ${dupes[0].first} · ${dupes[0].second})`
    : `${WORKERS * ROUNDS}번 동시 실행 · 내준 것 ${totalClaimed}곳 전부 다른 가게`,
);
check(
  "잡힌 수가 예상과 같다",
  totalClaimed === WORKERS * ROUNDS * N,
  `${totalClaimed} / ${WORKERS * ROUNDS * N}`,
);

// ── 검사 2 — 기록에 찢어진 줄이 없다 ─────────────────────────────────────────
{
  const raw = fs.readFileSync(path.join(TMP, "events.ndjson"), "utf8");
  const lines = raw.split("\n").filter((l) => l.trim());
  let broken = 0;
  for (const l of lines) { try { JSON.parse(l); } catch { broken++; } }
  check("기록(events.ndjson)에 찢어진 줄이 없다", broken === 0, `${lines.length}줄 중 ${broken}줄 깨짐`);
}

// ── 검사 3 — 시간이 지나면 대기열로 돌아온다 ─────────────────────────────────
const inProgress = () => Number(run(["status"]).out.match(/진행 중 (\d+)/)?.[1] ?? -1);
{
  const was = inProgress();
  const r = run(["claim", "--worker", "죽는조사원", "--n", "5", "--ttl", "1s"]);
  const ids = r.out.split("\n").filter(Boolean).map((l) => JSON.parse(l).id);
  const held = inProgress();
  check("잡은 직후에는 「진행 중」 으로 보인다", held === was + 5, `${was} → ${held}`);
  Atomics.wait(new Int32Array(new SharedArrayBuffer(4)), 0, 0, 1300);   // 시간 제한이 지나기를 기다린다
  const after = inProgress();
  check("시간 제한이 지나면 「진행 중」 에서 빠진다", after === was, `${held} → ${after}`);
  const again = run(["claim", "--worker", "다음조사원", "--n", "5", "--ttl", "10m"]);
  const againIds = again.out.split("\n").filter(Boolean).map((l) => JSON.parse(l).id);
  const returned = ids.filter((id) => againIds.includes(id));
  check(
    "시간 제한이 지난 것은 남이 다시 잡을 수 있다",
    returned.length === ids.length && ids.length === 5,
    `잡힌 ${ids.length}곳 중 ${returned.length}곳이 다시 나왔다`,
  );
}

// ── 검사 4·5 — done 의 두 가지 거부 ──────────────────────────────────────────
{
  const mine = run(["claim", "--worker", "정상조사원", "--n", "2", "--ttl", "10m"]);
  const [a, b] = mine.out.split("\n").filter(Boolean).map((l) => JSON.parse(l).id);

  const noFile = run(["done", "--worker", "정상조사원", "--id", a]);
  check("결과 파일 없이 done 하면 거부한다", noFile.status === 1, (noFile.err.split("\n")[1] ?? "").trim());

  fs.writeFileSync(path.join(TMP, "results", `${a}.json`), JSON.stringify({ id: a, found: false, queries: ["시험 검색어"] }));
  const withFile = run(["done", "--worker", "정상조사원", "--id", a]);
  check("결과 파일이 있으면 done 이 된다", withFile.status === 0, withFile.err.trim());

  fs.writeFileSync(path.join(TMP, "results", `${b}.json`), JSON.stringify({ id: b, found: false, queries: ["시험 검색어"] }));
  const stolen = run(["done", "--worker", "남의조사원", "--id", b]);
  check("남이 잡고 있는 것을 done 하면 거부한다", stolen.status === 1, (stolen.err.split("\n")[1] ?? "").trim());
}

// ── 검사 5.5 — 관문 둘 ────────────────────────────────────────────────────────
// 🔴 2026-09-09 에 조사원 둘이 검색 없이 결과 파일만 찍어내 2,283곳을 "끝냄" 으로 적었다.
//    그때 done 이 본 것은 "파일이 있는가" 하나뿐이었다. 관문이 둘 늘었고, 여기서 지킨다.
{
  const mine2 = run(["claim", "--worker", "관문시험", "--n", "2", "--ttl", "10m"]);
  const [c, d] = mine2.out.split("\n").filter(Boolean).map((l) => JSON.parse(l).id);

  // ① 무엇으로 검색했는지가 없으면 — 안 찾아본 것과 구별할 수 없다
  fs.writeFileSync(path.join(TMP, "results", `${c}.json`), JSON.stringify({ id: c, found: false }));
  const noQ = run(["done", "--worker", "관문시험", "--id", c]);
  check("queries 가 비면 done 을 거부한다", noQ.status === 1, (noQ.err.split("\n")[1] ?? "").trim());

  // 같은 파일에 검색어만 넣으면 통과한다 — 관문이 다른 이유로 막는 게 아님을 보인다
  fs.writeFileSync(path.join(TMP, "results", `${c}.json`), JSON.stringify({ id: c, found: false, queries: ["부산 무슨무슨식당"] }));
  const withQ = run(["done", "--worker", "관문시험", "--id", c]);
  check("검색어를 적으면 done 이 된다", withQ.status === 0, withQ.err.trim());

  // ② 잡자마자 끝냈다고 적으면 — 실제로 검색했으면 나올 수 없는 시간이다
  fs.writeFileSync(path.join(TMP, "results", `${d}.json`), JSON.stringify({ id: d, found: false, queries: ["부산 무슨무슨식당"] }));
  const tooFast = run(["done", "--worker", "관문시험", "--id", d], {
    env: { ...ENV, RESEARCH_MIN_MS: "60000" },   // 가게 하나에 60초를 요구하게 만든다
  });
  check("잡자마자 done 하면 거부한다", tooFast.status === 1, (tooFast.err.split("\n")[1] ?? "").trim());
}

// ── 검사 6 — 전부 done 하면 남은 것이 0 ──────────────────────────────────────
{
  for (;;) {
    const r = run(["claim", "--worker", "마무리", "--n", "50", "--ttl", "30m"]);
    const ids = r.out.split("\n").filter(Boolean).map((l) => JSON.parse(l).id);
    if (!ids.length) break;
    for (const id of ids) fs.writeFileSync(path.join(TMP, "results", `${id}.json`), JSON.stringify({ id, found: false, queries: ["시험 검색어"] }));
    run(["done", "--worker", "마무리", "--ids", ids.join(",")]);
  }
  // 앞 검사에서 다른 조사원이 잡은 것들은 시간 제한이 남아 있으므로, 그것까지 마무리한다
  const st = run(["status"]).out;
  const m = st.match(/남음 (\d+) · 진행 중 (\d+) · 끝남 (\d+)/);
  check(
    "전부 처리하면 남은 것이 0 이 된다",
    m && Number(m[1]) === 0,
    st.split("\n")[0],
  );
  const done = m ? Number(m[3]) : 0;
  check("끝난 것 + 진행 중 = 대기열 전체", m && done + Number(m[2]) === ITEMS, `${done} + ${m?.[2]} / ${ITEMS}`);
}

// ── 뒷정리 ───────────────────────────────────────────────────────────────────
fs.rmSync(TMP, { recursive: true, force: true });
console.log(failed ? `\n🔴 ${failed}개 실패` : `\n전부 통과`);
process.exit(failed ? 1 : 0);
