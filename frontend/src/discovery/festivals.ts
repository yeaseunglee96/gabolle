import { apiRequest } from '@/api/client';

// jaehyeon 님 계약(2026-09-07, S15P21E201-465): 쿼리는 startDate/endDate 다
// (from/to/region 이 아니다). title 은 회차 이름이 없으면 키 자체가 빠지므로
// 화면에서는 nameKo 로 대신 보여준다. overlapDates 는 여행 기간과 겹치는 날짜만
// 서버가 골라 주는 값이라, 이 화면(기간만 고르는 단독 조회)에서는 안 써도 된다.
export type Festival = {
  placeId: string;
  title: string | null;
  nameKo: string;
  titleEn: string | null;
  address: string;
  startDate: string;
  endDate: string;
  overlapDates: string[];
  imageUrl: string | null;
  admissionFee?: string | null;
  localScore?: number | null;
};

export function festivalDisplayTitle(festival: Festival) {
  return festival.title ?? festival.nameKo;
}

type FestivalResponse = { festivals: Festival[] };

export async function getFestivals(startDate: string, endDate: string, signal?: AbortSignal) {
  const query = new URLSearchParams({ startDate, endDate });
  const response = await apiRequest<FestivalResponse>(`/api/v1/festivals?${query.toString()}`, { signal });
  return Array.isArray(response.festivals) ? response.festivals : [];
}
