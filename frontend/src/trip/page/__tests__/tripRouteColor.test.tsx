// 여행 페이지 — 경로 선을 고른 경사·그늘 조건대로 칠하기 — S15P21E201-1896.
//
// 🔴 이 시험이 지키는 것(장효준 요청 2026-09-30):
//    ① 일정 응답의 slopeAvoid · shadePrefer 가 «지도 경로 선의 grading» 이 된다. 서버가 false 라고 답하면 기기 초안이 무엇이든 false 다.
//    ② 칸이 없는 옛 서버에서는 기기 초안(slopeConstraint === 'AVOID' · shadePreference === 'PREFER')으로 대신한다. 초안도 없으면 조건 없음.
//    ③ 조건을 골랐고 칠할 조각이 있으면 지도 위에 색 범례가 뜨고, 옛 「경사」·「그늘」 겹 칩은 없다.
//    ④ 🔴 여행 중 GPS 로 따라갈 때(출발 = RUNNING, 내 위치 있음)도 같은 지도 부품이 «같은 routes(같은 grading·조각)» 를 받는다.
//       — 지도 부품은 그 routes 를 routeGrading.ts 의 gradedSegments 로 칠한다(routeGradingMap.test.tsx).
//    ⑤ 조건을 안 골랐으면 조각이 있어도 범례가 없고 grading 은 «둘 다 false» 다(RouteMap 이 경로 자기 색 한 가지로 그린다).
import type { ReactNode } from 'react';
import { act, render, screen, waitFor } from '@testing-library/react-native';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';
import type { ItineraryDto } from '@/plan/itinerary';
import { gradedSegments } from '@/map/routeGrading';
import { TripPageMobile } from '@/trip/page/TripPageMobile';
import { GRADE_COLOR } from '@/map/slopeGrades';

type MapProps = { routes?: Array<{ grading?: { slope: boolean; shade: boolean }; pieces?: unknown[]; path?: unknown[] }>; currentLocation?: { latitude: number; longitude: number } | null };
const mockMapProps: MapProps[] = [];
let mockDraft: { slopeConstraint: 'AVOID' | 'ALLOW' | null; shadePreference: 'PREFER' | 'NO_PREFERENCE' | null } | null = null;

jest.mock('expo-router', () => ({ useRouter: () => ({ push: jest.fn(), replace: jest.fn(), back: jest.fn(), canGoBack: () => true }) }));
jest.mock('react-native-safe-area-context', () => ({ useSafeAreaInsets: () => ({ top: 0, bottom: 0, left: 0, right: 0 }) }));
jest.mock('@/auth/AuthProvider', () => ({ useAuth: () => ({ accessToken: 'token', ready: true }) }));
jest.mock('@/layout/useLayout', () => ({ useLayout: () => ({ kind: 'phone', desktop: false, width: 390, height: 844, isLandscape: false }) }));
jest.mock('@/map/RouteMap', () => ({ RouteMap: (props: MapProps) => { mockMapProps.push(props); return null; } }));
jest.mock('@/plan/PlanProvider', () => ({ ...jest.requireActual('@/plan/PlanProvider'), useOptionalPlanDraft: () => mockDraft }));
jest.mock('@/plan/itinerary', () => ({
  ...jest.requireActual('@/plan/itinerary'),
  loadItinerary: jest.fn(),
  loadItineraryPace: jest.fn(async () => ({ state: 'error', message: 'x' })),
}));
jest.mock('@/plan/tripCourses', () => ({ ...jest.requireActual('@/plan/tripCourses'), loadTripCourses: jest.fn(async () => ({ state: 'empty', message: 'x' })) }));
jest.mock('@/plan/placePhotos', () => ({ ...jest.requireActual('@/plan/placePhotos'), loadPlacePhotos: jest.fn(async () => ({})) }));
jest.mock('@/trip/tripBudget', () => ({ loadTripBudget: jest.fn(async () => ({ state: 'error', message: 'x' })) }));
jest.mock('@/trip/trips', () => ({ ...jest.requireActual('@/trip/trips'), invalidateTripLists: jest.fn(async () => {}), loadTrips: jest.fn(async () => ({ state: 'success', trips: [] })) }));
// 일정 응답에 실려 온 길(known)을 그대로 돌려준다 — 길찾기 요청 없이 «일정이 짠 그 길» 이 지도에 간다.
jest.mock('@/map/courseRoutePaths', () => ({
  ...jest.requireActual('@/map/courseRoutePaths'),
  useCourseRoutePaths: (_days: unknown, _token: unknown, options?: { known?: object }) => options?.known ?? {},
}));
jest.mock('@/trip/TripInvitePanel', () => ({ TripInvitePanel: () => null }));
jest.mock('@/trip/TripReadLinkPanel', () => ({ TripReadLinkPanel: () => null }));
jest.mock('@/trip/TripWeatherPanel', () => ({ TripWeatherPanel: () => null }));
jest.mock('@/social/StoryComposeForm', () => ({ StoryComposeForm: () => null }));
jest.mock('@/trip/TripNameSheet', () => ({ TripNameSheet: () => null }));
// 뼈대 부품은 reanimated 를 부르는데, 시험 환경에서는 그 꾸러미가 안 불린다(tripSheet.test 와 같은 사정).
jest.mock('@/components/Skeleton', () => ({ Skeleton: () => null }));
// 출발한 여행(RUNNING) + 내 위치가 잡힌 상태 — 여행 중 GPS 로 일정을 따라가는 때.
jest.mock('@/trip/page/useTripProgress', () => ({
  useTripProgress: () => ({ progress: { status: 'RUNNING', currentStopIndex: 0, outcomes: {} }, deviceOnly: false, error: null, start: jest.fn(), pause: jest.fn(), arrive: jest.fn(), skip: jest.fn() }),
}));
jest.mock('@/trip/page/useLiveLocation', () => ({
  useLiveLocation: () => ({ state: 'on', fix: { latitude: 35.1612, longitude: 129.1596, accuracy: 10, at: 1_800_000_000_000 } }),
}));

