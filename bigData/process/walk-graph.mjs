#!/usr/bin/env node
/**
 * 보행 그래프 — 우리 걷기 길찾기가 쓰는 압축 파일을 만든다 (S15P21E201-1630)
 *
 * 걷는 구간을 경사 색으로 칠하려면(사용자 결정 2026-09-25) 길 모양이 있어야 하는데, 서버의 걷기는
 * 직선 × 배수 어림이라 모양이 없다. 바깥 API 대신 우리 길 자료로 서버 안에서 길을 찾기로 했다.
 * 이 파일은 그 그래프를 만든다. 백엔드는 결과 파일을 자원으로 싣고 기동 뒤에 읽는다.
 *
 * 🔴 점은 좌표로 잇는다. 추출본(raw/pbf)에는 OSM 점 번호가 없고 좌표만 있다. 교차점에서 두 길이
 *    같은 OSM 점을 쓰면 좌표가 글자 하나까지 같으므로, 소수 7자리 좌표를 열쇠로 삼으면 교차점이 이어진다.
 *    2026-09-25 실측 — 점 47만 개 중 97.8% 가 한 덩어리로 이어졌다.
 *
 * 🔴 빼는 길 — {@link keepWay}
 *   자동차 전용(motorway · motorway_link) · 보행 금지(foot=no) · 통행 금지(access=no, 걸어도 된다는 표시 없이)
 *   · 공사 중(construction · proposed). 사람이 못 걷는 길이다.
 * 🔴 계단은 <b>길로 쓴다</b>. 대신 표시를 남긴다 — 나중에 휠체어가 피하는 길을 만들 때 쓴다.
 * 🔴 경사는 길마다 하나(구간 경사의 p50 — 그 길의 보통 기울기, 방향 없는 크기)다. 모르는 것으로 두는 길:
 *    30m 미만(표본 한두 개라 고도 자료가 한 번 튀면 그대로 값이 된다) · 다리·터널(길 높이 ≠ 땅 높이) ·
 *    경사를 못 잰 길. 경사 지도(S15P21E201-1569)·장소 경사(S15P21E201-1629)와 같은 규칙이다.
 *
 * 파일 모양 (gzip 안, 큰끝 정수)
 *   'GBWG' · 판(int32 = 1)
 *   점 수 N · 길 수 W · 점 참조 합 R            (int32 × 3)
 *   점 N 개:   위도×1e7 · 경도×1e7             (int32 × 2)
 *   길 W 개:   점 개수 k(int32) · 표시(uint8, 1=계단) · 경사 천분율(int16, -1 = 모름) · 점 번호 k 개(int32)
 *
 * 입력 (전부 data/ 아래 — 저장소에 없다)
 *   raw/pbf/road.ndjson · walk.ndjson · stairs.ndjson   collect/pbf_extract.py 가 만든다 (선 모양 · 표시)
 *   staged/segment-slope.ndjson                         process/slope.mjs 가 만든다 (길 번호 → 경사)
 *
 * 출력
 *   staged/walk-graph.bin.gz   그래프
 *   staged/walk-graph.json     만든 날 · 입력 파일의 크기·해시 · 개수 — 다시 만들 때 같은 결과인지 견준다
 *
 * 실행
 *   node process/walk-graph.mjs
 *
 * 종료 코드
 *   0  냈다
 *   1  불변식이 깨졌다 (한 덩어리가 너무 작다 · 0건)
 *   2  입력이 없다
 */
import { createReadStream, existsSync, readFileSync } from 'node:fs'
import { writeFile, mkdir } from 'node:fs/promises'
import { createHash } from 'node:crypto'
import { createInterface } from 'node:readline'
import { gzipSync } from 'node:zlib'
import { dirname, join, relative } from 'node:path'
import { fileURLToPath, pathToFileURL } from 'node:url'
import { stamp } from '../mlops/manifest.mjs'
import { log } from '../lib/log.mjs'

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..')
const WAYS = ['road', 'walk', 'stairs'].map((t) => [t, join(ROOT, `data/raw/pbf/${t}.ndjson`)])
const SEG = join(ROOT, 'data/staged/segment-slope.ndjson')
const OUT = join(ROOT, 'data/staged/walk-graph.bin.gz')
const META = join(ROOT, 'data/staged/walk-graph.json')

const EXIT = { OK: 0, INVARIANT: 1, INPUT: 2 }

/** 이보다 짧은 길의 경사는 모르는 것으로 둔다 (m). */
export const MIN_SLOPE_LENGTH_M = 30

