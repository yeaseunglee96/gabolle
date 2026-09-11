#!/usr/bin/env node
/**
 * 한국관광공사 무장애 여행 정보 수집기 — 문 앞에서 막히는 경우
 *
 * 왜 이걸 받나:
 *   지금 온톨로지의 휠체어 규칙 여섯 개는 전부 **길과 이동**에만 걸려 있다.
 *   그래서 경사도 계단도 다 통과한 뒤 **문 앞에서 막히는 경우를 말할 수 없다.**
 *   턱 하나, 없는 경사로 하나가 그 앞까지의 판정을 전부 무의미하게 만든다.
 *   bm:placeWheelchairAccessible 이 이 데이터를 기다리고 있었다.
 *
 *   활용신청은 2026-09-06 에 승인됐는데 **한 번도 호출하지 않은 채로 있었다.**
 *
 * 🔴 왜 collect/tourapi.mjs 와 파일을 나눴나
 *   config/sources.json 이 두 출처를 따로 둔 이유를 그대로 따른다 — 데이터셋이
 *   다르고 인증신청도 따로 났다. **한쪽이 막혀도 다른 쪽은 돌아야 한다.**
 *   한 파일에 합치면 관광정보가 거부당하는 날 무장애 정보도 같이 못 받는다.
 *
 * 🔴 요청주소를 지어내지 않는다
 *   config/sources.json 의 tourapi-barrier-free.endpoint 에서 읽는다.
 *   2026-09-08 실측으로 **버전 2 만 살아 있다** — KorWithService1 은 HTTP 400 이다.
 *
 * 무엇이 오나 (2026-09-08 실측, detailWithTour2 의 29개 필드):
 *   wheelchair · elevator · restroom · exit · parking · publictransport · route
 *   braileblock · helpdog · guidehuman · audioguide · bigprint · signguide
 *   videoguide · hearingroom · stroller · lactationroom · babysparechair …
 *   휠체어만 보는 것이 아니라 **시각·청각·유아동반까지** 함께 온다.
 *
 * 호출 예산:
 *   부산 182곳(2026-09-08 실측) → 목록 ~2회 + 상세 182회 ≈ 184회.
 *   한도는 오퍼레이션마다 따로 걸리므로 BIMS 버스 폴링을 갉아먹지 않는다.
 *
 * 준비:
 *   1. https://www.data.go.kr 로그인 → "한국관광공사_무장애 여행 정보" 활용신청
 *   2. bigData/.env 에  DATA_GO_KR_KEY=발급받은_디코딩키
 *
 * 실행:
 *   node collect/tourapi-barrier-free.mjs
 *   node collect/tourapi-barrier-free.mjs --dry-run
 *   node collect/tourapi-barrier-free.mjs --resume   # 앞서 받다 만 것을 잇는다
 *
 * 종료 코드:
 *   0  받아서 저장했다 (또는 --dry-run 설정 검사 통과)
 *   1  받긴 받았는데 불변식이 깨졌다 (0건, totalCount 미달 등)
 *   2  입력이 없다 — 키 없음 / 엔드포인트 없음 / API 가 키를 거부함
 *   3  일일 호출 한도를 다 썼다. 받은 데까지는 파일에 있고 --resume 으로 잇는다
 *
 * 🔴 collect/tourapi.mjs 와 같은 고침이다 (S15P21E201-331). 두 수집기가 **같은 키로
 *    같은 한도**를 쓰므로, 한쪽만 고쳐 두면 나머지 한쪽에서 똑같이 잃는다.
 */
