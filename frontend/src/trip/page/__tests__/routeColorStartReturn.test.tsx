// 여행 페이지 — 하루 시작·돌아가는 구간도 고른 조건 색으로 칠하기 — S15P21E201-1899.
//
// 🔴 이 시험이 지키는 것(운영 지도에서 색칠한 경로 한가운데 검은 선이 섞여 있던 것, 2026-09-30):
//    ① 출발지(또는 숙소)에서 첫 곳까지의 선이 «걷기로 받은 경사·그늘 조각» 과 «고른 조건» 을 지도로 넘긴다.
//       전에는 길(path)만 넘겨서, 조각을 받아 놓고도 그 구간만 남색 한 가지였다.
//    ② 걷는 날은 돌아가는 구간의 길도 걷기로 묻는다(도착 자리가 walkInto 에 든다). 걷는 항목이 없으면 안 든다 —
//       서버가 자동차·대중교통 여행의 돌아가는 구간을 걷기로 답하게 만들지 않는다.
//    ③ 조건을 안 골랐으면 조각이 있어도 grading 은 «둘 다 false» 다(지도가 남색 한 가지로 그린다).
//    ④ 조각이 없는 길(자동차·대중교통·어림)은 전처럼 자기 색 한 가지다 — 없는 경사·그늘을 지어내 칠하지 않는다.
import type { ReactNode } from 'react';
import { act, render, waitFor } from '@testing-library/react-native';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

import { legKey } from '@/map/courseRoutePaths';
import { gradedSegments } from '@/map/routeGrading';
import { GRADE_COLOR } from '@/map/slopeGrades';
import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';
import type { ItineraryDto } from '@/plan/itinerary';
import { TripPageMobile } from '@/trip/page/TripPageMobile';
import { RETURN_DAY_OFFSET, START_DAY_OFFSET, returnAnchorId, returnRoute, returnTrip, startTrip, walkIntoStopIds, type DayMap } from '@/trip/page/tripPageModel';

type MapRoute = { id?: string; color?: string; grading?: { slope: boolean; shade: boolean }; pieces?: unknown[]; path?: unknown[] };
type MapProps = { routes?: MapRoute[] };
type CourseOptions = { walkInto?: ReadonlySet<string>; known?: object };
const mockMapProps: MapProps[] = [];
const mockCourseOptions: CourseOptions[] = [];
let mockFetchedLegs: Record<string, unknown> = {};

jest.mock('expo-router', () => ({ useRouter: () => ({ push: jest.fn(), replace: jest.fn(), back: jest.fn(), canGoBack: () => true }) }));
jest.mock('react-native-safe-area-context', () => ({ useSafeAreaInsets: () => ({ top: 0, bottom: 0, left: 0, right: 0 }) }));
jest.mock('@/auth/AuthProvider', () => ({ useAuth: () => ({ accessToken: 'token', ready: true }) }));
jest.mock('@/layout/useLayout', () => ({ useLayout: () => ({ kind: 'phone', desktop: false, width: 390, height: 844, isLandscape: false }) }));
jest.mock('@/map/RouteMap', () => ({ RouteMap: (props: MapProps) => { mockMapProps.push(props); return null; } }));
jest.mock('@/plan/PlanProvider', () => ({ ...jest.requireActual('@/plan/PlanProvider'), useOptionalPlanDraft: () => null }));
jest.mock('@/plan/itinerary', () => ({
  ...jest.requireActual('@/plan/itinerary'),
  loadItinerary: jest.fn(),
  loadItineraryPace: jest.fn(async () => ({ state: 'error', message: 'x' })),
}));
jest.mock('@/plan/tripCourses', () => ({ ...jest.requireActual('@/plan/tripCourses'), loadTripCourses: jest.fn(async () => ({ state: 'empty', message: 'x' })) }));
jest.mock('@/plan/placePhotos', () => ({ ...jest.requireActual('@/plan/placePhotos'), loadPlacePhotos: jest.fn(async () => ({})) }));
jest.mock('@/trip/tripBudget', () => ({ loadTripBudget: jest.fn(async () => ({ state: 'error', message: 'x' })) }));
jest.mock('@/trip/trips', () => ({ ...jest.requireActual('@/trip/trips'), invalidateTripLists: jest.fn(async () => {}), loadTrips: jest.fn(async () => ({ state: 'success', trips: [] })) }));
// 길찾기 요청 없이, 지도가 «받아 온 길» 을 돌려준다 — 일정 응답의 길(known)에 시험이 정한 구간(출발·돌아가기)을 더해서.
// 어떤 구간을 걷기로 물었는지(walkInto)도 남긴다.
jest.mock('@/map/courseRoutePaths', () => {
  const react = jest.requireActual('react') as typeof import('react');
  return {
    ...jest.requireActual('@/map/courseRoutePaths'),
    useCourseRoutePaths: (_days: unknown, _token: unknown, options?: CourseOptions) => {
      mockCourseOptions.push(options ?? {});
      const known = options?.known;
      // 렌더마다 새 객체를 주면 지도 선이 매번 다시 계산된다 — 일정 응답의 길이 그대로인 동안은 같은 객체를 준다.
      return react.useMemo(() => ({ ...(known ?? {}), ...mockFetchedLegs }), [known]);
    },
  };
});
jest.mock('@/trip/TripInvitePanel', () => ({ TripInvitePanel: () => null }));
jest.mock('@/trip/TripReadLinkPanel', () => ({ TripReadLinkPanel: () => null }));
jest.mock('@/trip/TripWeatherPanel', () => ({ TripWeatherPanel: () => null }));
jest.mock('@/social/StoryComposeForm', () => ({ StoryComposeForm: () => null }));
jest.mock('@/trip/TripNameSheet', () => ({ TripNameSheet: () => null }));
// 뼈대 부품은 reanimated 를 부르는데, 시험 환경에서는 그 꾸러미가 안 불린다(tripSheet.test 와 같은 사정).
jest.mock('@/components/Skeleton', () => ({ Skeleton: () => null }));
// 출발한 여행(RUNNING) + 내 위치가 잡힌 상태 — 여행 중 GPS 로 일정을 따라가는 때도 같은 routes 를 받는다.
jest.mock('@/trip/page/useTripProgress', () => ({
  useTripProgress: () => ({ progress: { status: 'RUNNING', currentStopIndex: 0, outcomes: {} }, deviceOnly: false, error: null, start: jest.fn(), pause: jest.fn(), arrive: jest.fn(), skip: jest.fn() }),
}));
jest.mock('@/trip/page/useLiveLocation', () => ({
  useLiveLocation: () => ({ state: 'on', fix: { latitude: 35.1612, longitude: 129.1596, accuracy: 10, at: 1_800_000_000_000 } }),
}));

