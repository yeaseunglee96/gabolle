#!/usr/bin/env node
/**
 * 장소 경사 — 운영의 모든 장소에, 장소 번호(place_id)로 (S15P21E201-1629)
 *
 * 휠체어·유아차·큰 짐 판정을 「경사」 하나로 묶기로 했다(2026-09-25 사용자 결정). 그런데
 * 앞선 두 스크립트(place-slope.mjs · place-slope-sbiz.mjs)의 값은 그대로 못 쓴다.
 *
 *   ① 값이 있는 곳이 적다 — 운영 6,931곳 중 2,682곳. 걷기 길 168곳 중 28곳, 바다 16곳 중 4곳.
 *      관광공사 번호(contentid)와 상가 번호로만 붙여서 오픈스트리트맵·카카오 장소에는 값이 없다.
 *   ② 평지가 가파르게 나온다 — 해운대 그린레일웨이 10.2%, 감지해변 9.7%, 구포무장애숲길 11.8%.
 *      반경 안 길들의 높은 쪽(p90)을 대표로 쓰는 탓이다. 고층 건물 사이에서 고도 자료가 튀면
 *      짧은 조각 몇 개가 p90 을 끌어올린다.
 *
 * 그래서 이 파일은 둘을 바꾼다.
 *
 *   장소 = 운영 DB 의 장소 전체. 붙일 열쇠는 place_id — 출처가 무엇이든 한 규칙으로 붙는다.
 *   값   = 반경 200m 안의 걷는 길 구간들에 대해, 구간마다 p50Slope(그 길의 보통 기울기)를 쓰고
 *          구간 길이로 가중한 **가운데 값(p50)**.
 *
 * 경사 지도(S15P21E201-1569)에서 사용자가 정한 방식과 같다 — 가운데 값 · 30m 이상 구간만 ·
 * 다리·터널 제외. 그 지도에서 p90 은 평지인 해운대 해변가 길의 43% 를 「가파름」으로 칠했고,
 * 이 방식으로 평지 6~10%, 언덕 35~69% 가 됐다(구간 기준).
 *
 * 🔴 빼는 구간 — {@link keepSegment}
 *   계단      경사가 아니라 계단이다. STAIRS_PRESENT 라는 별도 항목이 있고 DB 가 추정값 저장을 막는다
 *   30m 미만  표본 한두 개짜리 조각이다. 고도 자료가 한 번 튀면 그대로 값이 된다
 *   다리·터널 길의 높이와 땅(고도 자료)의 높이가 다르다. 교량에서 100% 경사가 관측된다
 *   자동차 전용도로·보행 금지 사람이 못 걷는 길이다(motorway·motorway_link · foot=no).
 *             묻는 것이 「걷는 길이 얼마나 비탈인가」다
 *
 * 🔴 반경은 앞 스크립트와 같은 200m 다. 100m 아래로 내리지 않는다 — 경사 계산의 기준선이 100m 라
 *    그보다 좁으면 고도 오차 안에서 재는 것이다(place-slope.mjs 머리말의 반경 실측 참고).
 *
 * 🔴 표본이 모자라면 줄을 만들지 않는다 — 반경 안 걷는 길이 150m 도 안 되면 값이 없다.
 *    「모른다」를 「평지」로 바꿔 말하지 않는다.
 *
 * 🔴 이 값은 ESTIMATED 다. 주변 길에서 유도한 값이지 그 장소를 잰 것이 아니다.
 *
 * 🔴 이 값이 못 하는 것 — 방향을 못 가르고, 계단을 모른다. 한쪽은 평지로 들어가고 반대쪽만
 *    가파른 장소를 섞어 본다. 계단은 위에서 뺐으므로 「계단 있는 평지」는 평지로 나온다.
 *
 * 입력 (전부 data/ 아래 — 저장소에 없다)
 *   staged/segment-slope.ndjson   process/slope.mjs 가 만든다 (구간 번호 · 길이 · p50Slope)
 *   raw/pbf/road.ndjson · walk.ndjson  collect/pbf_extract.py 가 만든다 (구간 번호 → 선 모양 · 다리/터널 표시)
 *   staged/prod-places.tsv        운영 DB 에서 뽑는다. 아래 명령 그대로 (읽기만 한다):
 *
 *     ssh ubuntu@j15e201.p.ssafy.io "docker exec local-route-personalization-postgres-1 \
 *       psql -U app_user -d app_db -At -F \$'\t' -c \"
 *         SELECT p.place_id, p.source_type, coalesce(p.category,''), p.curation_status,
 *                replace(replace(p.name_ko, E'\\t', ' '), E'\\n', ' '), p.lat, p.lng
 *           FROM gabolle.place p WHERE p.lat IS NOT NULL ORDER BY p.place_id\"" \
 *       > data/staged/prod-places.tsv
 *
 * 실행
 *   node process/place-slope-by-id.mjs
 *
 * 종료 코드
 *   0  냈다
 *   1  불변식이 깨졌다 (0건, 대조군 어긋남)
 *   2  입력이 없다
 */
