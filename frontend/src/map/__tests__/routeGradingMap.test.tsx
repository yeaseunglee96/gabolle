// 지도가 경로 선을 «고른 조건대로» 칠하는가 — S15P21E201-1896.
//
// 🔴 이 시험이 지키는 것:
//    ① 조건을 안 골랐으면(grading 둘 다 false) 조각이 있어도 경로 자기 색 한 가지로 긋는다 — 웹·앱(WebView로 보내는 값) 둘 다.
//       전에는 걷는 구간이면 늘 경사 색이었다.
//    ② 경사만 · 그늘만 · 둘 다 고르면 각각의 규칙대로 조각마다 색을 달리해 긋는다.
//    ③ grading 을 안 준 경로(이동 경로 상세)는 전처럼 경사로 칠한다.
//    웹 지도(RouteMap.tsx)와 앱 지도(RouteMap.native.tsx → WebView 안의 HTML) 둘 다 본다. 여행 중 GPS 로 따라갈 때도
//    같은 부품·같은 routes 라서, 여기서 본 색이 그대로 나온다.
import { Platform } from 'react-native';
import { act, create } from 'react-test-renderer';

import { color } from '@/design/tokens';
import { buildKakaoMapHtml } from '@/map/kakaoMapHtml';
import type { RouteGrading } from '@/map/routeGrading';
import type { SlopePiece } from '@/map/slopeGrades';
// eslint-disable-next-line @typescript-eslint/no-require-imports
const { RouteMap } = require('../RouteMap.tsx') as typeof import('../RouteMap');

const mockWebViewCalls: string[] = [];
let mockWebViewProps: { onMessage?: (event: { nativeEvent: { data: string } }) => void } = {};
jest.mock('react-native-webview', () => {
  const React = jest.requireActual('react');
  return {
    WebView: React.forwardRef((props: object, ref: unknown) => {
      React.useImperativeHandle(ref, () => ({ injectJavaScript: (js: string) => { mockWebViewCalls.push(js); } }));
      mockWebViewProps = props;
      return null;
    }),
  };
});
jest.mock('@/i18n', () => ({ useI18n: () => ({ tx: (ko: string) => ko }) }));

const fakeElement = () => ({ style: {}, setAttribute() {}, appendChild() {}, textContent: '', onclick: null });
const fakeDocument = { createElement: fakeElement, addEventListener() {}, removeEventListener() {}, getElementById: () => ({}), head: { appendChild() {} } };
(globalThis as unknown as { document: unknown }).document = fakeDocument;
type LineOptions = { path: unknown[]; strokeWeight: number; strokeColor: string; strokeOpacity: number };
const lines: LineOptions[] = [];
class LatLng { constructor(public lat: number, public lng: number) {} getLat() { return this.lat; } getLng() { return this.lng; } }
class Point { constructor(public x: number, public y: number) {} }
class FakeMap { relayout() {} setBounds() {} setCenter() {} setLevel() {} panTo() {} getProjection() { return { containerPointFromCoords: () => new Point(0, 0), coordsFromContainerPoint: () => new LatLng(0, 0) }; } }
const fakeMaps = {
  load: (cb: () => void) => cb(), LatLng, Point, Map: FakeMap,
  LatLngBounds: class { extend() {} }, CustomOverlay: class { setMap() {} setPosition() {} },
  Polyline: class { constructor(options: LineOptions) { lines.push(options); } setMap() {} setPath() {} },
  event: { addListener() {} },
};

