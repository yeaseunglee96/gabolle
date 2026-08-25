/**
 * CLI 종료 코드.
 *
 * 🔴 순수 로직 테스트로는 **못 잡는 층**이 있다. `applyRelease` 는 예전에도
 *    `hadNothing` 을 정직하게 냈다. 그것을 받아 **0 을 반환하던 것**이 사고였다.
 *    판정은 맞는데 바깥에 전하는 신호가 틀린 경우다.
 *
 *    실제로 물렸다 — `AXMAP_AGENT=X` 로 claim 한 뒤 그 변수가 없는 셸에서
 *    release 를 불렀고, 이름이 `git config` 로 떨어져 아무것도 반납되지 않은
 *    채 종료 코드 0 이 나왔다. 반납했다고 믿었지만 락은 30분 더 남아 있었다.
 *
 * 그래서 여기서는 진짜 저장소를 만들고 진짜 프로세스를 돌린다.
 */

import { describe, it, before, after } from 'node:test'
import assert from 'node:assert/strict'
import { spawnSync } from 'node:child_process'
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const ROOT = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..')
const CLI = path.join(ROOT, 'bin', 'axmap.mjs')

let repo = null

/** 이 저장소 안에서 axMap 을 돌린다. `agent` 가 null 이면 환경변수를 **뺀다.** */
function bm(args, agent) {
  const env = { ...process.env }
  delete env.AXMAP_AGENT
  if (agent) env.AXMAP_AGENT = agent
  return spawnSync(process.execPath, [CLI, ...args], {
    cwd: repo, env, encoding: 'utf8', windowsHide: true,
  })
}

const git = (...a) => spawnSync('git', a, { cwd: repo, encoding: 'utf8', windowsHide: true })

before(() => {
  repo = fs.mkdtempSync(path.join(os.tmpdir(), 'axmap-cli-'))
  git('init', '-q')
  // 이름을 고정한다. 폴백 경로가 이 이름을 집는지 확인해야 하기 때문이다.
  git('config', 'user.name', 'fallback-name')
  git('config', 'user.email', 'x@example.com')
  git('config', 'commit.gpgsign', 'false')
  fs.writeFileSync(path.join(repo, 'a.txt'), 'hello\n')
  git('add', '-A')
  git('commit', '-q', '-m', 'first')
  const r = bm(['init'], 'setup')
  assert.equal(r.status, 0, `init 실패: ${r.stderr || r.stdout}`)
})

after(() => {
  try { fs.rmSync(repo, { recursive: true, force: true }) } catch { /* 지워지면 그만 */ }
})

describe('release 종료 코드', () => {
  it('잡은 사람이 반납하면 0', () => {
    assert.equal(bm(['claim', 'a.txt', '--task', 'T1', '--intent', '테스트'], 'alice').status, 0)
    const r = bm(['release'], 'alice')
    assert.equal(r.status, 0, r.stderr)
  })

  it('🔴 다른 이름으로 반납하면 5 — 조용히 성공하지 않는다', () => {
    assert.equal(bm(['claim', 'a.txt', '--task', 'T2', '--intent', '테스트'], 'alice').status, 0)
    const r = bm(['release'], 'bob')
    assert.equal(r.status, 5, `종료 코드가 ${r.status} 다. 0 이면 예전 사고가 돌아온 것이다`)
    // 락이 남아 있어야 한다 — 남의 이름으로 부른 것이 남의 락을 풀면 더 나쁘다
    assert.match(bm(['status'], 'bob').stdout, /alice/)
  })

  it('이름이 어디서 왔는지 말한다 — 원인이 거의 항상 그것이다', () => {
    const r = bm(['release'], null)   // 환경변수 없음 → git config 로 폴백
    assert.equal(r.status, 5)
    assert.match(r.stderr, /fallback-name/, '떨어진 이름을 안 알려준다')
    assert.match(r.stderr, /git config/, '어디서 왔는지 안 알려준다')
  })

  it('장부에 남의 것이 있으면 그것을 보여준다', () => {
    const r = bm(['release'], 'bob')
    assert.match(r.stderr, /alice \[T2\]/, '누가 잡고 있는지 안 보여주면 고칠 수가 없다')
    assert.equal(bm(['release'], 'alice').status, 0)
  })

  it('아무도 아무것도 안 잡았을 때도 5 — 반납할 것이 없는 것은 같다', () => {
    // 두 번 반납하면 두 번째는 실패다. 그것이 진실이다.
    const r = bm(['release'], 'alice')
    assert.equal(r.status, 5)
  })
})

