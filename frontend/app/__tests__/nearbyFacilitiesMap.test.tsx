// 근처 편의시설 화면의 지도 상자 — S15P21E201-2052. 네 탭(주차장·환전소·화장실·짐보관 — 약국은 S15P21E201-2066 에서 근처 병원·약국으로) 모두 목록 바로 위에 가까운 도움과 같은 지도를 둔다.
//
// 🔴 지키는 것:
//    ① 목록의 곳마다 지도에 번호 점이 있다 — 같은 순서·같은 번호, 목록 «바로 위»에. 불러오는 중에는 자리만, 오류·결과 없음에는 지도가 없다
//    ② 카드를 누르면 지도에서 그 점이, 지도의 점을 누르면 그 카드가 골라진다(가까운 도움과 같은 규칙). 첫 곳은 목록과 «같은 렌더에서» 이미 골라져 있다
//    ③ 기준점 — 내 위치로 찾았으면 «현재 위치» 점, 장소 기준이면 그 장소를, 위치를 못 써 서면에서 찾았으면 서면을 «기준점»으로 지도에 찍는다
//    지도 자체(던지지 않음 · 실패 안내)는 src/map/__tests__ 가 본다 — 여기서는 지도에 무엇을 주는가만 본다.
import { act, configure, fireEvent, render, waitFor, within } from '@testing-library/react-native';
import * as Location from 'expo-location';
import { Linking } from 'react-native';

import NearbyFacilitiesScreen from '../field/nearby';
import { getNearbyFacilities, SEOMYEON, type FacilityItem, type FacilityKind, type NearbyFacilities } from '@/field/nearbyFacilities';
import { readCurrentPosition } from '@/location/currentPosition';
import { syncLocationConsent } from '@/personalization/locationConsent';
import { listAvailableMapApps } from '@/utils/externalMaps';

const mockPush = jest.fn();
let mockParams: Record<string, string> = {};
const mockRequest = jest.fn();

jest.mock('expo-router', () => ({
  useRouter: () => ({ push: mockPush, replace: jest.fn(), back: jest.fn(), canGoBack: () => true }),
  useLocalSearchParams: () => mockParams,
}));
jest.mock('@/auth/AuthProvider', () => ({ useAuth: () => ({ accessToken: 'token', ready: true }) }));
jest.mock('@/components/BrandLogoLink', () => ({ BrandLogoLink: () => null }));
jest.mock('@/components/Screen', () => ({
  Screen: ({ children }: { children: unknown }) => { const { ScrollView } = jest.requireActual('react-native'); return <ScrollView>{children as never}</ScrollView>; },
}));
jest.mock('@/i18n', () => ({ useI18n: () => ({ tx: (ko: string) => ko, language: 'ko' }) }));
jest.mock('expo-location', () => ({ getForegroundPermissionsAsync: jest.fn(), requestForegroundPermissionsAsync: jest.fn() }));
jest.mock('@/location/currentPosition', () => ({ readCurrentPosition: jest.fn() }));
jest.mock('@/personalization/locationConsent', () => ({ ...jest.requireActual('@/personalization/locationConsent'), syncLocationConsent: jest.fn() }));
jest.mock('@/personalization/useLocationGate', () => ({ useLocationGate: () => ({ request: mockRequest, sheet: null }) }));
jest.mock('@/field/nearbyFacilities', () => ({ ...jest.requireActual('@/field/nearbyFacilities'), getNearbyFacilities: jest.fn() }));
jest.mock('@/utils/externalMaps', () => ({ listAvailableMapApps: jest.fn() }));

// 지도는 가짜 — 이 화면이 지도에 «무엇을 주는가»만 본다.
type MapPoint = { id: string; label: string; color: string; stops: Array<{ id: string; number: number; name: string; latitude: number; longitude: number }> };
type MapProps = {
  stops: Array<{ id: string; number: number; name: string; latitude: number; longitude: number }>;
  points?: MapPoint[];
  routes?: unknown[];
  selectedId: string;
  onSelect: (id: string) => void;
  currentLocation?: { latitude: number; longitude: number } | null;
  focusSelected?: boolean;
  fitPoints?: boolean;
  height?: number;
};
const mockMap = { renders: [] as MapProps[] };
jest.mock('@/map/RouteMap', () => ({
  RouteMap: (props: MapProps) => { mockMap.renders.push(props); return null; },
}));
const lastMap = () => mockMap.renders[mockMap.renders.length - 1];

