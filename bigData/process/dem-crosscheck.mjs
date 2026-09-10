#!/usr/bin/env node
/**
 * 고도 교차검증 — terrarium(지금 쓰는 것) × Copernicus GLO-30
 *
 * 🔴 무엇을 하나: 지금 쓰는 고도 자료에 **실제로 없는 혹**이 박혀 있다.
 *    화면에서 평지에 능선이 서고, process/slope.mjs 의 경사까지 오염된다.
 *    그 혹을 눈이 아니라 **다른 자료로** 잡아내서 목록으로 만든다.
 *
 *    만드는 것은 **대조 결과와 혹 마스크**다. 경사 수준을 다시 뽑거나 문항을
 *    고치는 것은 여기가 아니다 — 이 파일은 아무것도 고치지 않고 목록만 낸다.
 *
 * 🔴 왜 GLO-30 이 심판인가: terrarium 의 원본은 SRTM 2000-02 다. 부산의 매립지와
 *    모래해안은 그 뒤로 모양이 바뀌었고, 원본 자체에 보간 흠이 있다.
 *    GLO-30 은 2010-2015 TanDEM-X 라 **출처가 아예 다르다.** 같은 흠을 공유하지
 *    않으므로 둘이 어긋나는 자리가 곧 의심 자리다.
 *
 *    한 점으로 보면 이렇다 — 마린시티 앞 (35.15395, 129.14485):
 *      terrarium 51.5m · GLO-30 13.9m · 주변 terrarium 0~7m
 *    장산을 재면 둘 다 626~627m 로 맞는다. 산은 맞고 혹만 없다.
 *
 * ── 좌표계와 격자를 어떻게 맞췄나 (재표본) ────────────────────────────────
 *
 * 두 자료는 격자가 아예 다르다.
 *   terrarium  웹메르카토르 z15 타일 256x256. 픽셀이 위도마다 크기가 다르고
 *              부산에서 약 3.9m. 원본은 SRTM ~30m 를 보간한 것이라
 *              **격자가 촘촘하다고 정보가 촘촘한 게 아니다.**
 *   GLO-30     경위도(WGS84) 1초 격자. 부산에서 위도 방향 30.8m · 경도 방향 25.3m.
 *
 * 🔴 **GLO-30 격자를 공통 격자로 삼는다.** terrarium 을 거기에 맞춘다.
 *    반대로 하면 안 된다 — 3.9m 격자로 GLO-30 을 늘리면 없는 해상도를 지어내는
 *    것이고, 그러면 "혹이 있다/없다" 를 보간이 만들어낸 값으로 판정하게 된다.
 *    거친 쪽에 맞추면 양쪽 다 실제로 가진 정보만 쓴다.
 *
 *    공통 격자는 1초 정수 배수에 딱 맞춘다. 그래서 **GLO-30 은 보간 없이
 *    픽셀을 그대로 읽고**(오차 0), terrarium 만 겹선형(bilinear — 이웃 네 픽셀을
 *    거리비로 섞는 것)으로 뽑는다. 최근접이 아니라 겹선형인 이유는, 3.9m 격자에서
 *    최근접을 쓰면 30m 짜리 칸 하나를 대표하는 값이 **그 칸 안에서 어디를
 *    찍었느냐**에 흔들리기 때문이다. 혹의 꼭대기만 찍으면 혹이 커 보인다.
 *
 * ── 판정을 무엇으로 하나 ──────────────────────────────────────────────────
 *
 * 🔴 **높이를 그대로 빼면 안 된다.** 두 자료는 기준면도 다르고(EGM96 대 EGM2008)
 *    GLO-30 은 DSM(**땅이 아니라 땅 위에 있는 것의 꼭대기** — 건물·나무가 들어간다)
 *    이라 도심에서는 원래 높다. 높이차만 보면 진짜 언덕도 걸린다.
 *
 *    그래서 **혹의 솟은 정도(prominence)** 를 비교한다.
 *      솟음 = 그 자리의 높이 - 주변 반경 안의 가장 낮은 곳
 *    이걸 두 자료에서 **같은 창(window)** 으로 재고 비율을 본다.
 *      남은비율 = GLO-30 솟음 / terrarium 솟음
 *    진짜 언덕은 둘 다 솟아 있으니 비율이 1 근처다. 가짜 혹은 terrarium 에만
 *    있으니 비율이 0 근처다. 기준면 차이와 DSM 편향은 **빼기에서 사라진다** —
 *    솟음은 그 자리와 주변의 차이라, 자료 전체가 통째로 올라가 있어도 안 변한다.
 *
 * 🔴 이 판정이 진짜 언덕을 안 지우는지 아래 CHECKPOINTS 일곱 곳으로 매번 검산한다.
 *    셋은 진짜(REAL 이어야 한다), 넷은 가짜(FAKE 여야 한다). 하나라도 어긋나면
 *    **종료 코드가 1 이 된다** — 기준이 틀렸다는 뜻이므로 조용히 넘기지 않는다.
 *
 *   node process/dem-crosscheck.mjs
 *   node process/dem-crosscheck.mjs --quiet    진행 로그를 줄인다
 */
