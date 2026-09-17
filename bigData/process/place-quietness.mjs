#!/usr/bin/env node
/**
 * 장소 조용함 — 주변 도로 등급으로 소음 대리지표를 내고 장소에 붙인다 (S15P21E201-1154)
 *
 * 화면이 묻는 것은 "이 장소가 조용한가" 다. 취향 축 QUIETNESS_SCORE 가 그 자리인데
 * 배포 DB 에 값이 **한 줄도 없다** — 사용자가 "조용한 곳" 을 고르면 아무것도 안 나온다.
 * (근거: 배포 DB gabolle.place_feature 직접 집계, 커밋 ab4d0162. 🔴 갈래 목록 API 는
 *  점수형 축을 못 세므로 축의 생사 판단에 쓰지 않는다 — S15P21E201-1149)
 *
 *   조용함 = 반경 200m 안의 길들에 대해, 길이로 가중한 p90 소음가중치를 뒤집은 값
 *
 * ── 🔴 이것은 측정값이 아니다. 대리지표(proxy)다 ──────────────────────────────
 *
 * config/sources.json 의 28개 출처를 전부 훑었고 **소음을 주는 것은 하나도 없다**
 * (2026-09-17 확인). 그래서 "고속도로 옆은 시끄럽고 골목은 조용하다" 는 상식을 숫자로
 * 옮겼다. 데시벨이 아니고 어떤 기계도 이 값을 재지 않았다.
 *
 * 가중치는 코드가 아니라 **config/road-noise-weights.json** 에 있다. 숫자가 임의라는
 * 사실이 파일로 드러나야 다음 사람이 이것을 측정값으로 읽지 않는다. shade.mjs 가
 * 수종 가중치를 같은 이유로 파일에 뒀다.
 *
 * 그래서 적재하는 쪽은 evidence_status 를 **ESTIMATED** 로 넣어야 한다. 산출물 줄마다
 * 박아 두었다.
 *
 * ── 🔴 왜 slope.mjs 를 안 거치나 ────────────────────────────────────────────
 *
 * 경사(place-slope.mjs)는 중간 산출물 data/staged/segment-slope.ndjson 을 읽는다.
 * 그런데 이 PC 의 그 파일 67,676줄에 **좌표 칸이 아예 없어서** place-slope.mjs 가
 * 종료 코드 2 로 멈춘다 (2026-09-17 실측).
 *
 * 다시 만들려면 고도 타일(DEM)이 필요한데 경사는 고도를 쓰고 **조용함은 안 쓴다.**
 * 우리에게 필요한 것은 좌표와 도로 등급 둘뿐이고, 그 둘은 **원본 추출본에 이미 있다**
 * (road.ndjson·walk.ndjson 2만 줄 표본에서 좌표 100% · highway 100%).
 * 그래서 원본에서 바로 간다. 무거운 고도 단계가 통째로 빠진다.
 *
 * ── 🔴 왜 p90 인가 (평균이 아니라) ──────────────────────────────────────────
 *
 * 소음은 평균이 아니라 **제일 시끄러운 것**이 정한다. 조용한 골목 열 개 사이에 고속도로가
 * 하나 지나가면 그 동네는 시끄럽다. 평균을 쓰면 그 고속도로가 골목들에 희석돼 사라진다.
 * 그래서 "주변에서 시끄러운 축에 드는 길이 이 정도" 를 뜻하는 p90 을 쓴다.
 * place-slope.mjs 가 경사에서 p90 을 고른 것과 같은 이유다.
 *
 * 🔴 길이로 가중하는 이유도 같다. 우리가 묻는 것은 "몇 개의 길이 시끄러운가" 가 아니라
 *    "시끄러운 길이 얼마나 뻗어 있는가" 다. 가중이 없으면 짧은 토막이 여럿 모인 골목이
 *    결과를 끌고 간다.
 *
 * ── 🔴 이 값이 못 하는 것 — 거리를 안 가른다 ─────────────────────────────────
 *
 * 20m 옆 고속도로와 200m 밖 고속도로를 같게 센다. 실제 소음은 거리가 멀수록 줄지만,
 * **얼마나 줄어드는지를 정할 근거가 없다.** 감쇠 곡선을 지어내면 없는 정밀도를 있는 것처럼
 * 만드는 것이라, 반경 안을 고르게 세고 그 한계를 여기 적어 둔다.
 * place-slope.mjs 가 "방향을 못 가른다" 를 같은 자리에 적은 것과 같다.
 *
 * 그리고 **실내를 모른다.** 시끄러운 길가라도 창이 안쪽을 향한 가게는 조용하다.
 * 이 값은 "이 장소 **주변**이 얼마나 조용한가" 이지 "안에 앉으면 조용한가" 가 아니다.
 *
 * ── 🔴 모르는 것을 조용하다고 하지 않는다 ───────────────────────────────────
 *
 * 반경 안에 길이 거의 없으면(MIN_LENGTH_M 미만) **줄을 만들지 않는다.** 길이 없는 것과
 * 조용한 것은 다르다. 값이 없으면 화면이 그 줄을 안 그린다.
 * 같은 이유로 공사중·계획중인 길은 가중치가 null 이라 아예 안 센다.
 *
 * 입력
 *   data/raw/pbf/road.ndjson           collect/pbf_extract.py 가 만든다
 *   data/raw/pbf/walk.ndjson           같은 것
 *   config/road-noise-weights.json     이 저장소에 있다
 *   data/raw/tourapi/tourapi-busan.ndjson   collect/tourapi.mjs 가 만든다
 *
 * 🔴 상가정보(SBIZ) 장소 2,355곳은 여기서 안 다룬다. 원본 csv 가 이 PC 에 없다
 *    (2026-09-17 디스크 전체 확인). 경사가 place-slope.mjs 와 place-slope-sbiz.mjs 로
 *    나뉜 것과 같은 모양으로, 자료가 생기면 place-quietness-sbiz.mjs 를 따로 만든다.
 *
 * 실행
 *   node process/place-quietness.mjs
 *   node process/place-quietness.mjs --radius 300
 *
 * 종료 코드: 0 냈다 / 1 불변식 깨짐 / 2 입력 없음
 */