const { loadItinerary } = jest.requireMock('@/plan/itinerary') as { loadItinerary: jest.Mock };

// ── 순수 규칙 ─────────────────────────────────────────────────────────────

const map: DayMap = {
  stops: [
    { id: 's1', number: 1, name: '원갤러리', latitude: 35.1604, longitude: 129.1547 },
    { id: 's2', number: 2, name: '해운대 관광특구', latitude: 35.1587, longitude: 129.1604 },
  ],
  days: [],
};
const origin = { kind: 'ORIGIN' as const, label: null, lat: 35.1637, lng: 129.1589 };
const areaLodging = { kind: 'LODGING' as const, label: '해운대', lat: 35.1587, lng: 129.1604 };
const leg = { label: null, durationMin: 6, distanceM: 391, travelDataStatus: 'VERIFIED' as const };
// 조각 둘: 가파르고 볕뿐 / 완만하고 그늘 많음
const path = [{ latitude: 35.1637, longitude: 129.1589 }, { latitude: 35.1620, longitude: 129.1580 }, { latitude: 35.1604, longitude: 129.1547 }];
const pieces = [
  { from: 0, to: 1, slopePercent: 9.1, stairs: false, shade: 0 },
  { from: 1, to: 2, slopePercent: 1.0, stairs: false, shade: 0.9 },
];
const BOTH = { slope: true, shade: true };

