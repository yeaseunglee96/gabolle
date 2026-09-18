#!/usr/bin/env node
/**
 * 장소 경사 — 구간 경사를 장소에 붙인다 (S15P21E201-1047)
 *
 * 길의 경사는 process/slope.mjs 가 계산한다. 그런데 화면이 묻는 것은 길이 아니라
 * **장소**다 — "이 카페가 얼마나 비탈에 있나". 그 사이를 잇는 것이 이 파일이다.
 *
 * 규칙과 근거는 docs/PLACE-SLOPE.md 에 있다. 여기서는 그대로 구현한다.
 *
 *   장소 경사 = 반경 200m 안의 보행 가능한 구간들에 대해,
 *               구간마다 p50Slope 를 쓰고, 구간 길이로 가중한 p90
 *
 * 🔴 백분위를 두 번 겹치지 않는다
 *   구간 값은 이미 p90Slope(그 길의 가파른 부분)를 대표값으로 갖고 있다. 거기에 또
 *   p90 을 씌우면 "가파른 길들의 가장 가파른 부분"이 되어 부산 대부분이 비탈로 나온다.
 *   그래서 구간에서는 p50Slope(그 길의 보통 기울기)를 꺼내 반경 안에서 p90 을 잡는다.
 *   뜻은 "주변에서 가장 가파른 축에 드는 길이, 평소에 이 정도 기울기다" 로 읽힌다.
 *
 * 🔴 반경을 100m 아래로 내리지 마라
 *   경사 계산이 기준선 100m 를 실측으로 정했다 — 30m 로 재면 평지의 6% 가 "8% 이상"
 *   으로 나온다(전부 거짓). 반경이 기준선보다 작으면 잡음 안에서 재는 것이다.
 *
 *   2026-09-16 실측 — 반경을 흔들어 본 결과(감천문화마을 대 마린시티):
 *     100m  14.4%  대  1.9%
 *     150m  16.1%  대  1.9%
 *     200m  16.4%  대  2.7%      ← 고른 값
 *     300m  19.3%  대  2.7%
 *     400m  20.7%  대  3.5%
 *   어느 반경에서도 순서는 안 뒤집힌다. 다만 넓힐수록 둘 다 부푼다 — 멀리 있는
 *   언덕을 끌어오기 때문이다. 200m 가 그 사이에서 무난하다.
 *
 * 🔴 계단은 빼고 센다
 *   계단은 경사가 아니라 계단이고, STAIRS_PRESENT 라는 별도 항목이 있다.
 *   그 항목은 DB 가 추정값 저장을 막는다(ck_place_feature_safety_never_estimated).
 *   여기서 계단을 세어 그 칸을 채우면 안 된다.
 *
 * 🔴 표본이 모자라면 줄을 만들지 않는다
 *   "모른다" 를 "평지" 로 바꿔 말하지 않는다. 값이 없으면 화면이 그 줄을 안 그린다.
 *
 * 🔴 이 값은 ESTIMATED 다
 *   실측이 아니라 주변 길에서 유도한 값이다. 적재하는 쪽이 evidence_status 를
 *   ESTIMATED 로 넣어야 화면이 "(추정)" 을 붙여 그린다.
 *
 * 🔴 이 값이 못 하는 것 — 방향을 못 가른다
 *   한쪽은 평지로 들어가고 반대쪽만 가파른 장소가 있는데 반경 백분위는 모든 방향을
 *   섞는다. 부산은 산복도로 때문에 흔하다. 제대로 하려면 경로를 실제로 찾아야 하고
 *   그건 훨씬 비싸며 출발지마다 달라진다.
 *   그래서 이 값은 "주변이 얼마나 비탈인가" 이지 "가기가 얼마나 힘든가" 가 아니다.
 *
 * 입력
 *   data/staged/segment-slope.ndjson    process/slope.mjs 가 만든다 (lat·lon 필요)
 *   data/raw/tourapi/tourapi-busan.ndjson  collect/tourapi.mjs 가 만든다
 *
 * 실행
 *   node process/place-slope.mjs
 *   node process/place-slope.mjs --radius 300
 *
 * 종료 코드
 *   0  냈다
 *   1  불변식이 깨졌다 (0건, 대조군 어긋남)
 *   2  입력이 없다
 */
import { readFile, writeFile, mkdir } from 'node:fs/promises'
import { createReadStream, existsSync } from 'node:fs'
import { createInterface } from 'node:readline'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { stamp } from '../mlops/manifest.mjs'
import { log } from '../lib/log.mjs'

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..')
const SEG = join(ROOT, 'data/staged/segment-slope.ndjson')
const RAW = join(ROOT, 'data/raw/tourapi/tourapi-busan.ndjson')
const OUT = join(ROOT, 'data/staged')
const OUT_FILE = join(OUT, 'place-slope.ndjson')

const EXIT = { OK: 0, INVARIANT: 1, INPUT: 2 }

