#!/usr/bin/env node
/**
 * bigData 파트 검증. 판정은 종료 코드다 (0 성공 / 그 외 실패).
 * 개수를 여기에 적지 않는다 — 늘릴 때마다 낡는다.
 */
import { readFile, readdir, access, mkdtemp, rm, writeFile, mkdir, copyFile } from 'node:fs/promises'
import { execFileSync } from 'node:child_process'
import { join, dirname } from 'node:path'
import { tmpdir } from 'node:os'
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
  for (const d of ['collect', 'process', 'test', 'survey']) {
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

await t('완료 코드 — 인코딩·디코딩 왕복 (S15P21E201-409)', async () => {
  const { encodeSession, decodeCode, randomSessionId } = await import('../survey/codec.mjs')
  const sessionId = randomSessionId(() => 0.5)
  const code = encodeSession(sessionId, ['A', 'B', 'A', 'A'])
  const r = decodeCode(code)
  if (!r.ok) throw new Error(`정상 코드가 해독에 실패했다: ${r.reason}`)
  if (r.sessionId !== sessionId || r.answers.join('') !== 'ABAA') throw new Error('왕복 결과가 원본과 다르다')
  ok(`왕복 통과 (${code})`)

  // 🔴 한 글자를 바꾼 코드는 조용히 통과하면 안 된다 — 검사합이 잡아야 한다.
  const tampered = code.replace('A', 'B')
  const rt = decodeCode(tampered)
  if (tampered !== code && rt.ok) throw new Error(`한 글자 바뀐 코드가 통과했다: ${tampered}`)
  ok('한 글자 오기 검사합에서 걸림')

  // 모양이 아예 틀린 코드도 던지지 않고 {ok:false} 로 돌아와야 한다 (decode.mjs 가 목록화하려면).
  const bad = decodeCode('그냥아무거나')
  if (bad.ok) throw new Error('모양이 틀린 코드가 통과했다')
  ok('모양이 틀린 코드는 던지지 않고 ok:false 로 돌아온다')
})

await t('설문 해독→적재 스모크 (S15P21E201-409·499)', async () => {
  // 🔴 --out·--responses 로 임시 폴더에 쓰게 한다 — 실제 data/raw/survey/ 를
  //    테스트가 건드리면 안 된다(진짜 현장 응답이 쌓이는 자리다).
  const { encodeSession, randomSessionId } = await import('../survey/codec.mjs')
  const dir = await mkdtemp(join(tmpdir(), 'bigdata-survey-'))
  try {
    const design = { seed: 1, sets: [{ id: 'cs01' }, { id: 'cs02' }] }
    await writeFile(join(dir, 'design.json'), JSON.stringify(design))

    const s1 = randomSessionId(() => 0.1)
    const s2 = randomSessionId(() => 0.9)
    const codes = [
      encodeSession(s1, ['A', 'B']),
      encodeSession(s2, ['B', 'A']),
      's1', // 모양이 틀린 줄 — rejects 로 가야 한다
    ]
    await writeFile(join(dir, 'codes.txt'), codes.join('\n'))

    const decodeScript = join(ROOT, 'survey/decode.mjs')
    const loadScript = join(ROOT, 'survey/load.mjs')
    const decodedDir = join(dir, 'decoded')
    const responsesFile = join(dir, 'responses.ndjson')

    const decodeOut = execFileSync(
      process.execPath,
      [decodeScript, '--file', join(dir, 'codes.txt'), '--design', join(dir, 'design.json'), '--out', decodedDir],
      { encoding: 'utf8' },
    )
    if (!/해독 성공 2개, 검사합 실패 1개/.test(decodeOut)) throw new Error(`decode.mjs 출력이 기대와 다르다:\n${decodeOut}`)
    ok('decode.mjs — 정상 2개는 통과, 모양 틀린 1개는 rejects 로')

    const ndjsonFile = (await readdir(decodedDir)).find((f) => f.endsWith('.ndjson') && !f.includes('rejects'))
    if (!ndjsonFile) throw new Error('decode.mjs 가 세션 ndjson 을 안 만들었다')
    const sessionPath = join(decodedDir, ndjsonFile)

    const load1 = execFileSync(
      process.execPath,
      [loadScript, '--file', sessionPath, '--out', decodedDir, '--responses', responsesFile],
      { encoding: 'utf8' },
    )
    if (!/들어간 응답 4건, 검사합 재확인 실패 0건, 중복 건너뜀 0건/.test(load1)) {
      throw new Error(`load.mjs 첫 실행 출력이 기대와 다르다:\n${load1}`)
    }
    ok('load.mjs — 세션 2개(문항 2개씩) → 응답 4건 적재')

    // 🔴 같은 파일을 두 번 넣어도 표가 늘면 안 된다.
    const load2 = execFileSync(
      process.execPath,
      [loadScript, '--file', sessionPath, '--out', decodedDir, '--responses', responsesFile],
      { encoding: 'utf8' },
    )
    if (!/들어간 응답 0건, 검사합 재확인 실패 0건, 중복 건너뜀 4건/.test(load2)) {
      throw new Error(`load.mjs 재실행이 중복을 못 잡았다:\n${load2}`)
    }
    const finalLines = (await readFile(responsesFile, 'utf8')).split('\n').filter(Boolean)
    if (finalLines.length !== 4) throw new Error(`두 번 넣었는데 표가 ${finalLines.length}줄이다 — 4줄이어야 한다`)
    ok('load.mjs — 같은 파일을 두 번 넣어도 표가 늘지 않는다')
  } finally {
    await rm(dir, { recursive: true, force: true })
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

await t('기대값은 검증이 통과했을 때만 파일에 남는다 (S15P21E201-1266)', async () => {
  // 🔴 검증 표본이 틀리면 화면에 "위 기대값을 쓰지 마십시오" 가 뜬다. 그런데 그 말은
  //    사라지고 파일은 남는다. 실제로 판정 실패·실측 자리에 자리표시자 9999 가 박힌 채
  //    저장소에 커밋돼 있었다. 스크립트를 안 돌리고 파일만 연 사람은 그것을 믿는다.
  const dir = await mkdtemp(join(tmpdir(), 'bigdata-expected-'))
  try {
    // ① 일부러 검증이 틀어지는 가짜 자료를 깔고 진짜 스크립트를 돌린다
    await mkdir(join(dir, 'process'), { recursive: true })
    await mkdir(join(dir, 'data/raw/tourapi'), { recursive: true })
    await mkdir(join(dir, 'data/staged'), { recursive: true })
    await copyFile(join(ROOT, 'process/expected-feature-rows.mjs'), join(dir, 'process/expected-feature-rows.mjs'))

    // 관광공사 수집본은 응답 봉투 안에 JSON 이 한 번 더 들어 있는 모양이다
    const envelope = { response: { body: { items: { item: [{ contentid: 'T1', contenttypeid: '12' }] } } } }
    await writeFile(
      join(dir, 'data/raw/tourapi/tourapi-busan.ndjson'),
      JSON.stringify({ raw: JSON.stringify(envelope) }) + '\n',
    )
    // 축 넷 × (관광공사·상가) 한 줄씩. 경사 합계가 2 라 검증 표본과 어긋난다 — 일부러다
    for (const [name, field] of [
      ['place-slope', 'slopePercent'], ['place-quietness', 'quietnessScore'],
      ['place-locality', 'localityScore'], ['place-shade', 'shadeScore'],
    ]) {
      await writeFile(join(dir, 'data/staged/' + name + '.ndjson'), JSON.stringify({ contentid: 'T1', [field]: 1 }) + '\n')
      await writeFile(join(dir, 'data/staged/' + name + '-sbiz.ndjson'), JSON.stringify({ sourceId: 'S1', [field]: 1 }) + '\n')
    }

    let code = 0
    try { execFileSync(process.execPath, [join(dir, 'process/expected-feature-rows.mjs')], { stdio: 'pipe' }) }
    catch (e) { code = e.status }
    if (code !== 1) throw new Error('검증 표본이 틀렸는데 종료 코드가 ' + code + ' 다 — 1 이어야 한다')

    const made = JSON.parse(await readFile(join(dir, 'data/staged/_expected-feature-rows.json'), 'utf8'))
    if (made.calibration.ok !== false) throw new Error('판정이 파일에 실패로 안 적혔다')
    if (made.axes) throw new Error('검증이 틀렸는데 기대값 숫자가 파일에 남았다 — 다음 사람이 그것을 믿는다')
    if (!made.notUsable) throw new Error('왜 숫자가 없는지가 파일에 안 적혔다')
    ok('검증 실패 — 종료 코드 1 · 파일에 숫자 없음 · 이유는 적힘')

    // ② 저장소에 커밋된 파일도 판정과 숫자의 짝이 맞아야 한다
    if (!await has('data/staged/_expected-feature-rows.json')) return ok('기대값 미계산 — 건너뜀')
    const repo = JSON.parse(await readFile(join(ROOT, 'data/staged/_expected-feature-rows.json'), 'utf8'))
    if (repo.calibration.ok && !repo.axes) throw new Error('검증은 통과했는데 기대값이 없다')
    if (!repo.calibration.ok && repo.axes) throw new Error('저장소의 기대값 파일이 실패한 실행의 숫자를 갖고 있다')
    ok('저장소 파일 — 판정 ' + (repo.calibration.ok ? '통과' : '실패') + ' · 숫자 ' + (repo.axes ? '있음' : '없음') + ' (짝이 맞는다)')
  } finally {
    await rm(dir, { recursive: true, force: true })
  }
})

await t('받은 쪽 세기는 키 없이 돈다 (S15P21E201-1264)', async () => {
  // 🔴 --status 는 API 를 한 번도 안 부르는데 키 검사가 앞에 있어서, 키 없는 PC 에서는
  //    "받아 둔 쪽이 멀쩡한가" 를 볼 수조차 없었다. 레인마다 폴더를 따로 펼치면서
  //    실제로 걸렸다 — .env 는 저장소에 안 올리므로 clone 으로 안 따라온다.
  //
  // 🔴 여기서 지키는 것은 **둘**이다. 상태 보기가 키 없이 돌 것, 그리고 **그래도
  //    실제 수집은 여전히 멈출 것.** 뒤엣것이 없으면 이 고침은 자물쇠를 푼 것이 된다.
  const dir = await mkdtemp(join(tmpdir(), 'bigdata-facility-'))
  try {
    // .env 가 없는 저장소를 흉내낸다. 진짜 수집기를 그대로 복사해 오므로
    // 이 검사는 사본이 아니라 **지금 이 순간의 수집기**를 돌린다.
    await mkdir(join(dir, 'collect'), { recursive: true })
    await mkdir(join(dir, 'lib'), { recursive: true })
    await mkdir(join(dir, 'data/raw/facility/pages'), { recursive: true })
    await copyFile(join(ROOT, 'collect/disabled-facility.mjs'), join(dir, 'collect/disabled-facility.mjs'))
    await copyFile(join(ROOT, 'lib/log.mjs'), join(dir, 'lib/log.mjs'))

    // 받은 척할 한 쪽. 행이 있어야 "받았다" 로 센다 (S15P21E201-1212)
    await writeFile(
      join(dir, 'data/raw/facility/pages/page-0001.xml'),
      '<?xml version="1.0"?><facInfoList><totalCount>3000</totalCount>' +
        '<servList><faclNm>시험용</faclNm></servList></facInfoList>',
    )
    const script = join(dir, 'collect/disabled-facility.mjs')

    // ① 키가 없어도 받은 쪽 수를 낸다
    const out = execFileSync(process.execPath, [script, '--status'], { encoding: 'utf8' })
    if (!/받은 페이지 1개 \/ 전체 3개/.test(out)) throw new Error('--status 출력이 기대와 다르다:\n' + out)
    if (!/남은 페이지 2개/.test(out)) throw new Error('남은 쪽을 안 세었다:\n' + out)
    ok('키 없이 --status — 받은 1쪽 / 전체 3쪽, 남은 2쪽')

    // ② 그래도 실제 수집은 멈춘다. 여기가 뚫리면 키 없이 호출을 시도하게 된다
    let code = 0
    try { execFileSync(process.execPath, [script], { stdio: 'pipe' }) }
    catch (e) { code = e.status }
    if (code !== 2) throw new Error('키 없이 수집이 종료 코드 ' + code + ' 로 끝났다 — 2 여야 한다')
    ok('키 없이 수집 — 종료 코드 2 로 멈춘다')
  } finally {
    await rm(dir, { recursive: true, force: true })
  }
})

await t('쪽이 남았으면 마침 줄이 그렇게 말한다 (S15P21E201-1289)', async () => {
  // 🔴 -1212 가 donePages() 의 반환을 집합에서 {done, broken} 으로 바꾸면서 마침 줄 쪽을
  //    안 고쳤다. now.size 가 undefined 가 되고 남은 쪽이 NaN 이 되는데, NaN 은 거짓이라
  //    if (left) 가 언제나 거짓이 된다 — 쪽이 남아도 "🟢 전량 받았습니다" 로 갔다.
  //
  // 🔴 화면의 undefined 보다 그 판정이 나쁘다. 하루 한도가 있는 수집에서 "다 받았다" 는
  //    거짓말은 사람을 다음 단계로 보낸다.
  //
  // 오늘 몫을 0 으로 주면 호출을 한 번도 안 하고 곧장 마침 줄로 간다 — 하루 한도를
  // 쓰지 않고 "쪽이 남았는데 몫이 떨어진" 상황을 그대로 만들 수 있다.
  const dir = await mkdtemp(join(tmpdir(), 'bigdata-finish-'))
  try {
    await mkdir(join(dir, 'collect'), { recursive: true })
    await mkdir(join(dir, 'lib'), { recursive: true })
    await mkdir(join(dir, 'data/raw/facility/pages'), { recursive: true })
    await copyFile(join(ROOT, 'collect/disabled-facility.mjs'), join(dir, 'collect/disabled-facility.mjs'))
    await copyFile(join(ROOT, 'lib/log.mjs'), join(dir, 'lib/log.mjs'))
    // 몫이 0 이라 호출을 안 하므로 이 값은 쓰이지 않는다. 키 검사만 지나가면 된다
    await writeFile(join(dir, '.env'), 'DATA_GO_KR_KEY=not-used-budget-is-zero\n')
    // 전체 3쪽 중 1쪽만 받은 상태
    await writeFile(
      join(dir, 'data/raw/facility/pages/page-0001.xml'),
      '<?xml version="1.0"?><facInfoList><totalCount>3000</totalCount>' +
        '<servList><faclNm>시험용</faclNm></servList></facInfoList>',
    )

    const out = execFileSync(
      process.execPath,
      [join(dir, 'collect/disabled-facility.mjs'), '--budget', '0'],
      { encoding: 'utf8' },
    )
    if (/undefined/.test(out)) throw new Error('마침 줄에 undefined 가 찍혔다:\n' + out)
    if (!/받은 페이지 1\/3/.test(out)) throw new Error('받은 쪽수를 제대로 안 찍었다:\n' + out)
    if (!/남은 페이지 2개/.test(out)) throw new Error('남은 쪽을 안 알렸다:\n' + out)
    if (/전량 받았습니다/.test(out)) throw new Error('쪽이 2개 남았는데 전량 받았다고 말한다:\n' + out)
    ok('몫 소진 · 1/3 받음 — 남은 2개를 알리고, 전량이라 말하지 않는다')
    // 🔴 반대쪽 극단. if (left) 는 0 이 아니기만 하면 참이라 음수도 통과한다 —
    //    파일이 예상보다 많으면 "남은 페이지 -2개" 가 찍힌다.
    //    전체 건수를 1,000(=1쪽)으로 낮추고 3장을 두면 left 가 -2 가 된다.
    const page = (n, total) =>
      writeFile(
        join(dir, 'data/raw/facility/pages/page-000' + n + '.xml'),
        '<?xml version="1.0"?><facInfoList><totalCount>' + total + '</totalCount>' +
          '<servList><faclNm>시험용</faclNm></servList></facInfoList>',
      )
    await page(1, 1000)
    await page(2, 1000)
    await page(3, 1000)

    const more = execFileSync(
      process.execPath,
      [join(dir, 'collect/disabled-facility.mjs'), '--budget', '0'],
      { encoding: 'utf8' },
    )
    if (/남은 페이지 -/.test(more)) throw new Error('받은 것이 전체보다 많은데 남은 쪽을 음수로 말한다:\n' + more)
    if (/undefined/.test(more)) throw new Error('마침 줄에 undefined 가 찍혔다:\n' + more)
    ok('전체보다 많이 받았을 때 — 음수를 남은 쪽이라 말하지 않는다')
  } finally {
    await rm(dir, { recursive: true, force: true })
  }
})

console.log(failed ? `\n🔴 ${failed}건 실패` : '\n전부 통과')
process.exit(failed ? 1 : 0)
