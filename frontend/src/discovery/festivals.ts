import { apiRequest } from '@/api/client';
import type { FeatureSlot, PhotoLicense, PhotoSubject } from '@/discovery/places';
import type { LocalNames } from '@/discovery/localNames';

// 칸 이름은 서버(FestivalResponse.FestivalItem)를 그대로 따른다. 서버가 NON_NULL 이라
// 값이 없는 칸은 키째 빠지므로 물음표를 붙인다. 입장료(priceLevel)는 문자열이 아니라
// {value, evidenceStatus} 한 칸이다 — 장소 피처와 같은 모양이라 표기도 같은 함수를 쓴다.
export type Festival = {
  placeId: string;
  title?: string | null;
  nameKo: string;
  nameEn?: string | null;
  /** 일본어·중국어 이름 — 관광공사가 번역해 둔 곳만(S15P21E201-1859). 없으면 칸째 빠져 온다. */
  localNames?: LocalNames;
  address: string;
  startDate: string;
  endDate: string;
  overlapDates: string[];
  photoUrl?: string | null;
  // — 사진이 그 축제를 찍은 것이 아닐 수 있다. 둘 다 없으면 키째 안 오고
  // 그때 화면은 지금과 똑같이 그린다(서버 배포를 기다리지 않는다). 뜻은 places.ts 에 있다.
  photoSource?: string | null;
  photoSubject?: PhotoSubject | null;
  // 위키미디어 사진의 라이선스 — 뜻은 places.ts 의 PhotoLicense(S15P21E201-1610).
  photoLicense?: PhotoLicense | null;
  priceLevel?: FeatureSlot;
};

export function festivalDisplayTitle(festival: Festival) {
  return festival.title ?? festival.nameKo;
}

type FestivalResponse = { items: Festival[]; count: number };

export async function getFestivals(startDate: string, endDate: string, signal?: AbortSignal) {
  const query = new URLSearchParams({ startDate, endDate });
  try {
    const response = await apiRequest<FestivalResponse>(`/api/v1/festivals?${query.toString()}`, { signal });
    return Array.isArray(response.items) ? response.items : [];
  } catch (error) {
    // 🔴 오류를 삼키지 않는다 — S15P21E201-1346.
    //    예전에는 여기서 부산불꽃축제·광안리어방축제·BIFF 를 «지어내서» 돌려줬다.
    //    이름과 주소는 실재하는데 날짜만 조회 기간을 나눠 만든 값이라, 12월로 조회하면
    //    「부산불꽃축제 12/08~12/10」 같은 «없는 일정»이 그럴듯하게 나왔다.
    //    여행 계획을 거기 맞춘 사람은 헛걸음한다.
    //
    //    화면(app/festivals.tsx)에는 「불러오지 못했습니다 · 다시 시도」가 이미 있다.
    //    오류를 삼키는 바람에 그 자리가 «한 번도 안 뜨는» 죽은 코드였다. 이제 산다.
    throw error;
  }
}
