/**
 * 조사 대기열 — **여러 에이전트가 같은 가게를 두 번 조사하지 않게** 만드는 장치.
 *
 * 선정된 2,000곳을 여러 조사원(사람이든 AI 든)이 나눠 맡는다. 조사원은 서로를 모르고
 * 동시에 돈다. 그래서 **누가 무엇을 잡았는지**를 한 곳에 적고, 잡은 것은 남이 못 잡게 한다.
 *
 * ── 명령 ────────────────────────────────────────────────────────────────────────
 *   node research/queue.mjs init                       selected-2000 으로 대기열을 만든다
 *   node research/queue.mjs claim --worker haiku-1 --n 10   아직 아무도 안 잡은 10곳을 잡는다
 *   node research/queue.mjs done  --worker haiku-1 --id <id>   끝났다고 표시한다
 *   node research/queue.mjs status                     남은 것 · 진행 중 · 끝난 것
 *
 * ── 겹침을 어떻게 막나 — 셋 ────────────────────────────────────────────────────
 *
 * **① 잠금**(lock — *한 번에 한 프로세스만 들어가게 하는 문*).
 *    `fs.openSync(path, "wx")` 는 **파일이 이미 있으면 실패한다.** 만드는 것과 검사하는
 *    것이 운영체제 안에서 한 동작이라 두 프로세스가 동시에 성공할 수 없다. 그래서 이걸
 *    문으로 쓴다. 대기열을 읽고 쓰는 동안만 문을 잠그고, 끝나면 파일을 지운다.
 *    프로세스가 죽어서 문이 잠긴 채 남으면 `STALE_LOCK_MS` 뒤에 다음 사람이 부순다.
 *
 * **② 덧붙이기만 하는 기록**(append-only log — *고치지 않고 뒤에 한 줄씩 붙이는 장부*).
 *    상태를 파일에 덮어쓰지 않는다. `events.ndjson` 에 "누가 무엇을 잡았다 / 끝냈다" 를
 *    한 줄씩 붙이고, 지금 상태는 그 줄을 처음부터 다시 읽어 계산한다.
 *    🔴 **덮어쓰기가 없으면 반쯤 쓰다 죽어도 앞의 기록이 안 깨진다.** 마지막 한 줄만
 *    잘릴 수 있고, 깨진 줄은 읽을 때 버린다.
 *
 * **③ 시간 제한**(TTL — *Time To Live. 잡은 것이 살아 있는 시간*).
 *    기본 30분. 그 안에 `done` 이 안 오면 그 자리는 **다시 대기열로 돌아온다.**
 *    🔴 조사원이 죽어도 그 10곳이 영영 안 잡히는 일이 없어야 한다.
 *
 * ── 파일 ────────────────────────────────────────────────────────────────────────
 *   data/queue.ndjson    대기열. init 이 한 번 쓰고 그 뒤로 안 고친다
 *   data/events.ndjson   claim/done 기록. 여기만 자란다
 *   data/results/<id>.json  조사 결과. 가게 하나에 파일 하나 (README.md 가 칸을 정한다)
 *   data/.lock           잠금. 돌고 있는 동안만 있다
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const HERE = path.dirname(fileURLToPath(import.meta.url));
// 시험할 때 다른 폴더를 쓰게 한다 (queue.test.mjs). 평소에는 research/data 다.
const DATA = process.env.RESEARCH_DATA ?? path.join(HERE, "data");
const QUEUE = path.join(DATA, "queue.ndjson");
const EVENTS = path.join(DATA, "events.ndjson");
const RESULTS = path.join(DATA, "results");
const LOCK = path.join(DATA, ".lock");
const SELECTED = path.join(HERE, "..", "data", "staged", "selected-2000.ndjson");
const TRUTH_LINKED = path.join(HERE, "..", "data", "staged", "truth-linked.ndjson");

const DEFAULT_TTL_MS = 30 * 60 * 1000;   // 30분
const STALE_LOCK_MS = 30 * 1000;         // 잠긴 채 30초 넘게 있으면 죽은 것으로 본다
const LOCK_WAIT_MS = 20 * 1000;          // 문 앞에서 최대 20초 기다린다
// 🔴 가게 하나에 최소 이만큼은 써야 "끝냄" 으로 적을 수 있다. 아래 cmdDone 의 관문 ①
//    시험은 15초씩 기다릴 수 없으므로 낮출 수 있게 열어 두되, **시험용 폴더에서만** 먹는다.
//    RESEARCH_DATA 없이 이 값을 내리면 무시된다 — 진짜 데이터에서 관문을 끌 수 없어야 한다.
const MIN_MS_PER_STORE =
  process.env.RESEARCH_DATA && process.env.RESEARCH_MIN_MS
    ? Number(process.env.RESEARCH_MIN_MS)
    : 15 * 1000;                         // 15초

// ── 인자 ─────────────────────────────────────────────────────────────────────
const argv = process.argv.slice(2);
const cmd = argv[0];
function opt(name, dflt = undefined) {
  const i = argv.indexOf(name);
  return i >= 0 && argv[i + 1] !== undefined ? argv[i + 1] : dflt;
}
function optAll(name) {
  const out = [];
  for (let i = 0; i < argv.length; i++) if (argv[i] === name && argv[i + 1]) out.push(argv[i + 1]);
  return out;
}
const has = (name) => argv.includes(name);

/** "30m" · "2h" · "900s" · "30" (분) 을 밀리초로 */
function parseTtl(s) {
  if (s === undefined) return DEFAULT_TTL_MS;
  const m = String(s).trim().match(/^(\d+(?:\.\d+)?)\s*(ms|s|m|h)?$/i);
  if (!m) die(`--ttl 을 못 읽었습니다: ${s} (보기: 30m · 2h · 900s)`);
  const n = Number(m[1]);
  const unit = (m[2] ?? "m").toLowerCase();
  return n * { ms: 1, s: 1000, m: 60000, h: 3600000 }[unit];
}
function humanMs(ms) {
  if (ms < 0) ms = 0;
  const s = Math.round(ms / 1000);
  if (s < 60) return `${s}초`;
  const m = Math.floor(s / 60);
  if (m < 60) return `${m}분 ${s % 60}초`;
  return `${Math.floor(m / 60)}시간 ${m % 60}분`;
}
function die(msg) {
  console.error(`🔴 ${msg}`);
  process.exit(1);
}

