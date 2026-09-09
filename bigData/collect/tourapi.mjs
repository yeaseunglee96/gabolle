#!/usr/bin/env node
/**
 * 한국관광공사 국문 관광정보 (TourAPI) 수집기 — 영업시간·휴무일
 *
 * 왜 이걸 받나:
 *   "문 닫은 곳을 피해서 일정을 짜준다" 는 영업시간 없이는 성립하지 않는다.
 *   그런데 영업시간은 **계산으로 못 만든다.** OSM 의 opening_hours 태그는 부산
 *   전역에서 거의 비어 있고(collect 범위를 넓힐수록 나빠진다 — ../CLAUDE.md 6절),
 *   리뷰 사이트는 저장이 금지돼 있다(../CLAUDE.md 1절). 받아야만 알 수 있다.
 *
 *   활용신청은 2026-09-06 에 승인됐는데 **한 번도 호출하지 않은 채로 있었다.**
 *   그래서 온톨로지의 bm:openingHoursNormalized 가 계속 비어 있었다.
 *
 * 🔴 영업시간 필드 이름이 종류마다 다르다 — 하나로 받으면 대부분이 빈다
 *   2026-09-08 실측:
 *     음식점(39)   opentimefood     restdatefood
 *     관광지(12)   usetime          restdate
 *     문화시설(14) usetimeculture   restdateculture
 *   그래서 이 파일은 **이름을 하나로 뭉개지 않는다.** 응답을 원문 그대로 남기고,
 *   어느 필드가 영업시간인지는 process/ 가 판단한다. collect/street-trees.mjs 와
 *   같은 태도다 — 파싱 규칙은 나중에 바꿀 수 있지만, 안 받은 데이터는 못 만든다.
 *
 * 🔴 요청주소를 지어내지 않는다
 *   config/sources.json 의 tourapi.endpoint 에서 읽는다. 2026-09-08 실측으로
 *   **버전 2 만 살아 있다** — KorService1/areaBasedList1 은 HTTP 400 이다.
 *
 * 호출 예산:
 *   부산 662곳(2026-09-08 실측) → 목록 ~10회 + 상세 662회 ≈ 670회.
 *   일일 한도 10,000 의 7% 다. 한도는 **오퍼레이션마다 따로** 걸리므로
 *   BIMS 실시간 버스 폴링(collect/bims-poll.mjs)을 갉아먹지 않는다.
 *
 * 준비:
 *   1. https://www.data.go.kr 로그인 → "한국관광공사 국문 관광정보" 활용신청
 *   2. bigData/.env 에  DATA_GO_KR_KEY=발급받은_디코딩키
 *
 * 실행:
 *   node collect/tourapi.mjs
 *   node collect/tourapi.mjs --dry-run    # 키·엔드포인트 설정만 검사. 호출 안 함
 *
 * 종료 코드:
 *   0  받아서 저장했다 (또는 --dry-run 설정 검사 통과)
 *   2  입력이 없다 — 키 없음 / 엔드포인트 없음 / API 가 키를 거부함
 *   1  받긴 받았는데 불변식이 깨졌다 (0건, totalCount 미달 등)
 */
