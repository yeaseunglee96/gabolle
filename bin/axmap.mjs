#!/usr/bin/env node
/**
 * axMap CLI - 부수효과(git, fs, 시계)를 담당하는 층.
 * 판정 로직은 전부 src/protocol.mjs 의 순수 함수에 있다.
 *
 * 두 개의 관문이 있다.
 *   관문 1 (직렬화) : git push 의 ref CAS. 공짜로 따라오며 끌 수 없다.
 *                     실패는 거절이 아니라 "최신 상태 받아서 다시 하라"는 뜻.
 *   관문 2 (판정)   : checkOverlap(). 우리가 직접 묻는다.
 *                     "요청한 경로가 남이 잡은 경로와 겹치는가?"
 */

import { spawnSync } from 'node:child_process'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import {
  applyClaim,
  applyRelease,
  applyRenew,
  coversPath,
  formatBlocks,
  humanDuration,
  normalizePath,
  activeClaims,
  claimExpiresAt,
  myActiveClaim,
  agentNameError,
  claimPathError,
} from '../src/protocol.mjs'
import { auditLedger, formatAudit } from '../src/invariants.mjs'

/**
 * 누가 잡았는지의 종류. 판정에는 쓰이지 않고 화면에서 색을 나누는 데만 쓴다.
 *   human      사람이 직접
 *   agent      대화형 AI (사람이 보고 있음)
 *   background 백그라운드 에이전트 (사람이 안 보고 있음)
 *   team       다른 팀원
 */
const ACTORS = ['human', 'agent', 'background', 'team']

/**
 * `--actor` 값을 확정한다. 없으면 환경변수, 그것도 없으면 null.
 * 값이 있는데 목록에 없으면 **거부**한다 — 오타를 조용히 삼키면
 * 화면에서 색이 안 맞는데 이유를 찾을 수 없게 된다.
 */
function resolveActor(flag) {
  const raw = typeof flag === 'string' ? flag : process.env.AXMAP_ACTOR
  if (raw === undefined || raw === null || raw === '') return null
  if (!ACTORS.includes(raw)) {
    console.error(`알 수 없는 actor: ${raw}`)
    console.error(`쓸 수 있는 값: ${ACTORS.join(', ')}`)
    process.exit(1)
  }
  return raw
}

const LEDGER_BRANCH = 'axmap/claims'
const LEDGER_REL = path.join('.axmap', 'ledger')
/**
 * `release` 가 아무것도 반납하지 못했을 때. SPEC 8절.
 * 0 이 아니어야 하는 이유는 그 주변 주석에 적혀 있다.
 */
const EXIT_NOTHING_RELEASED = 5

const MAX_CAS_RETRIES = 5

// ---------------------------------------------------------------------------
// 기본 유틸
// ---------------------------------------------------------------------------

/**
 * git 이 훅을 실행할 때 심어놓는 환경변수들.
 * 이것들은 cwd 보다 우선하므로, 훅 안에서 다른 저장소(=장부 worktree)를 다루려면
 * 반드시 걷어내야 한다. 남겨두면 장부 명령이 커밋 중인 저장소를 가리켜 깨진다.
 */
const GIT_ENV_KEYS = [
  'GIT_DIR',
  'GIT_WORK_TREE',
  'GIT_COMMON_DIR',
  'GIT_INDEX_FILE',
  'GIT_OBJECT_DIRECTORY',
  'GIT_ALTERNATE_OBJECT_DIRECTORIES',
  'GIT_PREFIX',
]

function cleanEnv(keep = []) {
  const e = { ...process.env }
  for (const k of GIT_ENV_KEYS) if (!keep.includes(k)) delete e[k]
  return e
}

function git(args, opts = {}) {
  const r = spawnSync('git', args, {
    cwd: opts.cwd,
    input: opts.input,
    env: cleanEnv(opts.keepEnv ?? []),
    encoding: 'utf8',
    windowsHide: true,
  })
  return {
    code: r.status ?? 1,
    out: (r.stdout ?? '').trim(),
    err: (r.stderr ?? '').trim(),
  }
}

function gitOrDie(args, opts = {}) {
  const r = git(args, opts)
  if (r.code !== 0) die(`git ${args.join(' ')} 실패\n${r.err || r.out}`)
  return r
}

function die(msg, code = 1) {
  console.error(msg)
  process.exit(code)
}

// ---------------------------------------------------------------------------
// 장부 git 호출의 lock 재시도
//
// 🔴 여기가 없던 동안 `commitAndPush` 는 index.lock 하나에 **즉시 죽었다.**
//
//    같은 worktree 에서 다른 프로세스가 커밋 중이면 `git add -A` 가
//    "Unable to create '.../index.lock': File exists" 로 실패하고, gitOrDie 가
//    그대로 exit 1 을 냈다. MAX_CAS_RETRIES 루프는 **push 거부 전용**이라 이것을
//    전혀 덮지 않는다 — 관문 1 은 원격 ref 의 경합이고 이쪽은 로컬 파일의 경합이다.
//    앱에서 세션을 여럿 굴리면(= 이 프로젝트가 하려는 바로 그것) 매번 밟는다.
//
//    lock 실패는 거절이 아니라 "잠깐 뒤에 다시 해봐라" 다. 그래서 재시도한다.
// ---------------------------------------------------------------------------

/**
 * git 이 "지금 누가 쓰는 중" 이라고 말하는 방식들.
 * 파일 이름(index.lock)과 문장(File exists)을 함께 보는 이유는 git 버전·로케일에
 * 따라 둘 중 하나만 나오는 경우가 있기 때문이다. 넓게 잡아도 안전한 쪽인 것은,
 * 여기서 잘못 걸려봐야 **몇 번 더 기다렸다 같은 실패를 다시 내는 것**뿐이라서다.
 */
const LOCK_HINTS = ['index.lock', 'cannot lock ref', 'Unable to create', 'File exists']

/** 50 → 1600ms. 마지막까지 가면 총 3.15초 + 지터를 기다린다. */
const LOCK_BACKOFF_MS = [50, 100, 200, 400, 800, 1600]

/**
 * 마지막 lock 실패의 원문. 선언을 여기에 두는 이유는 TDZ 다 — 이 저장소는
 * `let` 이 첫 사용처보다 아래에 있어 부트스트랩이 통째로 죽은 적이 있다(CLAUDE.md).
 */
let lastLockError = ''

function isLockError(r) {
  const text = `${r.err ?? ''}\n${r.out ?? ''}`
  return LOCK_HINTS.some((h) => text.includes(h))
}

/**
 * 의존성 없는 동기 sleep. `Atomics.wait` 은 아무도 깨우지 않는 SharedArrayBuffer
 * 에서 timeout 만큼 정확히 멈춘다. spawnSync 로 짜인 이 파일에 async 를 들여오면
 * 호출부 전체가 물들므로 여기서 막는다.
 */
function sleepMs(ms) {
  Atomics.wait(new Int32Array(new SharedArrayBuffer(4)), 0, 0, ms)
}

/**
 * 몇 ms 를 기다릴까.
 *
 * 🔴 지터를 `Math.random()` 이 아니라 **pid** 로 만든다.
 *
 * 여러 프로세스를 흩어놓는 목적은 pid 로도 똑같이 달성된다 — 동시에 몰린 프로세스는
 * 서로 다른 pid 를 가지므로 서로 다른 만큼 기다린다. 반면 난수를 쓰면 같은 입력에서
 * 같은 타이밍이 재현되지 않아, 이 재시도가 실제로 도는지 테스트가 확인할 수 없다.
 * 이 저장소가 시계를 인자로 받는 것과 같은 이유다.
 */
function backoffFor(attempt) {
  const base = LOCK_BACKOFF_MS[Math.min(attempt, LOCK_BACKOFF_MS.length - 1)]
  return base + Math.floor((base * (process.pid % 64)) / 64)
}

