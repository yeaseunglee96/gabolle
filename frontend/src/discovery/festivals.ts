import { apiRequest } from '@/api/client';
import type { FeatureSlot, PhotoSubject } from '@/discovery/places';

// 칸 이름은 서버(FestivalResponse.FestivalItem)를 그대로 따른다. 서버가 NON_NULL 이라
// 값이 없는 칸은 키째 빠지므로 물음표를 붙인다. 입장료(priceLevel)는 문자열이 아니라
// {value, evidenceStatus} 한 칸이다 — 장소 피처와 같은 모양이라 표기도 같은 함수를 쓴다.
export type Festival = {
  placeId: string;
  title?: string | null;
  nameKo: string;
  nameEn?: string | null;
  address: string;
  startDate: string;
  endDate: string;
  overlapDates: string[];
  photoUrl?: string | null;
  // — 사진이 그 축제를 찍은 것이 아닐 수 있다. 둘 다 없으면 키째 안 오고
  // 그때 화면은 지금과 똑같이 그린다(서버 배포를 기다리지 않는다). 뜻은 places.ts 에 있다.
  photoSource?: string | null;
  photoSubject?: PhotoSubject | null;
  priceLevel?: FeatureSlot;
};

export function festivalDisplayTitle(festival: Festival) {
  return festival.title ?? festival.nameKo;
}

type FestivalResponse = { items: Festival[]; count: number };

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
  return NAMES.map(([nameKo, nameEn, address], index) => {
    const offset = Math.round((spanDays / (NAMES.length + 1)) * (index + 1));
    const festivalStart = new Date(start.getTime() + offset * 86_400_000);
    const festivalEnd = new Date(festivalStart.getTime() + 2 * 86_400_000);
    return {
      placeId: `sample-festival-${index}`,
      title: null,
      nameKo,
      nameEn,
      address,
      startDate: toIsoDate(festivalStart < end ? festivalStart : start),
      endDate: toIsoDate(festivalEnd < end ? festivalEnd : end),
      overlapDates: [],
      photoUrl: null,
      isSample: true,
    };
  });
}

export async function getFestivals(startDate: string, endDate: string, signal?: AbortSignal) {
  const query = new URLSearchParams({ startDate, endDate });
  try {
    const response = await apiRequest<FestivalResponse>(`/api/v1/festivals?${query.toString()}`, { signal });
    return Array.isArray(response.items) ? response.items : [];
  } catch (error) {
    if (error instanceof DOMException && error.name === 'AbortError') throw error;
    return buildSampleFestivals(startDate, endDate);
  }
}
