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
 *   · 그 이상인 세로 화면은 **세로로 긴 정도(높이 ÷ 폭)** 로 가른다 (S15P21E201-1947)
 *     태블릿 세로(실측 753×1205 → 1.6)는 데스크톱 판, 폴드 펼침 세로(실측 707×823 → 1.16)는 폰 판.
 *     🔴 전에는 「폭 768 이상」으로 갈랐다 — 실기기 탭이 753 이라 못 넘었고, 디스플레이 「화면 크기」를 키우면
 *        폭은 더 줄어든다. 화면비는 화면 크기 설정과 상관없이 그대로라 이쪽이 튼튼하다.
 */
export const shortSide = {
  phoneMax: 599,
} as const;

/** 세로 화면이 이만큼 길쭉하면(높이 ÷ 폭) 태블릿으로 본다. 탭 1.6 · 폴드 펼침 1.16 의 사이. */
export const tabletPortraitAspect = 1.3;

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
