// 로컬 탐색 화면의 맛집·카페 칩 (S15P21E201-2059) — 칩 순서 · 칩을 누를 때의 위치 동의 · 위치를 읽으면 구를 미리 고르는 길.
//
// 🔴 지키는 것
//   ① 카페 칩은 맛집 칩 바로 뒤다 — 서버가 글자순으로(카페가 앞) 줘도.
//   ② 맛집·카페 칩을 누르면 위치 동의를 «물은 적이 없을 때만» 묻는다(ensure). 「내 위치로」 버튼의 request(거절했어도 다시 묻는다)가 아니다 —
//      칩은 갈래를 고르는 손짓이지 위치를 쓰겠다는 말이 아니라서, 거절한 사람에게 칩을 누를 때마다 동의 창을 띄우면 안 된다.
//   ③ 위치를 읽으면 그 좌표를 맛집·카페 본문에 넘겨 가장 가까운 구를 미리 고른다. 이미 동의해서 위치를 읽어 둔 사람은 묻지도 않는다.
//   ④ 거부·실패하면 예전 그대로 «먼저 구를 골라 주세요».
import { configure, fireEvent, render, screen, waitFor } from '@testing-library/react-native';
import * as Location from 'expo-location';

import LocalExplore from '../explore';
import { getFacets } from '@/discovery/localExplore';
import { getAllLocalPlaces } from '@/discovery/localPlaces';
import { getLocalAreas, getLocalList, type RestaurantGu } from '@/discovery/localRestaurants';
import { readCurrentPosition } from '@/location/currentPosition';
import { OnboardingPreferencesProvider } from '@/onboarding/OnboardingPreferences';
import { syncLocationConsent } from '@/personalization/locationConsent';

const mockEnsure = jest.fn();
const mockRequest = jest.fn();

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
jest.mock('@/personalization/useLocationGate', () => ({ useLocationGate: () => ({ request: mockRequest, ensure: mockEnsure, sheet: null }) }));
jest.mock('@/discovery/localExplore', () => ({ ...jest.requireActual('@/discovery/localExplore'), getFacets: jest.fn(), getNearbyPlaces: jest.fn() }));
jest.mock('@/discovery/localPlaces', () => ({ ...jest.requireActual('@/discovery/localPlaces'), getAllLocalPlaces: jest.fn(), getNearbyLocalPlaces: jest.fn() }));
jest.mock('@/discovery/places', () => ({ ...jest.requireActual('@/discovery/places'), getPlacesByFacet: jest.fn() }));
jest.mock('@/discovery/localRestaurants', () => ({ ...jest.requireActual('@/discovery/localRestaurants'), getLocalAreas: jest.fn(), getLocalList: jest.fn() }));

// 🔴 느린 PC·CI(여러 잡이 한 기계에서 돈다)에서는 첫 화면을 그리는 데만 1초가 넘게 걸린다 — 기본 대기 1초로는 모자라다.
configure({ asyncUtilTimeout: 8000 });
jest.setTimeout(30000);

const HAEUNDAE = { latitude: 35.1631, longitude: 129.1636 };

const AREAS: RestaurantGu[] = [
  { gu: '해운대구', placeCount: 3, dongs: [{ dong: '우동', placeCount: 2 }, { dong: '중동', placeCount: 1 }] },
  { gu: '중구', placeCount: 1, dongs: [{ dong: '남포동', placeCount: 1 }] },
];

const item = (placeId: string, nameKo: string) => ({
  placeId, nameKo, nameEn: null, category: 'CAFE_HEALING', address: '해운대구 시험로 1', lat: 35.1, lng: 129.1, gu: '해운대구', dong: '우동', certifiedSources: ['KTO_CAFE'],
});

const mockedFacets = getFacets as jest.Mock;
const mockedAreas = getLocalAreas as jest.Mock;
const mockedList = getLocalList as jest.Mock;

function renderScreen() {
  return render(<OnboardingPreferencesProvider><LocalExplore /></OnboardingPreferencesProvider>);
}

/** 화면에 그려진 글자 조각을 나타난 순서대로 — 칩 순서를 보려는 것이다. */
function textsInOrder(node: unknown, out: string[] = []): string[] {
  if (typeof node === 'string') out.push(node);
  else if (Array.isArray(node)) node.forEach((child) => textsInOrder(child, out));
  else if (node && typeof node === 'object' && 'children' in node) textsInOrder((node as { children: unknown }).children, out);
  return out;
}

