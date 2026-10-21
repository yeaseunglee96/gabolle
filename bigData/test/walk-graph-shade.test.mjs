#!/usr/bin/env node
/**
 * process/walk-graph-shade.mjs 의 규칙 — S15P21E201-1895.
 *
 * 작은 가짜 그래프를 GBWG 바이트 그대로 만들어 읽고·맞추고·쓴다. 진짜 파일은 건드리지 않는다.
 * 판정은 종료 코드다(0 성공 / 그 외 실패). 개수를 여기에 적지 않는다.
 *
 *   node --test test/walk-graph-shade.test.mjs
 */
import test from 'node:test'
import assert from 'node:assert/strict'
import { gunzipSync } from 'node:zlib'
import {
  SHADE_UNKNOWN, buildGraphV2, gzipDeterministic, matchShade, parseGraph,
} from '../process/walk-graph-shade.mjs'

const E7 = (deg) => Math.round(deg * 1e7)

/** 점 넷을 한 줄로 이은 길 셋(A-B, B-C, C-D). 경도 0.0011° ≈ 100m. */
function sampleGraph({ shade = SHADE_UNKNOWN } = {}) {
  const lat = Int32Array.from([35.1, 35.1, 35.1, 35.1].map(E7))
  const lon = Int32Array.from([129.0, 129.0011, 129.0022, 129.0033].map(E7))
  const way = (a, b, slope) => ({ stairs: 0, slope, shade, nodes: Int32Array.from([a, b]) })
  return { version: 1, lat, lon, ways: [way(0, 1, 20), way(1, 2, 30), way(2, 3, -1)] }
}

/** 지도 겹 구간 한 개. 좌표는 [경도, 위도] 쌍을 평평하게 — 5자리로 반올림한 정수. */
function layerSeg(points, shadow, slope = 0) {
  const pts = new Int32Array(points.length * 2)
  points.forEach(([x, y], i) => { pts[2 * i] = Math.round(x * 1e5); pts[2 * i + 1] = Math.round(y * 1e5) })
  return { slope, shadow, pts, area: 'TEST' }
}

const ab = [[129.0, 35.1], [129.0011, 35.1]]
const bc = [[129.0011, 35.1], [129.0022, 35.1]]

test('v2 로 쓰고 다시 읽으면 길·그늘이 그대로다', () => {
  const g = sampleGraph()
  g.ways[0].shade = 37
  g.ways[1].shade = 0
  const back = parseGraph(buildGraphV2(g))
  assert.equal(back.version, 2)
  assert.deepEqual(back.ways.map((w) => w.shade), [37, 0, SHADE_UNKNOWN])
  assert.deepEqual(back.ways.map((w) => w.slope), [20, 30, -1])
})

test('v1 파일도 읽는다 — 그늘은 전부 모름(255)이다', () => {
  // v1 바이트를 손으로 쓴다: 길 = [점 수 · 계단 · 경사 · 점 번호…], 그늘 칸 없음
  const g = sampleGraph()
  const head = Buffer.alloc(20)
  head.write('GBWG', 0, 'ascii'); head.writeInt32BE(1, 4)
  head.writeInt32BE(g.lat.length, 8); head.writeInt32BE(g.ways.length, 12); head.writeInt32BE(g.ways.length * 2, 16)
  const nodes = Buffer.alloc(g.lat.length * 8)
  g.lat.forEach((v, i) => { nodes.writeInt32BE(v, i * 8); nodes.writeInt32BE(g.lon[i], i * 8 + 4) })
  const ways = Buffer.concat(g.ways.map((w) => {
    const b = Buffer.alloc(4 + 1 + 2 + w.nodes.length * 4)
    b.writeInt32BE(w.nodes.length, 0); b[4] = w.stairs; b.writeInt16BE(w.slope, 5)
    w.nodes.forEach((n, i) => b.writeInt32BE(n, 7 + i * 4))
    return b
  }))
  const v1 = parseGraph(Buffer.concat([head, nodes, ways]))
  assert.equal(v1.version, 1)
  assert.ok(v1.ways.every((w) => w.shade === SHADE_UNKNOWN))
  assert.deepEqual(v1.ways.map((w) => w.slope), [20, 30, -1])
})

