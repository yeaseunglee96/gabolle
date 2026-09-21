// 여행 카드 커버 사진 — S15P21E201-1378.
//
// 서버 여행 목록에는 사진이 없다(백엔드 1370 요청 중). 그때까지 화면이 스스로 만든다:
// 여행 → 일정 → 앞 정차지 여섯 가운데 사진이 있는 곳. 여행마다 호출이 둘 늘지만 카드가
// 글자뿐인 것보다 낫고, 한 번 받은 것은 세션 동안 기억한다. 서버가 커버를 주면 이 파일은 지운다.
import { loadItinerary } from '@/plan/itinerary';
import { loadPlacePhotos } from '@/plan/placePhotos';
import { loadTripItineraries } from '@/trip/trips';

const cache = new Map<string, Promise<string | null>>();

export function loadTripCover(tripId: string, accessToken: string | null): Promise<string | null> {
  let pending = cache.get(tripId);
  if (!pending) {
    pending = (async () => {
      const refs = await loadTripItineraries(tripId, accessToken);
      if (refs.state !== 'success' || !refs.itineraries.length) return null;
      const itinerary = await loadItinerary(refs.itineraries[0].itineraryId, accessToken);
      if (itinerary.state !== 'success') return null;
      const ids = itinerary.itinerary.days.flatMap((day) => day.items).slice(0, 6).map((item) => item.placeId).filter(Boolean);
      const photos = await loadPlacePhotos(ids);
      return ids.map((id) => photos[id]?.photoUrl ?? null).find(Boolean) ?? null;
    })().catch(() => null);
    cache.set(tripId, pending);
  }
  return pending;
}
