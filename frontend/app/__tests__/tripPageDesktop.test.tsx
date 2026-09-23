// 여행 페이지 넓은 화면 — 숫자를 짓는 규칙 (S15P21E201-1535).
//
// 시안: frontend/docs/design_handoff_trip_page/README.md
// 이 시험이 지키는 것:
//   · 머무름 = 다음 시각 − 지금 시각 − 다음 구간 이동. 🔴 모르면 null — 0 으로 치고 빼지 않는다
//   · 지도 번호는 카드 번호와 같다. 좌표 없는 곳을 건너뛰어도 번호가 당겨지지 않는다
//   · 「쇼진이」·「카페오뜨가」 — 받침 따라 조사
import type { ItineraryItemDto } from '@/plan/itinerary';
import { koreanSubject } from '@/i18n/korean';
import { dayMap, dayRoutes, formatDuration, formatManwon, minutesOfDay, stayMinutes } from '@/trip/page/tripPageModel';

const ko = (text: string) => text;
const item = (id: string, time: string, travel: number | null, lat: number | null = 35.1, lng: number | null = 129.1): ItineraryItemDto => ({
  id, startsAt: `2026-09-23T${time}:00+09:00`, title: id, locked: false, placeId: `p-${id}`,
  travelDurationMin: travel, travelDataStatus: travel === null ? null : 'ESTIMATED', lat, lng,
});

describe('머무름 시간', () => {
  // 시안 코스 A: 09:46 카페오뜨 → (21분) 11:53 늘리 → (29분) 14:08 무슈뱅상 → (20분) 16:14 쇼진
  const course = [item('카페오뜨', '09:46', 46), item('늘리', '11:53', 21), item('무슈뱅상', '14:08', 29), item('쇼진', '16:14', 20)];

  it('다음 시각 − 지금 시각 − 다음 구간 이동 — 시안의 「머무름 약 1시간 46분」', () => {
    expect(stayMinutes(course, 0)).toBe(106); // 11:53 − 09:46 − 21
    expect(formatDuration(106, ko)).toBe('1시간 46분');
  });

  it('마지막 곳은 null — 화면은 「마지막 장소」라고 적는다', () => {
    expect(stayMinutes(course, 3)).toBeNull();
  });

  it('🔴 다음 구간 이동 시간을 모르면 null — 0 으로 치면 이동 시간이 머무름으로 둔갑한다', () => {
    const unknownLeg = [item('a', '10:00', 30), item('b', '12:00', null)];
    expect(stayMinutes(unknownLeg, 0)).toBeNull();
  });

  it('시각이 겹치거나 거꾸로면 null — 음수 머무름은 없다', () => {
    expect(stayMinutes([item('a', '12:00', 0), item('b', '11:00', 10)], 0)).toBeNull();
  });

  it('시각을 못 읽으면 null', () => {
    expect(minutesOfDay('어제')).toBeNull();
    expect(minutesOfDay(null)).toBeNull();
  });
});

describe('지도 정차지', () => {
  it('정차지 id 는 일정 항목 id — 카드와 지도가 같은 id 로 서로를 켠다', () => {
    const map = dayMap([item('a', '09:00', 10), item('b', '10:00', 10)], 1);
    expect(map.stops.map((stop) => stop.id)).toEqual(['a', 'b']);
    expect(map.days).toEqual([{ day: 1, stops: map.stops }]);
  });

  it('🔴 좌표 없는 곳은 건너뛰되 번호는 카드 번호 그대로 — 3번 카드가 지도에서 2번이 되지 않는다', () => {
    const map = dayMap([item('a', '09:00', 10), item('b', '10:00', 10, null, null), item('c', '11:00', 10)], 1);
    expect(map.stops.map((stop) => [stop.id, stop.number])).toEqual([['a', 1], ['c', 3]]);
  });

  it('좌표가 하나도 없으면 경로를 묻지 않는다', () => {
    expect(dayMap([item('a', '09:00', 10, null, null)], 1).days).toEqual([]);
  });

  it('받아 온 길이 없으면 선은 추정(점선)이다', () => {
    const map = dayMap([item('a', '09:00', 10), item('b', '10:00', 10)], 2);
    const routes = dayRoutes(map, 2, '#000000', {});
    expect(routes).toHaveLength(1);
    expect(routes[0]).toMatchObject({ id: 'day-2-leg-0', estimated: true });
  });
});

describe('글자', () => {
  it('만원 한 자리 — 코스 알약의 「22.0만원」', () => {
    expect(formatManwon(220400, ko)).toBe('22.0만원');
    expect(formatManwon(67000, ko)).toBe('6.7만원');
  });

  it('시간 표기 — 한 시간이 넘으면 나눗셈을 넘기지 않는다', () => {
    expect(formatDuration(46, ko)).toBe('46분');
    expect(formatDuration(120, ko)).toBe('2시간');
  });

  it('주격 조사 — 받침이 있으면 「이」, 없거나 한글이 아니면 「가」', () => {
    expect(koreanSubject('쇼진')).toBe('이');
    expect(koreanSubject('카페오뜨')).toBe('가');
    expect(koreanSubject('The Bay 101')).toBe('가');
  });
});
