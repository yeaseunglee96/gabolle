import { resolveTripItinerary, type TripSummaryDto } from '@/trip/trips';

/**
 * 홈의 여행 카드는 여행을 받지만 상세 화면은 일정 식별자를 요구한다.
 * 🔴 일정이 여럿이어도 확정한 일정을 바로 연다 — 내 여행 카드와 같은 규칙(resolveTripItinerary, S15P21E201-1605).
 */
export async function resolveHomeTripDestination(trip: Pick<TripSummaryDto, 'tripId' | 'currentItineraryId'>, accessToken: string | null) {
  const result = await resolveTripItinerary(trip, accessToken);
  if (result.state === 'open') return `/trips/${result.itineraryId}/itinerary`;
  // 일정이 없거나 조회에 실패하면 내 여행에서 상태와 할 일을 보여준다.
  return '/trips';
}
