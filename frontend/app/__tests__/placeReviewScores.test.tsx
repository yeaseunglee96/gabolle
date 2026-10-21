// 장소 리뷰 목록 — 매긴 점수를 보여 준다(S15P21E201-1973).
//
// 구포시장 리뷰는 네 항목 모두 5점인데 본문이 없어, 목록에 「미인증」 배지만 있는 빈 칸으로 보였다.
jest.mock('expo-router', () => ({ useLocalSearchParams: () => ({}), useRouter: () => ({ push: jest.fn(), back: jest.fn() }) }));
jest.mock('expo-location', () => ({}));

import { reviewScoreLabels } from '../place-reviews/[id]';

const ko = (k: string) => k;
const en = (_k: string, e: string) => e;

describe('reviewScoreLabels', () => {
  it('🔴 점수만 있는 리뷰도 매긴 항목을 보여 준다', () => {
    expect(reviewScoreLabels({ foodScore: 5, priceScore: 5, accessibilityScore: 5, onsiteScore: 5 }, ko)).toEqual([
      '음식 · 좋아요', '가격 · 좋아요', '접근성 · 좋아요', '현장 이용 편의 · 좋아요',
    ]);
  });

  it('매기지 않은 항목은 빼고, 1·3·5 를 별로·보통·좋아요로 읽는다', () => {
    expect(reviewScoreLabels({ foodScore: 1, priceScore: null, accessibilityScore: 3, onsiteScore: null }, en)).toEqual([
      'Food · Not great', 'Accessibility · Okay',
    ]);
  });

  it('아무것도 안 매겼으면 빈 목록', () => {
    expect(reviewScoreLabels({ foodScore: null, priceScore: null, accessibilityScore: null, onsiteScore: null }, ko)).toEqual([]);
  });
});
