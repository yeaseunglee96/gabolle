#!/usr/bin/env node
/**
 * 유도값을 운영에 넣었을 때 **몇 곳에 붙어야 정상인가**를 산출물에서 계산한다.
 *
 * `backend/docs/PLACE-DATA-LOAD.md` 4절이 적재 뒤 확인용으로 쓴다.
 *
 * ── 왜 필요한가 — 줄 수는 기대값이 될 수 없다 ────────────────────────────
 *
 * 그 문서는 기대값을 **산출물 줄 수**로 적어 두고 *"0 이면 실패"*, *"줄 수보다
 * 작다고 실패는 아니다"* 라고만 말한다. **얼마나 작아야 정상인지가 없다.**
 * 그래서 돌린 사람이 판정을 못 한다.
 *
 * 산출물의 줄 하나가 곧 장소 하나가 아니다. **그 장소가 운영 DB 에 있어야**
 * 값이 붙는다. 관광공사 수집본은 656곳인데 장소 적재기가 **음식점 328곳을
 * 일부러 안 넣으므로**, 관광공사 열쇠로 붙을 수 있는 상한은 **328곳**이다.
 * 경사 산출물이 2,213줄이어도 327곳을 못 넘는다 —
 * **열쇠가 틀린 것이 아니라 자물쇠가 없는 것이다.**
 *
 * ── 🔴 계산을 믿을 수 있는 이유 — 정답이 붙은 표본이 하나 있다 ───────────
 *
 * 경사는 운영 실측값 **2,682** 가 이미 있다(2026-09-17, 갈래 목록 API).
 * 이 스크립트는 같은 계산을 경사에도 돌려 **2,682 가 나오는지 먼저 본다.**
 * 안 나오면 나머지 세 축의 숫자도 못 믿으므로 **그 자리에서 실패로 끝낸다** —
 * 틀린 기대값은 없는 기대값보다 나쁘다. 없는 기준은 사람이 의심이라도 하지만,
 * 틀린 기준은 정상인 것을 사고로 만들고 사고인 것을 정상으로 만든다.
 *
 * 원본이 바뀌면 이 눈금도 낡는다. 그때 이 검사가 **빨갛게 터지는 것이 목적이다.**
 *
 * ── 🔴 2026-09-18 — 실패하면 숫자를 파일에 안 남긴다 (S15P21E201-1266) ─────
 *
 * 검사가 터졌을 때 화면에는 **"위 기대값을 쓰지 마십시오"** 가 같이 뜬다.
 * 그런데 **그 말은 사라지고 파일은 남았다.** 쓰지 말라고 한 숫자만 살아남는다.
 *
 * 실제로 그렇게 커밋된 적이 있다 — 결과 파일이 실측 자리에 자리표시자 **9999**,
 * 판정 **실패**인 채로 저장소에 들어가 있었다. 스크립트를 안 돌리고 그 파일만 연
 * 사람은 **"검증이 실패했구나"** 로 읽거나 **낡은 숫자를 맞는 값으로 믿는다.**
 * 둘 다 오류 없이 사람을 잘못된 결론으로 보낸다.
 *
 * 그래서 위의 **"틀린 기대값은 없는 기대값보다 나쁘다"** 를 파일에도 적용한다.
 * 검증이 틀리면 파일에 **왜 숫자가 없는지만** 남는다. 실패 자체는 숨기지 않는다 —
 * 화면·종료 코드·파일의 `calibration` 이 셋 다 말한다. **숫자만 안 남긴다.**
 *
 * 실행
 *   node process/expected-feature-rows.mjs
 *   node process/expected-feature-rows.mjs --json   # 표 대신 JSON 만
 *
 * 종료 코드: 0 계산됨 / 1 검증 표본이 안 맞는다 / 2 입력 파일이 없다
 */
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const ROOT = path.join(path.dirname(fileURLToPath(import.meta.url)), '..')
const RAW = path.join(ROOT, 'data/raw/tourapi/tourapi-busan.ndjson')
const STAGED = path.join(ROOT, 'data/staged')
const OUT = path.join(STAGED, '_expected-feature-rows.json')

