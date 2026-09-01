#!/usr/bin/env node
/**
 * BIMS 폴러를 켜기 전에 돌리는 검사 — 한도를 넘는 설정으로 켜는 것을 막는다
 *
 * 왜 코드로 막나:
 *   일일 트래픽 한도를 넘기면 그 계정은 그날 남은 시간 동안 **아무것도 못 받는다.**
 *   실시간 데이터는 소급 수집이 안 되므로 그렇게 날린 시간은 영영 없다.
 *   그런데 넘겼는지는 넘긴 뒤에야 알 수 있다 — 응답이 오류로 바뀌는 것으로.
 *   그래서 켜기 전에 산수를 해서 거절한다.
 *
 *   이 저장소는 문서로 부탁하지 않고 코드로 막는다.
 *   `collect/overpass.mjs` 에 면적 상한 200km2 가 걸려 있는 것과 같은 장치다.
 *
 * 무엇을 보나:
 *   1. .env 에 키와 요청주소가 있는가
 *   2. BIMS_ROUTES · BIMS_INTERVAL_MS 로 계산한 하루 호출 수가 한도 안인가
 *   3. 그 노선들이 실제로 있는 노선인가, 중복이 없는가
 *   4. 내 이름이 config/bims-assign.json 에 있고, 내 .env 가 배정과 같은가
 *
 * 판정은 종료 코드다.  0 = 켜도 된다 · 2 = 거절 · 1 = 검사 자체가 실패
 *
 *   node collect/bims-preflight.mjs
 *   node collect/bims-preflight.mjs --as masdf13     # 내가 누구인지 직접 지정
 */
