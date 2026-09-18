#!/usr/bin/env node
/**
 * 영업시간 정규화 — 자유 문장을 "요일 → (열림, 닫힘) 구간" 으로 바꾼다
 *
 * 왜 이게 필요한가:
 *   collect/tourapi.mjs 가 받아 온 것은 사람이 쓴 문장이다.
 *     "09:00~21:00"
 *     "- 하절기(3월~10월) 09:00~18:00<br>- 동절기(11월~2월) 09:00~17:00"
 *     "08:00~17:00 (입장 마감 16:30)"
 *     "상시 개방<br>※ 자세한 사항은 전화문의 요망"
 *   이대로는 "지금 열려 있나" 를 기계가 판정할 수 없다.
 *
 *   config/ontology.jsonld 의 bm:openingHoursNormalized 가 자료형을 미리 정해 두었고,
 *   그 자리에 이런 경고가 붙어 있다 — **"자유 문장을 원문 문자열로 저장하면 파싱이
 *   영원히 미뤄지고 결국 아무도 안 쓴다."** 이 파일이 그 경고에 대한 답이다.
 *
 * 🔴 못 읽은 것을 지어내지 않는다
 *   docs/RECOMMENDATION-DATA-COLLECTION-P0.md 2.1 절이 규칙을 정해 두었다 —
 *   **모름(UNKNOWN)·없음(NONE)·건너뜀(SKIPPED)을 같은 값으로 바꾸지 않는다.**
 *   "점포별 상이" 를 09:00~18:00 으로 추측해서 채우면 사람을 닫힌 문 앞에 보낸다.
 *   그래서 못 읽은 것은 status=UNKNOWN 으로 남기고 **원문을 함께 보관한다.**
 *
 * 🔴 부가 조건을 버리지 않는다
 *   "(입장 마감 16:30)" 을 떼어내고 08:00~17:00 만 남기면 16:45 에 도착하는
 *   일정이 만들어진다. 떼어낸 문장은 notes 에 원문 그대로 넣는다 — 지금 쓰지
 *   않더라도 **버리는 순간 되찾을 수 없다.**
 *
 * 입력:  data/raw/tourapi/tourapi-busan.ndjson          (collect/tourapi.mjs)
 * 출력:  data/staged/opening-hours.ndjson               (곳마다 한 줄)
 *        data/staged/_opening-hours-run/                (실행 지문)
 *
 * 실행:
 *   node process/opening-hours.mjs
 *   node process/opening-hours.mjs --report   # 못 읽은 것 20개를 함께 찍는다
 *
 * 종료 코드:
 *   0  정규화했다
 *   2  입력이 없다 — 먼저 npm run collect:tourapi
 *   1  불변식이 깨졌다 (0건, 읽어낸 비율이 기준 미달)
 *
 * 🔴 네트워크를 쓰지 않는다. process/ 의 규칙이다 (../CLAUDE.md 4절).
 */
