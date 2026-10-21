// 시간별 날씨 아이콘 — 밤(19~05시) 맑음은 해가 아니라 달이다(S15P21E201-1963).
import { skyIcon } from '@/trip/TripWeatherPanel';

describe('시간별 날씨 아이콘', () => {
  it('🔴 밤 시간 맑음은 달이다 — 22시에 해가 뜬 것처럼 보이지 않게', () => {
    expect(skyIcon('CLEAR', '22:00')).toBe('🌙');
    expect(skyIcon('CLEAR', '19:00')).toBe('🌙');
    expect(skyIcon('CLEAR', '05:00')).toBe('🌙');
  });
  it('낮 맑음은 해, 구름은 시간과 상관없이 그대로', () => {
    expect(skyIcon('CLEAR', '06:00')).toBe('☀');
    expect(skyIcon('CLEAR', '18:00')).toBe('☀');
    expect(skyIcon('CLOUDY', '22:00')).toBe('☁');
    expect(skyIcon('PARTLY_CLOUDY', '13:00')).toBe('⛅');
  });
});
