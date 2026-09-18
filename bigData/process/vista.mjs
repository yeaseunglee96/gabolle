#!/usr/bin/env node
/**
 * 경치 **근사** — OSM 파생 세 축 (물가·녹지·상대고도)
 *
 * 🔴 이것은 경치의 측정이 아니다. **근사다.**
 *    로드뷰 이미지는 수집이 금지돼 있고(`CLAUDE.md` 1절), Mapillary 토큰은 아직 없다.
 *    그래서 `docs/WALKABILITY.md` 3절이 정한 대로 *"경치가 좋다"* 를 직접 재는 대신
 *    **"물가·녹지·고지대에 가깝다"** 로 바꿔 잰다. 완벽하지 않지만 0 보다 낫다.
 *    **이 파일이 내는 숫자를 "경치 점수" 라고 부르면 그 순간 거짓말이 된다.**
 *
 * 축은 셋이고 **따로 낸다. 합치지 않는다.**
 *    최종 가중치는 짝 비교 실험이 정한다 — `docs/FIELD-STUDY.md`.
 *    여기서 손으로 가중치를 정하면 그 결정을 뒤집는 것이다. 그래서 합산 점수가 없다.
 *
 *   node process/vista.mjs
 *
 * 산출: data/staged/segment-vista.ndjson · data/staged/_vista-summary.json
 * 판정: 종료 코드. 입력 없음 2 / 불변식 깨짐 1
 */
import { createReadStream, existsSync, readFileSync } from 'node:fs'
import { createInterface } from 'node:readline'
import { readdir, writeFile, mkdir, open } from 'node:fs/promises'
import { join, dirname, relative, sep } from 'node:path'
import { fileURLToPath } from 'node:url'
// 🔴 고도 타일 PNG 디코더는 다시 만들지 않는다. 검증된 것을 그대로 쓴다.
import { decodePNG, terrariumToElevation } from './png.mjs'
import { stamp } from '../mlops/manifest.mjs'
import { log } from '../lib/log.mjs'

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..')
const DEM  = join(ROOT, 'data/raw/dem')
const PBF  = join(ROOT, 'data/raw/pbf')
const OUT  = join(ROOT, 'data/staged')
const rel  = p => relative(ROOT, p).split(sep).join('/')

const ZOOM = 15, TILE = 256
/** 거친 격자 = 고도 픽셀 8x8 을 묶은 것 → 약 31m.
 *  🔴 3.9m 격자로 다루지 않는 이유: 원본이 SRTM ~30m 보간이라 **정보가 30m 밖에 없다.**
 *     3.9m 로 계산하면 정밀해 보이는 숫자가 나오는데 그 정밀도는 가짜다. */
const DOWN = 8
/** 바다 판정 고도. **손으로 고른 값이 아니라 물리 상수다** — 해수면은 0m 다.
 *  DEM 의 수직오차가 ±5m 라서 0 보다 위의 어떤 임계값도 잡음 안에 있다.
 *  17개 대조점에 맞춰 1m·2m 로 올리면 잡음에 파라미터를 맞추는 것이 된다. 아래 sweep 참조. */
const SEA_M = 0
/** 구간 위를 몇 m 간격으로 찍어볼 것인가. 거친 격자(31m) 보다 촘촘하면 충분하다. */
const STEP_M = 25
/** 상대고도의 "주변" — 정사각 이웃의 반변(m). 🔴 이 값은 추정 대상이라 **둘 다 낸다.** */
const REL_RADII_M = [500, 1000]

const R = 6371000, rad = d => d * Math.PI / 180
const haversine = (a, b) => {
  const dLat = rad(b.lat - a.lat), dLon = rad(b.lon - a.lon)
  const h = Math.sin(dLat/2)**2 + Math.cos(rad(a.lat))*Math.cos(rad(b.lat))*Math.sin(dLon/2)**2
  return 2 * R * Math.asin(Math.sqrt(h))
}
/** Web Mercator z15 의 지상 해상도. 위도에 따라 변한다 (부산 안에서 0.7% 차이). */
const mPerPx = lat => 156543.03392804097 * Math.cos(rad(lat)) / 2 ** ZOOM
const gx = lon => (lon + 180) / 360 * 2 ** ZOOM * TILE
const gy = lat => { const r = rad(lat)
  return (1 - Math.log(Math.tan(r) + 1 / Math.cos(r)) / Math.PI) / 2 * 2 ** ZOOM * TILE }

// ── 0. 입력 확인 — 없으면 종료 코드 2. **없는 것을 있는 것처럼 만들지 않는다.** ──────
const need = [
  join(DEM, '_meta-z15.json'), join(DEM, String(ZOOM)),
  join(PBF, 'road.ndjson'), join(PBF, 'poi.ndjson'),
  join(OUT, 'segment-slope.ndjson'),
]
const missing = need.filter(p => !existsSync(p))
if (missing.length) {
  console.error('🔴 입력이 없습니다:')
  for (const m of missing) console.error(`   ${rel(m)}`)
  console.error('   npm run collect:extract · npm run collect:terrain · npm run slope 를 먼저 도세요')
  process.exit(2)
}

const meta = JSON.parse(readFileSync(join(DEM, '_meta-z15.json'), 'utf8'))
const { x0, x1, y0, y1 } = meta.tileRange
const CPT = TILE / DOWN                       // 타일당 거친 셀 수
const GW = (x1 - x0 + 1) * CPT, GH = (y1 - y0 + 1) * CPT
const cellPx = DOWN                            // 거친 셀 = 8 픽셀
const cxOf = lon => Math.floor(gx(lon) / cellPx) - x0 * CPT
const cyOf = lat => Math.floor(gy(lat) / cellPx) - y0 * CPT
const inGrid = (cx, cy) => cx >= 0 && cy >= 0 && cx < GW && cy < GH