const at = (latitude: number, longitude: number) => ({ latitude, longitude });
const path = [at(35.1, 129.1), at(35.101, 129.1), at(35.102, 129.1), at(35.103, 129.1), at(35.104, 129.1)];
const mapStops = [{ id: 'a', number: 1, name: '해운대역', latitude: 35.1, longitude: 129.1 }, { id: 'b', number: 2, name: '해운대', latitude: 35.104, longitude: 129.1 }];
// 가파르고 볕뿐 · 조금 가파르지만 그늘 많음 · 계단 · 경사 모르고 그늘 짙음
const pieces: SlopePiece[] = [
  { from: 0, to: 1, slopePercent: 9.1, stairs: false, shade: 0 },
  { from: 1, to: 2, slopePercent: 6, stairs: false, shade: 0.9 },
  { from: 2, to: 3, slopePercent: 1, stairs: true, shade: 0.9 },
  { from: 3, to: 4, slopePercent: null, stairs: false, shade: 0.8 },
];
const routeOf = (grading?: RouteGrading) => ({ id: 'leg', color: color.brand.navy, stops: mapStops, path, estimated: false, pieces, ...(grading ? { grading } : {}) });
const routeColors = () => lines.filter((line) => line.strokeColor !== color.surface.card).map((line) => line.strokeColor);

const { success, warning, danger, muted } = { success: color.state.success, warning: color.state.warning, danger: color.state.danger, muted: color.text.muted };
// 조건별로 기대하는 색 (이웃한 같은 색은 한 선으로 합쳐진다)
const EXPECT = {
  slope: [danger, warning, danger, muted], // 9.1% 빨강 · 6% 노랑 · 계단 빨강 · 경사 모름 회색
  shade: [danger, success, danger, success], // 그늘 0 빨강 · 0.9 초록 · 계단 빨강 · 0.8 초록 — 경사는 안 본다
  // 합친 점수(S15P21E201-1898): 9.1%·그늘 0 → 0.81 빨강 · 6%·그늘 0.9 → 0.6·0.43+0.4·0.05 = 0.28 초록(경사만 보면 노랑이지만
  // 그늘이 넉넉하다) · 계단 빨강 · 경사 모르면 그늘만(0.8 → 0.09 초록)
  both: [danger, success, danger, success],
};

describe('웹 지도 — 고른 조건대로 칠하기', () => {
  const originalOs = Platform.OS;
  beforeAll(() => {
    Object.defineProperty(Platform, 'OS', { value: 'web', configurable: true });
    process.env.EXPO_PUBLIC_KAKAO_MAP_JS_KEY = 'test-key';
    (window as unknown as { kakao: unknown }).kakao = { maps: fakeMaps };
  });
  afterAll(() => {
    Object.defineProperty(Platform, 'OS', { value: originalOs, configurable: true });
    delete (window as unknown as { kakao?: unknown }).kakao;
  });
  beforeEach(() => { lines.length = 0; });
  const mount = (route: object) => {
    act(() => { create(<RouteMap stops={mapStops} routes={[route as never]} selectedId="a" onSelect={() => {}} />, { createNodeMock: () => fakeDocument.createElement() }); });
  };

  it('🔴 조건을 안 골랐으면 조각이 있어도 경로 자기 색 한 가지 — 전처럼 늘 경사 색이 아니다', () => {
    mount(routeOf({ slope: false, shade: false }));
    expect(routeColors()).toEqual([color.brand.navy]);
  });

  it('🔴 경사만 골랐으면 경사 색', () => {
    mount(routeOf({ slope: true, shade: false }));
    expect(routeColors()).toEqual(EXPECT.slope);
  });

  it('🔴 그늘만 골랐으면 그늘 색 — 경사는 안 본다(경사를 모르는 마지막 조각도 그늘로 칠한다)', () => {
    mount(routeOf({ slope: false, shade: true }));
    expect(routeColors()).toEqual(EXPECT.shade);
  });

  it('🔴 둘 다 골랐으면 합친 점수 — 6%·그늘 0.9 는 초록(경사만 보면 노랑), 경사 모르는 조각은 그늘만으로', () => {
    expect(EXPECT.both).not.toEqual(EXPECT.slope); // 합친 점수가 «경사만» 과 다르게 칠한다
    mount(routeOf({ slope: true, shade: true }));
    expect(routeColors()).toEqual(EXPECT.both);
  });

  it('🔴 grading 을 안 준 경로는 전처럼 경사로 칠한다(이동 경로 상세 화면)', () => {
    mount(routeOf());
    expect(routeColors()).toEqual(EXPECT.slope);
  });

  it('조각이 없으면 조건을 골랐어도 한 선이다', () => {
    mount({ ...routeOf({ slope: true, shade: true }), pieces: undefined });
    expect(routeColors()).toEqual([color.brand.navy]);
  });
});

