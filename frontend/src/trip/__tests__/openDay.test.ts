import { initialDayIndex } from '@/trip/openDay';

const days = [{ date: '2026-10-01' }, { date: '2026-10-02' }, { date: '2026-10-03' }];

describe('일정 화면을 처음 열 때의 날 칸 (S15P21E201-1921)', () => {
  it('🔴 여행 중이면 오늘 칸을 연다 — 둘째 날에 열었는데 1일차가 열렸다', () => {
    expect(initialDayIndex(days, undefined, new Date(2026, 9, 2, 9, 50))).toBe(1);
  });

  it('주소로 고른 날이 있으면 그 날이 먼저다', () => {
    expect(initialDayIndex(days, '3', new Date(2026, 9, 2))).toBe(2);
  });

  it('여행 전·후에는 첫날', () => {
    expect(initialDayIndex(days, undefined, new Date(2026, 8, 20))).toBe(0);
    expect(initialDayIndex(days, undefined, new Date(2026, 9, 9))).toBe(0);
  });
});
