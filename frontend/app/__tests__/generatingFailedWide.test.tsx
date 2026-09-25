// 넓은 화면에서 일정 만들기가 실패했을 때 — S15P21E201-1669.
//
// 🔴 이 시험이 지키는 것: 실패했는데 오른쪽 아래에 빈 여행표(프린터만 있고 표는 안 나오는 칸)가 그대로 남았고,
//    제목 「일정을 만들지 / 못했어요」는 넓은 화면에서도 두 줄로 끊겼다. 폰은 실패하면 동백이 대기 화면만 보이고
//    여행표가 없다(S15P21E201-1415) — 넓은 화면도 실패하면 여행표를 그리지 않는다.
import { getDefaultNormalizer, render, waitFor } from '@testing-library/react-native';

import type { ReactNode } from 'react';
import { AccessibilityInfo } from 'react-native';
import { SafeAreaProvider, initialWindowMetrics } from 'react-native-safe-area-context';

import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';

const Providers = ({ children }: { children: ReactNode }) => (
  <SafeAreaProvider initialMetrics={initialWindowMetrics ?? {
    frame: { x: 0, y: 0, width: 1280, height: 900 },
    insets: { top: 0, left: 0, right: 0, bottom: 0 },
  }}>
    <OnboardingPreferencesProvider>{children}</OnboardingPreferencesProvider>
  </SafeAreaProvider>
);

const mockPoll = jest.fn();

jest.mock('expo-router', () => ({
  useRouter: () => ({ replace: jest.fn(), push: jest.fn(), back: jest.fn(), canGoBack: () => false }),
  useLocalSearchParams: () => ({ jobId: 'job-fail' }),
  usePathname: () => '/plan/generating',
}));
jest.mock('@/auth/AuthProvider', () => ({ useAuth: () => ({ user: { userId: 'me' }, accessToken: 'token' }) }));
jest.mock('@/layout/useLayout', () => ({ useLayout: () => ({ kind: 'tablet', desktop: true, width: 1280, height: 900, isLandscape: true }) }));
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
jest.mock('@/plan/recommendations', () => ({ loadRecommendationResult: jest.fn() }));
jest.mock('@/plan/itinerary', () => ({ loadItinerary: jest.fn() }));
jest.mock('@/plan/placePhotos', () => ({ loadPlacePhotos: () => Promise.resolve({}) }));
jest.mock('@/onboarding/firstRun', () => ({ markChecklistStep: jest.fn() }));
jest.mock('@/plan/TripPass', () => {
  const { Text } = jest.requireActual('react-native');
  return { TripPass: () => <Text>여행표</Text> };
});

import Generating from '../(plan)/generating';

beforeEach(() => {
  jest.clearAllMocks();
  jest.spyOn(AccessibilityInfo, 'isReduceMotionEnabled').mockResolvedValue(true);
  jest.spyOn(AccessibilityInfo, 'addEventListener').mockReturnValue({ remove: jest.fn() } as never);
  mockPoll.mockResolvedValue({
    state: 'failed', jobId: 'job-fail', progress: null, stage: null, canCancel: false,
    errorMessage: '조건에 맞는 장소를 찾지 못했어요.', resultRef: null,
  });
});

describe('넓은 화면 — 일정 만들기 실패', () => {
  it('🔴 실패하면 여행표를 그리지 않는다 — 빈 프린터만 남기지 않는다', async () => {
    const view = render(<Providers><Generating /></Providers>);
    // 만드는 중에는 넓은 화면에 여행표 칸이 있다(프린터가 기다린다).
    expect(view.getByText('여행표')).toBeTruthy();

    await waitFor(() => expect(view.getByText('조건에 맞는 장소를 찾지 못했어요.')).toBeTruthy(), { timeout: 4000 });
    expect(view.queryByText('여행표')).toBeNull();
    expect(view.queryByText('TRIP PASS')).toBeNull();
    view.unmount();
  }, 10000);

  it('제목은 한 줄이다 — 넓은 화면에서 「일정을 만들지 / 못했어요」로 끊지 않는다', async () => {
    const view = render(<Providers><Generating /></Providers>);
    // 🔴 기본 비교는 줄바꿈을 빈칸으로 접는다 — 접지 않고 글자 그대로 본다.
    const exact = { normalizer: getDefaultNormalizer({ trim: false, collapseWhitespace: false }) };
    await waitFor(() => expect(view.getByText('일정을 만들지 못했어요', exact)).toBeTruthy(), { timeout: 4000 });
    view.unmount();
  }, 10000);
});
