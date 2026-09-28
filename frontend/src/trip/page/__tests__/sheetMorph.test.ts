// 여행 화면(폰) 탭바 = 창 — 떠 있는 막대가 제자리에서 늘어나 창이 된다(S15P21E201-1756, 사용자 요청).
//
// 🔴 사용자: 「일부러 하단 탭바를 하단에서 띄워 놓은 건데, 화면이 확장될 때 밑에서 올라오니까 이 의도가 사라진다.」
//    -1607 이 창을 «다 자란 채 화면 아래에서 밀어 올리기»(translateY)로 바꿨다(JS 매 프레임 높이 바꾸기가 폰에서 버벅여서).
//    이제는 막대의 폭·높이만 자란다 — 바닥과 모서리는 그대로라 밑에서 올라오지 않는다. Reanimated 가 폰 쪽에서 돌린다.
declare const require: (id: string) => any;
declare const __dirname: string;

import { CONTENT_IN, morphSize, TABS_OUT } from '../sheetMorph';

const bar = { width: 328, height: 64 };
const sheet = { width: 358, height: 646 };

describe('막대 → 창 모양', () => {
  it('0 이면 막대, 1 이면 창', () => {
    expect(morphSize(0, bar, sheet)).toEqual(bar);
    expect(morphSize(1, bar, sheet)).toEqual(sheet);
  });
  it('중간은 폭·높이가 함께 자란다 — 한쪽만 먼저 자라지 않는다', () => {
    expect(morphSize(0.5, bar, sheet)).toEqual({ width: 343, height: 355 });
  });
  it('범위 밖 값은 자른다 — 곡선이 튀어도 창보다 커지거나 막대보다 작아지지 않는다', () => {
    expect(morphSize(1.2, bar, sheet)).toEqual(sheet);
    expect(morphSize(-0.1, bar, sheet)).toEqual(bar);
  });
  it('🔴 속은 커지는 끝 무렵 나타나고, 탭 줄은 자라기 시작하면 곧 흐려진다 — 둘이 겹쳐 보이지 않는다', () => {
    expect(TABS_OUT).toBeLessThan(CONTENT_IN[0]);
    expect(CONTENT_IN[1]).toBe(1);
  });
});

describe('여행 화면이 이 움직임을 쓴다', () => {
  const { readFileSync } = require('fs');
  const { join } = require('path');
  const page = readFileSync(join(__dirname, '..', 'TripPageMobile.tsx'), 'utf8') as string;

  it('🔴 창을 화면 아래에서 밀어 올리지 않는다(translateY 없음)', () => {
    expect(page).not.toMatch(/translateY: shown\.interpolate/);
  });
  it('🔴 폰 쪽에서 돈다 — Reanimated 로 막대 모양을 키운다', () => {
    expect(page).toContain("from 'react-native-reanimated'");
    expect(page).toContain('morphSize(');
  });
  it('움직임 줄이기가 켜져 있으면 늘어나는 대신 서서히 나타난다', () => {
    expect(page).toMatch(/if \(reduceMotion\) \{[^}]*fade\.value = withTiming/);
  });
});
