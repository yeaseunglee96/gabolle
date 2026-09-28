/**
 * 장소 경사 규칙 — 가운데 값 · 30m 이상 · 다리·터널·자동차 전용 제외 (S15P21E201-1629)
 *
 * 🔴 왜 이 시험이 필요한가. 경사 값은 틀려도 아무 데도 빨간불이 안 켜진다. 앞 방식(p90)은 평지인
 *    해운대 그린레일웨이를 10.2% 로 냈고, 그 값이 휠체어·유아차 판정에 쓰이면 갈 수 있는 곳이
 *    조용히 빠진다. 규칙 하나하나가 그 방향의 거짓을 막는다.
 *
 *   node --test test/place-slope-by-id.test.mjs
 */
import { describe, it } from 'node:test'
import assert from 'node:assert/strict'
import { keepSegment, weightedQuantile, distanceToLineM, slopeIndex } from '../process/place-slope-by-id.mjs'

const seg = (over = {}) => ({ topic: 'road', length: 120, p50Slope: 0.03, ...over })

describe('어느 구간을 세는가', () => {
  it('보통 길은 센다', () => {
    assert.equal(keepSegment(seg(), { highway: 'residential' }), true)
  })

  it('계단은 안 센다 — 경사가 아니라 계단이다', () => {
    assert.equal(keepSegment(seg({ topic: 'stairs' }), { highway: 'steps' }), false)
  })

  it('30m 미만 조각은 안 센다 — 고도 자료가 한 번 튀면 그대로 값이 된다', () => {
    assert.equal(keepSegment(seg({ length: 29.9 }), {}), false)
    assert.equal(keepSegment(seg({ length: 30 }), {}), true)
  })

  it('다리·터널은 안 센다 — 길 높이와 땅 높이가 다르다', () => {
    assert.equal(keepSegment(seg(), { bridge: 'yes' }), false)
    assert.equal(keepSegment(seg(), { tunnel: 'culvert' }), false)
    assert.equal(keepSegment(seg(), { bridge: 'no', tunnel: 'no' }), true, '「아니다」 표시는 다리가 아니다')
  })

  it('사람이 못 걷는 길은 안 센다', () => {
    assert.equal(keepSegment(seg(), { highway: 'motorway' }), false)
    assert.equal(keepSegment(seg(), { highway: 'motorway_link' }), false)
    assert.equal(keepSegment(seg(), { highway: 'trunk', foot: 'no' }), false)
    assert.equal(keepSegment(seg(), { highway: 'trunk' }), true, '보도가 딸린 간선도로는 걷는다')
  })

  it('경사를 모르는 구간은 안 센다', () => {
    assert.equal(keepSegment(seg({ p50Slope: null }), {}), false)
  })
})

describe('대표값 — 길이로 가중한 가운데 값', () => {
  it('긴 평지 길 하나가 짧은 비탈 토막 여럿을 이긴다 — p90 이면 거꾸로다', () => {
    const rows = [{ v: 0.02, w: 300 }, { v: 0.4, w: 40 }, { v: 0.3, w: 40 }, { v: 0.25, w: 40 }]
    assert.equal(weightedQuantile(rows, 0.5), 0.02)
    assert.equal(weightedQuantile(rows, 0.9), 0.3, '같은 자료를 p90 으로 재면 비탈이 된다')
  })

  it('가중이 개수가 아니라 길이다', () => {
    // 개수로는 비탈이 셋 중 둘이지만 길이로는 평지가 더 길다.
    assert.equal(weightedQuantile([{ v: 0.3, w: 10 }, { v: 0.3, w: 10 }, { v: 0.01, w: 100 }], 0.5), 0.01)
  })
})

describe('반경 안에 드는가', () => {
  it('꼭짓점이 멀어도 선분이 반경을 지나가면 든다', () => {
    // 동서로 2km 뻗은 길, 꼭짓점은 양 끝 둘뿐. 장소는 한가운데서 북쪽으로 약 100m.
    const line = [{ lat: 35.1, lon: 129.0 }, { lat: 35.1, lon: 129.022 }]
    const d = distanceToLineM(35.1009, 129.011, line)
    assert.ok(d > 90 && d < 110, `거리 ${d}`)
  })
})

describe('한 점의 경사', () => {
  const east = (lat, lon, m) => [{ lat, lon }, { lat, lon: lon + m / 91000 }]

  it('반경 안 걷는 길이 150m 도 안 되면 값을 만들지 않는다 — 모른다를 평지라 하지 않는다', () => {
    const at = slopeIndex([{ v: 0.02, w: 100, geometry: east(35.1, 129.0, 100) }])
    assert.equal(at(35.1, 129.0005), null)
  })

  it('가운데 값을 백분율로 낸다', () => {
    const at = slopeIndex([
      { v: 0.02, w: 200, geometry: east(35.1, 129.0, 200) },
      { v: 0.5, w: 60, geometry: east(35.1002, 129.0, 60) },
    ])
    assert.deepEqual(at(35.1, 129.001), { slopePercent: 2, segments: 2, walkLengthM: 260 })
  })

  it('반경 밖 길은 안 센다', () => {
    const at = slopeIndex([
      { v: 0.02, w: 200, geometry: east(35.1, 129.0, 200) },
      { v: 0.5, w: 900, geometry: east(35.11, 129.0, 900) }, // 약 1.1km 북쪽
    ])
    assert.equal(at(35.1, 129.001).slopePercent, 2)
  })
})
