// 앱(폰) 지도는 «그릴 장소가 아직 없을 때» 안전한가 — S15P21E201-1889.
//
// 웹 지도는 빈 목록에서 던져 가까운 도움 화면이 회색 칸이 되거나 통째로 무너졌다(routeMapEmptyStops.test.tsx).
// 같은 화면이 폰 앱에서도 같은 일을 겪는지를 코드로 확인한다 — 실기기는 못 돌려서 WebView 를 가짜로 두고 «무엇을 보내는가»를 본다.
//   ① 빈 목록이면 WebView 를 아예 안 띄운다(그러니 카카오 SDK 가 빈 목록으로 불릴 일이 없다).
//   ② WebView 안 스크립트(kakaoMapHtml.ts)도 빈 입력에는 지도를 만들지도 던지지도 않는다.
//   ③ 탭을 바꿔 목록이 비었다 다시 차서 WebView 가 «다시 만들어져도», 새 WebView 가 준비됐다고 알리면 새 목록을 다시 그려 달라고 보낸다.
import { act, create, type ReactTestRenderer } from 'react-test-renderer';

import { buildKakaoMapHtml } from '@/map/kakaoMapHtml';
import type { MapStop } from '@/map/types';
// 🔴 jest(플랫폼 ios)에서 '@/map/RouteMap' 은 RouteMap.native.tsx 로 풀린다 — 앱 쪽을 본다.
import { RouteMap } from '@/map/RouteMap';

// jest.mock 공장 안에서는 `mock` 으로 시작하는 바깥 변수만 쓸 수 있다.
const mockWebView = {
  mounts: 0,
  injected: [] as string[],
  onMessage: null as null | ((event: { nativeEvent: { data: string } }) => void),
};
jest.mock('react-native-webview', () => {
  // eslint-disable-next-line @typescript-eslint/no-require-imports
  const React = require('react');
  const WebView = React.forwardRef((props: { onMessage: (event: { nativeEvent: { data: string } }) => void }, ref: unknown) => {
    React.useImperativeHandle(ref, () => ({ injectJavaScript: (code: string) => { mockWebView.injected.push(code); } }));
    React.useEffect(() => { mockWebView.mounts += 1; }, []);
    mockWebView.onMessage = props.onMessage;
    return null;
  });
  return { WebView };
});
jest.mock('@/i18n', () => ({ useI18n: () => ({ tx: (ko: string) => ko }) }));

const NO_ROUTES: never[] = [];
const stops: MapStop[] = [
  { id: 'pharmacy-0', number: 1, name: '해인온누리약국', latitude: 35.1578, longitude: 129.0597 },
  { id: 'pharmacy-1', number: 2, name: '다솜약국', latitude: 35.1531, longitude: 129.0621 },
];
const police: MapStop[] = [{ id: 'police-0', number: 1, name: '부산진경찰서', latitude: 35.1621, longitude: 129.0533 }];

const element = (items: MapStop[]) => <RouteMap stops={items} routes={NO_ROUTES} selectedId="" onSelect={() => {}} focusSelected height={220} />;
const mount = (items: MapStop[]) => { let tree!: ReactTestRenderer; act(() => { tree = create(element(items)); }); return tree; };
/** WebView 가 «카카오 SDK 가 떴다»고 알린다. */
const sdkLoaded = () => act(() => { mockWebView.onMessage?.({ nativeEvent: { data: JSON.stringify({ type: 'sdkLoaded' }) } }); });
const lastRender = () => mockWebView.injected.filter((code) => code.startsWith('window.__renderKakaoMap(')).pop() ?? '';

beforeAll(() => { process.env.EXPO_PUBLIC_KAKAO_MAP_JS_KEY = 'test-key'; });
beforeEach(() => { mockWebView.mounts = 0; mockWebView.injected = []; mockWebView.onMessage = null; });