// 🔴 느린 PC·CI 에서는 첫 화면을 그리는 데만 1초가 넘게 걸린다(nearbyFacilitiesScreen.test.tsx 와 같은 까닭).
configure({ asyncUtilTimeout: 8000 });
jest.setTimeout(30000);

const MY_POSITION = { latitude: 35.1532, longitude: 129.1186 };
const getMock = getNearbyFacilities as jest.Mock;

function grantLocation() {
  (syncLocationConsent as jest.Mock).mockResolvedValue(true);
  (Location.getForegroundPermissionsAsync as jest.Mock).mockResolvedValue({ granted: true, canAskAgain: true });
  (readCurrentPosition as jest.Mock).mockResolvedValue({ coords: MY_POSITION });
}

const at = (n: number) => ({ lat: 35.15 + n / 1000, lng: 129.06 + n / 1000 });
const parkingItem = (id: string, name: string, n: number): FacilityItem => ({
  id, name, kind: 'PARKING', distanceM: 100 * n, address: '부산광역시 부산진구 1', ...at(n),
  detail: {
    operator: 'PUBLIC', free: false, spaces: 20, fee: null, estimatedWon: 1200, note: null,
    openNow: true, todayHours: { open: '08:00', close: '20:00' }, closesAt: '20:00', allDay: false,
  },
});
const exchangeItem = (id: string, name: string, n: number): FacilityItem => ({
  id, name, kind: 'EXCHANGE', distanceM: 100 * n, address: '부산광역시 부산진구 2', ...at(n),
  detail: { tel: '051-241-0123', placeUrl: null },
});
const toiletItem = (id: string, name: string, n: number): FacilityItem => ({
  id, name, kind: 'TOILET', distanceM: 100 * n, address: '부산광역시 부산진구 3', ...at(n),
  detail: { type: 'PUBLIC', openHours: '09:00~18:00', alwaysOpen: false, accessible: false, diaper: false, diaperPlace: null, emergencyBell: false, cctv: false, tel: null },
});
const lockerItem = (id: string, name: string, n: number): FacilityItem => ({
  id, name, kind: 'LOCKER', distanceM: 100 * n, address: null, ...at(n),
  detail: { station: name, stationEn: null, line: '1', location: '(B1) 1번 출구', small: 10, medium: 10, large: 1, xlarge: 7, feeText: null, operator: '위드락커' },
});

const ITEMS: Record<FacilityKind, FacilityItem[]> = {
  PARKING: [parkingItem('p-1', '첫주차장', 1), parkingItem('p-2', '둘째주차장', 2), parkingItem('p-3', '셋째주차장', 3)],
  PHARMACY: [],
  EXCHANGE: [exchangeItem('ph-1', '첫환전소', 1), exchangeItem('ph-2', '둘째환전소', 2)],
  TOILET: [toiletItem('t-1', '첫화장실', 1), toiletItem('t-2', '둘째화장실', 2)],
  LOCKER: [lockerItem('l-1', '하단역', 1), lockerItem('l-2', '서면역', 2)],
};
const TAB_LABEL: Record<FacilityKind, string> = { PARKING: '주차장', PHARMACY: '약국', TOILET: '화장실', LOCKER: '짐보관', EXCHANGE: '환전소' };

function response(kind: FacilityKind, items: FacilityItem[]): NearbyFacilities {
  return { kind, radiusM: 700, stayMinutes: kind === 'PARKING' ? 120 : null, total: items.length, limit: 20, items };
}

/** 그려진 트리에서 testID 를 위에서 아래 순서로 모은다 — «지도가 목록 바로 위에 있는가»를 본다. */
function testIdsInOrder(node: unknown, out: string[] = []): string[] {
  if (!node || typeof node !== 'object') return out;
  if (Array.isArray(node)) { node.forEach((child) => testIdsInOrder(child, out)); return out; }
  const tree = node as { props?: { testID?: string }; children?: unknown[] | null };
  if (tree.props?.testID) out.push(tree.props.testID);
  (tree.children ?? []).forEach((child) => testIdsInOrder(child, out));
  return out;
}

