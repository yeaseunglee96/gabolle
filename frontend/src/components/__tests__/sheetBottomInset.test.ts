// 아래 창 여백 (S15P21E201-1765).
import { sheetBottomPadding } from '@/components/sheetBottomInset';

it('🔴 탐색 막대 높이만큼 더한다', () => {
  expect(sheetBottomPadding(32, 48)).toBe(80);
});
it('막대가 없으면 지금과 같다', () => {
  expect(sheetBottomPadding(32, 0)).toBe(32);
});
it('이상한 값은 0 으로 본다', () => {
  expect(sheetBottomPadding(32, -5)).toBe(32);
  expect(sheetBottomPadding(32, NaN)).toBe(32);
});
