#!/usr/bin/env node
/**
 * 걷는 길 파일(GBWG)의 「평지 잡음 바닥」 — S15P21E201-2023.
 *
 * ── 왜 필요한가 ─────────────────────────────────────────────────────────────
 *
 * 길의 경사는 SRTM 30m(2000년 위성 레이더 측량)을 보간한 고도 타일에서 낸다. 그 고도는 수직 오차가 ±5m 쯤이고
 * 건물 꼭대기까지 섞인 표면 높이라, 건물이 빽빽한 평지 도심에서 2~7% 의 가짜 경사가 생긴다. 2026-10-09 조사
 * (gabolle-archive/slope-audit-20261009.md)에서 서면 대로(길 20786, 481m)는 양끝 높이 차가 0.4% 인데
 * 7.4% 노랑으로 나왔고, 서면 중심은 길이로 27% 가 노랑·빨강이었다.
 *
 * ── 하는 일 ─────────────────────────────────────────────────────────────────
 *
 * 길마다 10m 간격으로 고도를 다시 뽑아(slope.mjs 와 같은 표본·같은 DEM 청소) **높낮이 폭 = 가장 높은 표본 − 가장 낮은
 * 표본**을 잰다. 폭이 고도 자료의 오차(5m) 안이면 그 길의 경사는 잰 것이 아니라 잡음이다 — 평탄으로 낮춘다.
 *   새 값 = min(원래 값, 양끝 높이 차 ÷ 길이, 49‰)   — 49‰ 는 지도에서 초록(5% 미만)의 위 끝
 * 손대지 않는 것: 계단(늘 빨강) · 모르는 길(-1, 다리·터널·30m 미만) · 이미 초록인 길 · 고도를 못 뽑은 길.
 *
 * 국토지리정보원 5m DEM 으로 바꾸기 전의 **임시 대책**이다. 그때는 이 단계를 빼고 경사를 처음부터 다시 낸다.
 *
 * ── 순서 ────────────────────────────────────────────────────────────────────
 *
 *   walk-graph.mjs(v1) → walk-graph-shade.mjs(v2, 그늘) → **이 스크립트**(v2 그대로, 경사 칸만 낮춘다)
 * 다시 돌려도 같은 결과다(멱등 — 이미 낮춘 길은 49‰ 이하라 대상이 아니다).
 *
 * 고도 타일(data/raw/dem/15/x_y.png, 커밋하지 않는다)이 없으면 --fetch 로 필요한 타일만 받는다(AWS terrarium,
 * collect/terrain.mjs 와 같은 곳).
 *
 * 사용
 *   node process/walk-graph-flat-floor.mjs [--fetch] [--graph <파일>] [--out <파일>] [--dem <폴더>]
 *
 * 종료 코드
 *   0 정상   1 입력이 깨졌다   2 입력이 없다
 */
