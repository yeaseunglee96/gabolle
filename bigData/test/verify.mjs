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
  if (!await has('data/staged/_slope-summary.json')) return ok('경사 미계산 — 건너뜀 (npm run slope)')
  const s = JSON.parse(await readFile(join(ROOT, 'data/staged/_slope-summary.json'), 'utf8'))
  // 🔴 평지 대조군의 거짓 양성이 0 이 아니면 기준선이 틀린 것이다
  const fp = s.calibration?.falsePositiveAt8pct?.[`${s.baselineM}m`]
  if (fp == null) throw new Error('보정 기록이 없다')
  if (fp > 0.01) throw new Error(`기준선 ${s.baselineM}m 의 거짓 양성 ${(fp*100).toFixed(0)}% — 100m 이상으로 올려라`)
  ok(`기준선 ${s.baselineM}m, 평지 거짓 양성 ${(fp*100).toFixed(0)}%`)
  if (s.representativeStat?.startsWith('max')) throw new Error('대표값이 최댓값이다 — 잡음에 끌려간다')
  ok(`대표값 ${s.representativeStat.split(' ')[0]}`)
})

console.log(failed ? `\n🔴 ${failed}건 실패` : '\n전부 통과')
process.exit(failed ? 1 : 0)
