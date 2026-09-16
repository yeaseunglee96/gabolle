import { categoryStand, MIN_CANDIDATES_PER_CATEGORY } from '../categoryStand';

// 후보가 모자란 갈래를 고르게 두면 여행 조건을 다 넣은 뒤에야 「조건을 만족하는 코스가
// 없어요」를 만난다 — S15P21E201-1005. 고르기 전에 가른다.
const MIN = MIN_CANDIDATES_PER_CATEGORY;

describe('갈래 후보가 충분한가', () => {
  it('엔진 최소치를 채우면 고를 수 있다', () => {
    expect(categoryStand(MIN)).toBe('enough');
    expect(categoryStand(MIN + 1)).toBe('enough');
  });

  it('최소치에 한 곳이라도 모자라면 못 고른다', () => {
    expect(categoryStand(MIN - 1)).toBe('short');
  });

  it('실측된 부족 갈래들 — 바다·자연·카페·도심', () => {
    // 2026-09-15 실측: 바다 2~4 · 자연 23 · 카페 26 · 도심 47.
    // 🔴 이 숫자를 코드가 아니라 서버 응답에서 받는다. 적재가 돌면 이 시험이 아니라
    // 화면이 저절로 풀린다 — 여기 적힌 숫자는 그때의 실측이지 기준이 아니다.
    for (const placeCount of [4, 23, 26, 47]) expect(categoryStand(placeCount)).toBe('short');
  });

  it('서버 응답에 아예 없는 갈래는 0곳으로 읽는다', () => {
    expect(categoryStand(undefined)).toBe('short');
  });
});
