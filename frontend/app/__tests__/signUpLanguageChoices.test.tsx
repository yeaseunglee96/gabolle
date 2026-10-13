// 가입 화면 두 곳(이메일 가입 · 소셜 가입 마무리)의 언어 선택 — S15P21E201-2053.
//
// 전에는 두 화면 모두 한국어·영어 두 칸뿐이었다. 앱은 여섯 언어를 아는데 가입에서만 둘이었고, 일본어·중국어·베트남어를
// 고른 사람도 가입하면서 한국어·영어로 되돌려야 했다.
//
// 이 시험이 지키는 것:
//   · 여섯 언어가 첫 화면의 언어 시트와 같은 순서로 나온다
//   · 🔴 처음 고른 값은 «첫 화면에서 고른 언어» 다 — 이메일 가입도, 소셜 가입 마무리도(소셜 업체의 언어 설정이 아니다)
//   · 칩을 누르면 화면 언어가 바뀌고, 가입 요청에는 서버가 받는 표기(KO · JA · ZH_HANS …)로 실려 나간다
//   · 폰 폭(390)에서 넘치지 않게 줄바꿈 칩이다 — 칸 폭을 박지 않는다
import AsyncStorage from '@react-native-async-storage/async-storage';
import { act, fireEvent, render, waitFor } from '@testing-library/react-native';
import type { ReactElement } from 'react';
import { StyleSheet } from 'react-native';

import { LANGUAGE_CODES, LANGUAGE_OPTIONS, type LanguageCode } from '@/i18n/languages';
import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';

let mockParams: Record<string, string> = {};
const mockSignup = jest.fn();
const mockCompleteOAuthSignup = jest.fn();
const mockAcceptTokens = jest.fn(async () => {});

jest.mock('expo-router', () => ({
  useRouter: () => ({ back: jest.fn(), replace: jest.fn(), push: jest.fn(), canGoBack: () => true }),
  useLocalSearchParams: () => mockParams,
}));
jest.mock('@/auth/authApi', () => ({
  signup: (...args: unknown[]) => mockSignup(...args),
  completeOAuthSignup: (...args: unknown[]) => mockCompleteOAuthSignup(...args),
  resendEmailVerification: jest.fn(),
}));
jest.mock('@/auth/pendingReturnTo', () => ({
  savePendingReturnTo: jest.fn(async () => {}),
  consumePendingReturnTo: jest.fn(async () => null),
  isSafeReturnPath: () => false,
  signedInDestination: () => '/home',
}));
jest.mock('@/auth/enterApp', () => ({ enterApp: jest.fn() }));
jest.mock('@/auth/AuthProvider', () => ({ useAuth: () => ({ user: null, ready: true, acceptTokens: mockAcceptTokens }) }));
jest.mock('@/components/BrandLogoLink', () => ({ BrandLogoLink: () => null }));
jest.mock('@/layout/useLayout', () => ({ useLayout: () => ({ kind: 'tablet', width: 1440, height: 900, isLandscape: false }) }));
jest.mock('react-native-safe-area-context', () => ({
  SafeAreaView: ({ children }: { children: unknown }) => children,
  useSafeAreaInsets: () => ({ top: 47, left: 0, right: 0, bottom: 34 }),
}));

import OAuthSignup from '../(auth)/oauth-signup';
import SignUp from '../(auth)/sign-up';

const STORAGE_KEY = 'gabolle:onboarding-preferences';
/** 첫 화면의 언어 시트(WelcomeLanguageSheet)와 같은 순서다 — 목록이 LANGUAGE_OPTIONS 한 곳이라서. */
const ENDONYMS = ['한국어', 'English', '日本語', '简体中文', '繁體中文', 'Tiếng Việt'];

type View = ReturnType<typeof render>;
const chip = (view: View, code: LanguageCode) => view.getByTestId(`signup-language-${code}`);
const selectedCodes = (view: View) => LANGUAGE_CODES.filter((code) => chip(view, code).props.accessibilityState.selected === true);
/**
 * 웹은 accessibilityState.selected 를 DOM 에 안 옮긴다 — 화면 읽기 프로그램이 읽는 aria-checked 로도 같은 칸이 골라져 있어야 한다.
 * 앱에서 aria-checked 는 accessibilityState.checked 로 바뀌어 host 에 닿는다.
 */
const checkedCodes = (view: View) => LANGUAGE_CODES.filter((code) => chip(view, code).props.accessibilityState.checked === true);

/** 첫 화면에서 이 언어를 골라 둔 채로 앱을 연 것처럼 한다. */
async function startedIn(language: LanguageCode) {
  await AsyncStorage.setItem(STORAGE_KEY, JSON.stringify({ language, mobility: 'none', hasEnteredApp: true }));
}