import { readFileSync, existsSync, readdirSync } from 'node:fs'
import { writeFile, mkdir } from 'node:fs/promises'
import { join, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
import { decodePNG, terrariumToElevation } from './png.mjs'
import { openGeoTIFF, ELEV_MIN, ELEV_MAX } from './geotiff.mjs'

const ROOT  = join(dirname(fileURLToPath(import.meta.url)), '..')
const DEM   = join(ROOT, 'data/raw/dem')            // terrarium z15 png
const GLO   = join(ROOT, 'data/raw/dem-glo30')      // Copernicus GLO-30 COG
const OUT   = join(ROOT, 'data/staged')
const QUIET = process.argv.includes('--quiet')

const ZOOM = 15, TILE = 256, ARC = 3600             // ARC = 1도당 격자 수 (1초)

// ── 판정 상수. 왜 이 값인지는 아래 각주에 ─────────────────────────────────
//
// BASE_R  주변을 어디까지 볼 것인가 (공통 격자 칸 수). 10칸 ≈ 위도 308m·경도 253m.
//         혹보다 넓어야 혹이 자기 바닥을 자기가 정하는 일이 안 생긴다.
//         부산의 가짜 혹은 대부분 300m 이하라 10칸이면 바깥 땅에 닿는다.
// PROM_MIN 이만큼도 안 솟았으면 애초에 혹이 아니다. 화면에 능선으로 보이는
//         최소치를 12m 로 잡았다 — SRTM 의 수직 오차가 ±5m 라 그 두 배 위다.
// DIFF_MIN 높이차가 이보다 작으면 두 자료가 사실상 같다고 본다.
// KEEP_FAKE GLO-30 에 이만큼도 안 남았으면 그 혹은 GLO-30 에 없는 것이다.
// KEEP_REAL 이만큼 남았으면 두 자료가 같은 지형을 보고 있다.
const BASE_R    = 10
const PROM_MIN  = 12
const DIFF_MIN  = 15
const KEEP_FAKE = 0.35
const KEEP_REAL = 0.60

// 🔴 **혹(bump)** 과 **지형(landform)** 을 가르는 폭. 30m 격자에서 600m 는 20칸이다.
//    이보다 넓으면 그건 혹이 아니라 산이나 언덕이다 — 부산의 산은 킬로미터 단위고,
//    가짜 혹은 실측해 보면 76~600m 에 몰려 있다 (마린시티 93m · 미음산단 124m).
//
//    🔴 이 값으로 **거르지는 않는다.** 넓은 것도 전부 판정해서 파일에 넣고
//    `narrow` 표시만 붙인다. 거르면 폭이 넓은 가짜(실제로 있다 — 1km 짜리도
//    나온다)가 조용히 사라지고, 사라진 것은 아무도 못 찾는다.
//    혹 마스크로 쓸 때는 `verdict === 'FAKE'` 로 거르면 되고, 진단이 세던
//    "혹 후보" 와 견주려면 `narrow` 까지 보면 된다.
const WIDTH_BUMP = 600

const log = (...a) => { if (!QUIET) console.log(new Date().toISOString().slice(11, 19), ...a) }
const rad = d => d * Math.PI / 180

// ── terrarium: 웹메르카토르 z15 전역 픽셀 좌표 ────────────────────────────
const gx = lon => (lon + 180) / 360 * 2 ** ZOOM * TILE
const gy = lat => { const r = rad(lat)
  return (1 - Math.log(Math.tan(r) + 1 / Math.cos(r)) / Math.PI) / 2 * 2 ** ZOOM * TILE }

/**
 * 🔴 검산할 일곱 곳. 진단이 실측으로 확인해 둔 것이다.
 *    셋은 진짜 언덕이라 REAL, 넷은 가짜 혹이라 FAKE 로 나와야 한다.
 *
 * 🔴 **좌표의 출처를 정직하게 적어 둔다.** 진단이 좌표까지 준 것은 마린시티
 *    하나뿐이고, 나머지 여섯은 진단이 **이름과 높이만** 남겼다. 진단이 만든
 *    후보 목록 자체는 data/ 아래(커밋 안 되는 자리)에 있어서 이 작업에는
 *    남아 있지 않았다. 그래서 이름으로 자리를 찾고, 그 반경 안에서 코드가
 *    terrarium 의 가장 높은 칸을 직접 고른다.
 *
 * 그래서 `terrarium` 칸에는 **진단이 적어 둔 높이**를 그대로 두고, 코드가 잰
 * 높이를 결과표에 나란히 찍는다. 둘이 어긋나면 **다른 지형을 본 것**이므로
 * 눈에 보여야 한다. 실제로 어긋나는 것이 있다 — 아래 note 에 적었다.
 *
 * ⚠️ 반경을 250~400m 로 좁게 잡은 이유: 넓게 잡으면(1km 이상) 해안의 혹 대신
 *    뒤에 있는 **진짜 산**이 잡힌다. 산이 언제나 더 높기 때문이다.
 */
const CHECKPOINTS = [
  // ── 진짜 언덕. FAKE 로 나오면 기준이 틀린 것이다 ─────────────────────────
  { name: '동백섬',        want: 'REAL', lat: 35.1536, lon: 129.1519, r: 250, terrarium: 40.4,
    note: '해운대 동백섬. 진단 40.4m 인데 이 코드는 51.8m 로 잰다 — 같은 섬의 다른 지점으로 보인다' },
  { name: '민락 백산',     want: 'REAL', lat: 35.1575, lon: 129.1306, r: 250, terrarium: 52.7,
    note: '수영구 민락동 백산. 진단 52.7m, 이 코드 62.2m' },
  { name: '장산',          want: 'REAL', lat: 35.1942, lon: 129.1446, r: 400, terrarium: 626.0,
    note: '실제 634m. 두 자료가 모두 620대라 여기서는 어긋나지 않는다' },
  // ── 가짜 혹. 반드시 FAKE 로 나와야 한다 ──────────────────────────────────
  { name: '마린시티 앞',   want: 'FAKE', lat: 35.15395, lon: 129.14485, r: 250, terrarium: 48.8,
    note: '🔴 진단이 좌표까지 준 유일한 점. 이 코드가 48.4m 로 재서 진단의 48.8m 와 맞는다' },
  { name: '해운대 갈맷길', want: 'FAKE', lat: 35.1597, lon: 129.1647, r: 250, terrarium: 42.2,
    note: '해운대해수욕장 동쪽 끝(미포). 진단 42.2m, 이 코드 38.2m' },
  { name: '다대포 앞',     want: 'FAKE', lat: 35.0569, lon: 128.9797, r: 250, terrarium: 47.7,
    note: '🔴 진단의 47.7m 짜리 점은 못 찾았다. 다대포~낙동강하구를 전부 훑어도 '
        + '47~48m 짜리 가짜는 없다. 여기 적은 것은 그 일대에서 **가장 큰 가짜**(61.2m, GLO-30 0m)다' },
  { name: '녹산 미음산단', want: 'FAKE', lat: 35.0858, lon: 128.8667, r: 250, terrarium: 55.0,
    note: '강서구 미음산단. 진단 55.0m, 이 코드 64.4m' },
]

// ═══ terrarium 타일 읽기 — 필요할 때만, 오래된 것부터 버린다 ════════════════
//
// 🔴 부산 bbox 는 z15 에서 3,127장이다. 전부 Float32 로 들면 820MB 라 못 든다.
//    공통 격자를 **북→남 한 줄씩** 훑으면 같은 위도띠의 타일만 계속 쓰므로,
//    최근 것 CACHE_MAX 장만 들고 있으면 타일 한 장을 딱 한 번씩만 푼다.
const CACHE_MAX = 200
const tiles = new Map()                    // "x_y" → Float32Array | null(없는 타일)
const scanned = new Set()                  // 값 범위 검사를 이미 한 타일
const range = { pixels: 0, tiles: 0, min: Infinity, max: -Infinity, worst: [] }

function tileAt(tx, ty) {
  const k = `${tx}_${ty}`
  if (tiles.has(k)) { const v = tiles.get(k); tiles.delete(k); tiles.set(k, v); return v }   // 최근 것으로
  const p = join(DEM, String(ZOOM), `${k}.png`)
  let v = null
  if (existsSync(p)) {
    v = terrariumToElevation(decodePNG(readFileSync(p)))
    // ── 값 범위 검사. 타일당 한 번만 센다 ─────────────────────────────────
    if (!scanned.has(k)) {
      scanned.add(k)
      let bad = 0, lo = Infinity, hi = -Infinity
      for (let i = 0; i < v.length; i++) {
        const e = v[i]
        if (e < ELEV_MIN || e > ELEV_MAX || !Number.isFinite(e)) {
          bad++; if (e < lo) lo = e; if (e > hi) hi = e
        }
      }
      if (bad) {
        range.pixels += bad; range.tiles++
        if (lo < range.min) range.min = lo
        if (hi > range.max) range.max = hi
        range.worst.push({ tile: k, pixels: bad, min: Number(lo.toFixed(1)), max: Number(hi.toFixed(1)) })
      }
    }
  }
  tiles.set(k, v)
  if (tiles.size > CACHE_MAX) tiles.delete(tiles.keys().next().value)   // 가장 오래된 것
  return v
}

/** 전역 픽셀 (px,py) 한 칸. 범위 밖 값은 **버린다** (NaN). */
function terrPx(px, py) {
  if (px < 0 || py < 0) return NaN
  const t = tileAt(Math.floor(px / TILE), Math.floor(py / TILE))
  if (!t) return NaN
  const v = t[(py % TILE) * TILE + (px % TILE)]
  return (v < ELEV_MIN || v > ELEV_MAX || !Number.isFinite(v)) ? NaN : v
}

/** 경위도 한 점의 terrarium 고도 — 겹선형. 이웃이 하나라도 고장이면 최근접으로 물러난다. */
function terrAt(lat, lon) {
  const X = gx(lon) - 0.5, Y = gy(lat) - 0.5     // 픽셀 **한가운데** 기준으로 옮긴다
  const x0 = Math.floor(X), y0 = Math.floor(Y)
  const fx = X - x0, fy = Y - y0
  const a = terrPx(x0, y0),     b = terrPx(x0 + 1, y0)
  const c = terrPx(x0, y0 + 1), d = terrPx(x0 + 1, y0 + 1)
  if (Number.isNaN(a) || Number.isNaN(b) || Number.isNaN(c) || Number.isNaN(d)) {
    // 성한 이웃 중 가장 가까운 것. 넷 다 고장이면 NaN
    const cand = [[a, fx, fy], [b, 1 - fx, fy], [c, fx, 1 - fy], [d, 1 - fx, 1 - fy]]
      .filter(([v]) => !Number.isNaN(v))
      .sort((p, q) => (p[1] ** 2 + p[2] ** 2) - (q[1] ** 2 + q[2] ** 2))
    return cand.length ? cand[0][0] : NaN
  }
  return (a * (1 - fx) + b * fx) * (1 - fy) + (c * (1 - fx) + d * fx) * fy
}

// ═══ GLO-30 모자이크 ════════════════════════════════════════════════════════
function openMosaic() {
  if (!existsSync(GLO)) throw new Error(`${GLO} 가 없다 — 먼저 node collect/copernicus-dem.mjs`)
  const files = readdirSync(GLO).filter(f => f.endsWith('.tif')).sort()
  if (!files.length) throw new Error(`${GLO} 에 .tif 가 없다 — 먼저 node collect/copernicus-dem.mjs`)
  const parts = files.map(f => openGeoTIFF(join(GLO, f)))
  log(`GLO-30 ${parts.length}장 열림 — ${files.map(f => f.slice(23, 33)).join(' ')}`)

  // GLO-30 에도 **같은 값 범위 검사**를 건다. 여기 걸린 것을 따로 센다 —
  // "검사는 걸어 뒀지만 걸린 게 없다" 와 "검사를 안 걸었다" 는 다른 말이다.
  const bad = { range: 0, nodata: 0, outside: 0 }
  /** 공통 격자가 1초 정수배에 맞춰져 있으므로 **보간 없이** 픽셀을 그대로 읽는다. */
  const at = (lat, lon) => {
    for (const g of parts) {
      const c = Math.round(g.colOf(lon)), r = Math.round(g.rowOf(lat))
      if (c < 0 || r < 0 || c >= g.width || r >= g.height) continue
      const v = g.at(c, r)
      if (v == null) { bad.nodata++; return NaN }
      if (v < ELEV_MIN || v > ELEV_MAX || !Number.isFinite(v)) { bad.range++; return NaN }
      return v
    }
    bad.outside++
    return NaN
  }
  return { at, bad, close: () => parts.forEach(g => g.close()), count: parts.length, files }
}

// ═══ 창 안의 가장 낮은 곳 (분리형 슬라이딩 최소) ═════════════════════════════
//
// 가로로 한 번, 세로로 한 번 미끄러뜨린다. 정사각 창을 매 칸마다 다시 훑으면
// (2R+1)^2 번인데 이렇게 가르면 2(2R+1) 번이라 10배 넘게 싸다. 결과는 같다.
// NaN(고장·바다밖)은 **없는 셈 친다** — 있으면 그 칸의 최소가 NaN 으로 번진다.
function minFilter(src, cols, rows, R) {
  const tmp = new Float32Array(src.length), out = new Float32Array(src.length)
  for (let r = 0; r < rows; r++) {
    const o = r * cols
    for (let c = 0; c < cols; c++) {
      let m = Infinity
      for (let k = Math.max(0, c - R), e = Math.min(cols - 1, c + R); k <= e; k++) {
        const v = src[o + k]; if (!Number.isNaN(v) && v < m) m = v
      }
      tmp[o + c] = m
    }
  }
  for (let c = 0; c < cols; c++) {
    for (let r = 0; r < rows; r++) {
      let m = Infinity
      for (let k = Math.max(0, r - R), e = Math.min(rows - 1, r + R); k <= e; k++) {
        const v = tmp[k * cols + c]; if (v < m) m = v
      }
      out[r * cols + c] = m === Infinity ? NaN : m
    }
  }
  return out
}

async function main() {
  const area = JSON.parse(readFileSync(join(ROOT, 'config/area.json'), 'utf8'))
  const b = area.target.bbox
  await mkdir(OUT, { recursive: true })

  // ── 공통 격자. 1초 정수배에 딱 맞춘다 ────────────────────────────────────
  const c0 = Math.round(b.west * ARC), c1 = Math.round(b.east * ARC)
  const r0 = Math.round(b.north * ARC), r1 = Math.round(b.south * ARC)
  const cols = c1 - c0 + 1, rows = r0 - r1 + 1
  const lonOf = c => (c0 + c) / ARC
  const latOf = r => (r0 - r) / ARC

  // 부산 위도에서 칸 하나가 몇 미터인가 (혹의 폭을 미터로 적으려고 쓴다)
  const latMid = (b.north + b.south) / 2
  const M_LAT = 111320 / ARC                              // 위도 1초 ≈ 30.9m
  const M_LON = 111320 * Math.cos(rad(latMid)) / ARC      // 경도 1초 ≈ 25.3m
  log(`공통 격자 ${cols} x ${rows} = ${(cols * rows / 1e6).toFixed(2)}M칸  `
    + `(칸 ${M_LON.toFixed(1)}m x ${M_LAT.toFixed(1)}m)`)

  // ── 두 자료를 같은 격자에 올린다 ─────────────────────────────────────────
  const T = new Float32Array(cols * rows)      // terrarium (겹선형 재표본)
  const G = new Float32Array(cols * rows)      // GLO-30 (그대로 읽음)
  const mos = openMosaic()

  let tOK = 0, gOK = 0
  for (let r = 0; r < rows; r++) {
    const lat = latOf(r), o = r * cols
    for (let c = 0; c < cols; c++) {
      const lon = lonOf(c)
      const t = terrAt(lat, lon); T[o + c] = t; if (!Number.isNaN(t)) tOK++
      const g = mos.at(lat, lon); G[o + c] = g; if (!Number.isNaN(g)) gOK++
    }
    if (!QUIET && r % 200 === 0) log(`  재표본 ${r}/${rows}  (타일 ${scanned.size}장 읽음)`)
  }
  log(`재표본 끝 — terrarium 성한 칸 ${tOK}  GLO-30 성한 칸 ${gOK}  terrarium 타일 ${scanned.size}장`)

  // ── 값 범위 검사 결과 ────────────────────────────────────────────────────
  range.worst.sort((a, z) => z.pixels - a.pixels)
  if (range.pixels) {
    log(`🔴 값 범위 검사 — terrarium 타일 ${scanned.size}장 중 ${range.tiles}장에 `
      + `${range.pixels}픽셀이 범위 밖 (${range.min.toFixed(0)} ~ ${range.max.toFixed(0)}m). 전부 버렸다`)
    for (const w of range.worst.slice(0, 8)) log(`     ${w.tile}  ${w.pixels}픽셀  ${w.min} ~ ${w.max}m`)
  } else log(`값 범위 검사 — terrarium 타일 ${scanned.size}장 전부 ${ELEV_MIN}~${ELEV_MAX}m 안. 버린 것 없음`)
  log(`값 범위 검사 — GLO-30 은 범위밖 ${mos.bad.range}칸 · 값없음 ${mos.bad.nodata}칸 `
    + `· 덮는 타일 없음 ${mos.bad.outside}칸 (검사는 terrarium 과 같은 ${ELEV_MIN}~${ELEV_MAX}m)`)

  // ── 주변 바닥과 솟음 ─────────────────────────────────────────────────────
  log(`주변 바닥 계산 (반경 ${BASE_R}칸 ≈ ${(BASE_R * M_LON).toFixed(0)}m x ${(BASE_R * M_LAT).toFixed(0)}m)`)
  const baseT = minFilter(T, cols, rows, BASE_R)
  const baseG = minFilter(G, cols, rows, BASE_R)

  const promT = new Float32Array(cols * rows)
  for (let i = 0; i < promT.length; i++) promT[i] = T[i] - baseT[i]

  /** 한 칸의 판정에 필요한 것 전부. 격자 밖이거나 자료가 없으면 null. */
  function measure(idx) {
    const t = T[idx], g = G[idx], bt = baseT[idx], bg = baseG[idx]
    if (Number.isNaN(t) || Number.isNaN(g) || Number.isNaN(bt) || Number.isNaN(bg)) return null
    const pT = t - bt, pG = g - bg
    const diff = t - g
    // 남은비율: terrarium 이 솟았다고 한 만큼 GLO-30 에 얼마나 남아 있나
    const keep = pT > 0.5 ? pG / pT : 1
    let verdict
    if (diff >= DIFF_MIN && keep <= KEEP_FAKE) verdict = 'FAKE'
    else if (keep >= KEEP_REAL || diff < DIFF_MIN / 2) verdict = 'REAL'
    else verdict = 'UNSURE'
    return { terrarium_m: t, glo30_m: g, baseT: bt, baseG: bg, promT: pT, promG: pG, diff_m: diff, keep, verdict }
  }

  /**
   * 혹의 폭. 꼭대기에서 **솟음의 절반 높이**까지 내려온 자리가 어디까지
   * 이어지는지 재고 미터로 바꾼다. 산처럼 끝없이 이어지면 상한에서 끊는다.
   */
  const WIDTH_CAP = 60
  function widthOf(pc, pr) {
    const idx = pr * cols + pc
    const half = baseT[idx] + (T[idx] - baseT[idx]) / 2
    const seen = new Set([idx]), stack = [[pc, pr]]
    let cMin = pc, cMax = pc, rMin = pr, rMax = pr, capped = false
    while (stack.length) {
      const [x, y] = stack.pop()
      for (const [dx, dy] of [[1, 0], [-1, 0], [0, 1], [0, -1]]) {
        const nx = x + dx, ny = y + dy
        if (nx < 0 || ny < 0 || nx >= cols || ny >= rows) continue
        if (Math.abs(nx - pc) > WIDTH_CAP || Math.abs(ny - pr) > WIDTH_CAP) { capped = true; continue }
        const ni = ny * cols + nx
        if (seen.has(ni)) continue
        const v = T[ni]
        if (Number.isNaN(v) || v < half) continue
        seen.add(ni); stack.push([nx, ny])
        if (nx < cMin) cMin = nx; if (nx > cMax) cMax = nx
        if (ny < rMin) rMin = ny; if (ny > rMax) rMax = ny
      }
    }
    const w = Math.max((cMax - cMin + 1) * M_LON, (rMax - rMin + 1) * M_LAT)
    return { width_m: Number(w.toFixed(0)), width_capped: capped }
  }

  // ── 혹 후보 찾기: 솟음이 PROM_MIN 이상이고 5x5 안에서 가장 솟은 칸 ────────
  log(`혹 후보 찾기 (솟음 ${PROM_MIN}m 이상 · 5x5 안 최고점)`)
  const cands = []
  for (let r = 2; r < rows - 2; r++) {
    for (let c = 2; c < cols - 2; c++) {
      const i = r * cols + c
      const p = promT[i]
      if (!(p >= PROM_MIN)) continue                 // NaN 도 여기서 걸러진다
      let isPeak = true
      for (let dr = -2; dr <= 2 && isPeak; dr++) for (let dc = -2; dc <= 2; dc++) {
        if (!dr && !dc) continue
        const q = promT[i + dr * cols + dc]
        // 같은 값이 붙어 있으면 위/왼쪽 것만 남긴다 — 한 혹이 둘로 세어지지 않게
        if (q > p || (q === p && (dr < 0 || (dr === 0 && dc < 0)))) { isPeak = false; break }
      }
      if (!isPeak) continue
      const m = measure(i)
      if (!m) continue
      cands.push({ c, r, i, ...m })
    }
  }
  log(`후보 ${cands.length}곳`)

  // ── 판정 ─────────────────────────────────────────────────────────────────
  const rowsOut = cands.map(x => {
    const { width_m, width_capped } = widthOf(x.c, x.r)
    return {
      lat: Number(latOf(x.r).toFixed(6)), lon: Number(lonOf(x.c).toFixed(6)),
      terrarium_m: Number(x.terrarium_m.toFixed(1)),
      glo30_m: Number(x.glo30_m.toFixed(1)),
      diff_m: Number(x.diff_m.toFixed(1)),
      width_m, verdict: x.verdict,
      // 산이 아니라 혹인가 (폭 WIDTH_BUMP 이하). 거르는 데 안 쓰고 표시만 한다
      narrow: width_m <= WIDTH_BUMP,
      // 판정 근거. 왜 그렇게 갈렸는지 줄만 보고 알 수 있게 같이 적는다
      prom_terrarium_m: Number(x.promT.toFixed(1)),
      prom_glo30_m: Number(x.promG.toFixed(1)),
      keep_ratio: Number(x.keep.toFixed(3)),
      width_capped,
    }
  })
  // 결정적으로 — 큰 것부터, 같으면 좌표 순
  rowsOut.sort((a, z) => z.diff_m - a.diff_m || a.lat - z.lat || a.lon - z.lon)

  const tally = { FAKE: 0, REAL: 0, UNSURE: 0 }
  const tallyNarrow = { FAKE: 0, REAL: 0, UNSURE: 0 }
  for (const x of rowsOut) { tally[x.verdict]++; if (x.narrow) tallyNarrow[x.verdict]++ }
  const narrowN = rowsOut.filter(x => x.narrow).length

  // ── 🔴 검산: 진짜 셋과 가짜 넷 ───────────────────────────────────────────
  const checks = []
  for (const cp of CHECKPOINTS) {
    // 반경 안에서 terrarium 이 가장 높은 칸을 직접 찾는다
    const dr = Math.ceil(cp.r / M_LAT), dc = Math.ceil(cp.r / M_LON)
    const rc = Math.round((r0 - cp.lat * ARC)), cc = Math.round(cp.lon * ARC - c0)
    let best = null
    for (let r = Math.max(0, rc - dr); r <= Math.min(rows - 1, rc + dr); r++)
      for (let c = Math.max(0, cc - dc); c <= Math.min(cols - 1, cc + dc); c++) {
        const v = T[r * cols + c]
        if (Number.isNaN(v)) continue
        if (!best || v > best.v) best = { v, r, c }
      }
    if (!best) { checks.push({ ...cp, got: null, ok: false, why: '자료 없음' }); continue }
    const m = measure(best.r * cols + best.c)
    if (!m) { checks.push({ ...cp, got: null, ok: false, why: '자료 없음' }); continue }
    checks.push({
      name: cp.name, want: cp.want, verdict: m.verdict, ok: m.verdict === cp.want,
      lat: Number(latOf(best.r).toFixed(6)), lon: Number(lonOf(best.c).toFixed(6)),
      terrarium_m: Number(m.terrarium_m.toFixed(1)), diagnosed_terrarium_m: cp.terrarium,
      glo30_m: Number(m.glo30_m.toFixed(1)), diff_m: Number(m.diff_m.toFixed(1)),
      prom_terrarium_m: Number(m.promT.toFixed(1)), prom_glo30_m: Number(m.promG.toFixed(1)),
      keep_ratio: Number(m.keep.toFixed(3)),
    })
  }

  console.log('\n검산 — 진짜 셋은 REAL, 가짜 넷은 FAKE 여야 한다')
  console.log('곳             바람  판정    terr(진단)    GLO30   높이차  솟음T  솟음G  남은비율')
  for (const k of checks) {
    console.log(`${k.ok ? '  ✓' : '  ✗'} ${k.name.padEnd(12)} ${k.want}  ${(k.verdict ?? '-').padEnd(6)} `
      + `${String(k.terrarium_m ?? '-').padStart(6)}(${k.diagnosed_terrarium_m}) `
      + `${String(k.glo30_m ?? '-').padStart(7)} ${String(k.diff_m ?? '-').padStart(7)} `
      + `${String(k.prom_terrarium_m ?? '-').padStart(6)} ${String(k.prom_glo30_m ?? '-').padStart(6)} `
      + `${String(k.keep_ratio ?? '-').padStart(7)}`)
  }
  const failed = checks.filter(k => !k.ok)

  // ── 내보내기 ─────────────────────────────────────────────────────────────
  const ndjson = join(OUT, 'dem-bumps.ndjson')
  await writeFile(ndjson, rowsOut.map(x => JSON.stringify(x)).join('\n') + '\n')
  await writeFile(join(OUT, 'dem-bumps-meta.json'), JSON.stringify({
    at: new Date().toISOString(),
    bbox: b,
    grid: { cols, rows, arcsecPerCell: 1, metersPerCell: { lon: Number(M_LON.toFixed(2)), lat: Number(M_LAT.toFixed(2)) } },
    resample: 'GLO-30 격자를 공통 격자로 삼고 terrarium 을 겹선형으로 맞췄다. GLO-30 은 보간 없이 픽셀 그대로',
    thresholds: { BASE_R, PROM_MIN, DIFF_MIN, KEEP_FAKE, KEEP_REAL, WIDTH_BUMP },
    rangeCheck: {
      min: ELEV_MIN, max: ELEV_MAX,
      terrariumTilesRead: scanned.size, terrariumTilesWithBad: range.tiles,
      terrariumBadPixels: range.pixels,
      terrariumBadSpan: range.pixels ? [Number(range.min.toFixed(1)), Number(range.max.toFixed(1))] : null,
      worstTiles: range.worst.slice(0, 20),
      glo30OutOfRange: mos.bad.range, glo30NoData: mos.bad.nodata, glo30NotCovered: mos.bad.outside,
    },
    candidates: rowsOut.length, tally,
    narrowCandidates: narrowN, tallyNarrow,
    checkpoints: checks,
    glo30Files: mos.files,
  }, null, 2))
  mos.close()

  console.log(`\n후보 ${rowsOut.length}곳(솟음 ${PROM_MIN}m 이상인 국소 최고점 전부)`)
  console.log(`   FAKE ${tally.FAKE} · REAL ${tally.REAL} · UNSURE ${tally.UNSURE}`)
  console.log(`그중 산이 아니라 혹인 것(폭 ${WIDTH_BUMP}m 이하) ${narrowN}곳`)
  console.log(`   FAKE ${tallyNarrow.FAKE} · REAL ${tallyNarrow.REAL} · UNSURE ${tallyNarrow.UNSURE}`)
  console.log(`→ ${ndjson}`)

  if (failed.length) {
    console.error(`\n🔴 검산 실패 ${failed.length}곳: ${failed.map(k => `${k.name}(${k.want}→${k.verdict ?? k.why})`).join(', ')}`)
    console.error('   기준이 틀렸다는 뜻이다. 통과시키지 않는다.')
    process.exitCode = 1
  }
}
main().catch(e => { console.error('치명:', e); process.exit(1) })
