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

// ── 짧은 길(30m 미만, 모름 -1) 채우기 — S15P21E201-2039 후속 ─────────────────────────
import { fillShortWays } from '../process/walk-graph-dtm-slope.mjs'

// 위도 1e-5도 ≈ 1.11m. 노드 0-1-2-3 을 북쪽으로 늘어놓는다.
const shortGraph = (ways) => ({
  lat: Int32Array.from([351500000, 351502000, 351503500, 351503800]), // 0→1 22m, 1→2 17m, 2→3 3.3m
  lon: Int32Array.from([1290500000, 1290500000, 1290500000, 1290500000]),
  ways,
})

test('짧은 모름 길은 양 끝 지면 높이 차 ÷ 길이로 채운다', () => {
  const g = shortGraph([{ stairs: 0, slope: -1, nodes: [0, 1] }])
  const h = [10, 12.2] // 22m 에 2.2m = 10%
  const stats = fillShortWays(g, (lat) => (lat < 35.1501 ? h[0] : h[1]))
  assert.ok(Math.abs(g.ways[0].slope - 100) <= 2, `${g.ways[0].slope}`)
  assert.equal(stats.filled, 1)
})

test('높이 차 0.5m 미만은 평지(0)', () => {
  const g = shortGraph([{ stairs: 0, slope: -1, nodes: [0, 1] }])
  fillShortWays(g, (lat) => (lat < 35.1501 ? 10 : 10.4))
  assert.equal(g.ways[0].slope, 0)
})

test('10m 미만 길은 높이 차가 커도 이웃 길 값을 따른다(잡음 방지), 이웃이 없으면 모름 그대로', () => {
  const g = shortGraph([
    { stairs: 0, slope: 40, nodes: [1, 2] },
    { stairs: 0, slope: -1, nodes: [2, 3] }, // 3.3m 에 2m 차 → 60% 가 나오면 안 된다
    { stairs: 0, slope: -1, nodes: [3, 3] },
  ])
  fillShortWays(g, (lat) => (lat > 35.15037 ? 12 : 10))
  assert.equal(g.ways[1].slope, 40)
})

test('계단 · 30m 이상 모름(다리·터널) · 고도 없는 길은 그대로', () => {
  const g = shortGraph([
    { stairs: 1, slope: -1, nodes: [0, 1] },
    { stairs: 0, slope: -1, nodes: [0, 1, 2] }, // 39m
    { stairs: 0, slope: -1, nodes: [1, 2] },
  ])
  const stats = fillShortWays(g, (lat) => (lat > 35.1502 ? null : 10))
  assert.deepEqual(g.ways.map((w) => w.slope), [-1, -1, -1])
  assert.equal(stats.missingElevation, 1)
})
