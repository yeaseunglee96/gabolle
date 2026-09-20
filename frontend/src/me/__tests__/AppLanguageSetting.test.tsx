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
  // 🔴 이 줄은 예전에 «영어로 뜨는 것»을 기대했다 — 번역표에 그 문구가 없어서였고,
  //    시험이 그 빈자리를 「의도된 것」이라고 적어 두고 있었다. S15P21E201-1356 에서
  //    표를 채웠으므로 이제 일본어로 뜬다. 빠진 번역을 시험이 굳혀 두지 않게 고친다.
  await view.findByText('メニューはまだ英語で表示されます。場所の名前と案内は選んだ言語で表示されます。');
});