// ── 1. DEM → 거친 격자 (셀마다 평균고도와 최저고도) ─────────────────────────────
log(`거친 격자 ${GW}x${GH} (셀 ${(meta.metersPerPixel * DOWN).toFixed(1)}m) — 타일을 훑습니다`)
const gMean = new Float32Array(GW * GH), gMin = new Float32Array(GW * GH)
const gHas  = new Uint8Array(GW * GH)
let tilesRead = 0, tilesAbsent = 0
{
  const t0 = Date.now()
  for (let tx = x0; tx <= x1; tx++) for (let ty = y0; ty <= y1; ty++) {
    const p = join(DEM, String(ZOOM), `${tx}_${ty}.png`)
    if (!existsSync(p)) { tilesAbsent++; continue }
    const e = terrariumToElevation(decodePNG(readFileSync(p))); tilesRead++
    const bx0 = (tx - x0) * CPT, by0 = (ty - y0) * CPT
    for (let by = 0; by < CPT; by++) for (let bx = 0; bx < CPT; bx++) {
      let s = 0, m = Infinity
      for (let j = 0; j < DOWN; j++) for (let i = 0; i < DOWN; i++) {
        const v = e[(by * DOWN + j) * TILE + bx * DOWN + i]
        s += v; if (v < m) m = v
      }
      const k = (by0 + by) * GW + bx0 + bx
      gMean[k] = s / (DOWN * DOWN); gMin[k] = m; gHas[k] = 1
    }
  }
  log(`  타일 ${tilesRead}장 읽음 / ${tilesAbsent}장 없음 / ${((Date.now()-t0)/1000).toFixed(1)}초`)
}
if (!tilesRead) { console.error('🔴 고도 타일을 한 장도 읽지 못했습니다'); process.exit(2) }

/**
 * 🔴 **고도 축은 해수면 아래를 0m 로 누른다.** terrarium 타일은 바다를 해저 지형으로
 *    담는데, 그 값이 경치와 아무 관계가 없고 게다가 이상하다 — 이 bbox 안에 -3,000m
 *    아래 셀이 있다. 한국해협 실제 최대 수심은 약 200m 다.
 *
 *    누르지 않으면 두 군데가 조용히 망가진다:
 *      1) 바다 위 교량 구간(광안대교 등)의 "고도" 가 해저 깊이가 된다 (실측 -374m)
 *      2) 해안 구간의 이웃 평균이 해저로 끌려가 상대고도가 부풀려진다
 *    바다 위 구간은 이제 0m 로 읽힌다. **DEM 은 교량 상판 높이를 모른다** — 0m 는
 *    실제(광안대교 약 35m)보다 낮지만 -374m 보다는 훨씬 덜 틀리다.
 *
 *    바다 마스크는 gMin/gMean 원본을 그대로 쓴다 (≤0 판정에 눌린 값을 쓸 수 없다).
 */
const gSurf = new Float32Array(GW * GH)
let demRawMin = Infinity, demMax = -Infinity, cellsWithData = 0, bathyOutliers = 0
for (let i = 0; i < GW * GH; i++) if (gHas[i]) {
  cellsWithData++
  if (gMean[i] < demRawMin) demRawMin = gMean[i]
  if (gMean[i] > demMax) demMax = gMean[i]
  if (gMean[i] < -300) bathyOutliers++
  gSurf[i] = gMean[i] > 0 ? gMean[i] : 0
}

// ── 2. 거리 변환 (EDT) — 어떤 셀에서 가장 가까운 씨앗 셀까지 몇 셀인가 ────────────
/** 1차원 제곱거리 EDT (Felzenszwalb–Huttenlocher 하한포락선). 근사 아니고 정확하다. */
function edt1d(f, n, d, v, z) {
  let k = 0; v[0] = 0; z[0] = -Infinity; z[1] = Infinity
  for (let q = 1; q < n; q++) {
    let s = ((f[q] + q*q) - (f[v[k]] + v[k]*v[k])) / (2*q - 2*v[k])
    while (s <= z[k]) { k--; s = ((f[q] + q*q) - (f[v[k]] + v[k]*v[k])) / (2*q - 2*v[k]) }
    k++; v[k] = q; z[k] = s; z[k + 1] = Infinity
  }
  k = 0
  for (let q = 0; q < n; q++) { while (z[k + 1] < q) k++; d[q] = (q - v[k])**2 + f[v[k]] }
}
/** 씨앗(1)까지의 **제곱** 셀거리 격자. 제곱으로 두는 이유: sqrt 를 조회할 때 한 번만 한다. */
function edt2d(seed) {
  const INF = 1e12, out = new Float64Array(GW * GH), m = Math.max(GW, GH)
  const f = new Float64Array(m), d = new Float64Array(m)
  const v = new Int32Array(m), z = new Float64Array(m + 1)
  for (let x = 0; x < GW; x++) {
    for (let y = 0; y < GH; y++) f[y] = seed[y * GW + x] ? 0 : INF
    edt1d(f, GH, d, v, z); for (let y = 0; y < GH; y++) out[y * GW + x] = d[y]
  }
  for (let y = 0; y < GH; y++) {
    for (let x = 0; x < GW; x++) f[x] = out[y * GW + x]
    edt1d(f, GW, d, v, z); for (let x = 0; x < GW; x++) out[y * GW + x] = d[x]
  }
  return out
}
/**
 * 🔴 **바다에 이어져 있는 것만 바다로 센다.**
 *    이 필터가 없으면 DEM 잡음이 만든 내륙의 고립된 "해수면 이하" 셀 때문에
 *    표고 350m 산에서 바다가 3km 라고 나온다. 아래 sweep 에 그 숫자가 있다.
 *    (bbox 남쪽·동쪽 경계는 실제로 열린 바다다 — 거기서 물을 채워 들어온다.)
 */
