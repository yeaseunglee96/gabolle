#!/usr/bin/env node
/**
 * AWS Terrain Tiles — 고도 타일 수집 (인증 불필요)
 *
 * ⚠️ 이것은 임시 대체품이다. 국토지리정보원 5m DEM 이 오면 교체한다.
 *
 *    타일 격자는 z15 에서 픽셀당 약 5m 지만, 원본 데이터는 SRTM ~30m 를 보간한 것이다.
 *    격자가 촘촘하다고 정보가 촘촘한 게 아니다 — 폭 6m 짜리 골목의 경사는 뭉개진다.
 *    산복도로처럼 넓게 오르내리는 지형은 이걸로도 잡히고, 계단 골목은 안 잡힌다.
 *
 * terrarium 인코딩: 고도(m) = (R * 256 + G + B / 256) - 32768
 *
 *   node collect/terrain.mjs            대상 구역
 *   node collect/terrain.mjs --zoom 14  더 성기게
 */
import { writeFile, mkdir, readFile } from 'node:fs/promises'
import { existsSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { log } from '../lib/log.mjs'

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..')
const OUT  = join(ROOT, 'data/raw/dem')
const args = process.argv.slice(2)
const ZOOM = Number(args.includes('--zoom') ? args[args.indexOf('--zoom') + 1] : 15)
const BASE = 'https://s3.amazonaws.com/elevation-tiles-prod/terrarium'
const CONCURRENCY = 6


const lon2x = (lon, z) => Math.floor((lon + 180) / 360 * 2 ** z)
const lat2y = (lat, z) => {
  const r = lat * Math.PI / 180
  return Math.floor((1 - Math.log(Math.tan(r) + 1 / Math.cos(r)) / Math.PI) / 2 * 2 ** z)
}

async function main() {
  const area = JSON.parse(await readFile(join(ROOT, 'config/area.json'), 'utf8'))
  const b = area.target.bbox
  await mkdir(join(OUT, String(ZOOM)), { recursive: true })

  const x0 = lon2x(b.west, ZOOM),  x1 = lon2x(b.east, ZOOM)
  const y0 = lat2y(b.north, ZOOM), y1 = lat2y(b.south, ZOOM)   // y 는 북쪽이 작다

  const tiles = []
  for (let x = x0; x <= x1; x++) for (let y = y0; y <= y1; y++) tiles.push({ x, y })

  // z15 에서 위도 35도 픽셀 크기 = 156543.03 * cos(lat) / 2^z
  const mPerPx = 156543.03392 * Math.cos(35.12 * Math.PI / 180) / 2 ** ZOOM
  log(`${area.target.name}  z${ZOOM}  타일 ${tiles.length}개  (픽셀 ≈ ${mPerPx.toFixed(1)}m)`)

  let done = 0, failed = 0, skipped = 0
  const queue = [...tiles]

  const worker = async () => {
    while (queue.length) {
      const { x, y } = queue.shift()
      const dest = join(OUT, String(ZOOM), `${x}_${y}.png`)
      if (existsSync(dest)) { skipped++; continue }
      try {
        const res = await fetch(`${BASE}/${ZOOM}/${x}/${y}.png`, { signal: AbortSignal.timeout(30000) })
        if (!res.ok) throw new Error(`HTTP ${res.status}`)
        await writeFile(dest, Buffer.from(await res.arrayBuffer()))
        done++
      } catch (e) { failed++; log(`  ⚠ ${x}/${y}: ${e.message}`) }
    }
  }
  await Promise.all(Array.from({ length: CONCURRENCY }, worker))

  await writeFile(join(OUT, `_meta-z${ZOOM}.json`), JSON.stringify({
    at: new Date().toISOString(), zoom: ZOOM, bbox: b,
    tileRange: { x0, x1, y0, y1 }, tiles: tiles.length,
    metersPerPixel: Number(mPerPx.toFixed(2)),
    encoding: 'terrarium: elev_m = (R*256 + G + B/256) - 32768',
    source: 'AWS elevation-tiles-prod',
    caveat: '원본 SRTM ~30m 보간. 격자는 5m 지만 정보는 30m. 국가 5m DEM 으로 교체 예정.',
  }, null, 2))

  log(`받음 ${done} / 이미있음 ${skipped} / 실패 ${failed}`)
  if (failed) process.exitCode = 1     // 실패를 숨기지 않는다
}
main().catch(e => { console.error('치명:', e); process.exit(1) })
