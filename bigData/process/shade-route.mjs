#!/usr/bin/env node
/**
 * 해운대 → 광안리 도보: **최단 거리** 경로와 **그늘을 고려한** 경로를 겨루게 한다.
 *
 * ── 🔴 왜 남의 길찾기와 비교하지 않는가 ─────────────────────────────────────
 *
 * 두 가지다. 첫째, **공개된 보행자 경로 API 가 없다** (wheelchair-route.mjs 가 같은
 * 자리에서 같은 결론을 적었다). 둘째가 더 중요하다 — **도로망이 다르면 차이의 원인을
 * 못 가린다.** 남의 선과 우리 선이 다를 때 그것이 "우리가 그늘을 고려해서" 인지
 * "애초에 다른 길을 알고 있어서" 인지 구별할 방법이 없다.
 *
 * 그래서 **같은 그래프 위에서 비용 함수만 바꾼다.** 지도도 데이터도 같고 바뀌는 것이
 * 하나뿐이면, 차이는 그 하나 때문이다.
 *
 * ── 🔴 그늘 %만으로는 "유용한가" 에 답하지 못한다 ───────────────────────────
 *
 * 그늘을 10%p 더 얻으려고 800m 를 더 걷는다면 그것은 좋은 경로가 아니다.
 * 그래서 숫자 하나가 아니라 **곡선**을 낸다 — 가중치 λ 를 0 부터 키우면서
 * "얼마를 더 걸으면 그늘이 얼마나 늘어나는가" 를 점으로 찍는다.
 * 어느 점이 좋은지는 사람마다 다르고, 그것은 짝 비교 실험(FIELD-STUDY.md)의 몫이다.
 *
 * 비용:  cost = 길이 × (1 + λ × (1 − 그늘비율))
 *        λ=0 이면 순수 최단거리다. λ 가 커질수록 볕을 피해 돌아간다.
 *
 * ── 🔴 그늘을 모르는 구간을 어떻게 다루나 ───────────────────────────────────
 *
 * 0(볕)으로 치면 라우터가 **측정 안 된 길을 전부 피한다.** 1(그늘)로 치면 반대로
 * 몰린다. 둘 다 데이터가 없다는 사실을 선호로 바꿔치기하는 것이다.
 * 그래서 **아는 구간들의 평균**을 쓴다 — 끌지도 밀지도 않는 값이다.
 * 그리고 경로마다 **모름 비율을 함께 낸다.** 숫자를 믿을지 말지는 그것을 보고 정한다.
 *
 * 입력
 *   data/raw/pbf/{road,walk,stairs}.ndjson   보행 그래프
 *   data/staged/segment-shadow.ndjson        구간별·시각대별 그늘 비율 (process/shadow.mjs)
 *   data/raw/pbf/{poi,transit}.ndjson        출발·도착을 이름으로 확정
 *
 * 출력
 *   data/staged/shade-route-<시각>.json      곡선과 요약 (🔴 수치는 여기에만)
 *   data/staged/shade-route-<시각>-trees.json  --trees 로 돌렸을 때. 이름을 갈라 예전 결과를 안 덮는다
 *   data/staged/shade-route-<시각>.geojson   지도용 — 최단거리와 그늘 우선 두 경로
 *
 *   node process/shade-route.mjs
 *   node process/shade-route.mjs --hour 15 --data <폴더>
 *
 * 종료 코드
 *   0 정상   1 불변식 위반   2 입력이 없다
 */

import fs from 'node:fs'
import path from 'node:path'
import readline from 'node:readline'
import { log } from '../lib/log.mjs'

const argv = process.argv.slice(2)
const arg = (name, dflt) => {
  const eq = argv.find((a) => a.startsWith('--' + name + '='))
  if (eq) return eq.slice(name.length + 3)
  const i = argv.indexOf('--' + name)
  return i >= 0 && argv[i + 1] ? argv[i + 1] : dflt
}

const HERE = path.dirname(new URL(import.meta.url).pathname.replace(/^\/([A-Za-z]:)/, '$1'))
const BIGDATA = path.dirname(HERE)
const DATA = path.resolve(arg('data', path.join(BIGDATA, 'data')))
const PBF = path.join(DATA, 'raw', 'pbf')
const STAGED = path.join(DATA, 'staged')
const HOUR = Number(arg('hour', '15'))