function keepBorderConnected(mask) {
  const keep = new Uint8Array(GW * GH), st = []
  const push = i => { if (mask[i] && !keep[i]) { keep[i] = 1; st.push(i) } }
  for (let x = 0; x < GW; x++) { push(x); push((GH - 1) * GW + x) }
  for (let y = 0; y < GH; y++) { push(y * GW); push(y * GW + GW - 1) }
  while (st.length) {
    const i = st.pop(), x = i % GW, y = (i - x) / GW
    if (x > 0) push(i - 1); if (x < GW - 1) push(i + 1)
    if (y > 0) push(i - GW); if (y < GH - 1) push(i + GW)
  }
  return keep
}
const countOn = m => { let n = 0; for (let i = 0; i < m.length; i++) n += m[i]; return n }

// ── 3. 바다 마스크 + 근거표 ────────────────────────────────────────────────────
// 🔴 물가 대조점: OSM 이 "여기는 물가다" 라고 말한 노드. 이 정의의 정확도를 재는 자다.
//    (경사 기준선을 해안 매립지 대조군으로 정한 것과 같은 방식 — CLAUDE.md 6절)
const seaCtrl = [], greenPts = []
const GREEN_LEISURE = new Set(['park','garden','nature_reserve','common','playground',
                               'pitch','golf_course','dog_park','picnic_table'])
const GREEN_TOURISM = new Set(['picnic_site','camp_site'])
const GREEN_NATURAL = new Set(['tree','tree_row','wood','scrub','grassland'])
const isGreen = t => GREEN_LEISURE.has(t.leisure) || GREEN_TOURISM.has(t.tourism)
                  || GREEN_NATURAL.has(t.natural) || t.landuse === 'forest' || t.landuse === 'grass'
const greenKinds = {}
let viewpoints = 0
{
  const rl = createInterface({ input: createReadStream(join(PBF, 'poi.ndjson')), crlfDelay: Infinity })
  for await (const line of rl) {
    if (!line) continue
    const o = JSON.parse(line), t = o.tags || {}
    if (t.tourism === 'viewpoint') viewpoints++
    if (t.leisure === 'marina' || t.leisure === 'slipway' || t.natural === 'beach'
        || t['seamark:type'] === 'harbour' || t.man_made === 'pier')
      seaCtrl.push({ lat: o.lat, lon: o.lon, kind: t.leisure || t.natural || t['seamark:type'] || t.man_made })
    if (isGreen(t)) {
      greenPts.push({ lat: o.lat, lon: o.lon })
      const k = GREEN_LEISURE.has(t.leisure) ? `leisure=${t.leisure}`
              : GREEN_TOURISM.has(t.tourism) ? `tourism=${t.tourism}`
              : GREEN_NATURAL.has(t.natural) ? `natural=${t.natural}` : `landuse=${t.landuse}`
      greenKinds[k] = (greenKinds[k] || 0) + 1
    }
  }
}

const rawSea = new Uint8Array(GW * GH)
for (let i = 0; i < GW * GH; i++) if (gHas[i] && gMin[i] <= SEA_M) rawSea[i] = 1
const seaMask = keepBorderConnected(rawSea)
const rawSeaN = countOn(rawSea), seaN = countOn(seaMask)
log(`바다 셀 ${seaN.toLocaleString()} / 해수면이하 원본 ${rawSeaN.toLocaleString()} (고립 ${(rawSeaN-seaN).toLocaleString()}개 버림)`)
if (!seaN) { console.error('🔴 바다 셀이 하나도 없습니다 — DEM 디코딩이 깨졌을 가능성'); process.exit(1) }

const seaD2 = edt2d(seaMask)
const seaDistAt = (lat, lon) => {
  const cx = cxOf(lon), cy = cyOf(lat)
  if (!inGrid(cx, cy)) return null
  return Math.sqrt(seaD2[cy * GW + cx]) * mPerPx(lat) * cellPx
}

/** 후보 정의를 나란히 재서 표로 남긴다. **왜 0m 를 골랐는지가 여기 있다.** */
function sweepRow(label, pred, connect) {
  const m = new Uint8Array(GW * GH)
  for (let i = 0; i < GW * GH; i++) if (gHas[i] && pred(i)) m[i] = 1
  const rawN = countOn(m)
  const use = connect ? keepBorderConnected(m) : m
  const d2 = edt2d(use)
  const at = (lat, lon) => { const cx = cxOf(lon), cy = cyOf(lat)
    return inGrid(cx, cy) ? Math.sqrt(d2[cy * GW + cx]) * mPerPx(lat) * cellPx : null }
  const cd = seaCtrl.map(c => at(c.lat, c.lon)).filter(v => v != null).sort((a, b) => a - b)
  const q = p => cd.length ? +cd[Math.min(cd.length - 1, Math.floor(cd.length * p))].toFixed(0) : null
  const probe = (la, lo) => { const v = at(la, lo); return v == null ? null : +v.toFixed(0) }
  return {
    def: label, borderConnected: connect,
    waterCells: rawN, waterCellsKept: countOn(use),
    waterAreaPct: +(rawN / cellsWithData * 100).toFixed(1),
    ctrlP50M: q(0.5), ctrlP90M: q(0.9), ctrlMaxM: cd.length ? +cd.at(-1).toFixed(0) : null,
    // 🔴 내륙 음성 대조: 표고 350m 산(금정산)·내륙 평지(서면). 누수하면 이 값이 무너진다.
    probeGeumjeongsanM: probe(35.2585, 129.0470),
    probeSeomyeonM: probe(35.1580, 129.0590),
    probeNakdongMidM: probe(35.2000, 128.9700),
  }
}
log('바다 정의 후보를 재는 중 (근거표)…')
const seaSweep = [
  sweepRow(`min<=${SEA_M} + 바다연결 (채택)`, i => gMin[i] <= SEA_M, true),
  sweepRow(`min<=${SEA_M} · 연결필터 없음`,   i => gMin[i] <= SEA_M, false),
  sweepRow('mean<=0 + 바다연결',              i => gMean[i] <= 0,    true),
  sweepRow('mean<=1 + 바다연결',              i => gMean[i] <= 1,    true),
  sweepRow('mean<=2 + 바다연결',              i => gMean[i] <= 2,    true),
]
for (const r of seaSweep) log(`  ${r.def.padEnd(26)} 물${String(r.waterAreaPct).padStart(5)}% 대조p50=${String(r.ctrlP50M).padStart(4)} p90=${String(r.ctrlP90M).padStart(5)} 금정산=${String(r.probeGeumjeongsanM).padStart(6)} 서면=${String(r.probeSeomyeonM).padStart(5)}`)

