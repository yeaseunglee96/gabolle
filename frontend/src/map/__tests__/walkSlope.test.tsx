// 여행 지도 — 걷는 길을 경사 색 조각으로 칠하기 — S15P21E201-1658 (백엔드 !1626 의 pieces).
//
// 🔴 이 시험이 지키는 것:
//    ① 색은 세 단계다(사용자 결정) — 5% 미만 초록 · 5~8.33% 노랑 · 8.33% 이상·계단 빨강 · 모름 회색.
//    ② 조각은 path 의 자리 번호(from·to, 둘 다 포함)로 자른다. 번호가 path 밖이면 조각을 버리고 한 선으로 그린다.
//    ③ 🔴 스위치가 꺼져 있으면(지금 — !1626 배포 전) 걷기 요청을 보내지 않는다. 켜면 걷는 구간만 걷기로 받는다.
//    ④ 조각이 있으면 웹·앱 지도 둘 다 조각마다 색을 달리해 긋는다. 🔴 색의 뜻 안내 문구는 기본 화면에 안 띄운다(S15P21E201-1820, 어색하다는 사용자 결정).
import { Platform } from 'react-native';
import { act, create } from 'react-test-renderer';
import { renderHook, waitFor } from '@testing-library/react-native';

import { color } from '@/design/tokens';
import { buildKakaoMapHtml } from '@/map/kakaoMapHtml';
import { clearCourseRoutePathCache, legKey, useCourseRoutePaths, WALK_SLOPE_ROUTES } from '@/map/courseRoutePaths';
import { getRouteDirections } from '@/map/routeDirections';
import { MODERATE_SLOPE_PERCENT, slopeColor, slopeSegments, STEEP_SLOPE_PERCENT } from '@/map/slopeGrades';
// eslint-disable-next-line @typescript-eslint/no-require-imports
const { RouteMap } = require('../RouteMap.tsx') as typeof import('../RouteMap');

jest.mock('@/map/routeDirections', () => ({ getRouteDirections: jest.fn() }));
jest.mock('react-native-webview', () => ({ WebView: () => null }));
jest.mock('@/i18n', () => ({ useI18n: () => ({ tx: (ko: string) => ko }) }));
const mockedDirections = getRouteDirections as jest.Mock;

const piece = (slopePercent: number | null, stairs = false) => ({ from: 0, to: 1, slopePercent, stairs });
const at = (latitude: number, longitude: number) => ({ latitude, longitude });
const path = [at(35.1, 129.1), at(35.101, 129.1), at(35.102, 129.1), at(35.103, 129.1), at(35.104, 129.1)];
const pieces = [
  { from: 0, to: 1, slopePercent: null, stairs: false },
  { from: 1, to: 3, slopePercent: 2.3, stairs: false },
  { from: 3, to: 4, slopePercent: null, stairs: true },
];

describe('경사 색', () => {
  it('🔴 세 단계와 모름 — 8.33% 는 경사 판정과 같은 값, 5% 는 우리가 정한 값', () => {
    expect(STEEP_SLOPE_PERCENT).toBe(8.33);
    expect(MODERATE_SLOPE_PERCENT).toBe(5);
    expect(slopeColor(piece(4.99))).toBe(color.state.success);
    expect(slopeColor(piece(5))).toBe(color.state.warning);
    expect(slopeColor(piece(8.32))).toBe(color.state.warning);
    expect(slopeColor(piece(8.33))).toBe(color.state.danger);
    expect(slopeColor(piece(1, true))).toBe(color.state.danger);
    expect(slopeColor(piece(null))).toBe(color.text.muted);
    expect(slopeColor(piece(null, true))).toBe(color.state.danger);
  });

  it('🔴 조각은 path 의 자리 번호로 자른다 — 둘 다 포함, 이웃 조각은 한 점을 나눈다', () => {
    const segments = slopeSegments(path, pieces);
    expect(segments?.map((segment) => segment.points.length)).toEqual([2, 3, 2]);
    expect(segments?.[1].points[0]).toBe(path[1]);
    expect(segments?.map((segment) => segment.color)).toEqual([color.text.muted, color.state.success, color.state.danger]);
  });

  it('번호가 path 밖이거나 조각이 없으면 null — 한 선으로 그린다', () => {
    expect(slopeSegments(path, [{ from: 0, to: 9, slopePercent: 1, stairs: false }])).toBeNull();
    expect(slopeSegments(path, [])).toBeNull();
    expect(slopeSegments(path, undefined)).toBeNull();
  });
});

