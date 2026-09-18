#!/usr/bin/env node
/**
 * 가로수 그늘 점수 — 구간별
 *
 * 만드는 것:
 *   data/staged/segment-shade.ndjson   구간 하나당 한 줄 (모든 구간. 모르면 null)
 *     · shadeDensity·shadeP  가중그루/m 과 부산 안 순위 — 장소 점수를 매길 때
 *     · treeShadeRatio       걷는 선이 덮이는 비율 0~1 — 건물 그림자와 합칠 때
 *   data/staged/_shade-summary.json    매칭 성공률·데이터 품질·불변식 결과
 *
 * 점수의 정의 (docs/WALKABILITY.md 2.1 이 준 방향 그대로):
 *
 *     shadeDensity = Σ(수종가중치 × 그루 수) ÷ 식재거리(m)      [가중그루/m]
 *
 *   🔴 "가로수가 있다/없다" 가 아니라 밀도다. 나무마다 그늘이 다르다는 것이
 *      이 데이터를 쓰는 이유이고, 수종 가중치는 config/tree-shade-weights.json 에 있다
 *      (코드에 숫자를 박지 않는다).
 *
 *   🔴 밀도로 만드는 이유가 하나 더 있다. 가로수 레코드는 도로 **구간**(시점→종점)
 *      단위인데 우리 구간은 OSM way 다. 둘의 경계가 안 맞는다. 그루 **수**를 나눠
 *      주려면 어느 way 에 몇 그루인지를 알아야 하는데 그건 모른다. 반면 **미터당
 *      밀도**는 같은 도로 위에서 대체로 유지된다고 볼 수 있어서, 모르는 것을
 *      지어내지 않고 붙일 수 있는 유일한 양이다.
 *
 *   🔴 백분위(shadeP)는 사람이 정한 상한이 아니라 **데이터가 정한다.** 매칭된
 *      구간들 안에서의 순위다. 절대 기준선을 손으로 적으면 그 줄이 그 순간 낡는다
 *      (CLAUDE.md 6절이 경사 기준선에 대해 말한 것과 같은 이유).
 *
 * ── 🔴 2026-09-18 — 건물 그림자와 합칠 수 있게 됐다 (S15P21E201-1221) ────────
 *
 * 이 자리에 *"다른 종류의 그늘이고, 합치는 방식은 아직 안 정해졌다"* 고 적혀 있었다.
 * 못 합친 진짜 이유는 종류가 아니라 **단위**였다 — 이쪽은 가중그루/m 과 백분위인데
 * 건물 그림자(`process/shadow.mjs`)는 **노면 그늘 비율 0~1** 이다. 자가 다르면 못 더한다.
 *
 *     treeShadeRatio = min(1, Σ(그루 수 × 수관폭m) ÷ 식재거리m × 보행로실효계수)
 *
 *   🔴 **`shadeDensity` 와 다른 양이다.** 수종 가중치 `w` 는 수관폭의 **제곱**에
 *      비례하는 넓이 프록시이고, 이쪽은 걷는 **선**이 덮이느냐라 폭을 그대로 더한다.
 *      둘 다 낸다 — 백분위는 순위에, 비율은 합치기에 쓴다.
 *
 *   🔴 **길 폭으로 나누지 않는다.** 걷는 사람에게 필요한 것은 노면 전체가 아니라
 *      걷는 선이고, 길 폭은 OSM 에 0.2% 밖에 없어 애초에 나눌 분모가 없다
 *      (`width` 태그 120/51,334 · `lanes` 2,734. 2026-09-18 실측).
 *
 *   🔴 **보행로실효계수는 잰 값이 아니라 정한 값이다.** 가로수는 보행자 편의가 아니라
 *      가로 경관·차도 분리를 위해 심은 것이라, 수관이 덮는 폭이 곧 머리 위 그늘이 아니다.
 *      숫자와 그 근거는 **`config/tree-shade-weights.json` 한 곳에만** 있다. 코드에 안 박는다.
 *
 *   🔴 **시각 차이는 그대로 남는다.** 가로수 그늘은 하루 종일 있고 건물 그림자는
 *      시각에 따라 뒤집힌다. 그래서 여기서 합치지 않고 **쓰는 쪽에서** 시각을 골라
 *      합친다 (`process/shade-route.mjs --trees`). 이 파일은 시각 없는 한 값만 낸다.
 *
 * ── 🔴 부산진구 67건은 못 살린다 — 파서를 고쳐도 안 된다 (S15P21E201-1229) ──
 *
 * 요약의 `칼럼밀림레코드` 를 보고 **"칼럼만 맞춰 주면 살아나겠네"** 로 읽기 쉽다.
 * 아니다. 2026-09-18 에 부산진구 67건을 전부 열어 **수종칸 38 × 67 = 2,546칸**을 셌다.
 *
 *     숫자가 든 칸        0      ← 그루 수가 한 칸도 없다
 *     total 이 숫자인 건  0
 *     글자가 든 칸        67     ← 전부 `metasequoia` 칸. 값은 랜드마크 이름("시영아파트")
 *     loc_nm · 좌표       67건 다 있다
 *     조사일자            전부 2025-12-11 (다른 구와 다르다)
 *
 * **제공처가 그루 수를 안 보냈다.** 칼럼 순서가 다른 것은 그 구가 **다른 모양의 엑셀**을
 * 냈다는 증거일 뿐, 우리가 고칠 자리가 아니다. 그래서 요약이 둘을 갈라 센다 —
 * `칼럼밀림레코드`(모양이 틀림)와 `그루수가아예없는레코드`(살릴 것이 없음).
 *
 * 🔴 **그래도 0 으로 채우지 않는다.** 채우면 부산진구가 통째로 "그늘 최악" 이 되어
 *    추천에서 밀린다. **「그늘 0」과 「모름」은 다른 것이다** — 그 구의 장소는 이 축이
 *    빠질 뿐이고, 그것이 맞는 동작이다.
 *
 * 🔴 **기장군 2건은 이것과 다르다.** 수종칸은 비었지만 `total` 이 있어(125·40)
 *    `etc_tree` 로 이미 살아난다. 처음에 이 둘도 못 살린다고 봤다가 재 보고 정정했다.
 *
 * 연제구 `reference_date` 가 `46000` 으로 온다 — 엑셀 일련번호가 글자로 넘어온 것이고
 * 읽으면 2025-12-09 라 이웃 구(12-10·12-11)와 앞뒤가 맞는다. 점수 계산에 안 쓰는 칸이라
 * **고치지 않고 여기 적어만 둔다.**
 *
 * 🔴 이것은 선호 가중치가 아니다. "그늘이 얼마나 많은가" 만 낸다.
 *    "그늘 있는 길을 얼마나 더 좋아하는가" 는 짝 비교 실험이 정한다 (docs/FIELD-STUDY.md).
 *
 * 실행:
 *   node process/shade.mjs
 *
 * 종료 코드: 0 성공 / 2 입력 없음 / 1 불변식 깨짐
 */
