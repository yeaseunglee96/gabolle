import { fireEvent, render, waitFor } from '@testing-library/react-native';
import AsyncStorage from '@react-native-async-storage/async-storage';
import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';
import { AppLanguageSetting } from '../AppLanguageSetting';

const mockUpdateProfile = jest.fn();
let mockUser: object | null = null;
jest.mock('@/auth/AuthProvider', () => ({ useAuth: () => ({ user: mockUser, updateProfile: mockUpdateProfile }) }));
beforeEach(async () => { jest.clearAllMocks(); mockUser = null; mockUpdateProfile.mockReset(); await AsyncStorage.clear(); });
async function mount() {
  const view = render(<OnboardingPreferencesProvider><AppLanguageSetting /></OnboardingPreferencesProvider>);
  await waitFor(() => expect(AsyncStorage.setItem).toHaveBeenCalled());
  return view;
}
it('lets a guest switch language without an account request', async () => {
  const view = await mount();
  fireEvent.press(view.getByLabelText('English'));
  await view.findByText('App language');
  expect(mockUpdateProfile).not.toHaveBeenCalled();
});
it('persists a member language to the profile', async () => {
  mockUser = { userId: 'test' };
  mockUpdateProfile.mockResolvedValue(undefined);
  const view = await mount();
  fireEvent.press(view.getByLabelText('English'));
  await view.findByText('App language');
  expect(mockUpdateProfile).toHaveBeenCalledWith({ language: 'EN' });
});
it('keeps the current language and explains a failed profile save', async () => {
  mockUser = { userId: 'test' };
  mockUpdateProfile.mockRejectedValue(new Error('offline'));
  const view = await mount();
  fireEvent.press(view.getByLabelText('English'));
  await view.findByRole('alert');
  expect(view.getByText('앱 언어')).toBeTruthy();
});
// 계정 칸에 고른 언어를 그대로 저장한다(S15P21E201-2015). 예전에는 한국어가 아니면 전부 EN 으로
// 근사해 보내서, 베트남어를 고른 사람의 알림·AI 답·메뉴 설명이 영어로 왔다. 서버
// LanguageNormalizer 는 VI·JA·ZH-HANS·ZH-HANT 를 이미 받는다.
it.each([
  ['Tiếng Việt', 'VI'],
  ['日本語', 'JA'],
  ['简体中文', 'ZH-HANS'],
  ['繁體中文', 'ZH-HANT'],
])('saves %s to the account as %s', async (label, expected) => {
  mockUser = { userId: 'test' };
  mockUpdateProfile.mockResolvedValue(undefined);
  const view = await mount();
  fireEvent.press(view.getByLabelText(label));
  await waitFor(() => expect(mockUpdateProfile).toHaveBeenCalledWith({ language: expected }));
});
it('offers all UI languages and drops the old "menus are in English" note', async () => {
  mockUser = { userId: 'test' };
  mockUpdateProfile.mockResolvedValue(undefined);
  const view = await mount();
  expect(view.getByLabelText('日本語')).toBeTruthy();
  expect(view.getByLabelText('简体中文')).toBeTruthy();
  expect(view.getByLabelText('繁體中文')).toBeTruthy();
  fireEvent.press(view.getByLabelText('日本語'));
  await waitFor(() => expect(mockUpdateProfile).toHaveBeenCalled());
  // 「메뉴는 아직 영어로 나와요」 안내는 2026-09-21 에 걷어냈다 — 다섯 언어가 다 번역돼 사실이 아니게 됐다.
  //    일본어를 고른 뒤 그 안내가 어느 언어로도 남아 있지 않아야 한다.
  await waitFor(() => expect(view.queryByText(/メニューはまだ英語|Menus are in English|메뉴는 아직 영어/)).toBeNull());
});
// S15P21E201-2054 — 갤럭시 S22 실기기(앱 1.0.3, 2026-10-14)에서 「繁體中文」이 「繁體中」으로 끝 글자가 잘렸다. 1988 의 «한 줄 · 글자 줄이기»가
// 들어간 앱에서도 그대로였다. 칩 안의 글자를 칩 폭 전체로 펴 여유를 주고(ChipLabel), 굵기를 가짜 굵게 문턱(600) 아래로 낮췄다.
// 안드로이드에서 실제로 끝 글자까지 보이는지는 실기기로 확인해야 한다 — 여기서는 처방이 빠지지 않았는지를 박는다.
it('🔴 언어 칩의 이름은 칩 폭 전체로 편 글자다 — 끝 글자가 잘리지 않을 여유, 가짜 굵게 문턱 아래의 굵기, 글자 줄이기 없음', async () => {
  const { StyleSheet } = require('react-native');
  const view = await mount();
  // 처음 연 화면은 한국어라 고른 칩의 글자는 «✓ 한국어» 한 문자열이다(1966).
  for (const name of ['✓ 한국어', 'English', '日本語', '简体中文', '繁體中文', 'Tiếng Việt']) {
    const label = view.getByText(name);
    const style = StyleSheet.flatten(label.props.style);
    expect(style.alignSelf).toBe('stretch');
    expect(style.textAlign).toBe('center');
    expect(Number(style.fontWeight)).toBeLessThan(600);
    expect(label.props.numberOfLines).toBeUndefined();
    expect(label.props.adjustsFontSizeToFit).toBeUndefined();
  }
});
// S15P21E201-1983 — 칸 폭을 30% 로 박아 두면(flexBasis) 글자를 키운 기기에서 「English」가 「Eng / lish」로
// 낱말 중간에서 꺾였다(갤럭시 탭 세로·글자 1.3배 실측). 칸은 최소 30% 만 차지하고 글자 길이만큼 넓어져야 한다.
it('lets a language chip grow to fit its name instead of breaking the word', async () => {
  const { StyleSheet } = require('react-native');
  const view = await mount();
  const chip = view.getByLabelText('English');
  const style = StyleSheet.flatten(chip.props.style);
  expect(style.flexBasis).toBeUndefined();
  expect(style.minWidth).toBe('30%');
  expect(style.flexGrow).toBe(1);
});