const { loadItinerary } = jest.requireMock('@/plan/itinerary') as { loadItinerary: jest.Mock };

const item = (id: string, lat: number, lng: number, extra: object = {}) => ({ id, startsAt: '2030-10-03T10:00:00', title: id, locked: false, placeId: `p-${id}`, lat, lng, ...extra });
// [경도, 위도] — 서버 순서. 조각 셋: 가파르고 볕뿐 / 완만하고 그늘 많음 / 그늘 모름
const travelPath: Array<[number, number]> = [[129.1588, 35.1636], [129.1596, 35.1612], [129.1604, 35.1587], [129.1610, 35.1570]];
const travelPieces = [
  { from: 0, to: 1, slopePercent: 9.1, stairs: false, shade: 0 },
  { from: 1, to: 2, slopePercent: 1.0, stairs: false, shade: 0.9 },
  { from: 2, to: 3, slopePercent: 1.0, stairs: false },
];
const itinerary = (extra: Partial<ItineraryDto>): ItineraryDto => ({
  id: 'it-1', tripId: 'trip-1', title: '해운대', version: 1,
  days: [{ date: '2030-10-03', items: [item('i1', 35.1636, 129.1588), item('i2', 35.1570, 129.1610, { travelPath, travelPieces })] }],
  ...extra,
} as ItineraryDto);

const queryClient = new QueryClient({ defaultOptions: { queries: { gcTime: Infinity } } });
const wrapper = ({ children }: { children: ReactNode }) => <QueryClientProvider client={queryClient}><OnboardingPreferencesProvider>{children}</OnboardingPreferencesProvider></QueryClientProvider>;
const mount = () => render(<TripPageMobile source={{ kind: 'itinerary', itineraryId: 'it-1' }} />, { wrapper });
const last = () => mockMapProps[mockMapProps.length - 1];
const legendRoutes = () => (last().routes ?? []).find((route) => route.pieces?.length);

const SLOPE_RULE = '‘가파른 경사 피하기’ 기준 · 초록 5% 미만 · 노랑 5~8.33% · 빨강 8.33% 초과 또는 계단';
const BOTH_RULE = '경사 60% + 그늘 40%를 합친 점수 · 계단은 빨강 · 그늘 자료가 없는 곳은 경사만';

beforeEach(() => { mockMapProps.length = 0; mockDraft = null; loadItinerary.mockReset(); jest.useFakeTimers(); });
// 창의 움직임을 끝까지 돌리고 정리한다 — 안 그러면 시험이 끝난 뒤에도 움직임이 돌아 경고가 쏟아진다(tripSheet.test 와 같은 사정).
afterEach(() => { act(() => { jest.runOnlyPendingTimers(); }); jest.useRealTimers(); });

