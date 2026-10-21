// 근처 편의시설 화면 (S15P21E201-2052) — 네 탭 · 위치 · 서버 · 가는 법.
// 🔴 지키는 것: ① 위치를 못 쓰면 서면 기준이라고 밝힌다 ② 서면에서 찾은 곳에는 «길 안내»를 열지 않는다(지도 앱만)
//    ③ 위치를 읽는 동안에는 서버에 묻지 않는다(서면으로 한 번, 내 위치로 또 한 번 부르지 않는다) ④ 요금·시간을 «모른다»는 말을 지어내지 않는다.
import { act, configure, fireEvent, render, waitFor, within } from '@testing-library/react-native';
import * as Location from 'expo-location';
import { Linking } from 'react-native';

import NearbyFacilitiesScreen from '../field/nearby';
import { getNearbyFacilities, SEOMYEON, type FacilityItem, type FacilityKind, type NearbyFacilities } from '@/field/nearbyFacilities';
import { readCurrentPosition } from '@/location/currentPosition';
import { syncLocationConsent } from '@/personalization/locationConsent';
import { listAvailableMapApps } from '@/utils/externalMaps';

const mockPush = jest.fn();
const mockReplace = jest.fn();
const mockBack = jest.fn();
let mockParams: Record<string, string> = {};
let mockLang: 'ko' | 'en' = 'ko';
const mockRequest = jest.fn();

