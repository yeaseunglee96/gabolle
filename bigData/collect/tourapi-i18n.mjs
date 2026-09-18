#!/usr/bin/env node
/**
 * 한국관광공사 다국어 관광정보 수집기 — 일문·중문(간체·번체)
 *
 * 왜 이걸 받나:
 *   앱이 언어 다섯(한국어·영어·일본어·중국어 간체·번체)을 고를 수 있게 됐다
 *   (S15P21E201-1109). 그런데 **외국인이 이 앱에서 실제로 읽는 글의 대부분은 화면 문구가
 *   아니라 장소 이름과 소개**다. 그것이 그 언어로 안 들어오면 언어를 골라도 체감이 안 바뀐다.
 *   VisitKorea 가 이미 번역해 둔 문장을 그대로 받는다 — 기계 번역을 새로 만들지 않는다.
 *
 * 🔴 왜 파일 하나인가 — 언어마다 한 벌씩 만들지 않는다
 *   collect/tourapi-en.mjs 와 이 파일은 **오퍼레이션 이름(areaBasedList2·detailIntro2)도,
 *   지역 거르는 법도, 오류 판정도, 불변식도 전부 같다.** 다른 것은 요청주소 하나뿐이다.
 *   세 벌로 복사하면 한 곳을 고칠 때 세 곳을 맞춰야 하고, 한쪽만 고치면 그 언어만 조용히
 *   다르게 돈다. 그래서 언어를 인자로 받는다.
 *
 *   🔴 tourapi-en.mjs 는 이 파일이 생기기 전에 있었고 지금 그대로 둔다. 남의 손이 돌리고
 *   있는 것을 이 티켓에서 갈아엎지 않는다. 다만 **다음에 영문 쪽을 고칠 일이 있으면 그때
 *   이 파일로 접는 것이 맞다** — 지금 두 벌인 것은 알고 있는 빚이다.
 *
 * 🔴 요청주소를 지어내지 않는다
 *   config/sources.json 의 tourapi-ja · tourapi-zh-hans · tourapi-zh-hant 에서 읽는다.
 *   2026-09-16 에 areaCode2 로 셋 다 resultCode 0000 을 확인하고 등록했다.
 *
 * 🔴 국문과 **반대로** areaCode 로 거른다 — 실측으로 확인했다 (2026-09-16)
 *   국문(KorService2)은 areacode 가 거의 비어 있어 lDongRegnCd 로 걸러야 한다(S15P21E201-1031).
 *   **다국어 서비스는 그 반대다.** lDongRegnCd 로 거르면 0건이고 areaCode=6 으로 걸러야 나온다.
 *   국문 수집기의 교훈을 그대로 옮겨 붙였다가 0건을 받았다 — 서비스마다 다시 재야 한다.
 *
 * 🔴 분류 번호도 다르다. 국문은 12·14·15·28·32·38·39 인데 다국어는 **76~85** 를 쓴다.
 *   국문 번호로 물으면 전부 0건이 오고 **아무 오류도 안 난다.**
 *
 *   2026-09-16 실측 (areaCode=6 부산, 분류 없이 전체):
 *     영문 158건 · 일문 164건 · 중문간체 154건 · 중문번체 137건
 *
 *   🔴 국문은 2,218곳인데 다국어는 160곳 안팎이다. **열 배 넘게 적다.** 관광공사가 번역해 둔
 *   것만 있기 때문이고, 우리가 늘릴 수 있는 것이 아니다. 다국어 화면에서 장소가 적게 보이는
 *   것은 버그가 아니라 이 사실이다.
 *
 * 🔴🔴 **contentid 는 국문과 다른 공간이다 — 저장소에 적혀 있던 가정이 틀렸다** (2026-09-16 실측)
 *
 *   collect/tourapi-en.mjs 머리말은 "contentid 는 국문·영문 서비스가 같은 값을 공유한다 —
 *   TourAPI 4.0 이 언어 서비스를 같은 콘텐츠 ID 공간 위에 다국어로 얹은 구조이기 때문이다
 *   (공식 문서 기준)" 라고 적고, 바로 뒤에 "다만 이건 문서상의 사실이고 실측은 아직이다" 라고
 *   덧붙여 뒀다. **재 봤더니 틀렸다.**
 *
 *     국문 656개 vs 일문 164개 — 겹치는 contentid **0개 (0.0%)**
 *     중문간체 0.0% · 중문번체 0.0%
 *
 *   같은 장소가 언어마다 다른 번호다. 심지어 **세 외국어끼리도 서로 다르다**:
 *     요트탈래       일문 2946717 · 중문간체 2946897 · 중문번체 2947938
 *     스카이라인 루지 일문 3078839 · 중문간체 3086672 · 중문번체 3085989
 *
 *   🔴 **contentid 로 조인하는 코드를 쓰지 마라.** 되는 척하면서 0건이 된다.
 *
 * 🔴 그럼 어떻게 잇나 — 좌표와 제목 속 한글
 *
 *   제목에 한국어 이름이 괄호로 들어 있다: 「YACHT TALE（요트탈래）」.
 *   좌표(mapx·mapy)는 다국어 쪽도 100% 차 있다.
 *
 *   실측 (좌표 소수 4자리 ≈ 11m 일치 또는 괄호 안 한글 일치):
 *     일문 30.9% · 중문간체 27.7% · 중문번체 34.1%
 *
 *   낮은 이유는 **두 집합이 애초에 다른 슬라이스**이기 때문이다 — 국문 수집본은 분류
 *   12~39, 다국어는 75~85 다. 겹치는 장소 자체가 적다.
 *
 * 🔴 그래서 **꼭 이어야 하는 것은 아니다.** 다국어 목록에는 이름·주소·좌표·사진·분류가 다
 *   들어 있다. 일본어 사용자에게는 **이 목록을 그대로 보여주면 된다.** 조인은 "국문 장소에
 *   번역을 얹고 싶을 때" 만 필요하고, 그때도 30% 만 얹힌다는 것을 알고 시작해야 한다.
 *
 * 준비:
 *   1. data.go.kr 에서 상품별 활용신청 (무료·개발단계 자동승인)
 *      - 한국관광공사_일문 관광정보서비스_GW
 *      - 한국관광공사_중문간체 관광정보서비스_GW
 *      - 한국관광공사_중문번체 관광정보서비스_GW
 *   2. bigData/.env 에  DATA_GO_KR_KEY=발급받은_디코딩키
 *      🔴 **Decoding** 키다. Encoding 키(%2F·%2B 가 들어 있는 것)를 넣으면 한 번 더
 *      인코딩돼서 서버가 거절한다 — 2026-09-16 에 실제로 겪었다.
 *
 * 실행:
 *   node collect/tourapi-i18n.mjs --lang ja
 *   node collect/tourapi-i18n.mjs --lang zh-Hans
 *   node collect/tourapi-i18n.mjs --lang zh-Hant
 *   node collect/tourapi-i18n.mjs --lang ja --dry-run   # 설정만 검사. 호출 안 함
 *   node collect/tourapi-i18n.mjs --lang ja --list-only # 목록만. 상세는 안 받는다
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
import { log } from '../lib/log.mjs'

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..')
const OUT = join(ROOT, 'data/raw/tourapi')

/** 고를 수 있는 언어. 코드는 앱의 src/i18n/languages.ts 와 같은 값을 쓴다. */
const LANGS = {
  ja: { sourceId: 'tourapi-ja', label: '일문', out: 'tourapi-busan-ja.ndjson' },
  'zh-Hans': { sourceId: 'tourapi-zh-hans', label: '중문 간체', out: 'tourapi-busan-zh-hans.ndjson' },
  'zh-Hant': { sourceId: 'tourapi-zh-hant', label: '중문 번체', out: 'tourapi-busan-zh-hant.ndjson' },
}

