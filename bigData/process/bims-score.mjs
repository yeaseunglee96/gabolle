#!/usr/bin/env node
/**
 * BIMS 노선 관광중요도 판정 — 290노선 중 어느 것을 먼저 수집할 것인가
 *
 * 무엇을 판정하나:
 *   "이 노선이 **외국인 관광객의 이동**에 얼마나 중요한가" 를 0~100 으로 매긴다.
 *   수집 예산(일일 트래픽 한도)이 유한하므로 어느 노선을 먼저 볼지 골라야 하고,
 *   그 선택의 근거를 파일로 남긴다.
 *
 * 🔴 **기준을 먼저 정하고 근거를 적은 뒤 계산한다.** 결과를 보고 기준을 고르면
 *    그건 검증이 아니라 사후 정당화다. 아래 CRITERIA 상수가 계산보다 위에 있는 것이
 *    그 순서를 코드로 못 박은 것이다. 가중치·반경·문턱값을 바꾸려면 이 상수를
 *    바꿔야 하고, 그 변경은 diff 에 남는다.
 *
 * 🔴 **네트워크를 쓰지 않는다** (bigData/CLAUDE.md 4절 — process/ 는 계산만).
 *    필요한 원자료는 `collect/bims-route-stops.mjs` 가 이미 받아 둔 캐시에서 읽는다.
 *
 *   node collect/bims-route-stops.mjs     # 먼저 (한 번만, 네트워크)
 *   node process/bims-score.mjs           # 그 다음 (네트워크 없음)
 *
 * 출력: data/staged/_bims-route-score.json
 */
import { readFile, writeFile, mkdir, stat } from 'node:fs/promises'
import { existsSync, createReadStream } from 'node:fs'
import { createInterface } from 'node:readline'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { log } from '../lib/log.mjs'

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..')
const OUT  = join(ROOT, 'data/staged/_bims-route-score.json')

/* ═══════════════════════════════════════════════════════════════════════════
   판정 기준 — 🔴 계산보다 먼저. 여기를 읽으면 점수가 무엇인지 알 수 있어야 한다.
   ═══════════════════════════════════════════════════════════════════════════ */

