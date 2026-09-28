// 실제 도로 선의 좌표 순서 — S15P21E201-1567.
//
// 🔴 서버는 [경도, 위도] 로 준다(RouteLeg.path). 앱이 [위도, 경도] 로 읽어 선이 위도 129 인 곳 — 지도 밖 — 에
//    그려졌고, 여행 페이지 지도에는 정차지 사이 선이 하나도 안 보였다. 운영 응답 그대로의 모양으로 붙든다.
import { renderHook, waitFor } from '@testing-library/react-native';

import { clearCourseRoutePathCache, legKey, useCourseRoutePaths } from '@/map/courseRoutePaths';

jest.mock('@/map/routeDirections', () => ({
  getRouteDirections: jest.fn(async () => ({
    state: 'success',
    // 운영 /api/v1/routes/directions 응답의 첫 점·끝 점(영남돼지 → 고재) — [경도, 위도]
    directions: { path: [[129.15544, 35.16012], [129.15868, 35.16543]], estimated: false },
  })),
}));

const a = { id: 'a', number: 1, name: '영남돼지', latitude: 35.1600, longitude: 129.1554 };
const b = { id: 'b', number: 2, name: '고재', latitude: 35.1653, longitude: 129.1586 };

describe('실제 도로 선의 좌표 순서', () => {
  beforeEach(() => clearCourseRoutePathCache());

  it('🔴 서버의 [경도, 위도] 를 위도·경도로 옮겨 읽는다 — 선이 부산 위에 놓인다', async () => {
    const days = [{ day: 1, stops: [a, b] }];
    const { result } = renderHook(() => useCourseRoutePaths(days, 'token'));

    await waitFor(() => expect(result.current[legKey(1, 0)]).toBeDefined());
    const first = result.current[legKey(1, 0)].path[0];
    expect(first.latitude).toBeCloseTo(35.16012, 5);
    expect(first.longitude).toBeCloseTo(129.15544, 5);
    expect(result.current[legKey(1, 0)].estimated).toBe(false);
  });
});