const DRY = process.argv.includes('--dry-run')
const LIST_ONLY = process.argv.includes('--list-only')

function readLangArg() {
  const i = process.argv.indexOf('--lang')
  return i >= 0 ? process.argv[i + 1] : null
}

/** 국문 수집기와 같은 안전장치 — collect/tourapi.mjs 주석 참고. */
const MAX_CALLS = 4000
const ROWS_PER_PAGE = 100

/** 국문 수집기와 같은 분류 — contentid 조인을 맞추려면 같은 종류를 받아야 한다. */
const CONTENT_TYPES = [
  [75, '레포츠'],
  [76, '관광지'],
  [77, '교통'],
  [78, '문화시설'],
  [79, '쇼핑'],
  [80, '숙박'],
  [82, '음식점'],
  [85, '행사·축제'],
]


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

async function resolveSource(sourceId) {
  const p = join(ROOT, 'config/sources.json')
  if (!existsSync(p)) return { base: null, from: 'config/sources.json 없음' }
  const reg = JSON.parse(await readFile(p, 'utf8'))
  const src = (reg.sources || []).find((s) => s.id === sourceId)
  return {
    base: src?.endpoint ?? null,
    from: src ? `config/sources.json ${sourceId}.endpoint` : `config/sources.json 에 ${sourceId} 없음`,
    areaCode: src?.areaCode ?? null,
  }
}

