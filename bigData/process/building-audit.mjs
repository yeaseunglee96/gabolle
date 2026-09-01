#!/usr/bin/env node
/**
 * 건물 층수 태그 빈 곳의 성격을 **면적으로** 검증한다.
 *
 * docs/WALKABILITY.md 2.2 가 남긴 가설이다:
 *
 *   "표본을 보면 아파트·대학 같은 큰 건물에 층수가 있고 작은 건물에 없는
 *    패턴이다. 그림자에 실제로 영향을 주는 것은 큰 건물이므로(2층 상가는
 *    그림자 5m 로 길을 못 덮는다) 빈 70% 는 대체로 그림자를 못 만드는
 *    건물일 가능성이 크다. 이건 가설이고 아직 확인하지 않았다."
 *
 * 그 문서는 **개수만** 셌다 (30.8%). 개수는 작은 창고 하나와 아파트 한 동을
 * 같은 1 로 센다. 그림자는 면적이 만든다. 그래서 **면적 기준으로 다시 센다.**
 *
 * 🔴 판정 기준을 계산보다 먼저 정하고 근거를 적는다 (아래 CRITERIA).
 *    결과를 보고 기준을 고르면 그건 검증이 아니라 사후 합리화다.
 *    가설이 기각되면 기각이라고 적는다 — 그 편이 팀에 필요한 정보다.
 *
 *   node process/building-audit.mjs
 *   → data/staged/_building-audit.json
 */
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { stamp } from '../mlops/manifest.mjs'

const ROOT = path.join(path.dirname(fileURLToPath(import.meta.url)), '..')
const IN = path.join(ROOT, 'data/raw/pbf/building.ndjson')
const STAGED = path.join(ROOT, 'data/staged')
const OUT = path.join(STAGED, '_building-audit.json')
// 🔴 실행 지문은 하위 폴더에 쓴다. stamp() 는 outDir/_run.json 을 쓰는데
//    data/staged/_run.json 은 이미 다른 단계(choice-design)의 지문이 들어 있다.
//    거기에 덮어쓰면 그 단계가 무엇으로 만들어졌는지가 사라진다.
//    지문 내용은 _building-audit.json 의 `run` 키에도 그대로 넣는다.
const STAMP_DIR = path.join(STAGED, 'building-audit')

// ─────────────────────────────────────────────────────────────────────────────
// 1. 물리 상수와 판단 — 그림자를 만들 수 있는 최소 건물은 무엇인가
// ─────────────────────────────────────────────────────────────────────────────
//
// WALKABILITY.md 2.2 의 논리를 그대로 이어받는다. 그 문서는 이렇게 적었다:
//   · 60m 건물, 태양고도 77°(여름 정오) → 그림자 14m
//   · 60m 건물, 태양고도 30°(오후 4시)  → 그림자 104m
//   · "2층 상가는 그림자 5m 로 길을 못 덮는다"
//
// 그림자 길이 = 높이 / tan(태양고도) 다. 위 세 줄이 서로 맞는지 확인하면:
//   60/tan(77°) = 13.9 ✅   60/tan(30°) = 103.9 ✅
//   2층 상가 = 층당 4m → 8m, 그림자 5m → 그림자/높이 = 0.625 → 태양고도 58°
// 즉 그 문서의 "2층 상가" 줄은 **태양고도 58°(여름 부산 오전 9시·오후 3시 무렵)**
// 을 가정한 것이다. 시각을 하나로 못 박으면 결론이 시각에 끌려다니므로
// **두 시각 모두에서 판정한다.**
const SUN = [
  { key: 'pm4', label: '오후 4시 (태양고도 30°)', altitudeDeg: 30, primary: true },
  { key: 'am9pm3', label: '오전 9시·오후 3시 (태양고도 58°)', altitudeDeg: 58, primary: false },
]