import { createReadStream, existsSync } from 'node:fs'
import { readFile, writeFile, mkdir } from 'node:fs/promises'
import readline from 'node:readline'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'
import { stamp } from '../mlops/manifest.mjs'
import { log } from '../lib/log.mjs'

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..')
const IN_TREES = join(ROOT, 'data/raw/trees/street-trees.ndjson')
const IN_WEIGHTS = join(ROOT, 'config/tree-shade-weights.json')
const IN_SEGS = join(ROOT, 'data/staged/segment-slope.ndjson')
const GEOM = ['road', 'walk'].map((f) => join(ROOT, `data/raw/pbf/${f}.ndjson`))
const OUT_SEG = join(ROOT, 'data/staged/segment-shade.ndjson')
const OUT_SUM = join(ROOT, 'data/staged/_shade-summary.json')


/** 가로수 레코드의 비수종 칼럼. 나머지 38개가 수종이다. */
const NON_SPECIES = new Set(['loc_nm', 'sec_timepoint', 'sec_endpoint', 'plant_distance', 'reference_date', 'lat', 'lng', 'total', 'gugun'])

const GUGUN = ['중구', '서구', '동구', '영도구', '부산진구', '동래구', '남구', '북구', '해운대구', '사하구', '금정구', '강서구', '연제구', '수영구', '사상구', '기장군']

/**
 * ── 도로명 정규화 규칙 ────────────────────────────────────────────────────
 *
 * 가로수 쪽 `loc_nm` 은 표기가 섞여 있다. 실측(835건): 587건이 "부산광역시 OO구 OO로",
 * 248건이 "OO로" 로만 온다. OSM 쪽 `name` 은 언제나 도로명만 있다.
 * 그래서 양쪽을 같은 모양으로 깎는다.
 *
 *   1. NFC 정규화 + 앞뒤 공백 제거
 *      (한글은 같은 글자를 조합형/완성형 두 가지로 적을 수 있어 눈에 안 보이는 차이가 난다)
 *   2. 맨 앞의 "부산광역시" 제거
 *   3. 그 다음에 오는 구·군 이름 하나 제거 (16개 목록)
 *   4. 괄호 주석 제거 — "녹산산업로(이면도로)", "명지국제로(1-1)", "가락대로(가락동)"
 *   5. 남은 공백 전부 제거 — "차성로 436번길" 과 "차성로436번길" 이 같은 길이다
 *
 * 🔴 그리고 **정확히 일치할 때만 매칭한다.** 접두사 일치를 쓰지 않는다.
 *    "중앙대로" 와 "중앙대로123번길" 은 **다른 길**이다. 번길은 본선에서 갈라져
 *    나온 이면도로이고, 본선의 가로수를 거기 붙이면 없는 그늘이 생긴다.
 *    그럴듯한 매칭이 이 파트에서 가장 나쁜 실패다.
 */
function canonRoad(s) {
  if (!s) return ''
  let t = String(s).normalize('NFC').trim()
  t = t.replace(/^부산광역시\s*/, '')
  for (const g of GUGUN) if (t.startsWith(g)) { t = t.slice(g.length); break }
  t = t.replace(/\([^)]*\)/g, '')
  return t.replace(/\s+/g, '')
}

/**
 * ── 식재거리 단위 규칙 ────────────────────────────────────────────────────
 *
 * 🔴 `plant_distance` 의 단위가 레코드마다 다르다. 실측(835건): 값이 0.12~41,195 에
 *    걸쳐 있고, 작은 값들("중앙대로332번길 = 0.12")은 km, 큰 값들("홍곡남로 = 680")은
 *    m 로 읽어야 앞뒤가 맞는다. 하나의 단위로 읽으면 어느 쪽이든 1000배가 틀린다.
 *
 * 규칙: 쉼표를 떼고 숫자로 읽은 뒤, **50 미만이면 km, 50 이상이면 m** 로 본다.
 *   경계를 50 으로 둔 이유 — 도로 구간을 m 로 적었을 때 50m 미만인 경우는 사실상
 *   없고, km 로 적었을 때 50km 를 넘는 경우도 부산 안에서는 없다. 두 분포가
 *   겹치지 않는 구간에 경계를 놓은 것이다.
 *
 * 🔴 이 규칙을 **혼자 믿지 않는다.** 서로 독립인 신호 둘로 매번 다시 검사한다.
 *   (a) 표기 신호 — 소수점이 있으면 km 로 적은 것이다("5.3", "0.12"). 이건 한쪽
 *       방향으로만 성립한다: 소수점이 있으면 반드시 km 지만, km 를 정수로 적은
 *       것("성남로=2")도 있어서 소수점이 없다고 m 인 것은 아니다.
 *       그래서 검사하는 명제는 **"소수점이 있는데 50 이상인 값은 없다"** 하나다.
 *       실측 835건에서 그런 값은 0건이었고 — 즉 50 이라는 경계가 소수(km) 분포와
 *       정수(m) 분포 사이의 빈 공간에 놓여 있다. 하나라도 생기면 경계가 더 이상
 *       두 분포를 가르지 못한다는 뜻이므로 종료 코드 1 이다
 *       (`distanceUnitCheck.소수점인데m으로읽힘`).
 *       경계 아래의 정수("성남로=2" → 2km)는 모호한 채로 km 로 읽고, 몇 건인지를
 *       `경계아래정수` 로 남긴다 — 2m 짜리 가로수 구간은 없기 때문이다.
 *   (b) 길이 신호 — 식재거리 ÷ 매칭된 OSM 도로 길이. 규칙이 맞으면 km 표기
 *       그룹과 m 표기 그룹의 중앙 비율이 같은 자릿수에 있어야 한다
 *       (`distanceUnitCheck`). 1000배 벌어지면 규칙이 틀린 것이고 종료 코드 1 이다.
 *
 * 값이 없으면 null 을 돌려준다. 🔴 0 이나 추정값으로 채우지 않는다 — 0 이면
 * 나눗셈이 무너지고, 추정값이면 없는 근거가 생긴다.
 */
