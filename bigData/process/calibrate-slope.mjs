#!/usr/bin/env node
/**
 * 경사 기준선 보정 — slope.mjs 의 BASELINE_M 을 정하는 근거
 *
 * 방법: 해안 매립지(최고 고도 12m 이하) 도로를 "실제로는 평지" 대조군으로 놓는다.
 *       거기서 "8% 이상 경사" 가 나오면 그것은 전부 거짓 양성이다.
 *       거짓 양성이 1% 이하가 되는 가장 짧은 기준선을 고른다 —
 *       짧을수록 실제 신호를 덜 뭉갠다.
 *
 * 🔴 범위를 바꾸거나 DEM 을 국가 5m 로 교체하면 이 스크립트를 다시 돌려
 *    기준선을 다시 정한다. 5m 는 수직오차가 훨씬 작아 30m 로 되돌릴 수 있을 것이다.
 *
 * 🔴 결과를 data/staged/_calibration.json 에 쓴다. test/verify.mjs 가 그것을 읽어
 *    "지금 쓰는 기준선의 거짓 양성이 1% 이하인가" 를 불변식으로 검사한다.
 *
 *   node process/calibrate-slope.mjs
 */
import { createReadStream, existsSync } from 'node:fs'
import { createInterface } from 'node:readline'
import { readFile, writeFile, mkdir } from 'node:fs/promises'
import { join, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
import { createDemReader } from './dem-clean.mjs'

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..')
const DEM  = join(ROOT, 'data/raw/dem')
const PBF  = join(ROOT, 'data/raw/pbf')
const OVP  = join(ROOT, 'data/raw/overpass')
const OUT  = join(ROOT, 'data/staged')
const ZOOM = 15, TILE = 256
const BASELINES = [30, 60, 100, 150, 200]

const FLAT_MAX_M  = 12     // 이 아래는 해안 매립지 = 평지여야 한다
const HILLY_MIN_M = 60     // 이 위는 산복도로 = 실제로 가파르다

const log = (...a) => console.log(new Date().toISOString().slice(11, 19), ...a)
const R = 6371000, rad = d => d * Math.PI / 180
const dist = (a, b) => {
  const dLat = rad(b.lat - a.lat), dLon = rad(b.lon - a.lon)
  const h = Math.sin(dLat/2)**2 + Math.cos(rad(a.lat))*Math.cos(rad(b.lat))*Math.sin(dLon/2)**2
  return 2 * R * Math.asin(Math.sqrt(h))
}

// 🔴 slope.mjs 와 **똑같은 청소된 DEM** 을 본다 (process/dem-clean.mjs).
//    이게 어긋나면 보정이 잰 기준선이 경사 계산에 안 맞는다 (S15P21E201-795).
//
// `--raw-dem` 은 청소를 끄고 원본으로 잰다. **대조군이 오염됐는지 보는 용도**다 —
// 가짜 혹이 평지 도로를 12 m 위로 밀어 올려 대조군에서 빼 버리므로, 켜고 끄고
// 두 번 돌려 `flatWays` 개수를 비교하면 그 일이 실제로 일어났는지 눈에 보인다.
// 이 값을 기준선 산출물로 쓰지 않는다.
const RAW_DEM = process.argv.includes('--raw-dem')
const dem = createDemReader({
  demDir: DEM, zoom: ZOOM, tileSize: TILE, debump: !RAW_DEM, rangeCheck: !RAW_DEM,
})
const tile = (tx, ty) => dem.tile(tx, ty)
const gx = lon => (lon + 180) / 360 * 2 ** ZOOM * TILE
const gy = lat => { const r = rad(lat)
  return (1 - Math.log(Math.tan(r) + 1 / Math.cos(r)) / Math.PI) / 2 * 2 ** ZOOM * TILE }
function elevAt(lat, lon) {
  const X = gx(lon), Y = gy(lat)
  const t = tile(Math.floor(X / TILE), Math.floor(Y / TILE))
  if (!t) return null
  return t[(Math.floor(Y) % TILE) * TILE + (Math.floor(X) % TILE)] / 10
}

async function* ways() {
  const topics = ['road', 'walk']
  if (existsSync(join(PBF, 'road.ndjson'))) {
    for (const t of topics) {
      const p = join(PBF, `${t}.ndjson`)
      if (!existsSync(p)) continue
      const rl = createInterface({ input: createReadStream(p), crlfDelay: Infinity })
      for await (const line of rl) if (line) yield JSON.parse(line)
    }
  } else {
    for (const t of topics) {
      const p = join(OVP, `${t}.json`)
      if (!existsSync(p)) continue
      for (const el of JSON.parse(await readFile(p, 'utf8')).elements) yield el
    }
  }
}

/** 한 way 의 고도 단면을 누적거리와 함께 만든다 */
function profile(geom) {
  const e = geom.map(p => elevAt(p.lat, p.lon))
  if (e.some(v => v === null)) return null
  const s = [{ d: 0, h: e[0] }]
  let acc = 0
  for (let i = 1; i < geom.length; i++) {
    acc += dist(geom[i - 1], geom[i])
    s.push({ d: acc, h: e[i] })
  }
  return { profile: s, min: Math.min(...e), max: Math.max(...e) }
}

function slopesAt(prof, baseline) {
  const out = []
  for (let i = 0; i < prof.length; i++) {
    let j = i
    while (j < prof.length - 1 && prof[j].d - prof[i].d < baseline) j++
    const run = prof[j].d - prof[i].d
    if (run < baseline * 0.6) break
    out.push(Math.abs(prof[j].h - prof[i].h) / run)
  }
  return out
}

async function main() {
  await mkdir(OUT, { recursive: true })
  const source = existsSync(join(PBF, 'road.ndjson')) ? 'pbf(전역)' : 'overpass(구역)'
  const flat = [], hilly = []

  for await (const w of ways()) {
    if (!w.geometry || w.geometry.length < 4) continue
    const p = profile(w.geometry)
    if (!p) continue
    if (p.max <= FLAT_MAX_M) flat.push(p.profile)
    else if (p.min >= HILLY_MIN_M) hilly.push(p.profile)
  }
  log(`입력 ${source} — 대조군(해안 평지) ${flat.length.toLocaleString()}개 / 산지 ${hilly.length.toLocaleString()}개`)
  if (flat.length < 50) {
    console.error('🔴 대조군이 너무 적습니다. 해안이 포함된 범위여야 보정이 됩니다.')
    process.exit(1)
  }

  const rows = []
  for (const B of BASELINES) {
    const stat = set => {
      const all = []
      for (const p of set) all.push(...slopesAt(p, B))
      if (!all.length) return null
      all.sort((a, b) => a - b)
      return {
        p50: all[Math.floor(all.length * 0.5)],
        p90: all[Math.floor(all.length * 0.9)],
        over8: all.filter(v => v >= 0.08).length / all.length,
        n: all.length,
      }
    }
    const f = stat(flat), h = stat(hilly)
    if (!f || !h) continue
    rows.push({
      baselineM: B, flatFalsePositive: +f.over8.toFixed(4), flatP90: +f.p90.toFixed(4),
      hillyOver8: +h.over8.toFixed(4), hillyP50: +h.p50.toFixed(4),
    })
    console.log(`기준선 ${String(B).padStart(3)}m │ 평지 거짓양성 ${(f.over8 * 100).toFixed(1).padStart(4)}% (p90 ${(f.p90 * 100).toFixed(1)}%)  ‖  산지 8%↑ ${(h.over8 * 100).toFixed(0)}% (중앙 ${(h.p50 * 100).toFixed(1)}%)`)
  }

  // 거짓 양성이 1% 이하이면서 가장 짧은 기준선 = 신호를 가장 덜 뭉갠다
  const pick = rows.find(r => r.flatFalsePositive <= 0.01)
  console.log('')
  if (pick) {
    console.log(`✅ 권장 기준선: ${pick.baselineM}m  (평지 거짓양성 ${(pick.flatFalsePositive * 100).toFixed(1)}%, 산지 신호 ${(pick.hillyOver8 * 100).toFixed(0)}% 유지)`)
  } else {
    console.log('🔴 어느 기준선에서도 거짓양성이 1% 아래로 안 내려갑니다. DEM 을 바꿔야 합니다.')
  }

  await writeFile(join(OUT, '_calibration.json'), JSON.stringify({
    at: new Date().toISOString(), source, zoom: ZOOM,
    control: { flatMaxM: FLAT_MAX_M, flatWays: flat.length, hillyMinM: HILLY_MIN_M, hillyWays: hilly.length },
    demClean: { tiles: dem.stats.tilesLoaded, repairedPx: dem.stats.repairedPx, bumps: dem.stats.bumps.length, pressedPx: dem.stats.pressedPx },
    rows, recommendedBaselineM: pick ? pick.baselineM : null,
    rule: '평지 대조군의 8% 이상 비율(=거짓양성)이 1% 이하인 가장 짧은 기준선을 쓴다',
  }, null, 2))

  if (!pick) process.exitCode = 1
}
main().catch(e => { console.error('치명:', e); process.exit(1) })
