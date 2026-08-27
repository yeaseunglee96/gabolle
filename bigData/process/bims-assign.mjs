#!/usr/bin/env node
/**
 * BIMS 수집 분산 배정 — 노선을 팀원 노트북에 나눈다
 *
 * 왜 나누나:
 *   공공데이터포털의 일일 트래픽 한도는 **계정마다, 그리고 오퍼레이션마다** 따로다.
 *   오퍼레이션(operation) = 이 API 가 제공하는 개별 기능 하나. 부산 BIMS 에는 6개가
 *   있고 각각이 자기 몫의 10,000회를 가진다. 한 사람이 한 키로 돌리면 그 한 사람의
 *   한도가 곧 팀 전체의 한도지만, 여섯 사람이 각자 키를 받으면 예산이 여섯 배가 된다.
 *
 *   그리고 노트북은 하루 종일 켜져 있지 않다. 09~18시만 돌리면 하루 1,440 사이클이
 *   아니라 540 사이클이다. 🔴 **그래서 1인당 노선 수가 하루 종일 돌릴 때보다 오히려
 *   늘어난다.** 이 산수를 사람이 암산하지 않고 코드가 한다.
 *
 * 🔴 네트워크를 쓰지 않는다 (bigData/CLAUDE.md 4절 — process/ 는 계산만).
 *
 *   node process/bims-score.mjs      # 먼저 — 점수가 있어야 나눌 수 있다
 *   node process/bims-assign.mjs
 *   node process/bims-assign.mjs --members a,b,c,d,e,f --night a
 *
 * 출력: config/bims-assign.json
 */
import { readFile, writeFile, mkdir } from 'node:fs/promises'
import { existsSync } from 'node:fs'
import { dirname, join } from 'node:path'
import { fileURLToPath } from 'node:url'

const ROOT  = join(dirname(fileURLToPath(import.meta.url)), '..')
const REPO  = join(ROOT, '..')
const OUT   = join(ROOT, 'config/bims-assign.json')

/* ═══════════════════════════════════════════════════════════════════════════
   한도 산수의 상수 — 🔴 왜 이 값인지가 각 줄에 붙어 있어야 한다.
   숫자만 있고 근거가 없으면 다음 사람이 그 값을 못 바꾼다 (바꿔도 되는지 모르므로).
   ═══════════════════════════════════════════════════════════════════════════ */

/** 근무시간. 팀원이 노트북을 켜 두는 시간대. 🔴 가정이 아니라 팀이 정한 값이고,
 *  바뀌면 여기만 고치면 된다. 18시 이후는 아래 야간 항목에서 따로 다룬다. */
const WORK_START_H = 9
const WORK_END_H   = 18
const WORK_SECONDS = (WORK_END_H - WORK_START_H) * 3600      // 32,400초

/** 폴링 주기. 🔴 60초를 고른 이유는 아래 PLANS 비교와 추천 근거에 적혀 있다.
 *  이미 이틀치를 60초로 모았으므로 바꾸면 기존 데이터와 비교가 안 된다는 이유도 있다. */
const INTERVAL_S = 60

/** 일일 트래픽 한도. data.go.kr 활용신청 상세 화면의 값이다.
 *  🔴 **계정당이 아니라 오퍼레이션(상세기능)당** 10,000회다 — 2026-08-28 포털에서 직접 확인. */
const DAILY_LIMIT = 10000

/** 안전여유. config/routes.json 이 "한도 10000 의 75%" 로 5노선을 역산했다.
 *  그 기준을 이어받는다 — 재시도·시계오차·중복 실행이 한도를 넘기지 않게 하는 몫이다. */
const SAFETY = 0.75
const BUDGET = Math.floor(DAILY_LIMIT * SAFETY)               // 7,500회/사람/오퍼레이션/일

/** 하루 종일(24시간) 돌릴 때의 노선당 호출 수. 야간 담당 산수에 쓴다. */
const ALLDAY_SECONDS = 24 * 3600

/** 팀이 말한 인원. 명단(governance/policy.json)이 이보다 적으면 그 사실을 산출물에 남긴다.
 *  🔴 없는 사람을 지어내지 않는다 — 빈 자리는 빈 자리로 남긴다. */
const EXPECTED_TEAM = 6

/* ═══════════════════════════════════════════════════════════════════════════ */

