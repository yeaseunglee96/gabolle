#!/usr/bin/env node
/**
 * axMap MCP 서버.
 *
 * Claude Code 같은 AI 도구가 "나는 이 파일들을 건드리겠다"를 선언하게 만든다.
 * 그래야 뷰어가 지금 무슨 일이 일어나는지 보여줄 수 있다.
 *
 * 설정 (.mcp.json 또는 도구별 MCP 설정):
 *   {
 *     "mcpServers": {
 *       "axmap": {
 *         "command": "node",
 *         "args": ["<axmap>/mcp/server.mjs"],
 *         "env": {
 *           "AXMAP_REPO": "<대상 저장소>",
 *           "AXMAP_ACTOR": "agent"
 *         }
 *       }
 *     }
 *   }
 *
 * 🔴 `env` 에 에이전트 이름을 **적어 두지 않는다.** 그 자리가 저장소에 커밋되는
 *    순간 모두가 같은 한 사람이 된다.
 *
 *    예전에는 `"AXMAP_AGENT": "${AXMAP_AGENT:-claude}"` 였다. clone 한 팀원 다섯이
 *    전부 `claude` 라는 한 명이 되고, `checkOverlap` 은 **자기 claim 을 겹침으로
 *    보지 않으므로** 서로의 영역을 아무 경고 없이 덮어쓴다. 락이 조용히 다섯 명에게
 *    발급된 것이고, 이 저장소가 fail-closed 로 막겠다고 선언한 바로 그 실패다.
 *
 *    그래서 이름은 아래 순서로만 정한다. **안전한 기본값으로 치환하지 않는다** —
 *    치환은 서로 다른 두 사람을 같은 사람으로 만들고, 그것이 곧 소유권 충돌이다.
 *
 *      1. AXMAP_AGENT
 *      2. git config user.name
 *      3. 없으면 die
 *
 * 판정은 전부 bin/axmap.mjs 에 맡긴다. 여기서 로직을 다시 구현하면
 * 두 경로의 동작이 갈라지고, 그러면 장부를 믿을 수 없게 된다.
 *
 * stdio 전송 — 줄바꿈으로 구분된 JSON-RPC 2.0.
 */

import { spawnSync } from 'node:child_process'
import fs from 'node:fs'
import path from 'node:path'
import readline from 'node:readline'
import { fileURLToPath } from 'node:url'

const HERE = path.dirname(fileURLToPath(import.meta.url))

/**
 * axMap 저장소 자신. `REPO`(작업 대상)와 다를 수 있다 —
 * 다른 저장소에 붙어 있는 동안에도 이 도구의 **코드**는 여기 있다.
 *
 * 이 구분이 이 파일에서 가장 자주 틀리는 곳이다. 기준은 하나다:
 *   프로그램(실행할 것) → `SELF`,  데이터(대상 저장소의 것) → `REPO`.
 */
const SELF = path.resolve(HERE, '..')
const CLI = path.join(SELF, 'bin', 'axmap.mjs')

const REPO = path.resolve(process.env.AXMAP_REPO ?? process.cwd())

/** axMap 저장소 자신을 겨누고 있는가. 브리핑이 무엇을 읽을지가 여기서 갈린다. */
const FOREIGN = REPO !== SELF

