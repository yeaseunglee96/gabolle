// 근처 편의시설 화면 — «장소 기준»으로 열렸을 때 (S15P21E201-2052, 장소 상세의 「근처 주차장」 줄에서 들어온다).
// 🔴 지키는 것: ① 그 장소에서 찾고 «장소 근처에서 찾았다·거리도 그 장소에서 잰 것»이라고 밝힌다 ② 내 위치를 읽지도 묻지도 않는다
//    ③ 장소의 좌표는 줄이지 않고 보낸다 ④ «길 안내»는 없다(출발지가 내가 아니라 그 장소) ⑤ «내 위치로 찾기»가 성공해야 기준이 옮겨간다 —
//    거절하면 그 장소 기준 그대로다(서면으로 튀지 않는다) ⑥ 좌표가 올바르지 않으면 장소 기준이 아니다(내 위치 길).
import { act, configure, fireEvent, render, waitFor } from '@testing-library/react-native';
import * as Location from 'expo-location';
import { Linking } from 'react-native';

import NearbyFacilitiesScreen from '../field/nearby';
import { getNearbyFacilities, SEOMYEON, type FacilityItem, type FacilityKind, type NearbyFacilities } from '@/field/nearbyFacilities';
import { readCurrentPosition } from '@/location/currentPosition';
import { syncLocationConsent } from '@/personalization/locationConsent';
import { listAvailableMapApps } from '@/utils/externalMaps';

const mockPush = jest.fn();
const mockBack = jest.fn();
let mockParams: Record<string, string> = {};
let mockLang: 'ko' | 'en' = 'ko';
const mockRequest = jest.fn();

jest.mock('expo-router', () => ({
  useRouter: () => ({ push: mockPush, replace: jest.fn(), back: mockBack, canGoBack: () => true }),
  useLocalSearchParams: () => mockParams,
}));
jest.mock('@/auth/AuthProvider', () => ({ useAuth: () => ({ accessToken: 'token', ready: true }) }));
jest.mock('@/components/BrandLogoLink', () => ({ BrandLogoLink: () => null }));
jest.mock('@/components/Screen', () => ({
  Screen: ({ children }: { children: unknown }) => { const { ScrollView } = jest.requireActual('react-native'); return <ScrollView>{children as never}</ScrollView>; },
}));
jest.mock('@/i18n', () => ({ useI18n: () => ({ tx: (ko: string, en: string) => (mockLang === 'ko' ? ko : en), language: mockLang }) }));
jest.mock('expo-location', () => ({ getForegroundPermissionsAsync: jest.fn(), requestForegroundPermissionsAsync: jest.fn() }));
jest.mock('@/location/currentPosition', () => ({ readCurrentPosition: jest.fn() }));
jest.mock('@/personalization/locationConsent', () => ({ ...jest.requireActual('@/personalization/locationConsent'), syncLocationConsent: jest.fn() }));
jest.mock('@/personalization/useLocationGate', () => ({ useLocationGate: () => ({ request: mockRequest, sheet: null }) }));
jest.mock('@/field/nearbyFacilities', () => ({ ...jest.requireActual('@/field/nearbyFacilities'), getNearbyFacilities: jest.fn() }));
jest.mock('@/utils/externalMaps', () => ({ listAvailableMapApps: jest.fn() }));

// 느린 PC·CI 에서는 첫 화면을 그리는 데만 1초가 넘게 걸린다(nearbyFacilitiesScreen.test.tsx 와 같은 까닭).
configure({ asyncUtilTimeout: 8000 });
jest.setTimeout(30000);

const PLACE = { latitude: 35.1587, longitude: 129.1604 };
const MY_POSITION = { latitude: 35.1532, longitude: 129.1186 };
const ANCHOR_PARAMS = { kind: 'PARKING', lat: '35.1587', lng: '129.1604', name: '해운대해수욕장', stay: '1' };

const getMock = getNearbyFacilities as jest.Mock;

const parkingItem = (id: string, name: string, estimatedWon: number | null, distanceM = 230): FacilityItem => ({
  id, name, kind: 'PARKING', distanceM, address: '부산광역시 해운대구 우동 1', lat: 35.16, lng: 129.16,
  detail: {
    operator: 'PUBLIC', free: false, spaces: 20, fee: null, estimatedWon, note: null,
    openNow: true, todayHours: { open: '08:00', close: '20:00' }, closesAt: '20:00', allDay: false,
  },
});