// ── ① 잠금 ───────────────────────────────────────────────────────────────────
function sleepSync(ms) {
  Atomics.wait(new Int32Array(new SharedArrayBuffer(4)), 0, 0, ms);
}
/** 대기열을 읽고 쓰는 동안만 문을 잠근다. 끝나면 반드시 연다. */
function withLock(fn) {
  fs.mkdirSync(DATA, { recursive: true });
  const start = Date.now();
  let fd = null;
  let lastCode = null;
  for (;;) {
    try {
      fd = fs.openSync(LOCK, "wx");   // 🔴 이미 있으면 실패한다 — 만들기와 검사가 한 동작이다
      break;
    } catch (e) {
      // 🔴 윈도우는 남이 같은 파일을 지우는 중이면 EEXIST 가 아니라 **EPERM** 을 던진다.
      //    (queue.test.mjs 를 조사원 8명으로 돌렸을 때 실제로 났다. 3명에서는 안 났다 —
      //     경합이 드물어서지 안전해서가 아니다.) 넷 다 "남이 갖고 있다" 로 본다.
      if (!["EEXIST", "EPERM", "EACCES", "EBUSY"].includes(e.code)) throw e;
      lastCode = e.code;
      let stale = false;
      try {
        stale = Date.now() - fs.statSync(LOCK).mtimeMs > STALE_LOCK_MS;
      } catch {
        continue;   // 그 사이 남이 지웠다 — 다시 시도
      }
      if (stale) {
        try { fs.unlinkSync(LOCK); } catch { /* 남이 먼저 지웠다 */ }
        continue;
      }
      if (Date.now() - start > LOCK_WAIT_MS)
        die(
          `잠금(${LOCK})을 ${humanMs(LOCK_WAIT_MS)} 동안 못 얻었습니다 (마지막 오류 ${lastCode}). ` +
            `다른 조사원이 오래 붙잡고 있거나, 그 파일에 쓸 권한이 없습니다`,
        );
      sleepSync(15 + Math.floor(Math.random() * 40));   // 서로 어긋나게 기다린다
    }
  }
  // 🔴 `fn` 안에서 `process.exit()` 를 부르면 `finally` 가 안 돈다 — 그러면 문이 잠긴 채
  //    남아서 **다음 조사원이 20초를 기다리다 죽는다.** 그래서 종료 갈고리에도 건다.
  //    (queue.test.mjs 가 이 사고를 실제로 잡았다.)
  const unlock = () => {
    try { fs.closeSync(fd); } catch { /* 이미 닫혔다 */ }
    try { fs.unlinkSync(LOCK); } catch { /* 이미 지워졌다 */ }
  };
  process.on("exit", unlock);
  try {
    fs.writeSync(fd, `pid ${process.pid} · ${new Date().toISOString()}\n`);
    return fn();
  } finally {
    process.off("exit", unlock);
    unlock();
  }
}