/**
 * 쪽지함 CLI. **`SELF` 기준이다 — `REPO` 가 아니다.**
 *
 * 🔴 예전에는 `path.join(REPO, 'tools', 'bus.mjs')` 였다. axMap 안에서 자기 자신을
 *    겨눠 쓰는 동안에는 두 경로가 같아서 아무 문제가 없었고, 남의 저장소에 붙이는
 *    순간 `MODULE_NOT_FOUND` 로 쪽지함이 통째로 죽었다. `bus.mjs` 는 **이 도구의
 *    일부**지 대상 저장소가 가진 파일이 아니다. 바로 위 `CLI` 가 처음부터 `SELF`
 *    기준이었던 것과 정확히 같은 이유다.
 *
 *    쪽지의 **내용**은 반대다. 그건 대상 저장소의 데이터이므로 `AXMAP_BUS_DIR` 로
 *    `REPO/docs/bus` 를 넘긴다. 안 그러면 팀의 쪽지가 axMap 저장소에 쌓여
 *    정작 그 팀의 저장소에는 아무것도 안 남는다.
 *
 *    이 버그가 오래 살아 있던 이유는 하나다 — REPO 와 SELF 가 다른 조합을
 *    아무도 돌려보지 않았다. `test/mcp.test.mjs` 가 그 조합을 고정한다.
 */
const BUS = path.join(SELF, 'tools', 'bus.mjs')
const BUS_DIR = path.join(REPO, 'docs', 'bus')
/**
 * 에이전트 이름. 기본값이 **없다** — 이유는 이 파일 머리말에 적어 두었다.
 * 요약하면: 모두에게 같은 기본 이름을 주면 여러 사람이 장부에서 한 명이 되고,
 * 그 순간 서로의 claim 이 겹침으로 보이지 않는다.
 */
function resolveAgent() {
  if (process.env.AXMAP_AGENT) return process.env.AXMAP_AGENT
  const r = spawnSync('git', ['config', 'user.name'], { cwd: REPO, encoding: 'utf8', windowsHide: true })
  const name = (r.stdout ?? '').trim()
  if (name) return name
  // die. 기본 이름으로 대신 채우면 두 사람이 한 사람이 되고, 그것이 곧 소유권 충돌이다.
  process.stderr.write(
    'axMap MCP: 에이전트 이름을 알 수 없습니다.\n' +
      '  AXMAP_AGENT 를 설정하거나 git config user.name 을 지정하세요.\n' +
      '  기본 이름으로 대신 채우지 않습니다 — 여러 사람이 같은 이름이 되면\n' +
      '  서로의 claim 을 겹침으로 보지 못해 같은 파일을 조용히 함께 고칩니다.\n',
  )
  process.exit(1)
}

const AGENT = resolveAgent()
const ACTOR = process.env.AXMAP_ACTOR ?? 'agent'
const TTL = process.env.AXMAP_TTL ?? '45m'

/**
 * 쪽지함 CLI 를 부른다. `cli` 와 달리 stdin 으로 본문을 넘긴다.
 *
 * 🔴 쪽지 하나 = 파일 하나라 두 에이전트가 동시에 보내도 안 부딪힌다.
 *    append-only 로그 파일 하나였다면 git 텍스트 충돌이 난다 —
 *    이 저장소가 장부를 파서로 만든 이유가 정확히 그것이다.
 */
function bus(args, input = undefined) {
  const r = spawnSync(process.execPath, [BUS, ...args], {
    cwd: REPO,
    encoding: 'utf8',
    windowsHide: true,
    input,
    env: { ...process.env, AXMAP_AGENT: AGENT, AXMAP_ACTOR: ACTOR, AXMAP_BUS_DIR: BUS_DIR },
  })
  return {
    code: r.status ?? 1,
    out: [(r.stdout ?? '').trim(), (r.stderr ?? '').trim()].filter(Boolean).join('\n'),
  }
}

function cli(args) {
  const r = spawnSync(process.execPath, [CLI, ...args], {
    cwd: REPO,
    encoding: 'utf8',
    windowsHide: true,
    env: { ...process.env, AXMAP_AGENT: AGENT, AXMAP_ACTOR: ACTOR },
  })
  return {
    code: r.status ?? 1,
    out: [(r.stdout ?? '').trim(), (r.stderr ?? '').trim()].filter(Boolean).join('\n'),
  }
}

// ---------------------------------------------------------------------------
// 도구
// ---------------------------------------------------------------------------

