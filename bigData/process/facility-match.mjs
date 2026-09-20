#!/usr/bin/env node
/**
 * 장애인편의시설 조사본을 우리 장소에 붙인다 — 이름으로만 붙인다
 *
 * 왜 이게 필요한가:
 *   확인된 휠체어 접근 정보가 2,762곳 중 102곳(3.7%)뿐이다. 이 조사본은
 *   **추정이 아니라 실측**이라 안전 갈래에 넣을 수 있는 유일한 자료다
 *   (ck_place_feature_safety_never_estimated 가 추정값을 막는다).
 *   docs/DISABLED-FACILITY.md 가 54% 수집분으로 42곳을 쟀고 전량이면 "80곳 안팎"
 *   이라고 **추정**해 두었다 — 그 추정은 확인된 적이 없다. 이 파일이 확인한다.
 *
 * 🔴 좌표로 붙이지 않는다. 이것은 타협하지 않는다
 *   반경 100m 로 붙이면 관광공사 64.0% · 상가 80.9% 가 맞는 것처럼 보이는데
 *   **대부분 다른 건물이다.** 실제로 나온 것: 수영동우담 ↔ 흥해참치(22m) ·
 *   코하루 ↔ 기현원룸(17m) · 서동솔밭집 ↔ 부산광역시립서동도서관(21m).
 *   **접근성 자료를 잘못 붙이면 휠체어 이용자를 못 들어가는 곳으로 보낸다.**
 *   그래서 이름이 정확히 같을 때만 붙이고, 좌표는 붙인 뒤 **검산에만** 쓴다.
 *
 * 🔴 모수를 하나로 고르지 않는다
 *   관광공사 쪽 이름 목록이 두 벌이다 — 원본 수집본(656곳)과 경사 산출물(2,213곳).
 *   운영 DB 에 실제로 들어간 관광공사 장소는 327곳이다(2026-09-21 실측).
 *   어느 것을 모수로 삼느냐에 따라 "몇 곳이 붙는다" 가 몇 배 달라지므로
 *   **둘 다 세어 함께 보고한다.** 하나만 세면 그 수가 혼자 돌아다닌다.
 *
 * 🔴 장소를 찾는 열쇠는 contentid 가 아니라 (sourceType, sourceId) 다
 *   처음에는 contentid 만 냈다. 그러면 상가가 통째로 빠진다 — 상가 장소는 contentid 가
 *   없고 "gabolle:place:SBIZ:" + 상가업소번호 로 만들어지기 때문이다.
 *   그런데 **DB 에 실제로 있는 것은 상가 쪽이 훨씬 많다** (관광공사 13/62 · 상가 36/36).
 *   그래서 둘을 같은 모양으로 낸다:
 *     TOURAPI → "gabolle:place:TOURAPI:" + contentid
 *     SBIZ    → "gabolle:place:SBIZ:"    + 상가업소번호
 *   place_id 계산은 적재기가 다시 하고, 여기 실은 placeId 와 대조해 다르면 그 줄을 거부한다.
 *
 * 입력:  data/raw/facility/pages/page-*.xml        (collect/disabled-facility.mjs)
 *        data/raw/tourapi/tourapi-busan.ndjson     (관광공사 원본)
 *        data/staged/place-slope.ndjson            (관광공사 넓은 목록)
 *        data/staged/place-slope-sbiz.ndjson       (상가 — 세기만 한다)
 * 출력:  data/staged/facility-matched.ndjson
 *        data/staged/_facility-match-run/
 *
 * 한 줄의 모양 — 데이터 파트와 백엔드 사이의 계약이다:
 *   {"sourceType":"SBIZ","sourceId":"MA010120220804042915",
 *    "placeId":"817670a9-1c15-3ce2-bad6-1c1eef7b8d72","title":"사계",
 *    "wfcltId":"...","faclNm":"사계","faclTyCd":"일반음식점",
 *    "distanceM":12,"origin":"place-slope-sbiz"}
 *
 *   distanceM 이 null 이면 **못 잰 것이지 먼 것이 아니다.**
 *   wfcltId 가 다음 단계의 열쇠다 — 실제 조사 항목은 기구표
 *   (getFacInfoOpenApiJpEvalInfoList)에 있다.
 *
 * 🔴 이 파일은 **접근 가능 여부를 판정하지 않는다.** 붙이기까지만 한다.
 *   기구표가 주는 evalInfo 는 "주출입구 접근로, 주출입구(문)" 같은 **조사 항목 이름**이고,
 *   항목이 있다는 것은 **그 항목을 조사했다는 뜻이지 접근 가능하다는 뜻이 아니다.**
 *   기존 BarrierFreeAccessibility 가 같은 선을 긋고 있다 — 원천이 "휠체어 접근 가능" 이라고
 *   **글자 그대로 썼을 때만** WHEELCHAIR 를 붙이고, 주석에 "뜻을 해석하지 않는다" 고 적어 두었다.
 *   판정 규칙은 사람이 값의 사전을 보고 정한다.
 *
 * 종료 코드:
 *   0  쟀다        1  입력이 없다 / 0곳이다
 */