/**
 * lock 류 실패면 백오프하며 다시 건다.
 *
 * lock 이 아닌 실패는 **재시도하지 않고 그대로 돌려준다.** 원인이 다른 것을
 * 기다렸다 다시 해봐야 같은 답이 나오고, 그동안 사람은 원인을 모른 채 기다린다.
 *
 * @returns git() 의 결과 + 끝내 lock 이 안 풀렸으면 `lockExhausted: true`
 */
function gitLockRetry(args, opts = {}) {
  for (let attempt = 0; ; attempt++) {
    const r = git(args, opts)
    if (r.code === 0 || !isLockError(r)) return r
    if (attempt >= LOCK_BACKOFF_MS.length) return { ...r, lockExhausted: true }
    // 조용히 기다리지 않는다. 몇 초 멈춘 이유를 사람이 알아야 한다.
    console.error(
      `장부가 잠겨 있습니다 (다른 에이전트가 쓰는 중). ` +
        `${backoffFor(attempt)}ms 뒤 재시도 ${attempt + 1}/${LOCK_BACKOFF_MS.length}`,
    )
    sleepMs(backoffFor(attempt))
  }
}

/**
 * CAS(관문 1) 재시도 사이의 대기.
 *
 * lock 재시도와 **같은 계단·같은 지터**를 쓴다. 둘은 층이 다르지만(로컬 파일 락 vs
 * 원격 ref 경합) 대응은 같다 — 흩어져서 다시 해보는 것. 계단을 둘로 나누면
 * 어느 쪽이 얼마나 기다렸는지 사람이 계산해야 한다.
 *
 * 마지막 회차 뒤에는 기다리지 않는다. 어차피 포기할 것을 기다리게 하면
 * 사람이 이유 없이 1.6초를 더 본다.
 *
 * @param {number} attempt 1부터 세는 회차 (호출부의 for 루프 변수 그대로)
 */
function casBackoff(attempt) {
  if (attempt >= MAX_CAS_RETRIES) return
  const ms = backoffFor(attempt - 1)
  console.error(`  ${ms}ms 기다렸다 다시 시도합니다.`)
  sleepMs(ms)
}

/** lock 으로 끝내 실패했을 때 사람에게 보일 이유. 원인을 뭉개지 않는다. */
function lockReason(r) {
  return (r.err || r.out || '').trim().split('\n')[0]
}

function lockedMessage(what) {
  return (
    `다른 에이전트가 장부를 쓰는 중이라 ${LOCK_BACKOFF_MS.length + 1}회 재시도 후 포기했습니다 (${what}).\n` +
    `  ${lastLockError}\n\n` +
    '같은 worktree 에서 여러 프로세스가 동시에 장부를 커밋하면 git 인덱스 락에서 경합합니다.\n' +
    '위 메시지의 락 파일이 죽은 프로세스의 잔해라면 직접 지운 뒤 다시 시도하세요.\n' +
    '(경로를 여기에 적어두지 않는 이유: worktree 이름이 바뀌면 그 줄이 먼저 낡는다.\n' +
    ' 진짜 경로는 git 이 낸 위 한 줄에 들어 있다.)'
  )
}

/** 시계. AXMAP_NOW 로 덮어쓸 수 있다 - 데모에서 TTL 만료를 30분 기다리지 않기 위해. */
function now() {
  const override = process.env.AXMAP_NOW
  if (override) {
    const t = Date.parse(override)
    if (Number.isNaN(t)) die(`AXMAP_NOW 를 해석할 수 없습니다: ${override}`)
    return t
  }
  return Date.now()
}

function parseTtl(v) {
  if (v == null) return 30 * 60_000
  const m = String(v).match(/^(\d+)\s*([smh])?$/)
  if (!m) die(`--ttl 형식이 올바르지 않습니다: ${v} (예: 30m, 2h, 90s)`)
  const n = Number(m[1])
  return n * { s: 1000, m: 60_000, h: 3_600_000 }[m[2] ?? 'm']
}

function parseArgs(argv) {
  const positional = []
  const flags = {}
  for (let i = 0; i < argv.length; i++) {
    const a = argv[i]
    if (!a.startsWith('--')) {
      positional.push(a)
      continue
    }
    const eq = a.indexOf('=')
    if (eq !== -1) {
      flags[a.slice(2, eq)] = a.slice(eq + 1)
    } else if (argv[i + 1] && !argv[i + 1].startsWith('--')) {
      flags[a.slice(2)] = argv[++i]
    } else {
      flags[a.slice(2)] = true
    }
  }
  return { positional, flags }
}

// ---------------------------------------------------------------------------
// 레포 / 장부 위치
// ---------------------------------------------------------------------------

/**
 * 안 읽은 쪽지를 알린다 (docs/bus).
 *
 * 🔴 쪽지를 **따로 기억해야 하는 것으로 두지 않는다.**
 *
 * 쪽지함을 MCP 도구로 내놓아도 "언제 열어볼지" 는 아무도 안 정해준다.
 * 도구가 있다고 쓰는 게 아니다 — 실제로 상대 세션이 내 쪽지를 읽은 것은
 * 커밋 메시지와 브리핑으로 "이걸 봐라" 라고 했기 때문이었다.
 *
 * 그래서 **반드시 부르는 도구에 얹는다.** claim 은 규칙상 코드를 건드리기 전에
 * 무조건 부른다. 거기서 알려주면 아무도 잊을 수 없다.
 * (화면이 숫자 옆에 문장을 붙여 오독을 막은 것과 같은 원리다.)
 *
 * 읽음 표시는 에이전트별 파일에 남긴다. 장부에 넣지 않는 이유는 —
 * 읽었는지는 **나만의 상태**라 남과 합의할 필요가 없고, 장부에 쓰면
 * 쪽지를 읽을 때마다 push 경합이 생긴다.
 */
function unreadNotes(root, me) {
  if (!me) return []
  const box = path.join(root, 'docs', 'bus')
  let names
  try { names = fs.readdirSync(box).filter((f) => f.endsWith('.md')) } catch { return [] }
  let seen = ''
  try {
    const raw = JSON.parse(fs.readFileSync(path.join(root, '.axmap-bus-seen.json'), 'utf8'))
    seen = raw[me] ?? ''
  } catch { /* 처음이면 아무것도 안 읽은 것 */ }

  const out = []
  for (const f of names.sort()) {
    const id = f.slice(0, -3)
    if (id <= seen) continue
    let head = ''
    try { head = fs.readFileSync(path.join(box, f), 'utf8').slice(0, 400) } catch { continue }
    const g = (k) => {
      const m = head.match(new RegExp('^' + k + ':\\s*(.*)$', 'm'))
      return m ? m[1].trim() : ''
    }
    const from = g('from')
    const to = g('to')
    // 내가 보낸 것은 뺀다 — 자기 쪽지에 자기가 놀라면 안 된다.
    if (from === me) continue
    if (to !== me && to !== 'all') continue
    out.push({ id, from, subject: g('subject') })
  }
  return out
}

/** claim·status 끝에 붙이는 알림. 없으면 아무것도 안 찍는다. */
function printUnread(root, me) {
  let notes = []
  try { notes = unreadNotes(root, me) } catch { return }   // 알림 때문에 claim 이 죽으면 안 된다
  if (!notes.length) return
  console.log('\n  안 읽은 쪽지 ' + notes.length + '건')
  for (const n of notes.slice(0, 3)) console.log('     ' + n.from + ' — ' + n.subject)
  if (notes.length > 3) console.log('     … 그 밖에 ' + (notes.length - 3) + '건')
  console.log('     읽기: node tools/bus.mjs list --to ' + me)
}

function repoRoot() {
  const r = git(['rev-parse', '--show-toplevel'])
  if (r.code !== 0) die('git 저장소 안에서 실행해야 합니다.')
  return r.out
}

function ledgerDir(root) {
  return path.join(root, LEDGER_REL)
}

function claimsDir(root) {
  return path.join(ledgerDir(root), 'claims')
}