const TOOLS = [
  {
    name: 'axmap_brief',
    description:
      '이 저장소에서 처음 일한다면 **가장 먼저** 호출한다. 무엇을 만드는 프로젝트인지, ' +
      '이미 내려진 결정이 무엇인지(다시 논의하지 말 것), 어떤 파일이 무슨 일을 하는지, ' +
      '끝내기 전에 무엇을 통과해야 하는지를 한 번에 준다. ' +
      '내용은 저장소의 문서와 각 파일 머리말에서 그때그때 읽어오므로 낡지 않는다.',
    inputSchema: { type: 'object', properties: {} },
  },

  /**
   * 🔴 왜 `init` 이 MCP 도구여야 하는가.
   *
   * 장부 없는 저장소에서 첫 `axmap_claim` 은 이렇게 떨어진다:
   *
   *     장부가 없습니다. 먼저 실행하세요:
   *       axmap init
   *
   * 메시지는 정확한데 **그 명령을 부를 방법이 도구 목록에 없었다.** 에이전트는
   * 지시를 읽고도 실행할 수 없고, 사람이 셸로 내려가야만 풀린다. 붙이자마자
   * 막히는 벽이 정확히 여기였다 — 그리고 이 저장소 안에서만 테스트하면
   * 장부가 이미 있으므로 한 번도 안 밟는다.
   *
   * 이 도구는 판정을 새로 하지 않는다. `bin/axmap.mjs init` 을 그대로 부른다.
   */
  {
    name: 'axmap_init',
    description:
      '이 저장소에 장부가 없을 때 한 번 부른다. 장부 worktree(.axmap/ledger)와 브랜치(axmap/claims)를 만든다. '
      + '이미 있으면 아무것도 바꾸지 않으므로 여러 번 불러도 안전하다. '
      + 'axmap_claim 이 "장부가 없습니다" 로 실패하면 이것을 부른 뒤 다시 claim 하십시오.',
    inputSchema: { type: 'object', properties: {} },
  },
  {
    name: 'axmap_claim',
    description:
      '파일을 수정하기 전에 반드시 호출한다. 건드릴 경로를 선언해 다른 에이전트와 겹치지 않게 한다. ' +
      '거부되면(exit 2) 재시도하지 말고 겹치지 않는 다른 작업으로 옮겨라.',
    inputSchema: {
      type: 'object',
      properties: {
        paths: { type: 'array', items: { type: 'string' }, description: '저장소 루트 기준 상대 경로. 파일 또는 디렉터리' },
        intent: { type: 'string', description: '무엇을 하려는지 한 문장. 사람이 화면에서 이걸 본다' },
        task: { type: 'string', description: '작업/티켓 식별자 (선택)' },
      },
      required: ['paths', 'intent'],
    },
  },
  {
    name: 'axmap_check',
    description: '선언하지 않고 겹침만 미리 확인한다. 작업 계획을 세울 때 쓴다.',
    inputSchema: {
      type: 'object',
      properties: { paths: { type: 'array', items: { type: 'string' } } },
      required: ['paths'],
    },
  },
  {
    name: 'axmap_status',
    description: '지금 누가 어떤 경로를 잡고 있고 무엇을 하려는지 본다.',
    inputSchema: { type: 'object', properties: {} },
  },
  {
    name: 'axmap_release',
    description: '작업이 끝나면 호출한다. 경로를 생략하면 전부 반납한다.',
    inputSchema: {
      type: 'object',
      properties: { paths: { type: 'array', items: { type: 'string' } } },
    },
  },
  {
    name: 'axmap_renew',
    description: '작업이 길어질 때 TTL 을 연장한다. 연장하지 않으면 락이 저절로 풀린다.',
    inputSchema: { type: 'object', properties: {} },
  },

  /**
   * 쪽지함 — 사람을 거치지 않고 다른 에이전트에게 말한다.
   *
   * 🔴 왜 MCP 인가. CLAUDE.md 에 적어두면 **읽은 에이전트만** 쓴다.
   *    도구로 내놓으면 목록에 뜨므로 존재를 알려줄 필요가 없다.
   *
   * ⚠️ 다만 MCP 도 "언제 쓸지" 는 못 정해준다. 그래서 안 읽은 쪽지 알림은
   *    `axmap_claim` 응답에 함께 실린다 — claim 은 규칙상 반드시 부른다.
   */
  {
    name: 'bus_inbox',
    description:
      '다른 에이전트가 나에게 보낸 쪽지를 읽는다. 세션을 시작할 때, 그리고 방향을 '
      + '바꾸기 전에 확인하십시오. id 를 주면 그 쪽지의 본문 전체를 냅니다.',
    inputSchema: {
      type: 'object',
      properties: {
        id: { type: 'string', description: '본문을 볼 쪽지 id. 없으면 목록만.' },
        all: { type: 'boolean', description: '남에게 간 것까지 전부 본다' },
      },
    },
  },
  {
    name: 'bus_send',
    description:
      '다른 에이전트에게 쪽지를 보낸다. 레인 배정, 방향 전환, 상대 코드에서 찾은 결함처럼 '
      + '**상대가 알아야 결정이 달라지는 것**을 보내십시오. 급한 것은 쪽지 대신 '
      + 'axmap_claim 의 intent 에 한 줄로 적는 편이 빠릅니다(그건 status 에 바로 보입니다). '
      + '보낸 쪽지는 커밋해야 상대에게 갑니다.',
    inputSchema: {
      type: 'object',
      properties: {
        to: { type: 'string', description: '받는 에이전트 이름. 생략하면 모두에게.' },
        subject: { type: 'string', description: '한 줄 제목' },
        body: { type: 'string', description: '본문 (마크다운)' },
      },
      required: ['subject', 'body'],
    },
  },
]

