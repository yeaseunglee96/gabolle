// 여행 지도 — 장소를 고를 때 전체 보기로 순간 이동했다가 날아오지 않는다 — S15P21E201-1654.
//
// 🔴 이 시험이 지키는 것(사용자가 폰에서 짚은 것 — 「고르면 늘 왼쪽에서 날아온다」):
//    ① 고른 곳만 바뀌면 전체 맞추기(setBounds — 순간 이동)를 하지 않는다. 지금 화면·줌에서 panTo 만 한다.
//    ② 마커·선을 다시 만들지 않는다 — 고른 마커와 풀린 마커의 모양만 바꾼다.
//    ③ 현재 위치만 바뀌거나, 누를 때 부르는 함수가 새것이어도 다시 맞추지 않는다.
//    ④ 장소·경로 목록이 바뀌면 지금처럼 다시 그리고 다시 맞춘다.
//    웹 지도(RouteMap.tsx)와 앱 지도(WebView 안의 HTML) 둘 다 본다. 실제 SDK 는 없으니 가짜 kakao 로 부른 것만 센다.
import { Platform } from 'react-native';
import { act, create } from 'react-test-renderer';

import { buildKakaoMapHtml } from '@/map/kakaoMapHtml';
// 🔴 '@/map/RouteMap' 은 jest(플랫폼 ios)에서 RouteMap.native.tsx 로 풀린다 — 웹 파일을 이름 그대로 집는다.
// eslint-disable-next-line @typescript-eslint/no-require-imports
const { RouteMap } = require('../RouteMap.tsx') as typeof import('../RouteMap');

jest.mock('react-native-webview', () => ({ WebView: () => null }));
jest.mock('@/i18n', () => ({ useI18n: () => ({ tx: (ko: string) => ko }) }));

type FakeElement = { style: Record<string, string>; attrs: Record<string, string>; setAttribute: (k: string, v: string) => void; appendChild: () => void; textContent: string; onclick: null | (() => void) };
const created: FakeElement[] = [];
const fakeElement = (): FakeElement => {
  const el: FakeElement = { style: {}, attrs: {}, setAttribute(k, v) { el.attrs[k] = v; }, appendChild() {}, textContent: '', onclick: null };
  created.push(el);
  return el;
};
const fakeDocument = { createElement: fakeElement, addEventListener() {}, removeEventListener() {}, getElementById: () => ({}), head: { appendChild() {} } };
(globalThis as unknown as { document: unknown }).document = fakeDocument;

const calls = { level: 8, setLevel: [] as number[], setBounds: 0, panTo: [] as Array<{ lat: number; lng: number }>, overlays: 0, lines: 0 };
class LatLng { constructor(public lat: number, public lng: number) {} }
class Point { constructor(public x: number, public y: number) {} }
class LatLngBounds { extend() {} }
class CustomOverlay { constructor() { calls.overlays += 1; } setMap() {} setPosition() {} }
class Polyline { constructor() { calls.lines += 1; } setMap() {} }
class FakeMap {
  relayout() {}
  setBounds() { calls.setBounds += 1; }
  setCenter() {}
  getLevel() { return calls.level; }
  setLevel(level: number) { calls.level = level; calls.setLevel.push(level); }
  panTo(target: LatLng) { calls.panTo.push({ lat: target.lat, lng: target.lng }); }
  getProjection() { return { containerPointFromCoords: (c: LatLng) => new Point(c.lng, c.lat), coordsFromContainerPoint: (p: Point) => new LatLng(p.y, p.x) }; }
}
const fakeMaps = { load: (cb: () => void) => cb(), LatLng, LatLngBounds, CustomOverlay, Polyline, Map: FakeMap, Point };

const stops = [
  { id: 'a', number: 1, name: '서면 숙소', latitude: 35.157, longitude: 129.059 },
  { id: 'b', number: 2, name: '광안리', latitude: 35.153, longitude: 129.118 },
  { id: 'c', number: 3, name: '해운대', latitude: 35.158, longitude: 129.160 },
];
const routes = [{ id: 'r', color: '#000', stops }];
const markerFor = (name: string) => created.filter((el) => (el.attrs['aria-label'] ?? '').includes(name)).pop();

beforeEach(() => { calls.level = 8; calls.setLevel = []; created.length = 0; calls.setBounds = 0; calls.panTo = []; calls.overlays = 0; calls.lines = 0; });

