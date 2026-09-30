#!/usr/bin/env node
/**
 * bigData 파트 검증. 판정은 종료 코드다 (0 성공 / 그 외 실패).
 * 개수를 여기에 적지 않는다 — 늘릴 때마다 낡는다.
 */
import { readFile, readdir, access } from 'node:fs/promises'
import { execFileSync } from 'node:child_process'
import { join, dirname } from 'node:path'
import { fileURLToPath } from 'node:url'

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..')
let failed = 0
const ok   = m => console.log(`  ✅ ${m}`)
const bad  = m => { console.log(`  ❌ ${m}`); failed++ }
const has  = async p => { try { await access(join(ROOT, p)); return true } catch { return false } }

async function t(name, fn) {
  console.log(name)
  try { await fn() } catch (e) { bad(e.message) }
}

await t('설정 파일', async () => {
  for (const f of ['config/area.json', 'config/sources.json']) {
    const j = JSON.parse(await readFile(join(ROOT, f), 'utf8'))
    ok(`${f} 유효`)
    if (f.endsWith('sources.json')) {
      // 라이선스 없는 출처가 들어오는 것을 막는다 — 이 프로젝트가 지는 방식이다
      const noLicense = j.sources.filter(s => !s.license)
      if (noLicense.length) throw new Error(`라이선스 없는 출처: ${noLicense.map(s => s.id)}`)
      ok(`출처 ${j.sources.length}건 전부 라이선스 명시`)
      // 금지 출처가 스크립트를 갖고 있으면 안 된다
      const armed = j.banned.filter(b => j.sources.some(s => s.id === b.id))
      if (armed.length) throw new Error(`금지 출처가 sources 에도 있다: ${armed.map(b => b.id)}`)
      ok(`금지 출처 ${j.banned.length}건 — 수집 경로 없음`)
    }
  }
})

await t('스크립트 문법', async () => {
  for (const d of ['collect', 'process', 'test']) {
    for (const f of await readdir(join(ROOT, d))) {
      if (!f.endsWith('.mjs')) continue
      execFileSync(process.execPath, ['--check', join(ROOT, d, f)], { stdio: 'pipe' })
    }
    ok(`${d}/ 전부 통과`)
  }
  // .py 는 파이썬이 있을 때만 본다. CI 이미지(node:20-alpine)에는 없다 —
  // 없다고 실패시키면 CI 를 위해 이미지를 무겁게 만들게 된다. 로컬에서 잡는다.
  let py = null
  for (const c of ['py', 'python3', 'python']) {
    try { execFileSync(c, ['--version'], { stdio: 'pipe' }); py = c; break } catch {}
  }
  if (!py) return ok('python 없음 — .py 문법 검사 건너뜀')
  for (const f of await readdir(join(ROOT, 'collect'))) {
    if (!f.endsWith('.py')) continue
    execFileSync(py, ['-c', `import ast,io,sys;ast.parse(io.open(sys.argv[1],encoding='utf-8').read())`,
                       join(ROOT, 'collect', f)], { stdio: 'pipe' })
  }
  ok(`collect/*.py 문법 통과 (${py})`)
})

await t('PNG 디코더 (terrarium 고도)', async () => {
  if (!await has('data/raw/dem/15')) return ok('DEM 없음 — 건너뜀 (npm run collect:terrain)')
  const { decodePNG, terrariumToElevation } = await import('../process/png.mjs')
  const f = (await readdir(join(ROOT, 'data/raw/dem/15'))).find(x => x.endsWith('.png'))
  const png = decodePNG(await readFile(join(ROOT, 'data/raw/dem/15', f)))
  if (png.width !== 256 || png.height !== 256) throw new Error(`타일 크기 ${png.width}x${png.height}`)
  const e = terrariumToElevation(png)
  const fin = Array.from(e).filter(v => v > -500 && v < 3000)
  if (fin.length < e.length * 0.99) throw new Error('고도값이 범위를 벗어난다')
  const max = Math.max(...fin)
  // 부산 중구·동구에 3000m 산은 없고, 전부 해수면일 수도 없다
  if (max < 5 || max > 900) throw new Error(`고도 최대 ${max}m — 디코딩이 깨졌을 가능성`)
  ok(`${f} 256x256, 고도 최대 ${max.toFixed(0)}m`)
})