// 길을 "덮는다" 고 말하려면 그림자가 몇 m 여야 하나.
// 부산에서 사람이 실제로 걷는 이면도로·골목은 폭 8~12 m 다. 중간값 10 m 를 쓴다.
// 🔴 이건 판단이다. 문서에 숨기지 않고 여기 파라미터로 둔다 — 바꾸면 결론이
//    어떻게 움직이는지 sensitivity 표에 나온다.
const ROAD_WIDTH_M = 10

// 층수 → 높이. WALKABILITY 2.2: 아파트 층당 약 2.8m, 상가 약 4m. 중간 3.2m.
const M_PER_LEVEL = 3.2

// 🔴 태그 없는 건물의 높이는 알 수 없다. 그래서 **바닥면적을 높이의 대리
//    지표로 쓴다.** 그 대리 관계는 태그가 **있는** 건물에서 실측한다:
//
//      A*(H) = 추정높이 ≥ H 인 (태그 보유) 건물들의 바닥면적 p10
//
//    뜻: "높이 H 이상인 건물의 90% 는 이 면적보다 넓다." 그보다 좁은 건물이
//    H 이상일 가능성은 10% 아래다. 이 공식은 태그 **없는** 건물의 분포를
//    보지 않고 정해지므로, 결과를 보고 고른 기준이 아니다.
//    최댓값이 아니라 분위수를 쓰는 이유는 CLAUDE.md 7절과 같다 —
//    최댓값은 잡음 표본 하나에 끌려간다.
const PROXY_QUANTILE = 0.10

// 대리 지표를 세울 표본이 이보다 적으면 A* 자체를 믿을 수 없다 → 종료 코드 1.
const MIN_PROXY_SAMPLE = 200

// ─────────────────────────────────────────────────────────────────────────────
// 2. 🔴 판정 기준 — 계산 전에 고정한다
// ─────────────────────────────────────────────────────────────────────────────
const CRITERIA = {
  hypothesis:
    '층수·높이 태그가 없는 건물은 대체로 작아서 그림자를 만들지 못한다. ' +
    '따라서 OSM 의 태그 보유분만으로 그림자 레이어를 시작할 수 있고, ' +
    'GIS건물통합정보(도형+대장이 합쳐진 국가 데이터)를 기다릴 필요가 없다.',

  primaryRule:
    'u = (그림자 후보 건물의 총 바닥면적 중 태그가 전혀 없는 건물의 면적 비중). ' +
    '그림자 후보 = 바닥면적 ≥ A*(필요높이). ' +
    'u < 20% → 지지 / 20% ≤ u < 40% → 부분 지지 / u ≥ 40% → 기각.',

  ruleRationale: [
    '구간(길) 단위의 그늘 판정은 "그늘 있음/없음" 이진값이다. 그림자를 만드는 면적의',
    '20% 가 빠져도 대부분 구간에서는 나머지 80% 가 이미 그 구간을 덮거나 못 덮는지를',
    '결정한다 — 판정의 방향이 유지된다. 40% 이상이 빠지면 재료의 절반 가까이가 없는',
    '것이고, 그때 나오는 그늘 지도는 틀렸는데도 그럴듯해 보인다. CLAUDE.md 7절이',
    '경계한 실패 방식이 정확히 그것이다 — 문법도 테스트도 초록인 채로 숫자가 틀린다.',
    '20/40 은 판단이며, 결과를 보기 전에 고정했다.',
  ].join(' '),

  bothTimesRule:
    '두 시각(오후 4시 30° / 오전9시·오후3시 58°) 모두에서 판정하고, ' +
    '주판정은 오후 4시다 — 여행 일정이 실제로 그늘을 필요로 하는 시각이고, ' +
    '태양이 낮아 작은 건물도 그림자를 만들기 때문에 **가설에 가장 불리한 시각**이다. ' +
    '가설에 유리한 58° 쪽만 보고 지지라고 적으면 그건 기준을 고른 것이다. ' +
    '두 시각의 판정이 다르면 나쁜 쪽을 최종 판정으로 삼는다.',

  strictSecondary:
    '보조(참고): p90(태그 없음 면적) < p10(태그 있음 면적) 이면 두 집단이 거의 ' +
    '완전히 분리된다는 뜻이다. 통과하기 매우 어려운 기준이라 주판정으로 쓰지 않고, ' +
    '분리 정도를 눈으로 보기 위해 함께 적는다.',
}

