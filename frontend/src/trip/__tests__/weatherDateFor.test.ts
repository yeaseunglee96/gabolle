// 여행 날씨 — 여행 중에는 오늘 날씨를 보인다 — S15P21E201-1911(실기기 10/2).
import { weatherDateFor } from '@/trip/TripWeatherPanel';

it('🔴 여행 둘째 날에는 출발일(지난 날) 대신 오늘 날짜를 고른다', () => {
  expect(weatherDateFor('2026-10-01', '2026-10-03', '2026-10-02')).toBe('2026-10-02');
  expect(weatherDateFor('2026-10-01', '2026-10-03', '2026-10-03')).toBe('2026-10-03');
});

it('여행 전·출발일·끝난 여행은 출발일 그대로', () => {
  expect(weatherDateFor('2026-10-05', '2026-10-07', '2026-10-02')).toBe('2026-10-05');
  expect(weatherDateFor('2026-10-02', '2026-10-03', '2026-10-02')).toBe('2026-10-02');
  expect(weatherDateFor('2026-09-20', '2026-09-22', '2026-10-02')).toBe('2026-09-20');
  expect(weatherDateFor(null, null, '2026-10-02')).toBeNull();
  expect(weatherDateFor(undefined, null, '2026-10-02')).toBeUndefined();
});