import { readFile, writeFile, mkdir } from 'node:fs/promises'
import { createReadStream, existsSync } from 'node:fs'
import { createInterface } from 'node:readline'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { stamp } from '../mlops/manifest.mjs'

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..')
const IN_ROAD = join(ROOT, 'data/raw/pbf/road.ndjson')
const IN_WALK = join(ROOT, 'data/raw/pbf/walk.ndjson')
const IN_WEIGHTS = join(ROOT, 'config/road-noise-weights.json')
const IN_PLACES = join(ROOT, 'data/raw/tourapi/tourapi-busan.ndjson')
const OUT = join(ROOT, 'data/staged')
const OUT_FILE = join(OUT, 'place-quietness.ndjson')

const EXIT = { OK: 0, INVARIANT: 1, INPUT: 2 }

const argIdx = process.argv.indexOf('--radius')
/** 반경(m). 경사와 같은 값을 쓴다 — 두 축이 다른 동네를 보면 비교가 안 된다. */
const RADIUS_M = argIdx > 0 ? Number(process.argv[argIdx + 1]) : 200

/** 반경 안 길 총 길이가 이만큼도 안 되면 값을 만들지 않는다. */
const MIN_LENGTH_M = 150

const log = (...a) => console.log(new Date().toISOString().slice(11, 19), ...a)

/**
 * 🔴 대조군 — 이것이 어긋나면 가중치나 반경이 틀린 것이다.
 *
 * 좌표는 **2026-09-17 에 tourapi 수집본에서 직접 꺼냈다.** 기억으로 적으면 틀린다.
 * 이름이 아니라 좌표로 박는 이유는 이름이 원천에서 바뀌기 때문이다.
 */
const CONTROL_QUIET = [
  ['범어사 성보박물관', 35.2839753157, 129.0681152667],
  ['금정산', 35.268448978, 129.0518587773],
  ['금정산성 동문', 35.2453406867, 129.0642277794],
]
const CONTROL_LOUD = [
  ['서면역 일대', 35.156639327, 129.0540422103],
  ['부산역 앞', 35.1177518053, 129.0427025928],
  ['서면 범천동', 35.1485125805, 129.0606113054],
]

/**
 * 🔴 절대 기준선을 박지 않고 **무리끼리 비교**한다.
 *
 * place-slope.mjs 는 "5% 이하가 평지" 처럼 절대값을 박았다. 그럴 수 있었던 것은 경사에
 * **실측 기준선이 있었기** 때문이다. 소음에는 그런 것이 없다 — 28개 출처에 측정치가 없다.
 *
 * 없는 기준선을 지어내면 그 줄이 그 순간 낡은 기준이 되고, 다음 사람이 그것을 믿는다.
 * 대신 **"산이 도심보다 조용해야 한다"** 만 요구한다. 이건 측정 없이도 확실하고,
 * 틀리면 가중치가 잘못됐다는 뜻이라 검사기로서 실제로 작동한다.
 *
 * 절대 기준선은 아래 실행 로그의 숫자를 사람이 보고 나서 정한다.
 */