import { writeFile, mkdir } from 'node:fs/promises'
import { createReadStream, existsSync } from 'node:fs'
import { createInterface } from 'node:readline'
import { dirname, join } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'
import { stamp } from '../mlops/manifest.mjs'
import { log } from '../lib/log.mjs'

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..')
const SEG = join(ROOT, 'data/staged/segment-slope.ndjson')
const WAYS = ['road', 'walk'].map((t) => join(ROOT, `data/raw/pbf/${t}.ndjson`))
const PLACES = join(ROOT, 'data/staged/prod-places.tsv')
const OUT_FILE = join(ROOT, 'data/staged/place-slope-by-id.ndjson')

const EXIT = { OK: 0, INVARIANT: 1, INPUT: 2 }

/** 반경(m). 🔴 100 아래로 내리지 않는다 — 머리말 참고. */
export const RADIUS_M = 200

/** 이보다 짧은 구간은 안 센다 (m). 경사 지도와 같은 값이다. */
export const MIN_SEGMENT_M = 30

/** 반경 안 걷는 길 총 길이가 이만큼도 안 되면 값을 만들지 않는다 (m). */
export const MIN_WALK_M = 150

/** 사람이 못 걷는 길의 종류. */
const NOT_WALKABLE = new Set(['motorway', 'motorway_link'])

/**
 * 🔴 대조군 — 이름이 아니라 좌표로 박는다. 이름은 원천에서 바뀐다.
 * 좌표는 2026-09-25 운영 DB 의 그 장소 행에서 꺼냈다.
 *
 * 🔴 이 방법이 <b>못 가르는</b>곳은 대조군에 안 넣는다 — 넣고 통과하게 반경·대표값을 비틀면 나머지
 *    6천 곳이 그 네 곳에 맞춰진다. 2026-09-25 에 기대와 어긋난 넷과 까닭(반경 100~300m · 가운데 값/p75
 *    어느 쪽으로 재도 안 갈렸다):
 *      흰여울문화마을   4.2%  가파름이 계단에 있다. 걷는 길(절영로·해안 산책로)은 실제로 완만하다
 *      구포무장애숲길  11.4%  좌표 둘레가 산길이다. 무장애 데크 자체는 길 자료에 따로 안 잡혔다
 *      장산             8.0%  산의 좌표가 산자락(대천공원 쪽)에 찍혀 있다. 산 하나를 점 하나로 대표한다
 *      감지해변         7.3%  해변 둘레가 태종대 비탈길이다
 */
export const CONTROL_FLAT = [
  ['해운대해수욕장', 35.1585232170784, 129.159854668484],
  ['해운대 그린레일웨이 (미포~송정 구간)', 35.1612857933, 129.1914273569],
  ['낙동강하구 (부산 국가지질공원)', 35.104551505, 128.9460085869],
]
export const CONTROL_STEEP = [
  ['황령산', 35.1579341561, 129.0827285679],
  ['금련산', 35.1602230601, 129.0974161159],
  ['감천문화마을', 35.09740872250286, 129.01056080474402],
]
/** 평지는 이보다 평평해야 하고, 비탈은 이보다 가팔라야 한다 (%). */
const FLAT_MAX_PCT = 5
const STEEP_MIN_PCT = 8.33

