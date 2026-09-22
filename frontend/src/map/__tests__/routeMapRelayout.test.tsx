// 칸 크기가 바뀌면 지도에 말해 주는가 — S15P21E201-1417.
//
// 피드의 지도 시트는 열리면서 커진다. 카카오 지도는 만들어질 때의 크기만 알아서, 안 말해 주면
// 맨 위 한 줄만 타일을 그리고 나머지는 회색 「kakaomap」 바탕이다(실기 2026-09-21).
// 실제 SDK 는 없으니 가짜 window.kakao 를 두고 «relayout 이 불리는가»만 잰다.
import { Platform } from 'react-native';
import { act, create } from 'react-test-renderer';
import { buildKakaoMapHtml } from '@/map/kakaoMapHtml';
// 🔴 '@/map/RouteMap' 은 jest(플랫폼 ios)에서 RouteMap.native.tsx 로 풀린다 — 웹 파일을 이름 그대로 집는다.
// eslint-disable-next-line @typescript-eslint/no-require-imports
const { RouteMap } = require('../RouteMap.tsx') as typeof import('../RouteMap');

// jest 는 .native.tsx 를 먼저 고를 수 있고, 그 안의 react-native-webview 는 시험 환경에 네이티브 모듈이 없다.
// 여기서 재는 것은 웹 쪽이라 WebView 는 빈 껍데기로 둔다.
jest.mock('react-native-webview', () => ({ WebView: () => null }));
jest.mock('@/i18n', () => ({ useI18n: () => ({ tx: (ko: string) => ko }) }));

// 시험 환경에 DOM 이 없다 — 지도가 만드는 마커·선 요소만 받아 주는 최소한의 document.
const fakeElement = () => ({ style: {}, setAttribute() {}, appendChild() {}, textContent: '', onclick: null });
(globalThis as unknown as { document: unknown }).document = { createElement: fakeElement, addEventListener() {}, removeEventListener() {}, getElementById: () => null, head: { appendChild() {} } };

const relayout = jest.fn();
const setBounds = jest.fn();
class LatLng { constructor(public lat: number, public lng: number) {} }
class LatLngBounds { extend() {} }
class CustomOverlay { setMap() {} }
class Polyline { setMap() {} }
class Map { relayout = relayout; setBounds = setBounds; setCenter() {} setLevel() {} }

describe('RouteMap 웹 — 칸 크기가 바뀌면', () => {
  const originalOs = Platform.OS;
  beforeAll(() => {
    Object.defineProperty(Platform, 'OS', { value: 'web', configurable: true });
    process.env.EXPO_PUBLIC_KAKAO_MAP_JS_KEY = 'test-key';
    (window as unknown as { kakao: unknown }).kakao = { maps: { load: (cb: () => void) => cb(), LatLng, LatLngBounds, CustomOverlay, Polyline, Map } };
  });
  afterAll(() => {
    Object.defineProperty(Platform, 'OS', { value: originalOs, configurable: true });
    delete (window as unknown as { kakao?: unknown }).kakao;
  });
  beforeEach(() => { relayout.mockClear(); setBounds.mockClear(); });

  const stops = [
    { id: 'a', number: 1, name: '해운대', latitude: 35.158, longitude: 129.160 },
    { id: 'b', number: 2, name: '광안리', latitude: 35.153, longitude: 129.118 },
  ];

  it('height 가 바뀌면 relayout 하고 같은 범위를 다시 맞춘다', () => {
    let tree!: ReturnType<typeof create>;
    // react-test-renderer 는 DOM 을 안 만든다 — 지도 칸(ref)만 진짜 div 로 준다.
    act(() => { tree = create(<RouteMap stops={stops} selectedId="a" onSelect={() => {}} height={120} />, { createNodeMock: () => document.createElement('div') }); });
    const boundsBefore = setBounds.mock.calls.length;
    expect(boundsBefore).toBeGreaterThan(0);
    act(() => { tree.update(<RouteMap stops={stops} selectedId="a" onSelect={() => {}} height={520} />); });
    expect(relayout).toHaveBeenCalled();
    expect(setBounds.mock.calls.length).toBeGreaterThan(boundsBefore);
  });
});

describe('앱(WebView) HTML', () => {
  it('창 크기가 바뀌면 relayout 한다', () => {
    const html = buildKakaoMapHtml('test-key');
    expect(html).toContain("addEventListener('resize'");
    expect(html).toContain('map.relayout()');
  });
});
