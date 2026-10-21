// 아래 시스템 막대·삼성 작업 표시줄에 창 단추가 가리지 않게 — S15P21E201-1997.
// !2053(지출 추가 창) 뒤 전수 점검에서 남은 한 곳: 첫 언어 고르기 시트(아래에서 올라오는데 여백이 고정값뿐).
declare const require: (id: string) => any;
declare const __dirname: string;

const read = (rel: string): string => {
  const { readFileSync } = require('fs');
  const { join } = require('path');
  return readFileSync(join(__dirname, '..', '..', '..', rel), 'utf8');
};

describe('창 아래 단추가 시스템 막대에 가리지 않는다', () => {
  it('첫 언어 고르기 시트는 아래 여백에 안전영역을 더한다', () => {
    const src = read('src/onboarding/WelcomeLanguageSheet.tsx');
    expect(src).toMatch(/useSheetBottomPadding\(/);
  });
});
