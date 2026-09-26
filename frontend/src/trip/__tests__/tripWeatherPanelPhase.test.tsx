// 날씨 창 머리 글의 때 (S15P21E201-1764).
import { weatherPhase } from '@/trip/TripWeatherPanel';

describe('날씨 창 — 여행 전/당일/뒤', () => {
  it('🔴 출발일이 오늘이면 「여행 전」이 아니다', () => {
    expect(weatherPhase('2026-09-26', '2026-09-26')).toBe('today');
  });
  it('출발일이 내일이면 여행 전', () => {
    expect(weatherPhase('2026-09-27', '2026-09-26')).toBe('before');
  });
  it('출발일이 지났으면 여행 중', () => {
    expect(weatherPhase('2026-09-25', '2026-09-26')).toBe('after');
  });
  it('날짜가 없으면 여행 전(지금까지와 같다)', () => {
    expect(weatherPhase(null, '2026-09-26')).toBe('before');
  });
});