function response(kind: FacilityKind, items: FacilityItem[], extra: Partial<NearbyFacilities> = {}): NearbyFacilities {
  return { kind, radiusM: 700, stayMinutes: kind === 'PARKING' ? 60 : null, total: items.length, limit: 20, items, ...extra };
}

const PARKING_ITEMS = [parkingItem('p-cheap', '싼주차장', 1200, 450), parkingItem('p-unknown', '모르는주차장', null, 90)];

async function renderScreen() {
  const view = render(<NearbyFacilitiesScreen />);
  await waitFor(() => expect(view.queryByTestId('facility-loading') === null).toBe(true));
  return view;
}

beforeEach(() => {
  jest.clearAllMocks();
  mockLang = 'ko';
  mockParams = { ...ANCHOR_PARAMS };
  mockRequest.mockResolvedValue(false);
  getMock.mockImplementation(async (kind: FacilityKind) => response(kind, kind === 'PARKING' ? PARKING_ITEMS : []));
  (listAvailableMapApps as jest.Mock).mockResolvedValue([{ key: 'kakao', labelKo: '카카오맵', labelEn: 'KakaoMap', open: jest.fn().mockResolvedValue(undefined) }]);
  (syncLocationConsent as jest.Mock).mockResolvedValue(true);
  (Location.getForegroundPermissionsAsync as jest.Mock).mockResolvedValue({ granted: true, canAskAgain: true });
  (readCurrentPosition as jest.Mock).mockResolvedValue({ coords: MY_POSITION });
  jest.spyOn(Linking, 'openSettings').mockResolvedValue(undefined);
});

afterEach(() => jest.restoreAllMocks());

describe('장소 기준으로 열린다', () => {
  it('🔴 그 장소에서, 좌표를 줄이지 않고(exact), 1시간 기준 주차장을 한 번만 묻는다', async () => {
    const view = await renderScreen();

    expect(getMock).toHaveBeenCalledTimes(1);
    expect(getMock).toHaveBeenCalledWith('PARKING', PLACE, 'token', expect.objectContaining({ radiusM: 700, stayMinutes: 60, exact: true }));
    expect(view.getByTestId('facility-tab-PARKING').props.accessibilityState.selected).toBe(true);
    expect(view.getByTestId('facility-stay-1').props.accessibilityState.selected).toBe(true);
    expect(view.getByTestId('facility-list')).toBeTruthy();
  });

  it('🔴 «「이름」 근처에서 찾았어요 · 거리도 그 장소에서 잰 거예요»라고 밝히고, 서면 기준 안내는 없다', async () => {
    const view = await renderScreen();

    expect(view.getByTestId('facility-anchor-notice')).toBeTruthy();
    expect(view.getByText('「해운대해수욕장」 근처에서 찾았어요. 거리도 그 장소에서 잰 거예요.')).toBeTruthy();
    expect(view.queryByTestId('facility-fallback-notice')).toBeNull();
    expect(view.queryByText('위치를 확인하고 있어요…')).toBeNull();
    expect(view.getByTestId('facility-search-my-location')).toBeTruthy();
  });

  it('이름이 없으면 «고른 장소 근처에서…»', async () => {
    mockParams = { ...ANCHOR_PARAMS, name: '' };
    const view = await renderScreen();

    expect(view.getByText('고른 장소 근처에서 찾았어요. 거리도 그 장소에서 잰 거예요.')).toBeTruthy();
  });

  it('🔴 내 위치를 읽지도 묻지도 않는다 — 동의 확인·권한·위치 읽기·동의 창 어느 것도 부르지 않는다', async () => {
    await renderScreen();

    expect(syncLocationConsent).not.toHaveBeenCalled();
    expect(Location.getForegroundPermissionsAsync).not.toHaveBeenCalled();
    expect(Location.requestForegroundPermissionsAsync).not.toHaveBeenCalled();
    expect(readCurrentPosition).not.toHaveBeenCalled();
    expect(mockRequest).not.toHaveBeenCalled();
  });

  it('🔴 «길 안내»가 없다 — 출발지가 내가 아니라 그 장소다. 지도 앱만, 카드를 눌러도 지도 앱이 열린다', async () => {
    const view = await renderScreen();

    expect(view.queryByTestId('facility-route-p-cheap')).toBeNull();
    expect(view.getByTestId('facility-map-p-cheap')).toBeTruthy();
    fireEvent.press(view.getByTestId('facility-open-p-cheap'));
    await waitFor(() => expect(listAvailableMapApps).toHaveBeenCalledTimes(1));
    expect(mockPush).not.toHaveBeenCalled();
  });

  it('서버가 준 순서 그대로 — 1시간 요금과 «요금 정보 없음»', async () => {
    const view = await renderScreen();

    expect(view.getByText('약 1,200원')).toBeTruthy();
    expect(view.getByText('요금 정보 없음')).toBeTruthy();
    expect(view.getAllByText('1시간 기준').length).toBeGreaterThan(0);
  });
});