const CRITERIA = {
  질문: '이 노선이 외국인 관광객의 이동에 얼마나 중요한가 (0~100)',

  신호: [
    {
      이름: '관광정류소 경유',
      만점: 35,
      근거: 'BIMS 가 정류소마다 stoptype 을 붙여 준다. 그 값이 "관광" 인 정류소는 '
          + '우리가 추정한 것이 아니라 운영기관(부산시)이 직접 관광지로 분류한 곳이다. '
          + '1차 자료이므로 가장 큰 가중치를 준다. 이름 맞추기도 좌표 추정도 필요 없다.',
      계산: '경유하는 관광정류소 수 ÷ 3 (3개 이상이면 만점) × 35',
      왜3: '전 부산에 관광정류소가 42개뿐이다. 한 노선이 그중 3개를 지나면 '
         + '우연이 아니라 관광 축을 따라가는 노선이다.',
    },
    {
      이름: '관광 목적지 POI 근접',
      만점: 25,
      근거: '관광정류소 분류는 거칠다(42개). OSM 의 관광 POI 로 촘촘하게 메운다. '
          + '정류소에서 도보권 안에 관광 목적지가 있으면 그 정류소는 관광 수요를 받는다.',
      계산: '반경 300m 안에 관광 목적지 POI 가 하나 이상인 정류소의 비율 × 25',
      왜300m: '보통 걸음 4분. 정류소에서 관광지까지 "걸어간다" 고 말할 수 있는 거리.',
    },
    {
      이름: 'focus 구역(중구·동구) 통과',
      만점: 15,
      근거: 'config/area.json 이 focus 를 "외국인 실동선" 이라고 정해 두었다. '
          + '그 판단을 다시 하지 않고 그대로 쓴다.',
      계산: 'focus bbox 안에 있는 정류소 비율 ÷ 0.25 (1/4 이상이면 만점) × 15',
      왜포화: '노선 전체가 중구에 있을 필요는 없다. 관광 구역을 "지나가는" 것으로 충분하다.',
    },
    {
      이름: '배차간격',
      만점: 15,
      근거: 'config/routes.json 이 이미 쓴 기준을 이어받는다 — 배차가 짧으면 '
          + '같은 시간에 더 많은 차량이 지나가므로 소요시간 표본이 더 빨리 쌓인다.',
      계산: 'headway ≤ 5분 → 만점, ≥ 20분 → 0점, 사이는 선형',
    },
    {
      이름: '관광객 시간대 커버',
      만점: 10,
      근거: '관광객은 이른 아침에 나가고 저녁에 돌아온다. 그 시간에 안 다니는 노선은 '
          + '관광 이동에 못 쓴다.',
      계산: '첫차 ≤ 07:00 이면 5점, 막차 ≥ 21:00 이면 5점',
    },
  ],

  '측정불가 처리': '🔴 어떤 신호를 그 노선에서 측정할 수 없으면(예: headway 가 null, '
    + '좌표를 아는 정류소가 0개) 그 신호에 0점을 주지 않는다. 0점은 "나쁘다" 는 뜻이고 '
    + '"모른다" 와 다르다. 대신 그 신호를 빼고 남은 신호의 가중치를 비례 배분해 100점 '
    + '만점으로 되돌린다. 어떤 신호가 빠졌는지는 노선마다 unmeasured 에 남긴다. '
    + '이 규칙은 결과를 보기 전에 정한 것이다.',

  '신호 무효화': '🔴 어떤 신호가 전 노선의 95% 이상에서 0이면 그 신호는 이 데이터로 '
    + '측정 불가라고 판정하고 전 노선에서 제외한다. 데이터가 없는 것을 "전부 나쁘다" 로 '
    + '읽으면 그 신호가 순위를 흔들지 않으면서도 점수를 깎는다. 이 규칙도 미리 정한다.',

  '점수에 넣지 않는 것': '숙박 POI(hotel·guest_house·hostel)는 "관광객이 가는 곳" 이 '
    + '아니라 "자는 곳" 이라 목적지 신호와 섞으면 해석이 흐려진다. 세기는 하되 점수에 '
    + '넣지 않는다. tourism=motel 은 아예 제외한다 — poi.ndjson 에서 601개로 관광 '
    + '태그 중 최다인데 한국의 모텔은 외국인 관광 숙박이 아니다. 넣으면 최다 클래스가 '
    + '신호를 삼킨다.',
}

/** 관광 "목적지" POI. 위 CRITERIA 의 결정을 코드로 옮긴 것이다. */
const DEST_TOURISM = new Set(['viewpoint', 'attraction', 'museum', 'artwork', 'gallery',
  'theme_park', 'zoo', 'aquarium', 'observatory', 'picnic_site', 'information', 'spa_resort'])
/** 숙박 — 세기만 하고 점수에 넣지 않는다. motel 은 여기에도 넣지 않는다. */
const STAY_TOURISM = new Set(['hotel', 'guest_house', 'hostel'])

const POI_RADIUS_M     = 300
const TOURIST_STOP_FULL = 3      // 관광정류소 몇 개면 만점인가
const FOCUS_SATURATE    = 0.25   // focus 정류소 비율 몇이면 만점인가
const HEADWAY_BEST      = 5      // 분. 이하면 만점
const HEADWAY_WORST     = 20     // 분. 이상이면 0점
const FIRST_BY          = 7 * 60 // 07:00 (분)
const LAST_AFTER        = 21 * 60// 21:00 (분)
const DEGENERATE_FRAC   = 0.95   // 이 비율 이상이 0이면 신호 무효

const W = { touristStop: 35, poi: 25, focus: 15, headway: 15, hours: 10 }

/* ═══════════════════════════════════════════════════════════════════════════ */

const need = async p => { if (!existsSync(p)) return false; return (await stat(p)).size > 0 }

/** 위경도 → 미터. 이 규모(수백 m)에서는 등장방형 근사로 충분하다. */
function distM(aLat, aLon, bLat, bLon) {
  const mPerDegLat = 111132
  const mPerDegLon = 111320 * Math.cos((aLat + bLat) / 2 * Math.PI / 180)
  const dy = (aLat - bLat) * mPerDegLat, dx = (aLon - bLon) * mPerDegLon
  return Math.hypot(dx, dy)
}

