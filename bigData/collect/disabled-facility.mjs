#!/usr/bin/env node
/**
 * 장애인편의시설 목록 수집 — 한국사회보장정보원 (S15P21E201-1156)
 *
 * 무장애 정보를 메우려고 받는다. 지금 확인된 휠체어 접근 정보는 **2,762곳 중 102곳(3.7%)**
 * 뿐이고, 기존 출처(한국관광공사 무장애 여행 정보)가 179건이라 **그걸 다 읽어도 6.5%가
 * 상한**이다. 이 출처는 그 벽을 넘는다.
 *
 * 🔴 **이 출처를 고른 이유는 실측 조사값이라서다.** 거리뷰로 사진을 보고 판정하면 그건
 *    추정값이고, DB 가 안전 갈래에 추정값을 못 넣게 막는다
 *    (ck_place_feature_safety_never_estimated). 이 자료는 VERIFIED 로 들어간다.
 *
 * ── 2026-09-17 실측 (호출 2회로 확인한 것) ────────────────────────────────
 *
 *   오퍼레이션   getDisConvFaclList        🔴 공개 문서에 없어서 맞춰 봤고 HTTP 200
 *   totalCount   181,901곳 (전국)
 *   numOfRows    1,000 이 그대로 먹는다 (요청 1000 → 실제 1000행, 340KB)
 *   좌표         🟢 온다 — faclLat · faclLng. 주소로 맞출 필요가 없다
 *   지역 필터    🔴 siDoCd=26 이 무시된다 (경기도가 돌아왔다). 부산은 우리가 거른다
 *   evalInfo     🔴 목록에 안 실려 온다. 기구표는 시설당 1회로 따로 불러야 한다
 *
 *   → 전량 = 181,901 ÷ 1,000 = **182회**
 *
 * ── 🔴 하루 100회다. 그래서 이어받기가 이 파일의 절반이다 ──────────────────
 *
 * 개발계정 트래픽이 **100/일** 이다. 182회는 하루에 못 끝낸다. 그래서
 *
 *   · **페이지마다 파일 하나**를 쓴다 (pages/page-0001.xml …)
 *   · 다시 돌리면 **이미 있는 페이지는 건너뛴다.** 그게 이어받기의 전부다
 *   · 🔴 **한 벌짜리 파일에 모아 쓰지 않는다.** tourapi 수집기가 그렇게 만들어져서,
 *        --resume 없이 돌리면 **통째로 비워졌다.** 페이지별 파일은 덮어쓸 수가 없다
 *   · 하루 몫(--budget)을 채우면 **멈춘다.** 남은 페이지 수를 찍고 종료 코드 0 으로 끝난다
 *
 * ── 🔴 실패를 숨기지 않는다 ──────────────────────────────────────────────
 *
 * 응답이 XML 인데 오류도 XML 로 온다. 한도를 넘기면 200 에 오류 본문이 실려 오기도 한다.
 * 그래서 **행이 하나도 없는 응답은 파일로 쓰지 않고 그 자리에서 멈춘다.** 빈 파일을
 * 저장하면 다음 실행이 "이 페이지는 받았다" 고 믿고 건너뛴다 — 구멍이 조용히 생긴다.
 *
 * ── 🔴 2026-09-18 — 읽을 때도 같은 것을 막는다 (S15P21E201-1212) ────────────
 *
 * 위 규칙은 **쓸 때만** 막고 있었다. 이어받기는 **파일 이름만** 보고 "받았다" 고
 * 판단했다. 그런데 받은 98쪽을 **LFS**(대용량 파일을 git 이력에 넣지 않고 따로
 * 보관하는 것)로 저장소에 올린 뒤, 그 자리가 조용한 오염 경로가 됐다.
 *
 * LFS 내용이 안 내려오면 파일 자리에 **포인터 글자만** 남는데 **이름도 개수도
 * 똑같다.** 그러면 1~98쪽을 건너뛰고 99쪽부터 받아, 최종 자료가 **"가짜 98 + 진짜
 * 84"** 가 된다. **아무 오류도 안 난다.**
 *
 * 그래서 받은 쪽을 셀 때 **그 파일에 실제 응답 행이 있는지**까지 본다.
 * 저장할 때 막던 것을 읽을 때도 막는 것뿐이고, 새 규칙이 아니다.
 *
 * 🔴 **"몇 행이면 정상인가" 는 정하지 않는다.** "읽을 수 있는 응답인가" 까지만
 *    본다 — 숫자를 정하면 그것이 또 낡는다.
 *
 * 🔴 **그리고 조용히 다시 받지 않는다.** 내용 없는 파일을 "안 받은 것" 으로 치고
 *    그냥 다시 받으면 **하루 한도 100회를 통째로 태운다.** 파일이 있는데 읽을 수
 *    없는 것은 **받다 만 것이 아니라 로컬 상태가 깨진 것**이다. 멈추고 무엇을
 *    하라고 말한다 — 대개 `git lfs pull` 한 번이면 끝난다.
 *
 * ── 🔴 2026-09-18 — 받은 쪽을 보는 데는 키가 필요 없다 (S15P21E201-1264) ───
 *
 * 위 검사를 **키 없는 PC 에서는 돌릴 수가 없었다.** `--status` 는 API 를 한 번도 안
 * 부르는데 키 검사가 그보다 앞에 있어서, 첫 줄에서 종료 코드 2 로 멈췄다.
 *
 * 레인마다 작업 폴더를 따로 펼치면서 실제로 걸렸다. `.env` 는 일부러 저장소에 안
 * 올리는 파일이라 **clone 으로는 안 따라온다.** 그래서 "받아 둔 98쪽이 진짜인가" 를
 * 보려던 사람이 **정작 그것을 보는 자리에서** 막혔다.
 *
 * 🔴 **받은 자료를 보는 일과 새로 받는 일은 다른 일이다.** 키는 뒤엣것에만 필요하다.
 *    **수집은 여전히 키 없이 안 돈다** — 검사 순서를 바꾼 것이지 느슨하게 한 것이
 *    아니다. 그 두 가지를 `test/verify.mjs` 가 같이 지킨다.
 *
 * ── 🔴 2026-09-19 — 마침 줄이 언제나 "전량 받았다" 고 말했다 (S15P21E201-1289) ──
 *
 * 위 -1212 가 `donePages()` 의 반환을 **집합에서 `{done, broken}` 두 칸으로** 바꿨는데,
 * 부르는 곳 둘 중 **마침 줄 쪽을 안 고쳤다.** 그래서 `now.size` 가 `undefined` 였다.
 *
 * 화면에 `받은 페이지 undefined/182` 가 찍히는 것은 눈에 띄지만, **진짜 문제는
 * 그 아래 판정이다.**
 *
 *     left = 182 - undefined  →  NaN
 *     if (left) …             →  NaN 은 거짓이다
 *
 * 🔴 그래서 **쪽이 남아 있어도 언제나 "🟢 전량 받았습니다" 로 갔다.**
 *    "🔴 남은 페이지 N개. 내일 다시 돌리면 그 다음부터 갑니다" 는 **한 번도 안 나왔다.**
 *    하루 한도가 있는 수집에서 "다 받았다" 는 거짓말은 비싸다 — 사람이 그 말을 믿고
 *    다음 단계로 넘어간다.
 *
 * 2026-09-19 새벽에는 **진짜로 전량이었기 때문에** 맞는 말이 나왔다. 그게 이 결함을
 * 더 오래 숨겼을 것이다.
 *
 * 실행
 *   node collect/disabled-facility.mjs                # 남은 페이지를 오늘 몫(98)만큼
 *   node collect/disabled-facility.mjs --budget 30    # 30회만
 *   node collect/disabled-facility.mjs --status       # 어디까지 받았나 (호출 안 함)
 *
 * 종료 코드: 0 받았다(또는 오늘 몫 소진) / 1 응답이 이상하다 / 2 입력·키 없음
 */
