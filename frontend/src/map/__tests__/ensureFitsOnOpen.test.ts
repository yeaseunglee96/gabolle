// 창을 열 때 지도 아래쪽 점이 창 윗변에 걸리던 것 — S15P21E201-1989 (빌드 47 폴드 접음, 해운대 당일 6번).
//
// 🔴 이 시험이 지키는 것:
//    ① 창을 열 때(refitKey → null) 가림 띠 뒤로 숨는 번호 점이 «있으면» 새 여백으로 다시 맞춘다. 다 보이면 그대로 둔다(튀지 않게, S15P21E201-1607).
//    ② 전체를 맞춘 뒤에는 고른 곳이 이미 보이는 띠 안이면 밀지 않는다 — 밀면 반대쪽 끝 점이 띠 밖으로 나갔다.
//    앱 지도(WebView 안의 HTML)를 가짜 kakao 로 돌려 부른 것만 잰다.
import { buildKakaoMapHtml } from '@/map/kakaoMapHtml';

class LatLng { constructor(public lat: number, public lng: number) {} }
class Point { constructor(public x: number, public y: number) {} }
class LatLngBounds { extend() {} }
class CustomOverlay { setMap() {} setPosition() {} }
class Polyline { setMap() {} }

// 점 id → 화면 y. 시험마다 바꾼다.
let screenY: Record<string, number> = {};
const setBounds = jest.fn();
const panTo = jest.fn();
class FakeMap {
  relayout() {}
  setBounds = setBounds;
  setCenter() {}
  setLevel() {}
  getLevel() { return 6; }
  panTo = panTo;
  getProjection() {
    return {
      containerPointFromCoords: (c: LatLng) => new Point(200, screenY[`${c.lat},${c.lng}`] ?? 150),
      coordsFromContainerPoint: (p: Point) => new LatLng(p.y, p.x),
    };
  }
}
const fakeMaps = { load: (cb: () => void) => cb(), LatLng, LatLngBounds, CustomOverlay, Polyline, Map: FakeMap, Point };
const fakeElement = () => ({ style: {}, setAttribute() {}, appendChild() {}, textContent: '', onclick: null });
const fakeDocument = { createElement: fakeElement, addEventListener() {}, removeEventListener() {}, getElementById: () => ({ clientHeight: 900, clientWidth: 400 }), head: { appendChild() {} } };

const stops = [
  { id: 'a', number: 1, name: '해리단길', latitude: 35.16, longitude: 129.16 },
  { id: 'b', number: 6, name: '해운대석각', latitude: 35.15, longitude: 129.15 },
];
const key = (s: { latitude: number; longitude: number }) => `${s.latitude},${s.longitude}`;
const colors = { navy: '#123', selected: '#456', canvas: '#fff' };

const boot = () => {
  const html = buildKakaoMapHtml('test-key');
  const code = html.split('<script>')[1].split('</script>')[0];
  const win: Record<string, unknown> = { addEventListener() {}, kakao: { maps: fakeMaps } };
  (globalThis as unknown as { document: unknown }).document = fakeDocument;
  new Function('window', 'document', code)(win, fakeDocument);
  return win as unknown as Record<string, ((data: unknown) => void) | undefined>;
};

// 창을 접은 상태(아래 가림 작음)로 처음 그린다.
const render = (win: Record<string, ((data: unknown) => void) | undefined>) =>
  win.__renderKakaoMap!({ stops, points: [], routes: [], selectedId: 'a', currentLocation: null, fitPadding: [104, 60, 200, 60], colors, focus: true, shiftY: 0 });
// 창을 연 여백 — 위 104, 아래 680 → 보이는 띠는 y 104 ~ 220.
const OPEN = { fitPadding: [104, 60, 680, 60], shiftY: 200 };

beforeEach(() => { setBounds.mockClear(); panTo.mockClear(); screenY = {}; });

describe('앱 지도 — 창을 열 때 가려진 점이 있을 때만 다시 맞춘다', () => {
  it('🔴 아래쪽 점이 창 뒤로 숨으면 새 여백으로 다시 맞춘다', () => {
    const win = boot();
    render(win);
    const before = setBounds.mock.calls.length;
    screenY[key(stops[1])] = 600; // 6번 점이 창(220 아래) 뒤에 있다
    expect(typeof win.__ensureKakaoMapFits).toBe('function');
    win.__ensureKakaoMapFits!(OPEN);
    expect(setBounds.mock.calls.length).toBe(before + 1);
    expect(setBounds.mock.calls[setBounds.mock.calls.length - 1].slice(1)).toEqual([104, 60, 680, 60]);
  });

  it('점이 다 보이면 맞추지 않는다 — 창을 열 때마다 지도가 튀지 않게', () => {
    const win = boot();
    render(win);
    const before = setBounds.mock.calls.length;
    screenY[key(stops[0])] = 150; screenY[key(stops[1])] = 180;
    win.__ensureKakaoMapFits!(OPEN);
    expect(setBounds.mock.calls.length).toBe(before);
  });

  it('🔴 전체를 맞춘 뒤 고른 곳이 이미 보이면 고른 곳으로 밀지 않는다', () => {
    const win = boot();
    screenY[key(stops[0])] = 150; // 고른 곳(1번)은 보이는 띠 안
    render(win);
    expect(panTo).not.toHaveBeenCalled();
  });

  it('고른 곳이 띠 밖이면 맞춘 뒤 그쪽으로 민다', () => {
    const win = boot();
    screenY[key(stops[0])] = 20; // 위 가림(104) 위 — 안 보인다
    render(win);
    expect(panTo).toHaveBeenCalled();
  });
});