describe('웹 지도 — 고른 곳이 바뀔 때', () => {
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

  const mount = (selectedId: string, extra: Record<string, unknown> = {}) => {
    let tree!: ReturnType<typeof create>;
    const onSelect = () => {};
    act(() => { tree = create(<RouteMap stops={stops} routes={routes} selectedId={selectedId} onSelect={onSelect} focusSelected {...extra} />, { createNodeMock: () => fakeDocument.createElement() }); });
    return { tree, onSelect };
  };

  it('🔴 전체 맞추기를 다시 하지 않고, 마커·선도 다시 만들지 않고, 지금 화면에서 panTo 만 한다', () => {
    const { tree, onSelect } = mount('a');
    const before = { ...calls, panTo: calls.panTo.length };
    expect(before.setBounds).toBeGreaterThan(0);
    act(() => { tree.update(<RouteMap stops={stops} routes={routes} selectedId="c" onSelect={onSelect} focusSelected />); });
    expect(calls.setBounds).toBe(before.setBounds);
    expect(calls.overlays).toBe(before.overlays);
    expect(calls.lines).toBe(before.lines);
    expect(calls.panTo[calls.panTo.length - 1]).toEqual({ lat: 35.158, lng: 129.160 });
  });

  it('🔴 위가 가려졌으면 고른 곳을 그만큼 덜 내려 민다 — 위에 뜬 색 범례 밑에 깔리지 않게(S15P21E201-1896)', () => {
    // 이 시험의 가짜 지도는 좌표를 화면 위치처럼 다룬다(y = 위도) — 민 만큼(위도 차이)이 곧 옮긴 높이다.
    mount('a', { bottomInset: 200 });
    const open = calls.panTo[calls.panTo.length - 1];
    calls.panTo = [];
    mount('a', { bottomInset: 200, topInset: 100 });
    const covered = calls.panTo[calls.panTo.length - 1];
    expect(open.lat - 35.157).toBeCloseTo(100); // 아래 200 이 가려짐 → 200 / 2
    expect(covered.lat - 35.157).toBeCloseTo(50); // 위 100 도 가려짐 → (200 − 100) / 2
  });

  it('🔴 고른 마커만 커진다 — 풀린 마커는 원래 크기로', () => {
    const { tree, onSelect } = mount('a');
    act(() => { tree.update(<RouteMap stops={stops} routes={routes} selectedId="c" onSelect={onSelect} focusSelected />); });
    expect(markerFor('해운대')?.style.transform).toBe('scale(1.25)');
    expect(markerFor('서면 숙소')?.style.transform).toBe('scale(1)');
  });

  it('🔴 누를 때 부르는 함수가 새것이어도, 현재 위치만 움직여도 다시 맞추지 않는다', () => {
    const { tree } = mount('a', { currentLocation: { latitude: 35.15, longitude: 129.1 } });
    const setBoundsBefore = calls.setBounds;
    const linesBefore = calls.lines;
    act(() => { tree.update(<RouteMap stops={stops} routes={routes} selectedId="a" onSelect={() => {}} focusSelected currentLocation={{ latitude: 35.151, longitude: 129.101 }} />); });
    expect(calls.setBounds).toBe(setBoundsBefore);
    expect(calls.lines).toBe(linesBefore);
  });

  it('장소 목록이 바뀌면 다시 그리고 다시 맞춘다', () => {
    const { tree, onSelect } = mount('a');
    const setBoundsBefore = calls.setBounds;
    const more = [...stops, { id: 'd', number: 4, name: '송정', latitude: 35.178, longitude: 129.199 }];
    act(() => { tree.update(<RouteMap stops={more} routes={routes} selectedId="a" onSelect={onSelect} focusSelected />); });
    expect(calls.setBounds).toBeGreaterThan(setBoundsBefore);
  });
});

describe('앱 지도(WebView 안의 HTML) — 고른 곳이 바뀔 때', () => {
  // HTML 안의 스크립트를 가짜 window·document 로 실제로 돌린다.
  const boot = () => {
    const html = buildKakaoMapHtml('test-key');
    const code = html.split('<script>')[1].split('</script>')[0];
    const win: Record<string, unknown> = { addEventListener() {}, kakao: { maps: fakeMaps } };
    new Function('window', 'document', code)(win, fakeDocument);
    return win as unknown as Record<string, (data: unknown) => void>;
  };
  const colors = { navy: '#123', selected: '#456', canvas: '#fff' };
  const data = (selectedId: string) => ({ stops, points: [], routes, selectedId, currentLocation: null, fitPadding: [60, 60, 60, 60], colors, focus: true, shiftY: 0 });

  it('🔴 처음 그릴 때는 맞추고 고른 곳으로 옮긴다', () => {
    const win = boot();
    win.__renderKakaoMap(data('a'));
    expect(calls.setBounds).toBe(1);
    expect(calls.panTo[calls.panTo.length - 1]).toEqual({ lat: 35.157, lng: 129.059 });
  });

  it('🔴 고른 곳만 바뀌면 다시 맞추지도 다시 그리지도 않고 panTo 만 한다', () => {
    const win = boot();
    win.__renderKakaoMap(data('a'));
    const before = { setBounds: calls.setBounds, overlays: calls.overlays, lines: calls.lines };
    win.__selectKakaoMap({ selectedId: 'c', focus: true, shiftY: 0 });
    expect(calls.setBounds).toBe(before.setBounds);
    expect(calls.overlays).toBe(before.overlays);
    expect(calls.lines).toBe(before.lines);
    expect(calls.panTo[calls.panTo.length - 1]).toEqual({ lat: 35.158, lng: 129.160 });
    expect(markerFor('해운대')?.style.transform).toBe('scale(1.25)');
    expect(markerFor('서면 숙소')?.style.transform).toBe('scale(1)');
  });

  it('🔴 멀리서 보고 있으면 고른 곳이 보이게 확대한다(S15P21E201-1903) — 이미 가까우면 줌을 건드리지 않는다', () => {
    const win = boot();
    win.__renderKakaoMap(data('a'));
    calls.level = 8; calls.setLevel = [];
    win.__selectKakaoMap({ selectedId: 'c', focus: true, shiftY: 0 });
    expect(calls.setLevel).toEqual([4]);
    calls.level = 3; calls.setLevel = [];
    win.__selectKakaoMap({ selectedId: 'b', focus: true, shiftY: 0 });
    expect(calls.setLevel).toEqual([]);
  });

  it('🔴 현재 위치만 움직이면 점만 옮긴다 — 다시 맞추지 않는다', () => {
    const win = boot();
    win.__renderKakaoMap(data('a'));
    const setBoundsBefore = calls.setBounds;
    win.__moveKakaoLocation({ latitude: 35.15, longitude: 129.1 });
    win.__moveKakaoLocation({ latitude: 35.151, longitude: 129.101 });
    expect(calls.setBounds).toBe(setBoundsBefore);
  });
});