import { appendFile, mkdir, readFile, writeFile } from 'node:fs/promises'
import { existsSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { stamp } from '../mlops/manifest.mjs'

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..')
const OUT = join(ROOT, 'data/raw/tourapi')
const OUT_FILE = join(OUT, 'tourapi-barrier-free-busan.ndjson')

const DRY = process.argv.includes('--dry-run')

/** 🔴 앞서 받다 만 것이 있으면 그것을 쓰고 안 받은 것만 받는다. 기본값이 아니다. */
const RESUME = process.argv.includes('--resume')

/** 종료 코드. 🔴 3 은 "자정까지 무엇을 해도 안 된다" 는 뜻이라 2 와 가른다. */
const EXIT = { OK: 0, INVARIANT: 1, INPUT: 2, QUOTA: 3 }

/**
 * 멈출 이유. 🔴 **요청이 떠 있는 채로 `process.exit()` 를 부르지 않는다.**
 * Windows 의 Node 가 죽고(`UV_HANDLE_CLOSING`) 종료 코드가 127 이 된다 —
 * 그러면 애써 갈라 놓은 3·2 가 무의미해진다. 던져서 빠져나온 뒤 exitCode 만 적는다.
 */
class Halt extends Error {
  constructor(code, lines = []) { super('halt'); this.code = code; this.lines = lines }
}
class QuotaHalt extends Halt { constructor() { super(EXIT.QUOTA) } }

/** 일일 한도는 HTTP 429 로도, HTTP 200 본문으로만도 온다. 코드 22 는 규격 값이다. */
const isQuotaExceeded = (text) =>
  /LIMITED_NUMBER_OF_SERVICE_REQUESTS/i.test(text) ||
  /"returnReasonCode"\s*:\s*"?22"?/.test(text)

/** 🔴 무한 페이지네이션과 폭주를 막는 안전장치. 실측 소요는 ~184회다. */
const MAX_CALLS = 600
const ROWS_PER_PAGE = 100

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

/** 엔드포인트를 지어내지 않는다 — 이미 팀이 적어 둔 곳에서 읽는다. */
async function resolveSource() {
  const p = join(ROOT, 'config/sources.json')
  if (!existsSync(p)) return { base: null, from: 'config/sources.json 없음' }
  const reg = JSON.parse(await readFile(p, 'utf8'))
  const src = (reg.sources || []).find((s) => s.id === 'tourapi-barrier-free')
  const base = process.env.TOURAPI_BF_ENDPOINT || src?.endpoint || null
  const from = process.env.TOURAPI_BF_ENDPOINT
    ? '.env TOURAPI_BF_ENDPOINT'
    : base
      ? 'config/sources.json tourapi-barrier-free.endpoint'
      : 'config/sources.json 에 tourapi-barrier-free.endpoint 없음'
  return { base, from, areaCode: src?.areaCode ?? null }
}

/** 공공데이터포털은 **오류도 HTTP 200 으로** 돌려준다. 그것을 성공으로 세지 않는다. */
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

  log('한국관광공사 무장애 여행 정보 수집')
  log(`  엔드포인트 ${base ?? '(없음)'}   ← ${from}`)
  log(`  지역코드   ${areaCode ?? '(없음)'}`)
  log(`  저장       ${OUT_FILE}`)
  log(`  호출 상한  ${MAX_CALLS}회 (목록 쪽당 ${ROWS_PER_PAGE}건)`)

  if (!base) {
    log('🔴 요청주소를 못 정했습니다. 지어내지 않습니다.')
    log('   config/sources.json 의 tourapi-barrier-free.endpoint 를 채우거나 .env 에 TOURAPI_BF_ENDPOINT 를 넣으십시오.')
    process.exit(EXIT.INPUT)
  }
  if (areaCode == null) {
    log('🔴 지역코드가 없습니다. config/sources.json 의 tourapi-barrier-free.areaCode 를 채우십시오.')
    process.exit(EXIT.INPUT)
  }
  if (!key) {
    log('🔴 DATA_GO_KR_KEY 가 없습니다. 수집하지 않았습니다.')
    log('   bigData/.env 에  DATA_GO_KR_KEY=발급받은_디코딩키')
    process.exit(EXIT.INPUT)
  }
  if (DRY) {
    log('--dry-run: 키와 엔드포인트 확인됨. 호출하지 않고 종료.')
    return
  }

  await mkdir(OUT, { recursive: true })

  const startedAt = new Date().toISOString()
  let calls = 0
  let saved = 0

  /** 앞서 받아 둔 응답. 열쇠는 "무엇을 물었나" 다. 있으면 네트워크를 안 탄다. */
  const cache = new Map()
  const keyOf = (meta) => (meta.stage === 'list' ? `list:${meta.page}` : `detail:${meta.contentid}`)

  if (RESUME && existsSync(OUT_FILE)) {
    for (const line of (await readFile(OUT_FILE, 'utf8')).split('\n')) {
      if (!line.trim()) continue
      try { const o = JSON.parse(line); cache.set(keyOf(o), o.raw) } catch { /* 깨진 줄은 버린다 */ }
    }
    saved = cache.size
    log(`  이어받기: 앞서 받아 둔 응답 ${saved}건을 씁니다 (그만큼 안 부릅니다)`)
  } else {
    await writeFile(OUT_FILE, '')   // 🔴 덧붙이기 전에 비운다. 안 그러면 지난 줄이 섞인다
  }

  /** 🔴 모아 뒀다 끝에 한 번 저장하지 않는다. 받는 족족 파일에 덧붙인다. */
  async function fetchRaw(op, params, meta) {
    const ck = keyOf(meta)
    if (cache.has(ck)) return classify(cache.get(ck)).json
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
      if (res.status === 429 || isQuotaExceeded(text)) throw new QuotaHalt()
      if (!res.ok) throw new Halt(EXIT.INPUT, [`🔴 HTTP ${res.status} — ${redact(text).slice(0, 300)}`])
    } catch (e) {
      if (e instanceof Halt) throw e
      throw new Halt(EXIT.INPUT, [`🔴 네트워크 실패 (${op} ${JSON.stringify(meta)}): ${redact(e.message)}`])
    }

    const c = classify(text)
    if (c.kind === 'denied') {
      throw new Halt(EXIT.INPUT, [
        '🔴 API 가 요청을 거부했습니다. 가짜 데이터를 만들지 않고 여기서 멈춥니다.',
        `   응답: ${redact(c.msg)}`,
      ])
    }
    if (c.kind === 'error') throw new Halt(EXIT.INPUT, [`🔴 API 오류 응답: ${redact(c.msg)}`])

    // 🔴 원문 그대로, 그 자리에서 파일에 덧붙인다.
    await appendFile(OUT_FILE, JSON.stringify({ ts: Date.now(), op, ...meta, raw: text }) + '\n')
    cache.set(ck, text)
    saved++
    return c.json
  }

  const targets = []
  let totalCount = null
  let details = 0
  let empty = 0

  /** 한도에 걸렸을 때 — 받은 데까지 지문을 남기고, 이어받는 법을 알려 주고 끝낸다. */
  const haltOnQuota = () => {
    log('')
    log('🔴 일일 호출 한도를 다 썼습니다. 오늘은 더 못 받습니다.')
    log(`   받아 둔 응답 ${saved}건은 파일에 남아 있습니다 — ${OUT_FILE}`)
    log('   자정이 지난 뒤 아래로 이어받으십시오. 이미 받은 것은 다시 안 부릅니다.')
    log('     node collect/tourapi-barrier-free.mjs --resume')
    stamp(join(ROOT, 'data/staged/_tourapi-bf-run'), {
      step: 'collect/tourapi-barrier-free',
      inputs: [join(ROOT, 'config/sources.json')],
      params: { endpointFrom: from, areaCode, rowsPerPage: ROWS_PER_PAGE, maxCalls: MAX_CALLS, startedAt, resume: RESUME },
      result: { halted: 'quota', calls, saved, places: targets.length, details, empty, totalCount,
                out: 'data/raw/tourapi/tourapi-barrier-free-busan.ndjson' },
    })
    process.exitCode = EXIT.QUOTA   // 🔴 exit() 가 아니다. Halt 주석 참고
  }

  try {
  // ── 1단계: 부산의 무장애 정보가 있는 곳 목록 ────────────────────────────
  for (let page = 1; ; page++) {
    const j = await fetchRaw('areaBasedList2',
      { areaCode, numOfRows: ROWS_PER_PAGE, pageNo: page },
      { stage: 'list', page })
    if (!j) break
    const body = j?.response?.body ?? {}
    if (totalCount == null) totalCount = Number(body.totalCount)
    const item = body?.items?.item
    const arr = Array.isArray(item) ? item : item ? [item] : []
    for (const it of arr) if (it?.contentid) targets.push(it.contentid)
    log(`  목록 ${page}쪽 ${arr.length}곳 (누적 ${targets.length}${Number.isFinite(totalCount) ? ' / ' + totalCount : ''})`)
    if (arr.length === 0) break
    if (Number.isFinite(totalCount) && targets.length >= totalCount) break
  }

  // ── 2단계: 곳마다 무장애 상세 (휠체어·엘리베이터·점자블록 …) ─────────────
  for (const [i, contentid] of targets.entries()) {
    const j = await fetchRaw('detailWithTour2', { contentId: contentid }, { stage: 'detail', contentid })
    if (!j) break
    const item = j?.response?.body?.items?.item
    const arr = Array.isArray(item) ? item : item ? [item] : []
    if (arr.length) details++; else empty++
    if ((i + 1) % 50 === 0) log(`  상세 ${i + 1}/${targets.length} (내용 있음 ${details} · 빈 것 ${empty})`)
  }
  } catch (e) {
    if (e instanceof QuotaHalt) { haltOnQuota(); return }
    if (e instanceof Halt) { for (const l of e.lines) log(l); process.exitCode = e.code; return }
    throw e
  }

  // 🔴 여기에 저장하는 줄이 없는 것이 정상이다. 응답은 받는 족족 이미 파일에 있다.

  stamp(join(ROOT, 'data/staged/_tourapi-bf-run'), {
    step: 'collect/tourapi-barrier-free',
    inputs: [join(ROOT, 'config/sources.json')],
    params: { endpointFrom: from, areaCode, rowsPerPage: ROWS_PER_PAGE, maxCalls: MAX_CALLS, startedAt, resume: RESUME },
    result: { calls, saved, places: targets.length, details, empty, totalCount, out: 'data/raw/tourapi/tourapi-barrier-free-busan.ndjson' },
  })

  log(`저장 완료: ${targets.length}곳 / 상세 ${details}건(빈 것 ${empty}) / 새로 부른 호출 ${calls}회 / 파일에 ${saved}줄`)

  // ── 불변식 ────────────────────────────────────────────────────────────
  if (targets.length === 0) {
    log('🔴 0곳입니다. 빈 파일을 성공으로 치지 않습니다.')
    process.exit(EXIT.INVARIANT)
  }
  if (Number.isFinite(totalCount) && targets.length < totalCount) {
    log(`🔴 totalCount ${totalCount} 중 ${targets.length}곳만 받았습니다. 부분 수집을 성공으로 치지 않습니다.`)
    process.exit(EXIT.INVARIANT)
  }
  if (details + empty < targets.length) {
    log(`🔴 ${targets.length}곳 중 ${details + empty}곳만 상세를 받았습니다 (호출 상한에 걸렸을 수 있습니다).`)
    process.exit(EXIT.INVARIANT)
  }
  if (details === 0) {
    log('🔴 상세가 전부 비었습니다. 무장애 정보를 한 건도 못 받았다는 뜻입니다.')
    process.exit(EXIT.INVARIANT)
  }
}

main().catch((e) => { console.error('치명:', redact(e?.stack || e)); process.exit(EXIT.INVARIANT) })