/** 🔴 가로수를 얹을지. 기본은 안 얹는다 — 건물 그림자만 쓴 예전 결과와 비교할 수 있게. */
const WITH_TREES = argv.includes('--trees')

/** 🔴 결과 파일 이름을 가른다. 안 가르면 --trees 가 예전 결과를 덮어써 비교할 것이 없어진다. */
const SUFFIX = WITH_TREES ? '-trees' : ''

/** λ 격자. 0 이 최단거리다. 위로 갈수록 볕을 피해 더 돈다. */
const LAMBDAS = [0, 0.25, 0.5, 1, 2, 4, 8]

const die = (code, ...a) => { console.error(...a); process.exit(code) }

const R = 6371008.8
function haversine(a, b) {
  const rad = Math.PI / 180
  const dLat = (b.lat - a.lat) * rad, dLon = (b.lon - a.lon) * rad
  const la1 = a.lat * rad, la2 = b.lat * rad
  const h = Math.sin(dLat / 2) ** 2 + Math.cos(la1) * Math.cos(la2) * Math.sin(dLon / 2) ** 2
  return 2 * R * Math.asin(Math.sqrt(h))
}

async function* readNdjson(file) {
  if (!fs.existsSync(file)) return
  const rl = readline.createInterface({ input: fs.createReadStream(file), crlfDelay: Infinity })
  for await (const line of rl) if (line.trim()) yield JSON.parse(line)
}

// 좌표를 손으로 적지 않는다. 무엇으로 확정했는지가 결과에 남아야 한다.
async function findByName(words, label) {
  const hit = []
  for (const file of ['poi.ndjson', 'transit.ndjson']) {
    for await (const n of readNdjson(path.join(PBF, file))) {
      const name = n.tags?.name ?? ''
      if (!words.every((w) => name.includes(w))) continue
      if (n.lat == null || n.lon == null) continue
      hit.push({ id: n.id, name, lat: n.lat, lon: n.lon, file, tags: n.tags })
    }
  }
  if (!hit.length) return null
  // 같은 이름이 여럿이면 그 무리의 중앙점에 가장 가까운 것을 쓴다 (한쪽 끝에 쏠리지 않게).
  const cLat = hit.reduce((s, h) => s + h.lat, 0) / hit.length
  const cLon = hit.reduce((s, h) => s + h.lon, 0) / hit.length
  let best = hit[0]
  for (const h of hit) {
    if (haversine({ lat: cLat, lon: cLon }, h) < haversine({ lat: cLat, lon: cLon }, best)) best = h
  }
  return {
    point: { lat: best.lat, lon: best.lon },
    evidence: {
      what: label, how: `poi/transit ndjson 에서 이름에 ${words.join('·')} 가 함께 든 점 ${hit.length}개 중 무리의 중앙에 가장 가까운 것`,
      pickedId: best.id, pickedName: best.name, candidates: hit.length,
      sample: hit.slice(0, 5).map((h) => ({ id: h.id, name: h.name })),
    },
  }
}

const KEYP = 7
const key = (lat, lon) => `${lat.toFixed(KEYP)},${lon.toFixed(KEYP)}`
const inBbox = (p, b) => p.lat >= b.south && p.lat <= b.north && p.lon >= b.west && p.lon <= b.east

