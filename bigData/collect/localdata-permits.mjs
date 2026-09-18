#!/usr/bin/env node
/**
 * 행정안전부 지방행정 인허가 데이터 (LOCALDATA) — 부산 음식점 5종 수집기
 *
 * 왜 이걸 받나:
 *   CLAUDE.md 2절이 "리뷰 별점을 신호로 쓰지 않는다. 대신 조작할 수 없는 관측
 *   신호를 쓴다 — **영업 지속기간**" 이라고 적었다. 그 영업 지속기간을 실제로
 *   가진 데이터가 이것이다. 칸에 `인허가일자` 와 `폐업일자` 가 그대로 있어서
 *   (폐업일자 - 인허가일자) 가 곧 "이 집이 몇 년 버텼나" 다.
 *   별점과 달리 **가게가 조작할 수 없다** — 관청이 적는 행정 기록이기 때문이다.
 *
 *   두 번째 쓸모는 매칭이다. 우리 정답지(data/truth/raw/*.tsv)의 가게 이름을
 *   후보 풀에 붙이려면 "부산에 실제로 존재하는 가게 목록" 이 있어야 하는데,
 *   그 목록이 이것이다. 일반음식점만으로는 카페·빵집·주점이 안 붙는다 —
 *   그래서 5종을 받는다.
 *
 *   세 번째는 **폐업 데이터가 살아있는 채로 들어있다**는 점이다. 보통의 상가
 *   목록은 지금 영업 중인 곳만 준다. 여기는 폐업한 곳도 폐업일자와 함께 남아서
 *   "이 골목에서 얼마나 많이 망했나"(주변 폐업률)를 셀 수 있다.
 *
 * 🔴 생존 편향을 조심하라. "오래 버틴 집이 맛있다" 가 아니라 "맛없는 집은 이미
 *    없다" 일 수 있다. 임대료가 싼 골목은 맛과 무관하게 오래 버틴다.
 *    이 데이터를 점수로 바꾸기 전에 docs/PERMITS.md 의 '함정' 절을 읽어라.
 *
 * 이 파일이 하는 일은 **받아서 원문 그대로 저장하는 것뿐**이다.
 *   collect/ 는 네트워크만 쓰고 계산은 안 한다 (CLAUDE.md 4절). 점수 계산도,
 *   좌표 변환도 여기서 하지 않는다. 하는 일은 딱 셋이다:
 *     1. 받는다  2. CP949 를 UTF-8 로 바꾼다  3. 칸이 실제로 채워졌는지 센다
 *   3번을 여기서 하는 이유는 아래 '헤더는 거짓말을 한다' 를 보라.
 *
 * ── 받는 곳 ────────────────────────────────────────────────────────────
 *
 *   https://file.localdata.go.kr/file/download/<업종slug>/info?orgCode=<지자체코드>
 *
 *   🔴 **옛 주소 www.localdata.go.kr 은 이 PC 에서 아예 안 열린다.**
 *      443 포트가 21초 뒤 타임아웃한다 (2026-09-08 실측). DNS 는 152.99.104.122
 *      로 풀리는데 연결이 안 된다. 그래서 www 쪽 경로(/datafile/each/*.zip 등)를
 *      쓰는 예전 코드·문서는 전부 못 쓴다. 받는 것은 file. 호스트뿐이다.
 *
 *   로그인도 활용신청도 API 키도 없다. 대신 **Referer 헤더가 필요하다** —
 *   안 붙이면 거부당한다. 그래서 REFERER 상수를 항상 같이 보낸다.
 *
 * ── 왜 전국이 아니라 구·군별로 받나 ────────────────────────────────────
 *
 *   둘 다 되지만 구·군별이 압도적으로 싸다. 2026-09-08 에 Range 요청으로 잰
 *   전국 파일 크기다 (본문을 안 받고 Content-Range 의 총 바이트만 읽었다):
 *
 *     general_restaurants  696,585,557 B  (664 MB)
 *     rest_cafes           207,348,652 B  (198 MB)
 *     bakeries              22,654,977 B  ( 22 MB)
 *     entertainment_bars    16,532,082 B  ( 16 MB)
 *     singing_bars          12,436,046 B  ( 12 MB)
 *                          ────────────
 *                           955,557,314 B  (911 MB)
 *
 *   부산만 받으면 이것이 수십 MB 로 줄어든다. 전국을 받아 거르면 900MB 를
 *   버리려고 900MB 를 내려받는 셈이다.
 *
 *   🔴 **그런데 orgCode 에 부산광역시 코드(6260000)를 넣으면 0건이 온다.**
 *      헤더 한 줄(501바이트)만 돌아온다. 음식점 인허가는 광역시가 아니라
 *      **구·군이 내주기 때문**이다. 그래서 아래 BUSAN_ORG_CODES 처럼 16개
 *      구·군 코드를 일일이 돌아야 한다. 이걸 모르면 "부산은 데이터가 없다" 는
 *      틀린 결론에 도달한다 — 실제로 처음에 그렇게 보였다.
 *
 *      16개 코드가 정확히 부산인지는 지어내지 않고 실측했다. 전국 bakeries
 *      파일(69,481행)을 받아 양방향으로 대조한 결과:
 *        - 코드가 3250000~3400000 인데 주소가 부산이 아닌 행: **0건**
 *        - 주소가 부산인데 코드가 그 범위 밖인 행: **0건**
 *      즉 이 16개가 부산 전부이고 부산만이다.
 *
 * ── 인코딩: 헤더가 거짓말을 한다 ───────────────────────────────────────
 *
 *   🔴 서버는 `Content-Type: text/csv;charset=UTF-8` 이라고 보낸다. **거짓이다.**
 *      실제 바이트는 CP949(EUC-KR 확장)다. 헤더를 믿고 UTF-8 로 읽으면 한글이
 *      전부 깨진다. 2026-09-08 에 첫 16바이트를 직접 떠서 확인했다:
 *        b0b3 b9e6 c0da c4a1 → CP949 로 "개방자치" (UTF-8 로는 깨진 바이트)
 *      그래서 응답을 **바이너리로 받아서** TextDecoder('euc-kr') 로 푼다.
 *      Node 의 'euc-kr' 라벨은 WHATWG 규격상 CP949(windows-949)로 매핑되므로
 *      EUC-KR 에 없는 확장 한글도 같이 풀린다. 외부 라이브러리가 필요 없다.
 *      ('cp949' 라는 라벨은 Node 가 모른다. 'euc-kr' 또는 'windows-949' 를 쓴다.)
 *
 * ── 헤더에 칸이 있다고 값이 있는 게 아니다 ─────────────────────────────
 *
 *   🔴 이 데이터의 가장 큰 함정이다. 39개 칸이 전부 헤더에 있지만 **상당수가
 *      통째로 비어 있다.** 칸 이름만 보고 "홈페이지가 있네" 라고 적었다가
 *      실제로는 0건이었던 사고가 이 팀에 이미 한 번 있었다.
 *      그래서 이 스크립트는 받을 때마다 **칸별로 값이 실제로 몇 건 들어있는지
 *      세서** _stats.json 에 적는다. 문서에 적는 숫자는 거기서 가져온다.
 *      사람이 눈으로 보고 판단하지 않는다.
 *
 * ── 좌표: EPSG:5174 다 (갈랐다). 그래도 여기서 변환하지는 않는다 ──────
 *
 *   칸 이름은 `좌표정보(X)` · `좌표정보(Y)` 이고 값은 미터 단위 평면좌표다
 *   (예: 197448.327811078). 위경도가 아니다.
 *
 *   🟢 **EPSG:5174 인지 5181 인지 2026-09-08 에 실측으로 갈랐다. 5174 다.**
 *      (5174 = Korean 1985 수정중부원점/베셀타원체, 5181 = Korea 2000 중부원점/GRS80.
 *       이름은 비슷한데 기준 타원체가 달라서 같은 점이 수백 m 어긋난다.)
 *
 *      서로 독립인 두 방법이 같은 답을 냈다:
 *
 *      1) **알려진 가게의 실제 위경도와 대조.** data/truth/raw 의 택슐랭 표는
 *         구글 My Maps 에서 온 WGS84 위경도를 갖고 있다. 상호가 유일하게
 *         일치하는 31곳을 두 좌표계로 각각 역투영해 거리를 쟀다:
 *           EPSG:5174 → 오차 중앙값 **20.4 m**
 *           EPSG:5181 → 오차 중앙값 **317.2 m**
 *         31곳 **전부** 5174 가 가까웠다. 남은 20m 는 구글 핀을 손으로 찍은
 *         오차와 건물 중심 차이로 설명되는 크기다.
 *
 *      2) **부산 육지 경계 안에 들어오나 (표본 174,246개).** config/boundary.geojson
 *         의 육지 경계로 점-in-폴리곤을 셌다:
 *           EPSG:5174 → 99.97% 가 육지 안 (밖 48개)
 *           EPSG:5181 → 95.17% 가 육지 안 (밖 8,424개 — 바다로 밀려났다)
 *         틀린 좌표계는 전부를 한 방향으로 미니까 해안가 가게가 바다에 빠진다.
 *
 *      차이가 40m 일 거라고 걱정했는데 실제로는 **~300m 였다.** 훨씬 크고,
 *      그래서 훨씬 분명하게 갈렸다.
 *
 *   🔴 **그런데도 이 파일은 변환하지 않는다.** CLAUDE.md 4절이 collect/ 는
 *      네트워크만 쓰고 계산은 안 한다고 정했다. 좌표 변환은 계산이다.
 *      원문을 원문대로 남겨야 나중에 변환식이 틀린 걸 알았을 때 되돌릴 수 있다.
 *      변환은 process/ 에서 하고, 그때 위 EPSG:5174 를 쓰면 된다.
 *
 *   값 끝에 공백이 붙어 온다(`197448.327811078    `). 쓰는 쪽에서 trim 하라.
 *   원문을 고치지 않으려고 여기서 지우지 않는다.
 *   좌표가 아예 빈 행도 있다 (업종별로 3~6%). 없는 것은 없는 대로 둔다.
 *
 * ── 멈췄다 다시 시작하기 ───────────────────────────────────────────────
 *
 *   구·군 조각을 data/raw/permits/parts/<slug>-<orgCode>.csv 로 따로 남긴다.
 *   이미 있고 헤더가 온전하면 건너뛴다. 80번(5종 × 16구군) 받는 중에 끊겨도
 *   다시 돌리면 안 받은 것만 받는다. 처음부터 다시 받으려면 --force.
 *
 * 실행:
 *   node collect/localdata-permits.mjs
 *   node collect/localdata-permits.mjs --dry-run   # 슬러그·연결만 검사. 본문 안 받음
 *   node collect/localdata-permits.mjs --force     # 이미 받은 것도 다시 받음
 *   node collect/localdata-permits.mjs --only=bakeries,rest_cafes
 *
 * 종료 코드:
 *   0  받아서 저장했다 (또는 --dry-run 검사 통과)
 *   2  입력이 없다 — 네트워크 실패 / 슬러그가 404 / 서버가 거부
 *   1  받긴 받았는데 불변식이 깨졌다 (0건, 헤더 불일치, 부산 아닌 행 등)
 */
