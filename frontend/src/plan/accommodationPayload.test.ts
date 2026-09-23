import { toCreateTripPayload } from '@/api/tripApi';
import { EMPTY_PLAN } from '@/plan/PlanProvider';
import { lodgingSnapshotOf, RECOMMENDED_LODGING_AREAS } from '@/plan/origins';

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

describe('홈 시작 바의 숙소 — 스냅샷으로 싣는다 (S15P21E201-1536)', () => {
  // 🔴 예전에는 accommodationLat·Lng·Name 을 보냈는데 서버에 그 칸이 없어 조용히 버려졌다.
  const 카카오호텔 = { name: '해운대 어느 호텔', address: '부산 해운대구 우동', lat: 35.1585, lng: 129.1598, externalId: '7913306', source: 'KAKAO_LOCAL' as const };

  it('🔴 검색에서 고른 숙소는 서버 계약 그대로 accommodation 에 싣는다 — source 도 바꾸지 않는다', () => {
    const lodgingPlace = lodgingSnapshotOf(카카오호텔);
    const payload = toCreateTripPayload({ ...EMPTY_PLAN, lodging: 카카오호텔.name, lodgingLat: 카카오호텔.lat, lodgingLng: 카카오호텔.lng, lodgingPlace });
    expect(payload.accommodation).toEqual({ source: 'KAKAO_LOCAL', externalId: '7913306', name: '해운대 어느 호텔', address: '부산 해운대구 우동', lat: 35.1585, lng: 129.1598 });
    expect(payload).not.toHaveProperty('accommodationLat');
  });

  it('🔴 추천 동네는 싣지 않는다 — 장소가 아니라 동네다(서버 TravelArea 와 같은 넷)', () => {
    for (const area of RECOMMENDED_LODGING_AREAS) expect(lodgingSnapshotOf(area)).toBeNull();
    const payload = toCreateTripPayload({ ...EMPTY_PLAN, lodging: '해운대', lodgingLat: 35.1587, lodgingLng: 129.1604, lodgingPlace: lodgingSnapshotOf(RECOMMENDED_LODGING_AREAS[0]) });
    expect(payload.accommodation).toBeNull();
  });

  it('「숙소 아직 안 정했어요」면 안 싣는다 — 출발지 기준으로 짠다', () => {
    expect(toCreateTripPayload(EMPTY_PLAN).accommodation).toBeNull();
  });

  it('우리 표의 숙소(accommodationPlaceId)가 있으면 스냅샷은 안 싣는다', () => {
    const payload = toCreateTripPayload({
      ...EMPTY_PLAN,
      accommodationPlace: { placeId: 'hotel-1', nameKo: '해운대 호텔', nameEn: 'Haeundae Hotel', address: '부산 해운대구', lat: 35.16, lng: 129.16 },
      lodgingPlace: lodgingSnapshotOf(카카오호텔),
    });
    expect(payload.accommodationPlaceId).toBe('hotel-1');
    expect(payload.accommodation).toBeNull();
  });

  it('🔴 서버가 거절할 모양(좌표 한쪽 없음)이면 스냅샷을 안 만든다', () => {
    expect(lodgingSnapshotOf({ ...카카오호텔, lng: Number.NaN })).toBeNull();
  });
});