describe('걷는 구간 받아 오기', () => {
  const stops = [
    { id: 'a', number: 1, name: '해운대역', latitude: 35.1636, longitude: 129.1588 },
    { id: 'b', number: 2, name: '해운대해수욕장', latitude: 35.1587, longitude: 129.1604 },
  ];
  const days = [{ day: 1, stops }];
  const walkResponse = { state: 'success', directions: { mode: 'WALK', path: path.map((p) => [p.longitude, p.latitude]), pieces, estimated: false, provider: 'OSM_WALK_GRAPH' } };
  beforeEach(() => { clearCourseRoutePathCache(); mockedDirections.mockReset(); mockedDirections.mockResolvedValue(walkResponse); });

  it('🔴 스위치는 켜져 있다 — 따로 말하지 않아도 걷는 구간은 걷기로 받고 조각을 싣는다(백엔드 !1626 운영 배포 뒤, S15P21E201-1712)', async () => {
    expect(WALK_SLOPE_ROUTES).toBe(true);
    const { result } = renderHook(() => useCourseRoutePaths(days, 'token', { walkInto: new Set(['b']) }));
    await waitFor(() => expect(result.current[legKey(1, 0)]).toBeTruthy());
    expect(mockedDirections.mock.calls[0][0].mode).toBe('WALK');
    expect(result.current[legKey(1, 0)].pieces).toEqual(pieces);
  });

  it('🔴 켜면 걷는 구간만 걷기로 받고, 조각을 싣는다', async () => {
    const { result } = renderHook(() => useCourseRoutePaths(days, 'token', { walkInto: new Set(['b']), walkSlope: true }));
    await waitFor(() => expect(result.current[legKey(1, 0)]).toBeTruthy());
    expect(mockedDirections.mock.calls[0][0].mode).toBe('WALK');
    expect(result.current[legKey(1, 0)].pieces).toEqual(pieces);
  });

  it('켜도 걷는 구간이 아니면 지금처럼 방식 없이 받는다', async () => {
    const { result } = renderHook(() => useCourseRoutePaths(days, 'token', { walkInto: new Set(), walkSlope: true }));
    await waitFor(() => expect(result.current[legKey(1, 0)]).toBeTruthy());
    expect(mockedDirections.mock.calls[0][0].mode).toBeUndefined();
  });
});

// ── 지도 ────────────────────────────────────────────────────────────────
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
const mapStops = [{ id: 'a', number: 1, name: '해운대역', latitude: 35.1, longitude: 129.1 }, { id: 'b', number: 2, name: '해운대', latitude: 35.104, longitude: 129.1 }];
const walkRoute = { id: 'leg', color: color.brand.navy, stops: mapStops, path, estimated: false, pieces };
const routeColors = () => lines.filter((line) => line.strokeColor !== color.surface.card).map((line) => line.strokeColor);

describe('웹 지도 — 경사 조각', () => {
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

  it('🔴 조각마다 색을 달리해 긋고, 경사 안내 문구는 띄우지 않는다(S15P21E201-1820)', () => {
    let tree!: ReturnType<typeof create>;
    act(() => { tree = create(<RouteMap stops={mapStops} routes={[walkRoute]} selectedId="a" onSelect={() => {}} />, { createNodeMock: () => fakeDocument.createElement() }); });
    expect(routeColors()).toEqual([color.text.muted, color.state.success, color.state.danger]);
    const text = JSON.stringify(tree.toJSON());
    expect(text).not.toContain('초록 완만');
    expect(text).not.toContain('걷는 길 —');
  });

  it('조각이 없는 경로는 지금처럼 한 선이고 안내도 없다', () => {
    let tree!: ReturnType<typeof create>;
    act(() => { tree = create(<RouteMap stops={mapStops} routes={[{ ...walkRoute, pieces: undefined }]} selectedId="a" onSelect={() => {}} />, { createNodeMock: () => fakeDocument.createElement() }); });
    expect(routeColors()).toEqual([color.brand.navy]);
    expect(JSON.stringify(tree.toJSON())).not.toContain('초록 완만');
  });
});

describe('앱 지도(WebView 안의 HTML) — 경사 조각', () => {
  beforeEach(() => { lines.length = 0; });
  it('🔴 앱이 잘라 보낸 조각마다 색을 달리해 긋는다', () => {
    const html = buildKakaoMapHtml('test-key');
    const code = html.split('<script>')[1].split('</script>')[0];
    const win: Record<string, unknown> = { addEventListener() {}, kakao: { maps: fakeMaps } };
    new Function('window', 'document', code)(win, fakeDocument);
    const segments = slopeSegments(path, pieces);
    (win.__renderKakaoMap as (data: unknown) => void)({
      stops: mapStops, points: [], routes: [{ ...walkRoute, segments }], selectedId: 'a', currentLocation: null, fitPadding: [60, 60, 60, 60],
      colors: { navy: '#123', selected: '#456', canvas: '#fff', casing: color.surface.card }, focus: false, shiftY: 0,
    });
    expect(routeColors()).toEqual([color.text.muted, color.state.success, color.state.danger]);
  });
});

describe('앱 지도(폰) — 경사 안내 문구', () => {
  it('🔴 경사 조각이 있어도 안내 문구를 띄우지 않는다(S15P21E201-1820)', () => {
    // eslint-disable-next-line @typescript-eslint/no-require-imports
    const { RouteMap: NativeRouteMap } = require('../RouteMap.native.tsx') as typeof import('../RouteMap.native');
    process.env.EXPO_PUBLIC_KAKAO_MAP_JS_KEY = 'test-key';
    let tree!: ReturnType<typeof create>;
    act(() => { tree = create(<NativeRouteMap stops={mapStops} routes={[walkRoute]} selectedId="a" onSelect={() => {}} />); });
    const text = JSON.stringify(tree.toJSON());
    expect(text).not.toContain('초록 완만');
    expect(text).not.toContain('걷는 길 —');
  });
});