import { mkdir, readFile, writeFile, readdir } from 'node:fs/promises'
import { existsSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { stamp } from '../mlops/manifest.mjs'
import { log } from '../lib/log.mjs'

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..')
const OUT = join(ROOT, 'data/raw/permits')
const PARTS = join(OUT, 'parts')

const HOST = 'https://file.localdata.go.kr'

/**
 * 🔴 Referer 가 없으면 서버가 거부한다. 브라우저에서 온 것처럼 보이게 하는
 *    장치이고, 이 값 자체는 공개 포털 주소라 비밀이 아니다.
 *    (www 호스트는 이 PC 에서 안 열리지만 Referer 문자열로 쓰는 것은 무관하다 —
 *     서버가 문자열만 보고 연결하지 않는다.)
 */
const REFERER = 'https://www.localdata.go.kr/'

/**
 * 부산 16개 구·군의 개방자치단체코드.
 *
 * 지어낸 것이 아니라 전국 파일에서 실측해 뽑았다 (파일 머리 주석 참고).
 * 광역시 코드 6260000 은 **여기 없다** — 넣으면 0건이 온다. 음식점 인허가를
 * 내주는 주체가 구·군이기 때문이다.
 *
 * 이름은 대조를 쉽게 하려고 적어 둔 것이고, 코드가 진짜다.
 */
const BUSAN_ORG_CODES = [
  ['3250000', '부산 중구'],
  ['3260000', '부산 서구'],
  ['3270000', '부산 동구'],
  ['3280000', '부산 영도구'],
  ['3290000', '부산 부산진구'],
  ['3300000', '부산 동래구'],
  ['3310000', '부산 남구'],
  ['3320000', '부산 북구'],
  ['3330000', '부산 해운대구'],
  ['3340000', '부산 사하구'],
  ['3350000', '부산 금정구'],
  ['3360000', '부산 강서구'],
  ['3370000', '부산 연제구'],
  ['3380000', '부산 수영구'],
  ['3390000', '부산 사상구'],
  ['3400000', '부산 기장군'],
]

/**
 * 받을 업종 5종.
 *
 * 🔴 슬러그는 추측하면 안 된다. 틀리면 404 다. 아래 다섯은 2026-09-08 에
 *    실제로 받아서 확인한 것이다. 예를 들어 일반음식점은 `rest_general`
 *    이나 `restaurants` 가 아니라 `general_restaurants` 다 — 앞의 둘은 404.
 *
 *    슬러그가 맞는지 싸게 확인하는 방법이 있다: 존재하지 않는 orgCode(예:
 *    광역시 코드 6260000)를 붙이면 유효한 슬러그는 헤더만 200 으로 돌려주고
 *    (501바이트) 없는 슬러그는 404 를 준다. --dry-run 이 이걸 쓴다.
 */
const CATEGORIES = [
  { slug: 'general_restaurants', ko: '일반음식점', why: '식당 본체. 매칭의 기본 후보 풀' },
  { slug: 'rest_cafes', ko: '휴게음식점', why: '카페·분식·패스트푸드가 여기 들어간다' },
  { slug: 'bakeries', ko: '제과점영업', why: '빵집. 부산은 빵집이 관광 자원이다' },
  { slug: 'entertainment_bars', ko: '유흥주점영업', why: '술집. 야간 상권 밀도' },
  { slug: 'singing_bars', ko: '단란주점영업', why: '술집. 유흥주점과 규제가 달라 따로 관리된다' },
]

const ARGV = process.argv.slice(2)
const DRY = ARGV.includes('--dry-run')
const FORCE = ARGV.includes('--force')
const ONLY = (ARGV.find((a) => a.startsWith('--only=')) || '').slice(7).split(',').filter(Boolean)


/** 39개 칸의 헤더. 받은 파일이 이것과 다르면 규격이 바뀐 것이므로 멈춘다. */
const EXPECTED_HEADER_FIRST = '개방자치단체코드'
const EXPECTED_COLS = 39

/**
 * 주소 맨 앞의 시·도 이름. 구·군 코드가 통째로 틀렸는지 교차검증하는 데 쓴다.
 * (전북·강원은 특별자치도로 이름이 바뀐 뒤 옛 표기도 남아 있어 둘 다 받는다.)
 */
const SIDO = /^(서울특별시|부산광역시|대구광역시|인천광역시|광주광역시|대전광역시|울산광역시|세종특별자치시|경기도|강원특별자치도|강원도|충청북도|충청남도|전북특별자치도|전라북도|전라남도|경상북도|경상남도|제주특별자치도)/

/**
 * 🔴 "부산이 아닌 주소" 를 몇 건까지 참을 것인가.
 *
 *    0 으로 두면 안 된다. 2026-09-08 실측으로 일반음식점 131,415행 중 19행이
 *    걸렸는데, 확인해 보니 **구·군 코드가 틀린 게 아니라 원본이 더러운 것**이었다.
 *      - 3건: 주소가 창원·울산이다. 전부 폐업. 부산 구청이 낸 인허가에 다른
 *             시 주소가 적혀 있다. 이전 개업지가 남았거나 입력 오류다.
 *      - 16건: 전부 기장군·폐업이고 지번주소가 "33-3" 처럼 **번지만** 있다.
 *             시·동이 통째로 빠진 옛 기록이다. 부산이 아닌 게 아니라 주소가 없다.
 *
 *    그래서 판정 기준을 "0건" 이 아니라 **비율**로 둔다. 코드를 진짜로 잘못
 *    넣으면(예: 대구 코드) 다른 시 주소가 100% 로 나온다. 원본이 더러운 것은
 *    0.02% 다. 둘은 4자리 수 차이라 0.5% 선이면 안전하게 갈린다.
 *
 *    시·도 이름이 아예 없는 주소(위 16건)는 **분자에 넣지 않는다** — 그건
 *    "다른 시" 라는 증거가 아니라 "모른다" 이기 때문이다. 대신 따로 세서
 *    _stats.json 에 남긴다. 나중에 주소로 매칭할 때 걸릴 것들이다.
 */
const FOREIGN_SIDO_MAX_RATIO = 0.005

/**
 * CSV 한 줄이 아니라 **레코드 단위**로 읽는다.
 *
 * 왜 split('\n') 이 아닌가: 주소 칸이 따옴표로 묶여 있고 그 안에 쉼표가 있다
 * (예: `"서울특별시 종로구 자하문로10길 7, 지상 1, 2층 (창성동)"`).
 * 따옴표 안의 줄바꿈도 원리상 가능하다. 줄 단위로 세면 행 수가 틀린다.
 * 여기서 세는 숫자가 곧 문서에 적히는 숫자라 대충 세면 안 된다.
 */
function* parseCsv(text) {
  let field = ''
  let row = []
  let inQuotes = false
  for (let i = 0; i < text.length; i++) {
    const c = text[i]
    if (inQuotes) {
      if (c === '"') {
        if (text[i + 1] === '"') { field += '"'; i++ } else inQuotes = false
      } else field += c
      continue
    }
    if (c === '"') { inQuotes = true; continue }
    if (c === ',') { row.push(field); field = ''; continue }
    if (c === '\r') continue
    if (c === '\n') { row.push(field); yield row; row = []; field = ''; continue }
    field += c
  }
  if (field !== '' || row.length) { row.push(field); yield row }
}

/**
 * 한 업종 × 한 구·군을 받는다. 반환: UTF-8 텍스트.
 * 🔴 응답을 text() 로 받지 않는다 — 그러면 Node 가 헤더의 거짓 charset 을 믿고
 *    UTF-8 로 풀어서 한글이 깨진다. arrayBuffer 로 받아 직접 푼다.
 */
async function fetchPart(slug, orgCode) {
  const url = `${HOST}/file/download/${slug}/info?orgCode=${orgCode}`
  let res
  try {
    res = await fetch(url, {
      headers: { Referer: REFERER },
      signal: AbortSignal.timeout(300000),
    })
  } catch (e) {
    return { kind: 'neterr', msg: e.message }
  }
  if (res.status === 404) return { kind: 'notfound', msg: `404 — 슬러그 '${slug}' 가 없습니다` }
  if (!res.ok) return { kind: 'http', msg: `HTTP ${res.status}` }
  const buf = Buffer.from(await res.arrayBuffer())
  const text = new TextDecoder('euc-kr').decode(buf)
  return { kind: 'ok', text, bytes: buf.length }
}

/** 슬러그가 실제로 있는지만 싸게 본다 (본문을 안 받는다). */
async function probeSlug(slug) {
  // 광역시 코드는 0건이라 헤더만 온다 — 유효성 확인에 딱 맞다
  const r = await fetchPart(slug, '6260000')
  if (r.kind !== 'ok') return r
  const first = r.text.split('\n')[0] || ''
  if (!first.startsWith(EXPECTED_HEADER_FIRST)) {
    return { kind: 'badheader', msg: `헤더가 '${EXPECTED_HEADER_FIRST}' 로 시작하지 않습니다: ${first.slice(0, 60)}` }
  }
  const cols = [...parseCsv(first)][0].length
  return { kind: 'ok', cols, bytes: r.bytes }
}

async function main() {
  const startedAt = new Date().toISOString()
  const cats = CATEGORIES.filter((c) => !ONLY.length || ONLY.includes(c.slug))
  if (!cats.length) {
    log(`🔴 --only 가 아무 업종에도 안 맞습니다. 쓸 수 있는 값: ${CATEGORIES.map((c) => c.slug).join(', ')}`)
    process.exit(2)
  }

  // ── --dry-run: 슬러그 5개가 살아있는지만 본다 ────────────────────────
  if (DRY) {
    let bad = 0
    for (const c of cats) {
      const r = await probeSlug(c.slug)
      if (r.kind === 'ok') log(`  ✅ ${c.slug} (${c.ko}) — 헤더 ${r.cols}칸`)
      else { log(`  🔴 ${c.slug} (${c.ko}) — ${r.msg}`); bad++ }
    }
    if (bad) { log(`🔴 ${bad}개 업종이 응답하지 않습니다.`); process.exit(2) }
    log('--dry-run 통과. 실제로 받으려면 --dry-run 없이 실행하십시오.')
    process.exit(0)
  }

  await mkdir(PARTS, { recursive: true })

  const summary = []
  let totalFetched = 0
  let totalSkipped = 0

  for (const cat of cats) {
    const parts = []
    let header = null

    for (const [code, name] of BUSAN_ORG_CODES) {
      const partFile = join(PARTS, `${cat.slug}-${code}.csv`)

      // ── 이어받기: 이미 있고 헤더가 온전하면 건너뛴다 ────────────────
      if (!FORCE && existsSync(partFile)) {
        const cached = await readFile(partFile, 'utf8')
        if (cached.startsWith(EXPECTED_HEADER_FIRST)) {
          parts.push({ code, name, text: cached, cached: true })
          totalSkipped++
          continue
        }
        log(`  ${cat.slug} ${name}: 받다 만 파일이라 다시 받습니다`)
      }

      const r = await fetchPart(cat.slug, code)
      if (r.kind === 'notfound') { log(`🔴 ${r.msg}`); process.exit(2) }
      if (r.kind !== 'ok') { log(`🔴 ${cat.slug} ${name} 실패: ${r.msg}`); process.exit(2) }
      if (!r.text.startsWith(EXPECTED_HEADER_FIRST)) {
        log(`🔴 ${cat.slug} ${name}: 헤더가 예상과 다릅니다. 규격이 바뀌었을 수 있습니다.`)
        process.exit(1)
      }
      await writeFile(partFile, r.text)
      parts.push({ code, name, text: r.text, cached: false })
      totalFetched++
      log(`  ${cat.slug} ${name}: ${(r.bytes / 1024).toFixed(0)}KB`)
    }

    // ── 합치기 + 세기 ────────────────────────────────────────────────
    const merged = []
    const cols = []       // 칸별 비어있지 않은 값의 수
    let rows = 0
    let open = 0          // 영업 중
    let closed = 0        // 폐업
    let otherState = 0
    let foreignSido = 0    // 주소 앞머리가 부산이 아닌 다른 시·도인 행
    let addrNoSido = 0     // 주소에 시·도가 아예 없는 행 (번지만 남은 옛 기록)
    let withCoord = 0
    const perOrg = {}

    for (const p of parts) {
      let first = true
      let n = 0
      for (const rec of parseCsv(p.text)) {
        if (first) {
          first = false
          if (!header) { header = rec; merged.push(rec) }
          else if (rec.join(',') !== header.join(',')) {
            log(`🔴 ${cat.slug} ${p.name}: 헤더가 다른 조각과 다릅니다.`)
            process.exit(1)
          }
          continue
        }
        if (rec.length === 1 && rec[0] === '') continue   // 마지막 빈 줄
        merged.push(rec)
        rows++; n++
        for (let i = 0; i < rec.length; i++) {
          if ((rec[i] ?? '').trim() !== '') cols[i] = (cols[i] || 0) + 1
        }
        const state = (rec[header.indexOf('영업상태명')] ?? '').trim()
        if (state === '폐업') closed++
        else if (state.startsWith('영업')) open++
        else otherState++
        // 🔴 코드가 진짜 부산인지 주소로 교차검증한다. 서버가 엉뚱한 걸 줘도 잡히게.
        //    지번주소가 비면 도로명주소를 본다. 둘 다 없으면 셀 수 없다.
        const addr = ((rec[header.indexOf('지번주소')] ?? '').trim()
          || (rec[header.indexOf('도로명주소')] ?? '').trim())
        if (addr !== '') {
          const m = addr.match(SIDO)
          if (!m) addrNoSido++
          else if (m[1] !== '부산광역시') foreignSido++
        }
        const x = (rec[header.indexOf('좌표정보(X)')] ?? '').trim()
        if (x !== '') withCoord++
      }
      perOrg[p.code] = { name: p.name, rows: n }
    }

    // 원문 그대로 다시 CSV 로 쓴다 (UTF-8). 따옴표 규칙은 RFC4180.
    const esc = (v) => (/[",\n]/.test(v) ? `"${v.replace(/"/g, '""')}"` : v)
    const outFile = join(OUT, `${cat.slug}.csv`)
    await writeFile(outFile, merged.map((r) => r.map(esc).join(',')).join('\n') + '\n')

    const fill = {}
    header.forEach((h, i) => { fill[h] = cols[i] || 0 })

    summary.push({
      slug: cat.slug, ko: cat.ko, rows, open, closed, otherState,
      foreignSido, addrNoSido, withCoord, perOrg, fill,
      out: `data/raw/permits/${cat.slug}.csv`,
    })
    log(`${cat.slug} (${cat.ko}): ${rows}행 — 영업 ${open} / 폐업 ${closed} / 기타 ${otherState}`
      + ` (타시도주소 ${foreignSido} · 시도없는주소 ${addrNoSido})`)

    if (rows === 0) {
      log(`🔴 ${cat.slug} 가 0건입니다. 빈 파일을 성공으로 치지 않습니다.`)
      process.exit(1)
    }
    const ratio = foreignSido / rows
    if (ratio > FOREIGN_SIDO_MAX_RATIO) {
      log(`🔴 ${cat.slug}: 주소가 부산이 아닌 행이 ${foreignSido}건 (${(ratio * 100).toFixed(2)}%)입니다.`)
      log('   원본이 더러운 수준(0.02%)이 아니라 구·군 코드가 틀린 수준입니다.')
      process.exit(1)
    }
    if (header.length !== EXPECTED_COLS) {
      log(`🔴 ${cat.slug}: 칸이 ${header.length}개입니다 (예상 ${EXPECTED_COLS}). 규격이 바뀌었습니다.`)
      process.exit(1)
    }
  }

  await writeFile(join(OUT, '_stats.json'), JSON.stringify({
    collectedAt: startedAt,
    source: 'file.localdata.go.kr (행정안전부 지방행정 인허가 데이터)',
    note: '좌표는 원문 그대로(미터 평면좌표)다. 실측 결과 EPSG:5174 이고, 변환은 process/ 의 몫이다 — docs/PERMITS.md 참고',
    coordCrs: 'EPSG:5174',
    coordCrsEvidence: '이름매칭 31곳 오차 중앙값 20.4m(5174) vs 317.2m(5181); 육지경계 포함률 99.97%(5174) vs 95.17%(5181)',
    orgCodes: Object.fromEntries(BUSAN_ORG_CODES),
    categories: summary,
  }, null, 2) + '\n')

  stamp(join(ROOT, 'data/staged/_permits-run'), {
    step: 'collect/localdata-permits',
    inputs: [],
    params: { host: HOST, orgCodes: BUSAN_ORG_CODES.length, categories: cats.map((c) => c.slug), startedAt },
    result: {
      fetched: totalFetched, skipped: totalSkipped,
      rows: Object.fromEntries(summary.map((s) => [s.slug, s.rows])),
      out: 'data/raw/permits/',
    },
  })

  const grand = summary.reduce((a, s) => a + s.rows, 0)
  log(`저장 완료: ${summary.length}종 / 합계 ${grand}행 (새로 받음 ${totalFetched}, 건너뜀 ${totalSkipped})`)
  log('칸별 실제 채움 건수는 data/raw/permits/_stats.json 의 fill 을 보십시오.')
}

main().catch((e) => { console.error('치명:', e?.stack || e); process.exit(1) })
