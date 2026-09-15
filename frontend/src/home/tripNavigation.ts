import { loadTripItineraries } from '@/trip/trips';

/** 홈의 여행 카드는 여행 식별자를 받지만 상세 화면은 일정 식별자를 요구한다. */
export async function resolveHomeTripDestination(tripId: string, accessToken: string | null) {
  const result = await loadTripItineraries(tripId, accessToken);
  if (result.state === 'success' && result.itineraries.length === 1) {
    return `/trips/${result.itineraries[0].itineraryId}/itinerary`;
  }
  // 일정이 없거나 여러 개이거나 조회에 실패하면 내 여행에서 상태와 선택지를 보여준다.
  return '/trips';
}