// ── 4. 상대고도 — 누적합표(summed-area table)로 정사각 이웃 평균을 O(1) 로 ────────
const sat = new Float64Array((GW + 1) * (GH + 1))
const satN = new Float64Array((GW + 1) * (GH + 1))
for (let y = 0; y < GH; y++) for (let x = 0; x < GW; x++) {
  const k = (y + 1) * (GW + 1) + (x + 1), i = y * GW + x
  sat[k]  = (gHas[i] ? gSurf[i] : 0) + sat[k-1] + sat[k-(GW+1)] - sat[k-(GW+1)-1]
  satN[k] = (gHas[i] ? 1 : 0)        + satN[k-1] + satN[k-(GW+1)] - satN[k-(GW+1)-1]
}
function boxMean(cx, cy, half) {
  const ax = Math.max(0, cx - half), bx = Math.min(GW - 1, cx + half)
  const ay = Math.max(0, cy - half), by = Math.min(GH - 1, cy + half)
  const g = (X, Y) => (Y + 1) * (GW + 1) + (X + 1)
  const s = sat[g(bx,by)] - sat[g(ax-1,by)] - sat[g(bx,ay-1)] + sat[g(ax-1,ay-1)]
  const n = satN[g(bx,by)] - satN[g(ax-1,by)] - satN[g(bx,ay-1)] + satN[g(ax-1,ay-1)]
  return n > 0 ? s / n : null
}

// ── 5. 녹지 점까지의 최단거리 ────────────────────────────────────────────────
// 기준집합이 수백 개뿐이라 **전수 비교**한다. 공간 색인을 붙이면 빨라지지만
// "가장 가까운 것을 정말 찾았는가" 를 증명해야 할 코드가 늘어난다. 안 늘린다.
// 비교는 국소 평면좌표(등거리원통)로 하고 — 대권거리의 소각 근사라 haversine 과
// 같은 구(R) 를 쓴다 — 부산 크기에서 오차는 0.1% 아래다.
const MPD = R * Math.PI / 180                          // 위도 1도의 m (구면)
const gLat = Float64Array.from(greenPts, p => p.lat)
const gLon = Float64Array.from(greenPts, p => p.lon)
function greenDist(lat, lon) {
  if (!gLat.length) return null
  const kx = MPD * Math.cos(rad(lat))
  let best = Infinity
  for (let i = 0; i < gLat.length; i++) {
    const dy = (gLat[i] - lat) * MPD, dx = (gLon[i] - lon) * kx
    const d2 = dy * dy + dx * dx
    if (d2 < best) best = d2
  }
  return Math.sqrt(best)
}

// ── 6. 구간 목록 — segment-slope.ndjson 이 정한 것과 **정확히 같은 집합**이어야 한다 ──
const wanted = new Set()
{
  const rl = createInterface({ input: createReadStream(join(OUT, 'segment-slope.ndjson')), crlfDelay: Infinity })
  for await (const line of rl) { if (!line) continue; const o = JSON.parse(line); wanted.add(`${o.topic}:${o.id}`) }
}
log(`대상 구간 ${wanted.size.toLocaleString()}개 (segment-slope.ndjson 과 같은 집합)`)

/** slope.mjs 와 **같은 순서**로 흘린다 (road → walk → stairs). 그래야 줄 번호가 맞물린다. */
async function* ways() {
  for (const t of ['road', 'walk', 'stairs']) {
    const p = join(PBF, `${t}.ndjson`)
    if (!existsSync(p)) continue
    const rl = createInterface({ input: createReadStream(p), crlfDelay: Infinity })
    for await (const line of rl) if (line) yield [t, JSON.parse(line)]
  }
}
function resample(geom) {
  const pts = [{ ...geom[0] }]; let acc = 0
  for (let i = 1; i < geom.length; i++) {
    const a = geom[i-1], b = geom[i], d = haversine(a, b)
    if (d === 0) continue
    for (let t = STEP_M - (acc % STEP_M); t < d; t += STEP_M)
      pts.push({ lat: a.lat + (b.lat-a.lat)*(t/d), lon: a.lon + (b.lon-a.lon)*(t/d) })
    acc += d; pts.push({ ...b })
  }
  return pts
}

