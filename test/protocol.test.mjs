import { test } from 'node:test'
import assert from 'node:assert/strict'
import {
  checkOverlap,
  coversPath,
  normalizePath,
  pathsOverlap,
  activeClaims,
  myActiveClaim,
  agentNameError,
  claimPathError,
} from '../src/protocol.mjs'

const NOW = Date.parse('2026-08-15T09:10:00.000Z')
const MIN = 60_000

const claim = (agent, paths, opts = {}) => ({
  agent,
  task: opts.task ?? `task-${agent}`,
  intent: opts.intent ?? null,
  since: opts.since ?? '2026-08-15T09:00:00.000Z',
  ttlMs: opts.ttlMs ?? 30 * MIN,
  paths,
})

// ---------------------------------------------------------------------------
// docs/EXPERIMENT.md 의 3개 케이스를 그대로 옮긴 것.
// git 의 줄 단위 판정은 Case 1 과 Case 2 에 서로 다른 답을 냈다. 입력이 같은데도.
// 파서 판정은 같은 답을 내야 한다. 이게 이 프로젝트가 파서를 고른 이유 전체다.
// ---------------------------------------------------------------------------

test('Case 1 - 서로 다른 경로, 장부가 비어 있음 -> 통과 (git 은 여기서 오탐했다)', () => {
  const claims = [claim('agent-a', ['src/auth/login.ts'])]
  const r = checkOverlap({ requested: ['src/user/profile.ts'], claims, me: 'agent-b', now: NOW })
  assert.equal(r.ok, true)
})

test('Case 2 - 서로 다른 경로, 장부에 제3자 항목이 있음 -> 통과', () => {
  const claims = [
    claim('agent-a', ['src/auth/login.ts']),
    claim('agent-c', ['src/core/config.ts']),
  ]
  const r = checkOverlap({ requested: ['src/user/profile.ts'], claims, me: 'agent-b', now: NOW })
  assert.equal(r.ok, true)
})

test('Case 1 과 Case 2 는 반드시 같은 답이어야 한다 - 무관한 제3자가 결과를 바꾸면 안 된다', () => {
  const requested = ['src/user/profile.ts']
  const sparse = [claim('agent-a', ['src/auth/login.ts'])]
  const dense = [...sparse, claim('agent-c', ['src/core/config.ts'])]
  const a = checkOverlap({ requested, claims: sparse, me: 'agent-b', now: NOW })
  const b = checkOverlap({ requested, claims: dense, me: 'agent-b', now: NOW })
  assert.deepEqual(a.ok, b.ok)
})

test('Case 3 - 같은 경로 -> 거부하고, 누가 왜 막았는지 말한다', () => {
  const claims = [claim('agent-a', ['src/auth/login.ts'], { task: 'task-12' })]
  const r = checkOverlap({ requested: ['src/auth/login.ts'], claims, me: 'agent-b', now: NOW })
  assert.equal(r.ok, false)
  assert.equal(r.blocks.length, 1)
  assert.equal(r.blocks[0].holder, 'agent-a')
  assert.equal(r.blocks[0].task, 'task-12')
})

// ---------------------------------------------------------------------------
// 경로 판정의 함정들
// ---------------------------------------------------------------------------

test('디렉터리 claim 은 그 아래 파일을 덮는다', () => {
  const claims = [claim('agent-a', ['src/auth'])]
  const r = checkOverlap({ requested: ['src/auth/login.ts'], claims, me: 'agent-b', now: NOW })
  assert.equal(r.ok, false)
  assert.equal(r.blocks[0].held, 'src/auth')
})

test('반대 방향도 막는다 - 파일이 잡힌 상태에서 상위 디렉터리 claim', () => {
  const claims = [claim('agent-a', ['src/auth/login.ts'])]
  const r = checkOverlap({ requested: ['src/auth'], claims, me: 'agent-b', now: NOW })
  assert.equal(r.ok, false)
})

test('단순 문자열 prefix 는 겹침이 아니다 - src/auth 가 src/authz 를 잡아먹으면 안 된다', () => {
  assert.equal(pathsOverlap('src/auth', 'src/authz'), false)
  const claims = [claim('agent-a', ['src/auth'])]
  const r = checkOverlap({ requested: ['src/authz/policy.ts'], claims, me: 'agent-b', now: NOW })
  assert.equal(r.ok, true)
})

test('윈도우 역슬래시와 ./ 접두사를 같은 경로로 본다', () => {
  assert.equal(normalizePath('.\\src\\auth\\login.ts'), 'src/auth/login.ts')
  const claims = [claim('agent-a', ['src/auth/login.ts'])]
  const r = checkOverlap({ requested: ['.\\src\\auth\\login.ts'], claims, me: 'agent-b', now: NOW })
  assert.equal(r.ok, false)
})

// ---------------------------------------------------------------------------
// TTL - 죽은 에이전트의 유령 락
// ---------------------------------------------------------------------------

test('만료된 claim 은 아무것도 막지 못한다', () => {
  const dead = claim('agent-a', ['src/auth/login.ts'], {
    since: '2026-08-15T08:00:00.000Z', // 08:30 만료, 지금은 09:10
    ttlMs: 30 * MIN,
  })
  const r = checkOverlap({ requested: ['src/auth/login.ts'], claims: [dead], me: 'agent-b', now: NOW })
  assert.equal(r.ok, true)
})