// ─────────────────────────────────────────────────────────────────────────────
const die = (code, msg) => { console.error(`🔴 ${msg}`); process.exit(code) }

if (!fs.existsSync(IN)) {
  die(2, `입력이 없습니다: ${path.relative(ROOT, IN)}\n   py collect/pbf_buildings.py 를 먼저 돌리세요`)
}

const raw = fs.readFileSync(IN, 'utf8')
const rows = []
for (const line of raw.split('\n')) {
  if (!line) continue
  const r = JSON.parse(line)
  if (r.topic !== 'building') continue
  if (!(typeof r.areaM2 === 'number' && Number.isFinite(r.areaM2) && r.areaM2 > 0)) {
    die(1, `areaM2 가 면적이 아니다 (id=${r.id}, areaM2=${r.areaM2}) — 추출이 깨졌다`)
  }
  rows.push(r)
}
if (rows.length === 0) die(2, `${path.relative(ROOT, IN)} 에 건물이 없습니다 — 추출을 다시 돌리세요`)

/** 정렬된 배열에서의 분위수 (선형 보간). */
function q(sorted, p) {
  if (sorted.length === 0) return null
  const i = (sorted.length - 1) * p
  const lo = Math.floor(i), hi = Math.ceil(i)
  return lo === hi ? sorted[lo] : sorted[lo] + (sorted[hi] - sorted[lo]) * (i - lo)
}
const sum = (a) => a.reduce((s, v) => s + v, 0)
const r2 = (v) => (v == null ? null : Math.round(v * 100) / 100)
const pct = (v) => (v == null ? null : Math.round(v * 1000) / 10)

/** 태그로 알 수 있는 추정 높이. height 가 있으면 그것을 쓴다 (실측 m 이다). */
const estHeight = (r) =>
  r.heightTag != null ? r.heightTag : r.levels != null ? r.levels * M_PER_LEVEL : null

const tagged = rows.filter((r) => r.levels != null || r.heightTag != null)
const untagged = rows.filter((r) => r.levels == null && r.heightTag == null)

// ── 불변식: 개수·면적이 나뉘어도 합이 보존되는가 ────────────────────────────
if (tagged.length + untagged.length !== rows.length) die(1, '태그 있음/없음 분할이 전체와 안 맞는다')
const areaAll = sum(rows.map((r) => r.areaM2))
const areaTagged = sum(tagged.map((r) => r.areaM2))
const areaUntagged = sum(untagged.map((r) => r.areaM2))
if (Math.abs(areaTagged + areaUntagged - areaAll) > areaAll * 1e-9) die(1, '면적 합이 보존되지 않는다')
if (tagged.length === 0 || untagged.length === 0) {
  die(1, `태그 보유가 ${tagged.length}/${rows.length} 다 — 태그 파싱이 깨졌을 가능성이 크다`)
}

const sortedAll = rows.map((r) => r.areaM2).sort((a, b) => a - b)
const sortedT = tagged.map((r) => r.areaM2).sort((a, b) => a - b)
const sortedU = untagged.map((r) => r.areaM2).sort((a, b) => a - b)
const QS = [0.1, 0.25, 0.5, 0.75, 0.9]
const quantiles = (s) => Object.fromEntries(QS.map((p) => [`p${p * 100}`, r2(q(s, p))]))

// ── 3. 시각별 판정 ──────────────────────────────────────────────────────────
const verdictRank = { 지지: 0, '부분 지지': 1, 기각: 2 }
const byTime = []

