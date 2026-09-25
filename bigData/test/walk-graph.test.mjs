/**
 * 보행 그래프 — 어느 길을 쓰고, 어떻게 잇고, 경사를 언제 모른다고 하는가 (S15P21E201-1630)
 *
 * 🔴 왜 이 시험이 필요한가. 그래프가 틀려도 빨간불이 안 켜진다 — 길찾기가 못 이으면 서버는 조용히
 *    직선 어림으로 떨어지고, 잘못 이으면 자동차 전용도로를 걷는 길로 안내한다.
 *
 *   node --test test/walk-graph.test.mjs
 */
import { describe, it } from 'node:test'
import assert from 'node:assert/strict'
import { keepWay, isStairs, slopePermille, buildGraph, encode, largestComponentShare } from '../process/walk-graph.mjs'

describe('어느 길을 쓰는가', () => {
  it('보통 길·보도·계단은 쓴다', () => {
    assert.equal(keepWay({ highway: 'residential' }), true)
    assert.equal(keepWay({ highway: 'footway' }), true)
    assert.equal(keepWay({ highway: 'steps' }), true)
    assert.equal(keepWay({ highway: 'trunk' }), true, '보도가 딸린 간선도로는 걷는다')
  })

  it('사람이 못 걷는 길은 뺀다', () => {
    assert.equal(keepWay({ highway: 'motorway' }), false)
    assert.equal(keepWay({ highway: 'motorway_link' }), false)
    assert.equal(keepWay({ highway: 'construction' }), false)
    assert.equal(keepWay({ highway: 'trunk', foot: 'no' }), false)
    assert.equal(keepWay({ highway: 'service', access: 'no' }), false)
    assert.equal(keepWay({ highway: 'service', access: 'no', foot: 'yes' }), true, '걸어도 된다는 표시가 있으면 쓴다')
  })

  it('계단은 갈래나 표시로 안다', () => {
    assert.equal(isStairs('stairs', {}), true)
    assert.equal(isStairs('walk', { highway: 'steps' }), true)
    assert.equal(isStairs('walk', { highway: 'footway' }), false)
  })
})

describe('경사를 언제 모른다고 하는가', () => {
  it('30m 이상 · 다리·터널 아님이면 p50 천분율', () => {
    assert.equal(slopePermille({ p50Slope: 0.083, length: 120 }, {}), 83)
    assert.equal(slopePermille({ p50Slope: -0.05, length: 120 }, {}), 50, '방향 없는 크기다')
  })

  it('짧은 조각 · 다리 · 터널 · 못 잰 길은 모른다(-1)', () => {
    assert.equal(slopePermille({ p50Slope: 0.2, length: 29 }, {}), -1)
    assert.equal(slopePermille({ p50Slope: 0.2, length: 120 }, { bridge: 'yes' }), -1)
    assert.equal(slopePermille({ p50Slope: 0.2, length: 120 }, { tunnel: 'yes' }), -1)
    assert.equal(slopePermille(undefined, {}), -1)
  })
})

describe('어떻게 잇는가', () => {
  const a = { lat: 35.1, lon: 129.0 }
  const b = { lat: 35.101, lon: 129.0 }
  const c = { lat: 35.101, lon: 129.001 }

  it('🔴 두 길이 같은 좌표의 점을 쓰면 한 점이 된다 — 교차점이 이어진다', () => {
    const g = buildGraph([
      { topic: 'road', tags: { highway: 'residential' }, geometry: [a, b] },
      { topic: 'walk', tags: { highway: 'footway' }, geometry: [b, c] },
    ], () => -1)
    assert.equal(g.lat.length, 3)
    assert.deepEqual(g.ways.map((w) => w.refs), [[0, 1], [1, 2]])
    assert.equal(largestComponentShare(g), 1)
  })

  it('못 걷는 길은 점도 안 만든다 — 자동차 전용도로 위의 점으로 끌려가지 않는다', () => {
    const g = buildGraph([{ topic: 'road', tags: { highway: 'motorway' }, geometry: [a, b] }], () => -1)
    assert.equal(g.lat.length, 0)
  })

  it('같은 점이 연달아 나오면 한 번만 적는다', () => {
    const g = buildGraph([{ topic: 'walk', tags: {}, geometry: [a, a, b] }], () => -1)
    assert.deepEqual(g.ways[0].refs, [0, 1])
  })

  it('파일 머리는 GBWG · 판 1 · 개수 셋이다', () => {
    const g = buildGraph([{ topic: 'stairs', tags: { highway: 'steps' }, geometry: [a, b] }], () => 120)
    const buf = encode(g)
    assert.equal(buf.toString('ascii', 0, 4), 'GBWG')
    assert.equal(buf.readInt32BE(4), 1)
    assert.deepEqual([buf.readInt32BE(8), buf.readInt32BE(12), buf.readInt32BE(16)], [2, 1, 2])
    // 길: 점 개수 · 계단 표시 · 경사
    const way = 20 + 2 * 8
    assert.deepEqual([buf.readInt32BE(way), buf.readUInt8(way + 4), buf.readInt16BE(way + 5)], [2, 1, 120])
  })
})
