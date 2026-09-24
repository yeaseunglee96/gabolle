// 도로 선이 점선으로 굳지 않는다 — S15P21E201-1575.
//
// 🔴 운영(2026-09-24): 여행 페이지를 열 때 로그인 열쇠가 한 번 바뀌어 진행 중 길 요청이 취소됐고, 그 취소가
//    「길 없음」으로 캐시에 남아 선이 끝까지 점선이었다. 두 경우를 붙든다 — 열쇠가 바뀌어도 선이 오고,
//    한 번 실패해도 다음에 다시 묻는다.
import { act, renderHook, waitFor } from '@testing-library/react-native';

import { clearCourseRoutePathCache, legKey, useCourseRoutePaths } from '@/map/courseRoutePaths';
import { getRouteDirections } from '@/map/routeDirections';

jest.mock('@/map/routeDirections', () => ({ getRouteDirections: jest.fn() }));
const mocked = getRouteDirections as jest.Mock;

const ok = { state: 'success', directions: { path: [[129.1554, 35.16], [129.1586, 35.1653]], estimated: false } };
const a = { id: 'a', number: 1, name: '영남돼지', latitude: 35.1600, longitude: 129.1554 };
const b = { id: 'b', number: 2, name: '고재', latitude: 35.1653, longitude: 129.1586 };
const days = [{ day: 1, stops: [a, b] }];

describe('도로 선이 점선으로 굳지 않는다', () => {
  beforeEach(() => { clearCourseRoutePathCache(); mocked.mockReset(); });

  it('🔴 받는 도중 로그인 열쇠가 바뀌어도 선이 온다 — 요청을 취소하지 않는다', async () => {
    let finish!: (value: unknown) => void;
    mocked.mockImplementation(() => new Promise((resolve) => { finish = resolve; }));
    const { result, rerender } = renderHook(
      ({ token }: { token: string | null }) => useCourseRoutePaths(days, token),
      { initialProps: { token: null as string | null } },
    );

    rerender({ token: 'token' });
    await act(async () => { finish(ok); });

    await waitFor(() => expect(result.current[legKey(1, 0)]?.estimated).toBe(false));
    expect(mocked).toHaveBeenCalledTimes(1);
    expect(mocked.mock.calls[0]).toHaveLength(2); // 취소 신호를 넘기지 않는다
  });

  it('🔴 한 번 못 받아도 다음에 다시 묻는다 — 「길 없음」을 기억하지 않는다', async () => {
    mocked.mockResolvedValueOnce({ state: 'error', message: '잠깐 실패' }).mockResolvedValue(ok);
    const first = renderHook(() => useCourseRoutePaths(days, 'token'));
    await waitFor(() => expect(mocked).toHaveBeenCalledTimes(1));
    first.unmount();

    const { result } = renderHook(() => useCourseRoutePaths(days, 'token'));
    await waitFor(() => expect(result.current[legKey(1, 0)]?.estimated).toBe(false));
    expect(mocked).toHaveBeenCalledTimes(2);
  });
});
