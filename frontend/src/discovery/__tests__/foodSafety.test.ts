import { hasFoodSafetyConfirmed, needsFoodSafetyCheck, type Place, type PlaceFeature } from '../places';

// 실측: 장소 「국수락」의 응답이 ALLERGEN_TAG 를 이 모양으로 준다
// 행은 있지만 아무도 조사하지 않았다는 뜻이다. 행의 존재만 세면 "확인됨"으로 뒤집힌다.
const notCollected = (featureType: string): PlaceFeature => ({ featureType, evidenceStatus: 'NOT_COLLECTED', value: null });
const verified = (featureType: string): PlaceFeature => ({ featureType, evidenceStatus: 'VERIFIED', value: false });
const unknown = (featureType: string): PlaceFeature => ({ featureType, evidenceStatus: 'UNKNOWN', value: null });
const estimated = (featureType: string): PlaceFeature => ({ featureType, evidenceStatus: 'ESTIMATED', value: false });

const restaurant = (features: PlaceFeature[]): Place => ({
  placeId: 'p1',
  nameKo: '국수락',
  nameEn: null,
  category: 'FOOD',
  address: '부산',
  lat: 35,
  lng: 129,
  features,
});

describe('음식점 안전 정보 판정', () => {
  it.each([
    ['수집 안 됨', notCollected],
    ['모름', unknown],
    ['추정', estimated],
  ])('%s 상태는 확인으로 치지 않는다', (_label, make) => {
    const place = restaurant([make('ALLERGEN_TAG'), make('DIETARY_SUPPORT_TAG')]);
    expect(hasFoodSafetyConfirmed(place)).toBe(false);
    expect(needsFoodSafetyCheck(place)).toBe(true);
  });

  it('두 태그가 모두 VERIFIED 일 때만 확인됨이다', () => {
    const place = restaurant([verified('ALLERGEN_TAG'), verified('DIETARY_SUPPORT_TAG')]);
    expect(hasFoodSafetyConfirmed(place)).toBe(true);
    expect(needsFoodSafetyCheck(place)).toBe(false);
  });

  it('한쪽만 확인됐으면 확인됨이 아니다', () => {
    const place = restaurant([verified('ALLERGEN_TAG'), notCollected('DIETARY_SUPPORT_TAG')]);
    expect(hasFoodSafetyConfirmed(place)).toBe(false);
    expect(needsFoodSafetyCheck(place)).toBe(true);
  });

  it('식음료 장소가 아니면 두 표시 모두 뜨지 않는다', () => {
    const museum = { ...restaurant([notCollected('ALLERGEN_TAG')]), category: 'CULTURE' };
    expect(hasFoodSafetyConfirmed(museum)).toBe(false);
    expect(needsFoodSafetyCheck(museum)).toBe(false);
  });
});
