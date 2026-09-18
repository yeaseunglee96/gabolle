#!/usr/bin/env node
/**
 * 층당 높이 보정 — **용도별 층당 높이를 손으로 정하지 않고 실측으로 정한다.**
 *
 * 왜 이게 있나:
 *   process/shadow.mjs 는 지금 층수에 **층당 2.8m 를 일괄로 곱한다.** 그 파일
 *   주석에 이유가 적혀 있다 — *"이 단계의 입력 스키마에는 용도를 가를 태그가
 *   아예 없다"*. 그래서 아파트(≈2.8m)와 상가(≈4m)를 못 갈랐고, 가를 수 없으니
 *   **낮은 쪽으로 눌러 두는** 선택을 했다.
 *
 *   V-World GIS건물통합정보에는 **실측 높이와 건축물용도명이 둘 다** 있다.
 *   그래서 상수를 문서에서 베끼는 대신 **데이터가 정하게 만들 수 있다** —
 *   이 저장소가 경사 기준선을 `_calibration.json` 이 정하게 한 것과 같은 방식이다
 *   (bigData/CLAUDE.md 6절).
 *
 * 방법:
 *   높이(A16)와 지상층수(A26)가 **둘 다 있는** 건물만 골라 `높이 ÷ 지상층수` 를
 *   내고, **건축물용도명별로** 그 분포(p10/p50/p90)를 구한다.
 *
 * 🔴 대표값은 최댓값이 아니다 (bigData/CLAUDE.md 7절). 여기서는 **p50(중위수)** 다.
 *    왜 p90 이 아닌가 — shadow.mjs 가 적어 둔 손실의 비대칭 때문이다:
 *      높이 과대평가 → "여기는 그늘이다" 라고 잘못 말한다 → 사용자를 뙤약볕에 보낸다
 *      높이 과소평가 → "그늘이 없다" 고 잘못 말한다 → 사용자가 손해를 안 본다
 *    p90 을 쓰면 그 용도 건물의 90% 를 **체계적으로 높게** 만든다. 잡음에
 *    끌려가지 않는 것과 위로 부풀리는 것은 다른 일이다. p10·p90 은 함께 적어
 *    두되(폭을 봐야 신뢰할지 정할 수 있다) 권장값은 p50 으로 낸다.
 *
 * 🔴 실측하고 나서 알게 된 것 — **용도만으로 가르면 아직 틀린다.**
 *    같은 용도 안에서도 층당 높이가 층수에 따라 크게 다르다 (실측):
 *      단독주택   1층 4.00m · 2층 3.65m · 3층 3.33m · 4층 2.98m
 *      공장       1층 9.50m · 2층 5.26m · 3층 4.12m · 4층 3.91m
 *    이유는 단순하다. **저층에서는 지붕·파라펫이 "한 층" 에 통째로 실린다.**
 *    1층 건물의 `높이 ÷ 1` 은 층고가 아니라 **그 건물의 높이 자체**다.
 *
 *    그래서 용도별 값 하나만 내면 고층 건물을 **위로** 부풀린다 — 위에서 말한
 *    나쁜 쪽 방향이다. 용도별 표(`recommend`)는 요구된 대로 내되, **층수 구간까지
 *    가른 표(`recommendByLevels`)를 함께 내고 그쪽을 권한다.**
 *
 * 🔴 값을 잘라내지 않는다. p10/p50/p90 자체가 이미 이상값에 둔감하고, 잘라내기
 *    시작하면 **어디까지 잘랐느냐가 결과를 정한다.** 대신 상식 범위를 벗어난
 *    비율의 **개수를 세어 보고**하고, 그것이 중위수를 망가뜨렸으면 아래 불변식이
 *    종료 코드 1 로 멈춘다. 조용히 예뻐지지 않게 하는 것이 요점이다.
 *
 * 출력  data/staged/_height-calibration.json
 *       data/staged/_height-calib-run/_run.json   ← 실행 지문 (mlops/manifest.mjs)
 *       🔴 data/staged/_run.json 은 다른 단계의 것이다. 건드리지 않는다.
 *
 *   node process/height-calibrate.mjs
 *   node process/height-calibrate.mjs --in <ndjson> --out <폴더>
 */
