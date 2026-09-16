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
// 🔴 평소엔 같이 안 보이는 것들을 한 화면에 모은다 — 상태 셋(확인됨·추정·미확인), 고정된 곳,
//    무료인 곳, 설명이 없는 곳, 구간 시간이 없는 곳. 다 채워진 자료만 찍으면 빈 값에서
//    깨지는 것을 영영 못 본다.
const day = (date, items) => ({ date, items })
const ITINERARY = {
  id: 'demo', title: '부산 2박 3일 — 바다와 시장', version: 3,
  totalEstimatedCostKrw: 48000, totalWalkingMeters: 6200, fallbackMode: 'MODEL',
  myRole: 'OWNER', canEdit: true,
  days: [
    day('2026-09-19', [
      { id: 'i1', placeId: 'p1', startsAt: '2026-09-19T09:30:00', title: '해운대 해수욕장', description: '아침 바다를 걷기 좋은 시간대예요.', estimatedCostKrw: 0, walkingMeters: 400, locked: false, dataStatus: 'VERIFIED', travelDurationMin: null, travelDataStatus: null },
      { id: 'i2', placeId: 'p2', startsAt: '2026-09-19T11:00:00', title: '동백섬 누리마루', description: null, estimatedCostKrw: null, walkingMeters: 1200, locked: true, dataStatus: 'ESTIMATED', travelDurationMin: 18, travelDataStatus: 'ESTIMATED' },
      { id: 'i3', placeId: 'p3', startsAt: '2026-09-19T13:00:00', title: '광안리 밀면집', description: '줄이 길면 옆 골목 분점도 같은 집이에요.', estimatedCostKrw: 18000, walkingMeters: 600, locked: false, dataStatus: 'UNKNOWN', travelDurationMin: 24, travelDataStatus: 'VERIFIED' },
      { id: 'i4', placeId: 'p4', startsAt: '2026-09-19T16:30:00', title: '흰여울문화마을', description: '계단이 많아 편한 신발을 권해요.', estimatedCostKrw: 0, walkingMeters: 900, locked: false, dataStatus: 'VERIFIED', travelDurationMin: 35, travelDataStatus: 'VERIFIED' },
    ]),
    day('2026-09-20', [
      { id: 'j1', placeId: 'p5', startsAt: '2026-09-20T10:00:00', title: '자갈치시장', description: null, estimatedCostKrw: 12000, walkingMeters: 700, locked: false, dataStatus: 'VERIFIED', travelDurationMin: null, travelDataStatus: null },
      { id: 'j2', placeId: 'p6', startsAt: '2026-09-20T12:30:00', title: '감천문화마을', description: '오르막이 이어져요.', estimatedCostKrw: null, walkingMeters: 1500, locked: false, dataStatus: 'ESTIMATED', travelDurationMin: 22, travelDataStatus: 'ESTIMATED' },
    ]),
    day('2026-09-21', [
      { id: 'k1', placeId: 'p7', startsAt: '2026-09-21T09:00:00', title: '송도 케이블카', description: null, estimatedCostKrw: 15000, walkingMeters: 300, locked: false, dataStatus: 'VERIFIED', travelDurationMin: null, travelDataStatus: null },
    ]),
  ],
}
const VERSIONS = [
  { version: 3, baseVersion: 2, operation: 'REORDER', createdBy: 'me', createdAt: '2026-09-16T05:00:00Z', requestId: 'r3' },
  { version: 2, baseVersion: 1, operation: 'LOCK', createdBy: 'me', createdAt: '2026-09-16T04:00:00Z', requestId: 'r2' },
  { version: 1, baseVersion: 0, operation: 'CREATE', createdBy: 'me', createdAt: '2026-09-16T03:00:00Z', requestId: 'r1' },
]

const browser = await chromium.launch()
const shots = []

async function shoot(name, width, height, { withData = true, route = '/trips/demo/itinerary' } = {}) {
  const page = await browser.newPage({ viewport: { width, height }, deviceScaleFactor: 2 })
  const errors = []
  page.on('console', (m) => { if (m.type() === 'error') errors.push(m.text().slice(0, 160)) })
  await page.route('**/api/v1/**', async (r) => {
    const url = r.request().url()
    if (!withData) return r.abort()                     // 서버에 못 닿는 상태를 흉내가 아니라 실제로 만든다
    const envelope = (data) => ({ status: 200, contentType: 'application/json', body: JSON.stringify({ data }) })
    if (/\/versions$/.test(url)) return r.fulfill(envelope({ items: VERSIONS }))
    if (/\/itineraries\/demo$/.test(url)) return r.fulfill(envelope(ITINERARY))
    return r.fulfill({ status: 404, contentType: 'application/json', body: JSON.stringify({ error: { code: 'NOT_FOUND', message: '없음' } }) })
  })
  await page.goto(`${BASE}${route}`, { waitUntil: 'load', timeout: 90000 })
  await page.waitForTimeout(3500)
  // 🔴 로그인으로 튕긴 것을 「찍었다」로 세지 않는다. 화면 이름은 그대로인데 그림은 다른
  //    화면이면, 나중에 그것을 보고 엉뚱한 화면을 고치게 된다.
  const landed = new URL(page.url()).pathname
  await page.screenshot({ path: join(OUT, `${name}.png`), fullPage: true })
  shots.push({ name, width, asked: route, landed, ok: landed === route, errors: errors.slice(0, 3) })
  await page.close()
}

await shoot('폰-390-일정', 390, 844)
await shoot('넓은화면-1280-일정', 1280, 900)
await shoot('폰-390-서버못닿음', 390, 844, { withData: false })

console.log(JSON.stringify(shots, null, 2))
await browser.close()