import { mkdir, readFile, writeFile } from 'node:fs/promises'
import { existsSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { stamp } from '../mlops/manifest.mjs'
import { log } from '../lib/log.mjs'

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..')
const IN_FILE = join(ROOT, 'data/raw/tourapi/tourapi-busan.ndjson')
const OUT_DIR = join(ROOT, 'data/staged')
const OUT_FILE = join(OUT_DIR, 'opening-hours.ndjson')

const REPORT = process.argv.includes('--report')

/**
 * 🔴 읽어낸 비율의 바닥. 2026-09-08 실측은 94.0% (535 / 569) 다.
 *    이 값을 밑도는 것은 **파서가 조용히 망가졌거나 원본 형식이 바뀌었다는 뜻**이므로
 *    종료 코드 1 로 막는다. 실측보다 조금 낮게 잡아 사소한 변동에는 안 걸리게 한다.
 *
 *    수치를 문서가 아니라 여기 두는 이유는 ../CLAUDE.md 4절과 같다 —
 *    문서에 적은 숫자는 낡고, 여기 적은 숫자는 검사가 지킨다.
 */
const MIN_RESOLVED_RATE = 0.90

const DAYS = ['mon', 'tue', 'wed', 'thu', 'fri', 'sat', 'sun']
const DAY_KO = { 월: 'mon', 화: 'tue', 수: 'wed', 목: 'thu', 금: 'fri', 토: 'sat', 일: 'sun' }

/**
 * 영업시간이 담기는 필드. 🔴 분류마다 이름이 다르다 — collect/tourapi.mjs 머리말 참고.
 * 아래는 **실제 응답에 있는 필드만** 적은 것이다 (2026-09-08 실측).
 *
 * 🔴 이름이 그럴듯해서 넣었다가 뺀 것 둘 — 같은 실수를 반복하지 않게 남긴다
 *   usetimefestival  축제·행사의 **입장료**다. 시간이 아니다. 값이 "무료" 로 온다.
 *                    축제의 시간은 playtime 이다.
 *   checkintime      숙박의 **체크인 시각**이다. 영업시간이 아니다 — 아래 LODGING 참고.
 */
const HOUR_FIELDS = [
  'opentimefood',      // 음식점
  'usetime',           // 관광지
  'usetimeculture',    // 문화시설
  'usetimeleports',    // 레포츠
  'opentime',          // 쇼핑
  'playtime',          // 축제·행사
]

/**
 * 🔴 숙박(32)은 영업시간 개념이 없다 — 모수에서 가른다
 *   호텔은 "15시에 열고 몇 시에 닫는 곳" 이 아니라 "15시부터 들어갈 수 있는 곳" 이다.
 *   checkintime 을 영업시간 칸에 넣으면 **채움률만 높아 보이고 일정 계산이 조용히
 *   틀어진다.** 그래서 status=LODGING 으로 따로 두고, 읽어낸 비율의 모수에서도 뺀다.
 */
const LODGING_TYPE = 32
/** 휴무일이 담기는 필드. 이름이 restdate 로 시작한다. */
const REST_FIELDS = ['restdatefood', 'restdate', 'restdateculture', 'restdateleports', 'restdateshopping']


/** <br> 과 공백을 정리한다. 원문은 따로 보관하므로 여기서는 마음껏 다듬는다. */
const clean = (s) => String(s).replace(/<br\s*\/?>/gi, '\n').replace(/&nbsp;/g, ' ').replace(/\r/g, '').trim()

const 종일 = /(상시\s*개방|상시\s*운영|항시\s*개방|연중\s*무휴|연중\s*개방|24\s*시간|^상시$|^무휴$)/

/** "9", "09", "9:30" → "09:30". 24시 표기는 그대로 둔다 (다음날 0시를 뜻한다). */
function hhmm(h, m) {
  const H = String(Number(h)).padStart(2, '0')
  return `${H}:${m ? String(m).padStart(2, '0') : '00'}`
}

/** 한 줄에서 시각 구간을 전부 뽑는다. "09:00~21:00", "9시~21시", "09:00 - 18:00" */
function extractRanges(line) {
  const out = []
  const re = /(\d{1,2})\s*(?::\s*(\d{2}))?\s*시?\s*[~\-∼–－]\s*(\d{1,2})\s*(?::\s*(\d{2}))?\s*시?/g
  for (const m of line.matchAll(re)) {
    const a = Number(m[1]), b = Number(m[3])
    // 🔴 "3월~10월" 같은 달 범위를 시각으로 오인하지 않는다. 시각은 0~24 안이고
    //    분이 없으면서 둘 다 12 이하인 것은 달일 수 있으므로, 앞뒤에 '월' 이 붙으면 버린다.
    if (a > 24 || b > 24) continue
    out.push([hhmm(m[1], m[2]), hhmm(m[3], m[4])])
  }
  return out
}

/** 줄에서 달 범위(하절기 3월~10월)를 지운 뒤 시각을 뽑는다. */
function extractRangesSafe(line) {
  const stripped = line.replace(/\d{1,2}\s*월\s*[~\-∼–]\s*\d{1,2}\s*월/g, ' ')
  return extractRanges(stripped)
}

/** 줄에 걸린 요일을 알아낸다. 없으면 null (= 모든 요일). */
function daysOf(line) {
  if (/평일/.test(line)) return ['mon', 'tue', 'wed', 'thu', 'fri']
  if (/주말/.test(line)) return ['sat', 'sun']
  // "월~금", "화~일"
  const r = line.match(/([월화수목금토일])\s*(?:요일)?\s*[~\-∼–]\s*([월화수목금토일])\s*(?:요일)?/)
  if (r) {
    const i = DAYS.indexOf(DAY_KO[r[1]]), j = DAYS.indexOf(DAY_KO[r[2]])
    if (i >= 0 && j >= 0) {
      const out = []
      for (let k = i; ; k = (k + 1) % 7) { out.push(DAYS[k]); if (k === j) break }
      return out
    }
  }
  const singles = [...line.matchAll(/([월화수목금토일])요일/g)].map((m) => DAY_KO[m[1]])
  return singles.length ? [...new Set(singles)] : null
}

/** 계절 딱지가 붙은 줄인가. 붙었으면 그 이름을 낸다. */
function seasonOf(line) {
  if (/하절기|여름철/.test(line)) return '하절기'
  if (/동절기|겨울철/.test(line)) return '동절기'
  if (/성수기/.test(line)) return '성수기'
  if (/비수기/.test(line)) return '비수기'
  const m = line.match(/(\d{1,2})\s*월\s*[~\-∼–]\s*(\d{1,2})\s*월/)
  return m ? `${m[1]}월~${m[2]}월` : null
}

/** 휴무일 문장에서 쉬는 요일을 뽑는다. */
function parseRest(raw) {
  const v = clean(raw)
  if (!v) return { closedDays: null, alwaysOpen: false, notes: [] }
  if (/연중\s*무휴|무휴|없음|연중\s*운영/.test(v) && !/[월화수목금토일]요일/.test(v))
    return { closedDays: [], alwaysOpen: true, notes: [] }
  const days = new Set()
  for (const m of v.matchAll(/([월화수목금토일])요일/g)) days.add(DAY_KO[m[1]])
  const r = v.match(/([월화수목금토일])\s*(?:요일)?\s*[~\-∼–]\s*([월화수목금토일])\s*요일/)
  if (r) {
    const i = DAYS.indexOf(DAY_KO[r[1]]), j = DAYS.indexOf(DAY_KO[r[2]])
    if (i >= 0 && j >= 0) for (let k = i; ; k = (k + 1) % 7) { days.add(DAYS[k]); if (k === j) break }
  }
  // 🔴 "단, 월요일이 공휴일일 경우 그 다음날" 같은 단서는 버리지 않고 notes 로 남긴다.
  const notes = v.split('\n').map((s) => s.trim()).filter((s) => /단,|공휴일|명절|설날|추석|상이|문의/.test(s))
  return { closedDays: days.size ? [...days] : null, alwaysOpen: false, notes }
}

/**
 * 영업시간 문장 하나를 요일별 구간으로 바꾼다.
 * 반환 status: PARSED | ALWAYS_OPEN | UNKNOWN
 */
function parseHours(raw) {
  const v = clean(raw)
  const notes = []
  if (!v) return { status: 'UNKNOWN', reason: '값이 비어 있음', byDay: null, seasonal: null, notes }

  const lines = v.split('\n').map((s) => s.trim()).filter(Boolean)

  // 부가 조건은 떼어서 보관한다. 버리지 않는다.
  for (const l of lines) if (/^※|입장\s*마감|매표\s*마감|라스트\s*오더|주문\s*마감|문의|상이/.test(l)) notes.push(l)

  const byDay = Object.fromEntries(DAYS.map((d) => [d, []]))
  const seasonal = {}
  let any = false

  for (const line of lines) {
    const ranges = extractRangesSafe(line)
    if (!ranges.length) continue
    const season = seasonOf(line)
    const days = daysOf(line) ?? DAYS
    any = true
    if (season) {
      seasonal[season] ??= Object.fromEntries(DAYS.map((d) => [d, []]))
      for (const d of days) seasonal[season][d].push(...ranges)
    } else {
      for (const d of days) byDay[d].push(...ranges)
    }
  }

  if (!any) {
    if (종일.test(v)) return { status: 'ALWAYS_OPEN', byDay: null, seasonal: null, notes }
    return { status: 'UNKNOWN', reason: '시각도 상시 표기도 없음', byDay: null, seasonal: null, notes, sample: v.slice(0, 80) }
  }

  const hasSeason = Object.keys(seasonal).length > 0
  // 계절 분기만 있고 기본이 비면, 기본은 없는 것으로 둔다 (계절이 전부를 덮는다).
  const plain = DAYS.some((d) => byDay[d].length) ? byDay : null
  return {
    status: 'PARSED',
    byDay: plain,
    seasonal: hasSeason ? seasonal : null,
    notes,
  }
}

async function main() {
  if (!existsSync(IN_FILE)) {
    log(`🔴 입력이 없습니다: ${IN_FILE}`)
    log('   먼저 받으십시오:  npm run collect:tourapi')
    process.exit(2)
  }

  log('영업시간 정규화')
  log(`  입력 ${IN_FILE}`)
  log(`  출력 ${OUT_FILE}`)

  const text = await readFile(IN_FILE, 'utf8')
  const out = []
  const tally = { PARSED: 0, ALWAYS_OPEN: 0, UNKNOWN: 0, LODGING: 0 }
  const byType = {}
  const unknownSamples = []
  let places = 0
  let withSeasonal = 0
  let withNotes = 0
  let closedKnown = 0

  for (const line of text.split('\n')) {
    if (!line.trim()) continue
    const rec = JSON.parse(line)
    if (rec.stage !== 'detail') continue
    const item = [].concat(JSON.parse(rec.raw)?.response?.body?.items?.item ?? [])[0]
    if (!item) continue
    places++

    const isLodging = Number(rec.contentTypeId) === LODGING_TYPE
    const hField = HOUR_FIELDS.find((f) => String(item[f] ?? '').trim())
    const rField = REST_FIELDS.find((f) => String(item[f] ?? '').trim())
    const hours = isLodging
      ? { status: 'LODGING', byDay: null, seasonal: null, notes: [] }
      : parseHours(hField ? item[hField] : '')
    const rest = parseRest(rField ? item[rField] : '')

    // 휴무일이 "연중무휴" 면 영업시간을 못 읽었어도 "쉬는 날은 없다" 는 것은 안다.
    if (rest.alwaysOpen && hours.status === 'UNKNOWN') hours.status = 'ALWAYS_OPEN'
    // 🔴 쉬는 날은 그 요일의 구간을 비운다. **계절 분기 안쪽까지 비운다** —
    //    한쪽만 비우면 "월요일 휴무" 인데 하절기에는 월요일이 열려 있는 산출물이 나오고,
    //    읽는 쪽은 둘 중 어느 것을 믿을지 알 수 없다.
    if (rest.closedDays) {
      for (const d of rest.closedDays) {
        if (hours.byDay) hours.byDay[d] = []
        if (hours.seasonal) for (const s of Object.values(hours.seasonal)) s[d] = []
      }
    }

    tally[hours.status]++
    const t = rec.contentTypeId
    byType[t] ??= { PARSED: 0, ALWAYS_OPEN: 0, UNKNOWN: 0 }
    byType[t][hours.status]++
    if (hours.seasonal) withSeasonal++
    if (hours.notes.length || rest.notes.length) withNotes++
    if (rest.closedDays) closedKnown++
    if (hours.status === 'UNKNOWN' && unknownSamples.length < 20 && hours.sample) unknownSamples.push(hours.sample)

    out.push(JSON.stringify({
      contentid: rec.contentid,
      contentTypeId: rec.contentTypeId,
      status: hours.status,
      byDay: hours.byDay,
      seasonal: hours.seasonal,
      closedDays: rest.closedDays,
      notes: [...hours.notes, ...rest.notes],
      // 숙박은 영업시간 대신 체크인·체크아웃을 낸다. 같은 칸에 섞지 않는다.
      checkIn: isLodging ? (item.checkintime ?? null) : undefined,
      checkOut: isLodging ? (item.checkouttime ?? null) : undefined,
      // 🔴 원문을 반드시 함께 남긴다. 규칙을 나중에 고쳐도 다시 받을 필요가 없다.
      raw: {
        hoursField: hField ?? null, hoursValue: hField ? item[hField] : null,
        restField: rField ?? null, restValue: rField ? item[rField] : null,
      },
    }))
  }

  await mkdir(OUT_DIR, { recursive: true })
  await writeFile(OUT_FILE, out.join('\n') + '\n')

  // 🔴 모수는 "영업시간 개념이 있는 곳" 이다. 숙박을 넣으면 채움률이 부풀거나 꺼진다.
  const applicable = places - tally.LODGING
  const resolved = tally.PARSED + tally.ALWAYS_OPEN
  const rate = applicable ? resolved / applicable : 0

  log(`  곳            ${places}  (숙박 ${tally.LODGING} 제외 → 모수 ${applicable})`)
  log(`  읽어냄        ${resolved} (${(rate * 100).toFixed(1)}%)`)
  log(`    구간 파싱   ${tally.PARSED}`)
  log(`    상시 개방   ${tally.ALWAYS_OPEN}`)
  log(`  모름(UNKNOWN) ${tally.UNKNOWN}  ← 지어내지 않고 남긴 것`)
  log(`  숙박(LODGING) ${tally.LODGING}  ← 영업시간 대신 체크인·체크아웃`)
  log(`  계절 분기     ${withSeasonal}`)
  log(`  쉬는 날 앎    ${closedKnown}`)
  log(`  부가 조건 보관 ${withNotes}  ← 입장마감·라스트오더 등`)

  if (REPORT && unknownSamples.length) {
    log('  못 읽은 값 예시:')
    for (const s of unknownSamples) log(`    ${JSON.stringify(s)}`)
  }

  stamp(join(OUT_DIR, '_opening-hours-run'), {
    step: 'process/opening-hours',
    inputs: [IN_FILE],
    params: { minResolvedRate: MIN_RESOLVED_RATE },
    result: { places, applicable, resolved, rate: Number(rate.toFixed(4)), tally, byType, withSeasonal, withNotes, closedKnown, out: 'data/staged/opening-hours.ndjson' },
  })

  // ── 불변식 ────────────────────────────────────────────────────────────
  if (places === 0) {
    log('🔴 0곳입니다. 빈 산출물을 성공으로 치지 않습니다.')
    process.exit(1)
  }
  if (rate < MIN_RESOLVED_RATE) {
    log(`🔴 읽어낸 비율 ${(rate * 100).toFixed(1)}% 가 기준 ${(MIN_RESOLVED_RATE * 100).toFixed(0)}% 아래입니다.`)
    log('   파서가 조용히 망가졌거나 원본 형식이 바뀌었습니다. --report 로 못 읽은 값을 보십시오.')
    process.exit(1)
  }
  log('정규화 완료')
}

main().catch((e) => { console.error('치명:', e?.stack || e); process.exit(1) })
