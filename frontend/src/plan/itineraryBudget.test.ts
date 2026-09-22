// 이 시험이 지키는 것은 하나다 — **모르는 비용이 0 원으로 세어지지 않는가.**
//
// 0 으로 세면 화면은 멀쩡하다. 숫자가 하나 뜨고, 막대가 그려지고, 아무 오류도 안 난다.
// 다만 그 숫자가 거짓이고, 사람은 그것을 믿고 예산을 짠다. 눈으로는 안 잡힌다.
import type { ItineraryDto, ItineraryItemDto } from '@/plan/itinerary';
import { budgetCategoryOf, summarizeItineraryBudget } from '@/plan/itineraryBudget';

const item = (over: Partial<ItineraryItemDto> & { id: string; placeId: string }): ItineraryItemDto => ({
  startsAt: '2026-10-03T10:00:00+09:00',
  title: '어딘가',
  locked: false,
  ...over,
});

const itinerary = (days: ItineraryDto['days']): ItineraryDto => ({
  id: 'it-1', title: '부산 여행', version: 1, days,
});

describe('budgetCategoryOf', () => {
  it('식당과 카페는 제 갈래로, 나머지 아는 갈래는 입장·체험으로 간다', () => {
    expect(budgetCategoryOf('FOOD')).toBe('FOOD');
    expect(budgetCategoryOf('CAFE_HEALING')).toBe('CAFE');
    expect(budgetCategoryOf('SEA_BEACH')).toBe('ADMISSION');
    // 아직 없는 갈래가 서버에 생겨도 떨어질 자리가 있다
    expect(budgetCategoryOf('SOMETHING_NEW')).toBe('ADMISSION');
  });

  it('🔴 갈래를 모르면 아무 칸에도 넣지 않는다', () => {
    expect(budgetCategoryOf(null)).toBeNull();
    expect(budgetCategoryOf(undefined)).toBeNull();
    expect(budgetCategoryOf('')).toBeNull();
  });
});