import { existsSync, mkdirSync, readFileSync, writeFileSync } from 'node:fs'
import { createHash } from 'node:crypto'
import { basename, dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { gunzipSync } from 'node:zlib'
import { buildGraphV2, gzipDeterministic, parseGraph } from './walk-graph-shade.mjs'

/** 높낮이 폭이 이 안이면 잡음이다 — 고도 자료(SRTM)의 수직 오차. */
export const FLAT_RANGE_M = 5
/** 낮춘 값의 상한 — 지도의 초록(5% 미만)의 위 끝. */
export const FLAT_MAX_PERMILLE = 49

const STEP_M = 10
const ZOOM = 15
const TILE = 256
const BASE = 'https://s3.amazonaws.com/elevation-tiles-prod/terrarium'

/**
 * 길 하나의 새 경사(‰). 규칙은 머리말 그대로. 표본에 빈 값이 하나라도 있으면 폭을 믿을 수 없으므로 그대로 둔다.
 * @param {{slope:number, stairs:number, elevations:(number|null)[], lengthM:number}} w
 */
export function flooredSlope({ slope, stairs, elevations, lengthM }) {
  if (stairs || slope < 0 || slope <= FLAT_MAX_PERMILLE) return slope
  if (!elevations || elevations.length < 2 || elevations.some((e) => e == null || !Number.isFinite(e))) return slope
  if (!(lengthM > 0)) return slope
  const range = Math.max(...elevations) - Math.min(...elevations)
  if (range > FLAT_RANGE_M) return slope
  const ends = Math.round((Math.abs(elevations.at(-1) - elevations[0]) / lengthM) * 1000)
  return Math.min(slope, ends, FLAT_MAX_PERMILLE)
}

// ── 좌표·표본 (slope.mjs 와 같은 계산) ──────────────────────────────────────
const R = 6371000
const rad = (d) => (d * Math.PI) / 180
function dist(a, b) {
  const dLat = rad(b.lat - a.lat), dLon = rad(b.lon - a.lon)
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(rad(a.lat)) * Math.cos(rad(b.lat)) * Math.sin(dLon / 2) ** 2
  return 2 * R * Math.asin(Math.sqrt(h))
}
function resample(geom) {
  const pts = [{ ...geom[0] }]
  let acc = 0
  for (let i = 1; i < geom.length; i++) {
    const a = geom[i - 1], b = geom[i], d = dist(a, b)
    if (d === 0) continue
    for (let t = STEP_M - (acc % STEP_M); t < d; t += STEP_M) pts.push({ lat: a.lat + (b.lat - a.lat) * (t / d), lon: a.lon + (b.lon - a.lon) * (t / d) })
    acc += d
    pts.push({ ...b })
  }
  return { pts, length: acc }
}
const gx = (lon) => ((lon + 180) / 360) * 2 ** ZOOM * TILE
const gy = (lat) => { const r = rad(lat); return ((1 - Math.log(Math.tan(r) + 1 / Math.cos(r)) / Math.PI) / 2) * 2 ** ZOOM * TILE }

function geometryOf(g, way) {
  return Array.from(way.nodes, (i) => ({ lat: g.lat[i] / 1e7, lon: g.lon[i] / 1e7 }))
}

/** 대상 길 — 계단이 아니고, 경사를 알고, 초록이 아닌 길. */
const isCandidate = (way) => !way.stairs && way.slope > FLAT_MAX_PERMILLE

async function fetchTiles(g, demDir) {
  const need = new Set()
  for (const way of g.ways) {
    if (!isCandidate(way)) continue
    for (const p of resample(geometryOf(g, way)).pts) {
      const tx = Math.floor(gx(p.lon) / TILE), ty = Math.floor(gy(p.lat) / TILE)
      for (let dx = -1; dx <= 1; dx++) for (let dy = -1; dy <= 1; dy++) need.add(`${tx + dx}_${ty + dy}`)
    }
  }
  const dir = join(demDir, String(ZOOM))
  mkdirSync(dir, { recursive: true })
  const queue = [...need].filter((k) => !existsSync(join(dir, `${k}.png`)))
  console.error(`고도 타일 ${need.size}장 필요 · 받을 것 ${queue.length}장`)
  let failed = 0
  const worker = async () => {
    while (queue.length) {
      const k = queue.shift()
      const [x, y] = k.split('_')
      try {
        const res = await fetch(`${BASE}/${ZOOM}/${x}/${y}.png`, { signal: AbortSignal.timeout(30000) })
        if (!res.ok) throw new Error(`HTTP ${res.status}`)
        writeFileSync(join(dir, `${k}.png`), Buffer.from(await res.arrayBuffer()))
      } catch (e) { failed++; console.error(`  타일 ${k}: ${e.message}`) }
    }
  }
  await Promise.all(Array.from({ length: 4 }, worker))
  if (failed) throw new Error(`고도 타일 ${failed}장을 받지 못했다`)
}

/** 그래프의 경사 칸에 바닥을 깐다. 바꾼 길 수와 예시를 돌려준다. */
export function applyFlatFloor(g, elevAt) {
  const stats = { candidates: 0, floored: 0, missingElevation: 0 }
  for (let k = 0; k < g.ways.length; k++) {
    const way = g.ways[k]
    if (!isCandidate(way)) continue
    stats.candidates++
    const { pts, length } = resample(geometryOf(g, way))
    const elevations = pts.map((p) => elevAt(p.lat, p.lon))
    if (elevations.some((e) => e == null)) stats.missingElevation++
    const next = flooredSlope({ slope: way.slope, stairs: way.stairs, elevations, lengthM: length })
    if (next !== way.slope) { way.slope = next; stats.floored++ }
  }
  return stats
}

const sha16 = (buf) => createHash('sha256').update(buf).digest('hex').slice(0, 16)
function arg(name, fallback) {
  const i = process.argv.indexOf(name)
  return i > 0 ? process.argv[i + 1] : fallback
}

async function main() {
  const here = dirname(fileURLToPath(import.meta.url))
  const repo = resolve(here, '..', '..')
  const graphPath = resolve(arg('--graph', join(repo, 'backend', 'src', 'main', 'resources', 'geo', 'walk-graph.bin.gz')))
  const outPath = resolve(arg('--out', graphPath))
  const demDir = resolve(arg('--dem', join(here, '..', 'data', 'raw', 'dem')))
  if (!existsSync(graphPath)) { console.error(`입력이 없다: ${graphPath}`); process.exit(2) }
  const graphBytes = readFileSync(graphPath)
  const g = parseGraph(gunzipSync(graphBytes))
  if (g.version !== 2) throw new Error('그늘이 붙은 v2 를 넣는다 — walk-graph-shade.mjs 를 먼저 돌린다')
  if (process.argv.includes('--fetch')) await fetchTiles(g, demDir)
  if (!existsSync(join(demDir, String(ZOOM)))) { console.error(`고도 타일이 없다: ${demDir} (--fetch)`); process.exit(2) }

  const { createDemReader } = await import('./dem-clean.mjs')
  const dem = createDemReader({ demDir, zoom: ZOOM, tileSize: TILE })
  const px = (X, Y) => {
    const tx = Math.floor(X / TILE), ty = Math.floor(Y / TILE)
    const t = dem.tile(tx, ty)
    if (!t) return null
    const ix = Math.min(TILE - 1, Math.max(0, Math.floor(X) - tx * TILE))
    const iy = Math.min(TILE - 1, Math.max(0, Math.floor(Y) - ty * TILE))
    return t[iy * TILE + ix] / 10
  }
  const elevAt = (lat, lon) => {
    const X = gx(lon) - 0.5, Y = gy(lat) - 0.5
    const x0 = Math.floor(X), y0 = Math.floor(Y), fx = X - x0, fy = Y - y0
    const q = [px(x0, y0), px(x0 + 1, y0), px(x0, y0 + 1), px(x0 + 1, y0 + 1)]
    if (q.some((v) => v === null)) return null
    return q[0] * (1 - fx) * (1 - fy) + q[1] * fx * (1 - fy) + q[2] * (1 - fx) * fy + q[3] * fx * fy
  }

  const stats = applyFlatFloor(g, elevAt)
  const out = gzipDeterministic(buildGraphV2(g))
  writeFileSync(outPath, out)
  const summary = {
    step: 'process/walk-graph-flat-floor',
    builtAt: new Date().toISOString().slice(0, 10),
    input: { path: basename(graphPath), sha256_16: sha16(graphBytes) },
    output: { path: basename(outPath), bytes: out.length, sha256_16: sha16(out) },
    counts: stats,
    rules: { range: `높낮이 폭 ≤ ${FLAT_RANGE_M}m 이면 평탄`, value: `min(원래, 양끝 차÷길이, ${FLAT_MAX_PERMILLE}‰)`, untouched: '계단 · 모름(-1) · 이미 초록 · 고도 못 뽑은 길', dem: 'terrarium z15 + dem-clean.mjs (slope.mjs 와 같다)' },
  }
  const metaPath = join(dirname(outPath), 'walk-graph.json')
  if (existsSync(metaPath)) {
    const meta = JSON.parse(readFileSync(metaPath, 'utf8'))
    meta.flatFloor = summary
    writeFileSync(metaPath, JSON.stringify(meta, null, 1) + '\n')
  }
  console.log(JSON.stringify(summary, null, 1))
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main().catch((e) => { console.error(e.message); process.exit(1) })
}
