#!/usr/bin/env node
/**
 * 한국관광공사 영문 관광정보 (TourAPI EngService2) 수집기
 *
 * 왜 이걸 받나:
 *   지금 앱의 "한영 번역"은 UI 문구(tx() 헬퍼)만 영어로 바뀌고, 장소 이름·설명 같은
 *   실제 콘텐츠는 국문 그대로 나간다 — 국문 TourAPI(collect/tourapi.mjs)만 받아서다.
 *   VisitKorea 영문 포털의 실제 번역문을 그대로 받으면(기계 번역을 새로 만들지 않고)
 *   외국인 사용자에게 장소 이름·소개를 영어로 보여줄 수 있다.
 *
 * 🔴 국문 수집기와 다른 점 하나 — 영업시간 필드는 안 받는다
 *   국문 수집기(tourapi.mjs)의 목적은 영업시간·휴무일이었다(온톨로지 bm:openingHoursNormalized).
 *   이 수집기의 목적은 번역문(이름·주소·소개)이지 영업시간이 아니다. detailIntro2 응답에
 *   영업시간류 필드가 있어도 여기서는 안 쓴다 — 국문 쪽 값을 신뢰하고, 영문 쪽은 텍스트만 본다.
 *
 * 🔴 contentid 는 국문·영문 서비스가 같은 값을 공유한다 — TourAPI 4.0 이 언어 서비스를
 *   같은 콘텐츠 ID 공간 위에 다국어로 얹은 구조이기 때문이다(공식 문서 기준). 그래서 이
 *   수집기가 받은 contentid 를 국문 수집기의 contentid 와 그대로 조인할 수 있다 — process/
 *   가 할 일이다. 🔴 다만 이건 문서상의 사실이고 실측은 아직이다 — 처음 돌릴 때
 *   tourapi-busan.ndjson 의 contentid 집합과 겹치는지 반드시 확인한다(아래 실행 뒤 체크).
 *
 * 🔴 요청주소를 지어내지 않는다
 *   config/sources.json 의 tourapi-en.endpoint 에서 읽는다. Base URL 은 data.go.kr의
 *   "한국관광공사_영문 관광정보서비스_GW" 페이지(Swagger Explore)에서 그대로 옮겼다:
 *   https://apis.data.go.kr/B551011/EngService2 — 오퍼레이션 이름(areaBasedList2·
 *   detailIntro2)까지 국문 서비스와 동일하다(2026-09-11 문서 확인, 호출은 안 해봄).
 *
 * 준비:
 *   1. https://www.data.go.kr/data/15101753/openapi.do 활용신청 — 무료, 개발단계 자동승인
 *      (이미 다른 TourAPI 상품에 쓰고 있는 DATA_GO_KR_KEY 계정으로 신청하면 된다 —
 *      계정은 하나, 상품별 활용신청만 따로 필요하다)
 *   2. bigData/.env 의 DATA_GO_KR_KEY 는 그대로 재사용한다 — 새 키 발급 불필요
 *
 * 실행:
 *   node collect/tourapi-en.mjs
 *   node collect/tourapi-en.mjs --dry-run    # 키·엔드포인트 설정만 검사. 호출 안 함
 *
 * 종료 코드:
 *   0  받아서 저장했다 (또는 --dry-run 설정 검사 통과)
 *   2  입력이 없다 — 키 없음 / 엔드포인트 없음 / API 가 키를 거부함 (활용신청 전이면 이거다)
 *   1  받긴 받았는데 불변식이 깨졌다 (0건, totalCount 미달 등)
 */
