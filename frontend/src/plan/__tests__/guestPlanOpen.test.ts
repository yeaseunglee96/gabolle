// 비회원도 여행을 만든다 — S15P21E201-317. 입구의 로그인 안내(-1818)를 걷어냈다.
//
// 서버가 익명 출입증으로 만든 여행을 그 출입증의 것으로 두고, 로그인하면 계정으로 넘긴다. 그러니 질문 화면과
// 마지막 단추 어디에서도 로그인으로 보내지 않는다. 민감 정보(알레르기·식단) 동의만은 계정에 기록이 남아야 해서
// 그 단추에서만 로그인으로 보낸다.
declare const require: (id: string) => any; declare const __dirname: string; const { readFileSync, existsSync } = require('fs');

const read = (rel: string) => readFileSync(`${__dirname}/${rel}`, 'utf8') as string;
const route = () => read('../../../app/(plan)/questions.tsx');

describe('비회원 여행 만들기', () => {
  it('입구에서 비회원을 안내 카드로 막지 않는다', () => {
    expect(existsSync(`${__dirname}/../GuestPlanGate.tsx`)).toBe(false);
    expect(route()).not.toMatch(/GuestPlanGate/);
  });

  it('「이 조건으로 일정 만들기」는 로그인 여부를 묻지 않고 보낸다', () => {
    const submit = route().split('const submitPlan = async')[1].split('const grantHealthConsentAndRetry')[0];
    expect(submit).not.toMatch(/sign-in/);
  });

  it('민감 정보 동의만은 비회원을 로그인으로 보내고, 돌아오면 /plan 이다', () => {
    const consent = route().split('const grantHealthConsentAndRetry = async')[1].split('};')[0];
    expect(consent).toMatch(/if \(!accessToken\) \{ router\.push\(\{ pathname: '\/sign-in', params: \{ returnTo: '\/plan' \} \}\); return; \}/);
  });
});
