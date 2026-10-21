// 점 표시(기준 장소)도 맞추는 범위에 넣을 수 있다 — S15P21E201-2052 (근처 편의시설 지도).
//
// 🔴 배포 전 캡처에서, 장소 기준으로 연 화면은 결과가 한 곳뿐이라 지도가 그 한 곳만 가운데에 두고 기준 장소(「기준점」 표시)는
//    지도 오른쪽 아래 모서리에 반쯤 걸렸다. 번호 장소(결과)만 맞추는 것이 기본이라서다(먼 출발지가 장소들을 한 점으로 뭉치던 것, S15P21E201-1903).
//    그래서 켜는 사람만 점 표시까지 맞추는 선택 속성(fitPoints)을 둔다 — 안 켜면 다른 지도 화면은 전과 같다.
// 웹 지도(RouteMap.tsx)와 앱 지도(WebView 안의 HTML 스크립트) 둘 다 본다. 실제 SDK 는 없으니 가짜 kakao 로 부른 것만 센다.
import { Platform } from 'react-native';
import { act, create } from 'react-test-renderer';

import { buildKakaoMapHtml } from '@/map/kakaoMapHtml';
// 🔴 '@/map/RouteMap' 은 jest(플랫폼 ios)에서 RouteMap.native.tsx 로 풀린다 — 웹 파일을 이름 그대로 집는다.
// eslint-disable-next-line @typescript-eslint/no-require-imports
const { RouteMap } = require('../RouteMap.tsx') as typeof import('../RouteMap');

jest.mock('react-native-webview', () => ({ WebView: () => null }));
jest.mock('@/i18n', () => ({ useI18n: () => ({ tx: (ko: string) => ko }) }));

const fakeElement = () => ({ style: {}, setAttribute() {}, appendChild() {}, textContent: '', onclick: null, clientHeight: 220, clientWidth: 340 });
const fakeDocument = { createElement: fakeElement, addEventListener() {}, removeEventListener() {}, getElementById: () => fakeElement(), head: { appendChild() {} } };
(globalThis as unknown as { document: unknown }).document = fakeDocument;

const calls = { extended: [] as Array<{ lat: number; lng: number }>, setBounds: 0, setCenter: [] as Array<{ lat: number; lng: number }>, setLevel: [] as number[] };
class LatLng { constructor(public lat: number, public lng: number) {} }
class Point { constructor(public x: number, public y: number) {} }
class LatLngBounds { extend(position: LatLng) { calls.extended.push({ lat: position.lat, lng: position.lng }); } }
class CustomOverlay { setMap() {} setPosition() {} }
class Polyline { setMap() {} }
class FakeMap {
  relayout() {}
  setBounds() { calls.setBounds += 1; }
  setCenter(target: LatLng) { calls.setCenter.push({ lat: target.lat, lng: target.lng }); }
  getLevel() { return 8; }
  setLevel(level: number) { calls.setLevel.push(level); }
  panTo() {}
  getProjection() { return { containerPointFromCoords: (c: LatLng) => new Point(c.lng, c.lat), coordsFromContainerPoint: (p: Point) => new LatLng(p.y, p.x) }; }
}
const fakeMaps = { load: (cb: () => void) => cb(), LatLng, LatLngBounds, CustomOverlay, Polyline, Map: FakeMap, Point };

const NO_ROUTES: never[] = [];
const result = [{ id: 'p-1', number: 1, name: '해맞이 공영주차장', latitude: 35.1633, longitude: 129.1614 }];
const reference = [{ id: 'reference', label: '기준점', color: '#123456', stops: [{ id: 'reference', number: 0, name: '해운대해수욕장', latitude: 35.1587, longitude: 129.1604 }] }];

beforeEach(() => { calls.extended = []; calls.setBounds = 0; calls.setCenter = []; calls.setLevel = []; });

describe('웹 지도 — 점 표시(기준점)를 맞추는 범위에 넣기', () => {
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

  const mount = (items = result, extra: Record<string, unknown> = {}) => {
    let tree!: ReturnType<typeof create>;
    act(() => { tree = create(<RouteMap stops={items} points={reference} routes={NO_ROUTES} selectedId="p-1" onSelect={() => {}} height={220} {...extra} />, { createNodeMock: () => fakeElement() }); });
    return tree;
  };

  it('🔴 기본은 번호 장소만 맞춘다 — 결과 한 곳이면 그 곳을 가운데에 두고 줌 5(전과 같다)', () => {
    mount();
    expect(calls.setBounds).toBe(0);
    expect(calls.extended).toEqual([{ lat: 35.1633, lng: 129.1614 }]);
    expect(calls.setCenter[0]).toEqual({ lat: 35.1633, lng: 129.1614 });
    expect(calls.setLevel).toContain(5);
  });

  it('🔴 fitPoints 를 켜면 기준점까지 넣어 맞춘다 — 기준 장소가 모서리에 걸리거나 화면 밖에 있지 않게', () => {
    mount(result, { fitPoints: true });
    // 그린 뒤 칸 크기를 맞추는 effect(relayout)가 한 번 더 맞추므로 횟수가 아니라 «불렸는가»를 본다
    expect(calls.setBounds).toBeGreaterThan(0);
    expect(calls.extended).toEqual([{ lat: 35.1633, lng: 129.1614 }, { lat: 35.1587, lng: 129.1604 }]);
  });

  it('fitPoints 를 켜도 번호 장소가 여럿이면 장소와 기준점을 모두 넣는다', () => {
    const more = [...result, { id: 'p-2', number: 2, name: '구덕주차장', latitude: 35.1601, longitude: 129.1580 }];
    mount(more, { fitPoints: true });
    expect(calls.extended).toHaveLength(3);
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
  const data = (extra: Record<string, unknown> = {}) => ({ stops: result, points: reference, routes: [], selectedId: 'p-1', currentLocation: null, fitPadding: [60, 60, 60, 60], colors, focus: true, shiftY: 0, ...extra });

  it('🔴 기본은 번호 장소만 맞춘다(전과 같다)', () => {
    boot().__renderKakaoMap(data());
    expect(calls.setBounds).toBe(0);
    expect(calls.extended).toEqual([{ lat: 35.1633, lng: 129.1614 }]);
  });

  it('🔴 fitPoints 를 받으면 기준점까지 넣어 맞춘다', () => {
    boot().__renderKakaoMap(data({ fitPoints: true }));
    expect(calls.setBounds).toBe(1);
    expect(calls.extended).toEqual([{ lat: 35.1633, lng: 129.1614 }, { lat: 35.1587, lng: 129.1604 }]);
  });
});