test('그늘 값이 0~100 도 255 도 아니면 쓰기를 거절한다 — 깨진 값을 파일에 굳히지 않는다', () => {
  const g = sampleGraph()
  g.ways[0].shade = 150
  assert.throws(() => buildGraphV2(g), /그늘 값/)
})

test('모양이 통째로 같은 길에 그늘을 붙인다 — 뒤집힌 줄도 같은 길이다', () => {
  const g = sampleGraph()
  const { shade, stats } = matchShade(g, [layerSeg(ab, 40), layerSeg([...bc].reverse(), 70)])
  assert.deepEqual(Array.from(shade), [40, 70, SHADE_UNKNOWN])
  assert.equal(stats.exact, 2)
})

test('짝이 없는 길은 이웃 값으로 메우지 않고 모름(255)으로 둔다', () => {
  const g = sampleGraph()
  const { shade } = matchShade(g, [layerSeg(ab, 40)])
  assert.equal(shade[1], SHADE_UNKNOWN)
  assert.equal(shade[2], SHADE_UNKNOWN)
})

test('0%(볕)는 모름이 아니다 — 0 으로 붙는다', () => {
  const g = sampleGraph()
  const { shade } = matchShade(g, [layerSeg(ab, 0)])
  assert.equal(shade[0], 0)
  assert.notEqual(shade[0], SHADE_UNKNOWN)
})

test('겹친 지역이 같은 길에 다른 값을 주면 평균을 반올림하고 충돌로 센다', () => {
  const g = sampleGraph()
  const { shade, stats } = matchShade(g, [layerSeg(ab, 20), layerSeg(ab, 41)])
  assert.equal(shade[0], 31)
  assert.equal(stats.conflicts, 1)
})

test('같은 값이면 충돌이 아니다', () => {
  const g = sampleGraph()
  const { shade, stats } = matchShade(g, [layerSeg(ab, 55), layerSeg(ab, 55)])
  assert.equal(shade[0], 55)
  assert.equal(stats.conflicts, 0)
})

test('사이 점이 3m 안쪽으로 어긋난 구간은 2단계로 맞춘다, 그보다 멀면 안 맞춘다', () => {
  // 세 점짜리 길: 가운데 점이 지도 겹과 살짝 다르다.
  const lat = Int32Array.from([35.1, 35.1, 35.1].map(E7))
  const lon = Int32Array.from([129.0, 129.0006, 129.0012].map(E7))
  const g = { version: 1, lat, lon, ways: [{ stairs: 0, slope: 10, shade: SHADE_UNKNOWN, nodes: Int32Array.from([0, 1, 2]) }] }
  const near = layerSeg([[129.0, 35.1], [129.00061, 35.10001], [129.0012, 35.1]], 60) // 약 1m
  const far = layerSeg([[129.0, 35.1], [129.0006, 35.10010], [129.0012, 35.1]], 60) // 약 11m
  const okNear = matchShade(g, [near])
  assert.equal(okNear.shade[0], 60)
  assert.equal(okNear.stats.tolerant, 1)
  const notFar = matchShade(g, [far])
  assert.equal(notFar.shade[0], SHADE_UNKNOWN)
})

test('같은 입력이면 같은 gzip 바이트다 — 수정 시각과 만든 OS 를 고정한다', () => {
  const g = sampleGraph()
  g.ways[0].shade = 37
  const a = gzipDeterministic(buildGraphV2(g))
  const b = gzipDeterministic(buildGraphV2(g))
  assert.ok(a.equals(b))
  assert.deepEqual(Array.from(a.subarray(4, 8)), [0, 0, 0, 0])
  assert.equal(a[9], 255)
  assert.ok(gunzipSync(a).equals(buildGraphV2(g)))
})

test('v2 를 입력으로 다시 돌려도 결과가 같다(멱등)', () => {
  const g = sampleGraph()
  const layers = [layerSeg(ab, 40), layerSeg(bc, 70)]
  const first = matchShade(g, layers)
  g.ways.forEach((w, k) => { w.shade = first.shade[k] })
  const bytes1 = buildGraphV2(g)
  const again = parseGraph(bytes1)
  const second = matchShade(again, layers)
  again.ways.forEach((w, k) => { w.shade = second.shade[k] })
  assert.ok(buildGraphV2(again).equals(bytes1))
})
