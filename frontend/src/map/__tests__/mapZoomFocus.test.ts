// 지도 첫 화면·고를 때 확대·확대축소 단추 — S15P21E201-1903 (2026-10-01 실기기: 해운대 네 곳이 한 점에 뭉치고, 카드를 눌러도 확대되지 않았다).
import { buildKakaoMapHtml } from '@/map/kakaoMapHtml';
import { FOCUS_LEVEL, fitPadding, fitTargets } from '@/map/mapFocus';

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
    // 보이는 부분의 가운데(가린 만큼 위)를 기준으로 확대한다
    expect(html).toContain('el.clientHeight / 2 - (shiftNow || 0)');
  });
});

describe('가로 화면처럼 지도가 낮을 때의 여백', () => {
  it('🔴 위아래 여백이 지도 높이를 다 먹지 않는다 — 맞출 자리를 60 은 남긴다(전에는 동아시아 전체로 물러났다)', () => {
    const [top, , bottom] = fitPadding(600, 410, 200);
    expect(410 - top - bottom).toBeGreaterThanOrEqual(60);
  });
  it('지도가 넉넉하면 전과 같다', () => {
    expect(fitPadding(0, 800, 0)).toEqual([60, 60, 60, 60]);
  });
});

describe('앱 지도 — 화면을 돌린 뒤 다시 맞출 때', () => {
  it('🔴 지금 지도 높이로 여백을 다시 줄인다 — 가로로 돌리면 동아시아 전체로 물러나던 것(S15P21E201-1903)', () => {
    const html = buildKakaoMapHtml('key');
    expect(html).toContain("var h = el.clientHeight || 0; var w = el.clientWidth || 0;");
    // 세로는 60 을 남기고(1903), 넓고 낮은 지도(탭 가로)는 높이의 45% 를 남긴다(S15P21E201-1980).
    expect(html).toContain("var room = Math.max(0, h - Math.max(60, Math.round(h * (w > h * 1.3 ? 0.45 : 0.25))));");
  });
});