describe('앱 지도 — 빈 목록', () => {
  it('🔴 ① 그릴 장소가 없으면 WebView 를 띄우지 않는다 — 카카오 SDK 가 빈 목록으로 불릴 일이 없다', () => {
    expect(() => mount([])).not.toThrow();
    expect(mockWebView.mounts).toBe(0);
  });

  it('장소가 있으면 WebView 를 띄우고, SDK 가 떴다는 알림이 오면 그 목록을 그려 달라고 보낸다', () => {
    mount(stops);
    expect(mockWebView.mounts).toBe(1);
    sdkLoaded();
    expect(lastRender()).toContain('해인온누리약국');
    expect(lastRender()).toContain('다솜약국');
  });

  it('🔴 ③ 탭을 바꿔 목록이 비었다 다시 차서 WebView 가 다시 만들어져도, 새 WebView 가 준비되면 «새» 목록을 그려 달라고 보낸다', () => {
    const tree = mount(stops);
    sdkLoaded();
    expect(mockWebView.mounts).toBe(1);

    act(() => { tree.update(element([])); }); // 탭을 바꾸는 순간 — 목록이 빈다
    expect(mockWebView.mounts).toBe(1);

    act(() => { tree.update(element(police)); }); // 다른 목록이 온다 — WebView 가 새로 만들어진다
    expect(mockWebView.mounts).toBe(2);
    mockWebView.injected = []; // 새 WebView 는 아직 HTML 을 읽는 중이라 이전에 보낸 것은 없다고 본다
    sdkLoaded(); // 새 WebView 가 «SDK 떴다»고 알린다
    expect(lastRender()).toContain('부산진경찰서');
    expect(lastRender()).not.toContain('해인온누리약국');
  });
});

describe('앱 지도 — WebView 안 스크립트(kakaoMapHtml.ts)', () => {
  // HTML 안의 스크립트를 가짜 window·document 로 실제로 돌린다(routeMapSelect.test.tsx 와 같은 방법).
  const calls = { maps: 0 };
  class LatLng { constructor(public lat: number, public lng: number) {} }
  class FakeMap { constructor() { calls.maps += 1; } relayout() {} setBounds() {} setCenter() {} setLevel() {} getLevel() { return 8; } panTo() {} getProjection() { return { containerPointFromCoords: () => ({ x: 0, y: 0 }), coordsFromContainerPoint: () => new LatLng(0, 0) }; } }
  const fakeMaps = { load: (cb: () => void) => cb(), LatLng, LatLngBounds: class { extend() {} }, CustomOverlay: class { setMap() {} setPosition() {} }, Polyline: class { setMap() {} }, Map: FakeMap, Point: class { constructor(public x: number, public y: number) {} } };
  const fakeEl = () => ({ style: {}, setAttribute() {}, appendChild() {}, textContent: '', onclick: null, clientHeight: 220, clientWidth: 390 });
  const fakeDocument = { createElement: fakeEl, addEventListener() {}, getElementById: () => fakeEl(), head: { appendChild() {} } };
  const boot = () => {
    const code = buildKakaoMapHtml('test-key').split('<script>')[1].split('</script>')[0];
    const win: Record<string, unknown> = { addEventListener() {}, kakao: { maps: fakeMaps } };
    new Function('window', 'document', code)(win, fakeDocument);
    return win as unknown as Record<string, (data: unknown) => void>;
  };
  const colors = { navy: '#123', selected: '#456', canvas: '#fff', casing: '#fff' };

  it('🔴 ② 빈 입력(장소도 점도 없음)이면 지도를 만들지도 던지지도 않는다', () => {
    calls.maps = 0;
    const win = boot();
    expect(() => win.__renderKakaoMap({ stops: [], points: [], routes: [], selectedId: '', currentLocation: null, fitPadding: [60, 60, 60, 60], colors, focus: true, shiftY: 0 })).not.toThrow();
    expect(calls.maps).toBe(0);
  });

  it('장소가 오면 그때 지도를 만든다', () => {
    calls.maps = 0;
    const win = boot();
    win.__renderKakaoMap({ stops: [], points: [], routes: [], selectedId: '', currentLocation: null, fitPadding: [60, 60, 60, 60], colors, focus: true, shiftY: 0 });
    win.__renderKakaoMap({ stops, points: [], routes: [], selectedId: 'pharmacy-0', currentLocation: null, fitPadding: [60, 60, 60, 60], colors, focus: true, shiftY: 0 });
    expect(calls.maps).toBe(1);
  });
});
