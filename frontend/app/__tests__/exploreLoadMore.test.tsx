// 탐색 화면의 갈래 목록이 20곳에서 끊기던 것 — S15P21E201-2060 (2026-10-16 웹 점검).
//
// 🔴 축제는 76곳인데 카드가 20장뿐이었다(전통시장 33 · 액티비티 33 · 자연 27 도 같았다). 서버가 hasNext:true 에 nextCursor:null 을 줬고,
//    앱은 첫 쪽만 받고 뒤를 버렸다. 이제 서버가 커서를 주고(백엔드 S15P21E201-2060) 화면은 «더 보기»로 끝까지 이어 받는다.
//
// 🔴 지키는 것
//   ① 다음 쪽이 있으면 「더 보기」가 있고, 누르면 받은 커서로 다음 쪽을 불러 카드가 뒤에 붙는다 — 76곳이 20·20·20·16 으로 다 보인다.
//   ② 끝에 닿으면 「더 보기」가 사라진다. 옛 서버(커서 없음)면 처음부터 안 그린다.
//   ③ 이어 받기가 실패하면 쌓인 카드는 그대로 두고 「더 불러오지 못했어요.」 + 다시 누를 수 있는 단추.
//   ④ 갈래를 바꾸면 앞 갈래의 늦은 답이 새 목록 뒤에 붙지 않는다.
import { act, configure, fireEvent, render, screen, waitFor } from '@testing-library/react-native';

import LocalExplore from '../explore';
import { getFacets } from '@/discovery/localExplore';
import { getPlacesByFacetPage } from '@/discovery/places';
import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';
import { syncLocationConsent } from '@/personalization/locationConsent';

// 지도 보기(S15P21E201-2068)의 지도는 웹뷰를 불러온다 — 이 시험은 목록만 본다.
jest.mock('@/map/RouteMap', () => ({ RouteMap: () => null }));
jest.mock('expo-router', () => ({
  useRouter: () => ({ push: jest.fn(), replace: jest.fn(), back: jest.fn(), canGoBack: () => true }),
  useLocalSearchParams: () => ({}),
}));
jest.mock('@/auth/AuthProvider', () => ({ useAuth: () => ({ accessToken: 'token', ready: true }) }));
jest.mock('@/components/BrandLogoLink', () => ({ BrandLogoLink: () => null }));
jest.mock('@/components/Screen', () => ({
  Screen: ({ children }: { children: unknown }) => { const { ScrollView } = jest.requireActual('react-native'); return <ScrollView>{children as never}</ScrollView>; },
}));
jest.mock('@/discovery/SuggestPlaceLink', () => ({ SuggestPlaceLink: () => null }));
jest.mock('expo-location', () => ({ getForegroundPermissionsAsync: jest.fn(), requestForegroundPermissionsAsync: jest.fn() }));
jest.mock('@/location/currentPosition', () => ({ readCurrentPosition: jest.fn() }));
jest.mock('@/personalization/locationConsent', () => ({ ...jest.requireActual('@/personalization/locationConsent'), syncLocationConsent: jest.fn() }));
jest.mock('@/personalization/useLocationGate', () => ({ useLocationGate: () => ({ request: jest.fn(), ensure: jest.fn(), sheet: null }) }));
jest.mock('@/discovery/localExplore', () => ({ ...jest.requireActual('@/discovery/localExplore'), getFacets: jest.fn(), getNearbyPlaces: jest.fn() }));
jest.mock('@/discovery/localPlaces', () => ({ ...jest.requireActual('@/discovery/localPlaces'), getAllLocalPlaces: jest.fn(async () => []), getNearbyLocalPlaces: jest.fn() }));
jest.mock('@/discovery/places', () => ({ ...jest.requireActual('@/discovery/places'), getPlacesByFacetPage: jest.fn() }));
jest.mock('@/discovery/localRestaurants', () => ({ ...jest.requireActual('@/discovery/localRestaurants'), getLocalAreas: jest.fn(), getLocalList: jest.fn() }));

// 느린 PC·CI 에서는 첫 화면을 그리는 데만 1초가 넘게 걸린다(exploreLocalKinds.test.tsx 와 같은 까닭).
configure({ asyncUtilTimeout: 8000 });
jest.setTimeout(30000);

const mockedFacets = getFacets as jest.Mock;
const mockedPage = getPlacesByFacetPage as jest.Mock;

const place = (name: string) => ({ placeId: `id-${name}`, nameKo: name, nameEn: null, category: 'FESTIVAL_EVENT', address: '부산 시험로 1', lat: 35.1, lng: 129.0 });
/** 「축제01」 … 만큼 n 곳. */
const festivals = (from: number, count: number) => Array.from({ length: count }, (_, index) => place(`축제${String(from + index).padStart(2, '0')}`));
const cardCount = () => screen.queryAllByLabelText(/상세 보기$/).length;

function renderScreen() {
  return render(<OnboardingPreferencesProvider><LocalExplore /></OnboardingPreferencesProvider>);
}

beforeEach(() => {
  jest.clearAllMocks();
  (syncLocationConsent as jest.Mock).mockResolvedValue(null);
  mockedFacets.mockResolvedValue({
    state: 'success',
    facets: [{
      userInputCode: 'EXPLORE', placeFeatureType: 'INTEREST_TAG', matchKind: 'TAG_OVERLAP', placeCount: 0,
      keys: [
        { featureKey: 'FESTIVAL', placeCount: 76, labelKo: '축제' },
        { featureKey: 'WALK', placeCount: 17, labelKo: '산책' },
      ],
    }],
  });
});