// ── 7. 본 계산 ────────────────────────────────────────────────────────────────
await mkdir(OUT, { recursive: true })
const fh = await open(join(OUT, 'segment-vista.ndjson'), 'w')
const w = fh.createWriteStream()
const seen = new Set()
const acc = { sea: [], green: [], elev: [] }
const accRel = Object.fromEntries(REL_RADII_M.map(r => [r, []]))
const halfCells = Object.fromEntries(REL_RADII_M.map(r =>
  [r, Math.max(1, Math.round(r / (meta.metersPerPixel * DOWN)))]))
let n = 0, dup = 0, outsideBbox = 0, noGridPts = 0
const bad = []
const gridDiagM = Math.hypot(GW, GH) * meta.metersPerPixel * DOWN
const b = meta.bbox
const t1 = Date.now()

for await (const [topic, el] of ways()) {
  const key = `${topic}:${el.id}`
  if (!wanted.has(key)) continue
  if (seen.has(key)) { dup++; continue }
  seen.add(key)
  if (!el.geometry || el.geometry.length < 2) continue

  const pts = resample(el.geometry)
  let cLat = 0, cLon = 0
  for (const p of el.geometry) { cLat += p.lat; cLon += p.lon }
  cLat /= el.geometry.length; cLon /= el.geometry.length
  if (cLat < b.south || cLat > b.north || cLon < b.west || cLon > b.east) outsideBbox++

  // 물가: 구간의 **최근접**. (7절의 p90 규칙은 표본마다 흔들리는 경사값을 위한 것이고,
  // 거리 격자는 매끄럽다. 잡음이 만드는 가짜 물은 위의 바다연결 필터가 이미 걷어냈다.)
  let sea = null
  for (const p of pts) { const d = seaDistAt(p.lat, p.lon); if (d != null && (sea == null || d < sea)) sea = d }

  let green = null
  for (const p of pts) { const d = greenDist(p.lat, p.lon); if (d != null && (green == null || d < green)) green = d }

  // 고도: 거친 격자(31m) 셀 평균의 구간 평균. **3.9m 로 보간하지 않는다 — 정보가 없다.**
  let es = 0, en = 0
  for (const p of pts) {
    const cx = cxOf(p.lon), cy = cyOf(p.lat)
    if (!inGrid(cx, cy)) continue
    const i = cy * GW + cx; if (!gHas[i]) continue
    es += gSurf[i]; en++
  }
  const elev = en ? es / en : null
  if (!en) noGridPts++

  const relOut = {}
  for (const rr of REL_RADII_M) {
    const cx = cxOf(cLon), cy = cyOf(cLat)
    const nb = (elev != null && inGrid(cx, cy)) ? boxMean(cx, cy, halfCells[rr]) : null
    relOut[`relElev${rr}M`] = nb == null ? null : +(elev - nb).toFixed(1)
  }

  // ── 불변식: 나쁜 값이 조용히 통과하지 못하게 ────────────────────────────────
  for (const [nm, v] of [['seaDistM', sea], ['greenPoiDistM', green]]) {
    if (v == null) continue
    if (!Number.isFinite(v) || v < 0) bad.push(`${key} ${nm}=${v} (음수 또는 유한하지 않다)`)
    if (v > gridDiagM * 1.001) bad.push(`${key} ${nm}=${v.toFixed(0)}m > 격자 대각선 ${gridDiagM.toFixed(0)}m`)
  }
  // 🔴 해수면 이하를 눌렀으므로 고도는 0 아래로 못 내려간다. 내려가면 누른 것이 새는 것이다.
  if (elev != null && (elev < -0.01 || elev > demMax + 0.01))
    bad.push(`${key} elevM=${elev.toFixed(1)} 이 표면고도 범위 [0, ${demMax.toFixed(1)}] 밖`)
  for (const rr of REL_RADII_M) {
    const v = relOut[`relElev${rr}M`]
    if (v != null && Math.abs(v) > demMax + 0.01)
      bad.push(`${key} relElev${rr}M=${v} 이 표면고도 진폭 ${demMax.toFixed(1)}m 를 넘는다`)
  }
  if (bad.length > 20) break

  if (sea   != null) acc.sea.push(sea)
  if (green != null) acc.green.push(green)
  if (elev  != null) acc.elev.push(elev)
  for (const rr of REL_RADII_M) { const v = relOut[`relElev${rr}M`]; if (v != null) accRel[rr].push(v) }

  n++
  w.write(JSON.stringify({
    id: el.id, topic, name: el.tags?.name ?? null,
    lat: +cLat.toFixed(5), lon: +cLon.toFixed(5),
    seaDistM:      sea   == null ? null : +sea.toFixed(0),
    greenPoiDistM: green == null ? null : +green.toFixed(0),
    elevM:         elev  == null ? null : +elev.toFixed(1),
    ...relOut,
  }) + '\n')
  if (n % 20000 === 0) log(`  ${n.toLocaleString()}개 / ${((Date.now()-t1)/1000).toFixed(0)}초`)
}
await new Promise(r => w.end(r))
await fh.close()
const secs = (Date.now() - t1) / 1000
log(`구간 ${n.toLocaleString()}개 / ${secs.toFixed(1)}초`)

// ── 8. 불변식 판정 ────────────────────────────────────────────────────────────
const fail = []
for (const m of bad) fail.push(m)
if (dup) fail.push(`중복 구간 키 ${dup}개 — 입력이 같은 way 를 두 번 담고 있다`)
const notFound = [...wanted].filter(k => !seen.has(k))
if (notFound.length)
  fail.push(`segment-slope 에 있는데 원본에서 못 찾은 구간 ${notFound.length}개 (예: ${notFound.slice(0,3)})`)
if (n !== wanted.size)
  fail.push(`출력 ${n}개 ≠ segment-slope ${wanted.size}개 — 두 파일을 줄 단위로 맞물릴 수 없다`)