import { readFile } from 'node:fs/promises'
import { existsSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const ROOT = join(dirname(fileURLToPath(import.meta.url)), '..')

/** 🔴 이 값들은 process/bims-assign.mjs 와 같아야 한다. 산출물에서 읽어 대조한다 —
 *  두 곳에 손으로 적어 두면 한쪽만 바뀌는 날이 온다. */
const FALLBACK = { DAILY_LIMIT: 10000, SAFETY: 0.75, WORK_HOURS: 9 }

const args = process.argv.slice(2)
const AS   = args.includes('--as') ? args[args.indexOf('--as') + 1] : null

const ok   = (...a) => console.log('  ✅', ...a)
const warn = (...a) => console.log('  ⚠ ', ...a)
const bad  = (...a) => console.log('  🔴', ...a)

async function loadEnv() {
  const p = join(ROOT, '.env')
  if (!existsSync(p)) return false
  for (const line of (await readFile(p, 'utf8')).split('\n')) {
    const m = line.match(/^\s*([A-Z_][A-Z0-9_]*)\s*=\s*(.*)\s*$/)
    if (m && !process.env[m[1]]) process.env[m[1]] = m[2].replace(/^["']|["']$/g, '')
  }
  return true
}

async function main() {
  console.log('BIMS 폴러 켜기 전 검사')
  console.log('─'.repeat(64))
  let refuse = false

  /* ── 1. .env ── */
  console.log('1. .env')
  if (!await loadEnv()) {
    bad('bigData/.env 가 없습니다.  cp .env.example .env  로 만들고 채우십시오.')
    process.exit(2)
  }
  // 🔴 키 값을 찍지 않는다. 있는지만 말한다.
  if (!process.env.DATA_GO_KR_KEY) {
    bad('DATA_GO_KR_KEY 가 비어 있습니다.')
    bad('  https://www.data.go.kr 회원가입 → "부산광역시_부산버스정보시스템" 활용신청')
    bad('  → 마이페이지의 **일반 인증키(Decoding)** 를 넣습니다. docs/SOURCES.md 1번.')
    refuse = true
  } else ok(`DATA_GO_KR_KEY 있음 (${process.env.DATA_GO_KR_KEY.length}자)`)

  if (!process.env.BIMS_ENDPOINT) {
    bad('BIMS_ENDPOINT 가 비어 있습니다. 요청주소는 지어내지 않습니다.')
    bad('  활용신청 상세의 End Point + 오퍼레이션 경로를 그대로 붙입니다.')
    bad('  예) https://apis.data.go.kr/6260000/BusanBIMS/busInfoByRouteId')
    refuse = true
  } else {
    ok(`BIMS_ENDPOINT ${process.env.BIMS_ENDPOINT}`)
    if (!/busInfoByRouteId/.test(process.env.BIMS_ENDPOINT)) {
      warn('요청주소가 busInfoByRouteId 가 아닙니다. 노선별 실시간 위치를 주는 오퍼레이션은 그것입니다.')
      warn('  다른 오퍼레이션이면 저장되는 내용이 달라지고, 그건 나중에 못 되돌립니다.')
    }
  }

  /* ── 2. 배정표 ── */
  console.log('2. 배정표')
  const assignPath = join(ROOT, 'config/bims-assign.json')
  let limits = { ...FALLBACK }, assign = null
  if (!existsSync(assignPath)) {
    bad('config/bims-assign.json 이 없습니다. 배정 없이 켜면 남과 같은 노선을 볼 수 있습니다.')
    bad('  만들려면: node process/bims-score.mjs && node process/bims-assign.mjs')
    refuse = true
  } else {
    assign = JSON.parse(await readFile(assignPath, 'utf8'))
    limits.DAILY_LIMIT = assign.상수?.일일한도 ?? FALLBACK.DAILY_LIMIT
    limits.SAFETY      = assign.상수?.안전여유 ?? FALLBACK.SAFETY
    ok(`배정표 있음 (${assign.배정?.length ?? 0}자리, 생성 ${String(assign.generatedAt).slice(0, 19)})`)
  }
  const BUDGET = Math.floor(limits.DAILY_LIMIT * limits.SAFETY)

  /* ── 3. 노선 목록 ── */
  console.log('3. BIMS_ROUTES')
  const raw = (process.env.BIMS_ROUTES || '').trim()
  if (!raw) {
    bad('BIMS_ROUTES 가 비어 있습니다.')
    bad('  비워 두면 폴러가 config/routes.json 의 노선을 씁니다 — 그것은 야간 담당의 몫이라')
    bad('  낮 담당자가 그대로 켜면 **같은 노선을 두 번 폴링**하게 됩니다.')
    bad('  config/bims-assign.json 에서 내 줄의 BIMS_ROUTES 를 그대로 복사해 넣으십시오.')
    process.exit(2)
  }
  const routes = raw.split(',').map(s => s.trim()).filter(Boolean)
  ok(`${routes.length}노선`)

  const dup = routes.filter((r, i) => routes.indexOf(r) !== i)
  if (dup.length) {
    bad(`중복된 노선이 있습니다: ${[...new Set(dup)].join(', ')}`)
    bad('  중복 폴링은 한도만 태우고 새 정보를 주지 않습니다.')
    refuse = true
  }

  const allPath = join(ROOT, 'config/routes-all.json')
  if (existsSync(allPath)) {
    const known = new Set(JSON.parse(await readFile(allPath, 'utf8')).routes.map(r => r.lineid))
    const unknown = routes.filter(r => !known.has(r))
    if (unknown.length) {
      bad(`부산 노선 목록에 없는 값: ${unknown.join(', ')}`)
      bad('  오타면 그 노선은 하루 종일 오류만 쌓습니다. config/routes-all.json 에서 확인하십시오.')
      refuse = true
    } else ok('모두 실제 노선입니다')
  } else warn('config/routes-all.json 이 없어 노선 존재 확인을 건너뜁니다')

  /* ── 4. 한도 산수 ── */
  console.log('4. 한도')
  const intervalMs = Number(process.env.BIMS_INTERVAL_MS || 30000)
  if (!Number.isFinite(intervalMs) || intervalMs < 1000) {
    bad(`BIMS_INTERVAL_MS 가 이상합니다: ${process.env.BIMS_INTERVAL_MS}`)
    process.exit(2)
  }
  const intervalS   = intervalMs / 1000
  const perHour     = routes.length * (3600 / intervalS)
  const workHours   = limits.WORK_HOURS
  const workCalls   = Math.round(perHour * workHours)
  const allDayCalls = Math.round(perHour * 24)

  console.log(`   주기 ${intervalS}초 · ${routes.length}노선 → 시간당 ${Math.round(perHour)}회`)
  console.log(`   근무 ${workHours}시간 가동   ${workCalls}회  (안전여유 예산 ${BUDGET} · 절대 한도 ${limits.DAILY_LIMIT})`)
  console.log(`   24시간 가동      ${allDayCalls}회`)

  const maxRoutesWork = Math.floor(BUDGET / ((3600 / intervalS) * workHours))
  if (workCalls > BUDGET) {
    bad(`근무 ${workHours}시간만 돌려도 안전여유 예산 ${BUDGET}회를 넘습니다 (${workCalls}회).`)
    bad(`  주기 ${intervalS}초라면 노선은 **${maxRoutesWork}개까지** 됩니다.`)
    bad(`  노선 ${routes.length}개를 유지하려면 주기를 ${Math.ceil(routes.length * 3600 * workHours / BUDGET)}초 이상으로 늘려야 합니다.`)
    bad('  🔴 주기를 늘리면 정류소 간 소요시간의 해상도가 떨어집니다. 노선을 줄이는 쪽이 낫습니다 —')
    bad('     근거는 config/bims-assign.json 의 "추천" 항목에 있습니다.')
    refuse = true
  } else {
    ok(`근무 ${workHours}시간 가동은 예산 안입니다 (${(workCalls / BUDGET * 100).toFixed(0)}%)`)
    // 🔴 노트북을 밤새 켜 두면 넘어간다. 언제 넘는지를 시각으로 알려 준다.
    const hoursToBudget = BUDGET / perHour
    const hoursToLimit  = limits.DAILY_LIMIT / perHour
    if (allDayCalls > BUDGET) {
      warn(`24시간 켜 두면 넘어갑니다. 안전여유 예산은 ${hoursToBudget.toFixed(1)}시간, `
         + `절대 한도는 ${hoursToLimit.toFixed(1)}시간에 닿습니다.`)
      warn(`  09:00 에 켰다면 각각 약 ${fmt(9 + hoursToBudget)} · ${fmt(9 + hoursToLimit)} 입니다.`)
      warn('  밤에도 켜 둘 생각이면 노선을 줄이거나 야간 담당과 겹치지 않는지 확인하십시오.')
    } else ok('24시간 켜 두어도 예산 안입니다')
  }

  /* ── 5. 내 배정과 같은가 ── */
  console.log('5. 내 배정')
  if (assign) {
    const rows = assign.배정 ?? []
    const nightRoutes = assign.야간?.지금하고있는것?.노선 ?? []
    /** 그 사람이 돌려야 하는 노선 = 배정된 낮 노선 + (야간 담당이면) 야간 노선.
     *  🔴 야간 담당은 낮 노선이 0개다(한도 산수가 그렇게 나온다). 그래서 낮 목록만
     *     비교하면 실제로 돌리고 있는 야간 5노선이 "남의 몫" 으로 잘못 잡힌다. */
    const expected = r => [
      ...(r.BIMS_ROUTES || '').replace(/^BIMS_ROUTES=/, '').split(',').filter(Boolean),
      ...(r.야간담당 ? nightRoutes : []),
    ]
    const mine = AS ? rows.find(r => String(r.담당) === AS)
                    : rows.find(r => {
                        const a = expected(r)
                        return a.length === routes.length && a.every(x => routes.includes(x))
                      })
    if (!mine) {
      if (AS) {
        bad(`배정표에 "${AS}" 라는 담당이 없습니다.`)
        bad(`  있는 담당: ${rows.map(r => r.담당).join(', ')}`)
        bad('  이름이 빠져 있으면 배정을 다시 만들어야 합니다:')
        bad('    node process/bims-assign.mjs --members <쉼표로 6명> --night <야간담당>')
      } else {
        bad('내 BIMS_ROUTES 와 똑같은 배정 줄을 찾지 못했습니다.')
        bad(`  --as <내이름> 으로 알려 주십시오. 있는 담당: ${rows.map(r => r.담당).join(', ')}`)
        bad('  🔴 배정과 다른 노선을 돌리면 남과 겹칠 수 있고, 겹친 만큼은 버리는 예산입니다.')
      }
      refuse = true
    } else {
      const want = expected(mine)
      const missing = want.filter(r => !routes.includes(r))
      const extra   = routes.filter(r => !want.includes(r))
      if (String(mine.담당).startsWith('(미정')) {
        bad(`"${mine.담당}" 는 아직 사람 이름이 안 들어간 자리입니다.`)
        bad('  배정표에 실제 이름을 넣고 다시 만드십시오 — 누가 무엇을 돌리는지 남지 않으면')
        bad('  나중에 빈 노선을 아무도 못 찾습니다.')
        refuse = true
      } else ok(`담당 ${mine.담당}${mine.야간담당 ? ' (야간담당)' : ''}`)
      if (missing.length) { bad(`배정에 있는데 내 .env 에 없는 노선: ${missing.join(', ')}`); refuse = true }
      if (extra.length)   { bad(`내 .env 에만 있는 노선: ${extra.join(', ')} — 남의 몫일 수 있습니다`); refuse = true }
      if (!missing.length && !extra.length) ok('.env 가 배정과 정확히 같습니다')
    }

    if (assign.명단?.미충원 > 0) {
      warn(`배정표에 ${assign.명단.미충원}자리가 비어 있습니다 (명단 ${assign.명단.찾은인원}명 / 팀 ${assign.명단.팀이말한인원}명).`)
      warn('  비어 있는 자리의 노선은 아무도 안 돌리고 있습니다.')
    }
    if (assign.야간?.담당자 === '(미정)') {
      warn('야간(18~09시) 담당이 아직 미정입니다 — 사람이 정할 결정입니다.')
    }
  }

  console.log('─'.repeat(64))
  if (refuse) {
    console.log('🔴 거절. 위 항목을 고친 뒤 다시 돌리십시오. 폴러를 켜지 마십시오.')
    process.exit(2)
  }
  console.log('✅ 통과. 이제 켜십시오:   npm run collect:bims')
  console.log('   🔴 노트북이 절전으로 들어가면 수집도 멈춥니다. 절전을 끄십시오.')
}

const fmt = h => {
  const t = ((h % 24) + 24) % 24
  return `${String(Math.floor(t)).padStart(2, '0')}:${String(Math.round((t % 1) * 60)).padStart(2, '0')}`
}

main().catch(e => { console.error('검사 자체가 실패했습니다:', e); process.exit(1) })
