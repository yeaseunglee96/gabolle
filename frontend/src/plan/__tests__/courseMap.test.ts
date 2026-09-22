// 코스를 지도 모양으로 —-1333.
//
// 🔴 이 시험이 지키는 것은 **좌표 없이 선을 긋지 않는가**이다. 없는 좌표를 0 으로 채우면
//    지도에 아프리카 서쪽 바다(기니만)가 찍히고, 그 점을 다른 점과 이으면 **실제로 안 가는
//    길**이 그려진다. 그건 빈 지도보다 나쁘다.
import { courseMapLayers, dayColor } from '@/plan/courseMap';
import type { TripCourse } from '@/plan/tripCourses';

const stop = (name: string, lat: number | null, lng: number | null) => ({
  placeId: null, name, time: null, note: null, photoUrl: null, photoSource: null, lat, lng,
});

const course = (days: TripCourse['days']): TripCourse => ({
  id: 'c1', title: '코스 A', tagline: '', days,
  summary: { places: null, moveMin: null, walkKm: null, costKrw: null },
  status: 'ESTIMATED', rationale: null, itineraryId: null,
});

describe('코스를 지도에 올린다', () => {
  it('정차 둘이면 선 하나가 된다 — 구간 하나다', () => {
    const { stops, routes } = courseMapLayers(course([
      { day: 1, stops: [stop('해운대', 35.1587, 129.1604), stop('광안리', 35.1532, 129.1189)] },
    ]));

    expect(routes).toHaveLength(1);
    expect(routes[0].stops.map((s) => s.name)).toEqual(['해운대', '광안리']);
    expect(stops).toHaveLength(2);
  });

  it('날짜마다 색이 다르다 — 같은 색이면 어느 날 동선인지 못 읽는다', () => {
    const { routes } = courseMapLayers(course([
      { day: 1, stops: [stop('가', 35.1, 129.1), stop('나', 35.2, 129.2)] },
      { day: 2, stops: [stop('다', 35.3, 129.3), stop('라', 35.4, 129.4)] },
    ]));

    expect(routes[0].color).not.toBe(routes[1].color);
    expect(routes[0].color).toBe(dayColor(0));
    expect(routes[1].color).toBe(dayColor(1));
  });

  it('🔴 좌표를 모르는 정차지는 안 그린다 — 0 으로 채우면 지도에 기니만이 찍힌다', () => {
    const { stops } = courseMapLayers(course([
      { day: 1, stops: [stop('아는 곳', 35.1, 129.1), stop('모르는 곳', null, null)] },
    ]));

    expect(stops.map((s) => s.name)).toEqual(['아는 곳']);
  });

  it('🔴 빠진 곳이 있어도 번호를 건너뛰지 않는다 — 지도의 번호는 찍힌 순서다', () => {
    const { stops } = courseMapLayers(course([
      { day: 1, stops: [stop('하나', 35.1, 129.1), stop('없음', null, 129.2), stop('둘', 35.3, 129.3)] },
    ]));

    expect(stops.map((s) => s.number)).toEqual([1, 2]);
  });

  it('🔴 하루 안에서 번호가 다시 1부터다 — 목록의 「2일차 1」과 같은 숫자를 봐야 한다', () => {
    const { routes } = courseMapLayers(course([
      { day: 1, stops: [stop('가', 35.1, 129.1), stop('나', 35.2, 129.2)] },
      { day: 2, stops: [stop('다', 35.3, 129.3), stop('라', 35.4, 129.4)] },
    ]));

    // 이제 선은 **구간마다** 하나다 — 1일차 한 구간, 2일차 한 구간.
    expect(routes[1].stops[0].number).toBe(1);
  });

  it('🔴 정차가 하나뿐인 날도 지도를 잃지 않는다 — 그릴 구간이 없을 뿐 점은 있다', () => {
    const { stops, routes } = courseMapLayers(course([
      { day: 1, stops: [stop('혼자', 35.1, 129.1)] },
    ]));

    expect(stops.map((s) => s.name)).toEqual(['혼자']);
    expect(routes).toEqual([]);
  });

  it('선을 구간마다 나눈다 — 한 구간은 실제 길, 다른 구간은 직선일 수 있기 때문이다', () => {
    const { routes } = courseMapLayers(course([
      { day: 1, stops: [stop('가', 35.1, 129.1), stop('나', 35.2, 129.2), stop('다', 35.3, 129.3)] },
    ]));

    expect(routes).toHaveLength(2);
    expect(routes.map((r) => r.stops.map((s) => s.name))).toEqual([['가', '나'], ['나', '다']]);
  });

  it('🔴 실제 경로를 받은 구간만 실선이 된다 — 못 받은 구간은 점선으로 남는다', () => {
    const { routes } = courseMapLayers(
      course([{ day: 1, stops: [stop('가', 35.1, 129.1), stop('나', 35.2, 129.2), stop('다', 35.3, 129.3)] }]),
      { '1-0': { path: [{ latitude: 35.1, longitude: 129.1 }, { latitude: 35.15, longitude: 129.15 }], estimated: false } },
    );

    expect(routes[0].estimated).toBe(false);
    expect(routes[0].path).toHaveLength(2);
    // 두 번째 구간은 경로를 못 받았다 — 실제 길인 척하지 않는다
    expect(routes[1].estimated).toBe(true);
    expect(routes[1].path).toBeUndefined();
  });

  it('🔴 서버가 「어림값」이라고 하면 경로가 있어도 점선이다', () => {
    const { routes } = courseMapLayers(
      course([{ day: 1, stops: [stop('가', 35.1, 129.1), stop('나', 35.2, 129.2)] }]),
      { '1-0': { path: [{ latitude: 35.1, longitude: 129.1 }, { latitude: 35.2, longitude: 129.2 }], estimated: true } },
    );

    expect(routes[0].estimated).toBe(true);
  });

  it('🔴 선이 실제 길이 아니라는 것을 적는다 — 안 적으면 걸어갈 수 있는 길로 읽는다', () => {
    const { routes } = courseMapLayers(course([
      { day: 1, stops: [stop('가', 35.1, 129.1), stop('나', 35.2, 129.2)] },
    ]));

    expect(routes[0].estimated).toBe(true);
  });

  it('🔴 좌표가 하나도 없으면 빈 것을 준다 — 화면이 그때 동선을 글로 세운다', () => {
    const { stops, routes } = courseMapLayers(course([
      { day: 1, stops: [stop('모름', null, null)] },
    ]));

    expect(stops).toEqual([]);
    expect(routes).toEqual([]);
  });

  it('코스가 없어도 안 깨진다', () => {
    expect(courseMapLayers(null)).toEqual({ stops: [], routes: [] });
  });

  it('정차지의 아이디가 서로 다르다 — 겹치면 지도가 한 점만 그린다', () => {
    const { stops } = courseMapLayers(course([
      { day: 1, stops: [stop('가', 35.1, 129.1), stop('나', 35.2, 129.2)] },
      { day: 2, stops: [stop('다', 35.3, 129.3), stop('라', 35.4, 129.4)] },
    ]));

    expect(new Set(stops.map((s) => s.id)).size).toBe(stops.length);
  });
});
