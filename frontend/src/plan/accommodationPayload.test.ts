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

describe('홈 시작 바의 숙소 — 좌표로 싣는다 (S15P21E201-1511)', () => {
  it('고른 숙소의 좌표와 이름을 백엔드가 정한 칸 이름으로 싣는다', () => {
    const payload = toCreateTripPayload({ ...EMPTY_PLAN, lodging: '해운대', lodgingLat: 35.1587, lodgingLng: 129.1604 });
    expect(payload).toEqual(expect.objectContaining({ accommodationLat: 35.1587, accommodationLng: 129.1604, accommodationName: '해운대' }));
  });

  it('「숙소 아직 안 정했어요」면 셋 다 비운다 — 출발지 기준으로 짠다', () => {
    const payload = toCreateTripPayload(EMPTY_PLAN);
    expect(payload.accommodationLat).toBeNull();
    expect(payload.accommodationLng).toBeNull();
    expect(payload.accommodationName).toBeNull();
  });

  it('🔴 좌표가 반쪽이면 아무것도 안 보낸다 — 서버가 반쪽 좌표를 거부한다', () => {
    const payload = toCreateTripPayload({ ...EMPTY_PLAN, lodging: '해운대', lodgingLat: 35.1587, lodgingLng: null });
    expect(payload.accommodationLat).toBeNull();
    expect(payload.accommodationLng).toBeNull();
    expect(payload.accommodationName).toBeNull();
  });
});
