// 가까운 도움 화면 — S15P21E201-1889. 위치 동의 창과 지도 칸.
//
// 🔴 지키는 것(2026-10-15 운영 실측):
//    ① 위치 동의를 한 적 없는 사람이 「내 위치 켜기」를 누르면 동의 창이 뜬다. 전에는 화면이 창(useLocationGate 의 sheet)을 안 그려서
//       단추가 창을 못 띄운 채 답을 영영 기다렸다 — 동의가 없는 사람은 이 화면에서 아무것도 못 했다.
//    ② 목록을 기다리는 동안 지도에 주는 stops 는 렌더마다 새 배열이 되지 않는다(지도가 렌더마다 다시 그리려 들었다).
//    ③ 목록이 오면 번호 점이 채워지고 첫 곳이 골라진다. 탭을 바꾸면 그 종류로 다시 찾는다.
//    ④ 목록의 곳을 누르면 지도의 그 점이, 지도의 점을 누르면 목록의 그 곳이 골라진다.
// 지도 자체(던지지 않음 · 실패 안내)는 src/map/__tests__/routeMapEmptyStops.test.tsx 가 본다 — 여기서는 지도에 무엇을 주는가만 본다.
import AsyncStorage from '@react-native-async-storage/async-storage';
import { act, configure, fireEvent, render, waitFor } from '@testing-library/react-native';
import * as Location from 'expo-location';

import NearbyHelpScreen from '../nearby-help';
import { getNearbyHelp, type ServerNearbyHelp, type ServerHelpPlace } from '@/field/helpPlacesApi';
import { readCurrentPosition } from '@/location/currentPosition';
import { __resetLocationConsentForTests } from '@/personalization/locationConsent';

const mockPush = jest.fn();
jest.mock('expo-router', () => ({
  useRouter: () => ({ push: mockPush, replace: jest.fn(), back: jest.fn(), canGoBack: () => true }),
  useLocalSearchParams: () => ({}),
}));
jest.mock('@/auth/AuthProvider', () => ({ useAuth: () => ({ accessToken: null, ready: true }) }));
jest.mock('@/components/BrandLogoLink', () => ({ BrandLogoLink: () => null }));
jest.mock('@/components/Screen', () => ({
  Screen: ({ children }: { children: unknown }) => { const { ScrollView } = jest.requireActual('react-native'); return <ScrollView>{children as never}</ScrollView>; },
}));
jest.mock('@/i18n', () => ({ useI18n: () => ({ tx: (ko: string) => ko, language: 'ko' }) }));
jest.mock('expo-location', () => ({ getForegroundPermissionsAsync: jest.fn(), requestForegroundPermissionsAsync: jest.fn() }));
jest.mock('@/location/currentPosition', () => ({ readCurrentPosition: jest.fn() }));
jest.mock('@/field/helpPlacesApi', () => ({ getNearbyHelp: jest.fn() }));

// 지도는 가짜 — 이 화면이 지도에 «무엇을 주는가»만 본다.
type MapProps = { stops: Array<{ id: string; number: number; name: string }>; selectedId: string; onSelect: (id: string) => void };
const mockMap = { renders: [] as MapProps[] };
jest.mock('@/map/RouteMap', () => ({
  RouteMap: (props: MapProps) => { mockMap.renders.push(props); return null; },
}));
const lastMap = () => mockMap.renders[mockMap.renders.length - 1];

// 🔴 느린 PC·CI(여러 잡이 한 기계에서 돈다)에서는 첫 화면을 그리는 데만 1초가 넘게 걸린다 — 기본 대기 1초로는 모자라다.
configure({ asyncUtilTimeout: 8000 });
jest.setTimeout(30000);

const MY_POSITION = { latitude: 35.1578, longitude: 129.0597 };
const CONSENT_KEY = 'gabolle.location-consent';

const place = (name: string, lat: number, lng: number, distanceMeters: number, type: string): ServerHelpPlace => ({
  name, nameEn: null, type, address: '부산광역시 부산진구 1', phone: '051-000-0000', lat, lng, distanceMeters,
  emergency: false, todayOpen: '09:00', todayClose: '20:00', openNow: true,
});
const answerFor = (kind: string, places: ServerHelpPlace[]): ServerNearbyHelp => ({ kind, places, source: 'HIRA', basedOn: '2026-06' });
const PHARMACIES = answerFor('PHARMACY', [place('해인온누리약국', 35.158, 129.06, 270, '약국'), place('다솜약국', 35.153, 129.062, 410, '약국'), place('모닝약국', 35.157, 129.057, 520, '약국')]);
const HOSPITALS = answerFor('HOSPITAL', [place('서면의원', 35.159, 129.061, 150, '의원'), place('부산진병원', 35.161, 129.058, 380, '병원')]);

