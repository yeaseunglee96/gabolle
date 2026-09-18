#!/usr/bin/env node
/**
 * 부산광역시_구군 가로수 현황 수집기
 *
 * 왜 이걸 받나:
 *   여름 부산에서 그늘 유무는 "조금 덜 쾌적" 이 아니라 **걷느냐 마느냐**를 가른다.
 *   그리고 **계산으로는 절대 못 만드는 데이터다** — 나무가 어디 심겼는지는
 *   위성 고도에도 OSM 태그에도 거의 없다. 받아야만 알 수 있다.
 *   docs/WALKABILITY.md 2.1 절이 "오늘 할 수 있는 것 중 가장 값이 크다" 고 적은 것.
 *
 * 이 파일이 하는 일은 **받아서 원문 그대로 저장하는 것뿐**이다.
 *   응답 본문을 파싱하지 않고 `raw` 문자열로 남긴다. collect/bims-poll.mjs 와 같은
 *   태도다 — 파싱 규칙은 나중에 바꿀 수 있지만, 안 받은 데이터는 못 만든다.
 *   점수 계산은 process/shade.mjs 가 한다 (네트워크 안 씀).
 *
 * 준비:
 *   1. https://www.data.go.kr 로그인
 *   2. "부산광역시_구군 가로수 현황" 활용신청 (자동승인. 키가 실제로 도는 데
 *      최대 1시간쯤 걸린다)
 *   3. bigData/.env 에  DATA_GO_KR_KEY=발급받은_디코딩키
 *
 * 실행:
 *   node collect/street-trees.mjs
 *   node collect/street-trees.mjs --dry-run    # 키·엔드포인트 설정만 검사. 호출 안 함
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
const OUT = join(ROOT, 'data/raw/trees')
const OUT_FILE = join(OUT, 'street-trees.ndjson')

const DRY = process.argv.includes('--dry-run')

/**
 * 🔴 API 호출 상한. 같은 계정 키를 BIMS 실시간 폴러가 쓰고 있다.
 *    (가로수는 다른 서비스라 일일 한도는 따로지만, 상한은 그것과 무관하게 지킨다.)
 *    한 쪽당 500건이고 전체가 1,000건 미만이라 정상 경로에서는 2~3회면 끝난다.
 *    이 상수는 "무한 페이지네이션 루프" 를 막는 안전장치다.
 */
const MAX_CALLS = 200
const ROWS_PER_PAGE = 500


/** 🔴 키를 절대 로그·산출물에 찍지 않는다. URL 을 통째로 출력할 일이 있으면 이걸 통과시킨다. */
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
 * 엔드포인트를 지어내지 않는다.
 *
 * collect/bims-poll.mjs 는 "기본값을 지어냈다가 .env 값이 한 번도 안 먹었다" 는
 * 버그를 겪고 기본값 자체를 없앴다. 여기서는 지어내는 대신 **이미 팀이 적어 둔
 * 곳에서 읽는다** — config/sources.json 의 busan-street-trees.endpoint.
 * (이 파일은 읽기만 한다. 고치지 않는다.)
 * .env 의 TREES_ENDPOINT 가 있으면 그쪽이 이긴다.
 */
async function resolveEndpoint() {
  if (process.env.TREES_ENDPOINT) return { url: process.env.TREES_ENDPOINT, from: '.env TREES_ENDPOINT' }
  const p = join(ROOT, 'config/sources.json')
  if (!existsSync(p)) return { url: null, from: 'config/sources.json 없음' }
  const reg = JSON.parse(await readFile(p, 'utf8'))
  const src = (reg.sources || []).find((s) => s.id === 'busan-street-trees')
  if (!src?.endpoint) return { url: null, from: 'config/sources.json 에 busan-street-trees.endpoint 없음' }
  return { url: src.endpoint, from: 'config/sources.json busan-street-trees.endpoint' }
}

/**
 * 공공데이터포털은 **오류도 HTTP 200 으로** 돌려준다. 그것을 성공으로 세지 않는다.
 * 반환: { kind: 'ok'|'denied'|'error', msg }
 *   denied = 키가 아직 안 돈다 / 등록되지 않았다 (활용신청 전파 대기 포함)
 */
function classify(text) {
  const t = text.trimStart()
  // 포털 게이트웨이 오류는 XML 로 온다. resultType=json 을 줘도 그렇다.
  if (/SERVICE_KEY_IS_NOT_REGISTERED|SERVICE ACCESS DENIED|등록되지\s*않은|NOT_REGISTERED_SERVICE|APPLICATION_ERROR|LIMITED_NUMBER_OF_SERVICE_REQUESTS/i.test(t))
    return { kind: 'denied', msg: t.replace(/\s+/g, ' ').slice(0, 400) }
  if (t.startsWith('<') && /errMsg|OpenAPI_ServiceResponse|SERVICE ERROR|cmmMsgHeader/i.test(t))
    return { kind: 'error', msg: t.replace(/\s+/g, ' ').slice(0, 400) }
  if (t.startsWith('{')) {
    let j
    try { j = JSON.parse(t) } catch { return { kind: 'error', msg: 'JSON 파싱 실패: ' + t.slice(0, 200) } }
    const code = j?.response?.header?.resultCode
    if (code != null && String(code) !== '00')
      return { kind: 'denied', msg: `resultCode=${code} ${j?.response?.header?.resultMsg ?? ''}` }
    return { kind: 'ok', json: j }
  }
  return { kind: 'error', msg: '예상 못 한 형식: ' + t.slice(0, 200) }
}