function plantDistanceM(raw) {
  if (raw == null || raw === '') return { m: null, unit: null, hasDot: false }
  const s = String(raw).replace(/,/g, '').trim()
  const v = Number(s)
  if (!Number.isFinite(v) || v <= 0) return { m: null, unit: null, hasDot: false }
  const hasDot = s.includes('.')
  return v < 50 ? { m: v * 1000, unit: 'km', hasDot } : { m: v, unit: 'm', hasDot }
}

/**
 * ── 물리 타당성 상한 ──────────────────────────────────────────────────────
 *
 * 가로수는 보통 6~8m 간격으로 심는다. 양쪽에 심어도 미터당 0.3그루 남짓이고,
 * 실측 분포도 중앙 0.12 · p90 0.24 그루/m 였다. **미터당 1그루를 넘으면**
 * 그루 수와 식재거리가 서로 안 맞는 것이다 (제공처가 2.63km 를 263 으로 적은
 * 것으로 보이는 행이 실제로 있다).
 *
 * 🔴 그렇다고 값을 깎지 않는다. 무엇이 틀렸는지 모르는 채로 깎으면 그럴듯한
 *    숫자가 된다. 표시만 하고(`implausible`) 개수를 요약에 남긴다 — 쓰는 쪽이
 *    거르든 말든 정하게.
 */
const TREES_PER_M_MAX = 1.0

const inBusan = (la, lo) => Number.isFinite(la) && Number.isFinite(lo) && la >= 34.8 && la <= 35.5 && lo >= 128.7 && lo <= 129.4

function haversine(a, b, c, d) {
  const R = 6371000, t = Math.PI / 180
  const dφ = (c - a) * t, dλ = (d - b) * t, φ1 = a * t, φ2 = c * t
  const h = Math.sin(dφ / 2) ** 2 + Math.cos(φ1) * Math.cos(φ2) * Math.sin(dλ / 2) ** 2
  return 2 * R * Math.asin(Math.sqrt(h))
}

const quantile = (sorted, q) => sorted.length ? sorted[Math.min(sorted.length - 1, Math.floor(sorted.length * q))] : null

function die(code, msg) { log(msg); process.exit(code) }

async function* ndjson(file) {
  const rl = readline.createInterface({ input: createReadStream(file), crlfDelay: Infinity })
  for await (const line of rl) if (line.trim()) yield JSON.parse(line)
}

