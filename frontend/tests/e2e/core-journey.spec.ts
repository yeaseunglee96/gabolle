import { expect, test } from '@playwright/test';

// 핵심 흐름 — 로그인 → 여행 기본정보 입력 → 추천 요청. S15P21E201-775.
//
// 지금까지의 스모크(tools/smoke-check.mjs)는 "화면에 글자가 그려졌는가·콘솔에
// 에러가 없는가"만 봤다(S15P21E201-252 재발 방지용) — 로그인 → 여행 만들기 →
// 일정 생성 같은 실제 사용자 흐름은 한 번도 자동으로 눌러본 적이 없었다.
//
// 계정은 tools/e2e-seed-user.mjs가 미리 만들어 E2E_TEST_EMAIL·E2E_TEST_PASSWORD
// 환경변수로 넘겨준다 — 이메일 인증을 실제로 받을 방법이 없어서, 그 스크립트가
// FunctionalJourneyTest(S15P21E201-779)와 같은 지름길(가입 API + DB에서 직접
// email_verified_at 채우기)을 쓴다.
//
// 데스크톱 뷰포트(플레이라이트 기본값)에서는 이 앱의 여행 만들기 마법사가
// 4화면(기본정보 → 취향 → 제약조건 → 최종확인)을 스크롤 한 페이지에 전부 펼쳐
// 보여준다("kind === 'tablet'" 레이아웃) — 모바일 폭에서만 쓰는 단계별 페이지
// 넘김(panelIndex)을 다룰 필요가 없다. 화면비별 레이아웃 분기는 src/layout/useLayout.ts
// 참고.
test('로그인 → 여행 기본정보 → 추천 요청까지 이어진다', async ({ page }) => {
  const email = process.env.E2E_TEST_EMAIL;
  const password = process.env.E2E_TEST_PASSWORD;
  if (!email || !password) {
    throw new Error('E2E_TEST_EMAIL·E2E_TEST_PASSWORD가 없다 — tools/e2e-seed-user.mjs를 먼저 돌려야 한다.');
  }

  // 1) 로그인 — 온보딩 화면(언어 선택·앱 소개 등)을 거칠 필요 없이 경로로 바로 간다.
  //    웹은 딥링크 타이밍 경합이 없다(네이티브 에뮬레이터에서 겪은 문제, S15P21E201-777).
  await page.goto('/sign-in');
  await page.getByLabel('이메일').fill(email);
  // 🔴 getByLabel('비밀번호')는 입력칸과 "비밀번호 보이기" 버튼 둘 다에 걸린다
  //    (aria-label이 겹친다) — textbox 역할로 좁힌다.
  await page.getByRole('textbox', { name: '비밀번호' }).fill(password);
  await page.getByRole('button', { name: '로그인', exact: true }).click();
  // 로그인은 홈("/")으로 돌아간다 — "/home"이 아니다(app/index.tsx가 데스크톱
  // 레이아웃에서 로그인 여부와 무관하게 이 경로 하나를 쓴다).
  await expect(page.getByRole('button', { name: '여행 계획 시작하기' })).toBeVisible({ timeout: 10_000 });

  // 2) 여행 기본정보 — 날짜·출발지·예산만 채우면 나머지(인원 1명, 이동수단
  //    TRANSIT)는 기본값이 이미 유효하다(EMPTY_PLAN, src/plan/PlanProvider.tsx).
  await page.goto('/plan/basic');
  // 🔴 실측(2026-09-10) — PlanProvider가 저장된 초안을 AsyncStorage(웹에서는
  // localStorage)에서 비동기로 불러온 뒤(hydration) 그 값으로 draft를 통째로 덮어쓴다.
  // 이 흐름이 끝나기 전에 날짜를 채우면, 채운 값이 잠깐 보였다가(Playwright의
  // inputValue()는 이때 값을 본다) hydration이 끝나며 사라진다(실제 DOM value는
  // 빈 문자열로 되돌아간다) — "시작일을 YYYY-MM-DD 형식으로 입력해 주세요" 오류로
  // 이어졌다. 짧게 기다려 hydration이 끝난 뒤에 채운다.
  await page.waitForTimeout(1000);
  const start = new Date();
  start.setDate(start.getDate() + 14);
  const end = new Date(start);
  end.setDate(end.getDate() + 1);
  const toIso = (date: Date) => date.toISOString().slice(0, 10);

  await page.getByLabel('여행 시작일').fill(toIso(start));
  await page.getByLabel('여행 종료일').fill(toIso(end));

  await page.getByLabel('출발지').fill('부산역');
  await expect(page.getByLabel(/출발지로 부산역 선택/)).toBeVisible({ timeout: 10_000 });
  await page.getByLabel(/출발지로 부산역 선택/).first().click();

  // 예산 키패드에서 "+10만" 을 세 번 눌러 30만 원을 만든다 — 최소 1만 원 이상만
  // 지키면 되므로 값 자체는 임의다(validateTripBasics, src/plan/tripBasics.ts).
  await page.getByLabel('10만원 더하기').click();
  await page.getByLabel('10만원 더하기').click();
  await page.getByLabel('10만원 더하기').click();

  await page.getByRole('button', { name: '다음', exact: true }).click();

  // 3) 취향 — 데스크톱 레이아웃의 "다음 단계" 버튼은 선택 여부와 무관하게 항상
  //    눌린다(taste.tsx 247행, kind === 'tablet'). 아무것도 안 골라도 진행된다.
  await expect(page).toHaveURL(/\/plan\/taste/);
  await page.getByRole('button', { name: '다음 단계' }).click();

  // 4) 제약조건 — 알레르기·식단 두 가지만 "해당 없음"으로 답하면 safetyReady다
  //    (constraints.tsx). 그 외(이동 보조·이용 조건)는 선택 사항이다.
  await expect(page).toHaveURL(/\/plan\/conditions/);
  const noneButtons = page.getByRole('radio', { name: '해당 없음' });
  await noneButtons.nth(0).click();
  await noneButtons.nth(1).click();
  await page.getByRole('button', { name: '최종 확인으로' }).click();

  // 5) 최종 확인 → 추천 요청 제출. 서버 응답(추천 Job 접수)을 실제로 기다린다 —
  //    이 클릭이 CoreJourneyFunctionalTest(S15P21E201-780)가 검증한
  //    POST /api/v1/trips + POST .../recommendation-jobs를 실제로 부른다.
  await expect(page).toHaveURL(/\/plan\/confirm/);
  await page.getByRole('button', { name: '이 조건으로 일정 만들기' }).click();

  // 🔴 완료 기준 — 추천 요청까지. 결과가 나올 때까지 기다리는 것은 이 흐름의
  //    책임이 아니다(그건 CoreJourneyFunctionalTest가 백엔드 쪽에서 이미 검증했다).
  //    generating 화면에 jobId가 실제로 붙어 도착하면 접수가 성공한 것이다.
  await expect(page).toHaveURL(/\/plan\/generating\?jobId=.+/, { timeout: 15_000 });
});