async function loadShade(hour) {
  const file = path.join(STAGED, 'segment-shadow.ndjson')
  if (!fs.existsSync(file)) die(2, `그늘 표가 없습니다: ${file}\n  먼저 node process/shadow.mjs 를 돌리세요.`)
  const byWay = new Map()
  let hours = null
  for await (const r of readNdjson(file)) {
    if (!hours) hours = (r.slots ?? []).map((s) => s.hour)
    const slot = (r.slots ?? []).find((s) => s.hour === hour)
    if (slot) byWay.set(r.id, slot.shadowRatio)
  }
  if (!byWay.size) die(2, `그늘 표에 ${hour}시 칸이 없습니다. 있는 시각: ${hours?.join(', ') ?? '(모름)'}`)

  // ── 🔴 --trees: 가로수를 얹는다 (S15P21E201-1221) ────────────────────────
  //
  // 길의 그늘은 나뭇잎 + 건물 그림자다. 둘 다 "노면이 덮이는 비율 0~1" 이라
  // 같은 자로 잰 값이고, 그래서 더할 수 있는 모양이 됐다.
  //
  // 🔴 **더하지 않고 max() 를 쓴다.** 나무 그늘과 건물 그림자는 같은 노면에
  //    겹쳐 지는 일이 흔한데, 더하면 그 겹친 몫을 두 번 세서 없는 그늘이 생긴다.
  //    독립으로 보고 1-(1-a)(1-b) 로 합치는 방법도 있지만 그건 겹침이 없다고
  //    가정하는 것이라 max() 보다 언제나 크다. 과대평가는 사람을 뙤약볕으로
  //    보내고 과소평가는 그 반대라 손해가 작다 — 그래서 낮은 쪽으로 기운다.
  //    (process/shadow.mjs 가 층높이에서 같은 이유로 낮은 쪽을 골랐다.)
  let trees = null
  if (WITH_TREES) {
    const tf = path.join(STAGED, 'segment-shade.ndjson')
    if (!fs.existsSync(tf)) die(2, `가로수 표가 없습니다: ${tf}\n  먼저 node process/shade.mjs 를 돌리세요.`)
    let n = 0, raised = 0, onlyTree = 0
    for await (const r of readNdjson(tf)) {
      const t = r.treeShadeRatio
      if (typeof t !== 'number') continue
      n++
      const b = byWay.get(r.id)
      if (b == null) { byWay.set(r.id, t); onlyTree++ }
      else if (t > b) { byWay.set(r.id, t); raised++ }
    }
    if (!n) die(2, `가로수 표에 treeShadeRatio 가 없습니다: ${tf}\n  process/shade.mjs 를 다시 돌리세요 (S15P21E201-1221 이후 판이어야 합니다).`)
    trees = { 가로수가있는구간: n, 가로수가더큰구간: raised, 건물그림자가없던구간: onlyTree, 합치는법: 'max(건물그림자, 가로수)' }
  }

  const vals = [...byWay.values()]
  const mean = vals.reduce((a, b) => a + b, 0) / vals.length
  return { byWay, mean, hours, count: byWay.size, trees }
}

async function buildGraph(bbox, shade) {
  const nodes = new Map()
  const ways = new Map()
  const files = { 'road.ndjson': 'road', 'walk.ndjson': 'walk', 'stairs.ndjson': 'stairs' }
  const nodeAt = (p) => {
    const k = key(p.lat, p.lon)
    let n = nodes.get(k)
    if (!n) { n = { lat: p.lat, lon: p.lon, edges: [] }; nodes.set(k, n) }
    return { k, n }
  }
  for (const [file, topic] of Object.entries(files)) {
    for await (const w of readNdjson(path.join(PBF, file))) {
      const geom = w.geometry ?? []
      if (geom.length < 2) continue
      if (!geom.some((p) => inBbox(p, bbox))) continue
      const known = shade.byWay.has(w.id)
      ways.set(w.id, {
        topic, name: w.tags?.name ?? null, highway: w.tags?.highway ?? null,
        shade: known ? shade.byWay.get(w.id) : shade.mean, shadeKnown: known,
      })
      for (let i = 1; i < geom.length; i++) {
        const a = geom[i - 1], b = geom[i]
        if (!inBbox(a, bbox) && !inBbox(b, bbox)) continue
        const { k: ka, n: na } = nodeAt(a)
        const { k: kb, n: nb } = nodeAt(b)
        if (ka === kb) continue
        const len = haversine(a, b)
        na.edges.push({ to: kb, len, wayId: w.id })
        nb.edges.push({ to: ka, len, wayId: w.id })   // 보행자는 일방통행을 안 따른다
      }
    }
  }
  return { nodes, ways }
}

function snap(graph, point) {
  let best = null
  for (const [k, n] of graph.nodes) {
    const d = haversine(point, n)
    if (!best || d < best.distM) best = { key: k, node: n, distM: d }
  }
  return best
}

/** 이진 힙 — 노드가 수만이라 선형 탐색으로는 λ 를 일곱 번 못 돈다. */
class Heap {
  constructor() { this.a = [] }
  get size() { return this.a.length }
  push(x) {
    const a = this.a; a.push(x)
    let i = a.length - 1
    while (i > 0) { const p = (i - 1) >> 1; if (a[p].d <= a[i].d) break; [a[p], a[i]] = [a[i], a[p]]; i = p }
  }
  pop() {
    const a = this.a, top = a[0], last = a.pop()
    if (a.length) {
      a[0] = last
      let i = 0
      for (;;) {
        const l = 2 * i + 1, r = l + 1
        let m = i
        if (l < a.length && a[l].d < a[m].d) m = l
        if (r < a.length && a[r].d < a[m].d) m = r
        if (m === i) break
        ;[a[m], a[i]] = [a[i], a[m]]; i = m
      }
    }
    return top
  }
}

