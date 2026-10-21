// 길 안내(/route-detail)에서 게스트가 서버 원문 오류를 보던 것 — S15P21E201-2060 (2026-10-16 웹 점검).
//
// 🔴 근처 편의시설·가까운 도움 카드의 「길 안내」는 이 화면으로 온다. 서버는 길찾기를 로그인한 사람에게만 열어서(RouteController —
//    바깥 업체 호출을 우리 비용으로 대신 시켜 주는 창구를 막으려는 것) 게스트의 요청은 셋 다 401 이었고, 화면에는
//    「경로를 불러오지 못했어요 · 인증 정보가 올바르지 않습니다.」가 그대로 떴다.
//
// 🔴 지키는 것
//   ① 게스트에게는 요청을 보내지 않는다(어차피 401) — 대신 「로그인하면 길 안내를 보여드려요」 + 로그인하기 + 지도 앱으로 열기.
//   ② 로그인하면 이 길 안내 그대로 돌아온다(returnTo 에 출발·도착·수단이 다 들어 있다).
//   ③ 지도 앱으로 열기는 출발·도착 좌표를 그대로 넘긴다.
//   ④ 로그인한 사람은 전과 같다 — 세 수단을 한꺼번에 묻는다. 로그인 복원 중에는 아직 아무것도 안 보낸다.
import { fireEvent, render, screen, waitFor } from '@testing-library/react-native';

import { isSafeReturnPath } from '@/auth/pendingReturnTo';
import { getRouteDirections } from '@/map/routeDirections';
import { listAvailableRouteMapApps } from '@/utils/externalMaps';

const mockPush = jest.fn();
let mockAuth: { accessToken: string | null; ready: boolean } = { accessToken: null, ready: true };

jest.mock('expo-router', () => ({
  useRouter: () => ({ push: mockPush, replace: jest.fn(), back: jest.fn(), canGoBack: () => true }),
  useLocalSearchParams: () => ({
    originLat: '35.1532', originLng: '129.1186', originName: '내 위치',
    destLat: '35.1587', destLng: '129.1604', destName: '수영약국', mode: 'WALK',
  }),
}));
jest.mock('@/auth/AuthProvider', () => ({ useAuth: () => mockAuth }));
jest.mock('@/i18n', () => ({ useI18n: () => ({ tx: (ko: string) => ko, language: 'ko' }) }));
jest.mock('@/components/Screen', () => ({
  Screen: ({ children }: { children: unknown }) => { const { ScrollView } = jest.requireActual('react-native'); return <ScrollView>{children as never}</ScrollView>; },
}));
jest.mock('@/map/RouteMap', () => ({ RouteMap: () => null }));
jest.mock('@/personalization/useLocationGate', () => ({ useLocationGate: () => ({ request: jest.fn(), ensure: jest.fn(), sheet: null }) }));
jest.mock('@/trip/page/useLiveLocation', () => ({ useLiveLocation: () => ({ fix: null }) }));
jest.mock('@/layout/useLayout', () => ({ useLayout: () => ({ kind: 'phone', width: 390, height: 844, desktop: false }) }));
jest.mock('@/map/routeDirections', () => ({ ...jest.requireActual('@/map/routeDirections'), getRouteDirections: jest.fn() }));
jest.mock('@/utils/externalMaps', () => ({ ...jest.requireActual('@/utils/externalMaps'), listAvailableRouteMapApps: jest.fn() }));

import RouteDetail from '../route-detail';

const mockedDirections = getRouteDirections as jest.Mock;
const mockedMapApps = listAvailableRouteMapApps as jest.Mock;

type JsonNode = { type: string; props: Record<string, unknown>; children: unknown };
/** 그려진 트리에서 조건에 맞는 첫 노드 — 화면에서 빠진(display: none) 것도 트리에는 남는다. */
function findNode(node: unknown, match: (node: JsonNode) => boolean): JsonNode | null {
  if (!node || typeof node !== 'object') return null;
  if (Array.isArray(node)) { for (const child of node) { const found = findNode(child, match); if (found) return found; } return null; }
  const current = node as JsonNode;
  if (match(current)) return current;
  return findNode(current.children, match);
}

const NOTICE_TITLE = '로그인하면 길 안내를 보여드려요';
const SERVER_RAW = '인증 정보가 올바르지 않습니다.';

beforeEach(() => {
  jest.clearAllMocks();
  mockAuth = { accessToken: null, ready: true };
  mockedDirections.mockResolvedValue({ state: 'error', message: SERVER_RAW });
});

