import { categoryStand, MIN_CANDIDATES_PER_CATEGORY } from '../categoryStand';

// 후보가 모자란 갈래를 고르게 두면 여행 조건을 다 넣은 뒤에야 「조건을 만족하는 코스가
// 없어요」를 만난다 — S15P21E201-1005. 고르기 전에 가른다.
const MIN = MIN_CANDIDATES_PER_CATEGORY;

describe('갈래 후보가 충분한가', () => {
  it('최소치를 채우면 고를 수 있다', () => {
    expect(categoryStand(MIN)).toBe('enough');
    expect(categoryStand(MIN + 1)).toBe('enough');
  });

  it('최소치에 한 곳이라도 모자라면 못 고른다', () => {
    expect(categoryStand(MIN - 1)).toBe('short');
  });

  it('서버 응답에 아예 없는 갈래는 0곳으로 읽는다', () => {
    expect(categoryStand(undefined)).toBe('short');
  });

  // 문턱이 60 이던 동안 바다는 영영 못 골랐다. 적재가 모자라서가 아니라 부산에 그만큼이
  // 없어서다 — 관광공사 총계로 부산 자연이 74곳, 그중 해안 32곳, 해수욕장 9곳이다.
  // 바다와 자연이 같은 74곳을 나눠 가지므로 둘 다 60 을 넘기는 배분은 존재하지 않는다.
  it('바다가 도달 가능한 문턱이어야 한다 — 부산 해안 전체가 32곳뿐이다', () => {
    const BUSAN_COASTAL_PLACES_TOTAL = 32; // 관광공사 실측 2026-09-16
    expect(MIN).toBeLessThanOrEqual(BUSAN_COASTAL_PLACES_TOTAL);
  });

  it('지금 운영 개수로 여섯 갈래가 다 고를 수 있다', () => {
    const measured = { 바다: 16, 자연: 47, 카페: 158, 도심: 85, 문화: 165, 음식: 2197 };
    for (const count of Object.values(measured)) expect(categoryStand(count)).toBe('enough');
  });

  it('한 줌뿐인 갈래는 여전히 막는다', () => {
    // 낮춘 것이 「아무거나 통과」가 되면 안 된다. 하루 네 곳도 못 채우는 갈래는 그대로 막힌다.
    for (const placeCount of [0, 1, 4, 8]) expect(categoryStand(placeCount)).toBe('short');
  });
});