describe('다른 탭 · 칩 · 넓히기도 그 장소 기준이다', () => {
  it('약국 탭 — 같은 장소에서, 좌표는 그대로, 체류 시간 없이', async () => {
    const view = await renderScreen();

    fireEvent.press(view.getByTestId('facility-tab-PHARMACY'));

    await waitFor(() => expect(getMock).toHaveBeenLastCalledWith('PHARMACY', PLACE, 'token', expect.objectContaining({ exact: true, stayMinutes: undefined })));
    expect(readCurrentPosition).not.toHaveBeenCalled();
  });

  it('머무는 시간 칩 — 3시간을 누르면 같은 장소에서 3시간 기준으로 다시', async () => {
    const view = await renderScreen();

    fireEvent.press(view.getByTestId('facility-stay-3'));

    await waitFor(() => expect(getMock).toHaveBeenLastCalledWith('PARKING', PLACE, 'token', expect.objectContaining({ stayMinutes: 180, exact: true })));
  });

  it('반경 안에 없으면 2km 까지 넓혀도 그 장소 기준', async () => {
    getMock.mockImplementation(async (kind: FacilityKind, _at: unknown, _token: unknown, options: { radiusM: number }) => response(kind, [], { radiusM: options.radiusM }));
    const view = await renderScreen();

    fireEvent.press(view.getByTestId('facility-widen'));

    await waitFor(() => expect(getMock).toHaveBeenLastCalledWith('PARKING', PLACE, 'token', expect.objectContaining({ radiusM: 2000, exact: true })));
  });
});

describe('«내 위치로 찾기»', () => {
  beforeEach(() => {
    mockRequest.mockResolvedValue(true);
    (Location.getForegroundPermissionsAsync as jest.Mock).mockResolvedValue({ granted: false, canAskAgain: true });
    (Location.requestForegroundPermissionsAsync as jest.Mock).mockResolvedValue({ granted: true, canAskAgain: true });
  });

  it('🔴 동의를 묻고 허락하면 기준이 내 위치로 옮겨간다 — 안내가 사라지고 길 안내가 생긴다. 좌표는 다시 100m 단위(exact 아님)', async () => {
    const view = await renderScreen();

    await act(async () => { fireEvent.press(view.getByTestId('facility-search-my-location')); });

    await waitFor(() => expect(getMock).toHaveBeenLastCalledWith('PARKING', MY_POSITION, 'token', expect.objectContaining({ exact: false })));
    await waitFor(() => expect(view.queryByTestId('facility-anchor-notice') === null).toBe(true));
    expect(mockRequest).toHaveBeenCalledTimes(1);
    expect(view.queryByTestId('facility-fallback-notice')).toBeNull();
    await waitFor(() => expect(view.getByTestId('facility-route-p-cheap')).toBeTruthy());
  });

  it('🔴 위치를 읽는 동안 같은 요청을 또 보내지 않는다 — 장소 기준 한 번, 내 위치 기준 한 번, 모두 둘', async () => {
    const view = await renderScreen();

    await act(async () => { fireEvent.press(view.getByTestId('facility-search-my-location')); });
    await waitFor(() => expect(getMock).toHaveBeenLastCalledWith('PARKING', MY_POSITION, 'token', expect.anything()));

    expect(getMock).toHaveBeenCalledTimes(2);
  });

  it('🔴 동의 창에서 거절하면 그 장소 기준 그대로다 — 서면으로 튀지 않고 요청도 더 가지 않는다', async () => {
    mockRequest.mockResolvedValue(false);
    const view = await renderScreen();

    await act(async () => { fireEvent.press(view.getByTestId('facility-search-my-location')); });

    expect(view.getByTestId('facility-anchor-notice')).toBeTruthy();
    expect(view.queryByTestId('facility-fallback-notice')).toBeNull();
    expect(getMock).toHaveBeenCalledTimes(1);
    expect(getMock).not.toHaveBeenCalledWith(expect.anything(), SEOMYEON, expect.anything(), expect.anything());
    expect(Location.requestForegroundPermissionsAsync).not.toHaveBeenCalled();
  });

  it('기기 권한을 거절해도 그 장소 기준 그대로다', async () => {
    (Location.requestForegroundPermissionsAsync as jest.Mock).mockResolvedValue({ granted: false, canAskAgain: true });
    const view = await renderScreen();

    await act(async () => { fireEvent.press(view.getByTestId('facility-search-my-location')); });

    expect(view.getByTestId('facility-anchor-notice')).toBeTruthy();
    expect(view.queryByTestId('facility-fallback-notice')).toBeNull();
    expect(readCurrentPosition).not.toHaveBeenCalled();
    expect(getMock).toHaveBeenCalledTimes(1);
  });

  it('위치를 읽다 실패해도 그 장소 기준 그대로다', async () => {
    (readCurrentPosition as jest.Mock).mockRejectedValue(new Error('position timeout'));
    const view = await renderScreen();

    await act(async () => { fireEvent.press(view.getByTestId('facility-search-my-location')); });

    expect(view.getByTestId('facility-anchor-notice')).toBeTruthy();
    expect(getMock).toHaveBeenCalledTimes(1);
  });

  it('기기 권한을 더 물을 수 없으면 설정을 연다 — 눌러도 아무 일 없는 단추가 되지 않게', async () => {
    (Location.getForegroundPermissionsAsync as jest.Mock).mockResolvedValue({ granted: false, canAskAgain: false });
    const view = await renderScreen();

    await act(async () => { fireEvent.press(view.getByTestId('facility-search-my-location')); });

    expect(Linking.openSettings).toHaveBeenCalled();
    expect(view.getByTestId('facility-anchor-notice')).toBeTruthy();
  });
});

