// 일정 화면을 실제로 띄워 그림으로 뽑는다 (S15P21E201-1089).
//
// 왜 이 파일이 있나 — 팀 규칙 `frontend/CLAUDE.md` 의 「화면을 고쳤으면 이미지를 보여주고
// 끝낸다」를 실제로 지키려면, 로그인과 실제 데이터가 필요한 화면도 띄울 수 있어야 한다.
// 그래서 서버 응답을 **고정 자료로 가로채서** 띄운다.
//
// 🔴 여기서 나온 그림은 「이런 응답이 오면 이렇게 그려진다」는 뜻이지 **실제 데이터가 아니다.**
//    서버가 정말 이런 값을 주는지는 이 그림으로 알 수 없다.
//
// 🔴 서버 응답은 { data, error } 봉투에 싸여 온다 (src/api/client.ts). 맨몸으로 주면
//    apiRequest 가 undefined 를 돌려주고 화면이 죽는다 — 실제로 한 번 죽여 보고 알았다.
//
// 쓰는 법 (frontend 폴더에서):
//   npx expo start --web --port 8082      ← 먼저 띄워 둔다
//   node tools/shoot-itinerary.mjs ./shots
//
// 환경변수: SHOOT_BASE (기본 http://localhost:8082)
import { createRequire } from 'node:module'
import { mkdirSync } from 'node:fs'
import { join } from 'node:path'

const OUT = process.argv[2] ?? './shots'
const BASE = (process.env.SHOOT_BASE ?? 'http://localhost:8082').replace(/\/$/, '')
const require = createRequire(join(process.cwd(), 'package.json'))
const { chromium } = require('playwright')

mkdirSync(OUT, { recursive: true })

// ── 고정 자료 ───────────────────────────────────────────────────────────────
// 🔴 description 은 전부 null 이다. 서버가 늘 null 로 준다 —
//    backend ItineraryQueryService: "description — place 표에 설명 칸이 없다".
//    채워 두면 실제보다 나은 화면을 보게 된다.
// 🔴 평소엔 같이 안 보이는 것들을 한 화면에 모은다 — 상태 셋(확인됨·추정·미확인), 고정된 곳,
//    무료인 곳, 설명이 없는 곳, 구간 시간이 없는 곳. 다 채워진 자료만 찍으면 빈 값에서
//    깨지는 것을 영영 못 본다.
const day = (date, items) => ({ date, items })
const ITINERARY = {
  id: 'demo', title: '부산 2박 3일 — 바다와 시장', version: 3, tripId: 'trip-demo',
  totalEstimatedCostKrw: 48000, totalWalkingMeters: 6200, fallbackMode: 'MODEL',
  myRole: 'OWNER', canEdit: true,
  days: [
    day('2026-09-19', [
      { id: 'i1', placeId: 'p1', startsAt: '2026-09-19T09:30:00', title: '해운대 해수욕장', description: null, estimatedCostKrw: 0, walkingMeters: 400, locked: false, dataStatus: 'VERIFIED', travelDurationMin: null, travelDataStatus: null },
      { id: 'i2', placeId: 'p2', startsAt: '2026-09-19T11:00:00', title: '동백섬 누리마루', description: null, estimatedCostKrw: null, walkingMeters: 1200, locked: true, dataStatus: 'ESTIMATED', travelDurationMin: 18, travelDataStatus: 'ESTIMATED' },
      { id: 'i3', placeId: 'p3', startsAt: '2026-09-19T13:00:00', title: '광안리 밀면집', description: null, estimatedCostKrw: 18000, walkingMeters: 600, locked: false, dataStatus: 'UNKNOWN', travelDurationMin: 24, travelDataStatus: 'VERIFIED' },
      { id: 'i4', placeId: 'p4', startsAt: '2026-09-19T16:30:00', title: '흰여울문화마을', description: null, estimatedCostKrw: 0, walkingMeters: 900, locked: false, dataStatus: 'VERIFIED', travelDurationMin: 35, travelDataStatus: 'VERIFIED' },
    ]),
    day('2026-09-20', [
      { id: 'j1', placeId: 'p5', startsAt: '2026-09-20T10:00:00', title: '자갈치시장', description: null, estimatedCostKrw: 12000, walkingMeters: 700, locked: false, dataStatus: 'VERIFIED', travelDurationMin: null, travelDataStatus: null },
      { id: 'j2', placeId: 'p6', startsAt: '2026-09-20T12:30:00', title: '감천문화마을', description: null, estimatedCostKrw: null, walkingMeters: 1500, locked: false, dataStatus: 'ESTIMATED', travelDurationMin: 22, travelDataStatus: 'ESTIMATED' },
    ]),
    day('2026-09-21', [
      { id: 'k1', placeId: 'p7', startsAt: '2026-09-21T09:00:00', title: '송도 케이블카', description: null, estimatedCostKrw: 15000, walkingMeters: 300, locked: false, dataStatus: 'VERIFIED', travelDurationMin: null, travelDataStatus: null },
    ]),
  ],
}
// 🔴 예산은 **일정이 아니라 여행**에 있다 (backend TripDto.budgetKrw). 이 응답을 안 주면
//    화면이 예산 줄을 아예 안 그린다 — 0 으로 떨어뜨리지 않기 때문이다.
const TRIP = { trip: { tripId: 'trip-demo', budgetKrw: 300000 }, constraints: [] }