const argIdx = process.argv.indexOf('--radius')
/** 반경(m). 🔴 100 아래로 내리지 마라 — 머리말 참고. */
const RADIUS_M = argIdx > 0 ? Number(process.argv[argIdx + 1]) : 200

/** 반경 안 보행로 총 길이가 이만큼도 안 되면 값을 만들지 않는다. */
const MIN_LENGTH_M = 150


/**
 * 🔴 대조군 — 이것이 어긋나면 반경이나 가중이 틀린 것이다.
 *
 * 경사 계산이 기준선을 정할 때 쓴 것과 같은 자리다. 좌표는 2026-09-16 수집본에서
 * 꺼냈다. 이름이 아니라 좌표로 박는 이유는, 이름은 원천에서 바뀌기 때문이다.
 */
const CONTROL_FLAT = [
  ['마린시티', 35.15603, 129.14366],
  ['을숙도 공원', 35.10449, 128.94598],
  ['센텀시티 스파랜드', 35.16882, 129.12952],
]
const CONTROL_STEEP = [
  ['감천문화마을', 35.09746, 129.01060],
  ['천마산 조각공원', 35.08806, 129.01610],
  ['망양로 산복도로전시관', 35.11767, 129.03321],
]
/** 매립지는 이보다 평평해야 하고, 산복도로는 이보다 가팔라야 한다 (%). */
const FLAT_MAX_PCT = 5
const STEEP_MIN_PCT = 10

// ── 거리 ─────────────────────────────────────────────────────────────
const M_PER_DEG_LAT = 110574
const mPerDegLon = (lat) => 111320 * Math.cos((lat * Math.PI) / 180)
function distM(aLat, aLon, bLat, bLon) {
  const dy = (aLat - bLat) * M_PER_DEG_LAT
  const dx = (aLon - bLon) * mPerDegLon(aLat)
  return Math.hypot(dx, dy)
}

/**
 * 길이로 가중한 백분위.
 *
 * 🔴 가중이 없으면 짧은 토막이 여럿 모인 골목이 결과를 끌고 간다. 우리가 묻는 것은
 *    "몇 개의 길이 가파른가" 가 아니라 "가파른 길이 얼마나 뻗어 있는가" 다.
 */
function weightedPercentile(rows, q) {
  const sorted = [...rows].sort((a, b) => a.v - b.v)
  const total = sorted.reduce((t, r) => t + r.w, 0)
  let acc = 0
  for (const r of sorted) {
    acc += r.w
    if (acc >= total * q) return r.v
  }
  return sorted.at(-1).v
}

