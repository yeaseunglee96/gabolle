// 예산 명세 — 시안 design_handoff_itinerary 7절 (S15P21E201-1432).
//
// 🔴 **모르는 값을 0 으로 세지 않는다.** 이 화면은 이미 하루 합계에서 그렇게 하고 있다
//    (itinerary.tsx 의 dayCost) — 값이 있는 칸만 더하고, 몇 칸을 알고 있는지를 같이 들고
//    다닌다. 여기서 규칙을 새로 짓지 않고 그것을 여행 전체로 넓혔다.
//
//    0 으로 세면 합계가 「싸다」고 거짓말한다. 12곳 중 2곳만 값이 있는 일정이
//    「38,000원」으로 보이고, 사람은 그 숫자를 믿고 예산을 짠다.
//
// 🔴 **서버는 지금 항목 비용을 하나도 안 준다.** 장소 표에 가격 칸이 없어서다 —
//    backend 의 RecommendationResultQueryService 가 `null, // estimatedCostKrw — 비용
//    데이터가 없다` 로 박아 두었고, 시험(`imageUrl·estimatedCostKrw 는 항상 null`)이
//    그것을 못박고 있다. 그러니 이 계산은 **당분간 known=0 을 돌려주는 것이 정상**이다.
//    버그가 아니다. 서버가 값을 싣는 날 이 코드가 그대로 받는다.
import type { ItineraryDto } from '@/plan/itinerary';

/** 시안 7절의 분해 막대 네 갈래. */
export type BudgetCategoryKey = 'FOOD' | 'CAFE' | 'ADMISSION' | 'TRANSIT';

/**
 * 장소 갈래 → 예산 갈래.
 *
 * 🔴 갈래를 **모르면 null 이다.** 아무 칸에나 넣지 않는다 — 장소 상세를 아직 못 받았거나
 *    서버에 갈래가 없는 곳을 「입장·체험」으로 세면, 화면은 그럴듯한데 근거가 없다.
 *    아는 갈래가 새로 생겨도(축제·야시장 등) 「입장·체험」으로 떨어지므로 안 깨진다.
 */
export function budgetCategoryOf(placeCategory: string | null | undefined): BudgetCategoryKey | null {
  if (!placeCategory) return null;
  if (placeCategory === 'FOOD') return 'FOOD';
  if (placeCategory === 'CAFE_HEALING') return 'CAFE';
  return 'ADMISSION';
}

/** 한 갈래의 합계. `known < count` 면 그 갈래에도 값을 모르는 곳이 있다. */
export type BudgetCategory = {
  key: BudgetCategoryKey;
  /** 이 갈래에 든 방문지 수 */
  count: number;
  /** 그중 비용을 아는 곳 */
  known: number;
  /** 아는 곳만 더한 값 — 「적어도 이만큼」이다 */
  krw: number;
};

export type BudgetDay = { dayIndex: number; date: string; count: number; known: number; krw: number };

export type BudgetSummary = {
  /** 아는 곳만 더한 여행 전체 합계 */
  krw: number;
  /** 비용을 아는 방문지 수 */
  known: number;
  /** 전체 방문지 수 */
  count: number;
  /** 비용을 모르는 방문지 수 — 화면이 「n곳 비용 미정」으로 적는 값 */
  unpriced: number;
  categories: BudgetCategory[];
  days: BudgetDay[];
  /** 여행에 적어 둔 예산. 안 정했거나 못 받았으면 null */
  budgetKrw: number | null;
  /**
   * 예산 − 총액. 예산을 모르면 null.
   * 🔴 음수면 초과다. 화면이 부호로 문구를 가른다 — 여기서 절댓값을 취하지 않는다.
   */
  remainingKrw: number | null;
};

const ORDER: BudgetCategoryKey[] = ['FOOD', 'CAFE', 'ADMISSION', 'TRANSIT'];

/**
 * 여행 전체 비용 명세.
 *
 * @param categoryByPlaceId 장소 갈래. 아직 못 받은 장소는 빠져 있어도 된다 — 그 곳은
 *   갈래 줄에 안 들어가고 총계에는 들어간다. 갈래를 아는 것과 비용을 아는 것은 다른 문제다.
 */
export function summarizeItineraryBudget(
  itinerary: ItineraryDto,
  categoryByPlaceId: Record<string, string | null | undefined>,
  budgetKrw: number | null,
): BudgetSummary {
  const buckets = new Map<BudgetCategoryKey, BudgetCategory>(
    // 🔴 자리를 미리 만든다. 값이 0 이어도 줄을 그려야 **서버가 값을 싣는 날 어디로
    //    들어오는지**가 보인다 — 이 화면의 「수단 — 아직 없어요」 칸과 같은 이유다.
    ORDER.map((key) => [key, { key, count: 0, known: 0, krw: 0 }]),
  );

  const days: BudgetDay[] = [];
  let krw = 0;
  let known = 0;
  let count = 0;

  itinerary.days.forEach((day, dayIndex) => {
    let dayKrw = 0;
    let dayKnown = 0;

    day.items.forEach((item) => {
      const priced = typeof item.estimatedCostKrw === 'number';
      const cost = priced ? (item.estimatedCostKrw as number) : 0;
      count += 1;
      dayKnown += priced ? 1 : 0;
      dayKrw += cost;

      const bucketKey = budgetCategoryOf(categoryByPlaceId[item.placeId]);
      if (bucketKey) {
        const bucket = buckets.get(bucketKey) as BudgetCategory;
        bucket.count += 1;
        bucket.known += priced ? 1 : 0;
        bucket.krw += cost;
      }
    });

    krw += dayKrw;
    known += dayKnown;
    days.push({ dayIndex, date: day.date, count: day.items.length, known: dayKnown, krw: dayKrw });
  });

  return {
    krw,
    known,
    count,
    unpriced: count - known,
    categories: ORDER.map((key) => buckets.get(key) as BudgetCategory),
    days,
    budgetKrw,
    // 🔴 예산이 0 이어도 「모름」이 아니다. null 만 모름이다.
    remainingKrw: budgetKrw == null ? null : budgetKrw - krw,
  };
}
