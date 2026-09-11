/**
 * 조사가 **실제로 이루어졌는지** 를 기록으로 되짚는다.
 *
 * ── 왜 있나 ────────────────────────────────────────────────────────────────────
 * 2026-09-09 새벽, 조사원 셋 중 둘이 **웹 검색을 하지 않고** 결과 파일만 찍어냈다.
 * 파일은 2,302개가 생겼고 대기열은 98.6% 완료로 보였다. 전부 `found: false` 였다.
 *
 * 그때 `done` 이 검사한 것은 **"결과 파일이 있는가"** 하나뿐이었다. 스크립트로 파일을
 * 한꺼번에 만들면 그 검사는 통과한다. 🔴 **파일의 존재는 조사의 증거가 아니다.**
 *
 * 이 프로그램은 흉내낼 수 없는 것을 본다 — **시간**. 가게 하나를 잡고 끝냈다고 적기까지
 * 몇 초가 흘렀는가. 웹을 정말 뒤졌으면 시간이 든다. 0.01초는 아무것도 안 한 것이다.
 *
 * ── 쓰기 ───────────────────────────────────────────────────────────────────────
 *   node research/audit.mjs              사람이 읽는 보고서
 *   node research/audit.mjs --ids        의심스러운 id 만 한 줄씩 (다른 명령에 넘길 때)
 *   node research/audit.mjs --floor 15   가게 하나당 최소 몇 초로 볼 것인가 (기본 15)
 *
 * 종료 코드는 0 이다. 이건 **판정이 아니라 자료**다 — 막는 것은 queue.mjs 의 관문이 한다.
 */
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const DATA = process.env.RESEARCH_DATA ?? path.join(HERE, "data");
const EVENTS = path.join(DATA, "events.ndjson");
const RESULTS = path.join(DATA, "results");

const argv = process.argv.slice(2);
const idsOnly = argv.includes("--ids");
const fi = argv.indexOf("--floor");
const FLOOR_S = fi >= 0 && argv[fi + 1] ? Number(argv[fi + 1]) : 15;

function readNdjson(f) {
  if (!fs.existsSync(f)) return [];
  return fs.readFileSync(f, "utf8").split("\n").filter(Boolean)
    .map((l) => { try { return JSON.parse(l); } catch { return null; } }).filter(Boolean);
}

// 잡은 시각을 id 마다 기억해 두고, 끝났다고 적힌 시각과의 차를 묶음 크기로 나눈다.
// 🔴 묶음으로 나누는 이유: 10곳을 한 번에 잡고 한 번에 끝냈다고 적으면 걸린 시간은
//    10곳이 나눠 쓴 것이다. 나누지 않으면 큰 묶음일수록 성실해 보인다.
const claimAt = new Map();
const rows = [];
for (const ev of readNdjson(EVENTS)) {
  if (!Array.isArray(ev.ids)) continue;
  if (ev.type === "claim") {
    for (const id of ev.ids) claimAt.set(id, Date.parse(ev.ts));
  } else if (ev.type === "done") {
    const per = (Date.parse(ev.ts) - Math.min(...ev.ids.map((i) => claimAt.get(i) ?? Date.parse(ev.ts)))) / ev.ids.length;
    for (const id of ev.ids) rows.push({ id, worker: ev.worker, sec: per / 1000 });
  }
}

// 결과 파일이 증거를 들고 있는가 — sources 든 queries 든 하나는 있어야 한다
function evidence(id) {
  const f = path.join(RESULTS, `${id}.json`);
  if (!fs.existsSync(f)) return { file: false, ev: false };
  try {
    const d = JSON.parse(fs.readFileSync(f, "utf8"));
    return { file: true, ev: (d.sources?.length ?? 0) > 0 || (d.queries?.length ?? 0) > 0, found: !!d.found };
  } catch { return { file: true, ev: false }; }
}

const suspect = [], good = [];
for (const r of rows) {
  const e = evidence(r.id);
  (r.sec < FLOOR_S && !e.ev ? suspect : good).push({ ...r, ...e });
}

if (idsOnly) {
  for (const r of suspect) console.log(r.id);
  process.exit(0);
}

const byWorker = new Map();
for (const r of rows) {
  const e = evidence(r.id);
  const w = byWorker.get(r.worker) ?? { n: 0, secs: [], ev: 0, found: 0, noFile: 0 };
  w.n++; w.secs.push(r.sec);
  if (e.ev) w.ev++;
  if (e.found) w.found++;
  if (!e.file) w.noFile++;
  byWorker.set(r.worker, w);
}

const med = (a) => { const s = [...a].sort((x, y) => x - y); return s.length ? s[s.length >> 1] : 0; };
console.log(`조사 기록 되짚기 — "끝냄" 으로 적힌 ${rows.length}곳\n`);
console.log(`가게 하나에 실제로 쓴 시간이 ${FLOOR_S}초 미만이고 증거도 없는 것을 의심스럽다고 본다.\n`);
console.log(`${"조사원".padEnd(12)}${"끝냄".padStart(8)}${"하나당 중앙값".padStart(16)}${"증거 있음".padStart(12)}${"찾음".padStart(8)}${"파일 없음".padStart(11)}`);
for (const [w, v] of [...byWorker].sort()) {
  console.log(`${w.padEnd(12)}${String(v.n).padStart(8)}${(med(v.secs).toFixed(2) + "초").padStart(16)}${String(v.ev).padStart(12)}${String(v.found).padStart(8)}${String(v.noFile).padStart(11)}`);
}
console.log(`\n의심스러운 것 ${suspect.length}곳 · 남길 만한 것 ${good.length}곳`);
if (suspect.length) {
  console.log(`\n🔴 이 ${suspect.length}곳은 조사된 적이 없다고 보아야 합니다.`);
  console.log(`   id 목록:  node research/audit.mjs --ids`);
}
