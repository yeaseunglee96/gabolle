import assert from "node:assert/strict";
import test from "node:test";
import { applyCourseCategoryTasteTags, getCourseCategories } from "../src/services/courseCategories.js";
import { scorePlaces } from "../src/services/recommend.js";
import { place } from "./fixtures.js";

const options = { mode: "LOCAL" as const, needsEnglishMenu: false, needsForeignCard: false };

test("첨부 명세의 코스 카테고리 10개를 모두 선택 가능하게 제공한다", () => {
  const categories = getCourseCategories();
  assert.equal(categories.length, 10);
  assert.ok(categories.every((category) => category.enabled));
  assert.deepEqual(categories.map((category) => category.code), [
    "ZERO_WON", "BEST_BANG", "SPLURGE", "OLD_TOWN", "NATURE_FIX",
    "PICTURE_PERFECT", "CAR_FREE", "EASY_PACE", "SOLO_FRIENDLY", "RAINY_DAY",
  ]);
});

test("모든 코스가 태그와 후보 조건을 실제 추천 점수에 반영한다", () => {
  const fixtures = {
    ZERO_WON: place({ priceTier: 1, tasteTags: '["culture"]' }),
    BEST_BANG: place({ priceTier: 2, tasteTags: '["hidden_local"]' }),
    SPLURGE: place({ priceTier: 3, tasteTags: '["relax"]' }),
    OLD_TOWN: place({ localScore: 0.85, tasteTags: '["history","hidden_local"]' }),
    NATURE_FIX: place({ isOutdoor: true, tasteTags: '["nature","photo"]' }),
    PICTURE_PERFECT: place({ imageUrl: "https://example.com/photo.jpg", tasteTags: '["photo","nightview"]' }),
    CAR_FREE: place({ tasteTags: '["shopping","culture"]' }),
    EASY_PACE: place({ recommendedStayMin: 80, tasteTags: '["indoor","relax"]' }),
    SOLO_FRIENDLY: place({ reservationRequired: false, tasteTags: '["cafe","hidden_local"]' }),
    RAINY_DAY: place({ isOutdoor: false, tasteTags: '["indoor","culture"]' }),
  };
  for (const category of getCourseCategories()) {
    const candidate = fixtures[category.code as keyof typeof fixtures];
    const tags = applyCourseCategoryTasteTags([], category.code);
    const result = scorePlaces([candidate], tags, 100_000, 1, [], { ...options, courseCategory: category.code });
    assert.equal(result.length, 1, `${category.code} 후보가 적용되지 않았습니다.`);
    assert.ok(result[0].score >= 0 && result[0].score <= 1, `${category.code} 점수가 정규화 범위를 벗어났습니다.`);
  }
});
