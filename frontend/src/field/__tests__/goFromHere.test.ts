import { busLinesOf, pickTodayTrip, rideSummary, searchDestinations, todayTargets } from '@/field/goFromHere';
import type { BusStop } from '@/field/busArrivals';
import type { RouteDirections } from '@/map/routeDirections';
import type { ItineraryItemDto } from '@/plan/itinerary';
import type { TripSummaryDto } from '@/trip/trips';

jest.mock('@/discovery/places', () => ({ searchPlacesByName: jest.fn() }));
jest.mock('@/plan/origins', () => ({ searchOrigins: jest.fn() }));
const { searchPlacesByName } = jest.requireMock('@/discovery/places') as { searchPlacesByName: jest.Mock };
const { searchOrigins } = jest.requireMock('@/plan/origins') as { searchOrigins: jest.Mock };

const trip = (tripId: string, startDate: string | null, endDate: string | null) => ({ tripId, startDate, endDate } as TripSummaryDto);
const item = (id: string, startsAt: string, lat: number | null = 35.1, lng: number | null = 129.1): ItineraryItemDto => ({ id, startsAt, title: `장소${id}`, locked: false, placeId: `p${id}`, lat, lng });

const transit = (steps: RouteDirections['steps'], extra: Partial<RouteDirections> = {}): RouteDirections => ({
  mode: 'TRANSIT', distanceM: 6900, durationMin: 92, taxiFareKrw: null, tollFareKrw: null, transferCount: 0, estimated: true, estimateReason: null, provider: 'TRANSIT_NETWORK', path: [], steps, ...extra,
});
const ride88 = { name: '88(A)번', guidance: '부전시장입구에서 88(A)번을(를) 타고 청학시장에서 내립니다.', distanceM: 6900, durationMin: 87 };