describe('summarizeItineraryBudget', () => {
  it('🔴 비용을 모르는 곳을 0 원으로 세지 않는다 — 아는 곳만 더하고 몇 곳인지 같이 말한다', () => {
    const summary = summarizeItineraryBudget(
      itinerary([{ date: '2026-10-03', items: [
        item({ id: 'a', placeId: 'p1', estimatedCostKrw: 24000 }),
        item({ id: 'b', placeId: 'p2' }),                        // 값 없음
        item({ id: 'c', placeId: 'p3', estimatedCostKrw: null }), // 값 없음(명시적 null)
      ] }]),
      {},
      null,
    );

    expect(summary.krw).toBe(24000);
    expect(summary.count).toBe(3);
    expect(summary.known).toBe(1);
    expect(summary.unpriced).toBe(2);
  });

  it('🔴 0 원(무료)은 「모름」이 아니다 — 아는 값으로 센다', () => {
    const summary = summarizeItineraryBudget(
      itinerary([{ date: '2026-10-03', items: [
        item({ id: 'a', placeId: 'p1', estimatedCostKrw: 0 }),
        item({ id: 'b', placeId: 'p2' }),
      ] }]),
      {},
      null,
    );

    expect(summary.krw).toBe(0);
    expect(summary.known).toBe(1);
    expect(summary.unpriced).toBe(1);
  });

  it('갈래별로 나누고, 갈래를 모르는 곳은 어느 줄에도 안 들어간다', () => {
    const summary = summarizeItineraryBudget(
      itinerary([{ date: '2026-10-03', items: [
        item({ id: 'a', placeId: 'p1', estimatedCostKrw: 24000 }),
        item({ id: 'b', placeId: 'p2', estimatedCostKrw: 14000 }),
        item({ id: 'c', placeId: 'p3', estimatedCostKrw: 12000 }),
        item({ id: 'd', placeId: 'p4', estimatedCostKrw: 9000 }), // 갈래 모름
      ] }]),
      { p1: 'FOOD', p2: 'CAFE_HEALING', p3: 'SEA_BEACH', p4: null },
      null,
    );

    const by = (key: string) => summary.categories.find((entry) => entry.key === key)!;
    expect(by('FOOD').krw).toBe(24000);
    expect(by('CAFE').krw).toBe(14000);
    expect(by('ADMISSION').krw).toBe(12000);
    // 갈래를 모르는 9,000원은 총계에는 있고 갈래 줄에는 없다
    expect(summary.krw).toBe(59000);
    expect(by('FOOD').count + by('CAFE').count + by('ADMISSION').count).toBe(3);
  });

  it('교통 줄은 자리만 있고 값이 없다 — 구간 요금을 주는 곳이 아직 없다', () => {
    const summary = summarizeItineraryBudget(
      itinerary([{ date: '2026-10-03', items: [item({ id: 'a', placeId: 'p1', estimatedCostKrw: 24000 })] }]),
      { p1: 'FOOD' },
      null,
    );

    const transit = summary.categories.find((entry) => entry.key === 'TRANSIT')!;
    expect(transit.count).toBe(0);
    expect(transit.known).toBe(0);
    expect(transit.krw).toBe(0);
  });

  it('일차별로 나눠 센다 — 순서는 일정의 날짜 순서 그대로다', () => {
    const summary = summarizeItineraryBudget(
      itinerary([
        { date: '2026-10-03', items: [item({ id: 'a', placeId: 'p1', estimatedCostKrw: 38000 })] },
        { date: '2026-10-04', items: [
          item({ id: 'b', placeId: 'p2', estimatedCostKrw: 102000 }),
          item({ id: 'c', placeId: 'p3' }),
        ] },
      ]),
      {},
      null,
    );

    expect(summary.days.map((day) => day.krw)).toEqual([38000, 102000]);
    expect(summary.days.map((day) => day.date)).toEqual(['2026-10-03', '2026-10-04']);
    expect(summary.days[1].known).toBe(1);
    expect(summary.days[1].count).toBe(2);
  });

  it('예산이 있으면 남은 금액을, 넘으면 음수를 돌려준다', () => {
    const one = itinerary([{ date: '2026-10-03', items: [item({ id: 'a', placeId: 'p1', estimatedCostKrw: 198000 })] }]);

    expect(summarizeItineraryBudget(one, {}, 300000).remainingKrw).toBe(102000);
    // 🔴 초과는 절댓값이 아니라 음수로 온다. 화면이 부호로 문구를 가른다
    expect(summarizeItineraryBudget(one, {}, 150000).remainingKrw).toBe(-48000);
  });

  it('🔴 예산을 모르면 null 이다 — 0 원 예산과 구분된다', () => {
    const one = itinerary([{ date: '2026-10-03', items: [item({ id: 'a', placeId: 'p1', estimatedCostKrw: 1000 })] }]);

    expect(summarizeItineraryBudget(one, {}, null).remainingKrw).toBeNull();
    expect(summarizeItineraryBudget(one, {}, 0).remainingKrw).toBe(-1000);
  });

  it('🔴 서버가 비용을 하나도 안 주는 지금 상태 — 합계 0, 아는 곳 0, 전부 미정', () => {
    const summary = summarizeItineraryBudget(
      itinerary([{ date: '2026-10-03', items: [
        item({ id: 'a', placeId: 'p1' }),
        item({ id: 'b', placeId: 'p2' }),
      ] }]),
      { p1: 'FOOD', p2: 'CAFE_HEALING' },
      300000,
    );

    expect(summary.known).toBe(0);
    expect(summary.unpriced).toBe(2);
    expect(summary.krw).toBe(0);
    // 아는 것이 없을 때 「예산이 30만원 남았다」고 말하면 안 된다 — 화면이 known 으로 가른다
    expect(summary.remainingKrw).toBe(300000);
  });

  it('빈 일정에도 안 죽는다', () => {
    const summary = summarizeItineraryBudget(itinerary([]), {}, null);
    expect(summary.count).toBe(0);
    expect(summary.days).toEqual([]);
    expect(summary.categories).toHaveLength(4);
  });
});