/**
 * 장부를 주고받을 원격의 **이름**.
 *
 * 🔴 예전에는 이 자리에 `hasRemote()` 가 있었고 `'origin'` 문자열을 직접 봤다.
 *    나머지 다섯 곳(fetch·push·ls-remote·init)이 그 판정을 믿고 `origin` 을 썼으므로,
 *    원격 이름이 `origin` 이 아니면 여섯 곳이 함께 거짓말했다.
 *
 *    증상이 무서운 쪽이다. `hasRemote` 가 false 가 되면 `syncLedger` 는 즉시
 *    return 하고 `pushLedger` 는 push 없이 'ok' 를 낸다. 그러면 **관문 1(CAS)이
 *    통째로 사라지고 관문 2만 남는데, 관문 2는 자기 장부에 대고 판정하므로
 *    영원히 자기 자신하고만 비교한다.** 여섯 명이 같은 파일을 잡아도 전부 성공한다.
 *    종료 코드는 0이고 경고는 init 때 한 줄뿐이었다 — 이 저장소가 금지한 fail-open 이다.
 *
 *    실제로 밟는 경우가 셋이다.
 *      - fork 워크플로 (origin=내 포크, upstream=본체)
 *      - `git remote rename origin gitlab` 같은 개인 취향
 *      - **원격이 둘 이상** — 팀 저장소를 `team` 으로 추가하고 origin 을 개인
 *        저장소로 두면 장부가 개인 저장소로 간다. 이건 경고조차 안 뜬다.
 *
 * 정하는 순서. 위에서부터 먼저 맞는 것을 쓴다.
 *
 *   1. `--remote <이름>`        그 명령에서만
 *   2. `AXMAP_REMOTE`           셸 단위
 *   3. `git config axmap.remote` 저장소에 박힌 값 — `init` 이 여기에 적어 둔다
 *   4. 원격이 **정확히 하나**면 그것. 이름이 무엇이든 상관없다
 *   5. `origin` 이 있으면 `origin` — 옛 동작과의 호환
 *   6. 그 밖 → `ambiguous`. **고르지 않는다**
 *
 * 6에서 하나를 골라 주지 않는 이유는 SPEC §3 의 원칙 그대로다 —
 * 치환은 서로 다른 두 입력을 같은 것으로 만들고, 락에서 그것은 곧 소유권 충돌이다.
 * 잘못 고르면 장부가 엉뚱한 저장소로 가고 **아무도 그것을 모른다.**
 *
 * @returns {{name: string|null, source: string, all: string[]}}
 *          `name` 이 null 이면 `source` 가 'none'(원격 없음) 또는 'ambiguous'(못 고름).
 */
function resolveRemote(root, flags = {}) {
  const all = git(['remote'], { cwd: root }).out.split('\n').map((s) => s.trim()).filter(Boolean)

  const pick = (name, source) => {
    // 지정된 이름이 실제로 없으면 조용히 넘어가지 않는다. 오타가 곧 무보호 상태다.
    if (!all.includes(name)) {
      die(
        `${source} 이(가) 가리키는 원격이 없습니다: ${name}\n` +
          (all.length ? `  이 저장소의 원격: ${all.join(', ')}` : '  이 저장소에는 원격이 없습니다.'),
      )
    }
    return { name, source, all }
  }

  if (typeof flags.remote === 'string') return pick(flags.remote, '--remote')
  if (process.env.AXMAP_REMOTE) return pick(process.env.AXMAP_REMOTE, 'AXMAP_REMOTE')

  const cfg = git(['config', '--get', 'axmap.remote'], { cwd: root }).out.trim()
  if (cfg) return pick(cfg, 'git config axmap.remote')

  if (all.length === 0) return { name: null, source: 'none', all }
  if (all.length === 1) return { name: all[0], source: '유일한 원격', all }
  if (all.includes('origin')) return { name: 'origin', source: '기본값(origin)', all }
  return { name: null, source: 'ambiguous', all }
}

/**
 * 원격 없이 도는 것을 **매번** 알린다.
 *
 * 🔴 예전에는 `init` 때 한 줄이 전부였다. 그 뒤 수백 번의 claim 이 전부 조용히
 *    성공하므로, 사람은 보호받고 있다고 믿는다. 락 시스템에서 가장 나쁜 실패는
 *    거부해야 할 것을 조용히 통과시키는 것이고, 여기서는 "혼자임"이 그것이다.
 *
 * 거부까지 가지 않고 경고에서 멈추는 이유는 혼자 쓰는 사람이 실제로 있기 때문이다.
 * 대신 **조용하지는 않게** 한다. stdout 이 아니라 stderr 로 낸다 — 파이프로
 * 흘려보내도 사람 눈에는 남는다.
 */
/**
 * 한 프로세스에서 한 번만 낸다. claim 은 CAS 재시도로 syncLedger 를 최대 5번 부르는데,
 * 같은 경고를 다섯 줄 쌓으면 사람이 그 다음부터 안 읽는다.
 *
 * 선언을 함수 위에 둔다 — 이 저장소는 `let` 이 첫 사용처보다 아래에 있어 TDZ 로
 * 부트스트랩이 통째로 죽은 적이 있다(CLAUDE.md).
 */
let soloWarned = false

function warnSolo(r) {
  if (r.name || soloWarned) return
  soloWarned = true
  if (r.source === 'ambiguous') {
    console.error(
      `경고: 원격이 여럿이라 장부를 어디에 둘지 정하지 못했습니다 (${r.all.join(', ')}).\n` +
        '      관문 1(CAS)이 작동하지 않습니다 — 다른 사람의 claim 을 보지 못합니다.\n' +
        '      정하려면:  git config axmap.remote <이름>',
    )
    return
  }
  console.error(
    '경고: 원격이 없어 단일 작업자 모드로 돕니다. 관문 1(CAS)이 작동하지 않습니다\n' +
      '      — 다른 사람의 claim 을 보지 못하고, 내 claim 도 아무에게도 가지 않습니다.',
  )
}

/**
 * 이름이 **어디서 왔는지**. 진단에 쓴다.
 *
 * 🔴 조용히 다른 사람이 되는 것이 이 도구에서 가장 위험한 사고다.
 *    `AXMAP_AGENT` 는 셸이 바뀌면 사라지고, 그러면 `git config user.name`
 *    으로 떨어져 **그럴듯한 다른 사람**이 된다. 아무 오류도 안 난다.
 */
let agentFrom = null

/**
 * 이름을 정하는 순서. **기본값은 없다.**
 *
 * 🔴 안전한 기본 이름을 두지 않는 이유. 예전 `.mcp.json` 은 이름이 없으면
 *    모두에게 `claude` 를 줬다. 그러면 팀원 다섯이 clone 했을 때 장부에는
 *    **한 명만** 존재하고, `checkOverlap` 은 자기 claim 을 겹침으로 보지 않으므로
 *    서로의 영역을 아무 경고 없이 덮어쓴다. 이 저장소가 fail-closed 로 막겠다고
 *    선언한 바로 그 실패다 — 치환이 곧 소유권 충돌이다.
 *
 * 옛 이름을 받아주던 한시적 분기는 걷어냈다. 이름을 바꾸기 전부터 돌고 있던 셸을
 * 위한 것이었고, 그런 셸은 이제 없다. 호환을 오래 두면 그것이 규격이 된다.
 */
function agentName(flags) {
  let n = null
  if (flags.agent) {
    n = flags.agent
    agentFrom = '--agent'
  } else if (process.env.AXMAP_AGENT) {
    n = process.env.AXMAP_AGENT
    agentFrom = 'AXMAP_AGENT'
  } else {
    n = git(['config', 'user.name']).out
    agentFrom = 'git config user.name'
  }
  if (!n) {
    die(
      '에이전트 이름을 알 수 없습니다. --agent 또는 AXMAP_AGENT 를 설정하세요.\n' +
        '기본 이름으로 대신 채우지 않습니다 — 여러 사람이 같은 이름이 되면\n' +
        '서로의 claim 을 겹침으로 보지 못해 같은 파일을 조용히 함께 고칩니다.',
    )
  }
  const err = agentNameError(String(n))
  if (err) die(err)
  return String(n)
}