// ── ② 기록 ───────────────────────────────────────────────────────────────────
function readNdjson(file) {
  if (!fs.existsSync(file)) return [];
  const out = [];
  let broken = 0;
  for (const line of fs.readFileSync(file, "utf8").split("\n")) {
    if (!line.trim()) continue;
    try { out.push(JSON.parse(line)); } catch { broken++; }   // 죽다 만 마지막 줄은 버린다
  }
  if (broken) console.error(`  ⚠ ${path.basename(file)} 에서 못 읽은 줄 ${broken}개 (버립니다)`);
  return out;
}
function appendEvent(ev) {
  fs.appendFileSync(EVENTS, JSON.stringify(ev) + "\n");
}

/**
 * 지금 상태를 기록으로부터 다시 계산한다.
 * 상태는 셋뿐이다 — `todo`(아무도 안 잡음) · `claimed`(잡혀 있음) · `done`(끝남).
 */
function readState() {
  const items = readNdjson(QUEUE);
  const byId = new Map(items.map((it) => [it.id, it]));
  const st = new Map([...byId.keys()].map((id) => [id, { status: "todo" }]));
  for (const ev of readNdjson(EVENTS)) {
    if (!Array.isArray(ev.ids)) continue;
    for (const id of ev.ids) {
      const s = st.get(id);
      if (!s) continue;                       // 대기열에 없는 id — 무시
      if (ev.type === "claim") {
        if (s.status === "done") continue;    // 끝난 것은 다시 안 잡힌다
        s.status = "claimed";
        s.worker = ev.worker;
        s.claimedAt = ev.ts;
        s.ttlMs = ev.ttlMs ?? DEFAULT_TTL_MS;
      } else if (ev.type === "done") {
        s.status = "done";
        s.worker = ev.worker;
        s.doneAt = ev.ts;
      }
    }
  }
  // ③ 시간이 지난 claim 은 대기열로 돌려보낸다
  const now = Date.now();
  let expired = 0;
  for (const s of st.values()) {
    if (s.status !== "claimed") continue;
    const left = s.ttlMs - (now - Date.parse(s.claimedAt));
    if (left <= 0) { s.status = "todo"; s.expiredFrom = s.worker; expired++; }
    else s.msLeft = left;
  }
  return { items, byId, st, expired };
}

