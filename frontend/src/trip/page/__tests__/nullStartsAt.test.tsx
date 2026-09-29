// 시각 없는 일정 항목(startsAt null) — 여행 상세가 통째로 깨지던 것.
//
// 서버는 두 경우에 항목 시각을 비운다. 오후에 만든 오늘 출발 여행에서 이동만으로 첫날이 차면 그날 전부를
// 비우고(DayTimeLayout), 일정을 고치며 새로 넣은 곳은 시각 없이 둔다. 화면은 그 값에 바로 .slice(11, 16) 를 걸어
// 「Cannot read properties of null (reading 'slice')」 로 오류 화면까지 갔다(2026-09-29 운영, 걷기만 고른 여행).
//
// 이 시험이 지키는 것:
//   · 시각 칸은 stopClock 을 거친다 — null 이면 null, 부르는 쪽이 「미정」을 그린다
//   · 여행 상세 화면 파일에 startsAt 을 바로 자르는 코드가 다시 들어오지 않는다
//   · 응답 검사·날씨·「어디로 가세요?」가 시각 없는 항목을 깨진 값으로 보지 않는다
import { todayTargets } from '@/field/goFromHere';
import { isItineraryDto } from '@/plan/apiContracts';
import { stopClock, type ItineraryItemDto } from '@/plan/itinerary';
import { weatherStops } from '@/trip/TripWeatherPanel';

jest.mock('@/discovery/places', () => ({ searchPlacesByName: jest.fn() }));
jest.mock('@/plan/origins', () => ({ searchOrigins: jest.fn() }));

// 소스를 읽는 시험은 이 저장소의 방식대로 require 로 부른다 — 앱 tsconfig 에 node 타입이 없다.
declare const require: (id: string) => any;
declare const __dirname: string;
const { readFileSync } = require('fs');
const { join } = require('path');
const source = (rel: string) => readFileSync(join(__dirname, '..', '..', '..', '..', rel), 'utf8') as string;

const item = (id: string, startsAt: string | null): ItineraryItemDto => ({
  id, startsAt, title: `장소${id}`, locked: false, placeId: `p${id}`, lat: 35.1, lng: 129.1,
});

describe('시각 칸', () => {
  it('시각이 있으면 HH:mm', () => {
    expect(stopClock('2026-09-29T18:10:00+09:00')).toBe('18:10');
  });

  it('null·빈 값·날짜만 있는 값은 null — 자르다 깨지지 않고 「미정」으로 그린다', () => {
    expect(stopClock(null)).toBeNull();
    expect(stopClock(undefined)).toBeNull();
    expect(stopClock('2026-09-29')).toBeNull();
  });
});

describe('여행 상세 화면 파일', () => {
  it.each([
    'src/trip/page/TripPageDesktop.tsx',
    'src/trip/page/TripPageMobile.tsx',
    'app/trips/[id]/itinerary.tsx',
  ])('%s 에 startsAt 을 바로 자르는 코드가 없다', (file) => {
    expect(source(file)).not.toMatch(/startsAt\.slice\(/);
  });
});

describe('시각 없는 항목을 받는 다른 자리', () => {
  it('응답 검사는 시각 없는 항목을 깨진 응답으로 보지 않는다', () => {
    const itinerary = { id: 'it-1', title: '여행', version: 1, days: [{ date: '2026-09-29', items: [item('1', null), item('2', '2026-09-29T19:00:00+09:00')] }] };
    expect(isItineraryDto(itinerary)).toBe(true);
  });

  it('날씨는 시각 없는 항목을 뺀다', () => {
    expect(weatherStops([item('1', null), item('2', '2026-09-29T19:00:00+09:00')])).toEqual([{ time: '19:00', name: '장소2' }]);
  });

  it('「어디로 가세요?」의 다음 장소는 시각 있는 곳에서 고른다', () => {
    const itinerary = { days: [{ date: '2026-09-29', items: [item('1', null), item('2', '2026-09-29T19:00:00+09:00')] }] };
    const targets = todayTargets(itinerary, '2026-09-29', new Date('2026-09-29T18:00:00+09:00'));
    expect(JSON.stringify(targets)).toContain('next:2');
    expect(JSON.stringify(targets)).not.toContain('next:1');
  });
});