/**
 * 화면을 그리고 «저장소에서 읽어 오는 일(첫 화면에서 고른 언어)»까지 끝낸 뒤 돌려준다.
 * 안 기다리면 시험이 끝난 뒤에 상태가 바뀌어 act 경고가 쏟아진다.
 */
async function mount(screen: ReactElement) {
  const view = render(<OnboardingPreferencesProvider>{screen}</OnboardingPreferencesProvider>);
  await act(async () => {});
  return view;
}
const mountSignUp = () => mount(<SignUp />);
const mountOAuthSignup = () => mount(<OAuthSignup />);

beforeEach(async () => {
  jest.clearAllMocks();
  mockParams = {};
  mockSignup.mockReset();
  mockCompleteOAuthSignup.mockReset();
  await AsyncStorage.clear();
});

describe('이메일 가입 화면', () => {
  it('🔴 여섯 언어가 모두 나온다 — 한국어·영어 둘뿐이던 것을 풀었다. 순서는 첫 화면 언어 시트와 같다', async () => {
    const view = await mountSignUp();

    const labels = view.getAllByRole('radio').map((node) => node.props.accessibilityLabel);
    expect(labels).toEqual(ENDONYMS);
    expect(LANGUAGE_OPTIONS.map((option) => option.endonym)).toEqual(ENDONYMS);
    for (const endonym of ENDONYMS) expect(view.getByText(endonym)).toBeTruthy();
  });

  it('🔴 처음 연 화면에서는 첫 화면에서 고른 언어가 골라져 있다', async () => {
    await startedIn('ja');
    const view = await mountSignUp();

    await waitFor(() => expect(selectedCodes(view)).toEqual(['ja']));
  });

  it.each(LANGUAGE_CODES)('첫 화면에서 %s 를 골랐으면 그 칩 하나만 골라져 있다', async (code) => {
    await startedIn(code);
    const view = await mountSignUp();

    await waitFor(() => expect(selectedCodes(view)).toEqual([code]));
  });

  it('칩을 누르면 화면 언어도 같이 바뀐다 — 지금 동작을 그대로 둔다', async () => {
    const view = await mountSignUp();
    expect(selectedCodes(view)).toEqual(['ko']);

    fireEvent.press(chip(view, 'ja'));

    await waitFor(() => expect(selectedCodes(view)).toEqual(['ja']));
    expect(checkedCodes(view)).toEqual(['ja']);
    // 필드 이름 「언어」 가 일본어로 바뀌었다 — 화면 언어가 바뀌었다는 뜻이다.
    expect(view.getByText('言語')).toBeTruthy();
    expect(view.queryByText('언어')).toBeNull();
  });

  it.each([
    ['ko', 'KO'],
    ['en', 'EN'],
    ['ja', 'JA'],
    ['zh-Hans', 'ZH_HANS'],
    ['zh-Hant', 'ZH_HANT'],
    ['vi', 'VI'],
  ] as const)('🔴 앱 언어 %s 를 고르고 가입하면 서버에는 %s 가 나간다', async (code, expected) => {
    mockSignup.mockResolvedValue({ userId: 'u1', email: 'me@example.com', status: 'PENDING_EMAIL_VERIFICATION' });
    const view = await mountSignUp();
    fireEvent.changeText(view.getByTestId('sign-up-email'), 'me@example.com');
    fireEvent.changeText(view.getByTestId('sign-up-password'), 'abcd1234!');
    fireEvent.changeText(view.getByTestId('sign-up-confirm'), 'abcd1234!');
    fireEvent.changeText(view.getByTestId('sign-up-name'), '효준');
    // 동의 상자는 글자가 아니라 역할로 찾는다 — 화면 언어가 바뀌어도 같다.
    for (const checkbox of view.getAllByRole('checkbox')) fireEvent.press(checkbox);

    fireEvent.press(chip(view, code));
    await waitFor(() => expect(selectedCodes(view)).toEqual([code]));
    fireEvent.press(view.getByTestId('sign-up-submit'));

    await waitFor(() => expect(mockSignup).toHaveBeenCalledTimes(1));
    expect(mockSignup.mock.calls[0][0]).toMatchObject({ email: 'me@example.com', language: expected });
  });

  it('🔴 칩을 안 눌러도 첫 화면에서 고른 언어로 가입된다 — 베트남어를 골랐으면 영어로 되돌아가지 않는다', async () => {
    await startedIn('vi');
    mockSignup.mockResolvedValue({ userId: 'u1', email: 'me@example.com', status: 'PENDING_EMAIL_VERIFICATION' });
    const view = await mountSignUp();
    await waitFor(() => expect(selectedCodes(view)).toEqual(['vi']));
    fireEvent.changeText(view.getByTestId('sign-up-email'), 'me@example.com');
    fireEvent.changeText(view.getByTestId('sign-up-password'), 'abcd1234!');
    fireEvent.changeText(view.getByTestId('sign-up-confirm'), 'abcd1234!');
    fireEvent.changeText(view.getByTestId('sign-up-name'), '효준');
    for (const checkbox of view.getAllByRole('checkbox')) fireEvent.press(checkbox);

    fireEvent.press(view.getByTestId('sign-up-submit'));

    await waitFor(() => expect(mockSignup).toHaveBeenCalledTimes(1));
    expect(mockSignup.mock.calls[0][0]).toMatchObject({ language: 'VI' });
  });

  it('🔴 폰 폭에서 넘치지 않는다 — 줄바꿈 칩이고 칸 폭을 박지 않았다', async () => {
    const view = await mountSignUp();

    const group = StyleSheet.flatten(view.getByTestId('signup-language-group').props.style);
    expect(group.flexDirection).toBe('row');
    expect(group.flexWrap).toBe('wrap');
    for (const code of LANGUAGE_CODES) {
      const style = StyleSheet.flatten(chip(view, code).props.style);
      // 폭을 «최소» 30% 로 두면 3 + 3 으로 채워지고, 글자가 길면 칸이 넓어지다 다음 줄로 간다.
      expect(style.minWidth).toBe('30%');
      expect(style.flexGrow).toBe(1);
      // 폭·기준을 박으면 글자를 키운 기기에서 낱말 중간에서 꺾인다(S15P21E201-1983).
      expect(style.width).toBeUndefined();
      expect(style.flexBasis).toBeUndefined();
      // 44pt 터치 기준 이상.
      expect(style.minHeight).toBeGreaterThanOrEqual(44);
    }
  });
});