await t('경사 기준선 보정 불변식', async () => {
  if (!await has('data/staged/_calibration.json'))
    return ok('보정 미실행 — 건너뜀 (npm run calibrate)')
  const c = JSON.parse(await readFile(join(ROOT, 'data/staged/_calibration.json'), 'utf8'))

  // 🔴 어느 기준선에서도 평지 거짓양성이 1% 아래로 안 내려가면 DEM 이 못 쓸 것이다
  if (c.recommendedBaselineM == null)
    throw new Error('어느 기준선에서도 거짓양성이 1% 아래로 안 내려간다 — DEM 을 교체해야 한다')
  const row = c.rows.find(r => r.baselineM === c.recommendedBaselineM)
  if (!row) throw new Error('권장 기준선이 측정표에 없다')
  if (row.flatFalsePositive > 0.01)
    throw new Error(`권장 기준선 ${row.baselineM}m 의 평지 거짓양성 ${(row.flatFalsePositive*100).toFixed(1)}% — 1% 를 넘는다`)
  // 잡음만 죽고 신호도 같이 죽으면 의미가 없다
  if (row.hillyOver8 < 0.2)
    throw new Error(`산지 신호가 ${(row.hillyOver8*100).toFixed(0)}% 로 무너졌다 — 기준선이 너무 길다`)
  ok(`권장 기준선 ${row.baselineM}m — 평지 거짓양성 ${(row.flatFalsePositive*100).toFixed(1)}%, 산지 신호 ${(row.hillyOver8*100).toFixed(0)}%`)
  ok(`대조군 평지 ${c.control.flatWays.toLocaleString()}개 / 산지 ${c.control.hillyWays.toLocaleString()}개`)

  if (!await has('data/staged/_slope-summary.json'))
    return ok('경사 미계산 — 건너뜀 (npm run slope)')
  const s = JSON.parse(await readFile(join(ROOT, 'data/staged/_slope-summary.json'), 'utf8'))

  // 🔴 계산에 쓴 기준선과 보정이 권장한 기준선이 어긋나면, 숫자는 그럴듯한데 틀린 것이다
  if (s.baselineM !== c.recommendedBaselineM)
    throw new Error(`계산 기준선 ${s.baselineM}m ≠ 보정 권장 ${c.recommendedBaselineM}m`)
  ok(`계산이 권장 기준선을 따랐다 (${s.baselineM}m)`)
  if (s.representativeStat?.startsWith('max'))
    throw new Error('대표값이 최댓값이다 — 잡음 표본 하나에 끌려간다')
  ok(`대표값 ${s.representativeStat.split(' ')[0]}`)
})

await t('걷는 길에 그늘 붙이기 (process/walk-graph-shade.mjs)', async () => {
  // 서버의 걷는 길 파일에 그늘을 붙이는 스크립트다. 파일 판(v1/v2)·짝짓기·같은 입력이면 같은 바이트 규칙을 지킨다.
  // 진짜 파일은 건드리지 않는다 — 작은 가짜 그래프로만 돈다(S15P21E201-1895).
  try {
    execFileSync(process.execPath, ['--test', join(ROOT, 'test/walk-graph-shade.test.mjs')], { stdio: 'pipe' })
  } catch (e) {
    throw new Error('단위 시험 실패' + '\n' + String(e.stdout || '').split('\n').slice(-25).join('\n'))
  }
  ok('단위 시험 통과')
})

console.log(failed ? `\n🔴 ${failed}건 실패` : '\n전부 통과')
process.exit(failed ? 1 : 0)