/**
 * 🔴 원자성 — 관문 1 이 CAS 인 이상 전제 조건이다.
 *
 * claim 레코드를 그냥 writeFileSync 로 쓰면, 다른 에이전트의 `git add -A` 가
 * **반쯤 쓰인 파일**을 스테이징할 수 있다. readClaims 는 파싱 실패를 건너뛰지 않고
 * 전체를 중단하므로(fail-closed), 그 순간 팀 전원의 장부가 함께 멈춘다.
 * 창은 좁지만 터지면 범위가 전부다.
 *
 * 그래서 임시 파일에 다 쓴 뒤 rename 하고, 임시 이름은 장부가 무시한다.
 * 아래 세 개는 그 설계가 실제로 서 있는지를 고정한다.
 */
describe('장부 쓰기의 원자성', () => {
  const ledger = () => path.join(repo, '.axmap', 'ledger')
  const lgit = (...a) =>
    spawnSync('git', a, { cwd: ledger(), encoding: 'utf8', windowsHide: true })

  it('임시 파일은 장부에 스테이징되지 않는다', () => {
    fs.writeFileSync(path.join(repo, 'atom1.txt'), 'x\n')
    assert.equal(bm(['claim', 'atom1.txt', '--task', 'AT1', '--intent', '원자성'], 'atom1').status, 0)
    const tmp = path.join(ledger(), 'claims', 'atom1.json.tmp-99999')
    fs.writeFileSync(tmp, '{"agent":"atom')
    try {
      // -n 은 무엇을 넣을지만 말하고 인덱스를 건드리지 않는다.
      // 같은 파일의 다른 테스트가 무작위 순서로 끼어들어도 안전하다.
      const out = lgit('add', '-A', '-n').stdout ?? ''
      assert.ok(!out.includes('tmp-99999'), `임시 파일이 장부에 실린다:\n${out}`)
    } finally {
      fs.rmSync(tmp, { force: true })
      bm(['release'], 'atom1')
    }
  })

  it('깨진 임시 파일이 있어도 판정은 멈추지 않는다', () => {
    const tmp = path.join(ledger(), 'claims', 'garbage.json.tmp-99998')
    fs.writeFileSync(tmp, '{ 이건 JSON 이 아니다')
    try {
      // `.json` 으로 끝나지 않으므로 readClaims 가 아예 보지 않는다.
      // 이것이 "못 막을 때는 실패의 크기라도 낮춘다" 의 실물이다.
      assert.equal(bm(['status'], 'atom2').status, 0)
    } finally {
      fs.rmSync(tmp, { force: true })
    }
  })

  it('claim 이 끝나면 임시 파일이 남지 않는다', () => {
    fs.writeFileSync(path.join(repo, 'atom3.txt'), 'x\n')
    assert.equal(bm(['claim', 'atom3.txt', '--task', 'AT3', '--intent', '원자성'], 'atom3').status, 0)
    try {
      const left = fs
        .readdirSync(path.join(ledger(), 'claims'))
        .filter((f) => f.includes('.tmp-'))
      assert.deepEqual(left, [], `임시 파일이 남았다: ${left.join(', ')}`)
    } finally {
      bm(['release'], 'atom3')
    }
  })
})
