// 「지도 보기」로 창을 접으면 지도를 한 번 다시 맞춘다 — S15P21E201-1754.
//
// 🔴 이 시험이 지키는 것(갤럭시 S10 실기 — 경로가 화면 위 15% 에 몰려 상태바·칩 밑으로 숨었다):
//    ① 창이 열린 채 맞춘 큰 아래 여백이 접은 뒤에도 남아 있었다. refitKey 가 새 값이 되면 지금 여백으로 다시 맞춘다.
//    ② 가린 높이(bottomInset)만 바뀌어서는, 또 refitKey 가 null 로 돌아가서는(창을 열 때) 맞추지 않는다 — 튀지 않게(S15P21E201-1607).
//    ③ 위 여백에 topInset(상태바·칩)을 더한다.
//    웹 지도(RouteMap.tsx)와 앱 지도(WebView 안의 HTML) 둘 다 본다. 실제 SDK 는 없으니 가짜 kakao 로 부른 것만 잰다.
import { Platform } from 'react-native';
import { act, create } from 'react-test-renderer';

import { buildKakaoMapHtml } from '@/map/kakaoMapHtml';
// 🔴 '@/map/RouteMap' 은 jest(플랫폼 ios)에서 RouteMap.native.tsx 로 풀린다 — 웹 파일을 이름 그대로 집는다.
// eslint-disable-next-line @typescript-eslint/no-require-imports
const { RouteMap } = require('../RouteMap.tsx') as typeof import('../RouteMap');

jest.mock('react-native-webview', () => ({ WebView: () => null }));
jest.mock('@/i18n', () => ({ useI18n: () => ({ tx: (ko: string) => ko }) }));

const fakeElement = () => ({ style: {}, setAttribute() {}, appendChild() {}, textContent: '', onclick: null });
const fakeDocument = { createElement: fakeElement, addEventListener() {}, removeEventListener() {}, getElementById: () => ({}), head: { appendChild() {} } };
(globalThis as unknown as { document: unknown }).document = fakeDocument;

const setBounds = jest.fn();
class LatLng { constructor(public lat: number, public lng: number) {} }
class Point { constructor(public x: number, public y: number) {} }
class LatLngBounds { extend() {} }
class CustomOverlay { setMap() {} setPosition() {} }
class Polyline { setMap() {} }
class FakeMap {
  relayout() {}
  setBounds = setBounds;
  setCenter() {}
  setLevel() {}
  panTo() {}
  getProjection() { return { containerPointFromCoords: (c: LatLng) => new Point(c.lng, c.lat), coordsFromContainerPoint: (p: Point) => new LatLng(p.y, p.x) }; }
}
const fakeMaps = { load: (cb: () => void) => cb(), LatLng, LatLngBounds, CustomOverlay, Polyline, Map: FakeMap, Point };

const stops = [
  { id: 'a', number: 1, name: '출발지', latitude: 35.1, longitude: 129.03 },
  { id: 'b', number: 2, name: '용두산', latitude: 35.1, longitude: 129.032 },
];
// setBounds(bounds, 위, 오른쪽, 아래, 왼쪽) — 마지막으로 맞춘 여백
const lastPad = () => setBounds.mock.calls[setBounds.mock.calls.length - 1].slice(1);

beforeEach(() => { setBounds.mockClear(); });

describe('웹 지도 — 창을 접을 때 다시 맞추기', () => {
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

  const props = { stops, selectedId: 'a', onSelect: () => {}, height: 844, topInset: 100 };

  it('🔴 refitKey 가 새 값이 되면 줄어든 아래 여백·위 여백으로 다시 맞춘다 — null 로 돌아갈 때는 안 맞춘다', () => {
    let tree!: ReturnType<typeof create>;
    act(() => { tree = create(<RouteMap {...props} bottomInset={600} refitKey={null} />, { createNodeMock: () => ({ clientHeight: 844 }) }); });
    expect(lastPad()).toEqual([56, 60, 577, 60]); // 세로도 높이의 25% 는 맞출 자리(S15P21E201-1986). 넘친 만큼은 위에서 먼저 덜어 낸다(S15P21E201-1987)
    const before = setBounds.mock.calls.length;
    // 창만 접혔다(가린 높이만 바뀜) — 맞추지 않는다
    act(() => { tree.update(<RouteMap {...props} bottomInset={200} refitKey={null} />); });
    expect(setBounds.mock.calls.length).toBe(before);
    // 「지도 보기」 — 한 번 다시 맞춘다
    act(() => { tree.update(<RouteMap {...props} bottomInset={200} refitKey="collapsed:0" />); });
    expect(setBounds.mock.calls.length).toBe(before + 1);
    expect(lastPad()).toEqual([160, 60, 260, 60]);
    // 창을 다시 연다 — 맞추지 않는다
    act(() => { tree.update(<RouteMap {...props} bottomInset={600} refitKey={null} />); });
    expect(setBounds.mock.calls.length).toBe(before + 1);
  });
});

describe('앱 지도(WebView 안의 HTML) — 다시 맞추기', () => {
  const boot = () => {
    const html = buildKakaoMapHtml('test-key');
    const code = html.split('<script>')[1].split('</script>')[0];
    const win: Record<string, unknown> = { addEventListener() {}, kakao: { maps: fakeMaps } };
    new Function('window', 'document', code)(win, fakeDocument);
    return win as unknown as Record<string, ((data: unknown) => void) | undefined>;
  };
  const colors = { navy: '#123', selected: '#456', canvas: '#fff' };

  it('🔴 __fitKakaoMap 은 새 여백으로 같은 범위를 다시 맞춘다', () => {
    const win = boot();
    win.__renderKakaoMap!({ stops, points: [], routes: [], selectedId: 'a', currentLocation: null, fitPadding: [160, 60, 624, 60], colors, focus: false, shiftY: 0 });
    expect(lastPad()).toEqual([160, 60, 624, 60]);
    expect(typeof win.__fitKakaoMap).toBe('function');
    win.__fitKakaoMap!({ fitPadding: [160, 60, 260, 60], shiftY: 100 });
    expect(lastPad()).toEqual([160, 60, 260, 60]);
  });
});
