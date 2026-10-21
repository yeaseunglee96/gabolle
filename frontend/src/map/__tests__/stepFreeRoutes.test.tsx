// 휠체어·유아차·계단 피하기 여행은 지도도 계단을 피하는 길로 묻는다.
//
// 🔴 이 시험이 지키는 것: 일정은 서버가 계단 없는 길로 짰는데, 지도가 보통 길을 물으면 계단 길이 그려진다.
//    ① stepFree=true 는 여행이 그렇다고 할 때만 주소에 붙는다 — 아니면 전과 같은 주소(옛 서버도 그대로 답한다).
//    ② 같은 두 점이라도 계단을 피하는 길과 보통 길은 다른 길이다 — 캐시 열쇠를 가른다.
import { renderHook, waitFor } from '@testing-library/react-native';

import { apiRequest } from '@/api/client';
import { clearCourseRoutePathCache, legKey, useCourseRoutePaths } from '@/map/courseRoutePaths';

jest.mock('@/api/client', () => ({
  ...jest.requireActual('@/api/client'),
  apiRequest: jest.fn(),
}));
const mockedRequest = apiRequest as jest.Mock;

const a = { id: 'a', number: 1, name: '해운대역', latitude: 35.1636, longitude: 129.1588 };
const b = { id: 'b', number: 2, name: '해운대해수욕장', latitude: 35.1587, longitude: 129.1604 };
const days = [{ day: 1, stops: [a, b] }];
const ok = { mode: 'WALK', path: [[129.1588, 35.1636], [129.1604, 35.1587]], estimated: false, pieces: [] };

const urls = () => mockedRequest.mock.calls.map((call) => String(call[0]));

describe('계단을 피하는 길로 묻기', () => {
  beforeEach(() => { clearCourseRoutePathCache(); mockedRequest.mockReset(); mockedRequest.mockResolvedValue(ok); });

  it('🔴 여행이 stepFree 면 구간 요청 주소에 stepFree=true 를 싣는다', async () => {
    const { result } = renderHook(() => useCourseRoutePaths(days, 'token', { walkInto: new Set(['b']), stepFree: true }));
    await waitFor(() => expect(result.current[legKey(1, 0)]).toBeTruthy());
    expect(urls()).toHaveLength(1);
    expect(urls()[0]).toContain('stepFree=true');
    expect(urls()[0]).toContain('mode=WALK');
  });

  it('🔴 stepFree 가 아니거나 모르면(옛 서버) 주소에 안 싣는다 — 전과 같은 주소', async () => {
    const off = renderHook(() => useCourseRoutePaths(days, 'token', { stepFree: false }));
    await waitFor(() => expect(off.result.current[legKey(1, 0)]).toBeTruthy());
    clearCourseRoutePathCache();
    const unknown = renderHook(() => useCourseRoutePaths(days, 'token'));
    await waitFor(() => expect(unknown.result.current[legKey(1, 0)]).toBeTruthy());
    expect(urls()).toHaveLength(2);
    urls().forEach((url) => expect(url).not.toContain('stepFree'));
  });

  it('🔴 같은 두 점이라도 계단을 피하는 길은 따로 묻는다 — 보통 길 캐시를 그대로 쓰지 않는다', async () => {
    const plain = renderHook(() => useCourseRoutePaths(days, 'token'));
    await waitFor(() => expect(plain.result.current[legKey(1, 0)]).toBeTruthy());
    const stepFree = renderHook(() => useCourseRoutePaths(days, 'token', { stepFree: true }));
    await waitFor(() => expect(stepFree.result.current[legKey(1, 0)]).toBeTruthy());
    expect(urls()).toHaveLength(2);
    expect(urls()[0]).not.toContain('stepFree');
    expect(urls()[1]).toContain('stepFree=true');
  });
});