const getMock = getNearbyHelp as jest.Mock;

function allowDevice() {
  (Location.getForegroundPermissionsAsync as jest.Mock).mockResolvedValue({ granted: true, canAskAgain: true });
  (Location.requestForegroundPermissionsAsync as jest.Mock).mockResolvedValue({ granted: true, canAskAgain: true });
  (readCurrentPosition as jest.Mock).mockResolvedValue({ coords: MY_POSITION });
}

beforeEach(async () => {
  jest.clearAllMocks();
  mockMap.renders = [];
  await AsyncStorage.clear();
  __resetLocationConsentForTests();
  allowDevice();
  getMock.mockImplementation(async (kind: string) => (kind === 'pharmacy' ? PHARMACIES : kind === 'hospital' ? HOSPITALS : answerFor('POLICE', [])));
});

describe('① 위치 동의를 한 적 없는 사람', () => {
  it('🔴 「내 위치 켜기」를 누르면 동의 창이 뜨고, 「동의하기」를 누르면 가까운 곳을 찾아 지도에 번호 점을 준다', async () => {
    const view = render(<NearbyHelpScreen />);
    // 동의가 없으니 조용히 읽지 않고, 묻는 것은 단추를 누를 때다
    await waitFor(() => expect(view.getByText('내 위치 켜기')).toBeTruthy());
    expect(getMock).not.toHaveBeenCalled();
    expect(view.queryByText('위치를 써도 될까요?') === null).toBe(true);

    fireEvent.press(view.getByText('내 위치 켜기'));
    // 🔴 전에는 여기서 아무 일도 안 일어났다 — 창을 그리는 곳이 없어서
    await waitFor(() => expect(view.getByText('위치를 써도 될까요?')).toBeTruthy());
    expect(getMock).not.toHaveBeenCalled();

    fireEvent.press(view.getByText('동의하기'));
    await waitFor(() => expect(getMock).toHaveBeenCalledWith('hospital', MY_POSITION, null));
    await waitFor(() => expect(lastMap().stops).toHaveLength(2));
    expect(lastMap().stops.map((stop) => [stop.number, stop.name])).toEqual([[1, '서면의원'], [2, '부산진병원']]);
    expect(await AsyncStorage.getItem(CONSENT_KEY)).toBe('true');
  });

  it('「위치 없이 쓰기」를 누르면 찾지 않고 단추가 그대로 남는다 — 다시 누르면 다시 묻는다', async () => {
    const view = render(<NearbyHelpScreen />);
    await waitFor(() => expect(view.getByText('내 위치 켜기')).toBeTruthy());
    fireEvent.press(view.getByText('내 위치 켜기'));
    await waitFor(() => expect(view.getByText('위치를 써도 될까요?')).toBeTruthy());

    fireEvent.press(view.getByText('위치 없이 쓰기'));
    await waitFor(() => expect(view.queryByText('위치를 써도 될까요?') === null).toBe(true));
    expect(getMock).not.toHaveBeenCalled();
    expect(await AsyncStorage.getItem(CONSENT_KEY)).toBe('false');
    expect(view.getByText('내 위치 켜기')).toBeTruthy();
  });
});

