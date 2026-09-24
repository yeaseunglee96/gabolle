// 일정 완성 화면의 「일정 보기 →」가 여는 주소 — S15P21E201-1559.
//
// 🔴 2026-09-24 build 43 실기기 재확인 중 실제로 재현됐다. 이 화면은 jobId 만 라우트
//    파라미터로 받고 tripId 는 못 받는다 — 트립 ID는 loadRecommendationResult() 응답
//    (recommendation.tripId)에만 있는데, 그 결과가 로컬 변수로만 쓰이고 상태로 안 남아서
//    버튼 핸들러가 대신 job.jobId(작업 ID)를 넣어 보냈다. 서버는 그 값으로
//    GET /api/v1/trips/{jobId}/recommendations 를 받아 항상 404 였다(운영 nginx 로그로 확인).
//
// 이 시험은 TripPass를 얕게 모킹해 onOpenItinerary 콜백을 직접 집어 눌러서, 실제 뒤집기
// 애니메이션 없이 "그 순간 어느 주소로 가려 하는가"만 잰다.
import { fireEvent, render, waitFor } from '@testing-library/react-native';

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

const mockReplace = jest.fn();
const mockPoll = jest.fn();
const mockLoadRecommendationResult = jest.fn();
let capturedOnOpenItinerary: (() => void) | undefined;

jest.mock('expo-router', () => ({
  useRouter: () => ({ replace: mockReplace, push: jest.fn(), back: jest.fn(), canGoBack: () => false }),
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
  return {
    ...actual,
    createRecommendationJobAdapter: () => ({ poll: mockPoll, submit: jest.fn() }),
  };
});
jest.mock('@/plan/recommendationJobStream', () => ({
  supportsJobProgressStream: () => false,
  openJobProgressStream: jest.fn(),
}));
jest.mock('@/plan/recommendations', () => ({
  loadRecommendationResult: (...args: unknown[]) => mockLoadRecommendationResult(...args),
}));
jest.mock('@/onboarding/firstRun', () => ({ markChecklistStep: jest.fn() }));
// TripPass는 얕게 모킹한다 — 실제 뒤집기 애니메이션을 흉내 내지 않고 onOpenItinerary만 집는다.
// 이 화면은 wide/phone 둘 다에서 TripPass를 두 번 쓰는데(그림 322·329행), 두 자리 다 같은
// onOpenItinerary 계산식을 쓰므로 어느 쪽이 렌더되든 상관없다(kind:'phone'이라 phone 쪽이 렌더된다).
jest.mock('@/plan/TripPass', () => {
  const { Pressable, Text } = jest.requireActual('react-native');
  return {
    TripPass: ({ onOpenItinerary }: { onOpenItinerary?: () => void }) => {
      capturedOnOpenItinerary = onOpenItinerary;
      return onOpenItinerary ? (
        <Pressable accessibilityRole="button" onPress={onOpenItinerary}>
          <Text>일정 보기 →</Text>
        </Pressable>
      ) : null;
    },
  };
});

import Generating from '../(plan)/generating';

beforeEach(() => {
  jest.clearAllMocks();
  capturedOnOpenItinerary = undefined;
  // 승차권 출력 애니메이션(Animated.sequence, 약 2.5초)을 끈다 — reduceMotion=true 로 즉시
  // 끝내서, 언마운트 뒤에도 남는 Animated 내부 타이머가 "환경이 정리된 뒤" 콘솔 오류를
  // 흩뿌리는 것을 막는다. 이 시험이 재는 것은 그 애니메이션이 아니라 onOpenItinerary가 여는 주소다.
  jest.spyOn(AccessibilityInfo, 'isReduceMotionEnabled').mockResolvedValue(true);
  jest.spyOn(AccessibilityInfo, 'addEventListener').mockReturnValue({ remove: jest.fn() } as never);
  mockPoll.mockResolvedValue({
    state: 'completed', jobId: 'job-abc-123', progress: 100, stage: null,
    canCancel: false, errorMessage: null, resultRef: null,
  });
  mockLoadRecommendationResult.mockResolvedValue({
    state: 'success', courses: [], conflicts: [], message: '', itineraryId: null,
    placeCount: null, estimatedTravelMinutes: null, tripId: 'trip-real-999',
  });
});

describe('일정 완성 화면 — 「일정 보기」가 여는 주소 (S15P21E201-1559)', () => {
  it('트립 ID(recommendation.tripId)로 이동한다 — 작업 ID(jobId)가 아니다', async () => {
    const view = render(<Providers><Generating /></Providers>);

    // 첫 폴링은 2초 뒤에 돈다(generating.tsx 의 폴링 간격) — 실제 타이머로 그만큼 기다린다.
    // 🔴 CI 러너는 로컬보다 느려서 jest 기본 5초 제한을 이 대기만으로 넘겼다(2026-09-24
    // 파이프라인 #220580 실패) — waitFor 자체의 timeout 이 아니라 it() 전체의 제한이라,
    // 그 셋째 인자를 넉넉히 올려야 한다.
    await waitFor(() => expect(capturedOnOpenItinerary).toBeDefined(), { timeout: 3000 });
    fireEvent.press(view.getByText('일정 보기 →'));

    expect(mockReplace).toHaveBeenCalledWith('/trips/trip-real-999/recommendations?jobId=job-abc-123');
    expect(mockReplace).not.toHaveBeenCalledWith(expect.stringContaining('/trips/job-abc-123/'));
    // 폴링 루프의 10초짜리 지연 타이머가 남아 있으면 이 테스트 파일이 끝난 뒤에도 불려
    // "환경이 이미 정리됐다" 오류가 난다 — 언마운트로 effect cleanup을 확실히 태운다.
    view.unmount();
  }, 10000);

  it('트립 ID를 아직 못 받았으면(로딩 중) 버튼 자체가 없다 — 작업 ID로 대신 채우지 않는다', async () => {
    let resolveLoad: (value: unknown) => void = () => {};
    mockLoadRecommendationResult.mockReturnValue(new Promise((resolve) => { resolveLoad = resolve; }));

    const view = render(<Providers><Generating /></Providers>);
    await waitFor(() => expect(mockPoll).toHaveBeenCalled(), { timeout: 3000 });

    expect(view.queryByText('일정 보기 →')).toBeNull();

    resolveLoad({ state: 'success', courses: [], conflicts: [], message: '', itineraryId: null, placeCount: null, estimatedTravelMinutes: null, tripId: 'trip-real-999' });
    await waitFor(() => expect(view.getByText('일정 보기 →')).toBeTruthy());
    view.unmount();
  }, 10000);
});
