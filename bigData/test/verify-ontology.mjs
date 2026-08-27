#!/usr/bin/env node
/**
 * 온톨로지 검사 — config/ontology.jsonld
 *
 * 판정은 종료 코드다 (0 성공 / 1 실패). 개수를 여기에 적지 않는다.
 *
 *   node test/verify-ontology.mjs
 *
 * 무엇을 붙잡나
 *   1. @context 로 펼쳤을 때 유효한 JSON-LD 인가 (외부 라이브러리 없이 구조만 본다)
 *   2. 모든 skos:Concept 에 prefLabel(ko·en 둘 다)과 definition 이 있는가
 *   3. 값을 갖는 속성에 단위 또는 값 범위가 선언돼 있는가
 *   4. 가능판정 규칙이 선언된 속성·파라미터·프로파일만 참조하는가
 *   5. 🔴 선호 가중치 숫자가 파일에 박혀 있지 않은가
 *   6. 🔴 미수집 출처에 지어낸 필드 이름이 없는가
 *   7. 수집됨이라고 적은 필드가 실제 파일에 있는가 (파일이 없으면 건너뛴다)
 *   8. 여행 상위 개념(장소·이동·일정·여행자·이동수단·역·출입구)이 전부 있는가
 *   9. 🔴 장소와 정류장·지하철 출입구의 관계가 정해 둔 대로인가
 *  10. 🔴 bm:Leg 이 시각을 갖는가 — 없으면 시각별 층이 붙을 자리가 없다
 *  11. 🔴 일정 → 이동 → 구간으로 내려가는 길이 실제로 이어지는가
 *  12. 🔴 장소 갈래의 태그가 실제 poi 파일에서 관측되는가 (파일이 없으면 건너뛴다)
 *
 * 7·12번은 데이터가 없는 clone 직후에도 돌아야 하므로 파일이 없으면 통과한다 —
 * test/verify.mjs 가 DEM·보정 검사에서 쓰는 것과 같은 태도다.
 *
 * JSON-LD(Linked Data 를 JSON 으로 쓰는 W3C 표준 — @context 가 짧은 이름을 URI 로
 * 펼친다)의 확장 규칙 전부를 구현하지 않는다. 이 파일이 실제로 쓰는 부분집합만
 * 구현하고, 그 밖의 것이 나오면 "펼칠 수 없다" 고 실패시킨다. 조용히 넘기지 않는다.
 */
import { readFile, access } from 'node:fs/promises'
import { createReadStream } from 'node:fs'
import { createInterface } from 'node:readline'
import { join, dirname, resolve } from 'node:path'
import { fileURLToPath } from 'node:url'

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..')
// 인자로 다른 파일을 줄 수 있다. 검사기 자신이 실제로 실패하는지 확인할 때 쓴다 —
// 통과만 해본 검사기는 검사기가 아니다. 데이터 경로는 언제나 bigData 기준이다.
const FILE = process.argv[2] ? resolve(process.argv[2]) : join(ROOT, 'config/ontology.jsonld')

const SKOS = 'http://www.w3.org/2004/02/skos/core#'
const BM   = 'https://lab.ssafy.com/s15-bigdata-dist-sub1/S15P21E201/ns/mobility#'
const S = n => SKOS + n
const B = n => BM + n

let failed = 0
const ok  = m => console.log(`  ✅ ${m}`)
const bad = m => { console.log(`  ❌ ${m}`); failed++ }
const section = n => console.log(n)
const exists = async p => { try { await access(p); return true } catch { return false } }

