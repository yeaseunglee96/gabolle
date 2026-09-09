#!/usr/bin/env node
/**
 * 짝 비교 응답에서 **비용함수 계수**를 추정한다 — 조건부 로짓(conditional logit).
 *
 * 조건부 로짓 = "여러 대안 중 하나를 고른 기록" 으로 각 속성의 가중치를 재는 모형.
 * 교통 연구의 표준 도구이고, 표본 수백이면 계수 4~6개를 안정적으로 뽑는다.
 * 왜 딥러닝이 아닌지는 [docs/FIELD-STUDY.md](../docs/FIELD-STUDY.md) 4절에 있다.
 *
 *   node process/choice-fit.mjs
 *
 * 입력: data/raw/survey/responses.ndjson   {"sessionId","setId","chosen":"A"|"B"}
 *       data/staged/choice-design.json
 * 출력: data/staged/_choice-model.json
 *
 * 🔴 이 파일에서 **모델 코드보다 검사 코드가 먼저**다. bigData 가 죽는 방식은
 *    화면이 깨지는 게 아니라 그럴듯한 숫자가 나오는 것이기 때문이다 (CLAUDE.md 7절).
 */
import fs from 'node:fs'
import path from 'node:path'
import { fileURLToPath } from 'node:url'
import { stamp } from '../mlops/manifest.mjs'

const ROOT = path.join(path.dirname(fileURLToPath(import.meta.url)), '..')
const STAGED = path.join(ROOT, 'data/staged')
const RESP = path.join(ROOT, 'data/raw/survey/responses.ndjson')
const DESIGN = path.join(STAGED, 'choice-design.json')

/**
 * 속성을 어떤 단위로 넣을지.
 *
 * 🔴 단위를 정하는 것이 곧 계수의 뜻을 정하는 것이다. 계단을 "단" 그대로 넣으면
 *    계수가 0.001 같은 값이 되어 유의성 판단이 눈으로 안 되고, 서로 비교도 안 된다.
 *    사람이 읽을 수 있는 크기로 맞춘다.
 */
const FEATURES = [
  { key: 'walkMin', name: '걷는 시간(분)', scale: (v) => v },
  { key: 'slopePct', name: '경사(%)', scale: (v) => v },
  { key: 'stairs', name: '계단(10단)', scale: (v) => v / 10 },
  { key: 'transfers', name: '환승(회)', scale: (v) => v },
]

function die(msg, code = 2) {
  console.error(msg)
  process.exit(code)
}

// ── 작은 선형대수 (K 가 4~6 이라 이걸로 충분하다) ───────────────────────────
function solve(A, b) {
  const n = b.length
  const M = A.map((row, i) => [...row, b[i]])
  for (let c = 0; c < n; c++) {
    let p = c
    for (let r = c + 1; r < n; r++) if (Math.abs(M[r][c]) > Math.abs(M[p][c])) p = r
    if (Math.abs(M[p][c]) < 1e-12) return null
    ;[M[c], M[p]] = [M[p], M[c]]
    for (let r = 0; r < n; r++) {
      if (r === c) continue
      const f = M[r][c] / M[c][c]
      for (let k = c; k <= n; k++) M[r][k] -= f * M[c][k]
    }
  }
  return M.map((row, i) => row[n] / row[i])
}

function inverse(A) {
  const n = A.length
  const I = A.map((_, i) => A.map((__, j) => (i === j ? 1 : 0)))
  const M = A.map((row, i) => [...row, ...I[i]])
  for (let c = 0; c < n; c++) {
    let p = c
    for (let r = c + 1; r < n; r++) if (Math.abs(M[r][c]) > Math.abs(M[p][c])) p = r
    if (Math.abs(M[p][c]) < 1e-12) return null
    ;[M[c], M[p]] = [M[p], M[c]]
    const d = M[c][c]
    for (let k = 0; k < 2 * n; k++) M[c][k] /= d
    for (let r = 0; r < n; r++) {
      if (r === c) continue
      const f = M[r][c]
      for (let k = 0; k < 2 * n; k++) M[r][k] -= f * M[c][k]
    }
  }
  return M.map((row) => row.slice(n))
}

