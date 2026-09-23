// 여행 날짜 달력의 «몇 번째 달» — S15P21E201-1539.
import { MAX_MONTH_OFFSET, monthAt, monthOffsetOf } from '@/home/monthJump';

const TODAY = new Date(2026, 8, 23); // 2026-09-23

describe('고른 출발일의 달부터 연다', () => {
  it('🔴 고른 날이 있으면 그 달로 — 이번 달부터 열면 고른 날이 안 보인다', () => {
    expect(monthOffsetOf('2026-12-05', TODAY)).toBe(3);
    expect(monthOffsetOf('2027-02-14', TODAY)).toBe(5);
  });

  it('날짜가 없거나 이번 달이면 이번 달', () => {
    expect(monthOffsetOf('', TODAY)).toBe(0);
    expect(monthOffsetOf('2026-09-30', TODAY)).toBe(0);
  });

  it('🔴 12개월 밖이나 지난 달은 가까운 끝으로 — 달력이 보여 줄 수 없는 달을 열지 않는다', () => {
    expect(monthOffsetOf('2028-01-01', TODAY)).toBe(MAX_MONTH_OFFSET);
    expect(monthOffsetOf('2026-05-01', TODAY)).toBe(0);
  });

  it('모양이 틀린 값은 이번 달', () => {
    expect(monthOffsetOf('내일', TODAY)).toBe(0);
  });
});

describe('몇 달 뒤의 연·월', () => {
  it('해를 넘긴다', () => {
    expect(monthAt(TODAY, 0)).toEqual({ year: 2026, month: 8 });
    expect(monthAt(TODAY, 4)).toEqual({ year: 2027, month: 0 });
    expect(monthAt(TODAY, MAX_MONTH_OFFSET)).toEqual({ year: 2027, month: 7 });
  });
});
