/**
 * MCP 서버를 **남의 저장소에 붙인 상태**로 돌린다.
 *
 * 🔴 이 파일이 없던 동안 세 가지가 동시에 깨져 있었다. 셋 다 원인이 하나다 —
 *    서버를 언제나 axMap 자신에게 겨눠 놓고 시험했고, 그때는 `REPO` 와 `SELF` 가
 *    같은 경로라서 둘을 헷갈려도 아무 증상이 없다.
 *
 *      1. `bus.mjs` 를 `REPO` 에서 찾아 남의 저장소에서 MODULE_NOT_FOUND 로 죽었다
 *      2. `axmap_init` 이 도구 목록에 없어, 장부 없는 저장소에서 에이전트가
 *         "axmap init 을 실행하라" 는 지시를 읽고도 실행할 수 없었다
 *      3. `axmap_brief` 가 대상이 무엇이든 axMap 자신의 코드 지도를 냈다
 *
 *    `npm test` 도 `npm run demo` 도 전부 초록이었다. 도는 조합이 하나뿐이었기
 *    때문이다. 그래서 여기서 고정하는 것은 기능이 아니라 **조합**이다:
 *    `REPO ≠ SELF`, 그리고 장부가 아직 없는 상태.
 *
 * 서버는 stdio + 줄바꿈 JSON-RPC 라 프로세스를 진짜로 띄워서 말을 건다.
 * 내부 함수를 import 해서 부르면 위의 셋을 하나도 못 잡는다 — 셋 다
 * 프로세스 경계와 경로 해석에서 생긴 문제다.
 */

import { describe, it, before, after } from 'node:test'
import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const SELF = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const SERVER = path.join(SELF, 'mcp', 'server.mjs')

let repo = null

/**
 * 요청들을 한 번에 밀어 넣고 응답을 순서대로 받는다.
 * 서버가 요청 하나마다 한 줄로 답하므로 id 로 짝을 맞춘다.
 */
function rpc(requests, { repoPath = repo, agent = 'tester' } = {}) {
  const lines = [
    { jsonrpc: '2.0', id: 0, method: 'initialize', params: { protocolVersion: '2024-11-05', capabilities: {}, clientInfo: { name: 't', version: '0' } } },
    { jsonrpc: '2.0', method: 'notifications/initialized' },
    ...requests,
  ]
  const env = { ...process.env, AXMAP_REPO: repoPath, AXMAP_AGENT: agent }
  const r = spawnSync(process.execPath, [SERVER], {
    input: lines.map((l) => JSON.stringify(l)).join('\n') + '\n',
    encoding: 'utf8',
    windowsHide: true,
    env,
  })
  const out = new Map()
  for (const line of (r.stdout ?? '').split('\n')) {
    if (!line.trim()) continue
    const m = JSON.parse(line)
    if (m.id !== undefined) out.set(m.id, m)
  }
  return out
}

const call = (id, name, args = {}) => ({ jsonrpc: '2.0', id, method: 'tools/call', params: { name, arguments: args } })
const textOf = (msg) => msg?.result?.content?.[0]?.text ?? ''

const git = (...a) => spawnSync('git', a, { cwd: repo, encoding: 'utf8', windowsHide: true })

before(() => {
  repo = fs.mkdtempSync(path.join(os.tmpdir(), 'axmap-mcp-'))
  git('init', '-q')
  git('config', 'user.name', 'foreign-user')
  git('config', 'user.email', 'x@example.com')
  git('config', 'commit.gpgsign', 'false')
  // 대상 저장소는 자기만의 문서를 갖는다. 브리핑이 **이것들**을 읽어야 한다.
  fs.writeFileSync(path.join(repo, 'CLAUDE.md'), '# 남의 프로젝트\n\n영상 스트리밍 백엔드다.\n\n## 검증\n\n```bash\nnpm run lint\n```\n')
  fs.mkdirSync(path.join(repo, 'docs'), { recursive: true })
  fs.writeFileSync(path.join(repo, 'docs', 'DECISIONS.md'), '# 결정\n\n## D1 · 큐는 Redis 로 간다\n')
  fs.writeFileSync(path.join(repo, 'app.js'), 'console.log(1)\n')
  git('add', '-A')
  git('commit', '-q', '-m', 'first')
  // 🔴 일부러 `axmap init` 을 하지 않는다. 장부 없는 상태가 이 파일의 절반이다.
})

after(() => {
  try { fs.rmSync(repo, { recursive: true, force: true }) } catch { /* 지워지면 그만 */ }
})

