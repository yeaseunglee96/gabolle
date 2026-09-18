/**
 * 조용함 판정 — 공통 알맹이 (S15P21E201-1156)
 *
 * 조용함을 내는 입구가 둘이다. 장소 명단이 어디서 오느냐만 다르다.
 *
 *   process/place-quietness.mjs        관광공사 수집본 (328곳)
 *   process/place-quietness-sbiz.mjs   상가정보 (2,355곳 — 운영 DB 의 88%)
 *
 * 🔴 **판정을 두 곳에 두지 않는다.** 경사는 place-slope.mjs 와 place-slope-sbiz.mjs 가
 *    같은 규칙을 **각자 한 벌씩** 갖고 있고, 대조군 여섯 자리를 *"일부러 같게 뒀다"* 는
 *    주석으로 묶어 두었다. 사람이 손으로 맞추는 것이라 **갈라질 수 있다.**
 *    여기서는 규칙을 이 파일 하나에 두고 둘이 **가져다 쓴다** — 갈라질 자리가 없다.
 *    (팀 코드 규칙 4.2 「판정이 두 곳에 생기지 않게 한다」)
 *
 * 규칙의 전문과 근거는 **docs/PLACE-QUIETNESS.md** 에 있다. 여기서는 그대로 구현한다.
 */
import { readFile } from 'node:fs/promises'
import { createReadStream } from 'node:fs'
import { createInterface } from 'node:readline'

export const EXIT = { OK: 0, INVARIANT: 1, INPUT: 2 }

export const log = (...a) => console.log(new Date().toISOString().slice(11, 19), ...a)

/** 반경(m). 경사와 같은 값이다 — 두 축이 다른 동네를 보면 비교가 안 된다. */
export const RADIUS_DEFAULT = 200

/** 반경 안 길 총 길이가 이만큼도 안 되면 값을 만들지 않는다. */
export const MIN_LENGTH_M = 150

// ── 거리 ─────────────────────────────────────────────────────────────
const M_PER_DEG_LAT = 110574
const mPerDegLon = (lat) => 111320 * Math.cos((lat * Math.PI) / 180)
export function distM(aLat, aLon, bLat, bLon) {
  const dy = (aLat - bLat) * M_PER_DEG_LAT
  const dx = (aLon - bLon) * mPerDegLon(aLat)
  return Math.hypot(dx, dy)
}

/**
 * 길이로 가중한 백분위.
 *
 * 🔴 가중이 없으면 짧은 토막이 여럿 모인 골목이 결과를 끌고 간다. 묻는 것은
 *    "몇 개의 길이 시끄러운가" 가 아니라 "시끄러운 길이 얼마나 뻗어 있는가" 다.
 */
