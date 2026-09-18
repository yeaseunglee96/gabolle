#!/usr/bin/env node
/**
 * 한국관광공사 TourAPI 축제·행사 회차 수집기 — 언제 어디서 하는가
 *
 * 왜 이걸 받나:
 *   축제 화면이 늘 비어 있다. 2026-09-16 실측으로 `GET /festivals` 가 count 0 이다.
 *   적재기와 화면은 이미 있는데 **넣을 값이 없었다** — 수집기가 없었기 때문이다.
 *   그리고 탐색에는 이미 끝난 행사가 뜬다. 축제는 장소가 아니라 **회차**라서
 *   기간(eventstartdate·eventenddate)이 없으면 "지금 갈 수 있는 곳"을 못 만든다.
 *
 * ─────────────────────────────────────────────────────────────────────────
 * 🔴 지역을 areaCode 로 거르지 않는다. lDongRegnCd 로 거른다
 * ─────────────────────────────────────────────────────────────────────────
 *   TourAPI 응답에는 지역 칸이 **둘** 있고 하나가 거의 비어 있다.
 *
 *     areacode      관광용 지역코드 (부산 = 6)
 *     lDongRegnCd   법정동 시도코드 (부산 = 26)
 *
 *   2026-09-16 실측 (searchFestival2, eventStartDate=20260101, 전국 728건 전수):
 *
 *     areacode 가 빈 칸으로 오는 것     715건 / 728건 (98.2%)
 *     lDongRegnCd 가 빈 것                0건 / 728건
 *     areaCode=6 으로 거르면              4건
 *     lDongRegnCd=26 으로 거르면         61건      ← 15배
 *     lDongRegnCd 로만 잡히는 것         57건      ← 지금까지 통째로 놓치던 몫
 *
 *   🔴 이 저장소의 기존 관광공사 수집기 셋(collect/tourapi.mjs · tourapi-en.mjs ·
 *      tourapi-barrier-free.mjs)은 **전부 areaCode 로 거른다.** 축제만의 문제가
 *      아니고, 부산 장소가 641곳 대 2,220곳으로 빠져 있는 것도 같은 뿌리로 보인다
 *      (인수인계 26.09.16 F-1). 그 셋을 고치는 것은 이 파일의 일이 아니다 —
 *      별도 티켓으로 잰 뒤에 고친다. **여기서 같이 고치지 마라.**
 *
 *   🔴 낡은 실측을 지우지 않는다. 인수인계 문서는 "전국 279건 중 278건" 이라고
 *      적었고 오늘 잰 것은 "728건 중 715건" 이다. 비율과 결론은 같고 모수가
 *      다르다 — 조회 조건이 달랐던 것으로 보이며 어느 쪽이 틀린 것이 아니다.
 *      그래서 숫자를 인용할 때 **날짜를 같이 적는다.**
 *
 * ─────────────────────────────────────────────────────────────────────────
 * 🔴 저장 순서가 적재 순서다 — 장소 → 기간 → 사진
 * ─────────────────────────────────────────────────────────────────────────
 *   기간 적재기는 **이미 장소로 들어간 축제에만** 기간을 붙인다. 순서를 뒤집으면
 *   기간이 전부 버려진다 — 예정 19건을 기존 장소 수집본과 대조했을 때 0/19 였다
 *   (인수인계 C-2). 다행히 searchFestival2 는 장소와 기간을 **한 응답에** 준다.
 *   그래서 이 파일은 stage 를 그 순서로 남긴다: list → detail → image.
 *   적재기가 파일을 위에서 아래로 읽으면 그대로 옳은 순서가 된다.
 *
 * ─────────────────────────────────────────────────────────────────────────
 * 🔴 사진 — 목록에 있다고 파일이 있는 것이 아니다
 * ─────────────────────────────────────────────────────────────────────────
 *   404 인 주소가 섞여 있다. **깨진 사진은 없는 사진보다 나쁘다** — 화면이
 *   "사진 있음"으로 알고 빈 칸을 그린다. 그래서 주소마다 **앞부분만 받아**
 *   살아 있는지 확인하고, 죽었으면 다음 후보로 넘어간다. 확인 결과는
 *   alive: true|false 로 원문 옆에 남긴다. 고르는 것은 process/ 의 몫이다.
 *
 *   🔴 사진이 무엇을 찍은 것인지는 **사진 자체의 제목(imgname)으로만** 판정한다.
 *      키워드는 "무엇이냐" 가 아니라 "무엇과 관련 있냐" 다 — 광안리 바다 사진의
 *      키워드에 「광안리 M 드론라이트쇼」가 들어 있다. 거기서 행사가 열리기
 *      때문이지 행사를 찍어서가 아니다. 축제 사진 35건 중 실제로 그 축제를 찍은
 *      것은 1건이었다 (인수인계 F-2). 이 파일은 판정하지 않고 원문을 남긴다.
 *
 * 🔴 라이선스 문구를 지어내지 않는다
 *   신청 화면에 적힌 것은 「이용허락범위 제한 없음」이고 **공공누리 유형 표기가
 *   없다.** 「공공누리 제1유형」이라고 적지 않는다 — 안전한 쪽으로 틀려도
 *   마찬가지다 (인수인계 F-4). 응답의 cpyrhtDivCd 는 **원문 그대로** 남기고
 *   여기서 뜻을 붙이지 않는다.
 *
 * 호출 예산:
 *   부산 축제 61곳(2026-09-16 실측) → 목록 1회 + 상세 61회 + 사진목록 61회 ≈ 123회.
 *   🔴 개발계정 한도는 **오퍼레이션당** 하루 1,000건이다. 계정당이 아니다
 *   (인수인계 F-5). searchFestival2 는 collect/tourapi.mjs 가 쓰는
 *   areaBasedList2 와 **다른 오퍼레이션**이라 그쪽 몫을 갉아먹지 않는다.
 *   사진이 살아 있는지 보는 요청은 tong.visitkorea.or.kr 이라 한도와 무관하다.
 *
 * 준비:
 *   1. data.go.kr → "한국관광공사 국문 관광정보" 활용신청 (KorService2)
 *      🔴 2026-09-16 실측 — tourapi.mjs 가 쓰는 그 신청으로 searchFestival2 가
 *         그대로 200 을 준다. **축제용으로 따로 신청할 것은 없었다.**
 *   2. bigData/.env 에  DATA_GO_KR_KEY=발급받은_디코딩키
 *
 * 실행:
 *   node collect/tourapi-festival.mjs
 *   node collect/tourapi-festival.mjs --dry-run   # 키·엔드포인트만 검사. 호출 안 함
 *   node collect/tourapi-festival.mjs --resume    # 받다 만 것을 잇는다
 *   node collect/tourapi-festival.mjs --no-photo-check   # 사진 생존 확인을 건너뛴다
 *
 * 종료 코드:
 *   0  받아서 저장했다 (또는 --dry-run 통과)
 *   1  받긴 받았는데 불변식이 깨졌다 (0건, totalCount 미달, 기간이 전부 빔)
 *   2  입력이 없다 — 키 없음 / 엔드포인트 없음 / API 가 키를 거부함
 *   3  일일 호출 한도를 다 썼다. 받은 데까지는 파일에 있고 --resume 으로 잇는다
 */
