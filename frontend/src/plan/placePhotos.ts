// 정차지 사진 — S15P21E201-1378.
//
// 일정 항목(ItineraryItemDto)에는 사진 칸이 없다. 장소 상세(GET /places/{id})의 photoUrl 을 정차지마다
// 받아 온다. 관광공사 사진이 있는 곳(관광지·시장)은 사진, 식당·카페는 대개 없다 — 없으면 갈래 아이콘.
// 같은 장소를 두 번 묻지 않게 한 번 받은 것은 세션 동안 기억한다.
import { getPlace, type PhotoLicense } from '@/discovery/places';

/** nameEn — 일정 항목에는 영어 이름 칸이 없어서 여기서 함께 싣는다(영어 화면의 장소 이름, S15P21E201-1735). */
export type PlacePhoto = { photoUrl: string | null; photoSource: string | null; category: string | null; photoLicense?: PhotoLicense | null; nameEn?: string | null };

const cache = new Map<string, Promise<PlacePhoto>>();
const NONE: PlacePhoto = { photoUrl: null, photoSource: null, category: null };

export function loadPlacePhoto(placeId: string): Promise<PlacePhoto> {
  if (!placeId) return Promise.resolve(NONE);
  let pending = cache.get(placeId);
  if (!pending) {
    pending = getPlace(placeId)
      .then((place) => ({ photoUrl: place.photoUrl ?? null, photoSource: place.photoSource ?? null, category: place.category ?? null, photoLicense: place.photoLicense ?? null, nameEn: place.nameEn ?? null }))
      .catch(() => NONE);
    cache.set(placeId, pending);
  }
  return pending;
}

/** 여럿을 한꺼번에 — 순서는 들어온 순서, 실패한 것은 빈 값. */
export async function loadPlacePhotos(placeIds: string[]): Promise<Record<string, PlacePhoto>> {
  const unique = [...new Set(placeIds.filter(Boolean))];
  const results = await Promise.all(unique.map((id) => loadPlacePhoto(id)));
  const out: Record<string, PlacePhoto> = {};
  unique.forEach((id, i) => { out[id] = results[i]; });
  return out;
}

/** 사진이 없을 때의 갈래 글자 — 지어내지 않고 갈래만 말한다. */
export function categoryGlyph(category: string | null | undefined): string {
  switch (category) {
    case 'FOOD': return '🍽';
    case 'CAFE_HEALING': return '☕';
    case 'SEA_BEACH': return '🌊';
    case 'NATURE_WALK': return '🌿';
    case 'CULTURE_TEMPLE': return '⛩';
    case 'CITY': return '🏙';
    case 'FESTIVAL': case 'FESTIVAL_EVENT': return '🎉';
    case 'NIGHT_VIEW': return '🌉';
    case 'TRADITIONAL_MARKET': case 'NIGHT_MARKET': return '🧺';
    default: return '📍';
  }
}

/** 시험·화면 새로 고침용. */
export function clearPlacePhotoCache() { cache.clear(); }