export function weightedPercentile(rows, q) {
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
 * 길을 최대 PIECE_M 짜리 조각으로 자른다.
 *
 * 🔴 **자르지 않으면 멀리 있는 길이 딸려 들어온다.** 반경 판정은 조각의 가운데 점에서
 *    재고, 긴 길은 가운데 점 하나로 대표가 안 되므로 `반경 + 길이/2` 만큼 여유를 준다.
 *    통짜 OSM way 를 그대로 넣으면 그 여유가 킬로미터가 된다.
 *
 *    2026-09-17 실측 — 금정산성 동문(산속)이 조용함 10점(도심보다 시끄러움)으로 나왔다.
 *      1차  낙동정맥 10,412m → 여유 5,206m. 5km 밖 고속도로가 붙었다
 *      2차  꼭짓점 단위로 잘라도 그대로. 큰길은 곧게 뻗어 **중간 꼭짓점이 없다** —
 *           화명대로 한 변이 850m 라 625m 밖인데 잡혔다
 *      3차  **변 안에서까지** 자르고 50점. 동문로(tertiary) 옆이라 맞다
 *
 * 100m 로 자르면 여유가 최대 50m 다. 반경 200m 에 비해 작아 결과를 밀지 않는다.
 * 🔴 더 잘게 자르지 않는 이유는 개수다 — 격자와 정렬 비용이 그만큼 는다.
 */
export const PIECE_M = 100

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

export function splitIntoPieces(geom) {
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

/**
 * 가중치 표를 읽는다.
 *
 * 🔴 숫자가 코드가 아니라 파일에 있는 이유는 **그것이 임의라는 사실이 드러나야** 하기
 *    때문이다. 자세한 것은 config/road-noise-weights.json 머리말.
 */
export async function loadWeights(path) {
  const doc = JSON.parse(await readFile(path, 'utf8'))
  if (!doc.weights || !Number.isFinite(doc.unknownWeight)) {
    throw new Error('가중치 파일에 weights 또는 unknownWeight 가 없습니다')
  }
  return { weights: doc.weights, unknownWeight: doc.unknownWeight }
}

/** 도로 추출본을 읽어 조각 목록을 만든다. */
export async function readRoadPieces(files, { weights, unknownWeight }) {
  const segs = []
  const stats = { ways: 0, skipped: 0, unknown: 0, noGeom: 0, unknownKinds: new Map() }
  for (const file of files) {
    const rl = createInterface({ input: createReadStream(file), crlfDelay: Infinity })
    for await (const line of rl) {
      if (!line) continue
      let o
      try { o = JSON.parse(line) } catch { continue }
      const geom = o.geometry
      if (!Array.isArray(geom) || geom.length < 2 || geom[0]?.lat == null) { stats.noGeom++; continue }

      const kind = o.tags?.highway
      let w
      if (kind && Object.prototype.hasOwnProperty.call(weights, kind)) {
        w = weights[kind]
        // 🔴 null 은 0(조용하다)이 아니라 '안 센다' 다. 공사중·계획중인 길이 그렇다
        if (w === null) { stats.skipped++; continue }
      } else {
        w = unknownWeight
        stats.unknown++
        const k = kind ?? '(없음)'
        stats.unknownKinds.set(k, (stats.unknownKinds.get(k) ?? 0) + 1)
      }

      for (const p of splitIntoPieces(geom)) segs.push({ lat: p.lat, lon: p.lon, length: p.length, w })
      stats.ways++
    }
  }
  return { segs, stats }
}

/**
 * 격자 색인을 만들고 조용함을 재는 함수를 돌려준다.
 *
 * 🔴 장소마다 전체 조각을 훑으면 수억 번이다. 격자로 나눠 둔다 (경사와 같은 칸 크기).
 */
export function buildIndex(segs, radiusM = RADIUS_DEFAULT) {
  const CELL_DEG = 0.005 // ≈ 550m
  const grid = new Map()
  for (const s of segs) {
    const k = `${Math.floor(s.lat / CELL_DEG)},${Math.floor(s.lon / CELL_DEG)}`
    const bucket = grid.get(k)
    if (bucket) bucket.push(s)
    else grid.set(k, [s])
  }
  const span = Math.ceil((radiusM + 400) / (CELL_DEG * M_PER_DEG_LAT))

  return function quietnessAt(lat, lon) {
    const rows = []
    const ci = Math.floor(lat / CELL_DEG)
    const cj = Math.floor(lon / CELL_DEG)
    for (let i = ci - span; i <= ci + span; i++) {
      for (let j = cj - span; j <= cj + span; j++) {
        for (const s of grid.get(`${i},${j}`) ?? []) {
          if (distM(lat, lon, s.lat, s.lon) > radiusM + s.length / 2) continue
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
}

/**
 * 🔴 대조군 — 두 입구가 **같은 자리**를 본다. 이 파일에 한 벌만 두었으므로 갈라질 수 없다.
 *
 * 좌표는 2026-09-17 에 tourapi 수집본에서 직접 꺼냈다. 기억으로 적으면 틀린다.
 * 이름이 아니라 좌표로 박는 이유는 이름이 원천에서 바뀌기 때문이다.
 */
export const CONTROL_QUIET = [
  ['범어사 성보박물관', 35.2839753157, 129.0681152667],
  ['금정산', 35.268448978, 129.0518587773],
  ['금정산성 동문', 35.2453406867, 129.0642277794],
]
export const CONTROL_LOUD = [
  ['서면역 일대', 35.156639327, 129.0540422103],
  ['부산역 앞', 35.1177518053, 129.0427025928],
  ['서면 범천동', 35.1485125805, 129.0606113054],
]

/**
 * 🔴 절대 기준선을 박지 않고 **무리끼리 비교**한다.
 *
 * place-slope.mjs 는 "5% 이하가 평지" 처럼 절대값을 박았다. 그럴 수 있었던 것은 경사에
 * **실측 기준선이 있었기** 때문이다. 소음에는 없다 — 28개 출처에 측정치가 하나도 없다.
 * 없는 기준선을 지어내면 그 줄이 그 순간 낡은 기준이 되고, 다음 사람이 그것을 믿는다.
 */
export const CONTROL_MIN_GAP = 15

/** 대조군을 재고 통과 여부까지 판정한다. 두 입구가 같은 판정을 쓴다. */
export function checkControls(quietnessAt) {
  const run = (list) => list.map(([n, la, lo]) => [n, quietnessAt(la, lo)?.quietnessScore ?? null])
  const quiet = run(CONTROL_QUIET)
  const loud = run(CONTROL_LOUD)
  const avg = (rows) => {
    const vals = rows.map(([, v]) => v).filter((v) => v != null)
    return vals.length ? vals.reduce((a, b) => a + b, 0) / vals.length : null
  }
  const quietAvg = avg(quiet)
  const loudAvg = avg(loud)
  const missing = [...quiet, ...loud].filter(([, v]) => v == null)
  const gap = quietAvg != null && loudAvg != null ? quietAvg - loudAvg : null
  return { quiet, loud, quietAvg, loudAvg, missing, gap, ok: !missing.length && gap >= CONTROL_MIN_GAP }
}

/** 대조군 결과를 찍는다. 두 입구가 같은 모양으로 보고한다. */
export function logControls(c) {
  log('  대조군 — 산·절 (조용해야 한다. 점수가 높을수록 조용)')
  for (const [n, v] of c.quiet) log(`    ${String(v ?? '값없음').padStart(6)}  ${n}`)
  log('  대조군 — 도심 (시끄러워야 한다)')
  for (const [n, v] of c.loud) log(`    ${String(v ?? '값없음').padStart(6)}  ${n}`)
  if (c.gap != null) {
    log(`  차이: 산 ${c.quietAvg.toFixed(1)} − 도심 ${c.loudAvg.toFixed(1)} = ${c.gap.toFixed(1)} (${CONTROL_MIN_GAP} 이상이어야 한다)`)
  }
}