/** POI 격자 색인. 정류소 8,789 × POI 13,003 을 전탐색하지 않기 위해. */
function buildGrid(points, cellDeg = 0.005) {
  const g = new Map()
  for (const p of points) {
    const k = `${Math.floor(p.lat / cellDeg)},${Math.floor(p.lon / cellDeg)}`
    if (!g.has(k)) g.set(k, [])
    g.get(k).push(p)
  }
  return { g, cellDeg }
}
function nearAny({ g, cellDeg }, lat, lon, radiusM) {
  const ci = Math.floor(lat / cellDeg), cj = Math.floor(lon / cellDeg)
  for (let i = ci - 1; i <= ci + 1; i++) for (let j = cj - 1; j <= cj + 1; j++) {
    for (const p of g.get(`${i},${j}`) || []) if (distM(lat, lon, p.lat, p.lon) <= radiusM) return true
  }
  return false
}

const hhmm = s => {
  const m = /^(\d{1,2}):(\d{2})$/.exec(String(s || ''))
  return m ? Number(m[1]) * 60 + Number(m[2]) : null
}

/**
 * 🔴 좌표표를 우리 자신의 실측으로 검증한다.
 *
 * `/busStopList` 가 준 정류소 좌표를 그대로 믿지 않는다. 우리는 이미 5노선을
 * 폴링해 두었고, 그 응답에는 **차량 GPS(lat/lin)** 가 정류소 항목에 실려 온다.
 * 같은 정류소(nodeid = bstopid)에 대한 차량 관측 위치의 중앙값과 좌표표를
 * 비교하면, 좌표표가 "그 정류소" 를 가리키는지 독립적으로 확인할 수 있다.
 *
 * 차량 GPS 는 정류소가 아니라 **버스**의 위치이므로 0m 가 나올 수는 없다.
 * 판정은 "같은 정류소를 가리키는가" 이고, 수백 m 안이면 통과다.
 */
async function validateCoords(stopCoords) {
  const dir = join(ROOT, 'data/raw/transit')
  const files = ['bims-2026-08-26.ndjson', 'bims-2026-08-27.ndjson']
    .map(f => join(dir, f)).filter(p => existsSync(p))
  if (files.length === 0) return { done: false, reason: '폴링 원자료가 없어 검증을 건너뜀' }

  const obs = new Map()   // bstopid → [[lat, lon], …]
  let sampled = 0
  const SAMPLE_EVERY = 7  // 전량을 뜯을 필요가 없다. 좌표 확인에는 표본으로 충분하다

  for (const p of files) {
    const rl = createInterface({ input: createReadStream(p, 'utf8'), crlfDelay: Infinity })
    let i = 0
    for await (const line of rl) {
      if (i++ % SAMPLE_EVERY !== 0) continue
      let rec; try { rec = JSON.parse(line) } catch { continue }
      sampled++
      // <item> 안에 nodeid 와 lat/lin 이 함께 있는 것만 (= 그 정류소에 차량이 있었다)
      for (const m of String(rec.raw).matchAll(/<item>([\s\S]*?)<\/item>/g)) {
        const b = m[1]
        const id  = (b.match(/<nodeid>([^<]*)</) || [])[1]
        const la  = Number((b.match(/<lat>([^<]*)</) || [])[1])
        const lo  = Number((b.match(/<lin>([^<]*)</) || [])[1])
        if (!id || !Number.isFinite(la) || !Number.isFinite(lo)) continue
        if (!obs.has(id)) obs.set(id, [])
        const arr = obs.get(id)
        if (arr.length < 40) arr.push([la, lo])
      }
    }
  }

  const med = a => { const s = [...a].sort((x, y) => x - y); return s[s.length >> 1] }
  const dists = []
  let matched = 0, missingInTable = 0
  for (const [id, pts] of obs) {
    const t = stopCoords[id]
    if (!t) { missingInTable++; continue }
    matched++
    dists.push(distM(med(pts.map(p => p[0])), med(pts.map(p => p[1])), t.lat, t.lon))
  }
  dists.sort((a, b) => a - b)
  const q = f => dists.length ? Math.round(dists[Math.floor(f * (dists.length - 1))]) : null
  return {
    done: true,
    방법: '폴링 원자료의 차량 GPS(<lat>/<lin>) 중앙값 vs /busStopList 좌표. 조인 키는 nodeid = bstopid (완전일치, 이름 맞추기 없음)',
    표본레코드: sampled, 관측정류소: obs.size,
    좌표표에있음: matched, 좌표표에없음: missingInTable,
    'ID조인성공률%': obs.size ? +(matched / obs.size * 100).toFixed(1) : null,
    거리m: { p50: q(0.5), p90: q(0.9), p99: q(0.99), max: dists.length ? Math.round(dists[dists.length - 1]) : null },
    '500m이내%': dists.length ? +(dists.filter(d => d <= 500).length / dists.length * 100).toFixed(1) : null,
  }
}

