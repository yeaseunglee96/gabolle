// 피드 사진 배치 — S15P21E201-1135.
//
// 사진을 가로로 줄 세우면 한 장짜리 글이 작고 허전해 보이고, 세 장짜리는 무엇이
// 중심인지 알 수 없다. 트위터가 쓰는 방식대로 **장수와 사진의 방향에 따라** 자리를
// 나눈다.
//
// 🔴 배치를 정하는 일과 그리는 일을 갈라 놓는다. 정하는 쪽은 아무것도 안 그리는
// 순수 함수라 시험이 쉽고, 세 화면(작성 미리보기·목록 카드·글 상세)이 같은 답을
// 쓰게 된다. 화면마다 따로 판단하면 같은 사진이 자리마다 다르게 보인다.

/** 크기를 알 수도, 모를 수도 있다. 모르면 null — 모르는 것을 0 으로 적지 않는다. */
export type PhotoGridItem = { width?: number | null; height?: number | null };

/**
 * 사진 자리 배치.
 *
 * - `single`   한 장을 크게
 * - `pair`     좌우 반반
 * - `leftBig`  왼쪽에 큰 하나 + 오른쪽에 둘을 위아래로
 * - `topWide`  위에 넓게 하나 + 아래에 둘을 좌우로
 * - `quad`     2×2
 */
export type PhotoGridPlan = 'single' | 'pair' | 'leftBig' | 'topWide' | 'quad';

/** 이 배치까지가 화면이 다룰 수 있는 최대 장수다. */
export const PHOTO_GRID_MAX = 4;

/**
 * 세로 사진인가. 🔴 **모르면 세로로 치지 않는다** — 크기를 못 재는 경우가 흔한데
 * (원격 사진을 아직 안 받았을 때), 그때 한쪽으로 단정하면 사진이 늦게 도착할 때마다
 * 배치가 툭 바뀐다. 모를 때의 기본 배치는 아래 {@link planPhotoGrid} 가 정한다.
 */
function isPortrait(item: PhotoGridItem | undefined): boolean | null {
  if (!item) return null;
  const { width, height } = item;
  if (typeof width !== 'number' || typeof height !== 'number') return null;
  if (width <= 0 || height <= 0) return null;
  return height > width;
}

/**
 * 사진 목록을 보고 배치를 정한다.
 *
 * 🔴 **세 장일 때만 사진의 방향을 본다.** 첫 장이 세로면 그 한 장이 왼쪽을 온전히
 * 차지하는 편이 자연스럽고(넘겨받은 예시가 그 모양이다), 첫 장이 가로면 위를 넓게
 * 쓰는 편이 자연스럽다. 나머지 장수는 방향과 무관하게 자리가 정해진다.
 *
 * 크기를 못 재면 `leftBig` 으로 간다 — 넘겨받은 예시의 모양이고, 나중에 크기가
 * 도착해 `topWide` 로 바뀌더라도 한 번만 바뀐다.
 */
export function planPhotoGrid(items: PhotoGridItem[]): PhotoGridPlan | null {
  const count = Math.min(items.length, PHOTO_GRID_MAX);
  if (count <= 0) return null;
  if (count === 1) return 'single';
  if (count === 2) return 'pair';
  if (count >= 4) return 'quad';
  return isPortrait(items[0]) === false ? 'topWide' : 'leftBig';
}

/**
 * 배치별로 사진 칸이 몇 개인지 — 화면이 넘치게 그리지 않도록.
 * 목록이 {@link PHOTO_GRID_MAX} 보다 길면 뒤는 그리지 않는다.
 */
export function photoGridSlots(plan: PhotoGridPlan): number {
  return plan === 'single' ? 1 : plan === 'pair' ? 2 : plan === 'quad' ? 4 : 3;
}
