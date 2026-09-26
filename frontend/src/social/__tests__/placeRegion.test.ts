// 장소 제목 아래 📍 줄 (S15P21E201-1759).
import { regionBesidePlace } from '@/social/placeRegion';

describe('장소 제목 아래 지역 줄', () => {
  it('🔴 「장소 · 구」면 구만 남긴다 — 장소 이름을 두 번 쓰지 않는다', () => {
    expect(regionBesidePlace('황령산 · 남구', '황령산')).toBe('남구');
  });

  it('🔴 지역이 장소 이름뿐이면 줄을 그리지 않는다', () => {
    expect(regionBesidePlace('모모스', '모모스')).toBeNull();
  });

  it('장소 이름과 상관없는 지역은 그대로 둔다', () => {
    expect(regionBesidePlace('해운대구', '해운대해수욕장')).toBe('해운대구');
  });

  it('비어 있으면 null', () => {
    expect(regionBesidePlace(null, '모모스')).toBeNull();
    expect(regionBesidePlace('  ', '모모스')).toBeNull();
  });
});
