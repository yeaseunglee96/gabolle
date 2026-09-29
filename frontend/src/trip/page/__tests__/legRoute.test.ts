import { legRouteParams } from '@/trip/page/legRoute';
import type { DayStart, ItineraryItemDto } from '@/plan/itinerary';

const ko = (k: string) => k;
const item = (id: string, lat: number | null | undefined, lng: number | null | undefined): ItineraryItemDto => ({ id, startsAt: '2026-10-01T10:00:00', title: `장소${id}`, locked: false, placeId: `p${id}`, lat, lng });
const name = (each: ItineraryItemDto) => each.title;

describe('구간 → 경로 상세 주소 (S15P21E201-1831)', () => {
  it('앞 곳에서 이 곳으로', () => {
    expect(legRouteParams([item('1', 35.1, 129.1), item('2', 35.2, 129.2)], 1, null, name, ko)).toEqual({
      originLat: '35.1', originLng: '129.1', originName: '장소1', destLat: '35.2', destLng: '129.2', destName: '장소2', destPlaceId: 'p2',
    });
  });

  it('그날 첫 곳은 하루 시작(숙소·출발지)에서', () => {
    const lodging: DayStart = { kind: 'LODGING', label: null, lat: 35.15, lng: 129.16 };
    expect(legRouteParams([item('1', 35.1, 129.1)], 0, lodging, name, ko)?.originName).toBe('숙소');
    expect(legRouteParams([item('1', 35.1, 129.1)], 0, { ...lodging, label: '해운대' }, name, ko)?.originName).toBe('해운대');
    expect(legRouteParams([item('1', 35.1, 129.1)], 0, null, name, ko)).toBeNull();
  });

  it('좌표를 모르면 null — 0 으로 채우지 않는다', () => {
    expect(legRouteParams([item('1', null, 129.1), item('2', 35.2, 129.2)], 1, null, name, ko)).toBeNull();
    expect(legRouteParams([item('1', 35.1, 129.1), item('2', undefined, undefined)], 1, null, name, ko)).toBeNull();
    expect(legRouteParams([], 0, null, name, ko)).toBeNull();
  });
});