async function main() {
  log(`장소 경사 — 반경 ${RADIUS_M}m · 구간 p50 을 길이로 가중해 p90`)

  if (!existsSync(SEG)) {
    log('🔴 구간 경사가 없습니다. 먼저 돌리십시오 — node process/slope.mjs')
    process.exitCode = EXIT.INPUT
    return
  }
  if (!existsSync(RAW)) {
    log('🔴 장소 수집본이 없습니다. 먼저 돌리십시오 — node collect/tourapi.mjs')
    process.exitCode = EXIT.INPUT
    return
  }
  if (!Number.isFinite(RADIUS_M) || RADIUS_M < 100) {
    log(`🔴 반경 ${RADIUS_M}m 는 쓰지 않습니다. 100m 아래는 고도 오차 안에서 재는 것입니다.`)
    process.exitCode = EXIT.INPUT
    return
  }

  // ── 구간 ───────────────────────────────────────────────────────────
  const segs = []
  let noCoord = 0
  {
    const rl = createInterface({ input: createReadStream(SEG), crlfDelay: Infinity })
    for await (const line of rl) {
      if (!line) continue
      const o = JSON.parse(line)
      if (o.topic === 'stairs') continue // 🔴 계단은 경사가 아니다
      if (o.lat == null || o.lon == null) { noCoord++; continue }
      segs.push(o)
    }
  }
  if (noCoord) {
    log(`🔴 좌표가 없는 구간이 ${noCoord}개 있습니다. slope.mjs 가 낡았습니다 — 다시 돌리십시오.`)
    process.exitCode = EXIT.INPUT
    return
  }
  log(`  구간 ${segs.length.toLocaleString()}개 (계단 제외)`)

  // ── 격자 ───────────────────────────────────────────────────────────
  // 🔴 장소마다 전체 구간을 훑으면 2천 x 7만 이다. 격자로 나눠 둔다.
  const CELL_DEG = 0.005 // ≈ 550m
  const grid = new Map()
  for (const s of segs) {
    const k = `${Math.floor(s.lat / CELL_DEG)},${Math.floor(s.lon / CELL_DEG)}`
    const bucket = grid.get(k)
    if (bucket) bucket.push(s)
    else grid.set(k, [s])
  }

  function slopeAt(lat, lon) {
    const rows = []
    const span = Math.ceil((RADIUS_M + 400) / (CELL_DEG * M_PER_DEG_LAT))
    const ci = Math.floor(lat / CELL_DEG)
    const cj = Math.floor(lon / CELL_DEG)
    for (let i = ci - span; i <= ci + span; i++) {
      for (let j = cj - span; j <= cj + span; j++) {
        for (const s of grid.get(`${i},${j}`) ?? []) {
          // 🔴 긴 길은 중점 하나로 대표가 안 된다 — 길이의 절반만큼 넉넉하게 본다
          if (distM(lat, lon, s.lat, s.lon) > RADIUS_M + s.length / 2) continue
          rows.push({ v: s.p50Slope, w: s.length })
        }
      }
    }
    const len = rows.reduce((t, r) => t + r.w, 0)
    if (len < MIN_LENGTH_M) return null
    return {
      slopePercent: +(weightedPercentile(rows, 0.9) * 100).toFixed(1),
      segments: rows.length,
      walkLengthM: Math.round(len),
    }
  }

  // ── 장소 ───────────────────────────────────────────────────────────
  const places = new Map()
  {
    const rl = createInterface({ input: createReadStream(RAW), crlfDelay: Infinity })
    for await (const line of rl) {
      if (!line) continue
      let o
      try { o = JSON.parse(line) } catch { continue }
      if (o.op !== 'areaBasedList2' || !o.raw) continue
      let body
      try { body = JSON.parse(o.raw)?.response?.body } catch { continue }
      const item = body?.items?.item
      for (const it of Array.isArray(item) ? item : item ? [item] : []) {
        const lat = Number(it.mapy), lon = Number(it.mapx)
        if (!it.contentid || !Number.isFinite(lat) || !Number.isFinite(lon)) continue
        places.set(String(it.contentid), { title: it.title ?? '', lat, lon })
      }
    }
  }
  log(`  장소 ${places.size.toLocaleString()}곳`)

  // ── 계산 ───────────────────────────────────────────────────────────
  await mkdir(OUT, { recursive: true })
  const lines = []
  let made = 0, tooFew = 0
  for (const [contentid, p] of places) {
    const r = slopeAt(p.lat, p.lon)
    if (!r) { tooFew++; continue } // 🔴 값을 지어내지 않는다
    made++
    lines.push(JSON.stringify({
      contentid, title: p.title,
      featureType: 'SLOPE_PERCENT',
      evidenceStatus: 'ESTIMATED', // 🔴 주변 길에서 유도한 값이다
      ...r,
      radiusM: RADIUS_M,
    }))
  }
  await writeFile(OUT_FILE, lines.join('\n') + (lines.length ? '\n' : ''))

  // ── 대조군 ─────────────────────────────────────────────────────────
  const check = (list) => list.map(([name, lat, lon]) => [name, slopeAt(lat, lon)?.slopePercent ?? null])
  const flat = check(CONTROL_FLAT)
  const steep = check(CONTROL_STEEP)

  log('')
  log(`저장 완료: ${made}곳 / 표본 모자람 ${tooFew}곳 — ${OUT_FILE}`)
  log('  대조군 — 매립지(평지여야 한다)')
  for (const [n, v] of flat) log(`    ${String(v ?? '값없음').padStart(6)}%  ${n}`)
  log('  대조군 — 산복도로(비탈이어야 한다)')
  for (const [n, v] of steep) log(`    ${String(v ?? '값없음').padStart(6)}%  ${n}`)

  stamp(join(ROOT, 'data/staged/_place-slope-run'), {
    step: 'process/place-slope',
    inputs: [SEG, RAW],
    params: { radiusM: RADIUS_M, minLengthM: MIN_LENGTH_M, stat: 'length-weighted p90 of segment p50Slope' },
    result: { places: places.size, made, tooFew, segments: segs.length, control: { flat, steep } },
  })

  // ── 불변식 ─────────────────────────────────────────────────────────
  if (made === 0) {
    log('🔴 한 곳도 못 냈습니다. 빈 파일을 성공으로 치지 않습니다.')
    process.exitCode = EXIT.INVARIANT
    return
  }
  const badFlat = flat.filter(([, v]) => v == null || v > FLAT_MAX_PCT)
  const badSteep = steep.filter(([, v]) => v == null || v < STEEP_MIN_PCT)
  if (badFlat.length || badSteep.length) {
    log('')
    log('🔴 대조군이 어긋났습니다. 반경이나 가중이 틀렸습니다.')
    for (const [n, v] of badFlat) log(`   평지여야 하는데 ${v ?? '값없음'}% — ${n} (${FLAT_MAX_PCT}% 이하여야 한다)`)
    for (const [n, v] of badSteep) log(`   비탈이어야 하는데 ${v ?? '값없음'}% — ${n} (${STEEP_MIN_PCT}% 이상이어야 한다)`)
    process.exitCode = EXIT.INVARIANT
    return
  }
  log('  대조군 통과')
}

main().catch((e) => { console.error('치명:', e?.stack || e); process.exit(EXIT.INVARIANT) })