async function main() {
  const coordsPath = join(ROOT, 'data/raw/transit/_stop-coords.json')
  const rsPath     = join(ROOT, 'data/raw/transit/_route-stops.json')
  const allPath    = join(ROOT, 'config/routes-all.json')
  const areaPath   = join(ROOT, 'config/area.json')
  const poiPath    = join(ROOT, 'data/raw/pbf/poi.ndjson')

  // 🔴 입력이 없으면 조용히 통과하지 않는다. 무엇이 없고 무엇을 돌리면 되는지 말하고 exit 2.
  const missing = []
  for (const [p, how] of [
    [allPath,    'config/routes-all.json — 저장소에 있어야 합니다'],
    [areaPath,   'config/area.json — 저장소에 있어야 합니다'],
    [coordsPath, 'node collect/bims-route-stops.mjs --stops-only'],
    [rsPath,     'node collect/bims-route-stops.mjs'],
  ]) if (!await need(p)) missing.push(`  없음: ${p}\n    → ${how}`)
  if (missing.length) {
    console.error('🔴 입력이 없어 점수를 낼 수 없습니다. 없는 것을 지어내지 않습니다.\n')
    console.error(missing.join('\n'))
    process.exit(2)
  }

  const all    = JSON.parse(await readFile(allPath, 'utf8'))
  const area   = JSON.parse(await readFile(areaPath, 'utf8'))
  const coords = JSON.parse(await readFile(coordsPath, 'utf8')).stops
  const rs     = JSON.parse(await readFile(rsPath, 'utf8'))
  const fb     = area.focus.bbox

  log(`정류소 좌표 ${Object.keys(coords).length}건 · 노선별 정류소 ${Object.keys(rs.routes).length}노선 · 전체 ${all.count}노선`)

  // 관광 목적지 POI. 없으면 그 신호는 측정 불가로 간다 — 지어내지 않는다.
  const dest = [], stay = []
  let poiAvailable = false
  if (await need(poiPath)) {
    poiAvailable = true
    const rl = createInterface({ input: createReadStream(poiPath, 'utf8'), crlfDelay: Infinity })
    for await (const line of rl) {
      let o; try { o = JSON.parse(line) } catch { continue }
      if (!Number.isFinite(o.lat) || !Number.isFinite(o.lon)) continue
      const t = o.tags || {}
      if (DEST_TOURISM.has(t.tourism) || t.historic || t.amenity === 'marketplace'
          || t.natural === 'beach' || t.natural === 'hot_spring') dest.push({ lat: o.lat, lon: o.lon })
      else if (STAY_TOURISM.has(t.tourism)) stay.push({ lat: o.lat, lon: o.lon })
    }
    log(`관광 목적지 POI ${dest.length}건 · 숙박 POI ${stay.length}건 (점수 제외)`)
  } else {
    log('⚠ poi.ndjson 이 없습니다. POI 근접 신호는 측정 불가로 처리합니다.')
  }
  const destGrid = buildGrid(dest)
  const stayGrid = buildGrid(stay)

  const validation = await validateCoords(coords)
  if (validation.done) {
    log(`좌표 검증: ID조인 ${validation['ID조인성공률%']}% · 중앙거리 ${validation.거리m.p50}m · 500m이내 ${validation['500m이내%']}%`)
  } else log(`좌표 검증: ${validation.reason}`)

  /* ── 1단계: 노선마다 원시 신호를 잰다 (점수 아직 안 만든다) ── */
  const rows = []
  let noRouteData = 0
  for (const r of all.routes) {
    const entry = rs.routes[r.lineid]
    if (!entry) { noRouteData++; rows.push({ ...r, stops: null, unmeasured: ['정류소목록없음'] }); continue }

    const stops = entry.stops
    let known = 0, tourist = 0, nearDest = 0, nearStay = 0, inFocus = 0
    for (const s of stops) {
      const c = s.bstopid ? coords[s.bstopid] : null
      if (!c) continue                       // 좌표를 모르는 정류소는 분모에서 뺀다
      known++
      if (c.stoptype === '관광') tourist++
      if (poiAvailable && nearAny(destGrid, c.lat, c.lon, POI_RADIUS_M)) nearDest++
      if (poiAvailable && nearAny(stayGrid, c.lat, c.lon, POI_RADIUS_M)) nearStay++
      if (c.lat >= fb.south && c.lat <= fb.north && c.lon >= fb.west && c.lon <= fb.east) inFocus++
    }
    rows.push({
      ...r, stops: stops.length, knownCoord: known,
      coordCoverage: stops.length ? +(known / stops.length).toFixed(3) : 0,
      touristStops: tourist,
      destStops: nearDest, destFrac: known ? +(nearDest / known).toFixed(3) : null,
      stayStops: nearStay,
      focusStops: inFocus, focusFrac: known ? +(inFocus / known).toFixed(3) : null,
    })
  }

  /* ── 2단계: 신호 무효화 검사 (미리 정한 규칙) ── */
  const measured = rows.filter(r => r.stops !== null && r.knownCoord > 0)
  const degenerate = {}
  const zeroFrac = f => measured.length ? measured.filter(f).length / measured.length : 1
  degenerate.touristStop = zeroFrac(r => !r.touristStops) >= DEGENERATE_FRAC
  degenerate.poi         = !poiAvailable || zeroFrac(r => !r.destStops) >= DEGENERATE_FRAC
  degenerate.focus       = zeroFrac(r => !r.focusStops) >= DEGENERATE_FRAC
  for (const [k, v] of Object.entries(degenerate)) if (v) log(`⚠ 신호 무효: ${k} — 측정 노선의 ${(DEGENERATE_FRAC * 100)}% 이상이 0. 점수에서 제외합니다.`)

  /* ── 3단계: 점수 ── */
  for (const r of rows) {
    const parts = {}, un = r.unmeasured ? [...r.unmeasured] : []

    if (r.stops === null) { un.push('관광정류소', 'POI근접', 'focus통과') }
    else if (r.knownCoord === 0) { un.push('관광정류소', 'POI근접', 'focus통과'); un.push('좌표0') }
    else {
      if (degenerate.touristStop) un.push('관광정류소(신호무효)')
      else parts.touristStop = Math.min(r.touristStops / TOURIST_STOP_FULL, 1) * W.touristStop
      if (degenerate.poi) un.push('POI근접(신호무효)')
      else parts.poi = r.destFrac * W.poi
      if (degenerate.focus) un.push('focus통과(신호무효)')
      else parts.focus = Math.min(r.focusFrac / FOCUS_SATURATE, 1) * W.focus
    }

    if (r.headway == null) un.push('배차간격(null)')
    else {
      const t = (HEADWAY_WORST - r.headway) / (HEADWAY_WORST - HEADWAY_BEST)
      parts.headway = Math.max(0, Math.min(1, t)) * W.headway
    }

    const f = hhmm(r.first), l = hhmm(r.last)
    if (f == null && l == null) un.push('첫차막차')
    else {
      // 막차가 자정을 넘기면 first > last 로 보인다. 그 경우 막차 조건은 충족으로 본다.
      const wraps = f != null && l != null && l < f
      parts.hours = (f != null && f <= FIRST_BY ? W.hours / 2 : 0)
                  + (wraps || (l != null && l >= LAST_AFTER) ? W.hours / 2 : 0)
    }

    // 🔴 측정불가 처리: 빠진 신호의 가중치를 남은 신호에 비례 배분한다 (0점을 주지 않는다)
    const usedW = Object.keys(parts).reduce((s, k) => s + W[k], 0)
    const raw   = Object.values(parts).reduce((s, v) => s + v, 0)
    r.signals   = Object.fromEntries(Object.entries(parts).map(([k, v]) => [k, +v.toFixed(2)]))
    r.unmeasured = un
    r.usedWeight = usedW
    r.score = usedW > 0 ? +(raw / usedW * 100).toFixed(2) : null
  }

  rows.sort((a, b) => (b.score ?? -1) - (a.score ?? -1))

  /* ── 4단계: 이 기준이 가진 뻔한 실패 모드를 스스로 검사한다 ──
     비율(destFrac·focusFrac)로 점수를 매기면 **짧은 노선이 유리해진다** —
     정류소가 20개뿐인 마을버스는 높은 비율을 내기 쉽다. 기준을 결과에 맞춰 고치지는
     않되(그건 사후 정당화다), 그 편향이 실제로 생겼는지는 재서 남긴다. */
  const med = a => { const s = [...a].sort((x, y) => x - y); return s.length ? s[s.length >> 1] : null }
  const scoredRows = rows.filter(r => r.score != null && r.stops)
  const topN  = scoredRows.slice(0, 78)
  const 편향검사 = {
    질문: '비율 기반 점수가 짧은 노선(마을버스)을 부당하게 밀어올렸는가',
    상위78_정류소수_중앙값: med(topN.map(r => r.stops)),
    전체_정류소수_중앙값:   med(scoredRows.map(r => r.stops)),
    상위78_관광POI인접정류소_절대수_중앙값: med(topN.map(r => r.destStops)),
    전체_관광POI인접정류소_절대수_중앙값:   med(scoredRows.map(r => r.destStops)),
    상위78_마을버스비율: +(topN.filter(r => r.type === '마을버스').length / topN.length).toFixed(3),
    전체_마을버스비율:   +(scoredRows.filter(r => r.type === '마을버스').length / scoredRows.length).toFixed(3),
  }
  편향검사.판정 = 편향검사.상위78_정류소수_중앙값 >= 편향검사.전체_정류소수_중앙값
    && 편향검사.상위78_마을버스비율 <= 편향검사.전체_마을버스비율
    ? '편향 없음 — 상위 노선이 오히려 더 길고 마을버스 비율도 전체보다 낮다. '
      + '절대수로 봐도 상위 노선이 더 많은 관광 POI 를 지난다.'
    : '🔴 편향 의심 — 상위 노선이 전체보다 짧거나 마을버스에 쏠렸다. 사람이 봐야 한다.'

  /* ── 5단계: 관광 거점 정류소 ──
     "노선을 따라가는" 관점(오퍼레이션 /busInfoByRouteId) 말고 "정류소에서 기다리는"
     관점(/stopArrByBstopid · /bitArrByArsno)으로 모을 때 어디를 볼 것인가.
     고르는 기준: 관광 목적지 POI 도보권(300m) 안에 있으면서 **경유 노선이 많은** 정류소.
     노선이 많다는 것은 그 정류소 하나로 여러 노선의 표본을 동시에 받는다는 뜻이다. */
  const routeCountByStop = new Map()
  for (const e of Object.values(rs.routes)) {
    for (const s of new Set(e.stops.map(x => x.bstopid).filter(Boolean))) {
      routeCountByStop.set(s, (routeCountByStop.get(s) || 0) + 1)
    }
  }
  const 관광거점정류소 = [...routeCountByStop.entries()]
    .map(([id, n]) => ({ id, n, c: coords[id] }))
    .filter(x => x.c && poiAvailable && nearAny(destGrid, x.c.lat, x.c.lon, POI_RADIUS_M))
    .sort((a, b) => b.n - a.n).slice(0, 30)
    .map(x => ({ bstopid: x.id, arsno: x.c.arsno ?? null, name: x.c.name, 경유노선수: x.n,
                 lat: +x.c.lat.toFixed(6), lon: +x.c.lon.toFixed(6) }))

  const out = {
    generatedAt: new Date().toISOString(),
    기준: CRITERIA,
    상수: { POI_RADIUS_M, TOURIST_STOP_FULL, FOCUS_SATURATE, HEADWAY_BEST, HEADWAY_WORST,
            FIRST_BY: '07:00', LAST_AFTER: '21:00', DEGENERATE_FRAC, 가중치: W },
    입력: {
      노선: all.count, 정류소좌표: Object.keys(coords).length,
      노선별정류소_받은수: Object.keys(rs.routes).length,
      노선별정류소_없는수: noRouteData,
      관광정류소_전체: Object.values(coords).filter(c => c.stoptype === '관광').length,
      관광목적지POI: dest.length, 숙박POI: stay.length,
      poi사용: poiAvailable,
    },
    좌표출처: {
      방법: '🔴 이름 맞추기를 쓰지 않았다. BIMS /busInfoByRouteId 의 nodeid 와 '
          + '/busStopList 의 bstopid 가 같은 값이라 완전일치(exact ID join)로 붙였다. '
          + 'OSM 정류소명과 맞추는 경로는 실패율이 높아 쓰지 않았다.',
      전체좌표커버리지: (() => {
        let t = 0, k = 0
        for (const r of rows) if (r.stops) { t += r.stops; k += r.knownCoord }
        return { 정류소_연결시도: t, 좌표확보: k, '커버리지%': t ? +(k / t * 100).toFixed(2) : null }
      })(),
    },
    좌표검증: validation,
    신호무효: degenerate,
    '신호무효 해설': degenerate.touristStop
      ? '🔴 stoptype="관광" 정류소 42곳을 경유하는 노선이 290개 중 0개다. 그 정류소들은 '
      + '부산시티투어버스 전용이고 BIMS 의 일반 노선 목록에 그 버스가 없다. 미리 정한 '
      + '무효화 규칙이 이 신호를 자동으로 뺐다 — 안 뺐다면 전 노선이 35점을 똑같이 '
      + '잃어 순위는 그대로인데 점수만 낮아 보였을 것이다.'
      : null,
    편향검사,
    관광거점정류소,
    '관광거점정류소 용도': '오퍼레이션 /stopArrByBstopid · /bitArrByArsno (정류소 도착정보)는 '
      + '/busInfoByRouteId 와 **별도의 일일 10,000회 예산**을 가진다. 그 예산으로 정류소 '
      + '관점의 표본을 더 받을 수 있고, 그때 어디를 볼지의 후보가 이 목록이다. '
      + '🔴 이번 배정에는 넣지 않았다 — 판단과 근거는 config/bims-assign.json 의 '
      + '"정류소도착정보" 항목에 있다.',
    routes: rows,
  }

  await mkdir(dirname(OUT), { recursive: true })
  await writeFile(OUT, JSON.stringify(out, null, 1))

  const scored = rows.filter(r => r.score != null)
  log('─'.repeat(60))
  log(`점수 산출 ${scored.length}노선 / 전체 ${all.count}`)
  log(`좌표 커버리지 ${out.좌표출처.전체좌표커버리지['커버리지%']}%`)
  log('상위 15:')
  for (const r of scored.slice(0, 15)) {
    log(`  ${String(r.score).padStart(6)}  ${String(r.num).padEnd(10)} 관광정류소 ${String(r.touristStops).padStart(2)} · POI ${String(Math.round((r.destFrac ?? 0) * 100)).padStart(3)}% · focus ${String(Math.round((r.focusFrac ?? 0) * 100)).padStart(3)}% · 배차 ${r.headway ?? '?'} · ${r.start}→${r.end}`)
  }
  log(`저장 ${OUT}`)

  // 🔴 점수를 하나도 못 낸 것은 실패다. 조용히 통과하지 않는다.
  if (scored.length === 0) { console.error('🔴 점수를 낸 노선이 0개입니다.'); process.exit(1) }
  if (noRouteData > 0) {
    log(`⚠ ${noRouteData}노선은 정류소 목록이 없어 배차·시간만으로 점수를 냈습니다.`)
    log('  마저 받으려면: node collect/bims-route-stops.mjs')
  }
}

main().catch(e => { console.error('치명:', e); process.exit(1) })
