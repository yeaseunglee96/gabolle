// S15P21E201-1991 — 일정 창을 연 폰 지도는 보이는 띠가 120~150 뿐이다. 그 띠에 번호 장소 전부를 맞추면 김해~송정까지 물러나
// 점이 한 덩어리로 겹쳤다(빌드 47 폴드 펼침). 띠가 좁으면 전체 맞춤 대신 고른 곳(없으면 1번)을 가까이 보여 준다.
import { buildKakaoMapHtml } from '../kakaoMapHtml';
import { FOCUS_LEVEL, NARROW_BAND, fitPadding, isNarrowBand } from '../mapFocus';

describe('isNarrowBand', () => {
  it('일정 창을 연 폰(띠 약 130) 은 좁다', () => {
    const height = 823;
    const pad = fitPadding(823 - 230, height, 100);
    expect(isNarrowBand(height, pad)).toBe(true);
  });

  it('창을 접은 폰·넓은 화면 큰 지도는 좁지 않다', () => {
    expect(isNarrowBand(823, fitPadding(200, 823, 100))).toBe(false);
    expect(isNarrowBand(600, fitPadding(0, 600, 54))).toBe(false);
  });

  it('높이를 아직 모르면, 또 아래가 가려 있지 않은 작은 지도는 좁다고 하지 않는다', () => {
    expect(isNarrowBand(0, [60, 60, 200, 60])).toBe(false);
    expect(isNarrowBand(120, [60, 60, 60, 60])).toBe(false);
  });

  it('기준은 200 이다', () => {
    expect(NARROW_BAND).toBe(200);
    expect(isNarrowBand(400, [100, 60, 101, 60])).toBe(true);
    expect(isNarrowBand(400, [100, 60, 100, 60])).toBe(false);
  });
});

describe('앱 지도(kakaoMapHtml) 도 같은 규칙', () => {
  const src = buildKakaoMapHtml('test-key');

  it('좁은 띠면 전체 맞춤 대신 고른 곳을 가까이 본다', () => {
    expect(src).toContain('NARROW_BAND = 200');
    expect(src).toMatch(/narrow\(pad, h\)/);
    expect(src).toContain(`FOCUS_LEVEL + 1`);
    expect(FOCUS_LEVEL).toBe(4);
  });

  it('창을 열 때 다시 맞출지 정할 때도 좁은 띠면 고른 곳만 본다', () => {
    const ensure = src.slice(src.indexOf('window.__ensureKakaoMapFits'), src.indexOf('window.__selectKakaoMap'));
    expect(ensure).toMatch(/narrow\(/);
  });
});
