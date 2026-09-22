// 비회원이 상세 화면에서 뒤로 가면 로그인 화면으로 튕기던 것 — 세 번째 재발을 막는다.
//
// 같은 증상을 두 번 고쳤다.
//
//      S15P21E201-1242  온보딩도 enterApp 으로 들어간다
//      S15P21E201-1292  router.back() 에 canGoBack() 가드 (backGuard.test.ts)
//
// 둘 다 안드로이드 하드웨어 뒤로가기와 화면 안 단추만 봤다. iOS 스와이프(TestFlight
// build 37, 실기기)에서 또 났다 — 마이페이지가 한 프레임 보인 뒤 로그인 화면.
//
// 원인은 뒤로가기 쪽이 아니라 `app/(tabs)/_layout.tsx` 에 있던 로그인 문이었다.
//
//      <ProtectedRoute publicPaths={['/home', '/feed', '/trips', '/me']} />
//
// 「탭은 모두 둘러볼 수 있다」고 써 놓고 여섯 중 넷만 넣었고, 목록은 글자까지 같아야
// 통과시켰다. 네이티브 화면은 이미 탭으로 돌아왔는데 라우터의 pathname 이 아직 안 바뀐
// 찰나에 비회원은 목록 밖으로 판정돼 Redirect 됐다. 비회원을 로그인으로 자동으로 보내는
// 코드는 앱 전체에서 그 Redirect 하나뿐이다.
//
// 🔴 그래서 규칙은 하나다 — **탭 그룹 레이아웃에는 로그인 문을 두지 않는다.** 각 탭
//    화면이 비회원을 스스로 다룬다(로그인 단추 · `enabled: Boolean(accessToken)`).
//    문을 다시 달고 싶어지면 이 시험이 빨개진다. 그때 이 머리말을 먼저 읽는다.
declare const require: (id: string) => any;
declare const __dirname: string;

const { readFileSync, readdirSync } = require('fs');
const { join } = require('path');

const ROOT = join(__dirname, '..', '..');
const TABS_LAYOUT = join(ROOT, 'app', '(tabs)', '_layout.tsx');
const TABS_DIR = join(ROOT, 'app', '(tabs)');

const layoutSource: string = readFileSync(TABS_LAYOUT, 'utf8');

/**
 * 주석은 빼고 코드만 남긴다. 이 레이아웃의 머리말이 「전에는 ProtectedRoute 였다」고
 * 설명하는데, 그 단어를 검사가 잡으면 사람이 왜 뺐는지를 못 적는다 — 설명을 지우게
 * 만드는 검사는 검사가 아니라 방해다.
 */
const codeOnly = layoutSource
  .replace(/\/\*[\s\S]*?\*\//g, '')
  .split(/\r?\n/)
  .filter((line) => !line.trim().startsWith('//'))
  .join('\n');

describe('탭 그룹 레이아웃에는 로그인 문이 없다', () => {
  it('🔴 app/(tabs)/_layout.tsx 는 ProtectedRoute 를 들여오지도, 그리지도 않는다', () => {
    expect(codeOnly).not.toMatch(/import[^;]*ProtectedRoute/);
    expect(codeOnly).not.toMatch(/<ProtectedRoute/);
  });

  it('🔴 publicPaths 허용 목록도 없다 — 목록은 늘 탭 수보다 짧아진다', () => {
    expect(codeOnly).not.toMatch(/publicPaths\s*=/);
  });

  it('이 검사가 실제 파일을 읽고 있다 — 탭 화면이 한 움큼 있고 레이아웃은 Slot 을 낸다', () => {
    const screens = (readdirSync(TABS_DIR) as string[]).filter((name) => name.endsWith('.tsx') && name !== '_layout.tsx');
    // 0 이면 폴더가 옮겨진 것이다. 그때는 이 시험도 같이 옮겨야 한다.
    expect(screens.length).toBeGreaterThanOrEqual(5);
    expect(codeOnly).toMatch(/<Slot\s*\/>/);
  });
});
