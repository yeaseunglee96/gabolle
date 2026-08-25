#!/usr/bin/env node
/**
 * 에이전트 사이의 쪽지함 — 사람을 거치지 않고 서로에게 말한다.
 *
 *   node tools/bus.mjs post --to <상대> --subject "<제목>"   (본문은 stdin)
 *   node tools/bus.mjs list [--to <나>] [--from <상대>] [--all]
 *   node tools/bus.mjs read <아이디>
 *   node tools/bus.mjs reply <아이디> --subject "<제목>"      (본문은 stdin)
 *
 * ── 왜 파일 하나가 아니라 **디렉터리**인가 ────────────────────────────────
 *
 * 🔴 쪽지 하나 = 파일 하나. 그래야 두 에이전트가 동시에 써도 안 부딪힌다.
 *
 * 처음엔 append-only 로그 파일 하나를 생각했다. 그런데 그건 우리가 이미
 * 아는 실패다 — 두 사람이 같은 파일 끝에 줄을 붙이면 git 이 텍스트 충돌을
 * 낸다. 이 저장소가 장부를 파서로 만든 이유가 정확히 그것이다
 * (docs/EXPERIMENT.md). 쪽지함에서 같은 실수를 반복하지 않는다.
 *
 * 파일 이름에 시각과 보낸 사람이 들어가므로 두 사람이 같은 이름을 쓸 일이 없다.
 * 충돌이 구조적으로 불가능하면 조율도 필요 없다.
 *
 * ── 왜 git 인가 ──────────────────────────────────────────────────────────
 *
 * 이미 있는 채널이다. 두 에이전트가 다른 컴퓨터에 있어도 pull/push 로 오간다.
 * 새 서버도, 새 의존성도, 새 인증도 필요 없다. 그리고 **기록이 남는다** —
 * 나중에 "왜 이렇게 정했나" 를 되짚을 수 있다.
 *
 * ⚠️ 실시간이 아니다. 상대가 pull 해야 읽는다. 급한 것은 claim 의 `--intent`
 *    에 한 줄로 적는 편이 빠르다 — 그건 `axmap status` 로 바로 보인다.
 */

import { execFileSync } from 'node:child_process'
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')

/**
 * 쪽지가 쌓이는 곳. **기본값은 이 저장소지만 `AXMAP_BUS_DIR` 로 옮길 수 있다.**
 *
 * 🔴 프로그램은 여기(axMap)에 있고 데이터는 대상 저장소에 있어야 한다.
 *    MCP 서버가 남의 저장소에 붙었을 때 이 값을 `<대상>/docs/bus` 로 넘긴다.
 *    안 그러면 그 팀의 쪽지가 axMap 저장소에 쌓이고, 정작 팀의 저장소에는
 *    아무것도 안 남는다 — 쪽지는 커밋해야 상대에게 가는데 커밋할 저장소가
 *    엉뚱한 곳이 되는 것이다.
 */
const BOX = process.env.AXMAP_BUS_DIR
  ? path.resolve(process.env.AXMAP_BUS_DIR)
  : path.join(ROOT, 'docs', 'bus')

const args = process.argv.slice(2)
const cmd = args[0]
const flag = (n, d = null) => { const i = args.indexOf(n); return i < 0 ? d : args[i + 1] }
const has = (n) => args.includes(n)

/** 나는 누구인가. 선점 프로토콜과 **같은 값**을 쓴다 — 두 이름을 두면 갈린다. */
function me() {
  const v = process.env.AXMAP_AGENT
  if (!v || !/^[\w.-]{1,64}$/.test(v)) {
    console.error('AXMAP_AGENT 를 먼저 정하세요 (선점과 같은 값).')
    process.exit(1)
  }
  return v
}

const stamp = (d) => d.toISOString().replace(/[-:]/g, '').replace(/\.\d+Z$/, 'Z')
const slug = (s) => s.toLowerCase().replace(/[^\w가-힣]+/g, '-').replace(/^-|-$/g, '').slice(0, 40) || 'msg'

function readAll() {
  let names = []
  try { names = fs.readdirSync(BOX).filter((f) => f.endsWith('.md')) } catch { return [] }
  return names.map((f) => {
    const text = fs.readFileSync(path.join(BOX, f), 'utf8')
    const head = {}
    // 앞머리 `키: 값` 줄들. 빈 줄이 나오면 본문이 시작된다.
    const lines = text.split('\n')
    let i = 0
    for (; i < lines.length; i++) {
      const m = lines[i].match(/^(\w+):\s*(.*)$/)
      if (!m) break
      head[m[1]] = m[2].trim()
    }
    return { id: f.replace(/\.md$/, ''), file: f, ...head, body: lines.slice(i).join('\n').trim() }
  }).sort((a, b) => (a.id < b.id ? -1 : 1))
}

function stdin() {
  try { return fs.readFileSync(0, 'utf8') } catch { return '' }
}

