/**
 * 버전 계산. 순수 함수라 시계도 git 도 필요 없다.
 *
 * 🔴 여기서 지키는 것은 **아래 자리를 0 으로 되돌리는 것**이다. 1.2.3 에서 minor 를
 *    올리면 1.3.0 이지 1.3.3 이 아니다. 안 되돌리면 patch 자리가 계속 남아 서로 다른
 *    두 갈래의 이력이 한 숫자에 섞이고, 그때부터 버전은 아무것도 가리키지 않는다.
 */

import { describe, it } from 'node:test'
import assert from 'node:assert/strict'
import { levelOf, parseVersion, nextVersion, latestVersion, planBump, formatVersion } from '../src/version.mjs'

describe('브랜치 이름에서 단계를 읽는다', () => {
  it('접두사는 무시한다 — fe/dev 도 be_system/dev 도 dev 다', () => {
    assert.equal(levelOf('dev'), 'dev')
    assert.equal(levelOf('fe/dev'), 'dev')
    assert.equal(levelOf('be_system/dev'), 'dev')
    assert.equal(levelOf('func'), 'func')
    assert.equal(levelOf('ai/func'), 'func')
    assert.equal(levelOf('main'), 'main')
    assert.equal(levelOf('master'), 'main')
  })

  it('기능 브랜치는 단계가 없다 — 버전을 올리지 않는다', () => {
    // feat/S15P21E201-144-login 에서 태그가 붙으면 브랜치마다 번호가 갈린다.
    assert.equal(levelOf('feat/S15P21E201-144-login'), null)
    assert.equal(levelOf('fix/S15P21E201-145-socket'), null)
    assert.equal(levelOf(''), null)
    assert.equal(levelOf(null), null)
  })
})

describe('버전을 읽는다', () => {
  it('v 가 있어도 없어도 읽는다', () => {
    assert.deepEqual(parseVersion('v1.2.3'), { major: 1, minor: 2, patch: 3 })
    assert.deepEqual(parseVersion('1.2.3'), { major: 1, minor: 2, patch: 3 })
  })

  it('🔴 못 읽으면 0.0.0 으로 치지 않고 null 이다', () => {
    // 0 으로 떨어뜨리면 이미 나간 번호를 다시 발급하게 되고,
    // 그때부터 같은 번호가 서로 다른 코드를 가리킨다.
    for (const bad of ['v1.2', 'vNext', 'v-old', '1.2.3.4', '', null, undefined]) {
      assert.equal(parseVersion(bad), null, `${bad} 를 버전으로 읽었다`)
    }
  })
})

describe('다음 버전', () => {
  const at = (s) => parseVersion(s)

  it('dev 는 patch 를 올린다 — 1.0.0 → 1.0.1', () => {
    assert.equal(formatVersion(nextVersion(at('1.0.0'), 'dev')), '1.0.1')
    assert.equal(formatVersion(nextVersion(at('1.0.1'), 'dev')), '1.0.2')
  })

  it('🔴 func 는 minor 를 올리고 patch 를 0 으로 되돌린다 — 1.0.3 → 1.1.0', () => {
    assert.equal(formatVersion(nextVersion(at('1.0.3'), 'func')), '1.1.0')
    assert.equal(formatVersion(nextVersion(at('1.1.0'), 'func')), '1.2.0')
  })

  it('🔴 main 은 major 를 올리고 아래를 전부 0 으로 되돌린다 — 1.2.0 → 2.0.0', () => {
    assert.equal(formatVersion(nextVersion(at('1.2.0'), 'main')), '2.0.0')
    assert.equal(formatVersion(nextVersion(at('1.2.7'), 'main')), '2.0.0')
  })

  it('첫 버전은 단계와 무관하게 1.0.0 이다', () => {
    // dev 에서 시작했다고 0.0.1 을 주면 사람이 "아직 아무것도 없다" 로 읽는데,
    // 실제로는 첫 기능이 들어간 상태다.
    for (const lv of ['dev', 'func', 'main']) {
      assert.equal(formatVersion(nextVersion(null, lv)), '1.0.0')
    }
  })

  it('요구한 흐름을 그대로 재현한다 — 하루치 → 다음날 → 한 주 뒤', () => {
    let v = null
    v = nextVersion(v, 'dev');  assert.equal(formatVersion(v), '1.0.0')  // 첫 작업
    v = nextVersion(v, 'dev');  assert.equal(formatVersion(v), '1.0.1')  // 다음 작업
    v = nextVersion(v, 'dev');  assert.equal(formatVersion(v), '1.0.2')
    v = nextVersion(v, 'func'); assert.equal(formatVersion(v), '1.1.0')  // 다음날, dev → func
    v = nextVersion(v, 'dev');  assert.equal(formatVersion(v), '1.1.1')
    v = nextVersion(v, 'func'); assert.equal(formatVersion(v), '1.2.0')  // 그 다음날
    v = nextVersion(v, 'main'); assert.equal(formatVersion(v), '2.0.0')  // 한 주 뒤, func → main
  })

  it('모르는 단계는 거부한다', () => {
    assert.throws(() => nextVersion(parseVersion('1.0.0'), 'feat'), /알 수 없는 단계/)
  })
})

describe('가장 높은 태그를 고른다', () => {
  it('🔴 문자열이 아니라 숫자로 센다 — v1.10.0 이 v1.9.0 보다 높다', () => {
    // 문자열 정렬은 "1.10.0" < "1.9.0" 이라고 답한다. 그대로 믿으면
    // 다음 버전이 이미 나간 번호가 된다.
    const best = latestVersion(['v1.9.0', 'v1.10.0', 'v1.2.0'])
    assert.equal(formatVersion(best), '1.10.0')
  })

  it('버전이 아닌 태그는 건너뛴다', () => {
    const best = latestVersion(['v-old', 'vNext', 'v1.0.0', 'release'])
    assert.equal(formatVersion(best), '1.0.0')
  })

  it('하나도 없으면 null', () => {
    assert.equal(latestVersion([]), null)
    assert.equal(latestVersion(['v-old']), null)
  })
})

describe('planBump — 브랜치와 태그를 합쳐 다음 태그까지', () => {
  it('func 브랜치에서 dev 작업분을 걷어 올린다', () => {
    const p = planBump('fe/func', ['v1.0.0', 'v1.0.1', 'v1.0.2'])
    assert.equal(p.level, 'func')
    assert.equal(p.from, '1.0.2')
    assert.equal(p.tag, 'v1.1.0')
  })

  it('🔴 기능 브랜치에서는 거부한다 — 브랜치마다 번호가 갈리면 버전이 무의미해진다', () => {
    assert.throws(
      () => planBump('feat/S15P21E201-144-login', ['v1.0.0']),
      /버전을 올리는 브랜치가 아닙니다/,
    )
  })
})