/**
 * 장부 worktree 의 존재만 확인한다.
 *
 * claims 디렉터리의 존재로 판정하면 안 된다. git 은 빈 디렉터리를 추적하지 않으므로
 * 모두가 반납해 claim 파일이 하나도 없으면 reset --hard 가 그 디렉터리를 지운다.
 * 그러면 "정상적으로 다 반납한 상태"가 "장부가 없음"으로 오진된다.
 */
function requireLedger(root) {
  if (!fs.existsSync(path.join(ledgerDir(root), '.git'))) {
    die(`장부가 없습니다. 먼저 실행하세요:\n  axmap init`)
  }
}

/** 지금 실행 위치가 장부 worktree 자신인가. */
function isLedgerWorktree(root) {
  return normalizePath(root).endsWith('/.axmap/ledger')
}

// ---------------------------------------------------------------------------
// 관문 1 - 원격과 동기화 / CAS push
// ---------------------------------------------------------------------------

/**
 * 원격 장부를 그대로 가져와 로컬 장부를 덮어쓴다.
 * reset --hard 인 이유: 장부는 병합 대상이 아니라 항상 원격이 진실이다.
 * 이전 시도에서 push 가 거부되어 남은 로컬 커밋도 여기서 같이 버려진다.
 */
/**
 * 원격에 닿을 수 있나.
 *
 * 🔴 fetch 실패를 "장부 브랜치가 아직 없음" 과 구분해야 한다.
 *
 * 예전에는 fetch 가 실패하면 조용히 통과시켰다. 그러면 **원격이 끊긴 상태에서도
 * claim 이 로컬 장부에만 기록되고**, 같은 시각 원격이 멀쩡한 다른 에이전트가
 * 같은 경로를 잡는다. 두 에이전트가 각자 "내가 쥐고 있다"고 믿는다 —
 * 락 시스템에서 가장 나쁜 실패 방향이고, 이 저장소가 내건 fail-closed 원칙과
 * 정면으로 어긋난다.
 *
 * `ls-remote` 로 원격 자체에 닿는지를 먼저 묻는다. 닿으면 브랜치가 없을 뿐이고,
 * 못 닿으면 그건 장애다.
 */
function remoteReachable(root, remote) {
  const r = git(['ls-remote', '--exit-code', remote, 'HEAD'], { cwd: ledgerDir(root) })
  // --exit-code 는 참조가 없을 때 2 를 준다. 그것도 "닿았다"는 뜻이다.
  return r.code === 0 || r.code === 2
}

function syncLedger(root) {
  const r0 = resolveRemote(root)
  const remote = r0.name
  // 🔴 조용히 넘어가지 않는다. 원격이 없으면 이 뒤의 모든 claim 이 성공하는데,
  //    그 성공은 "겹치지 않는다" 가 아니라 "남을 볼 수 없다" 는 뜻이다.
  if (!remote) { warnSolo(r0); return }
  // fetch 를 반드시 장부 worktree 안에서 실행해야 한다.
  // FETCH_HEAD 는 worktree 별로 따로 보관되므로, 메인 worktree 에서 fetch 하면
  // 장부 worktree 에서는 그 FETCH_HEAD 를 볼 수 없다.
  const dir = ledgerDir(root)
  const f = git(['fetch', '--quiet', remote, LEDGER_BRANCH], { cwd: dir })
  if (f.code === 0) {
    // reset 도 인덱스 락을 잡는다. add·commit 과 같은 경합에 같은 방식으로 대응한다.
    const r = gitLockRetry(['reset', '--hard', '--quiet', 'FETCH_HEAD'], { cwd: dir })
    if (r.lockExhausted) {
      lastLockError = lockReason(r)
      die(lockedMessage('장부 동기화'))
    }
    if (r.code !== 0) die(`git reset --hard FETCH_HEAD 실패\n${r.err || r.out}`)
    return
  }
  // fetch 가 실패했다. 원격에 닿기는 하는가?
  if (remoteReachable(root, remote)) return // 장부 브랜치가 아직 없다 - 첫 push 때 생긴다
  die(
    '원격 장부에 닿을 수 없습니다. 관문 1(직렬화)이 작동하지 않는 상태입니다.\n' +
      `  ${(f.err ?? '').trim().split('\n')[0]}\n\n` +
      '이 상태로 claim 하면 나만 아는 락이 됩니다 — 다른 에이전트는 그것을 볼 수 없고,\n' +
      '같은 경로를 동시에 잡게 됩니다. 연결을 고친 뒤 다시 시도하세요.',
  )
}

/**
 * 장부를 커밋하고 push 한다.
 *
 * @returns {'ok'|'rejected'|'nothing'|'unreachable'|'locked'}
 *   rejected 가 곧 CAS 실패 = 누가 먼저 도착함
 *   locked 는 로컬 인덱스 락 경합이 끝내 안 풀린 것. **여기서 죽지 않고 돌려준다** —
 *   부르는 쪽이 로컬 장부를 되돌려야 하기 때문이다. 되돌리지 않으면 커밋되지도
 *   push 되지도 않은 claim 파일이 worktree 에 남고, 그 저장소만 "내가 쥐고 있다"
 *   고 말한다. 그것이 이 파일이 rollbackLedger 로 막아온 fail-open 이다.
 */
function commitAndPush(root, message) {
  const dir = ledgerDir(root)
  const add = gitLockRetry(['add', '-A'], { cwd: dir })
  if (add.lockExhausted) {
    lastLockError = lockReason(add)
    return 'locked'
  }
  if (add.code !== 0) die(`git add -A 실패\n${add.err || add.out}`)
  if (git(['diff', '--cached', '--quiet'], { cwd: dir }).code === 0) return 'nothing'
  // --no-verify 가 반드시 필요하다.
  // 연결된 worktree 는 훅 디렉터리를 공유하므로(.git/hooks 는 git-common-dir 아래에 하나뿐),
  // 사용자가 pre-commit 훅을 설치하면 장부 커밋에도 그 훅이 발동한다.
  // 장부는 사용자 코드가 아니라 내부 기록이므로 사용자 훅의 대상이 아니다.
  const c = gitLockRetry(['commit', '--quiet', '--no-verify', '-m', message], { cwd: dir })
  if (c.lockExhausted) {
    lastLockError = lockReason(c)
    return 'locked'
  }
  if (c.code !== 0) die(`git commit 실패\n${c.err || c.out}`)
  const remote = resolveRemote(root).name
  if (!remote) return 'ok'
  const p = git(['push', '--quiet', remote, `HEAD:${LEDGER_BRANCH}`], { cwd: dir })
  if (p.code === 0) return 'ok'
  // 🔴 push 실패의 원인을 뭉개지 않는다.
  //
  // 예전에는 전부 'rejected'(= 남이 먼저 도착함) 로 만들었다. 그래서 원격 장애·
  // 인증 실패가 "누가 먼저 갱신했습니다. 잠시 후 다시 시도하세요" 로 표시됐고,
  // 그 조언은 절대 통하지 않는다. 원인을 알 수 없게 만드는 것이
  // fail-open 버그를 오래 숨긴 직접 원인이었다.
  if (!remoteReachable(root, remote)) {
    lastPushError = (p.err ?? '').trim()
    return 'unreachable'
  }
  return 'rejected'
}

/** 마지막 push 실패의 원문. 사람이 원인을 볼 수 있어야 한다. */
let lastPushError = ''

/**
 * 로컬 장부를 push 이전 상태로 되돌린다.
 *
 * 🔴 이것이 없으면 프로토콜이 fail-open 한다.
 *
 * push 가 끝내 실패했는데 로컬 장부에 claim 이 남아 있으면, 그 저장소의
 * `status` 는 "내가 쥐고 있다" 고 말하고 pre-commit 훅도 통과시킨다.
 * 정작 다른 에이전트는 그 claim 을 볼 수 없으므로 같은 경로를 잡는다.
 * **claim 은 원격 장부에 도달했을 때만 성립한다.**
 */