import { createReadStream, existsSync, statSync } from 'node:fs'
import { createInterface } from 'node:readline'
import { writeFile, mkdir } from 'node:fs/promises'
import { join, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'
import { stamp } from '../mlops/manifest.mjs'
import { log } from '../lib/log.mjs'

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..')

// ── 파라미터 ────────────────────────────────────────────────────────────────

/**
 * 🔴 권장값을 낼 최소 표본 수.
 *
 * 표본 3개로 만든 "상가 층당 4.7m" 는 없는 것보다 나쁘다 — 그럴듯한 숫자가
 * 근거처럼 쓰이기 때문이다. 그래서 문턱을 둔다. 100 을 고른 근거는 **중위수의
 * 신뢰구간이 순서통계량으로 계산된다**는 것이다:
 *
 *   중위수의 95% 구간 ≈ 순위 n/2 ± 1.96·√n/2
 *     n=  3 → 순위 1.5 ± 1.7   → 사실상 표본 전 범위. 아무것도 말하지 않는다
 *     n= 30 → 순위 15  ± 5.4   → 32~68 백분위 폭. 여전히 넓다
 *     n=100 → 순위 50  ± 9.8   → 40~60 백분위 폭. 실측 분포에서 대개 ±0.1m 안
 *
 * 그리고 문턱을 개수 하나로만 두지 않는다 — **그 구간의 실제 폭(m)도 검사한다**
 * (MAX_CI_HALF_M). 개수는 충분한데 분포가 이상하게 넓은 칸이 있으면, 그건
 * 표본이 모자란 것이 아니라 **그 칸으로 하나의 값을 말할 수 없다**는 뜻이다.
 */
const MIN_SAMPLES     = 100
const MAX_CI_HALF_M   = 0.10   // 중위수 95% 구간의 반폭 상한 (m)

/**
 * 🔴 불변식 — 권장 층당 높이가 이 범위를 벗어나면 종료 코드 1.
 *
 * 지하층수가 지상층수 자리에 들어가거나, 높이 단위가 cm 로 섞이거나, 층수와
 * 높이가 서로 다른 동을 가리키는 실수는 **이 검사에서만 잡힌다.** 문법 검사와
 * 단위 테스트는 전부 초록인 채로 통과한다 (bigData/CLAUDE.md 7절).
 *
 * 범위 근거: 주거 층고는 2.3~3.0m, 상업·공장은 3.5~6m 대다. 2m 아래는 사람이
 * 서지 못하고, 6m 위는 한 층이 아니라 여러 층을 한 층으로 적은 것이다.
 *
 * 🔴 이 자(ruler)는 **2층 이상에만 댄다.** 1층 건물의 `높이 ÷ 1` 은 층고가
 *    아니라 건물 높이 자체이고(위 머리말), 천장이 높은 단층 공장이 9.5m 인 것은
 *    오류가 아니라 사실이다. 잘못된 자를 대고 실패시키면, 다음 사람은 검사를
 *    끄는 것으로 대응한다.
 */
const SANE_MIN_M = 2.0
const SANE_MAX_M = 6.0

/** 상식 범위 밖 비율을 **세기만** 하는 창. 잘라내지는 않는다 (위 주석). */
const ODD_MIN_M = 1.5
const ODD_MAX_M = 10.0

/**
 * 층수 구간. 저층은 지붕이 통째로 실려 층당 높이가 부풀기 때문에 한 층씩
 * 따로 보고, 그 효과가 사라지는 4층 이상부터 묶는다.
 */
const BANDS = [
  { key: '1',   min: 1, max: 1 },
  { key: '2',   min: 2, max: 2 },
  { key: '3',   min: 3, max: 3 },
  { key: '4-5', min: 4, max: 5 },
  { key: '6+',  min: 6, max: Infinity },
]

// OSM 태그만 쓸 때의 보유율 — 이 데이터를 받은 이유가 이 둘을 메우는 것이었다.
// 실측 출처: data/raw/pbf/_building-summary.json (52,556채 중 height 1,098 / levels 16,128)
const OSM_BASELINE = { height: 0.021, levels: 0.308, buildings: 52556 }


// ── 통계 ────────────────────────────────────────────────────────────────────

/** 선형 보간 분위수. arr 은 **오름차순으로 정렬되어 있어야 한다.** */
function quantile(sorted, q) {
  if (sorted.length === 0) return null
  if (sorted.length === 1) return sorted[0]
  const pos = (sorted.length - 1) * q
  const lo = Math.floor(pos), hi = Math.ceil(pos)
  if (lo === hi) return sorted[lo]
  return sorted[lo] + (sorted[hi] - sorted[lo]) * (pos - lo)
}

/**
 * 중위수의 95% 신뢰구간 — 순서통계량으로. 분포 모양을 가정하지 않는다.
 * 순위 n/2 ± 1.96·√n/2 자리의 값을 그대로 읽는다.
 */
function medianCI95(sorted) {
  const n = sorted.length
  if (n < 2) return null
  const half = 1.96 * Math.sqrt(n) / 2
  const lo = Math.max(0, Math.floor(n / 2 - half))
  const hi = Math.min(n - 1, Math.ceil(n / 2 + half))
  return { lo: sorted[lo], hi: sorted[hi], halfWidth: (sorted[hi] - sorted[lo]) / 2 }
}

const r2 = (v) => (v === null || v === undefined ? null : Math.round(v * 100) / 100)
const r3 = (v) => (v === null || v === undefined ? null : Math.round(v * 1000) / 1000)
const r4 = (v) => (v === null || v === undefined ? null : Math.round(v * 10000) / 10000)

/**
 * 비율 배열 하나를 요약하고 권장값을 낼지 정한다.
 * @param {number[]} arr  층당 높이 비율 (정렬은 여기서 한다)
 */
function describe(arr) {
  const a = arr.slice().sort((x, y) => x - y)
  const ci = medianCI95(a)
  const p50 = quantile(a, 0.5)
  const odd = a.filter(v => v < ODD_MIN_M || v > ODD_MAX_M).length

  let recommend = null, reason
  if (a.length < MIN_SAMPLES) {
    reason = `표본 ${a.length} < ${MIN_SAMPLES} — 권장값을 내지 않는다`
  } else if (!ci || ci.halfWidth > MAX_CI_HALF_M) {
    reason = `중위수 95% 구간 반폭 ${r3(ci?.halfWidth)}m > ${MAX_CI_HALF_M}m — ` +
             `이 칸으로 하나의 값을 말할 수 없다`
  } else {
    recommend = r2(p50)
    reason = `p50, 표본 ${a.length}, 95% 구간 ±${r3(ci.halfWidth)}m`
  }

  return {
    n: a.length,
    p10: r2(quantile(a, 0.10)),
    p50: r2(p50),
    p90: r2(quantile(a, 0.90)),
    medianCI95: ci ? [r2(ci.lo), r2(ci.hi)] : null,
    ciHalfWidthM: ci ? r3(ci.halfWidth) : null,
    oddCount: odd,
    oddRate: r4(odd / a.length),
    recommend,
    reason,
  }
}

const bandOf = (levels) => BANDS.find(b => levels >= b.min && levels <= b.max).key

// ── 본체 ────────────────────────────────────────────────────────────────────

function parseArgs() {
  const a = process.argv.slice(2)
  const get = (k, d) => { const i = a.indexOf(k); return i >= 0 && a[i + 1] ? a[i + 1] : d }
  return {
    input: get('--in', join(ROOT, 'data/raw/building/gis-building.ndjson')),
    outDir: get('--out', join(ROOT, 'data/staged')),
  }
}

async function main() {
  const { input, outDir } = parseArgs()

  if (!existsSync(input)) {
    // 🔴 입력이 없으면 조용히 통과하지 않는다.
    console.error(`입력이 없습니다: ${input}`)
    console.error('   py collect/shp_buildings.py 를 먼저 돌리세요')
    console.error('   (V-World GIS건물통합정보 SHP → gis-building.ndjson)')
    process.exit(2)
  }

  log(`입력 ${input} (${(statSync(input).size / 1e6).toFixed(1)} MB)`)

  const NO_USE = '(용도명 없음)'
  const byUse = new Map()        // 용도 → 비율 배열
  const byUseBand = new Map()    // `용도 구간` → 비율 배열
  const byBand = new Map()       // 구간 → 비율 배열 (용도 무관)
  const all = []
  const tot = {
    lines: 0, badJson: 0, withHeight: 0, withLevels: 0, withBoth: 0,
    withUseName: 0, sampleWithUseName: 0, sampleNoUseName: 0,
    oddLow: 0, oddHigh: 0,
  }
  // 층수만 있고 높이가 없는 건물 — **실제로 층당 높이를 곱해야 할 대상**이다.
  // 용도별로 세어 두면 "이 권장값이 몇 채에 적용되는가" 를 말할 수 있다.
  const needEstimate = new Map()

  const rl = createInterface({ input: createReadStream(input), crlfDelay: Infinity })
  for await (const line of rl) {
    if (!line.trim()) continue
    tot.lines++
    let b
    try { b = JSON.parse(line) } catch { tot.badJson++; continue }

    const h = b.heightTag, lv = b.levels
    const hOk = typeof h === 'number' && Number.isFinite(h) && h > 0
    const lvOk = typeof lv === 'number' && Number.isFinite(lv) && lv >= 1
    const use = typeof b.useName === 'string' && b.useName ? b.useName : null

    if (hOk) tot.withHeight++
    if (lvOk) tot.withLevels++
    if (use) tot.withUseName++

    if (lvOk && !hOk) {
      const k = use ?? NO_USE
      needEstimate.set(k, (needEstimate.get(k) ?? 0) + 1)
    }

    if (!hOk || !lvOk) continue
    tot.withBoth++

    const ratio = h / lv
    if (ratio < ODD_MIN_M) tot.oddLow++
    else if (ratio > ODD_MAX_M) tot.oddHigh++

    all.push(ratio)
    const band = bandOf(lv)
    const push = (map, key) => {
      let arr = map.get(key)
      if (!arr) map.set(key, (arr = []))
      arr.push(ratio)
    }
    push(byBand, band)
    if (use) {
      tot.sampleWithUseName++
      push(byUse, use)
      push(byUseBand, `${use} ${band}`)
    } else {
      tot.sampleNoUseName++
    }
  }

  if (tot.lines === 0) { console.error('입력이 비어 있습니다'); process.exit(1) }
  if (tot.withBoth === 0) {
    console.error('높이와 지상층수가 둘 다 있는 건물이 하나도 없습니다 — 층당 높이를 낼 수 없습니다')
    process.exit(1)
  }

  log(`${tot.lines.toLocaleString()}채 읽음 · 층당 높이 표본 ${tot.withBoth.toLocaleString()}개`)

  // ── 용도별 · 용도×층수구간별 ──────────────────────────────────────────────
  const groups = []
  for (const [useName, arr] of byUse) {
    const d = describe(arr)
    const bands = []
    for (const bd of BANDS) {
      const sub = byUseBand.get(`${useName} ${bd.key}`)
      if (!sub) continue
      bands.push({ band: bd.key, minLevels: bd.min, ...describe(sub) })
    }
    groups.push({
      useName,
      share: r4(arr.length / tot.withBoth),
      ...d,
      appliesTo: needEstimate.get(useName) ?? 0,
      byLevels: bands,
    })
  }
  groups.sort((a, b) => b.n - a.n)

  // 전체(용도 무관) — 🔴 이게 없으면 안 된다. 층수만 있고 **용도명이 없는**
  // 건물이 실제로 많고, shadow.mjs 는 그것들에도 값을 하나 곱해야 한다.
  const overall = {
    label: '(전체 · 용도 무관)',
    ...describe(all),
    appliesTo: needEstimate.get(NO_USE) ?? 0,
    byLevels: BANDS.filter(b => byBand.has(b.key))
      .map(b => ({ band: b.key, minLevels: b.min, ...describe(byBand.get(b.key)) })),
  }

  // ── 바로 쓸 수 있는 두 개의 표 ───────────────────────────────────────────
  // 1) 용도별 값 하나 — 요구된 형태. 🔴 저층 편향이 남아 있다 (머리말)
  const table = { _default_: overall.recommend }
  for (const g of groups) if (g.recommend !== null) table[g.useName] = g.recommend

  // 2) 용도 × 층수구간 — 🔴 이쪽을 권한다
  const tableByLevels = { _default_: {} }
  for (const b of overall.byLevels) if (b.recommend !== null) tableByLevels._default_[b.band] = b.recommend
  for (const g of groups) {
    const row = {}
    for (const b of g.byLevels) if (b.recommend !== null) row[b.band] = b.recommend
    if (Object.keys(row).length) tableByLevels[g.useName] = row
  }

  // ── 불변식 ────────────────────────────────────────────────────────────────
  // 🔴 권장값을 **낸 것들만** 검사한다. 표본 5개짜리 희귀 용도의 이상한 중위수로
  //    전체 실행을 세우면, 정작 쓸 수 있는 값까지 못 만든다. 표본이 모자란
  //    칸은 권장값 자체가 null 이라 아무도 쓰지 않는다.
  const fails = []
  const checkSane = (label, v) => {
    if (!(v >= SANE_MIN_M && v <= SANE_MAX_M)) {
      fails.push(`권장 층당 높이가 상식 범위(${SANE_MIN_M}~${SANE_MAX_M}m)를 벗어났다: ` +
                 `${label} = ${v}m — 지하층수가 지상층수 자리에 들어갔거나 높이 단위가 섞였다`)
    }
  }
  for (const [k, v] of Object.entries(table)) checkSane(k, v)
  // 🔴 층수 구간표는 **2층 이상만** 이 자를 댄다 (SANE_MIN_M 주석).
  for (const [use, row] of Object.entries(tableByLevels))
    for (const [band, v] of Object.entries(row))
      if (band !== '1') checkSane(`${use}/${band}층`, v)

  // 이 데이터를 받은 이유가 OSM 의 2.1% 를 메우는 것이었다. 안 메워졌으면
  // 변환이 어딘가 틀렸거나 파일이 우리가 생각하는 그것이 아니다.
  const heightRate = tot.withHeight / tot.lines
  const levelsRate = tot.withLevels / tot.lines
  if (heightRate <= OSM_BASELINE.height) {
    fails.push(`높이 보유율 ${(heightRate * 100).toFixed(1)}% 가 OSM ` +
               `${(OSM_BASELINE.height * 100).toFixed(1)}% 보다 나아지지 않았다 — ` +
               `0 을 null 로 바꾸는 처리나 컬럼 대응이 틀렸다`)
  }

  const result = {
    step: 'height-calibrate',
    at: new Date().toISOString(),
    input: input.replace(/\\/g, '/'),
    source: {
      license: 'CC BY — 출처: 국토교통부 GIS건물통합정보 (V-World)',
      note: 'collect/shp_buildings.py 가 EPSG:5186 SHP 에서 변환한 것',
    },
    method: {
      sample: '높이(A16)와 지상층수(A26)가 둘 다 있는 건물만. 비율 = 높이 ÷ 지상층수',
      representative: 'p50 (중위수)',
      whyNotP90: '높이 과대평가는 "그늘이다" 라고 잘못 말해 사용자를 뙤약볕에 보낸다. ' +
                 '과소평가는 손해가 없다. 손실이 비대칭이라 대표값도 위로 밀지 않는다 (process/shadow.mjs 주석)',
      whyNotTrimmed: '값을 잘라내지 않는다. 잘라내면 어디까지 잘랐느냐가 결과를 정한다. ' +
                     '대신 상식 범위 밖 개수를 세고 중위수가 망가지면 종료 코드 1 로 멈춘다',
      zeroHandling: '🔴 원본에서 높이 0 · 지상층수 0 은 "없음" 이다. 변환 단계에서 이미 null 이 되어 여기 오지 않는다',
    },
    /** 🔴 실측하고 나서 알게 된 것. 이 표를 쓸 사람이 먼저 읽어야 한다. */
    caveat: {
      what: '용도가 같아도 층당 높이가 층수에 따라 크게 다르다',
      why: '저층에서는 지붕·파라펫이 "한 층" 에 통째로 실린다. 1층 건물의 (높이 ÷ 1) 은 층고가 아니라 건물 높이 자체다',
      soWhat: 'recommend(용도별 값 하나)는 고층 건물을 위로 부풀린다 — shadow.mjs 가 피하려던 방향이다. ' +
              'recommendByLevels(용도 × 층수구간)를 쓰는 것이 맞다',
      saneRangeAppliesTo: '2층 이상. 1층 칸은 층고가 아니라 건물 높이라서 2~6m 자를 대지 않는다',
    },
    params: {
      minSamples: MIN_SAMPLES,
      minSamplesWhy: '중위수의 95% 구간이 순위 n/2 ± 1.96·√n/2 다. n=3 은 전 범위, n=100 은 40~60 백분위 폭',
      maxCiHalfWidthM: MAX_CI_HALF_M,
      saneRangeM: [SANE_MIN_M, SANE_MAX_M],
      oddWindowM: [ODD_MIN_M, ODD_MAX_M],
      levelBands: BANDS.map(b => b.key),
    },
    coverage: {
      buildings: tot.lines,
      withHeight: tot.withHeight,
      withLevels: tot.withLevels,
      withBoth: tot.withBoth,
      withUseName: tot.withUseName,
      heightRate: r4(heightRate),
      levelsRate: r4(levelsRate),
      bothRate: r4(tot.withBoth / tot.lines),
      useNameRate: r4(tot.withUseName / tot.lines),
      osmBaseline: OSM_BASELINE,
      gain: {
        height: `${(OSM_BASELINE.height * 100).toFixed(1)}% → ${(heightRate * 100).toFixed(1)}%`,
        levels: `${(OSM_BASELINE.levels * 100).toFixed(1)}% → ${(levelsRate * 100).toFixed(1)}%`,
      },
    },
    quality: {
      badJson: tot.badJson,
      sampleWithUseName: tot.sampleWithUseName,
      sampleNoUseName: tot.sampleNoUseName,
      oddLow: tot.oddLow,
      oddHigh: tot.oddHigh,
      oddRate: r4((tot.oddLow + tot.oddHigh) / tot.withBoth),
    },
    overall,
    byUse: groups,
    /** 용도별 값 하나. 키가 없으면 `_default_`. 🔴 저층 편향 있음 — caveat 참조 */
    recommend: table,
    /** 🔴 이쪽을 권한다. recommendByLevels[useName]?.[band] ?? recommendByLevels._default_[band] */
    recommendByLevels: tableByLevels,
    consumer: {
      who: 'process/shadow.mjs 의 FLOOR_H_M (지금 2.8m 상수)',
      how: 'heightTag 가 없고 levels 만 있을 때 곱한다. 구간 키는 층수로 정한다: ' +
           BANDS.map(b => `${b.min}${b.max === Infinity ? '+' : (b.max > b.min ? `-${b.max}` : '')} → "${b.key}"`).join(' · '),
      note: '🔴 이 스크립트는 shadow.mjs 를 고치지 않는다. 연결은 담당자가 한다',
    },
    fails,
  }

  await mkdir(outDir, { recursive: true })
  const outPath = join(outDir, '_height-calibration.json')
  await writeFile(outPath, JSON.stringify(result, null, 2), 'utf8')

  // 🔴 실행 지문은 **하위 폴더에** 찍는다. data/staged/_run.json 에는 다른 단계의
  //    지문이 이미 있고, 그것을 덮어쓰면 그쪽 재현 근거가 사라진다.
  stamp(join(outDir, '_height-calib-run'), {
    step: 'height-calibrate',
    inputs: [input, join(ROOT, 'data/raw/building/_gis-building-summary.json')],
    params: result.params,
    result: {
      coverage: result.coverage,
      overallRecommend: overall.recommend,
      recommendedUses: Object.keys(table).length - 1,
      recommendedUseBands: Object.values(tableByLevels).reduce((s, r) => s + Object.keys(r).length, 0),
      fails: fails.length,
    },
  })

  // ── 출력 ──────────────────────────────────────────────────────────────────
  const pc = (v) => `${(v * 100).toFixed(1)}%`
  const cell = (v) => String(v === null ? '—' : v).padStart(5)
  console.log('─'.repeat(78))
  console.log(`  건물                ${tot.lines.toLocaleString().padStart(9)}`)
  console.log(`  높이 보유율         ${pc(heightRate).padStart(9)}   ← OSM ${pc(OSM_BASELINE.height)}`)
  console.log(`  지상층수 보유율     ${pc(levelsRate).padStart(9)}   ← OSM ${pc(OSM_BASELINE.levels)}`)
  console.log(`  건축물용도명 보유율 ${pc(tot.withUseName / tot.lines).padStart(9)}   ← OSM 에는 아예 없었다`)
  console.log(`  층당 높이 표본      ${tot.withBoth.toLocaleString().padStart(9)}   (상식 범위 밖 ${tot.oddLow + tot.oddHigh}, ${pc((tot.oddLow + tot.oddHigh) / tot.withBoth)})`)

  console.log('─'.repeat(78))
  console.log('  용도별 층당 높이 (m)       표본     p10   p50   p90   권장   적용대상')
  const show = (label, g) =>
    console.log(`  ${label.padEnd(22).slice(0, 22)} ${String(g.n).padStart(8)}  ` +
                `${cell(g.p10)} ${cell(g.p50)} ${cell(g.p90)} ${cell(g.recommend)}  ` +
                `${String(g.appliesTo ?? '').padStart(8)}`)
  for (const g of groups.slice(0, 15)) show(g.useName, g)
  if (groups.length > 15) console.log(`  … 그 밖 ${groups.length - 15}개 용도 (전부 _height-calibration.json 에 있다)`)
  show(overall.label, overall)

  console.log('─'.repeat(78))
  console.log('  🔴 용도 × 층수구간 (이쪽을 권한다 — 용도별 값 하나는 고층을 위로 부풀린다)')
  console.log(`  ${'용도'.padEnd(20)}${BANDS.map(b => (b.key + '층').padStart(9)).join('')}`)
  const bandRow = (label, row) =>
    console.log(`  ${label.padEnd(20).slice(0, 20)}` +
                BANDS.map(b => String(row?.[b.key] ?? '—').padStart(9)).join(''))
  for (const g of groups.slice(0, 10)) bandRow(g.useName, tableByLevels[g.useName])
  bandRow('(전체)', tableByLevels._default_)

  console.log('─'.repeat(78))
  console.log(`  권장값: 용도 ${Object.keys(table).length - 1}개 / 전체 ${groups.length}개 · ` +
              `용도×구간 칸 ${Object.values(tableByLevels).reduce((s, r) => s + Object.keys(r).length, 0)}개`)
  console.log(`  (표본 ${MIN_SAMPLES} 미만이거나 중위수 구간이 ±${MAX_CI_HALF_M}m 보다 넓으면 내지 않는다)`)
  console.log(`→ ${outPath}`)
  console.log(`→ ${join(outDir, '_height-calib-run', '_run.json')}`)

  if (fails.length) {
    console.error('\n🔴 불변식 실패')
    for (const f of fails) console.error(`  - ${f}`)
    process.exit(1)
  }
  console.log('불변식 통과')
}

main().catch((e) => { console.error(e); process.exit(1) })