import { mkdir, readFile, readdir, writeFile } from 'node:fs/promises'
import { existsSync } from 'node:fs'
import { createHash } from 'node:crypto'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { stamp } from '../mlops/manifest.mjs'
import { log } from '../lib/log.mjs'

/**
 * 백엔드가 장소를 찾는 식과 같은 계산 — 자바 UUID.nameUUIDFromBytes (MD5 기반 버전3 UUID).
 *
 * 🔴 이 값은 **검산용**이다. 적재기가 같은 식으로 다시 계산해 이 값과 대조하고, 다르면
 *   그 줄을 거부한다. 두 벌이 서로를 검산하므로 어느 쪽이 틀려도 조용히 안 지나간다.
 *   (2026-09-21 백엔드 파트가 파이썬으로 독립 구현해 대조 — 한 글자도 안 틀렸다)
 */
function placeIdOf(sourceType, sourceId) {
  const h = createHash('md5').update('gabolle:place:' + sourceType + ':' + sourceId, 'utf8').digest()
  h[6] = (h[6] & 0x0f) | 0x30
  h[8] = (h[8] & 0x3f) | 0x80
  const x = h.toString('hex')
  return [x.slice(0, 8), x.slice(8, 12), x.slice(12, 16), x.slice(16, 20), x.slice(20, 32)].join('-')
}

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..')
const PAGES = join(ROOT, 'data/raw/facility/pages')
const OUT_DIR = join(ROOT, 'data/staged')
const OUT_FILE = join(OUT_DIR, 'facility-matched.ndjson')

/** 좌표가 이보다 멀면 이름이 같아도 다른 곳으로 본다. 이름이 맞은 것들은 실측 0~18m 였다. */
const MAX_DISTANCE_M = 100

const norm = (s) => String(s || '').replace(/\s/g, '')

function field(xml, tag) {
  const m = xml.match(new RegExp('<' + tag + '>([^<]*)</' + tag + '>'))
  return m ? m[1] : ''
}

/**
 * 🔴 Number('') 는 0 이다. NaN 이 아니다.
 *   좌표가 비어 있는 줄을 그냥 Number() 에 넣으면 위도 0·경도 0(아프리카 앞바다)이 되고
 *   거리가 1만 2천 km 로 나온다. 실제로 그렇게 나왔고, 그 줄들이 「너무 멀다」며
 *   버려지고 있었다. **모르는 것과 틀린 것은 다르게 다뤄야 한다.**
 */
function num(v) {
  const s = String(v ?? '').trim()
  if (!s) return NaN
  const n = Number(s)
  return Number.isFinite(n) && n !== 0 ? n : NaN
}

function metersBetween(lat1, lng1, lat2, lng2) {
  if (![lat1, lng1, lat2, lng2].every(Number.isFinite)) return null
  const dy = (lat1 - lat2) * 111000
  const dx = (lng1 - lng2) * 88000
  return Math.round(Math.hypot(dx, dy))
}

async function readFacilities() {
  if (!existsSync(PAGES)) return null
  const files = (await readdir(PAGES)).filter((f) => f.endsWith('.xml')).sort()
  const rows = []
  let total = 0
  for (const f of files) {
    const xml = await readFile(join(PAGES, f), 'utf8')
    for (const m of xml.matchAll(/<servList>([\s\S]*?)<\/servList>/g)) {
      total++
      const s = m[1]
      const addr = field(s, 'lcMnad')
      if (!addr.startsWith('부산')) continue
      rows.push({
        faclNm: field(s, 'faclNm'),
        faclTyCd: field(s, 'faclTyCd'),
        lcMnad: addr,
        lat: num(field(s, 'faclLat')),
        lng: num(field(s, 'faclLng')),
        wfcltId: field(s, 'wfcltId'),
        salStaNm: field(s, 'salStaNm'),
      })
    }
  }
  return { pages: files.length, total, busan: rows }
}

