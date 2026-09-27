import { fireEvent, render, screen, waitFor } from '@testing-library/react-native';

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

const item = (sequence: number, placeName: string, category: string | null) => ({
  sequence, placeName, category, startsAt: `2026-10-03T${String(9 + sequence).padStart(2, '0')}:00:00+09:00`, endsAt: null, stayMinutes: 60,
});
const DATA = {
  title: '부산 가을 바다 2박 3일', startDate: '2026-10-03', finishDate: '2026-10-05', expiresAt: '2026-10-25T00:00:00+09:00',
  notShared: ['origin', 'contact', 'budget', 'partySize'],
  days: [{ date: '2026-10-03', items: [item(1, '해운대해수욕장', 'SEA_BEACH'), item(2, '감천문화마을', 'CULTURE_TEMPLE'), item(3, '어딘가', 'SOMETHING_NEW')] }],
} as unknown as SharedItineraryDto;

jest.setTimeout(20000);

beforeEach(() => getSharedItinerary.mockResolvedValue({ state: 'success', data: DATA }));


describe('공유 링크 화면의 언어 전환 — S15P21E201-1777', () => {
  it('🔴 로그인 없이도 이 화면에서 바로 English 로 바꾼다', async () => {
    render(<OnboardingPreferencesProvider><SharedItinerary /></OnboardingPreferencesProvider>);
    await waitFor(() => expect(screen.getByText('부산 가을 바다 2박 3일')).toBeTruthy(), { timeout: 10000 });
    expect(screen.getByText('공유된 여행 일정')).toBeTruthy();
    fireEvent.press(screen.getByLabelText('English'));
    await waitFor(() => expect(screen.getByText('Shared trip itinerary')).toBeTruthy());
    // 영어 이름이 없는 장소는 한글 옆에 읽는 법을 붙인다 — 영어 화면에 한글만 남지 않게.
    expect(screen.getByText(/해운대해수욕장 \(/)).toBeTruthy();
  });
});