function rollbackLedger(root, before) {
  if (!before) return
  const dir = ledgerDir(root)
  git(['reset', '--hard', '--quiet', before], { cwd: dir })
}

/** 장부 worktree 의 현재 HEAD. 실패하면 null (아직 커밋이 없는 첫 상태) */
function ledgerHead(root) {
  const r = git(['rev-parse', 'HEAD'], { cwd: ledgerDir(root) })
  return r.code === 0 ? r.out.trim() : null
}

// ---------------------------------------------------------------------------
// 장부 읽기 / 쓰기
// ---------------------------------------------------------------------------

/**
 * 장부 전체를 읽는다.
 *
 * 읽을 수 없는 레코드가 하나라도 있으면 전체를 중단한다 (fail-closed).
 * 건너뛰면 그 에이전트가 아무것도 잡고 있지 않은 것처럼 보이고,
 * 남의 락 위에 내 claim 이 발급된다. 락 시스템에서 가장 나쁜 실패 방향이다.
 */
function readClaims(root) {
  const dir = claimsDir(root)
  if (!fs.existsSync(dir)) return []
  return fs
    .readdirSync(dir)
    .filter((f) => f.endsWith('.json'))
    .map((f) => {
      const full = path.join(dir, f)
      let rec
      try {
        rec = JSON.parse(fs.readFileSync(full, 'utf8'))
      } catch (e) {
        die(
          `장부 레코드를 읽을 수 없습니다: claims/${f}\n${e.message}\n\n` +
            '겹침을 판정할 수 없으므로 중단합니다. 손상된 레코드를 고치거나 지운 뒤 다시 시도하세요.',
        )
      }
      if (!rec?.agent || !Array.isArray(rec.paths) || !rec.since || !Number.isFinite(rec.ttlMs)) {
        die(`장부 레코드의 필수 필드가 없습니다: claims/${f}\n필요: agent, paths, since, ttlMs`)
      }
      return rec
    })
}

// 에이전트 이름은 agentNameError() 로 이미 검증되었으므로 그대로 파일명이 된다.
// 여기서 문자를 치환하면 서로 다른 이름이 같은 파일을 가리킬 수 있다.
function claimPath(root, agent) {
  return path.join(claimsDir(root), `${agent}.json`)
}

/**
 * 파일 하나를 원자적으로 쓴다 — 임시 파일에 다 쓴 뒤 같은 디렉터리에서 rename 한다.
 *
 * 🔴 관문 1 이 CAS 인 이상 원자성은 선택이 아니다.
 *
 * `fs.writeFileSync` 는 호출 하나로 보이지만 커널이 여러 번에 나눠 쓸 수 있다.
 * 그 사이에 다른 에이전트의 `git add -A` 가 장부 worktree 를 통째로 스테이징하면
 * **반쯤 쓰인 레코드가 그대로 커밋된다.** readClaims 는 파싱 실패를 건너뛰지 않고
 * 전체를 중단하므로(fail-closed), 그 순간 팀 전원의 장부가 함께 멈춘다.
 * 창은 좁지만 터지면 범위가 전부다.
 *
 * 같은 디렉터리 안의 rename 은 같은 파일시스템이라 원자적이다. 보는 쪽은 옛 파일이나
 * 새 파일만 보고, 반쯤 쓰인 상태는 존재할 수 없다.
 *
 * 임시 이름이 `.json` 으로 끝나지 않는 것도 의도다. rename 직전에 누가 임시 파일을
 * 집어가도 readClaims 의 `.json` 필터가 걸러낸다 — 최악이 "깨진 레코드"가 아니라
 * "무해한 쓰레기 파일"이 된다. 못 막을 때는 실패의 크기라도 낮춘다.
 */
function writeFileAtomic(target, data) {
  const tmp = `${target}.tmp-${process.pid}`
  try {
    fs.writeFileSync(tmp, data)
    fs.renameSync(tmp, target)
  } catch (e) {
    // 정리하다 난 오류가 원래 원인을 가리지 않게 한다.
    try {
      fs.rmSync(tmp, { force: true })
    } catch {
      /* 무시 */
    }
    throw e
  }
}

/**
 * 장부 worktree 가 임시 파일을 절대 커밋하지 않게 한다.
 *
 * writeFileAtomic 의 창은 좁지만 0 은 아니고, 프로세스가 강제 종료되면 임시 파일이
 * 그대로 남는다. `git add -A` 는 그것까지 스테이징하므로 쓰레기가 장부에 실려
 * 모든 클론으로 복제된다. .gitignore 한 줄이면 두 경우가 같이 사라진다.
 *
 * 옛 장부에는 이 파일이 없으므로 claim 할 때마다 존재를 확인한다. 한 번 만들어지면
 * 다음 장부 커밋에 실려 다른 클론에도 따라간다.
 */
function ensureLedgerIgnore(root) {
  const gi = path.join(ledgerDir(root), '.gitignore')
  if (!fs.existsSync(gi)) fs.writeFileSync(gi, '*.tmp-*\n')
}

function writeClaim(root, claim) {
  fs.mkdirSync(claimsDir(root), { recursive: true })
  ensureLedgerIgnore(root)
  writeFileAtomic(claimPath(root, claim.agent), JSON.stringify(claim, null, 2) + '\n')
}

function myClaim(root, agent) {
  return readClaims(root).find((c) => c.agent === agent) ?? null
}

/**
 * 커밋되기 **전에** 실패했을 때 내 레코드 파일을 원래대로 돌린다.
 *
 * 🔴 `git reset --hard` 로는 이 경우를 못 돌린다. 처음 claim 하는 에이전트의
 *    레코드는 아직 한 번도 add 된 적이 없어 **추적되지 않는 파일**이고, reset 은
 *    추적되지 않는 파일을 건드리지 않는다. 그대로 두면 커밋도 push 도 되지 않은
 *    claim 이 worktree 에 남아 그 저장소만 "내가 쥐고 있다" 고 말한다 —
 *    rollbackLedger 가 막으려던 fail-open 이 그 옆문으로 그대로 들어온다.
 *
 *    `git clean` 을 쓰지 않는 이유: 같은 worktree 에서 동시에 일하는 다른
 *    프로세스가 방금 쓴 레코드까지 지운다. 내 파일 하나만 정확히 되돌린다.
 */
function restoreClaimFile(file, prev) {
  if (prev === null) fs.rmSync(file, { force: true })
  else fs.writeFileSync(file, prev)
}

// ---------------------------------------------------------------------------
// 명령: init
// ---------------------------------------------------------------------------

