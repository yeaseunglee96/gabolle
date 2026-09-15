import { apiRequest } from '@/api/client';

// jaehyeon 님 계약(2026-09-07, S15P21E201-476 · 다국어는 -430): GET /api/v1/places/{placeId}.
// nameKo·category·address·lat·lng·features·itineraryInclusion 은 이미 온다. addressEn·photoUrl·
// photoSource 는 값이 없으면 칸 자체가 안 온다(택시 카드의 addressEn 과 같은 규칙).
//
// openingHours·priceLevel(S15P21E201-478 착수 시 PlaceDetailResponse.java 원본으로 재확인):
// place_feature 의 OPENING_HOURS·PRICE_LEVEL 표식을 옮긴 자리다. 그 표식에 행이 있으면
// (VERIFIED·ESTIMATED·UNKNOWN 무엇이든) 실리고, 행이 아예 없으면(NOT_COLLECTED) 키 자체가
// 빠진다 — 그래서 optional 이다. value 의 JSON 구조는 데이터 담당이 아직 못 정했다고
// 문서에 적혀 있어(PlaceFeatureView.java) 화면은 "있다/없다"·VERIFIED 여부만 보고 값
// 자체는 그대로 문자열로 펼친다(숫자로 지레짐작해 서식을 걸지 않는다 — 시작 화면 흰
// 화면 사고, 상세설계서 v2 P-02).
//
// provenance·itineraryInclusion 의 정확한 칸 구조는 아직 화면에서 쓸 데가 없어 타입에 안
// 넣는다 — 모르는 것을 아는 척하지 않는다(CONTRIBUTING.md 0절). 쓸 데가 생기면 그때
// PlaceDetailResponse.java 로 다시 확인한다.
//
// photoUrl·photoSource 는 계약에는 있지만(2026-09-08 jaehyeon 님 확인) 아직 채우는 경로가
// 없어 늘 비어 있다 — 값이 없으면 NON_NULL 규칙 때문에 키 자체가 안 온다. 데이터 적재는
// S15P21E201-146(jaehyeon 님 담당, 미착수)이 끝나야 온다. 그래서 여기서는 선택 필드로만
// 받고, 화면에서는 없으면 지금처럼 자리표시만 보여준다.
export type PlaceFeature = { featureType: string; [key: string]: unknown };

export type EvidenceStatus = 'VERIFIED' | 'ESTIMATED' | 'UNKNOWN';
export type FeatureSlot = { value: unknown; evidenceStatus: EvidenceStatus };

export type Place = {
  placeId: string;
  nameKo: string;
  nameEn: string | null;
  category: string;
  address: string;
  addressEn?: string;
  lat: number;
  lng: number;
  features: PlaceFeature[];
  photoUrl?: string;
  photoSource?: string;
  openingHours?: FeatureSlot;
  priceLevel?: FeatureSlot;
};

// 값이 있어도 VERIFIED·ESTIMATED·UNKNOWN 셋 다 화면에는 보여준다(정보 없음과 다르다) —
// UNKNOWN 은 "확인은 했는데 결과가 없다"는 뜻이라 그 자체가 정보다. 다만 추정값은
// "추정"이라고 붙여 확정값과 헷갈리지 않게 한다.
export function formatFeatureSlot(slot: FeatureSlot | undefined, tx: (ko: string, en: string) => string): string | null {
  if (!slot) return null;
  if (slot.evidenceStatus === 'UNKNOWN' || slot.value == null) return tx('확인 안 됨', 'Unconfirmed');
  const text = typeof slot.value === 'string' || typeof slot.value === 'number' ? String(slot.value) : JSON.stringify(slot.value);
  return slot.evidenceStatus === 'ESTIMATED' ? tx(`${text} (추정)`, `${text} (est.)`) : text;
}

// 로컬점수(LOCALITY_SCORE) 표식이 붙어 있는지만 확인한다. 표식 안의 값 칸 이름은 아직 몰라서
// 숫자를 꺼내 보여주지 않는다 — 있는지 없는지만 배지로 표시한다.
export function hasLocalityScore(place: Place) {
  return place.features.some((feature) => feature.featureType === 'LOCALITY_SCORE');
}

// 장소 종류(category) 값의 정확한 목록을 아직 못 받았다(field/placePhrases.ts의
// defaultTabForCategory와 같은 사정). 그래서 정확히 아는 척하지 않고, 식당·카페로 흔히
// 쓰이는 키워드가 들어 있으면 식음료 장소로 본다.
function isFoodPlace(category: string) {
  return /FOOD|RESTAURANT|CAFE|맛집|카페|식당/i.test(category);
}

