// 장소 상세 사진을 여러 장 넘겨 본다 — S15P21E201-1839.
// 🔴 서버가 photos 를 안 보내는 판(옛 응답)에서도 예전처럼 한 장이 그려져야 한다 — 그것도 여기서 본다.
import { fireEvent, render } from '@testing-library/react-native';

const basePlace = {
  placeId: 'p1',
  nameKo: '해동용궁사',
  nameEn: 'Haedong Yonggungsa Temple',
  category: 'ATTRACTION',
  address: '부산광역시 기장군 기장읍 용궁길 86',
  lat: 35.18,
  lng: 129.22,
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

const threePhotos = [
  { url: 'https://example.test/0.jpg', source: '한국관광공사 공공누리 제1유형' },
  { url: 'https://example.test/1.jpg', source: '출처 : 부산관광아카이브', license: { name: '공공누리 제1유형', url: null, filePage: 'https://archive.visitbusan.net/file/1' } },
  { url: 'https://example.test/2.jpg', source: 'Google 지도 · 사진 홍길동' },
];

describe('장소 상세 사진 여러 장', () => {
  it('🔴 photos 가 세 장이면 세 쪽을 그리고 「1/3」을 보인다', async () => {
    mockPlace = { ...basePlace, photos: threePhotos };
    const view = mount();
    await view.findByTestId('place-photo-gallery');
    expect(view.getAllByTestId('place-photo-page')).toHaveLength(3);
    expect(view.getByTestId('place-photo-counter').props.children).toBe('1/3');
    expect(view.getByTestId('place-photo-credit').props.children).toBe('사진: 한국관광공사 공공누리 제1유형');
    // 넘길 수 있다는 표시 — 사진 수만큼 점(화면 낭독에서는 숨긴다 — 「사진 1/3」이 이미 말한다)
    expect(view.getByTestId('place-photo-dots', { includeHiddenElements: true }).props.children).toHaveLength(3);
  });

  it('🔴 넘기면 쪽 번호와 출처가 그 사진의 것으로 바뀐다 — 출처가 이름표를 달고 오면 「사진: 」을 또 붙이지 않는다', async () => {
    mockPlace = { ...basePlace, photos: threePhotos };
    const view = mount();
    const gallery = await view.findByTestId('place-photo-gallery');
    // 폭을 먼저 알려 준다 — 몇 번째 쪽인지를 폭으로 나눠 센다.
    fireEvent(gallery.parent!, 'layout', { nativeEvent: { layout: { width: 390, height: 240, x: 0, y: 0 } } });
    fireEvent.scroll(view.getByTestId('place-photo-gallery'), { nativeEvent: { contentOffset: { x: 390, y: 0 }, contentSize: { width: 1170, height: 240 }, layoutMeasurement: { width: 390, height: 240 } } });
    expect(view.getByTestId('place-photo-counter').props.children).toBe('2/3');
    const credit = view.getByTestId('place-photo-credit');
    expect(credit.props.children).toBe('출처 : 부산관광아카이브 · 공공누리 제1유형');
    // 파일 페이지가 있으면 누를 수 있는 링크다 — 예전 photoLicense 와 같은 동작.
    expect(credit.props.accessibilityRole).toBe('link');
  });

  it('🔴 photos 가 없으면(옛 응답) 예전처럼 photoUrl 한 장 — 쪽 번호 없이', async () => {
    mockPlace = basePlace;
    const view = mount();
    const credit = await view.findByTestId('place-photo-credit');
    expect(credit.props.children).toBe('사진: 한국관광공사 공공누리 제1유형');
    expect(view.queryByTestId('place-photo-gallery')).toBeNull();
    expect(view.queryByTestId('place-photo-counter')).toBeNull();
  });

  it('photos 가 한 장이면 넘길 것이 없다 — 쪽 번호 없이 그 한 장', async () => {
    mockPlace = { ...basePlace, photoUrl: undefined, photoSource: undefined, photos: [threePhotos[1]] };
    const view = mount();
    const credit = await view.findByTestId('place-photo-credit');
    expect(credit.props.children).toBe('출처 : 부산관광아카이브 · 공공누리 제1유형');
    expect(view.queryByTestId('place-photo-counter')).toBeNull();
  });
});