// ── 브리핑 ──────────────────────────────────────────────────────────────────

// `SELF` · `REPO` · `FOREIGN` 은 파일 맨 위에 있다. 기준은 거기 적어 두었다.

const readFrom = (base, rel) => {
  try { return fs.readFileSync(path.join(base, rel), 'utf8') } catch { return null }
}
/** axMap 자신의 파일. 코드 지도(`MAP`)처럼 **이 도구에 관한 것**만 여기서 읽는다. */
const readSelf = (rel) => readFrom(SELF, rel)
/** 대상 저장소의 파일. 브리핑의 내용은 원칙적으로 전부 이쪽이어야 한다. */
const readRepo = (rel) => readFrom(REPO, rel)

/** 제목·인용·빈 줄을 건너뛴 첫 문단. 남의 README 를 요약하지 않고 그대로 보여준다. */
function firstPara(text) {
  if (!text) return null
  const lines = text.split(/\r?\n/)
  const buf = []
  for (const raw of lines) {
    const l = raw.trim()
    if (!buf.length && (!l || l.startsWith('#') || l.startsWith('>') || l.startsWith('---'))) continue
    if (!l) break
    buf.push(l)
    if (buf.length >= 4) break
  }
  return buf.length ? buf.join(' ') : null
}

/** `docs/` 안에 무엇이 있는지. 목록일 뿐 지도가 아니다 — 지도는 지어내지 않는다. */
function docsList(base) {
  try {
    return fs.readdirSync(path.join(base, 'docs'))
      .filter((f) => f.toLowerCase().endsWith('.md'))
      .sort()
  } catch { return [] }
}

/**
 * 파일 맨 위 블록 주석의 **첫 문장**.
 *
 * 🔴 지도를 여기에 적어 두지 않고 **파일에서 긁어오는** 이유.
 *
 * 손으로 적은 지도는 코드보다 먼저 낡는다. 그리고 낡은 지도는 없는 지도보다
 * 나쁘다 — 새로 들어온 에이전트가 그것을 믿고 엉뚱한 파일을 고친다.
 * 각 파일의 머리말을 읽어 오면 **파일을 고친 사람이 지도도 고친 것**이 된다.
 * 그래서 이 저장소의 규칙("주석은 왜를 쓴다")이 곧 인수인계 문서가 된다.
 */
