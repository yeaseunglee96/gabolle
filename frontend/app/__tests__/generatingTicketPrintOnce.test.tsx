// 일정 생성 화면은 일정을 받아 온 뒤에만 티켓을 출력시킨다 — S15P21E201-1577.
//
// 🔴 완성 순간에는 아직 일정이 없다. 추천 결과 → 일정, 두 번을 더 받아 와야 티켓 코드(GB-…)가 생긴다.
//    전에는 그 사이에 빈 티켓을 한 번 출력하고, 일정이 오면 또 출력해 영수증이 두 번 나왔다.
//    이 시험은 TripPass 를 얕게 모킹해 「출력해도 된다(ready)」가 언제 켜지는지만 잰다.
//    한 번 나오는지 자체는 src/plan/__tests__/tripPassPrintOnce.test.tsx 가 잰다.
import { render, waitFor } from '@testing-library/react-native';

import type { ReactNode } from 'react';
import { AccessibilityInfo } from 'react-native';
import { SafeAreaProvider, initialWindowMetrics } from 'react-native-safe-area-context';

import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';

const Providers = ({ children }: { children: ReactNode }) => (
  <SafeAreaProvider initialMetrics={initialWindowMetrics ?? {
    frame: { x: 0, y: 0, width: 390, height: 844 },
    insets: { top: 47, left: 0, right: 0, bottom: 34 },
  }}>
    <OnboardingPreferencesProvider>{children}</OnboardingPreferencesProvider>
  </SafeAreaProvider>
);

const mockPoll = jest.fn();
const mockLoadRecommendationResult = jest.fn();
const mockLoadItinerary = jest.fn();
/** 티켓이 그려질 때마다 받은 (ready, 코드) — 출력 허락이 언제 켜졌고 그때 무엇이 찍혀 있었나. */
const renders: Array<{ ready: boolean; code: string }> = [];

jest.mock('expo-router', () => ({
  useRouter: () => ({ replace: jest.fn(), push: jest.fn(), back: jest.fn(), canGoBack: () => false }),
  useLocalSearchParams: () => ({ jobId: 'job-abc-123' }),
  usePathname: () => '/plan/generating',
}));
jest.mock('@/auth/AuthProvider', () => ({ useAuth: () => ({ user: { userId: 'me' }, accessToken: 'token' }) }));
jest.mock('@/layout/useLayout', () => ({ useLayout: () => ({ kind: 'phone', width: 390, height: 844, isLandscape: false }) }));
jest.mock('@/plan/PlanProvider', () => ({
  usePlan: () => ({
    draft: { startDate: '2026-10-03', endDate: '2026-10-05', origin: '부산역', travelAreas: ['HAEUNDAE'] },
    clear: jest.fn(),
  }),
}));
jest.mock('@/plan/recommendationJob', () => {
  const actual = jest.requireActual('@/plan/recommendationJob');
  return { ...actual, createRecommendationJobAdapter: () => ({ poll: mockPoll, submit: jest.fn() }) };
});
jest.mock('@/plan/recommendationJobStream', () => ({
  supportsJobProgressStream: () => false,
  openJobProgressStream: jest.fn(),
}));
jest.mock('@/plan/recommendations', () => ({
  loadRecommendationResult: (...args: unknown[]) => mockLoadRecommendationResult(...args),
}));
jest.mock('@/plan/itinerary', () => ({
  loadItinerary: (...args: unknown[]) => mockLoadItinerary(...args),
}));
jest.mock('@/plan/placePhotos', () => ({ loadPlacePhotos: () => Promise.resolve({}) }));
jest.mock('@/onboarding/firstRun', () => ({ markChecklistStep: jest.fn() }));
jest.mock('@/plan/TripPass', () => {
  const { Text } = jest.requireActual('react-native');
  return {
    TripPass: ({ ready, data }: { ready?: boolean; data: { code: string } }) => {
      renders.push({ ready: ready !== false, code: data.code });
      return <Text>{ready === false ? '출력 대기' : `출력:${data.code}`}</Text>;
    },
  };
});

import Generating from '../(plan)/generating';

const ITINERARY = {
  id: 'abc123def', title: '부산', version: 1, totalEstimatedCostKrw: null, totalWalkingMeters: null, fallbackMode: 'MODEL',
  days: [{ date: '2026-10-03', items: [{ id: 'i1', startsAt: '2026-10-03T09:30:00', title: '해운대', locked: false, placeId: 'p1' }] }],
};

beforeEach(() => {
  jest.clearAllMocks();
  renders.length = 0;
  jest.spyOn(AccessibilityInfo, 'isReduceMotionEnabled').mockResolvedValue(true);
  jest.spyOn(AccessibilityInfo, 'addEventListener').mockReturnValue({ remove: jest.fn() } as never);
  mockPoll.mockResolvedValue({
    state: 'completed', jobId: 'job-abc-123', progress: 100, stage: null,
    canCancel: false, errorMessage: null, resultRef: null,
  });
  mockLoadRecommendationResult.mockResolvedValue({
    state: 'success', courses: [], conflicts: [], message: '', itineraryId: 'abc123def',
    placeCount: null, estimatedTravelMinutes: null, tripId: 'trip-1',
  });
});

describe('일정 생성 화면 — 티켓은 일정을 받아 온 뒤 한 번 출력된다 (S15P21E201-1577)', () => {
  it('🔴 일정이 오기 전에는 출력하지 않는다 — 코드가 빈 티켓을 먼저 내보내지 않는다', async () => {
    let resolveItinerary: (value: unknown) => void = () => {};
    mockLoadItinerary.mockReturnValue(new Promise((resolve) => { resolveItinerary = resolve; }));

    const view = render(<Providers><Generating /></Providers>);
    // 첫 폴링은 2초 뒤에 돈다 — 완성되고 결과를 받을 때까지 기다린다.
    await waitFor(() => expect(mockLoadItinerary).toHaveBeenCalled(), { timeout: 3000 });
    expect(view.getByText('출력 대기')).toBeTruthy();

    resolveItinerary({ state: 'success', itinerary: ITINERARY });
    await waitFor(() => expect(view.getByText('출력:GB-ABC123')).toBeTruthy());

    // 출력 허락이 켜진 순간부터는 언제나 진짜 코드가 찍혀 있다 — 빈 티켓이 출력된 적이 없다.
    expect(renders.filter((r) => r.ready).every((r) => r.code === 'GB-ABC123')).toBe(true);
    view.unmount();
  }, 10000);

  it('🔴 일정을 못 받아 와도 프린터가 멈춰 있지 않는다 — 가진 값으로 한 번 나온다', async () => {
    mockLoadItinerary.mockResolvedValue({ state: 'error', message: '일정을 불러오지 못했어요.' });

    const view = render(<Providers><Generating /></Providers>);
    await waitFor(() => expect(view.getByText(/^출력:/)).toBeTruthy(), { timeout: 3000 });
    view.unmount();
  }, 10000);
});
