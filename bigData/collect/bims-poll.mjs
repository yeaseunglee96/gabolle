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

const log = (...a) => console.log(new Date().toISOString().slice(0, 19), ...a)

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
  const p = join(ROOT, 'config/routes.json')
  if (existsSync(p)) return JSON.parse(await readFile(p, 'utf8')).routes
  return null   // null = 전체 노선 (오퍼레이션이 지원할 때만)
}

let stats = { polls: 0, records: 0, errors: 0, since: new Date().toISOString() }

async function pollOnce(key, routeIds) {
  const day  = new Date().toISOString().slice(0, 10)
  const file = join(OUT, `bims-${day}.ndjson`)   // 하루 한 파일. 날짜로 파티션
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
  const key = process.env.DATA_GO_KR_KEY
  const routeIds = await routes()

  if (!ENDPOINT) {
    log('🔴 BIMS_ENDPOINT 가 없습니다. 요청주소를 지어내지 않습니다.')
    log('   data.go.kr 마이페이지 → 활용신청 상세의 End Point 와 오퍼레이션 경로를 합쳐')
    log('   bigData/.env 의 BIMS_ENDPOINT 에 그대로 넣으십시오.')
    log('   예) https://apis.data.go.kr/6260000/BusanBIMS/busInfoByRouteId')
    process.exit(2)
  }

  log('BIMS 폴링')
  log(`  엔드포인트 ${ENDPOINT}`)
  log(`  주기       ${INTERVAL_MS / 1000}초`)
  log(`  노선       ${routeIds ? routeIds.length + '개' : '전체'}`)
  log(`  저장       ${OUT}/bims-<날짜>.ndjson`)

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

  const tick = async () => {
    const n = await pollOnce(key, routeIds)
    if (stats.polls % 20 === 0 || n === 0) {
      log(`누적 ${stats.polls}회 / ${stats.records}건 / 오류 ${stats.errors}`)
      await writeFile(join(OUT, '_stats.json'), JSON.stringify(stats, null, 2))
    }
  }
  await tick()
  setInterval(tick, INTERVAL_MS)

  for (const sig of ['SIGINT', 'SIGTERM']) process.on(sig, async () => {
    await writeFile(join(OUT, '_stats.json'), JSON.stringify(stats, null, 2))
    log(`정지. 누적 ${stats.polls}회 / ${stats.records}건 / 오류 ${stats.errors}`)
    process.exit(0)
  })
}

main().catch(e => { console.error('치명:', e); process.exit(1) })