function post({ to, subject, body, replyTo = null }) {
  const from = me()
  if (!subject) { console.error('--subject 가 필요합니다.'); process.exit(1) }
  if (!body.trim()) { console.error('본문이 비었습니다 (stdin 으로 주세요).'); process.exit(1) }
  fs.mkdirSync(BOX, { recursive: true })
  const now = new Date()
  const id = `${stamp(now)}-${from}-${slug(subject)}`
  const head = [
    `from: ${from}`,
    `to: ${to ?? 'all'}`,
    `at: ${now.toISOString()}`,
    `subject: ${subject}`,
    replyTo ? `replyTo: ${replyTo}` : null,
  ].filter(Boolean).join('\n')
  // 임시 파일에 다 쓴 뒤 rename 한다. 같은 디렉터리 안의 rename 은 원자적이라
  // 읽는 쪽이 반쯤 쓰인 쪽지를 보는 일이 없다 — bin/axmap.mjs 의 writeFileAtomic 과
  // 같은 이유다. 장부만큼 치명적이진 않지만(쪽지는 판정에 쓰이지 않는다) 같은 값이면
  // 안전한 쪽으로 쓴다. 임시 이름이 `.md` 로 끝나지 않아 list 의 필터에도 안 걸린다.
  const dst = path.join(BOX, `${id}.md`)
  const tmp = `${dst}.tmp-${process.pid}`
  try {
    fs.writeFileSync(tmp, `${head}\n\n${body.trim()}\n`)
    fs.renameSync(tmp, dst)
  } catch (e) {
    try {
      fs.rmSync(tmp, { force: true })
    } catch {
      /* 무시 */
    }
    throw e
  }
  console.log(`보냄  ${id}`)
  // 🔴 안내에 경로를 지어내지 않는다. `AXMAP_BUS_DIR` 로 쪽지함이 다른 저장소에
  //    가 있을 수 있고, 그때 `node tools/bus.mjs` 는 그 저장소에 없는 경로다.
  //    거기 있는 사람이 실제로 칠 수 있는 것만 적는다.
  console.log(`  쪽지함: ${BOX}`)
  console.log(`  받는 사람은 그 저장소를 pull 한 뒤 자기 도구의 bus_inbox / bus.mjs list --to <자기이름> 로 읽는다.`)
  // 🔴 커밋·push 는 하지 않는다. 부르는 쪽이 자기 작업과 함께 올리게 둔다 —
  //    여기서 push 하면 남의 작업 중인 트리를 건드리게 된다.
}

function list() {
  const all = readAll()
  const to = flag('--to')
  const from = flag('--from')
  const rows = all.filter((m) => (has('--all') || !to || m.to === to || m.to === 'all')
    && (!from || m.from === from))
  if (!rows.length) return console.log('쪽지 없음.')
  for (const m of rows) {
    console.log(`${m.at?.slice(0, 16).replace('T', ' ')}  ${(m.from ?? '?').padEnd(18)} → ${(m.to ?? 'all').padEnd(18)} ${m.subject ?? ''}`)
    console.log(`    ${m.id}`)
  }
  console.log(`\n총 ${rows.length}개. 본문:  node tools/bus.mjs read <아이디>`)
}

function read(id) {
  const m = readAll().find((x) => x.id === id || x.id.includes(id))
  if (!m) { console.error(`없는 쪽지: ${id}`); process.exit(1) }
  console.log(`── ${m.subject}\n   ${m.from} → ${m.to}   ${m.at}\n`)
  console.log(m.body)
}

/** 지금 누가 무엇을 잡고 있나 — 쪽지를 보내기 전에 상대가 뭘 하는지 본다. */
function who() {
  try {
    console.log(execFileSync('node', [path.join(ROOT, 'bin', 'axmap.mjs'), 'status'], {
      encoding: 'utf8', env: { ...process.env, AXMAP_AGENT: process.env.AXMAP_AGENT ?? 'bus' },
    }))
  } catch (e) { console.error(e.message) }
}

switch (cmd) {
  case 'post': post({ to: flag('--to'), subject: flag('--subject'), body: stdin() }); break
  case 'reply': {
    const target = args[1]
    const src = readAll().find((x) => x.id === target || x.id.includes(target))
    if (!src) { console.error(`없는 쪽지: ${target}`); process.exit(1) }
    post({ to: src.from, subject: flag('--subject') ?? `Re: ${src.subject}`, body: stdin(), replyTo: src.id })
    break
  }
  case 'list': list(); break
  case 'read': read(args[1]); break
  case 'who': who(); break
  default:
    console.log(`에이전트 쪽지함

  node tools/bus.mjs post --to <상대> --subject "<제목>" < 본문.md
  node tools/bus.mjs list [--to <나>] [--from <상대>] [--all]
  node tools/bus.mjs read <아이디>
  node tools/bus.mjs reply <아이디> < 본문.md
  node tools/bus.mjs who          지금 누가 무엇을 잡고 있나

AXMAP_AGENT 를 선점과 같은 값으로 두세요.
쪽지는 커밋해야 상대에게 갑니다 — 자기 작업과 함께 올리면 됩니다.`)
}
