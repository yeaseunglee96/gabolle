// 전시에 쓰는 세 기기의 실제 화면 크기(dp)로 판과 회전 잠금을 고정한다 (S15P21E201-1940).
//   · Galaxy Z Fold6 바깥·펼침 — 실기기 `wm size`(968×2376, 1856×2160 @420dpi)에서 잰 값
//   · Galaxy S24+ · Galaxy Tab S9 FE+ — 삼성 공개 사양에서 계산한 값
// 정한 것: 폰(짧은 변 600 미만)은 세로로 잠근다 · 태블릿 세로는 데스크톱 판 · 폴드 펼침 세로는 폰 판.
//
// 🔴 세로 잠금은 app.json 의 orientation: "portrait" 한 줄로 한다. 회전 모듈(expo-screen-orientation)을
//    넣으려 했으나 잠금 파일이 바뀌면 의존성 검사가 돈다 — 그 검사는 지금 고칠 판이 없는 취약점
//    (node-forge 1.4.0, expo 가 씀) 때문에 빨갛다. 그래서 안드로이드 16 규칙에 기댄다:
//    targetSdk 36 앱은 큰 화면(짧은 변 600dp 이상 — 폴드 펼침·태블릿)에서 orientation 고정이 무시되어 계속 돈다.
//    폰(짧은 변 600 미만)에서만 세로로 잠긴다. 아래 「잠김」 열이 그 기대값이다.
import appJson from '../../../app.json';
import { breakpoint } from '@/layout/breakpoints';
import { isDesktopWindow } from '@/layout/useLayout';

/** 안드로이드 16 에서 orientation 고정이 먹는 화면인가 — 짧은 변이 폰 크기일 때. */
const lockedPortrait = (width: number, height: number) => Math.min(width, height) <= breakpoint.sm;

describe('전시 기기 표', () => {
  it.each([
    ['Fold6 바깥 세로', 369, 905, false, true],
    ['Fold6 바깥 가로', 905, 369, false, true],
    ['Fold6 펼침 세로', 707, 823, false, false],
    ['Fold6 펼침 가로', 823, 707, true, false],
    ['S24+ 세로', 384, 832, false, true],
    ['S24+ 가로', 832, 384, false, true],
    ['Tab S9 FE+ 세로', 800, 1280, true, false],
    ['Tab S9 FE+ 가로', 1280, 800, true, false],
    ['PC 브라우저', 1440, 900, true, false],
  ])('%s (%d×%d) → 데스크톱 %s · 세로 잠금 %s', (_name, width, height, desktop, lock) => {
    expect(isDesktopWindow(width, height)).toBe(desktop);
    expect(lockedPortrait(width, height)).toBe(lock);
  });
});

it('🔴 앱은 세로 고정으로 선언한다 — 폰만 잠기고 큰 화면은 안드로이드 16 이 풀어 준다', () => {
  expect(appJson.expo.orientation).toBe('portrait');
});
