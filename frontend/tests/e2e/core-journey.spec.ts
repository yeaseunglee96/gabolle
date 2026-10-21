import { expect, test } from '@playwright/test';

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
//
// 🔴 S15P21E201-1902(2026-09-30) — 같은 날 두 변경이 이 시험을 한꺼번에 깨뜨렸다. 이 잡은 예약과 승격 MR 에서만
//    돌아서 둘 다 그날 밤 승격 MR 에서야 드러났다.
//      · S15P21E201-1863 — 여행 조건 창이 홈 첫 진입이 아니라 「일정 물어보기」를 누를 때 뜬다
//      · S15P21E201-1865 — /plan 이 「한 번에 한 질문」에서 «네 단계 + 확인 표» 로 바뀌었다
//    지금 흐름: 홈 시작 바 → 「일정 물어보기」 → 여행 조건 창 → /plan 네 단계 → 확인 표 → 제출.
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

  // 1.5) 🔴 여행 조건(식단) 창은 이제 홈에서 안 뜬다 (S15P21E201-1863, 2026-09-30).
  //      전에는 처음 로그인한 사람에게 홈에 들어오자마자 떴는데(conditionsPromptState.ts 의
  //      shouldPromptOnHome), 로그인하자마자 창이 홈 전체를 가려서 그 함수가 항상 false 가 됐다.
  //      이 시험은 그 창을 여기서 기다리다가 10초 뒤 시간 초과로 계속 빨갰다 — 앱이 아니라 시험이 낡은 것이었다.
  //      창은 아래 3) 에서 «일정 물어보기» 를 누를 때 뜬다(shouldPromptBeforePlan — 한 번도 안 물어본 사람은
  //      상태가 null 이라 물어본다).

  // 2) 홈의 시작 바 — 출발지 · 날짜. 인원은 기본값 성인 2명이 이미 유효하다
  //    (EMPTY_START_BAR, src/home/startBarValue.ts).
  // 🔴 2026-10-05(파이프라인 228975) — 「출발지」만으로는 두 단추에 걸린다. 스크롤하면 나오는 고정 검색바
  //    (sticky-search, 「여행 조건 다시 펼치기 — 출발지, 날짜, 인원」)도 같은 낱말을 품는다.
  //    시작 바의 출발지 칸은 접근성 이름이 「출발지 어디서 출발해요?」로 시작한다 — 그 앞머리로 고른다.
  await page.getByRole('button', { name: /^출발지/ }).click();
  // 추천 출발지는 검색어 없이도 MAJOR_BUSAN_ORIGINS 기본 목록이 뜬다 — 굳이 타이핑해서
  // 서버 검색(searchOrigins)의 250ms 디바운스를 기다릴 필요가 없다.
  // 🔴 실측(2026-09-18, 파이프라인 206720) — "부산역"만으로는 두 요소에 걸린다.
  //    출발지 행(이름+주소가 한 접근성 이름으로 합쳐진다: "부산역 부산 동구
  //    중앙대로 206")과, 홈 화면의 다른 위젯(HeroStories/PlacePicks 등)에 있는
  //    "부산역 출발"이라는 전혀 다른 요소가 동시에 걸렸다. 주소(origins.ts의
  //    MAJOR_BUSAN_ORIGINS)까지 넣어 그 행만 특정한다.
  await page.getByRole('button', { name: /부산역.*중앙대로/ }).click();
  // 🔴 출발지를 고르면 pickOrigin이 «숙소» 패널을 연다 (S15P21E201-1511 부터 — 출발지 → 숙소 →
  //    날짜 → 인원). 탈출구 「숙소 아직 안 정했어요」로 넘기면 날짜 패널이 열린다.
  //    접근성 이름에 부제(「출발지 기준으로 일정을 짜요」)가 붙으므로 exact 로 찾지 않는다.
  //    "날짜" 세그먼트를 또 누르면 오히려 toggle()이 닫아 버리므로 누르지 않는다.
  await page.getByRole('button', { name: /^숙소 아직 안 정했어요/ }).click();
  // 🔴 S15P21E201-1584 부터 1박 이상은 숙소를 골라야 「일정 물어보기」가 열린다 — 「숙소를 골라 주세요」로 잠긴다
  //    (S15P21E201-1715: 이 시험이 1박 2일을 골라 60초 뒤 시간 초과로 빨개졌다). 당일치기는 숙소 없이 된다.
  //    숙소를 실제로 고르는 경로는 숙소 검색 서버 호출이 필요해 CI 시험에 넣지 않는다 — startBarValue 단위 시험이 맡는다.
  await page.getByRole('button', { name: '당일치기', exact: true }).click();

  await page.getByRole('button', { name: '일정 물어보기', exact: true }).click();

  // 2.5) 처음 일정을 물으면 여행 조건(식단) 창이 뜬다 — 여기서 답해 두면 아래 4) 에서 제출할 때 식단을 모른다고 다시 묻지 않는다
  //      (questions.tsx 의 hardUnknown 이 false 가 된다). 「저장하고 시작」을 누르면 창이 닫히며 /plan 으로 넘어간다
  //      (app/index.tsx 의 closeConditions → applyBarAndGo).
  //      🔴 알레르기는 S15P21E201-1497 에서 창에서 걷어냈다 — 「해당 없음」은 식단 하나뿐이다.
  await expect(page.getByText('여행 조건 미리 알려주기')).toBeVisible({ timeout: 10_000 });
  await page.getByRole('checkbox', { name: '해당 없음' }).click(); // 식단
  await page.getByRole('button', { name: '저장하고 시작' }).click();

  // 3) 여행 만들기 — 🔴 S15P21E201-1865(2026-09-30) 부터 «네 단계 + 확인 표» 다
  //    (app/(plan)/questions.tsx · src/plan/PlanSteps.tsx). 1425 의 「한 번에 한 질문」이 아니다.
  //      1 언제·누구와 (날짜·인원)            — 시작 바가 이미 채웠다
  //      2 어디서 출발·어떻게 다닐까 (출발지·숙소·이동수단·하루 시간) — 출발지는 시작 바, 이동수단·시간은 기본값이 있다
  //      3 어디로·얼마나 (지역·총예산)         — 지역은 반드시 골라야 한다
  //      4 어떤 여행 (선택)                    — 건너뛴다
  //      확인 표 → 「이 조건으로 일정 만들기」
  //    단계 자리는 «처음으로 덜 채운 필수 단계» 를 넘지 않는다 — 여기서는 1단계에서 시작한다.
  await expect(page).toHaveURL(/\/plan(\?|$)/);
  // 🔴 exact — 1단계 달력에 「다음 달」 단추가 같이 있다.
  const next = page.getByRole('button', { name: '다음', exact: true });
  const build = page.getByRole('button', { name: '이 조건으로 일정 만들기', exact: true });

  // 1단계 — 날짜·인원은 시작 바에서 왔다(당일치기 · 성인 2). 그대로 넘긴다.
  await expect(page.getByText('언제, 누구와 가세요?')).toBeVisible({ timeout: 10_000 });
  await next.click();

  // 2단계 — 출발지는 시작 바에서 왔고 당일치기라 숙소는 안 묻는다. 이동수단(필수)을 고른다 —
  //    기본값이 대중교통이라 이미 골라져 있지만, 사람이 하듯 한 번 누른다(칩은 radio 다).
  await expect(page.getByText('어디서 출발해서, 어떻게 다닐까요?')).toBeVisible();
  await page.getByRole('radio', { name: '대중교통', exact: true }).click();
  await next.click();

  // 3단계 — 지역(필수)은 하나 이상. 🔴 정확일치로 찾지 않는다 — 카드가 이름 아래에 부제를 같이 그려
  //    접근성 이름이 「해운대해변 · 동백섬 · 해리단길」처럼 붙는다. 앞글자로 찾는다.
  //    총예산(필수)은 기본값이 있어 이미 답한 것이지만, 「보통」 안(1인 하루 5만)을 고른다 — 이름이 금액과 붙으므로 앞글자로.
  await expect(page.getByText('어디로 가고, 얼마나 쓸까요?')).toBeVisible();
  await page.getByRole('checkbox', { name: /^해운대/ }).click();
  await page.getByRole('radio', { name: /^보통/ }).click();
  await next.click();

  // 4단계(선택) — 답하지 않고 건너뛰면 확인 표로 간다.
  await page.getByRole('button', { name: '건너뛰기', exact: true }).click();
  await expect(build).toBeVisible();

  // 4) 추천 요청 제출 — 식단은 2.5 단계의 창에서 이미 답했으므로(hardUnknown 이
  //    false 다) 여행 조건 창이 다시 뜨지 않고 바로 제출된다. 이 클릭이
  //    CoreJourneyFunctionalTest(S15P21E201-780)가 검증한 POST /api/v1/trips +
  //    POST .../recommendation-jobs를 실제로 부른다.
  await build.click();

  // 🔴 완료 기준 — 추천 요청까지. 결과가 나올 때까지 기다리는 것은 이 흐름의
  //    책임이 아니다(그건 CoreJourneyFunctionalTest가 백엔드 쪽에서 이미 검증했다).
  //    generating 화면에 jobId가 실제로 붙어 도착하면 접수가 성공한 것이다.
  await expect(page).toHaveURL(/\/plan\/generating\?jobId=.+/, { timeout: 15_000 });
});