/** 한 덩어리로 이어진 점이 이 비율보다 적으면 좌표로 잇기가 깨진 것이다. */
const MIN_LARGEST_SHARE = 0.9

const NOT_WALKABLE = new Set(['motorway', 'motorway_link', 'construction', 'proposed'])

/** 사람이 걸을 수 있는 길인가. {@code tags} 는 그 길의 OSM 표시다. */
export function keepWay(tags) {
  const t = tags ?? {}
  if (NOT_WALKABLE.has(t.highway)) return false
  if (t.foot === 'no') return false
  if (t.access === 'no' && !['yes', 'designated', 'permissive'].includes(t.foot)) return false
  return true
}

/** 계단인가 — 추출본의 갈래가 stairs 이거나 highway=steps. */
export function isStairs(topic, tags) {
  return topic === 'stairs' || (tags ?? {}).highway === 'steps'
}

/**
 * 그 길의 경사 천분율(정수). 모르면 -1. {@code seg} 는 구간 경사 한 줄(없으면 undefined).
 */
export function slopePermille(seg, tags) {
  const t = tags ?? {}
  if (!seg || seg.p50Slope == null || !((seg.length ?? 0) >= MIN_SLOPE_LENGTH_M)) return -1
  if ((t.bridge && t.bridge !== 'no') || (t.tunnel && t.tunnel !== 'no')) return -1
  return Math.min(32767, Math.round(Math.abs(seg.p50Slope) * 1000))
}

/** 좌표 열쇠 — 소수 7자리. 같은 OSM 점이면 글자까지 같다. */
export const nodeKey = (p) => `${p.lat.toFixed(7)},${p.lon.toFixed(7)}`

/**
 * 길 목록을 그래프(점 · 길)로 만든다. {@code ways} 는 {@code {topic, tags, geometry}} 목록,
 * {@code slopeOf(way)} 는 그 길의 경사 천분율(-1 = 모름).
 */
export function buildGraph(ways, slopeOf) {
  const idOf = new Map()
  const lat = []
  const lon = []
  const out = []
  for (const w of ways) {
    if (!keepWay(w.tags) || !Array.isArray(w.geometry) || w.geometry.length < 2) continue
    const refs = []
    for (const p of w.geometry) {
      const k = nodeKey(p)
      let i = idOf.get(k)
      if (i === undefined) {
        i = lat.length
        idOf.set(k, i)
        lat.push(Math.round(p.lat * 1e7))
        lon.push(Math.round(p.lon * 1e7))
      }
      if (refs.at(-1) !== i) refs.push(i) // 같은 점이 연달아 나오면 한 번만
    }
    if (refs.length < 2) continue
    out.push({ refs, stairs: isStairs(w.topic, w.tags), slope: slopeOf(w) })
  }
  return { lat, lon, ways: out }
}

/** 가장 큰 덩어리에 든 점의 비율. */
export function largestComponentShare(graph) {
  const n = graph.lat.length
  const adj = Array.from({ length: n }, () => [])
  for (const w of graph.ways) for (let i = 1; i < w.refs.length; i++) { adj[w.refs[i - 1]].push(w.refs[i]); adj[w.refs[i]].push(w.refs[i - 1]) }
  const seen = new Uint8Array(n)
  let best = 0
  for (let s = 0; s < n; s++) {
    if (seen[s]) continue
    seen[s] = 1
    const stack = [s]
    let size = 0
    while (stack.length) {
      const u = stack.pop()
      size++
      for (const v of adj[u]) if (!seen[v]) { seen[v] = 1; stack.push(v) }
    }
    best = Math.max(best, size)
  }
  return n === 0 ? 0 : best / n
}

/** 파일 모양대로 이진으로 쓴다(압축 전). */
export function encode(graph) {
  const refs = graph.ways.reduce((t, w) => t + w.refs.length, 0)
  const size = 4 + 4 + 12 + graph.lat.length * 8 + graph.ways.length * 7 + refs * 4
  const buf = Buffer.alloc(size)
  let o = 0
  o += buf.write('GBWG', o, 'ascii')
  o = buf.writeInt32BE(1, o)
  o = buf.writeInt32BE(graph.lat.length, o)
  o = buf.writeInt32BE(graph.ways.length, o)
  o = buf.writeInt32BE(refs, o)
  for (let i = 0; i < graph.lat.length; i++) { o = buf.writeInt32BE(graph.lat[i], o); o = buf.writeInt32BE(graph.lon[i], o) }
  for (const w of graph.ways) {
    o = buf.writeInt32BE(w.refs.length, o)
    o = buf.writeUInt8(w.stairs ? 1 : 0, o)
    o = buf.writeInt16BE(w.slope, o)
    for (const r of w.refs) o = buf.writeInt32BE(r, o)
  }
  return buf
}

