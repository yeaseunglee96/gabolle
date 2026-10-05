// S15P21E201-1987 — 빌드 45 기기 확인에서 나온 지도 점 가림과 당일 이름 후보.
declare const require: (id: string) => any;
declare const __dirname: string;
const fs = require('fs');
const path = require('path');

import { bigMapHeight, fitPadding } from '../mapFocus';
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

describe('여백이 넘치면 위에서 먼저 덜어 낸다 — 맨 아래 점이 아래 창 윗변에 걸리지 않게', () => {
  it('폴드 바깥 화면: 아래 여백은 같은 비율로 줄일 때보다 크고, 맞출 자리(높이의 25%)는 그대로다', () => {
    const [top, , bottom] = fitPadding(560, 700, 80);
    // 위 60+80=140, 아래는 지도 높이에서 위·가장자리를 뺀 500 으로 먼저 잘린다. 둘의 합 640 이 남길 수 있는 525 를 넘는다.
    const proportionalBottom = Math.round(500 * (525 / 640));
    expect(700 - top - bottom).toBe(Math.round(700 * 0.25));
    // 위는 60 까지 덜어 낸 뒤(남은 넘침 35) 둘을 같은 비율로 줄인다 — 위 56, 아래 469.
    expect(top).toBe(56);
    expect(bottom).toBe(469);
    expect(bottom).toBeGreaterThan(proportionalBottom + 40);
  });

  it('넘치지 않으면 그대로다', () => {
    expect(fitPadding(0, 700, 0)).toEqual([60, 60, 60, 60]);
  });

  it('앱 지도(kakaoMapHtml)도 같은 규칙이다', () => {
    const src = fs.readFileSync(path.join(__dirname, '../kakaoMapHtml.ts'), 'utf8');
    expect(src).toContain('var up = Math.min(pad[0] + pad[2] - room, Math.max(0, pad[0] - 60))');
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