/**
 * 이 구간을 세는가. {@code seg} 는 구간 경사 한 줄, {@code tags} 는 그 길의 OSM 표시다.
 */
export function keepSegment(seg, tags) {
  if (!seg || seg.topic === 'stairs') return false
  if (seg.p50Slope == null || !((seg.length ?? 0) >= MIN_SEGMENT_M)) return false
  const t = tags ?? {}
  if (t.bridge && t.bridge !== 'no') return false
  if (t.tunnel && t.tunnel !== 'no') return false
  if (NOT_WALKABLE.has(t.highway) || t.foot === 'no') return false
  return true
}

/**
 * 길이로 가중한 백분위. {@code rows} 는 {@code {v, w}} 목록.
 *
 * 🔴 가중이 없으면 짧은 토막이 여럿 모인 골목이 결과를 끌고 간다. 묻는 것은 「몇 개의 길이
 *    가파른가」가 아니라 「가파른 길이 얼마나 뻗어 있는가」다.
 */
export function weightedQuantile(rows, q) {
  const sorted = [...rows].sort((a, b) => a.v - b.v)
  const total = sorted.reduce((t, r) => t + r.w, 0)
  let acc = 0
  for (const r of sorted) {
    acc += r.w
    if (acc >= total * q) return r.v
  }
  return sorted.at(-1).v
}

// ── 거리 ─────────────────────────────────────────────────────────────
const M_PER_DEG_LAT = 110574
const mPerDegLon = (lat) => 111320 * Math.cos((lat * Math.PI) / 180)

/**
 * 한 점에서 꺾은선까지의 가장 가까운 거리(m). 장소 둘레(수백 m)만 보므로 평면으로 펴서 잰다.
 * 🔴 꼭짓점까지만 재면 꼭짓점이 드문 긴 길이 반경을 지나가도 안 걸린다 — 선분까지 잰다.
 */
export function distanceToLineM(lat, lon, geometry) {
  const kx = mPerDegLon(lat)
  let best = Infinity
  for (let i = 0; i < geometry.length; i++) {
    const ax = (geometry[i].lon - lon) * kx
    const ay = (geometry[i].lat - lat) * M_PER_DEG_LAT
    if (i === geometry.length - 1) {
      best = Math.min(best, Math.hypot(ax, ay))
      break
    }
    const bx = (geometry[i + 1].lon - lon) * kx
    const by = (geometry[i + 1].lat - lat) * M_PER_DEG_LAT
    const dx = bx - ax
    const dy = by - ay
    const len2 = dx * dx + dy * dy
    const t = len2 === 0 ? 0 : Math.max(0, Math.min(1, -(ax * dx + ay * dy) / len2))
    best = Math.min(best, Math.hypot(ax + t * dx, ay + t * dy))
  }
  return best
}

/**
 * 구간 목록으로 「한 점의 경사」를 묻는 함수를 만든다. 구간은 {@code {v, w, geometry}}.
 * 값이 없으면 {@code null} 이다.
 */
