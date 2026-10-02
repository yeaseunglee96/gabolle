// 쓴 돈 적기의 「일정에서 고르기」 — 이 여행 일정의 장소(S15P21E201-1935, UI 캔버스 ㉕ 「쓴 돈 적기」).
// 밥을 먹은 곳을 손으로 치지 않고 누르게 한다. 여행 중이면 오늘 장소가 먼저다 — 지금 쓴 돈은 대개 오늘 간 곳이다.
import type { ItineraryDto } from '@/plan/itinerary';

export type PlanPlace = { title: string; nameEn: string | null };

/** 고를 장소 — 오늘 날이 있으면 그날 것만, 없으면 모든 날을 순서대로. 같은 이름은 한 번만, 최대 `limit` 개. */
export function planPlacesFor(itinerary: Pick<ItineraryDto, 'days'> | null | undefined, todayKey: string, limit = 8): PlanPlace[] {
  if (!itinerary) return [];
  const today = itinerary.days.find((day) => day.date === todayKey);
  const days = today ? [today] : itinerary.days;
  const seen = new Set<string>();
  const out: PlanPlace[] = [];
  for (const day of days) {
    for (const item of day.items) {
      const title = item.title?.trim();
      if (!title || seen.has(title)) continue;
      seen.add(title);
      out.push({ title, nameEn: item.nameEn ?? null });
      if (out.length >= limit) return out;
    }
  }
  return out;
}
