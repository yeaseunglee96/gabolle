// 비밀번호 찾기 — 이메일 형식을 먼저 본다 (S15P21E201-1784).
import { fireEvent, render, screen } from '@testing-library/react-native';

import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';

const mockRequest = jest.fn(async (_email: string) => undefined);
jest.mock('@/auth/authApi', () => ({ requestPasswordReset: (email: string) => mockRequest(email) }));
jest.mock('expo-router', () => ({ useRouter: () => ({ back: jest.fn(), replace: jest.fn(), canGoBack: () => true }) }));
jest.mock('react-native-safe-area-context', () => ({
  SafeAreaView: ({ children }: { children: unknown }) => children,
  useSafeAreaInsets: () => ({ top: 0, left: 0, right: 0, bottom: 0 }),
}));

import ForgotPassword from '../(auth)/forgot-password';

const wrapper = ({ children }: { children: React.ReactNode }) => <OnboardingPreferencesProvider>{children}</OnboardingPreferencesProvider>;

beforeEach(() => mockRequest.mockClear());

it('🔴 이메일이 아닌 값이면 요청을 보내지 않고 형식 안내를 띄운다', () => {
  render(<ForgotPassword />, { wrapper });
  fireEvent.changeText(screen.getByLabelText('이메일'), 'bad');
  fireEvent.press(screen.getByText('재설정 링크 받기'));
  expect(mockRequest).not.toHaveBeenCalled();
  expect(screen.getByText('올바른 이메일 주소를 입력해 주세요.')).toBeTruthy();
});

it('올바른 이메일이면 그대로 요청한다', () => {
  render(<ForgotPassword />, { wrapper });
  fireEvent.changeText(screen.getByLabelText('이메일'), 'name@example.com');
  fireEvent.press(screen.getByText('재설정 링크 받기'));
  expect(mockRequest).toHaveBeenCalledWith('name@example.com');
});
