#!/usr/bin/env node
/**
 * 장소 조용함 — 상가정보 장소편 (S15P21E201-1156)
 *
 * `place-quietness.mjs` 는 관광공사 수집본만 읽는다. 상가정보 장소는 쳐다보지도 않는다.
 * 그런데 **그 2,355곳이 운영 DB 의 88%** 이고, 추천이 실제로 내놓는 장소의 거의 전부다.
 *
 *     출처                 장소 수    조용함이 붙은 곳 (S15P21E201-1154 시점)
 *     관광공사(TOURAPI)        328          328
 *     상가정보(SBIZ)          2,355            0     ← 이 파일이 채운다
 *
 * 그래서 「조용한 곳만 보기」를 켜면 사용자가 받는 음식점·카페에는 값이 하나도 없다.
 * 관광공사 쪽 숫자를 늘려도 이건 안 바뀐다. 경사가 정확히 같은 함정을 밟았고
 * `place-slope-sbiz.mjs` 로 풀었다 (S15P21E201-1047). 같은 모양으로 푼다.
 *
 * 🔴 **판정은 여기 없다.** `quietness-core.mjs` 한 곳에 있고 두 입구가 가져다 쓴다.
 *    경사 짝은 규칙을 각자 한 벌씩 갖고 대조군을 손으로 맞춰 두었는데, 그러면 갈라질 수 있다.
 *    이 파일이 하는 일은 **장소 명단과 좌표를 모아 주는 것뿐**이다.
 *
 * ── 🔴 2026-09-17 정정 — 「원본 csv 가 없다」는 내가 틀린 것이었다 ──────────────
 *
 * S15P21E201-1154 의 커밋·MR·문서에 *"상가정보 원본 csv 가 이 PC 에 없다 (디스크 전체
 * 확인)"* 이라고 적었다. **있었다.**
 *
 *   적혀 있던 이름·경로   data/raw/poi/sbiz-poi-busan-202606.csv   (config/sources.json)
 *   실제                  data/sbiz/부산_202606.csv   83MB · 159,689행
 *
 * 이름과 폴더 하나만 보고 없다고 썼다. 줄 수(159,689)는 등록부와 정확히 같았으니
 * **크기로 찾았으면 바로 나왔다.** 낡은 실측을 지우지 않고 정정과 함께 남긴다 —
 * 다음 사람이 "상가 88% 는 원래 못 한다" 로 읽지 않게.
 *
 * ── 장소 명단을 어디서 얻나 ───────────────────────────────────────────────
 *
 * csv 에는 부산 상가가 **159,689곳** 들어 있지만 운영 DB 에 적재된 것은 **2,355곳**이다.
 * 어느 2,355곳인지를 아는 자료가 `data/staged/place-slope-sbiz.ndjson` 이다 — 경사를
 * 붙일 때 쓴 바로 그 명단이고, 줄마다 `sourceId`(상가업소번호)가 있다.
 *
 * 🔴 **경사와 같은 장소 집합 위에 놓이는 것이 이 방식의 값어치다.** 두 축을 나란히
 *    비교할 수 있고, 한쪽에만 있는 장소가 생기지 않는다.
 *
 * 좌표는 csv 에서 상가업소번호로 찾는다.
 *
 * 입력
 *   data/staged/place-slope-sbiz.ndjson   장소 명단 (sourceId)
 *   data/sbiz/부산_202606.csv              좌표 (상가업소번호 · 경도 · 위도)
 *   data/raw/pbf/road.ndjson · walk.ndjson
 *   config/road-noise-weights.json
 *
 * 실행
 *   node process/place-quietness-sbiz.mjs
 *   node process/place-quietness-sbiz.mjs --radius 300
 *
 * 종료 코드: 0 냈다 / 1 불변식 깨짐 / 2 입력 없음
 */
import { writeFile, mkdir } from 'node:fs/promises'
import { createReadStream, existsSync } from 'node:fs'
import { createInterface } from 'node:readline'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { stamp } from '../mlops/manifest.mjs'
import {
  EXIT, log, RADIUS_DEFAULT, MIN_LENGTH_M, PIECE_M,
  loadWeights, readRoadPieces, buildIndex, checkControls, logControls, CONTROL_MIN_GAP,
} from './quietness-core.mjs'

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..')
const IN_ROAD = join(ROOT, 'data/raw/pbf/road.ndjson')
const IN_WALK = join(ROOT, 'data/raw/pbf/walk.ndjson')
const IN_WEIGHTS = join(ROOT, 'config/road-noise-weights.json')
const IN_ROSTER = join(ROOT, 'data/staged/place-slope-sbiz.ndjson')
const IN_CSV = join(ROOT, 'data/sbiz/부산_202606.csv')
const OUT = join(ROOT, 'data/staged')
const OUT_FILE = join(OUT, 'place-quietness-sbiz.ndjson')

