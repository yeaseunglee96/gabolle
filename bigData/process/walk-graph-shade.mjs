#!/usr/bin/env node
/**
 * 걷는 길 파일(GBWG)에 길마다 「그늘」을 붙인다 — S15P21E201-1895.
 *
 * ── 왜 필요한가 ─────────────────────────────────────────────────────────────
 *
 * 코스 지도가 사용자가 고른 조건(경사·그늘)대로 **경로 선 자체**를 칠하려면, 경로의 조각마다 그늘 값이 있어야 한다.
 * 그런데 서버의 걷는 길 그래프(GBWG v1)는 길마다 경사·계단만 들고 있어서 그늘을 줄 수 없다.
 *
 * 구간별 그늘(건물 그림자 하루 평균)은 process/shadow.mjs 가 재 둔 산출물(segment-shadow.ndjson)에 있지만
 * 그 파일은 저장소에 없다(data/ 는 커밋되지 않는다). 대신 앱이 지역별 지도 겹으로 들고 있는
 * frontend/public/layers/<지역>.json 에 같은 값이 [경사‰, 그림자%, 좌표] 로 들어 있다 — 그것을 읽는다.
 *
 * ── 하는 일 ─────────────────────────────────────────────────────────────────
 *
 *   GBWG v1(또는 v2) 파일 + 지도 겹 여섯 → GBWG v2 파일
 *
 * v2 는 v1 과 같되 **길마다 그늘 한 바이트**가 경사(2바이트) 뒤에 더해진다.
 *   0~100 = 그림자 %(건물 그림자 하루 평균, 100 이면 하루 종일 그늘) · 255 = 모름
 * 서버(WalkGraph.read)는 v1·v2 를 모두 읽는다. v1 을 읽으면 그늘은 전부 모름이다.
 *
 * ── 어떻게 짝을 맞추나 ──────────────────────────────────────────────────────
 *
 * 지도 겹의 구간과 그래프의 길을 **모양**으로 맞춘다 — 좌표를 소수 5자리(약 1m)로 반올림한 점 줄이 같으면 같은
 * 길이다. 방향은 가리지 않는다(뒤집힌 줄도 같은 길). 두 단계다.
 *   1단계  점 줄이 통째로 같다.
 *   2단계  1단계에서 못 맞춘 길 가운데, 처음·끝 점과 점 수가 같고 사이 점이 3m 안쪽으로 겹치는 것.
 * 그래도 못 맞춘 길(여섯 지역 밖, 다리·터널, 지도 겹을 만든 뒤 바뀐 길)은 **모름(255)**으로 둔다.
 * 이웃 길의 값으로 메우지 않는다 — 안 잰 것을 잰 것처럼 칠하면 화면이 거짓말을 한다.
 *
 * 한 모양에 값이 둘 이상이고 서로 다르면(여러 지역이 겹친 자리) 평균을 반올림한다. 같은 계산의 산출물이라 겹친
 * 자리도 보통 같은 값이다. 다르면 세어서 보고한다.
 *
 * ── 그래프를 다시 만들 때 ────────────────────────────────────────────────────
 *
 * process/walk-graph.mjs 로 걷는 길 파일을 다시 만들면 **v1(그늘 없음)** 이 나온다. 그대로 두면 서버는 그늘을 하나도
 * 모르게 되어 지도가 경사만 칠한다. 그래서 그래프를 다시 만든 뒤에는 이 스크립트를 다시 돌린다 — v1 을 넣어도, 이미
 * v2 인 파일을 넣어도(멱등) 된다. 서버 시험 WalkGraphResourceTest 가 「실어 둔 그래프에 그늘이 붙어 있는가」를 재서
 * 빠뜨리면 빨개진다. 지도 겹(frontend/public/layers)은 front/dev 에 있으므로 --layers 로 그 폴더를 가리킨다.
 *
 * ── 같은 입력이면 같은 파일 ────────────────────────────────────────────────
 *
 * 지도 겹은 파일 이름 순으로 읽고, gzip 머리의 「만든 OS」 바이트를 255(모름)로 고정한다 — 윈도우에서 돌려도
 * 리눅스에서 돌려도 같은 바이트가 나온다.
 *
 * 사용
 *   node process/walk-graph-shade.mjs
 *   node process/walk-graph-shade.mjs --graph <파일> --layers <폴더> --out <파일>
 *
 * 종료 코드
 *   0 정상   1 입력이 깨졌다   2 입력이 없다
 */
