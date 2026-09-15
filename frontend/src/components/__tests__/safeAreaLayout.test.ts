import { screenBottomPadding } from '../Screen';
import { tabBarBottomMargin } from '../TabBar';

describe('mobile safe-area layout', () => {
  it('does not reserve the tab bar and bottom inset twice', () => {
    expect(screenBottomPadding(48, true)).toBe(32);
  });

  it('keeps the inset on screens without a tab bar', () => {
    expect(screenBottomPadding(48, false)).toBe(80);
  });

  it('uses the larger of the system inset and the minimum visual gap', () => {
    expect(tabBarBottomMargin(48)).toBe(48);
    expect(tabBarBottomMargin(0)).toBe(8);
  });
});
