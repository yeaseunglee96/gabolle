// 지도 아래쪽이 창에 가려 있을 때 — S15P21E201-1607.
// 🔴 S15P21E201-1988 부터 위·아래 여백은 «가린 높이 + 점 반지름» 으로 따로 둔다. 예전의 «높이의 25%/45% 는 맞출 자리로 남긴다»
//    (S15P21E201-1980·1986) 규칙은 그 자리를 만들려고 가림 띠를 덜어 내서, 빌드 45·46 에서 번호 점이 창·칩 뒤로 숨었다.
//    이제 맞출 자리가 좁으면 그 자리에 맞춰 줌을 덜 당긴다. 줄이는 것은 맞출 자리가 48 보다 좁아질 때뿐이다.
import { MARKER_CLEARANCE, fitPadding, focusShiftY } from '@/map/mapFocus';

describe('창에 가린 지도', () => {
  it('가린 것이 없으면 지금까지와 같다 — 네 변 60', () => {
    expect(fitPadding(0, 844)).toEqual([60, 60, 60, 60]);
    expect(focusShiftY(0, 844)).toBe(0);
  });

  it('🔴 가린 만큼 아래 여백을 더 두고(점 반지름까지), 고른 곳은 보이는 부분의 가운데로', () => {
    const [t1, , b1] = fitPadding(600, 844);
    expect(t1).toBe(60);
    expect(b1).toBe(600 + MARKER_CLEARANCE);
    expect(focusShiftY(600, 844)).toBe(300);
  });

  it('창이 지도를 거의 다 가려도 맞출 자리 48 은 남긴다 — 여백이 지도보다 커지면 카카오 지도가 최대로 멀어진다', () => {
    const [top, , bottom] = fitPadding(2000, 400);
    expect(400 - top - bottom).toBe(48);
    expect(focusShiftY(2000, 400)).toBe(140);
  });

  it('🔴 위가 상태바·칩에 가려진 만큼(점 반지름까지) 위 여백을 둔다 — 정차지가 칩 밑으로 숨지 않게(S15P21E201-1754)', () => {
    expect(fitPadding(0, 844, 100)).toEqual([100 + MARKER_CLEARANCE, 60, 60, 60]);
    // 위아래가 함께 다 가려지면 아래부터 줄이고, 위 가림은 지킨다
    const [t2, , b2] = fitPadding(2000, 844, 100);
    expect(t2).toBe(100 + MARKER_CLEARANCE);
    expect(844 - t2 - b2).toBe(48);
  });

  it('🔴 고른 곳은 위 가림도 뺀 «보이는 부분» 의 가운데로 — 위에 뜬 색 범례 밑에 깔리지 않게(S15P21E201-1896)', () => {
    expect(focusShiftY(654, 884, 100)).toBe(277);
    expect(focusShiftY(654, 884)).toBe(327);
    expect(focusShiftY(654, 884, 0)).toBe(327);
  });

  it('위만 가려졌거나 위가 더 가려졌으면 밀지 않는다 — 음수 없이 지도 가운데(넓은 화면은 전처럼)', () => {
    expect(focusShiftY(0, 900, 80)).toBe(0);
    expect(focusShiftY(50, 900, 200)).toBe(0);
  });
});

describe('🔴 위·아래 가림 띠는 서로 덜어 내지 않는다 — S15P21E201-1988', () => {
  it('폴드 바깥 화면(높이 900, 위 120, 아래 620): 위도 아래도 가림 + 반지름', () => {
    expect(fitPadding(620, 900, 120, 369)).toEqual([120 + MARKER_CLEARANCE, 60, 620 + MARKER_CLEARANCE, 60]);
  });
  it('넓이를 주든 안 주든 같다(가로·세로 규칙을 따로 두지 않는다)', () => {
    expect(fitPadding(560, 700, 80, 1205)).toEqual(fitPadding(560, 700, 80));
    expect(fitPadding(600, 844, 0, 390)).toEqual(fitPadding(600, 844));
  });
  it('가림이 작으면 가림 + 반지름, 다만 60 보다 작게는 안 둔다', () => {
    expect(fitPadding(100, 900, 80, 369)).toEqual([80 + MARKER_CLEARANCE, 60, 100 + MARKER_CLEARANCE, 60]);
    expect(fitPadding(10, 900, 10)).toEqual([60, 60, 60, 60]);
  });
});