jest.mock('expo-router', () => ({
  useRouter: () => ({ push: mockPush, replace: mockReplace, back: mockBack, canGoBack: () => true }),
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
// 지도는 부품이 따로 시험한다(src/map/__tests__). 지도에 무엇을 주는가는 nearbyFacilitiesMap.test.tsx 가 본다 — 여기서는 빈 껍데기.
jest.mock('@/map/RouteMap', () => ({ RouteMap: () => null }));

// 🔴 느린 PC·CI(여러 잡이 한 기계에서 돈다)에서는 첫 화면을 그리는 데만 1초가 넘게 걸린다 — 기본 대기 1초로는 모자라다.
//    실측: 위치를 읽은 뒤 서버를 부르기까지 0.7초, 목록을 그리기까지 0.8초.
configure({ asyncUtilTimeout: 8000 });
jest.setTimeout(30000);

const MY_POSITION = { latitude: 35.1532, longitude: 129.1186 };

const getMock = getNearbyFacilities as jest.Mock;

function grantLocation() {
  (syncLocationConsent as jest.Mock).mockResolvedValue(true);
  (Location.getForegroundPermissionsAsync as jest.Mock).mockResolvedValue({ granted: true, canAskAgain: true });
  (readCurrentPosition as jest.Mock).mockResolvedValue({ coords: MY_POSITION });
}

function denyLocation() {
  (syncLocationConsent as jest.Mock).mockResolvedValue(false);
}

const itemBase = { address: '부산광역시 중구 중앙동 1', lat: 35.15, lng: 129.12 };

const parkingItem = (id: string, name: string, detail: Record<string, unknown>, distanceM = 230): FacilityItem => ({
  ...itemBase, id, name, kind: 'PARKING', distanceM,
  detail: {
    operator: 'PUBLIC', free: false, spaces: 20,
    fee: { baseMinutes: 30, baseWon: 300, addMinutes: 10, addWon: 100, dayMaxWon: 2400, freeMinutes: 0 },
    estimatedWon: 1200, note: null, openNow: true, todayHours: { open: '08:00', close: '20:00' }, closesAt: '20:00', allDay: false,
    ...detail,
  },
} as FacilityItem);

function response(kind: FacilityKind, items: FacilityItem[], extra: Partial<NearbyFacilities> = {}): NearbyFacilities {
  return { kind, radiusM: 700, stayMinutes: kind === 'PARKING' ? 120 : null, total: items.length, limit: 20, items, ...extra };
}

const PARKING_ITEMS = [
  parkingItem('p-free', '무료주차장', { free: true, fee: null, estimatedWon: 0 }, 120),
  parkingItem('p-paid', '유료주차장', {}, 230),
  parkingItem('p-partial', '일부만아는주차장', { fee: { baseMinutes: 30, baseWon: 300, addMinutes: 10, addWon: null, dayMaxWon: null, freeMinutes: 0 }, estimatedWon: null }, 300),
  parkingItem('p-unknown', '모르는주차장', { fee: null, estimatedWon: null, openNow: null, todayHours: null, closesAt: null }, 90),
];

const PHARMACY_ITEM: FacilityItem = {
  ...itemBase, id: 'ph-1', name: '시험약국', kind: 'PHARMACY', distanceM: 410,
  detail: {
    tel: '051-241-0123', directions: '1호선 토성역 8번출구', note: null, hoursAreReported: true, openNow: true,
    todayHours: { open: '09:00', close: '18:00' }, closesAt: '18:00', allDay: false, opensSunday: true, opensHoliday: false, lateNight: false,
  },
};

const EXCHANGE_ITEM: FacilityItem = {
  ...itemBase, id: 'ex-1', name: '시험환전', kind: 'EXCHANGE', distanceM: 403,
  detail: { tel: '051-245-0000', placeUrl: 'http://place.map.kakao.com/1' },
};

const TOILET_ITEM: FacilityItem = {
  ...itemBase, id: 't-1', name: '시험화장실', kind: 'TOILET', distanceM: 150,
  detail: { type: 'PUBLIC', openHours: '09:00~18:00', alwaysOpen: false, accessible: true, diaper: true, diaperPlace: 'WOMEN', emergencyBell: false, cctv: false, tel: null },
};

const LOCKER_ITEM: FacilityItem = {
  ...itemBase, id: 'l-1', name: '하단역', address: null, kind: 'LOCKER', distanceM: 80,
  detail: {
    station: '하단', stationEn: 'Hadan', line: '1', location: '(B1) 1번 엘리베이터 옆', small: 10, medium: 10, large: 1, xlarge: 7,
    feeText: '특대: 6,000원 / 대:4,000원 / 중: 3,000원 / 소: 2,000원(3시간당)', operator: '위드락커',
  },
};

async function renderScreen() {
  const view = render(<NearbyFacilitiesScreen />);
  // 🔴 «없다»는 조건은 불리언으로 쓴다 — `expect(요소).toBeNull()` 이 실패하면 Jest 가 React 파이버 트리를 통째로 글로 찍어(수십만 글자)
  //    폴링 한 번에 1초가 넘게 걸리고, 시험 하나가 2~3초가 된다.
  await waitFor(() => expect(view.queryByTestId('facility-loading') === null).toBe(true));
  return view;
}

beforeEach(() => {
  jest.clearAllMocks();
  mockParams = {};
  mockLang = 'ko';
  mockRequest.mockResolvedValue(false);
  getMock.mockImplementation(async (kind: FacilityKind) => response(kind, kind === 'PARKING' ? PARKING_ITEMS : []));
  (listAvailableMapApps as jest.Mock).mockResolvedValue([{ key: 'kakao', labelKo: '카카오맵', labelEn: 'KakaoMap', open: jest.fn().mockResolvedValue(undefined) }]);
  jest.spyOn(Linking, 'openURL').mockResolvedValue(true);
  jest.spyOn(Linking, 'openSettings').mockResolvedValue(undefined);
  grantLocation();
});

afterEach(() => jest.restoreAllMocks());

describe('주차장 탭 — 내 위치에서', () => {
  it('🔴 위치를 읽은 뒤 한 번만 묻는다 — 서면으로 먼저 묻지 않는다. 700m · 2시간 기준으로', async () => {
    const view = await renderScreen();

    expect(view.getByTestId('facility-list')).toBeTruthy();
    expect(getMock).toHaveBeenCalledTimes(1);
    expect(getMock).toHaveBeenCalledWith('PARKING', MY_POSITION, 'token',
      expect.objectContaining({ radiusM: 700, stayMinutes: 120, accessible: false, diaper: false }));
    expect(view.queryByTestId('facility-fallback-notice')).toBeNull();
  });

  it('서버가 준 순서 그대로 그린다 — 무료 → 예상요금 → 일부만 아는 곳 → 모르는 곳, 모르는 곳은 무료도 0원도 아니다', async () => {
    const view = await renderScreen();

    const cards = view.getAllByTestId(/^facility-card-/).map((node) => node.props.testID);
    expect(cards).toEqual(['facility-card-p-free', 'facility-card-p-paid', 'facility-card-p-partial', 'facility-card-p-unknown']);
    expect(view.getByText('무료')).toBeTruthy();
    expect(view.getByText('약 1,200원')).toBeTruthy();
    expect(view.getByText('기본 30분 300원')).toBeTruthy();
    expect(view.getByText('요금 정보 없음')).toBeTruthy();
    expect(view.getByText('요금이 싼 순이에요. 요금을 모르는 곳은 맨 뒤에 있어요.')).toBeTruthy();
  });

  it('몇 시간 머물러요? 칩 — 기본 2시간, 3시간을 누르면 다시 묻고 «3시간 기준»이 된다', async () => {
    const view = await renderScreen();
    expect(view.getByTestId('facility-stay-2').props.accessibilityState.selected).toBe(true);
    expect(view.getByText('몇 시간 머물러요?')).toBeTruthy();

    fireEvent.press(view.getByTestId('facility-stay-3'));

    await waitFor(() => expect(getMock).toHaveBeenCalledTimes(2));
    expect(getMock).toHaveBeenLastCalledWith('PARKING', MY_POSITION, 'token', expect.objectContaining({ stayMinutes: 180 }));
    await waitFor(() => expect(view.getByTestId('facility-stay-3').props.accessibilityState.selected).toBe(true));
    expect(view.getAllByText('3시간 기준').length).toBeGreaterThan(0);
  });

  it('칩은 네 개 — 1·2·3·4시간', async () => {
    const view = await renderScreen();

    for (const hours of [1, 2, 3, 4]) expect(view.getByTestId(`facility-stay-${hours}`)).toBeTruthy();
    expect(view.queryByTestId('facility-stay-5')).toBeNull();
  });

  it('총 개수가 보여 준 개수보다 많으면 «몇 곳 중 앞의 몇 곳만»이라고 말한다', async () => {
    getMock.mockResolvedValue(response('PARKING', PARKING_ITEMS.slice(0, 2), { total: 134 }));

    const view = await renderScreen();

    expect(view.getByText('134곳 중 앞의 2곳만 보여 드려요')).toBeTruthy();
  });

  it('다 보여 줬으면 그 말이 없다', async () => {
    const view = await renderScreen();

    expect(view.queryByTestId('facility-more')).toBeNull();
  });
});

// 🔴 S15P21E201-2069 — 위치를 못 쓰면 서면으로 넘어가지 않고 «어디 근처를 볼까요?»를 묻는다(사용자 결정 2026-10-20).
describe('위치를 못 쓸 때 — 어디 근처를 볼지 고른다', () => {
  beforeEach(() => denyLocation());

  it('🔴 서면에서 찾지 않는다 — 서버에 묻지 않고 «어디 근처를 볼까요?»와 자주 가는 곳을 보여 준다', async () => {
    const view = await renderScreen();

    expect(getMock).not.toHaveBeenCalled();
    expect(view.getByTestId('facility-fallback-notice')).toBeTruthy();
    expect(view.getByText('내 위치를 쓸 수 없어요. 아래에서 어디 근처를 볼지 골라 주세요.')).toBeTruthy();
    expect(view.getByTestId('facility-pick')).toBeTruthy();
    expect(view.getByTestId('facility-area-chip-김해공항')).toBeTruthy();
    expect(view.getByTestId('facility-ask-location')).toBeTruthy();
  });

  it('자주 가는 곳을 누르면 그 자리에서 찾고 «그 장소 근처» 안내가 뜬다', async () => {
    const view = await renderScreen();

    await act(async () => { fireEvent.press(view.getByTestId('facility-area-chip-김해공항')); });

    await waitFor(() => expect(getMock).toHaveBeenLastCalledWith('PARKING', { latitude: 35.1732, longitude: 128.9466 }, 'token', expect.objectContaining({ exact: true })));
    expect(view.getByText('「김해공항」 근처에서 찾았어요. 거리도 그 장소에서 잰 거예요.')).toBeTruthy();
    expect(view.queryByTestId('facility-fallback-notice')).toBeNull();
  });

  it('🔴 고른 자리에서 찾은 곳에는 «길 안내»가 없다 — 지도 앱 단추만, 그 단추를 눌러야 지도 앱이 열린다(카드를 누르면 지도에서 그 점이 골라질 뿐)', async () => {
    const view = await renderScreen();
    await act(async () => { fireEvent.press(view.getByTestId('facility-area-chip-서면')); });
    await waitFor(() => expect(view.getByTestId('facility-map-p-paid')).toBeTruthy());

    expect(view.queryByTestId('facility-route-p-paid')).toBeNull();
    expect(view.getByTestId('facility-map-p-paid')).toBeTruthy();
    fireEvent.press(view.getByTestId('facility-open-p-paid'));
    expect(listAvailableMapApps).not.toHaveBeenCalled();
    fireEvent.press(view.getByTestId('facility-map-p-paid'));
    await waitFor(() => expect(listAvailableMapApps).toHaveBeenCalledTimes(1));
    expect(mockPush).not.toHaveBeenCalled();
  });

  it('«내 위치로 다시 찾기» — 동의를 묻고, 허락하면 내 위치에서 다시 찾아 안내가 사라진다', async () => {
    const view = await renderScreen();
    mockRequest.mockResolvedValue(true);
    (Location.requestForegroundPermissionsAsync as jest.Mock).mockResolvedValue({ granted: true, canAskAgain: true });
    (Location.getForegroundPermissionsAsync as jest.Mock).mockResolvedValue({ granted: false, canAskAgain: true });
    (readCurrentPosition as jest.Mock).mockResolvedValue({ coords: MY_POSITION });

    await act(async () => { fireEvent.press(view.getByTestId('facility-ask-location')); });

    await waitFor(() => expect(getMock).toHaveBeenLastCalledWith('PARKING', MY_POSITION, 'token', expect.anything()));
    await waitFor(() => expect(view.queryByTestId('facility-fallback-notice') === null).toBe(true));
    expect(mockRequest).toHaveBeenCalled();
    // 이제 길 안내가 생긴다
    expect(view.getByTestId('facility-route-p-paid')).toBeTruthy();
  });

  it('동의를 거절하면 고르라는 안내가 그대로다', async () => {
    const view = await renderScreen();
    mockRequest.mockResolvedValue(false);

    await act(async () => { fireEvent.press(view.getByTestId('facility-ask-location')); });

    expect(view.getByTestId('facility-fallback-notice')).toBeTruthy();
    expect(Location.requestForegroundPermissionsAsync).not.toHaveBeenCalled();
  });

  it('기기 권한을 더 물을 수 없으면 «설정에서 위치 허용하기» — 눌러도 아무 일 없는 단추를 두지 않는다', async () => {
    (syncLocationConsent as jest.Mock).mockResolvedValue(true);
    (Location.getForegroundPermissionsAsync as jest.Mock).mockResolvedValue({ granted: false, canAskAgain: false });

    const view = await renderScreen();

    expect(view.queryByTestId('facility-ask-location')).toBeNull();
    fireEvent.press(view.getByTestId('facility-open-settings'));
    expect(Linking.openSettings).toHaveBeenCalled();
  });

  it('위치를 읽다 실패해도(8초 안에 못 읽음 등) 서면으로 가지 않고 고르라고 한다', async () => {
    (Location.getForegroundPermissionsAsync as jest.Mock).mockResolvedValue({ granted: true, canAskAgain: true });
    (syncLocationConsent as jest.Mock).mockResolvedValue(true);
    (readCurrentPosition as jest.Mock).mockRejectedValue(new Error('position timeout'));

    const view = await renderScreen();

    expect(getMock).not.toHaveBeenCalled();
    expect(view.getByTestId('facility-fallback-notice')).toBeTruthy();
  });
});

describe('가는 법 — 내 위치를 알 때', () => {
  it('길 안내 — 앱 안 /route-detail 에 내 위치와 그 자리를 넘기고, 가까우면 걸어서', async () => {
    const view = await renderScreen();

    fireEvent.press(view.getByTestId('facility-route-p-paid'));

    expect(mockPush).toHaveBeenCalledWith({
      pathname: '/route-detail',
      params: {
        originLat: String(MY_POSITION.latitude), originLng: String(MY_POSITION.longitude), originName: '내 위치',
        destLat: '35.15', destLng: '129.12', destName: '유료주차장', mode: 'WALK',
      },
    });
  });

  it('멀면(1.2km 넘게) 수단을 못 박지 않는다', async () => {
    getMock.mockResolvedValue(response('PARKING', [parkingItem('far', '먼주차장', {}, 1500)]));
    const view = await renderScreen();

    fireEvent.press(view.getByTestId('facility-route-far'));

    const params = (mockPush.mock.calls[0][0] as { params: Record<string, string> }).params;
    expect(params.mode).toBeUndefined();
    expect(params.destName).toBe('먼주차장');
  });

  it('🔴 카드를 누르면 길 안내로 가지 않는다 — 지도에서 그 점이 골라질 뿐이다(S15P21E201-2052). 길 안내는 «길 안내» 단추가 한다', async () => {
    const view = await renderScreen();

    fireEvent.press(view.getByTestId('facility-open-p-paid'));

    expect(mockPush).not.toHaveBeenCalled();
  });

  it('지도 앱 — 프로젝트의 외부 지도 열기 함수로 그 자리의 이름·좌표를 열고, 첫 지도 앱을 연다', async () => {
    const open = jest.fn().mockResolvedValue(undefined);
    (listAvailableMapApps as jest.Mock).mockResolvedValue([{ key: 'kakao', labelKo: '카카오맵', labelEn: 'KakaoMap', open }]);
    const view = await renderScreen();

    fireEvent.press(view.getByTestId('facility-map-p-paid'));

    await waitFor(() => expect(open).toHaveBeenCalledTimes(1));
    expect(listAvailableMapApps).toHaveBeenCalledWith({ name: '유료주차장', latitude: 35.15, longitude: 129.12 });
  });

  it('설치된 지도 앱이 하나도 없어도 터지지 않는다', async () => {
    (listAvailableMapApps as jest.Mock).mockResolvedValue([]);
    const view = await renderScreen();

    fireEvent.press(view.getByTestId('facility-map-p-paid'));

    await waitFor(() => expect(listAvailableMapApps).toHaveBeenCalled());
    expect(view.getByTestId('facility-list')).toBeTruthy();
  });
});

describe('네 탭', () => {
  it('탭은 주차장 · 환전소 · 화장실 · 짐보관 — 이모지와 함께, 첫 탭이 선택돼 있다. 약국 탭은 없다(근처 병원·약국으로)', async () => {
    const view = await renderScreen();

    expect(view.getByTestId('facility-tab-PARKING').props.accessibilityState.selected).toBe(true);
    for (const [kind, label] of [['PARKING', '주차장'], ['EXCHANGE', '환전소'], ['TOILET', '화장실'], ['LOCKER', '짐보관']]) {
      expect(view.getByTestId(`facility-tab-${kind}`).props.accessibilityLabel).toBe(label);
    }
    for (const emoji of ['🅿️', '💱', '🚻', '🧳']) expect(view.getByText(emoji)).toBeTruthy();
    expect(view.queryByTestId('facility-tab-PHARMACY')).toBeNull();
  });

  it('환전소 탭 — 서버에 EXCHANGE 를 묻고(체류 시간 없이), 「가기 전에 전화로 확인」을 목록 위에 적는다. 전화 걸기', async () => {
    getMock.mockImplementation(async (kind: FacilityKind) => response(kind, kind === 'EXCHANGE' ? [EXCHANGE_ITEM] : PARKING_ITEMS));
    const view = await renderScreen();

    fireEvent.press(view.getByTestId('facility-tab-EXCHANGE'));

    await waitFor(() => expect(view.getByTestId('facility-card-ex-1')).toBeTruthy());
    expect(getMock).toHaveBeenLastCalledWith('EXCHANGE', MY_POSITION, 'token', expect.objectContaining({ stayMinutes: undefined, accessible: false, diaper: false }));
    expect(view.getByText('영업시간과 환율은 곳마다 달라요. 가기 전에 전화로 확인해 주세요. 은행에서도 환전할 수 있어요.')).toBeTruthy();
    expect(view.getByText('가까운 순이에요.')).toBeTruthy();
    fireEvent.press(view.getByTestId('facility-call-ex-1'));
    expect(Linking.openURL).toHaveBeenCalledWith('tel:0512450000');
    expect(view.queryByText('몇 시간 머물러요?')).toBeNull();
  });

  it('화장실 탭 — 장애인용 · 기저귀 교환대 필터 칩이 서버 필터로 간다', async () => {
    getMock.mockImplementation(async (kind: FacilityKind) => response(kind, kind === 'TOILET' ? [TOILET_ITEM] : []));
    const view = await renderScreen();
    fireEvent.press(view.getByTestId('facility-tab-TOILET'));
    await waitFor(() => expect(view.getByTestId('facility-card-t-1')).toBeTruthy());
    expect(getMock).toHaveBeenLastCalledWith('TOILET', MY_POSITION, 'token', expect.objectContaining({ accessible: false, diaper: false }));
    // 필터 칩 하나 + 카드의 표시 하나
    expect(view.getAllByText('♿ 장애인용')).toHaveLength(2);
  });

  it('화장실 필터 — 칩을 누르면 다시 묻고, 한 번 더 누르면 풀린다', async () => {
    getMock.mockImplementation(async (kind: FacilityKind) => response(kind, kind === 'TOILET' ? [TOILET_ITEM] : []));
    const view = await renderScreen();
    fireEvent.press(view.getByTestId('facility-tab-TOILET'));
    await waitFor(() => expect(view.getByTestId('facility-filter-accessible')).toBeTruthy());

    fireEvent.press(view.getByTestId('facility-filter-accessible'));
    await waitFor(() => expect(getMock).toHaveBeenLastCalledWith('TOILET', MY_POSITION, 'token', expect.objectContaining({ accessible: true, diaper: false })));
    fireEvent.press(view.getByTestId('facility-filter-diaper'));
    await waitFor(() => expect(getMock).toHaveBeenLastCalledWith('TOILET', MY_POSITION, 'token', expect.objectContaining({ accessible: true, diaper: true })));
    fireEvent.press(view.getByTestId('facility-filter-accessible'));
    await waitFor(() => expect(getMock).toHaveBeenLastCalledWith('TOILET', MY_POSITION, 'token', expect.objectContaining({ accessible: false, diaper: true })));
  });

  it('짐보관 탭 — 서버에 LOCKER 를 묻고 역 카드가 나온다', async () => {
    getMock.mockImplementation(async (kind: FacilityKind) => response(kind, kind === 'LOCKER' ? [LOCKER_ITEM] : []));
    const view = await renderScreen();

    fireEvent.press(view.getByTestId('facility-tab-LOCKER'));

    await waitFor(() => expect(view.getByTestId('facility-card-l-1')).toBeTruthy());
    expect(getMock).toHaveBeenLastCalledWith('LOCKER', MY_POSITION, 'token', expect.anything());
    expect(view.getByText('하단역')).toBeTruthy();
    expect(view.getByText('가까운 순이에요.')).toBeTruthy();
  });

  it('?kind=exchange 로 열면 환전소 탭에서 시작한다. 모르는 값(빠진 약국 포함)은 첫 탭', async () => {
    mockParams = { kind: 'exchange' };
    getMock.mockImplementation(async (kind: FacilityKind) => response(kind, kind === 'EXCHANGE' ? [EXCHANGE_ITEM] : []));
    const view = await renderScreen();

    expect(getMock).toHaveBeenCalledWith('EXCHANGE', MY_POSITION, 'token', expect.anything());
    expect(view.getByTestId('facility-tab-EXCHANGE').props.accessibilityState.selected).toBe(true);

    view.unmount();
    getMock.mockClear();
    mockParams = { kind: 'pharmacy' };
    const other = await renderScreen();
    expect(other.getByTestId('facility-tab-PARKING').props.accessibilityState.selected).toBe(true);
  });
});

describe('빈 결과 · 실패', () => {
  it('🔴 반경 700m 안에 없으면 «아직 없어요» + 2km 넓혀 찾기 — 누르면 2000 으로 다시 묻는다. 넓혀도 없으면 단추가 없다', async () => {
    getMock.mockImplementation(async (kind: FacilityKind, _at: unknown, _token: unknown, options: { radiusM: number }) =>
      response(kind, [], { radiusM: options.radiusM }));
    const view = await renderScreen();
    expect(view.getByText('반경 700m 안에는 아직 없어요')).toBeTruthy();

    fireEvent.press(view.getByTestId('facility-widen'));

    await waitFor(() => expect(getMock).toHaveBeenLastCalledWith('PARKING', MY_POSITION, 'token', expect.objectContaining({ radiusM: 2000 })));
    await waitFor(() => expect(view.getByText('반경 2.0km 안에는 아직 없어요')).toBeTruthy());
    expect(view.queryByTestId('facility-widen')).toBeNull();
    // 결과 없음 상자 안에 다른 곳 고르기가 같이 있다(S15P21E201-2069) — 목록 아래 따로 두지 않는다
    expect(within(view.getByTestId('facility-empty')).getByTestId('facility-area')).toBeTruthy();
    expect(view.getAllByTestId('facility-area')).toHaveLength(1);
  });

  it('다른 탭으로 가면 반경이 다시 700m 다', async () => {
    getMock.mockImplementation(async (kind: FacilityKind, _at: unknown, _token: unknown, options: { radiusM: number }) =>
      response(kind, [], { radiusM: options.radiusM }));
    const view = await renderScreen();
    fireEvent.press(view.getByTestId('facility-widen'));
    await waitFor(() => expect(getMock).toHaveBeenLastCalledWith('PARKING', MY_POSITION, 'token', expect.objectContaining({ radiusM: 2000 })));

    fireEvent.press(view.getByTestId('facility-tab-LOCKER'));

    await waitFor(() => expect(getMock).toHaveBeenLastCalledWith('LOCKER', MY_POSITION, 'token', expect.objectContaining({ radiusM: 700 })));
  });

  it('서버 오류 — «불러올 수 없어요» + 다시 시도. 누르면 다시 묻는다', async () => {
    getMock.mockRejectedValueOnce(new Error('boom'));
    const view = render(<NearbyFacilitiesScreen />);
    await waitFor(() => expect(view.getByTestId('facility-error')).toBeTruthy());
    expect(view.getByText('지금은 이 정보를 불러올 수 없어요. 잠시 뒤 다시 시도해 주세요.')).toBeTruthy();

    fireEvent.press(view.getByTestId('facility-retry'));

    await waitFor(() => expect(view.getByTestId('facility-list')).toBeTruthy());
    expect(getMock).toHaveBeenCalledTimes(2);
  });

  it('불러오는 동안 «가까운 곳을 찾고 있어요…» 를 보인다', async () => {
    let finish: (value: NearbyFacilities) => void = () => undefined;
    getMock.mockImplementation(() => new Promise<NearbyFacilities>((resolve) => { finish = resolve; }));
    const view = render(<NearbyFacilitiesScreen />);

    // 서버를 부른 뒤에야 finish 가 진짜 풀이 함수다 — 위치를 읽는 동안에도 «찾고 있어요»가 보이지만 그때는 아직 묻지 않았다
    await waitFor(() => expect(getMock).toHaveBeenCalledTimes(1));
    expect(view.getByTestId('facility-loading')).toBeTruthy();
    expect(view.getByText('가까운 곳을 찾고 있어요…')).toBeTruthy();
    await act(async () => { finish(response('PARKING', PARKING_ITEMS)); });
    await waitFor(() => expect(view.getByTestId('facility-list')).toBeTruthy());
  });

  it('🔴 탭을 빨리 바꿔도 늦게 온 앞 탭의 답이 화면을 덮지 않는다', async () => {
    const resolvers: Record<string, (value: NearbyFacilities) => void> = {};
    getMock.mockImplementation((kind: FacilityKind) => new Promise<NearbyFacilities>((resolve) => { resolvers[kind] = resolve; }));
    const view = render(<NearbyFacilitiesScreen />);
    await waitFor(() => expect(resolvers.PARKING).toBeDefined());

    fireEvent.press(view.getByTestId('facility-tab-LOCKER'));
    await waitFor(() => expect(resolvers.LOCKER).toBeDefined());
    await act(async () => { resolvers.LOCKER(response('LOCKER', [LOCKER_ITEM])); });
    await act(async () => { resolvers.PARKING(response('PARKING', PARKING_ITEMS)); });

    expect(view.getByTestId('facility-card-l-1')).toBeTruthy();
    expect(view.queryByTestId('facility-card-p-paid')).toBeNull();
  });
});

describe('영어 화면', () => {
  it('제목·탭·안내·칩이 영어다', async () => {
    mockLang = 'en';
    denyLocation();
    const view = await renderScreen();

    expect(view.getByText('Nearby facilities')).toBeTruthy();
    expect(view.getByText('Find nearby parking, currency exchanges, restrooms and luggage lockers.')).toBeTruthy();
    expect(view.getByTestId('facility-tab-PARKING').props.accessibilityLabel).toBe('Parking');
    expect(view.getByTestId('facility-tab-TOILET').props.accessibilityLabel).toBe('Restrooms');
    expect(view.getByTestId('facility-tab-LOCKER').props.accessibilityLabel).toBe('Lockers');
    expect(view.getByText('How long will you stay?')).toBeTruthy();
    expect(view.getByText('2 hours')).toBeTruthy();
    expect(view.getByText('1 hour')).toBeTruthy();
    expect(view.getByText('We cannot use your location. Choose where to look below.')).toBeTruthy();
    expect(view.getByText('Where should we look?')).toBeTruthy();
    // 위치를 못 쓰면 목록은 자리를 고른 뒤에 온다(S15P21E201-2069)
    await act(async () => { fireEvent.press(view.getByTestId('facility-area-chip-서면')); });
    await waitFor(() => expect(getMock).toHaveBeenCalled());
    expect(view.getByText('Cheapest first. Lots with unknown fees come last.')).toBeTruthy();
    expect(view.getByText('No fee information')).toBeTruthy();
  });
});
