// 지도에 그릴 장소가 «아직 없을» 때 — S15P21E201-1889 (가까운 도움 화면의 회색 지도 · 탭을 바꾸면 화면이 무너지던 것).
//
// 🔴 운영 실측(2026-10-15, https://gabolle.kr/nearby-help — Playwright 로 재현). 이 화면은 서버 응답을 기다리는 동안 «빈 목록»으로 지도를 먼저 올린다.
//    ① 목록 응답이 카카오 SDK 보다 늦으면, 지도 칸이 목록이 뜬 뒤에도 영영 회색 빈칸이었다.
//       빈 목록으로 그리다 stops[0].latitude 에서 던졌고, 그 콜백이 카카오 SDK 의 load 콜백 줄 안에서 던지면 SDK 가 뒤따르는 콜백을
//       다시는 안 부른다 → 목록이 와서 다시 그리라고 해도 그 콜백이 안 불린다. 화면에는 아무 안내도 없었다.
//    ② SDK 가 이미 떠 있는 채로 목록이 비면(탭을 바꿀 때) 콜백이 곧바로 불려 effect 안에서 던졌고, 화면 전체가
//       「화면을 불러오지 못했어요」 오류 화면으로 무너졌다.
//    실제 SDK 는 없으니 가짜 window.kakao 로 두 순서를 흉내 낸다 — 「한 콜백이 던지면 그 뒤 콜백은 영영 안 불린다」까지 운영과 같게.
import { Platform } from 'react-native';
import { act, create, type ReactTestRenderer } from 'react-test-renderer';

import type { MapStop } from '@/map/types';
// 🔴 '@/map/RouteMap' 은 jest(플랫폼 ios)에서 RouteMap.native.tsx 로 풀린다 — 웹 파일을 이름 그대로 집는다.
// eslint-disable-next-line @typescript-eslint/no-require-imports
const { RouteMap, MAP_DRAW_TIMEOUT_MS } = require('../RouteMap.tsx') as typeof import('../RouteMap');

jest.mock('react-native-webview', () => ({ WebView: () => null }));
jest.mock('@/i18n', () => ({ useI18n: () => ({ tx: (ko: string) => ko }) }));

type FakeElement = { style: Record<string, string>; attrs: Record<string, string>; setAttribute: (k: string, v: string) => void; appendChild: () => void; textContent: string; onclick: null | (() => void) };
const created: FakeElement[] = [];
const fakeElement = (): FakeElement => {
  const el: FakeElement = { style: {}, attrs: {}, setAttribute(k, v) { el.attrs[k] = v; }, appendChild() {}, textContent: '', onclick: null };
  created.push(el);
  return el;
};
const fakeDocument = { createElement: fakeElement, addEventListener() {}, removeEventListener() {}, getElementById: () => null, head: { appendChild() {} } };
(globalThis as unknown as { document: unknown }).document = fakeDocument;

const calls = { maps: 0, overlays: 0, removed: 0, setBounds: 0, centers: [] as Array<{ lat: number; lng: number }> };
let failBounds: string | null = null;
class LatLng { constructor(public lat: number, public lng: number) {} }
class Point { constructor(public x: number, public y: number) {} }
class LatLngBounds { extend() { if (failBounds) throw new Error(failBounds); } }
class CustomOverlay { constructor() { calls.overlays += 1; } setMap(map: unknown) { if (map === null) calls.removed += 1; } setPosition() {} }
class Polyline { setMap() {} }
class FakeMap {
  constructor(_host: unknown, options: { center: LatLng }) { calls.maps += 1; calls.centers.push({ lat: options.center.lat, lng: options.center.lng }); }
  relayout() {}
  setBounds() { calls.setBounds += 1; }
  setCenter() {}
  getLevel() { return 8; }
  setLevel() {}
  panTo() {}
  getProjection() { return { containerPointFromCoords: (c: LatLng) => new Point(c.lng, c.lat), coordsFromContainerPoint: (p: Point) => new LatLng(p.y, p.x) }; }
}

// ── 가짜 카카오 SDK ───────────────────────────────────────────────────────
// ready — 시작 파일·둘째 파일이 다 떴나. 아직이면 load 콜백은 줄을 서고, 다 뜨는 순간 차례로 불린다. 떠 있으면 곧바로(동기) 불린다.
// broken — 🔴 운영 실측: 콜백 하나가 던지면 SDK 는 그 뒤의 load 콜백을 다시는 안 부른다.
const sdk = { ready: true, broken: false, queue: [] as Array<() => void> };
function runCallback(callback: () => void) {
  try { callback(); } catch (error) { sdk.broken = true; throw error; }
}
const fakeMaps = {
  load: (callback: () => void) => {
    if (sdk.broken) return;
    if (sdk.ready) { runCallback(callback); return; }
    sdk.queue.push(callback);
  },
  LatLng, LatLngBounds, CustomOverlay, Polyline, Map: FakeMap, Point,
};
/** SDK 가 다 뜬 순간 — 줄 선 콜백을 차례로 부른다. 하나가 던지면 나머지는 안 불린다(던진 것은 호출한 쪽으로 나간다). */
function finishLoading() {
  sdk.ready = true;
  while (sdk.queue.length && !sdk.broken) runCallback(sdk.queue.shift()!);
}

