#!/usr/bin/env node
/**
 * BIMS 실시간 버스 위치 폴링 — 이 저장소에서 가장 시급한 수집기
 *
 * 🔴 실시간 데이터는 소급 수집이 안 된다. 오늘 안 켜면 한 달 뒤에 한 달치가 없다.
 *    다른 모든 데이터는 늦어도 따라잡지만 이것만은 영원히 못 메운다.
 *
 * 무엇을 만드나:
 *   정류장 간 실제 소요시간의 "분포". 평균이 아니라 분산이 핵심이다 —
 *   평균 12분인데 최악 35분인 노선은 외국인에게 추천하면 안 된다.
 *   시간표에는 절대 안 나오고, 기존 여행 앱이 못 하는 게 정확히 이것이다.
 *
 * 준비:
 *   1. https://www.data.go.kr 회원가입
 *   2. 부산 BIMS 오픈API 활용신청 (자동승인, 보통 1시간 내 키 발급)
 *   3. bigData/.env 에  DATA_GO_KR_KEY=발급받은_디코딩키
 *
 * 실행 (끄지 않고 계속 둔다):
 *   node collect/bims-poll.mjs
 *   node collect/bims-poll.mjs --dry-run     키 없이 설정만 검사
 */
import { appendFile, mkdir, readFile, writeFile } from 'node:fs/promises'
import { existsSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { log } from '../lib/log.mjs'

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..')
const OUT  = join(ROOT, 'data/raw/transit')

const DRY = process.argv.includes('--dry-run')

/**
 * 🔴 이 둘은 **`loadEnv()` 뒤에** 정해야 한다. 모듈 로드 시점에 정하면 안 된다.
 *
 * 예전에는 여기서 `const ENDPOINT = process.env.BIMS_ENDPOINT || '...'` 로 정했다.
 * 그런데 `.env` 를 읽는 `loadEnv()` 는 `main()` 안에서 **나중에** 돈다. 그래서
 * `.env` 에 넣은 값이 한 번도 반영되지 않고 **아래 기본값이 늘 이겼다.**
 *
 * 하필 그 기본값이 지어낸 주소다 — 위 주석이 "지어내지 않는다" 고 못 박은 바로
 * 그것이다. 그래서 증상이 "키를 넣었는데 API 오류" 로 나타나고, 사람은 키를 의심한다.
 * 값은 맞았고 읽는 순서가 틀렸던 것이다.
 *
 * 기본값도 없앤다. 못 정하면 **멈추고 물어본다** — 지어낸 주소로 조용히 실패하는
 * 것보다 낫다.
 */
let INTERVAL_MS = 30000
let ENDPOINT = null


/* ═══════════════════════════════════════════════════════════════════════════
   서버(AWS)에서 여러 키를 동시에 돌리려고 더한 것들 — S15P21E201-633

   노트북 한 대에서 키 하나로 돌 때는 필요 없던 것이 셋 있다.
   ═══════════════════════════════════════════════════════════════════════════ */

/**
 * ① 이 프로세스의 이름. 출력 파일이 겹치지 않게 한다.
 *
 * 🔴 **없으면 6개 프로세스가 같은 파일에 동시에 append 한다.** 리눅스에서 append 가
 *    쪼개지지 않는다고 보장되는 크기는 4KB 까지인데, 여기 한 줄은 API 응답 원본이라
 *    그보다 훨씬 크다. 그러면 줄이 서로 끼어들어 **JSON 이 깨진 채로 쌓인다** —
 *    수집은 성공한 것처럼 보이고, 몇 주 뒤 파싱할 때 발견한다. 그때는 되돌릴 수 없다.
 *
 * 안 주면 옛 이름 그대로 쓴다 — 노트북에서 혼자 돌리던 방식이 그대로 돈다.
 */
const INSTANCE = process.env.BIMS_INSTANCE || null
const suffix = INSTANCE ? `-${INSTANCE}` : ''

/**
 * ② 시각을 한국 시간으로 본다. 서버는 UTC 라 그대로 두면 시간표가 9시간 어긋난다.
 *
 * 하루가 바뀌는 자리도 여기서 정한다 — 예산이 "하루 10,000회" 라서, 날짜 경계가
 * 시간표와 다르면 한도 계산이 어긋난다.
 */
const TZ = process.env.BIMS_TZ || 'Asia/Seoul'
const TZ_FMT = new Intl.DateTimeFormat('en-CA', {
  timeZone: TZ, year: 'numeric', month: '2-digit', day: '2-digit',
  hour: '2-digit', minute: '2-digit', hourCycle: 'h23',
})
function zoned() {
  const p = Object.fromEntries(
    TZ_FMT.formatToParts(new Date()).filter(x => x.type !== 'literal').map(x => [x.type, x.value]))
  return { date: `${p.year}-${p.month}-${p.day}`, min: Number(p.hour) * 60 + Number(p.minute) }
}

/** "05:00-01:00" 처럼 자정을 넘는 구간도 받는다. */
function parseRanges(spec) {
  if (!spec) return null
  return spec.split(',').map(s => s.trim()).filter(Boolean).map(part => {
    const m = part.match(/^(\d{1,2}):(\d{2})\s*-\s*(\d{1,2}):(\d{2})$/)
    if (!m) { log(`🔴 시간 구간을 못 읽었습니다: "${part}" — 예) 05:00-01:00`); process.exit(2) }
    return { from: +m[1] * 60 + +m[2], to: +m[3] * 60 + +m[4] }
  })
}
const inRanges = (min, ranges) => !!ranges && ranges.some(({ from, to }) =>
  from <= to ? (min >= from && min < to) : (min >= from || min < to))   // to <= from 이면 자정을 넘는 구간

/**
 * ③ 하루 호출 상한. 산수가 틀려도 키를 태우지 않게 하는 마지막 방어선.
 *
 * 🔴 한도를 넘기면 그 키는 **그날 남은 시간 동안 아무것도 못 받는다.** 실시간 데이터는
 *    소급 수집이 안 되므로 그렇게 날린 시간은 영영 없다. 시간표를 손으로 고치다 한 자리
 *    틀리는 것은 언제든 일어나는데, 그 대가가 되돌릴 수 없다면 코드가 막아야 한다.
 *
 * 0 이면 상한 없음(옛 동작).
 */
const DAILY_CAP = Number(process.env.BIMS_DAILY_CAP || 0)

const sleep = ms => new Promise(r => setTimeout(r, ms))

async function loadEnv() {
  const p = join(ROOT, '.env')
  if (!existsSync(p)) return
  for (const line of (await readFile(p, 'utf8')).split('\n')) {
    const m = line.match(/^\s*([A-Z_][A-Z0-9_]*)\s*=\s*(.*)\s*$/)
    if (m && !process.env[m[1]]) process.env[m[1]] = m[2].replace(/^["']|["']$/g, '')
  }
}

/** 노선 목록. .env 의 BIMS_ROUTES 에 쉼표로 넣거나, 없으면 대상 구역 통과 노선을 쓴다. */
async function routes() {
  if (process.env.BIMS_ROUTES) return process.env.BIMS_ROUTES.split(',').map(s => s.trim())

  /**
   * 🔴 배정표에서 **내 몫만** 읽는다. 서버에서 6개를 동시에 띄울 때 이게 없으면 노선
   *    목록을 배포 설정에 여섯 번 베껴 적게 되고, 배정을 다시 계산한 날 그 여섯 벌 중
   *    하나만 안 고쳐진다. 그러면 두 프로세스가 같은 노선을 돌고 **겹친 만큼이 그대로
   *    버리는 예산**이 된다 — 아무 오류도 안 나고, 커버리지만 조용히 줄어든다.
   *
   *    배정은 config/bims-assign.json 하나가 정한다. 여기서는 이름으로 찾아 쓰기만 한다.
   */
  if (INSTANCE) {
    const p = join(ROOT, 'config/bims-assign.json')
    if (existsSync(p)) {
      const plan = JSON.parse(await readFile(p, 'utf8'))
      const mine = (plan['배정'] || []).find(a => a['담당'] === INSTANCE)
      if (!mine) {
        log(`🔴 배정표에 "${INSTANCE}" 가 없습니다. 이름을 지어내지 않습니다.`)
        log(`   있는 이름: ${(plan['배정'] || []).map(a => a['담당']).join(', ')}`)
        process.exit(2)
      }
      return (mine['노선'] || []).map(r => r.lineid)
    }
  }

  const p = join(ROOT, 'config/routes.json')
  if (existsSync(p)) return JSON.parse(await readFile(p, 'utf8')).routes
  return null   // null = 전체 노선 (오퍼레이션이 지원할 때만)
}

// calls = 오늘 실제로 부른 횟수. 하루 상한을 재는 값이라 날이 바뀌면 0 으로 돌아간다.
let stats = { polls: 0, records: 0, errors: 0, calls: 0, since: new Date().toISOString() }

async function pollOnce(key, routeIds) {
  // 🔴 날짜를 UTC 가 아니라 TZ(기본 한국) 기준으로 끊는다. UTC 로 끊으면 파일이
  //    한국 시간 오전 9시에 갈리는데, 그건 하루의 한가운데다.
  const day  = zoned().date
  const file = join(OUT, `bims-${day}${suffix}.ndjson`)   // 하루 한 파일. 날짜로 파티션
  const ts   = Date.now()
  let wrote = 0

  for (const routeId of (routeIds || [null])) {
    const url = new URL(ENDPOINT)
    url.searchParams.set('serviceKey', key)
    url.searchParams.set('numOfRows', '500')
    url.searchParams.set('pageNo', '1')
    url.searchParams.set('resultType', 'json')
    if (routeId) url.searchParams.set('lineid', routeId)

    try {
      const res = await fetch(url, { signal: AbortSignal.timeout(20000) })
      const text = await res.text()
      if (!res.ok) throw new Error(`HTTP ${res.status} ${text.slice(0, 120)}`)

      // 공공데이터포털은 에러도 200 으로 XML 을 돌려준다. 그것을 성공으로 세지 않는다.
      if (text.trimStart().startsWith('<') && /errMsg|OpenAPI_ServiceResponse|SERVICE ERROR/i.test(text)) {
        throw new Error('API 오류 응답: ' + text.replace(/\s+/g, ' ').slice(0, 200))
      }

      // 원본 그대로 남긴다. 파싱은 나중에 바꿀 수 있지만 안 받은 데이터는 못 만든다.
      await appendFile(file, JSON.stringify({ ts, routeId, raw: text }) + '\n')
      wrote++
    } catch (e) {
      stats.errors++
      log(`  ⚠ ${routeId ?? '전체'}: ${e.message}`)
    }
  }
  stats.polls++; stats.records += wrote
  return wrote
}

async function main() {
  await loadEnv()
  // 🔴 여기서 정한다. 위 선언부의 주석을 보라 — 순서가 이 파일의 버그였다.
  INTERVAL_MS = Number(process.env.BIMS_INTERVAL_MS || 30000)
  ENDPOINT = process.env.BIMS_ENDPOINT || null

  await mkdir(OUT, { recursive: true })

  /**
   * 🔴 공공데이터포털은 인증키를 **두 가지 형태**로 준다 — Encoding 과 Decoding.
   *    화면에 나란히 있어서 어느 쪽을 복사했는지 사람은 잘 기억하지 못한다.
   *
   *    아래에서 요청을 만들 때 URLSearchParams 가 값을 **다시 인코딩한다.** 그래서
   *    Encoding 형태를 그대로 넣으면 `%2B` 가 `%252B` 가 되어 API 가 거절한다.
   *    증상은 "키를 넣었는데 인증 오류" 이고, 사람은 키를 의심하며 재발급을 받는다.
   *    값은 맞았고 형태가 틀렸던 것이다.
   *
   *    Base64 키에는 `%` 가 나올 수 없으므로, 퍼센트 기호가 보이면 Encoding 형태다.
   *    되돌려서 쓴다. 사람에게 "어느 쪽을 복사했는지" 를 묻지 않아도 되게 한다.
   */
  const rawKey = process.env.DATA_GO_KR_KEY
  const encoded = rawKey && /%[0-9A-Fa-f]{2}/.test(rawKey)
  const key = encoded ? decodeURIComponent(rawKey) : rawKey
  const routeIds = await routes()

  if (!ENDPOINT) {
    log('🔴 BIMS_ENDPOINT 가 없습니다. 요청주소를 지어내지 않습니다.')
    log('   data.go.kr 마이페이지 → 활용신청 상세의 End Point 와 오퍼레이션 경로를 합쳐')
    log('   bigData/.env 의 BIMS_ENDPOINT 에 그대로 넣으십시오.')
    log('   예) https://apis.data.go.kr/6260000/BusanBIMS/busInfoByRouteId')
    process.exit(2)
  }

  const ACTIVE = parseRanges(process.env.BIMS_ACTIVE_HOURS)
  const RUSH   = parseRanges(process.env.BIMS_RUSH_HOURS)
  const RUSH_MS = Number(process.env.BIMS_RUSH_INTERVAL_MS || INTERVAL_MS)

  log('BIMS 폴링')
  log(`  엔드포인트 ${ENDPOINT}`)
  log(`  노선       ${routeIds ? routeIds.length + '개' : '전체'}`)
  log(`  저장       ${OUT}/bims-<날짜>${suffix}.ndjson`)
  if (INSTANCE) log(`  이름       ${INSTANCE}`)
  if (encoded)  log('  키형태     Encoding 으로 보여 되돌려서 씁니다')
  if (ACTIVE) {
    log(`  시간대     ${TZ} 기준`)
    log(`  도는시간   ${process.env.BIMS_ACTIVE_HOURS}`)
    log(`  주기       러시아워(${process.env.BIMS_RUSH_HOURS || '없음'}) ${RUSH_MS / 1000}초 · 그 밖 ${INTERVAL_MS / 1000}초`)
  } else {
    log(`  주기       ${INTERVAL_MS / 1000}초 (하루 종일)`)
  }
  if (DAILY_CAP) log(`  하루상한   ${DAILY_CAP}회 — 넘으면 그날은 멈춥니다`)

  if (!key) {
    log('')
    log('🔴 DATA_GO_KR_KEY 가 없습니다. 수집이 시작되지 않았습니다.')
    log('   1. https://www.data.go.kr 회원가입 → 부산 BIMS 오픈API 활용신청')
    log('   2. bigData/.env 에  DATA_GO_KR_KEY=발급키')
    log('   3. 활용신청 화면의 "요청주소" 를 BIMS_ENDPOINT 에 넣습니다')
    log('')
    log('   실시간 데이터는 소급 수집이 안 됩니다. 오늘 켜지 않으면 오늘치가 영영 없습니다.')
    process.exit(2)              // 🔴 조용히 성공한 척하지 않는다
  }
  if (DRY) { log('--dry-run: 키 확인됨. 폴링하지 않고 종료.'); return }

  const statsFile = join(OUT, `_stats${suffix}.json`)
  const callsPerTick = (routeIds || [null]).length

  const tick = async () => {
    const n = await pollOnce(key, routeIds)
    stats.calls += callsPerTick
    if (stats.polls % 20 === 0 || n === 0) {
      log(`누적 ${stats.polls}회 / ${stats.records}건 / 오류 ${stats.errors} / 오늘호출 ${stats.calls}`)
      await writeFile(statsFile, JSON.stringify(stats, null, 2))
    }
  }

  for (const sig of ['SIGINT', 'SIGTERM']) process.on(sig, async () => {
    await writeFile(statsFile, JSON.stringify(stats, null, 2))
    log(`정지. 누적 ${stats.polls}회 / ${stats.records}건 / 오류 ${stats.errors}`)
    process.exit(0)
  })

  /**
   * 🔴 `setInterval` 을 안 쓴다. 주기가 시간대마다 바뀌기 때문이다 — 한 번 걸어 두면
   *    그 주기가 하루 종일 고정된다. 매 번 "지금 몇 시인가" 를 다시 보고 다음 간격을
   *    정하려면 스스로 다시 예약하는 반복문이어야 한다.
   *
   *    덤으로 겹침이 없어진다. `setInterval` 은 앞선 호출이 안 끝나도 다음을 시작하는데,
   *    네트워크가 느린 날 그러면 요청이 쌓이면서 **한도를 조용히 초과한다.**
   */
  let capDay = zoned().date
  let capIdleTicks = 0
  while (true) {
    const now = zoned()

    if (now.date !== capDay) {           // 날이 바뀌면 상한도 새로 센다
      capDay = now.date
      stats.calls = 0
    }

    if (ACTIVE && !inRanges(now.min, ACTIVE)) {   // 버스가 안 다니는 시간
      await sleep(60000)
      continue
    }
    if (DAILY_CAP && stats.calls + callsPerTick > DAILY_CAP) {
      // 🔴 폴링 횟수(stats.polls)를 늘려서 로그를 줄이지 않는다 — 그러면 실제로 부르지
      //    않은 것이 통계에 섞인다. 로그를 줄이는 일은 로그 전용 카운터로 한다.
      if (capIdleTicks++ % 60 === 0) log(`오늘 상한 ${DAILY_CAP}회에 도달 — ${TZ} 자정까지 쉽니다`)
      await sleep(60000)
      continue
    }

    await tick()
    await sleep(RUSH && inRanges(now.min, RUSH) ? RUSH_MS : INTERVAL_MS)
  }
}

main().catch(e => { console.error('치명:', e); process.exit(1) })