describe('🔴 좌표가 올바르지 않으면 장소 기준이 아니다 — 내 위치 길', () => {
  it.each([
    ['위도만', { kind: 'PARKING', lat: '35.1587' }],
    ['경도만', { kind: 'PARKING', lng: '129.1604' }],
    ['빈 문자열 — Number("") 은 0 이라 따로 거른다', { kind: 'PARKING', lat: '', lng: '' }],
    ['숫자가 아님', { kind: 'PARKING', lat: 'abc', lng: 'def' }],
    ['지구 밖 위도', { kind: 'PARKING', lat: '91', lng: '129' }],
    ['지구 밖 경도', { kind: 'PARKING', lat: '35', lng: '181' }],
    ['(0, 0)', { kind: 'PARKING', lat: '0', lng: '0' }],
  ])('%s', async (_label, params) => {
    mockParams = params as Record<string, string>;
    (Location.getForegroundPermissionsAsync as jest.Mock).mockResolvedValue({ granted: true, canAskAgain: true });
    const view = await renderScreen();

    expect(view.queryByTestId('facility-anchor-notice')).toBeNull();
    expect(getMock).toHaveBeenCalledWith('PARKING', MY_POSITION, 'token', expect.objectContaining({ exact: false }));
    expect(readCurrentPosition).toHaveBeenCalled();
  });

  it('머무는 시간 값이 칩에 없으면(9) 기본 2시간', async () => {
    mockParams = { ...ANCHOR_PARAMS, stay: '9' };
    const view = await renderScreen();

    expect(view.getByTestId('facility-stay-2').props.accessibilityState.selected).toBe(true);
    expect(getMock).toHaveBeenCalledWith('PARKING', PLACE, 'token', expect.objectContaining({ stayMinutes: 120 }));
  });

  it('이름이 아주 길어도 60자까지만 찍는다 — 주소창으로 들어온 값일 수 있다', async () => {
    mockParams = { ...ANCHOR_PARAMS, name: '가'.repeat(200) };
    const view = await renderScreen();

    expect(view.getByText(`「${'가'.repeat(60)}」 근처에서 찾았어요. 거리도 그 장소에서 잰 거예요.`)).toBeTruthy();
  });
});

describe('영어', () => {
  it('장소 기준 안내와 단추가 영어다', async () => {
    mockLang = 'en';
    const view = await renderScreen();

    expect(view.getByText('Searched near “해운대해수욕장”. Distances are measured from that place.')).toBeTruthy();
    expect(view.getByText('Search from my location')).toBeTruthy();
  });
});