const NO_ROUTES: never[] = [];
const stops: MapStop[] = [
  { id: 'a', number: 1, name: '서면약국', latitude: 35.157, longitude: 129.059 },
  { id: 'b', number: 2, name: '다솜약국', latitude: 35.153, longitude: 129.062 },
  { id: 'c', number: 3, name: '모닝약국', latitude: 35.158, longitude: 129.057 },
];
const otherStops: MapStop[] = [
  { id: 'x', number: 1, name: '부산진경찰서', latitude: 35.162, longitude: 129.053 },
  { id: 'y', number: 2, name: '서면파출소', latitude: 35.160, longitude: 129.066 },
];
const FAILURE_TITLE = '지금은 지도를 불러올 수 없어요';
const FILE_FAILURE_TITLE = '지도 파일을 못 받았어요';

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
beforeEach(() => {
  created.length = 0;
  calls.maps = 0; calls.overlays = 0; calls.removed = 0; calls.setBounds = 0; calls.centers = [];
  failBounds = null;
  sdk.ready = true; sdk.broken = false; sdk.queue = [];
});
// 🔴 지도를 끝내 못 그릴 때를 재는 타이머(MAP_DRAW_TIMEOUT_MS)는 지도 칸이 사라질 때 걷힌다 — 시험도 매번 걷어야 Jest 가 제때 끝난다.
const mounted: ReactTestRenderer[] = [];
afterEach(() => {
  act(() => { mounted.splice(0).forEach((tree) => tree.unmount()); });
  jest.useRealTimers();
});

const element = (items: MapStop[], extra: Record<string, unknown> = {}) => (
  <RouteMap stops={items} routes={NO_ROUTES} selectedId="" onSelect={() => {}} focusSelected height={220} {...extra} />
);
const mount = (items: MapStop[], extra: Record<string, unknown> = {}) => {
  let tree!: ReactTestRenderer;
  act(() => { tree = create(element(items, extra), { createNodeMock: () => fakeElement() }); });
  mounted.push(tree);
  return tree;
};
const update = (tree: ReactTestRenderer, items: MapStop[], extra: Record<string, unknown> = {}) => act(() => { tree.update(element(items, extra)); });
const text = (tree: ReactTestRenderer) => JSON.stringify(tree.toJSON());
const markerFor = (name: string) => created.filter((el) => (el.attrs['aria-label'] ?? '').includes(name)).pop();

describe('웹 지도 — 그릴 장소가 아직 없을 때(빈 목록)', () => {
  it('🔴 빈 목록으로 그려도 던지지 않고, 지도도 안 만든다 — 던지면 SDK 가 죽거나 화면이 무너진다', () => {
    let tree!: ReactTestRenderer;
    expect(() => { tree = mount([]); }).not.toThrow();
    expect(calls.maps).toBe(0);
    expect(text(tree)).not.toContain(FAILURE_TITLE);
    expect(sdk.broken).toBe(false);
  });

  it('🔴 빈 목록으로 시작해 나중에 채워지면 그때 지도가 그려지고 번호 점이 생긴다(SDK 가 이미 떠 있을 때)', () => {
    const tree = mount([]);
    update(tree, stops);
    expect(calls.maps).toBe(1);
    expect(markerFor('서면약국')?.textContent).toBe('1');
    expect(markerFor('모닝약국')?.textContent).toBe('3');
    expect(calls.setBounds).toBeGreaterThan(0);
  });

  it('🔴 SDK 가 목록보다 «먼저» 뜨는 순서 — 빈 목록 때문에 SDK 가 죽지 않아, 나중에 목록이 오면 그려진다(운영의 회색 빈칸)', () => {
    sdk.ready = false; // 시작 파일은 왔지만 둘째 파일을 아직 받는 중
    const tree = mount([]);
    // 그릴 것이 없을 때 SDK 에 맡기는 것은 «미리 받아 두라»는 빈 콜백 하나뿐이다 — 지도를 만드는 콜백은 맡기지 않는다
    expect(sdk.queue).toHaveLength(1);
    update(tree, stops);
    expect(sdk.queue).toHaveLength(2);
    // SDK 가 다 뜬다 — 콜백이 던지면 여기서 던진다
    expect(() => act(() => { finishLoading(); })).not.toThrow();
    expect(sdk.broken).toBe(false);
    expect(calls.maps).toBe(1);
    expect(markerFor('다솜약국')?.textContent).toBe('2');
  });

  it('목록을 기다리는 동안 SDK 의 둘째 파일을 미리 받아 둔다 — 목록이 온 뒤에야 받기 시작하면 지도가 그만큼 늦게 뜬다', () => {
    sdk.ready = false;
    mount([]);
    expect(sdk.queue).toHaveLength(1); // load 를 불러야 둘째 파일을 받기 시작한다(autoload=false)
    act(() => { finishLoading(); });
    expect(calls.maps).toBe(0); // 받아 두기만 하고 지도는 안 만든다
    expect(sdk.broken).toBe(false);
  });

  it('🔴 그려진 뒤 목록이 비어도(탭을 바꿀 때) 던지지 않고 지난 점을 걷는다 — 지난 목록의 점이 남으면 없는 곳을 가리킨다', () => {
    const tree = mount(stops);
    expect(calls.overlays).toBe(3);
    const removedBefore = calls.removed;
    expect(() => update(tree, [])).not.toThrow();
    expect(calls.removed - removedBefore).toBe(3);
    expect(text(tree)).not.toContain(FAILURE_TITLE);
    expect(sdk.broken).toBe(false);
  });

  it('🔴 비었다가 다른 목록(다른 탭)이 오면 같은 지도를 다시 써서 새 점을 그린다', () => {
    const tree = mount(stops);
    update(tree, []);
    update(tree, otherStops);
    expect(calls.maps).toBe(1); // 지도는 새로 만들지 않는다
    expect(calls.overlays).toBe(3 + 2);
    expect(markerFor('부산진경찰서')?.textContent).toBe('1');
  });

  it('번호 장소가 없고 점 표시만 있는 지도도 던지지 않는다 — 첫 번째 점을 가운데로(앱 지도와 같다)', () => {
    const points = [{ id: 'station', label: '역', color: '#123456', stops: [{ id: 's1', number: 0, name: '서면역', latitude: 35.1578, longitude: 129.0597 }] }];
    let tree!: ReactTestRenderer;
    act(() => { tree = create(<RouteMap stops={[]} points={points} routes={NO_ROUTES} selectedId="" onSelect={() => {}} />, { createNodeMock: () => fakeElement() }); });
    mounted.push(tree);
    expect(calls.maps).toBe(1);
    expect(calls.centers[0]).toEqual({ lat: 35.1578, lng: 129.0597 });
  });
});

