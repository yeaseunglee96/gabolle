#!/usr/bin/env node
/**
 * process/walk-graph-flat-floor.mjs 의 규칙 — S15P21E201-2023.
 *
 * 「평지 잡음 바닥」: 길 하나의 높낮이 폭(가장 높은 표본 − 가장 낮은 표본)이 고도 자료 오차(5m) 안이면,
 * 그 길의 경사는 잰 것이 아니라 잡음이다 — 평탄으로 낮춘다. 계단과 모르는 길은 손대지 않는다.
 * 판정은 종료 코드다(0 성공 / 그 외 실패).
 *
 *   node --test test/walk-graph-flat-floor.test.mjs
 */
import test from 'node:test'
import assert from 'node:assert/strict'
import { FLAT_MAX_PERMILLE, FLAT_RANGE_M, flooredSlope } from '../process/walk-graph-flat-floor.mjs'

test('높낮이 폭 5m 안의 노랑 길(서면 대로 7.4%)은 평탄으로 낮아진다', () => {
  // 481m 길, 표본 높이 3~7m(폭 4m), 양끝 차 2m
  const elev = [3, 5, 7, 4, 6, 5]
  const out = flooredSlope({ slope: 74, stairs: 0, elevations: elev, lengthM: 481 })
  assert.ok(out < 50, `평탄(5% 미만)이어야 한다: ${out}‰`)
  assert.equal(out, Math.round((2 / 481) * 1000))
})

test('폭이 꼭 5m 여도 평탄이다(5m 이하)', () => {
  assert.ok(flooredSlope({ slope: 120, stairs: 0, elevations: [0, 5], lengthM: 300 }) < 50)
})

test('짧은 길은 양끝 차로 재도 평탄 문턱 아래로 묶인다', () => {
  // 40m 길에 양끝 4m 차 = 10% — 그래도 폭이 잡음 안이므로 평탄 상한에 묶는다
  assert.equal(flooredSlope({ slope: 150, stairs: 0, elevations: [0, 4], lengthM: 40 }), FLAT_MAX_PERMILLE)
})

test('폭이 5m 를 넘는 길(감천 언덕)은 그대로다', () => {
  assert.equal(flooredSlope({ slope: 158, stairs: 0, elevations: [10, 20, 30, 50], lengthM: 256 }), 158)
})

test('계단은 그대로 — 계단은 늘 빨강이다', () => {
  assert.equal(flooredSlope({ slope: 90, stairs: 1, elevations: [0, 1], lengthM: 50 }), 90)
})

test('모르는 길(-1)은 모름으로 남는다 — 0(평지)으로 바꾸지 않는다', () => {
  assert.equal(flooredSlope({ slope: -1, stairs: 0, elevations: [0, 1], lengthM: 100 }), -1)
})

test('이미 초록인 길은 그대로 — 값을 올리지 않는다', () => {
  assert.equal(flooredSlope({ slope: 20, stairs: 0, elevations: [0, 4], lengthM: 100 }), 20)
})

test('고도를 못 뽑은 길(표본 없음·빈 값)은 그대로', () => {
  assert.equal(flooredSlope({ slope: 74, stairs: 0, elevations: [], lengthM: 400 }), 74)
  assert.equal(flooredSlope({ slope: 74, stairs: 0, elevations: [3, null, 4], lengthM: 400 }), 74)
})

test('문턱 상수 — 5m · 평탄 상한 49‰(지도 초록은 5% 미만)', () => {
  assert.equal(FLAT_RANGE_M, 5)
  assert.equal(FLAT_MAX_PERMILLE, 49)
})
