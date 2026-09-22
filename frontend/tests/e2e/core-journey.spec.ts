import { expect, test } from '@playwright/test';

/**
 * 선택 질문을 넘길 때 「다음」을 눌러 보는 최대 횟수.
 *
 * 🔴 질문 수가 아니라 «무한 반복을 막는 상한» 이다. 실제 질문 수(PLAN_QUESTIONS)를 여기 적으면
 * 질문이 늘 때마다 이 시험이 또 깨진다 — 그 결합을 끊으려고 넉넉한 수를 둔다.
 */
const PLAN_QUESTION_GUARD = 20;

// 핵심 흐름 — 로그인 → 여행 조건 → 추천 요청. S15P21E201-775.
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
// 🔴 S15P21E201-1257 — S15P21E201-1233(여행 조건을 한 페이지로 합치기)이
//    /plan/basic·/plan/taste·/plan/conditions·/plan/confirm 을 전부 /plan 으로
//    보내는 얇은 리다이렉트로 바꿨다. 이 시험은 옛 다단계 URL 전환을 그대로
//    기대하고 있었고, 그래서 front/dev 기반 모든 MR의 frontend:e2e 가 막혔다.
//    아래는 새 흐름(홈 시작 바 → 여행 조건 모달 → /plan 질문 카드 한 페이지)이다.
//
// 데스크톱 뷰포트(플레이라이트 기본값 1280×720)는 isAtLeast(width, 'lg')가
// true라 app/index.tsx의 데스크톱 분기("/")를 그대로 쓴다 — 폰 폭에서만 쓰는
// PlanStartBar(wide=false)의 알약·탭 UI를 다룰 필요가 없다.
test('로그인 → 여행 조건 → 추천 요청까지 이어진다', async ({ page }) => {
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
  // 레이아웃에서 로그인 여부와 무관하게 이 경로 하나를 쓴다). "여행 계획
  // 시작하기" 단추는 S15P21E201-1245 에서 걷어냈다 — 시작 바가 그 자리를 대신한다.
  await expect(page.getByRole('button', { name: '일정 물어보기' })).toBeVisible({ timeout: 10_000 });

  // 1.5) 처음 로그인한 사람에게는 홈에 들어오자마자 여행 조건(알레르기·식단) 모달이
  //      뜬다(conditionsPromptState.ts의 shouldPromptOnHome — 한 번도 안 물어본
  //      사람은 상태가 null이다). 여기서 답해 두면 "일정 물어보기"를 눌러도 다시
  //      안 묻는다 — shouldPromptBeforePlan이 'SAVED'는 다시 안 묻기 때문이다.
  await expect(page.getByText('여행 조건 미리 알려주기')).toBeVisible({ timeout: 10_000 });
  const noneChips = page.getByRole('checkbox', { name: '해당 없음' });
  await noneChips.nth(0).click(); // 알레르기
  await noneChips.nth(1).click(); // 식단
  await page.getByRole('button', { name: '저장하고 시작' }).click();

  // 2) 홈의 시작 바 — 출발지 · 날짜. 인원은 기본값 성인 2명이 이미 유효하다
  //    (EMPTY_START_BAR, src/home/startBarValue.ts).
  await page.getByRole('button', { name: '출발지', exact: false }).click();
  // 추천 출발지는 검색어 없이도 MAJOR_BUSAN_ORIGINS 기본 목록이 뜬다 — 굳이 타이핑해서
  // 서버 검색(searchOrigins)의 250ms 디바운스를 기다릴 필요가 없다.
  // 🔴 실측(2026-09-18, 파이프라인 206720) — "부산역"만으로는 두 요소에 걸린다.
  //    출발지 행(이름+주소가 한 접근성 이름으로 합쳐진다: "부산역 부산 동구
  //    중앙대로 206")과, 홈 화면의 다른 위젯(HeroStories/PlacePicks 등)에 있는
  //    "부산역 출발"이라는 전혀 다른 요소가 동시에 걸렸다. 주소(origins.ts의
  //    MAJOR_BUSAN_ORIGINS)까지 넣어 그 행만 특정한다.
  await page.getByRole('button', { name: /부산역.*중앙대로/ }).click();
  // 출발지를 고르면 pickOrigin이 곧바로 날짜 패널을 연다(section을 'dates'로 바꾼다) —
  // "날짜" 세그먼트를 또 누르면 오히려 toggle()이 닫아 버리므로 누르지 않는다.
  await page.getByRole('button', { name: '1박 2일', exact: true }).click();

  await page.getByRole('button', { name: '일정 물어보기', exact: true }).click();

  // 3) 여행 조건 — 🔴 S15P21E201-1425 부터 «한 번에 한 질문» 이다
  //    (app/(plan)/questions.tsx 가 PLAN_QUESTIONS[index] 하나만 그리고 이전/다음으로 넘긴다).
  //    1377 의 「세 장」도, 그 전의 「한 질문 = 한 장」도 아니다.
  await expect(page).toHaveURL(/\/plan(\?|$)/);

  // 여행 범위(필수) — 하나 이상 고른다.
  // 🔴 정확일치로 찾지 않는다. 선택지 카드가 제목 아래에 부제를 같이 그리므로(-1320,
  //    OptionCard) 접근성 이름이 「해운대해변 · 동백섬 · 해리단길」이 된다. exact 는
  //    영영 못 맞춘다 — 앞글자로 찾는다.
  const next = page.getByRole('button', { name: '다음', exact: true });
  const build = page.getByRole('button', { name: '이 조건으로 일정 만들기', exact: true });

  await page.getByRole('checkbox', { name: /^해운대/ }).click();
  await next.click();
  // 총예산(필수) — "+10만"을 한 번만 눌러도 0보다 커져 답한 것으로 본다.
  await page.getByRole('button', { name: '+10만', exact: true }).click();
  await next.click();
  // 하루 여행 시간 · 이동수단(필수) — 이동수단만 고르면 답한 것으로 본다.
  await page.getByRole('checkbox', { name: /^대중교통/ }).click();

  // 남은 질문은 전부 선택이라 답하지 않고 넘긴다.
  // 🔴 몇 개인지 세지 않는다. 이 시험은 화면 구성이 바뀔 때마다 깨졌다
  //    (1233 → 1257 → 1425). 「마지막 장에 닿을 때까지 다음을 누른다」로 두면 질문이
  //    늘거나 줄어도 버틴다. 마지막 장에서는 그 자리 단추가 「이 조건으로 일정 만들기」로
  //    바뀌므로 «다음이 사라지는 것» 자체가 멈추는 신호다.
  for (let step = 0; step < PLAN_QUESTION_GUARD; step += 1) {
    if (await build.isVisible()) break;
    await next.click();
  }
  await expect(build).toBeVisible();

  // 4) 추천 요청 제출 — 알레르기·식단은 1.5단계에서 이미 답했으므로(hardUnknown이
  //    false다) 여행 조건 모달이 다시 뜨지 않고 바로 제출된다. 이 클릭이
  //    CoreJourneyFunctionalTest(S15P21E201-780)가 검증한 POST /api/v1/trips +
  //    POST .../recommendation-jobs를 실제로 부른다.
  await page.getByRole('button', { name: '이 조건으로 일정 만들기', exact: true }).click();

  // 🔴 완료 기준 — 추천 요청까지. 결과가 나올 때까지 기다리는 것은 이 흐름의
  //    책임이 아니다(그건 CoreJourneyFunctionalTest가 백엔드 쪽에서 이미 검증했다).
  //    generating 화면에 jobId가 실제로 붙어 도착하면 접수가 성공한 것이다.
  await expect(page).toHaveURL(/\/plan\/generating\?jobId=.+/, { timeout: 15_000 });
});