describe('「어디로 가세요?」 재료 (S15P21E201-1834)', () => {
  it('오늘 날짜가 걸린 여행 — 여럿이면 먼저 시작한 것', () => {
    const trips = [trip('b', '2026-09-28', '2026-10-01'), trip('a', '2026-09-27', '2026-09-30'), trip('c', '2026-10-02', '2026-10-03'), trip('d', null, null)];
    expect(pickTodayTrip(trips, '2026-09-29')?.tripId).toBe('a');
    expect(pickTodayTrip(trips, '2026-10-05')).toBeNull();
  });

  it('다음 장소는 아직 시작 안 한 첫 곳, 숙소는 그날 돌아가는 곳이 숙소일 때만', () => {
    const days = [{
      date: '2026-09-29',
      items: [item('1', '2026-09-29T10:32:00+09:00'), item('2', '2026-09-29T14:41:00+09:00'), item('3', '2026-09-29T19:00:00+09:00')],
      returnLeg: { kind: 'LODGING' as const, label: '남포동', lat: 35.098, lng: 129.03, durationMin: 30, distanceM: 4022, travelDataStatus: null },
    }];
    const got = todayTargets({ days }, '2026-09-29', new Date('2026-09-29T12:00:00+09:00'));
    expect(got.next?.name).toBe('장소2');
    expect(got.next?.startsAt).toBe('2026-09-29T14:41:00+09:00');
    expect(got.lodging).toMatchObject({ kind: 'lodging', name: '남포동', latitude: 35.098 });
    // 다 지났으면 「다음」은 없다
    expect(todayTargets({ days }, '2026-09-29', new Date('2026-09-29T20:00:00+09:00')).next).toBeNull();
    // 마지막 날(출발지로 돌아감)은 숙소가 아니다
    expect(todayTargets({ days: [{ ...days[0], returnLeg: { ...days[0].returnLeg, kind: 'ORIGIN' as const, label: null } }] }, '2026-09-29', new Date('2026-09-29T12:00:00+09:00')).lodging).toBeNull();
    // 그날 일정이 없으면 둘 다 없다
    expect(todayTargets({ days }, '2026-09-30', new Date())).toEqual({ next: null, lodging: null });
  });

  it('좌표를 모르는 장소는 「다음」으로 고르지 않는다', () => {
    const days = [{ date: '2026-09-29', items: [item('1', '2026-09-29T14:00:00+09:00', null, null), item('2', '2026-09-29T15:00:00+09:00')] }];
    expect(todayTargets({ days }, '2026-09-29', new Date('2026-09-29T12:00:00+09:00')).next?.name).toBe('장소2');
  });

  it('처음 타는 버스와 타는 정류장 — 주변 정류장에 그 버스가 오면 실시간 도착을 붙인다', () => {
    const stops: BusStop[] = [
      { nodeId: 'a', nodeName: '부전시장입구', lat: 0, lng: 0, arrivals: [{ routeNo: '88(A)', arrivalSeconds: 700, remainingStops: 5, vehicleType: null }, { routeNo: '88(A)', arrivalSeconds: 300, remainingStops: 2, vehicleType: null }, { routeNo: '10', arrivalSeconds: 60, remainingStops: 1, vehicleType: null }] },
      { nodeId: 'b', nodeName: '서면역', lat: 0, lng: 0, arrivals: [{ routeNo: '88(A)', arrivalSeconds: 30, remainingStops: 0, vehicleType: null }] },
    ];
    expect(rideSummary(transit([{ name: '도보', guidance: '', distanceM: 100, durationMin: 2 }, ride88]), stops)).toEqual({ kind: 'bus', line: '88(A)', board: '부전시장입구', alight: '청학시장', transfers: 0, liveSeconds: 300 });
  });

  it('맞물리는 도착이 없으면 지어내지 않는다', () => {
    expect(rideSummary(transit([ride88]), [])?.liveSeconds).toBeNull();
    expect(rideSummary(transit([{ name: '2호선', guidance: '서면역에서 2호선을(를) 타고 광안역에서 내립니다.', distanceM: 1, durationMin: 1 }]), [])).toMatchObject({ kind: 'subway', line: '2호선', board: '서면역', liveSeconds: null });
  });

  it('걷는 편이 빨라 걷기로 답이 오면 탈 것이 없다', () => {
    expect(rideSummary(transit([], { mode: 'WALK' }), [])).toBeNull();
  });

  it('강조할 버스 번호 — 버스 단계만', () => {
    expect([...busLinesOf(transit([ride88, { name: '도보', guidance: '', distanceM: 1, durationMin: 1 }, { name: '1003번', guidance: '', distanceM: 1, durationMin: 1 }, { name: '1호선', guidance: '', distanceM: 1, durationMin: 1 }]))]).toEqual(['88(A)', '1003']);
    expect(busLinesOf(null).size).toBe(0);
  });

  it('검색 — 우리 장소 먼저, 같은 이름은 한 번, 한쪽이 실패해도 나머지로', async () => {
    searchPlacesByName.mockResolvedValueOnce([{ placeId: 'p1', nameKo: '자갈치시장', nameEn: null, category: 'MARKET', address: '부산 중구', lat: 35.09, lng: 129.03 }]);
    searchOrigins.mockResolvedValueOnce({ state: 'success', degraded: false, items: [
      { name: '자갈치 시장', address: '부산 중구 자갈치해안로', lat: 35.09, lng: 129.03, externalId: 'k1', source: 'KAKAO_LOCAL' },
      { name: '자갈치역', address: '부산 중구', lat: 35.097, lng: 129.03, externalId: 'k2', source: 'KAKAO_LOCAL' },
    ] });
    const got = await searchDestinations('자갈치', null);
    expect(got.map((each) => each.name)).toEqual(['자갈치시장', '자갈치역']);
    expect(got[0].placeId).toBe('p1');

    searchPlacesByName.mockRejectedValueOnce(new Error('down'));
    searchOrigins.mockResolvedValueOnce({ state: 'success', degraded: false, items: [{ name: '자갈치역', address: '부산 중구', lat: 35.097, lng: 129.03, externalId: 'k2', source: 'KAKAO_LOCAL' }] });
    expect((await searchDestinations('자갈치', null)).map((each) => each.name)).toEqual(['자갈치역']);
  });
});