describe('갈래 목록 「더 보기」', () => {
  it('🔴 축제 76곳이 20 · 20 · 20 · 16 으로 이어져 다 보이고, 끝에서 「더 보기」가 사라진다', async () => {
    mockedPage
      .mockResolvedValueOnce({ items: festivals(1, 20), nextCursor: 'c1' })
      .mockResolvedValueOnce({ items: festivals(21, 20), nextCursor: 'c2' })
      .mockResolvedValueOnce({ items: festivals(41, 20), nextCursor: 'c3' })
      .mockResolvedValueOnce({ items: festivals(61, 16), nextCursor: null });
    renderScreen();
    fireEvent.press(await screen.findByText('축제'));

    await waitFor(() => expect(cardCount()).toBe(20));
    for (const expected of [40, 60, 76]) {
      fireEvent.press(await screen.findByTestId('explore-load-more'));
      await waitFor(() => expect(cardCount()).toBe(expected));
    }

    expect(screen.queryByTestId('explore-load-more')).toBeNull();
    // 받은 커서를 순서대로 되돌려 보냈다 — 첫 쪽은 커서 없이.
    expect(mockedPage.mock.calls.map((call) => [call[1], call[2], call[3]])).toEqual([
      ['FESTIVAL', 20, null], ['FESTIVAL', 20, 'c1'], ['FESTIVAL', 20, 'c2'], ['FESTIVAL', 20, 'c3'],
    ]);
    expect(screen.getByLabelText('축제76 상세 보기')).toBeTruthy();
  });

  it('🔴 옛 서버(커서가 없다)면 처음부터 「더 보기」를 그리지 않는다 — 눌러도 안 되는 단추는 없는 것보다 나쁘다', async () => {
    mockedPage.mockResolvedValueOnce({ items: festivals(1, 20), nextCursor: null });
    renderScreen();
    fireEvent.press(await screen.findByText('축제'));

    await waitFor(() => expect(cardCount()).toBe(20));
    expect(screen.queryByTestId('explore-load-more')).toBeNull();
  });

  it('🔴 이어 받기가 실패하면 쌓인 카드는 그대로 두고 안내와 다시 누를 단추를 둔다 — 다시 누르면 이어진다', async () => {
    mockedPage
      .mockResolvedValueOnce({ items: festivals(1, 20), nextCursor: 'c1' })
      .mockRejectedValueOnce(new Error('network'))
      .mockResolvedValueOnce({ items: festivals(21, 5), nextCursor: null });
    renderScreen();
    fireEvent.press(await screen.findByText('축제'));
    await waitFor(() => expect(cardCount()).toBe(20));

    fireEvent.press(await screen.findByTestId('explore-load-more'));
    expect(await screen.findByText('더 불러오지 못했어요.')).toBeTruthy();
    expect(cardCount()).toBe(20);

    fireEvent.press(screen.getByTestId('explore-load-more'));
    await waitFor(() => expect(cardCount()).toBe(25));
    expect(screen.queryByText('더 불러오지 못했어요.')).toBeNull();
    expect(screen.queryByTestId('explore-load-more')).toBeNull();
  });

  it('같은 장소가 두 쪽에 걸려 와도 한 번만 그린다 — 쪽 사이에 자료가 바뀌었을 때', async () => {
    mockedPage
      .mockResolvedValueOnce({ items: festivals(1, 20), nextCursor: 'c1' })
      .mockResolvedValueOnce({ items: [place('축제20'), ...festivals(21, 3)], nextCursor: null });
    renderScreen();
    fireEvent.press(await screen.findByText('축제'));
    await waitFor(() => expect(cardCount()).toBe(20));

    fireEvent.press(await screen.findByTestId('explore-load-more'));

    await waitFor(() => expect(cardCount()).toBe(23));
  });

  it('🔴 갈래를 바꾸면 앞 갈래의 늦은 답이 새 목록 뒤에 붙지 않는다', async () => {
    let releaseOld: (value: { items: ReturnType<typeof festivals>; nextCursor: string | null }) => void = () => undefined;
    mockedPage.mockImplementation((_type: string, key: string, _limit: number, cursor: string | null) => {
      if (key === 'FESTIVAL' && !cursor) return Promise.resolve({ items: festivals(1, 20), nextCursor: 'c1' });
      if (key === 'FESTIVAL') return new Promise((resolve) => { releaseOld = resolve as never; });
      return Promise.resolve({ items: [place('산책로1'), place('산책로2')], nextCursor: null });
    });
    renderScreen();
    fireEvent.press(await screen.findByText('축제'));
    await waitFor(() => expect(cardCount()).toBe(20));
    fireEvent.press(await screen.findByTestId('explore-load-more')); // 늦게 오는 답을 기다리는 중

    fireEvent.press(await screen.findByText('산책'));
    await waitFor(() => expect(screen.queryByLabelText('산책로1 상세 보기')).toBeTruthy());
    await act(async () => { releaseOld({ items: festivals(21, 20), nextCursor: null }); });

    expect(cardCount()).toBe(2);
    expect(screen.queryByLabelText('축제21 상세 보기')).toBeNull();
  });
});