describe('게스트', () => {
  it('🔴 요청을 보내지 않고, 서버의 401 원문 대신 로그인 안내를 보인다', async () => {
    render(<RouteDetail />);

    expect(await screen.findByText(NOTICE_TITLE)).toBeTruthy();
    expect(mockedDirections).not.toHaveBeenCalled();
    expect(screen.queryByText(SERVER_RAW)).toBeNull();
    expect(screen.queryByText('경로를 불러오지 못했어요')).toBeNull();
    expect(screen.queryByText('경로를 불러오고 있어요…')).toBeNull();
    // 안내는 이유를 말한다 — 「한도가 있는 외부 자료」라서, 그리고 지금 할 수 있는 길(지도 앱)을.
    expect(screen.getByText(/로그인한 분에게만/)).toBeTruthy();
    expect(screen.getByText(/지도 앱에서 바로 길을 찾을 수 있어요/)).toBeTruthy();
  });

  it('🔴 「로그인하기」는 이 길 안내로 돌아오는 주소를 들고 간다 — 출발·도착·수단이 그대로 들어 있고 안전한 앱 안 주소다', async () => {
    render(<RouteDetail />);
    fireEvent.press(await screen.findByTestId('route-guest-sign-in'));

    expect(mockPush).toHaveBeenCalledTimes(1);
    const { pathname, params } = mockPush.mock.calls[0][0] as { pathname: string; params: { returnTo: string } };
    expect(pathname).toBe('/sign-in');
    expect(isSafeReturnPath(params.returnTo)).toBe(true);
    const [path, query] = params.returnTo.split('?');
    expect(path).toBe('/route-detail');
    expect(Object.fromEntries(new URLSearchParams(query))).toEqual({
      originLat: '35.1532', originLng: '129.1186', originName: '내 위치',
      destLat: '35.1587', destLng: '129.1604', destName: '수영약국', mode: 'WALK',
    });
  });

  it('🔴 「지도 앱으로 열기」는 출발·도착 좌표를 그대로 넘겨 지도 앱 길찾기를 연다', async () => {
    const open = jest.fn().mockResolvedValue(undefined);
    mockedMapApps.mockResolvedValue([{ key: 'kakao', labelKo: '카카오맵', labelEn: 'KakaoMap', open }]);
    render(<RouteDetail />);

    fireEvent.press(await screen.findByTestId('route-guest-map-app'));

    await waitFor(() => expect(open).toHaveBeenCalledTimes(1));
    expect(mockedMapApps).toHaveBeenCalledWith({ originLat: 35.1532, originLng: 129.1186, destLat: 35.1587, destLng: 129.1604, destName: '수영약국' });
  });

  it('지도 앱을 못 열어도 화면이 죽지 않는다', async () => {
    mockedMapApps.mockRejectedValue(new Error('no app'));
    render(<RouteDetail />);

    fireEvent.press(await screen.findByTestId('route-guest-map-app'));

    await waitFor(() => expect(mockedMapApps).toHaveBeenCalled());
    expect(screen.getByText(NOTICE_TITLE)).toBeTruthy();
  });

  it('수단 탭은 숨긴다 — 받을 길이 없는데 「찾는 중…」이 영영 도는 것을 막는다', async () => {
    render(<RouteDetail />);
    await screen.findByText(NOTICE_TITLE);

    // 탭은 그려지되 화면에서 빠진다(display: none) — 보이는 것만 찾는 기본 조회에는 안 잡히고, 그려진 트리를 보면 스타일이 걸려 있다.
    expect(screen.queryByText('찾는 중…')).toBeNull();
    const tablist = findNode(screen.toJSON(), (node) => node.props?.accessibilityRole === 'tablist');
    expect(tablist).toBeTruthy();
    expect(JSON.stringify(tablist?.props.style)).toContain('"display":"none"');
  });
});

describe('로그인한 사람 · 복원 중', () => {
  it('🔴 로그인한 사람은 전과 같다 — 세 수단을 한꺼번에 묻고, 안내는 안 보인다', async () => {
    mockAuth = { accessToken: 'member-token', ready: true };
    render(<RouteDetail />);

    await waitFor(() => expect(mockedDirections).toHaveBeenCalledTimes(3));
    expect(mockedDirections.mock.calls.map((call) => call[0].mode).sort()).toEqual(['CAR', 'TRANSIT', 'WALK']);
    for (const call of mockedDirections.mock.calls) expect(call[1]).toBe('member-token');
    expect(screen.queryByText(NOTICE_TITLE)).toBeNull();
  });

  it('🔴 로그인 복원 중에는 아무것도 안 보내고 안내도 안 띄운다 — 로그인한 사람에게 잠깐 「로그인하면…」이 비치지 않게', async () => {
    mockAuth = { accessToken: null, ready: false };
    render(<RouteDetail />);

    expect(await screen.findByText('경로를 불러오고 있어요…')).toBeTruthy();
    expect(mockedDirections).not.toHaveBeenCalled();
    expect(screen.queryByText(NOTICE_TITLE)).toBeNull();
  });

  it('복원이 끝나 로그인한 것으로 밝혀지면 그때 묻는다', async () => {
    mockAuth = { accessToken: null, ready: false };
    const view = render(<RouteDetail />);
    expect(mockedDirections).not.toHaveBeenCalled();

    mockAuth = { accessToken: 'member-token', ready: true };
    view.rerender(<RouteDetail />);

    await waitFor(() => expect(mockedDirections).toHaveBeenCalledTimes(3));
    expect(screen.queryByText(NOTICE_TITLE)).toBeNull();
  });
});