// ── 조건부 로짓 ────────────────────────────────────────────────────────────
/** @param obs [{x: number[][], chosen: index}] */
function fit(obs, K) {
  let b = new Array(K).fill(0)
  let ll = -Infinity
  let hess = null

  for (let it = 0; it < 60; it++) {
    let LL = 0
    const g = new Array(K).fill(0)
    const H = Array.from({ length: K }, () => new Array(K).fill(0))

    for (const o of obs) {
      const u = o.x.map((xj) => xj.reduce((s, v, k) => s + v * b[k], 0))
      const mx = Math.max(...u)
      const ex = u.map((v) => Math.exp(v - mx))
      const den = ex.reduce((s, v) => s + v, 0)
      const p = ex.map((v) => v / den)
      LL += u[o.chosen] - (mx + Math.log(den))

      const xbar = new Array(K).fill(0)
      for (let j = 0; j < o.x.length; j++) for (let k = 0; k < K; k++) xbar[k] += p[j] * o.x[j][k]
      for (let k = 0; k < K; k++) g[k] += o.x[o.chosen][k] - xbar[k]
      for (let j = 0; j < o.x.length; j++) {
        for (let a = 0; a < K; a++) {
          for (let c = 0; c < K; c++) H[a][c] -= p[j] * (o.x[j][a] - xbar[a]) * (o.x[j][c] - xbar[c])
        }
      }
    }

    const step = solve(H.map((r) => r.map((v) => -v)), g)
    if (!step) break
    let moved = 0
    for (let k = 0; k < K; k++) { b[k] += step[k]; moved += Math.abs(step[k]) }
    hess = H
    if (Math.abs(LL - ll) < 1e-9 && moved < 1e-8) { ll = LL; break }
    ll = LL
  }

  const cov = hess ? inverse(hess.map((r) => r.map((v) => -v))) : null
  const se = cov ? cov.map((r, i) => Math.sqrt(Math.max(r[i], 0))) : new Array(K).fill(NaN)
  return { beta: b, ll, se }
}

/** 관측 하나의 로그가능도 — 홀드아웃 평가에 쓴다. */
function llOf(obs, b) {
  let s = 0
  for (const o of obs) {
    const u = o.x.map((xj) => xj.reduce((t, v, k) => t + v * b[k], 0))
    const mx = Math.max(...u)
    const den = u.reduce((t, v) => t + Math.exp(v - mx), 0)
    s += u[o.chosen] - (mx + Math.log(den))
  }
  return s
}

// ── 읽기 ───────────────────────────────────────────────────────────────────
if (!fs.existsSync(DESIGN)) die(`문항이 없습니다: ${DESIGN}\n  먼저: npm run choice:design`)
if (!fs.existsSync(RESP)) {
  die(
    `응답이 아직 없습니다: ${RESP}\n\n` +
      '  현장에서 받아온 응답을 한 줄에 하나씩 넣으세요:\n' +
      '    {"sessionId":"s01","setId":"cs01","chosen":"A"}\n\n' +
      '  🔴 없는 것을 만들어내지 않습니다. 조용히 통과하지도 않습니다.',
  )
}

const design = JSON.parse(fs.readFileSync(DESIGN, 'utf8'))
const bySet = new Map(design.sets.map((s) => [s.id, s]))
const rows = fs.readFileSync(RESP, 'utf8').split('\n').map((l) => l.trim()).filter(Boolean).map((l) => JSON.parse(l))

const obs = []
const people = new Map()
let skipped = 0
for (const r of rows) {
  const set = bySet.get(r.setId)
  if (!set) { skipped++; continue }
  const idx = set.alternatives.findIndex((a) => a.alt === r.chosen)
  if (idx < 0) { skipped++; continue }
  const x = set.alternatives.map((a) => FEATURES.map((f) => f.scale(a[f.key])))
  const o = { x, chosen: idx, who: r.sessionId ?? 'anon' }
  obs.push(o)
  people.set(o.who, (people.get(o.who) ?? 0) + 1)
}

if (!obs.length) die('쓸 수 있는 응답이 없습니다. setId 와 chosen 이 문항과 맞는지 보세요.')

// ── 🔴 사람 단위 홀드아웃 ──────────────────────────────────────────────────
//    관측 단위로 쪼개면 같은 사람의 다른 답이 학습과 검증에 나뉘어 들어가고,
//    그러면 "이 사람의 취향" 을 외운 것이 일반화로 보인다. 사람으로 자른다.
const persons = [...people.keys()].sort()
const holdN = Math.max(1, Math.floor(persons.length * 0.25))
const holdout = new Set(persons.slice(-holdN))
const train = obs.filter((o) => !holdout.has(o.who))
const test = obs.filter((o) => holdout.has(o.who))

