#!/usr/bin/env node
/**
 * process/walk-graph-dtm-slope.mjs 의 규칙 — S15P21E201-2039.
 * 국토지리정보원 수치지형도로 만든 지면 고도(DTM)로 경사를 다시 잴 때, 계단·모르는 길(-1)·고도 없는 길은 그대로 둔다.
 * 판정은 종료 코드다(0 성공 / 그 외 실패).
 *
 *   node --test test/walk-graph-dtm-slope.test.mjs
 */
import test from 'node:test'
import assert from 'node:assert/strict'
import { applyDtmSlope, p50Slope } from '../process/walk-graph-dtm-slope.mjs'

const line = (n, step = 10) => Array.from({ length: n }, (_, i) => ({ s: i * step }))

test('평지(높이 차 0.5m 안)는 1% 이하 — 0.5m ÷ 기준선 50m', () => {
  const pts = line(49)
  const s = p50Slope(pts, pts.map((p, i) => 5 + (i % 2) * 0.5), 50)
  assert.ok(s <= 0.01, `${s}`)
})

test('고르게 10% 오르는 길은 10%', () => {
  const pts = line(30)
  assert.ok(Math.abs(p50Slope(pts, pts.map((p) => p.s * 0.1), 50) - 0.1) < 1e-9)
})

test('고도가 하나라도 없으면 null — 원래 값을 지킨다', () => {
  const pts = line(10)
  assert.equal(p50Slope(pts, [1, 2, null, 4, 5, 6, 7, 8, 9, 10], 50), null)
})

test('계단 · 모름(-1) · 고도 없는 길은 손대지 않는다', () => {
  const g = {
    lat: Int32Array.from([351500000, 351501000, 351502000]),
    lon: Int32Array.from([1290500000, 1290500000, 1290500000]),
    ways: [
      { stairs: 1, slope: 300, nodes: [0, 1, 2] },
      { stairs: 0, slope: -1, nodes: [0, 1, 2] },
      { stairs: 0, slope: 80, nodes: [0, 1, 2] },
    ],
  }
  const stats = applyDtmSlope(g, () => null, 50)
  assert.deepEqual(g.ways.map((w) => w.slope), [300, -1, 80])
  assert.equal(stats.missingElevation, 1)
  const flat = applyDtmSlope(g, () => 12, 50)
  assert.equal(g.ways[2].slope, 0)
  assert.equal(flat.changed, 1)
})
