// 여행 돈 정산(S15P21E201-1935) — 누가 누구에게 얼마. 틀리면 동행끼리 돈 얘기가 꼬인다.
import { approxForeignText, settle, totalsByCategory, type Expense } from '../expenses';

const line = (paidBy: string, amountKrw: number, splitEven = true, category: Expense['category'] = 'FOOD'): Expense => ({
  expenseId: `${paidBy}-${amountKrw}`, createdBy: paidBy, paidBy, amountKrw, category, placeName: null, note: null, splitEven, spentAt: '2026-10-03T12:00:00+09:00',
});

describe('settle', () => {
  it('둘이 반씩 — 덜 낸 사람이 차이의 반을 보낸다', () => {
    expect(settle([line('me', 42000), line('jisu', 6800)], ['me', 'jisu'])).toEqual([{ from: 'jisu', to: 'me', amountKrw: 17600 }]);
  });

  it('셋이면 보내는 횟수가 적게', () => {
    const transfers = settle([line('a', 90000)], ['a', 'b', 'c']);
    expect(transfers).toEqual([{ from: 'b', to: 'a', amountKrw: 30000 }, { from: 'c', to: 'a', amountKrw: 30000 }]);
  });

  it('🔴 안 나누어떨어지는 원은 낸 사람이 진다 — 1원짜리 정산 줄이 생기지 않는다', () => {
    expect(settle([line('a', 10000)], ['a', 'b', 'c'])).toEqual([{ from: 'b', to: 'a', amountKrw: 3333 }, { from: 'c', to: 'a', amountKrw: 3333 }]);
  });

  it('혼자 쓴 돈(반씩 나누기 아님)은 정산에 안 들어간다', () => {
    expect(settle([line('a', 50000, false)], ['a', 'b'])).toEqual([]);
  });

  it('혼자 여행이면 정산이 없다', () => {
    expect(settle([line('a', 50000)], ['a'])).toEqual([]);
  });

  it('🔴 나간 동행이 낸 돈도 그 사람 몫으로 — 받을 돈이 사라지지 않는다', () => {
    expect(settle([line('left', 20000)], ['a', 'b'])).toEqual([{ from: 'a', to: 'left', amountKrw: 10000 }, { from: 'b', to: 'left', amountKrw: 10000 }]);
  });
});

describe('totalsByCategory', () => {
  it('갈래별 합계를 큰 것부터', () => {
    expect(totalsByCategory([line('a', 1000, true, 'CAFE'), line('a', 18000), line('a', 9000), line('a', 3000, true, 'TRANSPORT')])).toEqual([
      { category: 'FOOD', amountKrw: 27000 }, { category: 'TRANSPORT', amountKrw: 3000 }, { category: 'CAFE', amountKrw: 1000 },
    ]);
  });
});

describe('approxForeignText', () => {
  it('엔·대만 달러는 정수, 달러는 소수 둘째 자리', () => {
    expect(approxForeignText(48800, 'JPY', 9.18)).toBe('≈ ¥5,316');
    expect(approxForeignText(48800, 'TWD', 43.1)).toBe('≈ NT$1,132');
    expect(approxForeignText(48800, 'USD', 1380)).toBe('≈ $35.36');
  });
  it('모르는 통화는 코드를 뒤에 · 환율이 이상하면 안 그린다', () => {
    expect(approxForeignText(10000, 'EUR', 1500)).toBe('≈ 6.67 EUR');
    expect(approxForeignText(10000, 'USD', 0)).toBeNull();
  });
});
