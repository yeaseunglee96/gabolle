// 사진 위 글자가 묻히지 않는가 — S15P21E201-1203.
import { render } from '@testing-library/react-native';
import { StyleSheet } from 'react-native';

const mockPlace = {
  placeId: 'p1',
  nameKo: '영도다리축제',
  nameEn: 'Yeongdo Bridge Festival',
  category: 'FESTIVAL',
  address: '부산광역시 영도구 해양로301번길 45 (동삼동)',
  lat: 35.07,
  lng: 129.07,
  features: [],
  photoUrl: 'https://example.test/hero.jpg',
  photoSource: '한국관광공사 공공누리 제3유형',
};

jest.mock('expo-router', () => ({
  useRouter: () => ({ back: jest.fn(), replace: jest.fn(), push: jest.fn(), canGoBack: () => true }),
  useLocalSearchParams: () => ({ id: 'p1' }),
  usePathname: () => '/place/p1',
}));
jest.mock('@/discovery/places', () => {
  const actual = jest.requireActual('@/discovery/places');
  return { ...actual, getPlace: jest.fn(async () => mockPlace) };
});
// 이 화면은 소리·지도까지 끌고 온다. 여기서 재는 것은 사진 위 글자라 그쪽은 흔든다.
jest.mock('@/components/PlacePhraseModal', () => ({ PlacePhraseModal: () => null }));
jest.mock('@/auth/AuthProvider', () => ({ useAuth: () => ({ user: null, accessToken: null, ready: true }) }));
jest.mock('@/layout/useLayout', () => ({ useLayout: () => ({ kind: 'phone', width: 390, height: 844, isLandscape: false }) }));
jest.mock('react-native-safe-area-context', () => ({
  SafeAreaView: ({ children }: { children: unknown }) => children,
  useSafeAreaInsets: () => ({ top: 47, left: 0, right: 0, bottom: 34 }),
}));

import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';
import PlaceDetail from '../place/[id]';

const mount = () => render(<OnboardingPreferencesProvider><PlaceDetail /></OnboardingPreferencesProvider>);

/** 여러 겹으로 들어온 style 을 하나로 눌러 본다 — 배열이든 아니든 같게 읽는다. */
function flat(style: unknown): Record<string, unknown> {
  return (StyleSheet.flatten(style as never) ?? {}) as Record<string, unknown>;
}

describe('장소 사진 위 글자 — 공공누리 출처가 읽혀야 한다', () => {
  it('🔴 사진이 있으면 출처 줄이 그려진다 — 이용 조건이다', async () => {
    const view = mount();
    const credit = await view.findByTestId('place-photo-credit');
    expect(credit).toBeTruthy();
  });

  it('🔴 출처 줄을 흐리게 하지 않는다 — opacity 가 다시 붙으면 여기서 걸린다', async () => {
    const view = mount();
    const credit = await view.findByTestId('place-photo-credit');
    const style = flat(credit.props.style);
    // undefined(안 정함) 이거나 1(불투명) 이어야 한다. 0.8 같은 값이 들어오면 실패한다.
    expect(style.opacity === undefined || style.opacity === 1).toBe(true);
  });

  it('🔴 사진 위 글자에는 그림자가 있다 — 배경이 어떤 색이든 윤곽이 선다', async () => {
    const view = mount();
    const credit = await view.findByTestId('place-photo-credit');
    const style = flat(credit.props.style);
    expect(style.textShadowColor).toBeTruthy();
    expect(Number(style.textShadowRadius)).toBeGreaterThan(0);
  });
});
