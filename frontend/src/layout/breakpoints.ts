// 화면 폭 경계값 — 오직 여기서만 숫자로 적는다. 다른 곳은 전부 이 값을 참조한다
// (tools/check-breakpoints.mjs 가 이 파일 밖의 숫자 임계값을 잡아낸다).
export const breakpoint = {
  sm: 599,
  md: 1023,
  lg: 1439,
} as const;

/**
 * 짧은 변 경계값 (S15P21E201-1940).
 *   · phoneMax 미만 → 폰. 세로로 잠근다 — 가로로 돌리면 높이가 370dp 남짓이라 하단 메뉴가 내용을 덮는다
 *   · 그 이상이면서 폭이 tabletPortraitMin 이상인 세로 화면 → 데스크톱 판 (Tab S9 FE+ 세로 800dp)
 *     폴드 펼침 세로(707·717dp)는 이보다 좁아서 폰 판으로 남는다
 */
export const shortSide = {
  phoneMax: 599,
  tabletPortraitMin: 768,
} as const;

export type WidthTier = 'sm' | 'md' | 'lg' | 'xl';

export function widthTier(width: number): WidthTier {
  if (width <= breakpoint.sm) return 'sm';
  if (width <= breakpoint.md) return 'md';
  if (width <= breakpoint.lg) return 'lg';
  return 'xl';
}

/** width 가 tier 의 하한 이상인지. 예: isAtLeast(1024, 'lg') → true */
export function isAtLeast(width: number, tier: Exclude<WidthTier, 'sm'>): boolean {
  if (tier === 'md') return width > breakpoint.sm;
  if (tier === 'lg') return width > breakpoint.md;
  return width > breakpoint.lg; // 'xl'
}