const K = FEATURES.length
const m = fit(train.length >= K * 10 ? train : obs, K)

const coefs = FEATURES.map((f, i) => ({
  feature: f.name,
  key: f.key,
  beta: m.beta[i],
  se: m.se[i],
  z: m.se[i] ? m.beta[i] / m.se[i] : NaN,
}))

// 대체율 — "경사 1% 는 걷는 시간 몇 분과 같은가". 계수 자체보다 이게 읽힌다.
const walkB = m.beta[0]
const mrt = walkB
  ? FEATURES.slice(1).map((f, i) => ({ feature: f.name, minutesEquivalent: m.beta[i + 1] / walkB }))
  : []

// ── 검사 — 통과 못 하면 종료 코드로 말한다 ─────────────────────────────────
const checks = []
const push = (ok, id, msg) => checks.push({ ok, id, msg })

push(m.beta[0] < 0, 'walk-negative', '걷는 시간 계수가 음수여야 한다 (오래 걷는 것이 싫어야 한다)')
push(Math.abs(coefs[0].z) > 1.96, 'walk-significant',
  '걷는 시간 계수가 0 과 구별되어야 한다 — 아니면 실험이 작동하지 않은 것이다')
push(m.beta[1] < 0, 'slope-negative',
  '경사 계수가 음수여야 한다 — 양수면 "오르막이 좋다" 는 뜻이고 그건 데이터가 아니라 버그다')
push(persons.length >= 8, 'enough-people', `응답자가 8명 이상이어야 한다 (지금 ${persons.length}명)`)
push(obs.length >= K * 20, 'enough-obs', `관측이 ${K * 20}개 이상이어야 한다 (지금 ${obs.length}개)`)

let heldLL = null
if (test.length) {
  heldLL = llOf(test, m.beta)
  const chance = test.reduce((s, o) => s - Math.log(o.x.length), 0)
  push(heldLL > chance, 'beats-chance',
    `홀드아웃에서 무작위보다 나아야 한다 (모델 ${heldLL.toFixed(1)} vs 무작위 ${chance.toFixed(1)})`)
}

const out = {
  at: new Date().toISOString(),
  n: { observations: obs.length, people: persons.length, skipped, trainObs: train.length, holdoutPeople: holdN },
  features: FEATURES.map((f) => f.name),
  coefficients: coefs,
  minutesEquivalent: mrt,
  logLikelihood: m.ll,
  holdoutLogLikelihood: heldLL,
  checks,
}
fs.mkdirSync(STAGED, { recursive: true })
fs.writeFileSync(path.join(STAGED, '_choice-model.json'), JSON.stringify(out, null, 1))
stamp(STAGED, { step: 'choice-fit', inputs: [RESP, DESIGN], params: { features: FEATURES.map((f) => f.key) }, result: { n: out.n, checks: checks.filter((c) => !c.ok).length } })

// ── 보고 ───────────────────────────────────────────────────────────────────
console.log(`조건부 로짓 — 응답자 ${persons.length}명 / 관측 ${obs.length}개${skipped ? ` (버림 ${skipped})` : ''}`)
for (const c of coefs) {
  console.log(`  ${c.feature.padEnd(14)} ${c.beta >= 0 ? ' ' : ''}${c.beta.toFixed(4)}  (SE ${c.se.toFixed(4)}, z ${c.z.toFixed(2)})`)
}
if (mrt.length) {
  console.log('\n  걷는 시간으로 환산하면')
  for (const r of mrt) console.log(`    ${r.feature.padEnd(14)} 1단위 = ${r.minutesEquivalent.toFixed(2)}분`)
}
const bad = checks.filter((c) => !c.ok)
console.log('')
for (const c of checks) console.log(`  ${c.ok ? 'ok  ' : '🔴  '}${c.id} — ${c.msg}`)
if (bad.length) {
  console.error(`\n검사 ${bad.length}건 실패. 계수를 제품에 넣지 마십시오.`)
  process.exit(1)
}
console.log('\n통과. data/staged/_choice-model.json')
