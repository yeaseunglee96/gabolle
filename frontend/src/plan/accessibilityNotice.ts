// 휠체어 안내 창에 적을 숫자 — 승차권에 보이는 일정만 센다(S15P21E201-1732).
//
// 🔴 전에는 추천 결과 목록으로 셌다. 그 목록은 코스 A·B·C 세 안의 장소를 합친 것이라, 승차권의 방문지는 8곳인데
//    창은 「24곳 중 23곳」이라고 했다. 서버는 일정 응답의 항목마다 warningCodes 를 준다 — 그것으로 센다.
import type { ItineraryDto } from './itinerary';

export const ACCESSIBILITY_UNVERIFIED = 'ACCESSIBILITY_UNVERIFIED';

/**
 * 보이는 일정에서 휠체어로 들어갈 수 있는지 확인 안 된 곳 수와 전체 수.
 * 항목 경고 칸이 하나도 없으면(옛 서버) null — 모른다. 그때 0곳이라고 말하면 「확인됐다」로 읽힌다.
 */
export function itineraryAccessibilityCounts(itinerary: { days: Array<{ items: Array<Pick<ItineraryDto['days'][number]['items'][number], 'warningCodes'>> }> }): { unverified: number; total: number } | null {
  const items = itinerary.days.flatMap((day) => day.items);
  if (!items.some((item) => Array.isArray(item.warningCodes))) return null;
  return { unverified: items.filter((item) => item.warningCodes?.includes(ACCESSIBILITY_UNVERIFIED)).length, total: items.length };
}