import { appendFile, mkdir, readFile } from 'node:fs/promises'
import { existsSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { stamp } from '../mlops/manifest.mjs'
import { log } from '../lib/log.mjs'

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..')
const OUT = join(ROOT, 'data/raw/tourapi')
const OUT_FILE = join(OUT, 'tourapi-festival-busan.ndjson')

const DRY = process.argv.includes('--dry-run')
const RESUME = process.argv.includes('--resume')
const NO_PHOTO_CHECK = process.argv.includes('--no-photo-check')

/** 종료 코드. 🔴 "다시 하면 되나" 를 숫자로 가른다. collect/tourapi.mjs 와 같다. */
const EXIT = { OK: 0, INVARIANT: 1, INPUT: 2, QUOTA: 3 }

/**
 * 멈출 이유. 🔴 여기서 process.exit() 를 부르지 않는다 — 요청이 떠 있는 채로
 * 부르면 Windows 의 Node 가 죽으면서 종료 코드가 127 이 되고, 애써 갈라 놓은
 * 3(한도)·2(입력)이 통째로 무의미해진다 (collect/tourapi.mjs 2026-09-11 실측).
 */
class Halt extends Error {
  constructor(code, lines = []) { super('halt'); this.code = code; this.lines = lines }
}
class QuotaHalt extends Halt { constructor() { super(EXIT.QUOTA) } }

const MAX_CALLS = 400
const ROWS_PER_PAGE = 100

/**
 * 언제부터의 회차를 받나. 올해 1월 1일부터다.
 *
 * 🔴 "오늘부터" 로 하지 않는다. 지난 회차도 받아 둬야 **작년에 열렸던 축제가
 *    올해 또 열리는지**를 알 수 있고, 무엇보다 지금 화면에 뜨는 지난 행사가
 *    어디서 왔는지 대조할 수 있다. 무엇을 보여줄지는 process/ 와 화면이 정한다.
 */
const EVENT_START_FROM = (process.env.FESTIVAL_FROM || '20260101').replace(/\D/g, '')


/** 🔴 키를 절대 로그·산출물에 찍지 않는다. */
const redact = (s) => String(s).replace(/serviceKey=[^&\s]*/gi, 'serviceKey=<가림>')

/** 🔴 data.go.kr 은 한도 초과를 HTTP 429 로도 주고 200 본문으로도 준다. 둘 다 본다. */
const isQuotaExceeded = (text) =>
  /LIMITED_NUMBER_OF_SERVICE_REQUESTS/i.test(text) || /"returnReasonCode"\s*:\s*"?22"?/.test(text)

async function loadEnv() {
  const p = join(ROOT, '.env')
  if (!existsSync(p)) return
  for (const line of (await readFile(p, 'utf8')).split('\n')) {
    const m = line.match(/^\s*([A-Z_][A-Z0-9_]*)\s*=\s*(.*)\s*$/)
    if (m && !process.env[m[1]]) process.env[m[1]] = m[2].replace(/^["']|["']$/g, '')
  }
}

/** 🔴 요청주소를 지어내지 않는다 — 팀이 적어 둔 config/sources.json 에서 읽는다. */
async function resolveSource() {
  const p = join(ROOT, 'config/sources.json')
  if (!existsSync(p)) return { base: null, from: 'config/sources.json 없음', regnCd: null }
  const reg = JSON.parse(await readFile(p, 'utf8'))
  const src = (reg.sources || []).find((s) => s.id === 'tourapi-festival')
  const base = process.env.TOURAPI_ENDPOINT || src?.endpoint || null
  return {
    base,
    from: process.env.TOURAPI_ENDPOINT ? '.env TOURAPI_ENDPOINT'
      : src?.endpoint ? 'config/sources.json tourapi-festival.endpoint'
      : 'config/sources.json 에 tourapi-festival.endpoint 없음',
    regnCd: src?.lDongRegnCd ?? null,
  }
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

/** 응답의 items.item 은 1건일 때 객체, 여러 건일 때 배열, 0건일 때 빈 문자열로 온다. */
const rows = (j) => {
  const it = j?.response?.body?.items?.item
  return Array.isArray(it) ? it : it ? [it] : []
}

/**
 * 사진 주소가 살아 있나 — **앞부분만** 받아 본다.
 *
 * 🔴 HEAD 로 묻지 않는다. 이 저장소(tong.visitkorea.or.kr)는 HEAD 에 405 나
 *    엉뚱한 상태를 주는 일이 있어서, 없는 파일과 구별이 안 된다. Range 로 첫
 *    바이트만 달라고 하면 실제로 파일을 여는 길이라 확실하다.
 *
 * 반환: { alive, status, reason } — 못 정했으면 alive:null 이다.
 *   🔴 "확인 못 함" 을 "죽었다" 로 바꿔 말하지 않는다. 시간초과·네트워크 실패는
 *      파일이 없다는 뜻이 아니다 (이 팀의 VERIFIED/UNKNOWN 규칙과 같은 자리다).
 */
async function checkPhoto(url) {
  if (!url || !/^https?:\/\//i.test(url)) return { alive: null, status: null, reason: '주소 없음' }
  try {
    const res = await fetch(url, { headers: { Range: 'bytes=0-255' }, signal: AbortSignal.timeout(10000) })
    if (res.status === 200 || res.status === 206) {
      const buf = new Uint8Array(await res.arrayBuffer())
      if (buf.length === 0) return { alive: false, status: res.status, reason: '본문이 비었다' }
      return { alive: true, status: res.status, reason: null }
    }
    return { alive: false, status: res.status, reason: `HTTP ${res.status}` }
  } catch (e) {
    return { alive: null, status: null, reason: `확인 못 함: ${String(e?.message ?? e).slice(0, 80)}` }
  }
}

async function main() {
  await loadEnv()
  const key = process.env.DATA_GO_KR_KEY
  const { base, from, regnCd } = await resolveSource()

  log('한국관광공사 축제·행사 회차 수집 (기간·장소·사진)')
  log(`  엔드포인트 ${base ?? '(없음)'}   ← ${from}`)
  log(`  지역       lDongRegnCd=${regnCd ?? '(없음)'}   🔴 areaCode 가 아니다`)
  log(`  기간       ${EVENT_START_FROM} 부터 시작하는 회차`)
  log(`  저장       ${OUT_FILE}`)
  log(`  호출 상한  ${MAX_CALLS}회 (목록 쪽당 ${ROWS_PER_PAGE}건)`)

  if (!base) {
    log('🔴 요청주소를 못 정했습니다. 지어내지 않습니다.')
    log('   config/sources.json 의 tourapi-festival.endpoint 를 채우십시오.')
    process.exitCode = EXIT.INPUT; return
  }
  if (regnCd == null) {
    log('🔴 법정동 시도코드가 없습니다. config/sources.json 의 tourapi-festival.lDongRegnCd 를 채우십시오.')
    log('   🔴 areaCode 로 바꿔 끼우지 마십시오 — 그러면 61건이 4건이 됩니다 (2026-09-16 실측).')
    process.exitCode = EXIT.INPUT; return
  }
  if (!key) {
    log('🔴 DATA_GO_KR_KEY 가 없습니다. 수집하지 않았습니다.')
    log('   bigData/.env 에  DATA_GO_KR_KEY=발급받은_디코딩키')
    process.exitCode = EXIT.INPUT; return
  }
  if (DRY) { log('--dry-run: 키와 엔드포인트 확인됨. 호출하지 않고 종료.'); return }

  await mkdir(OUT, { recursive: true })

  const startedAt = new Date().toISOString()
  let calls = 0
  let saved = 0

  /** 🔴 앞서 받아 둔 응답. 일일 한도가 있는 API 라서 이미 산 것을 다시 사지 않는다. */
  const cache = new Map()
  const keyOf = (m) => m.stage === 'list' ? `list:${m.page}` : `${m.stage}:${m.contentid}`

  if (RESUME && existsSync(OUT_FILE)) {
    for (const line of (await readFile(OUT_FILE, 'utf8')).split('\n')) {
      if (!line.trim()) continue
      try { const o = JSON.parse(line); if (o.raw) cache.set(keyOf(o), o.raw) } catch { /* 깨진 줄은 버린다 */ }
    }
    saved = cache.size
    log(`  이어받기: 앞서 받아 둔 응답 ${saved}건을 씁니다 (그만큼 안 부릅니다)`)
  }

  async function fetchRaw(op, params, meta) {
    const ck = keyOf(meta)
    if (cache.has(ck)) return classify(cache.get(ck)).json
    if (calls >= MAX_CALLS) { log(`🔴 호출 상한 ${MAX_CALLS}회에 도달했습니다. 여기서 멈춥니다.`); return null }

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
        '   활용신청 직후라면 키가 실제로 도는 데 10~30분쯤 걸립니다.',
      ])
    }
    if (c.kind === 'error') throw new Halt(EXIT.INPUT, [`🔴 API 오류 응답: ${redact(c.msg)}`])

    // 🔴 원문 그대로, 그 자리에서 파일에 덧붙인다. 아래 개수 세기는 진행 판단용이다.
    await appendFile(OUT_FILE, JSON.stringify({ ts: Date.now(), op, ...meta, raw: text }) + '\n')
    cache.set(ck, text)
    saved++
    return c.json
  }

  const festivals = []            // { contentid, title, start, end, firstimage }
  let details = 0, detailEmpty = 0
  let photoListed = 0, photoAlive = 0, photoDead = 0, photoUnknown = 0
  let totalCount = null

  const result = () => ({
    calls, saved, festivals: festivals.length, details, detailEmpty, totalCount,
    photos: { listed: photoListed, alive: photoAlive, dead: photoDead, unknown: photoUnknown },
    regnCd, eventStartFrom: EVENT_START_FROM,
    out: 'data/raw/tourapi/tourapi-festival-busan.ndjson',
  })

  const haltOnQuota = () => {
    log('')
    log('🔴 일일 호출 한도를 다 썼습니다. 오늘은 더 못 받습니다.')
    log(`   받아 둔 응답 ${saved}건은 파일에 남아 있습니다 — ${OUT_FILE}`)
    log('   자정이 지난 뒤 아래로 이어받으십시오. 이미 받은 것은 다시 안 부릅니다.')
    log('     node collect/tourapi-festival.mjs --resume')
    stamp(join(ROOT, 'data/staged/_tourapi-festival-run'), {
      step: 'collect/tourapi-festival',
      inputs: [join(ROOT, 'config/sources.json')],
      params: { endpointFrom: from, lDongRegnCd: regnCd, rowsPerPage: ROWS_PER_PAGE, maxCalls: MAX_CALLS, startedAt, resume: RESUME },
      result: { halted: 'quota', ...result() },
    })
    process.exitCode = EXIT.QUOTA
  }

  try {
    // ── 1단계: 목록 — 장소와 기간이 **한 응답에** 온다 ────────────────────
    //    🔴 그래서 이 단계가 "장소 → 기간" 두 몫을 한꺼번에 채운다 (C-2).
    for (let page = 1; ; page++) {
      const j = await fetchRaw('searchFestival2',
        { eventStartDate: EVENT_START_FROM, lDongRegnCd: regnCd, numOfRows: ROWS_PER_PAGE, pageNo: page, arrange: 'A' },
        { stage: 'list', page })
      if (!j) break
      if (totalCount == null) totalCount = Number(j?.response?.body?.totalCount)
      const arr = rows(j)
      for (const it of arr) {
        if (!it?.contentid) continue
        festivals.push({
          contentid: String(it.contentid),
          title: it.title ?? '',
          start: String(it.eventstartdate ?? ''),
          end: String(it.eventenddate ?? ''),
          firstimage: it.firstimage ?? '',
        })
      }
      if (arr.length === 0) break
      if (Number.isFinite(totalCount) && festivals.length >= totalCount) break
    }
    log(`  목록 축제 ${festivals.length}${Number.isFinite(totalCount) ? ' / ' + totalCount : ''}건`)

    // ── 2단계: 상세 — 이용요금·주최·문의처 (usetimefestival 등) ───────────
    for (const [i, f] of festivals.entries()) {
      const j = await fetchRaw('detailIntro2',
        { contentId: f.contentid, contentTypeId: 15 },
        { stage: 'detail', contentid: f.contentid })
      if (!j) break
      if (rows(j).length) details++; else detailEmpty++
      if ((i + 1) % 20 === 0) log(`  상세 ${i + 1}/${festivals.length} (내용 있음 ${details} · 빈 것 ${detailEmpty})`)
    }

    // ── 3단계: 사진 — 목록을 받고, 주소가 **실제로 살아 있는지** 확인한다 ──
    for (const [i, f] of festivals.entries()) {
      const j = await fetchRaw('detailImage2',
        { contentId: f.contentid, imageYN: 'Y', numOfRows: 20, pageNo: 1 },
        { stage: 'image', contentid: f.contentid })
      if (!j) break
      const imgs = rows(j)
      photoListed += imgs.length

      if (NO_PHOTO_CHECK) continue

      // 🔴 대표사진(firstimage)도 같이 본다. 화면이 그것부터 쓰기 때문이다.
      const urls = [f.firstimage, ...imgs.map((x) => x.originimgurl || x.smallimageurl)].filter(Boolean)
      const seen = new Set()
      const checked = []
      for (const url of urls) {
        if (seen.has(url)) continue
        seen.add(url)
        const r = await checkPhoto(url)
        if (r.alive === true) photoAlive++
        else if (r.alive === false) photoDead++
        else photoUnknown++
        // 🔴 원문 imgname 을 같이 남긴다. 무엇을 찍은 사진인지는 이것으로만 판정한다 (F-2).
        const meta = imgs.find((x) => (x.originimgurl || x.smallimageurl) === url)
        checked.push({ url, alive: r.alive, status: r.status, reason: r.reason,
                       imgname: meta?.imgname ?? (url === f.firstimage ? '(대표사진)' : null),
                       cpyrhtDivCd: meta?.cpyrhtDivCd ?? null })
        // 🔴 살아 있는 것을 하나 찾으면 나머지는 안 본다. 다음 후보로 넘어가는 것이
        //    바로 이 자리다 — 첫 후보가 404 면 그 다음 것을 본다.
        if (r.alive === true) break
      }
      await appendFile(OUT_FILE, JSON.stringify({
        ts: Date.now(), op: 'photo-check', stage: 'image-check', contentid: f.contentid, checked,
      }) + '\n')
      if ((i + 1) % 20 === 0) log(`  사진 ${i + 1}/${festivals.length} (살아있음 ${photoAlive} · 죽음 ${photoDead} · 확인못함 ${photoUnknown})`)
    }
  } catch (e) {
    if (e instanceof QuotaHalt) { haltOnQuota(); return }
    if (e instanceof Halt) { for (const l of e.lines) log(l); process.exitCode = e.code; return }
    throw e
  }

  stamp(join(ROOT, 'data/staged/_tourapi-festival-run'), {
    step: 'collect/tourapi-festival',
    inputs: [join(ROOT, 'config/sources.json')],
    params: { endpointFrom: from, lDongRegnCd: regnCd, rowsPerPage: ROWS_PER_PAGE, maxCalls: MAX_CALLS, startedAt, resume: RESUME },
    result: result(),
  })

  const withPeriod = festivals.filter((f) => f.start && f.end).length
  log('')
  log(`저장 완료: 축제 ${festivals.length}건 / 상세 ${details}건(빈 것 ${detailEmpty}) / 기간 있는 것 ${withPeriod}건`)
  log(`           사진 목록 ${photoListed}장 · 살아있음 ${photoAlive} · 죽음 ${photoDead} · 확인못함 ${photoUnknown}`)
  log(`           새로 부른 호출 ${calls}회 / 파일에 ${saved}줄`)

  // ── 불변식 ──────────────────────────────────────────────────────────────
  if (festivals.length === 0) {
    log('🔴 0건입니다. 빈 파일을 성공으로 치지 않습니다.')
    log('   🔴 지역을 areaCode 로 거르고 있지 않은지 보십시오 — 그러면 거의 다 빠집니다.')
    process.exitCode = EXIT.INVARIANT; return
  }
  if (Number.isFinite(totalCount) && festivals.length < totalCount) {
    log(`🔴 totalCount ${totalCount} 중 ${festivals.length}건만 받았습니다. 부분 수집을 성공으로 치지 않습니다.`)
    process.exitCode = EXIT.INVARIANT; return
  }
  if (details + detailEmpty < festivals.length) {
    log(`🔴 ${festivals.length}건 중 ${details + detailEmpty}건만 상세를 받았습니다 (호출 상한에 걸렸을 수 있습니다).`)
    process.exitCode = EXIT.INVARIANT; return
  }
  // 🔴 축제에서 기간이 없으면 "회차" 가 아니다. 그건 장소일 뿐이고 이 수집기의 뜻이 없다.
  if (withPeriod === 0) {
    log('🔴 기간(eventstartdate·eventenddate)이 있는 것이 한 건도 없습니다.')
    log('   축제는 장소가 아니라 회차입니다. 기간 없이는 적재해도 화면에서 쓸 수 없습니다.')
    process.exitCode = EXIT.INVARIANT; return
  }
}

main().catch((e) => { console.error('치명:', redact(e?.stack || e)); process.exit(EXIT.INVARIANT) })