import { existsSync, readFileSync, readdirSync, writeFileSync } from 'node:fs'
import { createHash } from 'node:crypto'
import { basename, dirname, join, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'
import { gunzipSync, gzipSync } from 'node:zlib'

/** 그늘을 모른다는 표시(한 바이트). */
export const SHADE_UNKNOWN = 255

const INTERIOR_TOLERANCE_M = 3

/** 좌표(도) → 소수 5자리 정수(약 1.1m 칸). */
const r5 = (deg) => Math.round(deg * 1e5)

/**
 * GBWG 파일(gunzip 한 바이트)을 읽는다.
 * v1: 길 = [점 수(4) · 계단(1) · 경사‰(2) · 점 번호(4)×점 수]
 * v2: 길 = [점 수(4) · 계단(1) · 경사‰(2) · 그늘(1) · 점 번호(4)×점 수]
 * 모든 정수는 빅엔디언이고 점 좌표는 도 × 1e7 이다.
 */
export function parseGraph(buf) {
  if (buf.length < 20 || buf.toString('ascii', 0, 4) !== 'GBWG') throw new Error('걷는 길 파일(GBWG)이 아니다')
  let o = 4
  const version = buf.readInt32BE(o); o += 4
  if (version !== 1 && version !== 2) throw new Error(`GBWG v${version} 은 모른다 — v1·v2 만 읽는다`)
  const n = buf.readInt32BE(o); o += 4
  const w = buf.readInt32BE(o); o += 4
  const refs = buf.readInt32BE(o); o += 4
  const lat = new Int32Array(n)
  const lon = new Int32Array(n)
  for (let i = 0; i < n; i++) { lat[i] = buf.readInt32BE(o); lon[i] = buf.readInt32BE(o + 4); o += 8 }
  const ways = new Array(w)
  let seen = 0
  for (let k = 0; k < w; k++) {
    const count = buf.readInt32BE(o); o += 4
    const stairs = buf[o++]
    const slope = buf.readInt16BE(o); o += 2
    let shade = SHADE_UNKNOWN
    if (version === 2) shade = buf[o++]
    const nodes = new Int32Array(count)
    for (let i = 0; i < count; i++) { nodes[i] = buf.readInt32BE(o); o += 4 }
    seen += count
    ways[k] = { stairs, slope, shade, nodes }
  }
  if (seen !== refs) throw new Error(`점 참조 수가 머리말(${refs})과 다르다(${seen})`)
  if (o !== buf.length) throw new Error(`파일 끝에 남는 바이트가 있다(${buf.length - o})`)
  return { version, lat, lon, ways }
}

/** 그래프를 GBWG v2 바이트로 쓴다. 길마다 {@code shade} 가 0~100 또는 255 여야 한다. */
export function buildGraphV2(g) {
  const n = g.lat.length
  const w = g.ways.length
  let refs = 0
  for (const way of g.ways) refs += way.nodes.length
  const size = 20 + n * 8 + refs * 4 + w * 8
  const buf = Buffer.alloc(size)
  let o = 0
  buf.write('GBWG', o, 'ascii'); o += 4
  buf.writeInt32BE(2, o); o += 4
  buf.writeInt32BE(n, o); o += 4
  buf.writeInt32BE(w, o); o += 4
  buf.writeInt32BE(refs, o); o += 4
  for (let i = 0; i < n; i++) { buf.writeInt32BE(g.lat[i], o); buf.writeInt32BE(g.lon[i], o + 4); o += 8 }
  for (const way of g.ways) {
    buf.writeInt32BE(way.nodes.length, o); o += 4
    buf[o++] = way.stairs
    buf.writeInt16BE(way.slope, o); o += 2
    const shade = way.shade
    if (!(shade === SHADE_UNKNOWN || (Number.isInteger(shade) && shade >= 0 && shade <= 100))) {
      throw new Error(`그늘 값이 0~100 도 255 도 아니다: ${shade}`)
    }
    buf[o++] = shade
    for (let i = 0; i < way.nodes.length; i++) { buf.writeInt32BE(way.nodes[i], o); o += 4 }
  }
  return buf
}

/** 지도 겹 여러 개의 구간을 한 목록으로 — 파일 이름 순. 구간 = {@code { slope, shadow, pts }}(pts 는 반올림한 [x,y] 쌍의 평평한 배열). */
export function readLayerSegments(dir) {
  const files = readdirSync(dir).filter((f) => f.endsWith('.json')).sort()
  const segs = []
  for (const f of files) {
    const d = JSON.parse(readFileSync(join(dir, f), 'utf8'))
    if (!Array.isArray(d.segs)) throw new Error(`${f}: segs 배열이 없다`)
    for (const s of d.segs) {
      if (!Array.isArray(s) || s.length < 3 || s[1] == null || !Array.isArray(s[2]) || s[2].length < 4) continue
      const flat = s[2]
      const pts = new Int32Array(flat.length)
      for (let i = 0; i < flat.length; i++) pts[i] = r5(flat[i])
      segs.push({ slope: s[0], shadow: Math.max(0, Math.min(100, Math.round(s[1]))), pts, area: d.area ?? basename(f, '.json') })
    }
  }
  return { files, segs }
}

const keyOf = (pts) => pts.join(',')

function reversed(pts) {
  const out = new Int32Array(pts.length)
  const m = pts.length / 2
  for (let i = 0; i < m; i++) { out[2 * i] = pts[2 * (m - 1 - i)]; out[2 * i + 1] = pts[2 * (m - 1 - i) + 1] }
  return out
}

/** 그래프 길의 점 줄을 소수 5자리 정수 쌍의 평평한 배열로. */
function wayPoints(g, way) {
  const pts = new Int32Array(way.nodes.length * 2)
  for (let i = 0; i < way.nodes.length; i++) {
    pts[2 * i] = Math.round(g.lon[way.nodes[i]] / 100)
    pts[2 * i + 1] = Math.round(g.lat[way.nodes[i]] / 100)
  }
  return pts
}

/** 소수 5자리 칸 한 칸 ≈ 위도 1.11m · 경도 0.91m. 두 점 사이 거리(m)를 칸 수로 어림한다. */
function cellDistM(ax, ay, bx, by) {
  const dy = (ay - by) * 1.11
  const dx = (ax - bx) * 0.91
  return Math.hypot(dx, dy)
}

/**
 * 지도 겹 구간과 그래프 길을 맞춰 길마다 그늘(0~100, 모름 255)을 정한다.
 * @returns {{ shade: Uint8Array, stats: object }}
 */
export function matchShade(g, segs) {
  const exact = new Map() // 통째 모양 → 그늘 값들
  const ends = new Map() // 처음·끝·점 수 → 구간들
  const add = (map, key, item) => { const l = map.get(key); if (l) l.push(item); else map.set(key, [item]) }
  for (const s of segs) {
    const rev = reversed(s.pts)
    add(exact, keyOf(s.pts), s.shadow)
    const rk = keyOf(rev)
    if (rk !== keyOf(s.pts)) add(exact, rk, s.shadow)
    const m = s.pts.length / 2
    const a = `${s.pts[0]},${s.pts[1]}`
    const b = `${s.pts[2 * (m - 1)]},${s.pts[2 * (m - 1) + 1]}`
    add(ends, `${a}|${b}|${m}`, s)
    if (a !== b) add(ends, `${b}|${a}|${m}`, { ...s, pts: rev })
  }
  const stats = { layerSegments: segs.length, ways: g.ways.length, exact: 0, tolerant: 0, unmatched: 0, conflicts: 0, alreadyHad: 0, slopeAgree: 0, slopeCompared: 0 }
  const shade = new Uint8Array(g.ways.length).fill(SHADE_UNKNOWN)
  const pick = (values) => {
    const lo = Math.min(...values)
    const hi = Math.max(...values)
    if (lo !== hi) stats.conflicts++
    return Math.round(values.reduce((a, b) => a + b, 0) / values.length)
  }
  for (let k = 0; k < g.ways.length; k++) {
    const way = g.ways[k]
    if (way.nodes.length < 2) continue
    const pts = wayPoints(g, way)
    const hit = exact.get(keyOf(pts))
    if (hit) {
      shade[k] = pick(hit)
      stats.exact++
      continue
    }
    const m = pts.length / 2
    const cand = ends.get(`${pts[0]},${pts[1]}|${pts[2 * (m - 1)]},${pts[2 * (m - 1) + 1]}|${m}`)
    if (!cand) continue
    const ok = cand.filter((s) => {
      for (let i = 0; i < m; i++) if (cellDistM(pts[2 * i], pts[2 * i + 1], s.pts[2 * i], s.pts[2 * i + 1]) > INTERIOR_TOLERANCE_M) return false
      return true
    })
    if (ok.length) {
      shade[k] = pick(ok.map((s) => s.shadow))
      stats.tolerant++
    }
  }
  stats.matched = stats.exact + stats.tolerant
  // 짝이 안 지어진 지도 겹 구간 수 — 맞춘 길 수와는 다르다(한 구간이 여러 길에 맞을 수 있다).
  const usedKeys = new Set()
  for (let k = 0; k < g.ways.length; k++) if (shade[k] !== SHADE_UNKNOWN) usedKeys.add(keyOf(wayPoints(g, g.ways[k])))
  let unmatched = 0
  for (const s of segs) {
    const fwd = keyOf(s.pts)
    if (!usedKeys.has(fwd) && !usedKeys.has(keyOf(reversed(s.pts)))) unmatched++
  }
  stats.unmatched = unmatched
  // 경사가 같은 길인지 어림으로 본다(맞춘 길 가운데 경사가 둘 다 있고 ±10‰ 안쪽인 비율).
  const bySlope = new Map()
  for (const s of segs) bySlope.set(keyOf(s.pts), s.slope)
  for (let k = 0; k < g.ways.length; k++) {
    if (shade[k] === SHADE_UNKNOWN) continue
    const pts = wayPoints(g, g.ways[k])
    const s = bySlope.get(keyOf(pts)) ?? bySlope.get(keyOf(reversed(pts)))
    if (s == null || g.ways[k].slope < 0) continue
    stats.slopeCompared++
    if (Math.abs(s - g.ways[k].slope) <= 10) stats.slopeAgree++
  }
  return { shade, stats }
}

/** 같은 입력이면 같은 gzip 바이트 — OS 바이트를 255(모름)로 고정한다. */
export function gzipDeterministic(buf) {
  const gz = gzipSync(buf, { level: 9 })
  gz[4] = gz[5] = gz[6] = gz[7] = 0 // 수정 시각
  gz[9] = 255 // 만든 OS
  return gz
}

function sha16(buf) {
  return createHash('sha256').update(buf).digest('hex').slice(0, 16)
}

function arg(name, fallback) {
  const i = process.argv.indexOf(name)
  return i > 0 ? process.argv[i + 1] : fallback
}

async function main() {
  const here = dirname(fileURLToPath(import.meta.url))
  const repo = resolve(here, '..', '..')
  const graphPath = resolve(arg('--graph', join(repo, 'backend', 'src', 'main', 'resources', 'geo', 'walk-graph.bin.gz')))
  const layersDir = resolve(arg('--layers', join(repo, 'frontend', 'public', 'layers')))
  const outPath = resolve(arg('--out', graphPath))
  for (const p of [graphPath, layersDir]) {
    if (!existsSync(p)) { console.error(`입력이 없다: ${p}`); process.exit(2) }
  }
  const graphBytes = readFileSync(graphPath)
  const g = parseGraph(gunzipSync(graphBytes))
  const { files, segs } = readLayerSegments(layersDir)
  g.ways.forEach((way) => { way.shade = SHADE_UNKNOWN })
  const baseSha = sha16(buildGraphV2(g))
  const { shade, stats } = matchShade(g, segs)
  g.ways.forEach((way, k) => { way.shade = shade[k] })
  const out = gzipDeterministic(buildGraphV2(g))
  writeFileSync(outPath, out)

  const known = shade.reduce((n, s) => n + (s === SHADE_UNKNOWN ? 0 : 1), 0)
  const summary = {
    step: 'process/walk-graph-shade',
    format: 'GBWG v2 — 길마다 그늘 한 바이트(0~100 그림자 %, 255 모름)',
    builtAt: new Date().toISOString().slice(0, 10),
    inputs: [
      // baseSha256_16 = 그늘 칸을 모두 「모름」으로 돌려놓은 v2 바이트의 지문. v1 을 넣든 이미 v2 인 파일을 넣든
      // 같은 값이 나와, 다시 돌려도 「어느 그래프 위에 그늘을 붙였나」가 바뀌지 않는다.
      { path: basename(graphPath), version: `v${g.version}`, sha256_16: sha16(graphBytes), baseSha256_16: baseSha },
      ...files.map((f) => ({ path: `layers/${f}`, sha256_16: sha16(readFileSync(join(layersDir, f))) })),
    ],
    output: { path: basename(outPath), bytes: out.length, sha256_16: sha16(out) },
    counts: { ways: g.ways.length, withShade: known, shareWithShade: Number((known / g.ways.length).toFixed(4)), layerSegments: stats.layerSegments, matchedExact: stats.exact, matchedTolerant: stats.tolerant, layerSegmentsUnmatched: stats.unmatched, conflicts: stats.conflicts, slopeAgreeShare: stats.slopeCompared ? Number((stats.slopeAgree / stats.slopeCompared).toFixed(4)) : null },
    rules: { shadowBasis: '건물 그림자 · 2026-07-15 · 9–18시 평균', match: '점 줄 통째 같음 → 처음·끝·점 수 같고 사이 점 3m 이내', unmatched: '모름(255) — 이웃 길 값으로 메우지 않는다' },
  }
  const metaPath = join(dirname(outPath), 'walk-graph.json')
  if (existsSync(metaPath)) {
    const meta = JSON.parse(readFileSync(metaPath, 'utf8'))
    meta.format = 'GBWG v2 — v1(process/walk-graph.mjs 머리말)에 길마다 그늘 한 바이트가 경사 뒤에 더해진 것. 그늘은 shade 칸 참고'
    meta.shade = summary
    writeFileSync(metaPath, JSON.stringify(meta, null, 1) + '\n')
  }
  console.log(JSON.stringify(summary, null, 1))
}

if (process.argv[1] && resolve(process.argv[1]) === fileURLToPath(import.meta.url)) {
  main().catch((e) => { console.error(e.message); process.exit(1) })
}
