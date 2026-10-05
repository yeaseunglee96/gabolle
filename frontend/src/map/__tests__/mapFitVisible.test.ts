// S15P21E201-1987 — 빌드 45 기기 확인에서 나온 지도 점 가림과 당일 이름 후보.
declare const require: (id: string) => any;
declare const __dirname: string;
const fs = require('fs');
const path = require('path');

import { MARKER_CLEARANCE, bigMapHeight, fitPadding } from '../mapFocus';
import { collapseSameDayRange, planNameStep } from '@/trip/tripNaming';

describe('큰 지도 높이는 보이는 영역에 맞춘다', () => {
  it('보이는 영역 높이를 알면 그보다 길게 그리지 않는다 — 탭 가로(창 ~750, 보이는 영역 ~430)', () => {
    expect(bigMapHeight(753, 430)).toBe(430);
  });

  it('보이는 영역을 아직 모르면 전처럼 창 높이로 어림한다', () => {
    expect(bigMapHeight(1000, 0)).toBe(740);
    expect(bigMapHeight(600, 0)).toBe(520);
  });

  it('보이는 영역이 아주 낮아도 320 은 남긴다', () => {
    expect(bigMapHeight(400, 200)).toBe(320);
  });

  it('넓은 화면 여행 화면이 이 함수를 쓴다', () => {
    const src = fs.readFileSync(path.join(__dirname, '../../trip/page/TripPageDesktop.tsx'), 'utf8');
    expect(src).toMatch(/bigMapHeight\(windowHeight, fillHeight\)/);
    expect(src).not.toMatch(/Math\.max\(520, windowHeight - 260\)/);
  });
});

describe('위·아래 여백은 따로 — 가림 띠 + 점 반지름(S15P21E201-1988)', () => {
  // 빌드 45: 아래 점이 창 윗변에 걸림 · 빌드 46: 위 점이 「장소 N곳」 칩 뒤로 숨음. 한쪽에서 덜어 내지 않는다.
  it('폴드 바깥·창 연 상태(높이 945, 위 가림 100, 아래 가림 703): 위도 아래도 가림 + 반지름을 지킨다', () => {
    const [top, , bottom] = fitPadding(703, 945, 100);
    expect(top).toBe(100 + MARKER_CLEARANCE);
    expect(bottom).toBe(703 + MARKER_CLEARANCE);
  });

  it('범례까지 떠 있어도(위 가림 132) 위를 덜어 내지 않는다', () => {
    const [top] = fitPadding(703, 945, 132);
    expect(top).toBe(132 + MARKER_CLEARANCE);
  });

  it('지도 높이를 거의 다 덮을 때만 줄인다 — 맞출 자리 48 은 남기고, 아래부터', () => {
    const [top, , bottom] = fitPadding(380, 500, 60);
    expect(500 - top - bottom).toBe(48);
    expect(top).toBe(60 + MARKER_CLEARANCE);
  });

  it('가림이 없으면 네 변 60', () => {
    expect(fitPadding(0, 700, 0)).toEqual([60, 60, 60, 60]);
  });

  it('넓은 화면 큰 지도는 「장소 N곳」 칩 높이를 위 가림으로 넘긴다', () => {
    const src = fs.readFileSync(path.join(__dirname, '../../trip/page/TripPageDesktop.tsx'), 'utf8');
    expect(src).toContain('topInset={routeLegend ? LEGEND_COVER : BIG_MAP_CHIP_COVER}');
  });

  it('앱 지도(kakaoMapHtml)는 높이 비율로 덜어 내지 않는다 — 같은 규칙(48 을 남길 때만 아래부터)', () => {
    const src = fs.readFileSync(path.join(__dirname, '../kakaoMapHtml.ts'), 'utf8');
    expect(src).not.toMatch(/0.45 : 0.25/);
    expect(src).not.toContain('var up = Math.min(pad[0] + pad[2] - room');
    expect(src).toContain('var over = pad[0] + pad[2] + 48 - h');
  });
});

describe('당일 여행 이름 후보의 날짜 범위', () => {
  it('같은 날짜 두 번은 한 번만', () => {
    expect(collapseSameDayRange('2026-10-21 ~ 2026-10-21')).toBe('2026-10-21');
    expect(collapseSameDayRange('해운대 2026-10-21~2026-10-21')).toBe('해운대 2026-10-21');
  });

  it('다른 날짜 범위는 그대로', () => {
    expect(collapseSameDayRange('2026-10-21 ~ 2026-10-22')).toBe('2026-10-21 ~ 2026-10-22');
  });

  it('후보 목록에 적용되고, 줄인 뒤 겹치는 후보는 하나만 남는다', () => {
    const step = planNameStep({ state: 'success', suggestions: ['영도 하루', '2026-10-21 ~ 2026-10-21', '2026-10-21'], source: 'TEMPLATE', discardedCount: 0 } as never);
    expect(step.suggestions).toEqual(['영도 하루', '2026-10-21']);
  });
});
