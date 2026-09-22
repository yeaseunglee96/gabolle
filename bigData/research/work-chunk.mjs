/**
 * 가격+narrative 조사 — 자동 청크 배정. `price-queue.mjs` 를 감싸서, 사람이 청크
 * 번호를 고르거나 axmap 선점 상태를 직접 안 봐도 되게 한다.
 *
 * 하는 일 (전부 자동):
 *   1. git pull --ff-only 로 최신 체크포인트를 받는다 (실패하면 멈춘다 — 직접 본다)
 *   2. `axmap status --json` 으로 지금 아무도 안 잡고 있고, 아직 안 끝난 청크를
 *      번호가 가장 앞선 것부터 찾는다
 *   3. 그 청크를 선점하고(`bigData/research/data/combined-results/chunk-<번호>`),
 *      15분마다 자동으로 TTL 을 늘린다 (청크 하나가 30분 TTL 을 넘길 수 있어서다)
 *   4. `price-queue.mjs --chunk <번호>` 를 그대로 돌린다
 *   5. 끝나면(성공이든 Ctrl+C 든) 반납하고, 커밋 명령을 안내한다
 *
 * 사용
 *   node research/work-chunk.mjs                    다음 빈 청크를 자동으로 잡아 돈다
 *   node research/work-chunk.mjs --model claude-sonnet-4-6   Gemini 할당량이 막혔을 때
 *
 * 🔴 이 스크립트는 git add·commit·push 는 안 한다 — 결과를 실제로 팀 저장소에
 * 올리는 것은 사람이 마지막에 확인하고 직접 한다 (끝나면 그 명령을 그대로 찍어 준다).
 */
import { spawnSync } from "node:child_process";
import fs from "node:fs";
import path from "node:path";
import { fileURLToPath } from "node:url";

const HERE = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(HERE, "..");
const SELECTED = path.join(ROOT, "data/staged/selected-2000.ndjson");
const RESULTS_DIR = path.join(HERE, "data/combined-results");
const CHUNK_SIZE = 50;
const TASK = "S15P21E201-1414";
const CLAIM_PREFIX = "bigData/research/data/combined-results/chunk-";
const TTL = "45m";
const RENEW_INTERVAL_MS = 15 * 60 * 1000;

const argv = process.argv.slice(2);
const str = (n, d) => { const i = argv.indexOf(n); return i < 0 ? d : argv[i + 1]; };
const MODEL = str("--model", null); // 안 주면 price-queue.mjs 기본값(gemini) 그대로

function sh(cmd, args, opts = {}) {
  return spawnSync(cmd, args, { encoding: "utf8", shell: process.platform === "win32", ...opts });
}

function loadTargetIds() {
  const lines = fs.readFileSync(SELECTED, "utf8").trim().split("\n");
  return lines
    .map((l) => JSON.parse(l))
    .filter((r) => r.pick.rule !== "tourapi-nonfood")
    .map((r) => r.id);
}

function isDone(id) {
  const f = path.join(RESULTS_DIR, `${id}.json`);
  if (!fs.existsSync(f)) return false;
  try { return JSON.parse(fs.readFileSync(f, "utf8")).found !== null; }
  catch { return false; }
}

/** true=다 끝남 · false=아직 남음 · null=범위 밖(청크 번호가 너무 큼) */
function chunkDone(ids, chunkNo) {
  const start = (chunkNo - 1) * CHUNK_SIZE;
  const scope = ids.slice(start, start + CHUNK_SIZE);
  if (scope.length === 0) return null;
  return scope.every(isDone);
}

function claimedChunkNumbers() {
  const r = sh("npx", ["-y", "axmap-cli@latest", "status", "--json"]);
  if (r.status !== 0) {
    console.error("🔴 axmap status 를 못 읽었습니다 — 선점 상태를 모르는 채로는 자동 배정을 안 합니다.");
    console.error(r.stderr || r.stdout);
    process.exit(1);
  }
  const data = JSON.parse(r.stdout);
  const claimed = new Set();
  for (const rec of data.active || []) {
    for (const p of rec.paths || []) {
      const m = /chunk-(\d+)$/.exec(p);
      if (m) claimed.add(Number(m[1]));
    }
  }
  return claimed;
}

