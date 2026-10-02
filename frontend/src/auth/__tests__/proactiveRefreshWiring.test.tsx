// AuthProvider 가 새 접속 표의 수명을 api/client 에 알리고, 앱이 앞에 오면 표를 보게 하는지.
// 미리 갱신하는 판단 자체는 api/__tests__/proactiveRefresh.test.ts 가 본다 — 여기는 «이어져 있나» 만 본다.
import { act, fireEvent, render, waitFor } from '@testing-library/react-native';
import { AppState, Pressable, Text } from 'react-native';
import { AuthProvider, useAuth } from '../AuthProvider';
import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';
import { apiRequest, trackAccessToken } from '@/api/client';

const mockRouter = { replace: jest.fn(), push: jest.fn() };
jest.mock('expo-router', () => ({ useRouter: () => mockRouter }));

jest.mock('expo-secure-store', () => ({
  getItemAsync: jest.fn().mockResolvedValue(null),
  setItemAsync: jest.fn().mockResolvedValue(undefined),
  deleteItemAsync: jest.fn().mockResolvedValue(undefined),
}));

jest.mock('@/notifications/pushToken', () => ({
  registerPushToken: jest.fn().mockResolvedValue('skipped'),
  unregisterPushToken: jest.fn().mockResolvedValue(undefined),
}));

const mockLogin = jest.fn();
const mockGetMe = jest.fn();
const mockRefreshMobileSession = jest.fn();
jest.mock('../authApi', () => ({
  ...jest.requireActual('../authApi'),
  login: (...args: unknown[]) => mockLogin(...args),
  getMe: (...args: unknown[]) => mockGetMe(...args),
  refreshMobileSession: (...args: unknown[]) => mockRefreshMobileSession(...args),
}));

const user = { userId: 'u1', email: 'user@example.com', displayName: '테스트', language: 'KO', status: 'ACTIVE' };
let sentTokens: (string | null)[];

function SignInProbe() {
  const { signIn, accessToken } = useAuth();
  return (
    <>
      <Text testID="token">{accessToken ?? 'none'}</Text>
      <Pressable testID="sign-in" onPress={() => void signIn('user@example.com', 'password')}><Text>로그인</Text></Pressable>
    </>
  );
}

async function renderSignedIn(expiresIn: number) {
  mockLogin.mockResolvedValue({ accessToken: 'login-token', refreshToken: 'login-refresh', expiresIn, sessionId: 's1', user });
  const view = render(
    <OnboardingPreferencesProvider>
      <AuthProvider>
        <SignInProbe />
      </AuthProvider>
    </OnboardingPreferencesProvider>,
  );
  fireEvent.press(view.getByTestId('sign-in'));
  await waitFor(() => expect(view.getByTestId('token').props.children).toBe('login-token'));
  return view;
}

beforeEach(() => {
  jest.clearAllMocks();
  sentTokens = [];
  mockGetMe.mockResolvedValue(user);
  mockRefreshMobileSession.mockResolvedValue({ accessToken: 'refreshed-token', refreshToken: 'next-refresh', expiresIn: 1800, sessionId: 's1', user });
  globalThis.fetch = jest.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    if (String(input).includes('/auth/anonymous')) {
      return new Response(JSON.stringify({ data: { sessionId: 's', sessionToken: 'anon-token', issuedAt: '' }, error: null, meta: { requestId: 'r0' } }), {
        status: 200, headers: { 'content-type': 'application/json' },
      });
    }
    const authorization = (init?.headers as Record<string, string> | undefined)?.Authorization ?? null;
    sentTokens.push(authorization ? authorization.replace('Bearer ', '') : null);
    return new Response(JSON.stringify({ data: { ok: true }, error: null, meta: { requestId: 'r1' } }), {
      status: 200, headers: { 'content-type': 'application/json' },
    });
  }) as unknown as typeof fetch;
});

afterEach(() => {
  trackAccessToken(null);
  jest.restoreAllMocks();
});

it('로그인으로 받은 표가 곧 끝나면, 다음 요청 앞에서 저장된 갱신 표로 갱신하고 새 표로 보낸다', async () => {
  const view = await renderSignedIn(30);

  await act(async () => { await apiRequest('/api/v1/places', { accessToken: 'login-token' }); });

  expect(mockRefreshMobileSession).toHaveBeenCalledWith('login-refresh');
  expect(sentTokens).toEqual(['refreshed-token']);
  expect(view.getByTestId('token').props.children).toBe('refreshed-token');
});

it('표가 넉넉하면 요청 앞에서 갱신하지 않는다', async () => {
  await renderSignedIn(1800);

  await act(async () => { await apiRequest('/api/v1/places', { accessToken: 'login-token' }); });

  expect(mockRefreshMobileSession).not.toHaveBeenCalled();
  expect(sentTokens).toEqual(['login-token']);
});

it('앱이 다시 앞에 오면 곧 끝나는 표를 갱신한다', async () => {
  const listeners: ((state: string) => void)[] = [];
  jest.spyOn(AppState, 'addEventListener').mockImplementation((_type, listener) => {
    listeners.push(listener as (state: string) => void);
    return { remove: jest.fn() } as unknown as ReturnType<typeof AppState.addEventListener>;
  });
  const view = await renderSignedIn(30);
  expect(listeners.length).toBeGreaterThan(0);

  await act(async () => { listeners.forEach((listener) => listener('background')); });
  expect(mockRefreshMobileSession).not.toHaveBeenCalled();

  await act(async () => { listeners.forEach((listener) => listener('active')); });
  await waitFor(() => expect(view.getByTestId('token').props.children).toBe('refreshed-token'));
  expect(mockRefreshMobileSession).toHaveBeenCalledTimes(1);
});
