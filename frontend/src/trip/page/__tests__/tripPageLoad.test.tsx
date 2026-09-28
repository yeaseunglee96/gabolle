// 여행 일정 화면 여는 길 — S15P21E201-1599.
//
// 🔴 이 시험이 지키는 것:
//    ① 한 번 여는 동안 코스 목록(recommendations)과 일정은 한 번씩만 부른다. 전에는 로그인 복구가 끝나기 전에
//       열쇠 없이 불러 401 → 갱신이 겹치고, 열쇠가 바뀔 때마다 처음부터 다시 불러서 운영에서 한 번 여는데
//       일정 ×6 · 코스 목록 ×4 · 갱신 ×3 이 나갔다(조율 세션 실측).
//    ② 확정한 일정은 코스 목록을 기다리지 않고 먼저 그린다. 코스 목록은 서버에서 수 초가 걸린다.
import type { ReactNode } from 'react';
import { act, renderHook, waitFor } from '@testing-library/react-native';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';
import type { ItineraryDto } from '@/plan/itinerary';
import type { TripCourse, TripCoursesResult } from '@/plan/tripCourses';
import { useTripPage } from '@/trip/page/useTripPage';

const auth = { accessToken: null as string | null, ready: false };
jest.mock('expo-router', () => ({ useRouter: () => ({ push: jest.fn() }) }));
jest.mock('@/auth/AuthProvider', () => ({ useAuth: () => auth }));
jest.mock('@/plan/itinerary', () => ({
  ...jest.requireActual('@/plan/itinerary'),
  loadItinerary: jest.fn(),
  loadItineraryPace: jest.fn(async () => ({ state: 'error', message: 'x' })),
}));
jest.mock('@/plan/tripCourses', () => ({ ...jest.requireActual('@/plan/tripCourses'), loadTripCourses: jest.fn() }));
jest.mock('@/plan/placePhotos', () => ({ ...jest.requireActual('@/plan/placePhotos'), loadPlacePhotos: jest.fn(async () => ({})) }));
jest.mock('@/trip/tripBudget', () => ({ loadTripBudget: jest.fn(async () => ({ state: 'error', message: 'x' })) }));
jest.mock('@/map/courseRoutePaths', () => ({ useCourseRoutePaths: () => ({}) }));
jest.mock('@/trip/trips', () => ({ ...jest.requireActual('@/trip/trips'), invalidateTripLists: jest.fn(async () => {}), loadTrips: jest.fn(async () => ({ state: 'success', trips: [] })) }));
jest.mock('@/trip/tripNaming', () => ({ ...jest.requireActual('@/trip/tripNaming'), wasTripNameAsked: jest.fn(async () => true) }));

const { loadItinerary } = jest.requireMock('@/plan/itinerary') as { loadItinerary: jest.Mock };
const { loadTripCourses } = jest.requireMock('@/plan/tripCourses') as { loadTripCourses: jest.Mock };
const { invalidateTripLists } = jest.requireMock('@/trip/trips') as { invalidateTripLists: jest.Mock };

const ITINERARY: ItineraryDto = {
  id: 'it-1', tripId: 'trip-1', title: '광안리 여행', version: 1,
  days: [{ date: '2026-10-03', items: [{ id: 'i1', startsAt: '2026-10-03T10:00:00', title: '카페', locked: false, placeId: 'p1' }] }],
} as ItineraryDto;
const course = (id: string, itineraryId: string | null): TripCourse => ({
  id, title: id, tagline: '', days: [{ day: 1, stops: [] }], summary: { places: null, moveMin: null, walkKm: null, costKrw: null },
  status: 'ESTIMATED', rationale: null, itineraryId, preview: itineraryId ? null : ITINERARY,
});
const LIST: TripCoursesResult = { state: 'success', courses: [course('A', null), course('B', 'it-1'), course('C', null)], full: true };

const queryClient = new QueryClient({ defaultOptions: { queries: { gcTime: Infinity } } });
const wrapper = ({ children }: { children: ReactNode }) => <QueryClientProvider client={queryClient}><OnboardingPreferencesProvider>{children}</OnboardingPreferencesProvider></QueryClientProvider>;
const source = { kind: 'itinerary' as const, itineraryId: 'it-1' };

beforeEach(() => {
  auth.accessToken = null;
  auth.ready = false;
  loadItinerary.mockReset().mockResolvedValue({ state: 'success', itinerary: ITINERARY });
  loadTripCourses.mockReset().mockResolvedValue(LIST);
});

