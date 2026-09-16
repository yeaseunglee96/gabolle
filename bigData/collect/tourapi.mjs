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
 *   node collect/tourapi.mjs --resume     # 앞서 받다 만 것을 잇는다 (안 받은 것만 부른다)
 *
 * 종료 코드:
 *   0  받아서 저장했다 (또는 --dry-run 설정 검사 통과)
 *   1  받긴 받았는데 불변식이 깨졌다 (0건, totalCount 미달 등)
 *   2  입력이 없다 — 키 없음 / 엔드포인트 없음 / API 가 키를 거부함
 *   3  일일 호출 한도를 다 썼다. 받은 데까지는 파일에 있고 --resume 으로 잇는다
 *
 * 🔴 3 을 2 와 가르는 이유: **지금 다시 걸어 보는 것이 소용 있는지**가 다르다.
 *    2 는 사람이 무언가 고쳐야 하고, 3 은 자정까지 무엇을 해도 안 된다.
 *    뭉쳐 두면 파이프라인이 한도 초과를 붙잡고 재시도를 돌린다.
 */
import { appendFile, mkdir, readFile, writeFile } from 'node:fs/promises'
import { existsSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { stamp } from '../mlops/manifest.mjs'

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..')
const OUT = join(ROOT, 'data/raw/tourapi')
const OUT_FILE = join(OUT, 'tourapi-busan.ndjson')

const DRY = process.argv.includes('--dry-run')

/**
 * 🔴 앞서 받다 만 것이 있으면 그것을 쓰고 안 받은 것만 받는다.
 *
 * 기본값이 아닌 이유는 **놀라지 않게** 하기 위해서다. 그냥 돌리면 늘 처음부터다.
 * 한도에 걸려 멈추면 끝 줄이 이 깃발을 붙인 명령을 그대로 알려 준다.
 */
const RESUME = process.argv.includes('--resume')

/**
 * 🔴 목록까지만 받고 상세는 건너뛴다 — S15P21E201-1031
 *
 *   지역 필터를 lDongRegnCd 로 고치면 대상이 639곳에서 2,218곳으로 는다. 그런데
 *   상세(detailIntro2)는 **곳마다 한 번씩** 부르므로 2,218회가 필요하고, 일일
 *   한도는 오퍼레이션당 1,000회다. 하루에 못 끝낸다.
 *
 *   그래서 첫날은 이 깃발로 목록만 받아 **몇 곳인지부터 확정**하고, 다음 날
 *   --resume 으로 상세를 잇는다. 목록 응답은 이미 파일에 있으므로 다시 안 부른다.
 *
 *   🔴 이 깃발로 받은 파일에는 **영업시간이 없다.** "2,218곳이 들어왔다" 를
 *      "2,218곳의 상세가 있다" 로 읽으면 안 된다. 끝 줄이 그것을 찍는다.
 *
 *   🔴 --resume 없이 돌리면 앞서 받아 둔 파일을 **비우고 처음부터** 받는다
 *      (아래 writeFile(OUT_FILE, '') 자리). data/raw/tourapi/tourapi-busan.ndjson 은
 *      **커밋돼 있는 파일**이라, 이 깃발로 돌린 결과를 그대로 커밋하면 2026-09-11 에
 *      받아 둔 상세 수집본이 목록만 든 파일로 바뀐다. 2026-09-16 에 실제로 667줄이
 *      26줄이 됐다 — 되돌렸다. 이 깃발은 **숫자를 재는 데만** 쓰고 수집본은 커밋하지 마라.
 */
const LIST_ONLY = process.argv.includes('--list-only')

/**
 * 종료 코드. 🔴 **"다시 하면 되나" 를 숫자로 가른다.**
 *
 * 예전에는 일일 한도 초과가 다른 HTTP 오류와 같은 2 였다. 그러면 사람도
 * 파이프라인도 **지금 다시 걸어 보는 것이 소용 있는지**를 알 수 없다 —
 * 일일 한도는 자정까지 무엇을 해도 안 되고, 순간 장애는 1분 뒤면 된다.
 */
const EXIT = {
  OK: 0,
  /** 받긴 받았는데 불변식이 깨졌다 */
  INVARIANT: 1,
  /** 입력이 없다 — 키 없음 · 엔드포인트 없음 · 키 거부 · 그 밖의 오류 */
  INPUT: 2,
  /** 🔴 일일 호출 한도. 받은 데까지는 파일에 남아 있고, 내일 --resume 으로 잇는다 */
  QUOTA: 3,
}

/**
 * 멈출 이유. `fetchRaw` 가 던지고 `main` 이 받아 끝낸다.
 *
 * 🔴 **여기서 `process.exit()` 를 부르지 않는다 (2026-09-11 실측).**
 *    요청이 아직 떠 있는 채로 `process.exit()` 를 부르면 Windows 의 Node 가
 *    죽는다 — `Assertion failed: !(handle->flags & UV_HANDLE_CLOSING)` 이 뜨고
 *    **종료 코드가 127** 이 된다. 그러면 애써 갈라 놓은 3(한도)·2(입력)이
 *    통째로 무의미해진다. 실제로 이 고침을 시험하다 걸렸다.
 *
 *    그래서 던져서 빠져나온 뒤 `process.exitCode` 만 적어 두고 **자연스럽게**
 *    끝나게 한다. Node 는 할 일이 없어지면 그 코드로 나간다.
 */
class Halt extends Error {
  constructor(code, lines = []) {
    super('halt')
    this.code = code
    this.lines = lines
  }
}

/** 일일 호출 한도. 받은 데까지는 이미 파일에 있다. */
class QuotaHalt extends Halt {
  constructor() { super(EXIT.QUOTA) }
}

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

/**
 * 일일 호출 한도를 다 썼나.
 *
 * 🔴 **이름과 코드를 둘 다 본다.** data.go.kr 은 같은 사정을 두 모양으로 알린다 —
 *    HTTP 429 로 줄 때도 있고, HTTP 200 에 본문으로만 줄 때도 있다. 그리고
 *    문구는 바뀔 수 있어도 `returnReasonCode` 22 는 규격에 박힌 값이다.
 *    2026-09-11 실측 응답: `LIMITED_NUMBER_OF_SERVICE_REQUESTS_EXCEEDS_ERROR` · 코드 22.
 */
const isQuotaExceeded = (text) =>
  /LIMITED_NUMBER_OF_SERVICE_REQUESTS/i.test(text) ||
  /"returnReasonCode"\s*:\s*"?22"?/.test(text)

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
/**
 * 🔴 지역을 areaCode 가 아니라 lDongRegnCd 로 거른다 — S15P21E201-1031
 *
 *   관광공사 응답에는 지역 칸이 둘 있고 하나가 거의 비어 있다.
 *     areacode      관광용 지역코드 (부산 = 6)   ← 대부분 빈 문자열로 온다
 *     lDongRegnCd   법정동 시도코드 (부산 = 26)  ← 전부 차 있다
 *
 *   2026-09-16 실측 (areaBasedList2, contentTypeId 7종 전수):
 *     areaCode=6 으로 거르면        639곳
 *     lDongRegnCd=26 으로 거르면  2,218곳   ← 1,579곳을 놓치고 있었다
 *     관광지 135→351 · 문화시설 32→120 · 쇼핑 47→980 · 음식점 319→515
 *
 *   감천문화마을이 검색에 없던 것도 이것 때문이다 — areacode 와 cat1 이 둘 다
 *   빈 채로 등록돼 있어 지역 필터에서 통째로 빠졌다 (S15P21E201-920).
 *
 *   🔴 areaCode 로 되돌리지 마라. 되돌리면 부산 장소의 71% 가 조용히 사라지고
 *      아무 오류도 안 난다.
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
  return { base, from, regnCd: src?.lDongRegnCd ?? null }
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
  const { base, from, regnCd } = await resolveSource()

  log('한국관광공사 국문 관광정보 수집 (영업시간·휴무일)')
  log(`  엔드포인트 ${base ?? '(없음)'}   ← ${from}`)
  log(`  지역       lDongRegnCd=${regnCd ?? '(없음)'}   🔴 areaCode 가 아니다`)
  log(`  저장       ${OUT_FILE}`)
  log(`  호출 상한  ${MAX_CALLS}회 (목록 쪽당 ${ROWS_PER_PAGE}건)`)

  if (!base) {
    log('🔴 요청주소를 못 정했습니다. 지어내지 않습니다.')
    log('   config/sources.json 의 tourapi.endpoint 를 채우거나 .env 에 TOURAPI_ENDPOINT 를 넣으십시오.')
    process.exit(EXIT.INPUT)
  }
  if (regnCd == null) {
    log('🔴 법정동 시도코드가 없습니다. config/sources.json 의 tourapi.lDongRegnCd 를 채우십시오.')
    log('   🔴 areaCode 로 바꿔 끼우지 마십시오 — 부산 장소의 71% 가 조용히 빠집니다 (2026-09-16 실측).')
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

  /**
   * 🔴 앞서 받아 둔 응답. 키는 "무엇을 물었나" 다.
   *
   * 여기 있으면 **네트워크를 안 부른다.** 일일 한도가 있는 API 라서, 이미 산 것을
   * 다시 사는 것이 가장 비싼 낭비다. 2026-09-11 에 상세 330건을 받아 놓고 한도에
   * 걸렸는데, 그 330건이 전부 사라져 다음 날 같은 값을 다시 치러야 했다.
   */
  const cache = new Map()
  const keyOf = (meta) =>
    meta.stage === 'list' ? `list:${meta.contentTypeId}:${meta.page}` : `detail:${meta.contentid}`

  if (RESUME && existsSync(OUT_FILE)) {
    for (const line of (await readFile(OUT_FILE, 'utf8')).split('\n')) {
      if (!line.trim()) continue
      try {
        const o = JSON.parse(line)
        cache.set(keyOf(o), o.raw)
      } catch {
        // 🔴 깨진 줄은 조용히 버린다 — 한 줄이 깨졌다고 나머지를 버리면
        //    이어받기의 뜻이 없어진다. 이 파일은 한 줄이 곧 한 응답이다.
      }
    }
    saved = cache.size
    log(`  이어받기: 앞서 받아 둔 응답 ${saved}건을 씁니다 (그만큼 안 부릅니다)`)
  } else {
    // 🔴 처음부터라면 파일을 **먼저 비운다.** 아래에서 한 줄씩 덧붙이므로,
    //    안 비우면 지난 실행의 줄이 섞여 개수가 거짓말을 한다.
    await writeFile(OUT_FILE, '')
  }

  /**
   * 한 번 부르고 **원문을 그 자리에서 파일에 덧붙인다.** 거부·오류면 멈춘다.
   *
   * 🔴 **모아 뒀다 끝에 한 번 저장하지 않는다.** 예전에는 `lines` 배열에 쌓아
   *    마지막에 `writeFile` 을 한 번 했다. 그래서 중간에 멈추면 **받은 것이
   *    한 건도 안 남았다** — 2026-09-11 에 상세 330건이 그렇게 사라졌다.
   *    NDJSON 은 한 줄이 한 응답이라 덧붙이기에 안전하다.
   */
  async function fetchRaw(op, params, meta) {
    const ck = keyOf(meta)
    if (cache.has(ck)) return classify(cache.get(ck)).json   // 이미 있다 — 안 부른다

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
      // 🔴 일일 한도를 다른 오류와 갈라 낸다. HTTP 429 로도 오고, HTTP 200 에
      //    본문으로만 오기도 한다 — 그래서 두 쪽을 다 본다.
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
        '   활용신청 직후라면 키가 실제로 도는 데 최대 1시간쯤 걸립니다.',
      ])
    }
    if (c.kind === 'error') throw new Halt(EXIT.INPUT, [`🔴 API 오류 응답: ${redact(c.msg)}`])

    // 🔴 원문 그대로, **그 자리에서** 파일에 덧붙인다. 아래 개수 세기는 진행
    //    판단용일 뿐이고 저장물은 언제나 원문이다.
    await appendFile(OUT_FILE, JSON.stringify({ ts: Date.now(), op, ...meta, raw: text }) + '\n')
    cache.set(ck, text)
    saved++
    return c.json
  }

  const targets = []          // { contentid, contentTypeId }
  const perType = {}          // 종류별 실적 — 불변식과 로그에 쓴다
  let details = 0
  let empty = 0

  /** 한도에 걸렸을 때 — 받은 데까지 지문을 남기고, 이어받는 법을 알려 주고 끝낸다. */
  const haltOnQuota = () => {
    log('')
    log('🔴 일일 호출 한도를 다 썼습니다. 오늘은 더 못 받습니다.')
    log(`   받아 둔 응답 ${saved}건은 파일에 남아 있습니다 — ${OUT_FILE}`)
    log('   자정이 지난 뒤 아래로 이어받으십시오. 이미 받은 것은 다시 안 부릅니다.')
    log('     node collect/tourapi.mjs --resume')
    stamp(join(ROOT, 'data/staged/_tourapi-run'), {
      step: 'collect/tourapi',
      inputs: [join(ROOT, 'config/sources.json')],
      params: { endpointFrom: from, lDongRegnCd: regnCd, rowsPerPage: ROWS_PER_PAGE, maxCalls: MAX_CALLS, startedAt, resume: RESUME },
      result: { halted: 'quota', calls, saved, places: targets.length, details, empty, perType,
                out: 'data/raw/tourapi/tourapi-busan.ndjson' },
    })
    process.exitCode = EXIT.QUOTA   // 🔴 exit() 가 아니다. 위 주석(Halt) 참고
  }

  try {
  // ── 1단계: 분류마다 목록을 받아 contentid 를 모은다 ──────────────────────
  for (const [typeId, typeName] of CONTENT_TYPES) {
    let totalCount = null
    let got = 0
    for (let page = 1; ; page++) {
      const j = await fetchRaw('areaBasedList2',
        { lDongRegnCd: regnCd, contentTypeId: typeId, numOfRows: ROWS_PER_PAGE, pageNo: page },
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
  if (LIST_ONLY) {
    log(`  --list-only: 상세를 건너뜁니다. ${targets.length}곳의 목록만 받았습니다.`)
  }
  for (const [i, t] of (LIST_ONLY ? [] : targets).entries()) {
    const j = await fetchRaw('detailIntro2',
      { contentId: t.contentid, contentTypeId: t.contentTypeId },
      { stage: 'detail', contentid: t.contentid, contentTypeId: t.contentTypeId })
    if (!j) break
    const item = j?.response?.body?.items?.item
    const arr = Array.isArray(item) ? item : item ? [item] : []
    if (arr.length) details++; else empty++
    if ((i + 1) % 100 === 0) log(`  상세 ${i + 1}/${targets.length} (내용 있음 ${details} · 빈 것 ${empty})`)
  }
  } catch (e) {
    if (e instanceof QuotaHalt) { haltOnQuota(); return }
    if (e instanceof Halt) { for (const l of e.lines) log(l); process.exitCode = e.code; return }
    throw e
  }

  // 🔴 여기에 저장하는 줄이 없는 것이 정상이다. 응답은 받는 족족 이미 파일에 있다.

  // 실행 지문. 🔴 data/staged/_run.json 을 덮지 않는다 — 하위 폴더에 찍는다.
  stamp(join(ROOT, 'data/staged/_tourapi-run'), {
    step: 'collect/tourapi',
    inputs: [join(ROOT, 'config/sources.json')],
    params: { endpointFrom: from, lDongRegnCd: regnCd, rowsPerPage: ROWS_PER_PAGE, maxCalls: MAX_CALLS, startedAt, resume: RESUME },
    result: { calls, saved, places: targets.length, details, empty, perType, out: 'data/raw/tourapi/tourapi-busan.ndjson' },
  })

  if (LIST_ONLY) {
    log('')
    log(`🔴 목록만 받았습니다 — ${targets.length}곳. **상세(영업시간·휴무일)는 아직 옛 것입니다.**`)
    log('   "이 숫자만큼 상세가 있다" 는 뜻이 아닙니다. 다음으로 이으십시오:')
    log('     node collect/tourapi.mjs --resume')
  }
  log(`저장 완료: ${targets.length}곳 / 상세 ${details}건(빈 것 ${empty}) / 새로 부른 호출 ${calls}회 / 파일에 ${saved}줄`)

  // ── 불변식 ────────────────────────────────────────────────────────────
  if (targets.length === 0) {
    log('🔴 0곳입니다. 빈 파일을 성공으로 치지 않습니다.')
    process.exit(EXIT.INVARIANT)
  }
  for (const [id, s] of Object.entries(perType)) {
    if (Number.isFinite(s.totalCount) && s.listed < s.totalCount) {
      log(`🔴 ${s.name}(${id}) totalCount ${s.totalCount} 중 ${s.listed}곳만 받았습니다. 부분 수집을 성공으로 치지 않습니다.`)
      process.exit(EXIT.INVARIANT)
    }
  }
  if (!LIST_ONLY && details + empty < targets.length) {
    log(`🔴 ${targets.length}곳 중 ${details + empty}곳만 상세를 받았습니다 (호출 상한에 걸렸을 수 있습니다).`)
    process.exit(EXIT.INVARIANT)
  }
  if (!LIST_ONLY && details === 0) {
    log('🔴 상세가 전부 비었습니다. 영업시간을 한 건도 못 받았다는 뜻입니다.')
    process.exit(EXIT.INVARIANT)
  }
}

main().catch((e) => { console.error('치명:', redact(e?.stack || e)); process.exit(EXIT.INVARIANT) })
