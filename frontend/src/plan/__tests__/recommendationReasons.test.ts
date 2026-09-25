// 추천 이유 문구 — S15P21E201-1640.
//
// 🔴 이 시험이 지키는 것: 서버가 새로 싣는 이유 코드가 「추천 조건 반영」으로 뭉개지지 않는다. 그리고
//    TOP_CONTRIBUTOR_<축> 은 뜻이 바뀌었다(백엔드 !1634) — 「절댓값이 가장 큰 축」이 아니라 「같은 결과 안에서
//    다른 곳보다 가장 두드러진 축」이다. 전에는 89% 가 「거리」였다.
import { reasonLabel } from '@/plan/recommendations';

describe('추천 이유 문구', () => {
  it('걷기만 고른 여행의 첫날 — 출발지에서 걸어갈 수 있는 곳', () => {
    expect(reasonLabel('WALK_ONLY_FIRST_DAY')).toBe('출발지에서 걸어갈 수 있어요');
  });

  it('🔴 가장 두드러진 축은 「다른 곳보다 ○○이 돋보임」 — 받침에 맞는 조사로', () => {
    expect(reasonLabel('TOP_CONTRIBUTOR_distance')).toBe('다른 곳보다 거리가 돋보임');
    expect(reasonLabel('TOP_CONTRIBUTOR_cuisine')).toBe('다른 곳보다 음식 취향이 돋보임');
    // 모르는 축도 서로 다른 글자로 보인다 — 전부 같은 안전장치 문구로 뭉개지 않는다
    expect(reasonLabel('TOP_CONTRIBUTOR_newAxis')).toBe('다른 곳보다 newAxis가 돋보임');
  });

  it('모르는 코드는 지금처럼 「추천 조건 반영」', () => {
    expect(reasonLabel('SOMETHING_NEW')).toBe('추천 조건 반영');
  });
});
