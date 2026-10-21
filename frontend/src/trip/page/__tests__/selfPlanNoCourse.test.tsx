// 직접 짠 여행에는 코스 확정 표시가 없다 — S15P21E201-2018.
//
// 🔴 「AI 없이 직접 짜기」로 만든 일정은 고른 추천 코스가 없다. 서버가 일정에 origin=SELF 를 실어 주면
//    여행 화면은 코스 목록(추천)을 묻지 않고, 「✓ 코스 A 확정」·코스 바꾸기·코스 A/B/C 를 그리지 않는다.
import type { ItineraryDto } from '@/plan/itinerary';
import { loadTripPageCourses } from '@/trip/page/tripPageData';

jest.mock('@/plan/itinerary', () => ({ ...jest.requireActual('@/plan/itinerary'), loadItinerary: jest.fn() }));
jest.mock('@/plan/tripCourses', () => ({ ...jest.requireActual('@/plan/tripCourses'), loadTripCourses: jest.fn() }));

const { loadItinerary } = jest.requireMock('@/plan/itinerary') as { loadItinerary: jest.Mock };
const { loadTripCourses } = jest.requireMock('@/plan/tripCourses') as { loadTripCourses: jest.Mock };

const itinerary = (origin?: 'SELF' | 'RECOMMENDATION'): ItineraryDto => ({
  id: 'it-1', tripId: 'trip-1', title: '내가 짠 여행', version: 2, origin,
  days: [{ date: '2026-10-10', items: [] }],
} as ItineraryDto);

beforeEach(() => {
  loadItinerary.mockReset();
  loadTripCourses.mockReset().mockResolvedValue({ state: 'success', courses: [], full: false });
});

describe('직접 짠 여행의 여행 화면', () => {
  it('🔴 origin=SELF 면 직접 짠 여행으로 열고 추천 코스 목록을 묻지 않는다', async () => {
    loadItinerary.mockResolvedValue({ state: 'success', itinerary: itinerary('SELF') });
    const page = await loadTripPageCourses({ kind: 'itinerary', itineraryId: 'it-1' }, 'token');
    expect(page.state === 'ready' && page.selfPlanned).toBe(true);
    expect(page.state === 'ready' && page.courses).toHaveLength(1);
    expect(loadTripCourses).not.toHaveBeenCalled();
  });

  it('추천이 만든 일정(또는 칸이 없는 옛 서버)은 그대로 코스를 보인다', async () => {
    for (const origin of ['RECOMMENDATION', undefined] as const) {
      loadItinerary.mockResolvedValue({ state: 'success', itinerary: itinerary(origin) });
      const page = await loadTripPageCourses({ kind: 'itinerary', itineraryId: 'it-1' }, 'token');
      expect(page.state === 'ready' && page.selfPlanned).toBe(false);
    }
    expect(loadTripCourses).toHaveBeenCalledTimes(2);
  });
});
