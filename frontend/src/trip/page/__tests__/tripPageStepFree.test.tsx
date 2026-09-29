// 여행 페이지 지도 — 휠체어·유아차·계단 피하기 여행의 걷는 길.
//
// 🔴 이 시험이 지키는 것: 일정 응답의 stepFree 가 true 일 때만 지도의 구간 요청이 계단을 피하는 길로 묻는다.
//    옛 서버(칸 없음)와 보통 여행은 전처럼 보통 길이다.
import type { ReactNode } from 'react';
import { renderHook, waitFor } from '@testing-library/react-native';
import { QueryClient, QueryClientProvider } from '@tanstack/react-query';

import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';
import type { ItineraryDto } from '@/plan/itinerary';
import { useTripPage } from '@/trip/page/useTripPage';

const auth = { accessToken: 'token-1' as string | null, ready: true };
jest.mock('expo-router', () => ({ useRouter: () => ({ push: jest.fn() }) }));
jest.mock('@/auth/AuthProvider', () => ({ useAuth: () => auth }));
jest.mock('@/plan/itinerary', () => ({
  ...jest.requireActual('@/plan/itinerary'),
  loadItinerary: jest.fn(),
  loadItineraryPace: jest.fn(async () => ({ state: 'error', message: 'x' })),
}));
jest.mock('@/plan/tripCourses', () => ({ ...jest.requireActual('@/plan/tripCourses'), loadTripCourses: jest.fn(async () => ({ state: 'empty', message: 'x' })) }));
jest.mock('@/plan/placePhotos', () => ({ ...jest.requireActual('@/plan/placePhotos'), loadPlacePhotos: jest.fn(async () => ({})) }));
jest.mock('@/trip/tripBudget', () => ({ loadTripBudget: jest.fn(async () => ({ state: 'error', message: 'x' })) }));
jest.mock('@/map/courseRoutePaths', () => ({ ...jest.requireActual('@/map/courseRoutePaths'), useCourseRoutePaths: jest.fn(() => ({})) }));
jest.mock('@/trip/trips', () => ({ ...jest.requireActual('@/trip/trips'), invalidateTripLists: jest.fn(async () => {}), loadTrips: jest.fn(async () => ({ state: 'success', trips: [] })) }));

const { loadItinerary } = jest.requireMock('@/plan/itinerary') as { loadItinerary: jest.Mock };
const { useCourseRoutePaths } = jest.requireMock('@/map/courseRoutePaths') as { useCourseRoutePaths: jest.Mock };

const item = (id: string, lat: number, lng: number, extra: object = {}) => ({ id, startsAt: '2026-10-03T10:00:00', title: id, locked: false, placeId: `p-${id}`, lat, lng, ...extra });
const TWO_STOPS = [item('i1', 35.1636, 129.1588), item('i2', 35.1587, 129.1604, { walkingMeters: 600 })];
const itinerary = (extra: Partial<ItineraryDto>): ItineraryDto => ({
  id: 'it-1', tripId: 'trip-1', title: '해운대', version: 1, days: [{ date: '2026-10-03', items: TWO_STOPS }], ...extra,
} as ItineraryDto);

const queryClient = new QueryClient({ defaultOptions: { queries: { gcTime: Infinity } } });
const wrapper = ({ children }: { children: ReactNode }) => <QueryClientProvider client={queryClient}><OnboardingPreferencesProvider>{children}</OnboardingPreferencesProvider></QueryClientProvider>;
const source = { kind: 'itinerary' as const, itineraryId: 'it-1' };
const lastOptions = () => useCourseRoutePaths.mock.calls[useCourseRoutePaths.mock.calls.length - 1][2];

beforeEach(() => { useCourseRoutePaths.mockClear(); loadItinerary.mockReset(); });

describe('여행 페이지 지도 — stepFree', () => {
  it('🔴 일정이 stepFree 면 구간 길을 계단을 피하는 길로 묻는다', async () => {
    loadItinerary.mockResolvedValue({ state: 'success', itinerary: itinerary({ stepFree: true }) });
    const view = renderHook(() => useTripPage(source), { wrapper });
    await waitFor(() => expect(view.result.current.loaded?.id).toBe('it-1'));
    await waitFor(() => expect(lastOptions().stepFree).toBe(true));
  });

  it('🔴 stepFree 가 false 이거나 칸이 없으면(옛 서버) 보통 길로 묻는다', async () => {
    loadItinerary.mockResolvedValue({ state: 'success', itinerary: itinerary({ stepFree: false }) });
    const off = renderHook(() => useTripPage(source), { wrapper });
    await waitFor(() => expect(off.result.current.loaded?.id).toBe('it-1'));
    expect(lastOptions().stepFree).toBe(false);
    off.unmount();

    loadItinerary.mockResolvedValue({ state: 'success', itinerary: itinerary({}) });
    const old = renderHook(() => useTripPage(source), { wrapper });
    await waitFor(() => expect(old.result.current.loaded?.id).toBe('it-1'));
    expect(lastOptions().stepFree).toBe(false);
  });
});
