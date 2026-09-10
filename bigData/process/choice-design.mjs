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
 */
const ATTRS = [
  { key: 'walkMin', label: '걷는 시간', unit: '분', levels: [8, 12, 18, 25] },
  { key: 'slopePct', label: '가장 가파른 곳', unit: '%', levels: [0, 5, 10, 15] },
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
 */
function dominated(a, b) {
  const le = ATTRS.every((at) => a[at.key] <= b[at.key])
  const ge = ATTRS.every((at) => a[at.key] >= b[at.key])
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
  const f = (x) => `${x.walkMin}분 / 경사${x.slopePct}% / 계단${x.stairs} / 환승${x.transfers}`
  console.log(`  ${s.id}  A: ${f(s.alternatives[0])}   vs   B: ${f(s.alternatives[1])}`)
}
console.log(`  … 그 밖에 ${Math.max(0, sets.length - 3)}개`)
