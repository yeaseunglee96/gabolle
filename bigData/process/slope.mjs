#!/usr/bin/env node
/**
 * 구간별 경사 계산 — DEM 타일 × OSM 도로/보행로
 *
 * 🔴 왜 계산하나: OSM 의 incline(경사) 태그는 대상 구역에서 1.2% 밖에 없다.
 *    태그로는 아무것도 못 한다. 고도에서 만들어야 한다.
 *
 * ⚠️ 경사 산출 기준선(baseline) = 100m. 이 값은 실측으로 정했다.
 *
 *    해안 매립지(고도 12m 이하, 실제로는 평지) 549개를 대조군으로 놓고 재보니:
 *      기준선  30m → 평지의 6% 가 "8% 이상 경사" 로 나온다  (전부 거짓)
 *      기준선  60m → 2%
 *      기준선 100m → 0%          ← 거짓 양성이 사라지는 지점
 *    같은 기준선에서 산복도로(60m 이상)는 여전히 58% 가 8% 이상으로 남는다.
 *    즉 100m 에서 잡음만 죽고 신호는 산다.
 *
 *    이유: 타일 격자는 3.9m 지만 원본은 SRTM ~30m 를 보간한 것이고, 수직 오차가
 *    ±5m 쯤 된다. 30m 기준선에서 5m 오차는 곧 17% 경사다 — 없는 언덕이 생긴다.
 *    5m 국가 DEM 이 오면 기준선을 30m 로 되돌릴 수 있다.
 *
 * ⚠️ 대표값은 최댓값이 아니라 p90 을 쓴다. 최댓값은 잡음 표본 하나에 끌려간다.
 *
 *   node process/slope.mjs
 */
