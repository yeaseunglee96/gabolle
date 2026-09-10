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

// 🔴 백엔드에 /api/v1/festivals 자체가 아직 없다(컨트롤러 0건, 2026-09-10 실측).
// 그래서 이 화면은 지금 항상 비어 보인다 — API가 연결되기 전까지, 실제로 부산에서
// 해마다 열리는 축제 이름은 그대로 쓰되(거짓 정보를 주지 않으려고) 날짜는 요청한
// 기간 안에서 임의로 계산한 예시일 뿐이다. isSample 이 true 인 항목은 화면에서
// "샘플" 배지를 반드시 붙인다 — 실제 일정처럼 보이면 안 된다.
export type SampleFestival = Festival & { isSample: true };

function toIsoDate(date: Date) {
  return date.toISOString().slice(0, 10);
}

export function buildSampleFestivals(startDate: string, endDate: string): SampleFestival[] {
  const start = new Date(`${startDate}T00:00:00`);
  const end = new Date(`${endDate}T00:00:00`);
  const spanDays = Math.max(1, Math.round((end.getTime() - start.getTime()) / 86_400_000));
  const NAMES: Array<[string, string, string]> = [
    ['부산불꽃축제', 'Busan Fireworks Festival', '부산 해운대구 해운대해수욕장'],
    ['광안리어방축제', 'Gwangalli Eobang Festival', '부산 수영구 광안리해수욕장'],
    ['부산국제영화제(BIFF)', 'Busan International Film Festival', '부산 해운대구 영화의전당'],
  ];
  return NAMES.map(([nameKo, titleEn, address], index) => {
    const offset = Math.round((spanDays / (NAMES.length + 1)) * (index + 1));
    const festivalStart = new Date(start.getTime() + offset * 86_400_000);
    const festivalEnd = new Date(festivalStart.getTime() + 2 * 86_400_000);
    return {
      placeId: `sample-festival-${index}`,
      title: null,
      nameKo,
      titleEn,
      address,
      startDate: toIsoDate(festivalStart < end ? festivalStart : start),
      endDate: toIsoDate(festivalEnd < end ? festivalEnd : end),
      overlapDates: [],
      imageUrl: null,
      admissionFee: null,
      localScore: null,
      isSample: true,
    };
  });
}

export async function getFestivals(startDate: string, endDate: string, signal?: AbortSignal) {
  const query = new URLSearchParams({ startDate, endDate });
  try {
    const response = await apiRequest<FestivalResponse>(`/api/v1/festivals?${query.toString()}`, { signal });
    return Array.isArray(response.festivals) ? response.festivals : [];
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') throw error;
    return buildSampleFestivals(startDate, endDate);
  }
}
