// 저장한 장소 번호 → 화면에 그릴 모양. 저장한 장소 목록(SavedPlacesBody)과 여행 만들기의
// 「저장한 후보」 줄(SavedCandidates)이 같이 쓴다 — 두 곳이 따로 바꾸면 같은 장소가 두 이름으로 보인다.
//
// 서버의 저장 목록은 장소 번호만 준다. 그래서 장소마다 상세를 한 번 더 부른다(S15P21E201-1969 카드 3 이
// 목록 응답에 이름·사진을 실어 주면 이 왕복이 사라진다).
import type { LanguageCode } from '@/i18n/languages';
import { ApiClientError } from '@/api/client';
import { DEMO_PLACES } from '@/discovery/savedPlaces';
import { getPlace, needsFoodSafetyCheck } from '@/discovery/places';
import { placeNameForLanguage } from '@/discovery/romanize';
import { otherNameFor } from '@/discovery/localNames';
import { addressForLanguage } from '@/discovery/localAddress';
import type { MustVisitPlace } from '@/plan/PlanProvider';

export type SavedPlaceCard = { placeId: string; title: string; subtitle: string; image: number | { uri: string } | null; needsFoodSafetyCheck: boolean };

// 카카오 평점처럼 근거 없는 값을 지어내 보여주지 않는다. 상세 화면에 이미 있던 배지(알레르기 확인 필요)만
// 옮긴다 — 데모 장소는 features 자체가 없어 자연히 꺼진 채로 남는다.
export async function resolveSavedPlace(placeId: string, tx: (ko: string, en: string) => string, language: LanguageCode): Promise<SavedPlaceCard | null> {
  if (placeId in DEMO_PLACES) {
    const demo = DEMO_PLACES[placeId as keyof typeof DEMO_PLACES];
    return { placeId, title: tx(demo.titleKo, demo.titleEn), subtitle: tx(demo.subtitleKo, demo.subtitleEn), image: demo.image, needsFoodSafetyCheck: false };
  }
  try {
    const place = await getPlace(placeId);
    return { placeId, title: placeNameForLanguage(place.nameKo, otherNameFor(place.nameEn, place.localNames, language), language), subtitle: addressForLanguage(place, language), image: place.photoUrl ? { uri: place.photoUrl } : null, needsFoodSafetyCheck: needsFoodSafetyCheck(place) };
  } catch (error) {
    // 지워졌거나(404) 서버가 잠깐 안 되는 장소는 목록에서 조용히 뺀다 — 저장한 것 자체는 남아 있으니
    // 다음에 다시 열면 보일 수 있다.
    if (error instanceof ApiClientError) return null;
    return null;
  }
}

/**
 * 저장한 후보 하나를 「꼭 가고 싶은 곳」 모양으로. 추천 엔진은 **우리 DB 의 장소 번호**만 자리를
 * 비워 둘 수 있어서, 데모 카드(DB 에 없는 세 곳)와 못 불러온 장소는 고를 수 없게 null 로 뺀다.
 */
export async function resolveSavedCandidate(placeId: string): Promise<MustVisitPlace | null> {
  if (placeId in DEMO_PLACES) return null;
  try {
    const place = await getPlace(placeId);
    return { placeId, nameKo: place.nameKo, nameEn: place.nameEn, lat: place.lat, lng: place.lng };
  } catch {
    return null;
  }
}