async function renderScreen() {
  const view = render(<NearbyFacilitiesScreen />);
  // 🔴 «없다» 조건은 불리언으로 쓴다(nearbyFacilitiesScreen.test.tsx 의 같은 주의) — 실패하면 Jest 가 트리를 통째로 글로 찍어 느려진다.
  await waitFor(() => expect(view.queryByTestId('facility-loading') === null).toBe(true));
  return view;
}

beforeEach(() => {
  jest.clearAllMocks();
  mockParams = {};
  mockMap.renders = [];
  mockRequest.mockResolvedValue(false);
  getMock.mockImplementation(async (kind: FacilityKind) => response(kind, ITEMS[kind]));
  (listAvailableMapApps as jest.Mock).mockResolvedValue([{ key: 'kakao', labelKo: '카카오맵', labelEn: 'KakaoMap', open: jest.fn().mockResolvedValue(undefined) }]);
  jest.spyOn(Linking, 'openURL').mockResolvedValue(true);
  grantLocation();
});

afterEach(() => jest.restoreAllMocks());

describe('① 네 탭 모두 — 목록 바로 위의 지도 상자', () => {
  it.each(['PARKING', 'EXCHANGE', 'TOILET', 'LOCKER'] as FacilityKind[])('🔴 %s 탭 — 목록의 곳마다 번호 점(같은 순서·번호), 첫 곳이 골라져 있고, 지도 상자가 목록 위에 있다', async (kind) => {
    const view = await renderScreen();
    if (kind !== 'PARKING') {
      fireEvent.press(view.getByTestId(`facility-tab-${kind}`));
      await waitFor(() => expect(view.getByTestId(`facility-card-${ITEMS[kind][0].id}`)).toBeTruthy());
    }
    const items = ITEMS[kind];
    await waitFor(() => expect(lastMap().stops).toHaveLength(items.length));
    expect(lastMap().stops).toEqual(items.map((item, index) => ({ id: item.id, number: index + 1, name: item.name, latitude: item.lat, longitude: item.lng })));
    expect(lastMap().selectedId).toBe(items[0].id);
    expect(lastMap().routes).toEqual([]); // 경로가 아니다 — 점을 선으로 잇지 않는다
    expect(lastMap().focusSelected).toBe(true);
    // 기준점(장소·서면 기준)이 결과 한두 곳 옆에서 모서리에 걸리지 않게, 점 표시도 맞춘다
    expect(lastMap().fitPoints).toBe(true);
    expect(lastMap().height).toBe(220);

    // 카드마다 같은 번호의 핀 — 점과 카드를 눈으로 맞춘다
    items.forEach((item, index) => expect(within(view.getByTestId(`facility-pin-${item.id}`)).getByText(String(index + 1))).toBeTruthy());
    // 지도 상자는 목록 바로 위다(그 사이에는 정렬 안내 글뿐)
    const order = testIdsInOrder(view.toJSON());
    expect(order.indexOf('facility-map')).toBeGreaterThanOrEqual(0);
    expect(order.indexOf('facility-map')).toBeLessThan(order.indexOf('facility-list'));
    expect(order.slice(order.indexOf('facility-map') + 1, order.indexOf('facility-list'))).toEqual([]);
  });

  it('🔴 불러오는 중에는 지도 자리만(빈 목록), 오류와 결과 없음에는 지도가 없다 — 빈 회색 칸을 남기지 않는다', async () => {
    let finish!: (value: NearbyFacilities) => void;
    getMock.mockImplementation(() => new Promise<NearbyFacilities>((resolve) => { finish = resolve; }));
    const view = render(<NearbyFacilitiesScreen />);
    await waitFor(() => expect(getMock).toHaveBeenCalledTimes(1));
    expect(view.getByTestId('facility-loading')).toBeTruthy();
    expect(view.getByTestId('facility-map')).toBeTruthy();
    expect(lastMap().stops).toEqual([]);

    await act(async () => { finish(response('PARKING', [])); });
    await waitFor(() => expect(view.getByTestId('facility-empty')).toBeTruthy());
    expect(view.queryByTestId('facility-map')).toBeNull();

    getMock.mockRejectedValue(new Error('boom'));
    fireEvent.press(view.getByTestId('facility-widen'));
    await waitFor(() => expect(view.getByTestId('facility-error')).toBeTruthy());
    expect(view.queryByTestId('facility-map')).toBeNull();
  });
});

