#!/usr/bin/env node
/**
 * 장소 조용함 — 관광공사 장소편 (S15P21E201-1154)
 *
 * 화면이 묻는 것은 "이 장소가 조용한가" 다. 취향 축 QUIETNESS_SCORE 가 그 자리인데
 * 배포 DB 에 값이 **한 줄도 없었다** — 사용자가 "조용한 곳" 을 고르면 아무것도 안 나왔다.
 * (근거: 배포 DB gabolle.place_feature 직접 집계, 커밋 ab4d0162. 🔴 갈래 목록 API 는
 *  점수형 축을 못 세므로 축의 생사 판단에 쓰지 않는다 — S15P21E201-1149)
 *
 *   조용함 = 반경 200m 안의 길들에 대해, 길이로 가중한 p90 소음가중치를 뒤집은 값
 *
 * 🔴 **판정은 여기 없다.** `quietness-core.mjs` 한 곳에 있고 입구 둘이 가져다 쓴다
 *    (S15P21E201-1156). 이 파일이 하는 일은 **장소 명단과 좌표를 모아 주는 것뿐**이다.
 *
 *      process/place-quietness.mjs        ← 여기. 관광공사 수집본 (328곳)
 *      process/place-quietness-sbiz.mjs     상가정보 (2,355곳 — 운영 DB 의 88%)
 *
 * 🔴 이것은 측정값이 아니라 **대리지표**다. 등록된 28개 출처에 소음 측정치가 하나도 없다.
 *    "고속도로 옆은 시끄럽고 골목은 조용하다" 를 숫자로 옮긴 것이고, 데시벨이 아니다.
 *    그래서 줄마다 evidenceStatus 가 ESTIMATED 다.
 *
 * 🔴 경사(place-slope.mjs)와 달리 **고도를 안 쓴다.** 그래서 좌표가 빠진 중간 산출물
 *    segment-slope.ndjson 이나 DEM 타일이 필요 없고, 원본 추출본에서 바로 간다.
 *
 * 규칙의 전문·한계·실측 숫자는 **docs/PLACE-QUIETNESS.md** 에 있다.
 *
 * 입력
 *   data/raw/pbf/road.ndjson · walk.ndjson   collect/pbf_extract.py 가 만든다
 *   config/road-noise-weights.json           이 저장소에 있다
 *   data/raw/tourapi/tourapi-busan.ndjson    collect/tourapi.mjs 가 만든다
 *
 * 실행
 *   node process/place-quietness.mjs
 *   node process/place-quietness.mjs --radius 300
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
const IN_PLACES = join(ROOT, 'data/raw/tourapi/tourapi-busan.ndjson')
const OUT = join(ROOT, 'data/staged')
const OUT_FILE = join(OUT, 'place-quietness.ndjson')

const argIdx = process.argv.indexOf('--radius')
const RADIUS_M = argIdx > 0 ? Number(process.argv[argIdx + 1]) : RADIUS_DEFAULT

async function main() {
  log(`장소 조용함(관광공사) — 반경 ${RADIUS_M}m · 도로 등급을 길이로 가중해 p90`)

  for (const [f, hint] of [
    [IN_ROAD, 'python collect/pbf_extract.py'],
    [IN_WALK, 'python collect/pbf_extract.py'],
    [IN_WEIGHTS, '이 파일이 저장소에 있어야 합니다'],
    [IN_PLACES, 'node collect/tourapi.mjs'],
  ]) {
    if (!existsSync(f)) {
      log(`🔴 입력이 없습니다: ${f}`)
      log(`   먼저 돌리십시오 — ${hint}`)
      process.exitCode = EXIT.INPUT
      return
    }
  }
  if (!Number.isFinite(RADIUS_M) || RADIUS_M < 50) {
    log(`🔴 반경 ${RADIUS_M}m 는 쓰지 않습니다.`)
    process.exitCode = EXIT.INPUT
    return
  }

  // ── 길 ─────────────────────────────────────────────────────────────
  const weights = await loadWeights(IN_WEIGHTS)
  const { segs, stats } = await readRoadPieces([IN_ROAD, IN_WALK], weights)
  log(`  길 ${stats.ways.toLocaleString()}개 → 조각 ${segs.length.toLocaleString()}개 · 안 셈 ${stats.skipped} (공사중·계획중) · 등급모름 ${stats.unknown} · 좌표없음 ${stats.noGeom}`)
  if (stats.unknown) {
    const top = [...stats.unknownKinds].sort((a, b) => b[1] - a[1]).slice(0, 5)
    log(`     🔴 표에 없는 등급이 있습니다 — ${top.map(([k, v]) => `${k}=${v}`).join(' · ')}`)
    log(`        많으면 config/road-noise-weights.json 을 고치십시오.`)
  }
  if (!segs.length) {
    log('🔴 길이 하나도 없습니다.')
    process.exitCode = EXIT.INPUT
    return
  }
  const quietnessAt = buildIndex(segs, RADIUS_M)

  // ── 장소 ───────────────────────────────────────────────────────────
  const places = new Map()
  {
    const rl = createInterface({ input: createReadStream(IN_PLACES), crlfDelay: Infinity })
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
    const r = quietnessAt(p.lat, p.lon)
    if (!r) { tooFew++; continue } // 🔴 값을 지어내지 않는다
    made++
    lines.push(JSON.stringify({
      contentid, title: p.title,
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

  stamp(join(ROOT, 'data/staged/_place-quietness-run'), {
    step: 'process/place-quietness',
    inputs: [IN_ROAD, IN_WALK, IN_WEIGHTS, IN_PLACES],
    params: {
      radiusM: RADIUS_M, minLengthM: MIN_LENGTH_M, pieceM: PIECE_M,
      stat: 'length-weighted p90 of road-class noise weight, inverted',
      unknownWeight: weights.unknownWeight,
    },
    result: {
      places: places.size, made, tooFew, ways: stats.ways, pieces: segs.length,
      skippedRoads: stats.skipped, unknownClassRoads: stats.unknown,
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
