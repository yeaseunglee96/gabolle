// 추천 코스 2안·3안 고르기 — S15P21E201-1454.
//
// 🔴 이 시험이 지키는 것은 **눌러도 아무 일이 안 나는 안이 없는가**이다. 2안·3안은 서버가 미리보기만
//    보내고 일정은 고를 때 만든다. 버튼이 「일정 번호가 있나」만 보면 그 둘은 영영 못 고르고,
//    보기엔 세 안인데 둘은 장식이 된다.
const mockApiRequest = jest.fn();
jest.mock('@/api/client', () => ({ ...jest.requireActual('@/api/client'), apiRequest: (...args: unknown[]) => mockApiRequest(...args) }));

import type { ItineraryDto } from '@/plan/itinerary';
import { adaptCourse, canConfirmCourse, ensureCourseItinerary } from '@/plan/tripCourses';

const preview = { id: 'req:1', title: '부산 여행', version: 0, days: [{ date: '2026-10-04', items: [] }] } as unknown as ItineraryDto;

beforeEach(() => mockApiRequest.mockReset());

describe('미리보기를 싣고 온다', () => {
  it('일정이 없는 안은 서버가 보낸 미리보기를 그대로 든다 — 화면이 그것으로 카드를 그린다', () => {
    const course = adaptCourse({ id: 'req:1', itineraryId: null, preview });

    expect(course.preview).toBe(preview);
    expect(canConfirmCourse(course)).toBe(true);
  });

  it('날이 없는 미리보기는 일정인 척 넘기지 않는다', () => {
    const course = adaptCourse({ id: 'req:1', preview: { id: 'x' } as unknown as ItineraryDto });

    expect(course.preview).toBeNull();
    expect(canConfirmCourse(course)).toBe(false);
  });
});

describe('고른 안의 일정 번호', () => {
  it('이미 일정이 있으면 서버를 부르지 않는다', async () => {
    const course = adaptCourse({ id: 'req:0', itineraryId: 'it-a' });

    await expect(ensureCourseItinerary('trip-1', course, 'token')).resolves.toEqual({ state: 'success', itineraryId: 'it-a' });
    expect(mockApiRequest).not.toHaveBeenCalled();
  });

  it('🔴 일정이 없으면 이 안의 번호로 만들어 달라고 하고, 받은 일정 번호를 준다', async () => {
    mockApiRequest.mockResolvedValue({ itineraryId: 'it-b' });
    const course = adaptCourse({ id: 'req:1', itineraryId: null, preview });

    await expect(ensureCourseItinerary('trip-1', course, 'token')).resolves.toEqual({ state: 'success', itineraryId: 'it-b' });
    expect(mockApiRequest).toHaveBeenCalledWith('/api/v1/trips/trip-1/course',
      expect.objectContaining({ method: 'POST', accessToken: 'token', body: { courseId: 'req:1' } }));
  });

  it('못 만들면 실패를 돌려준다 — 조용히 아무 일도 안 나게 두지 않는다', async () => {
    mockApiRequest.mockRejectedValue(new Error('그 코스를 찾지 못했어요.'));
    const course = adaptCourse({ id: 'req:9', preview });

    await expect(ensureCourseItinerary('trip-1', course, null)).resolves.toEqual({ state: 'error', message: '그 코스를 찾지 못했어요.' });
  });
});
