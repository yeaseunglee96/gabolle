// 프로필 사진은 「저장」을 눌러야 계정에 붙는다 — S15P21E201-1906(팀원 보고: 저장 안 눌렀는데 바뀌어 있었다).
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react-native';

const mockUpdateProfile = jest.fn(async () => {});
const mockUpload = jest.fn(async () => ({ state: 'success', imageUrl: 'https://x/a.jpg' }));
jest.mock('@/auth/AuthProvider', () => ({ useAuth: () => ({ accessToken: 't', user: { userId: 'u1', displayName: '알렉스', avatarUrl: null, coverUrl: null }, updateProfile: mockUpdateProfile, deleteAccount: jest.fn(), signOut: jest.fn() }) }));
jest.mock('@/social/stories', () => ({ uploadStoryImage: (...a: unknown[]) => (mockUpload as jest.Mock)(...a) }));
jest.mock('@/social/imageResize', () => ({ resizeForUpload: jest.fn(async () => ({ uri: 'file://cover.jpg', width: 1, height: 1 })) }));
jest.mock('expo-image-picker', () => ({ launchImageLibraryAsync: jest.fn(async () => ({ canceled: false, assets: [{ uri: 'file://me.jpg', fileName: 'me.jpg', mimeType: 'image/jpeg' }] })) }));
jest.mock('expo-router', () => ({ useRouter: () => ({ push: jest.fn(), replace: jest.fn(), back: jest.fn() }), useLocalSearchParams: () => ({}) }));
jest.mock('@/me/profileAvatar', () => ({ deviceAvatarKey: (id: string) => `avatar-${id}`, loadProfileAvatar: jest.fn(async () => null) }));
jest.mock('@/plan/PlanProvider', () => ({ usePlan: () => ({ draft: {}, update: jest.fn(), reset: jest.fn() }) }));
jest.mock('@/layout/useLayout', () => ({ useLayout: () => ({ kind: 'phone', desktop: false, width: 390, height: 844 }) }));
import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';
import { ProfileBody } from '@/me/panels/ProfileBody';

it('🔴 사진을 골라도 저장 전에는 올리지 않고, 저장을 누르면 그때 올려 계정에 붙인다', async () => {
  render(<OnboardingPreferencesProvider><ProfileBody /></OnboardingPreferencesProvider>);
  fireEvent.press(await screen.findByText('사진 바꾸기'));
  await waitFor(() => expect(screen.getByText('「저장」을 누르면 프로필 사진이 바뀌어요.')).toBeTruthy());
  expect(mockUpload).not.toHaveBeenCalled();
  expect(mockUpdateProfile).not.toHaveBeenCalled();
  await act(async () => { fireEvent.press(screen.getByText('저장')); });
  await waitFor(() => expect(mockUpdateProfile).toHaveBeenCalledWith({ avatarUrl: 'https://x/a.jpg' }));
  expect(mockUpload).toHaveBeenCalledTimes(1);
});
