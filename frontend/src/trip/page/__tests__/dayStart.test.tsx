// 하루 시작 — 그날 어디서 출발하는가 (S15P21E201-1580).
//
// 🔴 이 시험이 지키는 것은 「어디서 출발하는지」를 틀리게 말하지 않는 것이다. 서버는 첫날은 출발지, 둘째 날부터는
//    숙소에서 이동 시간을 잰다. 화면이 매일 「출발지에서」라고 적으면 숙소에서 나서는 사람에게 틀린 말을 한다.
import { render } from '@testing-library/react-native';

import { legKey } from '@/map/courseRoutePaths';
import { formatTravelLabel } from '@/plan/itinerarySummary';
import type { ItineraryItemDto } from '@/plan/itinerary';
import { DayStartRow } from '@/trip/page/DayStartRow';
import { RETURN_DAY_OFFSET, START_DAY_OFFSET, returnRoute, startTrip, type DayMap } from '@/trip/page/tripPageModel';

const tx = (ko: string) => ko;

const map: DayMap = {
  stops: [
    { id: 's1', number: 1, name: '부다면옥', latitude: 35.1624, longitude: 129.1630 },
    { id: 's2', number: 2, name: '제스터', latitude: 35.1650, longitude: 129.1600 },
  ],
  days: [],
};
const hotel = { kind: 'LODGING' as const, label: '신라스테이 해운대', lat: 35.1601, lng: 129.1631 };
const station = { kind: 'ORIGIN' as const, label: null, lat: 35.1152, lng: 129.0403 };

describe('하루 시작 줄', () => {
  it('둘째 날부터 — 숙소에서 시작하고 숙소 이름을 적는다', () => {
    const view = render(<DayStartRow tx={tx} start={hotel} />);
    expect(view.getByText('숙소에서 시작')).toBeTruthy();
    expect(view.getByText('신라스테이 해운대')).toBeTruthy();
  });

  it('🔴 첫날은 출발지 — 숙소라고 적지 않는다', () => {
    const view = render(<DayStartRow tx={tx} start={station} />);
    expect(view.getByText('출발지에서 시작')).toBeTruthy();
    expect(view.queryByText('숙소에서 시작')).toBeNull();
  });

  it('🔴 서버가 안 보내면(옛 응답·출발지 모름) 아무것도 안 그린다', () => {
    expect(render(<DayStartRow tx={tx} start={null} />).toJSON()).toBeNull();
    expect(render(<DayStartRow tx={tx} start={undefined} />).toJSON()).toBeNull();
  });
});

describe('지도의 하루 시작 선', () => {
  it('출발점에서 첫 정차지로 잇는다', () => {
    const start = startTrip(map, 2, hotel)!;
    expect(start.day.stops.map((stop) => stop.name)).toEqual(['신라스테이 해운대', '부다면옥']);
    expect(start.kind).toBe('LODGING');
    expect(start.approximate).toBe(false);
  });

  it('🔴 길을 받아 오는 열쇠가 정차지 구간·돌아가는 구간과 안 겹친다 — 겹치면 다른 구간의 길이 그려진다', () => {
    const start = startTrip(map, 1, station)!;
    expect(start.day.day).toBe(START_DAY_OFFSET + 1);
    expect(legKey(start.day.day, 0)).not.toBe(legKey(1, 0));
    expect(legKey(start.day.day, 0)).not.toBe(legKey(RETURN_DAY_OFFSET + 1, 0));
  });

  it('받아 온 길이 있으면 그 길로 그린다', () => {
    const start = startTrip(map, 1, station)!;
    const path = [{ latitude: 35.1152, longitude: 129.0403 }, { latitude: 35.1624, longitude: 129.163 }];
    const route = returnRoute(start, '#000', { [legKey(start.day.day, 0)]: { path, estimated: false } });
    expect(route.path).toBe(path);
    expect(route.estimated).toBe(false);
  });

  it('동네 숙소면 곧은 점선 — 정확한 숙소를 모르는데 길을 지어내지 않는다(S15P21E201-1570 과 같은 규칙)', () => {
    expect(startTrip(map, 2, { ...hotel, label: '해운대', lat: 35.1587, lng: 129.1604 })!.approximate).toBe(true);
  });

  it('출발점을 모르거나 정차지가 없으면 선을 안 만든다', () => {
    expect(startTrip(map, 1, null)).toBeNull();
    expect(startTrip({ stops: [], days: [] }, 1, station)).toBeNull();
  });
});

describe('첫 곳 아래 글자', () => {
  const item = { travelDurationMin: 7, travelDataStatus: 'VERIFIED' } as ItineraryItemDto;

  it('🔴 숙소에서 나서는 날은 「숙소에서」 — 「출발지에서」가 아니다', () => {
    expect(formatTravelLabel(item, tx, 'LODGING')).toBe('숙소에서 7분');
    expect(formatTravelLabel({ ...item, travelDataStatus: 'ESTIMATED' }, tx, 'LODGING')).toBe('숙소에서 7분 (어림)');
  });

  it('첫날은 「출발지에서」', () => {
    expect(formatTravelLabel(item, tx, 'ORIGIN')).toBe('출발지에서 7분');
  });
});
