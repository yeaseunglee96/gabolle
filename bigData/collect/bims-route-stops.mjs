#!/usr/bin/env node
/**
 * BIMS 정적 데이터 수집 — 정류소 좌표표 + 노선별 정류소 목록
 *
 * 왜 필요한가:
 *   `config/routes-all.json` 에는 노선당 배차간격·첫차·막차·기점·종점만 있다.
 *   "이 노선이 관광지를 지나는가" 를 판정하려면 **그 노선이 서는 정류소**와
 *   **그 정류소가 어디인가(좌표)** 가 있어야 한다. 둘 다 여기서 받는다.
 *   `config/routes.json` 의 "//다음" 주석이 예고한 단계다.
 *
 * 🔴 **이것은 폴링이 아니라 한 번만 도는 수집기다.**
 *    정류소 좌표와 노선의 정류소 순서는 노선이 개편되지 않는 한 바뀌지 않는다.
 *    받아서 캐시에 남기고 다시 부르지 않는다. `process/bims-score.mjs` 는
 *    네트워크를 쓰지 않고 이 캐시만 읽는다
 *    (bigData/CLAUDE.md 4절 — collect/ 는 네트워크 O, process/ 는 네트워크 X).
 *
 * 🔴 **일일 트래픽 한도는 계정마다, 그리고 오퍼레이션(상세기능)마다 따로 10,000회다.**
 *    오퍼레이션 = 이 API 가 제공하는 개별 기능 하나. 이 API 에는 6개가 있고
 *    각각이 자기 몫의 10,000회를 가진다. 그래서:
 *
 *      · 정류소 좌표는 `/busStopList` 에서 받는다 — **1회 호출로 전량**이고,
 *        실시간 수집기(`/busInfoByRouteId`)의 예산을 한 방울도 안 쓴다.
 *      · 노선별 정류소 순서만은 `/busInfoByRouteId` 를 써야 한다. 이 정보를 주는
 *        오퍼레이션이 그것뿐이다. 노선당 1회씩, 딱 한 번만 낸다.
 *
 *    한 번 실행의 호출 상한을 코드로 건다 — `collect/overpass.mjs` 의 면적 상한
 *    200km2 와 같은 장치다. 문서로 부탁하지 않고 코드가 막는다.
 *
 * 이어서 받는다 — 캐시에 이미 있는 노선은 건너뛴다. 끊겨도 다시 돌리면 이어진다.
 *
 *   node collect/bims-route-stops.mjs                 # 캐시에 없는 것만
 *   node collect/bims-route-stops.mjs --max 50        # 이번 실행은 50회까지만
 *   node collect/bims-route-stops.mjs --stops-only    # 정류소 좌표표만 (1회)
 *   node collect/bims-route-stops.mjs --force         # 캐시를 버리고 다시
 */