function gist(rel) {
  const src = readSelf(rel)
  if (src === null) return '(파일 없음 — 지도가 낡았습니다)'
  const m = src.match(/\/\*\*([\s\S]*?)\*\//)
  if (!m) return '(머리말 주석 없음)'
  for (const raw of m[1].split('\n')) {
    const line = raw.replace(/^\s*\*\s?/, '').trim()
    if (line) return line
  }
  return '(머리말이 비어 있음)'
}

/**
 * 경로만 여기 적고 설명은 파일에서 가져온다.
 * 경로는 거의 안 바뀌고 설명은 자주 바뀌므로, 낡을 수 있는 쪽을 자동화한다.
 */
const MAP = [
  ['판정 (순수)', 'src/protocol.mjs'],
  ['불변식 검사기 (공유)', 'src/invariants.mjs'],
  ['CLI · git · 훅', 'bin/axmap.mjs'],
  ['MCP 서버 (이 파일)', 'mcp/server.mjs'],
  ['뷰어 서버 · API', 'app/server.mjs'],
  ['대화 세션 = CLI 프로세스', 'app/lib/session.mjs'],
  ['세션마다의 이름 (슬롯)', 'app/lib/slots.mjs'],
  ['AI CLI 감지 · 로그인', 'app/lib/agentcli.mjs'],
  ['사다리 배치', 'app/lib/ladder.mjs'],
  ['3D 힘 그래프 렌더러', 'app/web/graph.js'],
  ['화면 전체 (오버레이)', 'app/web/overlay.js'],
  ['대화 칸', 'app/web/chat.js'],
  ['데스크톱 셸 (Electron)', 'desktop/main.mjs'],
]

/** axMap 자신을 겨눴을 때. 코드 지도가 있는 유일한 경우다. */
function briefSelf() {
  const out = []
  out.push('# axMap — 새로 들어온 에이전트를 위한 브리핑')
  out.push('')
  out.push('이 브리핑은 **파일에서 긁어온 것**이라 코드와 함께 갱신된다.')
  out.push('낡았다고 느껴지면 그것은 브리핑이 아니라 그 파일의 머리말이 낡은 것이다.')
  out.push('')

  out.push('## 먼저 읽을 것')
  out.push('')
  out.push('| | |')
  out.push('|---|---|')
  out.push('| 작업 규칙 (필수) | `CLAUDE.md` — **파일을 고치기 전에 `claim` 이 먼저다** |')
  out.push('| 규격 | `docs/SPEC.md` — 명령과 claim 의미론. 코드보다 이쪽이 먼저다 |')
  out.push('| 제품 방향과 버린 대안 | `docs/DECISIONS.md` |')
  out.push('| 지켜야 할 성질 | `docs/INVARIANTS.md` (I1~I7) |')
  out.push('| 왜 파서인가 | `docs/EXPERIMENT.md` |')
  out.push('| 무엇을 재는가 | `docs/BENCH.md` — M3 도달 호출 번호가 주 지표다 |')
  out.push('')

  out.push('## 코드 지도')
  out.push('')
  for (const [role, rel] of MAP) out.push(`- **${role}** — \`${rel}\`\n  ${gist(rel)}`)
  out.push('')
  return out
}

/**
 * 남의 저장소에 붙었을 때.
 *
 * 🔴 여기서 **axMap 의 문서를 대신 보여주지 않는다.**
 *
 * 예전에는 브리핑이 통째로 `readSelf` 였다. 그래서 남의 프로젝트에 붙여도
 * `src/protocol.mjs` · `app/web/graph.js` 같은 **axMap 의 코드 지도**가 나왔고,
 * 받는 쪽에는 자기 저장소를 설명하는 것처럼 보인다.
 *
 * 이 저장소는 "낡은 지도는 없는 지도보다 나쁘다" 고 적어 두었는데,
 * 남의 지도를 자기 지도인 양 주는 것은 그보다 한 단계 더 나쁘다.
 * 낡은 지도는 언젠가 맞았기라도 하다.
 *
 * 그래서 대상에 문서가 없으면 **없다고 말하고 끝낸다.** 채워 넣지 않는다.
 * 코드 지도는 아예 만들지 않는다 — 지어낸 지도가 정확히 그 실패이기 때문이다.
 */
function briefForeign() {
  const out = []
  out.push(`# ${path.basename(REPO)} — axMap 브리핑`)
  out.push('')
  out.push('⚠️ 아래는 전부 **이 대상 저장소에서 읽은 것**이다. axMap 자신의 문서가 아니다.')
  out.push('axMap 은 선점 도구로만 붙어 있고, 이 저장소의 코드에 관해서는 아무것도 모른다.')
  out.push('')

  out.push('## 이 저장소가 스스로 말하는 것')
  out.push('')
  const claude = readRepo('CLAUDE.md')
  out.push(claude
    ? `- \`CLAUDE.md\` **있음 — 가장 먼저 읽는다.** ${firstPara(claude) ?? ''}`.trimEnd()
    : '- `CLAUDE.md` 없음 — 이 저장소에는 에이전트가 지킬 규칙이 아직 적혀 있지 않다')
  const readme = readRepo('README.md')
  if (readme) out.push(`- \`README.md\` — ${firstPara(readme) ?? '(첫 문단이 비어 있다)'}`)
  const docs = docsList(REPO)
  out.push(docs.length
    ? `- \`docs/\` — ${docs.map((d) => `\`${d}\``).join(' · ')}`
    : '- `docs/` 없음')
  out.push('')
  return out
}

/** 제목만 뽑는다. 어느 저장소를 읽든 규칙은 같다. */
const headings = (text, re) => (text ? [...text.matchAll(re)].map((m) => m[1].trim()) : [])

function brief() {
  const read = FOREIGN ? readRepo : readSelf
  const out = FOREIGN ? briefForeign() : briefSelf()

  const decs = headings(read('docs/DECISIONS.md'), /^## (D\d+ · .+)$/gm)
  if (decs.length) {
    out.push('## 이미 내려진 결정 (다시 논의하지 말 것 — 뒤집으려면 근거를 새로 대야 한다)')
    out.push('')
    for (const t of decs) out.push(`- ${t}`)
    out.push('')
  }

  const invs = headings(read('docs/INVARIANTS.md'), /^#{2,3} *(I\d+[^\n]*)$/gm)
  if (invs.length) {
    out.push('## 불변식')
    out.push('')
    for (const t of invs) out.push(`- ${t}`)
    out.push('')
  }

  /**
   * 🔴 검증 목록을 `package.json` 에서 뽑지 않는다.
   *
   * scripts 에는 검사(`test` · `smoke` · `demo:*`)와 실행(`app` · `desktop`)이
   * 섞여 있다. 전부 나열하면 새 에이전트가 앱을 띄워 놓고 "검증했다" 고 여긴다.
   * **무엇이 통과해야 하는지를 정한 곳은 `CLAUDE.md` 의 `## 검증` 절**이므로
   * 거기서 읽어 온다. 규격이 진실이고 목록은 그 사본이다.
   */
  const rules = read('CLAUDE.md')
  const fence = rules?.split('## 검증')[1]?.match(/```[a-z]*\n([\s\S]*?)```/)
  const cmds = fence ? fence[1].split('\n').map((l) => l.trim()).filter((l) => l.startsWith('npm ')) : []
  if (cmds.length) {
    out.push('## 끝내기 전에 전부 통과해야 하는 것')
    out.push('')
    for (const c of cmds) out.push(`- \`${c}\``)
    out.push('')
    // axMap 자신에게만 해당하는 주의다. 남의 저장소에 붙여 말하지 않는다.
    if (!FOREIGN) {
      out.push('🔴 `app/web/` 를 건드렸으면 `npm run smoke` 는 **선택이 아니다.**')
      out.push('`npm test` 는 브라우저 코드를 실행하지 않으므로 화면이 죽어도 초록이다.')
      out.push('')
    }
  }

  // 🔴 막다른 길을 미리 없앤다. 장부가 없으면 claim 이 전부 실패하는데,
  //    브리핑은 보통 claim 보다 먼저 불린다. 여기서 말해 주면 한 번에 이어진다.
  if (!fs.existsSync(path.join(REPO, '.axmap', 'ledger', '.git'))) {
    out.push('## 🔴 이 저장소에는 아직 장부가 없다')
    out.push('')
    out.push('`axmap_init` 을 먼저 부른다. 그 전에는 claim 이 전부 실패한다.')
    out.push('')
  }

  out.push('## 이 도구를 쓰는 순서')
  out.push('')
  out.push('1. `axmap_status` — 지금 누가 무엇을 잡고 있나')
  out.push('2. `axmap_check` — 내가 건드릴 곳이 비었나')
  out.push('3. `axmap_claim` — 좁게 잡는다. 거부(exit 2)되면 재시도하지 말고 방향을 바꾼다')
  out.push('4. 작업 → `axmap_renew` (길어지면) → `axmap_release` (끝나면 즉시)')
  out.push('')
  out.push(`대상 저장소: \`${REPO}\``)
  out.push(`이 도구의 저장소: \`${SELF}\``)
  return out.join('\n')
}

