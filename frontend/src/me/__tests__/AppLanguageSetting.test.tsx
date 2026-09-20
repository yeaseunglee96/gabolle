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
// — 이 화면이 한국어·영어 둘뿐이라는 사용자 리포트. 이제 다섯 다 고를 수
// 있고, 계정 칸(KO/EN 둘뿐)에는 한국어가 아니면 전부 EN으로 근사해 보낸다.
it('offers all five UI languages and approximates non-Korean picks as EN for the account', async () => {
  mockUser = { userId: 'test' };
  mockUpdateProfile.mockResolvedValue(undefined);
  const view = await mount();
  expect(view.getByLabelText('日本語')).toBeTruthy();
  expect(view.getByLabelText('简体中文')).toBeTruthy();
  expect(view.getByLabelText('繁體中文')).toBeTruthy();
  fireEvent.press(view.getByLabelText('日本語'));
  await waitFor(() => expect(mockUpdateProfile).toHaveBeenCalledWith({ language: 'EN' }));
  // 「메뉴는 아직 영어로 나와요」 안내는 2026-09-21 에 걷어냈다 — 다섯 언어가 다 번역돼 사실이 아니게 됐다.
  //    일본어를 고른 뒤 그 안내가 어느 언어로도 남아 있지 않아야 한다.
  await waitFor(() => expect(view.queryByText(/メニューはまだ英語|Menus are in English|메뉴는 아직 영어/)).toBeNull());
});