console.log("git pull --ff-only 로 최신 체크포인트를 받습니다...");
const pull = sh("git", ["pull", "--ff-only"]);
console.log((pull.stdout || "") + (pull.stderr || ""));
if (pull.status !== 0) {
  console.error("🔴 git pull 이 실패했습니다 (충돌이거나 로컬에 커밋 안 된 변경이 있을 수 있습니다).");
  console.error("   직접 살펴보고 해결한 뒤 다시 실행하세요 — 자동으로 밀어붙이지 않습니다.");
  process.exit(1);
}

const ids = loadTargetIds();
const numChunks = Math.ceil(ids.length / CHUNK_SIZE);
const claimed = claimedChunkNumbers();

let picked = null;
for (let c = 1; c <= numChunks; c++) {
  if (claimed.has(c)) continue;
  const done = chunkDone(ids, c);
  if (done === null || done === true) continue;
  picked = c;
  break;
}

if (picked == null) {
  console.log("지금 자동으로 잡을 수 있는 청크가 없습니다 — 전부 끝났거나 전부 남이 잡고 있습니다.");
  console.log("`npx axmap-cli@latest status` 로 직접 확인해 보세요.");
  process.exit(0);
}

const claimPath = `${CLAIM_PREFIX}${picked}`;
console.log(`\n청크 ${picked} 을 선점합니다 (${claimPath})...`);
const claim = sh("npx", [
  "-y", "axmap-cli@latest", "claim", claimPath,
  "--task", TASK, "--intent", `가격조사 청크${picked} (자동배정)`, "--ttl", TTL,
]);
console.log(claim.stdout || "");
if (claim.status !== 0) {
  console.error(claim.stderr || claim.stdout);
  console.error("🔴 선점 실패 — 방금 다른 사람이 먼저 잡았을 수 있습니다. 다시 실행해 보세요.");
  process.exit(1);
}

let released = false;
function release() {
  if (released) return;
  released = true;
  console.log(`\n청크 ${picked} 을 반납합니다...`);
  sh("npx", ["-y", "axmap-cli@latest", "release", claimPath]);
}
process.on("SIGINT", () => { release(); process.exit(130); });
process.on("SIGTERM", () => { release(); process.exit(143); });

// 🔴 청크 하나(최대 50곳)가 TTL(45분)을 넘길 수 있어 15분마다 자동으로 늘린다.
// 안 하면 오래 걸리는 청크가 중간에 "풀린 것"으로 보여 다른 사람이 같은 번호를
// 다시 잡을 수 있다.
const renewTimer = setInterval(() => {
  console.log(`(자동 갱신) 청크 ${picked} 의 TTL 을 늘립니다...`);
  sh("npx", ["-y", "axmap-cli@latest", "renew", "--ttl", TTL]);
}, RENEW_INTERVAL_MS);

console.log(`\n청크 ${picked} 조사를 시작합니다...\n`);
const priceArgs = ["price-queue.mjs", "--chunk", String(picked)];
if (MODEL) priceArgs.push("--model", MODEL);
const proc = sh("node", priceArgs, { cwd: HERE, stdio: "inherit", encoding: undefined });

clearInterval(renewTimer);
release();

if (proc.status !== 0) {
  console.error(`\n🔴 조사가 오류로 끝났습니다 (종료 코드 ${proc.status}).`);
  console.error("   그래도 지금까지 끝낸 곳은 결과 파일로 남아 있습니다 — 이 스크립트를 다시 돌리면 이어서 됩니다.");
  process.exit(proc.status ?? 1);
}

console.log(`\n청크 ${picked} 끝. 결과를 커밋해서 올리세요:\n`);
console.log(`  git add -f bigData/research/data/combined-results/*.json`);
console.log(`  git commit -m "[S15P21E201-1414] chore: [Data] 가격 조사 청크 ${picked} 결과"`);
console.log(`  git push origin bigData/dev`);
