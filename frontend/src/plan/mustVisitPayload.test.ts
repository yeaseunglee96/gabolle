// 꼭 가고 싶은 장소가 여행 생성 요청에 실리는가 —.
import { toCreateTripPayload } from '@/api/tripApi';
import { EMPTY_PLAN, type MustVisitPlace } from '@/plan/PlanProvider';

const place = (placeId: string, nameKo: string): MustVisitPlace => ({
  placeId,
  nameKo,
  nameEn: null,
  lat: 35.1587,
  lng: 129.1604,
});

describe('여행 생성 요청의 꼭 가고 싶은 장소', () => {
  it('고른 순서 그대로 식별자만 싣는다', () => {
    const payload = toCreateTripPayload({
      ...EMPTY_PLAN,
      startDate: '2026-10-12',
      endDate: '2026-10-13',
      mustVisitPlaces: [place('p-1', '해운대 관광특구'), place('p-2', '감천문화마을')],
    });

    expect(payload.mustVisitPlaceIds).toEqual(['p-1', 'p-2']);
  });

  // 아무것도 안 고르고 넘어가는 것이 보통이다. 그때 서버가 받는 모양이 달라지면 안 된다.
  it('안 고르면 빈 배열이다', () => {
    const payload = toCreateTripPayload({
      ...EMPTY_PLAN,
      startDate: '2026-10-12',
      endDate: '2026-10-13',
    });

    expect(payload.mustVisitPlaceIds).toEqual([]);
  });
});