function cmdInit(flags = {}) {
  const root = repoRoot()
  const dir = ledgerDir(root)

  // .axmap 을 손으로 지웠어도 git 쪽에는 worktree 등록이 남아
  // worktree add 가 "already registered" 로 실패한다. 먼저 정리한다.
  git(['worktree', 'prune'], { cwd: root })

  if (fs.existsSync(path.join(dir, '.git'))) {
    console.log(`장부가 이미 있습니다: ${LEDGER_REL}`)
    return
  }
  if (fs.existsSync(dir) && fs.readdirSync(dir).length) {
    die(
      `${LEDGER_REL} 가 이미 있지만 정상적인 장부 worktree 가 아닙니다.\n` +
        `지운 뒤 다시 시도하세요:  rm -rf ${LEDGER_REL}`,
    )
  }

  // 🔴 원격을 고르는 것은 **여기서 한 번**이고, 고른 결과를 저장소에 박아 둔다.
  //
  //    claim 이 매번 다시 추론하면 상황이 바뀔 때마다(원격 추가·이름 변경) 판정이
  //    조용히 달라진다. 어제는 team 으로 가던 장부가 오늘은 origin 으로 간다.
  //    init 이 정하고 git config 에 적어 두면 그 뒤로는 아무도 추측하지 않는다.
  //
  //    여럿인데 못 고르겠으면 **고르지 말고 멈춘다.** 잘못 고르면 장부가 엉뚱한
  //    저장소로 가고, 그건 아무 증상이 없다 — push 는 성공하고 팀은 못 본다.
  const r = resolveRemote(root, flags)
  if (r.source === 'ambiguous') {
    die(
      `원격이 둘 이상입니다. 장부를 어디에 둘지 골라 주세요.\n\n` +
        r.all.map((n) => `  ${n.padEnd(10)} ${git(['remote', 'get-url', n], { cwd: root }).out}`).join('\n') +
        `\n\n  axmap init --remote <이름>\n` +
        `  (또는 미리:  git config axmap.remote <이름>)\n\n` +
        `아무거나 고르지 않는 이유: 장부가 엉뚱한 저장소로 가면 push 는 성공하고\n` +
        `팀은 서로의 claim 을 영영 못 봅니다. 증상이 없는 실패입니다.`,
    )
  }
  const remote = r.name
  if (remote) {
    // 고른 근거가 추론이었다면 못 박는다. 이미 설정에서 왔으면 다시 쓰지 않는다.
    if (!r.source.startsWith('git config')) {
      git(['config', 'axmap.remote', remote], { cwd: root })
    }
    console.log(`장부 원격: ${remote}  (${r.source})`)
  }

  // 원격에 이미 장부가 있으면 그것을 쓰고, 없으면 빈 트리로 새로 만든다.
  let base = null
  if (remote && git(['fetch', '--quiet', remote, LEDGER_BRANCH], { cwd: root }).code === 0) {
    base = git(['rev-parse', 'FETCH_HEAD'], { cwd: root }).out
    console.log('원격 장부를 발견했습니다. 이어서 사용합니다.')
  } else {
    // 작업 트리를 건드리지 않고 고아 브랜치를 만드는 방법:
    // 빈 트리 -> 그 트리를 가리키는 부모 없는 커밋 -> 브랜치.
    const tree = gitOrDie(['mktree'], { cwd: root, input: '' }).out
    base = gitOrDie(['commit-tree', tree, '-m', 'axmap: ledger init'], { cwd: root }).out
    console.log('새 장부를 만들었습니다.')
  }

  if (git(['rev-parse', '--verify', '--quiet', LEDGER_BRANCH], { cwd: root }).code !== 0) {
    gitOrDie(['branch', LEDGER_BRANCH, base], { cwd: root })
  }
  gitOrDie(['worktree', 'add', '--quiet', dir, LEDGER_BRANCH], { cwd: root })
  fs.mkdirSync(claimsDir(root), { recursive: true })
  // git 은 빈 디렉터리를 추적하지 않는다. 모두가 반납해 claim 파일이 0개가 되면
  // claims/ 자체가 사라지므로, 디렉터리를 붙잡아둘 파일을 하나 둔다.
  fs.writeFileSync(path.join(claimsDir(root), '.gitkeep'), '')
  // 임시 파일이 장부에 실리지 않게 한다 (writeFileAtomic 참고).
  ensureLedgerIgnore(root)

  // 장부 worktree 가 본 저장소에서 untracked 로 보이지 않게 한다.
  const gi = path.join(root, '.gitignore')
  const cur = fs.existsSync(gi) ? fs.readFileSync(gi, 'utf8') : ''
  if (!cur.split(/\r?\n/).includes('.axmap/')) {
    fs.writeFileSync(gi, (cur && !cur.endsWith('\n') ? cur + '\n' : cur) + '.axmap/\n')
  }

  if (remote) {
    const p = git(['push', '--quiet', remote, `HEAD:${LEDGER_BRANCH}`], { cwd: dir })
    if (p.code !== 0) console.error(`경고: 장부 push 실패 - ${p.err}`)
  } else {
    warnSolo(r)
  }

  console.log(`준비 완료. 장부 브랜치: ${LEDGER_BRANCH}`)
}

// ---------------------------------------------------------------------------
// 명령: claim  (핵심)
// ---------------------------------------------------------------------------

function cmdClaim(positional, flags) {
  const root = repoRoot()
  requireLedger(root)
  const me = agentName(flags)
  if (!positional.length) die('claim 할 경로를 하나 이상 지정하세요.\n  axmap claim src/auth --task task-12')
  for (const p of positional) {
    const err = claimPathError(p)
    if (err) die(err)
  }
  const want = positional.map(normalizePath)

  // 오타 방어. 아직 만들지 않은 파일을 미리 잡을 수도 있으므로 경고에 그친다.
  // 그냥 두면 존재하지 않는 경로를 잡은 채 실제 수정은 pre-commit 에 막혀 혼란스럽다.
  for (const p of want) {
    if (!fs.existsSync(path.join(root, p))) {
      console.error(`경고: 저장소에 없는 경로입니다 (오타가 아닌지 확인하세요): ${p}`)
    }
  }

  const ttlMs = parseTtl(flags.ttl)

  for (let attempt = 1; attempt <= MAX_CAS_RETRIES; attempt++) {
    syncLedger(root) // 관문 1 준비: 최신 장부를 확보
    const t = now()

    // 관문 2. 판정과 상태 전이는 전부 순수 함수 안에 있다.
    const res = applyClaim({
      claims: readClaims(root),
      me,
      requested: want,
      now: t,
      ttlMs,
      task: typeof flags.task === 'string' ? flags.task : null,
      intent: typeof flags.intent === 'string' ? flags.intent : null,
      // 🔴 잘못된 값을 조용히 폴백하지 않는다.
      // 이 저장소는 "이상한 입력을 안전한 값으로 치환하지 말고 거부한다"를
      // 명시적 원칙으로 걸어뒀다 (CLAUDE.md, SPEC §3 입력 검증).
      // 치환은 서로 다른 두 입력을 같은 것으로 만든다 — 여기서는 색깔만
      // 정하는 필드라 피해가 작지만, 원칙에 예외를 두면 그 예외가 기준이 된다.
      actor: resolveActor(flags.actor),
    })
    if (!res.ok) {
      console.error(formatBlocks(res.blocks, t))
      process.exit(2)
    }
    if (res.hadExpired) {
      console.error(`알림: ${me} 의 이전 claim 이 만료되어 새 claim 으로 시작합니다.`)
    }
    // push 실패 시 되돌아갈 지점. 커밋을 만들기 **전에** 잡아둔다.
    const before = ledgerHead(root)
    // 커밋 전에 실패할 수도 있다. 그때는 HEAD 가 아니라 이 파일을 되돌려야 한다.
    const myFile = claimPath(root, me)
    const myFileBefore = fs.existsSync(myFile) ? fs.readFileSync(myFile) : null
    writeClaim(root, res.record)

    // 관문 1: push 가 거부되면 그 사이에 누가 장부를 바꾼 것이다.
    const pushed = commitAndPush(root, `claim(${me}): ${want.join(' ')}`)
    if (pushed === 'ok' || pushed === 'nothing') {
      console.log(`claim 성공 - ${me}`)
      for (const p of res.record.paths) console.log(`  + ${p}`)
      console.log(`  TTL ${humanDuration(ttlMs)} (만료 ${new Date(t + ttlMs).toISOString()})`)
      printUnread(root, me)
      return
    }
    if (pushed === 'unreachable') {
      // 원격 장애다. 재시도해도 결과가 같고, 로컬에 남기면 나만 아는 락이 된다.
      rollbackLedger(root, before)
      die(
        '원격 장부에 push 할 수 없어 claim 을 취소했습니다.\n' +
          `  ${lastPushError.split('\n')[0]}\n\n` +
          '로컬에만 남겨두면 다른 에이전트는 이 claim 을 볼 수 없고,\n' +
          '같은 경로를 동시에 잡게 됩니다. 그래서 아무것도 잡지 않은 상태로 되돌렸습니다.',
      )
    }
    if (pushed === 'locked') {
      // 백오프를 다 쓰고도 인덱스 락이 안 풀렸다. 로컬에 남은 claim 은 이 저장소
      // 에서만 보이는 락이므로 되돌린다 — unreachable 과 같은 이유다.
      // 커밋 전에 실패했으므로 HEAD 되돌리기만으로는 부족하다(restoreClaimFile 참고).
      restoreClaimFile(myFile, myFileBefore)
      rollbackLedger(root, before)
      die(lockedMessage('claim'))
    }
    console.error(`관문 1: push 거부됨 (누가 먼저 장부를 갱신함). 재시도 ${attempt}/${MAX_CAS_RETRIES}`)
    // 다음 회차는 syncLedger 가 원격 상태로 reset 하므로 이 커밋은 어차피 사라진다.
    // 그래도 마지막 회차에서 빠져나갈 때를 위해 여기서 되돌려 둔다.
    rollbackLedger(root, before)
    /**
     * 🔴 즉시 다시 몰아치지 않는다.
     *
     * 예전에는 5회를 쉬지 않고 붙였다. 그러면 경합 중인 프로세스들이 매번 같은
     * 순간에 같이 fetch·push 하므로 **같은 순서로 같이 지는 쪽**이 계속 나온다
     * (I3 진행성이 걸리는 자리다). 흩어놓아야 누군가는 통과한다.
     */
    casBackoff(attempt)
  }

  die(`장부 경합이 심해 ${MAX_CAS_RETRIES}회 재시도 후 포기했습니다. 잠시 후 다시 시도하세요.\n(로컬 장부는 되돌렸습니다 — 아무것도 잡지 않은 상태입니다.)`)
}