async function main() {
  await loadEnv()
  const key = process.env.DATA_GO_KR_KEY
  const { url: ENDPOINT, from } = await resolveEndpoint()

  log('부산 가로수 현황 수집')
  log(`  엔드포인트 ${ENDPOINT ?? '(없음)'}   ← ${from}`)
  log(`  저장       ${OUT_FILE}`)
  log(`  호출 상한  ${MAX_CALLS}회 (쪽당 ${ROWS_PER_PAGE}건)`)

  if (!ENDPOINT) {
    log('🔴 요청주소를 못 정했습니다. 지어내지 않습니다.')
    log('   data.go.kr 활용신청 상세의 End Point 를 .env 의 TREES_ENDPOINT 에 넣으십시오.')
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
  let totalCount = null
  let collected = 0

  for (let page = 1; ; page++) {
    if (calls >= MAX_CALLS) {
      log(`🔴 호출 상한 ${MAX_CALLS}회에 도달했습니다. 여기서 멈춥니다.`)
      break
    }
    const u = new URL(ENDPOINT)
    u.searchParams.set('serviceKey', key)
    u.searchParams.set('pageNo', String(page))
    u.searchParams.set('numOfRows', String(ROWS_PER_PAGE))
    u.searchParams.set('resultType', 'json')

    let text
    try {
      const res = await fetch(u, { signal: AbortSignal.timeout(30000) })
      text = await res.text()
      calls++
      if (!res.ok) {
        log(`🔴 HTTP ${res.status} — ${redact(text).slice(0, 300)}`)
        process.exit(2)
      }
    } catch (e) {
      log(`🔴 네트워크 실패 (${page}쪽): ${redact(e.message)}`)
      process.exit(2)
    }

    const c = classify(text)
    if (c.kind === 'denied') {
      log('🔴 API 가 요청을 거부했습니다. 가짜 데이터를 만들지 않고 여기서 멈춥니다.')
      log(`   응답: ${redact(c.msg)}`)
      log('   활용신청 직후라면 키가 실제로 도는 데 최대 1시간쯤 걸립니다. 뒤에 다시 실행하십시오.')
      process.exit(2)
    }
    if (c.kind === 'error') {
      log(`🔴 API 오류 응답: ${redact(c.msg)}`)
      process.exit(2)
    }

    // 🔴 원문 그대로 남긴다. 아래 개수 세기는 진행 판단용일 뿐, 저장물은 원문이다.
    lines.push(JSON.stringify({ ts: Date.now(), page, rows: ROWS_PER_PAGE, raw: text }))

    const body = c.json?.response?.body ?? {}
    if (totalCount == null) totalCount = Number(body.totalCount)
    const item = body?.items?.item
    const n = Array.isArray(item) ? item.length : item ? 1 : 0
    collected += n
    log(`  ${page}쪽 ${n}건 (누적 ${collected}${Number.isFinite(totalCount) ? ' / ' + totalCount : ''})`)

    if (n === 0) break
    if (Number.isFinite(totalCount) && collected >= totalCount) break
  }

  await writeFile(OUT_FILE, lines.join('\n') + '\n')

  // 실행 지문. 🔴 data/staged/_run.json 을 덮지 않는다 — 하위 폴더에 찍는다.
  stamp(join(ROOT, 'data/staged/_trees-run'), {
    step: 'collect/street-trees',
    inputs: [join(ROOT, 'config/sources.json')],
    params: { endpointFrom: from, rowsPerPage: ROWS_PER_PAGE, maxCalls: MAX_CALLS, startedAt },
    result: { calls, pages: lines.length, records: collected, totalCount, out: 'data/raw/trees/street-trees.ndjson' },
  })

  log(`저장 완료: ${lines.length}쪽 / ${collected}건 / 호출 ${calls}회`)

  // ── 불변식 ────────────────────────────────────────────────────────────
  if (collected === 0) {
    log('🔴 0건입니다. 빈 파일을 성공으로 치지 않습니다.')
    process.exit(1)
  }
  if (Number.isFinite(totalCount) && collected < totalCount) {
    log(`🔴 totalCount ${totalCount} 중 ${collected} 건만 받았습니다. 부분 수집을 성공으로 치지 않습니다.`)
    process.exit(1)
  }
}

main().catch((e) => { console.error('치명:', redact(e?.stack || e)); process.exit(1) })
