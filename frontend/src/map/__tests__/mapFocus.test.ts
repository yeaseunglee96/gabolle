// 지도 아래쪽이 창에 가려 있을 때 — S15P21E201-1607.
import { fitPadding, focusShiftY } from '@/map/mapFocus';

describe('창에 가린 지도', () => {
  it('가린 것이 없으면 지금까지와 같다 — 네 변 60', () => {
    expect(fitPadding(0, 844)).toEqual([60, 60, 60, 60]);
    expect(focusShiftY(0, 844)).toBe(0);
  });

  it('🔴 가린 만큼 아래 여백을 더 두고, 고른 곳은 보이는 부분의 가운데로(가린 높이의 절반만큼 위)', () => {
    // 390×844 폰에서 창이 아래 600 을 가린다 — 보이는 것은 위 244
    expect(fitPadding(600, 844)).toEqual([60, 60, 660, 60]);
    expect(focusShiftY(600, 844)).toBe(300);
  });

  it('창이 지도를 거의 다 가려도 맞출 자리는 남긴다 — 여백이 지도보다 커지면 카카오 지도가 최대로 멀어진다', () => {
    const [, , bottom] = fitPadding(2000, 400);
    expect(bottom).toBe(280);
    expect(focusShiftY(2000, 400)).toBe(140);
  });
});
