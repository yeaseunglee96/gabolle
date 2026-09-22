// 피드 사진 배치 — S15P21E201-1135.

/** 크기를 알 수도, 모를 수도 있다. 모르면 null — 모르는 것을 0 으로 적지 않는다. */
export type PhotoGridItem = { width?: number | null; height?: number | null };

/** 사진 자리 배치. */
export type PhotoGridPlan = 'single' | 'pair' | 'leftBig' | 'topWide' | 'quad';

/** 이 배치까지가 화면이 다룰 수 있는 최대 장수다. */
export const PHOTO_GRID_MAX = 4;

/**
 * 세로 사진인가. 모르면 세로로 치지 않는다 — 크기를 못 재는 경우가 흔한데
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

/** 사진 목록을 보고 배치를 정한다. */
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