// ── @context 의 중복 키 — JSON.parse 는 조용히 뒤엣것만 남긴다 ────────────────
// 접두사와 용어 별칭이 같은 이름을 쓰면 접두사가 사라지고, 그 순간 모든
// prefix:suffix 가 펼쳐지지 않는다. 눈으로는 안 보이는 종류의 고장이라 원문을 본다.
function duplicateContextKeys(text) {
  const at = text.indexOf('"@context"')
  if (at < 0) return null
  const start = text.indexOf('{', at)
  if (start < 0) return null
  let depth = 0, end = -1
  const keys = []
  for (let i = start; i < text.length; i++) {
    const c = text[i]
    if (c === '"') {
      let j = i + 1
      while (j < text.length && !(text[j] === '"' && text[j - 1] !== '\\')) j++
      const token = text.slice(i + 1, j)
      let k = j + 1
      while (k < text.length && /\s/.test(text[k])) k++
      if (depth === 1 && text[k] === ':') keys.push(token)
      i = j
      continue
    }
    if (c === '{' || c === '[') depth++
    else if (c === '}' || c === ']') { depth--; if (depth === 0) { end = i; break } }
  }
  if (end < 0) return null
  const seen = new Set(), dup = new Set()
  for (const k of keys) { if (seen.has(k)) dup.add(k); seen.add(k) }
  return [...dup]
}

