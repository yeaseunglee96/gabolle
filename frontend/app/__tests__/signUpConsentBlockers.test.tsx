// 회원가입 — 버튼이 왜 잠겼는지 적고, 입력칸은 «입력한 뒤에만» 빨갛다 (S15P21E201-1518).
//
// 시안: frontend/docs/design_handoff_signup_consent/README.md
// 이 시험이 지키는 것:
//   · 비활성 이유 상자 — 처음엔 열 줄, 다 채우면 사라지고 버튼이 열린다
//   · 🔴 처음 연 빈 칸은 오류가 아니다 — 옅은 붉은 채움은 입력한 뒤에만
//   · 폰은 진행 점으로 칸을 건너뛰어 와도 상자가 «버튼이 왜 잠겼나» 를 거짓말하지 않는다
import { fireEvent, render } from '@testing-library/react-native';
import { StyleSheet } from 'react-native';

import { color } from '@/design/tokens';
import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';

let mockKind: 'phone' | 'tablet' = 'tablet';

jest.mock('expo-router', () => ({
  useRouter: () => ({ back: jest.fn(), replace: jest.fn(), push: jest.fn(), canGoBack: () => true }),
  useLocalSearchParams: () => ({}),
}));
// 폰 칸 넘김 애니메이션 — jest 에는 네이티브 worklets 가 없어 불러오기만 해도 죽는다. 라이브러리가 주는
// 가짜(react-native-reanimated/mock)도 worklets 를 불러 똑같이 죽는다(2026-09-23 실측). 이 화면이 쓰는 것만 흉내 낸다.
jest.mock('react-native-reanimated', () => {
  const { View } = require('react-native');
  const chain = { duration: () => chain, reduceMotion: () => chain };
  return { __esModule: true, default: { View }, FadeInRight: chain, FadeOutLeft: chain, ReduceMotion: { System: 'system' } };
});
jest.mock('@/auth/authApi', () => ({ signup: jest.fn(), resendEmailVerification: jest.fn() }));
jest.mock('@/auth/pendingReturnTo', () => ({ savePendingReturnTo: jest.fn(async () => {}), isSafeReturnPath: () => false, signedInDestination: () => '/home' }));
// 로그인 안 한 사람의 가입 화면이다 — 로그인한 사람은 비킨다(S15P21E201-1793, signUpWhenSignedIn 시험).
jest.mock('@/auth/AuthProvider', () => ({ useAuth: () => ({ user: null, ready: true }) }));
jest.mock('@/components/BrandLogoLink', () => ({ BrandLogoLink: () => null }));
jest.mock('@/layout/useLayout', () => ({ useLayout: () => ({ kind: mockKind, width: mockKind === 'phone' ? 390 : 1440, height: 900, isLandscape: false }) }));
jest.mock('react-native-safe-area-context', () => ({
  SafeAreaView: ({ children }: { children: unknown }) => children,
  useSafeAreaInsets: () => ({ top: 47, left: 0, right: 0, bottom: 34 }),
}));

import SignUp from '../(auth)/sign-up';

const HEADING = '아직 남은 것';
/** 남은 것은 한 줄에 「 · 」로 — 처음 손댄 직후(이메일만 친 상태)의 모양 */
const AGREEMENTS = ['만 14세 이상입니다.', '이용약관에 동의합니다. (필수)', '개인정보 처리방침에 동의합니다. (필수)'];
const mount = () => render(<OnboardingPreferencesProvider><SignUp /></OnboardingPreferencesProvider>);
type RowStyle = { backgroundColor?: string; borderColor?: string; borderWidth?: number; borderRadius?: number };
/** 입력칸을 감싼 행 — 테두리와 채움은 거기 있다. 입력칸에서 위로 올라가 처음 만나는 둥근 상자다. */
const rowOf = (view: ReturnType<typeof mount>, testID: string): RowStyle => {
  let node = view.getByTestId(testID).parent;
  while (node) {
    const style = StyleSheet.flatten(node.props.style as never) as RowStyle | undefined;
    if (style?.borderRadius) return style;
    node = node.parent;
  }
  throw new Error(`${testID} 를 감싼 행을 못 찾았다`);
};
const submitDisabled = (view: ReturnType<typeof mount>) => Boolean(view.getByTestId('sign-up-submit').props.accessibilityState?.disabled);

beforeEach(() => { mockKind = 'tablet'; });

