// 실사용자 흐름 E2E(S15P21E201-775)가 로그인할 계정을 준비한다.
//
// 이메일 인증을 실제로 받을 방법이 없으므로, FunctionalJourneyTest(S15P21E201-779,
// 백엔드 쪽 기능 테스트 하네스)가 쓰는 것과 같은 지름길을 여기서도 쓴다 — 가입 API를
// 그대로 부르고, DB에서 직접 email_verified_at·status를 채운다. "운영과 같은 경로"가
// 아니라 이메일을 실제로 받을 방법이 없어서 테스트에서만 쓰는 지름길이라는 점을
// FunctionalJourneyTest 쪽 주석과 같이 여기 남긴다.
//
// 사용법: node tools/e2e-seed-user.mjs
// 필요한 환경변수: EXPO_PUBLIC_API_BASE_URL(가입을 받을 백엔드), E2E_DB_URL(같은 DB에
// 직접 연결할 postgres:// 주소 — 백엔드가 보는 것과 같은 DB·스키마를 가리켜야 한다)
// 표준출력에 E2E_TEST_EMAIL=...·E2E_TEST_PASSWORD=... 두 줄을 찍는다 — 호출자가
// 환경변수로 받아 Playwright 테스트에 넘긴다.
import pg from 'pg'

const apiBaseUrl = (process.env.EXPO_PUBLIC_API_BASE_URL ?? 'http://localhost:8080').replace(/\/$/, '')
const dbUrl = process.env.E2E_DB_URL
if (!dbUrl) {
  console.error('E2E_DB_URL이 없다 — 예: postgres://gabolle:gabolle@localhost:5433/gabolle_test?options=-csearch_path%3De2etest')
  process.exit(1)
}

const email = `e2e-${Date.now()}@example.com`
const password = 'E2ePlaywright1234!'
const displayName = 'E2E Playwright'

const signupResponse = await fetch(`${apiBaseUrl}/api/v1/auth/signup`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({
    email,
    password,
    displayName,
    language: 'KO',
    ageGateAccepted: true,
    deviceId: 'e2e-seed',
    consents: { TERMS_OF_SERVICE: true, PRIVACY_POLICY: true },
    behaviorPersonalizationEnabled: false,
  }),
})
if (!signupResponse.ok) {
  console.error(`🔴 가입 실패: ${signupResponse.status} ${await signupResponse.text()}`)
  process.exit(1)
}

const client = new pg.Client({ connectionString: dbUrl })
await client.connect()
try {
  const result = await client.query(
    'UPDATE local_credential SET email_verified_at = now() WHERE email = $1 RETURNING user_id',
    [email],
  )
  if (result.rowCount !== 1) {
    console.error(`🔴 방금 가입한 계정을 local_credential에서 못 찾았다: ${email}`)
    process.exit(1)
  }
  const userId = result.rows[0].user_id
  await client.query("UPDATE app_user SET status = 'ACTIVE' WHERE user_id = $1", [userId])
} finally {
  await client.end()
}

console.log(`E2E_TEST_EMAIL=${email}`)
console.log(`E2E_TEST_PASSWORD=${password}`)