async function main() {
  for (const [f, hint] of [[IN_TREES, 'node collect/street-trees.mjs'], [IN_WEIGHTS, '이 파일이 저장소에 있어야 합니다'], [IN_SEGS, 'npm run slope']])
    if (!existsSync(f)) die(2, `🔴 입력 없음: ${f}\n   먼저: ${hint}`)

  const weightsDoc = JSON.parse(await readFile(IN_WEIGHTS, 'utf8'))
  const W = weightsDoc.weights

  // 🔴 보행로실효계수는 설정에서만 온다. 없으면 지어내지 않고 멈춘다 (S15P21E201-1221).
  const WALK_SHARE = weightsDoc['보행로실효계수']
  if (!(typeof WALK_SHARE === 'number' && WALK_SHARE > 0 && WALK_SHARE <= 1))
    die(2, `🔴 config/tree-shade-weights.json 에 '보행로실효계수'(0~1)가 없습니다.\n   덮는 비율을 이 값 없이 내면 그 숫자는 코드에 박힌 가정이 됩니다.`)

  // 수관폭 — "15~20" 같은 범위 표기의 중앙을 쓴다. 없는 수종은 전체 중앙값으로 대신한다.
  const canopyMid = (s) => { const m = String(s ?? '').match(/([\d.]+)\s*~\s*([\d.]+)/); return m ? (+m[1] + +m[2]) / 2 : null }
  const canopyKnown = Object.fromEntries(
    Object.entries(W).map(([k, v]) => [k, canopyMid(v['수관폭m'])]).filter(([, c]) => c != null))
  const canopyVals = Object.values(canopyKnown).sort((a, b) => a - b)
  if (!canopyVals.length) die(1, '🔴 수관폭을 하나도 못 읽었습니다. 덮는 비율을 낼 수 없습니다.')
  const CANOPY_FALLBACK = canopyVals[canopyVals.length >> 1]
  const canopyOf = (k) => canopyKnown[k] ?? CANOPY_FALLBACK
  const canopyGuessed = Object.keys(W).filter((k) => !(k in canopyKnown))

  // ── 1. 원문에서 레코드 복원 ─────────────────────────────────────────────
  const records = []
  for await (const page of ndjson(IN_TREES)) {
    const body = JSON.parse(page.raw)?.response?.body
    const it = body?.items?.item
    if (Array.isArray(it)) records.push(...it)
    else if (it) records.push(it)
  }
  if (!records.length) die(2, '🔴 원문에서 레코드를 하나도 못 읽었습니다.')
  log(`가로수 레코드 ${records.length}건`)

  const speciesCols = Object.keys(records[0]).filter((k) => !NON_SPECIES.has(k))
  const missingWeights = speciesCols.filter((k) => !(k in W))
  const extraWeights = Object.keys(W).filter((k) => !speciesCols.includes(k))
  if (missingWeights.length)
    die(1, `🔴 가중치가 없는 수종 칼럼: ${missingWeights.join(', ')}\n   config/tree-shade-weights.json 이 실제 응답과 어긋납니다. 지어내지 않고 멈춥니다.`)

  // ── 2. 레코드 정리 ─────────────────────────────────────────────────────
  const q = {
    columnShift: 0, shiftExamples: [],       // 제공처 스프레드시트 칼럼 밀림
    noCounts: 0, noCountsByGugun: {},        // 그중 살릴 그루 수가 아예 없는 것 (S15P21E201-1229)
    totalIsFormula: 0, totalEmpty: 0, totalMismatch: 0, totalMismatchExamples: [],
    noDistance: 0, distKm: 0, distM: 0, dotButM: 0, intBelowBoundary: 0,
    badCoord: 0, badCoordExamples: [],
    totalOnlyNoSpecies: 0, totalOnlyExamples: [],
    implausible: 0, implausibleExamples: [],
  }
  const perSpecies = Object.fromEntries(speciesCols.map((k) => [k, 0]))
  const clean = []

  for (const r of records) {
    // 🔴 제공처가 16개 구·군 엑셀을 합치면서 일부 행의 칼럼이 밀렸다.
    //    수종 칼럼에 "시영아파트" 같은 지명이 들어 있는 행이 그것이다.
    //    이런 행은 그루 수가 통째로 없다 — 0 으로 읽으면 "나무가 없는 길" 이
    //    되어 버리므로 점수를 만들지 않고 제외하고 개수를 남긴다.
    const shifted = speciesCols.filter((k) => r[k] != null && r[k] !== '' && !Number.isFinite(Number(r[k])))
    if (shifted.length) {
      q.columnShift++
      if (q.shiftExamples.length < 5) q.shiftExamples.push({ loc_nm: r.loc_nm, 칼럼: shifted[0], 값: String(r[shifted[0]]) })

      // 🔴 **「칼럼이 밀렸다」와 「그루 수가 없다」는 다른 것이다** (S15P21E201-1229).
      //    앞은 우리가 고치면 살아난다는 뜻으로 읽히고, 뒤는 **원본에 없다**는 뜻이다.
      //    이 자리에서 실제로 살릴 것이 있는지를 함께 세지 않으면, 다음 사람이 파서를
      //    고치러 들어갔다가 아무것도 못 살리고 나온다 — 실제로 그 일이 있었다.
      const anyCount = speciesCols.some((k) => Number(r[k]) > 0) || Number(String(r.total ?? '').replace(/,/g, '')) > 0
      if (!anyCount) {
        q.noCounts++
        const g = String(r.gugun ?? '(모름)').replace(/^부산광역시\s*/, '').trim()
        q.noCountsByGugun[g] = (q.noCountsByGugun[g] ?? 0) + 1
      }
      continue
    }

    const counts = {}
    let trees = 0, weighted = 0, canopyM = 0
    for (const k of speciesCols) {
      const n = r[k] == null || r[k] === '' ? 0 : Number(r[k])   // 빈 값 = 그 수종 0그루
      if (n < 0 || !Number.isFinite(n)) die(1, `🔴 음수/비수치 그루 수: ${r.loc_nm} ${k}=${r[k]}`)
      if (n > 0) counts[k] = n
      trees += n
      weighted += n * W[k].w
      // 🔴 `weighted` 와 다른 양이다. `w` 는 수관폭의 **제곱**에 비례하는 넓이 프록시고,
      //    이쪽은 걷는 **선**이 얼마나 덮이는지라 폭을 그대로 더한다 (S15P21E201-1221).
      canopyM += n * canopyOf(k)
      perSpecies[k] += n
    }

    // 🔴 API 의 `total` 을 믿지 않는다. 실측상 87건이 엑셀 수식 문자열("SUM(J6:AU6)")로
    //    오고, 숫자인 것 중에도 수종합과 다른 것이 있다. 그루 수는 **수종 칼럼에서
    //    다시 더한다.** total 은 품질 지표로만 센다.
    const rawTotal = r.total
    if (rawTotal == null || rawTotal === '') q.totalEmpty++
    else if (!Number.isFinite(Number(rawTotal))) q.totalIsFormula++
    else if (Number(rawTotal) !== trees) {
      q.totalMismatch++
      if (q.totalMismatchExamples.length < 5) q.totalMismatchExamples.push({ loc_nm: r.loc_nm, total: Number(rawTotal), 수종합: trees })
    }

    // 🔴 총계는 있는데 수종 내역이 통째로 비어 있는 행이 있다 (실측 2건).
    //    0 그루로 읽으면 "그늘 없는 길" 이 되어 틀린다. 무슨 나무인지 모르므로
    //    '기타 수종' 가중치를 쓴다 — 그 값이 정확히 "모르는 나무" 를 위해 있다.
    const rawTotalN = Number(rawTotal)
    if (trees === 0 && Number.isFinite(rawTotalN) && rawTotalN > 0) {
      trees = rawTotalN
      weighted = rawTotalN * W.etc_tree.w
      canopyM = rawTotalN * canopyOf('etc_tree')
      perSpecies.etc_tree += rawTotalN
      q.totalOnlyNoSpecies++
      if (q.totalOnlyExamples.length < 5) q.totalOnlyExamples.push({ loc_nm: r.loc_nm, total: rawTotalN })
    }

    const { m: distM, unit, hasDot } = plantDistanceM(r.plant_distance)
    if (distM == null) q.noDistance++
    else {
      if (unit === 'km') q.distKm++; else q.distM++
      // 독립 신호 대조. 검사하는 명제는 하나뿐이다 — "소수점이 있는데 m 으로 읽힌 값" 은 없어야 한다.
      if (hasDot && unit === 'm') q.dotButM++
      if (!hasDot && unit === 'km') q.intBelowBoundary++
    }

    // 물리 타당성. 값을 고치지 않고 표시만 한다.
    const treesPerM = distM ? trees / distM : null
    const implausible = treesPerM != null && treesPerM > TREES_PER_M_MAX
    if (implausible) {
      q.implausible++
      if (q.implausibleExamples.length < 6) q.implausibleExamples.push({ loc_nm: r.loc_nm, 그루: trees, 식재거리원문: String(r.plant_distance), 그루당m: Number((1 / treesPerM).toFixed(2)) })
    }

    const la = Number(r.lat), lo = Number(r.lng)
    const coordOk = inBusan(la, lo)
    if (!coordOk) {
      q.badCoord++
      if (q.badCoordExamples.length < 5) q.badCoordExamples.push({ loc_nm: r.loc_nm, lat: r.lat, lng: r.lng })
    }

    clean.push({
      road: canonRoad(r.loc_nm), rawLoc: r.loc_nm, gugun: r.gugun,
      from: r.sec_timepoint, to: r.sec_endpoint,
      trees, weighted, canopyM, counts, distM, distUnit: unit, implausible,
      lat: coordOk ? la : null, lng: coordOk ? lo : null,
    })
  }

  // 🔴 불변식: 같은 행렬을 두 방향으로 더한 값이 같아야 한다.
  //    (레코드별 합계의 합) == (수종별 합계의 합). 어긋나면 파싱에서 뭔가 샜다.
  const sumByRecord = clean.reduce((a, c) => a + c.trees, 0)
  const sumBySpecies = Object.values(perSpecies).reduce((a, b) => a + b, 0)
  if (sumByRecord !== sumBySpecies)
    die(1, `🔴 그루 수 합계 불일치: 레코드별 합 ${sumByRecord} vs 수종별 합 ${sumBySpecies}`)
  log(`쓸 수 있는 레코드 ${clean.length}건 / 그루 수 ${sumByRecord.toLocaleString()}그루 (칼럼 밀림 ${q.columnShift}건 제외)`)

  // ── 3. 구간 읽기 ───────────────────────────────────────────────────────
  const segs = []
  const byName = new Map()
  for await (const s of ndjson(IN_SEGS)) {
    segs.push({ id: s.id, name: s.name ?? null, length: s.length })
    if (!s.name) continue
    const c = canonRoad(s.name)
    if (!byName.has(c)) byName.set(c, [])
    byName.get(c).push(segs.length - 1)
  }
  const named = segs.filter((s) => s.name).length
  log(`구간 ${segs.length}개 (이름 있음 ${named} / 유니크 도로명 ${byName.size})`)

  // 구간 대표점 — PBF 지오메트리의 평균. 좌표 검증에만 쓴다.
  const centroid = new Map()
  for (const f of GEOM) {
    if (!existsSync(f)) continue
    for await (const w of ndjson(f)) {
      const g = w.geometry
      if (!g?.length) continue
      let la = 0, lo = 0
      for (const p of g) { la += p.lat; lo += p.lon }
      centroid.set(w.id, [la / g.length, lo / g.length])
    }
  }
  log(`구간 대표점 ${centroid.size}개`)

  // ── 4. 매칭 ────────────────────────────────────────────────────────────
  /**
   * 🔴 이름이 같은 구간이 부산 전역에 흩어져 있다 (예: 같은 도로명의 way 가 최대 192개).
   *    가로수 레코드는 그중 **한 구간**(시점→종점)을 가리키는데 시점·종점이 지명이라
   *    좌표로 풀 수가 없다. 그래서 레코드의 대표 좌표를 반경으로 써서 잘라낸다.
   *
   *      반경 = max(700m, 식재거리/2 + 300m)
   *
   *    식재거리가 그 구간의 길이이므로 대표점에서 구간 끝까지는 대략 그 절반이다.
   *    대표점이 정확히 중점이 아닌 것을 300m 로 감안하고, 식재거리가 없는 레코드를
   *    위해 하한 700m 를 뒀다. 반경 밖의 동일명 구간은 **다른 동네의 같은 이름**으로
   *    보고 붙이지 않는다.
   *
   *    좌표가 부산 밖인 레코드(제공처 오기)는 반경을 못 쓴다. 그때는 동일명 구간
   *    전부에 붙이고 `coordFiltered: false` 로 표시한다 — 신뢰도가 낮다는 뜻이다.
   */
  const attach = new Map()          // segIdx -> {weighted, distM}[]
  let matchedRecs = 0, unmatchedRecs = 0
  const unmatchedExamples = []
  const unmatchedTrees = { count: 0 }
  const nearestDists = []
  let recsWithoutCoordFilter = 0
  const ratioKm = [], ratioM = []

  for (const c of clean) {
    const idxs = byName.get(c.road)
    if (!idxs?.length) {
      unmatchedRecs++
      unmatchedTrees.count += c.trees
      if (unmatchedExamples.length < 15) unmatchedExamples.push({ loc_nm: c.rawLoc, 그루: c.trees })
      continue
    }
    const useCoord = c.lat != null
    const R = Math.max(700, (c.distM ?? 0) / 2 + 300)
    let picked = [], best = Infinity
    for (const i of idxs) {
      const cen = centroid.get(segs[i].id)
      if (!useCoord || !cen) { picked.push(i); continue }
      const d = haversine(c.lat, c.lng, cen[0], cen[1])
      if (d < best) best = d
      if (d <= R) picked.push(i)
    }
    if (useCoord && Number.isFinite(best)) nearestDists.push(best)
    if (!useCoord) recsWithoutCoordFilter++

    if (!picked.length) {
      // 이름은 있는데 반경 안에 하나도 없다 = 다른 동네의 동명 도로였다.
      unmatchedRecs++
      unmatchedTrees.count += c.trees
      if (unmatchedExamples.length < 15) unmatchedExamples.push({ loc_nm: c.rawLoc, 그루: c.trees, 사유: `동일명 구간이 반경 ${Math.round(R)}m 밖에만 있음` })
      continue
    }
    matchedRecs++

    // 단위 규칙 점검용: 식재거리 ÷ (붙은 구간들의 OSM 길이 합)
    if (c.distM != null) {
      const osmLen = picked.reduce((a, i) => a + (segs[i].length || 0), 0)
      if (osmLen > 0) (c.distUnit === 'km' ? ratioKm : ratioM).push(c.distM / osmLen)
    }

    // 🔴 식재거리가 없으면 붙은 구간들의 OSM 길이 합으로 대신한다.
    //    출처가 다르므로 산출물에 distanceSource 로 표시한다.
    let denom = c.distM, src = 'api'
    if (denom == null) {
      denom = picked.reduce((a, i) => a + (segs[i].length || 0), 0)
      src = 'osm'
    }
    if (!(denom > 0)) continue          // 🔴 0 으로 나누지 않는다
    // 🔴 거리를 OSM 으로 대신한 경우에도 물리 타당성을 다시 본다 — 짧은 way 하나에
    //    큰 레코드가 붙으면 밀도가 터진다.
    const impl = c.implausible || c.trees / denom > TREES_PER_M_MAX
    for (const i of picked) {
      if (!attach.has(i)) attach.set(i, [])
      attach.get(i).push({ weighted: c.weighted, canopyM: c.canopyM, denom, src, coordFiltered: useCoord, implausible: impl })
    }
  }

  // ── 5. 구간별 점수 ─────────────────────────────────────────────────────
  const densities = []
  const ratios = []
  const out = segs.map((s, i) => {
    const rs = attach.get(i)
    if (!rs?.length) return { id: s.id, name: s.name, matched: false, shadeDensity: null, shadeP: null, treeShadeRatio: null }
    // 여러 레코드가 같은 구간에 붙으면 길이가중 평균 밀도 = Σ가중그루 / Σ거리
    const wsum = rs.reduce((a, r) => a + r.weighted, 0)
    const dsum = rs.reduce((a, r) => a + r.denom, 0)
    const density = dsum > 0 ? wsum / dsum : null
    if (density != null) densities.push(density)
    // 🔴 걷는 선이 덮이는 비율 0~1 (S15P21E201-1221). 백분위와 달리 **절대량**이라
    //    건물 그림자(segment-shadow.ndjson)와 같은 자로 잴 수 있다.
    const csum = rs.reduce((a, r) => a + (r.canopyM ?? 0), 0)
    const ratio = dsum > 0 ? Math.min(1, (csum / dsum) * WALK_SHARE) : null
    if (ratio != null) ratios.push(ratio)
    return {
      id: s.id, name: s.name, matched: true,
      shadeDensity: density == null ? null : Number(density.toFixed(5)),
      shadeP: null,
      treeShadeRatio: ratio == null ? null : Number(ratio.toFixed(4)),
      records: rs.length,
      distanceSource: rs.every((r) => r.src === 'api') ? 'api' : rs.every((r) => r.src === 'osm') ? 'osm' : 'mixed',
      coordFiltered: rs.every((r) => r.coordFiltered),
      // 🔴 원본의 그루 수와 식재거리가 물리적으로 안 맞는 레코드가 섞였다는 표시.
      //    값을 깎지 않았으니 쓰는 쪽이 거를 수 있게 남긴다.
      implausible: rs.some((r) => r.implausible),
    }
  })

  const sortedD = [...densities].sort((a, b) => a - b)
  const pctOf = (v) => {
    let lo = 0, hi = sortedD.length
    while (lo < hi) { const m = (lo + hi) >> 1; if (sortedD[m] < v) lo = m + 1; else hi = m }
    return Number((100 * lo / Math.max(1, sortedD.length - 1)).toFixed(1))
  }
  for (const o of out) if (o.shadeDensity != null) o.shadeP = Math.min(100, pctOf(o.shadeDensity))

  await mkdir(dirname(OUT_SEG), { recursive: true })
  await writeFile(OUT_SEG, out.map((o) => JSON.stringify(o)).join('\n') + '\n')

  // ── 6. 요약 ────────────────────────────────────────────────────────────
  const matchedSegs = out.filter((o) => o.matched).length
  const med = (a) => { const s = [...a].sort((x, y) => x - y); return s.length ? Number(quantile(s, 0.5).toFixed(3)) : null }
  const nd = [...nearestDists].sort((a, b) => a - b)

  const topSpecies = Object.entries(perSpecies).filter(([, n]) => n > 0).sort((a, b) => b[1] - a[1])
    .map(([k, n]) => ({ 수종: W[k].ko, col: k, 그루: n, w: W[k].w }))

  const summary = {
    step: 'process/shade',
    at: new Date().toISOString(),
    입력: {
      가로수레코드: records.length,
      쓸수있는레코드: clean.length,
      그루수합계: sumByRecord,
      구간: segs.length,
      이름있는구간: named,
    },
    덮는비율: {
      '//': '걷는 선이 나뭇잎에 덮이는 비율 0~1 (treeShadeRatio). 백분위(shadeP)와 달리 절대량이라 건물 그림자(process/shadow.mjs 의 shadowRatio)와 같은 자로 잰다 — 그래서 둘을 합칠 수 있다.',
      '//식': 'Σ(그루 수 x 수관폭m) / 식재거리m x 보행로실효계수. 1 을 넘으면 1 로 자른다.',
      보행로실효계수: WALK_SHARE,
      '//계수출처': 'config/tree-shade-weights.json 이 정한다. 코드에 박지 않는다 — 현장 실측으로 바꿀 값이다.',
      대상구간: ratios.length,
      최소: ratios.length ? Number(Math.min(...ratios).toFixed(3)) : null,
      중앙: med(ratios),
      최대: ratios.length ? Number(Math.max(...ratios).toFixed(3)) : null,
      '1.0에포화된구간': ratios.filter((v) => v >= 0.999).length,
      '수관폭을중앙값으로대신한수종': canopyGuessed.length,
      '수관폭대신값m': CANOPY_FALLBACK,
      '//포화': '🔴 포화가 늘면 계수가 크다는 뜻이다. 포화된 구간은 서로 구별이 안 되므로 순위에서 같은 값이 된다.',
    },
    매칭: {
      성공레코드: matchedRecs,
      실패레코드: unmatchedRecs,
      성공률: Number((100 * matchedRecs / clean.length).toFixed(1)),
      매칭된구간: matchedSegs,
      매칭된구간비율: Number((100 * matchedSegs / segs.length).toFixed(1)),
      전체구간대비이름있는구간중: Number((100 * matchedSegs / Math.max(1, named)).toFixed(1)),
      좌표필터를못쓴레코드: recsWithoutCoordFilter,
      '실패한레코드의그루수': unmatchedTrees.count,
      실패예시: unmatchedExamples,
      '//정규화규칙': '부산광역시 → 구·군 → 괄호주석 제거 후 공백 전부 제거. 정확히 일치할 때만 매칭한다 — "중앙대로" 와 "중앙대로123번길" 은 다른 길이다.',
      '//좌표반경': 'max(700m, 식재거리/2 + 300m). 반경 밖의 동일명 구간은 다른 동네의 같은 이름으로 보고 붙이지 않는다.',
    },
    좌표검증: {
      '//': '이름으로 찾은 도로가 맞는 도로인지를 독립적으로 재는 값. 가로수 레코드의 대표 좌표에서 동일명 구간까지의 최단거리다. 이름 매칭이 엉뚱한 도로를 잡았다면 이 값이 커진다.',
      대상레코드: nd.length,
      최단거리m: { p10: Math.round(quantile(nd, 0.1) ?? 0), 중앙: Math.round(quantile(nd, 0.5) ?? 0), p90: Math.round(quantile(nd, 0.9) ?? 0), 최대: Math.round(nd[nd.length - 1] ?? 0) },
      '500m이내': nd.filter((d) => d <= 500).length,
      '1km이내': nd.filter((d) => d <= 1000).length,
    },
    데이터품질: {
      '//': '🔴 제공처 원본이 16개 구·군 엑셀을 합친 것이라 정합성이 낮다. 숨기지 않고 센다.',
      칼럼밀림레코드: q.columnShift,
      칼럼밀림예시: q.shiftExamples,
      '//살릴것이있나': '🔴 칼럼밀림은 「우리가 고치면 살아난다」로 읽히는데, 그 안에 살릴 그루 수가 아예 없는 것이 섞여 있다. 그건 파서 문제가 아니라 제공처가 안 보낸 것이다. 둘을 갈라 센다 (S15P21E201-1229).',
      '그루수가아예없는레코드': q.noCounts,
      '그루수없음_구별': q.noCountsByGugun,
      '//못살린다': '위 구는 가로수 그늘 자료가 0 이다. 🔴 0 으로 채우지 않는다 — 그 구가 통째로 「그늘 최악」이 되어 추천에서 밀린다. 「그늘 0」과 「모름」은 다른 것이다 (config/tree-shade-weights.json 의 결측과-0의-구별).',
      'total이엑셀수식문자열': q.totalIsFormula,
      'total이빈값': q.totalEmpty,
      'total이수종합과다름': q.totalMismatch,
      total불일치예시: q.totalMismatchExamples,
      '//total': 'total 을 쓰지 않고 수종 칼럼에서 다시 더했다. 위 숫자는 그 판단의 근거다.',
      '총계만있고수종내역없음': q.totalOnlyNoSpecies,
      총계만있는레코드예시: q.totalOnlyExamples,
      '//총계만': "무슨 나무인지 모르므로 '기타 수종' 가중치로 계산했다. 0 그루로 읽으면 '그늘 없는 길' 이 되어 틀린다.",
      식재거리없음: q.noDistance,
      '식재거리km표기': q.distKm,
      '식재거리m표기': q.distM,
      물리적으로안맞는레코드: q.implausible,
      '//물리': `그루 수 ÷ 식재거리 가 ${TREES_PER_M_MAX}그루/m 를 넘는 행. 가로수 간격은 보통 6~8m 이고 실측 중앙값도 0.12그루/m 라, 이 행들은 제공처가 거리를 잘못 적은 것으로 보인다. 🔴 값을 깎지 않고 산출물에 implausible:true 로만 표시했다.`,
      물리불일치예시: q.implausibleExamples,
      부산범위밖좌표: q.badCoord,
      부산범위밖좌표예시: q.badCoordExamples,
    },
    distanceUnitCheck: {
      '//': '식재거리 단위 규칙(50 미만이면 km)이 맞는지를 매번 다시 재는 값. 식재거리 ÷ 붙은 OSM 구간 길이 합. 규칙이 맞으면 km 표기 그룹과 m 표기 그룹의 중앙값이 같은 자릿수에 있어야 한다. 1000배 벌어지면 규칙이 틀린 것이다.',
      'km표기그룹_중앙비율': med(ratioKm),
      'm표기그룹_중앙비율': med(ratioM),
      표본: { km: ratioKm.length, m: ratioM.length },
      '//표기신호': '독립 신호 대조. 소수점이 있으면 반드시 km 다. 그런 값이 50 이상으로 읽혔다면 경계가 소수(km) 분포와 정수(m) 분포를 더 이상 못 가른다는 뜻이라 종료 코드 1 이다.',
      '소수점인데m으로읽힘': q.dotButM,
      경계아래정수: q.intBelowBoundary,
      '//경계아래정수': '"성남로=2" 처럼 소수점 없이 50 미만인 값. 표기만으로는 단위를 모르지만 2m 짜리 가로수 구간은 없으므로 km 로 읽었다. 모호한 채로 읽었다는 사실을 지우지 않는다.',
    },
    점수: {
      '//': 'shadeDensity = Σ(수종가중치 × 그루수) ÷ 식재거리(m). 단위는 가중그루/m. shadeP 는 매칭된 구간 안에서의 백분위이며 데이터가 정한다 — 손으로 적은 기준선이 아니다.',
      매칭된구간수: sortedD.length,
      '물리적으로안맞는레코드가붙은구간': out.filter((o) => o.implausible).length,
      밀도: { min: Number((sortedD[0] ?? 0).toFixed(4)), p25: Number((quantile(sortedD, 0.25) ?? 0).toFixed(4)), 중앙: Number((quantile(sortedD, 0.5) ?? 0).toFixed(4)), p75: Number((quantile(sortedD, 0.75) ?? 0).toFixed(4)), p90: Number((quantile(sortedD, 0.9) ?? 0).toFixed(4)), max: Number((sortedD[sortedD.length - 1] ?? 0).toFixed(4)) },
    },
    수종: {
      '//': '가중치는 config/tree-shade-weights.json 이 정한다. 전부 추정값이며 근거가 그 파일에 한 줄씩 적혀 있다.',
      가중치파일에만있는칼럼: extraWeights,
      그루수순: topSpecies,
    },
    '못한것': [
      '가로수 레코드의 시점→종점을 좌표로 풀지 못했다. 지명이라 지오코딩이 필요하고, 그래서 한 레코드가 같은 도로명의 여러 OSM 구간에 같은 밀도로 붙는다. 구간 안에서 그늘이 어디서 끊기는지는 모른다.',
      '수종 가중치는 전부 문헌상 수관폭·수고에서 유도한 추정이다. 부산 현장 실측이 아니다.',
      '칼럼이 밀린 레코드는 그루 수가 통째로 없어 복원하지 못했다. 제공처 원본을 고쳐야 한다.',
      '그루 수와 식재거리가 물리적으로 안 맞는 레코드를 고치지 않고 표시만 했다 (implausible). 무엇이 틀렸는지 모르는 채로 깎으면 그럴듯한 숫자가 되기 때문이다.',
      '건물 그림자(segment-shadow.ndjson)와 합치지 않았다. 다른 종류의 그늘이고 합치는 방식이 아직 안 정해졌다.',
    ],
  }
  await writeFile(OUT_SUM, JSON.stringify(summary, null, 1))

  stamp(join(ROOT, 'data/staged/_shade-run'), {
    step: 'process/shade',
    inputs: [IN_TREES, IN_WEIGHTS, IN_SEGS],
    params: { 반경: 'max(700, distM/2+300)', 단위경계: 50, 매칭: '정규화 후 정확일치' },
    result: { 매칭레코드: matchedRecs, 매칭구간: matchedSegs, 그루수: sumByRecord },
  })

  log(`매칭 ${matchedRecs}/${clean.length}건 (${summary.매칭.성공률}%) → 구간 ${matchedSegs}개`)
  log(`좌표검증: 동일명 구간까지 중앙 ${summary.좌표검증.최단거리m.중앙}m, 500m 이내 ${summary.좌표검증['500m이내']}/${nd.length}`)
  log(`저장: ${OUT_SEG}`)
  log(`저장: ${OUT_SUM}`)

  // ── 7. 불변식 ──────────────────────────────────────────────────────────
  const bad = []
  if (out.length !== segs.length) bad.push(`출력 줄 수 ${out.length} != 구간 수 ${segs.length}`)
  if (matchedSegs === 0) bad.push('매칭된 구간이 0개다')
  for (const o of out) {
    if (o.shadeDensity == null) { if (o.matched && o.records) bad.push(`매칭됐는데 밀도가 null: ${o.id}`); continue }
    if (!(o.shadeDensity >= 0)) bad.push(`음수/비수치 밀도: ${o.id} = ${o.shadeDensity}`)
    if (!Number.isFinite(o.shadeDensity)) bad.push(`무한 밀도 (0 으로 나눴다): ${o.id}`)
    if (o.shadeP == null || o.shadeP < 0 || o.shadeP > 100) bad.push(`백분위 범위 밖: ${o.id} = ${o.shadeP}`)
    if (bad.length > 5) break
  }
  if (sumByRecord === 0) bad.push('그루 수 합계가 0이다')
  if (q.dotButM > 0)
    bad.push(`식재거리 단위 경계가 무너졌다 — 소수점이 있는데(=km) 50 이상으로 읽힌 값이 ${q.dotButM}건 있다`)
  const ratioGap = med(ratioKm) && med(ratioM) ? Math.max(med(ratioKm), med(ratioM)) / Math.min(med(ratioKm), med(ratioM)) : 1
  if (ratioGap > 100) bad.push(`식재거리 단위 규칙이 의심스럽다 — km 표기와 m 표기 그룹의 중앙 비율이 ${ratioGap.toFixed(0)}배 벌어진다`)

  if (bad.length) { for (const b of bad) log('🔴 불변식 실패: ' + b); process.exit(1) }
  log('불변식 통과')
}

main().catch((e) => { console.error('치명:', e); process.exit(1) })