for (const s of SUN) {
  // 그림자 길이 = 높이 / tan(고도). 길을 덮으려면 그림자 ≥ ROAD_WIDTH_M.
  const shadowPerHeight = 1 / Math.tan((s.altitudeDeg * Math.PI) / 180)
  const neededHeightM = ROAD_WIDTH_M / shadowPerHeight
  // 층수는 "몇 층쯤인가" 를 사람이 읽으라고 적는 것이다. 올림하면 16.003m 가 6층으로
  // 보여 없는 정밀도가 생긴다 — 소수 한 자리로 그대로 적는다.
  const neededLevels = Math.round((neededHeightM / M_PER_LEVEL) * 10) / 10

  // A* — 태그 보유 건물에서 실측한 면적 문턱
  const proxy = tagged.filter((r) => estHeight(r) >= neededHeightM).map((r) => r.areaM2)
                      .sort((a, b) => a - b)
  if (proxy.length < MIN_PROXY_SAMPLE) {
    die(1, `${s.label}: 높이 ${r2(neededHeightM)}m 이상인 태그 보유 건물이 ${proxy.length}개다 ` +
           `(${MIN_PROXY_SAMPLE}개 미만) — 면적 문턱 A* 를 신뢰할 수 없다`)
  }
  const aStar = q(proxy, PROXY_QUANTILE)

  const cand = rows.filter((r) => r.areaM2 >= aStar)
  const candT = cand.filter((r) => r.levels != null || r.heightTag != null)
  const candU = cand.filter((r) => r.levels == null && r.heightTag == null)
  const candArea = sum(cand.map((r) => r.areaM2))
  const candAreaU = sum(candU.map((r) => r.areaM2))
  const u = candArea > 0 ? candAreaU / candArea : 0

  const verdict = u < 0.2 ? '지지' : u < 0.4 ? '부분 지지' : '기각'

  byTime.push({
    key: s.key, label: s.label, primary: s.primary,
    sunAltitudeDeg: s.altitudeDeg,
    shadowPerHeight: r2(shadowPerHeight),
    roadWidthM: ROAD_WIDTH_M,
    neededHeightM: r2(neededHeightM),
    neededLevelsAt3_2m: neededLevels,
    proxySampleN: proxy.length,
    shadowCandidateThresholdM2: r2(aStar),
    candidates: {
      count: cand.length,
      countShareOfAll: pct(cand.length / rows.length),
      areaM2: r2(candArea),
      areaShareOfAll: pct(candArea / areaAll),
      taggedCount: candT.length,
      untaggedCount: candU.length,
      untaggedCountSharePct: pct(candU.length / cand.length),
      untaggedAreaM2: r2(candAreaU),
    },
    uUntaggedAreaSharePct: pct(u),
    verdict,
  })
}

const primary = byTime.find((t) => t.primary)
const finalVerdict = byTime.reduce((worst, t) =>
  verdictRank[t.verdict] > verdictRank[worst] ? t.verdict : worst, '지지')

// ── 4. 민감도 — 고정 문턱에서도 같은 얘기가 나오나 ──────────────────────────
// 결론이 A* 한 점에 걸려 있는지 보려고 손으로 고른 문턱에서도 같이 잰다.
// 🔴 이것으로 판정을 바꾸지 않는다. 판정은 위의 A* 규칙이다.
const sensitivity = [50, 100, 200, 500, 1000, 2000].map((th) => {
  const c = rows.filter((r) => r.areaM2 >= th)
  const cu = c.filter((r) => r.levels == null && r.heightTag == null)
  const ca = sum(c.map((r) => r.areaM2))
  return {
    thresholdM2: th, count: c.length,
    untaggedCount: cu.length,
    untaggedCountSharePct: pct(c.length ? cu.length / c.length : 0),
    untaggedAreaSharePct: pct(ca ? sum(cu.map((r) => r.areaM2)) / ca : 0),
  }
})