const args = process.argv.slice(2)
const arg  = k => args.includes(k) ? args[args.indexOf(k) + 1] : null
const log  = (...a) => console.log(a.join(' '))

const callsPerRoutePerDay = interval => Math.floor(WORK_SECONDS / interval)
const maxRoutes = interval => Math.floor(BUDGET / callsPerRoutePerDay(interval))

async function main() {
  const scorePath = join(ROOT, 'data/staged/_bims-route-score.json')
  const policyPath = join(REPO, 'governance/policy.json')
  const currentPath = join(ROOT, 'config/routes.json')

  // 🔴 입력이 없으면 조용히 통과하지 않는다.
  if (!existsSync(scorePath)) {
    console.error('🔴 data/staged/_bims-route-score.json 이 없습니다. 점수 없이 나누면 근거가 없습니다.')
    console.error('   먼저: node process/bims-score.mjs')
    process.exit(2)
  }
  const score = JSON.parse(await readFile(scorePath, 'utf8'))
  const ranked = score.routes.filter(r => r.score != null)
  if (ranked.length === 0) { console.error('🔴 점수가 매겨진 노선이 0개입니다.'); process.exit(2) }

  /* ── 명단 ── */
  let members = arg('--members')?.split(',').map(s => s.trim()).filter(Boolean) ?? null
  let rosterSource = '--members 인자'
  if (!members) {
    if (!existsSync(policyPath)) {
      console.error('🔴 governance/policy.json 이 없고 --members 도 없습니다. 팀원을 지어내지 않습니다.')
      process.exit(2)
    }
    const policy = JSON.parse(await readFile(policyPath, 'utf8'))
    members = (policy.voters ?? []).map(v => v.id)
    rosterSource = 'governance/policy.json 의 voters'
  }
  if (members.length === 0) {
    console.error('🔴 명단이 비었습니다. 나눌 대상이 없습니다.')
    process.exit(2)
  }

  /* ── 야간 담당 ── */
  const nightId = arg('--night')
  const current = existsSync(currentPath) ? JSON.parse(await readFile(currentPath, 'utf8')) : null
  const nightRoutes = current?.routes ?? []
  const nightCalls = nightRoutes.length * Math.floor(ALLDAY_SECONDS / INTERVAL_S)
  const nightLeftover = BUDGET - nightCalls
  const nightHolderDayRoutes = Math.max(0, Math.floor(nightLeftover / callsPerRoutePerDay(INTERVAL_S)))

  /* ── 자리 수 ──
     🔴 두 안의 비교와 실제 배정이 **같은 인원 수**를 써야 한다. 안 그러면 표에 적힌
        커버리지와 실제로 나눠 준 노선 수가 어긋난다. 명단이 기대 인원보다 적어도
        빈 자리를 포함해 센다 — 그 자리는 채워질 자리이고, 비교는 채워진 뒤를 말한다. */
  const 미충원 = Math.max(0, EXPECTED_TEAM - members.length)
  const totalSlots = members.length + 미충원
  const dayWorkers = Math.max(1, totalSlots - (nightId ? 1 : 0))

  /* ── 두 안 ──
     전 노선을 덮으려면 주기를 늘려야 한다. 무엇이 맞는지 근거를 대고 판정한다.
     두 안을 다 계산해서 나란히 놓는다. */
  const planA_routes = maxRoutes(INTERVAL_S)
  const planB_perPerson = Math.ceil(score.입력.노선 / dayWorkers)
  const planB_interval = Math.ceil(WORK_SECONDS * planB_perPerson / BUDGET)

  const σ = i => +(i / Math.sqrt(6)).toFixed(1)   // 양자화 오차의 표준편차 = T/√6

  const PLANS = [
    {
      안: 'A — 일부 노선 고해상도',
      주기초: INTERVAL_S,
      '1인노선수': planA_routes,
      '덮는노선수': Math.min(planA_routes * dayWorkers, score.입력.노선),
      '커버리지%': +(Math.min(planA_routes * dayWorkers, score.입력.노선) / score.입력.노선 * 100).toFixed(1),
      '1인일일호출': planA_routes * callsPerRoutePerDay(INTERVAL_S),
      '양자화오차초': σ(INTERVAL_S),
    },
    {
      안: 'B — 전 노선 저해상도',
      주기초: planB_interval,
      '1인노선수': planB_perPerson,
      '덮는노선수': score.입력.노선,
      '커버리지%': 100,
      '1인일일호출': planB_perPerson * callsPerRoutePerDay(planB_interval),
      '양자화오차초': σ(planB_interval),
    },
  ]

  const 추천 = {
    고른안: 'A',
    근거: [
      '🔴 **해상도는 되돌릴 수 없는 방향이다.** 60초로 모아 두면 나중에 언제든 '
      + '3분 간격으로 다운샘플링(표본을 솎아 저해상도로 만드는 것)할 수 있다. '
      + '반대는 안 된다 — 3분으로 모은 것에서 60초를 만들 수 없고, 실시간 데이터는 '
      + '소급 수집이 안 되므로 다시 받을 수도 없다. 커버리지는 나중에 사람을 늘리거나 '
      + '노선을 추가해 되찾을 수 있지만 해상도는 영영 못 되찾는다.',

      '우리가 만들려는 것은 평균이 아니라 **분포**다 (collect/bims-poll.mjs 머리말). '
      + '주기 T 로 폴링하면 버스가 정류소를 지난 시각을 T 안쪽으로만 알 수 있고, '
      + '구간 소요시간은 양쪽 끝의 오차를 함께 받으므로 **양자화 오차의 표준편차가 T/√6** 이다. '
      + `A안은 ${σ(INTERVAL_S)}초, B안은 ${σ(planB_interval)}초다.`,

      `도시부 정류소 간격은 보통 400m 안팎이고 소요시간은 1~2분이다. B안의 오차 `
      + `${σ(planB_interval)}초는 **재려는 값 자체보다 크다.** 그러면 구간 하나는 아예 못 재고 `
      + '여러 정류소를 묶은 구간만 볼 수 있는데, 묶는 순간 우리가 찾던 구간별 꼬리(최악값)가 '
      + '평균에 씻겨 사라진다.',

      '🔴 다만 정직하게: 실제 구간 소요시간의 분산은 **아직 안 재봤다.** 재려면 '
      + '구간 추출 파이프라인이 필요하고 그건 아직 없다. 그래서 "B안이 확실히 부족하다" 가 '
      + '아니라 **"A안은 나중에 재보고 결정할 수 있게 두고, B안은 그 선택지를 없앤다"** 가 '
      + '이 추천의 근거다. 모를 때는 되돌릴 수 있는 쪽을 고른다.',

      '부수적으로: 이미 이틀치를 60초로 모았다. 주기를 바꾸면 그 이틀과 이후가 '
      + '같은 잣대로 비교되지 않는다.',

      '커버리지 손실은 무작위가 아니다 — data/staged/_bims-route-score.json 이 매긴 '
      + '관광중요도 상위부터 덮으므로, 빠지는 것은 외국인 이동과 가장 먼 노선들이다.',
    ],
  }

  /* ── 배정 ──
     점수 높은 순으로 사람에게 돌아가며 나눈다(라운드로빈). 정렬된 목록에서 하나씩
     떼어 주므로 🔴 **같은 노선이 두 사람에게 갈 수 없다** — 중복 폴링은 한도만 태우고
     새 정보를 안 준다. 야간에 이미 도는 노선도 낮 후보에서 뺀다(같은 이유). */
  const nightSet = new Set(nightRoutes)
  const pool = ranked.filter(r => !nightSet.has(r.lineid))

  const dayMembers = members.filter(m => m !== nightId)
  const slots = []
  for (const id of members) {
    const isNight = id === nightId
    slots.push({ id, 야간담당: isNight, 한도: isNight ? nightHolderDayRoutes : planA_routes, routes: [] })
  }
  // 빈 자리 — 명단이 기대 인원보다 적을 때. 사람을 지어내지 않고 자리만 남긴다.
  for (let i = 0; i < 미충원; i++) {
    slots.push({ id: `(미정-${i + 1})`, 야간담당: false, 한도: planA_routes, routes: [], 미충원: true })
  }

  const takers = slots.filter(s => s.한도 > 0)
  let idx = 0
  for (const r of pool) {
    if (takers.every(s => s.routes.length >= s.한도)) break
    for (let n = 0; n < takers.length; n++) {
      const s = takers[(idx + n) % takers.length]
      if (s.routes.length < s.한도) {
        s.routes.push(r); idx = (idx + n + 1) % takers.length; break
      }
    }
  }

  const assigned = new Set(slots.flatMap(s => s.routes.map(r => r.lineid)))
  // 🔴 불변식: 같은 노선이 두 번 나가지 않았는가. 어기면 조용히 넘어가지 않는다.
  const totalDealt = slots.reduce((n, s) => n + s.routes.length, 0)
  if (totalDealt !== assigned.size) {
    console.error(`🔴 중복 배정이 생겼습니다: ${totalDealt}건 배정 / 고유 ${assigned.size}개`)
    process.exit(1)
  }

  const 배정 = slots.map(s => ({
    담당: s.id,
    ...(s.미충원 ? { '🔴': '명단에 없는 자리다. 사람이 채워야 한다.' } : {}),
    야간담당: s.야간담당,
    노선수: s.routes.length,
    '하루예상호출': s.routes.length * callsPerRoutePerDay(INTERVAL_S)
                  + (s.야간담당 ? nightCalls : 0),
    '한도대비%': +(((s.routes.length * callsPerRoutePerDay(INTERVAL_S)
                  + (s.야간담당 ? nightCalls : 0)) / DAILY_LIMIT) * 100).toFixed(1),
    BIMS_ROUTES: 'BIMS_ROUTES=' + s.routes.map(r => r.lineid).join(','),
    BIMS_INTERVAL_MS: `BIMS_INTERVAL_MS=${INTERVAL_S * 1000}`,
    노선: s.routes.map(r => ({ lineid: r.lineid, num: r.num, type: r.type, 점수: r.score,
      관광POI인접정류소비율: r.destFrac, focus비율: r.focusFrac, 배차: r.headway,
      첫차: r.first, 막차: r.last, 구간: `${r.start}→${r.end}` })),
  }))

  const out = {
    generatedAt: new Date().toISOString(),
    '이 파일이 근거다': '수치는 문서에 적지 않는다. docs/BIMS-FLEET.md 는 왜 이렇게 나눴는지만 '
      + '설명하고, 얼마나·몇 개는 전부 여기 있다. 배정이 바뀌면 이 파일만 다시 만든다.',

    상수: {
      근무시간: `${WORK_START_H}:00–${WORK_END_H}:00`,
      근무초: WORK_SECONDS,
      주기초: INTERVAL_S,
      일일한도: DAILY_LIMIT,
      '한도의 단위': '🔴 계정당이 아니라 **오퍼레이션(상세기능)당**. 부산 BIMS 에는 6개가 있고 '
        + '각각 10,000회다. 노선별 실시간 위치는 /busInfoByRouteId 하나에서만 나오므로 '
        + '이 배정이 쓰는 예산은 그 오퍼레이션의 10,000회다.',
      안전여유: SAFETY,
      '안전여유 근거': 'config/routes.json 이 "한도 10000 의 75%" 로 5노선을 역산했다. 그 기준을 잇는다.',
      '1인예산': BUDGET,
      '노선당 하루 호출': callsPerRoutePerDay(INTERVAL_S),
      '1인 최대 노선수': planA_routes,
    },

    산수: `노선수 × (근무초 ÷ 주기초) ≤ 한도 × 안전여유  →  `
        + `노선수 × (${WORK_SECONDS} ÷ ${INTERVAL_S}) ≤ ${DAILY_LIMIT} × ${SAFETY}  →  `
        + `노선수 × ${callsPerRoutePerDay(INTERVAL_S)} ≤ ${BUDGET}  →  노선수 ≤ ${planA_routes}`,

    '왜 근무시간만 돌리면 노선이 늘어나나':
      `하루 종일(24시간) 돌리면 노선당 ${Math.floor(ALLDAY_SECONDS / INTERVAL_S)}회를 쓰므로 `
      + `1인 ${Math.floor(BUDGET / Math.floor(ALLDAY_SECONDS / INTERVAL_S))}노선이 한계다. `
      + `${WORK_START_H}~${WORK_END_H}시만 돌리면 노선당 ${callsPerRoutePerDay(INTERVAL_S)}회라 `
      + `1인 ${planA_routes}노선이 된다. 같은 한도로 ${planA_routes - Math.floor(BUDGET / Math.floor(ALLDAY_SECONDS / INTERVAL_S))}노선이 늘어난다 — `
      + '노트북이 꺼져 있는 시간이 예산을 안 쓰기 때문이다.',

    두안비교: PLANS,
    추천,

    명단: {
      출처: rosterSource,
      찾은인원: members.length,
      팀이말한인원: EXPECTED_TEAM,
      미충원,
      '🔴': 미충원 > 0
        ? `명단에서 ${members.length}명을 찾았고 팀은 ${EXPECTED_TEAM}명이다. `
          + `${미충원}자리를 "(미정-n)" 으로 비워 두었다. governance/policy.json 의 voters 는 `
          + '이 저장소에 커밋 이력이 있는 사람만 담는 명단이라 아직 커밋이 없는 팀원이 빠져 있다. '
          + '사람이 이름을 넣어 다시 돌려야 한다: node process/bims-assign.mjs --members a,b,c,d,e,f --night a'
        : '명단 인원과 팀 인원이 일치한다.',
      명단: members,
    },

    야간: {
      상태: '🔴 미결 — 사람이 정한다',
      질문: `${WORK_END_H}시~${WORK_START_H}시를 누가, 어떤 노선으로 덮을 것인가`,
      왜중요한가: '퇴근 후 러시아워(18~20시)가 오히려 소요시간의 분산이 큰 구간이다. '
        + '낮만 덮으면 하루 중 가장 변동이 큰 시간대가 통째로 빈다. '
        + '그런데 그 시간에 노트북을 켜 두는 것은 사람의 생활 문제라 코드가 정할 수 없다.',
      지금하고있는것: {
        노선: nightRoutes,
        설명: `config/routes.json 의 ${nightRoutes.length}노선을 24시간 계속 돌리고 있다. 그대로 둔다.`,
        하루호출: nightCalls,
        '한도대비%': +(nightCalls / DAILY_LIMIT * 100).toFixed(1),
        BIMS_ROUTES: 'BIMS_ROUTES=' + nightRoutes.join(','),
      },
      담당자: nightId ?? '(미정)',
      '담당자 근거': nightId
        ? `--night ${nightId} 로 지정됨. 지금 이 5노선을 실제로 돌리고 있는 PC 의 담당자라는 관찰에 근거한다. 확정은 사람이 한다.`
        : '지정되지 않음. --night <id> 로 지정한다.',
      '🔴 산수': `24시간 ${nightRoutes.length}노선 = ${nightCalls}회로 1인예산 ${BUDGET} 의 `
        + `${(nightCalls / BUDGET * 100).toFixed(0)}% 를 이미 쓴다. 남는 ${nightLeftover}회로는 `
        + `낮 노선을 ${nightHolderDayRoutes}개밖에 못 돌린다. `
        + '**야간을 맡은 사람에게 낮 노선을 같이 주면 한도를 넘긴다.** 그래서 0개로 잡았다.',
      사람이정할것: [
        `야간을 계속 ${nightRoutes.length}노선으로 둘 것인가, 아니면 18~20시만 노선을 늘릴 것인가`,
        '야간 담당을 한 사람이 계속 질 것인가, 요일별로 돌릴 것인가',
        '야간 담당자는 낮 노선을 받지 않는다 — 위 산수가 그렇게 나온다. 동의하는가',
      ],
    },

    정류소도착정보: {
      결정: '🔴 이번 배정에 넣지 않는다',
      무엇인가: '오퍼레이션 /stopArrByBstopid · /bitArrByArsno 는 "정류소에 곧 올 버스" 를 준다. '
        + '노선을 따라가는 관점이 아니라 정류소에서 기다리는 관점이다. '
        + '각각 별도의 일일 10,000회 예산이라 이 배정의 예산을 전혀 안 쓴다.',
      왜안하나: [
        '① 그것을 받을 수집기가 아직 없다. 만들면 응답 검증까지 해야 하고, 지금 '
        + '되돌릴 수 없는 것은 실시간 위치(/busInfoByRouteId)다. 그쪽을 먼저 확실히 켠다.',
        '② 도착정보는 운영기관의 **예측값**이지 관측값이 아니다. 우리가 만들려는 것은 '
        + '실측 소요시간의 분포이고, 예측값은 그 검증에는 써도 원자료로는 성격이 다르다. '
        + '섞어 두면 나중에 "이 숫자가 관측인가 예측인가" 를 못 가린다.',
        '③ 🔴 다만 이것도 소급 수집이 안 된다. 미루는 것은 공짜가 아니다 — '
        + '오늘 안 받은 도착정보도 영영 없다. 그래서 "안 한다" 가 아니라 "다음" 이다.',
      ],
      '하려면 어디를 보나': 'data/staged/_bims-route-score.json 의 관광거점정류소 30곳. '
        + '관광 목적지 POI 도보권(300m) 안에 있으면서 경유 노선이 가장 많은 정류소들이고, '
        + '부산역·남포동·자갈치·서면이 위에 온다. 고른 기준은 손이 아니라 데이터다.',
      '아직 모르는 것': '파라미터 정의는 포털의 "OpenAPI활용가이드_부산버스정보시스템_v2.0.docx" 에 '
        + '있는데 아직 안 받았다. 확정하려면 그 문서를 봐야 한다. 지어내지 않는다.',
    },

    운영계정: {
      '무엇인가': 'data.go.kr 활용신청 화면의 「운영계정 신청하기」. 개발계정보다 일일 한도가 크다.',
      '상태': '신청하지 않았다. 🔴 신청은 사람이 한다 — 도구가 대신 누르지 않는다.',
      '넣으면 무엇이 바뀌나': '한도가 커지면 위 산수의 DAILY_LIMIT 만 바꾸면 되고 나머지 구조는 같다. '
        + '다만 승인까지 시간이 걸리므로 그걸 기다리며 오늘 수집을 미루면 안 된다.',
    },

    점수출처: {
      파일: 'data/staged/_bims-route-score.json',
      기준요약: score.기준?.질문,
      좌표: score.좌표출처?.방법,
      좌표커버리지: score.좌표출처?.전체좌표커버리지,
      좌표검증: score.좌표검증,
      신호무효: score.신호무효,
      편향검사판정: score.편향검사?.판정,
    },

    배정,
    미배정: {
      수: score.입력.노선 - assigned.size - nightRoutes.length,
      설명: '점수 하위 노선들이다. 사람이 늘거나 야간 정책이 정해지면 여기서 채운다.',
      상위20: pool.filter(r => !assigned.has(r.lineid)).slice(0, 20)
        .map(r => ({ lineid: r.lineid, num: r.num, 점수: r.score })),
    },
  }

  await mkdir(dirname(OUT), { recursive: true })
  await writeFile(OUT, JSON.stringify(out, null, 1))

  log('─'.repeat(64))
  log(`한도 산수  ${out.산수}`)
  log(`명단       ${members.length}명 (${rosterSource})` + (미충원 ? ` · 🔴 ${미충원}자리 미충원` : ''))
  log(`야간       ${nightRoutes.length}노선 24시간 = ${nightCalls}회 → 담당자의 낮 노선 ${nightHolderDayRoutes}개`)
  log('')
  log('두 안:')
  for (const p of PLANS) log(`  ${p.안.padEnd(22)} 주기 ${String(p.주기초).padStart(3)}초 · 1인 ${String(p['1인노선수']).padStart(2)}노선 · 커버리지 ${String(p['커버리지%']).padStart(5)}% · 양자화오차 ${p['양자화오차초']}초`)
  log(`  → 추천: ${추천.고른안}`)
  log('')
  log('배정:')
  for (const b of 배정) log(`  ${String(b.담당).padEnd(16)} ${String(b.노선수).padStart(2)}노선 · 하루 ${String(b['하루예상호출']).padStart(5)}회 · 한도의 ${b['한도대비%']}%${b.야간담당 ? '  ← 야간담당' : ''}`)
  log(`저장 ${OUT}`)

  // 🔴 한도를 넘긴 배정이 하나라도 있으면 실패로 끝낸다.
  const over = 배정.filter(b => b['하루예상호출'] > BUDGET)
  if (over.length) {
    console.error(`🔴 한도를 넘긴 배정: ${over.map(o => o.담당).join(', ')}`)
    process.exit(1)
  }
  if (미충원 > 0) log(`⚠ ${미충원}자리가 비어 있습니다. 사람 이름을 넣어 다시 돌리십시오.`)
}

main().catch(e => { console.error('치명:', e); process.exit(1) })