async function tourapiRaw() {
  const p = join(ROOT, 'data/raw/tourapi/tourapi-busan.ndjson')
  const out = new Map()
  const ambiguous = new Set()
  if (!existsSync(p)) return { byName: out, ambiguous }
  for (const ln of (await readFile(p, 'utf8')).split('\n')) {
    if (!ln) continue
    let o
    try { o = JSON.parse(ln) } catch { continue }
    if (o.op !== 'areaBasedList2') continue
    let j
    try { j = JSON.parse(o.raw) } catch { continue }
    const it = j?.response?.body?.items?.item
    for (const x of Array.isArray(it) ? it : it ? [it] : []) {
      if (!x?.contentid || !x?.title) continue
      const n = norm(x.title)
      const contentid = String(x.contentid)
      const prev = out.get(n)
      // 같은 이름이 두 장소를 가리키면 그 이름은 통째로 버린다 — fromNdjson 주석 참고
      if (prev && prev.contentid !== contentid) { ambiguous.add(n); continue }
      out.set(n, { contentid, title: x.title, lat: num(x.mapy), lng: num(x.mapx) })
    }
  }
  for (const n of ambiguous) out.delete(n)
  return { byName: out, ambiguous }
}

/**
 * 🔴 이름이 두 장소를 가리키면 **그 이름은 통째로 버린다.**
 *
 *   Map 에 그냥 넣으면 같은 이름의 뒤엣것이 앞엣것을 **조용히 덮는다.** 실제로 상가에서
 *   36곳이 34곳으로 줄었고 아무 데도 표시가 안 났다.
 *
 *   그렇다고 둘 다 넣을 수도 없다. 시설 기록은 하나인데 후보가 둘이면 **어느 쪽인지 모른다.**
 *   접근성 자료에서 모르는 것을 찍으면 휠체어 이용자를 못 들어가는 곳으로 보낸다.
 *   그래서 **버리되 몇 개를 버렸는지 반드시 센다.** 세지 않고 버리는 것이 가장 나쁘다.
 */
async function fromNdjson(file, key) {
  const p = join(OUT_DIR, file)
  const out = new Map()
  const ambiguous = new Set()
  if (!existsSync(p)) return { byName: out, ambiguous }
  for (const ln of (await readFile(p, 'utf8')).split('\n')) {
    if (!ln) continue
    let o
    try { o = JSON.parse(ln) } catch { continue }
    if (!o?.title || !o?.[key]) continue
    const n = norm(o.title)
    const id = String(o[key])
    const prev = out.get(n)
    if (prev && prev.id !== id) { ambiguous.add(n); continue }
    out.set(n, { id, title: o.title })
  }
  for (const n of ambiguous) out.delete(n)
  return { byName: out, ambiguous }
}

