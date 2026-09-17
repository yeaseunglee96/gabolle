import { screenBottomPadding } from '../Screen';
import { tabBarBottomMargin } from '../TabBar';

describe('mobile safe-area layout', () => {
  // 🔴 2026-09-17 (S15P21E201-1155) — 기대값이 32 에서 144 로 바뀌었다.
  //
  // 탭바가 **떠 있게** 되면서 레이아웃 자리를 안 먹는다. 그래서 화면이 그만큼을 대신
  // 비워야 한다 — 안 비우면 스크롤 맨 끝 내용이 알약 밑에 깔린 채 드러낼 방법이 없다.
  //   기본 여백 32 + 알약 높이 64 + 알약 아래 간격 max(8, 48) = 144
  it('reserves room for the floating tab bar', () => {
    expect(screenBottomPadding(48, true)).toBe(32 + 64 + 48);
  });

  it('keeps the inset on screens without a tab bar', () => {
    expect(screenBottomPadding(48, false)).toBe(80);
  });

  it('uses the larger of the system inset and the minimum visual gap', () => {
    expect(tabBarBottomMargin(48)).toBe(48);
    expect(tabBarBottomMargin(0)).toBe(8);
  });
});
