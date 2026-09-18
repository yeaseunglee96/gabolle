// 화면 폭 경계값 — 오직 여기서만 숫자로 적는다. 다른 곳은 전부 이 값을 참조한다
// (tools/check-breakpoints.mjs 가 이 파일 밖의 숫자 임계값을 잡아낸다).
export const breakpoint = {
  sm: 599,
  md: 1023,
  lg: 1439,
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