describe('소셜 가입 마무리 화면', () => {
  beforeEach(() => {
    mockParams = { provider: 'google', signupTicket: 'ticket-1', email: 'me@example.com', displayName: '여행자', emailProvided: 'true' };
  });

  it('🔴 여섯 언어가 모두 나온다', async () => {
    const view = await mountOAuthSignup();

    expect(view.getAllByRole('radio').map((node) => node.props.accessibilityLabel)).toEqual(ENDONYMS);
  });

  it('🔴 처음 값은 첫 화면에서 고른 언어다 — 소셜 업체의 언어 설정(영어)이 앞서지 않는다', async () => {
    await startedIn('zh-Hant');
    // 이전에는 이 값이 처음 값이었다(영어면 EN, 그 밖엔 KO). 한국어 화면을 보는 사람이 영어 계정으로 가입될 수 있었다.
    mockParams = { ...mockParams, language: 'EN' };
    const view = await mountOAuthSignup();

    await waitFor(() => expect(selectedCodes(view)).toEqual(['zh-Hant']));
  });

  it('칩을 누르면 화면 언어도 같이 바뀐다', async () => {
    const view = await mountOAuthSignup();
    expect(selectedCodes(view)).toEqual(['ko']);

    fireEvent.press(chip(view, 'vi'));

    await waitFor(() => expect(selectedCodes(view)).toEqual(['vi']));
    expect(view.getByText('Ngôn ngữ')).toBeTruthy();
  });

  it.each([
    ['ko', 'KO'],
    ['en', 'EN'],
    ['ja', 'JA'],
    ['zh-Hans', 'ZH_HANS'],
    ['zh-Hant', 'ZH_HANT'],
    ['vi', 'VI'],
  ] as const)('🔴 앱 언어 %s 를 고르고 가입을 마치면 서버에는 %s 가 나간다', async (code, expected) => {
    mockCompleteOAuthSignup.mockResolvedValue({ status: 'LOGGED_IN' });
    const view = await mountOAuthSignup();
    for (const checkbox of view.getAllByRole('checkbox')) fireEvent.press(checkbox);

    fireEvent.press(chip(view, code));
    await waitFor(() => expect(selectedCodes(view)).toEqual([code]));
    fireEvent.press(view.getByTestId('oauth-signup-submit'));

    await waitFor(() => expect(mockCompleteOAuthSignup).toHaveBeenCalledTimes(1));
    expect(mockCompleteOAuthSignup.mock.calls[0][0]).toMatchObject({ signupTicket: 'ticket-1', language: expected });
  });

  it('칸 폭을 박지 않은 줄바꿈 칩이다 — 이메일 가입 화면과 같은 부품이다', async () => {
    const view = await mountOAuthSignup();

    expect(StyleSheet.flatten(view.getByTestId('signup-language-group').props.style).flexWrap).toBe('wrap');
    expect(StyleSheet.flatten(chip(view, 'vi').props.style).minWidth).toBe('30%');
  });
});
