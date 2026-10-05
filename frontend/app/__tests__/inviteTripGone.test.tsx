// 지운 여행의 초대 링크 — S15P21E201-1985.
//
// 🔴 폴드 점검(10/5): 지운 여행의 동행 초대 링크를 열면 아무 안내 없이 「내 여행」 목록으로 갔다.
//    받은 사람은 링크가 됐는지 안 됐는지 모른다. 공유 링크처럼 「찾을 수 없어요」를 말한다.
import { render, screen, waitFor } from '@testing-library/react-native';

import { ApiClientError } from '@/api/client';
import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';

const mockReplace = jest.fn();
jest.mock('expo-router', () => ({
  useRouter: () => ({ push: jest.fn(), replace: mockReplace, back: jest.fn() }),
  useLocalSearchParams: () => ({ token: 'tok123' }),
}));
jest.mock('react-native-safe-area-context', () => ({
  SafeAreaView: ({ children }: { children: unknown }) => children,
  useSafeAreaInsets: () => ({ top: 0, left: 0, right: 0, bottom: 0 }),
}));
jest.mock('@/auth/AuthProvider', () => ({ useAuth: () => ({ accessToken: 'at', user: { id: 'u1' }, ready: true }) }));
jest.mock('@/trip/collaboration', () => ({ acceptTripInvite: jest.fn() }));
jest.mock('@/trip/trips', () => ({ loadTripItineraries: jest.fn() }));
const { acceptTripInvite } = jest.requireMock('@/trip/collaboration') as { acceptTripInvite: jest.Mock };
const { loadTripItineraries } = jest.requireMock('@/trip/trips') as { loadTripItineraries: jest.Mock };

import AcceptInvite from '../invite/[token]';

jest.setTimeout(20000);
beforeEach(() => { mockReplace.mockClear(); });

describe('지운 여행의 초대 링크', () => {
  it('🔴 초대는 받아졌는데 여행이 없으면(404) 목록으로 보내지 않고 안내한다', async () => {
    acceptTripInvite.mockResolvedValue({ tripId: 't1', role: 'OWNER', alreadyMember: true, joinedAt: '' });
    loadTripItineraries.mockResolvedValue({ state: 'unavailable', message: 'x' });
    render(<OnboardingPreferencesProvider><AcceptInvite /></OnboardingPreferencesProvider>);
    await waitFor(() => expect(screen.getByText('이 초대로 갈 수 있는 여행이 없어요')).toBeTruthy(), { timeout: 10000 });
    expect(mockReplace).not.toHaveBeenCalledWith('/trips');
    expect(screen.getByText('내 여행으로')).toBeTruthy();
  });

  it('🔴 서버가 초대를 못 찾으면(TRIP_INVITE_NOT_FOUND) 같은 안내', async () => {
    acceptTripInvite.mockRejectedValue(new ApiClientError('not found', 'TRIP_INVITE_NOT_FOUND', 404));
    render(<OnboardingPreferencesProvider><AcceptInvite /></OnboardingPreferencesProvider>);
    await waitFor(() => expect(screen.getByText('이 초대로 갈 수 있는 여행이 없어요')).toBeTruthy(), { timeout: 10000 });
  });
});
