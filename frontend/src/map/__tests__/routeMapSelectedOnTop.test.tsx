// 고른 점은 겹친 점들 «위에» 온다 — S15P21E201-2052 (근처 편의시설 지도).
//
// 🔴 약국·화장실은 결과가 스무 곳이라 점이 한 덩어리로 몰린다. 마커 요소의 z-index 는 카카오가 씌운 «오버레이 상자 안»에서만 통해서,
//    고른 점(1번)이 나중에 그려진 점들(15·17·19·20번) 뒤에 가려 «카드를 누르면 그 점이 강조된다»가 눈에 안 보였다(배포 전 캡처).
//    그래서 오버레이 자체의 z-index 를 올린다 — 번호 장소(2)·점 표시(1)보다 위인 3.
// 웹 지도(RouteMap.tsx)와 앱 지도(WebView 안 스크립트) 둘 다 본다. 실제 SDK 는 없으니 가짜 kakao 로 부른 것만 센다.
import { Platform } from 'react-native';
import { act, create } from 'react-test-renderer';

import { buildKakaoMapHtml } from '@/map/kakaoMapHtml';
// 🔴 '@/map/RouteMap' 은 jest(플랫폼 ios)에서 RouteMap.native.tsx 로 풀린다 — 웹 파일을 이름 그대로 집는다.
// eslint-disable-next-line @typescript-eslint/no-require-imports
const { RouteMap } = require('../RouteMap.tsx') as typeof import('../RouteMap');

jest.mock('react-native-webview', () => ({ WebView: () => null }));
jest.mock('@/i18n', () => ({ useI18n: () => ({ tx: (ko: string) => ko }) }));

type FakeElement = { style: Record<string, string>; attrs: Record<string, string>; setAttribute: (k: string, v: string) => void; appendChild: () => void; textContent: string; onclick: null | (() => void); clientHeight: number; clientWidth: number };
const fakeElement = (): FakeElement => {
  const el: FakeElement = { style: {}, attrs: {}, setAttribute(k, v) { el.attrs[k] = v; }, appendChild() {}, textContent: '', onclick: null, clientHeight: 220, clientWidth: 340 };
  return el;
};
const fakeDocument = { createElement: fakeElement, addEventListener() {}, removeEventListener() {}, getElementById: () => fakeElement(), head: { appendChild() {} } };
(globalThis as unknown as { document: unknown }).document = fakeDocument;

// 오버레이마다 «지금 z-index»를 적는다 — 만들 때 준 값, 그 뒤 setZIndex 로 바뀐 값. 어느 마커의 오버레이인지는 안의 요소가 알려 준다(번호 글자).
const overlays: Array<{ label: string; z: number; setZIndexCalls: number[] }> = [];
let withoutSetZIndex = false;
class LatLng { constructor(public lat: number, public lng: number) {} }
class Point { constructor(public x: number, public y: number) {} }
class LatLngBounds { extend() {} }
class CustomOverlay {
  record: { label: string; z: number; setZIndexCalls: number[] };
  constructor(options: { content: FakeElement; zIndex: number }) {
    this.record = { label: options.content.textContent, z: options.zIndex, setZIndexCalls: [] };
    overlays.push(this.record);
    if (!withoutSetZIndex) (this as unknown as { setZIndex: (z: number) => void }).setZIndex = (z: number) => { this.record.z = z; this.record.setZIndexCalls.push(z); };
  }
  setMap() {}
  setPosition() {}
}
class Polyline { setMap() {} }
class FakeMap {
  relayout() {}
  setBounds() {}
  setCenter() {}
  getLevel() { return 8; }
  setLevel() {}
  panTo() {}
  getProjection() { return { containerPointFromCoords: (c: LatLng) => new Point(c.lng, c.lat), coordsFromContainerPoint: (p: Point) => new LatLng(p.y, p.x) }; }
}
const fakeMaps = { load: (cb: () => void) => cb(), LatLng, LatLngBounds, CustomOverlay, Polyline, Map: FakeMap, Point };

const NO_ROUTES: never[] = [];
const stops = [
  { id: 'a', number: 1, name: '서면역', latitude: 35.1578, longitude: 129.0597 },
  { id: 'b', number: 2, name: '전포역', latitude: 35.1531, longitude: 129.0621 },
  { id: 'c', number: 3, name: '범내골역', latitude: 35.1431, longitude: 129.0581 },
];
const reference = [{ id: 'reference', label: '기준점', color: '#123456', stops: [{ id: 'reference', number: 0, name: '부산시청', latitude: 35.1796, longitude: 129.0756 }] }];
const zOf = (label: string) => overlays.filter((overlay) => overlay.label === label).pop()?.z;

