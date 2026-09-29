// 일정 응답에 실려 온 경사 조각 길 — 들어오는 곳의 travelPath + travelPieces.
//
// 🔴 이 시험이 지키는 것:
//    ① 조각이 실려 온 구간은 길을 다시 묻지 않고 그 조각으로 칠한다(요청이 줄고, 일정이 짠 그 길이 그려진다).
//    ② 조각이 없거나(옛 서버) 번호가 길 밖이면 쓰지 않는다 — 전처럼 길을 받아 온다.
//    ③ 좌표 없는 곳을 건너뛴 두 정차지 사이에는 쓰지 않는다 — 그 길은 그 두 점 사이 길이 아니다.
import { renderHook, waitFor } from '@testing-library/react-native';

import { clearCourseRoutePathCache, legKey, useCourseRoutePaths } from '@/map/courseRoutePaths';
import { getRouteDirections } from '@/map/routeDirections';
import type { ItineraryItemDto } from '@/plan/itinerary';
import { dayMap, dayRoutes, itineraryLegs } from '@/trip/page/tripPageModel';

jest.mock('@/map/routeDirections', () => ({ getRouteDirections: jest.fn() }));
const mockedDirections = getRouteDirections as jest.Mock;

const item = (id: string, lat: number | null, lng: number | null, extra: Partial<ItineraryItemDto> = {}): ItineraryItemDto => (
  { id, startsAt: '2026-10-03T10:00:00', title: id, locked: false, placeId: `p-${id}`, lat, lng, ...extra }
);
// [경도, 위도] — 서버 순서
const travelPath: Array<[number, number]> = [[129.1588, 35.1636], [129.1596, 35.1612], [129.1604, 35.1587]];
const travelPieces = [
  { from: 0, to: 1, slopePercent: 9.1, stairs: false },
  { from: 1, to: 2, slopePercent: 2.0, stairs: false },
];

describe('일정에 실려 온 경사 조각 길', () => {
  it('🔴 들어오는 곳에 길과 조각이 있으면 그 구간의 길로 쓴다 — [경도, 위도] 를 위도·경도로', () => {
    const items = [item('a', 35.1636, 129.1588), item('b', 35.1587, 129.1604, { travelPath, travelPieces })];
    const legs = itineraryLegs(items, dayMap(items, 1), 1);
    expect(Object.keys(legs)).toEqual([legKey(1, 0)]);
    expect(legs[legKey(1, 0)].path[0]).toEqual({ latitude: 35.1636, longitude: 129.1588 });
    expect(legs[legKey(1, 0)].estimated).toBe(false);
    expect(legs[legKey(1, 0)].pieces).toEqual(travelPieces);
    // 지도 선까지 조각이 그대로 간다 — 웹·앱 지도가 이것으로 경사 색을 칠한다(walkSlope.test.tsx)
    expect(dayRoutes(dayMap(items, 1), 1, '#000', legs)[0].pieces).toEqual(travelPieces);
  });

  it('🔴 조각이 없거나(옛 서버) 길만 있으면 쓰지 않는다', () => {
    const noPieces = [item('a', 35.1636, 129.1588), item('b', 35.1587, 129.1604, { travelPath })];
    const oldServer = [item('a', 35.1636, 129.1588), item('b', 35.1587, 129.1604)];
    const emptyPieces = [item('a', 35.1636, 129.1588), item('b', 35.1587, 129.1604, { travelPath, travelPieces: [] })];
    for (const items of [noPieces, oldServer, emptyPieces]) expect(itineraryLegs(items, dayMap(items, 1), 1)).toEqual({});
  });

  it('🔴 조각 번호가 길 밖이면 버린다 — 전처럼 받아 온다', () => {
    const items = [item('a', 35.1636, 129.1588), item('b', 35.1587, 129.1604, { travelPath, travelPieces: [{ from: 0, to: 5, slopePercent: 1, stairs: false }] })];
    expect(itineraryLegs(items, dayMap(items, 1), 1)).toEqual({});
  });

  it('🔴 좌표 없는 곳을 건너뛴 두 정차지 사이에는 쓰지 않는다', () => {
    const items = [item('a', 35.1636, 129.1588), item('mid', null, null), item('b', 35.1587, 129.1604, { travelPath, travelPieces })];
    expect(itineraryLegs(items, dayMap(items, 1), 1)).toEqual({});
  });
});

describe('구간 받아 오기 — 이미 있는 길', () => {
  const a = { id: 'a', number: 1, name: 'a', latitude: 35.1636, longitude: 129.1588 };
  const b = { id: 'b', number: 2, name: 'b', latitude: 35.1587, longitude: 129.1604 };
  const c = { id: 'c', number: 3, name: 'c', latitude: 35.1532, longitude: 129.1186 };
  const days = [{ day: 1, stops: [a, b, c] }];
  const fetched = { state: 'success', directions: { path: [[129.1604, 35.1587], [129.1186, 35.1532]], estimated: false } };
  const known = { [legKey(1, 0)]: { path: [{ latitude: 35.1636, longitude: 129.1588 }, { latitude: 35.1587, longitude: 129.1604 }], estimated: false, pieces: travelPieces.slice(0, 1) } };
  beforeEach(() => { clearCourseRoutePathCache(); mockedDirections.mockReset(); mockedDirections.mockResolvedValue(fetched); });

  it('🔴 이미 있는 구간은 묻지 않고 그대로 돌려준다 — 나머지만 묻는다', async () => {
    const { result } = renderHook(() => useCourseRoutePaths(days, 'token', { known }));
    await waitFor(() => expect(result.current[legKey(1, 1)]).toBeTruthy());
    expect(mockedDirections).toHaveBeenCalledTimes(1);
    expect(mockedDirections.mock.calls[0][0]).toMatchObject({ originLat: b.latitude, destLat: c.latitude });
    expect(result.current[legKey(1, 0)]).toBe(known[legKey(1, 0)]);
  });

  it('없으면 전처럼 전부 묻는다', async () => {
    const { result } = renderHook(() => useCourseRoutePaths(days, 'token'));
    await waitFor(() => expect(result.current[legKey(1, 1)]).toBeTruthy());
    expect(mockedDirections).toHaveBeenCalledTimes(2);
  });
});
