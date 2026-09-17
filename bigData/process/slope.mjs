#!/usr/bin/env node
/**
 * 구간별 경사 계산 — DEM 타일 × OSM 도로/보행로
 *
 * 🔴 왜 계산하나: OSM 의 incline(경사) 태그는 실측 보유율이 1.2% 다.
 *    태그로는 아무것도 못 한다. 고도에서 만들어야 한다.
 *
 * ⚠️ 경사 산출 기준선(baseline) = 100m. 이 값은 실측으로 정했다.
 *
 *    해안 매립지(고도 12m 이하, 실제로는 평지)를 대조군으로 놓고 재보니:
 *      기준선  30m → 평지의 6% 가 "8% 이상 경사" 로 나온다  (전부 거짓)
 *      기준선  60m → 2%
 *      기준선 100m → 0%          ← 거짓 양성이 사라지는 지점
 *    같은 기준선에서 산복도로는 여전히 58% 가 8% 이상으로 남는다.
 *    즉 100m 에서 잡음만 죽고 신호는 산다.
 *
 *    이유: 타일 격자는 3.9m 지만 원본은 SRTM ~30m 를 보간한 것이고, 수직 오차가
 *    ±5m 쯤 된다. 30m 기준선에서 5m 오차는 곧 17% 경사다 — 없는 언덕이 생긴다.
 *    5m 국가 DEM 이 오면 process/calibrate-slope.mjs 를 다시 돌려 기준선을 다시 정한다.
 *
 * ⚠️ 대표값은 최댓값이 아니라 p90 이다. 최댓값은 잡음 표본 하나에 끌려간다.
 *
 * 🔴 부산 전역은 중구·동구의 241배다. 그래서 전부 스트리밍이다 —
 *    입력도 NDJSON 줄 단위, 출력도 NDJSON, DEM 타일도 필요할 때만 올린다.
 *    타일 3,127개를 전부 Float32 로 들면 820MB 다. 대부분은 바다라 안 쓴다.
 *
 *   node process/slope.mjs
 */
