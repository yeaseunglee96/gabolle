// 로컬 탐색 결과 격자 — 몇 열이고 한 장이 몇인가.
//
// 화면에서 떼어 둔다. 이 계산이 화면 안에 있으면 재려면 그려야 하고, 폭 하나하나를
// 그려 보는 것은 실제로 아무도 안 한다 — 그래서 그 자리가 조용히 틀린다.
import { gutter, spacing } from '@/design/tokens';
import { MAX_CONTENT_WIDTH, MAX_SPLIT_WIDTH } from '@/components/Screen';
import { isAtLeast } from '@/layout/breakpoints';

/** 카드 사이 간격. */
export const EXPLORE_GRID_GAP = spacing[4];

/**
 * 폭이 정하는 열 수 — 폰 2 · 600~1023 3 · 1024~ 4 (시안 05·06).
 *
 * 경계값은 이 저장소의 반응형 표를 그대로 쓴다. 이 화면에서 새 숫자를 만들지 않는다.
 */
export function exploreColumns(width: number): 2 | 3 | 4 {
  if (isAtLeast(width, 'lg')) return 4;
  if (isAtLeast(width, 'md')) return 3;
  return 2;
}

/**
 * 한 장의 폭. 화면 폭에서 **내용 최대 폭 상한**과 좌우 여백을 뺀 뒤 열 수로 나눈다.
 *
 * 🔴 상한을 빼먹으면 1920 짜리 화면에서 카드가 480 씩 벌어진다 — 내용은 1440 에서
 *    멈추는데 계산만 화면 끝까지 가기 때문이다. 상한은 Screen 이 정한 값을 그대로
 *    가져온다. 여기 숫자를 다시 적으면 둘이 따로 낡는다.
 */
export function exploreCardWidth(width: number): number {
  const columns = exploreColumns(width);
  const cap = isAtLeast(width, 'lg') ? MAX_SPLIT_WIDTH : isAtLeast(width, 'md') ? MAX_CONTENT_WIDTH : width;
  const available = Math.min(width, cap) - gutter * 2;
  return Math.max(0, Math.floor((available - EXPLORE_GRID_GAP * (columns - 1)) / columns));
}