describe('회원가입 — 넓은 화면', () => {
  it('🔴 처음 연 화면에는 상자가 없다 — 빈 칸은 오류가 아니다(사용자 의견 2026-10-02). 어느 칸도 빨갛지 않다', () => {
    const view = mount();
    expect(view.queryByText(HEADING)).toBeNull();
    expect(submitDisabled(view)).toBe(true);
    for (const id of ['sign-up-email', 'sign-up-password', 'sign-up-confirm', 'sign-up-name']) {
      expect(rowOf(view, id).backgroundColor).toBe(color.surface.card);
    }
  });

  it('🔴 입력한 뒤에만 옅은 붉은 채움 — 틀린 이메일을 치면 칸이 물들고, 지우면 돌아온다', () => {
    const view = mount();
    fireEvent.changeText(view.getByTestId('sign-up-email'), 'abc');
    expect(rowOf(view, 'sign-up-email').backgroundColor).toBe(color.state.dangerFieldBg);
    expect(view.getByText('올바른 이메일 주소를 입력해 주세요.')).toBeTruthy();
    fireEvent.changeText(view.getByTestId('sign-up-email'), '');
    expect(rowOf(view, 'sign-up-email').backgroundColor).toBe(color.surface.card);
  });

  it('🔴 칸 안의 ✕ 는 키보드 탭 순서에서 빠진다 — 이메일에서 탭을 누르면 비밀번호로 바로 간다', () => {
    const view = mount();
    fireEvent.changeText(view.getByTestId('sign-up-email'), 'me@example.com');
    fireEvent.changeText(view.getByTestId('sign-up-password'), 'abc');
    fireEvent.changeText(view.getByTestId('sign-up-confirm'), 'abc');
    fireEvent.changeText(view.getByTestId('sign-up-name'), '효준');
    for (const label of ['이메일 지우기', '비밀번호 지우기', '비밀번호 확인 지우기', '이름 지우기']) {
      expect(view.getByLabelText(label).props.tabIndex).toBe(-1);
    }
  });

  it('포커스는 붉은 2px 선이고, 떠나면 돌아온다', () => {
    const view = mount();
    fireEvent(view.getByTestId('sign-up-name'), 'focus');
    expect(rowOf(view, 'sign-up-name')).toMatchObject({ borderColor: color.action.outline, borderWidth: 2 });
    fireEvent(view.getByTestId('sign-up-name'), 'blur');
    expect(rowOf(view, 'sign-up-name')).toMatchObject({ borderColor: color.surface.field, borderWidth: 1 });
  });

  it('손대면 남은 것이 한 줄로 뜨고 — 비밀번호는 한 줄, 동의는 개수 — 다 채우면 사라져 회원가입이 열린다', () => {
    const view = mount();
    fireEvent.changeText(view.getByTestId('sign-up-email'), 'me@example.com');
    expect(view.getByText(HEADING)).toBeTruthy();
    expect(view.getByText('비밀번호 조건 — 칸 아래 표시 · 이름(1~30자) · 필수 동의 3개')).toBeTruthy();
    fireEvent.changeText(view.getByTestId('sign-up-password'), 'abcd1234!');
    fireEvent.changeText(view.getByTestId('sign-up-confirm'), 'abcd1234!');
    fireEvent.changeText(view.getByTestId('sign-up-name'), '효준');
    // 동의만 남는다
    expect(view.getByText('필수 동의 3개')).toBeTruthy();
    fireEvent.press(view.getByText(AGREEMENTS[0]));
    expect(view.getByText('필수 동의 2개')).toBeTruthy();
    for (const label of AGREEMENTS.slice(1)) fireEvent.press(view.getByText(label));
    expect(view.queryByText(HEADING)).toBeNull();
    expect(submitDisabled(view)).toBe(false);
  });
});

describe('회원가입 — 폰', () => {
  it('🔴 진행 점으로 마지막 칸에 건너뛰어 와도, 동의를 다 하면 «앞 칸이 비었다» 가 남는다 — 상자 없이 잠긴 버튼이 되지 않는다', () => {
    mockKind = 'phone';
    const view = mount();
    fireEvent.press(view.getByLabelText('약관 동의 단계로 이동'));
    for (const label of AGREEMENTS) fireEvent.press(view.getByText(label));
    expect(submitDisabled(view)).toBe(true);
    expect(view.getByText(HEADING)).toBeTruthy();
    expect(view.getByText('이메일 주소 · 비밀번호 조건 — 칸 아래 표시 · 이름(1~30자)')).toBeTruthy();
  });
});