const argIdx = process.argv.indexOf('--radius')
const RADIUS_M = argIdx > 0 ? Number(process.argv[argIdx + 1]) : RADIUS_DEFAULT

/**
 * 따옴표를 존중하는 한 줄 파서.
 *
 * 🔴 `line.split(',')` 로는 안 된다. 상호명에 쉼표가 들어 있는 가게가 있고, 그러면
 *    그 줄부터 칸이 밀려 **경도·위도 자리에 엉뚱한 값이 들어온다.** 조용히 틀린다.
 */
function parseCsvLine(line) {
  const out = []
  let cur = ''
  let inQuote = false
  for (let i = 0; i < line.length; i++) {
    const c = line[i]
    if (inQuote) {
      if (c === '"') {
        if (line[i + 1] === '"') { cur += '"'; i++ } // 따옴표 두 개는 따옴표 하나
        else inQuote = false
      } else cur += c
    } else if (c === '"') inQuote = true
    else if (c === ',') { out.push(cur); cur = '' }
    else cur += c
  }
  out.push(cur)
  return out
}

async function main() {
  log(`장소 조용함(상가정보) — 반경 ${RADIUS_M}m · 도로 등급을 길이로 가중해 p90`)

  for (const [f, hint] of [
    [IN_ROAD, 'python collect/pbf_extract.py'],
    [IN_WALK, 'python collect/pbf_extract.py'],
    [IN_WEIGHTS, '이 파일이 저장소에 있어야 합니다'],
    [IN_ROSTER, 'node process/place-slope-sbiz.mjs — 장소 명단이 여기서 나온다'],
    [IN_CSV, '소상공인시장진흥공단 상가정보 원본. config/sources.json 의 sbiz-poi 참고'],
  ]) {
    if (!existsSync(f)) {
      log(`🔴 입력이 없습니다: ${f}`)
      log(`   먼저 돌리십시오 — ${hint}`)
      process.exitCode = EXIT.INPUT
      return
    }
  }

  // ── 장소 명단 ──────────────────────────────────────────────────────
  const roster = new Map() // 상가업소번호 → title
  {
    const rl = createInterface({ input: createReadStream(IN_ROSTER), crlfDelay: Infinity })
    for await (const line of rl) {
      if (!line.trim()) continue
      let o
      try { o = JSON.parse(line) } catch { continue }
      if (o.sourceType !== 'SBIZ' || !o.sourceId) continue
      roster.set(String(o.sourceId), o.title ?? '')
    }
  }
  log(`  장소 명단 ${roster.size.toLocaleString()}곳 (운영 DB 에 적재된 상가 장소)`)
  if (!roster.size) {
    log('🔴 명단이 비었습니다.')
    process.exitCode = EXIT.INPUT
    return
  }

  // ── 좌표 ───────────────────────────────────────────────────────────
  const places = []
  let csvRows = 0
  {
    const rl = createInterface({ input: createReadStream(IN_CSV, 'utf8'), crlfDelay: Infinity })
    let header = null
    let iId = -1, iLon = -1, iLat = -1, iName = -1
    for await (const line of rl) {
      if (!line.trim()) continue
      const cols = parseCsvLine(line)
      if (!header) {
        header = cols.map((c) => c.replace(/^"|"$/g, ''))
        iId = header.indexOf('상가업소번호')
        iLon = header.indexOf('경도')
        iLat = header.indexOf('위도')
        iName = header.indexOf('상호명')
        // 🔴 칸 이름이 바뀌면 조용히 빈 결과를 내지 말고 그 자리에서 멈춘다
        if (iId < 0 || iLon < 0 || iLat < 0) {
          log(`🔴 csv 머리줄에서 칸을 못 찾았습니다 — 상가업소번호=${iId} 경도=${iLon} 위도=${iLat}`)
          process.exitCode = EXIT.INPUT
          return
        }
        continue
      }
      csvRows++
      const id = cols[iId]
      if (!roster.has(id)) continue
      const lat = Number(cols[iLat]), lon = Number(cols[iLon])
      if (!Number.isFinite(lat) || !Number.isFinite(lon)) continue
      places.push({ sourceId: id, title: roster.get(id) || cols[iName] || '', lat, lon })
    }
  }
  log(`  csv ${csvRows.toLocaleString()}행에서 좌표를 찾은 장소 ${places.length.toLocaleString()}곳`)
  const missing = roster.size - places.length
  if (missing > 0) log(`     🔴 명단에 있는데 csv 에서 못 찾은 곳 ${missing}곳 — 수집분 판이 다를 수 있습니다`)

  // ── 길 ─────────────────────────────────────────────────────────────
  const weights = await loadWeights(IN_WEIGHTS)
  const { segs, stats } = await readRoadPieces([IN_ROAD, IN_WALK], weights)
  log(`  길 ${stats.ways.toLocaleString()}개 → 조각 ${segs.length.toLocaleString()}개 · 안 셈 ${stats.skipped} (공사중·계획중) · 등급모름 ${stats.unknown}`)
  if (!segs.length) {
    log('🔴 길이 하나도 없습니다.')
    process.exitCode = EXIT.INPUT
    return
  }
  const quietnessAt = buildIndex(segs, RADIUS_M)

  // ── 계산 ───────────────────────────────────────────────────────────
  await mkdir(OUT, { recursive: true })
  const lines = []
  let made = 0, tooFew = 0
  for (const p of places) {
    const r = quietnessAt(p.lat, p.lon)
    if (!r) { tooFew++; continue } // 🔴 값을 지어내지 않는다
    made++
    lines.push(JSON.stringify({
      sourceType: 'SBIZ', sourceId: p.sourceId, title: p.title,
      featureType: 'QUIETNESS_SCORE',
      evidenceStatus: 'ESTIMATED', // 🔴 도로 등급에서 유도한 값이다. 잰 것이 아니다
      ...r,
      radiusM: RADIUS_M,
    }))
  }
  await writeFile(OUT_FILE, lines.join('\n') + (lines.length ? '\n' : ''))

  // ── 대조군 ─────────────────────────────────────────────────────────
  const c = checkControls(quietnessAt)
  log('')
  log(`저장 완료: ${made}곳 / 표본 모자람 ${tooFew}곳 — ${OUT_FILE}`)
  logControls(c)

  stamp(join(ROOT, 'data/staged/_place-quietness-sbiz-run'), {
    step: 'process/place-quietness-sbiz',
    inputs: [IN_ROAD, IN_WALK, IN_WEIGHTS, IN_ROSTER, IN_CSV],
    params: {
      radiusM: RADIUS_M, minLengthM: MIN_LENGTH_M, pieceM: PIECE_M,
      stat: 'length-weighted p90 of road-class noise weight, inverted',
      unknownWeight: weights.unknownWeight,
    },
    result: {
      roster: roster.size, placesWithCoord: places.length, made, tooFew,
      ways: stats.ways, pieces: segs.length,
      control: { quiet: c.quiet, loud: c.loud, quietAvg: c.quietAvg, loudAvg: c.loudAvg },
    },
  })

  // ── 불변식 ─────────────────────────────────────────────────────────
  if (made === 0) {
    log('🔴 한 곳도 못 냈습니다. 빈 파일을 성공으로 치지 않습니다.')
    process.exitCode = EXIT.INVARIANT
    return
  }
  if (!c.ok) {
    log('')
    if (c.missing.length) {
      log('🔴 대조군에 값이 없습니다. 반경 안에 길이 안 잡혔습니다.')
      for (const [n] of c.missing) log(`   값없음 — ${n}`)
    } else {
      log('🔴 대조군이 어긋났습니다. 산이 도심보다 조용하게 안 나옵니다.')
      log(`   산 ${c.quietAvg.toFixed(1)} − 도심 ${c.loudAvg.toFixed(1)} = ${c.gap.toFixed(1)} (${CONTROL_MIN_GAP} 이상이어야 한다)`)
    }
    process.exitCode = EXIT.INVARIANT
    return
  }
  log('  대조군 통과')
}

main().catch((e) => { console.error('치명:', e?.stack || e); process.exit(EXIT.INVARIANT) })
