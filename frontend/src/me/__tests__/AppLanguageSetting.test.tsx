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