describe('웹 지도 — 끝내 못 그릴 때는 회색 빈칸 대신 실패 안내', () => {
  it('🔴 그리다 오류가 나면 화면을 무너뜨리지 않고 실패 안내를 낸다 — SDK 도 안 죽는다', () => {
    failBounds = 'boom';
    let tree!: ReactTestRenderer;
    expect(() => { tree = mount(stops); }).not.toThrow();
    expect(text(tree)).toContain(FAILURE_TITLE);
    expect(text(tree)).toContain('지도를 그리다 오류가 났다: boom');
    expect(sdk.broken).toBe(false);
    // 다음에 목록이 바뀌어 그려지면 안내가 걷힌다
    failBounds = null;
    update(tree, otherStops);
    expect(text(tree)).not.toContain(FAILURE_TITLE);
    expect(calls.maps).toBe(1);
  });

  it(`🔴 그릴 것이 있는데 SDK 가 콜백을 안 부르면 ${MAP_DRAW_TIMEOUT_MS / 1000}초 뒤 「지도 파일을 못 받았어요」 — 회색 빈칸으로 두지 않는다`, () => {
    jest.useFakeTimers();
    sdk.ready = false;
    const tree = mount(stops);
    act(() => { jest.advanceTimersByTime(MAP_DRAW_TIMEOUT_MS - 1); });
    expect(text(tree)).not.toContain(FILE_FAILURE_TITLE);
    act(() => { jest.advanceTimersByTime(1); });
    expect(text(tree)).toContain(FILE_FAILURE_TITLE);
    // 늦게라도 SDK 가 뜨면 지도가 그려지고 안내는 걷힌다
    act(() => { finishLoading(); });
    expect(calls.maps).toBe(1);
    expect(text(tree)).not.toContain(FILE_FAILURE_TITLE);
  });

  it('SDK 가 이미 떠 있어 곧바로 그려지면 타이머를 만들지 않는다 — 다른 지도 화면에 남는 것이 없다', () => {
    jest.useFakeTimers();
    mount(stops);
    expect(calls.maps).toBe(1);
    expect(jest.getTimerCount()).toBe(0);
  });

  it('그릴 것이 없으면 타이머도 없다 — 목록을 기다리는 동안 안내를 띄우지 않는다', () => {
    jest.useFakeTimers();
    sdk.ready = false;
    const tree = mount([]);
    expect(jest.getTimerCount()).toBe(0);
    act(() => { jest.advanceTimersByTime(MAP_DRAW_TIMEOUT_MS * 2); });
    expect(text(tree)).not.toContain(FILE_FAILURE_TITLE);
  });

  it('지도 칸이 사라지면(화면을 떠나면) 타이머도 걷는다', () => {
    jest.useFakeTimers();
    sdk.ready = false;
    const tree = mount(stops);
    expect(jest.getTimerCount()).toBe(1);
    act(() => { tree.unmount(); });
    expect(jest.getTimerCount()).toBe(0);
  });
});