beforeEach(() => { overlays.length = 0; withoutSetZIndex = false; });

describe('웹 지도 — 고른 점의 오버레이를 위로', () => {
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

  const element = (selectedId: string) => <RouteMap stops={stops} points={reference} routes={NO_ROUTES} selectedId={selectedId} onSelect={() => {}} focusSelected height={220} />;

  it('🔴 처음부터 고른 점은 오버레이가 3 으로 올라가 있고, 나머지는 번호 장소 2 · 점 표시 1 그대로다', () => {
    act(() => { create(element('a'), { createNodeMock: () => fakeElement() }); });
    expect(zOf('1')).toBe(3);
    expect(zOf('2')).toBe(2);
    expect(zOf('3')).toBe(2);
    expect(zOf('기준점')).toBe(1);
  });

  it('🔴 고른 곳이 바뀌면 새로 고른 점이 3 으로 올라가고, 풀린 점은 원래 순서(2)로 돌아온다 — 다시 그리지 않는다', () => {
    let tree!: ReturnType<typeof create>;
    act(() => { tree = create(element('a'), { createNodeMock: () => fakeElement() }); });
    const created = overlays.length;
    act(() => { tree.update(element('c')); });
    expect(overlays.length).toBe(created); // 오버레이를 새로 만들지 않는다
    expect(zOf('3')).toBe(3);
    expect(zOf('1')).toBe(2);
    expect(zOf('2')).toBe(2);
  });

  it('고른 것이 점 표시(기준점)여도 위로 오고, 풀리면 점 표시 순서(1)로 돌아온다', () => {
    let tree!: ReturnType<typeof create>;
    act(() => { tree = create(element('reference'), { createNodeMock: () => fakeElement() }); });
    expect(zOf('기준점')).toBe(3);
    act(() => { tree.update(element('a')); });
    expect(zOf('기준점')).toBe(1);
    expect(zOf('1')).toBe(3);
  });

  it('setZIndex 가 없는 환경에서도 던지지 않는다 — 요소의 z-index 만으로 전처럼 그린다', () => {
    withoutSetZIndex = true;
    let tree!: ReturnType<typeof create>;
    expect(() => { act(() => { tree = create(element('a'), { createNodeMock: () => fakeElement() }); }); }).not.toThrow();
    expect(() => { act(() => { tree.update(element('b')); }); }).not.toThrow();
  });
});

describe('앱 지도 스크립트(WebView 안의 HTML)', () => {
  const boot = () => {
    const code = buildKakaoMapHtml('test-key').split('<script>')[1].split('</script>')[0];
    const win: Record<string, unknown> = { addEventListener() {}, kakao: { maps: fakeMaps } };
    new Function('window', 'document', code)(win, fakeDocument);
    return win as unknown as Record<string, (data: unknown) => void>;
  };
  const colors = { navy: '#123', selected: '#456', canvas: '#fff', casing: '#fff' };
  const data = (selectedId: string) => ({ stops, points: reference, routes: [], selectedId, currentLocation: null, fitPadding: [60, 60, 60, 60], colors, focus: true, shiftY: 0 });

  it('🔴 처음부터 고른 점은 3, 나머지는 2 · 점 표시 1', () => {
    boot().__renderKakaoMap(data('a'));
    expect(zOf('1')).toBe(3);
    expect(zOf('2')).toBe(2);
    expect(zOf('기준점')).toBe(1);
  });

  it('🔴 고른 곳이 바뀌면 새로 고른 점이 3 으로, 풀린 점은 원래 순서로', () => {
    const win = boot();
    win.__renderKakaoMap(data('a'));
    const created = overlays.length;
    win.__selectKakaoMap({ selectedId: 'c', focus: true, shiftY: 0 });
    expect(overlays.length).toBe(created);
    expect(zOf('3')).toBe(3);
    expect(zOf('1')).toBe(2);
  });

  it('setZIndex 가 없는 환경에서도 던지지 않는다', () => {
    withoutSetZIndex = true;
    const win = boot();
    expect(() => win.__renderKakaoMap(data('a'))).not.toThrow();
    expect(() => win.__selectKakaoMap({ selectedId: 'b', focus: true, shiftY: 0 })).not.toThrow();
  });
});