describe('앱 지도(WebView 안의 HTML) — 앱이 잘라 보낸 조각', () => {
  beforeEach(() => { lines.length = 0; });
  it('🔴 앱이 고른 조건대로 잘라 보낸 조각마다 색을 달리해 긋는다', () => {
    const html = buildKakaoMapHtml('test-key');
    const code = html.split('<script>')[1].split('</script>')[0];
    const win: Record<string, unknown> = { addEventListener() {}, kakao: { maps: fakeMaps } };
    new Function('window', 'document', code)(win, fakeDocument);
    // 앱이 보내는 것과 같은 함수로 자른다
    // eslint-disable-next-line @typescript-eslint/no-require-imports
    const { gradedSegments } = require('../routeGrading') as typeof import('../routeGrading');
    const segments = gradedSegments(path, pieces, { slope: true, shade: true });
    (win.__renderKakaoMap as (data: unknown) => void)({
      stops: mapStops, points: [], routes: [{ ...routeOf({ slope: true, shade: true }), segments }], selectedId: 'a', currentLocation: null, fitPadding: [60, 60, 60, 60],
      colors: { navy: '#123', selected: '#456', canvas: '#fff', casing: color.surface.card }, focus: false, shiftY: 0,
    });
    expect(routeColors()).toEqual(EXPECT.both);
  });
});

describe('앱 지도(폰) — WebView 로 보내는 값', () => {
  const sent = () => {
    const call = mockWebViewCalls.filter((js) => js.startsWith('window.__renderKakaoMap(')).pop();
    return JSON.parse(call!.replace(/^window\.__renderKakaoMap\(/, '').replace(/\); true;$/, '')) as { routes: Array<{ segments?: Array<{ color: string; points: unknown[] }>; grading?: RouteGrading }> };
  };
  const mountNative = (route: object) => {
    // eslint-disable-next-line @typescript-eslint/no-require-imports
    const { RouteMap: NativeRouteMap } = require('../RouteMap.native.tsx') as typeof import('../RouteMap.native');
    process.env.EXPO_PUBLIC_KAKAO_MAP_JS_KEY = 'test-key';
    mockWebViewCalls.length = 0;
    act(() => { create(<NativeRouteMap stops={mapStops} routes={[route as never]} selectedId="a" onSelect={() => {}} />); });
    // 지도 SDK 가 떴다는 신호 — 이때 앱이 그릴 것을 보낸다
    act(() => { mockWebViewProps.onMessage?.({ nativeEvent: { data: JSON.stringify({ type: 'sdkLoaded' }) } }); });
    return sent();
  };

  it('🔴 조건을 안 골랐으면 조각을 잘라 보내지 않는다 — 한 선', () => {
    const payload = mountNative(routeOf({ slope: false, shade: false }));
    expect(payload.routes[0].segments).toBeUndefined();
  });

  it('🔴 고른 조건대로 자른 조각과 색을 보낸다 — 웹 지도와 같은 함수(gradedSegments)로 자른다', () => {
    expect(mountNative(routeOf({ slope: true, shade: false })).routes[0].segments?.map((segment) => segment.color)).toEqual(EXPECT.slope);
    expect(mountNative(routeOf({ slope: false, shade: true })).routes[0].segments?.map((segment) => segment.color)).toEqual(EXPECT.shade);
    expect(mountNative(routeOf({ slope: true, shade: true })).routes[0].segments?.map((segment) => segment.color)).toEqual(EXPECT.both);
  });

  it('grading 을 안 준 경로는 전처럼 경사로 자른다', () => {
    expect(mountNative(routeOf()).routes[0].segments?.map((segment) => segment.color)).toEqual(EXPECT.slope);
  });
});
