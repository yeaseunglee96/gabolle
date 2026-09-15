import { routeMapUrl } from './externalMaps';

// S15P21E201-753 / 명세 4절 — 대중교통 단계 안내는 이 앱이 못 준다(업체 미정).
// 그래서 지도 앱으로 넘기는데, 🔴 목적지만 넘기면 사용자가 거기서 길찾기를 다시 눌러야 한다.
// 출발·도착이 함께 실리고 대중교통으로 열리는지를 여기서 잠근다.
const route = { originLat: 35.1587, originLng: 129.1604, destLat: 35.1532, destLng: 129.1187, destName: '광안리 해수욕장' };

describe('지도 앱 경로 주소', () => {
  it.each(['kakao', 'google', 'apple'] as const)('%s — 출발지와 도착지를 모두 싣는다', (provider) => {
    const url = routeMapUrl(provider, route, provider === 'kakao' ? 'app' : 'web');
    expect(url).toContain('35.1587');
    expect(url).toContain('129.1187');
  });

  it('🔴 대중교통으로 연다 — 자동차 경로로 열면 이 버튼이 대신하는 것이 사라진다', () => {
    expect(routeMapUrl('kakao', route, 'app')).toContain('PUBLICTRANSIT');
    expect(routeMapUrl('google', route, 'web')).toContain('travelmode=transit');
    expect(routeMapUrl('google', route, 'app')).toContain('directionsmode=transit');
    expect(routeMapUrl('apple', route, 'web')).toContain('dirflg=r');
  });

  it('장소 이름에 공백·한글이 있어도 주소가 깨지지 않는다', () => {
    const url = routeMapUrl('kakao', route, 'web');
    expect(url).toContain(encodeURIComponent('광안리 해수욕장'));
    expect(url).not.toContain(' ');
  });
});
