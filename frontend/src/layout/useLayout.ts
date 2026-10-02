// 갤럭시 폴드8 대응. 이 훅이 이 셸에서 제일 중요하다
// 폴드 기기는 앱이 켜진 채로 화면비가 접힘(좁고 길쭉) ↔ 펼침(거의 정사각)으로 바뀌는데
// Figma 에는 폰(390) 과 웹(1440) 만 있고 그 사이 폭에 대한 디자인이 없다.
// useWindowDimensions 는 회전·접힘 때마다 다시 렌더링을 트리거하므로 실시간으로 따라간다.
//
// 🔴 모바일이냐 데스크톱이냐는 **여기 한 곳**에서 정한다 (S15P21E201-1563).
//    전에는 둘로 갈려 있었다 — 위쪽 메뉴·탭바는 「짧은 변 600 이상」, 화면 내용은 「폭 1024 이상」.
//    폴드8 을 펼치면(717×795 · 795×717) 앞의 것만 넘어서 위쪽 메뉴 밑에 폰 내용이 뜨고,
//    여행 페이지는 두 판 어디에도 안 들어 옛 화면이 떴다.
//    사용자가 정한 것: 외부 화면·펼침 세로 = 모바일, 펼침 가로 = 데스크톱.
import { useWindowDimensions } from 'react-native';

import { isAtLeast, shortSide } from './breakpoints';

/** `tablet` 은 옛 이름이다 — 뜻은 «데스크톱처럼 동작한다»(위쪽 메뉴 · 넓은 판). `desktop` 과 같다. */
export type LayoutKind = 'phone' | 'tablet';

export type Layout = {
  kind: LayoutKind;
  /** 데스크톱 판을 그리나. `kind === 'tablet'` 과 같다 — 새 코드는 이것을 읽는다. */
  desktop: boolean;
  width: number;
  height: number;
  isLandscape: boolean;
};


/**
 * 데스크톱으로 볼 화면인가 — 짧은 변이 600 이상이고, **가로이거나 폭이 1024 이상**.
 *   · 폴드 외부 화면 374×918 / 918×374 → 모바일 (짧은 변이 모자란다)
 *   · 폴드 펼침 세로 717×795           → 모바일 (세로이고 폭이 768 에 못 미친다)
 *   · 폴드 펼침 가로 795×717           → 데스크톱
 *   · 태블릿 세로 800×1280              → 데스크톱 (폭 768 이상 — S15P21E201-1940, 폰 판을 늘리면 카드가 지나치게 커진다)
 *   · PC 브라우저                      → 데스크톱 (폭 1024 이상이면 세로로 긴 창이어도)
 * 기기 표는 __tests__/deviceTable.test.ts 에 고정해 두었다.
 */
export function isDesktopWindow(width: number, height: number): boolean {
  if (Math.min(width, height) <= shortSide.phoneMax) return false;
  return width > height || width >= shortSide.tabletPortraitMin || isAtLeast(width, 'lg');
}

export function useLayout(): Layout {
  const { width, height } = useWindowDimensions();
  const desktop = isDesktopWindow(width, height);

  return {
    kind: desktop ? 'tablet' : 'phone',
    desktop,
    width,
    height,
    isLandscape: width > height,
  };
}