function route(graph, startKey, goalKey, lambda) {
  const dist = new Map([[startKey, 0]])
  const prev = new Map()
  const done = new Set()
  const q = new Heap()
  q.push({ k: startKey, d: 0 })
  while (q.size) {
    const { k, d } = q.pop()
    if (done.has(k)) continue
    done.add(k)
    if (k === goalKey) break
    for (const e of graph.nodes.get(k).edges) {
      const w = graph.ways.get(e.wayId)
      const cost = e.len * (1 + lambda * (1 - w.shade))
      const nd = d + cost
      if (nd < (dist.get(e.to) ?? Infinity)) {
        dist.set(e.to, nd)
        prev.set(e.to, { from: k, wayId: e.wayId, len: e.len })
        q.push({ k: e.to, d: nd })
      }
    }
  }
  if (!prev.has(goalKey)) return null

  const steps = []
  for (let k = goalKey; k !== startKey; ) {
    const p = prev.get(k)
    steps.push({ ...p, to: k })
    k = p.from
  }
  steps.reverse()

  let lengthM = 0, shadeLen = 0, unknownLen = 0
  const coords = [[graph.nodes.get(startKey).lon, graph.nodes.get(startKey).lat]]
  const wayIds = new Set()
  for (const s of steps) {
    const w = graph.ways.get(s.wayId)
    lengthM += s.len
    shadeLen += s.len * w.shade
    if (!w.shadeKnown) unknownLen += s.len
    wayIds.add(s.wayId)
    const n = graph.nodes.get(s.to)
    coords.push([n.lon, n.lat])
  }
  return {
    lambda,
    lengthM: +lengthM.toFixed(1),
    shadeRatio: +(shadeLen / lengthM).toFixed(4),
    unknownShareOfLength: +(unknownLen / lengthM).toFixed(4),
    wayCount: wayIds.size,
    coords,
  }
}