beforeEach(() => {
  jest.clearAllMocks();
  mockedFacets.mockResolvedValue({
    state: 'success',
    // 🔴 글자순으로 온 응답 — LOCAL_CAFE 가 LOCAL_RESTAURANT 앞이다.
    facets: [{
      userInputCode: 'EXPLORE', placeFeatureType: 'INTEREST_TAG', matchKind: 'TAG_OVERLAP', placeCount: 0,
      keys: [
        { featureKey: 'WALK', placeCount: 3, labelKo: '산책' },
        { featureKey: 'LOCAL_CAFE', placeCount: 262, labelKo: '카페' },
        { featureKey: 'LOCAL_RESTAURANT', placeCount: 697, labelKo: '맛집' },
      ],
    }],
  });
  (getAllLocalPlaces as jest.Mock).mockResolvedValue([]);
  mockedAreas.mockResolvedValue({ areas: AREAS, nearestGu: null });
  mockedList.mockResolvedValue({ items: [item('a', '가카페'), item('b', '나카페')], hasNext: false });
  // 기본은 «위치를 쓰기로 한 적 없음» — 화면을 열어도 조용히 읽지 않는다.
  (syncLocationConsent as jest.Mock).mockResolvedValue(null);
  (Location.getForegroundPermissionsAsync as jest.Mock).mockResolvedValue({ granted: false, canAskAgain: true });
  (Location.requestForegroundPermissionsAsync as jest.Mock).mockResolvedValue({ granted: true, canAskAgain: true });
  (readCurrentPosition as jest.Mock).mockResolvedValue({ coords: HAEUNDAE });
  mockEnsure.mockResolvedValue(true);
  mockRequest.mockResolvedValue(true);
});

describe('칩 순서', () => {
  it('🔴 카페 칩은 맛집 칩 바로 뒤다 — 서버가 글자순(카페가 앞)으로 줘도', async () => {
    renderScreen();
    await screen.findByText('맛집');

    const texts = textsInOrder(screen.toJSON());
    expect(texts.indexOf('맛집')).toBeGreaterThan(-1);
    expect(texts.indexOf('카페')).toBe(texts.indexOf('맛집') + 2); // 맛집 칩의 건수 글자 하나가 사이에 있다
    // 일반 갈래(산책)는 그 앞에 그대로다.
    expect(texts.indexOf('산책')).toBeLessThan(texts.indexOf('맛집'));
  });

  it('맛집·카페를 고르면 범위 토글(내 근처/부산 전체)이 사라지고 구 → 동이 범위를 대신한다', async () => {
    renderScreen();
    expect(await screen.findByText('내 근처')).toBeTruthy();

    fireEvent.press(await screen.findByText('카페'));

    await screen.findByText('구 고르기');
    expect(screen.queryByText('내 근처')).toBeNull();
    expect(screen.getByText('현지인이 추천했거나 공공기관이 소개한 카페만 보여 드려요. 구와 동을 차례로 골라 보세요.')).toBeTruthy();
  });
});

