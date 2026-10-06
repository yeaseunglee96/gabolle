// 앱(네이티브) 넓은 화면은 아래 탭 막대, 웹은 위쪽 메뉴 (S15P21E201-1999).
import { showsWideTabBar, WIDE_TAB_ROOTS } from '../TabBar';

declare const require: (id: string) => any;
declare const __dirname: string;

const read = (rel: string): string => {
  const { readFileSync } = require('fs');
  const { join } = require('path');
  return readFileSync(join(__dirname, '..', '..', '..', rel), 'utf8');
};

describe('넓은 화면 아래 탭 막대', () => {
  it('앱은 탭 첫 화면에서 띄운다', () => {
    for (const path of WIDE_TAB_ROOTS) {
      expect(showsWideTabBar('android', path)).toBe(true);
      expect(showsWideTabBar('ios', path)).toBe(true);
    }
  });

  it('웹은 띄우지 않는다 — 위쪽 메뉴가 맡는다', () => {
    for (const path of WIDE_TAB_ROOTS) expect(showsWideTabBar('web', path)).toBe(false);
  });

  it('앱이라도 탭 첫 화면이 아니면(여행 화면 큰 지도 등) 띄우지 않는다', () => {
    expect(showsWideTabBar('android', '/trips/abc/itinerary')).toBe(false);
    expect(showsWideTabBar('android', '/place/1')).toBe(false);
    expect(showsWideTabBar('android', '/')).toBe(false);
  });

  it('TabBar 는 넓은 화면이면 이 판정으로만 숨긴다', () => {
    const src = read('src/components/TabBar.tsx');
    expect(src).toContain("if (kind === 'tablet' && !showsWideTabBar(Platform.OS, pathname)) return null;");
    expect(src).not.toMatch(/if \(kind === 'tablet'\) return null;/);
  });

  it('위쪽 메뉴는 앱에서 메뉴·여행 만들기를 빼고, 웹에서는 그대로 둔다', () => {
    const src = read('src/nav/TopNav.tsx');
    expect(src).toContain("const menuInBottomBar = Platform.OS !== 'web';");
    expect(src).toContain('const capsule = menuInBottomBar ? null : (');
    expect(src).toContain("{menuInBottomBar ? null : <Pressable accessibilityRole=\"link\" accessibilityState={{ selected: planActive }} onPress={() => router.push('/plan')}");
  });

  it('마이페이지 넓은 판도 탭 막대를 그리고 바닥을 비운다', () => {
    const src = read('app/(tabs)/me.tsx');
    expect(src).toContain('<TabBar active="me" />');
    expect(src).toContain("Platform.OS !== 'web' && { paddingBottom: spacing[8] + TAB_BAR_HEIGHT + tabBarBottomMargin(insets.bottom) }");
  });
});