import { mkdir, readFile, writeFile } from 'node:fs/promises'
import { existsSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { stamp } from '../mlops/manifest.mjs'

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..')
const OUT = join(ROOT, 'data/raw/tourapi')
const OUT_FILE = join(OUT, 'tourapi-busan.ndjson')

const DRY = process.argv.includes('--dry-run')

/**
 * 🔴 무한 페이지네이션과 폭주를 막는 안전장치. 실측 소요는 ~670회다.
 *    상한에 닿으면 성공으로 치지 않고 불변식에서 걸러진다.
 */
const MAX_CALLS = 2000
const ROWS_PER_PAGE = 100

/**
 * 받을 분류. TourAPI 의 contentTypeId 다.
 * 축제·행사(15)는 기간이 있는 것이라 "영업시간" 개념이 다르지만, 일정에 넣을
 * 후보이므로 같이 받는다. 어떻게 쓸지는 process/ 가 정한다.
 */
const CONTENT_TYPES = [
  [12, '관광지'],
  [14, '문화시설'],
  [15, '축제·행사'],
  [28, '레포츠'],
  [32, '숙박'],
  [38, '쇼핑'],
  [39, '음식점'],
]

const log = (...a) => console.log(new Date().toISOString().slice(0, 19), ...a)

/** 🔴 키를 절대 로그·산출물에 찍지 않는다. */
const redact = (s) => String(s).replace(/serviceKey=[^&\s]*/gi, 'serviceKey=<가림>')

async function loadEnv() {
  const p = join(ROOT, '.env')
  if (!existsSync(p)) return
  for (const line of (await readFile(p, 'utf8')).split('\n')) {
    const m = line.match(/^\s*([A-Z_][A-Z0-9_]*)\s*=\s*(.*)\s*$/)
    if (m && !process.env[m[1]]) process.env[m[1]] = m[2].replace(/^["']|["']$/g, '')
  }
}

/**
 * 엔드포인트를 지어내지 않는다 — 이미 팀이 적어 둔 곳에서 읽는다.
 * .env 의 TOURAPI_ENDPOINT 가 있으면 그쪽이 이긴다.
 */
async function resolveSource() {
  const p = join(ROOT, 'config/sources.json')
  if (!existsSync(p)) return { base: null, from: 'config/sources.json 없음' }
  const reg = JSON.parse(await readFile(p, 'utf8'))
  const src = (reg.sources || []).find((s) => s.id === 'tourapi')
  const base = process.env.TOURAPI_ENDPOINT || src?.endpoint || null
  const from = process.env.TOURAPI_ENDPOINT
    ? '.env TOURAPI_ENDPOINT'
    : base
      ? 'config/sources.json tourapi.endpoint'
      : 'config/sources.json 에 tourapi.endpoint 없음'
  return { base, from, areaCode: src?.areaCode ?? null }
}

/**
 * 공공데이터포털은 **오류도 HTTP 200 으로** 돌려준다. 그것을 성공으로 세지 않는다.
 * 반환: { kind: 'ok'|'denied'|'error', ... }
 */
function classify(text) {
  const t = text.trimStart()
  if (/SERVICE_KEY_IS_NOT_REGISTERED|SERVICE ACCESS DENIED|등록되지\s*않은|NOT_REGISTERED_SERVICE|APPLICATION_ERROR|LIMITED_NUMBER_OF_SERVICE_REQUESTS/i.test(t))
    return { kind: 'denied', msg: t.replace(/\s+/g, ' ').slice(0, 400) }
  if (t.startsWith('<') && /errMsg|OpenAPI_ServiceResponse|SERVICE ERROR|cmmMsgHeader/i.test(t))
    return { kind: 'error', msg: t.replace(/\s+/g, ' ').slice(0, 400) }
  if (t.startsWith('{')) {
    let j
    try { j = JSON.parse(t) } catch { return { kind: 'error', msg: 'JSON 파싱 실패: ' + t.slice(0, 200) } }
    const code = j?.response?.header?.resultCode
    if (code != null && String(code) !== '0000' && String(code) !== '00')
      return { kind: 'denied', msg: `resultCode=${code} ${j?.response?.header?.resultMsg ?? ''}` }
    return { kind: 'ok', json: j }
  }
  return { kind: 'error', msg: '예상 못 한 형식: ' + t.slice(0, 200) }
}

async function main() {
  await loadEnv()
  const key = process.env.DATA_GO_KR_KEY
  const { base, from, areaCode } = await resolveSource()

  log('한국관광공사 국문 관광정보 수집 (영업시간·휴무일)')
  log(`  엔드포인트 ${base ?? '(없음)'}   ← ${from}`)
  log(`  지역코드   ${areaCode ?? '(없음)'}`)
  log(`  저장       ${OUT_FILE}`)
  log(`  호출 상한  ${MAX_CALLS}회 (목록 쪽당 ${ROWS_PER_PAGE}건)`)

  if (!base) {
    log('🔴 요청주소를 못 정했습니다. 지어내지 않습니다.')
    log('   config/sources.json 의 tourapi.endpoint 를 채우거나 .env 에 TOURAPI_ENDPOINT 를 넣으십시오.')
    process.exit(2)
  }
  if (areaCode == null) {
    log('🔴 지역코드가 없습니다. config/sources.json 의 tourapi.areaCode 를 채우십시오.')
    process.exit(2)
  }
  if (!key) {
    log('🔴 DATA_GO_KR_KEY 가 없습니다. 수집하지 않았습니다.')
    log('   bigData/.env 에  DATA_GO_KR_KEY=발급받은_디코딩키')
    process.exit(2)
  }
  if (DRY) {
    log('--dry-run: 키와 엔드포인트 확인됨. 호출하지 않고 종료.')
    return
  }

  await mkdir(OUT, { recursive: true })

  const startedAt = new Date().toISOString()
  const lines = []
  let calls = 0

  /** 한 번 부르고 원문을 그대로 줄로 남긴다. 거부·오류면 여기서 멈춘다. */
  async function fetchRaw(op, params, meta) {
    if (calls >= MAX_CALLS) {
      log(`🔴 호출 상한 ${MAX_CALLS}회에 도달했습니다. 여기서 멈춥니다.`)
      return null
    }
    const u = new URL(`${base.replace(/\/$/, '')}/${op}`)
    u.searchParams.set('serviceKey', key)
    u.searchParams.set('MobileOS', 'ETC')
    u.searchParams.set('MobileApp', 'GABOLLE')
    u.searchParams.set('_type', 'json')
    for (const [k, v] of Object.entries(params)) u.searchParams.set(k, String(v))

    let text
    try {
      const res = await fetch(u, { signal: AbortSignal.timeout(30000) })
      text = await res.text()
      calls++
      if (!res.ok) { log(`🔴 HTTP ${res.status} — ${redact(text).slice(0, 300)}`); process.exit(2) }
    } catch (e) {
      log(`🔴 네트워크 실패 (${op} ${JSON.stringify(meta)}): ${redact(e.message)}`)
      process.exit(2)
    }

    const c = classify(text)
    if (c.kind === 'denied') {
      log('🔴 API 가 요청을 거부했습니다. 가짜 데이터를 만들지 않고 여기서 멈춥니다.')
      log(`   응답: ${redact(c.msg)}`)
      log('   활용신청 직후라면 키가 실제로 도는 데 최대 1시간쯤 걸립니다.')
      process.exit(2)
    }
    if (c.kind === 'error') { log(`🔴 API 오류 응답: ${redact(c.msg)}`); process.exit(2) }

    // 🔴 원문 그대로 남긴다. 아래 개수 세기는 진행 판단용일 뿐, 저장물은 원문이다.
    lines.push(JSON.stringify({ ts: Date.now(), op, ...meta, raw: text }))
    return c.json
  }

  // ── 1단계: 분류마다 목록을 받아 contentid 를 모은다 ──────────────────────
  const targets = []          // { contentid, contentTypeId }
  const perType = {}          // 종류별 실적 — 불변식과 로그에 쓴다

  for (const [typeId, typeName] of CONTENT_TYPES) {
    let totalCount = null
    let got = 0
    for (let page = 1; ; page++) {
      const j = await fetchRaw('areaBasedList2',
        { areaCode, contentTypeId: typeId, numOfRows: ROWS_PER_PAGE, pageNo: page },
        { stage: 'list', contentTypeId: typeId, page })
      if (!j) break
      const body = j?.response?.body ?? {}
      if (totalCount == null) totalCount = Number(body.totalCount)
      const item = body?.items?.item
      const arr = Array.isArray(item) ? item : item ? [item] : []
      for (const it of arr) if (it?.contentid) targets.push({ contentid: it.contentid, contentTypeId: typeId })
      got += arr.length
      if (arr.length === 0) break
      if (Number.isFinite(totalCount) && got >= totalCount) break
    }
    perType[typeId] = { name: typeName, listed: got, totalCount }
    log(`  목록 ${typeName}(${typeId}) ${got}${Number.isFinite(totalCount) ? ' / ' + totalCount : ''}곳`)
  }

  // ── 2단계: 곳마다 상세를 받는다 (여기에 영업시간·휴무일이 있다) ──────────
  let details = 0
  let empty = 0
  for (const [i, t] of targets.entries()) {
    const j = await fetchRaw('detailIntro2',
      { contentId: t.contentid, contentTypeId: t.contentTypeId },
      { stage: 'detail', contentid: t.contentid, contentTypeId: t.contentTypeId })
    if (!j) break
    const item = j?.response?.body?.items?.item
    const arr = Array.isArray(item) ? item : item ? [item] : []
    if (arr.length) details++; else empty++
    if ((i + 1) % 100 === 0) log(`  상세 ${i + 1}/${targets.length} (내용 있음 ${details} · 빈 것 ${empty})`)
  }

  await writeFile(OUT_FILE, lines.join('\n') + '\n')

  // 실행 지문. 🔴 data/staged/_run.json 을 덮지 않는다 — 하위 폴더에 찍는다.
  stamp(join(ROOT, 'data/staged/_tourapi-run'), {
    step: 'collect/tourapi',
    inputs: [join(ROOT, 'config/sources.json')],
    params: { endpointFrom: from, areaCode, rowsPerPage: ROWS_PER_PAGE, maxCalls: MAX_CALLS, startedAt },
    result: { calls, places: targets.length, details, empty, perType, out: 'data/raw/tourapi/tourapi-busan.ndjson' },
  })

  log(`저장 완료: ${targets.length}곳 / 상세 ${details}건(빈 것 ${empty}) / 호출 ${calls}회`)

  // ── 불변식 ────────────────────────────────────────────────────────────
  if (targets.length === 0) {
    log('🔴 0곳입니다. 빈 파일을 성공으로 치지 않습니다.')
    process.exit(1)
  }
  for (const [id, s] of Object.entries(perType)) {
    if (Number.isFinite(s.totalCount) && s.listed < s.totalCount) {
      log(`🔴 ${s.name}(${id}) totalCount ${s.totalCount} 중 ${s.listed}곳만 받았습니다. 부분 수집을 성공으로 치지 않습니다.`)
      process.exit(1)
    }
  }
  if (details + empty < targets.length) {
    log(`🔴 ${targets.length}곳 중 ${details + empty}곳만 상세를 받았습니다 (호출 상한에 걸렸을 수 있습니다).`)
    process.exit(1)
  }
  if (details === 0) {
    log('🔴 상세가 전부 비었습니다. 영업시간을 한 건도 못 받았다는 뜻입니다.')
    process.exit(1)
  }
}

main().catch((e) => { console.error('치명:', redact(e?.stack || e)); process.exit(1) })
