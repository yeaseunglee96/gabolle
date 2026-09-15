// 갈래별 후보가 충분한가 — S15P21E201-1005.
//
// 바다(2~4곳)·자연(23)·카페(26)·도심(47) 은 부산 전체를 다 긁어도 엔진이 요구하는 최소치에
// 못 미친다. 지금은 고를 수 있고, 여행 조건을 다 입력한 뒤에야 「조건을 만족하는 코스가
// 없어요」를 만난다. 🔴 고르기 전에 말해 준다 — 끝까지 가서 만나는 빈 화면이 가장 나쁘다.
//
// 🔴 개수는 서버(GET /api/v1/places/categories)에서 받는다. 그래서 적재가 돌아 후보가
// 채워지면 이 코드를 고치지 않아도 저절로 풀린다. 어느 갈래가 부족한지를 여기 적어 두면
// 그 목록이 낡는 순간 거짓말이 된다.
export const MIN_CANDIDATES_PER_CATEGORY = 60;

export type CategoryStand = 'enough' | 'short';

/**
 * @param placeCount 서버가 낸 이 갈래의 장소 수. 서버는 place.category 에 실제로 있는 값만
 *   내므로, 응답에 아예 없는 갈래는 0곳이다 (undefined 를 0 으로 읽는 이유).
 */
export function categoryStand(placeCount: number | undefined): CategoryStand {
  return (placeCount ?? 0) >= MIN_CANDIDATES_PER_CATEGORY ? 'enough' : 'short';
}