describe('여행 페이지 — 경로 선 색', () => {
  it('🔴 서버가 알려 준 경사·그늘 선택이 지도 경로의 grading 이 된다 — 여행 중 GPS 로 따라갈 때도 같은 routes 를 받는다', async () => {
    loadItinerary.mockResolvedValue({ state: 'success', itinerary: itinerary({ slopeAvoid: true, shadePrefer: true }) });
    mount();
    await waitFor(() => expect(legendRoutes()).toBeTruthy());
    const route = legendRoutes()!;
    expect(route.grading).toEqual({ slope: true, shade: true });
    expect(route.pieces).toEqual(travelPieces);
    // GPS: 출발한 여행의 내 위치가 «같은 지도 부품» 에 같이 간다 — 다른 지도·다른 선이 아니다
    expect(last().currentLocation).toEqual({ latitude: 35.1612, longitude: 129.1596 });
    // 지도 부품이 이 routes 를 칠하는 함수 — 합친 점수: 가파른 볕(bad) · 완만한 그늘(good) · 그늘 모름은 경사만(good)
    const segments = gradedSegments(route.path as never, route.pieces as never, route.grading);
    expect(segments?.map((segment) => segment.color)).toEqual([GRADE_COLOR.bad, GRADE_COLOR.good]);
    // 범례가 뜨고, 옛 「경사」·「그늘」 겹 칩은 없다
    expect(screen.getByText(BOTH_RULE)).toBeTruthy();
    expect(screen.queryByText('경사')).toBeNull();
    expect(screen.queryByText('그늘')).toBeNull();
  });

  it('🔴 경사만 골랐으면 경사 기준 범례 — 그늘 값은 무시한다', async () => {
    loadItinerary.mockResolvedValue({ state: 'success', itinerary: itinerary({ slopeAvoid: true, shadePrefer: false }) });
    mount();
    await waitFor(() => expect(legendRoutes()).toBeTruthy());
    expect(legendRoutes()!.grading).toEqual({ slope: true, shade: false });
    expect(screen.getByText(SLOPE_RULE)).toBeTruthy();
  });

  it('🔴 아무것도 안 골랐으면 조각이 있어도 범례가 없고 grading 은 둘 다 false — 경로 자기 색 한 가지', async () => {
    loadItinerary.mockResolvedValue({ state: 'success', itinerary: itinerary({ slopeAvoid: false, shadePrefer: false }) });
    mount();
    await waitFor(() => expect(legendRoutes()).toBeTruthy());
    const route = legendRoutes()!;
    expect(route.grading).toEqual({ slope: false, shade: false });
    expect(gradedSegments(route.path as never, route.pieces as never, route.grading)).toBeNull();
    expect(screen.queryByText(SLOPE_RULE)).toBeNull();
    expect(screen.queryByText(BOTH_RULE)).toBeNull();
    expect(screen.queryByText('잘 맞아요')).toBeNull();
  });

  it('🔴 서버가 false 라고 답하면 기기 초안이 «경사 피하기» 여도 따르지 않는다 — 초안은 새 여행의 답이다', async () => {
    mockDraft = { slopeConstraint: 'AVOID', shadePreference: 'PREFER' };
    loadItinerary.mockResolvedValue({ state: 'success', itinerary: itinerary({ slopeAvoid: false, shadePrefer: false }) });
    mount();
    await waitFor(() => expect(legendRoutes()).toBeTruthy());
    expect(legendRoutes()!.grading).toEqual({ slope: false, shade: false });
  });

  it('🔴 칸이 없는 옛 서버에서는 기기 초안으로 대신한다', async () => {
    mockDraft = { slopeConstraint: 'AVOID', shadePreference: 'PREFER' };
    loadItinerary.mockResolvedValue({ state: 'success', itinerary: itinerary({}) });
    mount();
    await waitFor(() => expect(legendRoutes()).toBeTruthy());
    expect(legendRoutes()!.grading).toEqual({ slope: true, shade: true });
    expect(screen.getByText(BOTH_RULE)).toBeTruthy();
  });

  it('🔴 옛 서버인데 초안도 없으면(다른 기기에서 연 여행) 조건 없음 — 예전처럼 늘 경사 색으로 칠하지 않는다', async () => {
    loadItinerary.mockResolvedValue({ state: 'success', itinerary: itinerary({}) });
    mount();
    await waitFor(() => expect(legendRoutes()).toBeTruthy());
    expect(legendRoutes()!.grading).toEqual({ slope: false, shade: false });
    expect(screen.queryByText(SLOPE_RULE)).toBeNull();
  });

  it('🔴 그늘 값이 하나도 안 온 길(그늘 자료 밖·옛 서버)을 그늘까지 골랐으면 «경사로만 칠했다» 고 말한다', async () => {
    const noShade = travelPieces.map(({ shade: _shade, ...rest }) => rest);
    loadItinerary.mockResolvedValue({
      state: 'success',
      itinerary: itinerary({
        slopeAvoid: true,
        shadePrefer: true,
        days: [{ date: '2030-10-03', items: [item('i1', 35.1636, 129.1588), item('i2', 35.1570, 129.1610, { travelPath, travelPieces: noShade })] }],
      }),
    });
    mount();
    await waitFor(() => expect(legendRoutes()).toBeTruthy());
    expect(screen.getByText('지금 보이는 길은 그늘 자료가 아직 없어 경사로만 칠했어요')).toBeTruthy();
  });
});