// ── 5. 사후 정밀화 — 🔴 판정에 쓰지 않는다. 왜 그런지를 보려고 잰다 ────────
//
// 면적을 높이의 대리 지표로 쓴 것은 근사다. 부산 강서구·사하구의 공단에는
// **바닥은 넓은데 1층인 창고·공장**이 많고, 그것들은 위 판정에서 "그림자 후보"
// 로 들어가 버린다. 그래서 u 가 부풀었을 가능성이 있다.
// 그 부풀음을 걷어내고 다시 재 본다:
//
//   면적 구간마다, **태그가 있는** 건물에서 "필요 높이 이상일 확률" p(구간) 을
//   실측한다. 태그 없는 건물의 면적에 그 확률을 곱하면 **그림자를 만들 것으로
//   기대되는 면적**이 된다. 그것이 전체 그림자 재료에서 차지하는 비중을 본다.
//
// ⚠️ 가정: "같은 면적 구간 안에서는 태그 유무가 높이와 무관하다."
//    이 가정은 완벽하지 않다 — 태그는 사람이 붙이고, 사람은 아파트에 관심이 많다.
//    그래서 이 값도 참값이 아니라 **또 하나의 추정**이다. 판정은 위의 A* 규칙이다.
const BINS = [0, 50, 100, 200, 400, 800, 1600, 3200, 6400, Infinity]
function refine(neededHeightM) {
  const binOf = (a) => BINS.findIndex((e, i) => a >= e && a < BINS[i + 1])
  const tallN = [], tagN = [], tagTallArea = [], untagArea = []
  for (const r of tagged) {
    const b = binOf(r.areaM2)
    tagN[b] = (tagN[b] || 0) + 1
    const tall = estHeight(r) >= neededHeightM
    tallN[b] = (tallN[b] || 0) + (tall ? 1 : 0)
    tagTallArea[b] = (tagTallArea[b] || 0) + (tall ? r.areaM2 : 0)
  }
  for (const r of untagged) {
    const b = binOf(r.areaM2)
    untagArea[b] = (untagArea[b] || 0) + r.areaM2
  }
  let missing = 0, known = 0
  const table = []
  for (let b = 0; b < BINS.length - 1; b++) {
    const n = tagN[b] || 0
    const p = n ? (tallN[b] || 0) / n : 0
    const exp = (untagArea[b] || 0) * p
    missing += exp
    known += tagTallArea[b] || 0
    table.push({
      binM2: `${BINS[b]}–${BINS[b + 1] === Infinity ? '∞' : BINS[b + 1]}`,
      taggedSampleN: n,
      pTallPct: pct(p),
      taggedShadowAreaM2: r2(tagTallArea[b] || 0),
      untaggedAreaM2: r2(untagArea[b] || 0),
      untaggedExpectedShadowAreaM2: r2(exp),
    })
  }
  return {
    neededHeightM: r2(neededHeightM),
    estimatedMissingShadowAreaSharePct: pct(missing / (missing + known)),
    table,
  }
}
const refinementPostHoc = {
  '//': '🔴 판정에 쓰지 않았다. A* 규칙(위)이 판정이다. 이건 그 판정이 대리 지표의 ' +
        '거친 근사 때문에 생긴 것인지 확인하려고 사후에 잰 것이다.',
  assumption: '같은 면적 구간 안에서는 태그 유무와 높이가 무관하다 (완벽하지 않은 가정)',
  primary: refine(ROAD_WIDTH_M * Math.tan((SUN[0].altitudeDeg * Math.PI) / 180)),
}

// 태그가 없는 건물 중 가장 넓은 것들 — 사람이 osm.org 에서 직접 확인할 수 있게 남긴다.
const evidenceLargestUntagged = untagged
  .slice().sort((a, b) => b.areaM2 - a.areaM2).slice(0, 10)
  .map((r) => ({
    areaM2: r.areaM2,
    osm: r.id < 0 ? `relation/${-r.id}` : `way/${r.id}`,
    at: [r.ring.reduce((s, p) => s + p[0], 0) / r.ring.length,
         r.ring.reduce((s, p) => s + p[1], 0) / r.ring.length].map((v) => Math.round(v * 1e4) / 1e4),
  }))

