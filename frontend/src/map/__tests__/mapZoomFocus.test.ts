// 지도 첫 화면·고를 때 확대·확대축소 단추 — S15P21E201-1903 (2026-10-01 실기기: 해운대 네 곳이 한 점에 뭉치고, 카드를 눌러도 확대되지 않았다).
import { buildKakaoMapHtml } from '@/map/kakaoMapHtml';
import { FOCUS_LEVEL, fitTargets } from '@/map/mapFocus';

describe('지도 맞추기 대상', () => {
  const stop = (id: string) => ({ id });
  it('🔴 번호 장소가 있으면 번호 장소만 맞춘다 — 먼 출발지·숙소 점은 넣지 않는다', () => {
    const stops = [stop('1'), stop('2')];
    expect(fitTargets(stops, [...stops, stop('start')])).toBe(stops);
  });
  it('번호 장소가 없으면(점 표시만 있는 지도) 보이는 점 전부로 맞춘다', () => {
    const visible = [stop('station')];
    expect(fitTargets([], visible)).toBe(visible);
  });
  it('고를 때 확대하는 줌은 동네가 보이는 4 다', () => {
    expect(FOCUS_LEVEL).toBe(4);
  });
});

describe('앱 지도 스크립트(kakaoMapHtml)', () => {
  const html = buildKakaoMapHtml('key');
  it('🔴 맞추는 범위를 번호 장소로 잡는다', () => {
    expect(html).toContain('var fitStops = stops.length ? stops : visible;');
  });
  it('🔴 고르면 FOCUS_LEVEL 까지 확대한다 — mapFocus.ts 와 같은 값', () => {
    expect(html).toContain(`var FOCUS_LEVEL = ${FOCUS_LEVEL};`);
    expect(html).toContain('map.getLevel() > FOCUS_LEVEL) map.setLevel(FOCUS_LEVEL)');
  });
  it('🔴 확대·축소 단추가 부르는 함수가 있다', () => {
    expect(html).toContain('window.__zoomKakaoMap = function (delta)');
  });
});