if (seaN < rawSeaN * 0.5)
  fail.push(`바다연결 셀이 해수면이하 셀의 ${(seaN/rawSeaN*100).toFixed(0)}% 뿐 — 연결 판정이 깨졌다`)
if (!greenPts.length) fail.push('녹지 기준점이 0개 — 녹지 축을 낼 근거가 없다')

// 🔴 보유율이 0 이면 실패다. 한 구간도 값을 못 냈다는 뜻이다.
const cover = {
  seaDistM: n ? acc.sea.length / n : 0,
  greenPoiDistM: n ? acc.green.length / n : 0,
  elevM: n ? acc.elev.length / n : 0,
  ...Object.fromEntries(REL_RADII_M.map(r => [`relElev${r}M`, n ? accRel[r].length / n : 0])),
}
for (const [k, v] of Object.entries(cover)) if (v === 0) fail.push(`${k} 보유율 0 — 한 구간도 값을 못 냈다`)

// 🔴 바다 정의의 정확도 검사: OSM 이 물가라고 말한 곳이 바다에서 멀면 마스크가 깨진 것이다.
const ctrlD = seaCtrl.map(c => seaDistAt(c.lat, c.lon)).filter(v => v != null).sort((a, b) => a - b)
const CTRL_P90_LIMIT_M = 2000
const ctrlP90 = ctrlD.length ? ctrlD[Math.min(ctrlD.length - 1, Math.floor(ctrlD.length * 0.9))] : null
if (!ctrlD.length) fail.push('물가 대조점이 0개 — 바다 마스크를 검증할 자가 없다')
else if (ctrlP90 > CTRL_P90_LIMIT_M)
  fail.push(`물가 대조점 p90 거리 ${ctrlP90.toFixed(0)}m > ${CTRL_P90_LIMIT_M}m — 바다 마스크가 물가를 못 잡는다`)

// ── 9. 요약 ──────────────────────────────────────────────────────────────────
const q = (a, p) => a.length ? +a[Math.min(a.length-1, Math.floor(a.length*p))].toFixed(1) : null
const dist = a => { const s = [...a].sort((x, y) => x - y)
  return { n: s.length, min: q(s,0), p10: q(s,.1), p25: q(s,.25), p50: q(s,.5),
           p75: q(s,.75), p90: q(s,.9), max: s.length ? +s.at(-1).toFixed(1) : null } }
const pearson = (A, B) => {
  const xs = [], ys = []
  for (let i = 0; i < A.length; i++) if (A[i] != null && B[i] != null) { xs.push(A[i]); ys.push(B[i]) }
  if (xs.length < 2) return null
  const mx = xs.reduce((a,b)=>a+b,0)/xs.length, my = ys.reduce((a,b)=>a+b,0)/ys.length
  let sxy = 0, sxx = 0, syy = 0
  for (let i = 0; i < xs.length; i++) { const a = xs[i]-mx, c = ys[i]-my; sxy += a*c; sxx += a*a; syy += c*c }
  return (sxx && syy) ? +(sxy/Math.sqrt(sxx*syy)).toFixed(3) : null
}
// 상관은 축이 서로 겹치는지를 보여준다 — 가중치를 추정할 사람이 알아야 하는 것이다.
const pairAligned = { sea: [], green: [], rel: [] }
{
  const rl = createInterface({ input: createReadStream(join(OUT, 'segment-vista.ndjson')), crlfDelay: Infinity })
  for await (const line of rl) { if (!line) continue; const o = JSON.parse(line)
    pairAligned.sea.push(o.seaDistM); pairAligned.green.push(o.greenPoiDistM)
    pairAligned.rel.push(o.relElev1000M) }
}

// 🔴 숫자를 문장에 베껴 적지 않는다 — 베끼면 낡는다. 여기서 세서 문장에 끼운다.
const relBig = accRel[REL_RADII_M.at(-1)]
const relNegPct = relBig.length ? +(relBig.filter(v => v < 0).length / relBig.length * 100).toFixed(0) : null

