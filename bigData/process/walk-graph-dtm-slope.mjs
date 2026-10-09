#!/usr/bin/env node
/**
 * 걷는 길 파일(GBWG v2)의 경사를 **국토지리정보원 수치지형도로 만든 지면 고도(DTM)** 로 다시 잰다 — S15P21E201-2039.
 *
 * ── 왜 ─────────────────────────────────────────────────────────────────────
 *
 * 지금까지 경사는 SRTM 30m(2000년 위성 레이더 측량) 고도에서 냈다. SRTM 은 DSM(표면 모델 — 건물·나무 꼭대기까지 잰 높이)에
 * 가깝고 수직 오차가 ±5m 라, 건물이 빽빽한 평지 도심(서면)이 노랑·빨강으로 나왔다(gabolle-archive/slope-audit-20261009.md).
 * 이번에는 국토지리정보원 1:5,000 수치지형도 Ver2.0 의 **등고선(5m 간격)·표고점(측량한 땅 높이 점)·해안선(0m)** 을
 * 이어 붙여 만든 DTM(지면 모델 — 건물을 뺀 맨땅 높이)을 쓴다. 만드는 쪽은 process/ngii-dtm.py 이고, 결과는 지금
 * 파이프라인과 같은 terrarium(높이를 PNG 색으로 담는 타일 형식) z15 타일이다.
 *
 * ── 하는 일 ─────────────────────────────────────────────────────────────────
 *
 * 경사를 아는 길(값 ≥ 0)이고 계단이 아닌 길마다, slope.mjs 와 같은 계산을 DTM 으로 다시 한다.
 *   10m 간격 표본 → 기준선(BASELINE_M) 떨어진 두 점의 높이 차 ÷ 거리 → 절댓값의 중앙값(p50) → 천분율(‰)
 * 손대지 않는 것: 계단 · 모르는 길(-1 = 다리·터널·30m 미만) · 표본 하나라도 고도가 없는 길(원래 값 유지, 개수를 센다).
 * 이전 단계의 「평지 잡음 바닥」(walk-graph-flat-floor.mjs)은 SRTM 오차를 누르던 임시 대책이라 이 단계가 덮어쓴다.
 *
 * 사용
 *   node process/walk-graph-dtm-slope.mjs --tiles-out <파일>          필요한 타일 목록(x_y 줄 단위)만 쓴다
 *   node process/walk-graph-dtm-slope.mjs --dem <폴더> [--graph <파일>] [--out <파일>] [--baseline <m>]
 *
 * 종료 코드
 *   0 정상   1 입력이 깨졌다   2 입력이 없다
 */
