#!/usr/bin/env node
/**
 * 세 데이터를 같은 가게로 잇는다 — 인허가 · 상가정보 · 관광공사
 *
 * 왜 이어야 하나:
 *   셋은 겹치는 데이터가 아니라 **서로 다른 질문에 답한다.**
 *
 *     상가정보(159,689곳)  거기 무엇이 있나 — 상호·업종·좌표
 *     인허가(183,144행)    그게 진짜인가 — 몇 년 버텼나, 죽었나 (폐업 124,910)
 *     관광공사(662곳)      지금 열었나 — 영업시간·휴무일
 *
 *   따로 있으면 각각 반쪽이다. 이어야 한 장소에 대해 **"어디에 있고 · 몇 년
 *   버텼고 · 언제 문 여는가"** 를 한꺼번에 말할 수 있다.
 *
 * 🔴 이름만으로 잇지 않는다
 *   "본가", "할매국밥" 같은 이름은 부산에만 수십 개다. 이름만 맞추면 서로 다른
 *   가게가 한 덩어리가 되고, 그 뒤의 모든 계산이 조용히 틀린다.
 *   그래서 **이름과 자리를 함께** 본다.
 *
 * 잇는 방법 둘 — 확신이 센 것부터
 *   1) 이름 + 도로명주소(건물번호까지)가 같다      … 확신 높음
 *   2) 이름이 같고 좌표가 100m 안에 있다           … 주소 표기가 다를 때
 *   두 방법 다 이름은 정규화해서 견준다 (공백·괄호·기호 제거).
 *
 * 🔴 못 이은 것을 "없는 것" 으로 만들지 않는다
 *   관광공사 662곳 중 상당수는 애초에 가게가 아니라 해수욕장·전망대다.
 *   인허가는 음식·주류 5종뿐이라 그런 곳이 있을 수 없다. **안 이어지는 것이
 *   정상인 짝이 있다.** 그래서 이 파일은 "몇 %가 이어졌나" 를 낼 때
 *   **분류별로 나눠서** 낸다 — 뭉치면 낮은 숫자가 실패처럼 보인다.
 *
 * 입력:  data/staged/permits-wgs84.ndjson             (process/permits-wgs84.mjs)
 *        data/raw/poi/sbiz-poi-busan-202606.csv
 *        data/raw/tourapi/tourapi-busan.ndjson         (collect/tourapi.mjs)
 * 출력:  data/staged/place-link.ndjson
 *        data/staged/_place-link-run/
 *
 * 실행:  node process/place-link.mjs
 *
 * 종료 코드:
 *   0  이었다
 *   2  입력이 없다
 *   1  불변식이 깨졌다 (0건, 이은 비율이 기준 미달)
 *
 * 🔴 네트워크를 쓰지 않는다.
 */