const summary = {
  '//': '경치의 근사. 측정이 아니다. 무엇으로 근사했는지가 approximatedBy 에 있다.',
  at: new Date().toISOString(),
  step: 'vista',
  segments: n,
  '//🔴이것은무엇이아닌가': [
    '경치를 측정한 값이 **아니다**. 로드뷰 이미지는 수집이 금지돼 있고(CLAUDE.md 1절) Mapillary 토큰은 아직 없다.',
    '세 축을 합친 "경치 점수" 를 여기서 내지 **않는다**. 가중치는 짝 비교 실험(docs/FIELD-STUDY.md)이 정한다.',
    '손으로 가중치를 정하면 그 결정을 뒤집는 것이므로, 합산 필드가 아예 없다.',
  ],
  approximatedBy: {
    seaDistM: {
      approximates: '물가 근접 (WALKABILITY.md 3절)',
      how: `DEM 고도 ≤ ${SEA_M}m(해수면) 픽셀을 포함하는 ${(meta.metersPerPixel*DOWN).toFixed(0)}m 셀 중, bbox 가장자리의 열린 바다와 이어진 것까지의 최단거리. 구간을 ${STEP_M}m 마다 찍어 최근접값을 쓴다.`,
      '🔴어떻게바다거리를냈나': '해안선 데이터로 낸 것이 **아니다.** collect/pbf_extract.py 는 highway·foot 태그가 붙은 way 만 뽑았으므로 natural=coastline · natural=water · waterway 가 추출본에 **하나도 없다**(실측: 0건). 그래서 DEM 의 해저 지형(terrarium 타일은 바다를 0m 또는 음수로 담는다)에서 바다를 되만들었다. bbox 남쪽 경계까지의 거리 같은 거친 대체가 아니라, 실제 부산 해안 형상이 나온다.',
      '🔴못한것': '강·호수는 대체로 못 잡는다. 강 수면은 해수면보다 위에 있어서(실측: 낙동강 하구 +0.8m, 중류 +3.0m) 이 정의에 안 들어온다. 낙동강 중류 지점의 이 축 값은 아래 seaMaskCalibration 의 probeNakdongMidM 이고, 그것이 곧 오차다. 강변 산책로를 "물가" 로 보려면 수계 데이터가 따로 필요하다.',
      unit: 'm',
    },
    greenPoiDistM: {
      approximates: '녹지 근접 (WALKABILITY.md 3절)',
      how: `OSM 녹지 **점** 피처까지의 최단 대권거리. 기준집합 ${greenPts.length}개.`,
      referenceSet: greenKinds,
      '🔴이축을믿지마라': `공원·숲의 **면(폴리곤)이 추출본에 없다.** collect/pbf_extract.py 는 POI 를 **노드만** 내보내고(2차 루프), leisure=park 인 way·relation 은 highway 태그가 없어 버려졌다. 그래서 기준집합이 ${greenPts.length}개 점뿐이고, 금정산·장산·황령산 같은 부산 최대 녹지가 전부 빠져 있다. 이 축은 녹지까지의 거리를 **체계적으로 과대평가**한다. "가장 가까운 공원까지의 거리" 로 읽으면 틀린다.`,
      '고치는길': 'pbf_extract.py 에 leisure/landuse/natural way 추출을 더한다. 이 파일이 아니라 거기서 고쳐야 한다.',
      unit: 'm',
    },
    relElevM: {
      approximates: '상대 고도 — 주변보다 높은가 (WALKABILITY.md 3절)',
      how: `구간 평균고도 − 구간 중심을 둘러싼 정사각 이웃의 평균고도. 반변 ${REL_RADII_M.join('m · ')}m 두 가지를 **둘 다 낸다** — 어느 반경이 맞는지는 짝 비교가 정할 일이라 여기서 고르지 않는다.`,
      '🔴이것은전망이아니라지형이다': `이웃이 **대칭**이라 이 축이 재는 것은 "봉우리인가 골짜기인가" 이지 "전망이 트였는가" 가 아니다. 산비탈 도로는 아래쪽으로 전망이 열려 있어도 위쪽 산이 평균을 끌어올려 **음수**가 된다. 실측: 망양로(중구·서구 산복도로)는 같은 도로인데도 구간에 따라 -67m ~ +55m 로 갈린다. 반경 ${REL_RADII_M.at(-1)}m 기준 전체 구간의 ${relNegPct}% 가 음수인데, 그것은 부산 도로의 대다수가 골짜기·비탈을 따라간다는 뜻이지 전망이 없다는 뜻이 아니다. 진짜 전망을 재려면 가시권(viewshed) 계산이 필요하고 그건 이 파일이 한 일이 아니다.`,
      '🔴주의': '이웃 평균에 바다(0m)가 들어간다. 그래서 해안 언덕은 상대고도가 높게 나오고, 이 축은 seaDistM 과 상관이 생긴다 — 아래 axisCorrelation 에 실측값이 있다. 가중치를 추정할 때 이 상관을 모르면 계수가 서로를 잡아먹는다.',
      '해상도': `고도는 ${(meta.metersPerPixel*DOWN).toFixed(0)}m 셀 평균이다. 3.9m 격자로 보간하지 않았다 — 원본이 SRTM ~30m 라 3.9m 정밀도는 가짜다.`,
      unit: 'm',
    },
  },
  // 🔴 보유율 = 값을 낼 수 있었던 구간 비율
  coverage: Object.fromEntries(Object.entries(cover).map(([k, v]) => [k, +(v * 100).toFixed(2) + '%'])),
  distribution: {
    seaDistM: dist(acc.sea), greenPoiDistM: dist(acc.green), elevM: dist(acc.elev),
    ...Object.fromEntries(REL_RADII_M.map(r => [`relElev${r}M`, dist(accRel[r])])),
  },
  axisCorrelation: {
    '//': '세 축이 서로 겹치는 정도(피어슨). 1 에 가까우면 같은 것을 두 번 재는 것이다.',
    seaDist_vs_greenPoiDist: pearson(pairAligned.sea, pairAligned.green),
    seaDist_vs_relElev1000: pearson(pairAligned.sea, pairAligned.rel),
    greenPoiDist_vs_relElev1000: pearson(pairAligned.green, pairAligned.rel),
  },
  seaMaskCalibration: {
    rule: '해수면(0m)은 물리 상수이므로 임계값을 손으로 흔들지 않는다. 이 표는 흔들었으면 어떻게 됐는지를 기록으로 남긴 것이다.',
    whyNotHigherThreshold: `DEM 수직오차가 ±5m 다(data/raw/dem/_meta-z15.json). 0m 위의 어떤 임계값도 그 잡음 안에 있어서, 대조점 ${seaCtrl.length}개에 맞춰 1m·2m 로 올리는 것은 잡음에 파라미터를 맞추는 것이다 — CLAUDE.md 7절이 말하는 "그럴듯한데 틀린 숫자".`,
    whyBorderConnected: '연결필터를 빼면 DEM 잡음이 만든 내륙의 고립 셀 때문에 표고 350m 산(금정산)에서 바다가 3km 로 나온다. 표의 두 번째 줄이 그 숫자다.',
    controlSet: `물가 대조점 ${seaCtrl.length}개 (OSM leisure=marina/slipway · natural=beach · seamark:type=harbour · man_made=pier 노드)`,
    controlP90LimitM: CTRL_P90_LIMIT_M,
    chosen: seaSweep[0].def,
    rows: seaSweep,
    '//대조점최악': '대조점 중 가장 먼 것들은 낙동강 안의 마리나다. 마스크의 결함이 아니라 위에 적은 "강은 못 잡는다" 가 그대로 드러난 것이다.',
  },
  dem: {
    zoom: ZOOM, downsample: DOWN, cellM: +(meta.metersPerPixel * DOWN).toFixed(2),
    grid: `${GW}x${GH}`, tilesRead: tilesRead, tilesAbsent,
    surfaceElevMinM: 0, surfaceElevMaxM: +demMax.toFixed(1),
    rawElevMinM: +demRawMin.toFixed(1),
    '🔴해저지형을눌렀다': `고도 축은 해수면 아래를 0m 로 눌렀다. 원본 최저 평균고도는 ${demRawMin.toFixed(0)}m 이고, -300m 아래 셀이 ${bathyOutliers}개 있다 — 한국해협 실제 최대 수심은 약 200m 이므로 그 값들은 타일 원본의 이상값이다. 누르지 않으면 바다 위 교량 구간의 고도가 해저 깊이가 되고(실측 -374m), 해안 구간의 이웃 평균이 해저로 끌려가 상대고도가 부풀려진다.`,
    bathymetryOutlierCells: bathyOutliers,
    seaCellsRaw: rawSeaN, seaCellsBorderConnected: seaN,
    isolatedCellsDropped: rawSeaN - seaN,
    caveat: meta.caveat,
  },
  notes: {
    segmentsOutsideTargetBbox: outsideBbox,
    segmentsWithNoGridPoint: noGridPts,
    joinsWith: 'data/staged/segment-slope.ndjson — (topic, id) 로 맞물린다. 줄 수와 순서가 같다.',
    notUsed: `tourism=viewpoint 노드가 추출본에 ${viewpoints}개 있다. WALKABILITY.md 3절이 "전망대와의 거리" 도 후보로 적었지만 이 작업은 축을 셋으로 정했으므로 넣지 않았다. 넣을 값이 있는 데이터다.`,
    representativeStat: `구간 대표값은 물가·녹지는 구간 내 최근접(min), 고도는 구간 평균. 최댓값을 대표값으로 쓴 곳은 없다.`,
  },
}

