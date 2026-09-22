import { getPlace, type Place } from './places';
import { loadItinerary, loadItineraryPace } from '@/plan/itinerary';

// 날짜 순서·항목 순서를 그대로 믿는다 — 서버가 day.items 를 시각순으로 준다는 것은 이미
// 다른 화면(trips/[id]/itinerary.tsx)이 기대는 계약이다. 여러 날 중 가장 나중 날짜의
// 그 안에서 가장 나중 항목을 "마지막"으로 본다.
export type LastVisitedPlaceResult =
  | { state: 'success'; place: Place }
  | { state: 'none' }
  | { state: 'unavailable' | 'offline' | 'error'; message: string };

export async function getLastVisitedPlace(itineraryId: string, accessToken: string | null): Promise<LastVisitedPlaceResult> {
  const itineraryResult = await loadItinerary(itineraryId, accessToken);
  if (itineraryResult.state !== 'success') return itineraryResult;
  const { days } = itineraryResult.itinerary;

  // 날짜별 페이스 조회는 서로 의존하지 않으므로 병렬로 부른다 — 날짜가 많은 여행일수록
  // 순차 호출은 화면 로딩을 불필요하게 늘린다.
  const paceResults = await Promise.all(days.map((_, dayIndex) => loadItineraryPace(itineraryId, dayIndex, accessToken)));

  let lastVisitedPlaceId: string | null = null;
  days.forEach((day, dayIndex) => {
    const paceResult = paceResults[dayIndex];
    if (paceResult.state !== 'success') return; // 하루 조회 실패가 나머지 날짜를 막지 않는다
    const itemsById = new Map(day.items.map((item) => [item.id, item.placeId]));
    for (const paceItem of paceResult.pace.items) {
      if (!paceItem.visited) continue;
      const placeId = itemsById.get(paceItem.itemId);
      if (placeId) lastVisitedPlaceId = placeId;
    }
  });
  if (!lastVisitedPlaceId) return { state: 'none' };

  try {
    return { state: 'success', place: await getPlace(lastVisitedPlaceId) };
  } catch (error) {
    return { state: 'error', message: error instanceof Error ? error.message : '마지막 방문지를 불러오지 못했어요.' };
  }
}