describe('여행 일정 화면 여는 길', () => {
  it('🔴 한 번 여는 동안 코스 목록·일정은 한 번씩 — 로그인 복구 전엔 안 부르고, 열쇠가 바뀌어도 다시 안 부른다', async () => {
    const view = renderHook(() => useTripPage(source), { wrapper });
    // 로그인 복구 전 — 열쇠 없이 부르면 401 이 나고 갱신이 겹친다
    expect(loadItinerary).not.toHaveBeenCalled();
    expect(loadTripCourses).not.toHaveBeenCalled();

    auth.accessToken = 'token-1';
    auth.ready = true;
    view.rerender({});
    await waitFor(() => expect(view.result.current.page?.state === 'ready' && view.result.current.courses).toHaveLength(3));
    await waitFor(() => expect(view.result.current.loaded?.id).toBe('it-1'));

    // 한 시간마다 새로 받는 열쇠 — 바뀌었다고 처음부터 다시 부르지 않는다
    auth.accessToken = 'token-2';
    view.rerender({});
    await act(async () => { await Promise.resolve(); });

    expect(loadTripCourses).toHaveBeenCalledTimes(1);
    expect(loadItinerary).toHaveBeenCalledTimes(1);
  });

  it('🔴 코스 목록이 오기 전에 확정한 일정을 먼저 그린다 — 목록이 오면 그 일정의 코스가 켜진다', async () => {
    let arrive: (value: TripCoursesResult) => void = () => {};
    loadTripCourses.mockReturnValue(new Promise<TripCoursesResult>((resolve) => { arrive = resolve; }));
    auth.accessToken = 'token-1';
    auth.ready = true;
    const view = renderHook(() => useTripPage(source), { wrapper });

    await waitFor(() => expect(view.result.current.loaded?.id).toBe('it-1'));
    expect(view.result.current.confirmed).toBe(true);
    expect(view.result.current.page).toMatchObject({ state: 'ready', coursesLoading: true });

    await act(async () => { arrive(LIST); });
    await waitFor(() => expect(view.result.current.courses).toHaveLength(3));
    expect(view.result.current.page).toMatchObject({ state: 'ready', coursesLoading: false, full: true });
    // 연 일정(it-1)은 코스 B 다 — 그 칸이 켜지고 확정으로 보인다
    expect(view.result.current.courseIndex).toBe(1);
    expect(view.result.current.confirmed).toBe(true);
    expect(view.result.current.loaded?.id).toBe('it-1');
    expect(loadItinerary).toHaveBeenCalledTimes(1);
  });

  it('코스 목록을 못 받아도 연 일정 하나로 그린다 — 「세 코스 비교는 준비 중」', async () => {
    loadTripCourses.mockResolvedValue({ state: 'empty', message: 'x' });
    auth.accessToken = 'token-1';
    auth.ready = true;
    const view = renderHook(() => useTripPage(source), { wrapper });
    await waitFor(() => expect(view.result.current.page).toMatchObject({ state: 'ready', coursesLoading: false, full: false }));
    expect(view.result.current.courses.map((entry) => entry.itineraryId)).toEqual(['it-1']);
    expect(view.result.current.confirmed).toBe(true);
    // 목록이 비었다고 연 일정을 다시 받지 않는다 — 이미 손에 있다
    expect(loadTripCourses).toHaveBeenCalledWith('trip-1', null, 'token-1');
    expect(loadItinerary).toHaveBeenCalledTimes(1);
  });

  it('🔴 코스를 확정하면 여행 목록 캐시를 비운다 — 돌아가 카드를 눌렀을 때 새로 고른 일정이 열리게 (S15P21E201-1605)', async () => {
    auth.accessToken = 'token-1';
    auth.ready = true;
    const view = renderHook(() => useTripPage({ kind: 'trip', tripId: 'trip-1', jobId: null }), { wrapper });
    await waitFor(() => expect(view.result.current.courses).toHaveLength(3));
    invalidateTripLists.mockClear();
    await act(async () => { await view.result.current.confirm(view.result.current.courses[1]); });
    expect(invalidateTripLists).toHaveBeenCalledTimes(1);
  });

  it('🔴 2·3안(미리보기)을 보다 1안으로 돌아와도 미리보기 번호로 pace 를 부르지 않는다 (S15P21E201-1641)', async () => {
    // 한 번의 그리기 동안 「고른 코스」는 1안인데 「불러온 일정」은 아직 미리보기다 — 그 틈에 미리보기 번호로 불렀다(운영 500 10건).
    const { loadItineraryPace } = jest.requireMock('@/plan/itinerary') as { loadItineraryPace: jest.Mock };
    const preview = { ...ITINERARY, id: 'job-1:1' } as ItineraryDto;
    const withPreview: TripCoursesResult = { state: 'success', full: true, courses: [course('A', 'it-1'), { ...course('B', null), preview }] };
    loadTripCourses.mockResolvedValue(withPreview);
    auth.accessToken = 'token-1';
    auth.ready = true;
    const view = renderHook(() => useTripPage(source), { wrapper });
    await waitFor(() => expect(view.result.current.courses).toHaveLength(2));
    await act(async () => { view.result.current.setCourseIndex(1); });
    await waitFor(() => expect(view.result.current.loaded?.id).toBe('job-1:1'));
    await act(async () => { view.result.current.setCourseIndex(0); });
    await waitFor(() => expect(view.result.current.loaded?.id).toBe('it-1'));
    const asked = loadItineraryPace.mock.calls.map(([id]) => id);
    expect(asked).not.toContain('job-1:1');
    expect(asked).toContain('it-1');
  });
});
