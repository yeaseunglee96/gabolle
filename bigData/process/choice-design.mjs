#!/usr/bin/env node
/**
 * 짝 비교 문항을 만든다 — 해운대·광안리 현장 실험용.
 *
 * 설계 근거는 [docs/FIELD-STUDY.md](../docs/FIELD-STUDY.md) 에 있다. 요약하면:
 * 하루에 만나는 외국인은 20~50명인데, 한 사람에게 경로 두 개를 나란히 놓고
 * 12번 고르게 하면 관측이 12배가 된다. **표본을 늘리는 유일한 방법이 이것이다.**
 *
 *   node process/choice-design.mjs [--sets 12] [--seed 20260827]
 *
 * 결과: data/staged/choice-design.json  (설문 화면이 그대로 읽는다)
 */
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { stamp } from '../mlops/manifest.mjs'

const ROOT = path.join(path.dirname(fileURLToPath(import.meta.url)), '..')
const OUT = path.join(ROOT, 'data/staged')

const arg = (k, d) => {
  const i = process.argv.indexOf(k)
  return i < 0 ? d : process.argv[i + 1]
}
const SETS = Number(arg('--sets', 12))
const SEED = Number(arg('--seed', 20260827))

/**
 * 🔴 무작위를 쓰되 **시드를 기록한다.** 같은 시드면 같은 문항이 나온다.
 *    안 그러면 "어제 쓴 그 설문" 을 다시 만들 수 없고, 그러면 두 회차를 합칠 수 없다.
 */
function rng(seed) {
  let s = seed >>> 0
  return () => {
    s = (s * 1664525 + 1013904223) >>> 0
    return s / 4294967296
  }
}