// ---------------------------------------------------------------------------
// 명령: release / renew
// ---------------------------------------------------------------------------

function cmdRelease(positional, flags) {
  const root = repoRoot()
  requireLedger(root)
  const me = agentName(flags)
  const drop = positional.map(normalizePath)

  for (let attempt = 1; attempt <= MAX_CAS_RETRIES; attempt++) {
    syncLedger(root)
    const res = applyRelease({ claims: readClaims(root), me, drop })
    if (res.hadNothing) {
      /**
       * 🔴 반납을 시켰는데 아무것도 반납 안 된 것은 **성공이 아니다.**
       *
       * 두 대로 협업하다 실제로 물렸다. `AXMAP_AGENT=X` 로 claim 한 뒤
       * 그 변수가 없는 셸에서 release 를 불렀고, 이름이 `git config` 로
       * 떨어져 "잡고 있는 경로가 없습니다" + **종료 코드 0** 이 나왔다.
       * 반납했다고 믿었지만 락은 그대로였고 다른 에이전트를 30분 더 막았다.
       *
       * 0 을 주면 스크립트와 에이전트는 성공으로 읽는다. 락 시스템에서
       * 조용히 통과시키는 것이 가장 나쁜 실패다.
       */
      const others = readClaims(root).filter((c) => c.agent !== me)
      console.error(`반납할 것이 없습니다 — "${me}" 이(가) 잡고 있는 경로가 하나도 없습니다.`)
      console.error(`  이 이름은 ${agentFrom} 에서 왔습니다.`)
      if (others.length) {
        console.error('')
        console.error('  장부에는 다른 이름으로 잡힌 것이 있습니다:')
        for (const c of others) console.error(`    ${c.agent} [${c.task}] ${c.paths.length}개`)
        console.error('')
        console.error('  잡을 때와 다른 이름으로 반납하려 한 것일 수 있습니다.')
        console.error('  claim 할 때 쓴 AXMAP_AGENT 를 같은 값으로 주고 다시 시도하세요.')
      }
      process.exit(EXIT_NOTHING_RELEASED)
    }
    if (res.unheld.length) {
      console.error(`알림: 잡고 있지 않은 경로는 무시합니다: ${res.unheld.join(', ')}`)
    }
    if (res.record) writeClaim(root, res.record)
    else fs.rmSync(claimPath(root, me), { force: true })

    const pushed = commitAndPush(root, `release(${me}): ${drop.length ? drop.join(' ') : 'all'}`)
    if (pushed === 'ok' || pushed === 'nothing') {
      const left = res.record?.paths.length ?? 0
      console.log(`release 완료 - ${me}${left ? ` (남은 ${left}개)` : ' (전부 반납)'}`)
      return
    }
    if (pushed === 'locked') die(lockedMessage('release'))
    console.error(`관문 1: push 거부됨. 재시도 ${attempt}/${MAX_CAS_RETRIES}`)
    casBackoff(attempt)
  }
  die('release 실패 - 장부 경합이 계속됩니다.')
}

function cmdRenew(flags) {
  const root = repoRoot()
  requireLedger(root)
  const me = agentName(flags)
  const ttlMs = parseTtl(flags.ttl)

  for (let attempt = 1; attempt <= MAX_CAS_RETRIES; attempt++) {
    syncLedger(root)
    const t = now()
    const res = applyRenew({ claims: readClaims(root), me, now: t, ttlMs })
    if (!res.ok) {
      die(
        `${me} 의 유효한 claim 이 없습니다.\n` +
          'TTL 이 이미 만료되었다면 그 사이 다른 에이전트가 가져갔을 수 있습니다.\n' +
          'axmap claim 으로 다시 선점하세요 (겹침 검사를 다시 거칩니다).',
      )
    }
    writeClaim(root, res.record)
    const pushed = commitAndPush(root, `renew(${me})`)
    if (pushed === 'ok' || pushed === 'nothing') {
      console.log(`renew 완료 - ${me}, TTL ${humanDuration(ttlMs)} 연장`)
      return
    }
    if (pushed === 'locked') die(lockedMessage('renew'))
    console.error(`관문 1: push 거부됨. 재시도 ${attempt}/${MAX_CAS_RETRIES}`)
    casBackoff(attempt)
  }
  die('renew 실패 - 장부 경합이 계속됩니다.')
}

// ---------------------------------------------------------------------------
// 명령: status
// ---------------------------------------------------------------------------

function cmdStatus(flags) {
  const root = repoRoot()
  requireLedger(root)
  syncLedger(root)
  const t = now()
  const all = readClaims(root)

  if (flags.json) {
    console.log(
      JSON.stringify(
        {
          now: new Date(t).toISOString(),
          active: activeClaims(all, t),
          expired: all.filter((c) => claimExpiresAt(c) <= t),
        },
        null,
        2,
      ),
    )
    return
  }

  const live = activeClaims(all, t)
  const dead = all.filter((c) => claimExpiresAt(c) <= t)

  if (!live.length && !dead.length) {
    console.log('장부가 비어 있습니다. 아무도 아무것도 잡고 있지 않습니다.')
    return
  }

  console.log(`장부 상태 (${new Date(t).toISOString()})\n`)
  for (const c of live) {
    console.log(`  ${c.agent}${c.task ? `  [${c.task}]` : ''}  - ${humanDuration(claimExpiresAt(c) - t)} 남음`)
    if (c.intent) console.log(`    "${c.intent}"`)
    for (const p of c.paths) console.log(`      ${p}`)
    console.log('')
  }
  for (const c of dead) {
    console.log(`  ${c.agent}  - 만료됨 (자동 회수 대상, 아무것도 막지 않음)`)
    for (const p of c.paths) console.log(`      ${p}`)
    console.log('')
  }
}

// ---------------------------------------------------------------------------
// 명령: verify  (pre-commit 훅용)
// ---------------------------------------------------------------------------

