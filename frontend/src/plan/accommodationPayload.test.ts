import { toCreateTripPayload } from '@/api/tripApi';
import { EMPTY_PLAN } from '@/plan/PlanProvider';

describe('여행 생성 요청의 숙소와 이용 조건', () => {
  it('검색에서 고른 숙소 식별자와 이용 조건을 서버 계약 이름으로 싣는다', () => {
    const payload = toCreateTripPayload({
      ...EMPTY_PLAN,
      accommodation: '해운대 호텔',
      accommodationPlace: { placeId: 'hotel-1', nameKo: '해운대 호텔', nameEn: 'Haeundae Hotel', address: '부산 해운대구', lat: 35.16, lng: 129.16 },
      englishMenuRequired: true,
      foreignCardRequired: true,
      soloDiningPreferred: true,
      maxTransfers: 2,
    });
    expect(payload).toEqual(expect.objectContaining({ accommodationPlaceId: 'hotel-1', englishMenuRequired: true, foreignCardRequired: true, soloFriendlyPriority: true, maxTransitTransfers: 2 }));
  });

  it('직접 입력만 하거나 자차를 고르면 잘못된 숙소·환승 제한을 보내지 않는다', () => {
    const payload = toCreateTripPayload({ ...EMPTY_PLAN, accommodation: '검색 결과에서 고르지 않은 이름', transport: 'CAR', maxTransfers: 3 });
    expect(payload.accommodationPlaceId).toBeNull();
    expect(payload.maxTransitTransfers).toBeNull();
  });
});
