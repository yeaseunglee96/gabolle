// 당일 여행 공유 화면 — S15P21E201-1985.
//
// 🔴 폴드 점검(10/5): 이름 안 붙인 당일 여행을 공유하니 제목이 「2026-10-15 ~ 2026-10-15」(서버 자리표시 제목)였고,
//    바로 아래 줄이 날짜를 한 번 더 적었다. 이름이 없으면 날짜 하나로 부르고, 같은 날짜를 두 번 쓰지 않는다.
import { render, screen, waitFor } from '@testing-library/react-native';

import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';
import type { SharedItineraryDto } from '@/share/sharedItinerary';

jest.mock('expo-router', () => ({
  useRouter: () => ({ push: jest.fn(), replace: jest.fn(), back: jest.fn() }),
  useLocalSearchParams: () => ({ token: 'abc123' }),
}));
jest.mock('react-native-safe-area-context', () => ({
  SafeAreaView: ({ children }: { children: unknown }) => children,
  useSafeAreaInsets: () => ({ top: 0, left: 0, right: 0, bottom: 0 }),
}));
jest.mock('@/plan/PlanProvider', () => ({ usePlan: () => ({ update: jest.fn(), clear: jest.fn(async () => {}) }) }));
jest.mock('@/share/sharedItinerary', () => ({ getSharedItinerary: jest.fn() }));
const { getSharedItinerary } = jest.requireMock('@/share/sharedItinerary') as { getSharedItinerary: jest.Mock };

import SharedItinerary from '../s/[token]';

const DATA = {
  title: '2026-10-15 ~ 2026-10-15', startDate: '2026-10-15', finishDate: '2026-10-15', expiresAt: '2026-11-04T00:00:00+09:00',
  notShared: ['origin'],
  days: [{ date: '2026-10-15', items: [] }],
} as unknown as SharedItineraryDto;

jest.setTimeout(20000);

describe('당일 여행 공유 제목', () => {
  it('🔴 자리표시 제목은 날짜 하나로 부르고, 같은 날짜를 또 적지 않는다', async () => {
    getSharedItinerary.mockResolvedValue({ state: 'success', data: DATA });
    render(<OnboardingPreferencesProvider><SharedItinerary /></OnboardingPreferencesProvider>);
    await waitFor(() => expect(screen.getByText('공유된 여행 일정')).toBeTruthy(), { timeout: 10000 });
    expect(screen.queryByText(/2026-10-15 ~ 2026-10-15/)).toBeNull();
    expect(screen.queryByText(/–/)).toBeNull();
    // 제목 자리에 「10월 15일 (목)」 한 번, 날짜 줄은 따로 그리지 않는다(일차 머리 「1일차 · 10월 15일 (목)」 은 별개).
    expect(screen.getAllByText('10월 15일 (목)')).toHaveLength(1);
  });
});