describe('남의 저장소에 붙었을 때', () => {
  it('🔴 장부가 없으면 claim 이 부를 수 있는 이름을 알려준다 — 막다른 길을 남기지 않는다', () => {
    const r = rpc([call(1, 'axmap_claim', { paths: ['app.js'], intent: '무언가' })])
    const t = textOf(r.get(1))
    assert.equal(r.get(1).result.isError, true, '장부가 없는데 성공으로 내면 안 된다')
    assert.match(t, /장부가 없습니다/)
    // CLI 는 셸 명령을 안내한다. 에이전트에게는 셸이 없으므로 도구 이름이 함께 있어야 한다.
    assert.match(t, /axmap_init/, 'MCP 에서 부를 수 있는 이름이 안내에 없다')
  })

  it('🔴 axmap_init 이 도구 목록에 있다', () => {
    const r = rpc([{ jsonrpc: '2.0', id: 1, method: 'tools/list', params: {} }])
    const names = r.get(1).result.tools.map((t) => t.name)
    assert.ok(names.includes('axmap_init'), `도구 목록에 axmap_init 이 없다: ${names.join(', ')}`)
  })

  it('axmap_init → claim 이 이어진다 — 사람이 셸로 내려가지 않아도 된다', () => {
    const r = rpc([
      call(1, 'axmap_init'),
      call(2, 'axmap_claim', { paths: ['app.js'], intent: 'init 뒤에는 되어야 한다' }),
    ])
    assert.equal(r.get(1).result.isError, false, `init 실패: ${textOf(r.get(1))}`)
    assert.equal(r.get(2).result.isError, false, `init 뒤 claim 실패: ${textOf(r.get(2))}`)
    assert.ok(fs.existsSync(path.join(repo, '.axmap', 'ledger', '.git')), '장부 worktree 가 안 생겼다')
  })

  it('🔴 쪽지함이 대상 저장소에 생긴다 — axMap 저장소가 아니라', () => {
    const r = rpc([call(1, 'bus_send', { to: 'someone', subject: '제목', body: '본문' })])
    assert.equal(r.get(1).result.isError, false, `쪽지를 못 보냈다: ${textOf(r.get(1))}`)

    const box = path.join(repo, 'docs', 'bus')
    const here = fs.existsSync(box) ? fs.readdirSync(box).filter((f) => f.endsWith('.md')) : []
    assert.ok(here.length > 0, '대상 저장소에 쪽지가 안 생겼다 — bus.mjs 를 REPO 에서 찾고 있지 않은지 본다')

    // 🔴 axMap 자신의 쪽지함이 오염되면 안 된다. 팀의 쪽지가 도구 저장소에 쌓이는 것은
    //    조용한 실패다 — 보낸 사람은 성공을 보고, 받는 사람은 영원히 못 받는다.
    //
    //    쪽지함 폴더가 아예 없는 저장소도 있다(새로 시작한 팀). 그때 이 검사는
    //    자동으로 통과여야지 예외로 죽으면 안 된다 — 없는 것과 오염된 것은 다르다.
    let mine = []
    try { mine = fs.readdirSync(path.join(SELF, 'docs', 'bus')) } catch { /* 없으면 빈 것 */ }
    assert.equal(mine.filter((f) => f.includes('제목')).length, 0, 'axMap 저장소에 남의 쪽지가 떨어졌다')
  })

  it('bus_inbox 가 예외로 죽지 않는다', () => {
    const r = rpc([call(1, 'bus_inbox', {})], { agent: 'someone' })
    const t = textOf(r.get(1))
    assert.equal(r.get(1).result.isError, false, `쪽지함이 죽었다: ${t}`)
    assert.doesNotMatch(t, /MODULE_NOT_FOUND|Cannot find module/, 'bus.mjs 를 대상 저장소에서 찾고 있다')
  })
})

describe('브리핑은 대상 저장소를 설명한다', () => {
  it('🔴 axMap 자신의 코드 지도를 남에게 주지 않는다', () => {
    const t = textOf(rpc([call(1, 'axmap_brief')]).get(1))
    // 이것들은 axMap 의 파일이다. 남의 브리핑에 나오면 남의 지도를 준 것이다.
    for (const leak of ['src/protocol.mjs', 'app/web/graph.js', 'desktop/main.mjs']) {
      assert.ok(!t.includes(leak), `대상 저장소 브리핑에 axMap 의 파일이 샜다: ${leak}`)
    }
  })

  it('대상의 문서를 읽는다', () => {
    const t = textOf(rpc([call(1, 'axmap_brief')]).get(1))
    assert.match(t, /영상 스트리밍 백엔드다/, '대상의 CLAUDE.md 를 안 읽었다')
    assert.match(t, /D1 · 큐는 Redis 로 간다/, '대상의 DECISIONS.md 를 안 읽었다')
    assert.match(t, /npm run lint/, '대상의 검증 절을 안 읽었다')
  })

  it('axMap 의 검증 명령을 남의 저장소에 요구하지 않는다', () => {
    const t = textOf(rpc([call(1, 'axmap_brief')]).get(1))
    assert.doesNotMatch(t, /npm run demo:chaos/, 'axMap 의 검증 목록이 샜다')
  })
})

describe('자기 자신을 겨눴을 때는 그대로다', () => {
  it('코드 지도와 검증 목록이 나온다', () => {
    const t = textOf(rpc([call(1, 'axmap_brief')], { repoPath: SELF }).get(1))
    assert.match(t, /## 코드 지도/)
    assert.match(t, /src\/protocol\.mjs/)
    assert.match(t, /npm run demo:chaos/, 'CLAUDE.md 의 검증 절에서 읽어와야 한다')
  })
})

describe('이름이 없으면 뜨지 않는다', () => {
  it('AXMAP_AGENT 도 git config user.name 도 없으면 서버가 죽는다', () => {
    // 기본 이름으로 채우면 여러 사람이 장부에서 한 명이 된다. 그래서 fail-closed 다.
    const bare = fs.mkdtempSync(path.join(os.tmpdir(), 'axmap-noname-'))
    try {
      const env = { ...process.env, AXMAP_REPO: bare }
      delete env.AXMAP_AGENT
      // git 저장소가 아니므로 user.name 폴백도 비어 있다.
      const r = spawnSync(process.execPath, [SERVER], {
        input: '', encoding: 'utf8', windowsHide: true, env,
      })
      assert.notEqual(r.status, 0, '이름 없이 떴다 — 여러 에이전트가 한 사람이 된다')
      assert.match(r.stderr, /에이전트 이름을 알 수 없습니다/)
    } finally {
      try { fs.rmSync(bare, { recursive: true, force: true }) } catch { /* 지워지면 그만 */ }
    }
  })
})
