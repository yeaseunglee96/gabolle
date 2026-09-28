// 모바일이냐 데스크톱이냐 — 판정은 useLayout 한 곳 (S15P21E201-1563).
//
// 🔴 이 시험이 지키는 것은 사용자가 정한 폴드8 네 모양이다.
//    외부 화면(세로·가로)과 펼침 세로는 모바일, 펼침 가로는 데스크톱.
//    전에는 펼치면 위쪽 메뉴는 데스크톱, 내용은 폰으로 둘이 섞였다.
import { isDesktopWindow } from '@/layout/useLayout';

describe('모바일이냐 데스크톱이냐', () => {
  it.each([
    ['폴드 외부 화면 세로', 374, 918, false],
    ['폴드 외부 화면 가로', 918, 374, false],
    ['폴드 펼침 세로', 717, 795, false],
    ['폴드 펼침 가로', 795, 717, true],
  ])('%s (%d×%d) → 데스크톱 %s', (_name, width, height, desktop) => {
    expect(isDesktopWindow(width, height)).toBe(desktop);
  });

  it('보통 폰은 모바일 — 가로로 돌려도', () => {
    expect(isDesktopWindow(390, 844)).toBe(false);
    expect(isDesktopWindow(844, 390)).toBe(false);
  });

  it('PC 브라우저는 데스크톱 — 폭이 1024 이상이면 세로로 긴 창이어도', () => {
    expect(isDesktopWindow(1440, 900)).toBe(true);
    expect(isDesktopWindow(1024, 1200)).toBe(true);
  });
});