export function slopeIndex(segments) {
  // 🔴 장소마다 전체 구간을 훑으면 7천 x 5만 이다. 구간이 걸친 격자 칸마다 넣어 둔다.
  const CELL_DEG = 0.005 // ≈ 550m
  const grid = new Map()
  for (const s of segments) {
    let minLat = Infinity, maxLat = -Infinity, minLon = Infinity, maxLon = -Infinity
    for (const p of s.geometry) {
      minLat = Math.min(minLat, p.lat); maxLat = Math.max(maxLat, p.lat)
      minLon = Math.min(minLon, p.lon); maxLon = Math.max(maxLon, p.lon)
    }
    for (let i = Math.floor(minLat / CELL_DEG); i <= Math.floor(maxLat / CELL_DEG); i++) {
      for (let j = Math.floor(minLon / CELL_DEG); j <= Math.floor(maxLon / CELL_DEG); j++) {
        const k = `${i},${j}`
        const bucket = grid.get(k)
        if (bucket) bucket.push(s)
        else grid.set(k, [s])
      }
    }
  }
  return function slopeAt(lat, lon) {
    const seen = new Set()
    const rows = []
    const ci = Math.floor(lat / CELL_DEG)
    const cj = Math.floor(lon / CELL_DEG)
    for (let i = ci - 1; i <= ci + 1; i++) {
      for (let j = cj - 1; j <= cj + 1; j++) {
        for (const s of grid.get(`${i},${j}`) ?? []) {
          if (seen.has(s)) continue
          seen.add(s)
          if (distanceToLineM(lat, lon, s.geometry) > RADIUS_M) continue
          rows.push({ v: s.v, w: s.w })
        }
      }
    }
    // 반경에 닿은 길의 <b>전체</b> 길이다(앞 스크립트·경사 지도와 같다). 반경 안으로 잘라 세는 것도 재 봤는데
    // 대조군이 더 나아지지 않았다(2026-09-25 — 감지해변 7.3→4.5% 지만 장산 8.0→6.7%).
    const len = rows.reduce((t, r) => t + r.w, 0)
    if (len < MIN_WALK_M) return null
    return {
      slopePercent: +(weightedQuantile(rows, 0.5) * 100).toFixed(1),
      segments: rows.length,
      walkLengthM: Math.round(len),
    }
  }
}

async function* lines(file) {
  const rl = createInterface({ input: createReadStream(file), crlfDelay: Infinity })
  for await (const line of rl) if (line) yield line
}

