// 홈 카드 줄의 장 수 — 세로 탭에서 일곱 장으로 짓눌리지 않게(S15P21E201-1949).
import { homeCardColumns, homeCardWidth } from '@/home/HomeRow';

describe('homeCardColumns', () => {
  it('🔴 시안 폭 1440 은 그대로 일곱 장이고 카드는 184 다', () => {
    expect(homeCardColumns(1440)).toBe(7);
    expect(homeCardWidth(1440)).toBe(184);
  });

  it('🔴 세로 탭은 서너 장 — 아이패드 미니 768 · 서피스 960 · 아이패드 프로 1032', () => {
    expect(homeCardColumns(768)).toBe(3);
    expect(homeCardColumns(960)).toBe(4);
    expect(homeCardColumns(1032)).toBe(4);
    expect(homeCardWidth(768)).toBeGreaterThan(200);
  });

  it('아주 넓어도 일곱 장을 넘지 않고, 좁아도 세 장 밑으로 안 간다', () => {
    expect(homeCardColumns(2560)).toBe(7);
    expect(homeCardColumns(360)).toBe(3);
  });
});