import { readFile, readdir, writeFile, mkdir } from 'node:fs/promises'
import { join, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
import { decodePNG, terrariumToElevation } from './png.mjs'

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..')
const DEM  = join(ROOT, 'data/raw/dem')
const OVP  = join(ROOT, 'data/raw/overpass')
const OUT  = join(ROOT, 'data/staged')
const ZOOM = 15, TILE = 256
const BASELINE_M = 100         // 경사를 재는 기준 거리 (위 주석의 실측 근거)
const STEP_M = 10              // 형상 재샘플 간격

const log = (...a) => console.log(new Date().toISOString().slice(11, 19), ...a)
const R = 6371000, rad = d => d * Math.PI / 180
const dist = (a, b) => {
  const dLat = rad(b.lat - a.lat), dLon = rad(b.lon - a.lon)
  const h = Math.sin(dLat/2)**2 + Math.cos(rad(a.lat))*Math.cos(rad(b.lat))*Math.sin(dLon/2)**2
  return 2 * R * Math.asin(Math.sqrt(h))
}

const tiles = new Map()
const gx = lon => (lon + 180) / 360 * 2 ** ZOOM * TILE
const gy = lat => { const r = rad(lat)
  return (1 - Math.log(Math.tan(r) + 1 / Math.cos(r)) / Math.PI) / 2 * 2 ** ZOOM * TILE }

function px(X, Y) {                       // 전역 픽셀 → 고도. 타일 경계를 넘어도 된다
  const tx = Math.floor(X / TILE), ty = Math.floor(Y / TILE)
  const t = tiles.get(`${tx}_${ty}`)
  if (!t) return null
  const ix = Math.min(TILE - 1, Math.max(0, Math.floor(X) - tx * TILE))
  const iy = Math.min(TILE - 1, Math.max(0, Math.floor(Y) - ty * TILE))
  return t[iy * TILE + ix]
}

function elevAt(lat, lon) {               // 양선형 보간
  const X = gx(lon) - 0.5, Y = gy(lat) - 0.5
  const x0 = Math.floor(X), y0 = Math.floor(Y), fx = X - x0, fy = Y - y0
  const q = [px(x0, y0), px(x0 + 1, y0), px(x0, y0 + 1), px(x0 + 1, y0 + 1)]
  if (q.some(v => v === null)) return null
  return q[0]*(1-fx)*(1-fy) + q[1]*fx*(1-fy) + q[2]*(1-fx)*fy + q[3]*fx*fy
}

/** 형상을 STEP_M 간격으로 다시 찍는다 */
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

async function main() {
  await mkdir(OUT, { recursive: true })

  // 1. DEM 타일 적재
  const files = (await readdir(join(DEM, String(ZOOM)))).filter(f => f.endsWith('.png'))
  for (const f of files) {
    const [x, y] = f.replace('.png', '').split('_')
    tiles.set(`${x}_${y}`, terrariumToElevation(decodePNG(await readFile(join(DEM, String(ZOOM), f)))))
  }
  log(`DEM 타일 ${tiles.size}개 적재`)

  // 2. 도로/보행로 순회
  const out = [], hist = {}, byClass = {}
  let noDem = 0, totalLen = 0, samples = 0
  const t0 = Date.now()

  for (const topic of ['road', 'walk', 'stairs']) {
    for (const el of JSON.parse(await readFile(join(OVP, `${topic}.json`), 'utf8')).elements) {
      if (el.type !== 'way' || !el.geometry || el.geometry.length < 2) continue
      const { pts, length } = resample(el.geometry)
      if (length < 5) continue

      const elev = pts.map(p => elevAt(p.lat, p.lon))
      samples += pts.length
      if (elev.some(v => v === null)) { noDem++; continue }

      // BASELINE_M 창으로 경사
      const slopes = []
      for (let i = 0; i < pts.length; i++) {
        let j = i
        while (j < pts.length - 1 && pts[j].s - pts[i].s < BASELINE_M) j++
        const run = pts[j].s - pts[i].s
        if (run < BASELINE_M * 0.6) break
        slopes.push((elev[j] - elev[i]) / run)
      }
      if (!slopes.length) {                                  // 짧은 구간은 양 끝으로
        slopes.push((elev.at(-1) - elev[0]) / Math.max(length, 1))
      }
      const abs = slopes.map(Math.abs).sort((a, b) => a - b)
      const maxSlope  = abs.at(-1)
      const p90Slope  = abs[Math.floor(abs.length * 0.9)]
      const p50Slope  = abs[Math.floor(abs.length * 0.5)]
      const meanSlope = abs.reduce((a, b) => a + b, 0) / abs.length
      let ascent = 0, descent = 0
      for (let i = 1; i < elev.length; i++) {
        const d = elev[i] - elev[i-1]; d > 0 ? ascent += d : descent -= d
      }

      totalLen += length
      const cls = el.tags?.highway || topic
      byClass[cls] ??= { len: 0, maxSum: 0, n: 0 }
      byClass[cls].len += length; byClass[cls].maxSum += p50Slope; byClass[cls].n++

      const bucket = p90Slope >= 0.20 ? '20%+' : p90Slope >= 0.15 ? '15-20%'
                   : p90Slope >= 0.10 ? '10-15%' : p90Slope >= 0.08 ? '8-10%'
                   : p90Slope >= 0.04 ? '4-8%' : '0-4%'
      hist[bucket] = (hist[bucket] || 0) + length

      out.push({
        id: el.id, topic, highway: el.tags?.highway ?? null,
        name: el.tags?.name ?? null,
        length: +length.toFixed(1),
        p90Slope: +p90Slope.toFixed(4), p50Slope: +p50Slope.toFixed(4),
        maxSlope: +maxSlope.toFixed(4), meanSlope: +meanSlope.toFixed(4),
        ascent: +ascent.toFixed(1), descent: +descent.toFixed(1),
        stepCount: el.tags?.step_count ? Number(el.tags.step_count) : null,
        // 계단 단수 추정 — 표준 단높이 0.17m. 태그는 0.5% 밖에 없다
        stepEst: topic === 'stairs' ? Math.round(Math.max(ascent, descent) / 0.17) : null,
        inclineTag: el.tags?.incline ?? null,
        widthTag: el.tags?.width ?? null,
      })
    }
  }
  const secs = (Date.now() - t0) / 1000

  await writeFile(join(OUT, 'segment-slope.json'), JSON.stringify(out))
  await writeFile(join(OUT, '_slope-summary.json'), JSON.stringify({
    at: new Date().toISOString(), zoom: ZOOM, baselineM: BASELINE_M, stepM: STEP_M,
    ways: out.length, samples, totalLengthKm: +(totalLen/1000).toFixed(1),
    demMissWays: noDem, elapsedSec: +secs.toFixed(2), histogramM: hist,
    representativeStat: 'p90Slope (최댓값은 잡음에 끌려가므로 대표값으로 쓰지 않는다)',
    calibration: { control: '해안 매립지 549개', falsePositiveAt8pct: { '30m': 0.06, '60m': 0.02, '100m': 0.00 } },
    caveat: '고도 원본은 SRTM ~30m 보간, 수직오차 ±5m. 100m 기준선으로 눌렀으나 짧은 급경사 골목은 여전히 뭉개진다. 5m 국가 DEM 으로 교체 필요.',
  }, null, 2))

  log(`구간 ${out.length}개 / 샘플 ${samples.toLocaleString()}개 / ${secs.toFixed(2)}초`)
  if (noDem) log(`⚠ DEM 밖이라 건너뛴 구간 ${noDem}개`)

  console.log('\n📐 경사별 연장 (전체 ' + (totalLen/1000).toFixed(1) + ' km)')
  for (const k of ['0-4%','4-8%','8-10%','10-15%','15-20%','20%+']) {
    const m = hist[k] || 0
    console.log(`  ${k.padEnd(7)} ${(m/1000).toFixed(1).padStart(6)} km  ${'█'.repeat(Math.round(m/totalLen*40))}`)
  }
  const steep = ['8-10%','10-15%','15-20%','20%+'].reduce((a,k)=>a+(hist[k]||0),0)
  console.log(`\n  🔴 8% 이상 = ${(steep/1000).toFixed(1)} km (${(steep/totalLen*100).toFixed(1)}%) — 휠체어·캐리어 사실상 불가`)

  const stairs = out.filter(o => o.topic === 'stairs' && o.stepEst)
  if (stairs.length) {
    const tot = stairs.reduce((a,s)=>a+s.stepEst,0)
    const worst = stairs.sort((a,b)=>b.stepEst-a.stepEst).slice(0,5)
    console.log(`\n🪜 계단 ${stairs.length}개 · 추정 총 ${tot.toLocaleString()}단`)
    for (const s of worst) console.log(`   ${String(s.stepEst).padStart(4)}단  ${s.name || '(이름없음)'}  ${s.length}m`)
  }
}
main().catch(e => { console.error('치명:', e); process.exit(1) })