describe('② 카드와 지도의 선택 연동', () => {
  const selectedOf = (view: ReturnType<typeof render>, id: string) => view.getByTestId(`facility-open-${id}`).props.accessibilityState.selected as boolean;

  it('🔴 카드를 누르면 지도에서 그 점이, 지도의 점을 누르면 그 카드가 골라진다 — 길 안내·지도 앱으로 가지 않는다', async () => {
    const view = await renderScreen();
    await waitFor(() => expect(lastMap().selectedId).toBe('p-1'));
    expect(selectedOf(view, 'p-1')).toBe(true);
    expect(selectedOf(view, 'p-3')).toBe(false);

    // 카드 → 지도
    fireEvent.press(view.getByTestId('facility-open-p-3'));
    await waitFor(() => expect(lastMap().selectedId).toBe('p-3'));
    expect(selectedOf(view, 'p-3')).toBe(true);
    expect(selectedOf(view, 'p-1')).toBe(false);
    expect(mockPush).not.toHaveBeenCalled();
    expect(listAvailableMapApps).not.toHaveBeenCalled();

    // 지도 → 카드
    act(() => { lastMap().onSelect('p-2'); });
    await waitFor(() => expect(selectedOf(view, 'p-2')).toBe(true));
    expect(selectedOf(view, 'p-3')).toBe(false);
  });

  it('🔴 목록이 오는 «바로 그 렌더»에서 첫 곳이 이미 골라져 있다 — 늦게 고르면 지도가 «고른 곳 바뀜»으로 알고 1번으로 확대한다', async () => {
    const view = await renderScreen();
    fireEvent.press(view.getByTestId('facility-tab-EXCHANGE'));
    await waitFor(() => expect(lastMap().stops).toHaveLength(2));
    fireEvent.press(view.getByTestId('facility-tab-TOILET'));
    await waitFor(() => expect(lastMap().stops.map((stop) => stop.id)).toEqual(['t-1', 't-2']));
    // 목록을 가진 모든 렌더에서, 고른 곳은 그 목록 안의 곳이다(빈 값도 지난 탭의 것도 아니다)
    const withList = mockMap.renders.filter((props) => props.stops.length > 0);
    expect(withList.length).toBeGreaterThan(2);
    for (const props of withList) expect(props.stops.map((stop) => stop.id)).toContain(props.selectedId);
  });

  it('탭을 바꿨다 돌아오면 전에 누른 곳이 아니라 첫 곳부터', async () => {
    const view = await renderScreen();
    fireEvent.press(view.getByTestId('facility-open-p-3'));
    await waitFor(() => expect(lastMap().selectedId).toBe('p-3'));

    fireEvent.press(view.getByTestId('facility-tab-EXCHANGE'));
    await waitFor(() => expect(lastMap().stops.map((stop) => stop.id)).toEqual(['ph-1', 'ph-2']));
    expect(lastMap().selectedId).toBe('ph-1');

    fireEvent.press(view.getByTestId('facility-tab-PARKING'));
    await waitFor(() => expect(lastMap().stops).toHaveLength(3));
    expect(lastMap().selectedId).toBe('p-1');
  });

  it('길 안내·지도 앱 단추는 지도 선택과 따로 동작한다 — 누르면 그 길로 가고 선택은 그대로', async () => {
    const view = await renderScreen();
    await waitFor(() => expect(lastMap().selectedId).toBe('p-1'));

    fireEvent.press(view.getByTestId('facility-route-p-2'));
    expect(mockPush).toHaveBeenCalledWith(expect.objectContaining({ pathname: '/route-detail' }));
    expect(lastMap().selectedId).toBe('p-1');
  });
});