// ── @context 를 우리가 쓰는 부분집합만큼 해석한다 ─────────────────────────────
function buildContext(ctx, errs) {
  const prefixes = {}, terms = {}
  for (const [k, v] of Object.entries(ctx)) {
    if (k.startsWith('@')) continue
    if (typeof v === 'string') {
      if (/[#/]$/.test(v)) prefixes[k] = v
      else terms[k] = { id: v }
    } else if (v && typeof v === 'object') {
      if (!v['@id']) errs.push(`@context 의 "${k}" 에 @id 가 없다`)
      terms[k] = { id: v['@id'], type: v['@type'], container: v['@container'] }
    } else errs.push(`@context 의 "${k}" 값이 문자열도 객체도 아니다`)
  }
  for (const [p, u] of Object.entries(prefixes))
    if (!/^https?:\/\//.test(u)) errs.push(`접두사 "${p}" 가 절대 URI 가 아니다: ${u}`)
  return { prefixes, terms }
}

function expandIri(v, C) {
  if (typeof v !== 'string' || !v) return null
  if (v.startsWith('@')) return v
  if (/^https?:\/\//.test(v)) return v
  if (C.terms[v]) return expandIri(C.terms[v].id, C)
  const i = v.indexOf(':')
  if (i > 0) {
    const p = v.slice(0, i)
    if (C.prefixes[p]) return C.prefixes[p] + v.slice(i + 1)
  }
  return null
}

const arr = v => Array.isArray(v) ? v : [v]

function expandValue(raw, term, C, path, errs) {
  if (term?.container === '@language') {
    if (raw === null || typeof raw !== 'object' || Array.isArray(raw)) {
      errs.push(`${path}: 언어 맵이어야 하는데 아니다`)
      return []
    }
    return Object.entries(raw).map(([lang, val]) => ({ '@value': val, '@language': lang }))
  }
  const out = []
  for (const v of arr(raw)) {
    if (v !== null && typeof v === 'object' && !Array.isArray(v)) {
      out.push(expandNode(v, C, path, errs))
    } else if (term?.type === '@id') {
      const iri = expandIri(v, C)
      if (!iri) errs.push(`${path}: 값 "${v}" 를 @context 로 펼칠 수 없다`)
      else out.push({ '@id': iri })
    } else {
      out.push({ '@value': v })
    }
  }
  return out
}

function expandNode(o, C, path, errs) {
  const node = { '@node': true, '@path': path }
  for (const [k, raw] of Object.entries(o)) {
    if (k === '//' || k.startsWith('//')) continue          // 사람용 주석
    if (k === '@id') {
      const iri = expandIri(raw, C)
      if (!iri) { errs.push(`${path}: @id "${raw}" 를 펼칠 수 없다`); continue }
      node['@id'] = iri
      continue
    }
    if (k === '@type') {
      node['@type'] = arr(raw).map(t => {
        const iri = expandIri(t, C)
        if (!iri) errs.push(`${path}: @type "${t}" 를 펼칠 수 없다`)
        return iri
      }).filter(Boolean)
      continue
    }
    if (k.startsWith('@')) continue
    const iri = expandIri(k, C)
    if (!iri) { errs.push(`${path}: 키 "${k}" 를 @context 로 펼칠 수 없다 — 실제 확장에서 이 줄은 사라진다`); continue }
    const p = node['@id'] ?? path
    node[iri] = expandValue(raw, C.terms[k], C, `${p} → ${k}`, errs)
  }
  return node
}

function* walk(node) {
  yield node
  for (const [k, vals] of Object.entries(node)) {
    if (k.startsWith('@')) continue
    for (const v of vals) if (v && v['@node']) yield* walk(v)
  }
}

const lits  = (n, iri) => (n[iri] ?? []).filter(v => '@value' in v).map(v => v['@value'])
const lit   = (n, iri) => lits(n, iri)[0]
const ids   = (n, iri) => (n[iri] ?? []).filter(v => '@id' in v).map(v => v['@id'])
const nodes = (n, iri) => (n[iri] ?? []).filter(v => v && v['@node'])
const langs = (n, iri) => Object.fromEntries((n[iri] ?? [])
  .filter(v => '@language' in v).map(v => [v['@language'], v['@value']]))
const has   = (n, iri) => (n[iri] ?? []).length > 0
const short = iri => iri.startsWith(BM) ? 'bm:' + iri.slice(BM.length)
  : iri.startsWith(SKOS) ? 'skos:' + iri.slice(SKOS.length) : iri

// ── 데이터 파일에서 필드를 실제로 확인한다 ───────────────────────────────────
async function firstLines(path, n) {
  const stream = createReadStream(path)
  const rl = createInterface({ input: stream, crlfDelay: Infinity })
  const out = []
  for await (const line of rl) {
    if (line.trim()) out.push(line)
    if (out.length >= n) break
  }
  rl.close(); stream.destroy()
  return out
}

function resolvePath(obj, path) {
  let cur = obj
  for (const seg of path.replace(/\[(\d+)\]/g, '.$1').split('.')) {
    if (cur === null || cur === undefined) return undefined
    cur = cur[seg]
  }
  return cur
}

// ═══════════════════════════════════════════════════════════════════════════
const raw = await readFile(FILE, 'utf8')

section('원문')
const dup = duplicateContextKeys(raw)
if (dup === null) bad('@context 블록을 원문에서 찾지 못했다')
else if (dup.length) bad(`@context 에 중복 키: ${dup.join(', ')} — JSON.parse 가 뒤엣것만 남겨 접두사가 사라진다`)
else ok('@context 에 중복 키 없음')

let doc
try { doc = JSON.parse(raw) } catch (e) { bad(`JSON 파싱 실패: ${e.message}`); process.exit(1) }

section('@context')
const ctxErrs = []
if (!doc['@context']) { bad('@context 가 없다'); process.exit(1) }
if (!Array.isArray(doc['@graph'])) { bad('@graph 가 배열이 아니다'); process.exit(1) }
const C = buildContext(doc['@context'], ctxErrs)
ctxErrs.forEach(bad)
if (!ctxErrs.length) ok('접두사·용어 정의가 전부 해석된다')

section('확장 (JSON-LD 구조)')
const expErrs = []
const graph = doc['@graph'].map((n, i) => expandNode(n, C, `@graph[${i}]`, expErrs))
expErrs.forEach(bad)
if (!expErrs.length) ok('모든 키와 값이 @context 로 펼쳐진다')

const byId = new Map()
for (const n of graph) {
  if (!n['@id']) { bad(`${n['@path']}: 최상위 노드에 @id 가 없다`); continue }
  if (byId.has(n['@id'])) bad(`@id 중복: ${short(n['@id'])}`)
  byId.set(n['@id'], n)
}
if (byId.size === graph.length) ok('최상위 노드의 @id 가 전부 있고 겹치지 않는다')

const all = []
for (const n of graph) for (const x of walk(n)) all.push(x)

const isType = (n, t) => (n['@type'] ?? []).includes(t)
const concepts = graph.filter(n => isType(n, S('Concept')))

// broader 를 따라 올라가 조상인지 본다
function under(id, ancestor, depth = 0) {
  if (id === ancestor) return true
  if (depth > 16) return false
  const n = byId.get(id)
  if (!n) return false
  return ids(n, S('broader')).some(p => under(p, ancestor, depth + 1))
}
const attributes = concepts.filter(n => under(n['@id'], B('Attribute')) && n['@id'] !== B('Attribute'))
const parameters = concepts.filter(n => under(n['@id'], B('Parameter')) && n['@id'] !== B('Parameter'))
const rules      = concepts.filter(n => under(n['@id'], B('FeasibilityRule')) && n['@id'] !== B('FeasibilityRule'))
const profiles   = concepts.filter(n => under(n['@id'], B('UserProfile')) && n['@id'] !== B('UserProfile'))
const metas      = concepts.filter(n => under(n['@id'], B('MetaProperty')) && n['@id'] !== B('MetaProperty'))

section('SKOS 개념')
{
  let n = 0
  for (const c of concepts) {
    const pl = langs(c, S('prefLabel')), df = langs(c, S('definition'))
    for (const l of ['ko', 'en'])
      if (!pl[l]) { bad(`${short(c['@id'])}: prefLabel 에 ${l} 가 없다`); n++ }
    if (!Object.keys(df).length) { bad(`${short(c['@id'])}: definition 이 없다`); n++ }
    else if (!df.ko) { bad(`${short(c['@id'])}: definition 에 ko 가 없다`); n++ }
    if (!ids(c, S('inScheme')).length) { bad(`${short(c['@id'])}: inScheme 이 없다`); n++ }
  }
  if (!n) ok(`skos:Concept 전부 prefLabel(ko·en) · definition · inScheme 을 갖는다`)
}

section('참조 무결성')
{
  let n = 0
  const linkPreds = [S('broader'), S('inScheme'), S('topConceptOf'),
    B('onClass'), B('rangeClass'), B('derivedFrom'), B('appliesToProfile'), B('property'), B('parameter')]
  for (const node of all)
    for (const p of linkPreds)
      for (const target of ids(node, p))
        if (target.startsWith(BM) && !byId.has(target)) {
          bad(`${short(node['@id'] ?? node['@path'])}: ${short(p)} 가 없는 것을 가리킨다 → ${short(target)}`); n++
        }
  if (!n) ok('broader · onClass · property · parameter 등 모든 참조가 실재한다')
}

section('값과 단위')
{
  let n = 0
  for (const a of [...attributes, ...parameters]) {
    const hasUnit  = has(a, B('unit'))
    const hasRange = has(a, B('valueRange'))
    const hasCodes = has(a, B('codeList'))
    if (!hasUnit && !hasRange && !hasCodes) {
      bad(`${short(a['@id'])}: 단위(unit)도 값 범위(valueRange)도 코드목록(codeList)도 없다`); n++
    }
  }
  if (!n) ok('값을 갖는 속성·파라미터에 전부 단위 또는 값 범위가 선언돼 있다')
}

section('코드목록 준수')
{
  let n = 0
  for (const m of metas) {
    const open = lit(m, B('codeListOpen'))
    if (open !== false) continue
    const allowed = new Set(lits(m, B('codeList')))
    if (!allowed.size) { bad(`${short(m['@id'])}: 닫힌 코드목록인데 값이 비었다`); n++; continue }
    for (const node of all)
      for (const v of lits(node, m['@id']))
        if (!allowed.has(v)) {
          bad(`${short(node['@id'] ?? node['@path'])}: ${short(m['@id'])} 값 "${v}" 가 코드목록에 없다`); n++
        }
  }
  if (!n) ok('닫힌 코드목록을 쓰는 술어의 모든 사용값이 목록 안에 있다')
}

section('가능판정 규칙')
{
  let n = 0
  const attrIds  = new Set(attributes.map(a => a['@id']))
  const paramIds = new Set(parameters.map(p => p['@id']))
  const profIds  = new Set([B('UserProfile'), ...profiles.map(p => p['@id'])])
  for (const r of rules) {
    const name = short(r['@id'])
    const to = ids(r, B('appliesToProfile'))
    if (!to.length) { bad(`${name}: appliesTo 가 없다`); n++ }
    for (const t of to) if (!profIds.has(t)) { bad(`${name}: appliesTo 가 프로파일이 아니다 → ${short(t)}`); n++ }
    if (!ids(r, B('onClass')).length) { bad(`${name}: onClass 가 없다`); n++ }
    if (!lits(r, B('verdict')).length) { bad(`${name}: verdict 가 없다`); n++ }
    if (!lits(r, B('evidenceStatus')).length) { bad(`${name}: evidenceStatus 가 없다`); n++ }
    const conds = nodes(r, B('condition'))
    if (!conds.length) { bad(`${name}: condition 이 없다`); n++ }
    for (const c of conds) {
      const props = ids(c, B('property'))
      if (props.length !== 1) { bad(`${name}: condition 에 property 가 정확히 하나여야 한다`); n++ }
      for (const p of props) if (!attrIds.has(p)) {
        bad(`${name}: 선언되지 않은 속성을 참조한다 → ${short(p)}`); n++
      }
      if (!lits(c, B('operator')).length) { bad(`${name}: condition 에 operator 가 없다`); n++ }
      const params = ids(c, B('parameter'))
      const consts = lits(c, B('constant'))
      if (params.length + consts.length !== 1) {
        bad(`${name}: condition 은 parameter 또는 constant 중 정확히 하나를 가져야 한다`); n++
      }
      for (const p of params) if (!paramIds.has(p)) {
        bad(`${name}: 선언되지 않은 파라미터를 참조한다 → ${short(p)}`); n++
      }
    }
  }
  if (!n) ok('모든 규칙이 선언된 속성·파라미터·프로파일만 참조한다')

  let m = 0
  for (const p of profiles) {
    const covered = rules.some(r => ids(r, B('appliesToProfile')).some(t => under(p['@id'], t)))
    if (!covered) { bad(`${short(p['@id'])}: 이 프로파일에 적용되는 규칙이 하나도 없다`); m++ }
  }
  if (!m) ok('네 프로파일 전부 최소 하나의 규칙에 덮인다 (상위 프로파일 상속 포함)')
}

// ── 여기부터가 여행 상위 개념 층이다 ──────────────────────────────────────────
// 구간 단위 값(경사·그늘·경치)은 그것만으로는 여행이 되지 않는다. 어디를 가고
// 언제 가는가가 없으면 전부 떠 있다. 아래 검사들은 그 접착제가 실제로 붙어
// 있는지만 본다 — 좋다/싫다는 여전히 이 파일이 담지 않는다.

section('여행 상위 개념')
{
  let n = 0
  for (const s of ['Waypoint', 'Place', 'PlaceCategory', 'TravelMode',
    'Traveler', 'Itinerary', 'Leg', 'Station', 'StationEntrance']) {
    const node = byId.get(B(s))
    if (!node) { bad(`bm:${s} 가 없다 — 여행 상위 개념이 빠졌다`); n++; continue }
    if (!isType(node, S('Concept'))) { bad(`bm:${s} 가 skos:Concept 이 아니다`); n++; continue }
    const pl = langs(node, S('prefLabel')), df = langs(node, S('definition'))
    for (const l of ['ko', 'en']) if (!pl[l]) { bad(`bm:${s}: prefLabel 에 ${l} 가 없다`); n++ }
    if (!df.ko) { bad(`bm:${s}: definition 에 ko 가 없다`); n++ }
  }
  if (!n) ok('장소 · 갈래 · 이동수단 · 여행자 · 일정 · 이동 · 역 · 출입구가 전부 있고 이름과 뜻을 갖는다')
}

section('🔴 장소와 정류장·출입구의 관계')
{
  let n = 0
  // 정류장과 지하철 출입구는 길의 요소이면서 동시에 갈 수 있는 지점이다.
  // 정체는 bm:NetworkElement 에 남기고 역할만 bm:Waypoint 로 공유한다 — docs/ONTOLOGY.md 2.1.
  for (const s of ['Place', 'BusStop', 'StationEntrance'])
    if (!under(B(s), B('Waypoint'))) {
      bad(`bm:${s} 가 bm:Waypoint 아래에 없다 — 이동 한 토막의 끝점이 될 수 없게 된다`); n++
    }
  for (const s of ['BusStop', 'StationEntrance'])
    if (under(B(s), B('Place'))) {
      bad(`bm:${s} 가 bm:Place 아래에 있다 — 길의 요소와 여행 목적지를 같은 것으로 뭉갠 것이다`); n++
    }
  if (under(B('Station'), B('Waypoint'))) {
    bad('bm:Station 이 bm:Waypoint 아래에 있다 — 역 중심점을 이동의 끝점으로 삼으면 출구가 수백 m 벌어진 역에서 거리가 매번 틀린다. 끝점은 bm:StationEntrance 다'); n++
  }
  if (!n) ok('정류장·출입구는 길의 요소로 남고 끝점 역할만 bm:Place 와 공유한다 (역 자체는 끝점이 아니다)')
}

section('🔴 Leg 이 시각을 갖는가')
{
  const legAttrs = attributes.filter(a => ids(a, B('onClass')).includes(B('Leg')))
  const temporal = legAttrs.filter(a => lits(a, B('isTemporal')).includes(true))
  if (!legAttrs.length) bad('bm:Leg 에 붙은 속성이 하나도 없다')
  else if (!temporal.length) {
    bad('bm:Leg 에 isTemporal: true 인 속성이 없다 — 시각이 없으면 건물 그림자·영업시간·첫차막차·기온이 붙을 자리가 없다')
  } else ok(`bm:Leg 이 시각을 갖는다 (${temporal.map(a => short(a['@id'])).join(' · ')})`)

  for (const a of attributes)
    if (lits(a, B('isTemporal')).includes(true) && !has(a, B('unit')) && !has(a, B('valueRange')))
      bad(`${short(a['@id'])}: 시각 속성인데 단위도 값 범위도 없다 — 형식을 모르면 다음 사람이 반드시 잘못 읽는다`)
}

section('🔴 일정 → 이동 → 구간')
{
  // onClass → rangeClass 를 간선으로 보고 실제로 내려갈 수 있는지 본다.
  const edges = new Map()
  for (const a of attributes)
    for (const from of ids(a, B('onClass')))
      for (const to of ids(a, B('rangeClass'))) {
        if (!edges.has(from)) edges.set(from, [])
        edges.get(from).push({ to, via: a['@id'] })
      }
  const pathBetween = (from, to) => {
    const q = [[from, []]], seen = new Set([from])
    while (q.length) {
      const [cur, trail] = q.shift()
      if (cur === to) return trail
      for (const e of edges.get(cur) ?? [])
        if (!seen.has(e.to)) { seen.add(e.to); q.push([e.to, [...trail, e]]) }
    }
    return null
  }
  const toLeg = pathBetween(B('Itinerary'), B('Leg'))
  const toSeg = pathBetween(B('Leg'), B('Segment'))
  if (!toLeg) bad('bm:Itinerary 에서 bm:Leg 으로 내려가는 rangeClass 관계가 없다 — 하루 일정이 이동을 갖지 못한다')
  if (!toSeg) bad('bm:Leg 에서 bm:Segment 로 내려가는 rangeClass 관계가 없다 — 구간별로 만든 경사·그늘 값이 이동에 붙지 못한다')
  if (toLeg && toSeg) {
    const trail = [...toLeg, ...toSeg].map(e => short(e.via)).join(' → ')
    ok(`bm:Itinerary → bm:Leg → bm:Segment 가 이어진다 (${trail})`)
  }
}

section('🔴 장소 갈래가 실제 태그에서 왔는가')
{
  let n = 0
  const cats = concepts.filter(c => ids(c, S('broader')).includes(B('PlaceCategory')))
  if (!cats.length) { bad('bm:PlaceCategory 아래에 갈래가 하나도 없다'); n++ }
  const wanted = new Map()                       // "키=값" → 그것을 적은 갈래들
  for (const c of cats) {
    const codes = lits(c, B('codeList'))
    if (!codes.length) { bad(`${short(c['@id'])}: 갈래인데 codeList 가 비었다 — 무엇을 묶는지 말하지 않는다`); n++; continue }
    for (const code of codes) {
      if (typeof code !== 'string' || !/^[a-z_]+=[^=]+$/.test(code)) {
        bad(`${short(c['@id'])}: codeList 값 "${code}" 가 OSM 태그의 키=값 형식이 아니다`); n++; continue
      }
      if (!wanted.has(code)) wanted.set(code, [])
      wanted.get(code).push(short(c['@id']))
    }
  }
  const POI = join(ROOT, 'data/raw/pbf/poi.ndjson')
  if (!await exists(POI)) {
    ok('data/raw/pbf/poi.ndjson 없음 — 갈래의 태그 대조는 건너뜀')
    if (!n) ok('장소 갈래가 전부 형식에 맞는 codeList 를 갖는다')
  } else {
    const seen = new Set()
    const stream = createReadStream(POI)
    const rl = createInterface({ input: stream, crlfDelay: Infinity })
    for await (const line of rl) {
      if (!line.trim()) continue
      let o
      try { o = JSON.parse(line) } catch { continue }
      for (const [k, v] of Object.entries(o.tags ?? {})) seen.add(`${k}=${v}`)
    }
    rl.close(); stream.destroy()
    for (const [code, owners] of wanted)
      if (!seen.has(code)) {
        bad(`${owners.join(' · ')}: "${code}" 가 poi.ndjson 에 없다 — 갈래는 실제 태그에서만 만든다`); n++
      }
    if (!n) ok('장소 갈래의 모든 태그가 data/raw/pbf/poi.ndjson 에서 실제로 관측된다')
  }
}

section('🔴 선호 가중치가 새어 들어왔는가')
{
  let n = 0
  for (const node of all)
    for (const [k, vals] of Object.entries(node)) {
      if (k.startsWith('@')) continue
      if (!/coefficient|weight|가중치/i.test(k)) continue
      for (const v of vals)
        if (typeof v['@value'] === 'number') {
          bad(`${short(node['@id'] ?? node['@path'])}: ${short(k)} 에 숫자 ${v['@value']} 가 박혀 있다 — 계수는 짝 비교 실험이 추정한다 (docs/FIELD-STUDY.md)`); n++
        }
    }
  for (const a of attributes)
    if (lit(a, B('role')) === 'cost' && !lits(a, B('coefficientStatus')).length) {
      bad(`${short(a['@id'])}: 비용함수 입력인데 coefficientStatus 자리가 없다`); n++
    }
  if (!n) ok('선호 계수 숫자가 없고, 비용함수 입력 속성은 전부 계수 자리를 비워 두고 있다')
}

section('🔴 미수집 출처')
{
  let n = 0
  for (const node of all) {
    const st = lit(node, B('status'))
    const name = short(node['@id'] ?? node['@path'])
    if (st === '미수집') {
      const fields = [...lits(node, B('field')), ...nodes(node, B('sourceField')).flatMap(s => lits(s, B('field')))]
      if (fields.length) { bad(`${name}: 미수집인데 필드 이름이 적혀 있다 (${fields.join(', ')}) — 지어낸 이름은 실제 응답과 어긋난다`); n++ }
      if (!lits(node, B('expectedFrom')).length) { bad(`${name}: 미수집인데 expectedFrom(어느 출처에서 올 것인가)이 없다`); n++ }
    }
    if (st === '부분수집' && !has(node, B('sourceField')) && !lits(node, B('expectedFrom')).length) {
      bad(`${name}: 부분수집인데 sourceField 도 expectedFrom 도 없다`); n++
    }
    if (lits(node, B('fieldCandidate')).length && !lits(node, B('candidateSource')).length) {
      bad(`${name}: fieldCandidate 를 적었으면 candidateSource(어디서 본 이름인가)를 함께 적어야 한다`); n++
    }
  }
  for (const a of attributes) {
    const st = lit(a, B('status'))
    if (st === '수집됨' && !has(a, B('sourceField'))) { bad(`${short(a['@id'])}: 수집됨인데 sourceField 가 없다`); n++ }
    if (st === '파생' && !ids(a, B('derivedFrom')).length) { bad(`${short(a['@id'])}: 파생인데 derivedFrom 이 없다`); n++ }
  }
  if (!n) ok('미수집 출처에 지어낸 필드 이름이 없고, 수집됨은 전부 출처 필드를 가리킨다')
}

section('출처 필드 대조')
{
  const byDataset = new Map()
  for (const node of all)
    for (const s of nodes(node, B('sourceField'))) {
      const ds = lit(s, B('dataset'))
      if (!ds) { bad(`${short(node['@id'] ?? node['@path'])}: sourceField 에 dataset 이 없다`); continue }
      if (!byDataset.has(ds)) byDataset.set(ds, [])
      byDataset.get(ds).push({ owner: short(node['@id'] ?? node['@path']),
        field: lit(s, B('field')), xml: lit(s, B('xmlElement')) })
    }

  for (const [ds, uses] of byDataset) {
    const p = join(ROOT, ds)
    if (!await exists(p)) { ok(`${ds} 없음 — 건너뜀`); continue }
    let n = 0
    if (ds.endsWith('.ndjson')) {
      const lines = await firstLines(p, 5)
      if (!lines.length) { bad(`${ds}: 비어 있다`); continue }
      let head
      try { head = JSON.parse(lines[0]) } catch (e) { bad(`${ds}: 첫 줄이 JSON 이 아니다 — ${e.message}`); continue }
      for (const u of uses) {
        if (!u.field) { bad(`${u.owner}: ${ds} 를 가리키는데 field 가 없다`); n++; continue }
        if (/[.[]/.test(u.field)) { bad(`${u.owner}: ndjson 은 최상위 키만 검사한다 (${u.field}) — 중첩 위치는 note 에 적는다`); n++; continue }
        if (!(u.field in head)) { bad(`${ds}: 첫 줄에 "${u.field}" 키가 없다 (${u.owner})`); n++; continue }
        if (u.xml && !lines.some(l => l.includes(`<${u.xml}>`))) {
          bad(`${ds}: 앞 몇 줄의 응답 안에 <${u.xml}> 이 없다 (${u.owner})`); n++
        }
      }
    } else if (ds.endsWith('.json')) {
      const j = JSON.parse(await readFile(p, 'utf8'))
      for (const u of uses) {
        if (!u.field) { bad(`${u.owner}: ${ds} 를 가리키는데 field 가 없다`); n++; continue }
        if (resolvePath(j, u.field) === undefined) { bad(`${ds}: 경로 "${u.field}" 가 없다 (${u.owner})`); n++ }
      }
    } else { ok(`${ds}: 검사할 줄 형식이 아니다 — 건너뜀`); continue }
    if (!n) ok(`${ds}: 참조된 필드가 전부 실재한다`)
  }
}

console.log(failed ? `\n🔴 ${failed}건 실패` : '\n전부 통과')
process.exit(failed ? 1 : 0)