describe('② ③ ④ 이미 동의한 사람 — 지도 칸', () => {
  beforeEach(async () => { await AsyncStorage.setItem(CONSENT_KEY, 'true'); });

  it('🔴 목록을 기다리는 동안 지도에 주는 stops 는 렌더마다 새 배열이 아니다 — 목록이 오면 번호 점이 채워지고 첫 곳이 골라진다', async () => {
    let finish!: (value: ServerNearbyHelp) => void;
    getMock.mockImplementation(() => new Promise<ServerNearbyHelp>((resolve) => { finish = resolve; }));

    const view = render(<NearbyHelpScreen />);
    await waitFor(() => expect(getMock).toHaveBeenCalledTimes(1));
    await waitFor(() => expect(mockMap.renders.length).toBeGreaterThan(0));
    view.rerender(<NearbyHelpScreen />);
    view.rerender(<NearbyHelpScreen />);
    // 아직 목록이 없는 동안 — 지도는 빈 목록을 받고, 그 빈 목록은 매번 같은 배열이다
    const waiting = mockMap.renders.filter((props) => props.stops.length === 0);
    expect(waiting.length).toBeGreaterThan(1);
    expect(new Set(waiting.map((props) => props.stops)).size).toBe(1);

    await act(async () => { finish(HOSPITALS); });
    await waitFor(() => expect(lastMap().stops).toHaveLength(2));
    await waitFor(() => expect(lastMap().selectedId).toBe('hospital-0'));
  });

  it('탭을 바꾸면 그 종류로 다시 찾고, 그 종류의 번호 점이 지도에 채워진다', async () => {
    const view = render(<NearbyHelpScreen />);
    await waitFor(() => expect(lastMap()?.stops).toHaveLength(2));

    fireEvent.press(view.getByTestId('nearby-kind-pharmacy'));
    await waitFor(() => expect(getMock).toHaveBeenLastCalledWith('pharmacy', MY_POSITION, null));
    await waitFor(() => expect(lastMap().stops).toHaveLength(3));
    expect(lastMap().stops.map((stop) => stop.id)).toEqual(['pharmacy-0', 'pharmacy-1', 'pharmacy-2']);
    await waitFor(() => expect(lastMap().selectedId).toBe('pharmacy-0'));
  });

  it('🔴 목록이 오는 «바로 그 렌더»에서 첫 곳이 이미 골라져 있다 — 한 렌더 늦게 고르면 지도가 「고른 곳이 바뀜」으로 알고 1번으로 확대해, 멀리 흩어진 곳(경찰)은 1번 점만 보인다', async () => {
    const view = render(<NearbyHelpScreen />);
    await waitFor(() => expect(lastMap()?.stops).toHaveLength(2));
    fireEvent.press(view.getByTestId('nearby-kind-pharmacy'));
    await waitFor(() => expect(lastMap().stops).toHaveLength(3));
    fireEvent.press(view.getByTestId('nearby-kind-hospital'));
    await waitFor(() => expect(lastMap().stops).toHaveLength(2));
    // 목록을 가진 모든 렌더에서, 고른 곳은 그 목록 안의 곳이다(빈 값도 지난 탭의 것도 아니다)
    const withList = mockMap.renders.filter((props) => props.stops.length > 0);
    expect(withList.length).toBeGreaterThan(2);
    for (const props of withList) expect(props.stops.map((stop) => stop.id)).toContain(props.selectedId);
  });

  it('탭을 바꿨다 돌아오면 전에 누른 곳이 아니라 첫 곳부터 — 지난 탭에서 누른 것을 끌고 오지 않는다', async () => {
    const view = render(<NearbyHelpScreen />);
    await waitFor(() => expect(lastMap()?.stops).toHaveLength(2));
    fireEvent.press(view.getByLabelText('부산진병원 지도에서 보기'));
    await waitFor(() => expect(lastMap().selectedId).toBe('hospital-1'));

    fireEvent.press(view.getByTestId('nearby-kind-pharmacy'));
    await waitFor(() => expect(lastMap().stops).toHaveLength(3));
    expect(lastMap().selectedId).toBe('pharmacy-0');

    fireEvent.press(view.getByTestId('nearby-kind-hospital'));
    await waitFor(() => expect(lastMap().stops).toHaveLength(2));
    expect(lastMap().selectedId).toBe('hospital-0');
  });

  it('🔴 ④ 목록의 곳을 누르면 지도의 그 점이, 지도의 점을 누르면 목록의 그 곳이 골라진다', async () => {
    const view = render(<NearbyHelpScreen />);
    await waitFor(() => expect(lastMap()?.stops).toHaveLength(2));
    await waitFor(() => expect(lastMap().selectedId).toBe('hospital-0'));
    const row = (name: string) => view.getByLabelText(`${name} 지도에서 보기`);
    expect(row('서면의원').props.accessibilityState.selected).toBe(true);
    expect(row('부산진병원').props.accessibilityState.selected).toBe(false);

    // 목록 → 지도
    fireEvent.press(row('부산진병원'));
    await waitFor(() => expect(lastMap().selectedId).toBe('hospital-1'));
    expect(row('부산진병원').props.accessibilityState.selected).toBe(true);

    // 지도 → 목록
    act(() => { lastMap().onSelect('hospital-0'); });
    await waitFor(() => expect(row('서면의원').props.accessibilityState.selected).toBe(true));
    expect(row('부산진병원').props.accessibilityState.selected).toBe(false);
  });
});