import { readFile, writeFile, mkdir, readdir } from 'node:fs/promises'
import { existsSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { log } from '../lib/log.mjs'

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..')
const ENV = join(ROOT, '.env')
const OUT_DIR = join(ROOT, 'data/raw/facility/pages')

const BASE = 'https://apis.data.go.kr/B554287/DisabledPersonConvenientFacility'
const OP = 'getDisConvFaclList'

/** 한 번에 받는 행 수. 1,000 이 먹는 것을 2026-09-17 에 실측했다. */
const ROWS = 1000

/** 개발계정 하루 한도가 100 이다. 찔러 보기 몫을 남겨 98 로 둔다. */
const DEFAULT_BUDGET = 98

/** 서버에 예의. 공공 API 라 몰아치지 않는다. */
const GAP_MS = 400

const EXIT = { OK: 0, BAD: 1, INPUT: 2 }

const arg = (name, dflt) => {
  const i = process.argv.indexOf(name)
  return i > 0 ? Number(process.argv[i + 1]) : dflt
}
const BUDGET = arg('--budget', DEFAULT_BUDGET)
const STATUS_ONLY = process.argv.includes('--status')

const pageName = (n) => `page-${String(n).padStart(4, '0')}.xml`

const countRows = (xml) => (xml.match(/<servList>/g) ?? []).length

/** LFS 내용이 안 내려온 파일. 이름은 멀쩡하고 안에는 포인터 글자만 있다. */
const isLfsPointer = (text) => text.startsWith('version https://git-lfs.github.com/spec/v1')

/**
 * 이미 받은 페이지 번호. 이것이 이어받기의 전부다.
 *
 * 🔴 **이름이 아니라 내용으로 센다** (S15P21E201-1212). 이름만 보면 LFS 포인터나
 * 쓰다 만 파일을 "받았다" 로 읽고 그 쪽을 영영 건너뛴다 — 머리말 참고.
 *
 * @returns {{done: Set<number>, broken: {file: string, lfs: boolean}[]}}
 *   {@code broken} 은 **파일은 있는데 응답 행이 없는 것**이다. 안 받은 것과 다르게 다룬다
 */
async function donePages() {
  if (!existsSync(OUT_DIR)) return { done: new Set(), broken: [] }
  const files = await readdir(OUT_DIR)
  const done = new Set()
  const broken = []
  for (const f of files) {
    const m = f.match(/^page-(\d{4})\.xml$/)
    if (!m) continue
    const text = await readFile(join(OUT_DIR, f), 'utf8')
    if (countRows(text) > 0) {
      done.add(Number(m[1]))
      continue
    }
    broken.push({ file: f, lfs: isLfsPointer(text) })
  }
  return { done, broken }
}

const totalOf = (xml) => Number((xml.match(/<totalCount>(\d+)</) ?? [])[1])

async function main() {
  // 🔴 키는 **실제로 호출할 때만** 본다 (S15P21E201-1264). 머리말 참고.
  //    검사를 뒤로 미루지 않고 여기서 건너뛰는 이유는, 진짜 수집이 키 없이 돌 때
  //    **파일 100장을 다 읽고 나서** 멈추게 되기 때문이다. 실패는 첫 줄에서 말한다.
  let KEY = null
  if (!STATUS_ONLY) {
    if (!existsSync(ENV)) {
      log(`🔴 .env 가 없습니다: ${ENV}`)
      process.exitCode = EXIT.INPUT
      return
    }
    KEY = (await readFile(ENV, 'utf8')).match(/^DATA_GO_KR_KEY=(.*)$/m)?.[1]?.trim()
    if (!KEY) {
      log('🔴 .env 에 DATA_GO_KR_KEY 가 없습니다.')
      process.exitCode = EXIT.INPUT
      return
    }
  }

  await mkdir(OUT_DIR, { recursive: true })
  const { done, broken } = await donePages()

  // 🔴 파일은 있는데 응답 행이 없다. 안 받은 것으로 치고 다시 받으면 하루 한도를
  //    통째로 태우므로, 멈추고 무엇을 하라고 말한다.
  if (broken.length) {
    const lfs = broken.filter((b) => b.lfs)
    log(`🔴 받은 쪽 파일 ${broken.length}개에 응답 행이 없습니다. 이름만 있고 내용이 없습니다.`)
    for (const b of broken.slice(0, 5)) log(`   ${b.file}${b.lfs ? '  (LFS 내용이 안 내려왔습니다)' : ''}`)
    if (broken.length > 5) log(`   … 그 밖 ${broken.length - 5}개`)
    log('')
    if (lfs.length) {
      log('   👉 git lfs pull 을 한 번 돌리고 다시 실행하십시오.')
      log('      (LFS — 큰 파일을 git 이력에 넣지 않고 따로 보관하는 것. 내용이 따로 내려옵니다)')
    }
    else {
      log('   👉 그 파일들을 지우고 다시 실행하십시오. 지운 쪽만 다시 받습니다.')
    }
    log('   이대로 두면 그 쪽들을 "받았다" 고 믿고 건너뛰어 자료에 구멍이 생깁니다.')
    process.exitCode = EXIT.BAD
    return
  }

  // 전체 페이지 수는 이미 받은 응답에서 읽는다 — 상태만 보려고 호출을 쓰지 않는다.
  let total = null
  if (done.size) {
    const first = [...done].sort((a, b) => a - b)[0]
    total = totalOf(await readFile(join(OUT_DIR, pageName(first)), 'utf8'))
  }
  const lastPage = total ? Math.ceil(total / ROWS) : null

  if (STATUS_ONLY) {
    log(`받은 페이지 ${done.size}개${lastPage ? ` / 전체 ${lastPage}개` : ' (전체 수 미상 — 아직 한 장도 없음)'}`)
    if (lastPage) {
      const missing = []
      for (let p = 1; p <= lastPage; p++) if (!done.has(p)) missing.push(p)
      log(`  남은 페이지 ${missing.length}개${missing.length ? ` — 다음 ${missing.slice(0, 5).join(', ')}${missing.length > 5 ? ' …' : ''}` : ''}`)
      log(`  받은 시설 수 ≈ ${(done.size * ROWS).toLocaleString()} / ${total.toLocaleString()}`)
    }
    return
  }

  log(`장애인편의시설 목록 — 한 번에 ${ROWS}행 · 오늘 몫 ${BUDGET}회`)
  if (lastPage) log(`  이미 받은 페이지 ${done.size} / ${lastPage}`)

  let used = 0
  let page = 0
  let stoppedBy = '전부 받음'
  while (used < BUDGET) {
    page++
    if (lastPage && page > lastPage) break
    // 전체 페이지 수를 아직 모르면 첫 장을 받아 보고 정한다
    if (!lastPage && page > 1 && total == null) break
    if (done.has(page)) continue

    const url = `${BASE}/${OP}?serviceKey=${encodeURIComponent(KEY)}&numOfRows=${ROWS}&pageNo=${page}`
    let res, xml
    try {
      res = await fetch(url)
      xml = await res.text()
    } catch (e) {
      log(`🔴 ${page}쪽 — 호출 실패: ${e?.message ?? e}`)
      process.exitCode = EXIT.BAD
      return
    }
    used++

    const n = countRows(xml)
    if (res.status !== 200 || n === 0) {
      // 🔴 빈 응답을 파일로 쓰지 않는다. 쓰면 다음 실행이 "받았다" 고 믿고 건너뛴다.
      log(`🔴 ${page}쪽 — HTTP ${res.status} · 행 0개. 저장하지 않고 멈춥니다.`)
      log(`   응답 앞부분: ${xml.slice(0, 300).replace(/\s+/g, ' ')}`)
      log(`   (하루 한도를 넘었거나 키가 막혔을 수 있습니다. ${used}회 썼습니다.)`)
      process.exitCode = EXIT.BAD
      return
    }

    await writeFile(join(OUT_DIR, pageName(page)), xml)
    if (total == null) {
      total = totalOf(xml)
      log(`  전체 ${total.toLocaleString()}곳 → 페이지 ${Math.ceil(total / ROWS)}개`)
    }
    if (used % 10 === 0 || page === 1) log(`  ${page}쪽 · ${n}행 (${used}/${BUDGET}회)`)
    if (used < BUDGET) await new Promise((r) => setTimeout(r, GAP_MS))
  }
  if (used >= BUDGET) stoppedBy = '오늘 몫 소진'

  // 🔴 donePages() 는 {done, broken} 을 준다. 집합으로 받으면 size 가 undefined 가 되고,
  //    남은 쪽 계산이 NaN 이 되어 언제나 "전량 받았다" 로 간다 (S15P21E201-1289).
  const { done: now } = await donePages()
  const lp = total ? Math.ceil(total / ROWS) : null
  const left = lp ? lp - now.size : null
  log('')
  log(`${stoppedBy} — 이번에 ${used}회 사용 · 받은 페이지 ${now.size}${lp ? `/${lp}` : ''}`)
  if (left) log(`  🔴 남은 페이지 ${left}개. 내일 같은 명령을 다시 돌리면 그 다음부터 갑니다.`)
  else log(`  🟢 전량 받았습니다. 다음은 부산만 거르는 단계입니다.`)
}

main().catch((e) => { console.error('치명:', e?.stack || e); process.exit(EXIT.BAD) })
