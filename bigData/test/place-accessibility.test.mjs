/**
 * 휠체어 접근 판정 규칙 — **표기 변형에 안 새는가**
 *
 * 🔴 왜 이 시험이 필요한가. 이 규칙은 **틀려도 아무 데도 빨간불이 안 켜진다.**
 *    항목 이름이 안 걸리면 그 장소는 그냥 "근거 없음" 으로 세어지고 오류도 로그도 안 남는다.
 *    실제로 그럴 뻔했다 —
 *
 *      주출입구 높이차이 제거          71곳
 *      주출입구높이차이제거(경사로)      3곳   ← 공백 없음 + 괄호
 *
 *    같은 것인데 정확 일치로는 안 걸린다. 2026-09-21 에 표본을 33곳에서 88곳으로 넓히자
 *    항목 이름이 5종에서 11종으로 늘면서 드러났다. **표본이 작으면 사전이 거짓말을 한다.**
 *
 * 🔴 반대쪽도 지킨다. 느슨하게 풀면 못 들어가는 곳을 들어갈 수 있다고 말하게 된다.
 *    「주출입구(문)」·「주출입문」은 **걸리면 안 된다** — 문이 있다는 것만으로는
 *    통과 여부를 모른다. 접근성 자료에서 그것이 가장 위험한 실패다.
 *
 * 🔴 쉼표를 지우지 않는다. 지우면 앞 항목의 끝과 뒤 항목의 앞이 붙어 **없던 이름이 생긴다** —
 *    「주출입구, 접근로 안내」 가 「주출입구접근로」 로 읽힌다.
 *
 *   node --test test/place-accessibility.test.mjs
 */
import { describe, it } from 'node:test'
import assert from 'node:assert/strict'
import { meetsRule } from '../process/place-accessibility.mjs'

describe('휠체어 접근 판정 규칙', () => {
  it('기본 표기를 붙인다', () => {
    assert.equal(meetsRule('주출입구 접근로'), true)
    assert.equal(meetsRule('주출입구 높이차이 제거'), true)
    assert.equal(meetsRule('주출입구 높이차이 제거, 주출입구 접근로, 주출입구(문)'), true)
  })

  it('공백이 없고 괄호가 붙은 변형도 붙인다', () => {
    assert.equal(meetsRule('주출입구높이차이제거(경사로)'), true)
    assert.equal(
      meetsRule('장애인전용주차구역, 주출입구높이차이제거(경사로), 주출입문, 승강기'),
      true,
      '변형만 있는 줄이 새면 안 된다',
    )
  })

  it('문만 있으면 안 붙인다 — 좁은 쪽을 지킨다', () => {
    assert.equal(meetsRule('주출입구(문)'), false)
    assert.equal(meetsRule('주출입문'), false)
    assert.equal(meetsRule('승강기, 주출입구(문)'), false)
  })

  it('앱 코드에 대응이 없는 항목은 안 붙인다', () => {
    assert.equal(meetsRule('장애인사용가능화장실'), false)
    assert.equal(meetsRule('장애인전용주차구역'), false)
    assert.equal(meetsRule('승강기'), false)
    assert.equal(meetsRule('안내설비, 장애인사용가능객실'), false)
  })

  it('쉼표로 갈린 두 항목이 붙어서 없던 이름이 되면 안 된다', () => {
    assert.equal(
      meetsRule('주출입구, 접근로 안내'),
      false,
      '쉼표까지 지우면 「주출입구접근로」 가 되어 거짓으로 통과한다',
    )
  })

  it('값이 없으면 안 붙인다 — 모르는 것을 있다고 하지 않는다', () => {
    assert.equal(meetsRule(null), false)
    assert.equal(meetsRule(undefined), false)
    assert.equal(meetsRule(''), false)
    assert.equal(meetsRule('   '), false)
  })
})