async function* lines(file) {
  const rl = createInterface({ input: createReadStream(file), crlfDelay: Infinity })
  for await (const line of rl) if (line) yield line
}

const sha = (file) => createHash('sha256').update(readFileSync(file)).digest('hex').slice(0, 16)

async function main() {
  log('보행 그래프 — 걷는 길을 좌표로 잇는다')
  for (const f of [SEG, ...WAYS.map(([, f]) => f)]) {
    if (!existsSync(f)) {
      log(`🔴 입력이 없습니다 — ${f}. 머리말의 입력 목록을 보십시오.`)
      process.exitCode = EXIT.INPUT
      return
    }
  }

  const slopeById = new Map()
  for await (const line of lines(SEG)) {
    const o = JSON.parse(line)
    slopeById.set(o.id, o)
  }
  const ways = []
  for (const [topic, file] of WAYS) {
    for await (const line of lines(file)) {
      const w = JSON.parse(line)
      ways.push({ id: w.id, topic, tags: w.tags, geometry: w.geometry })
    }
  }
  const graph = buildGraph(ways, (w) => slopePermille(slopeById.get(w.id), w.tags))
  const share = largestComponentShare(graph)
  const raw = encode(graph)
  const gz = gzipSync(raw, { level: 9 })
  await mkdir(dirname(OUT), { recursive: true })
  await writeFile(OUT, gz)

  const stairs = graph.ways.filter((w) => w.stairs).length
  const withSlope = graph.ways.filter((w) => w.slope >= 0).length
  const meta = {
    step: 'process/walk-graph',
    format: 'GBWG v1 — 머리말 참고',
    builtAt: new Date().toISOString().slice(0, 10),
    inputs: [SEG, ...WAYS.map(([, f]) => f)].map((f) => ({ path: relative(ROOT, f).replaceAll('\\', '/'), bytes: readFileSync(f).length, sha256_16: sha(f) })),
    output: { path: relative(ROOT, OUT).replaceAll('\\', '/'), bytes: gz.length, rawBytes: raw.length, sha256_16: sha(OUT) },
    counts: { nodes: graph.lat.length, ways: graph.ways.length, refs: graph.ways.reduce((t, w) => t + w.refs.length, 0), stairs, withSlope },
    largestComponentShare: +share.toFixed(4),
    rules: { excludedHighway: [...NOT_WALKABLE], footNo: true, accessNoWithoutFoot: true, stairsKeptAsWay: true, slope: `p50Slope · ${MIN_SLOPE_LENGTH_M}m 이상 · 다리·터널 제외`, nodeKey: '위경도 소수 7자리' },
  }
  await writeFile(META, JSON.stringify(meta, null, 1) + '\n')
  log(`  점 ${meta.counts.nodes.toLocaleString()} · 길 ${meta.counts.ways.toLocaleString()} (계단 ${stairs} · 경사 있음 ${withSlope}) · 한 덩어리 ${(share * 100).toFixed(1)}%`)
  log(`  파일 ${(gz.length / 1e6).toFixed(2)}MB (압축 전 ${(raw.length / 1e6).toFixed(2)}MB) — ${OUT}`)

  stamp(join(ROOT, 'data/staged/_walk-graph-run'), {
    step: 'process/walk-graph',
    inputs: [SEG, ...WAYS.map(([, f]) => f)],
    params: meta.rules,
    result: { ...meta.counts, largestComponentShare: meta.largestComponentShare, bytes: gz.length },
  })

  if (graph.ways.length === 0) {
    log('🔴 길이 하나도 없습니다.')
    process.exitCode = EXIT.INVARIANT
    return
  }
  if (share < MIN_LARGEST_SHARE) {
    log(`🔴 한 덩어리가 ${(share * 100).toFixed(1)}% 뿐입니다 — 좌표로 잇기가 깨졌습니다(${MIN_LARGEST_SHARE * 100}% 이상이어야 한다).`)
    process.exitCode = EXIT.INVARIANT
  }
}

const runDirectly = process.argv[1] && import.meta.url === pathToFileURL(process.argv[1]).href
if (runDirectly) {
  main().catch((e) => { console.error('치명:', e?.stack || e); process.exit(EXIT.INVARIANT) })
}
