#!/usr/bin/env node
/**
 * 장애인편의시설 실태조사를 장소의 휠체어 접근 갈래로 옮긴다
 *
 * 입력:  data/staged/facility-matched.ndjson   (process/facility-match.mjs — 이름으로 붙인 것)
 *        data/raw/facility/eval/<wfcltId>.json  (collect/facility-eval.mjs — 기구표)
 * 출력:  data/staged/place-accessibility.ndjson
 *        data/staged/_place-accessibility-run/
 *
 * 🔴 한 번 접었다가 증거를 보고 뒤집은 일이다 (2026-09-21, S15P21E201-1365)
 *   처음에는 접었다. 근거는 *"evalInfo 는 조사 항목 이름일 뿐이고 통과 가능 여부를 말하지
 *   않는다. 폭·기울기·문턱 값이 없다"* 였다. 그 판단을 세 가지가 뒤집었다.
 *
 *   1. **`장애인사용가능화장실`** — 항목 **이름 자체가 판정**이다. "조사한 것을 전부
 *      나열하는" 방식이면 이런 이름이 나올 수 없다.
 *      🔴 처음 사전은 상가 33곳만 보고 만들어서 이 항목이 안 보였다. 작은 식당엔 그 시설이
 *      없다. **표본이 한쪽에 쏠리면 사전이 거짓말을 한다.**
 *   2. **순방향 11 / 11 일치.** 관광공사가 소개글에 「휠체어 접근 가능」이라고 쓴 11곳
 *      전부에 「주출입구 접근로」 또는 「높이차이 제거」가 실려 있었다. 어긋난 곳 0.
 *      서로 다른 기관이 따로 조사한 값이 하나도 안 어긋났다.
 *   3. **`evalInfo` 가 아예 없는 곳이 2곳.** 조사 대상 목록이라면 비기 어렵다.
 *      「적정한 것만 싣는다」쪽에 힘이 실린다.
 *
 *   그리고 편의증진법 시행령이 접근로의 수치를 못 박고 있다 —
 *   **유효폭 1.2m 이상 · 기울기 1/12 이하.** 그래서 "폭·기울기 값이 없다" 는 처음 근거는
 *   틀렸다. 그 값은 **기준 안에 이미 들어 있다.**
 *
 *   ⚠️ 역방향(관광공사가 그 문구를 안 쓴 곳)도 3 / 3 에 항목이 있었다. 이것은 어긋남이
 *   아니다 — 기존 규칙이 **문구를 글자 그대로 썼을 때만** 붙이는 방식이라, 안 썼다는 것은
 *   못 들어간다는 뜻이 아니라 소개글에 그 문장이 없다는 뜻이다(대형 호텔·시립 문화회관).
 *   즉 이 자료가 관광공사보다 **넓게 잡는다.**
 *
 * 🔴 붙이는 규칙 — 좁은 쪽으로 정했다
 *   붙인다    「주출입구 접근로」 또는 「주출입구 높이차이 제거」 가 있을 때
 *   안 붙인다 **「주출입구(문)」만** 있을 때 — 문이 있다는 것만으로는 통과 여부를 모른다
 *   안 붙인다 `장애인사용가능화장실` · `승강기` · `장애인전용주차구역`
 *             — 앱의 이동 조건 코드에 대응이 없다
 *
 *   실측: 88곳 중 문이 있는 곳 41 · 접근로가 있는 곳 34. **문만 있는 곳이 7곳**이고
 *   그 7곳은 "출입구는 조사됐는데 접근로는 기준을 못 맞췄거나 없다" 로 읽는 것이 안전하다.
 *
 * 🔴 규칙에 걸러진 줄도 **버리지 않고 다 낸다**
 *   여기서 거르면 **왜 걸렀는지가 산출물에서 사라진다.** 적재기가 거른다.
 *   그리고 `evalRaw` 에 원문을 그대로 실어서, 나중에 규칙을 바꿀 때 **다시 안 부르고
 *   다시 판정**할 수 있게 한다. 하루 한도가 100회라 다시 부르는 것은 싸지 않다.
 *
 * 한 줄의 모양 — 데이터 파트와 백엔드 사이의 계약이다:
 *   {"sourceType":"SBIZ","sourceId":"MA0101...","placeId":"8176...","featureKey":"WHEELCHAIR",
 *    "wheelchairAccess":true,"wfcltId":"2635010600-1-13960059",
 *    "evalRaw":"주출입구 높이차이 제거, 주출입구(문)","distanceM":null}
 *
 *   placeId          검산용. 적재기가 다시 계산해 대조하고 다르면 그 줄을 거부한다
 *   featureKey       언제나 "WHEELCHAIR". place_feature 제약상 태그형은 이 칸이 필수다
 *   wheelchairAccess **이 파일의 규칙이 내린 판정.** false 인 줄도 낸다
 *   wfcltId          `source_id` 로 저장한다 — 문제 제보가 오면 되짚는 열쇠다
 *   distanceM        null 은 「못 잼」이지 「멂」이 아니다
 *
 * 종료 코드:
 *   0  만들었다        1  입력이 없다 / 0줄이다
 */
