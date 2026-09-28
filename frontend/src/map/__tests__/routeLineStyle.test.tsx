// 여행 지도 경로 선 모양 — S15P21E201-1656.
//
// 🔴 이 시험이 지키는 것(사용자: 「선이 지글지글하다 — 네이버 지도는 부드럽다」):
//    ① 일정 경로 선은 점선이 아니다 — 넓은 흰 테두리 선을 먼저 깔고 그 위에 실선을 긋는다.
//    ② 어림 구간은 점선 대신 옅게(불투명도를 낮춰) 그린다. 「어림」 안내는 지도 위 문구로 남는다.
//    ③ 줌이 멀면 작은 꺾임을 덜어 내고, 줌이 바뀌면 다시 셈한다.
//    ④ 경사·그늘 겹(굵기를 직접 준 선)은 건드리지 않는다.
//    웹 지도(RouteMap.tsx)와 앱 지도(WebView 안의 HTML) 둘 다 본다.
import { Platform } from 'react-native';
import { act, create } from 'react-test-renderer';

import { buildKakaoMapHtml } from '@/map/kakaoMapHtml';
import { color } from '@/design/tokens';
// eslint-disable-next-line @typescript-eslint/no-require-imports
const { RouteMap } = require('../RouteMap.tsx') as typeof import('../RouteMap');

jest.mock('react-native-webview', () => ({ WebView: () => null }));
jest.mock('@/i18n', () => ({ useI18n: () => ({ tx: (ko: string) => ko }) }));

const fakeElement = () => ({ style: {}, setAttribute() {}, appendChild() {}, textContent: '', onclick: null });
const fakeDocument = { createElement: fakeElement, addEventListener() {}, removeEventListener() {}, getElementById: () => ({}), head: { appendChild() {} } };
(globalThis as unknown as { document: unknown }).document = fakeDocument;

type LineOptions = { path: Array<{ lat: number; lng: number }>; strokeWeight: number; strokeColor: string; strokeOpacity: number; strokeStyle: string };
const lines: Array<{ options: LineOptions; paths: number[] }> = [];
const zoomListeners: Array<() => void> = [];
// 도/픽셀 — 줌을 흉내 낸다. 0.0005 ≈ 45m/픽셀(멀리) · 0.000005 ≈ 0.45m/픽셀(가까이)
let degreesPerPixel = 0.000005;

class LatLng { constructor(public lat: number, public lng: number) {} getLat() { return this.lat; } getLng() { return this.lng; } }
class Point { constructor(public x: number, public y: number) {} }
class LatLngBounds { extend() {} }
class CustomOverlay { setMap() {} setPosition() {} }
class Polyline {
  record: { options: LineOptions; paths: number[] };
  constructor(options: LineOptions) { this.record = { options, paths: [options.path.length] }; lines.push(this.record); }
  setMap() {}
  setPath(path: unknown[]) { this.record.paths.push(path.length); }
}
class FakeMap {
  relayout() {}
  setBounds() {}
  setCenter() {}
  setLevel() {}
  panTo() {}
  getProjection() {
    return {
      containerPointFromCoords: (c: LatLng) => new Point(c.lng, c.lat),
      coordsFromContainerPoint: (p: Point) => new LatLng(35.15 + p.y * degreesPerPixel, 129.05 + p.x * degreesPerPixel),
    };
  }
}
const fakeMaps = {
  load: (cb: () => void) => cb(), LatLng, LatLngBounds, CustomOverlay, Polyline, Map: FakeMap, Point,
  event: { addListener: (_target: unknown, type: string, handler: () => void) => { if (type === 'zoom_changed') zoomListeners.push(handler); } },
};

const stops = [
  { id: 'a', number: 1, name: '서면', latitude: 35.157, longitude: 129.059 },
  { id: 'b', number: 2, name: '해운대', latitude: 35.158, longitude: 129.160 },
];
// 자동차 길처럼 촘촘하게 흔들리는 모양 — 41점
const wiggly = Array.from({ length: 41 }, (_, i) => ({ latitude: 35.15 + (i % 2) * 0.0001, longitude: 129.05 + i * 0.0005 }));
const estimatedRoute = { id: 'r1', color: '#3366ff', stops, path: wiggly, estimated: true };
const realRoute = { id: 'r2', color: '#3366ff', stops, path: wiggly, estimated: false };
const slopeLayer = { id: 'slope', color: '#ff0000', stops, path: wiggly, estimated: false, weight: 4, opacity: 0.7 };

beforeEach(() => { lines.length = 0; zoomListeners.length = 0; degreesPerPixel = 0.000005; });

const routeLines = () => lines.filter((line) => line.options.strokeWeight !== 4);

