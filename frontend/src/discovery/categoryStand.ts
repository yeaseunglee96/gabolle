// 갈래별 후보가 충분한가 —.

// / 범위 안 후보가 이보다 적으면 출발지 기준으로 채운다 —.
// * ... 정확한 근거가 있는 값은 아니고 운영을 보고 조정할 값이다. */
export const MIN_CANDIDATES_PER_CATEGORY = 12;

export type CategoryStand = 'enough' | 'short';

/**
 * @param placeCount 서버가 낸 이 갈래의 장소 수. 서버는 place.category 에 실제로 있는 값만
 * 내므로, 응답에 아예 없는 갈래는 0곳이다 (undefined 를 0 으로 읽는 이유).
 */
export function categoryStand(placeCount: number | undefined): CategoryStand {
  return (placeCount ?? 0) >= MIN_CANDIDATES_PER_CATEGORY ? 'enough' : 'short';
}