// ---------------------------------------------------------------------------

function callTool(name, args = {}) {
  const paths = Array.isArray(args.paths) ? args.paths.map(String) : []

  switch (name) {
    case 'axmap_brief':
      return { ok: true, text: brief() }
    case 'axmap_init': {
      const r = cli(['init'])
      return {
        ok: r.code === 0,
        text: r.code === 0
          ? `${r.out}\n이제 axmap_claim 을 부를 수 있습니다.`
          : `장부를 만들지 못했습니다 (exit ${r.code})\n${r.out}`,
      }
    }
    case 'axmap_claim': {
      if (!paths.length) return { ok: false, text: 'paths 가 비어 있습니다.' }
      const a = ['claim', ...paths, '--ttl', TTL, '--actor', ACTOR]
      if (args.intent) a.push('--intent', String(args.intent))
      if (args.task) a.push('--task', String(args.task))
      const r = cli(a)
      return {
        ok: r.code === 0,
        text:
          r.code === 0
            ? `선언 완료. 이 경로들만 수정하십시오.\n${r.out}`
            : r.code === 2
              ? `거부되었습니다. 재시도하지 말고 다른 작업으로 옮기십시오.\n${r.out}`
              // 🔴 막다른 길을 남기지 않는다. CLI 는 "axmap init 을 실행하라" 고
              //    말하지만 에이전트에게 셸이 없다. 부를 수 있는 이름으로 바꿔준다.
              : r.out.includes('장부가 없습니다')
                ? `${r.out}\n\n→ MCP 에서는 axmap_init 을 부르십시오. 그 뒤 이 claim 을 다시 시도하면 됩니다.`
                : `실패 (exit ${r.code})\n${r.out}`,
      }
    }
    case 'bus_inbox': {
      const a = args.id ? ['read', String(args.id)] : ['list', ...(args.all ? ['--all'] : ['--to', ACTOR])]
      const r = bus(a)
      return {
        ok: r.code === 0,
        // 🔴 "쪽지 없음" 을 실패로 내지 않는다. 없는 것과 못 읽은 것은 다르다.
        text: r.code === 0 ? (r.out.trim() || '쪽지 없음.') : `쪽지함을 읽지 못했습니다.\n${r.out}`,
      }
    }
    case 'bus_send': {
      if (!args.subject || !args.body) return { ok: false, text: 'subject 와 body 가 필요합니다.' }
      const a = ['post', '--subject', String(args.subject)]
      if (args.to) a.push('--to', String(args.to))
      const r = bus(a, String(args.body))
      return {
        ok: r.code === 0,
        text: r.code === 0
          ? `${r.out}\n쪽지는 **커밋해야** 상대에게 갑니다. 자기 작업과 함께 올리십시오.`
          : `보내지 못했습니다.\n${r.out}`,
      }
    }
    case 'axmap_check': {
      // status 를 읽어 겹침을 계산한다. 장부를 바꾸지 않는다.
      const r = cli(['status', '--json'])
      if (r.code !== 0) return { ok: false, text: r.out }
      let live = { active: [] }
      try { live = JSON.parse(r.out) } catch { /* 그대로 넘긴다 */ }
      const norm = (p) => String(p).replace(/\\/g, '/').replace(/\/+$/, '')
      const hit = []
      for (const c of live.active ?? []) {
        if (c.agent === AGENT) continue
        for (const want of paths.map(norm)) {
          for (const held of (c.paths ?? []).map(norm)) {
            if (want === held || want.startsWith(held + '/') || held.startsWith(want + '/')) {
              hit.push(`${want} ← ${c.agent} (${c.intent ?? c.task ?? '이유 미기재'}) 가 ${held} 를 점유 중`)
            }
          }
        }
      }
      return {
        ok: true,
        text: hit.length ? `겹칩니다:\n${hit.join('\n')}` : '겹치지 않습니다. claim 해도 됩니다.',
      }
    }
    case 'axmap_status': {
      const r = cli(['status'])
      return { ok: r.code === 0, text: r.out || '장부가 비어 있습니다.' }
    }
    case 'axmap_release': {
      const r = cli(['release', ...paths])
      return { ok: r.code === 0, text: r.out }
    }
    case 'axmap_renew': {
      const r = cli(['renew', '--ttl', TTL])
      return { ok: r.code === 0, text: r.out }
    }
    default:
      return { ok: false, text: `알 수 없는 도구: ${name}` }
  }
}

