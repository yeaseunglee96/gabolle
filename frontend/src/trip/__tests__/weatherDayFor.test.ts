// 여행 페이지 날씨 창 — 여행 중이면 오늘 하루를 넘긴다 — S15P21E201-1919(웹 10/2).
import { weatherDayFor } from '@/trip/weatherDay';

const days = [
  { date: '2026-09-30', items: [{ startsAt: null, title: 'a' }] },
  { date: '2026-10-01', items: [{ startsAt: null, title: 'b' }] },
  { date: '2026-10-02', items: [{ startsAt: null, title: 'c' }] },
];

it('🔴 여행 셋째 날에는 출발일이 아니라 오늘 하루(날짜·그날 일정)를 고른다', () => {
  expect(weatherDayFor(days, '2026-10-02')).toBe(days[2]);
  expect(weatherDayFor(days, '2026-10-01')).toBe(days[1]);
});

it('여행 전·끝난 여행·빈 일정은 첫날 그대로', () => {
  expect(weatherDayFor(days, '2026-09-20')).toBe(days[0]);
  expect(weatherDayFor(days, '2026-10-09')).toBe(days[0]);
  expect(weatherDayFor([], '2026-10-02')).toBeUndefined();
});