// ── init ─────────────────────────────────────────────────────────────────────
function cmdInit() {
  const from = opt("--from", SELECTED);
  const also = opt("--also", TRUTH_LINKED);
  if (!fs.existsSync(from))
    die(`${from} 이 없습니다. 먼저:  node process/select-2000.mjs`);
  if (fs.existsSync(QUEUE) && !has("--force")) {
    const { st } = readState();
    const done = [...st.values()].filter((s) => s.status === "done").length;
    die(
      `대기열이 이미 있습니다 (끝난 것 ${done}곳). 지우고 다시 만들려면 --force 를 붙이세요.\n` +
        `   🔴 --force 는 진행 기록(events.ndjson)도 지웁니다. 조사 결과 파일은 안 지웁니다.`,
    );
  }
  fs.mkdirSync(RESULTS, { recursive: true });
  const src = readNdjson(from);
  // 🔴 정답지에 실린 집도 대기열에 넣는다 — 조사가 끝난 뒤 **조사원을 채점**하기 위해서다
  //    (process/truth-link.mjs · README.md "조사를 채점한다"). 2,000곳 밖에 있는 것만 더한다.
  const extra = also && fs.existsSync(also) ? readNdjson(also) : [];
  if (also && !extra.length && also === TRUTH_LINKED)
    console.error(`  ⚠ ${path.relative(process.cwd(), also)} 이 없습니다 — 채점용 정답지가 대기열에 안 들어갑니다`);

  const seen = new Set();
  const lines = [];
  let nExtra = 0;
  for (const [list, isExtra] of [[src, false], [extra, true]]) {
    for (const r of list) {
      if (seen.has(r.id)) continue;   // 같은 가게가 두 줄이면 하나만 — 겹침의 첫 번째 원인
      seen.add(r.id);
      if (isExtra) nExtra++;
      // 🔴 여기 적는 칸은 **조사원이 보는 전부**다. 어느 규칙으로 뽑혔는지도, 정답지인지도
      //    적지 않는다 — 알면 그쪽으로 답을 맞춘다.
      lines.push(JSON.stringify({
        id: r.id,
        name: r.name,
        branch: r.branch ?? null,
        roadAddr: r.roadAddr,
        gu: r.gu,
        hdong: r.hdong,
        category: r.category?.name ?? null,
        lon: r.lon,
        lat: r.lat,
      }));
    }
  }
  fs.writeFileSync(QUEUE, lines.join("\n") + "\n");
  fs.writeFileSync(EVENTS, "");
  console.log(
    `대기열 ${lines.length}곳 → ${path.relative(process.cwd(), QUEUE)}` +
      (nExtra ? `  (선정 ${lines.length - nExtra} + 채점용 ${nExtra})` : ""),
  );
  const dropped = src.length + extra.length - lines.length;
  if (dropped) console.log(`  (원본 ${src.length + extra.length}줄 중 겹치는 id ${dropped}줄을 지웠습니다)`);
}

// ── claim ────────────────────────────────────────────────────────────────────
function cmdClaim() {
  const worker = opt("--worker");
  if (!worker) die("--worker <이름> 이 필요합니다. 조사원마다 다른 이름을 쓰세요");
  const n = Number(opt("--n", "10"));
  if (!Number.isInteger(n) || n < 1) die("--n 은 1 이상의 정수여야 합니다");
  const ttlMs = parseTtl(opt("--ttl"));

  const claimed = withLock(() => {
    const { byId, st } = readState();
    if (!byId.size) die(`대기열이 비어 있습니다. 먼저:  node research/queue.mjs init`);
    const pick = [];
    for (const [id, s] of st) {
      if (pick.length >= n) break;
      if (s.status === "todo") pick.push(id);
    }
    if (!pick.length) return [];
    appendEvent({
      ts: new Date().toISOString(),
      type: "claim",
      worker,
      ttlMs,
      ids: pick,
      pid: process.pid,
    });
    return pick.map((id) => byId.get(id));
  });

  if (!claimed.length) {
    console.error(`잡을 것이 없습니다 — 남은 것이 0곳이거나 전부 다른 조사원이 잡고 있습니다.`);
    console.error(`  지금 상태:  node research/queue.mjs status`);
    process.exit(0);
  }
  console.error(
    `${worker} 가 ${claimed.length}곳을 잡았습니다 (시간 제한 ${humanMs(ttlMs)}). ` +
      `끝나면 반드시 done 을 부르세요.`,
  );
  for (const it of claimed) console.log(JSON.stringify(it));
}