test('만료 직전 1분 전에는 여전히 막는다', () => {
  const live = claim('agent-a', ['src/auth/login.ts'])
  const justBefore = Date.parse('2026-08-15T09:29:00.000Z')
  const r = checkOverlap({ requested: ['src/auth/login.ts'], claims: [live], me: 'agent-b', now: justBefore })
  assert.equal(r.ok, false)
})

test('activeClaims 는 만료된 것을 걸러낸다', () => {
  const claims = [
    claim('agent-a', ['a'], { since: '2026-08-15T08:00:00.000Z' }),
    claim('agent-b', ['b']),
  ]
  assert.deepEqual(activeClaims(claims, NOW).map((c) => c.agent), ['agent-b'])
})

// ---------------------------------------------------------------------------
// 자기 자신
// ---------------------------------------------------------------------------

test('내 claim 은 나를 막지 않는다 - 작업 도중 경로를 추가할 수 있어야 한다', () => {
  const claims = [claim('agent-a', ['src/auth/login.ts'])]
  const r = checkOverlap({
    requested: ['src/auth/login.ts', 'src/auth/token.ts'],
    claims,
    me: 'agent-a',
    now: NOW,
  })
  assert.equal(r.ok, true)
})

// ---------------------------------------------------------------------------
// pre-commit 검사 (claim 없이 몰래 고치는 것 차단)
// ---------------------------------------------------------------------------

test('coversPath - claim 한 디렉터리 아래 파일은 커밋 가능', () => {
  assert.equal(coversPath(['src/auth'], 'src/auth/login.ts'), true)
  assert.equal(coversPath(['src/auth/login.ts'], 'src/auth/login.ts'), true)
})

test('coversPath - claim 하지 않은 파일은 차단', () => {
  assert.equal(coversPath(['src/auth'], 'src/user/profile.ts'), false)
  assert.equal(coversPath(['src/auth'], 'src/authz/policy.ts'), false)
})

test('coversPath - claim 이 하나도 없으면 전부 차단', () => {
  assert.equal(coversPath([], 'src/auth/login.ts'), false)
})

// ---------------------------------------------------------------------------
// fail-open 방어
//
// 락 시스템에서 최악의 실패는 거부해야 할 것을 조용히 통과시키는 것이다.
// 아래 테스트들은 전부 실제로 발견된 버그를 고정한 것이다.
// ---------------------------------------------------------------------------

test('만료된 내 claim 은 다음 claim 에 합쳐지지 않는다 - 부활 방지', () => {
  // 1. 내가 src/foo 를 잡았다가 만료됐다
  const expiredMine = claim('agent-a', ['src/foo'], { since: '2026-08-15T08:00:00.000Z' })
  // 2. agent-b 가 정당하게 src/foo 를 가져갔다
  const theirs = claim('agent-b', ['src/foo'])
  // 3. 내가 이제 src/bar 를 claim 한다
  const prev = myActiveClaim([expiredMine, theirs], 'agent-a', NOW)

  // 만료된 내 레코드는 합칠 대상이 아니다.
  // 합쳐졌다면 src/foo 가 검사 없이 부활해 agent-b 와 동시 소유가 된다.
  assert.equal(prev, null)
})

test('만료되지 않은 내 claim 은 정상적으로 합쳐진다', () => {
  const mine = claim('agent-a', ['src/foo'])
  const prev = myActiveClaim([mine], 'agent-a', NOW)
  assert.deepEqual(prev.paths, ['src/foo'])
})

test('에이전트 이름은 치환하지 않고 거부한다 - 파일명 충돌 방지', () => {
  // "agent/a" 와 "agent_a" 를 둘 다 agent_a.json 으로 치환하면
  // 서로의 claim 을 덮어써 소유권이 조용히 사라진다.
  assert.ok(agentNameError('agent/a'))
  assert.ok(agentNameError('../etc/passwd'))
  assert.ok(agentNameError('a\\b'))
  assert.ok(agentNameError('a:b'))
  assert.ok(agentNameError(''))
  assert.ok(agentNameError('a'.repeat(65)))
  assert.ok(agentNameError(' x'), '앞뒤 공백은 파일명에서 사라져 다른 이름과 겹칠 수 있다')
  assert.equal(agentNameError('agent-a'), null)
  assert.equal(agentNameError('claude_1.2'), null)
})

test('한글 등 유니코드 이름을 허용한다', () => {
  // 파일 경로를 깨뜨리는 문자만 막는다. 한글까지 막으면 한국 팀이 자기 이름을 못 쓴다.
  assert.equal(agentNameError('나'), null)
  assert.equal(agentNameError('장효준'), null)
  assert.equal(agentNameError('田中'), null)
  assert.equal(agentNameError('백그라운드-1'), null)
})

test('저장소 밖이나 절대 경로는 claim 할 수 없다', () => {
  assert.ok(claimPathError('/etc/passwd'))
  assert.ok(claimPathError('C:/Windows/System32'))
  assert.ok(claimPathError('../other-repo/src'))
  assert.ok(claimPathError('src/../../escape'))
  assert.ok(claimPathError(''))
  assert.equal(claimPathError('src/auth/login.ts'), null)
  assert.equal(claimPathError('./src/auth/'), null)
})

test('저장소 루트 전체는 claim 할 수 없다', () => {
  assert.ok(claimPathError('.'))
  assert.ok(claimPathError('/'))
})