const CONTROL_MIN_GAP = 15

// ── 거리 ─────────────────────────────────────────────────────────────
const M_PER_DEG_LAT = 110574
const mPerDegLon = (lat) => 111320 * Math.cos((lat * Math.PI) / 180)
function distM(aLat, aLon, bLat, bLon) {
  const dy = (aLat - bLat) * M_PER_DEG_LAT
  const dx = (aLon - bLon) * mPerDegLon(aLat)
  return Math.hypot(dx, dy)
}

/** 길이로 가중한 백분위. place-slope.mjs 와 같은 함수다. */
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

/**
 * 길이가 최대 PIECE_M 인 조각들로 자른다. 조각마다 { lat, lon, length } — 가운데 점이다.
 *
 * 🔴 왜 자르나. **자르지 않으면 멀리 있는 길이 딸려 들어온다.**
 *
 * 반경 판정은 조각의 가운데 점에서 잰다. 그런데 긴 길은 가운데 점 하나로 대표가 안 되므로
 * `반경 + 길이/2` 만큼 여유를 준다 (place-slope.mjs 에서 베낀 줄이다).
 *
 * 그 줄은 **원본에서는 안전했다.** place-slope.mjs 가 읽는 것은 slope.mjs 가 이미 잘라 둔
 * 짧은 구간이라 여유가 몇십 미터였다. 그런데 **여기 입력은 자르지 않은 통짜 OSM way** 다.
 *
 *   2026-09-17 실측 — 금정산성 동문(산속)이 조용함 10점으로 나왔다. 도심보다 시끄럽다.
 *   반경 200m 안에는 동문로(tertiary, 0.5)와 산길뿐인데도 그랬다.
 *   원인은 낙동정맥 같은 10,412m 짜리 길이었다 — 여유가 `200 + 5,206 = 5,406m` 가 되어
 *   **5km 밖 고속도로가 산속 장소에 붙었다.**
 *
 * 100m 로 자르면 여유가 최대 50m 다. 반경 200m 에 비해 작아 결과를 밀지 않는다.
 * 🔴 더 잘게 자르지 않는 이유는 개수다 — 조각 수가 늘면 격자와 정렬 비용이 그만큼 는다.
 *
 * 🔴 **꼭짓점에서만 끊으면 모자란다.** 큰길은 곧게 뻗어 중간 꼭짓점이 없다 — 한 변이
 *    수백 미터다. 꼭짓점 단위로만 끊으면 그 변이 통째로 한 조각이 되어 여유가 다시 커진다.
 *
 *    2026-09-17 2차 실측 — 조각내기를 넣고도 금정산성 동문이 10점 그대로였다.
 *    범인은 **화명대로(trunk, 가중치 0.9)** 였고 거리가 **625m** 였다. 한 변이 850m 라
 *    여유가 425m 였던 것이다. 그래서 **변 안에서도 쪼갠다.**
 */
const PIECE_M = 100

/** 점들의 길이 절반 지점. 구부러져 있어도 길 위에 남는다. */
function midOf(pts, length) {
  let acc = 0
  for (let i = 1; i < pts.length; i++) {
    const d = distM(pts[i - 1].lat, pts[i - 1].lon, pts[i].lat, pts[i].lon)
    if (acc + d >= length / 2) {
      const t = d === 0 ? 0 : (length / 2 - acc) / d
      return {
        length,
        lat: pts[i - 1].lat + (pts[i].lat - pts[i - 1].lat) * t,
        lon: pts[i - 1].lon + (pts[i].lon - pts[i - 1].lon) * t,
      }
    }
    acc += d
  }
  const last = pts.at(-1)
  return { length, lat: last.lat, lon: last.lon }
}