// 보조 엄격 기준
const p90u = q(sortedU, 0.9), p10t = q(sortedT, 0.1)
const strictPass = p90u < p10t

// 층수 분포 (태그 보유분) — 그림자를 만들 만한 층이 실제로 얼마나 있나
const lv = tagged.filter((r) => r.levels != null).map((r) => r.levels).sort((a, b) => a - b)

const out = {
  at: new Date().toISOString(),
  step: 'building-audit',
  source: {
    file: path.relative(ROOT, IN).split(path.sep).join('/'),
    lines: rows.length,
    bytes: fs.statSync(IN).size,
    scope: 'config/area.json target.bbox (부산 전역 bbox — 바다·김해·양산 일부 포함, 경계 클립 전)',
  },
  hypothesis: CRITERIA.hypothesis,
  criteria: CRITERIA,
  physics: {
    formula: '그림자 길이 = 건물 높이 / tan(태양고도)',
    roadWidthM: ROAD_WIDTH_M,
    roadWidthRationale: '부산에서 사람이 걷는 이면도로·골목 폭 8~12m 의 중간값. 판단이다.',
    mPerLevel: M_PER_LEVEL,
    mPerLevelRationale: 'WALKABILITY.md 2.2 — 아파트 2.8m / 상가 4m 의 중간',
    areaProxy: `A*(H) = 추정높이 ≥ H 인 태그 보유 건물의 면적 p${PROXY_QUANTILE * 100}`,
  },
  tagCoverage: {
    '//': '🔴 이것이 이 감사의 핵심이다. WALKABILITY.md 는 개수만 셌다.',
    totalBuildings: rows.length,
    totalFootprintM2: r2(areaAll),
    totalFootprintKm2: r2(areaAll / 1e6),
    byCount: {
      withLevels: tagged.filter((r) => r.levels != null).length,
      withHeight: tagged.filter((r) => r.heightTag != null).length,
      withEither: tagged.length,
      withEitherPct: pct(tagged.length / rows.length),
      untagged: untagged.length,
      untaggedPct: pct(untagged.length / rows.length),
    },
    byArea: {
      withEitherM2: r2(areaTagged),
      withEitherPct: pct(areaTagged / areaAll),
      untaggedM2: r2(areaUntagged),
      untaggedPct: pct(areaUntagged / areaAll),
    },
    countVsAreaGapPct: pct(areaTagged / areaAll - tagged.length / rows.length),
  },
  areaQuantilesM2: {
    all: quantiles(sortedAll),
    tagged: quantiles(sortedT),
    untagged: quantiles(sortedU),
    meanTagged: r2(areaTagged / tagged.length),
    meanUntagged: r2(areaUntagged / untagged.length),
  },
  levelsDistribution: {
    n: lv.length,
    p10: q(lv, 0.1), p50: q(lv, 0.5), p90: q(lv, 0.9),
    atLeast5Levels: lv.filter((v) => v >= 5).length,
  },
  byTime,
  sensitivityFixedThresholds: sensitivity,
  refinementPostHoc,
  evidenceLargestUntagged,
  strictSecondary: {
    rule: 'p90(태그 없음) < p10(태그 있음)',
    p90UntaggedM2: r2(p90u),
    p10TaggedM2: r2(p10t),
    pass: strictPass,
  },
  result: {
    primaryTime: primary.label,
    primaryUntaggedShadowAreaSharePct: primary.uUntaggedAreaSharePct,
    verdictByTime: Object.fromEntries(byTime.map((t) => [t.key, t.verdict])),
    verdict: finalVerdict,
    conclusion: null,   // 아래에서 채운다
  },
}

