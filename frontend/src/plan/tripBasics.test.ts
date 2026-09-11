import { EMPTY_PLAN } from './PlanProvider';
import { addDays, formatBudgetEn, formatBudgetKo, validateTripBasics } from './tripBasics';

describe('formatBudgetKo', () => {
  it('만 원 단위로 떨어지면 "만 원"으로 줄여 보여준다', () => {
    expect(formatBudgetKo(300000)).toBe('30만 원');
  });

  it('만 원 단위로 안 떨어지면(예전 원 단위 초안) 원 그대로 보여준다', () => {
    expect(formatBudgetKo(123456)).toBe('123,456원');
  });
});

describe('formatBudgetEn', () => {
  it('₩ 기호와 콤마 단위로 원 그대로 보여준다', () => {
    expect(formatBudgetEn(300000)).toBe('₩300,000');
  });
});

describe('addDays', () => {
  it('날짜를 UTC 기준으로 더한다', () => {
    expect(addDays('2026-09-10', 3)).toBe('2026-09-13');
  });

  it('월 경계를 넘어가도 올바르게 넘어간다', () => {
    expect(addDays('2026-09-29', 3)).toBe('2026-10-02');
  });
});

describe('validateTripBasics', () => {
  const valid = {
    ...EMPTY_PLAN,
    startDate: '2026-09-20',
    endDate: '2026-09-22',
    origin: '부산역',
    originLat: 35.1152,
    originLng: 129.0423,
    budgetKrw: 300000,
  };

  it('전부 올바르면 에러가 없다', () => {
    expect(validateTripBasics(valid, '2026-09-10')).toEqual({});
  });

  it('시작일이 오늘보다 이전이면 막는다', () => {
    const errors = validateTripBasics(valid, '2026-09-25');
    expect(errors.startDate).toBeDefined();
  });

  it('7박을 넘기면 막는다', () => {
    const errors = validateTripBasics({ ...valid, endDate: '2026-09-30' }, '2026-09-10');
    expect(errors.endDate).toBeDefined();
  });

  it('종료일이 시작일보다 빠르면 막는다', () => {
    const errors = validateTripBasics({ ...valid, endDate: '2026-09-19' }, '2026-09-10');
    expect(errors.endDate).toBeDefined();
  });

  it('출발지 좌표가 없으면 막는다 — 목록에서 고르지 않고 글자만 입력한 경우', () => {
    const errors = validateTripBasics({ ...valid, originLat: null, originLng: null }, '2026-09-10');
    expect(errors.origin).toBeDefined();
  });

  it('예산이 10,000원 단위가 아니면 막는다', () => {
    const errors = validateTripBasics({ ...valid, budgetKrw: 15000 }, '2026-09-10');
    expect(errors.budgetKrw).toBeDefined();
  });

  it('종료 시각이 시작 시각보다 늦지 않으면 막는다', () => {
    const errors = validateTripBasics({ ...valid, dayStartTime: '18:00', dayEndTime: '09:00' }, '2026-09-10');
    expect(errors.dayEndTime).toBeDefined();
  });

  it('성인·아동 합이 0명이면 막는다', () => {
    const errors = validateTripBasics({ ...valid, adults: 0, children: 0 }, '2026-09-10');
    expect(errors.travelers).toBeDefined();
  });
});