async function main() {
  log('편의시설 ↔ 우리 장소 붙이기 (이름 일치만)')

  const fac = await readFacilities()
  if (!fac) {
    log('🔴 받은 쪽이 없습니다. 먼저 collect/disabled-facility.mjs 를 돌리십시오.')
    process.exit(1)
  }
  log('  쪽 ' + fac.pages + ' · 전국 행 ' + fac.total + ' · 부산 ' + fac.busan.length)

  const rawR = await tourapiRaw()
  const wideR = await fromNdjson('place-slope.ndjson', 'contentid')
  const sbizR = await fromNdjson('place-slope-sbiz.ndjson', 'sourceId')
  const raw = rawR.byName
  const wide = wideR.byName
  const sbiz = sbizR.byName
  const ambiguousTotal = rawR.ambiguous.size + wideR.ambiguous.size + sbizR.ambiguous.size
  log('  모수  관광공사 원본 ' + raw.size + ' · 관광공사 넓은 목록 ' + wide.size + ' · 상가 ' + sbiz.size)
  log('  🔴 이름이 두 장소를 가리켜 통째로 뺀 이름  ' + ambiguousTotal
    + '  (원본 ' + rawR.ambiguous.size + ' · 넓은 목록 ' + wideR.ambiguous.size + ' · 상가 ' + sbizR.ambiguous.size + ')')
  if (ambiguousTotal) {
    const some = [...sbizR.ambiguous, ...rawR.ambiguous].slice(0, 5)
    log('     예: ' + some.join(' · ') + '  ← 어느 장소인지 모르므로 안 붙인다')
  }

  const rows = []
  const seen = new Set()
  let hitRaw = 0
  let hitWide = 0
  let hitSbiz = 0
  let tooFar = 0
  let closed = 0
  let noCoord = 0
  const farSamples = []

  function emit(sourceType, sourceId, title, f, d, origin) {
    const dedupe = sourceType + ':' + sourceId
    if (seen.has(dedupe)) return
    seen.add(dedupe)
    rows.push({
      sourceType,
      sourceId,
      placeId: placeIdOf(sourceType, sourceId),
      title,
      wfcltId: f.wfcltId,
      faclNm: f.faclNm,
      faclTyCd: f.faclTyCd,
      distanceM: d,
      origin,
    })
  }

  for (const f of fac.busan) {
    const n = norm(f.faclNm)
    if (!n) continue
    if (f.salStaNm && f.salStaNm !== '영업') { closed++; continue }

    // 관광공사 원본 — 양쪽에 좌표가 있어 검산할 수 있는 유일한 쪽이다.
    const r = raw.get(n)
    if (r) {
      hitRaw++
      const d = metersBetween(r.lat, r.lng, f.lat, f.lng)
      if (d != null && d > MAX_DISTANCE_M) {
        tooFar++
        if (farSamples.length < 5) farSamples.push(f.faclNm + ' ' + d + 'm')
      } else {
        if (d == null) noCoord++
        emit('TOURAPI', r.contentid, r.title, f, d, 'tourapi-raw')
      }
    } else {
      const w = wide.get(n)
      // 넓은 목록에는 좌표가 없다 — 검산을 못 했다는 것을 distanceM: null 로 남긴다
      if (w) { hitWide++; emit('TOURAPI', w.id, w.title, f, null, 'place-slope') }
    }

    // 🔴 상가는 관광공사와 **따로** 본다. 두 가지 이유가 있다.
    //   1. 같은 이름이 양쪽에 있을 수 있고, 그때 둘 다 DB 의 서로 다른 장소다
    //   2. 분기 뒤에 두면 관광공사에 먼저 걸린 이름이 상가에서 안 세어져 **적게 나온다.**
    //      실제로 26 으로 찍혔는데 34 였다
    const s = sbiz.get(n)
    if (s) { hitSbiz++; emit('SBIZ', s.id, s.title, f, null, 'place-slope-sbiz') }
  }

  await mkdir(OUT_DIR, { recursive: true })
  await writeFile(OUT_FILE, rows.map((r) => JSON.stringify(r)).join('\n') + '\n')

  log('')
  log('  이름 일치 — 관광공사 원본       ' + hitRaw)
  log('  이름 일치 — 관광공사 넓은 목록   ' + hitWide + '  ← 좌표 검산 못 함')
  log('  이름 일치 — 상가               ' + hitSbiz)
  log('  좌표가 ' + MAX_DISTANCE_M + 'm 넘어 버린 것   ' + tooFar + '  ' + farSamples.join(' · '))
  log('  🔴 좌표가 없어 검산 못 한 것     ' + noCoord + '  ← 버리지 않았다. 모르는 것이지 틀린 것이 아니다')
  log('  영업중이 아니라 버린 것         ' + closed)
  const byType = { TOURAPI: 0, SBIZ: 0 }
  for (const r of rows) byType[r.sourceType]++
  log('  산출물 줄(중복 제거)           ' + rows.length + '  (TOURAPI ' + byType.TOURAPI + ' · SBIZ ' + byType.SBIZ + ')')

  stamp(join(OUT_DIR, '_facility-match-run'), {
    tool: 'process/facility-match.mjs',
    output: 'data/staged/facility-matched.ndjson',
    result: { pages: fac.pages, busan: fac.busan.length, hitRaw, hitWide, hitSbiz, tooFar, noCoord, closed, rows: rows.length },
  })

  if (rows.length === 0) {
    log('🔴 0곳입니다. 빈 산출물을 성공으로 치지 않습니다.')
    process.exit(1)
  }
  log('붙이기 완료')
}

main().catch((e) => { console.error('치명:', e?.stack || e); process.exit(1) })