const EXIT = { OK: 0, BAD: 1, INPUT: 2 }

/**
 * 🔴 장소 적재기가 안 넣는 갈래. 관광공사 `contenttypeid` 39 = 음식점이다.
 *
 * 음식은 상가정보에서 따로 들어오므로 관광공사 쪽에서 또 넣으면 같은 가게가
 * 두 곳이 된다. 그래서 일부러 뺀다 — 빠뜨린 것이 아니다.
 */
const FOOD_CONTENT_TYPE = '39'

/**
 * 🔴 검증 표본. 경사를 실제로 넣고 운영에서 센 값이다 (2026-09-17).
 *
 * 이 숫자가 안 맞으면 아래 계산이 현실과 어긋난 것이고, 나머지 세 축도 못 믿는다.
 */
const CALIBRATION = { axis: 'SLOPE_PERCENT', measured: 2682, measuredOn: '2026-09-17' }

/** 축마다 산출물 둘(관광공사 열쇠 · 상가 열쇠)과 값 칸 이름. */
const AXES = [
  { axis: 'SLOPE_PERCENT', label: '경사', tour: 'place-slope.ndjson', sbiz: 'place-slope-sbiz.ndjson', field: 'slopePercent' },
  { axis: 'QUIETNESS_SCORE', label: '조용함', tour: 'place-quietness.ndjson', sbiz: 'place-quietness-sbiz.ndjson', field: 'quietnessScore' },
  { axis: 'LOCALITY_SCORE', label: '로컬', tour: 'place-locality.ndjson', sbiz: 'place-locality-sbiz.ndjson', field: 'localityScore' },
  { axis: 'SHADE_SCORE', label: '그늘', tour: 'place-shade.ndjson', sbiz: 'place-shade-sbiz.ndjson', field: 'shadeScore' },
]

const die = (code, msg) => {
  console.error(msg)
  process.exit(code)
}

const readLines = (file) => {
  if (!fs.existsSync(file)) die(EXIT.INPUT, `🔴 입력 파일이 없습니다: ${file}`)
  return fs.readFileSync(file, 'utf8').split('\n').filter((l) => l.trim()).map((l) => JSON.parse(l))
}

/**
 * 운영 `place` 에 실제로 들어간 관광공사 장소의 열쇠.
 *
 * 수집본은 API 응답 봉투 그대로라 `raw` 안에 한 번 더 JSON 이 들어 있다.
 */
function tourApiPlacesInDb() {
  const byId = new Map()
  for (const rec of readLines(RAW)) {
    if (!rec.raw) continue
    let body
    try {
      body = JSON.parse(rec.raw)
    }
    catch {
      continue // 오류 응답도 같은 파일에 섞여 있다. 세지 않는다
    }
    const items = body?.response?.body?.items
    if (!items || typeof items !== 'object') continue
    const arr = Array.isArray(items.item) ? items.item : items.item ? [items.item] : []
    for (const it of arr) {
      const id = String(it.contentid ?? '')
      if (id) byId.set(id, String(it.contenttypeid ?? '?'))
    }
  }
  const kept = new Set()
  for (const [id, type] of byId) {
    if (type !== FOOD_CONTENT_TYPE) kept.add(id)
  }
  return { all: byId.size, inDb: kept }
}

