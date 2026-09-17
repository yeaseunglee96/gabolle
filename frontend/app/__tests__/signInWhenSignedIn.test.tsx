// 로그인한 사람에게 로그인 화면을 보여주지 않는다 — S15P21E201-1199.
//
// 🔴 이 시험이 지키는 것은 **화면의 모양이 아니라 「누가 서 있는가」** 다.
//    구글 로그인은 앱 안 브라우저가 `https://j15e201.p.ssafy.io/oauth/...` 에 착지하는데,
//    그 주소가 app.json 의 App Link 와 글자 그대로 겹쳐서 안드로이드가 **앱을 한 번 더 연다.**
//    그래서 `router.replace` 가 바꿔 놓는 것은 방금 쌓인 칸뿐이고, 로그인 화면은 그 아래
//    그대로 남는다. 홈에서 뒤로 가기 한 번이면 로그인한 사람이 로그인 폼을 본다.
//
//    고침은 「아래 칸을 지운다」가 아니라 「이 화면이 스스로 비킨다」이다. 그러니 시험도
//    스택 모양이 아니라 **비켰는가**를 본다 — 스택을 재는 시험은 라우터가 판을 올릴 때마다 낡는다.
import { render, waitFor } from '@testing-library/react-native';

import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';

// jest.mock 의 공장 함수는 바깥 변수를 못 본다 — `mock` 으로 시작하는 이름만 예외다.
const mockBack = jest.fn();
const mockReplace = jest.fn();
const mockPush = jest.fn();
const mockCanGoBack = jest.fn(() => true);
const mockAuth = { signIn: jest.fn(), acceptTokens: jest.fn(), user: null as unknown, ready: true };

jest.mock('expo-router', () => ({
  useRouter: () => ({ back: mockBack, replace: mockReplace, push: mockPush, canGoBack: mockCanGoBack }),
  useLocalSearchParams: () => ({}),
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
  it('🔴 로그인한 채로 이 화면에 오면 쌓인 칸을 돌려보낸다', async () => {
    mockAuth.user = { userId: 'u1', displayName: '이예승' };
    render(<OnboardingPreferencesProvider><SignIn /></OnboardingPreferencesProvider>);
    await waitFor(() => expect(mockBack).toHaveBeenCalledTimes(1));
    expect(mockReplace).not.toHaveBeenCalled();
  });

  it('돌아갈 칸이 없으면 홈으로 바꾼다 — 그래야 다음 뒤로 가기가 앱을 빠져나간다', async () => {
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

  it('세션을 아직 못 읽었으면(ready=false) 아무 데도 보내지 않는다', async () => {
    mockAuth.user = { userId: 'u1', displayName: '이예승' };
    mockAuth.ready = false;
    render(<OnboardingPreferencesProvider><SignIn /></OnboardingPreferencesProvider>);
    await waitFor(() => expect(mockCanGoBack).not.toHaveBeenCalled());
    expect(mockBack).not.toHaveBeenCalled();
    expect(mockReplace).not.toHaveBeenCalled();
  });
});
