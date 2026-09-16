#!/usr/bin/env node
/**
 * 장소 경사 — 상가정보 장소편 (S15P21E201-1047)
 *
 * `process/place-slope.mjs` 와 **계산 규칙이 같다.** 다른 것은 장소를 어디서 읽느냐뿐이다.
 *
 *   place-slope.mjs       관광공사 수집본에서 장소를 읽는다 (contentid 가 열쇠)
 *   place-slope-sbiz.mjs  ← 이 파일. 운영 DB 에서 뽑아 둔 장소 목록을 읽는다
 *
 * 🔴 왜 이 파일이 필요한가 — 경사가 정작 필요한 곳에 없었다
 *
 *   2026-09-16 운영 실측:
 *
 *     출처              장소 수    경사 붙은 곳
 *     관광공사             328          327
 *     상가정보(음식·카페)  2,355            0
 *
 *   `place-slope.mjs` 는 관광공사 수집본만 읽는다. 상가정보 장소는 **쳐다보지도 않는다.**
 *   그런데 그 2,355곳이 운영 DB 의 88% 이고, 추천이 실제로 내놓는 장소의 거의 전부다
 *   (2026-09-16 실측 — 사용자가 만든 2일 8곳 일정이 전부 음식점·카페·술집이었다).
 *
 *   그래서 「완만한 곳만 보기(경사 기준)」를 켜면, **사용자가 받는 장소에는 경사 값이
 *   하나도 없다.** 관광공사 쪽 숫자를 아무리 늘려도 이건 안 바뀐다.
 *
 * 🔴 계산을 다시 만들지 않았다
 *
 *   반경 200m · 구간 p50Slope 를 길이로 가중한 p90 · 보행로 150m 미만이면 값 없음 ·
 *   계단 제외 · 대조군 검사 — 전부 `place-slope.mjs` 와 같다. 근거는
 *   `docs/PLACE-SLOPE.md` 에 있고 여기서 다시 논하지 않는다.
 *
 *   🔴 규칙을 여기서 바꾸면 두 파일이 서로 다른 뜻의 값을 같은 칸에 넣게 된다.
 *      바꿀 일이 생기면 **양쪽을 같이** 바꾸고 대조군으로 확인한다.
 *
 * 입력
 *   data/staged/segment-slope.ndjson   process/slope.mjs 가 만든다 (lat·lon 필요)
 *   data/staged/no-slope-places.tsv    운영 DB 에서 뽑는다. 아래 명령 그대로:
 *
 *     ssh ubuntu@j15e201.p.ssafy.io "docker exec local-route-personalization-postgres-1 \
 *       psql -U app_user -d app_db -At -F '\t' -c \"
 *         SELECT p.source_type, p.source_id, p.name_ko, p.lat, p.lng
 *           FROM gabolle.place p
 *          WHERE NOT EXISTS (SELECT 1 FROM gabolle.place_feature f
 *                             WHERE f.place_id = p.place_id
 *                               AND f.feature_type = 'SLOPE_PERCENT')
 *            AND p.lat IS NOT NULL
 *          ORDER BY p.source_type, p.source_id;\"" > data/staged/no-slope-places.tsv
 *
 *   🔴 좌표를 수집본이 아니라 **운영 DB 에서** 가져오는 이유: 붙일 대상이 운영의 행이다.
 *      수집본에서 가져오면 운영에 없는 장소까지 계산하게 되고, 그것이 정확히 지금
 *      관광공사 쪽에서 2,213줄 중 1,886줄이 버려지고 있는 이유다.
 *
 * 실행
 *   node process/place-slope-sbiz.mjs
 *   node process/place-slope-sbiz.mjs --radius 300
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
import { fileURLToPath } from 'node:url'
import { stamp } from '../mlops/manifest.mjs'

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..')
const SEG = join(ROOT, 'data/staged/segment-slope.ndjson')
const PLACES = join(ROOT, 'data/staged/no-slope-places.tsv')
const OUT = join(ROOT, 'data/staged')
const OUT_FILE = join(OUT, 'place-slope-sbiz.ndjson')

const EXIT = { OK: 0, INVARIANT: 1, INPUT: 2 }

const argIdx = process.argv.indexOf('--radius')
/** 반경(m). 🔴 100 아래로 내리지 마라 — place-slope.mjs 머리말 참고. */
const RADIUS_M = argIdx > 0 ? Number(process.argv[argIdx + 1]) : 200

/** 반경 안 보행로 총 길이가 이만큼도 안 되면 값을 만들지 않는다. */
const MIN_LENGTH_M = 150

const log = (...a) => console.log(new Date().toISOString().slice(11, 19), ...a)

/**
 * 🔴 대조군 — place-slope.mjs 와 **같은 여섯 자리**다. 일부러 같게 뒀다.
 *    두 파일이 같은 규칙을 쓰는지 검사하는 자리이기도 하다 — 값이 어긋나면 규칙이 갈라진 것이다.
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

/** 길이로 가중한 백분위. place-slope.mjs 와 같다. */
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
  log(`장소 경사(상가정보) — 반경 ${RADIUS_M}m · 구간 p50 을 길이로 가중해 p90`)

  if (!existsSync(SEG)) {
    log('🔴 구간 경사가 없습니다. 먼저 돌리십시오 — node process/slope.mjs')
    process.exitCode = EXIT.INPUT
    return
  }
  if (!existsSync(PLACES)) {
    log(`🔴 장소 목록이 없습니다: ${PLACES}`)
    log('   머리말의 psql 명령으로 운영 DB 에서 뽑으십시오.')
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
  const places = []
  {
    const rl = createInterface({ input: createReadStream(PLACES), crlfDelay: Infinity })
    for await (const line of rl) {
      if (!line.trim()) continue
      const [sourceType, sourceId, title, lat, lon] = line.split('\t')
      const y = Number(lat), x = Number(lon)
      if (!sourceType || !sourceId || !Number.isFinite(y) || !Number.isFinite(x)) continue
      places.push({ sourceType, sourceId, title: title ?? '', lat: y, lon: x })
    }
  }
  log(`  장소 ${places.length.toLocaleString()}곳`)

  // ── 계산 ───────────────────────────────────────────────────────────
  await mkdir(OUT, { recursive: true })
  const lines = []
  const bySource = {}
  let made = 0, tooFew = 0
  for (const p of places) {
    const r = slopeAt(p.lat, p.lon)
    if (!r) { tooFew++; continue } // 🔴 값을 지어내지 않는다
    made++
    bySource[p.sourceType] = (bySource[p.sourceType] ?? 0) + 1
    lines.push(JSON.stringify({
      sourceType: p.sourceType, sourceId: p.sourceId, title: p.title,
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
  log(`  출처별: ${Object.entries(bySource).map(([k, v]) => `${k} ${v}`).join(' · ')}`)
  log('  대조군 — 매립지(평지여야 한다)')
  for (const [n, v] of flat) log(`    ${String(v ?? '값없음').padStart(6)}%  ${n}`)
  log('  대조군 — 산복도로(비탈이어야 한다)')
  for (const [n, v] of steep) log(`    ${String(v ?? '값없음').padStart(6)}%  ${n}`)

  stamp(join(ROOT, 'data/staged/_place-slope-sbiz-run'), {
    step: 'process/place-slope-sbiz',
    inputs: [SEG, PLACES],
    params: { radiusM: RADIUS_M, minLengthM: MIN_LENGTH_M, stat: 'length-weighted p90 of segment p50Slope' },
    result: { places: places.length, made, tooFew, bySource, segments: segs.length, control: { flat, steep } },
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

await main()
