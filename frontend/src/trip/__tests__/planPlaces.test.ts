// 쓴 돈 적기의 「일정에서 고르기」(S15P21E201-1935).
import { planPlacesFor } from '../planPlaces';

const item = (title: string, nameEn: string | null = null) => ({ id: title, startsAt: null, title, nameEn, locked: false } as never);
const itinerary = { days: [{ date: '2026-10-03', items: [item('해운대 해수욕장', 'Haeundae Beach'), item('광안리 밀면집')] }, { date: '2026-10-04', items: [item('감천문화마을'), item('해운대 해수욕장')] }] };

describe('planPlacesFor', () => {
  it('여행 중이면 오늘 장소만 — 지금 쓴 돈은 대개 오늘 간 곳이다', () => {
    expect(planPlacesFor(itinerary, '2026-10-04').map((place) => place.title)).toEqual(['감천문화마을', '해운대 해수욕장']);
  });

  it('여행 날이 아니면 모든 날을 순서대로, 같은 이름은 한 번만', () => {
    expect(planPlacesFor(itinerary, '2026-09-30')).toEqual([
      { title: '해운대 해수욕장', nameEn: 'Haeundae Beach' }, { title: '광안리 밀면집', nameEn: null }, { title: '감천문화마을', nameEn: null },
    ]);
  });

  it('일정이 없으면 빈 목록 · 개수 제한', () => {
    expect(planPlacesFor(null, '2026-10-03')).toEqual([]);
    expect(planPlacesFor(itinerary, '2026-09-30', 2)).toHaveLength(2);
  });
});
