// — 재현 시나리오를 그대로 옮긴 회귀 시험.
import { fireEvent, render, waitFor } from '@testing-library/react-native';
import { Pressable, Text } from 'react-native';
import { AuthProvider, useAuth } from '../AuthProvider';
import { OnboardingPreferencesProvider, useOnboardingPreferences } from '@/onboarding/OnboardingPreferences';

const mockRouter = { replace: jest.fn(), push: jest.fn() };
jest.mock('expo-router', () => ({ useRouter: () => mockRouter }));

// SecureStore는 네이티브 모듈이다 — Jest 환경에는 브릿지가 없어 직접 부르면 죽는다.
// 이 시험은 토큰 저장 자체를 보는 게 아니라 언어 보존을 보는 것이라 빈 저장소로 충분하다.
jest.mock('expo-secure-store', () => ({
  getItemAsync: jest.fn().mockResolvedValue(null),
  setItemAsync: jest.fn().mockResolvedValue(undefined),
  deleteItemAsync: jest.fn().mockResolvedValue(undefined),
}));

const mockLogin = jest.fn();
const mockGetMe = jest.fn();
jest.mock('../authApi', () => ({
  ...jest.requireActual('../authApi'),
  login: (...args: unknown[]) => mockLogin(...args),
  getMe: (...args: unknown[]) => mockGetMe(...args),
}));

function Probe() {
  const { language, setLanguage } = useOnboardingPreferences();
  const { signIn } = useAuth();
  return (
    <>
      <Text testID="language">{language}</Text>
      <Pressable testID="pick-japanese" onPress={() => setLanguage('ja')}><Text>일본어</Text></Pressable>
      <Pressable testID="sign-in" onPress={() => void signIn('user@example.com', 'password')}><Text>로그인</Text></Pressable>
    </>
  );
}

it('로그인 전에 고른 화면 언어(일본어)를, 계정의 KO 값이 로그인 후 덮어쓰지 않는다', async () => {
  mockLogin.mockResolvedValue({ accessToken: 'token', refreshToken: null, expiresIn: 3600, sessionId: 's1', user: { userId: 'u1', email: 'user@example.com', displayName: '테스트', language: 'KO', status: 'ACTIVE' } });
  mockGetMe.mockResolvedValue({ userId: 'u1', email: 'user@example.com', displayName: '테스트', language: 'KO', status: 'ACTIVE' });

  const view = render(
    <OnboardingPreferencesProvider>
      <AuthProvider>
        <Probe />
      </AuthProvider>
    </OnboardingPreferencesProvider>,
  );

  // 온보딩 화면에서 일본어를 고른 상태를 흉내낸다 (로그인 전).
  fireEvent.press(view.getByTestId('pick-japanese'));
  expect(view.getByTestId('language').props.children).toBe('ja');

  fireEvent.press(view.getByTestId('sign-in'));
  await waitFor(() => expect(mockLogin).toHaveBeenCalled());
  await waitFor(() => expect(mockGetMe).toHaveBeenCalled());

  // 계정 언어(KO)가 로그인 뒤 화면 언어를 덮어쓰지 않아야 한다.
  expect(view.getByTestId('language').props.children).toBe('ja');
});