/** 공공데이터포털은 오류도 HTTP 200 으로 돌려준다 — 국문·영문 수집기와 같은 판정. */
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
  const langArg = readLangArg()
  const lang = langArg ? LANGS[langArg] : null
  if (!lang) {
    log('🔴 --lang 을 주십시오: ' + Object.keys(LANGS).join(' · '))
    log('   예) node collect/tourapi-i18n.mjs --lang ja')
    process.exit(2)
  }

  await loadEnv()
  const key = process.env.DATA_GO_KR_KEY
  const { base, from, areaCode } = await resolveSource(lang.sourceId)
  const outFile = join(OUT, lang.out)

  log(`한국관광공사 ${lang.label} 관광정보 수집 (이름·소개 번역문)`)
  log(`  언어       ${langArg}   (${lang.sourceId})`)
  log(`  엔드포인트 ${base ?? '(없음)'}   ← ${from}`)
  log(`  지역       areaCode=${areaCode ?? '(없음)'}   🔴 국문과 달리 lDongRegnCd 가 아니다`)
  log(`  저장       ${outFile}`)
  log(`  호출 상한  ${MAX_CALLS}회 (목록 쪽당 ${ROWS_PER_PAGE}건)${LIST_ONLY ? '  · 목록만' : ''}`)

  if (!base) {
    log('🔴 요청주소를 못 정했습니다. 지어내지 않습니다.')
    log(`   config/sources.json 의 ${lang.sourceId}.endpoint 를 채우십시오.`)
    process.exit(2)
  }
  if (areaCode == null) {
    log(`🔴 지역코드가 없습니다. config/sources.json 의 ${lang.sourceId}.areaCode 를 채우십시오.`)
    log('   🔴 lDongRegnCd 로 바꿔 끼우지 마십시오 — 다국어 서비스에서는 0건이 옵니다 (2026-09-16 실측).')
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

    lines.push(JSON.stringify({ ts: Date.now(), lang: langArg, op, ...meta, raw: text }))
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
        { areaCode, arrange: 'A', contentTypeId: typeId, numOfRows: ROWS_PER_PAGE, pageNo: page },
        { stage: 'list', contentTypeId: typeId, page })
      if (!j) break
      const body = j?.response?.body ?? {}
      if (totalCount == null) totalCount = Number(body.totalCount)
      const item = body?.items?.item
      const arr = Array.isArray(item) ? item : item ? [item] : []
      // 🔴 이름이 목록에 들어 있다 — 이 수집기의 본체다. 상세(소개문)는 덤이라 --list-only 로
      //    이름만 먼저 받을 수 있게 해 뒀다.
      for (const it of arr) if (it?.contentid) targets.push({ contentid: it.contentid, contentTypeId: typeId, title: it.title ?? null })
      got += arr.length
      if (arr.length === 0) break
      if (Number.isFinite(totalCount) && got >= totalCount) break
    }
    perType[typeId] = { name: typeName, listed: got, totalCount }
    log(`  목록 ${typeName}(${typeId}) ${got}${Number.isFinite(totalCount) ? ' / ' + totalCount : ''}곳`)
  }

  // ── 🔴 빠진 분류가 있는지 센다 ─────────────────────────────────────────
  //
  // 분류 번호를 코드에 박아 두면 **목록에 없는 분류가 통째로 조용히 빠진다.** 실제로 겪었다 —
  // 처음에 75(레포츠)를 빼먹었고 164곳 중 154곳만 받았는데 아무 오류도 안 났다. 그래서
  // 분류를 안 건 전체 건수를 한 번 더 물어 **합이 맞는지 확인한다.** 사람의 눈이 아니라
  // 숫자가 잡게 한다.
  const totalAll = await (async () => {
    const j = await fetchRaw('areaBasedList2',
      { areaCode, arrange: 'A', numOfRows: 1, pageNo: 1 }, { stage: 'total' })
    return Number(j?.response?.body?.totalCount)
  })()
  const listedSum = Object.values(perType).reduce((a, s) => a + s.listed, 0)
  log(`  분류 합 ${listedSum} / 전체 ${Number.isFinite(totalAll) ? totalAll : '?'}곳`)

  // ── 2단계: 곳마다 상세(소개문)를 받는다 ──────────────────────────────────
  let details = 0
  let empty = 0
  if (!LIST_ONLY) {
    for (const [i, t] of targets.entries()) {
      const j = await fetchRaw('detailIntro2',
        { contentId: t.contentid, contentTypeId: t.contentTypeId },
        { stage: 'detail', contentid: t.contentid, contentTypeId: t.contentTypeId })
      if (!j) break
      const item = j?.response?.body?.items?.item
      const arr = Array.isArray(item) ? item : item ? [item] : []
      if (arr.length) details++; else empty++
      if ((i + 1) % 200 === 0) log(`  상세 ${i + 1}/${targets.length} (내용 있음 ${details} · 빈 것 ${empty})`)
    }
  }

  await writeFile(outFile, lines.join('\n') + '\n')

  stamp(join(ROOT, `data/staged/_${lang.sourceId}-run`), {
    step: `collect/tourapi-i18n --lang ${langArg}`,
    inputs: [join(ROOT, 'config/sources.json')],
    params: { lang: langArg, endpointFrom: from, areaCode, rowsPerPage: ROWS_PER_PAGE, maxCalls: MAX_CALLS, listOnly: LIST_ONLY, startedAt },
    result: { calls, places: targets.length, details, empty, perType, out: `data/raw/tourapi/${lang.out}` },
  })

  log(`저장 완료: ${targets.length}곳${LIST_ONLY ? '' : ` / 상세 ${details}건(빈 것 ${empty})`} / 호출 ${calls}회`)
  log('🔴 contentid 로 국문과 조인하지 마십시오 — 2026-09-16 실측에서 겹침 0.0% 였습니다.')
  log('   좌표(mapx·mapy)나 제목 괄호 안의 한글로 이어야 하고, 그래도 30% 안팎입니다.')
  log('   자세한 것은 이 파일 머리말.')

  // ── 불변식 ────────────────────────────────────────────────────────────
  if (targets.length === 0) {
    log('🔴 0곳입니다. 빈 파일을 성공으로 치지 않습니다.')
    process.exit(1)
  }
  if (Number.isFinite(totalAll) && listedSum !== totalAll) {
    log(`🔴 분류별 합 ${listedSum} 이 전체 ${totalAll} 과 다릅니다. CONTENT_TYPES 에 없는 분류가 있습니다.`)
    log('   분류를 안 건 목록을 한 번 받아 contenttypeid 를 세어 보고, 빠진 번호를 CONTENT_TYPES 에 더하십시오.')
    log('   🔴 이 차이를 무시하지 마십시오 — 그 분류의 장소가 앱에서 통째로 안 보입니다.')
    process.exit(1)
  }
  for (const [id, s] of Object.entries(perType)) {
    if (Number.isFinite(s.totalCount) && s.listed < s.totalCount) {
      log(`🔴 ${s.name}(${id}) totalCount ${s.totalCount} 중 ${s.listed}곳만 받았습니다. 부분 수집을 성공으로 치지 않습니다.`)
      process.exit(1)
    }
  }
  if (!LIST_ONLY) {
    if (details + empty < targets.length) {
      log(`🔴 ${targets.length}곳 중 ${details + empty}곳만 상세를 받았습니다 (호출 상한에 걸렸을 수 있습니다).`)
      process.exit(1)
    }
    if (details === 0) {
      log('🔴 상세가 전부 비었습니다. 번역문을 한 건도 못 받았다는 뜻입니다.')
      process.exit(1)
    }
  }
}

main().catch((e) => { console.error('치명:', redact(e?.stack || e)); process.exit(1) })