/**
 * 속성과 수준.
 *
 * 🔴 수준을 실제 부산 값에서 가져온다. `_slope-summary.json` 이 말하는 실제 분포를
 *    벗어난 조합을 물으면(예: 경사 40%), 응답자는 상상으로 답하고 계수는 그 상상을
 *    학습한다. 그래서 현실에 있는 범위 안에서만 흔든다.
 *
 * 🔴 **정정 (2026-09-10) — 경사 수준이 `[0, 5, 10, 15]` 였다. 손으로 적은 숫자였고
 *    두 군데가 틀렸다.** 낡은 값을 지우지 않고 무엇이 왜 틀렸는지 남긴다.
 *
 *    1. **내리막이 한 번도 안 물어졌다.** 넷이 전부 0 이상이라 "내려가는 길" 이라는
 *       선택지가 설문에 아예 없었다. 부산은 올라간 만큼 내려온다 — 아래 실측에서
 *       보행·차도 연장의 **48.6%(382 km)가 내리막**이다. 없는 것을 물었으니
 *       계수는 오르막 비용만 배우고, 추천 경로는 "내리막이면 편하다" 를 모른다.
 *    2. **위쪽이 너무 낮았다.** 가장 가파른 수준 15% 는 실측 분포의 **77분위**밖에
 *       안 된다. 중구·동구 산복도로의 상위 20%는 그보다 가파른데 물어본 적이 없다.
 *
 * 🔴 **어떻게 다시 정했나 — 실측 분포의 분위수다.**
 *
 *    측정: `npm run calibrate` → `npm run slope` (2026-09-10 실행)
 *      · 범위   config/area.json 의 `focus` = 부산 중구·동구 39 km2
 *      · 입력   OSM 도로·보행로·계단 4,854개 / 연장 785.6 km + AWS terrarium DEM z15
 *      · 산출   data/staged/segment-slope.ndjson · data/staged/_slope-summary.json
 *      · 기준선 100m — 손으로 정하지 않았다. calibrate-slope.mjs 가 재서 정한다
 *               (평지 거짓양성 0.5%). slope.mjs 의 ⚠️ 주석 참고
 *      · 대표값 구간의 `p90Slope`. 최댓값이 아니다 — 최댓값은 잡음 표본 하나에
 *               끌려간다. 그래서 라벨 "가장 가파른 곳" 은 실제로 **90분위**를 뜻한다
 *
 *    🔴 **부호를 어디서 얻었나.** slope.mjs 는 `slopes.map(Math.abs)` 로 **부호를
 *       버린다** — p90Slope 는 세기(steepness)일 뿐 오르막/내리막이 아니다. 게다가
 *       OSM way 의 노드 순서는 통행 방향이 아니라 **그린 사람 마음**이라, 파일에
 *       남은 부호를 그대로 쓰면 그건 지형이 아니라 편집 이력이다.
 *
 *       그래서 통행 방향으로 되돌린다: **한 구간은 양쪽으로 다 걸을 수 있으므로
 *       ±세기 둘 다에 절반씩 기여한다.** 이렇게 만든 분포는 대칭이 되는데, 그
 *       대칭이 맞는지는 지어내지 않고 **따로 잰 값으로 확인했다** — 구간별 순경사
 *       (ascent-descent)/length 의 음수 비중이 연장 기준 48.6% 로 거의 반반이다.
 *
 *    고른 분위: **10 / 30 / 70 / 90**. 이유는 둘이다.
 *      · 1·99분위(±39.6%)는 **DEM 잡음과 계단 골목**이라 경로 문항으로 물으면 거짓말이다
 *      · 40~60분위는 서로 3%p 안에 몰려 있어 **응답자가 구별하지 못한다** — 문항만 버린다
 *      10/30/70/90 이면 넷이 고르게 벌어지고 양 끝이 실제로 존재하는 범위 안에 남는다
 *
 *      10분위 = -16.0  ·  30분위 = -6.0  ·  70분위 = +6.0  ·  90분위 = +16.0
 *      (부호 없는 세기로는 40분위 5.98% · 80분위 16.02% 다. 반올림해서 6 과 16 을 쓴다)
 *
 *    ⚠️ 이 넷은 **`focus`(중구·동구) 실측**이다. 부산 전역 PBF 로 slope 를 다시 돌리면
 *       분포가 달라진다 — 그때는 이 주석의 숫자도 같이 고친다. 베껴 적힌 숫자는 낡는다.
 *
 * ─────────────────────────────────────────────────────────────────────────────
 * 🔴 **재검증 (2026-09-10, S15P21E201-795) — 고도 자료에 가짜 혹이 있었다.
 *    걸러내고 다시 뽑았다. 결론부터: 이 넷은 **안 움직였다.** 그대로 둔다.**
 *
 *    무엇이 문제였나: 고도 원본(SRTM 레이더)이 **매립지·모래해안에 없는 봉우리**를
 *    만들어 놓았다. 마린시티 앞은 주변이 5 m 인데 혼자 **51.5 m** 다.
 *    걸러내는 코드는 [process/dem-clean.mjs](dem-clean.mjs) 에 있고, 왜 그 숫자인지도
 *    거기 적혀 있다. `slope.mjs` 와 `calibrate-slope.mjs` 가 **둘 다** 그것을 통해 읽는다.
 *
 *    거르기 전과 후 (둘 다 `focus` 중구·동구 · 4,854구간 785.6 km · 연장 기준 분위):
 *
 *      | | 40분위 | 80분위 | 수준 넷 |
 *      |---|---|---|---|
 *      | 거르기 전 | 5.98% | 16.02% | `[-16, -6, 6, 16]` |
 *      | **거른 뒤** | **5.96%** | **16.01%** | **`[-16, -6, 6, 16]`** ← 같다 |
 *
 *    **0.02%p 움직였다. 반올림 한참 아래다.** 그래서 수준을 바꾸지 않는다.
 *
 *    🔴 **"문제가 없었다" 는 뜻이 아니다. 문제가 여기 없었다는 뜻이다.**
 *       가짜 혹은 **매립지·모래해안에 몰려 있다.** 부산 전역 타일 3,127장을 전수로
 *       훑으면 **81곳**이 걸리는데(누른 면적 1.49 km2), 중구·동구에는 그중 **5곳**뿐이고
 *       그 5곳은 이 동네 도로망을 지나가지 않는다. 중구·동구는 산복도로라 경사가
 *       **진짜로** 가파르고, 그 진짜가 분포를 지배한다.
 *
 * 🔴 **그래서 진짜 문제는 따로 있다 — 이 문항은 해운대·광안리에서 도는데
 *    수준은 중구·동구에서 뽑혔다.** (이 파일 머리말이 "해운대·광안리 현장 실험용"이다.)
 *
 *    그리고 **가짜 혹이 몰려 있는 곳이 바로 해운대다.** 실측:
 *
 *      마린시티 도로 6.37 km — 전부 실제 평지다
 *        거르기 전 p90 경사 **25.5%** · p90 ≥8% 인 연장 **39.5%**
 *        거른 뒤   p90 경사  **3.7%** · p90 ≥8% 인 연장  **0.0%**
 *        (`마린시티1로` 1,147 m 한 줄이 25.5% → 3.7% 로 내려온다)
 *
 *    해운대·광안리를 넣고(총 9,912구간 1,500.7 km) 다시 뽑으면 수준이 이렇게 된다:
 *
 *      | | 40분위 | 80분위 | 수준 넷 |
 *      |---|---|---|---|
 *      | 해운대 포함 · 거르기 전 | 5.51% | 14.96% | `[-15, -6, 6, 15]` |
 *      | 해운대 포함 · 거른 뒤 | 5.44% | 14.81% | `[-15, -5, 5, 15]` |
 *
 *    ⚠️ **그런데 이 값을 여기 박지 않았다.** 이유는 **재현이 안 되기 때문**이다.
 *       `npm run collect:overpass` 는 `config/area.json` 의 `focus`(중구·동구)만 받는다.
 *       해운대 자료는 이번에 손으로 따로 받은 것이라, 이 숫자를 박아 두면 다음 사람이
 *       저장소 명령만으로 다시 만들 수 없고 **검증할 수 없는 숫자**가 남는다.
 *       그건 이 저장소가 제일 싫어하는 것이다.
 *
 *       **범위를 해운대로 옮길 것인가는 사람이 정할 일이다** — `config/area.json` 에
 *       설문 구역을 추가하고 `collect:overpass` 로 받게 만든 뒤, 그때 이 넷을 다시 뽑는다.
 *       S15P21E201-795 의 별도 항목으로 남긴다.
 */