import { existsSync, readFileSync, writeFileSync } from 'node:fs'
import { createHash } from 'node:crypto'
import { basename, dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { gunzipSync } from 'node:zlib'
import { buildGraphV2, gzipDeterministic, parseGraph } from './walk-graph-shade.mjs'
import { decodePNG, terrariumToElevation } from './png.mjs'

/** 경사를 재는 두 점 사이 거리. SRTM 때는 잡음을 누르려 100m 였다 — DTM 에서 다시 정한 값은 머리말 아래 summary 에 남는다. */
export const BASELINE_M = 50
const STEP_M = 10
const ZOOM = 15
const TILE = 256

const R = 6371000
const rad = (d) => (d * Math.PI) / 180
function dist(a, b) {
  const dLat = rad(b.lat - a.lat), dLon = rad(b.lon - a.lon)
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(rad(a.lat)) * Math.cos(rad(b.lat)) * Math.sin(dLon / 2) ** 2
  return 2 * R * Math.asin(Math.sqrt(h))
}
function resample(geom) {
  const pts = [{ ...geom[0], s: 0 }]
  let acc = 0
  for (let i = 1; i < geom.length; i++) {
    const a = geom[i - 1], b = geom[i], d = dist(a, b)
    if (d === 0) continue
    for (let t = STEP_M - (acc % STEP_M); t < d; t += STEP_M) pts.push({ lat: a.lat + (b.lat - a.lat) * (t / d), lon: a.lon + (b.lon - a.lon) * (t / d), s: acc + t })
    acc += d
    pts.push({ ...b, s: acc })
  }
  return { pts, length: acc }
}
const gx = (lon) => ((lon + 180) / 360) * 2 ** ZOOM * TILE
const gy = (lat) => { const r = rad(lat); return ((1 - Math.log(Math.tan(r) + 1 / Math.cos(r)) / Math.PI) / 2) * 2 ** ZOOM * TILE }
const geometryOf = (g, way) => Array.from(way.nodes, (i) => ({ lat: g.lat[i] / 1e7, lon: g.lon[i] / 1e7 }))
const isTarget = (way) => !way.stairs && way.slope >= 0

/** slope.mjs 와 같은 p50 경사(비율). 고도가 하나라도 없으면 null. */
export function p50Slope(pts, elev, baselineM) {
  if (elev.some((v) => v == null || !Number.isFinite(v))) return null
  const slopes = []
  for (let i = 0; i < pts.length; i++) {
    let j = i
    while (j < pts.length - 1 && pts[j].s - pts[i].s < baselineM) j++
    const run = pts[j].s - pts[i].s
    if (run < baselineM * 0.6) break
    slopes.push((elev[j] - elev[i]) / run)
  }
  const length = pts.at(-1).s
  if (!slopes.length) slopes.push((elev.at(-1) - elev[0]) / Math.max(length, 1))
  const abs = slopes.map(Math.abs).sort((a, b) => a - b)
  return abs[Math.floor(abs.length * 0.5)]
}

export function createReader(demDir) {
  const tiles = new Map()
  const tile = (tx, ty) => {
    const k = `${tx}_${ty}`
    if (!tiles.has(k)) {
      const p = join(demDir, String(ZOOM), `${k}.png`)
      tiles.set(k, existsSync(p) ? terrariumToElevation(decodePNG(readFileSync(p))) : null)
    }
    return tiles.get(k)
  }
  const px = (X, Y) => {
    const tx = Math.floor(X / TILE), ty = Math.floor(Y / TILE)
    const t = tile(tx, ty)
    if (!t) return null
    const v = t[(Y - ty * TILE) * TILE + (X - tx * TILE)]
    return v < -1000 ? null : v // -32768 = 도엽 밖(ngii-dtm.py)
  }
  return (lat, lon) => {
    const X = gx(lon) - 0.5, Y = gy(lat) - 0.5
    const x0 = Math.floor(X), y0 = Math.floor(Y), fx = X - x0, fy = Y - y0
    const q = [px(x0, y0), px(x0 + 1, y0), px(x0, y0 + 1), px(x0 + 1, y0 + 1)]
    if (q.some((v) => v == null)) return null
    return q[0] * (1 - fx) * (1 - fy) + q[1] * fx * (1 - fy) + q[2] * (1 - fx) * fy + q[3] * fx * fy
  }
}

/** 길마다 경사를 다시 잰다. 그래프를 제자리에서 고치고 통계를 돌려준다. */
export function applyDtmSlope(g, elevAt, baselineM = BASELINE_M) {
  const stats = { targets: 0, changed: 0, missingElevation: 0 }
  for (const way of g.ways) {
    if (!isTarget(way)) continue
    stats.targets++
    const { pts } = resample(geometryOf(g, way))
    const s = p50Slope(pts, pts.map((p) => elevAt(p.lat, p.lon)), baselineM)
    if (s == null) { stats.missingElevation++; continue }
    const next = Math.min(32767, Math.round(s * 1000))
    if (next !== way.slope) { way.slope = next; stats.changed++ }
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
  if (!existsSync(graphPath)) { console.error(`입력이 없다: ${graphPath}`); process.exit(2) }
  const graphBytes = readFileSync(graphPath)
  const g = parseGraph(gunzipSync(graphBytes))
  if (g.version !== 2) throw new Error('그늘이 붙은 v2 를 넣는다')

  const tilesOut = arg('--tiles-out')
  if (tilesOut) {
    const need = new Set()
    for (const way of g.ways) {
      if (!isTarget(way)) continue
      for (const p of resample(geometryOf(g, way)).pts) {
        const X = gx(p.lon), Y = gy(p.lat)
        for (const dx of [-1, 1]) for (const dy of [-1, 1]) need.add(`${Math.floor((X + dx) / TILE)}_${Math.floor((Y + dy) / TILE)}`)
      }
    }
    writeFileSync(tilesOut, [...need].sort().join('\n') + '\n')
    console.log(`타일 ${need.size}장`)
    return
  }

  const demDir = arg('--dem')
  if (!demDir || !existsSync(join(demDir, String(ZOOM)))) { console.error(`DTM 타일 폴더가 없다: ${demDir}`); process.exit(2) }
  const baselineM = Number(arg('--baseline', BASELINE_M))
  const outPath = resolve(arg('--out', graphPath))
  const stats = applyDtmSlope(g, createReader(resolve(demDir)), baselineM)
  const out = gzipDeterministic(buildGraphV2(g))
  writeFileSync(outPath, out)
  const summary = {
    step: 'process/walk-graph-dtm-slope',
    builtAt: new Date().toISOString().slice(0, 10),
    input: { path: basename(graphPath), sha256_16: sha16(graphBytes) },
    output: { path: basename(outPath), bytes: out.length, sha256_16: sha16(out) },
    counts: stats,
    rules: {
      elevation: '국토지리정보원 1:5,000 수치지형도 Ver2.0(2025) 등고선·표고점·해안선 → 5m DTM(process/ngii-dtm.py) → terrarium z15',
      slope: `10m 표본 · 기준선 ${baselineM}m · 절댓값 p50 · ‰`,
      untouched: '계단 · 모름(-1) · 고도 못 뽑은 길(원래 값)',
      supersedes: 'flatFloor (SRTM 오차용 임시 대책 — 이 값이 덮어쓴다)',
    },
  }
  const metaPath = join(dirname(outPath), 'walk-graph.json')
  if (existsSync(metaPath)) {
    const meta = JSON.parse(readFileSync(metaPath, 'utf8'))
    meta.dtmSlope = summary
    writeFileSync(metaPath, JSON.stringify(meta, null, 1) + '\n')
  }
  console.log(JSON.stringify(summary, null, 1))
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main().catch((e) => { console.error(e.message); process.exit(1) })
}