async function main() {
  if (!fs.existsSync(PBF)) die(2, `추출본이 없습니다: ${PBF}`)
  const shade = await loadShade(HOUR)
  log(`그늘 표  ${shade.count.toLocaleString()}구간 · ${HOUR}시 · 아는 구간 평균 ${(shade.mean * 100).toFixed(1)}%`)

  const from = await findByName(['해운대', '해수욕장'], '해운대해수욕장')
    ?? await findByName(['해운대역'], '해운대역')
  const to = await findByName(['광안리', '해수욕장'], '광안리해수욕장')
    ?? await findByName(['광안', '해수욕장'], '광안리해수욕장(광안)')
  if (!from) die(2, '해운대 기준점을 데이터에서 못 찾았습니다. 좌표를 지어내지 않습니다.')
  if (!to) die(2, '광안리 기준점을 데이터에서 못 찾았습니다. 좌표를 지어내지 않습니다.')
  log(`출발  ${from.evidence.pickedName}  (${from.point.lat.toFixed(5)}, ${from.point.lon.toFixed(5)})`)
  log(`도착  ${to.evidence.pickedName}  (${to.point.lat.toFixed(5)}, ${to.point.lon.toFixed(5)})`)

  const pts = [from.point, to.point]
  const bbox = {
    south: Math.min(...pts.map((p) => p.lat)) - 0.02, north: Math.max(...pts.map((p) => p.lat)) + 0.02,
    west: Math.min(...pts.map((p) => p.lon)) - 0.025, east: Math.max(...pts.map((p) => p.lon)) + 0.025,
  }
  const graph = await buildGraph(bbox, shade)
  log(`그래프  노드 ${graph.nodes.size.toLocaleString()} · way ${graph.ways.size.toLocaleString()}`)

  const s = snap(graph, from.point), g = snap(graph, to.point)
  log(`붙임    출발 ${s.distM.toFixed(0)}m · 도착 ${g.distM.toFixed(0)}m 떨어진 노드에 붙였습니다`)

  const curve = []
  for (const lam of LAMBDAS) {
    const r = route(graph, s.key, g.key, lam)
    if (!r) die(1, `λ=${lam} 에서 경로를 못 찾았습니다 — 그래프가 끊겼습니다.`)
    curve.push(r)
    log(`  λ=${String(lam).padStart(4)}  ${(r.lengthM / 1000).toFixed(2)}km  그늘 ${(r.shadeRatio * 100).toFixed(1)}%  (모름 ${(r.unknownShareOfLength * 100).toFixed(0)}%)`)
  }

  const base = curve[0]
  const rows = curve.map((r) => ({
    lambda: r.lambda,
    lengthM: r.lengthM,
    extraM: +(r.lengthM - base.lengthM).toFixed(1),
    extraPct: +(((r.lengthM / base.lengthM) - 1) * 100).toFixed(2),
    shadeRatio: r.shadeRatio,
    shadeGainPoint: +((r.shadeRatio - base.shadeRatio) * 100).toFixed(2),
    unknownShareOfLength: r.unknownShareOfLength,
    wayCount: r.wayCount,
  }))

  // 🔴 불변식 — 그럴듯한데 틀린 숫자를 통과시키지 않는다
  const bad = []
  if (base.lambda !== 0) bad.push('첫 점이 λ=0(최단거리)이 아니다')
  for (const r of rows) {
    if (r.extraM < -0.5) bad.push(`λ=${r.lambda}: 최단거리보다 짧다 (${r.extraM}m)`)
    if (r.shadeRatio < 0 || r.shadeRatio > 1) bad.push(`λ=${r.lambda}: 그늘 비율이 0~1 밖이다`)
  }
  if (bad.length) die(1, '불변식 위반:\n  ' + bad.join('\n  '))

  const best = rows[rows.length - 1]
  const out = {
    generatedAt: new Date().toISOString(),
    hour: HOUR,
    from: from.evidence, to: to.evidence,
    snap: { startM: +s.distM.toFixed(1), goalM: +g.distM.toFixed(1) },
    graph: { nodes: graph.nodes.size, ways: graph.ways.size, bbox },
    shadeTable: {
      segments: shade.count, hoursAvailable: shade.hours, knownMean: +shade.mean.toFixed(4),
      재료: WITH_TREES ? '건물 그림자 + 가로수' : '건물 그림자만',
      ...(shade.trees ? { 가로수: shade.trees } : {}),
    },
    unknownPolicy: '그늘을 모르는 구간은 아는 구간들의 평균을 쓴다 — 끌지도 밀지도 않게. 경로마다 모름 비율을 함께 낸다.',
    curve: rows,
    headline: {
      baselineKm: +(base.lengthM / 1000).toFixed(3),
      baselineShadePct: +(base.shadeRatio * 100).toFixed(1),
      maxShadePct: +(best.shadeRatio * 100).toFixed(1),
      costOfMaxShadeM: best.extraM,
      costOfMaxShadePct: best.extraPct,
    },
  }
  fs.mkdirSync(STAGED, { recursive: true })
  fs.writeFileSync(path.join(STAGED, `shade-route-${HOUR}${SUFFIX}.json`), JSON.stringify(out, null, 2))

  const geo = {
    type: 'FeatureCollection',
    features: curve
      .filter((r) => r.lambda === 0 || r.lambda === LAMBDAS[LAMBDAS.length - 1])
      .map((r) => ({
        type: 'Feature',
        properties: {
          lambda: r.lambda, kind: r.lambda === 0 ? '최단거리' : '그늘 우선',
          lengthM: r.lengthM, shadeRatio: r.shadeRatio, hour: HOUR,
        },
        geometry: { type: 'LineString', coordinates: r.coords },
      })),
  }
  fs.writeFileSync(path.join(STAGED, `shade-route-${HOUR}${SUFFIX}.geojson`), JSON.stringify(geo))

  log('')
  log(`최단거리 ${out.headline.baselineKm}km · 그늘 ${out.headline.baselineShadePct}%`)
  log(`그늘 우선 ${(best.lengthM / 1000).toFixed(3)}km · 그늘 ${out.headline.maxShadePct}%`)
  log(`→ ${best.extraM}m(${best.extraPct}%) 더 걸어 그늘을 ${best.shadeGainPoint}%p 더 얻는다`)
  log('')
  log(`썼습니다  ${path.join(STAGED, `shade-route-${HOUR}${SUFFIX}.json`)}`)
  log(`          ${path.join(STAGED, `shade-route-${HOUR}${SUFFIX}.geojson`)}`)
}

main().catch((e) => { console.error('치명:', e); process.exit(1) })
