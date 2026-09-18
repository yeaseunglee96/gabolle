// 화면 폭 경계값 — 오직 여기서만 숫자로 적는다. 다른 곳은 전부 이 값을 참조한다
// (tools/check-breakpoints.mjs 가 이 파일 밖의 숫자 임계값을 잡아낸다).
//
// 참고 견본에서 경계값이 9종(520·640·680·720·760·840·900·1100·1101)으로 난립해
// 있었다 — 화면마다 그때그때 편한 값을 썼기 때문이다. 44개 화면에 흩어진 뒤에
// 통일하려면 44개를 다 다시 봐야 하므로, 첫 화면을 만들기 전에 3개로 못박는다.
// 근거: 상세설계서 반응형 레이아웃(F-SYS-02) · 기획서 v7 12.1절. S15P21E201-280.
//
// | 구간          | 레이아웃                    |
// |---------------|-----------------------------|
// | 360 ~ 599     | 1열 · 하단 고정 탭           |
// | 600 ~ 1023    | 1~2열 · 상단 가로 바         |
// | 1024 ~ 1439   | 사이드바 + 본문              |
// | 1440 ~        | 사이드바 + 본문 + 지도 패널  |
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
