// 장소 상세 「내 여행 후보에 저장」 — S15P21E201-1644.
//
// 🔴 이 시험이 지키는 것:
//    ① 「이미 저장했나」를 알기 전에는 단추를 누를 수 없다. 전에는 먼저 누르면 늦게 온 그 답이 방금 누른 표시를 되돌려서
//       「눌렀는데 아무 일도 없다」로 보였다.
//    ② 서버에 저장된 첫 하트에 「다음 추천에 반영할까요?」를 한 번 묻는다.
import AsyncStorage from '@react-native-async-storage/async-storage';
import { act, fireEvent, render, screen, waitFor } from '@testing-library/react-native';

const mockPlace = {
  placeId: 'p1', nameKo: '광안리해수욕장', nameEn: 'Gwangalli Beach', category: 'BEACH',
  address: '부산 수영구', lat: 35.15, lng: 129.12, features: [], photoUrl: null, photoSource: null,
};
let mockGetPlace: () => Promise<unknown> = async () => mockPlace;

jest.mock('@/analytics/appEvents', () => ({ sendAppEvent: jest.fn() }));
jest.mock('expo-router', () => ({
  useRouter: () => ({ back: jest.fn(), replace: jest.fn(), push: jest.fn(), canGoBack: () => true }),
  useLocalSearchParams: () => ({ id: 'p1' }),
  usePathname: () => '/place/p1',
}));
jest.mock('@/discovery/places', () => ({ ...jest.requireActual('@/discovery/places'), getPlace: jest.fn(() => mockGetPlace()) }));
jest.mock('@/discovery/savedPlaces', () => ({
  ...jest.requireActual('@/discovery/savedPlaces'),
  loadSavedPlaceIds: jest.fn(async () => []),
  setSavedPlace: jest.fn(async () => ({ sync: 'synced' })),
}));
jest.mock('@/auth/authApi', () => ({
  ...jest.requireActual('@/auth/authApi'),
  updateMyConsents: jest.fn(async () => ({})),
  getMyConsents: jest.fn(async () => ({ behaviorPersonalizationEnabled: false })),
}));
jest.mock('@/components/PlacePhraseModal', () => ({ PlacePhraseModal: () => null }));
jest.mock('@/auth/AuthProvider', () => ({ useAuth: () => ({ user: { userId: 'me' }, accessToken: 'tok', ready: true }) }));
jest.mock('@/layout/useLayout', () => ({ useLayout: () => ({ kind: 'phone', width: 390, height: 844, isLandscape: false }) }));
jest.mock('react-native-safe-area-context', () => ({
  SafeAreaView: ({ children }: { children: unknown }) => children,
  useSafeAreaInsets: () => ({ top: 47, left: 0, right: 0, bottom: 34 }),
}));

import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';
import { setBehaviorConsent } from '@/personalization/behaviorConsent';
import PlaceDetail from '../place/[id]';

const { loadSavedPlaceIds } = jest.requireMock('@/discovery/savedPlaces') as { loadSavedPlaceIds: jest.Mock };

const mount = () => render(<OnboardingPreferencesProvider><PlaceDetail /></OnboardingPreferencesProvider>);
const saveButton = () => screen.getByRole('button', { name: '내 여행 후보에 저장' });

beforeEach(async () => {
  mockGetPlace = async () => mockPlace;
  await AsyncStorage.clear();
  await setBehaviorConsent(false);
});

describe('장소 저장 단추', () => {
  it('🔴 저장했는지 알기 전에는 누를 수 없다 — 늦게 온 답이 누른 표시를 되돌리지 않게', async () => {
    let answer: (ids: string[]) => void = () => {};
    loadSavedPlaceIds.mockImplementationOnce(() => new Promise<string[]>((resolve) => { answer = resolve; }));
    mount();
    await waitFor(() => expect(saveButton().props.accessibilityState).toMatchObject({ disabled: true }));
    await act(async () => { answer([]); });
    await waitFor(() => expect(saveButton().props.accessibilityState).toMatchObject({ disabled: false }));
  });

  it('🔴 불러온 뒤 저장하면 다음 추천에 반영할지 한 번 묻는다', async () => {
    mount();
    await waitFor(() => expect(saveButton().props.accessibilityState).toMatchObject({ disabled: false }));
    await act(async () => { fireEvent.press(saveButton()); });
    await waitFor(() => expect(screen.getByText('하트·저장한 곳을 다음 추천에 반영할까요?')).toBeTruthy());
  });
});