describe('③ 기준점 — 거리를 어디서 쟀는가', () => {
  it('🔴 내 위치로 찾았으면 지도에 «현재 위치»를 주고 기준점 표시는 없다(현재 위치 점이 곧 기준)', async () => {
    await renderScreen();
    await waitFor(() => expect(lastMap().stops).toHaveLength(3));
    expect(lastMap().currentLocation).toEqual(MY_POSITION);
    expect(lastMap().points ?? []).toEqual([]);
  });

  it('🔴 장소 기준이면 그 장소를 «기준점»으로 찍고 현재 위치 점은 없다 — 이름·좌표 그대로', async () => {
    mockParams = { kind: 'PARKING', lat: '35.1587', lng: '129.1604', name: '해운대해수욕장', stay: '1' };
    await renderScreen();
    await waitFor(() => expect(lastMap().stops).toHaveLength(3));
    expect(lastMap().currentLocation).toBeNull();
    expect(lastMap().points).toEqual([{
      id: 'reference', label: '기준점', color: expect.any(String),
      stops: [{ id: 'reference', number: 0, name: '해운대해수욕장', latitude: 35.1587, longitude: 129.1604 }],
    }]);
    // 번호 장소(stops)에는 기준점이 끼지 않는다 — 목록과 번호가 어긋나지 않게
    expect(lastMap().stops.map((stop) => stop.id)).toEqual(['p-1', 'p-2', 'p-3']);
  });

  it('장소 기준에서 «내 위치로 찾기»가 성공하면 기준점 표시가 사라지고 현재 위치 점이 생긴다', async () => {
    mockParams = { kind: 'PARKING', lat: '35.1587', lng: '129.1604', name: '해운대해수욕장' };
    mockRequest.mockResolvedValue(true);
    (Location.requestForegroundPermissionsAsync as jest.Mock).mockResolvedValue({ granted: true, canAskAgain: true });
    (Location.getForegroundPermissionsAsync as jest.Mock).mockResolvedValue({ granted: false, canAskAgain: true });
    const view = await renderScreen();
    await waitFor(() => expect(lastMap().points).toHaveLength(1));

    await act(async () => { fireEvent.press(view.getByTestId('facility-search-my-location')); });
    await waitFor(() => expect(getMock).toHaveBeenLastCalledWith('PARKING', MY_POSITION, 'token', expect.anything()));
    await waitFor(() => expect(lastMap().currentLocation).toEqual(MY_POSITION));
    expect(lastMap().points ?? []).toEqual([]);
  });

  it('🔴 위치를 못 써 고른 자리(김해공항)에서 찾았으면 그 자리를 «기준점»으로 찍는다 — 현재 위치 점은 없다', async () => {
    (syncLocationConsent as jest.Mock).mockResolvedValue(false);
    const view = await renderScreen();
    await act(async () => { fireEvent.press(view.getByTestId('facility-area-chip-김해공항')); });
    await waitFor(() => expect(lastMap().stops).toHaveLength(3));
    expect(lastMap().currentLocation).toBeNull();
    expect(lastMap().points).toEqual([{
      id: 'reference', label: '기준점', color: expect.any(String),
      stops: [{ id: 'reference', number: 0, name: '김해공항', latitude: 35.1732, longitude: 128.9466 }],
    }]);
  });

  it('기준점 목록(points)은 렌더마다 새 배열이 아니다 — 지도가 렌더마다 다시 그리려 들지 않게', async () => {
    mockParams = { kind: 'PARKING', lat: '35.1587', lng: '129.1604', name: '해운대해수욕장' };
    const view = await renderScreen();
    await waitFor(() => expect(lastMap().stops).toHaveLength(3));
    view.rerender(<NearbyFacilitiesScreen />);
    view.rerender(<NearbyFacilitiesScreen />);
    const withList = mockMap.renders.filter((props) => props.stops.length > 0);
    expect(withList.length).toBeGreaterThan(1);
    expect(new Set(withList.map((props) => props.points)).size).toBe(1);
    expect(new Set(withList.map((props) => props.stops)).size).toBe(1);
  });
});
