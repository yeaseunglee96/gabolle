import { apiRequest } from '@/api/client';

// jaehyeon 님 계약(2026-09-07, S15P21E201-476 · 다국어는 -430): GET /api/v1/places/{placeId}.
// nameKo·category·address·lat·lng·features·itineraryInclusion 은 이미 온다. addressEn·photoUrl·
// photoSource 는 값이 없으면 칸 자체가 안 온다(택시 카드의 addressEn 과 같은 규칙).
// openingHours·priceLevel·provenance·itineraryInclusion 의 정확한 칸 구조는 아직 못 받아서(모르는
// 것을 아는 척하지 않는다 — CLAUDE.md), 여기서는 타입에 넣지 않는다. 화면에서 쓰려면 그때 다시
// jaehyeon 님께 구조를 확인하고 추가한다.
//
// photoUrl·photoSource 는 계약에는 있지만(2026-09-08 jaehyeon 님 확인) 아직 채우는 경로가
// 없어 늘 비어 있다 — 값이 없으면 NON_NULL 규칙 때문에 키 자체가 안 온다. 데이터 적재는
// S15P21E201-146(jaehyeon 님 담당, 미착수)이 끝나야 온다. 그래서 여기서는 선택 필드로만
// 받고, 화면에서는 없으면 지금처럼 자리표시만 보여준다.
export type PlaceFeature = { featureType: string; [key: string]: unknown };

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
};

// 로컬점수(LOCALITY_SCORE) 표식이 붙어 있는지만 확인한다. 표식 안의 값 칸 이름은 아직 몰라서
// 숫자를 꺼내 보여주지 않는다 — 있는지 없는지만 배지로 표시한다.
export function hasLocalityScore(place: Place) {
  return place.features.some((feature) => feature.featureType === 'LOCALITY_SCORE');
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
