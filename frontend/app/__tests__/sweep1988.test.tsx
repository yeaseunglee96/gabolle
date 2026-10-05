// 빌드 46 기기 확인(10/5)에서 나온 것 — 소스에서 지킨다(S15P21E201-1988).
// node 타입이 없어 import 대신 require — foldSweep2.test.tsx 와 같은 방식.
declare const require: (id: string) => any;
declare const __dirname: string;

const read = (p: string): string => require('fs').readFileSync(require('path').join(__dirname, '..', '..', p), 'utf8');

describe('빌드 46 확인', () => {
  it('🔴 404 상태 표시줄 글자는 어둡게 — 위쪽 안전 영역은 밝은 바탕이라 밝은 글자가 묻혔다', () => {
    const src = read('app/+not-found.tsx');
    expect(src).toContain('<StatusBar style="dark" />');
    expect(src).not.toContain("style={isDesktop ? 'dark' : 'light'}");
  });

  it('🔴 언어 칩 이름은 한 줄, 넘치면 글자를 줄인다 — 탭 세로에서 「繁體中文」이 「繁體中」으로 잘렸다', () => {
    const src = read('src/me/AppLanguageSetting.tsx');
    expect(src).toMatch(/numberOfLines=\{1\} adjustsFontSizeToFit/);
  });
});
