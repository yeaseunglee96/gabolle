// 장소 상세 사진 영역 — 긴 이름이 「›」 단추·「1/15」 쪽 번호와 겹치던 것(S15P21E201-2060, 2026-10-16 웹 점검).
//
// 🔴 사진이 여러 장이면 사진 판 오른쪽에 쪽 번호 알약(오른쪽 위)과 「›」 단추(웹, 세로 가운데)가 떠 있다. 이름은 사진 아래에 놓이는데
//    긴 이름(「수변최고돼지국밥」 · 「Gamcheon Culture Village」)이 두 줄로 꺾이면 단추 높이까지 올라와 글자가 단추 밑에 깔렸다.
//    이름·주소는 오른쪽을 비워 단추·쪽 번호 왼쪽에서 꺾이게 한다 — 사진이 한 장이면 그것들이 없으니 비우지 않는다.
//    글자가 실제로 안 겹치는지는 화면을 직접 띄워 본다(frontend/CLAUDE.md) — 이 시험은 그 여백이 걸려 있는지만 지킨다.
import { StyleSheet } from 'react-native';
import { render } from '@testing-library/react-native';

const basePlace = {
  placeId: 'p1',
  nameKo: '수변최고돼지국밥',
  nameEn: null,
  category: 'FOOD',
  address: '부산광역시 수영구 광안해변로 219번길 8',
  lat: 35.15,
  lng: 129.12,
  features: [],
  photoUrl: 'https://example.test/0.jpg',
  photoSource: '한국관광공사 공공누리 제1유형',
};
let mockPlace: Record<string, unknown> = basePlace;

jest.mock('expo-router', () => ({
  useRouter: () => ({ back: jest.fn(), replace: jest.fn(), push: jest.fn(), canGoBack: () => true }),
  useLocalSearchParams: () => ({ id: 'p1' }),
  usePathname: () => '/place/p1',
}));
jest.mock('@/discovery/places', () => {
  const actual = jest.requireActual('@/discovery/places');
  return { ...actual, getPlace: jest.fn(async () => mockPlace) };
});
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
const photos = [
  { url: 'https://example.test/0.jpg', source: '한국관광공사 공공누리 제1유형' },
  { url: 'https://example.test/1.jpg', source: '출처 : 부산관광아카이브' },
];
const paddingRightOf = (node: { props: { style?: unknown } }) => (StyleSheet.flatten(node.props.style as never) as { paddingRight?: number } | undefined)?.paddingRight ?? 0;

// 「›」 단추는 사진 오른쪽에서 8 떨어져 폭 36 이다 — 왼쪽 끝이 44. 글자 오른쪽 끝은 거기서 12 이상 안쪽(= 56)이어야 한다.
// (쪽 번호 알약은 오른쪽 12 + 폭 약 40~50 이라 이 안쪽에 든다.) heroCopy 의 안쪽 여백 16 은 따로 있다.
const MIN_TEXT_RIGHT_EDGE = 8 + 36 + 12;
const HERO_COPY_PADDING = 16;

describe('사진이 여러 장이면 이름·주소의 오른쪽을 비운다', () => {
  it('🔴 이름 · 주소 · 한국어 주소가 단추·쪽 번호 왼쪽에서 꺾이도록 오른쪽 여백이 있다', async () => {
    mockPlace = { ...basePlace, photos };
    const view = mount();
    await view.findByTestId('place-photo-gallery');

    const title = view.getByText('수변최고돼지국밥');
    const subtitle = view.getByText('부산광역시 수영구 광안해변로 219번길 8');
    for (const node of [title, subtitle]) {
      expect(HERO_COPY_PADDING + paddingRightOf(node)).toBeGreaterThanOrEqual(MIN_TEXT_RIGHT_EDGE);
    }
  });

  it('출처 줄은 맨 아래라 단추와 안 겹친다 — 여백을 주지 않는다', async () => {
    mockPlace = { ...basePlace, photos };
    const view = mount();
    const credit = await view.findByTestId('place-photo-credit');
    expect(paddingRightOf(credit)).toBe(0);
  });

  it('🔴 사진이 한 장이면 쪽 번호도 단추도 없다 — 이름이 전과 같은 폭을 쓴다', async () => {
    mockPlace = basePlace;
    const view = mount();
    const title = await view.findByText('수변최고돼지국밥');
    expect(view.queryByTestId('place-photo-counter')).toBeNull();
    expect(paddingRightOf(title)).toBe(0);
  });
});