// 안전 정보(알레르기·식단) 확인 필요 여부 — S15P21E201-478·-141.
// 대조표(user_place_code_map, S15P21E201-545 마이그레이션)의 짝: 알레르기 = ALLERGEN_TAG,
// 식단 = DIETARY_SUPPORT_TAG. 둘 다 태그형이라 "해당 없음"과 "확인 안 됨"을 이 배열만으로는
// 구분할 수 없다 — 행이 없으면 알레르기 유발 성분이 실제로 없는 것인지 아무도 확인을
// 안 한 것인지 똑같이 아무 표식도 안 남는다. 그래서 이 둘만은 "없으면 안전하다"로 읽지
// 않는다(기획서 2.1절 원칙 3) — 행이 하나도 없으면 무조건 확인 필요로 취급한다.
export function needsFoodSafetyCheck(place: Place) {
  if (!isFoodPlace(place.category)) return false;
  const hasAllergenInfo = place.features.some((feature) => feature.featureType === 'ALLERGEN_TAG');
  const hasDietInfo = place.features.some((feature) => feature.featureType === 'DIETARY_SUPPORT_TAG');
  return !hasAllergenInfo || !hasDietInfo;
}

// S15P21E201-325: "확인 못 함"과 "확인했고 문제 없음"을 화면에서 다르게 보여줘야 한다 —
// 안 그러면 needsFoodSafetyCheck 가 false 인 자리에 아무것도 안 뜨고, 그 빈 자리를
// 사용자는 "안전하다고 확인됨"과 구분 못 한다. 두 태그가 전부 있어야만(=행이 없다는
// 뜻이 아니어야만) "확인됨"이고, 식음료 장소가 아니면 이 표시 자체가 의미 없다.
export function hasFoodSafetyConfirmed(place: Place) {
  if (!isFoodPlace(place.category)) return false;
  const hasAllergenInfo = place.features.some((feature) => feature.featureType === 'ALLERGEN_TAG');
  const hasDietInfo = place.features.some((feature) => feature.featureType === 'DIETARY_SUPPORT_TAG');
  return hasAllergenInfo && hasDietInfo;
}

// 장소 이름 한글·영문 병기(S15P21E201-264) — 언어 설정과 무관하게 "해운대 해수욕장 (Haeundae
// Beach)" 형태로 둘 다 보여준다. 영문 이름이 한국인 택시 기사에게는 쓸모없고, 한글 이름만
// 보여주면 영어 사용자가 못 읽는다. 영문이 없으면 괄호 없이 한국어 원문만 보여준다 — 빈
// 문자열을 보여주지 않는다.
export function bilingualPlaceName(nameKo: string, nameEn?: string | null) {
  const trimmedEn = nameEn?.trim();
  return trimmedEn ? `${nameKo} (${trimmedEn})` : nameKo;
}

export function getPlace(placeId: string, signal?: AbortSignal) {
  return apiRequest<Place>(`/api/v1/places/${encodeURIComponent(placeId)}`, { signal });
}

// 계약: backend/src/main/java/com/gabolle/backend/place/api/PlaceQueryController.java (S15P21E201-462).
// GET /api/v1/places?query=&limit= — query 와 facetType 은 정확히 하나만 보내야 한다(둘 다
// 없거나 둘 다 있으면 400). 여기서는 이름 검색만 쓰므로 query 만 보낸다.
export type PlaceSearchItem = { placeId: string; nameKo: string; nameEn: string | null; category: string; address: string; lat: number; lng: number };

type PlacePageDto = { items: PlaceSearchItemDto[]; limit: number; nextCursor: string | null; hasNext: boolean; rankTruncated: boolean };
type PlaceSearchItemDto = { placeId: string; nameKo: string; nameEn: string | null; category: string; address: string; lat: number; lng: number; matchedField: 'NAME_KO' | 'NAME_EN' | null };

export async function searchPlacesByName(query: string, signal?: AbortSignal): Promise<PlaceSearchItem[]> {
  const dto = await apiRequest<PlacePageDto>(`/api/v1/places?query=${encodeURIComponent(query)}&limit=8`, { signal });
  return dto.items.map(({ placeId, nameKo, nameEn, category, address, lat, lng }) => ({ placeId, nameKo, nameEn, category, address, lat, lng }));
}