describe('하루 시작 선 — 걷기로 받은 조각과 고른 조건을 지도로 넘긴다', () => {
  it('🔴 출발지→첫 곳 선이 조각과 조건을 그대로 넘긴다 — 지도가 조각 색으로 칠할 수 있다', () => {
    const start = startTrip(map, 1, origin)!;
    const route = returnRoute(start, '#111', { [legKey(start.day.day, 0)]: { path, estimated: false, pieces } }, BOTH);
    expect(route.pieces).toBe(pieces);
    expect(route.grading).toEqual(BOTH);
    // 지도 부품이 이 선을 칠하는 함수 — 가파른 볕(bad) · 완만한 그늘(good). 전에는 조각이 없어 null(남색 한 가지)이었다.
    expect(gradedSegments(route.path as never, route.pieces as never, route.grading)?.map((segment) => segment.color)).toEqual([GRADE_COLOR.bad, GRADE_COLOR.good]);
  });

  it('돌아가는 선도 같다 — 걷기로 받은 조각이 있으면 조건 색이다', () => {
    const back = returnTrip(map, 1, { ...origin, ...leg })!;
    const route = returnRoute(back, '#111', { [legKey(back.day.day, 0)]: { path, estimated: false, pieces } }, { slope: true, shade: false });
    expect(route.grading).toEqual({ slope: true, shade: false });
    expect(gradedSegments(route.path as never, route.pieces as never, route.grading)?.map((segment) => segment.color)).toEqual([GRADE_COLOR.bad, GRADE_COLOR.good]);
  });

  it('조건을 안 넘기면 grading 칸 자체가 없다 — 전과 같은 모양', () => {
    const start = startTrip(map, 1, origin)!;
    const route = returnRoute(start, '#111', { [legKey(start.day.day, 0)]: { path, estimated: false, pieces } });
    expect('grading' in route).toBe(false);
  });

  it('🔴 아무것도 안 골랐으면 조각이 있어도 남색 한 가지 — grading 이 둘 다 false 라 지도가 조각으로 안 자른다', () => {
    const start = startTrip(map, 1, origin)!;
    const route = returnRoute(start, '#111', { [legKey(start.day.day, 0)]: { path, estimated: false, pieces } }, { slope: false, shade: false });
    expect(gradedSegments(route.path as never, route.pieces as never, route.grading)).toBeNull();
  });

  it('🔴 조각이 없는 길(자동차·대중교통·어림)은 전처럼 한 가지 색 — 없는 경사·그늘을 지어내지 않는다', () => {
    const start = startTrip(map, 1, origin)!;
    const route = returnRoute(start, '#111', { [legKey(start.day.day, 0)]: { path, estimated: false } }, BOTH);
    expect(route.pieces).toBeUndefined();
    expect(gradedSegments(route.path as never, route.pieces as never, route.grading)).toBeNull();
  });

  it('동네 숙소면 받아 온 길도 조각도 안 쓴다 — 정확한 숙소를 모르는데 길을 지어내지 않는다(S15P21E201-1570)', () => {
    const start = startTrip(map, 2, areaLodging)!;
    expect(start.approximate).toBe(true);
    const route = returnRoute(start, '#111', { [legKey(start.day.day, 0)]: { path, estimated: false, pieces } }, BOTH);
    expect(route.path).toBeUndefined();
    expect(route.pieces).toBeUndefined();
    expect(route.estimated).toBe(true);
  });
});

describe('걷기로 묻는 정차지 — 걷는 날은 돌아가는 구간도 걷기다', () => {
  it('🔴 걷는 항목이 있는 날은 그 항목들과 돌아가는 자리를 걷기로 묻는다', () => {
    const ids = walkIntoStopIds([{ items: [{ id: 'a', walkingMeters: 630 }, { id: 'b', walkingMeters: 420 }] }]);
    expect([...ids].sort()).toEqual([returnAnchorId(1), 'a', 'b'].sort());
  });

  it('🔴 걷는 항목이 하나도 없는 날(자동차·대중교통 여행, 거리를 모르는 날)은 돌아가는 구간을 걷기로 묻지 않는다', () => {
    const ids = walkIntoStopIds([{ items: [{ id: 'a', walkingMeters: null }, { id: 'b' }] }, { items: [] }]);
    expect(ids.size).toBe(0);
  });

  it('날마다 따로 본다 — 둘째 날만 걷는 여행이면 둘째 날 돌아가는 자리만 든다', () => {
    const ids = walkIntoStopIds([{ items: [{ id: 'a', walkingMeters: null }] }, { items: [{ id: 'b', walkingMeters: 300 }] }]);
    expect([...ids].sort()).toEqual([returnAnchorId(2), 'b'].sort());
    expect(ids.has(returnAnchorId(1))).toBe(false);
  });

  it('돌아가는 자리의 id 는 returnTrip 이 지도에 찍는 자리와 같다 — 길을 받아 오는 쪽이 이 id 로 걷기를 고른다', () => {
    const back = returnTrip(map, 3, { ...origin, ...leg })!;
    expect(back.anchor.id).toBe(returnAnchorId(3));
  });
});

// ── 여행 페이지 안에서 — 위 규칙을 정말 쓰는가 ─────────────────────────────

const item = (id: string, lat: number, lng: number, extra: object = {}) => ({ id, startsAt: '2030-10-03T10:00:00', title: id, locked: false, placeId: `p-${id}`, lat, lng, ...extra });
const returnLeg = { kind: 'ORIGIN' as const, ...leg, lat: origin.lat, lng: origin.lng };
const itinerary = (walking: boolean, extra: Partial<ItineraryDto> = {}): ItineraryDto => ({
  id: 'it-1', tripId: 'trip-1', title: '해운대', version: 1, slopeAvoid: true, shadePrefer: true,
  days: [{
    date: '2030-10-03',
    items: [
      item('i1', 35.1604, 129.1547, walking ? { walkingMeters: 630 } : {}),
      item('i2', 35.1587, 129.1604, walking ? { walkingMeters: 500 } : {}),
    ],
    start: { kind: 'ORIGIN', label: null, lat: origin.lat, lng: origin.lng },
    returnLeg,
  }],
  ...extra,
} as ItineraryDto);