function main() {
  const json = process.argv.includes('--json')
  const tour = tourApiPlacesInDb()

  // 🔴 상가 장소가 운영에 몇 곳 있나는 여기서 셀 수 없다 — 그건 DB 의 사실이다.
  //    대신 경사 상가 산출물을 쓴다. 그 2,355곳은 마이그레이션으로 전부 들어갔고
  //    운영 실측 2,682 = 관광공사 327 + 상가 2,355 로 맞아떨어진 바로 그 집합이다.
  const sbizInDb = new Set(readLines(path.join(STAGED, 'place-slope-sbiz.ndjson')).map((r) => r.sourceId))

  const rows = AXES.map((a) => {
    const tourRows = readLines(path.join(STAGED, a.tour))
    const sbizRows = readLines(path.join(STAGED, a.sbiz))
    const tourIds = new Set(tourRows.map((r) => String(r.contentid)))
    const sbizIds = new Set(sbizRows.map((r) => r.sourceId))
    const tourHit = [...tourIds].filter((id) => tour.inDb.has(id)).length
    const sbizHit = [...sbizIds].filter((id) => sbizInDb.has(id)).length
    const values = [...tourRows, ...sbizRows].map((r) => r[a.field]).filter((v) => typeof v === 'number')
    return {
      axis: a.axis,
      label: a.label,
      tourLines: tourIds.size,
      tourExpected: tourHit,
      sbizLines: sbizIds.size,
      sbizExpected: sbizHit,
      expected: tourHit + sbizHit,
      valueMin: Math.min(...values),
      valueMax: Math.max(...values),
    }
  })

  // 🔴 검증 표본을 먼저 본다. 여기서 틀리면 아래 숫자를 내놓지 않는다.
  const sample = rows.find((r) => r.axis === CALIBRATION.axis)
  const calibrated = sample.expected === CALIBRATION.measured

  const result = {
    generatedAt: new Date().toISOString(),
    tourApiPlaces: { collected: tour.all, loadedIntoDb: tour.inDb.size, excludedFood: tour.all - tour.inDb.size },
    sbizPlacesInDb: sbizInDb.size,
    calibration: { ...CALIBRATION, computed: sample.expected, ok: calibrated },
    // 🔴 통과했을 때만 숫자를 넣는다 (S15P21E201-1266). 머리말 참고 —
    //    화면의 "쓰지 마십시오" 는 사라지고 파일은 남는다.
    ...(calibrated
      ? { axes: rows }
      : { notUsable: '검증 표본이 안 맞아 기대값을 내지 않았다. 경사를 운영에서 다시 세어 CALIBRATION 을 갱신한 뒤 다시 돌리십시오.' }),
  }

  fs.mkdirSync(STAGED, { recursive: true })
  fs.writeFileSync(OUT, `${JSON.stringify(result, null, 2)}\n`)

  if (json) console.log(JSON.stringify(result, null, 2))
  else {
    console.log(`장소 — 관광공사 수집 ${tour.all}곳 중 ${tour.inDb.size}곳이 DB 에 있다 (음식 ${tour.all - tour.inDb.size}곳은 일부러 안 넣는다)`)
    console.log(`       상가 ${sbizInDb.size}곳`)
    console.log('')
    console.log('축      관광공사줄  →붙음    상가줄  →붙음   합계기대   값 범위')
    for (const r of rows) {
      console.log(
        `${r.label.padEnd(6)} ${String(r.tourLines).padStart(9)} ${String(r.tourExpected).padStart(7)} `
        + `${String(r.sbizLines).padStart(9)} ${String(r.sbizExpected).padStart(7)} ${String(r.expected).padStart(10)}`
        + `   ${r.valueMin.toFixed(1)}~${r.valueMax.toFixed(1)}`,
      )
    }
    console.log('')
    console.log(`→ ${OUT}`)
  }

  if (!calibrated) {
    die(EXIT.BAD, `\n🔴 검증 표본이 안 맞습니다. ${CALIBRATION.axis} 계산값 ${sample.expected} ≠ 운영 실측 ${CALIBRATION.measured}(${CALIBRATION.measuredOn}).\n`
      + '   산출물이나 장소 적재 규칙이 바뀐 것입니다. **위 기대값을 쓰지 마십시오** —\n'
      + '   경사를 운영에서 다시 세어 CALIBRATION 을 갱신한 뒤에 다시 돌리십시오.')
  }
  console.log(`🟢 검증 표본 일치 — 경사 계산 ${sample.expected} = 운영 실측 ${CALIBRATION.measured} (${CALIBRATION.measuredOn})`)
}

main()
