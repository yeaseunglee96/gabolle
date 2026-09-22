// 로그인한 사람에게 로그인 화면을 보여주지 않는다 — S15P21E201-1199.
import { fireEvent, render, waitFor } from '@testing-library/react-native';

import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';

// jest.mock 의 공장 함수는 바깥 변수를 못 본다 — `mock` 으로 시작하는 이름만 예외다.
const mockBack = jest.fn();
const mockReplace = jest.fn();
const mockPush = jest.fn();
const mockCanGoBack = jest.fn(() => true);
const mockAuth = { signIn: jest.fn(), acceptTokens: jest.fn(), user: null as unknown, ready: true };
/** 화면이 가려졌다가 다시 보이는 일을 손으로 만든다. */
const mockFocus: { run: null | (() => void | (() => void)) } = { run: null };
function refocus() {
  const cleanup = mockFocus.run?.();
  if (typeof cleanup === 'function') cleanup();  // 포커스를 잃고
  mockFocus.run?.();                              // 다시 얻는다
}

jest.mock('expo-router', () => ({
  useRouter: () => ({ back: mockBack, replace: mockReplace, push: mockPush, canGoBack: mockCanGoBack, canDismiss: () => false, dismissAll: () => {} }),
  useLocalSearchParams: () => ({}),
  // 화면이 보이는 동안은 useEffect 와 같이 돌고, 언마운트할 때 cleanup 이 돌게 한다.
  // 「돌아왔다」는 재마운트로 흉내 낼 수 없어(실제로도 재마운트되지 않는다)
  // 아래에서 refocus 으로 포커스를 손으로 돌려 준다.
  useFocusEffect: (cb: () => void | (() => void)) => {
    mockFocus.run = cb;
    // eslint-disable-next-line react-hooks/rules-of-hooks
    require('react').useEffect(() => cb(), [cb]);
  },
}));

jest.mock('@/auth/AuthProvider', () => ({ useAuth: () => mockAuth }));

jest.mock('@/auth/pendingReturnTo', () => ({
  savePendingReturnTo: jest.fn(async () => {}),
  resolveDestination: jest.fn(async () => '/home'),
  guestDestination: jest.fn(() => '/home'),
}));
// 소셜 아이콘은 react-native-svg 로 그린다. 이 시험이 재는 것은 그림이 아니라 누가 서 있는가다.
jest.mock('@/components/SocialProviderIcon', () => ({ SocialProviderIcon: () => null }));
jest.mock('@/auth/oauth', () => ({ loginWithOAuth: jest.fn() }));
jest.mock('@/auth/oauthNavigation', () => ({ navigateAfterOAuthComplete: jest.fn() }));
jest.mock('@/layout/useLayout', () => ({ useLayout: () => ({ kind: 'phone', width: 390, height: 844, isLandscape: false }) }));
jest.mock('@/components/ScenicVideo', () => ({ ScenicVideo: () => null }));
jest.mock('react-native-safe-area-context', () => ({
  SafeAreaView: ({ children }: { children: unknown }) => children,
  useSafeAreaInsets: () => ({ top: 47, left: 0, right: 0, bottom: 34 }),
}));

import SignIn from '../(auth)/sign-in';

beforeEach(() => {
  jest.clearAllMocks();
  mockCanGoBack.mockReturnValue(true);
  mockAuth.user = null;
  mockAuth.ready = true;
});

describe('로그인 화면 — 이미 로그인한 사람은 붙잡지 않는다', () => {
  it('🔴 로그인한 채로 이 화면에 오면 홈으로 비킨다 — 뒤로 가 아니라 앞으로다', async () => {
    mockAuth.user = { userId: 'u1', displayName: '이예승' };
    render(<OnboardingPreferencesProvider><SignIn /></OnboardingPreferencesProvider>);
    await waitFor(() => expect(mockReplace).toHaveBeenCalledWith('/home'));
    expect(mockBack).not.toHaveBeenCalled();
  });

  it('돌아갈 칸이 없어도 홈으로 바꾼다 — 그래야 다음 뒤로 가기가 앱을 빠져나간다', async () => {
    mockAuth.user = { userId: 'u1', displayName: '이예승' };
    mockCanGoBack.mockReturnValue(false);
    render(<OnboardingPreferencesProvider><SignIn /></OnboardingPreferencesProvider>);
    await waitFor(() => expect(mockReplace).toHaveBeenCalledWith('/home'));
    expect(mockBack).not.toHaveBeenCalled();
  });

  it('로그인 안 한 사람은 그대로 로그인 화면에 둔다', async () => {
    const view = render(<OnboardingPreferencesProvider><SignIn /></OnboardingPreferencesProvider>);
    // 「로그인」은 제목·단추 둘이라 이 화면에만 있는 말로 본다.
    await waitFor(() => expect(view.getByText('비회원으로 둘러보기')).toBeTruthy());
    expect(mockBack).not.toHaveBeenCalled();
    expect(mockReplace).not.toHaveBeenCalled();
  });

  // 뒤로 가기로 돌아와도 이 화면은 다시 만들어지지 않는다. 쌓인 칸으로 살아 있다가
  // 다시 보일 뿐이라, 「여기서 방금 로그인했다」는 표시가 그대로 남아 가드를 막았다.
  // 그리는 순간이 아니라 포커스가 돌아오는 순간을 봐야 한다.
  it('🔴 이 화면에서 로그인한 뒤 뒤로 돌아오면 — 그때는 비킨다', async () => {
    const view = render(<OnboardingPreferencesProvider><SignIn /></OnboardingPreferencesProvider>);
    // 이 화면에서 로그인 절차를 시작한다.
    fireEvent.press(view.getByLabelText('Google로 계속하기'));
    // 로그인이 끝나 사용자가 생겼다. 아직 이 화면에 있는 동안은 비키지 않는다.
    mockAuth.user = { userId: 'u1', displayName: '이예승' };
    expect(mockBack).not.toHaveBeenCalled();
    // 목적지로 갔다가 뒤로 돌아온다.
    refocus();
    await waitFor(() => expect(mockReplace).toHaveBeenCalledWith('/home'));
    expect(mockBack).not.toHaveBeenCalled();
  });

  it('세션을 아직 못 읽었으면(ready=false) 아무 데도 보내지 않는다', async () => {
    mockAuth.user = { userId: 'u1', displayName: '이예승' };
    mockAuth.ready = false;
    render(<OnboardingPreferencesProvider><SignIn /></OnboardingPreferencesProvider>);
    expect(mockBack).not.toHaveBeenCalled();
    expect(mockReplace).not.toHaveBeenCalled();
  });
});
