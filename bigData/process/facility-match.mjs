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
 * 🔴 상가는 이 적재기로 못 들어간다
 *   백엔드 AccessibilityLoader 는 contentid 로 장소를 찾는다
 *   (TourApiPlaceLoader.placeIdOf). 상가 장소는 contentid 가 없고
 *   "gabolle:place:SBIZ:" + 상가업소번호 로 만들어진다. 그래서 상가에서 이름이 맞아도
 *   **넣을 길이 없다.** 세기는 하되 산출물에는 안 넣고 몇 곳인지 보고만 한다.
 *
 * 입력:  data/raw/facility/pages/page-*.xml        (collect/disabled-facility.mjs)
 *        data/raw/tourapi/tourapi-busan.ndjson     (관광공사 원본)
 *        data/staged/place-slope.ndjson            (관광공사 넓은 목록)
 *        data/staged/place-slope-sbiz.ndjson       (상가 — 세기만 한다)
 * 출력:  data/staged/facility-matched.ndjson
 *        data/staged/_facility-match-run/
 *
 * 한 줄의 모양:
 *   {"contentid":"...","title":"...","wfcltId":"...","faclNm":"...",
 *    "faclTyCd":"...","distanceM":12,"source":"tourapi-raw"}
 *
 *   wfcltId 가 다음 단계의 열쇠다 — 실제 접근성 값(주출입구 접근로·승강기)은
 *   기구표(getFacInfoOpenApiJpEvalInfoList)에 있고 아직 한 번도 안 불렀다.
 *
 * 종료 코드:
 *   0  쟀다        1  입력이 없다 / 0곳이다
 */
import { mkdir, readFile, readdir, writeFile } from 'node:fs/promises'
import { existsSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { stamp } from '../mlops/manifest.mjs'
import { log } from '../lib/log.mjs'

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
  if (!existsSync(p)) return out
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
      out.set(norm(x.title), {
        contentid: String(x.contentid),
        title: x.title,
        lat: num(x.mapy),
        lng: num(x.mapx),
      })
    }
  }
  return out
}

async function fromNdjson(file, key) {
  const p = join(OUT_DIR, file)
  const out = new Map()
  if (!existsSync(p)) return out
  for (const ln of (await readFile(p, 'utf8')).split('\n')) {
    if (!ln) continue
    let o
    try { o = JSON.parse(ln) } catch { continue }
    if (!o?.title || !o?.[key]) continue
    out.set(norm(o.title), { id: String(o[key]), title: o.title })
  }
  return out
}

async function main() {
  log('편의시설 ↔ 우리 장소 붙이기 (이름 일치만)')

  const fac = await readFacilities()
  if (!fac) {
    log('🔴 받은 쪽이 없습니다. 먼저 collect/disabled-facility.mjs 를 돌리십시오.')
    process.exit(1)
  }
  log('  쪽 ' + fac.pages + ' · 전국 행 ' + fac.total + ' · 부산 ' + fac.busan.length)

  const raw = await tourapiRaw()
  const wide = await fromNdjson('place-slope.ndjson', 'contentid')
  const sbiz = await fromNdjson('place-slope-sbiz.ndjson', 'sourceId')
  log('  모수  관광공사 원본 ' + raw.size + ' · 관광공사 넓은 목록 ' + wide.size + ' · 상가 ' + sbiz.size)

  const rows = []
  const seen = new Set()
  let hitRaw = 0
  let hitWide = 0
  let hitSbiz = 0
  let tooFar = 0
  let closed = 0
  let noCoord = 0
  const farSamples = []

  for (const f of fac.busan) {
    const n = norm(f.faclNm)
    if (!n) continue

    // 🔴 상가는 관광공사와 **따로** 센다. 분기 뒤에 두면 같은 이름이 관광공사에 먼저
    //    걸릴 때 상가에서 안 세어져 **적게 나온다.** 실제로 26 으로 찍혔는데 34 였다.
    if (sbiz.has(n)) hitSbiz++

    const r = raw.get(n)
    if (r) {
      hitRaw++
      const d = metersBetween(r.lat, r.lng, f.lat, f.lng)
      if (d != null && d > MAX_DISTANCE_M) {
        tooFar++
        if (farSamples.length < 5) farSamples.push(f.faclNm + ' ' + d + 'm')
        continue
      }
      if (d == null) noCoord++
      if (f.salStaNm && f.salStaNm !== '영업') { closed++; continue }
      if (!seen.has(r.contentid)) {
        seen.add(r.contentid)
        rows.push({
          contentid: r.contentid, title: r.title, wfcltId: f.wfcltId,
          faclNm: f.faclNm, faclTyCd: f.faclTyCd, distanceM: d, source: 'tourapi-raw',
        })
      }
      continue
    }

    const w = wide.get(n)
    if (w) {
      hitWide++
      if (f.salStaNm && f.salStaNm !== '영업') { closed++; continue }
      if (!seen.has(w.id)) {
        seen.add(w.id)
        // 넓은 목록에는 좌표가 없다 — 검산을 못 했다는 것을 줄에 남긴다
        rows.push({
          contentid: w.id, title: w.title, wfcltId: f.wfcltId,
          faclNm: f.faclNm, faclTyCd: f.faclTyCd, distanceM: null, source: 'place-slope',
        })
      }
      continue
    }
  }

  await mkdir(OUT_DIR, { recursive: true })
  await writeFile(OUT_FILE, rows.map((r) => JSON.stringify(r)).join('\n') + '\n')

  log('')
  log('  이름 일치 — 관광공사 원본       ' + hitRaw)
  log('  이름 일치 — 관광공사 넓은 목록   ' + hitWide + '  ← 좌표 검산 못 함')
  log('  이름 일치 — 상가               ' + hitSbiz + '  🔴 contentid 가 없어 이 적재기로는 못 들어감')
  log('  좌표가 ' + MAX_DISTANCE_M + 'm 넘어 버린 것   ' + tooFar + '  ' + farSamples.join(' · '))
  log('  🔴 좌표가 없어 검산 못 한 것     ' + noCoord + '  ← 버리지 않았다. 모르는 것이지 틀린 것이 아니다')
  log('  영업중이 아니라 버린 것         ' + closed)
  log('  산출물 줄(중복 제거)           ' + rows.length)

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
