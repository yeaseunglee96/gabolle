import assert from "node:assert/strict";
import test from "node:test";
import { diversifyScoredPlaces, estimatePlaceCost, kakaoRestaurantReviewSignal, scorePlaces } from "../src/services/recommend.js";
import { place, scored } from "./fixtures.js";

const options = {
  mode: "LOCAL" as const,
  needsEnglishMenu: false,
  needsForeignCard: false,
};

test("가격대와 인원수로 예상 비용을 계산한다", () => {
  assert.equal(estimatePlaceCost(1, 2), 0);
  assert.equal(estimatePlaceCost(3, 2), 40_000);
});

test("영어 메뉴·해외카드 필수 조건과 제외 목록을 적용한다", () => {
  const candidates = [
    place({ id: "ok" }),
    place({ id: "no-en", hasEnglishMenu: false }),
    place({ id: "no-card", foreignCardPayment: false }),
    place({ id: "excluded" }),
  ];
  const result = scorePlaces(candidates, ["cafe"], 100_000, 1, ["excluded"], {
    ...options,
    needsEnglishMenu: true,
    needsForeignCard: true,
  });
  assert.deepEqual(result.map((item) => item.id), ["ok"]);
});

test("추천 모드에 따라 장소 순위가 달라진다", () => {
  const landmark = place({ id: "landmark", localScore: 0.2, tasteTags: JSON.stringify(["landmark"]) });
  const local = place({ id: "local", localScore: 1, tasteTags: JSON.stringify(["hidden_local"]) });
  const essential = scorePlaces([landmark, local], [], 100_000, 1, [], { ...options, mode: "ESSENTIAL" });
  const localMode = scorePlaces([landmark, local], [], 100_000, 1, [], options);
  assert.equal(essential[0].id, "landmark");
  assert.equal(localMode[0].id, "local");
});

test("선택한 여행 취향이 식당의 일반 로컬 점수보다 우선 반영된다", () => {
  const natureExperience = place({ id: "nature", category: "TOURIST", localScore: 0.78, tasteTags: JSON.stringify(["nature", "relax", "photo"]) });
  const unrelatedRestaurant = place({ id: "food", category: "RESTAURANT", localScore: 0.96, tasteTags: JSON.stringify(["food", "hidden_local"]) });
  const result = scorePlaces([unrelatedRestaurant, natureExperience], ["nature", "relax"], 100_000, 1, [], options);
  assert.equal(result[0].id, "nature");
});

test("식당은 검증된 카카오 평점과 후기 신뢰도를 추천에 반영한다", () => {
  const reviewed = place({
    id: "reviewed", category: "RESTAURANT", kakaoRating: 4.7, kakaoReviewCount: 320,
    kakaoPositiveReviewRate: 0.92, kakaoReviewSource: "LICENSED_IMPORT", kakaoReviewKeywords: JSON.stringify(["친절해요", "재료가 신선해요"]),
  });
  const unreviewed = place({ id: "unreviewed", category: "RESTAURANT" });
  const result = scorePlaces([unreviewed, reviewed], [], 100_000, 1, [], options);
  assert.equal(result[0].id, "reviewed");
  assert.match(result[0].kakaoReviewKeywords.join(" "), /친절/);
});

test("후기 수가 적은 고평점은 베이지안 보정하고 미승인 출처는 무시한다", () => {
  const oneReview = place({ category: "RESTAURANT", kakaoRating: 5, kakaoReviewCount: 1, kakaoReviewSource: "MANUAL_VERIFIED" });
  const manyReviews = place({ category: "RESTAURANT", kakaoRating: 4.6, kakaoReviewCount: 500, kakaoReviewSource: "LICENSED_IMPORT" });
  const unapproved = place({ category: "RESTAURANT", kakaoRating: 5, kakaoReviewCount: 999, kakaoReviewSource: "SCRAPED" });
  assert.ok(kakaoRestaurantReviewSignal(manyReviews)! > kakaoRestaurantReviewSignal(oneReview)!);
  assert.equal(kakaoRestaurantReviewSignal(unapproved), null);
});

test("최근 일정에서 반복된 장소를 쉬게 하고 비슷한 품질의 새 후보를 올린다", () => {
  const candidates = Array.from({ length: 12 }, (_, index) => scored({ id: `place-${index}`, score: 0.9 - index * 0.01 }));
  const usage = new Map(candidates.slice(0, 6).map((candidate) => [candidate.id, 5]));
  const result = diversifyScoredPlaces(candidates, usage, "variation-1");
  assert.ok(result.slice(0, 4).every((candidate) => !usage.has(candidate.id)));
});

test("고정 장소는 다양성 cooldown 대상에서 제외한다", () => {
  const candidates = [scored({ id: "pinned", score: 0.9 }), scored({ id: "new", score: 0.86 })];
  const result = diversifyScoredPlaces(candidates, new Map([["pinned", 20]]), "variation-2", ["pinned"]);
  assert.equal(result[0].id, "pinned");
});