// ---------------------------------------------------------------------------
// JSON-RPC
// ---------------------------------------------------------------------------

function send(msg) {
  process.stdout.write(JSON.stringify(msg) + '\n')
}

function handle(req) {
  const { id, method, params } = req

  if (method === 'initialize') {
    return {
      protocolVersion: '2024-11-05',
      capabilities: { tools: {} },
      serverInfo: { name: 'axmap', version: '0.1.0' },
    }
  }
  if (method === 'tools/list') return { tools: TOOLS }
  if (method === 'tools/call') {
    const r = callTool(params?.name, params?.arguments ?? {})
    return { content: [{ type: 'text', text: r.text }], isError: !r.ok }
  }
  if (method === 'ping') return {}
  return null // 알림이거나 지원하지 않는 메서드
}

const rl = readline.createInterface({ input: process.stdin })
rl.on('line', (line) => {
  const s = line.trim()
  if (!s) return
  let req
  try {
    req = JSON.parse(s)
  } catch {
    return
  }
  // 알림(id 없음)에는 답하지 않는다
  if (req.id === undefined) return
  try {
    const result = handle(req)
    if (result === null) {
      send({ jsonrpc: '2.0', id: req.id, error: { code: -32601, message: `지원하지 않는 메서드: ${req.method}` } })
    } else {
      send({ jsonrpc: '2.0', id: req.id, result })
    }
  } catch (e) {
    send({ jsonrpc: '2.0', id: req.id, error: { code: -32603, message: e.message } })
  }
})

process.stderr.write(`axMap MCP · 저장소 ${REPO} · 에이전트 ${AGENT} (${ACTOR})\n`)
