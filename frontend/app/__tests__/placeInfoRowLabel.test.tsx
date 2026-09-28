// 장소 상세 정보 줄 — 값이 길어도 이름표가 눌리지 않는다(S15P21E201-1727).
//
// 🔴 2026-09-26 발표 시연 점검(폰 390). 국제시장 상세의 「영업시간」이 한 글자씩 세로로 꺾였다(영/업/시/간,
//    영어는 H/o/ur/s). 정보 줄은 이름표와 값이 가로로 놓이는데 둘 다 줄어들 수 있어서, 요일별 영업시간
//    여섯 개가 붙은 긴 값이 이름표 칸을 한 글자 폭까지 밀어냈다. 넓은 화면은 값이 한 줄에 들어가 안 보인다.
//    jest 에는 배치 계산이 없으므로 «누가 줄어드는가» 설정을 직접 본다 — 이름표는 안 줄고 값만 준다.
import AsyncStorage from '@react-native-async-storage/async-storage';
import { render, screen, waitFor } from '@testing-library/react-native';
import { StyleSheet } from 'react-native';

const LONG_HOURS = '월 09:00~20:00 · 화 09:00~20:00 · 수 09:00~20:00 · 목 09:00~20:00 · 금 09:00~20:00 · 토 09:00~20:00';
const mockPlace = {
  placeId: 'market-1', nameKo: '국제시장', nameEn: null, category: 'MARKET',
  address: '부산광역시 중구 신창로4가 일원', addressEn: null, lat: 35.1, lng: 129.03, photoUrl: null, photoSource: null,
  features: [],
  openingHours: { value: { raw: LONG_HOURS }, evidenceStatus: 'VERIFIED' },
};

jest.mock('@/analytics/appEvents', () => ({ sendAppEvent: jest.fn() }));
jest.mock('expo-router', () => ({
  useRouter: () => ({ back: jest.fn(), replace: jest.fn(), push: jest.fn(), canGoBack: () => true }),
  useLocalSearchParams: () => ({ id: 'market-1' }),
  usePathname: () => '/place/market-1',
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

describe('장소 상세 정보 줄 — 긴 값이 이름표를 누르지 않는다', () => {
  afterEach(async () => { await AsyncStorage.clear(); });

  it.each([['ko', '영업시간'], ['en', 'Hours']])('🔴 %s — 이름표 「%s」는 줄어들지 않고, 값만 줄어든다', async (language, label) => {
    await AsyncStorage.setItem('gabolle:onboarding-preferences', JSON.stringify({ language, mobility: 'none', hasEnteredApp: true }));
    render(<OnboardingPreferencesProvider><PlaceDetail /></OnboardingPreferencesProvider>);
    await waitFor(() => expect(screen.getByText(label)).toBeTruthy());
    expect(StyleSheet.flatten(screen.getByText(label).props.style)?.flexShrink).toBe(0);
    const value = screen.getByText(new RegExp('09:00~20:00'));
    expect(StyleSheet.flatten(value.props.style)?.flexShrink).toBe(1);
  });
});