import { readFile, writeFile, mkdir } from 'node:fs/promises'
import { existsSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { log } from '../lib/log.mjs'

const ROOT   = join(dirname(fileURLToPath(import.meta.url)), '..')
const OUT    = join(ROOT, 'data/raw/transit')
const COORDS = join(OUT, '_stop-coords.json')     // 정류소 → 좌표 (오퍼레이션 /busStopList)
const CACHE  = join(OUT, '_route-stops.json')     // 노선 → 정류소 순서 (/busInfoByRouteId)

/**
 * 🔴 한 번 실행에 부를 수 있는 최대 횟수.
 *    290노선 + 좌표표 1회 = 291 을 한 번에 끝내고도 남되, 실수로 루프가 돌아도
 *    하루 한도(오퍼레이션당 10,000)를 태우지 못하는 크기여야 한다.
 *    같은 키로 실시간 폴러가 함께 돌고 있다는 것을 전제로 잡은 값이다.
 */
const MAX_CALLS = 300
const PAUSE_MS  = 250     // 호출 사이 휴식. 공공 API 에 대한 예의다. 줄이지 말 것

/** 오퍼레이션 경로. End Point 뒤에 붙는다. .env 의 BIMS_ENDPOINT 에서 호스트+베이스를 얻는다. */
const OP_STOPS  = '/busStopList'
const OP_ROUTE  = '/busInfoByRouteId'

const args      = process.argv.slice(2)
const FORCE     = args.includes('--force')
const STOPS_ONLY= args.includes('--stops-only')
const MAX_ARG   = args.includes('--max') ? Number(args[args.indexOf('--max') + 1]) : MAX_CALLS

const sleep = ms => new Promise(r => setTimeout(r, ms))

let calls = 0
const budget = Math.min(MAX_ARG, MAX_CALLS)

async function loadEnv() {
  const p = join(ROOT, '.env')
  if (!existsSync(p)) return
  for (const line of (await readFile(p, 'utf8')).split('\n')) {
    const m = line.match(/^\s*([A-Z_][A-Z0-9_]*)\s*=\s*(.*)\s*$/)
    if (m && !process.env[m[1]]) process.env[m[1]] = m[2].replace(/^["']|["']$/g, '')
  }
}

/** <item>…</item> 를 평평한 객체 배열로. 응답이 XML 로만 오므로 직접 뜯는다. */
function parseItems(xml) {
  const items = []
  for (const m of xml.matchAll(/<item>([\s\S]*?)<\/item>/g)) {
    const o = {}
    for (const f of m[1].matchAll(/<([a-zA-Z0-9_]+)>([\s\S]*?)<\/\1>/g)) o[f[1]] = f[2].trim()
    items.push(o)
  }
  return items
}

/** 🔴 키가 들어간 URL 은 만들기만 하고 절대 로그·산출물에 남기지 않는다. */
async function call(base, op, key, params, timeoutMs = 30000) {
  if (calls >= budget) throw new Error(`호출 상한 ${budget} 도달 — 멈춥니다`)
  const url = new URL(base + op)
  url.searchParams.set('serviceKey', key)
  for (const [k, v] of Object.entries(params)) url.searchParams.set(k, v)
  calls++
  const res  = await fetch(url, { signal: AbortSignal.timeout(timeoutMs) })
  const text = await res.text()
  if (!res.ok) throw new Error(`HTTP ${res.status}`)
  // 공공데이터포털은 에러도 200 으로 XML 을 돌려준다. 그것을 성공으로 세지 않는다.
  if (/errMsg|OpenAPI_ServiceResponse|SERVICE ERROR/i.test(text)) {
    throw new Error('API 오류 응답: ' + text.replace(/\s+/g, ' ').slice(0, 120))
  }
  return text
}

/** 부산 전 정류소의 좌표표. totalCount 를 한 페이지에 다 담아 1회로 끝낸다. */
async function fetchStopCoords(base, key) {
  if (!FORCE && existsSync(COORDS)) {
    const c = JSON.parse(await readFile(COORDS, 'utf8'))
    log(`정류소 좌표표: 캐시 ${Object.keys(c.stops).length}건 — 호출 0회`)
    return c
  }
  log('정류소 좌표표를 받습니다 (/busStopList, 1회)')
  const text  = await call(base, OP_STOPS, key, { numOfRows: '9000', pageNo: '1' }, 90000)
  const total = Number((text.match(/<totalCount>(\d+)</) || [])[1] || 0)
  const items = parseItems(text)

  // 🔴 한 페이지에 다 안 들어왔으면 조용히 넘어가지 않는다. 부족한 채로 점수를 내면
  //    "좌표가 없어서 빠진 정류소" 가 "관광지가 없는 정류소" 로 둔갑한다.
  if (total && items.length < total) {
    log(`⚠ ${items.length}/${total} 만 왔습니다. 페이지를 넘겨 마저 받습니다.`)
    for (let page = 2; items.length < total && calls < budget; page++) {
      const more = await call(base, OP_STOPS, key, { numOfRows: '9000', pageNo: String(page) }, 90000)
      const got  = parseItems(more)
      if (got.length === 0) break
      items.push(...got)
      await sleep(PAUSE_MS)
    }
  }

  const stops = {}
  let bad = 0
  for (const it of items) {
    const lat = Number(it.gpsy), lon = Number(it.gpsx)
    // 좌표가 없거나 부산 범위 밖이면 버린다. 지어내서 채우지 않는다.
    if (!Number.isFinite(lat) || !Number.isFinite(lon) || lat < 34.5 || lat > 35.6 || lon < 128.5 || lon > 129.5) { bad++; continue }
    stops[it.bstopid] = { name: it.bstopnm, arsno: it.arsno, lat, lon, stoptype: it.stoptype ?? null }
  }
  const out = { fetchedAt: new Date().toISOString(), source: `data.go.kr BusanBIMS ${OP_STOPS}`,
                totalCount: total, parsed: items.length, dropped: bad, stops }
  await writeFile(COORDS, JSON.stringify(out))
  log(`정류소 좌표표: ${Object.keys(stops).length}건 저장 (버린 것 ${bad})`)
  return out
}

/** 노선별 정류소 순서. 이 정보를 주는 오퍼레이션이 /busInfoByRouteId 뿐이라 노선당 1회. */
async function fetchRouteStops(base, key, all) {
  let cache = { fetchedAt: null, source: `data.go.kr BusanBIMS ${OP_ROUTE}`, routes: {} }
  if (!FORCE && existsSync(CACHE)) cache = JSON.parse(await readFile(CACHE, 'utf8'))

  const todo = all.filter(r => !cache.routes[r.lineid])
  if (todo.length === 0) {
    log(`노선별 정류소: 캐시에 ${Object.keys(cache.routes).length}노선 — 호출 0회`)
    return cache
  }
  const room = budget - calls
  log(`노선별 정류소: ${todo.length}노선 남음, 이번 실행 여유 ${room}회`)
  if (todo.length > room) log(`⚠ 여유가 모자랍니다. ${room}노선만 받고 멈춥니다 — 다시 돌리면 이어집니다.`)

  let ok = 0, fail = 0
  for (const r of todo) {
    if (calls >= budget) break
    try {
      const text  = await call(base, OP_ROUTE, key, { numOfRows: '500', pageNo: '1', lineid: r.lineid })
      const items = parseItems(text)
      if (items.length === 0) throw new Error('item 이 0개')
      cache.routes[r.lineid] = {
        num: r.num,
        stops: items.map(it => ({
          idx:    Number(it.bstopidx),
          name:   it.bstopnm ?? null,
          bstopid: it.nodeid ?? null,     // 🔴 /busStopList 의 bstopid 와 같은 값이다 (실측 확인)
          arsno:  it.arsno ?? null,
        })).filter(s => Number.isFinite(s.idx)),
      }
      ok++
      if (ok % 25 === 0) { log(`  ${ok}/${todo.length} …`); await writeFile(CACHE, JSON.stringify(cache)) }
    } catch (e) {
      fail++
      log(`  ⚠ ${r.num} (${r.lineid}): ${e.message}`)
      if (/호출 상한/.test(e.message)) break
    }
    await sleep(PAUSE_MS)
  }
  cache.fetchedAt = new Date().toISOString()
  await writeFile(CACHE, JSON.stringify(cache))
  log(`노선별 정류소: 성공 ${ok} · 실패 ${fail} · 누적 ${Object.keys(cache.routes).length}/${all.length}`)
  return { ...cache, _ok: ok, _fail: fail }
}

async function main() {
  await loadEnv()
  const key = process.env.DATA_GO_KR_KEY
  const ep  = process.env.BIMS_ENDPOINT

  // 🔴 지어낸 주소로 조용히 실패하지 않는다. bims-poll.mjs 와 같은 태도다.
  if (!ep) {
    console.error('🔴 BIMS_ENDPOINT 가 없습니다. 요청주소를 지어내지 않습니다.')
    console.error('   bigData/.env 에 활용신청 상세의 요청주소를 그대로 넣으십시오.')
    console.error('   예) https://apis.data.go.kr/6260000/BusanBIMS/busInfoByRouteId')
    process.exit(2)
  }
  if (!key) {
    console.error('🔴 DATA_GO_KR_KEY 가 없습니다. docs/SOURCES.md 1번을 보십시오.')
    process.exit(2)
  }
  // 오퍼레이션 경로를 떼어 베이스만 남긴다. .env 는 실시간 오퍼레이션을 가리키고 있다.
  const base = ep.replace(/\/[^/]*$/, '')

  const allPath = join(ROOT, 'config/routes-all.json')
  if (!existsSync(allPath)) {
    console.error('🔴 config/routes-all.json 이 없습니다. 노선 목록 없이는 받을 것이 없습니다.')
    process.exit(2)
  }
  const all = JSON.parse(await readFile(allPath, 'utf8')).routes

  await mkdir(OUT, { recursive: true })
  log(`베이스 ${base}   1회 실행 호출 상한 ${budget}`)   // 🔴 키가 든 URL 은 안 찍는다

  const coords = await fetchStopCoords(base, key)
  if (STOPS_ONLY) { log(`끝. 호출 ${calls}회.`); return }
  const routes = await fetchRouteStops(base, key, all)

  log('─'.repeat(50))
  log(`총 호출 ${calls}회 (상한 ${budget})`)
  log(`  정류소 좌표 ${Object.keys(coords.stops).length}건 → ${COORDS}`)
  log(`  노선 ${Object.keys(routes.routes).length}/${all.length} → ${CACHE}`)

  // 🔴 실패를 숨기지 않는다.
  if (Object.keys(routes.routes).length === 0) {
    console.error('🔴 한 노선도 받지 못했습니다.'); process.exit(1)
  }
  if (Object.keys(routes.routes).length < all.length) {
    log(`⚠ ${all.length - Object.keys(routes.routes).length}노선이 아직 없습니다. 다시 돌리면 이어집니다.`)
    process.exitCode = 1
  }
}

main().catch(e => { console.error('치명:', e); process.exit(1) })