describe('칩을 누르면 위치 동의를 «묻는다(ensure)»', () => {
  it('🔴 처음 눌렀을 때 한 번 묻고(request 가 아니다), 위치를 읽으면 그 좌표로 가장 가까운 구를 미리 골라 그 구의 전체 목록을 보여 준다', async () => {
    mockedAreas.mockImplementation(async (_kind: string, options: { coords?: unknown } = {}) => ({ areas: AREAS, nearestGu: options.coords ? '해운대구' : null }));
    renderScreen();

    fireEvent.press(await screen.findByText('맛집'));

    await waitFor(() => expect(mockedList).toHaveBeenCalled());
    expect(mockEnsure).toHaveBeenCalledTimes(1);
    expect(mockRequest).not.toHaveBeenCalled();
    // 좌표로 서버를 불렀고(맛집), 그 구의 전체(동 없이)를 불렀다.
    const located = mockedAreas.mock.calls.filter((call) => call[1]?.coords);
    expect(located.length).toBeGreaterThan(0);
    expect(located[0][0]).toBe('RESTAURANT');
    expect(located[0][1].coords).toEqual(HAEUNDAE);
    expect(mockedList.mock.calls[0][0]).toBe('RESTAURANT');
    expect(mockedList.mock.calls[0][1]).toEqual({ gu: '해운대구', dong: null });
    expect(await screen.findByText('내 위치와 가까운 구(해운대구)를 골랐어요. 다른 구를 눌러도 돼요.')).toBeTruthy();
    expect(screen.getByText('✓ 해운대구')).toBeTruthy();
    // 소개한 목록 배지가 카드에 달린다.
    expect((await screen.findAllByText('관광공사 추천')).length).toBe(2);
  });

  it('🔴 카페 칩도 같다 — 이미 위치를 읽었으니 다시 묻지 않고, 카페로 서버를 부른다', async () => {
    mockedAreas.mockImplementation(async (_kind: string, options: { coords?: unknown } = {}) => ({ areas: AREAS, nearestGu: options.coords ? '해운대구' : null }));
    renderScreen();
    fireEvent.press(await screen.findByText('맛집'));
    await waitFor(() => expect(mockedList).toHaveBeenCalled());

    mockedAreas.mockClear();
    mockedList.mockClear();
    fireEvent.press(await screen.findByText('카페'));

    await waitFor(() => expect(mockedList).toHaveBeenCalled());
    expect(mockEnsure).toHaveBeenCalledTimes(1);
    expect(mockedAreas.mock.calls[0][0]).toBe('CAFE');
    expect(mockedAreas.mock.calls[0][1].coords).toEqual(HAEUNDAE);
    expect(mockedList.mock.calls[0][0]).toBe('CAFE');
    expect(mockedList.mock.calls[0][1]).toEqual({ gu: '해운대구', dong: null });
  });

  it('🔴 동의를 거절하면 위치를 읽지 않고, 예전 그대로 «먼저 구를 골라 주세요» — 칩을 다시 눌러도 조르지 않는다', async () => {
    mockEnsure.mockResolvedValue(false);
    renderScreen();

    fireEvent.press(await screen.findByText('맛집'));
    expect(await screen.findByText('먼저 구를 골라 주세요.')).toBeTruthy();
    fireEvent.press(await screen.findByText('카페'));
    await screen.findByText('먼저 구를 골라 주세요.');

    expect(mockEnsure).toHaveBeenCalledTimes(1);
    expect(mockRequest).not.toHaveBeenCalled();
    expect(readCurrentPosition).not.toHaveBeenCalled();
    expect(mockedList).not.toHaveBeenCalled();
    // 좌표 없이 불렀다.
    expect(mockedAreas.mock.calls.every((call) => !call[1]?.coords)).toBe(true);
  });

  it('기기 권한을 거부하면(동의는 했어도) 위치를 못 읽고 예전 그대로다', async () => {
    (Location.requestForegroundPermissionsAsync as jest.Mock).mockResolvedValue({ granted: false, canAskAgain: false });
    renderScreen();

    fireEvent.press(await screen.findByText('맛집'));
    expect(await screen.findByText('먼저 구를 골라 주세요.')).toBeTruthy();

    expect(readCurrentPosition).not.toHaveBeenCalled();
    expect(mockedList).not.toHaveBeenCalled();
  });

  it('위치를 읽다가 실패해도 예전 그대로다 — 오류 화면으로 바뀌지 않는다', async () => {
    (readCurrentPosition as jest.Mock).mockRejectedValue(new Error('position timeout'));
    renderScreen();

    fireEvent.press(await screen.findByText('맛집'));
    expect(await screen.findByText('먼저 구를 골라 주세요.')).toBeTruthy();
    expect(screen.getByText('해운대구')).toBeTruthy();
    expect(mockedList).not.toHaveBeenCalled();
  });

  it('🔴 이미 동의하고 권한도 준 사람은 화면을 열 때 조용히 읽어 둔 위치를 쓴다 — 칩을 눌러도 다시 묻지 않는다', async () => {
    (syncLocationConsent as jest.Mock).mockResolvedValue(true);
    (Location.getForegroundPermissionsAsync as jest.Mock).mockResolvedValue({ granted: true, canAskAgain: true });
    mockedAreas.mockImplementation(async (_kind: string, options: { coords?: unknown } = {}) => ({ areas: AREAS, nearestGu: options.coords ? '중구' : null }));
    renderScreen();
    await waitFor(() => expect(readCurrentPosition).toHaveBeenCalled());

    fireEvent.press(await screen.findByText('맛집'));

    await waitFor(() => expect(mockedList).toHaveBeenCalled());
    expect(mockEnsure).not.toHaveBeenCalled();
    expect(mockRequest).not.toHaveBeenCalled();
    expect(mockedList.mock.calls[0][1]).toEqual({ gu: '중구', dong: null });
  });
});
