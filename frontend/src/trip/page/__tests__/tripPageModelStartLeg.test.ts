// 하루 시작 구간(출발지 → 첫 곳)도 일정 응답에 실려 온 경사 조각으로 칠한다 — S15P21E201-2044.
//
// 🔴 대중교통 여행은 걸어서 간 짧은 구간에도 walkingMeters 가 비어 있다(서버 travel_mode=BUS). 그래서 화면이 출발 구간을
//    걷기로 묻지 않아 조각 없는 길이 오고, 첫 항목에 실려 온 조각은 버려져 지도에 남색 한 줄로 남았다(폰 2026-10-10, 서면).
import { legKey } from '@/map/courseRoutePaths';
import type { ItineraryItemDto } from '@/plan/itinerary';
import { dayMap, itineraryLegs, returnRoute, START_DAY_OFFSET, startTrip } from '@/trip/page/tripPageModel';

const item = (id: string, lat: number, lng: number, extra: Partial<ItineraryItemDto> = {}): ItineraryItemDto => (
  { id, startsAt: '2026-10-15T09:00:00', title: id, locked: false, placeId: `p-${id}`, lat, lng, ...extra }
);
const travelPath: Array<[number, number]> = [[129.0594, 35.1578], [129.0600, 35.1570], [129.0610, 35.1560]];
const travelPieces = [
  { from: 0, to: 1, slopePercent: null, stairs: false },
  { from: 1, to: 2, slopePercent: 1.2, stairs: false },
];
const start = { kind: 'ORIGIN', label: null, lat: 35.1578, lng: 129.0594 } as const;

describe('하루 시작 구간의 경사 조각', () => {
  it('🔴 첫 항목에 길과 조각이 실려 오면 출발 구간의 길로 쓴다', () => {
    const items = [item('a', 35.1560, 129.0610, { travelPath, travelPieces }), item('b', 35.1543, 129.0645)];
    const map = dayMap(items, 1);
    const legs = itineraryLegs(items, map, 1, start);
    const key = legKey(START_DAY_OFFSET + 1, 0);
    expect(legs[key]?.pieces).toEqual(travelPieces);
    expect(legs[key]?.path[0]).toEqual({ latitude: 35.1578, longitude: 129.0594 });
    const back = startTrip(map, 1, start)!;
    expect(returnRoute(back, '#000', legs).pieces).toEqual(travelPieces);
  });

  it('출발점이 없거나 첫 정차지가 첫 항목이 아니면 쓰지 않는다', () => {
    const items = [item('a', 35.1560, 129.0610, { travelPath, travelPieces })];
    expect(itineraryLegs(items, dayMap(items, 1), 1)[legKey(START_DAY_OFFSET + 1, 0)]).toBeUndefined();
    const noFirst = [{ ...items[0], lat: null as unknown as number }, item('b', 35.1543, 129.0645, { travelPath, travelPieces })];
    expect(itineraryLegs(noFirst, dayMap(noFirst, 1), 1, start)[legKey(START_DAY_OFFSET + 1, 0)]).toBeUndefined();
  });

  it('동네 숙소(어림)에서 시작하면 쓰지 않는다 — 곧은 점선 그대로', () => {
    const items = [item('a', 35.1560, 129.0610, { travelPath, travelPieces })];
    const approx = { kind: 'LODGING', label: null, lat: 35.1587, lng: 129.1604 } as const;
    const map = dayMap(items, 1);
    if (!startTrip(map, 1, approx)?.approximate) return; // 어림 판정은 dayStart 시험이 지킨다
    expect(itineraryLegs(items, map, 1, approx)[legKey(START_DAY_OFFSET + 1, 0)]).toBeUndefined();
  });
});