describe('웹 지도 — 경로 선 모양', () => {
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
  const mount = (routes: unknown[]) => {
    let tree!: ReturnType<typeof create>;
    act(() => { tree = create(<RouteMap stops={stops} routes={routes as never} selectedId="a" onSelect={() => {}} />, { createNodeMock: () => fakeDocument.createElement() }); });
    return tree;
  };

  it('🔴 어림 구간도 점선이 아니다 — 흰 테두리 선 위에 옅은 실선', () => {
    mount([estimatedRoute]);
    const [casing, line] = routeLines();
    expect(routeLines()).toHaveLength(2);
    expect(casing.options).toMatchObject({ strokeColor: color.surface.card, strokeStyle: 'solid' });
    expect(casing.options.strokeWeight).toBeGreaterThan(line.options.strokeWeight);
    expect(line.options).toMatchObject({ strokeColor: '#3366ff', strokeStyle: 'solid' });
    expect(lines.some((l) => l.options.strokeStyle === 'shortdash')).toBe(false);
  });

  it('🔴 어림은 실제 길보다 옅다 — 점선 대신 불투명도로 가른다', () => {
    mount([estimatedRoute, realRoute]);
    const [, estimated, , real] = routeLines();
    expect(estimated.options.strokeOpacity).toBeLessThan(real.options.strokeOpacity);
  });

  it('🔴 줌이 멀면 작은 꺾임을 덜어 내고, 줌이 바뀌면 다시 셈한다', () => {
    degreesPerPixel = 0.0005;
    mount([estimatedRoute]);
    const [, line] = routeLines();
    expect(line.paths[0]).toBeLessThan(wiggly.length);
    degreesPerPixel = 0.000005;
    act(() => { zoomListeners.forEach((listener) => listener()); });
    expect(line.paths[line.paths.length - 1]).toBe(wiggly.length);
  });

  it('경사·그늘 겹은 그대로 — 테두리도 단순화도 없다', () => {
    degreesPerPixel = 0.0005;
    mount([slopeLayer]);
    expect(lines).toHaveLength(1);
    expect(lines[0].options).toMatchObject({ strokeWeight: 4, strokeOpacity: 0.7, strokeColor: '#ff0000' });
    expect(lines[0].paths[0]).toBe(wiggly.length);
  });
});

describe('앱 지도(WebView 안의 HTML) — 경로 선 모양', () => {
  const boot = () => {
    const html = buildKakaoMapHtml('test-key');
    const code = html.split('<script>')[1].split('</script>')[0];
    const win: Record<string, unknown> = { addEventListener() {}, kakao: { maps: fakeMaps } };
    new Function('window', 'document', code)(win, fakeDocument);
    return win as unknown as Record<string, (data: unknown) => void>;
  };
  const data = (routes: unknown[]) => ({ stops, points: [], routes, selectedId: 'a', currentLocation: null, fitPadding: [60, 60, 60, 60], colors: { navy: '#123', selected: '#456', canvas: '#fff', casing: color.surface.card }, focus: false, shiftY: 0 });

  it('🔴 어림 구간도 흰 테두리 위의 옅은 실선 — 점선이 없다', () => {
    boot().__renderKakaoMap(data([estimatedRoute, realRoute]));
    const [casing, estimated, , real] = routeLines();
    expect(routeLines()).toHaveLength(4);
    expect(casing.options).toMatchObject({ strokeColor: color.surface.card, strokeStyle: 'solid' });
    expect(estimated.options.strokeStyle).toBe('solid');
    expect(estimated.options.strokeOpacity).toBeLessThan(real.options.strokeOpacity);
    expect(lines.some((l) => l.options.strokeStyle === 'shortdash')).toBe(false);
  });

  it('🔴 줌이 멀면 작은 꺾임을 덜어 내고, 줌이 바뀌면 다시 셈한다', () => {
    degreesPerPixel = 0.0005;
    boot().__renderKakaoMap(data([estimatedRoute]));
    const [, line] = routeLines();
    expect(line.paths[0]).toBeLessThan(wiggly.length);
    degreesPerPixel = 0.000005;
    zoomListeners.forEach((listener) => listener());
    expect(line.paths[line.paths.length - 1]).toBe(wiggly.length);
  });

  it('경사·그늘 겹은 그대로', () => {
    degreesPerPixel = 0.0005;
    boot().__renderKakaoMap(data([slopeLayer]));
    expect(lines).toHaveLength(1);
    expect(lines[0].options).toMatchObject({ strokeWeight: 4, strokeOpacity: 0.7 });
    expect(lines[0].paths[0]).toBe(wiggly.length);
  });
});