import { mkdir, readFile, writeFile } from 'node:fs/promises'
import { existsSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { stamp } from '../mlops/manifest.mjs'
import { log } from '../lib/log.mjs'

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..')
const PERMITS = join(ROOT, 'data/staged/permits-wgs84.ndjson')
const SBIZ = join(ROOT, 'data/raw/poi/sbiz-poi-busan-202606.csv')
const TOUR = join(ROOT, 'data/raw/tourapi/tourapi-busan.ndjson')
const OUT_DIR = join(ROOT, 'data/staged')
const OUT_FILE = join(OUT_DIR, 'place-link.ndjson')

/** 좌표로 이을 때 허용하는 거리. 이보다 멀면 다른 가게로 본다. */
const NEAR_METERS = 100

/**
 * 🔴 상가정보에 인허가가 붙는 비율의 바닥.
 *    2026-09-08 실측은 아래 로그가 찍는다. 이 값을 밑돌면 이름·주소 정규화가
 *    망가졌다는 뜻이다 — 원본 형식이 바뀌면 조용히 0 에 수렴하므로 검사로 막는다.
 */
const MIN_SBIZ_FOOD_LINK_RATE = 0.20


function splitCsv(line) {
  const out = []
  let cur = '', q = false
  line = line.replace(/\r$/, '')
  for (let i = 0; i < line.length; i++) {
    const c = line[i]
    if (q) {
      if (c === '"') { if (line[i + 1] === '"') { cur += '"'; i++ } else q = false }
      else cur += c
    } else if (c === '"') q = true
    else if (c === ',') { out.push(cur); cur = '' }
    else cur += c
  }
  out.push(cur)
  return out
}

const normName = (s) => String(s ?? '').replace(/\(.*?\)/g, '').replace(/[\s\-_.,'"·`!?&+]/g, '').toLowerCase()

const normAddr = (s) => {
  const t = String(s ?? '').replace(/\(.*?\)/g, ' ').replace(/\s+/g, ' ').trim()
  const m = t.match(/^(.*?\d+(?:-\d+)?)(?:\s|$)/)
  return (m ? m[1] : t).replace(/\s/g, '')
}

function distM(lon1, lat1, lon2, lat2) {
  const R = 6371000
  const dLat = (lat2 - lat1) * Math.PI / 180
  const dLon = (lon2 - lon1) * Math.PI / 180
  const mLat = (lat1 + lat2) / 2 * Math.PI / 180
  return R * Math.hypot(dLat, dLon * Math.cos(mLat))
}

/** 좌표를 격자 칸으로 나눠 담는다. 17만 × 16만을 전부 견주지 않기 위한 것. */
const CELL = 0.0015   // 약 150 m
const cellKey = (lon, lat) => `${Math.round(lon / CELL)},${Math.round(lat / CELL)}`

async function main() {
  for (const [p, hint] of [[PERMITS, 'node process/permits-wgs84.mjs'], [SBIZ, '상가정보 CSV'], [TOUR, 'npm run collect:tourapi']]) {
    if (!existsSync(p)) { log(`🔴 입력이 없습니다: ${p}`); log(`   먼저: ${hint}`); process.exit(2) }
  }

  log('세 데이터 잇기 (인허가 · 상가정보 · 관광공사)')

  // ── 인허가 색인 ─────────────────────────────────────────────────────────
  const permitByKey = new Map()      // 이름|주소 → [레코드…]
  const permitByCell = new Map()     // 격자 → [레코드…]
  let permitRows = 0
  for (const line of (await readFile(PERMITS, 'utf8')).split('\n')) {
    if (!line.trim()) continue
    const o = JSON.parse(line)
    permitRows++
    const n = normName(o.name)
    if (!n) continue
    const k = `${n}|${normAddr(o.roadAddr)}`
    if (normAddr(o.roadAddr)) (permitByKey.get(k) ?? permitByKey.set(k, []).get(k)).push(o)
    if (o.lon != null) {
      const ck = cellKey(o.lon, o.lat)
      ;(permitByCell.get(ck) ?? permitByCell.set(ck, []).get(ck)).push(o)
    }
  }
  log(`  인허가 ${permitRows}행 색인 완료`)

  // ── 관광공사 목록 (title · addr1 · mapx · mapy) ──────────────────────────
  const tours = []
  for (const line of (await readFile(TOUR, 'utf8')).split('\n')) {
    if (!line.trim()) continue
    const rec = JSON.parse(line)
    if (rec.stage !== 'list') continue
    const items = [].concat(JSON.parse(rec.raw)?.response?.body?.items?.item ?? [])
    for (const it of items) {
      const lon = Number(it.mapx), lat = Number(it.mapy)
      tours.push({
        contentid: it.contentid,
        contentTypeId: Number(it.contenttypeid ?? rec.contentTypeId),
        title: it.title,
        addr: it.addr1,
        lon: Number.isFinite(lon) ? lon : null,
        lat: Number.isFinite(lat) ? lat : null,
      })
    }
  }
  log(`  관광공사 ${tours.length}곳`)

  /** 한 곳에 붙는 인허가를 찾는다. 반환 {rec, how} 또는 null */
  function findPermit(name, addr, lon, lat) {
    const n = normName(name)
    if (!n) return null
    const a = normAddr(addr)
    if (a) {
      const hit = permitByKey.get(`${n}|${a}`)
      if (hit?.length) return { rec: hit[0], how: 'name+addr' }
    }
    if (lon == null) return null
    let best = null, bestD = Infinity
    const cx = Math.round(lon / CELL), cy = Math.round(lat / CELL)
    for (let i = -1; i <= 1; i++) for (let j = -1; j <= 1; j++) {
      for (const r of permitByCell.get(`${cx + i},${cy + j}`) ?? []) {
        if (normName(r.name) !== n) continue
        const d = distM(lon, lat, r.lon, r.lat)
        if (d < bestD) { bestD = d; best = r }
      }
    }
    return best && bestD <= NEAR_METERS ? { rec: best, how: `name+near(${bestD.toFixed(0)}m)` } : null
  }

  // ── 상가정보를 훑으며 잇는다 ─────────────────────────────────────────────
  const out = []
  const text = await readFile(SBIZ, 'utf8')
  const lines = text.split('\n')
  const head = splitCsv(lines[0])
  const ci = {
    id: head.indexOf('상가업소번호'), name: head.indexOf('상호명'),
    big: head.indexOf('상권업종대분류명'), mid: head.indexOf('상권업종중분류명'),
    road: head.indexOf('도로명주소'), lon: head.indexOf('경도'), lat: head.indexOf('위도'),
    gu: head.indexOf('시군구명'),
  }
  if (Object.values(ci).some((v) => v < 0)) { log('🔴 상가정보 CSV 의 칸 이름이 바뀌었습니다.'); process.exit(1) }

  let sbizRows = 0, sbizFood = 0, sbizFoodLinked = 0, linkedAny = 0
  const howTally = {}
  for (let i = 1; i < lines.length; i++) {
    if (!lines[i].trim()) continue
    const c = splitCsv(lines[i])
    sbizRows++
    const big = c[ci.big]
    const isFood = big === '음식'
    if (isFood) sbizFood++
    const lon = Number(c[ci.lon]), lat = Number(c[ci.lat])
    const hit = findPermit(c[ci.name], c[ci.road], Number.isFinite(lon) ? lon : null, Number.isFinite(lat) ? lat : null)
    if (!hit) continue
    linkedAny++
    if (isFood) sbizFoodLinked++
    howTally[hit.how.replace(/\(.*\)/, '')] = (howTally[hit.how.replace(/\(.*\)/, '')] ?? 0) + 1
    out.push(JSON.stringify({
      sbizId: c[ci.id], name: c[ci.name], gu: c[ci.gu],
      category: { big, mid: c[ci.mid] },
      lon: Number.isFinite(lon) ? lon : null, lat: Number.isFinite(lat) ? lat : null,
      permit: {
        id: hit.rec.id, state: hit.rec.state,
        openedOn: hit.rec.openedOn, closedOn: hit.rec.closedOn,
        category: hit.rec.category,
      },
      tour: null,
      how: hit.how,
    }))
  }
  log(`  상가정보 ${sbizRows}행 · 음식 ${sbizFood}곳`)
  log(`    인허가가 붙은 것 ${linkedAny} (음식만 ${sbizFoodLinked} · ${(100 * sbizFoodLinked / sbizFood).toFixed(1)}%)`)
  for (const [k, v] of Object.entries(howTally)) log(`      ${k}: ${v}`)

  // ── 관광공사를 붙인다 ────────────────────────────────────────────────────
  // 상가정보 쪽 색인을 만들어 놓고 관광공사를 견준다.
  const sbizByKey = new Map()
  for (const l of out) {
    const o = JSON.parse(l)
    const n = normName(o.name)
    if (n && o.lon != null) {
      const ck = cellKey(o.lon, o.lat)
      ;(sbizByKey.get(ck) ?? sbizByKey.set(ck, []).get(ck)).push(o)
    }
  }
  let tourLinked = 0
  const tourByType = {}
  const linkedIndex = new Map(out.map((l, i) => [JSON.parse(l).sbizId, i]))
  for (const t of tours) {
    tourByType[t.contentTypeId] ??= { total: 0, linked: 0 }
    tourByType[t.contentTypeId].total++
    if (t.lon == null) continue
    const n = normName(t.title)
    let best = null, bestD = Infinity
    const cx = Math.round(t.lon / CELL), cy = Math.round(t.lat / CELL)
    for (let i = -1; i <= 1; i++) for (let j = -1; j <= 1; j++) {
      for (const o of sbizByKey.get(`${cx + i},${cy + j}`) ?? []) {
        if (normName(o.name) !== n) continue
        const d = distM(t.lon, t.lat, o.lon, o.lat)
        if (d < bestD) { bestD = d; best = o }
      }
    }
    if (best && bestD <= NEAR_METERS) {
      const idx = linkedIndex.get(best.sbizId)
      if (idx != null) {
        const o = JSON.parse(out[idx])
        o.tour = { contentid: t.contentid, contentTypeId: t.contentTypeId, title: t.title }
        out[idx] = JSON.stringify(o)
        tourLinked++
        tourByType[t.contentTypeId].linked++
      }
    }
  }
  log(`  관광공사가 셋 다 붙은 것 ${tourLinked}곳`)

  await mkdir(OUT_DIR, { recursive: true })
  await writeFile(OUT_FILE, out.join('\n') + '\n')

  const rate = sbizFood ? sbizFoodLinked / sbizFood : 0
  stamp(join(OUT_DIR, '_place-link-run'), {
    step: 'process/place-link',
    inputs: [PERMITS, SBIZ, TOUR],
    params: { nearMeters: NEAR_METERS, minSbizFoodLinkRate: MIN_SBIZ_FOOD_LINK_RATE },
    result: { permitRows, sbizRows, sbizFood, sbizFoodLinked, foodLinkRate: Number(rate.toFixed(4)), linkedAny, tours: tours.length, tourLinked, tourByType, howTally, out: 'data/staged/place-link.ndjson' },
  })

  log(`저장 완료: ${out.length}줄`)

  // ── 불변식 ────────────────────────────────────────────────────────────
  if (out.length === 0) { log('🔴 한 건도 잇지 못했습니다.'); process.exit(1) }
  if (rate < MIN_SBIZ_FOOD_LINK_RATE) {
    log(`🔴 음식 업종이 인허가에 붙은 비율 ${(rate * 100).toFixed(1)}% 가 기준 ${(MIN_SBIZ_FOOD_LINK_RATE * 100).toFixed(0)}% 아래입니다.`)
    log('   이름·주소 정규화가 망가졌거나 원본 형식이 바뀌었습니다.')
    process.exit(1)
  }
}

main().catch((e) => { console.error('치명:', e?.stack || e); process.exit(1) })
