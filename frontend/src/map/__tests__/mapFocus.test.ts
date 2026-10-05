// 지도 아래쪽이 창에 가려 있을 때 — S15P21E201-1607.
import { fitPadding, focusShiftY } from '@/map/mapFocus';

describe('창에 가린 지도', () => {
  it('가린 것이 없으면 지금까지와 같다 — 네 변 60', () => {
    expect(fitPadding(0, 844)).toEqual([60, 60, 60, 60]);
    expect(focusShiftY(0, 844)).toBe(0);
  });

  it('🔴 가린 만큼 아래 여백을 더 두고, 고른 곳은 보이는 부분의 가운데로(가린 높이의 절반만큼 위)', () => {
    // 390×844 폰에서 창이 아래 600 을 가린다 — 맞출 자리는 높이의 25%(211) 는 남긴다(S15P21E201-1986)
    const [t1, , b1] = fitPadding(600, 844);
    expect(b1).toBeGreaterThan(t1);
    expect(844 - t1 - b1).toBeGreaterThanOrEqual(210);
    expect(focusShiftY(600, 844)).toBe(300);
  });

  it('창이 지도를 거의 다 가려도 맞출 자리는 남긴다 — 여백이 지도보다 커지면 카카오 지도가 최대로 멀어진다', () => {
    const [top, , bottom] = fitPadding(2000, 400);
    expect(400 - top - bottom).toBeGreaterThanOrEqual(99);
    expect(focusShiftY(2000, 400)).toBe(140);
  });

  it('🔴 위가 상태바·칩에 가려진 만큼 위 여백을 더 둔다 — 정차지가 칩 밑으로 숨지 않게(S15P21E201-1754)', () => {
    // 폰에서 상태바 24 + 요약·경사/그늘 칩 — 위 100 이 가려진다
    expect(fitPadding(0, 844, 100)).toEqual([160, 60, 60, 60]);
    // 위아래가 함께 가려져도 맞출 자리(높이의 25%)는 남긴다
    const [t2, , b2] = fitPadding(2000, 844, 100);
    expect(844 - t2 - b2).toBeGreaterThanOrEqual(210);
  });

  it('🔴 고른 곳은 위 가림도 뺀 «보이는 부분» 의 가운데로 — 위에 뜬 색 범례 밑에 깔리지 않게(S15P21E201-1896)', () => {
    // 열린 창이 아래 654 를, 상태바·요약·범례가 위 100 을 가린다 — 보이는 곳은 위에서 100 ~ 230
    expect(focusShiftY(654, 884, 100)).toBe(277);
    // 위 가림을 안 주면 전과 같다
    expect(focusShiftY(654, 884)).toBe(327);
    expect(focusShiftY(654, 884, 0)).toBe(327);
  });

  it('위만 가려졌거나 위가 더 가려졌으면 밀지 않는다 — 음수 없이 지도 가운데(넓은 화면은 전처럼)', () => {
    expect(focusShiftY(0, 900, 80)).toBe(0);
    expect(focusShiftY(50, 900, 200)).toBe(0);
  });
});

describe('넓고 낮은 지도(탭·웹 가로) — S15P21E201-1980', () => {
  it('🔴 아래 창이 지도를 거의 다 가려도 가로 지도에서는 높이의 45% 는 맞출 자리로 남긴다(전에는 60px 띠에 맞춰 통영·거제까지 물러났다)', () => {
    const height = 700;
    const [top, , bottom] = fitPadding(560, height, 80, 1205);
    expect(height - top - bottom).toBeGreaterThanOrEqual(Math.round(height * 0.45) - 1);
  });
  it('세로 지도는 넓이를 주든 안 주든 같다', () => {
    expect(fitPadding(600, 844, 0, 390)).toEqual(fitPadding(600, 844));
  });
  it('가로라도 가림이 작으면 손대지 않는다', () => {
    expect(fitPadding(100, 700, 80, 1205)).toEqual(fitPadding(100, 700, 80));
  });
});

describe('좁고 긴 지도(폴드 바깥 화면) — S15P21E201-1986', () => {
  it('🔴 아래 창이 대부분을 덮어도 높이의 25% 는 맞출 자리로 남긴다(전에는 100px 남짓에 맞춰 김해공항~오륙도가 한 화면)', () => {
    const height = 900;
    const [top, , bottom] = fitPadding(620, height, 120, 369);
    expect(height - top - bottom).toBeGreaterThanOrEqual(Math.round(height * 0.25) - 1);
  });
  it('가림이 작으면 손대지 않는다', () => {
    expect(fitPadding(100, 900, 80, 369)).toEqual([140, 60, 160, 60]);
  });
});