const ATTRS = [
  { key: 'walkMin', label: '걷는 시간', unit: '분', levels: [8, 12, 18, 25] },
  // 음수 = 내리막, 양수 = 오르막. 위 주석의 실측 분위수에서 나왔다
  { key: 'slopePct', label: '가장 가파른 곳', unit: '%', levels: [-16, -6, 6, 16] },
  { key: 'stairs', label: '계단', unit: '단', levels: [0, 20, 50, 100] },
  { key: 'transfers', label: '환승', unit: '회', levels: [0, 1] },
]

/** 한 대안 = 속성마다 수준 하나. */
function draw(r) {
  const a = {}
  for (const at of ATTRS) a[at.key] = at.levels[Math.floor(r() * at.levels.length)]
  return a
}

/**
 * 🔴 **지배되는 쌍을 버린다.** 한쪽이 모든 속성에서 더 좋으면 답이 뻔하고,
 *    뻔한 답은 계수를 하나도 못 알려준다 — 그 문항은 응답자의 집중력만 쓴다.
 *    설계 단계에서 버리는 것이 분석 단계에서 버리는 것보다 훨씬 싸다.
 *
 * 🔴 **정정 (2026-09-10) — 경사에 음수가 들어오면서 이 판정이 깨졌다.**
 *
 *    원래는 네 속성 모두 `a <= b` 면 a 가 낫다고 봤다. **"숫자가 작을수록 좋다"**
 *    를 깔고 있었고, 경사가 전부 0 이상일 때는 맞는 말이었다.
 *
 *    음수가 들어오자 그게 깨진다. -16%(가파른 내리막)는 -6% 보다 숫자가 작지만
 *    더 편하지 않다 — 무릎에도, 휠체어 제동에도 더 나쁘다. 그대로 두면 코드가
 *    **가장 가파른 내리막을 "가장 좋은 수준"으로 착각해** 멀쩡한 문항을 버리고
 *    뻔한 문항을 남긴다. 부담이 되는 것은 방향이 아니라 **세기(|경사|)** 다.
 *
 *    그래서 둘을 고친다.
 *
 *    1. 경사는 **세기로 비교한다** (`cost`). 걷는 시간·계단·환승은 작을수록 좋은 게
 *       확실하므로 숫자 그대로 둔다.
 *    2. **부호가 서로 다른 짝은 지배로 판정하지 않는다.** 오르막과 내리막 중
 *       무엇이 더 싫은지는 **이 설문이 재려는 것 자체**다. 코드가 미리 답을 정해
 *       버리면 그 답을 잴 문항이 통째로 사라진다. 모르는 것은 모르는 채로 물어본다.
 */
