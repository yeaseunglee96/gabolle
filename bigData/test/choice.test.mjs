/**
 * 추정기가 **아는 답을 되찾는가** — 정답을 심어둔 가짜 응답으로 검사한다.
 *
 * 🔴 왜 이 테스트가 필요한가. 조건부 로짓은 틀려도 숫자가 나온다. 부호가 뒤집혀도,
 *    수렴을 안 해도, 그럴듯한 계수가 찍힌다. bigData 가 죽는 방식이 정확히 그것이라
 *    (CLAUDE.md 7절) **값이 아니라 값을 만든 절차**를 붙잡아야 한다.
 *
 *    그래서 참값을 정해 두고 그것으로 응답을 만든 뒤, 추정기가 그 참값을 되찾는지
 *    본다. 못 되찾으면 실험 데이터가 아무리 좋아도 소용없다.
 *
 *   node --test test/choice.test.mjs
 */
import { describe, it } from 'node:test'
import assert from 'node:assert/strict'
import { execFileSync } from 'node:child_process'
import fs from 'node:fs'
import os from 'node:os'
import path from 'node:path'
import { fileURLToPath } from 'node:url'

const ROOT = path.join(path.dirname(fileURLToPath(import.meta.url)), '..')

/** 참값. 전부 음수다 — 오래 걷는 것도, 가파른 것도, 계단도, 환승도 싫다. */
const TRUE = { walkMin: -0.15, slopePct: -0.12, stairs10: -0.08, transfers: -0.5 }

function rng(seed) {
  let s = seed >>> 0
  return () => ((s = (s * 1664525 + 1013904223) >>> 0) / 4294967296)
}

function utility(a) {
  return TRUE.walkMin * a.walkMin + TRUE.slopePct * a.slopePct + TRUE.stairs10 * (a.stairs / 10) + TRUE.transfers * a.transfers
}

/** 로짓 확률대로 고른다 — 사람이 완벽하지 않다는 것까지 흉내낸다. */
function pick(set, r) {
  const u = set.alternatives.map(utility)
  const mx = Math.max(...u)
  const ex = u.map((v) => Math.exp(v - mx))
  const den = ex.reduce((s, v) => s + v, 0)
  const t = r() * den
  let acc = 0
  for (let i = 0; i < ex.length; i++) { acc += ex[i]; if (t <= acc) return set.alternatives[i].alt }
  return set.alternatives[set.alternatives.length - 1].alt
}

describe('짝 비교 추정기', () => {
  it('🔴 심어둔 참값의 부호를 전부 되찾는다', () => {
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'choice-'))
    try {
      // 이 저장소를 통째로 흉내내지 않는다. 필요한 두 폴더만 만든다.
      fs.mkdirSync(path.join(tmp, 'data/staged'), { recursive: true })
      fs.mkdirSync(path.join(tmp, 'data/raw/survey'), { recursive: true })
      for (const d of ['process', 'mlops']) {
        fs.mkdirSync(path.join(tmp, d), { recursive: true })
        for (const f of fs.readdirSync(path.join(ROOT, d))) {
          fs.copyFileSync(path.join(ROOT, d, f), path.join(tmp, d, f))
        }
      }

      execFileSync(process.execPath, ['process/choice-design.mjs', '--sets', '14'], { cwd: tmp })
      const design = JSON.parse(fs.readFileSync(path.join(tmp, 'data/staged/choice-design.json'), 'utf8'))

      const r = rng(7)
      const lines = []
      for (let p = 1; p <= 30; p++) {
        for (const s of design.sets) {
          lines.push(JSON.stringify({ sessionId: `s${String(p).padStart(2, '0')}`, setId: s.id, chosen: pick(s, r) }))
        }
      }
      fs.writeFileSync(path.join(tmp, 'data/raw/survey/responses.ndjson'), lines.join('\n') + '\n')

      const out = execFileSync(process.execPath, ['process/choice-fit.mjs'], { cwd: tmp, encoding: 'utf8' })
      assert.match(out, /통과/, `검사를 통과하지 못했다:\n${out}`)

      const m = JSON.parse(fs.readFileSync(path.join(tmp, 'data/staged/_choice-model.json'), 'utf8'))
      for (const c of m.coefficients) {
        assert.ok(c.beta < 0, `${c.feature} 계수가 음수가 아니다: ${c.beta}`)
      }
      // 걷는 시간은 반드시 유의해야 한다 — 아니면 실험 자체가 작동하지 않은 것이다.
      assert.ok(Math.abs(m.coefficients[0].z) > 1.96, `걷는 시간이 유의하지 않다: z=${m.coefficients[0].z}`)
    } finally {
      fs.rmSync(tmp, { recursive: true, force: true })
    }
  })

  it('응답이 없으면 조용히 통과하지 않는다', () => {
    const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'choice-none-'))
    try {
      for (const d of ['process', 'mlops']) {
        fs.mkdirSync(path.join(tmp, d), { recursive: true })
        for (const f of fs.readdirSync(path.join(ROOT, d))) fs.copyFileSync(path.join(ROOT, d, f), path.join(tmp, d, f))
      }
      execFileSync(process.execPath, ['process/choice-design.mjs'], { cwd: tmp })
      let code = 0
      try { execFileSync(process.execPath, ['process/choice-fit.mjs'], { cwd: tmp, stdio: 'pipe' }) }
      catch (e) { code = e.status }
      assert.notEqual(code, 0, '응답이 없는데 성공으로 끝났다 — 없는 것을 만들어낸 것과 같다')
    } finally {
      fs.rmSync(tmp, { recursive: true, force: true })
    }
  })
})
