// 첫 실행 코치마크의 단추가 화면 밖으로 밀리던 것 — S15P21E201-1448.
//
// 🔴 2026-09-21 실기(SM-G973N, 1080×2280, 3단추 탐색줄, APK versionCode 29). 가입을 마친 «첫
//    화면»에서 설명 덩이가 시작 바 아래에 붙는데, 그 아래 남은 높이보다 내용이 길었다.
//    「바로 여행 만들기」는 탐색줄 뒤에 절반만 보이고 「먼저 둘러볼게요」는 화면 밖이었다.
//    닫는 길이 그 하나뿐이라, 사실상 «안드로이드 뒤로가기 말고는 닫을 방법이 없었다».
//
// 🔴 1403 의 「접기」는 동백이 설명과 «겹치는» 것을 고쳤을 뿐, 덩이가 아래로 «넘치는» 것은
//    그대로였다. 그래서 여기서는 겹침이 아니라 «넘침»을 잰다.
import { coachCopyPlacement } from '@/onboarding/HomeCoach';

// tsconfig 가 node 타입을 안 들고 있어서 import 로 쓰면 타입 검사가 막힌다 —
// 이 저장소의 다른 파일 검사 시험과 같은 방식이다(app/__tests__/homeHeaderBell.test.ts).
declare const require: (id: string) => any;
declare const __dirname: string;

const GAP = 16;

describe('coach copy placement', () => {
  it('keeps the copy below the start bar when it fits', () => {
    expect(coachCopyPlacement({
      screenHeight: 2280, insetTop: 72, insetBottom: 120,
      startBarBottom: 900, startBarTop: 700, copyHeight: 400, gap: GAP,
    })).toBe('below');
  });

  // 실기에서 실제로 잰 값에 가깝다 — 시작 바 아래가 1830 이고 덩이가 400 쯤이었다.
  it('moves the copy above the start bar when the bottom runs out', () => {
    expect(coachCopyPlacement({
      screenHeight: 2280, insetTop: 72, insetBottom: 120,
      startBarBottom: 1830, startBarTop: 1440, copyHeight: 400, gap: GAP,
    })).toBe('above');
  });

  it('counts the system bar — the same copy fits on a phone without one', () => {
    const shared = { screenHeight: 2280, insetTop: 72, startBarBottom: 1700, startBarTop: 1300, copyHeight: 520, gap: GAP };
    expect(coachCopyPlacement({ ...shared, insetBottom: 0 })).toBe('below');
    expect(coachCopyPlacement({ ...shared, insetBottom: 120 })).toBe('above');
  });

  it('stays below when it fits neither way — reading order is the tiebreaker', () => {
    expect(coachCopyPlacement({
      screenHeight: 1200, insetTop: 72, insetBottom: 120,
      startBarBottom: 800, startBarTop: 600, copyHeight: 900, gap: GAP,
    })).toBe('below');
  });

  it('stays below until the height has been measured', () => {
    expect(coachCopyPlacement({
      screenHeight: 2280, insetTop: 72, insetBottom: 120,
      startBarBottom: 1830, startBarTop: 1440, copyHeight: 0, gap: GAP,
    })).toBe('below');
  });
});

// 🔴 되돌려도 화면은 그려지고 단추만 아래로 사라진다 — 그래서 화면이 이 판정을 «쓰는지»도 잰다.
describe('the coach screen uses the placement', () => {
  it('positions the copy from coachCopyPlacement, not from a fixed top', () => {
    const { readFileSync } = require('fs');
    const { join } = require('path');
    const source = readFileSync(join(__dirname, '..', 'HomeCoach.tsx'), 'utf8') as string;

    expect(source).toContain('coachCopyPlacement({');
    expect(source).toContain('styles.copy, copyPosition');
    expect(source).not.toContain('styles.copy, { top: startCopyTop }');
  });
});