async function main() {
  log(`장소 경사(장소 번호) — 반경 ${RADIUS_M}m · 걷는 길 ${MIN_SEGMENT_M}m 이상 · 다리·터널 제외 · 길이 가중 가운데 값`)

  for (const f of [SEG, ...WAYS, PLACES]) {
    if (!existsSync(f)) {
      log(`🔴 입력이 없습니다 — ${f}. 머리말의 입력 목록을 보십시오.`)
      process.exitCode = EXIT.INPUT
      return
    }
  }

  // ── 구간: 경사 + 선 모양 ────────────────────────────────────────────
  const slopeById = new Map()
  for await (const line of lines(SEG)) {
    const o = JSON.parse(line)
    slopeById.set(o.id, o)
  }
  const segments = []
  const dropped = { stairsOrShort: 0, bridgeOrTunnel: 0, notWalkable: 0, noGeometry: 0 }
  for (const file of WAYS) {
    for await (const line of lines(file)) {
      const way = JSON.parse(line)
      const seg = slopeById.get(way.id)
      if (!seg) continue
      if (!keepSegment(seg, way.tags)) {
        const t = way.tags ?? {}
        if ((t.bridge && t.bridge !== 'no') || (t.tunnel && t.tunnel !== 'no')) dropped.bridgeOrTunnel++
        else if (NOT_WALKABLE.has(t.highway) || t.foot === 'no') dropped.notWalkable++
        else dropped.stairsOrShort++
        continue
      }
      if (!Array.isArray(way.geometry) || way.geometry.length < 2) { dropped.noGeometry++; continue }
      segments.push({ v: seg.p50Slope, w: seg.length, geometry: way.geometry })
    }
  }
  log(`  구간 ${segments.length.toLocaleString()}개 셈 · 뺌 ${JSON.stringify(dropped)}`)
  const slopeAt = slopeIndex(segments)

  // ── 장소 ───────────────────────────────────────────────────────────
  const places = []
  for await (const line of lines(PLACES)) {
    const [placeId, sourceType, category, curationStatus, name, lat, lng] = line.split('\t')
    if (!placeId || !Number.isFinite(Number(lat)) || !Number.isFinite(Number(lng))) continue
    places.push({ placeId, sourceType, category, curationStatus, name, lat: Number(lat), lon: Number(lng) })
  }
  log(`  장소 ${places.length.toLocaleString()}곳`)

  // ── 계산 ───────────────────────────────────────────────────────────
  await mkdir(dirname(OUT_FILE), { recursive: true })
  const out = []
  const byCategory = {}
  for (const p of places) {
    const r = slopeAt(p.lat, p.lon)
    const c = (byCategory[p.category || '(없음)'] ??= { places: 0, made: 0 })
    c.places++
    if (!r) continue // 🔴 값을 지어내지 않는다
    c.made++
    out.push({ p, r })
  }
  await writeFile(OUT_FILE, out.map(({ p, r }) => JSON.stringify({
    placeId: p.placeId, title: p.name,
    featureType: 'SLOPE_PERCENT',
    evidenceStatus: 'ESTIMATED', // 🔴 주변 길에서 유도한 값이다
    ...r,
    radiusM: RADIUS_M,
    stat: 'p50',
  })).join('\n') + (out.length ? '\n' : ''))

  // ── 기준별로 넘는 곳 — 판정 기준값을 고를 때 쓴다 ───────────────────
  const over = Object.fromEntries([5, 8.33, 10, 12].map((t) => [t, out.filter(({ r }) => r.slopePercent > t).length]))

  // ── 대조군 ─────────────────────────────────────────────────────────
  const check = (list) => list.map(([name, lat, lon]) => [name, slopeAt(lat, lon)?.slopePercent ?? null])
  const flat = check(CONTROL_FLAT)
  const steep = check(CONTROL_STEEP)

  log('')
  log(`저장 완료: ${out.length}곳 / 표본 모자람 ${places.length - out.length}곳 — ${OUT_FILE}`)
  log('  갈래별 값이 있는 곳')
  for (const [c, v] of Object.entries(byCategory).sort((a, b) => b[1].places - a[1].places)) {
    log(`    ${c.padEnd(16)} ${String(v.made).padStart(5)} / ${v.places}`)
  }
  log(`  기준을 넘는 곳: ${Object.entries(over).map(([t, n]) => `${t}% 초과 ${n}곳`).join(' · ')}`)
  log('  대조군 — 평지여야 한다')
  for (const [n, v] of flat) log(`    ${String(v ?? '값없음').padStart(6)}%  ${n}`)
  log('  대조군 — 비탈이어야 한다')
  for (const [n, v] of steep) log(`    ${String(v ?? '값없음').padStart(6)}%  ${n}`)

  stamp(join(ROOT, 'data/staged/_place-slope-by-id-run'), {
    step: 'process/place-slope-by-id',
    inputs: [SEG, ...WAYS, PLACES],
    params: { radiusM: RADIUS_M, minSegmentM: MIN_SEGMENT_M, minWalkM: MIN_WALK_M,
      stat: 'length-weighted p50 of segment p50Slope', excluded: 'stairs · bridge · tunnel · motorway · foot=no' },
    result: { places: places.length, made: out.length, segments: segments.length, dropped, byCategory, over,
      control: { flat, steep } },
  })

  // ── 불변식 ─────────────────────────────────────────────────────────
  if (out.length === 0) {
    log('🔴 한 곳도 못 냈습니다. 빈 파일을 성공으로 치지 않습니다.')
    process.exitCode = EXIT.INVARIANT
    return
  }
  const badFlat = flat.filter(([, v]) => v == null || v > FLAT_MAX_PCT)
  const badSteep = steep.filter(([, v]) => v == null || v < STEEP_MIN_PCT)
  if (badFlat.length || badSteep.length) {
    log('')
    log('🔴 대조군이 어긋났습니다.')
    for (const [n, v] of badFlat) log(`   평지여야 하는데 ${v ?? '값없음'}% — ${n} (${FLAT_MAX_PCT}% 이하여야 한다)`)
    for (const [n, v] of badSteep) log(`   비탈이어야 하는데 ${v ?? '값없음'}% — ${n} (${STEEP_MIN_PCT}% 이상이어야 한다)`)
    process.exitCode = EXIT.INVARIANT
    return
  }
  log('  대조군 통과')
}

const runDirectly = process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href
if (runDirectly) {
  main().catch((e) => { console.error('치명:', e?.stack || e); process.exit(EXIT.INVARIANT) })
}
