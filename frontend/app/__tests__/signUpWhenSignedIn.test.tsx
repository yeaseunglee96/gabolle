// 로그인한 사람에게 가입 화면을 보여주지 않는다 — S15P21E201-1793.
//
// 🔴 /sign-in 은 로그인한 사람을 비켜 보냈는데(S15P21E201-1199 · 1594) /sign-up 은 양식을 그대로 띄웠다.
//    같은 규칙: returnTo 가 안전하면 거기로, 아니면 홈으로. 로그인 복구가 늦게 끝나도 비킨다.
import { render, waitFor } from '@testing-library/react-native';

import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';

const mockReplace = jest.fn();
const mockAuth = { user: null as unknown, ready: true };
let mockParams: { returnTo?: string } = {};

jest.mock('expo-router', () => ({
  useRouter: () => ({ back: jest.fn(), replace: mockReplace, push: jest.fn(), canGoBack: () => true, canDismiss: () => false, dismissAll: () => {} }),
  useLocalSearchParams: () => mockParams,
}));
jest.mock('react-native-reanimated', () => {
  const { View } = require('react-native');
  const chain = { duration: () => chain, reduceMotion: () => chain };
  return { __esModule: true, default: { View }, FadeInRight: chain, FadeOutLeft: chain, ReduceMotion: { System: 'system' } };
});
jest.mock('@/auth/AuthProvider', () => ({ useAuth: () => mockAuth }));
jest.mock('@/auth/authApi', () => ({ signup: jest.fn(), resendEmailVerification: jest.fn() }));
jest.mock('@/auth/pendingReturnTo', () => ({
  // 목적지 규칙(signedInDestination)은 진짜를 쓴다 — 로그인 화면과 같은 규칙인지가 이 시험의 대상이다.
  ...jest.requireActual('@/auth/pendingReturnTo'),
  savePendingReturnTo: jest.fn(async () => {}),
}));
jest.mock('@/components/BrandLogoLink', () => ({ BrandLogoLink: () => null }));
jest.mock('@/layout/useLayout', () => ({ useLayout: () => ({ kind: 'phone', width: 390, height: 844, isLandscape: false }) }));
jest.mock('react-native-safe-area-context', () => ({
  SafeAreaView: ({ children }: { children: unknown }) => children,
  useSafeAreaInsets: () => ({ top: 47, left: 0, right: 0, bottom: 34 }),
}));

import SignUp from '../(auth)/sign-up';

const renderScreen = () => render(<OnboardingPreferencesProvider><SignUp /></OnboardingPreferencesProvider>);

beforeEach(() => {
  jest.clearAllMocks();
  mockAuth.user = null;
  mockAuth.ready = true;
  mockParams = {};
});

describe('가입 화면 — 이미 로그인한 사람은 붙잡지 않는다', () => {
  it('🔴 로그인한 채로 오면 홈으로 비킨다', async () => {
    mockAuth.user = { userId: 'u1', displayName: '이예승' };
    renderScreen();
    await waitFor(() => expect(mockReplace).toHaveBeenCalledWith('/home'));
  });

  it('안전한 returnTo 가 있으면 거기로 간다 — 로그인 화면과 같은 규칙', async () => {
    mockAuth.user = { userId: 'u1', displayName: '이예승' };
    mockParams = { returnTo: '/trips' };
    renderScreen();
    await waitFor(() => expect(mockReplace).toHaveBeenCalledWith('/trips'));
  });

  it('로그인 복구가 화면보다 늦게 끝나도 비킨다', async () => {
    mockAuth.ready = false;
    const screen = renderScreen();
    expect(mockReplace).not.toHaveBeenCalled();
    mockAuth.ready = true;
    mockAuth.user = { userId: 'u1', displayName: '이예승' };
    screen.rerender(<OnboardingPreferencesProvider><SignUp /></OnboardingPreferencesProvider>);
    await waitFor(() => expect(mockReplace).toHaveBeenCalledWith('/home'));
  });

  it('로그인 안 한 사람은 그대로 가입 양식을 본다', async () => {
    const screen = renderScreen();
    await waitFor(() => expect(screen.queryAllByText('회원가입').length + screen.queryAllByText('이메일').length).toBeGreaterThan(0));
    expect(mockReplace).not.toHaveBeenCalled();
  });
});
