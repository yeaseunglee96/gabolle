/**
 * 가격+narrative 조사 — 자동 청크 배정. `price-queue.mjs` 를 감싸서, 사람이 청크
 * 번호를 고르거나 axmap 선점 상태를 직접 안 봐도 되게 한다.
 *
 * 하는 일 (전부 자동):
 *   1. git pull --ff-only 로 최신 체크포인트를 받는다 (실패하면 멈춘다 — 직접 본다)
 *   2. `axmap status --json` 으로 지금 아무도 안 잡고 있고, 아직 안 끝난 청크를
 *      번호가 가장 앞선 것부터 찾는다
 *   3. 그 청크를 선점한다(`bigData/research/data/combined-results/chunk-<번호>`).
 *      🔴 2번은 "읽은 시점"의 스냅샷이라, 읽은 뒤 선점하기까지 몇 초 사이에 남이
 *      같은 번호를 먼저 잡을 수 있다 — 관문(axmap claim)이 막아 주지만(데이터는
 *      안 깨진다), 그걸 "더 이상 남은 청크가 없다"로 잘못 읽으면 자동화가 조용히
 *      멈춘다(진미리·이예승 실측, 2026-09-22). 그래서 거부되면 그 번호를 빼고
 *      "이 스크립트 안에서" 바로 다음 번호로 다시 시도한다 — 사람이 다시 실행할
 *      필요가 없다. 3번 연속 거부되면 "다들 나눠 가졌다"는 뜻으로 보고 그만둔다
 *   4. 선점에 성공하면 15분마다 자동으로 TTL 을 늘린다 (청크 하나가 30분 TTL 을
 *      넘길 수 있어서다)
 *   5. `price-queue.mjs --chunk <번호>` 를 그대로 돌린다
 *   6. 끝나면(성공이든 Ctrl+C 든) 반납하고, 커밋 명령을 안내한다
 *
 * 사용
 *   node research/work-chunk.mjs                    다음 빈 청크를 자동으로 잡아 돈다
 *   node research/work-chunk.mjs --model claude-sonnet-4-6   Gemini 할당량이 막혔을 때
 *
 * 🔴 한 창에서 이 스크립트를 여러 개 띄우면 `AXMAP_SESSION` 을 서로 다르게 줘야
 * axmap 이 서로를 막는다 — 안 그러면 (이름, 세션) 짝이 같아져 서로 안 겹치는
 * 것으로 본다. 이 스크립트는 `AXMAP_SESSION` 이 없으면 스스로 하나 만든다
 * (이미 있으면 그 값을 그대로 쓴다).
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

// axmap 은 (이름, 세션) 짝으로 임자를 가른다 — 이게 없으면 한 창의 다른 루프가
// 서로를 못 막는다. 이미 손으로 준 값이 있으면(예: 여러 창을 직접 돌릴 때) 그걸
// 존중하고, 없을 때만 스스로 만든다. 아래 모든 spawnSync 호출(price-queue.mjs
// 포함)은 process.env 를 그대로 물려받으므로 여기 한 번만 설정하면 된다.
process.env.AXMAP_SESSION ??= `work-chunk-${process.pid}-${Date.now()}`;
console.log(`(AXMAP_SESSION=${process.env.AXMAP_SESSION})`);

/**
 * 🔴 Windows 에서 shell:true 로 배열 인자를 넘기면 Node 가 공백으로 그냥 이어붙인다
 * (따옴표를 알아서 안 씌운다 — 실측: `--intent "가격조사 청크17 (자동배정)"` 이
 * 셋으로 쪼개져 axmap 이 뒤 둘을 엉뚱한 선점 경로로 먹었다). 공백·괄호가 있으면
 * 여기서 직접 따옴표를 씌운다.
 */
function shQuote(a) {
  return process.platform === "win32" && /[\s()]/.test(a) ? `"${String(a).replace(/"/g, '\\"')}"` : a;
}
function sh(cmd, args, opts = {}) {
  const qargs = process.platform === "win32" ? args.map(shQuote) : args;
  return spawnSync(cmd, qargs, { encoding: "utf8", shell: process.platform === "win32", ...opts });
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

function pickChunk(excluded) {
  for (let c = 1; c <= numChunks; c++) {
    if (excluded.has(c)) continue;
    const done = chunkDone(ids, c);
    if (done === null || done === true) continue;
    return c;
  }
  return null;
}

// claimedChunkNumbers() 는 "읽은 시점"의 스냅샷이라, 여기서부터 실제 claim 을
// 부르기까지의 몇 초 사이에 남이 같은 번호를 먼저 잡을 수 있다. 그래서 거부되면
// 그 번호만 제외 목록에 더하고 "이 프로세스 안에서" 바로 다음 번호로 다시 고른다
// — 밖에서 스크립트를 재실행할 필요가 없다. 3번 연속 거부는 "남이 먼저 잡았다"가
// 아니라 "남은 청크를 다들 나눠 가졌다"는 뜻이라 그만둔다.
const MAX_CONSECUTIVE_REJECTS = 3;
const excluded = new Set(claimed);
let picked = null;
let claimPath = null;
let claim = null;
let rejects = 0;

for (;;) {
  picked = pickChunk(excluded);
  if (picked == null) {
    if (rejects > 0) {
      console.log(`\n선점이 ${rejects}번 연속 거부됐고, 더 시도할 청크도 없습니다 — 남은 청크를 다들 나눠 가진 것 같습니다.`);
    } else {
      console.log("지금 자동으로 잡을 수 있는 청크가 없습니다 — 전부 끝났거나 전부 남이 잡고 있습니다.");
      console.log("`npx axmap-cli@latest status` 로 직접 확인해 보세요.");
    }
    process.exit(0);
  }

  claimPath = `${CLAIM_PREFIX}${picked}`;
  console.log(`\n청크 ${picked} 을 선점합니다 (${claimPath})...`);
  claim = sh("npx", [
    "-y", "axmap-cli@latest", "claim", claimPath,
    "--task", TASK, "--intent", `가격조사 청크${picked} (자동배정)`, "--ttl", TTL,
  ]);
  console.log(claim.stdout || "");

  if (claim.status === 0) break;

  console.error(claim.stderr || claim.stdout);
  excluded.add(picked);
  rejects += 1;
  if (rejects >= MAX_CONSECUTIVE_REJECTS) {
    console.error(`🔴 선점이 ${MAX_CONSECUTIVE_REJECTS}번 연속 거부됐습니다 — 남은 청크를 다른 사람들이 다 나눠 가진 것 같습니다. 그만둡니다.`);
    process.exit(0);
  }
  console.error(`🔴 선점 실패 — 방금 다른 사람이 먼저 잡았을 수 있습니다. 다음 번호로 자동으로 다시 시도합니다 (${rejects}/${MAX_CONSECUTIVE_REJECTS}).`);
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
console.log(`  git add -f bigData/research/data/combined-results`);
console.log(`  git commit -m "[S15P21E201-1414] chore: [Data] 가격 조사 청크 ${picked} 결과"`);
console.log(`  git push origin bigData/dev`);
