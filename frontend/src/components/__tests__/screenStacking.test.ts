// 화면이 쌓여 메모리가 차던 것(S15P21E201-1975) — 소스 검사.
// 넓은 화면(탭)의 위쪽 메뉴가 홈·피드·내 여행을 router.push 로 열어, 메뉴를 오갈 때마다 화면이 쌓였다.
// 폰 아래 탭(TabBar)은 이미 replace 다. 가려진 화면은 다시 그리지 않게 얼린다(react-native-screens freeze).
declare const __dirname: string;
const { readFileSync } = require('fs');
const { join } = require('path');
const read = (...p: string[]) => readFileSync(join(__dirname, '..', '..', '..', ...p), 'utf8') as string;

describe('화면 쌓임', () => {
  it('위쪽 메뉴는 앱에서 replace 로 옮긴다(웹은 브라우저 뒤로 가기를 위해 push)', () => {
    const src = read('src', 'nav', 'TopNav.tsx');
    expect(src).toMatch(/Platform\.OS === 'web' \? router\.push\(item\.path\) : router\.replace\(item\.path\)/);
  });
  it('앱에서 가려진 화면을 얼린다', () => {
    const src = read('app', '_layout.tsx');
    expect(src).toMatch(/enableFreeze\(true\)/);
  });
});