function splitIntoPieces(geom) {
  const pieces = []
  let carry = 0
  let cur = [geom[0]]
  for (let i = 1; i < geom.length; i++) {
    let aLat = geom[i - 1].lat, aLon = geom[i - 1].lon
    const bLat = geom[i].lat, bLon = geom[i].lon
    let remain = distM(aLat, aLon, bLat, bLon)
    // 🔴 변이 길면 변 안에서 끊는다. while 인 이유는 한 변에서 여러 번 끊길 수 있어서다
    while (carry + remain >= PIECE_M) {
      const need = PIECE_M - carry
      const t = remain === 0 ? 0 : need / remain
      const cLat = aLat + (bLat - aLat) * t
      const cLon = aLon + (bLon - aLon) * t
      cur.push({ lat: cLat, lon: cLon })
      pieces.push(midOf(cur, PIECE_M))
      aLat = cLat; aLon = cLon
      remain -= need
      carry = 0
      cur = [{ lat: aLat, lon: aLon }]
    }
    carry += remain
    cur.push({ lat: bLat, lon: bLon })
  }
  if (carry > 0 && cur.length >= 2) pieces.push(midOf(cur, carry))
  return pieces
}

async function main() {
  log(`장소 조용함 — 반경 ${RADIUS_M}m · 도로 등급을 길이로 가중해 p90`)

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

  // ── 가중치 ─────────────────────────────────────────────────────────
  const wDoc = JSON.parse(await readFile(IN_WEIGHTS, 'utf8'))
  const WEIGHTS = wDoc.weights
  const UNKNOWN_W = wDoc.unknownWeight
  if (!WEIGHTS || !Number.isFinite(UNKNOWN_W)) {
    log('🔴 가중치 파일에 weights 또는 unknownWeight 가 없습니다.')
    process.exitCode = EXIT.INPUT
    return
  }

  // ── 길 ─────────────────────────────────────────────────────────────
  const segs = []
  let skipped = 0 // 가중치 null (공사중·계획중)
  let unknown = 0 // 표에 없는 등급
  let noGeom = 0
  let ways = 0
  const unknownKinds = new Map()
  for (const file of [IN_ROAD, IN_WALK]) {
    const rl = createInterface({ input: createReadStream(file), crlfDelay: Infinity })
    for await (const line of rl) {
      if (!line) continue
      let o
      try { o = JSON.parse(line) } catch { continue }
      const geom = o.geometry
      if (!Array.isArray(geom) || geom.length < 2 || geom[0]?.lat == null) { noGeom++; continue }

      const kind = o.tags?.highway
      let w
      if (kind && Object.prototype.hasOwnProperty.call(WEIGHTS, kind)) {
        w = WEIGHTS[kind]
        if (w === null) { skipped++; continue } // 🔴 0 이 아니라 '안 센다'
      } else {
        w = UNKNOWN_W
        unknown++
        unknownKinds.set(kind ?? '(없음)', (unknownKinds.get(kind ?? '(없음)') ?? 0) + 1)
      }

      // 🔴 통짜 길을 그대로 넣지 않는다 — splitIntoPieces 머리말 참고
      for (const p of splitIntoPieces(geom)) {
        segs.push({ lat: p.lat, lon: p.lon, length: p.length, w })
      }
      ways++
    }
  }
  log(`  길 ${ways.toLocaleString()}개 → 조각 ${segs.length.toLocaleString()}개 · 안 셈 ${skipped} (공사중·계획중) · 등급모름 ${unknown} · 좌표없음 ${noGeom}`)
  if (unknown) {
    const top = [...unknownKinds].sort((a, b) => b[1] - a[1]).slice(0, 5)
    log(`     🔴 표에 없는 등급이 있습니다 — ${top.map(([k, v]) => `${k}=${v}`).join(' · ')}`)
    log(`        많으면 config/road-noise-weights.json 을 고치십시오.`)
  }
  if (!segs.length) {
    log('🔴 길이 하나도 없습니다.')
    process.exitCode = EXIT.INPUT
    return
  }

  // ── 격자 ───────────────────────────────────────────────────────────
  // 🔴 장소마다 전체 길을 훑으면 수백만 번이다. 격자로 나눠 둔다 (경사와 같은 칸 크기).
  const CELL_DEG = 0.005 // ≈ 550m
  const grid = new Map()
  for (const s of segs) {
    const k = `${Math.floor(s.lat / CELL_DEG)},${Math.floor(s.lon / CELL_DEG)}`
    const bucket = grid.get(k)
    if (bucket) bucket.push(s)
    else grid.set(k, [s])
  }

  function quietnessAt(lat, lon) {
    const rows = []
    const span = Math.ceil((RADIUS_M + 400) / (CELL_DEG * M_PER_DEG_LAT))
    const ci = Math.floor(lat / CELL_DEG)
    const cj = Math.floor(lon / CELL_DEG)
    for (let i = ci - span; i <= ci + span; i++) {
      for (let j = cj - span; j <= cj + span; j++) {
        for (const s of grid.get(`${i},${j}`) ?? []) {
          // 🔴 긴 길은 중점 하나로 대표가 안 된다 — 길이의 절반만큼 넉넉하게 본다
          if (distM(lat, lon, s.lat, s.lon) > RADIUS_M + s.length / 2) continue
          rows.push({ v: s.w, w: s.length })
        }
      }
    }
    const len = rows.reduce((t, r) => t + r.w, 0)
    if (len < MIN_LENGTH_M) return null // 🔴 값을 지어내지 않는다
    const noiseP90 = weightedPercentile(rows, 0.9)
    return {
      // 0(시끄럽다) ~ 100(조용하다). 소음가중치를 뒤집은 것이다.
      quietnessScore: +((1 - noiseP90) * 100).toFixed(1),
      noiseP90: +noiseP90.toFixed(3),
      roads: rows.length,
      roadLengthM: Math.round(len),
    }
  }

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
    if (!r) { tooFew++; continue }
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
  const check = (list) => list.map(([n, la, lo]) => [n, quietnessAt(la, lo)?.quietnessScore ?? null])
  const quiet = check(CONTROL_QUIET)
  const loud = check(CONTROL_LOUD)
  const avg = (rows) => {
    const vals = rows.map(([, v]) => v).filter((v) => v != null)
    return vals.length ? vals.reduce((a, b) => a + b, 0) / vals.length : null
  }
  const quietAvg = avg(quiet)
  const loudAvg = avg(loud)

  log('')
  log(`저장 완료: ${made}곳 / 표본 모자람 ${tooFew}곳 — ${OUT_FILE}`)
  log('  대조군 — 산·절 (조용해야 한다. 점수가 높을수록 조용)')
  for (const [n, v] of quiet) log(`    ${String(v ?? '값없음').padStart(6)}  ${n}`)
  log('  대조군 — 도심 (시끄러워야 한다)')
  for (const [n, v] of loud) log(`    ${String(v ?? '값없음').padStart(6)}  ${n}`)
  if (quietAvg != null && loudAvg != null) {
    log(`  차이: 산 ${quietAvg.toFixed(1)} − 도심 ${loudAvg.toFixed(1)} = ${(quietAvg - loudAvg).toFixed(1)} (${CONTROL_MIN_GAP} 이상이어야 한다)`)
  }

  stamp(join(ROOT, 'data/staged/_place-quietness-run'), {
    step: 'process/place-quietness',
    inputs: [IN_ROAD, IN_WALK, IN_WEIGHTS, IN_PLACES],
    params: {
      radiusM: RADIUS_M,
      minLengthM: MIN_LENGTH_M,
      stat: 'length-weighted p90 of road-class noise weight, inverted',
      unknownWeight: UNKNOWN_W,
    },
    result: {
      places: places.size, made, tooFew, roads: segs.length,
      skippedRoads: skipped, unknownClassRoads: unknown,
      control: { quiet, loud, quietAvg, loudAvg },
    },
  })

  // ── 불변식 ─────────────────────────────────────────────────────────
  if (made === 0) {
    log('🔴 한 곳도 못 냈습니다. 빈 파일을 성공으로 치지 않습니다.')
    process.exitCode = EXIT.INVARIANT
    return
  }
  const missing = [...quiet, ...loud].filter(([, v]) => v == null)
  if (missing.length) {
    log('')
    log('🔴 대조군에 값이 없습니다. 반경 안에 길이 안 잡혔습니다.')
    for (const [n] of missing) log(`   값없음 — ${n}`)
    process.exitCode = EXIT.INVARIANT
    return
  }
  if (quietAvg - loudAvg < CONTROL_MIN_GAP) {
    log('')
    log('🔴 대조군이 어긋났습니다. 산이 도심보다 조용하게 안 나옵니다.')
    log(`   산 ${quietAvg.toFixed(1)} − 도심 ${loudAvg.toFixed(1)} = ${(quietAvg - loudAvg).toFixed(1)}`)
    log(`   가중치(config/road-noise-weights.json)나 반경이 틀렸습니다.`)
    process.exitCode = EXIT.INVARIANT
    return
  }
  log('  대조군 통과')
}

main().catch((e) => { console.error('치명:', e?.stack || e); process.exit(EXIT.INVARIANT) })
