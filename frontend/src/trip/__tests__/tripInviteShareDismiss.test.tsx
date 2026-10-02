// 웹에서 초대 링크를 만든 뒤 공유 창을 닫으면 「초대 링크를 만들지 못했습니다」가 성공 카드와 같이 떴다(S15P21E201-1958).
import { fireEvent, render, screen, waitFor } from '@testing-library/react-native';
import { Platform } from 'react-native';

import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';
import { TripInvitePanel } from '@/trip/TripInvitePanel';

jest.mock('expo-router', () => ({ useRouter: () => ({ push: jest.fn() }) }));
jest.mock('@/auth/AuthProvider', () => ({ useAuth: () => ({ accessToken: 'token', ready: true }) }));
jest.mock('@/onboarding/firstRun', () => ({ markChecklistStep: jest.fn() }));
jest.mock('@/trip/TripReadLinkPanel', () => ({ TripReadLinkPanel: () => null }));
jest.mock('@/trip/collaboration', () => ({
  createCompanionInvite: jest.fn().mockResolvedValue({ inviteUrl: 'https://x/invite/abc', expiresAt: '2026-10-09T00:00:00Z' }),
}));

// CI 의 Node 는 navigator 가 없다(로컬 Node 24 는 있다) — 시험용으로 하나 둔다.
if (!(globalThis as { navigator?: unknown }).navigator) Object.defineProperty(globalThis, 'navigator', { value: {}, configurable: true, writable: true });
const nav = globalThis.navigator as unknown as Record<string, unknown>;
const realOS = Platform.OS;
const realShare = nav.share;
const realClipboard = nav.clipboard;

beforeEach(() => Object.defineProperty(Platform, 'OS', { value: 'web', configurable: true }));
afterEach(() => {
  Object.defineProperty(Platform, 'OS', { value: realOS, configurable: true });
  nav.share = realShare;
  Object.defineProperty(nav, 'clipboard', { value: realClipboard, configurable: true });
});

it('웹에서 공유 창을 닫아도(AbortError) 실패 카드를 띄우지 않는다', async () => {
  nav.share = jest.fn().mockRejectedValue(Object.assign(new Error('Share canceled'), { name: 'AbortError' }));
  render(<OnboardingPreferencesProvider><TripInvitePanel tripId="t1" /></OnboardingPreferencesProvider>);
  fireEvent.press(screen.getByText(/초대 링크 만들기/));
  await waitFor(() => expect(screen.getByText('초대 링크를 만들었어요')).toBeTruthy());
  expect(screen.queryByText('초대 링크를 만들지 못했습니다')).toBeNull();
});

it('공유가 없는 브라우저는 링크를 복사하고 그렇게 알린다', async () => {
  nav.share = undefined;
  const writeText = jest.fn().mockResolvedValue(undefined);
  Object.defineProperty(nav, 'clipboard', { value: { writeText }, configurable: true });
  render(<OnboardingPreferencesProvider><TripInvitePanel tripId="t1" /></OnboardingPreferencesProvider>);
  fireEvent.press(screen.getByText(/초대 링크 만들기/));
  await waitFor(() => expect(screen.getByTestId('trip-invite-share-notice')).toBeTruthy());
  expect(writeText).toHaveBeenCalledWith('https://x/invite/abc');
  expect(screen.queryByText('초대 링크를 만들지 못했습니다')).toBeNull();
});
