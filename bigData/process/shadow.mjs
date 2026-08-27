#!/usr/bin/env node
/**
 * 구간별·시각별 그늘 비율 — 건물 높이 × 태양 고도
 *
 * 🔴 왜 계산하나: docs/WALKABILITY.md 2.2 절이 정한 것이다. 그림자는 사진으로
 *    긁을 것이 아니라 **경사를 고도에서 계산한 것과 같은 종류의 계산**이다.
 *      건물 높이 + 태양 고도(날짜·시각) = 그림자 길이·방향
 *
 * 🔴 그림자는 시각에 따라 답이 뒤집힌다. 여름 정오 부산에서 해는 거의 머리 위라
 *    60m 건물도 그림자가 14m 밖에 안 되어 길을 못 덮는다. 오후에 해가 30° 로
 *    내려가면 같은 건물이 100m 넘는 그림자를 만든다. 그래서 구간마다 **하나의
 *    그늘 점수가 아니라 시각대별 점수**를 낸다 — 여행 일정에는 시간표가 있다.
 *
 * 입력
 *   data/raw/pbf/building.ndjson      {id, topic, levels, heightTag, areaM2, ring:[[lat,lon],…]}
 *   data/staged/segment-slope.ndjson  구간 목록 (id·topic·length·p90Slope…)
 *   data/raw/pbf/{road,walk,stairs}.ndjson   구간 id → 실제 선형(geometry)
 *     ↑ segment-slope.ndjson 에는 좌표가 없다. 그래서 원본 추출본에서 id 로 붙인다.
 *
 * 출력
 *   data/staged/segment-shadow.ndjson  구간별 · 시각대별 그늘 비율
 *   data/staged/_shadow-summary.json   요약 (🔴 수치는 여기에만 적는다. 문서에 베끼지 않는다)
 *   data/staged/_shadow-run/_run.json  실행 지문 (mlops/manifest.mjs 의 stamp)
 *
 *   node process/shadow.mjs
 *   node process/shadow.mjs --data=<폴더>   # raw/pbf 와 staged 를 그 아래에서 찾는다 (검증용)
 *
 * 종료 코드
 *   0  정상        1  불변식 위반 (그럴듯한데 틀린 숫자)      2  입력이 없다
 */
