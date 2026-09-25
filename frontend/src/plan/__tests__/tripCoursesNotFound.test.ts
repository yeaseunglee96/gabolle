// 없는 여행으로 코스 목록을 부르지 않는다 — S15P21E201-1641.
//
// 🔴 이 시험이 지키는 것: 운영에서 4일간 55번, 없는 여행(지워졌거나 남의 여행)으로 코스 목록을 불렀다. 서버는 404
//    TRIP_NOT_FOUND 로 맞게 답했다. 앱은 작업 목록 조회에서 「없음」을 받고도 코스 목록을 또 불렀고, 코스 목록의 404 는
//    「아직 이 기능이 서버에 없다」(옛 계약 가정)로 읽어 구분하지 못했다.
import { ApiClientError, apiRequest } from '@/api/client';
import { loadTripCourses } from '@/plan/tripCourses';
import { loadTripPageCourses } from '@/trip/page/tripPageData';

jest.mock('@/api/client', () => ({ ...jest.requireActual('@/api/client'), apiRequest: jest.fn() }));
const request = jest.mocked(apiRequest);
const notFound = () => new ApiClientError('여행을 찾을 수 없어요.', 'TRIP_NOT_FOUND', 404);
const calls = (fragment: string) => request.mock.calls.filter(([path]) => String(path).includes(fragment)).length;

beforeEach(() => request.mockReset());

describe('없는 여행', () => {
  it('🔴 코스 목록이 「그런 여행 없음」이면 not-found — 옛 일정으로 대신 채우려고 또 부르지 않는다', async () => {
    request.mockRejectedValue(notFound());
    await expect(loadTripCourses('gone', 'it-1', 'token')).resolves.toMatchObject({ state: 'not-found' });
    expect(request).toHaveBeenCalledTimes(1);
  });

  it('코드 없는 404(옛 서버 — 코스 계약이 아직 없음)는 지금처럼 일정 하나로 대신한다', async () => {
    request.mockImplementation(async (path) => {
      if (String(path).includes('/recommendations')) throw new ApiClientError('없음', 'NOT_FOUND', 404);
      return { id: 'it-1', tripId: 'trip-1', title: '광안리', version: 1, days: [{ date: '2026-10-03', items: [] }] };
    });
    await expect(loadTripCourses('trip-1', 'it-1', 'token')).resolves.toMatchObject({ state: 'success', full: false });
  });

  it('🔴 여행 화면: 작업 목록 조회가 「그런 여행 없음」이면 코스 목록을 0번 부른다', async () => {
    request.mockImplementation(async (path) => {
      if (String(path).includes('/recommendation-jobs')) throw notFound();
      return {};
    });
    await expect(loadTripPageCourses({ kind: 'trip', tripId: 'gone' }, 'token')).resolves.toMatchObject({ state: 'error' });
    expect(calls('/recommendations')).toBe(0);
  });
});