// 장소 갈래 — 예산 카드의 갈래 막대가 이 값으로 나뉜다. 사진은 전부 null 이라
// 정차 칸이 갈래 아이콘으로 떨어진다(바깥 그림을 안 받아 찍기가 빨라진다).
// 🔴 이 자료에는 **카페가 한 곳도 없다.** 갈래 줄이 「카페 0곳 · 미정」으로 뜨는 것이
//    맞다 — 억지로 한 곳을 카페로 바꾸면 없는 것을 있는 것처럼 보게 된다.
const PLACE_CATEGORY = {
  p1: 'SEA_BEACH', p2: 'NATURE_WALK', p3: 'FOOD', p4: 'CITY',
  p5: 'TRADITIONAL_MARKET', p6: 'CITY', p7: 'NATURE_WALK',
}

const VERSIONS = [
  { version: 3, baseVersion: 2, operation: 'REORDER', createdBy: 'me', createdAt: '2026-09-16T05:00:00Z', requestId: 'r3' },
  { version: 2, baseVersion: 1, operation: 'LOCK', createdBy: 'me', createdAt: '2026-09-16T04:00:00Z', requestId: 'r2' },
  { version: 1, baseVersion: 0, operation: 'CREATE', createdBy: 'me', createdAt: '2026-09-16T03:00:00Z', requestId: 'r1' },
]

// 🔴 **오늘 실제 서버가 주는 모습.** 항목 비용이 하나도 없다 — 장소 표에 가격 칸이
//    없어서다(backend 시험 「imageUrl·estimatedCostKrw 는 항상 null」이 못박고 있다).
//    위 고정 자료는 값이 들어왔을 때를 보려고 채운 것이라, 그것만 찍으면 **사람이 실제로
//    보게 될 화면을 한 번도 안 보고** 끝난다.
const withoutCosts = (itinerary) => ({
  ...itinerary,
  totalEstimatedCostKrw: null,
  days: itinerary.days.map((d) => ({ ...d, items: d.items.map((i) => ({ ...i, estimatedCostKrw: null })) })),
})

const browser = await chromium.launch()
const shots = []

