// 주소 없는 장소 상세 — 번역 함수에 null 을 넘기지 않는다(S15P21E201-1726, 1725 의 호출부 쪽).
//
// 🔴 주소가 없는 장소(OSM 출처 등, address=null)를 일본어·중국어로 열면 화면이 하얗게 죽었다 —
//    부제 줄이 tx(null, …) 를 불러 번역 함수의 일·중 갈래가 null.replace() 로 멈췄다(이예승 님 웹 실기).
//    번역 함수 쪽 방어(!1720)는 들어갔다. 이 시험은 호출부가 애초에 null 을 넘기지 않는지를 본다 —
//    그래서 번역 함수를 「문자열이 아니면 바로 실패」로 감싸 둔다.
import AsyncStorage from '@react-native-async-storage/async-storage';
import { render, screen, waitFor } from '@testing-library/react-native';

const mockPlace = {
  placeId: 'osm-1', nameKo: '이바구길 전망대', nameEn: 'Ibagu-gil Observatory', category: 'CULTURE_TEMPLE',
  address: null, addressEn: null, lat: 35.12, lng: 129.04, features: [], photoUrl: null, photoSource: null,
};

jest.mock('@/i18n/pick', () => {
  const actual = jest.requireActual('@/i18n/pick');
  return {
    ...actual,
    pickLanguage: (language: string, text: { ko: unknown; en: unknown }) => {
      if (typeof text.ko !== 'string' || typeof text.en !== 'string') throw new Error(`번역 함수가 문자열이 아닌 값을 받음: ${String(text.ko)}`);
      return actual.pickLanguage(language, text);
    },
  };
});
jest.mock('@/analytics/appEvents', () => ({ sendAppEvent: jest.fn() }));
jest.mock('expo-router', () => ({
  useRouter: () => ({ back: jest.fn(), replace: jest.fn(), push: jest.fn(), canGoBack: () => true }),
  useLocalSearchParams: () => ({ id: 'osm-1' }),
  usePathname: () => '/place/osm-1',
}));
jest.mock('@/discovery/places', () => {
  const actual = jest.requireActual('@/discovery/places');
  return { ...actual, getPlace: jest.fn(async () => mockPlace) };
});
jest.mock('@/components/PlacePhraseModal', () => ({ PlacePhraseModal: () => null }));
jest.mock('@/auth/AuthProvider', () => ({ useAuth: () => ({ user: { userId: 'me' }, accessToken: 'tok', ready: true }) }));
jest.mock('@/layout/useLayout', () => ({ useLayout: () => ({ kind: 'phone', width: 390, height: 844, isLandscape: false }) }));
jest.mock('react-native-safe-area-context', () => ({
  SafeAreaView: ({ children }: { children: unknown }) => children,
  useSafeAreaInsets: () => ({ top: 47, left: 0, right: 0, bottom: 34 }),
}));

import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';
import PlaceDetail from '../place/[id]';

describe('주소 없는 장소 상세 — 일본어·중국어로 열어도 그려진다', () => {
  it.each(['ja', 'zh-Hans', 'zh-Hant'])('🔴 %s — 번역 함수에 null 을 넘기지 않고, 이름은 그려진다', async (language) => {
    await AsyncStorage.setItem('gabolle:onboarding-preferences', JSON.stringify({ language, mobility: 'none', hasEnteredApp: true }));
    render(<OnboardingPreferencesProvider><PlaceDetail /></OnboardingPreferencesProvider>);
    await waitFor(() => expect(screen.getAllByText(/Ibagu-gil Observatory|이바구길 전망대/).length).toBeGreaterThan(0));
    expect(screen.queryByText('null')).toBeNull();
    await AsyncStorage.clear();
  });
});