const queryClient = new QueryClient({ defaultOptions: { queries: { gcTime: Infinity } } });
const wrapper = ({ children }: { children: ReactNode }) => <QueryClientProvider client={queryClient}><OnboardingPreferencesProvider>{children}</OnboardingPreferencesProvider></QueryClientProvider>;
const mount = () => render(<TripPageMobile source={{ kind: 'itinerary', itineraryId: 'it-1' }} />, { wrapper });
const lastRoutes = () => mockMapProps[mockMapProps.length - 1]?.routes ?? [];
const lastWalkInto = () => mockCourseOptions[mockCourseOptions.length - 1]?.walkInto ?? new Set<string>();
const startRouteId = `return-${START_DAY_OFFSET + 1}`;
const backRouteId = `return-${RETURN_DAY_OFFSET + 1}`;

beforeEach(() => { mockMapProps.length = 0; mockCourseOptions.length = 0; mockFetchedLegs = {}; loadItinerary.mockReset(); jest.useFakeTimers(); });
// 창의 움직임을 끝까지 돌리고 정리한다 — 안 그러면 시험이 끝난 뒤에도 움직임이 돌아 경고가 쏟아진다(tripSheet.test 와 같은 사정).
afterEach(() => { act(() => { jest.runOnlyPendingTimers(); }); jest.useRealTimers(); });

describe('여행 페이지 — 출발·돌아가는 구간의 색', () => {
  it('🔴 걷기로 받은 출발·돌아가는 구간이 조각과 고른 조건을 지도로 넘긴다 — 여행 중 GPS 로 따라갈 때도 같은 routes 다', async () => {
    mockFetchedLegs = {
      [legKey(START_DAY_OFFSET + 1, 0)]: { path, estimated: false, pieces },
      [legKey(RETURN_DAY_OFFSET + 1, 0)]: { path, estimated: false, pieces },
    };
    loadItinerary.mockResolvedValue({ state: 'success', itinerary: itinerary(true) });
    mount();
    await waitFor(() => expect(lastRoutes().some((route) => route.id === startRouteId)).toBe(true));
    for (const id of [startRouteId, backRouteId]) {
      const route = lastRoutes().find((candidate) => candidate.id === id)!;
      expect(route.grading).toEqual(BOTH);
      expect(route.pieces).toEqual(pieces);
      expect(gradedSegments(route.path as never, route.pieces as never, route.grading)?.map((segment) => segment.color)).toEqual([GRADE_COLOR.bad, GRADE_COLOR.good]);
    }
  });

  it('🔴 걷는 날은 돌아가는 구간의 길을 걷기로 묻는다 — 출발 구간은 첫 항목이 걷기라 전부터 걷기다', async () => {
    loadItinerary.mockResolvedValue({ state: 'success', itinerary: itinerary(true) });
    mount();
    await waitFor(() => expect(lastWalkInto().size).toBeGreaterThan(0));
    expect(lastWalkInto().has(returnAnchorId(1))).toBe(true);
    expect(lastWalkInto().has('i1')).toBe(true);
    expect(lastWalkInto().has('i2')).toBe(true);
  });

  it('🔴 걷는 항목이 없는 여행은 돌아가는 구간을 걷기로 묻지 않는다 — 자동차·대중교통 여행의 돌아가는 길을 걷기로 바꾸지 않는다', async () => {
    loadItinerary.mockResolvedValue({ state: 'success', itinerary: itinerary(false) });
    mount();
    await waitFor(() => expect(lastRoutes().some((route) => route.id === startRouteId)).toBe(true));
    expect(lastWalkInto().size).toBe(0);
  });

  it('🔴 조건을 안 골랐으면 출발·돌아가는 선도 grading 이 둘 다 false — 남색 한 가지', async () => {
    mockFetchedLegs = {
      [legKey(START_DAY_OFFSET + 1, 0)]: { path, estimated: false, pieces },
      [legKey(RETURN_DAY_OFFSET + 1, 0)]: { path, estimated: false, pieces },
    };
    loadItinerary.mockResolvedValue({ state: 'success', itinerary: itinerary(true, { slopeAvoid: false, shadePrefer: false }) });
    mount();
    await waitFor(() => expect(lastRoutes().some((route) => route.id === startRouteId)).toBe(true));
    for (const id of [startRouteId, backRouteId]) {
      const route = lastRoutes().find((candidate) => candidate.id === id)!;
      expect(route.grading).toEqual({ slope: false, shade: false });
      expect(gradedSegments(route.path as never, route.pieces as never, route.grading)).toBeNull();
    }
  });
});