async function shoot(name, width, height, { withData = true, route = '/trips/demo/itinerary', act, costs = 'some' } = {}) {
  const page = await browser.newPage({ viewport: { width, height }, deviceScaleFactor: 2 })
  const errors = []
  page.on('console', (m) => { if (m.type() === 'error') errors.push(m.text().slice(0, 160)) })
  await page.route('**/api/v1/**', async (r) => {
    const url = r.request().url()
    if (!withData) return r.abort()                     // 서버에 못 닿는 상태를 흉내가 아니라 실제로 만든다
    const envelope = (data) => ({ status: 200, contentType: 'application/json', body: JSON.stringify({ data }) })
    if (/\/versions$/.test(url)) return r.fulfill(envelope({ items: VERSIONS }))
    if (/\/itineraries\/demo$/.test(url)) return r.fulfill(envelope(costs === 'none' ? withoutCosts(ITINERARY) : ITINERARY))
    if (url.endsWith('/trips/trip-demo')) return r.fulfill(envelope(TRIP))
    // 장소 상세 — 갈래만 준다. 사진은 null 이라 정차 칸이 갈래 아이콘으로 떨어진다.
    const afterPlaces = url.includes('/places/') ? url.split('/places/')[1].split(/[?#]/)[0] : null
    if (afterPlaces) return r.fulfill(envelope({ placeId: afterPlaces, name: afterPlaces, category: PLACE_CATEGORY[afterPlaces] ?? null, photoUrl: null, photoSource: null }))
    return r.fulfill({ status: 404, contentType: 'application/json', body: JSON.stringify({ error: { code: 'NOT_FOUND', message: '없음' } }) })
  })
  await page.goto(`${BASE}${route}`, { waitUntil: 'load', timeout: 90000 })
  await page.waitForTimeout(3500)
  // 평소엔 안 보이는 상태(펼침, 더 보기 패널)도 찍는다. 규칙이 「상태가 여럿이면 여럿 다」다.
  if (act) { await act(page); await page.waitForTimeout(600) }
  // 🔴 로그인으로 튕긴 것을 「찍었다」로 세지 않는다. 화면 이름은 그대로인데 그림은 다른
  //    화면이면, 나중에 그것을 보고 엉뚱한 화면을 고치게 된다.
  const landed = new URL(page.url()).pathname
  await page.screenshot({ path: join(OUT, `${name}.png`), fullPage: true })
  shots.push({ name, width, asked: route, landed, ok: landed === route, errors: errors.slice(0, 3) })
  await page.close()
}

await shoot('폰-390-일정', 390, 844)
await shoot('넓은화면-1280-일정', 1280, 900)
// 시안이 그린 두 폭 (S15P21E201-1432)
await shoot('넓은화면-1024-일정', 1024, 1200)
// 🔴 오늘 실제 서버가 주는 모습 — 비용이 하나도 없는 판
await shoot('폰-390-비용없음', 390, 844, { costs: 'none' })
// 🔴 폰에서 예산 카드는 **화면 아래**에 있다. 이 화면은 페이지가 길어지는 것이 아니라
//    안쪽 상자가 구르므로 fullPage 로는 안 찍힌다 — 굴려 놓고 찍는다.
const scrollToBottom = async (page) => {
  await page.evaluate(() => {
    const boxes = Array.from(document.querySelectorAll('div'))
      .filter((d) => d.scrollHeight > d.clientHeight + 40 && d.clientHeight > 200)
    const box = boxes[boxes.length - 1]
    if (box) box.scrollTop = box.scrollHeight
  })
}
await shoot('폰-390-예산', 390, 844, { act: scrollToBottom })
await shoot('폰-390-예산-비용없음', 390, 844, { costs: 'none', act: scrollToBottom })
// 🔴 **알약이 정말 움직이는가.** 정지 그림 한 장으로는 「2일차에 빨간 칸이 있다」까지만
//    보이고, 그것은 칸마다 배경을 켜고 끈 것과 구분이 안 된다. 누른 직후(구르는 중)와
//    다 구른 뒤를 둘 다 찍어서 **가는 도중이 있다**는 것을 남긴다.
const pickDay2 = (waitMs) => async (page) => {
  await page.getByTestId('itinerary-day-2').click()
  await page.waitForTimeout(waitMs)
}
await shoot('넓은화면-1024-일차2-가는중', 1024, 1200, { act: pickDay2(150) })
await shoot('넓은화면-1024-일차2-도착', 1024, 1200, { act: pickDay2(900) })
await shoot('넓은화면-1024-비용없음', 1024, 1200, { costs: 'none' })
await shoot('폰-390-서버못닿음', 390, 844, { withData: false })
await shoot('폰-390-펼침', 390, 844, { act: async (page) => { await page.getByRole('button', { name: /해운대 해수욕장/ }).first().click() } })
await shoot('폰-390-더보기', 390, 844, { act: async (page) => { await page.getByRole('button', { name: /더 보기|More/ }).first().click() } })

console.log(JSON.stringify(shots, null, 2))
await browser.close()