import { createReadStream, existsSync } from 'node:fs'
import { createInterface } from 'node:readline'
import { open, mkdir, writeFile } from 'node:fs/promises'
import { join, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
import { stamp } from '../mlops/manifest.mjs'

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..')
const dataArg = process.argv.find(a => a.startsWith('--data='))
const DATA    = dataArg ? dataArg.slice('--data='.length) : join(ROOT, 'data')
const PBF     = join(DATA, 'raw/pbf')
const STAGED  = join(DATA, 'staged')
const BUILDINGS = join(PBF, 'building.ndjson')
const SLOPE     = join(STAGED, 'segment-slope.ndjson')
const OUT_NDJSON  = join(STAGED, 'segment-shadow.ndjson')
const OUT_SUMMARY = join(STAGED, '_shadow-summary.json')
// 🔴 stamp() 는 outDir 에 `_run.json` 을 고정 이름으로 쓴다. staged/ 에는 이미
//    다른 단계(choice-*)가 쓴 _run.json 이 있어서, 거기에 그대로 찍으면 남의
//    실행 지문을 지운다. 그래서 산출물 옆의 전용 하위 폴더에 찍는다.
const RUN_DIR = join(STAGED, '_shadow-run')

// ── 파라미터 ────────────────────────────────────────────────────────────────
const REF_DATE  = { y: 2026, m: 7, d: 15 }   // 한여름. 그늘이 실제로 판단을 가르는 때
const TZ_OFFSET_H = 9                        // KST. 한국은 서머타임이 없다
const SLOT_HOURS  = [9, 12, 15, 17, 18]      // 아래 SLOT_WHY 에 고른 이유가 있다
const SLOT_WHY = {
  9:  '아침 출발 — 해가 낮고 동쪽에 있어 그림자가 서쪽으로 길게 눕는다',
  12: '정오 — 해가 가장 높다. 그림자가 가장 짧아 "건물이 많아도 그늘이 없는" 최악의 시각',
  15: '오후 3시 — 한국 여름의 기온 최고 시각. 그늘 유무가 가장 크게 체감된다',
  17: 'WALKABILITY.md 2.2 가 기준으로 삼은 "해 30°" 시각대 — 같은 건물의 그림자가 정오의 7배가 된다',
  18: '저녁 산책 — 그림자가 가장 길다. 이 시각에도 그늘이 없으면 정말 없는 길이다',
}
// 층당 높이. 🔴 아래 heightOf() 주석이 왜 낮은 쪽인지를 설명한다
const FLOOR_H_M   = 2.8
const SAMPLE_M    = 20     // 구간을 이 간격으로 찍어 각 점이 그늘인지 본다
const RAY_STEP_M  = 10     // 해 쪽으로 광선을 이 간격으로 전진시킨다
const MAX_REACH_M = 400    // 그보다 먼 건물의 그림자는 중간 건물·지형에 이미 먹힌다
const CELL_DEG    = 0.0025 // 건물 격자 색인 한 칸 (위도 약 278m)
const MIN_ALT_DEG = 0.5    // 이보다 낮으면 해가 진 것으로 본다
const REF_LAT = 35.14, REF_LON = 129.03   // 요약표에 쓸 부산 대표 지점 (bbox 중심 부근)

const log = (...a) => console.log(new Date().toISOString().slice(11, 19), ...a)
const rad = d => d * Math.PI / 180
const deg = r => r * 180 / Math.PI
const norm360 = a => ((a % 360) + 360) % 360
const clamp = (v, lo, hi) => Math.min(hi, Math.max(lo, v))

// ── 태양 위치 ───────────────────────────────────────────────────────────────
/**
 * 대기 굴절(atmospheric refraction — 공기가 빛을 휘어 해가 실제보다 높아 보이는 것)
 * 보정. NOAA Solar Calculator 가 쓰는 근사식. 지평선 근처에서만 크다.
 * @returns {number} 더해야 할 각도(도)
 */
function refractionDeg(altDeg) {
  if (altDeg > 85) return 0
  const te = Math.tan(rad(altDeg))
  let arcsec
  if (altDeg > 5) arcsec = 58.1 / te - 0.07 / te ** 3 + 0.000086 / te ** 5
  else if (altDeg > -0.575)
    arcsec = 1735 + altDeg * (-518.2 + altDeg * (103.4 + altDeg * (-12.79 + altDeg * 0.711)))
  else arcsec = -20.772 / te
  return arcsec / 3600
}

/**
 * 태양의 고도(altitude)와 방위(azimuth).
 *
 * 출처: **NOAA Solar Calculator** 의 공식 — Jean Meeus, *Astronomical Algorithms*
 * (2nd ed., 1998) 의 태양 위치 장을 NOAA 가 스프레드시트로 옮긴 그 식이다.
 * 외부 라이브러리를 쓰지 않는다 (process/ 는 네트워크도 의존성도 늘리지 않는다).
 *
 * @param {Date}   utc    UTC 시각
 * @param {number} latDeg 위도(북+)
 * @param {number} lonDeg 경도(동+)
 * @returns {{altDeg:number, azDeg:number, declDeg:number, eqTimeMin:number, hourAngleDeg:number}}
 *   azDeg 는 북 0° 에서 시계방향 (동 90 · 남 180 · 서 270)
 */
export function sunPosition(utc, latDeg, lonDeg) {
  const jd = utc.getTime() / 86400000 + 2440587.5      // 율리우스일
  const t  = (jd - 2451545.0) / 36525                  // J2000 기준 율리우스 세기

  const L0 = norm360(280.46646 + t * (36000.76983 + t * 0.0003032))      // 기하평균황경
  const M  = 357.52911 + t * (35999.05029 - 0.0001537 * t)               // 기하평균근점이각
  const e  = 0.016708634 - t * (0.000042037 + 0.0000001267 * t)          // 궤도 이심률
  const C  = Math.sin(rad(M))     * (1.914602 - t * (0.004817 + 0.000014 * t))
           + Math.sin(rad(2 * M)) * (0.019993 - 0.000101 * t)
           + Math.sin(rad(3 * M)) * 0.000289                             // 중심차
  const trueLong = L0 + C
  const omega    = 125.04 - 1934.136 * t                                  // 달 승교점
  const appLong  = trueLong - 0.00569 - 0.00478 * Math.sin(rad(omega))    // 겉보기황경
  const eps0 = 23 + (26 + (21.448 - t * (46.815 + t * (0.00059 - t * 0.001813))) / 60) / 60
  const eps  = eps0 + 0.00256 * Math.cos(rad(omega))                      // 황도경사
  const decl = Math.asin(Math.sin(rad(eps)) * Math.sin(rad(appLong)))     // 적위(rad)

  const y = Math.tan(rad(eps / 2)) ** 2
  const eqTime = 4 * deg(                                                 // 균시차(분)
      y * Math.sin(2 * rad(L0))
    - 2 * e * Math.sin(rad(M))
    + 4 * e * y * Math.sin(rad(M)) * Math.cos(2 * rad(L0))
    - 0.5 * y * y * Math.sin(4 * rad(L0))
    - 1.25 * e * e * Math.sin(2 * rad(M)))

  const utcMin = utc.getUTCHours() * 60 + utc.getUTCMinutes() + utc.getUTCSeconds() / 60
  const tst = (((utcMin + eqTime + 4 * lonDeg) % 1440) + 1440) % 1440      // 진태양시(분)
  const ha  = tst / 4 - 180                                               // 시각(hour angle), 음수=오전

  const lat  = rad(latDeg)
  const cosZ = Math.sin(lat) * Math.sin(decl) + Math.cos(lat) * Math.cos(decl) * Math.cos(rad(ha))
  const z    = Math.acos(clamp(cosZ, -1, 1))                              // 천정각
  const altDeg = 90 - deg(z) + refractionDeg(90 - deg(z))

  let azDeg
  const den = Math.cos(lat) * Math.sin(z)
  if (Math.abs(den) < 1e-9) azDeg = ha > 0 ? 180 : 0
  else {
    const a = deg(Math.acos(clamp((Math.sin(lat) * Math.cos(z) - Math.sin(decl)) / den, -1, 1)))
    azDeg = ha > 0 ? norm360(a + 180) : norm360(540 - a)
  }
  return { altDeg, azDeg, declDeg: deg(decl), eqTimeMin: eqTime, hourAngleDeg: ha }
}

/** 그림자 길이 = 높이 / tan(태양고도). 해가 지면 무한대 = 온통 그늘(=밤)이다. */
export const shadowLengthM = (heightM, altDeg) =>
  altDeg <= MIN_ALT_DEG ? Infinity : heightM / Math.tan(rad(altDeg))

/** KST 벽시계 시각 → UTC Date */
const kst = (hour, minute = 0) =>
  new Date(Date.UTC(REF_DATE.y, REF_DATE.m - 1, REF_DATE.d, hour - TZ_OFFSET_H, minute))

// ── 건물 높이 ───────────────────────────────────────────────────────────────
/**
 * 건물 한 채의 높이(m). 못 정하면 null — **없는 값을 지어내지 않는다.**
 *
 * 1) `heightTag`(OSM 의 height 태그, 미터)가 있으면 그것이 우선이다. 실측값이다.
 * 2) 없고 `levels`(building:levels, 층수)만 있으면 층당 높이를 곱한다.
 *
 * 🔴 층당 높이를 2.8m 로 고정한 이유.
 *    WALKABILITY.md 2.2 는 "한국 아파트 층당 약 2.8m, 상가 약 4m" 라고 적었다.
 *    그런데 이 단계의 입력 스키마에는 **용도를 가를 태그가 아예 없다**
 *    (id·levels·heightTag·areaM2·ring 뿐이다). 면적으로 "넓고 낮으면 상가" 라고
 *    추정할 수는 있지만, 그 추정이 틀리면 높이가 **위로** 튀고 그림자가 커진다.
 *
 *    두 방향의 오류는 값이 다르다.
 *      과대평가 → "여기는 그늘이다" 라고 잘못 말한다 → 사용자를 뙤약볕에 보낸다
 *      과소평가 → "그늘이 없다" 고 잘못 말한다 → 사용자가 손해를 안 본다
 *    그래서 가를 수 없을 때는 **낮은 쪽(2.8m)** 을 쓴다. 비대칭한 손실에는
 *    비대칭하게 대응하는 것이 맞다. 용도 태그가 입력에 생기면 그때 나눈다.
 */
function heightOf(b) {
  const ht = b.heightTag
  if (typeof ht === 'number' && Number.isFinite(ht) && ht > 0 && ht < 500) return ht
  const lv = b.levels
  if (typeof lv === 'number' && Number.isFinite(lv) && lv >= 1 && lv <= 150) return lv * FLOOR_H_M
  return null
}

// ── 건물 격자 색인 ──────────────────────────────────────────────────────────
const cellKey = (lat, lon) =>
  (Math.floor(lat / CELL_DEG) + 40000) * 200000 + (Math.floor(lon / CELL_DEG) + 80000)

/** ring 정규화: [[lat,lon],…] → 유효하면 그대로, 아니면 null */
function normalizeRing(ring) {
  if (!Array.isArray(ring) || ring.length < 3) return null
  for (const p of ring) {
    if (!Array.isArray(p) || p.length < 2) return null
    if (!Number.isFinite(p[0]) || !Number.isFinite(p[1])) return null
  }
  return ring
}

/** 점이 다각형 안인가 — 표준 ray casting. x=lon, y=lat */
function pointInRing(ring, lat, lon) {
  let inside = false
  for (let i = 0, j = ring.length - 1; i < ring.length; j = i++) {
    const yi = ring[i][0], xi = ring[i][1], yj = ring[j][0], xj = ring[j][1]
    if ((yi > lat) !== (yj > lat) && lon < ((xj - xi) * (lat - yi)) / (yj - yi) + xi) inside = !inside
  }
  return inside
}

async function loadBuildings(path) {
  const rings = [], heights = []
  const cellMaxH = new Map(), cellList = new Map()
  const stat = { lines: 0, byHeightTag: 0, byLevels: 0, noHeight: 0, badGeom: 0, badJson: 0 }
  let hmax = 0
  const hs = []

  const rl = createInterface({ input: createReadStream(path), crlfDelay: Infinity })
  for await (const line of rl) {
    if (!line.trim()) continue
    stat.lines++
    let b
    try { b = JSON.parse(line) } catch { stat.badJson++; continue }
    const h = heightOf(b)
    if (h === null) { stat.noHeight++; continue }
    const ring = normalizeRing(b.ring)
    if (!ring) { stat.badGeom++; continue }

    let s = 90, n = -90, w = 180, e = -180
    for (const [la, lo] of ring) {
      if (la < s) s = la; if (la > n) n = la
      if (lo < w) w = lo; if (lo > e) e = lo
    }
    // 건물 하나가 5km 를 넘으면 그건 건물이 아니라 깨진 도형이다
    if (n - s > 0.05 || e - w > 0.05) { stat.badGeom++; continue }

    const idx = rings.length
    rings.push(ring); heights.push(h); hs.push(h)
    if (h > hmax) hmax = h
    if (typeof b.heightTag === 'number' && Number.isFinite(b.heightTag) && b.heightTag > 0 && b.heightTag < 500)
      stat.byHeightTag++
    else stat.byLevels++

    for (let la = Math.floor(s / CELL_DEG); la <= Math.floor(n / CELL_DEG); la++)
      for (let lo = Math.floor(w / CELL_DEG); lo <= Math.floor(e / CELL_DEG); lo++) {
        const k = (la + 40000) * 200000 + (lo + 80000)
        if ((cellMaxH.get(k) ?? 0) < h) cellMaxH.set(k, h)
        let list = cellList.get(k)
        if (!list) cellList.set(k, (list = []))
        list.push(idx)
      }
  }
  hs.sort((a, b) => a - b)
  return { rings, heights, cellMaxH, cellList, hmax, stat, heightsSorted: hs }
}

// ── 그늘 판정 ───────────────────────────────────────────────────────────────
/**
 * 점 (lat,lon) 이 그늘인가.
 *
 * 해 쪽(방위 azDeg)으로 광선을 쏘아 전진시킨다. 거리 s 에서 광선의 높이는
 * s·tan(고도) 다. 그 자리에 그보다 높은 건물이 있으면 해가 가려진 것이다.
 * (그림자를 건물에서 앞으로 그리는 것과 같은 계산을 반대로 푼 것뿐이다 —
 *  그림자 길이 = 높이/tan(고도), 방향은 태양 방위의 반대편.)
 */
function isShaded(lat, lon, altDeg, azDeg, B) {
  if (altDeg <= MIN_ALT_DEG) return true            // 해가 졌다
  const tanAlt = Math.tan(rad(altDeg))
  const reach = Math.min(MAX_REACH_M, B.hmax / tanAlt)
  if (!(reach > 0)) return false
  const dLat = 1 / 111320
  const dLon = 1 / (111320 * Math.cos(rad(lat)))
  const nComp = Math.cos(rad(azDeg)), eComp = Math.sin(rad(azDeg))

  for (let s = RAY_STEP_M; s <= reach; s += RAY_STEP_M) {
    const needH = s * tanAlt
    if (needH > B.hmax) break
    const la = lat + dLat * s * nComp
    const lo = lon + dLon * s * eComp
    const k = cellKey(la, lo)
    const mh = B.cellMaxH.get(k)
    if (mh === undefined || mh < needH) continue
    for (const bi of B.cellList.get(k)) {
      if (B.heights[bi] < needH) continue
      if (pointInRing(B.rings[bi], la, lo)) return true
    }
  }
  return false
}

// ── 구간 리샘플 ─────────────────────────────────────────────────────────────
const R_EARTH = 6371000
function haversine(a, b) {
  const dLat = rad(b.lat - a.lat), dLon = rad(b.lon - a.lon)
  const h = Math.sin(dLat / 2) ** 2 +
            Math.cos(rad(a.lat)) * Math.cos(rad(b.lat)) * Math.sin(dLon / 2) ** 2
  return 2 * R_EARTH * Math.asin(Math.sqrt(h))
}
/** 선형을 stepM 간격으로 찍는다. 양 끝점은 항상 포함한다. */
function resample(geom, stepM) {
  const pts = [{ lat: geom[0].lat, lon: geom[0].lon }]
  let acc = 0
  for (let i = 1; i < geom.length; i++) {
    const a = geom[i - 1], b = geom[i], d = haversine(a, b)
    if (!(d > 0)) continue
    for (let t = stepM - (acc % stepM); t < d; t += stepM)
      pts.push({ lat: a.lat + (b.lat - a.lat) * (t / d), lon: a.lon + (b.lon - a.lon) * (t / d) })
    acc += d
    pts.push({ lat: b.lat, lon: b.lon })
  }
  return { pts, length: acc }
}

const pct = (sorted, p) =>
  sorted.length ? sorted[clamp(Math.floor(sorted.length * p), 0, sorted.length - 1)] : null

// ── 불변식 ──────────────────────────────────────────────────────────────────
/**
 * 🔴 이 검사가 없으면 "그럴듯한데 틀린 숫자" 가 그대로 통과한다 (CLAUDE.md 7절).
 *    WALKABILITY.md 2.2 의 실측 기준 두 개를 코드가 직접 재현하는지 본다:
 *      여름 정오 부산 해 ≈ 77°, 그때 60m 건물 그림자 ≈ 14m
 *      해가 ≈ 30° 로 내려가면 같은 건물 그림자 ≈ 104m
 */
function checkInvariants() {
  const out = []
  const add = (name, ok, got, want) => out.push({ name, ok, got, want })

  // (1) 그림자 기하 — 태양 위치와 무관한 순수 계산
  const s77 = shadowLengthM(60, 77)
  const s30 = shadowLengthM(60, 30)
  add('60m 건물 · 해 77° → 그림자 ≈ 14m', Math.abs(s77 - 14) <= 1.0, +s77.toFixed(2), '14 ± 1 m')
  add('60m 건물 · 해 30° → 그림자 ≈ 104m', Math.abs(s30 - 104) <= 5.0, +s30.toFixed(2), '104 ± 5 m')

  // (2) 태양 위치 구현 — 남중고도가 실제로 77° 근처로 나오는가
  let noon = { altDeg: -99, azDeg: 0 }, noonMin = 0
  for (let m = 10 * 60; m <= 14 * 60; m++) {
    const p = sunPosition(kst(0, m), REF_LAT, REF_LON)
    if (p.altDeg > noon.altDeg) { noon = p; noonMin = m }
  }
  add(`${REF_DATE.y}-${String(REF_DATE.m).padStart(2, '0')}-${String(REF_DATE.d).padStart(2, '0')} 부산 남중고도 ≈ 77°`,
      Math.abs(noon.altDeg - 77) <= 2.0, +noon.altDeg.toFixed(2), '77 ± 2 °')
  add('남중 방위 ≈ 남쪽(180°)', Math.abs(noon.azDeg - 180) <= 5.0, +noon.azDeg.toFixed(2), '180 ± 5 °')
  add('남중 시각 = 12시 근처(KST)', Math.abs(noonMin - 12 * 60) <= 60,
      `${String(Math.floor(noonMin / 60)).padStart(2, '0')}:${String(noonMin % 60).padStart(2, '0')}`, '11:00~13:00')

  // 오후에 고도가 30° 를 지나는 시각을 찾아, 그때 60m 그림자가 104m 근처인가
  let best = null
  for (let m = noonMin + 1; m <= 21 * 60; m++) {
    const p = sunPosition(kst(0, m), REF_LAT, REF_LON)
    if (best === null || Math.abs(p.altDeg - 30) < Math.abs(best.p.altDeg - 30)) best = { m, p }
  }
  add('오후에 해가 30° 를 지난다', best !== null && Math.abs(best.p.altDeg - 30) <= 0.5,
      best ? +best.p.altDeg.toFixed(2) : null, '30 ± 0.5 °')
  const sBest = best ? shadowLengthM(60, best.p.altDeg) : null
  add('그 시각 60m 건물 그림자 ≈ 104m', sBest !== null && Math.abs(sBest - 104) <= 5.0,
      sBest === null ? null : +sBest.toFixed(2), '104 ± 5 m')

  // (3) 방위 부호 — 여기가 뒤집히면 그림자가 통째로 반대쪽에 생긴다.
  //     숫자는 여전히 그럴듯해서 눈으로는 못 잡는다.
  const am = sunPosition(kst(9), REF_LAT, REF_LON)
  const pm = sunPosition(kst(17), REF_LAT, REF_LON)
  add('오전 09시 해는 동쪽(방위 < 180°)', am.azDeg < 180, +am.azDeg.toFixed(1), '< 180 °')
  add('오후 17시 해는 서쪽(방위 > 180°)', pm.azDeg > 180, +pm.azDeg.toFixed(1), '> 180 °')

  return {
    checks: out,
    noon: { atKst: `${String(Math.floor(noonMin / 60)).padStart(2, '0')}:${String(noonMin % 60).padStart(2, '0')}`,
            altDeg: +noon.altDeg.toFixed(2), azDeg: +noon.azDeg.toFixed(2) },
    alt30: best && { atKst: `${String(Math.floor(best.m / 60)).padStart(2, '0')}:${String(best.m % 60).padStart(2, '0')}`,
                     altDeg: +best.p.altDeg.toFixed(2), shadow60mM: +sBest.toFixed(1) },
  }
}

// ── 본체 ────────────────────────────────────────────────────────────────────
async function main() {
  const t0 = Date.now()

  // 불변식을 **입력보다 먼저** 본다. 데이터가 없는 날에도 계산은 검증된다.
  const inv = checkInvariants()
  console.log('불변식 — WALKABILITY.md 2.2 실측 기준 대조')
  for (const c of inv.checks)
    console.log(`  ${c.ok ? '✅' : '❌'} ${c.name}  → ${c.got} (기대 ${c.want})`)
  const failed = inv.checks.filter(c => !c.ok)
  if (failed.length) {
    console.error(`\n🔴 불변식 ${failed.length}건 위반. 계산식이 틀렸다 — 숫자를 내보내지 않는다.`)
    process.exit(1)
  }
  console.log('')

  // 입력 확인 — 없으면 조용히 통과하지 않는다
  const missing = []
  if (!existsSync(BUILDINGS)) missing.push(BUILDINGS)
  if (!existsSync(SLOPE)) missing.push(SLOPE)
  const geomFiles = ['road', 'walk', 'stairs'].map(t => join(PBF, `${t}.ndjson`)).filter(existsSync)
  if (!geomFiles.length) missing.push(join(PBF, 'road.ndjson'))
  if (missing.length) {
    console.error('🔴 입력이 없습니다. 그늘을 계산할 수 없습니다.')
    for (const m of missing) console.error(`   없음: ${m}`)
    console.error('   building.ndjson  ← PBF 추출 (collect 단계에서 건물 topic 을 뽑아야 한다)')
    console.error('   segment-slope.ndjson ← npm run slope')
    process.exit(2)
  }

  // 1) 건물
  log('건물 읽는 중…')
  const B = await loadBuildings(BUILDINGS)
  log(`건물 ${B.stat.lines.toLocaleString()}줄 · 높이 있음 ${B.rings.length.toLocaleString()} ` +
      `(height 태그 ${B.stat.byHeightTag.toLocaleString()} / 층수 환산 ${B.stat.byLevels.toLocaleString()}) ` +
      `· 최고 ${B.hmax.toFixed(1)}m`)
  if (!B.rings.length) {
    console.error('🔴 높이를 정할 수 있는 건물이 하나도 없습니다. 입력 스키마가 어긋났을 가능성이 큽니다.')
    console.error('   기대: {"id":…,"topic":"building","levels":…,"heightTag":…,"areaM2":…,"ring":[[lat,lon],…]}')
    process.exit(1)
  }

  // 2) 구간 목록 (좌표는 없다 — id·길이·분류만)
  const segs = new Map()
  {
    const rl = createInterface({ input: createReadStream(SLOPE), crlfDelay: Infinity })
    for await (const line of rl) {
      if (!line.trim()) continue
      const s = JSON.parse(line)
      segs.set(s.id, { topic: s.topic ?? null, highway: s.highway ?? null, name: s.name ?? null,
                       length: s.length ?? null, p90Slope: s.p90Slope ?? null })
    }
  }
  log(`구간 ${segs.size.toLocaleString()}개 (${SLOPE})`)
  if (!segs.size) { console.error('🔴 구간이 비어 있습니다.'); process.exit(1) }

  // 3) 원본 추출본을 흘리며 좌표를 붙이고 바로 계산해서 바로 쓴다 (전역이라 스트리밍)
  await mkdir(STAGED, { recursive: true })
  const fh = await open(OUT_NDJSON, 'w')
  const w = fh.createWriteStream()

  const ratiosByHour = new Map(SLOT_HOURS.map(h => [h, []]))
  let done = 0, samplesTotal = 0, tooShort = 0, noGeom = 0
  let minRatio = 1, maxRatio = 0

  for (const gf of geomFiles) {
    const rl = createInterface({ input: createReadStream(gf), crlfDelay: Infinity })
    for await (const line of rl) {
      if (!line.trim()) continue
      const el = JSON.parse(line)
      const meta = segs.get(el.id)
      if (!meta) continue
      if (!el.geometry || el.geometry.length < 2) { noGeom++; continue }
      const { pts, length } = resample(el.geometry, SAMPLE_M)
      if (length < 5) { tooShort++; continue }
      samplesTotal += pts.length

      // 태양 위치는 구간의 첫 점에서 구한다. 부산은 위도 0.5°·경도 0.6° 폭이라
      // 대표점 하나로 뭉개면 남중고도가 최대 0.5°, 남중시각이 2분 어긋난다.
      const lat0 = pts[0].lat, lon0 = pts[0].lon
      const slots = []
      for (const hour of SLOT_HOURS) {
        const sun = sunPosition(kst(hour), lat0, lon0)
        let shaded = 0
        if (sun.altDeg <= MIN_ALT_DEG) shaded = pts.length
        else for (const p of pts) if (isShaded(p.lat, p.lon, sun.altDeg, sun.azDeg, B)) shaded++
        const ratio = shaded / pts.length
        ratiosByHour.get(hour).push(ratio)
        if (ratio < minRatio) minRatio = ratio
        if (ratio > maxRatio) maxRatio = ratio
        slots.push({
          hour, sunAltDeg: +sun.altDeg.toFixed(2), sunAzDeg: +sun.azDeg.toFixed(1),
          sunUp: sun.altDeg > MIN_ALT_DEG, shadowRatio: +ratio.toFixed(3),
        })
      }
      const rs = slots.map(s => s.shadowRatio)
      const worst = slots[rs.indexOf(Math.min(...rs))]
      const best  = slots[rs.indexOf(Math.max(...rs))]

      w.write(JSON.stringify({
        id: el.id, topic: meta.topic, highway: meta.highway, name: meta.name,
        length: meta.length ?? +length.toFixed(1),
        date: `${REF_DATE.y}-${String(REF_DATE.m).padStart(2, '0')}-${String(REF_DATE.d).padStart(2, '0')}`,
        samples: pts.length,
        slots,
        meanShadowRatio: +(rs.reduce((a, b) => a + b, 0) / rs.length).toFixed(3),
        // 🔴 그늘은 "높을수록 좋은" 지표다. 그래서 구간 판정의 보수적인 쪽은
        //    최댓값도 p90 도 아니라 **가장 그늘이 없는 시각** 이다.
        worstHour: worst.hour, worstShadowRatio: worst.shadowRatio,
        bestHour: best.hour, bestShadowRatio: best.shadowRatio,
      }) + '\n')

      if (++done % 5000 === 0) log(`  ${done.toLocaleString()} / ${segs.size.toLocaleString()} 구간`)
    }
  }
  await new Promise(r => w.end(r))
  await fh.close()
  const elapsed = (Date.now() - t0) / 1000

  // 4) 출력 불변식 — 여기서도 조용히 넘어가지 않는다
  const outChecks = []
  outChecks.push({ name: '계산된 구간 > 0', ok: done > 0, got: done, want: '> 0' })
  outChecks.push({ name: '그늘 비율이 0~1 범위', ok: minRatio >= 0 && maxRatio <= 1,
                   got: `${minRatio.toFixed(3)} ~ ${maxRatio.toFixed(3)}`, want: '0 ~ 1' })
  for (const h of SLOT_HOURS)
    outChecks.push({ name: `${h}시 표본 수 = 구간 수`, ok: ratiosByHour.get(h).length === done,
                     got: ratiosByHour.get(h).length, want: done })
  const outFailed = outChecks.filter(c => !c.ok)
  if (outFailed.length) {
    console.error('\n🔴 출력 불변식 위반:')
    for (const c of outFailed) console.error(`   ❌ ${c.name} → ${c.got} (기대 ${c.want})`)
    process.exit(1)
  }

  // 5) 요약 — 🔴 수치는 여기에만 적는다
  const byHour = {}
  for (const h of SLOT_HOURS) {
    const arr = ratiosByHour.get(h).slice().sort((a, b) => a - b)
    const sun = sunPosition(kst(h), REF_LAT, REF_LON)
    byHour[h] = {
      sunAltDeg: +sun.altDeg.toFixed(2), sunAzDeg: +sun.azDeg.toFixed(1),
      shadowOf60mBuildingM: sun.altDeg > MIN_ALT_DEG ? +shadowLengthM(60, sun.altDeg).toFixed(1) : null,
      why: SLOT_WHY[h],
      // p90 = 구간 분포의 대표값 (CLAUDE.md 7절 — 최댓값은 표본 하나에 끌려간다).
      // p10 도 같이 낸다: 그늘은 클수록 좋은 지표라 보수적인 쪽이 아래쪽이다.
      p10ShadowRatio: +pct(arr, 0.10).toFixed(3),
      p50ShadowRatio: +pct(arr, 0.50).toFixed(3),
      p90ShadowRatio: +pct(arr, 0.90).toFixed(3),
      meanShadowRatio: +(arr.reduce((a, b) => a + b, 0) / arr.length).toFixed(3),
      segmentsOverHalfShaded: arr.filter(v => v >= 0.5).length,
    }
  }
  const hsorted = B.heightsSorted
  const summary = {
    at: new Date().toISOString(),
    date: `${REF_DATE.y}-${String(REF_DATE.m).padStart(2, '0')}-${String(REF_DATE.d).padStart(2, '0')}`,
    timezone: `KST (UTC+${TZ_OFFSET_H}, 서머타임 없음)`,
    solarModel: 'NOAA Solar Calculator (Jean Meeus, Astronomical Algorithms 2nd ed.) — 직접 구현, 외부 의존성 없음',
    slotHours: SLOT_HOURS,
    slotRationale:
      '한여름(7월 15일) 하루를 다섯 시각으로 자른다. 그늘은 하루 안에서 답이 뒤집히는 값이라 ' +
      '하나의 점수로 뭉개면 틀린다. 12시는 그림자가 가장 짧은 최악, 15시는 기온 최고, ' +
      '17시는 WALKABILITY.md 2.2 가 기준으로 삼은 "해 30°" 시각대, 9시와 18시는 양 끝이다. ' +
      '여행 일정에는 시간표가 있으므로 이 성질이 오히려 잘 맞는다.',
    params: {
      floorHeightM: FLOOR_H_M,
      floorHeightNote:
        '입력 스키마에 건물 용도 태그가 없어 아파트(2.8m)와 상가(4m)를 가를 수 없다. ' +
        '가를 수 없으면 낮은 쪽을 쓴다 — 그림자를 과대평가하면 "그늘이 있다" 고 잘못 말해 ' +
        '사용자를 뙤약볕에 보내지만, 과소평가는 그 반대라 손해가 작다.',
      sampleM: SAMPLE_M, rayStepM: RAY_STEP_M, maxReachM: MAX_REACH_M, cellDeg: CELL_DEG,
      minAltDeg: MIN_ALT_DEG,
    },
    buildings: {
      lines: B.stat.lines,
      usedForShadow: B.rings.length,
      fromHeightTag: B.stat.byHeightTag,
      fromLevels: B.stat.byLevels,
      noHeight: B.stat.noHeight,
      badGeometry: B.stat.badGeom,
      badJson: B.stat.badJson,
      maxHeightM: +B.hmax.toFixed(1),
      p50HeightM: +pct(hsorted, 0.5).toFixed(1),
      p90HeightM: +pct(hsorted, 0.9).toFixed(1),
    },
    segments: {
      inSlopeFile: segs.size,
      computed: done,
      geometryMissing: segs.size - done,
      tooShort, noGeometryField: noGeom,
      samples: samplesTotal,
      geometrySources: geomFiles.map(f => f.split(/[\\/]/).pop()),
    },
    byHour,
    representativeStat:
      'p90ShadowRatio (구간 분포의 대표값 — 최댓값은 표본 하나에 끌려간다, CLAUDE.md 7절). ' +
      '다만 그늘은 클수록 좋은 지표라 보수적인 쪽은 p10 이다. 구간 단위 판정에는 worstShadowRatio 를 쓴다.',
    invariants: inv,
    elapsedSec: +elapsed.toFixed(2),
    caveat:
      '건물만 세었다. 가로수 그늘(WALKABILITY.md 2.1)·지형 그늘·고가도로·아케이드는 안 들어 있다. ' +
      '층수가 없는 건물은 아예 제외했다 — 높이를 지어내지 않는다. 따라서 이 값은 실제 그늘의 하한이다.',
  }
  await writeFile(OUT_SUMMARY, JSON.stringify(summary, null, 2))

  stamp(RUN_DIR, {
    step: 'shadow',
    inputs: [BUILDINGS, SLOPE, ...geomFiles],
    params: { date: summary.date, slotHours: SLOT_HOURS, floorHeightM: FLOOR_H_M,
              sampleM: SAMPLE_M, rayStepM: RAY_STEP_M, maxReachM: MAX_REACH_M },
    result: { segments: done, buildingsUsed: B.rings.length, samples: samplesTotal,
              invariantFailures: 0 },
  })

  log(`구간 ${done.toLocaleString()}개 · 표본 ${samplesTotal.toLocaleString()}개 · ${elapsed.toFixed(1)}초`)
  console.log(`\n🌳 시각대별 그늘 (${summary.date}, 부산)`)
  console.log('  시각  해고도   60m건물 그림자   p10    p50    p90    절반이상 그늘인 구간')
  for (const h of SLOT_HOURS) {
    const r = byHour[h]
    console.log(`  ${String(h).padStart(2)}시  ${String(r.sunAltDeg).padStart(6)}°  ` +
                `${String(r.shadowOf60mBuildingM).padStart(9)} m  ` +
                `${r.p10ShadowRatio.toFixed(2).padStart(5)}  ${r.p50ShadowRatio.toFixed(2).padStart(5)}  ` +
                `${r.p90ShadowRatio.toFixed(2).padStart(5)}  ${String(r.segmentsOverHalfShaded).padStart(8)}`)
  }
  console.log(`\n  → ${OUT_NDJSON}`)
  console.log(`  → ${OUT_SUMMARY}`)
  console.log(`  → ${join(RUN_DIR, '_run.json')}`)
}

main().catch(e => { console.error('치명:', e); process.exit(1) })
