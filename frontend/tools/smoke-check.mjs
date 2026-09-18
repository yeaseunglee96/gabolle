// 화면이 실제로 뜨는지 본다 (S15P21E201-252).
//
// 타입 검사·단위 테스트는 브라우저 코드를 실행하지 않는다. 주석 안의 `*/`가
// 파일을 조기 종료시키거나, `let` 선언이 첫 사용처보다 아래에 있어 부트스트랩이
// 통째로 죽어도 — 둘 다 그 검사들은 통과한다. 이 스크립트는 헤드리스 브라우저로
// 실제로 페이지를 열어서, 화면이 그려졌는지와 콘솔에 에러가 안 찍혔는지를 본다.
//
// 사용법: node tools/smoke-check.mjs <URL>
import { chromium } from 'playwright'

const url = process.argv[2]
if (!url) {
  console.error('사용법: node tools/smoke-check.mjs <URL>')
  process.exit(1)
}

const consoleErrors = []
const pageErrors = []

const browser = await chromium.launch()
const page = await browser.newPage()

page.on('console', (msg) => {
  if (msg.type() === 'error') consoleErrors.push(msg.text())
})
page.on('pageerror', (err) => {
  pageErrors.push(err.message)
})

let navigationFailed = false
try {
  await page.goto(url, { waitUntil: 'networkidle', timeout: 30000 })
} catch (err) {
  navigationFailed = true
  console.error(`🔴 페이지를 열 수 없다: ${err.message}`)
}

// Expo Router 웹은 클라이언트 사이드 렌더링이라, networkidle 뒤에도 화면을
// 그리는 마지막 프레임이 남아 있을 수 있다. 짧게 더 기다린다.
await page.waitForTimeout(1000)

const bodyText = navigationFailed ? '' : await page.evaluate(() => document.body.innerText.trim())
const rootHtml = navigationFailed ? '' : await page.evaluate(() => document.getElementById('root')?.innerHTML ?? document.body.innerHTML)

await browser.close()

// 🔴 "Failed to load resource" 는 브라우저가 네트워크 요청 실패를 콘솔
// error 채널로 보내는 것이다 — 이 검사 환경엔 백엔드가 없어서 앱이 시작할 때
// API 를 부르면 항상 이 모양으로 뜬다. 앱 코드의 결함이 아니라 환경 특성이라,
// 이것까지 실패로 잡으면 이 검사는 매번 빨갛고 아무도 안 믿게 된다.
// 그 밖의 console.error(앱 코드가 실제로 찍은 에러)는 그대로 실패 사유로 남긴다.
const realConsoleErrors = consoleErrors.filter((e) => !e.startsWith('Failed to load resource'))
const ignoredNetworkErrors = consoleErrors.length - realConsoleErrors.length

console.log(`URL: ${url}`)
console.log(`body 텍스트 길이: ${bodyText.length}`)
console.log(`콘솔 에러: ${realConsoleErrors.length}건 (네트워크 요청 실패 ${ignoredNetworkErrors}건은 백엔드 없는 환경 특성으로 제외)`)
console.log(`페이지 에러(런타임 예외): ${pageErrors.length}건`)

let failed = false

if (navigationFailed) {
  failed = true
}

// 🔴 흰 화면 판정 — body에 실제 글자가 하나도 없으면 아무것도 안 그려진 것이다.
// root의 innerHTML 길이도 함께 본다 — 텍스트는 없어도 SVG/이미지만으로 이루어진
// 화면일 수 있어서, 완전히 비어 있는지(둘 다 사실상 빈 문자열인지)로 판정한다.
if (!navigationFailed && bodyText.length === 0 && rootHtml.trim().length < 50) {
  console.error('🔴 흰 화면이다 — body에 텍스트가 없고 root도 사실상 비어 있다.')
  failed = true
}

if (pageErrors.length > 0) {
  console.error('🔴 런타임 예외가 발생했다 (부트스트랩이 죽었을 때 이 모양이다):')
  for (const e of pageErrors) console.error(`  - ${e}`)
  failed = true
}

if (realConsoleErrors.length > 0) {
  console.error('🔴 콘솔에 error 로그가 찍혔다 (네트워크 요청 실패 제외):')
  for (const e of realConsoleErrors) console.error(`  - ${e}`)
  failed = true
}

if (failed) {
  console.error('\n스모크 검사 실패.')
  process.exit(1)
}

console.log('\n스모크 검사 통과 — 화면이 그려졌고 콘솔 에러가 없다.')
