// 관광공사 「무슬림 친화 식당」 목록 표식 — S15P21E201-1878 (서버 S15P21E201-1873, source KTO_MUSLIM_FRIENDLY).
import { hasFoodSafetyConfirmed, isMuslimFriendlyListed, needsFoodSafetyCheck, type Place } from '@/discovery/places';

const place = (features: Place['features']): Place => ({
  placeId: 'p', nameKo: '사마르칸트', nameEn: null, category: 'FOOD', address: '부산광역시 동구 중앙대로 1', lat: 35.1, lng: 129.0, features,
});
const LISTED = { featureType: 'DIETARY_SUPPORT_TAG', featureKey: 'HALAL', evidenceStatus: 'VERIFIED', value: true, sourceType: 'KTO_MUSLIM_FRIENDLY' };
const ALLERGEN = { featureType: 'ALLERGEN_TAG', featureKey: 'PEANUT', evidenceStatus: 'VERIFIED', value: false, sourceType: 'OWNER' };

describe('무슬림 친화 식당 목록 표식', () => {
  it('목록 출처의 할랄 표식이면 목록 식당이다', () => {
    expect(isMuslimFriendlyListed(place([LISTED]))).toBe(true);
    expect(isMuslimFriendlyListed(place([{ ...LISTED, sourceType: 'OWNER' }]))).toBe(false);
    expect(isMuslimFriendlyListed(place([{ ...LISTED, evidenceStatus: 'NOT_COLLECTED' }]))).toBe(false);
  });

  it('🔴 목록 표식은 「알레르기·식단 확인됨」을 켜지 않는다 — 목록에 있다는 것이지 가게 식단을 다 확인한 것이 아니다', () => {
    const listedWithAllergen = place([LISTED, ALLERGEN]);
    expect(hasFoodSafetyConfirmed(listedWithAllergen)).toBe(false);
    expect(needsFoodSafetyCheck(listedWithAllergen)).toBe(true);
  });

  it('가게 단위로 확인한 식단 표식은 예전처럼 센다', () => {
    const checked = place([{ ...LISTED, sourceType: 'OWNER' }, ALLERGEN]);
    expect(hasFoodSafetyConfirmed(checked)).toBe(true);
    expect(needsFoodSafetyCheck(checked)).toBe(false);
  });
});