const cost = (at, v) => (at.key === 'slopePct' ? Math.abs(v) : v)

function dominated(a, b) {
  // 한쪽은 오르막, 한쪽은 내리막 → 우열을 코드가 못 정한다. 버리지 않는다 (위 2).
  if (Math.sign(a.slopePct) * Math.sign(b.slopePct) < 0) return false
  const le = ATTRS.every((at) => cost(at, a[at.key]) <= cost(at, b[at.key]))
  const ge = ATTRS.every((at) => cost(at, a[at.key]) >= cost(at, b[at.key]))
  return le || ge
}

function build() {
  const r = rng(SEED)
  const sets = []
  let guard = 0
  while (sets.length < SETS && guard++ < SETS * 500) {
    const A = draw(r)
    const B = draw(r)
    if (dominated(A, B)) continue
    sets.push({ id: `cs${String(sets.length + 1).padStart(2, '0')}`, alternatives: [{ alt: 'A', ...A }, { alt: 'B', ...B }] })
  }
  return sets
}

const sets = build()
if (sets.length < SETS) {
  console.error(`문항을 ${SETS}개 만들지 못했습니다 (${sets.length}개). 수준을 넓히거나 --sets 를 줄이세요.`)
  process.exit(1)
}

fs.mkdirSync(OUT, { recursive: true })
const outFile = path.join(OUT, 'choice-design.json')
fs.writeFileSync(
  outFile,
  JSON.stringify(
    {
      '//': '짝 비교 문항. 설계 근거는 docs/FIELD-STUDY.md.',
      '//재현': `같은 --seed ${SEED} 로 다시 만들면 똑같이 나온다. 회차를 합치려면 시드를 바꾸지 않는다.`,
      '//경사부호': 'slopePct 는 음수가 내리막, 양수가 오르막이다. 화면에 그대로 "-16%" 로 적지 말고 "내리막 16%" 로 보여준다.',
      '//경사출처': 'data/staged/segment-slope.ndjson 실측(중구·동구 4,854구간 785.6km)의 10/30/70/90 분위. 자세한 것은 process/choice-design.mjs 의 ATTRS 주석.',
      seed: SEED,
      attributes: ATTRS,
      sets,
    },
    null,
    1,
  ),
)

stamp(OUT, { step: 'choice-design', inputs: [], params: { sets: SETS, seed: SEED }, result: { made: sets.length } })

console.log(`짝 비교 문항 ${sets.length}개 — ${path.relative(ROOT, outFile).split(path.sep).join('/')}`)
console.log(`  시드 ${SEED} (같은 시드면 같은 문항)`)
for (const s of sets.slice(0, 3)) {
  // 🔴 "-16%" 라고 찍으면 사람이 못 읽는다. 부호를 말로 푼다 (음수 = 내리막)
  const slope = (v) => `${v < 0 ? '내리막' : '오르막'}${Math.abs(v)}%`
  const f = (x) => `${x.walkMin}분 / ${slope(x.slopePct)} / 계단${x.stairs} / 환승${x.transfers}`
  console.log(`  ${s.id}  A: ${f(s.alternatives[0])}   vs   B: ${f(s.alternatives[1])}`)
}
console.log(`  … 그 밖에 ${Math.max(0, sets.length - 3)}개`)