import { mkdir, readFile, writeFile } from 'node:fs/promises'
import { existsSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { stamp } from '../mlops/manifest.mjs'

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..')
const OUT = join(ROOT, 'data/raw/tourapi')
const OUT_FILE = join(OUT, 'tourapi-busan-en.ndjson')

const DRY = process.argv.includes('--dry-run')

/** 국문 수집기와 같은 안전장치 — collect/tourapi.mjs 주석 참고. */
const MAX_CALLS = 2000
const ROWS_PER_PAGE = 100

/** 국문 수집기와 같은 분류 — contentid 조인을 맞추려면 같은 종류를 받아야 한다. */
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

/** 엔드포인트를 지어내지 않는다 — config/sources.json 에서 읽는다. */
async function resolveSource() {
  const p = join(ROOT, 'config/sources.json')
  if (!existsSync(p)) return { base: null, from: 'config/sources.json 없음' }
  const reg = JSON.parse(await readFile(p, 'utf8'))
  const src = (reg.sources || []).find((s) => s.id === 'tourapi-en')
  const base = process.env.TOURAPI_EN_ENDPOINT || src?.endpoint || null
  const from = process.env.TOURAPI_EN_ENDPOINT
    ? '.env TOURAPI_EN_ENDPOINT'
    : base
      ? 'config/sources.json tourapi-en.endpoint'
      : 'config/sources.json 에 tourapi-en.endpoint 없음'
  return { base, from, areaCode: src?.areaCode ?? null }
}

/** 공공데이터포털은 오류도 HTTP 200 으로 돌려준다 — 국문 수집기와 같은 판정. */
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

  log('한국관광공사 영문 관광정보 수집 (이름·소개 번역문)')
  log(`  엔드포인트 ${base ?? '(없음)'}   ← ${from}`)
  log(`  지역코드   ${areaCode ?? '(없음)'}`)
  log(`  저장       ${OUT_FILE}`)
  log(`  호출 상한  ${MAX_CALLS}회 (목록 쪽당 ${ROWS_PER_PAGE}건)`)

  if (!base) {
    log('🔴 요청주소를 못 정했습니다. 지어내지 않습니다.')
    log('   config/sources.json 의 tourapi-en.endpoint 를 채우거나 .env 에 TOURAPI_EN_ENDPOINT 를 넣으십시오.')
    process.exit(2)
  }
  if (areaCode == null) {
    log('🔴 지역코드가 없습니다. config/sources.json 의 tourapi-en.areaCode 를 채우십시오.')
    process.exit(2)
  }
  if (!key) {
    log('🔴 DATA_GO_KR_KEY 가 없습니다. 수집하지 않았습니다.')
    log('   bigData/.env 에  DATA_GO_KR_KEY=발급받은_디코딩키  (국문 TourAPI 와 같은 키)')
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
      log('   활용신청 직후라면(자동승인이라도) 키가 실제로 도는 데 최대 1시간쯤 걸립니다.')
      process.exit(2)
    }
    if (c.kind === 'error') { log(`🔴 API 오류 응답: ${redact(c.msg)}`); process.exit(2) }

    lines.push(JSON.stringify({ ts: Date.now(), op, ...meta, raw: text }))
    return c.json
  }

  // ── 1단계: 분류마다 목록을 받아 contentid 를 모은다 ──────────────────────
  const targets = []
  const perType = {}

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

  // ── 2단계: 곳마다 상세(소개문)를 받는다 ──────────────────────────────────
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

  stamp(join(ROOT, 'data/staged/_tourapi-en-run'), {
    step: 'collect/tourapi-en',
    inputs: [join(ROOT, 'config/sources.json')],
    params: { endpointFrom: from, areaCode, rowsPerPage: ROWS_PER_PAGE, maxCalls: MAX_CALLS, startedAt },
    result: { calls, places: targets.length, details, empty, perType, out: 'data/raw/tourapi/tourapi-busan-en.ndjson' },
  })

  log(`저장 완료: ${targets.length}곳 / 상세 ${details}건(빈 것 ${empty}) / 호출 ${calls}회`)
  log('🔴 다음 확인은 사람이: tourapi-busan.ndjson 의 contentid 집합과 이 파일의 contentid 집합이')
  log('   실제로 겹치는지 보십시오. 안 겹치면 두 서비스가 다른 ID 공간을 쓴다는 뜻이라 이 파일의')
  log('   주석(contentid 공유)이 틀린 것이고, 이름·좌표 기반 매칭으로 다시 설계해야 합니다.')

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
    log('🔴 상세가 전부 비었습니다. 번역문을 한 건도 못 받았다는 뜻입니다.')
    process.exit(1)
  }
}

main().catch((e) => { console.error('치명:', redact(e?.stack || e)); process.exit(1) })