import { mkdir, readFile, writeFile } from 'node:fs/promises'
import { existsSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { stamp } from '../mlops/manifest.mjs'
import { log } from '../lib/log.mjs'

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..')
const IN_FILE = join(ROOT, 'data/staged/facility-matched.ndjson')
const EVAL_DIR = join(ROOT, 'data/raw/facility/eval')
const OUT_DIR = join(ROOT, 'data/staged')
const OUT_FILE = join(OUT_DIR, 'place-accessibility.ndjson')

const FEATURE_KEY = 'WHEELCHAIR'

/** 🔴 이 둘 중 하나가 있을 때만 붙인다. 「주출입구(문)」만으로는 안 된다. */
const ACCESS_ITEMS = ['주출입구 접근로', '주출입구 높이차이 제거']
const ACCESS_PREFIXES = ACCESS_ITEMS.map((s) => s.replace(/\s/g, ''))

const safeName = (id) => String(id).replace(/[^\w.-]/g, '_')

/**
 * 🔴 항목 이름에 표기 변형이 있다. 정확 일치로 비교하면 조용히 샌다
 *
 *   표본을 넓힐 때마다 새 표기가 나왔다 — 33곳에서 5종, 45곳에서 6종, 88곳에서 **11종**.
 *   그 중 하나가 이렇다:
 *
 *     주출입구 높이차이 제거          ← 71곳
 *     **주출입구높이차이제거(경사로)**  ← 3곳. 공백이 없고 괄호가 붙었다
 *
 *   같은 것인데 정확 일치로는 안 걸린다. **경사로가 있다고 적힌 곳이 「없음」으로
 *   판정되고 있었다.** 그래서 공백을 지우고 **앞머리 일치**로 본다.
 *
 *   🔴 「주출입구(문)」과 「주출입문」은 여전히 안 걸린다 — 앞머리가 다르다.
 *      좁게 가기로 한 결정이 여기서 안 흔들린다.
 */
function meetsRule(evalRaw) {
  if (!evalRaw) return false
  return evalRaw
    .split(',')
    .map((s) => s.trim().replace(/\s/g, ''))
    .some((it) => ACCESS_PREFIXES.some((p) => it.startsWith(p)))
}

async function main() {
  log('편의시설 실태조사 → 장소 휠체어 접근 갈래')

  if (!existsSync(IN_FILE)) {
    log('🔴 붙인 목록이 없습니다. 먼저 process/facility-match.mjs 를 돌리십시오.')
    process.exit(1)
  }

  const matched = (await readFile(IN_FILE, 'utf8')).split('\n').filter(Boolean).map((l) => JSON.parse(l))

  const rows = []
  let noEval = 0
  let yes = 0
  let no = 0
  const itemFreq = new Map()

  for (const m of matched) {
    const p = join(EVAL_DIR, safeName(m.wfcltId) + '.json')
    if (!existsSync(p)) { noEval++; continue }
    let e
    try { e = JSON.parse(await readFile(p, 'utf8')) } catch { noEval++; continue }

    const evalRaw = e.evalInfo ?? null
    for (const it of (evalRaw || '').split(',').map((s) => s.trim()).filter(Boolean)) {
      itemFreq.set(it, (itemFreq.get(it) || 0) + 1)
    }

    const ok = meetsRule(evalRaw)
    if (ok) yes++; else no++

    rows.push({
      sourceType: m.sourceType,
      sourceId: m.sourceId,
      placeId: m.placeId,
      featureKey: FEATURE_KEY,
      wheelchairAccess: ok,
      wfcltId: m.wfcltId,
      evalRaw,
      distanceM: m.distanceM,
    })
  }

  await mkdir(OUT_DIR, { recursive: true })
  await writeFile(OUT_FILE, rows.map((r) => JSON.stringify(r)).join('\n') + '\n')

  const byType = { TOURAPI: 0, SBIZ: 0 }
  for (const r of rows) if (r.wheelchairAccess) byType[r.sourceType]++

  log('')
  log('  붙인 줄                  ' + matched.length)
  log('  기구표를 아직 안 받은 줄   ' + noEval)
  log('  산출물 줄                ' + rows.length)
  log('  🟢 규칙을 만족(true)      ' + yes + '  (TOURAPI ' + byType.TOURAPI + ' · SBIZ ' + byType.SBIZ + ')')
  log('  ⚪ 못 만족(false)         ' + no + '  ← 버리지 않고 낸다. 적재기가 거른다')
  log('')
  log('  항목 사전 (산출물에 실린 시설 기준):')
  for (const [k, v] of [...itemFreq.entries()].sort((a, b) => b[1] - a[1])) {
    log('    ' + String(v).padStart(3) + '  ' + k)
  }

  stamp(join(OUT_DIR, '_place-accessibility-run'), {
    tool: 'process/place-accessibility.mjs',
    output: 'data/staged/place-accessibility.ndjson',
    rule: { featureKey: FEATURE_KEY, accessItems: ACCESS_ITEMS },
    result: { matched: matched.length, noEval, rows: rows.length, yes, no, byType },
  })

  if (rows.length === 0) {
    log('🔴 0줄입니다. 빈 산출물을 성공으로 치지 않습니다.')
    process.exit(1)
  }
  log('만들기 완료')
}

main().catch((e) => { console.error('치명:', e?.stack || e); process.exit(1) })