import { createReadStream, existsSync } from 'node:fs'
import { createInterface } from 'node:readline'
import { readFile, readdir, writeFile, mkdir, open } from 'node:fs/promises'
import { join, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
import { createDemReader } from './dem-clean.mjs'

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..')
const DEM  = join(ROOT, 'data/raw/dem')
const PBF  = join(ROOT, 'data/raw/pbf')
const OVP  = join(ROOT, 'data/raw/overpass')
const OUT  = join(ROOT, 'data/staged')
const ZOOM = 15, TILE = 256
const BASELINE_M = 100
const STEP_M = 10

const log = (...a) => console.log(new Date().toISOString().slice(11, 19), ...a)
const R = 6371000, rad = d => d * Math.PI / 180
const dist = (a, b) => {
  const dLat = rad(b.lat - a.lat), dLon = rad(b.lon - a.lon)
  const h = Math.sin(dLat/2)**2 + Math.cos(rad(a.lat))*Math.cos(rad(b.lat))*Math.sin(dLon/2)**2
  return 2 * R * Math.asin(Math.sqrt(h))
}

// ── DEM: 필요할 때만 올린다. 데시미터 Int16 으로 메모리를 절반으로 ──────────
// 🔴 원본 타일을 그대로 쓰지 않는다. process/dem-clean.mjs 가 먼저 청소한다 —
//    값 범위 밖(손상된 세로 실선)을 메우고, 매립지의 **가짜 혹**을 누른다.
//    calibrate-slope.mjs 도 같은 파일을 통해 읽는다. 두 곳이 다른 DEM 을 보면
//    "보정이 잰 것" 과 "경사가 쓴 것" 이 어긋난다 (S15P21E201-795).
//
// `--raw-dem` 을 주면 청소를 끄고 **원본 그대로** 계산한다. 쓰는 자리는 하나다:
// **청소가 무엇을 바꿨는지 나란히 보려고.** 이 값을 산출물로 쓰지 않는다.
const RAW_DEM = process.argv.includes('--raw-dem')
const dem = createDemReader({
  demDir: DEM, zoom: ZOOM, tileSize: TILE, debump: !RAW_DEM, rangeCheck: !RAW_DEM,
})
const tile = (tx, ty) => dem.tile(tx, ty)

const gx = lon => (lon + 180) / 360 * 2 ** ZOOM * TILE
const gy = lat => { const r = rad(lat)
  return (1 - Math.log(Math.tan(r) + 1 / Math.cos(r)) / Math.PI) / 2 * 2 ** ZOOM * TILE }

function px(X, Y) {
  const tx = Math.floor(X / TILE), ty = Math.floor(Y / TILE)
  const t = tile(tx, ty)
  if (!t) return null
  const ix = Math.min(TILE - 1, Math.max(0, Math.floor(X) - tx * TILE))
  const iy = Math.min(TILE - 1, Math.max(0, Math.floor(Y) - ty * TILE))
  return t[iy * TILE + ix] / 10
}

function elevAt(lat, lon) {
  const X = gx(lon) - 0.5, Y = gy(lat) - 0.5
  const x0 = Math.floor(X), y0 = Math.floor(Y), fx = X - x0, fy = Y - y0
  const q = [px(x0, y0), px(x0 + 1, y0), px(x0, y0 + 1), px(x0 + 1, y0 + 1)]
  if (q[0] === null || q[1] === null || q[2] === null || q[3] === null) return null
  return q[0]*(1-fx)*(1-fy) + q[1]*fx*(1-fy) + q[2]*(1-fx)*fy + q[3]*fx*fy
}

function resample(geom) {
  const pts = [{ ...geom[0], s: 0 }]
  let acc = 0
  for (let i = 1; i < geom.length; i++) {
    const a = geom[i-1], b = geom[i], d = dist(a, b)
    if (d === 0) continue
    for (let t = STEP_M - (acc % STEP_M); t < d; t += STEP_M)
      pts.push({ lat: a.lat + (b.lat-a.lat)*(t/d), lon: a.lon + (b.lon-a.lon)*(t/d), s: acc + t })
    acc += d
    pts.push({ ...b, s: acc })
  }
  return { pts, length: acc }
}

/** 입력원을 고른다: PBF 추출본(전역) 이 있으면 그것, 없으면 Overpass(구역). */
async function* ways() {
  const topics = ['road', 'walk', 'stairs']
  if (existsSync(join(PBF, 'road.ndjson'))) {
    for (const t of topics) {
      const p = join(PBF, `${t}.ndjson`)
      if (!existsSync(p)) continue
      const rl = createInterface({ input: createReadStream(p), crlfDelay: Infinity })
      for await (const line of rl) if (line) yield [t, JSON.parse(line)]
    }
  } else {
    for (const t of topics) {
      const p = join(OVP, `${t}.json`)
      if (!existsSync(p)) continue
      for (const el of JSON.parse(await readFile(p, 'utf8')).elements) yield [t, el]
    }
  }
}

async function main() {
  await mkdir(OUT, { recursive: true })
  const source = existsSync(join(PBF, 'road.ndjson')) ? 'pbf(전역)' : 'overpass(구역)'
  const nTiles = existsSync(join(DEM, String(ZOOM)))
    ? (await readdir(join(DEM, String(ZOOM)))).filter(f => f.endsWith('.png')).length : 0
  log(`입력 ${source} · DEM 타일 ${nTiles}개 (필요한 것만 올린다)`)

  // 🔴 기준선은 손으로 정하지 않는다. calibrate-slope.mjs 의 측정을 따른다.
  //    범위나 DEM 이 바뀌면 그 값도 바뀌므로, 어긋나면 여기서 알린다.
  const calPath = join(OUT, '_calibration.json')
  let calibration = null
  if (existsSync(calPath)) {
    calibration = JSON.parse(await readFile(calPath, 'utf8'))
    if (calibration.recommendedBaselineM !== BASELINE_M) {
      log(`🔴 기준선 불일치: 이 스크립트는 ${BASELINE_M}m, 보정 권장은 ${calibration.recommendedBaselineM}m`)
      log(`   BASELINE_M 을 고치거나 npm run calibrate 를 다시 도세요.`)
      process.exitCode = 1
    } else {
      log(`보정 확인 — 기준선 ${BASELINE_M}m, 평지 거짓양성 ${(calibration.rows.find(r => r.baselineM === BASELINE_M).flatFalsePositive * 100).toFixed(1)}%`)
    }
  } else {
    log('⚠ 보정 기록이 없습니다. npm run calibrate 를 먼저 도세요.')
  }

  const fh = await open(join(OUT, 'segment-slope.ndjson'), 'w')
  const w  = fh.createWriteStream()

  const hist = {}, byClass = {}
  let n = 0, noDem = 0, totalLen = 0, samples = 0, stairsN = 0, stairsSteps = 0
  let worstStairs = []
  const t0 = Date.now()

  for await (const [topic, el] of ways()) {
    if (!el.geometry || el.geometry.length < 2) continue
    const { pts, length } = resample(el.geometry)
    if (length < 5) continue
    samples += pts.length

    const elev = pts.map(p => elevAt(p.lat, p.lon))
    if (elev.some(v => v === null)) { noDem++; continue }

    const slopes = []
    for (let i = 0; i < pts.length; i++) {
      let j = i
      while (j < pts.length - 1 && pts[j].s - pts[i].s < BASELINE_M) j++
      const run = pts[j].s - pts[i].s
      if (run < BASELINE_M * 0.6) break
      slopes.push((elev[j] - elev[i]) / run)
    }
    if (!slopes.length) slopes.push((elev.at(-1) - elev[0]) / Math.max(length, 1))

    const abs = slopes.map(Math.abs).sort((a, b) => a - b)
    const maxSlope = abs.at(-1)
    const p90Slope = abs[Math.floor(abs.length * 0.9)]
    const p50Slope = abs[Math.floor(abs.length * 0.5)]
    let ascent = 0, descent = 0
    for (let i = 1; i < elev.length; i++) {
      const d = elev[i] - elev[i-1]
      if (d > 0) ascent += d; else descent -= d
    }

    n++; totalLen += length
    const cls = el.tags?.highway || topic
    byClass[cls] ??= { len: 0, sum: 0, n: 0 }
    byClass[cls].len += length; byClass[cls].sum += p50Slope; byClass[cls].n++

    const bucket = p90Slope >= 0.20 ? '20%+' : p90Slope >= 0.15 ? '15-20%'
                 : p90Slope >= 0.10 ? '10-15%' : p90Slope >= 0.08 ? '8-10%'
                 : p90Slope >= 0.04 ? '4-8%' : '0-4%'
    hist[bucket] = (hist[bucket] || 0) + length

    const stepEst = topic === 'stairs' ? Math.round(Math.max(ascent, descent) / 0.17) : null
    if (stepEst) {
      stairsN++; stairsSteps += stepEst
      worstStairs.push({ steps: stepEst, name: el.tags?.name ?? null, len: +length.toFixed(0),
                         lat: +el.geometry[0].lat.toFixed(5), lon: +el.geometry[0].lon.toFixed(5) })
      if (worstStairs.length > 400) {
        worstStairs.sort((a, b) => b.steps - a.steps)
        worstStairs = worstStairs.slice(0, 200)
      }
    }

    // 🔴 중점 좌표 — 이게 없으면 이 줄이 **어느 장소 옆인지 판정할 수 없다**
    //    (S15P21E201-1047, docs/PLACE-SLOPE.md). 장소 경사는 "반경 안의 구간"을
    //    모아서 내는데, 위치가 없으면 안인지 밖인지 가릴 수가 없다.
    //
    //    거리 기준 한가운데다 — 점 개수가 아니라 s(누적 거리)로 고른다. 꺾인 데가
    //    몰려 있는 길에서 인덱스 중간을 쓰면 한쪽으로 쏠린다.
    //
    //    🔴 긴 길은 점 하나로 대표가 안 된다. 그래서 length 를 같이 남긴다 —
    //    읽는 쪽이 "중점이 반경 + length/2 안인가" 로 넉넉하게 보면 된다.
    const midIdx = pts.findIndex((p) => p.s >= length / 2)
    const mid = pts[midIdx < 0 ? pts.length - 1 : midIdx]

    w.write(JSON.stringify({
      id: el.id, topic, highway: el.tags?.highway ?? null, name: el.tags?.name ?? null,
      length: +length.toFixed(1),
      lat: +mid.lat.toFixed(6), lon: +mid.lon.toFixed(6),
      p90Slope: +p90Slope.toFixed(4), p50Slope: +p50Slope.toFixed(4), maxSlope: +maxSlope.toFixed(4),
      ascent: +ascent.toFixed(1), descent: +descent.toFixed(1),
      stepCount: el.tags?.step_count ? Number(el.tags.step_count) : null, stepEst,
      inclineTag: el.tags?.incline ?? null, widthTag: el.tags?.width ?? null,
    }) + '\n')

    if (n % 20000 === 0) log(`  ${n.toLocaleString()}개 / ${(totalLen/1000).toFixed(0)} km / 타일 ${dem.stats.tilesLoaded}장`)
  }

  await new Promise(r => w.end(r))
  await fh.close()
  const secs = (Date.now() - t0) / 1000
  worstStairs.sort((a, b) => b.steps - a.steps)

  await writeFile(join(OUT, '_slope-summary.json'), JSON.stringify({
    at: new Date().toISOString(), source, zoom: ZOOM, baselineM: BASELINE_M, stepM: STEP_M,
    ways: n, samples, totalLengthKm: +(totalLen/1000).toFixed(1),
    demMissWays: noDem, tilesLoaded: dem.stats.tilesLoaded, tilesMissing: dem.stats.tilesMissing,
    elapsedSec: +secs.toFixed(2), histogramM: hist,
    stairs: { count: stairsN, estimatedSteps: stairsSteps, worst: worstStairs.slice(0, 30) },
    byClass: Object.fromEntries(Object.entries(byClass).map(([k, v]) =>
      [k, { km: +(v.len/1000).toFixed(1), medianSlope: +(v.sum/v.n).toFixed(4), ways: v.n }])),
    representativeStat: 'p90Slope (최댓값은 잡음에 끌려가므로 대표값으로 쓰지 않는다)',
    // 보정 수치를 여기에 베껴 적지 않는다 — 베끼면 낡는다. 측정 파일을 가리킨다.
    calibrationRef: 'data/staged/_calibration.json',
    calibrationAt: calibration?.at ?? null,
    calibrationOk: calibration ? calibration.recommendedBaselineM === BASELINE_M : null,
    // DEM 청소 내역 — 무엇을 걷어내고 잰 숫자인지 남긴다 (S15P21E201-795)
    demClean: RAW_DEM ? null : {
      repairedPx: dem.stats.repairedPx,
      bumps: dem.stats.bumps.length,
      pressedPx: dem.stats.pressedPx,
      ref: 'process/dem-clean.mjs — 문턱과 근거가 거기 있다',
    },
    demRaw: RAW_DEM || undefined,
    caveat: '고도 원본은 SRTM ~30m 보간, 수직오차 ±5m. 기준선으로 눌렀으나 짧은 급경사 골목은 여전히 뭉개진다. 5m 국가 DEM 으로 교체 필요.',
    caveatBumps: RAW_DEM
      ? '🔴 --raw-dem 으로 돌렸다. 매립지·모래해안의 가짜 혹이 그대로 들어 있다. 비교용이지 산출물이 아니다.'
      : '매립지·모래해안의 가짜 혹은 process/dem-clean.mjs 가 걷어냈다. 폭 98m 미만·높이 25m 초과의 진짜 저지대 둔덕은 같이 지워진다.',
  }, null, 2))

  log(`구간 ${n.toLocaleString()}개 / 샘플 ${samples.toLocaleString()}개 / ${secs.toFixed(1)}초`)
  log(`DEM 타일 ${dem.stats.tilesLoaded}장 사용, ${dem.stats.tilesMissing}장 없음, 고도 없어 건너뜀 ${noDem.toLocaleString()}개`)
  log(`DEM 청소 — 범위 밖 ${dem.stats.repairedPx.toLocaleString()}px 메움 · 가짜 혹 ${dem.stats.bumps.length}개 (${dem.stats.pressedPx.toLocaleString()}px) 누름`)

  console.log(`\n📐 경사별 연장 — p90, 기준선 ${BASELINE_M}m (전체 ${(totalLen/1000).toFixed(0)} km)`)
  for (const k of ['0-4%','4-8%','8-10%','10-15%','15-20%','20%+']) {
    const m = hist[k] || 0
    console.log(`  ${k.padEnd(7)} ${(m/1000).toFixed(1).padStart(8)} km  ${'█'.repeat(Math.round(m/totalLen*40))}`)
  }
  const steep = ['8-10%','10-15%','15-20%','20%+'].reduce((a,k)=>a+(hist[k]||0),0)
  console.log(`\n  🔴 8% 이상 = ${(steep/1000).toFixed(0)} km (${(steep/totalLen*100).toFixed(1)}%)`)
  if (stairsN) {
    console.log(`\n🪜 계단 ${stairsN.toLocaleString()}개 · 추정 총 ${stairsSteps.toLocaleString()}단`)
    for (const s of worstStairs.slice(0, 5))
      console.log(`   ${String(s.steps).padStart(4)}단  ${(s.name || '(이름없음)').padEnd(18)} ${s.len}m`)
  }
}
main().catch(e => { console.error('치명:', e); process.exit(1) })