// ── done ─────────────────────────────────────────────────────────────────────
function cmdDone() {
  const worker = opt("--worker");
  if (!worker) die("--worker <이름> 이 필요합니다");
  const ids = [...optAll("--id"), ...String(opt("--ids", "")).split(",").map((s) => s.trim()).filter(Boolean)];
  if (!ids.length) die("--id <id> 가 필요합니다 (여러 개면 --id 를 반복하거나 --ids a,b,c)");

  withLock(() => {
    const { byId, st } = readState();
    const ok = [];
    const problems = [];
    for (const id of ids) {
      const s = st.get(id);
      if (!s) { problems.push(`${id} — 대기열에 없는 id 입니다`); continue; }
      if (s.status === "done") {
        console.error(`  · ${id} 는 이미 끝난 것으로 되어 있습니다 (${s.worker}) — 넘어갑니다`);
        continue;
      }
      if (s.status === "claimed" && s.worker !== worker) {
        problems.push(
          `${id} 는 ${s.worker} 가 잡고 있습니다 (${humanMs(s.msLeft)} 남음). ` +
            `남의 자리를 끝났다고 적으면 안 됩니다`,
        );
        continue;
      }
      // 🔴 결과 파일이 없으면 끝난 것이 아니다. "못 찾았다" 도 파일로 적어야 한다 (README.md)
      const rf = path.join(RESULTS, `${id}.json`);
      if (!fs.existsSync(rf)) {
        problems.push(
          `${id} (${byId.get(id)?.name}) — 결과 파일이 없습니다: ${path.relative(process.cwd(), rf)}\n` +
            `      찾은 게 없어도 "found": false 로 파일을 남겨야 합니다 (research/README.md)`,
        );
        continue;
      }
      // 🔴 관문 ① — **시간**. 잡자마자 끝났다고 적을 수 없다.
      //    2026-09-09 새벽, 조사원 둘이 검색을 건너뛰고 **가게 하나당 0.01초**로 2,220곳을
      //    "끝냄" 으로 적었다. 결과 파일도 한꺼번에 찍어냈기 때문에 바로 위의 "파일이
      //    있는가" 검사는 그대로 통과했다. 🔴 **파일이 있다는 것은 조사했다는 증거가 아니다.**
      //    실제로 검색하면 시간이 든다. 시간은 흉내낼 수 없다. 그래서 시간을 본다.
      //    (그날의 기록: node research/audit.mjs)
      if (s.claimedAt) {
        const spent = Date.now() - Date.parse(s.claimedAt);
        const need = MIN_MS_PER_STORE * ids.length;
        if (spent < need) {
          problems.push(
            `${id} (${byId.get(id)?.name}) — 잡은 지 ${Math.round(spent / 1000)}초 만에 끝났다고 적으려 했습니다. ` +
              `${ids.length}곳이면 ${Math.round(need / 1000)}초는 걸려야 합니다
` +
              `      🔴 실제로 검색했다면 이 시간이 안 나옵니다. 한 번에 적게 잡으세요 (--n 3)`,
          );
          continue;
        }
      }

      // 🔴 관문 ② — **증거**. "못 찾았다" 는 찾아보고 나서만 할 수 있는 말이다.
      //    무엇으로 검색했는지가 안 적혀 있으면, 안 찾아본 것과 구별할 방법이 없다.
      let parsed = null;
      try { parsed = JSON.parse(fs.readFileSync(rf, "utf8")); } catch { /* 아래에서 걸린다 */ }
      if (!parsed || !Array.isArray(parsed.queries) || !parsed.queries.length) {
        problems.push(
          `${id} (${byId.get(id)?.name}) — 결과 파일에 "queries" 가 비어 있습니다
` +
            `      무엇으로 검색했는지를 적으세요: "queries": ["부산 수영구 팁시펍", "팁시펍 광안리 후기"]
` +
            `      🔴 검색하지 않고 적은 "못 찾음" 은 데이터가 아니라 빈 칸입니다`,
        );
        continue;
      }

      if (s.status === "todo" && s.expiredFrom)
        console.error(`  · ${id} 는 시간이 지나 대기열로 돌아갔던 것입니다 (원래 ${s.expiredFrom}) — 받습니다`);
      ok.push(id);
    }
    if (ok.length)
      appendEvent({ ts: new Date().toISOString(), type: "done", worker, ids: ok, pid: process.pid });
    console.error(`${worker}: 끝남으로 적은 것 ${ok.length}곳`);
    if (problems.length) {
      console.error(`🔴 적지 못한 것 ${problems.length}곳:`);
      for (const p of problems) console.error(`   · ${p}`);
      process.exit(1);
    }
  });
}

