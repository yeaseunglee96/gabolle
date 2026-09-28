// 지도의 하루 끝 돌아가는 선 — S15P21E201-1567.
import { legKey } from '@/map/courseRoutePaths';
import { RETURN_DAY_OFFSET, returnRoute, returnTrip, type DayMap } from '@/trip/page/tripPageModel';

const map: DayMap = {
  stops: [
    { id: 's1', number: 1, name: '동백공원', latitude: 35.1537, longitude: 129.1523 },
    { id: 's2', number: 2, name: '부다면옥', latitude: 35.1624, longitude: 129.1630 },
  ],
  days: [],
};
// 우리 표의 숙소(그랜드 조선) — 동네 중심이 아니라 도로 선을 그린다(S15P21E201-1570).
const lodging = { kind: 'LODGING' as const, label: '그랜드 조선 부산', lat: 35.1601, lng: 129.1631, durationMin: 9, distanceM: 613, travelDataStatus: 'ESTIMATED' as const };

describe('지도의 하루 끝 돌아가는 선', () => {
  it('마지막 정차지에서 숙소로 잇는다', () => {
    const back = returnTrip(map, 1, lodging)!;
    expect(back.day.stops.map((stop) => stop.name)).toEqual(['부다면옥', '그랜드 조선 부산']);
    expect(back.anchor.latitude).toBe(35.1601);
    expect(back.kind).toBe('LODGING');
  });

  it('🔴 길을 받아 오는 열쇠가 정차지 구간과 안 겹친다 — 겹치면 첫 구간의 길이 돌아가는 선에 그려진다', () => {
    const back = returnTrip(map, 1, lodging)!;
    expect(back.day.day).toBe(RETURN_DAY_OFFSET + 1);
    expect(legKey(back.day.day, 0)).not.toBe(legKey(1, 0));
  });

  it('길을 못 받았으면 곧은 점선, 받았으면 그 길', () => {
    const back = returnTrip(map, 1, lodging)!;
    expect(returnRoute(back, '#000', {}).estimated).toBe(true);
    const path = [{ latitude: 35.1624, longitude: 129.163 }, { latitude: 35.1587, longitude: 129.1604 }];
    const route = returnRoute(back, '#000', { [legKey(back.day.day, 0)]: { path, estimated: false } });
    expect(route.path).toBe(path);
    expect(route.estimated).toBe(false);
  });

  it('돌아갈 자리를 모르거나 정차지가 없으면 선을 안 만든다', () => {
    expect(returnTrip(map, 1, null)).toBeNull();
    expect(returnTrip({ stops: [], days: [] }, 1, lodging)).toBeNull();
  });
});

describe('🔴 동네 숙소로 돌아가는 선 — S15P21E201-1570', () => {
  // 해운대 동네 중심 — 해수욕장 모래사장 위. 여기로 차 길을 그리면 동백섬까지 돌아갔다.
  const areaLodging = { kind: 'LODGING' as const, label: '해운대', lat: 35.1587, lng: 129.1604, durationMin: 9, distanceM: 613, travelDataStatus: 'ESTIMATED' as const };
  const hotel = { ...areaLodging, label: '그랜드 조선 부산', lat: 35.1601048456, lng: 129.1631548469 };

  it('동네 숙소면 받아 온 길이 있어도 곧은 점선이다 — 정확한 숙소를 모르는데 길을 지어내지 않는다', () => {
    const back = returnTrip(map, 1, areaLodging)!;
    expect(back.approximate).toBe(true);
    const path = [{ latitude: 35.16, longitude: 129.16 }, { latitude: 35.158, longitude: 129.14 }, { latitude: 35.1587, longitude: 129.1604 }];
    const route = returnRoute(back, '#000', { [legKey(back.day.day, 0)]: { path, estimated: false } });
    expect(route.path).toBeUndefined();
    expect(route.estimated).toBe(true);
  });

  it('검색한 호텔이면 도로 선 그대로', () => {
    expect(returnTrip(map, 1, hotel)!.approximate).toBe(false);
  });

  it('마지막 날 출발지(역·집)는 사용자가 고른 곳이라 도로 선', () => {
    expect(returnTrip(map, 2, { ...areaLodging, kind: 'ORIGIN', label: null })!.approximate).toBe(false);
  });
});
