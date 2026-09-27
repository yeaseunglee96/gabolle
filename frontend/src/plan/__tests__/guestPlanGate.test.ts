// 비회원이 여행 만들기에 들어오면 질문보다 먼저 「로그인이 필요하다」를 알린다 — S15P21E201-1818.
//
// 예전엔 일곱 질문을 다 답한 뒤 마지막 「이 조건으로 일정 만들기」에서야 로그인으로 보냈다.
// 들인 수고가 버려진다. 로그인 필수는 그대로 두고, 들어오는 입구에서 먼저 알린다.
declare const require: (id: string) => any; declare const __dirname: string; const { readFileSync } = require('fs');

const read = (rel: string) => readFileSync(`${__dirname}/${rel}`, 'utf8') as string;
const route = () => read('../../../app/(plan)/questions.tsx');
const gate = () => read('../GuestPlanGate.tsx');

describe('비회원 여행 만들기 입구 안내', () => {
  it('질문 화면은 인증을 다 읽은 뒤 비회원이면 안내 카드를 먼저 그린다', () => {
    expect(route()).toMatch(/if \(authReady && !user\) return <GuestPlanGate \/>;/);
  });

  it('로그인하고 만들기 → 로그인 화면, 끝나면 /plan 으로 돌아온다', () => {
    expect(gate()).toMatch(/tx\('로그인하고 만들기'/);
    expect(gate()).toMatch(/pathname: '\/sign-in', params: \{ returnTo: '\/plan' \}/);
  });

  it('둘러보기 계속 → 뒤로 간다(뒤가 없으면 홈)', () => {
    expect(gate()).toMatch(/tx\('둘러보기 계속'/);
    expect(gate()).toMatch(/router\.canGoBack\(\)/);
  });

  it('문구는 다섯 언어로 있다', () => {
    const t = read('../../i18n/translations.ts');
    for (const ko of ['일정 만들기는 로그인 후 쓸 수 있어요', '로그인하고 만들기', '둘러보기 계속']) {
      expect(t).toContain(`'${ko}': {`);
    }
  });
});