// ── status ───────────────────────────────────────────────────────────────────
function cmdStatus() {
  const { items, byId, st } = readState();
  if (!items.length) {
    console.log(`대기열이 없습니다. 먼저:  node research/queue.mjs init`);
    return;
  }
  const todo = [], claimed = [], done = [];
  for (const [id, s] of st) (s.status === "done" ? done : s.status === "claimed" ? claimed : todo).push(id);
  const pct = ((done.length / items.length) * 100).toFixed(1);
  console.log(`대기열 ${items.length}곳 — 남음 ${todo.length} · 진행 중 ${claimed.length} · 끝남 ${done.length} (${pct}%)`);

  const back = todo.filter((id) => st.get(id).expiredFrom);
  if (back.length) console.log(`  시간이 지나 되돌아온 것 ${back.length}곳 (이것도 "남음" 에 들어 있습니다)`);

  // 조사원별
  const byWorker = new Map();
  for (const [id, s] of st) {
    if (!s.worker) continue;
    const key = s.worker;
    if (!byWorker.has(key)) byWorker.set(key, { claimed: 0, done: 0 });
    if (s.status === "claimed") byWorker.get(key).claimed++;
    else if (s.status === "done") byWorker.get(key).done++;
  }
  if (byWorker.size) {
    console.log(`\n조사원`);
    for (const [w, c] of [...byWorker.entries()].sort((a, b) => b[1].done - a[1].done))
      console.log(`  ${w.padEnd(14)} 진행 중 ${String(c.claimed).padStart(4)} · 끝냄 ${String(c.done).padStart(5)}`);
  }

  // 진행 중인 것 중 가장 오래된 것
  const oldest = claimed
    .map((id) => ({ id, s: st.get(id) }))
    .sort((a, b) => a.s.msLeft - b.s.msLeft)
    .slice(0, 5);
  if (oldest.length) {
    console.log(`\n곧 시간이 지나는 것`);
    for (const { id, s } of oldest)
      console.log(`  ${byId.get(id)?.name ?? id} — ${s.worker} · ${humanMs(s.msLeft)} 남음`);
  }

  // 결과 파일이 대기열과 어긋나지 않는지
  const files = fs.existsSync(RESULTS) ? fs.readdirSync(RESULTS).filter((f) => f.endsWith(".json")) : [];
  const orphan = files.filter((f) => !byId.has(f.replace(/\.json$/, "")));
  console.log(`\n결과 파일 ${files.length}개` + (orphan.length ? ` · 🔴 대기열에 없는 것 ${orphan.length}개` : ""));
  const doneNoFile = done.filter((id) => !fs.existsSync(path.join(RESULTS, `${id}.json`)));
  if (doneNoFile.length) console.log(`  🔴 끝났다고 적혔는데 결과 파일이 없는 것 ${doneNoFile.length}개`);
}

// ── 실행 ─────────────────────────────────────────────────────────────────────
const COMMANDS = { init: cmdInit, claim: cmdClaim, done: cmdDone, status: cmdStatus };
if (!cmd || !COMMANDS[cmd]) {
  console.error(
    `사용법:\n` +
      `  node research/queue.mjs init [--from <ndjson>] [--force]\n` +
      `  node research/queue.mjs claim --worker <이름> [--n 10] [--ttl 30m]\n` +
      `  node research/queue.mjs done  --worker <이름> --id <id> [--id <id> …]\n` +
      `  node research/queue.mjs status\n`,
  );
  process.exit(cmd ? 1 : 0);
}
COMMANDS[cmd]();