out.result.conclusion =
  finalVerdict === '지지'
    ? 'OSM 태그 보유분만으로 그림자 레이어를 시작할 수 있다. GIS건물통합정보는 정밀도 향상용이고 일정을 막지 않는다.'
    : finalVerdict === '부분 지지'
      ? '시작은 되지만 그늘 판정에 눈에 보이는 누락이 남는다. GIS건물통합정보를 병행해서 신청하고, 그때까지 나온 그늘 지도는 "누락 있음" 을 명시해 쓴다.'
      : '🔴 가설 기각. 그림자를 만들 만한 면적의 상당 부분이 층수·높이 태그가 없다. ' +
        'OSM 만으로 만든 그늘 지도는 틀렸는데도 그럴듯해 보인다. GIS건물통합정보(도형+층수)를 기다려야 한다.'

fs.mkdirSync(STAGED, { recursive: true })
const run = stamp(STAMP_DIR, {
  step: 'building-audit',
  inputs: [IN],
  params: {
    roadWidthM: ROAD_WIDTH_M, mPerLevel: M_PER_LEVEL,
    proxyQuantile: PROXY_QUANTILE, minProxySample: MIN_PROXY_SAMPLE,
    sunAltitudesDeg: SUN.map((s) => s.altitudeDeg),
    verdictCuts: { support: 0.2, partial: 0.4 },
  },
  result: { buildings: rows.length, verdict: finalVerdict, u: primary.uUntaggedAreaSharePct },
})
out.run = run
fs.writeFileSync(OUT, JSON.stringify(out, null, 2))

// ── 화면 보고 ───────────────────────────────────────────────────────────────
const c = out.tagCoverage
console.log(`건물 ${c.totalBuildings.toLocaleString()}개 · 총 바닥면적 ${c.totalFootprintKm2} km2`)
console.log(`태그 보유율   개수 ${c.byCount.withEitherPct}%   면적 ${c.byArea.withEitherPct}%   (차이 ${c.countVsAreaGapPct}%p)`)
console.log(`면적 p50      태그 있음 ${out.areaQuantilesM2.tagged.p50} m2 / 없음 ${out.areaQuantilesM2.untagged.p50} m2`)
console.log('─'.repeat(72))
for (const t of byTime) {
  console.log(`${t.primary ? '★' : ' '} ${t.label}`)
  console.log(`    길 ${t.roadWidthM}m 를 덮는 데 필요한 높이 ${t.neededHeightM}m (≈${t.neededLevelsAt3_2m}층)`)
  console.log(`    → 그림자 후보 면적 문턱 A* = ${t.shadowCandidateThresholdM2} m2 (표본 ${t.proxySampleN.toLocaleString()}개)`)
  console.log(`    → 후보 ${t.candidates.count.toLocaleString()}개 중 태그 없음 ${t.candidates.untaggedCount.toLocaleString()}개`)
  console.log(`    → u(후보 면적 중 태그 없음 비중) = ${t.uUntaggedAreaSharePct}%   ⇒ ${t.verdict}`)
}
console.log('─'.repeat(72))
console.log(`보조 엄격 기준 p90(없음) ${out.strictSecondary.p90UntaggedM2} < p10(있음) ${out.strictSecondary.p10TaggedM2} → ${strictPass ? '통과' : '불통과'}`)
console.log(`사후 정밀화(판정에 안 씀): 면적 구간별 "높이 이상일 확률" 로 보정해도 ` +
            `누락 그림자재료 ${refinementPostHoc.primary.estimatedMissingShadowAreaSharePct}%`)
console.log(`\n판정: ${finalVerdict}`)
console.log(out.result.conclusion)
console.log(`\n→ ${path.relative(ROOT, OUT).split(path.sep).join('/')}`)
console.log(`→ ${path.relative(ROOT, path.join(STAMP_DIR, '_run.json')).split(path.sep).join('/')} (실행 지문)`)
