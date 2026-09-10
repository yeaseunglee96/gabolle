import { apiRequest } from '@/api/client';

// jaehyeon 님 계약(S15P21E201-217): GET /api/v1/places/{placeId}/taxi-card.
// addressEn 은 값이 없으면 키 자체가 안 온다(NON_NULL 직렬화) — 화면은 영문 주소가
// 없으면 그 칸을 아예 그리지 않아야 한다("정보 없음" 같은 자리표시도 남기지 않는다).
// driverSentence 는 항상 한국어다 — 읽는 사람이 택시 기사라 언어 설정과 무관하다.
export type TaxiCard = {
  placeId: string;
  nameKo: string;
  addressKo: string;
  addressEn?: string;
  resolvedLanguage: string;
  driverSentence: string;
};

export function getTaxiCard(placeId: string, signal?: AbortSignal) {
  return apiRequest<TaxiCard>(`/api/v1/places/${encodeURIComponent(placeId)}/taxi-card`, { signal });
}