function cmdVerify(flags) {
  const root = repoRoot()
  // 장부 자신에 대한 커밋은 검사 대상이 아니다.
  // commitAndPush 가 --no-verify 를 쓰므로 보통은 여기 오지 않지만,
  // 사람이 장부를 손으로 고칠 때를 대비한 두 번째 그물이다.
  if (isLedgerWorktree(root)) return
  requireLedger(root)
  const me = agentName(flags)
  syncLedger(root)

  // 여기서만 GIT_INDEX_FILE 을 남긴다.
  // git commit --only <경로> 는 임시 인덱스를 만들어 이 변수로 알려주므로,
  // 걷어내면 실제로 커밋될 내용이 아닌 엉뚱한 인덱스를 검사하게 된다.
  /**
   * 🔴 `-c core.quotepath=false` 가 없으면 **한글 경로가 통과하지 못한다.**
   *
   * git 은 기본으로 ASCII 밖의 글자를 8진 이스케이프로 바꾸고 경로 전체를
   * 따옴표로 감싼다 —
   *
   *   docs/bus/방향-전환.md
   *     → "docs/bus/\353\260\251\355\226\245-\354\240\204\355\231\230.md"
   *
   * 그러면 앞에 따옴표가 붙어 있어 `docs/bus` 로 시작하지 않게 되고,
   * **디렉터리를 선점했는데도 그 아래 파일이 거부된다.** 실제로 겪었다 —
   * `docs/bus` 를 잡은 채로 한글 제목 쪽지를 커밋하려다 막혔다.
   *
   * 더 나쁜 방향도 가능하다: 겹침 판정이 경로를 못 알아보면 **막아야 할 것을
   * 통과시킬** 수도 있다. 락에서 최악은 조용한 통과다.
   *
   * 이 저장소는 주석·문서·커밋 메시지가 전부 한국어라 파일 이름도 한국어가
   * 될 수 있다. 예외가 아니라 기본값으로 다뤄야 한다.
   */
  const staged = gitOrDie(['-c', 'core.quotepath=false', 'diff', '--cached', '--name-only'], {
    cwd: root,
    keepEnv: ['GIT_INDEX_FILE'],
  })
    .out.split('\n')
    .map((s) => s.trim())
    .filter(Boolean)
    .map(normalizePath)
    .filter((f) => !f.startsWith('.axmap/'))

  if (!staged.length) return

  const mine = myActiveClaim(readClaims(root), me, now())
  const paths = mine?.paths ?? []
  const uncovered = staged.filter((f) => !coversPath(paths, f))

  if (uncovered.length) {
    console.error('커밋 거부 - claim 하지 않은 파일을 수정했습니다.\n')
    for (const f of uncovered) console.error(`  x ${f}`)
    console.error(`\n${me} 이(가) 현재 잡고 있는 경로:`)
    if (paths.length) for (const p of paths) console.error(`  - ${p}`)
    else console.error('  (없음)')
    console.error('\n해결:\n  axmap claim <경로>   먼저 선점한 뒤 커밋하세요')
    process.exit(3)
  }
}

// ---------------------------------------------------------------------------
// 명령: audit  (사후 증명)
// ---------------------------------------------------------------------------

function cmdAudit(flags) {
  const root = repoRoot()
  requireLedger(root)
  const dir = ledgerDir(root)
  if (flags.fetch) syncLedger(root)

  // 오래된 것부터 재생한다.
  const log = gitOrDie(['log', '--reverse', '--format=%H%x00%ct%x00%s'], { cwd: dir }).out
  const commits = log ? log.split('\n').map((l) => l.split('\0')) : []

  const snapshots = []
  for (const [sha, ct, subject] of commits) {
    const files = git(['ls-tree', '-r', '--name-only', sha, 'claims/'], { cwd: dir }).out
    const claims = []
    // readClaims 와 같은 규칙으로 .json 만 본다. claims/ 에는 .gitkeep 도 있다.
    for (const f of (files ? files.split('\n') : []).filter((f) => f.endsWith('.json'))) {
      const blob = git(['show', `${sha}:${f}`], { cwd: dir })
      if (blob.code !== 0) continue
      try {
        claims.push(JSON.parse(blob.out))
      } catch {
        die(`감사 중단 - ${sha.slice(0, 7)} 의 ${f} 를 파싱할 수 없습니다.`)
      }
    }
    snapshots.push({ commit: sha, time: Number(ct) * 1000, subject, claims })
  }

  const report = auditLedger(snapshots)
  if (flags.json) {
    console.log(JSON.stringify(report, null, 2))
  } else {
    console.log(formatAudit(report))
  }
  if (!report.ok) process.exit(4)
}

function cmdHookInstall() {
  const root = repoRoot()
  const dir = gitOrDie(['rev-parse', '--git-common-dir'], { cwd: root }).out
  const hooks = path.resolve(root, dir, 'hooks')
  fs.mkdirSync(hooks, { recursive: true })
  // 검사 대상 저장소가 아니라 이 CLI 자신의 위치를 박는다.
  // axMap 은 검사 대상 저장소 바깥에 설치되어 있을 수 있다.
  const self = fileURLToPath(import.meta.url).replace(/\\/g, '/')
  const body = `#!/bin/sh
# axMap - claim 하지 않은 파일의 커밋을 막는다
exec node "${self}" verify
`

  /**
   * 🔴 `pre-commit` 하나로는 부족하다.
   *
   * git 은 **자동 머지 커밋에 `pre-commit` 을 부르지 않는다.** `pre-merge-commit`
   * 을 부른다. 그래서 훅을 설치해도 이 경로가 통째로 열려 있었다 —
   *
   *   git commit           막힘 (pre-commit)
   *   git commit -m … 후
   *   git merge <브랜치>   **안 막힘** → 남의 영역이 main 으로 들어오고 push 까지 된다
   *
   * 실제로 재현됐다. claim 이 0건인 에이전트가 `--no-verify` 로 브랜치에 커밋한 뒤
   * merge 하면 무단 변경이 main 에 올라간다.
   *
   * `git merge` 가 충돌 없이 자동 커밋할 때는 `pre-merge-commit` 이,
   * 충돌을 사람이 풀고 `git commit` 할 때는 `pre-commit` 이 돈다. 둘 다 걸어야 한다.
   *
   * cherry-pick·revert·rebase 는 내부적으로 `git commit` 경로를 타므로
   * `pre-commit` 으로 덮인다. `--no-verify` 는 여전히 우회할 수 있고,
   * 그건 훅의 본질적 한계라 문서에 남긴다 (SPEC §7).
   */
  const installed = []
  for (const name of ['pre-commit', 'pre-merge-commit']) {
    const file = path.join(hooks, name)
    fs.writeFileSync(file, body)
    try {
      fs.chmodSync(file, 0o755)
    } catch {
      /* 윈도우에서는 무시 */
    }
    installed.push(file)
  }
  for (const f of installed) console.log(`훅 설치 완료: ${f}`)
  console.log('  (merge 로 남의 영역이 들어오는 경로까지 막습니다)')
}

// ---------------------------------------------------------------------------

const HELP = `axmap - AI 에이전트 작업 선점 프로토콜

  axmap init                         장부(고아 브랜치 + worktree)를 준비한다
  axmap claim <경로...>              경로를 선점한다  [--task --intent --ttl 30m]
  axmap release [경로...]            반납한다 (경로 생략 시 전부)
  axmap renew                        TTL 을 연장한다  [--ttl 30m]
  axmap status                       누가 무엇을 잡고 있는지  [--json]
  axmap verify                       staged 파일이 내 claim 안에 있는지 검사
  axmap audit                        장부 이력 전체를 재생해 상호배제 위반을 사후 증명
                                        [--json] [--fetch]
  axmap hook install                 verify 를 pre-commit 훅으로 설치

에이전트 이름: --agent 또는 AXMAP_AGENT, 없으면 git config user.name
`

const { positional, flags } = parseArgs(process.argv.slice(2))
const [cmd, ...rest] = positional

switch (cmd) {
  case 'init':
    cmdInit(flags)
    break
  case 'claim':
    cmdClaim(rest, flags)
    break
  case 'release':
    cmdRelease(rest, flags)
    break
  case 'renew':
    cmdRenew(flags)
    break
  case 'status':
    cmdStatus(flags)
    break
  case 'verify':
    cmdVerify(flags)
    break
  case 'audit':
    cmdAudit(flags)
    break
  case 'hook':
    if (rest[0] === 'install') cmdHookInstall()
    else die(HELP)
    break
  default:
    console.log(HELP)
    process.exit(cmd ? 1 : 0)
}