await writeFile(join(OUT, '_vista-summary.json'), JSON.stringify(summary, null, 2))
// 🔴 지문은 `data/staged/_vista-run/` 에 둔다. `data/staged/_run.json` 하나에 쓰면
//    단계마다 서로를 덧씌워서, 여러 단계가 같은 폴더에 산출물을 내는 지금 구조에서는
//    **마지막에 돈 것만 남는다.** 지문의 목적이 "이 숫자는 무엇으로 만들었나" 인데
//    그게 지워지면 지문을 남긴 의미가 없다. mlops/manifest.mjs 는 고치지 않았으므로
//    단계마다 폴더를 하나씩 쓰는 방식으로 피한다.
stamp(join(OUT, '_vista-run'), {
  step: 'vista',
  inputs: [join(PBF, 'road.ndjson'), join(PBF, 'walk.ndjson'), join(PBF, 'stairs.ndjson'),
           join(PBF, 'poi.ndjson'), join(OUT, 'segment-slope.ndjson'), join(DEM, '_meta-z15.json')],
  params: { seaLevelM: SEA_M, downsample: DOWN, stepM: STEP_M, relRadiiM: REL_RADII_M,
            demTilesRead: tilesRead, borderConnectedSeaOnly: true },
  result: { segments: n, coverage: cover, greenReferencePoints: greenPts.length,
            seaControlP90M: ctrlP90 == null ? null : +ctrlP90.toFixed(0) },
})

// ── 10. 화면 ─────────────────────────────────────────────────────────────────
console.log(`\n🌊 물가·녹지·상대고도 — **경치의 근사다. 측정이 아니다.**`)
console.log(`   ${rel(join(OUT, 'segment-vista.ndjson'))}  구간 ${n.toLocaleString()}개`)
console.log(`\n보유율 (값을 낼 수 있었던 구간 비율)`)
for (const [k, v] of Object.entries(cover)) console.log(`   ${k.padEnd(14)} ${(v*100).toFixed(2)}%`)
console.log(`\n분포`)
for (const [k, d] of Object.entries(summary.distribution))
  console.log(`   ${k.padEnd(14)} p10=${String(d.p10).padStart(7)} p50=${String(d.p50).padStart(7)} p90=${String(d.p90).padStart(8)} max=${String(d.max).padStart(8)}`)
console.log(`\n바다 마스크 대조점 ${ctrlD.length}개: p50=${q(ctrlD,.5)}m p90=${ctrlP90?.toFixed(0)}m max=${ctrlD.at(-1)?.toFixed(0)}m (한계 ${CTRL_P90_LIMIT_M}m)`)
console.log(`녹지 기준점 ${greenPts.length}개 — 🔴 공원·숲 폴리곤이 추출본에 없어 이 축은 거리를 과대평가한다`)
console.log(`축 상관: 바다↔녹지 ${summary.axisCorrelation.seaDist_vs_greenPoiDist} · 바다↔상대고도 ${summary.axisCorrelation.seaDist_vs_relElev1000} · 녹지↔상대고도 ${summary.axisCorrelation.greenPoiDist_vs_relElev1000}`)

if (fail.length) {
  console.error(`\n🔴 불변식 ${fail.length}건 실패`)
  for (const f of fail.slice(0, 25)) console.error(`   ${f}`)
  process.exit(1)
}
console.log('\n불변식 전부 통과')
