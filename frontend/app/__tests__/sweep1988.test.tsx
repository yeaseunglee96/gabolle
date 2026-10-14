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

  it('🔴 언어 칩 이름은 칩 폭 전체로 편 글자(ChipLabel)다 — 「한 줄 · 글자 줄이기」를 걷어냈다 (S15P21E201-2054)', () => {
    // 1988 은 탭 세로에서 「繁體中文」이 「繁體中」으로 잘리는 것을 numberOfLines + adjustsFontSizeToFit 으로 막으려 했다.
    // 그 처방이 들어간 앱 1.0.3 을 갤럭시 S22 에서 열어도 똑같이 잘렸다(2026-10-14) — 잘림을 가릴 뿐 고치지 못했고, RN 안드로이드는
    // adjustsFontSizeToFit 이 켜지면 줄임표(…)까지 꺼 버려 잘린 흔적도 안 남긴다. 그래서 두 언어 칩 모두 ChipLabel 로 바꿨다.
    for (const file of ['src/me/AppLanguageSetting.tsx', 'src/auth/SignupLanguagePicker.tsx']) {
      const src = read(file);
      expect(src).toContain('<ChipLabel');
      expect(src).not.toContain('adjustsFontSizeToFit');
      expect(src).not.toContain('numberOfLines');
    }
  });
});
