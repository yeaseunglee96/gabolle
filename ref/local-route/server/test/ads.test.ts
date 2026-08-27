import test from "node:test";
import assert from "node:assert/strict";
import { rankEligibleAds } from "../src/services/ads.js";
import { scorePlaces } from "../src/services/recommend.js";
import { place } from "./fixtures.js";

test("광고 입찰가는 광고 후보 안에서만 순위를 바꾸고 자연 추천은 바꾸지 않는다", () => {
  const organicPlaces = [place({ id: "organic-local", localScore: 0.9 }), place({ id: "organic-landmark", localScore: 0.3, tasteTags: '["landmark"]' })];
  const options = { mode: "LOCAL" as const, needsEnglishMenu: false, needsForeignCard: false };
  const before = scorePlaces(organicPlaces, ["hidden_local"], 100000, 1, [], options).map((item) => item.id);
  const ads = rankEligibleAds([
    { id: "low", name: "숙박", serviceCategory: "LODGING", bidCpc: 100, budget: 10000, spent: 0, targetingModes: "[]", targetingLanguage: null, place: { id: "organic-local", nameKo: "로컬", nameEn: null, category: "CAFE", localScore: 0.9, imageUrl: null } },
    { id: "high", name: "택시", serviceCategory: "TAXI", bidCpc: 100000, budget: 200000, spent: 0, targetingModes: "[]", targetingLanguage: null, place: { id: "organic-landmark", nameKo: "광고", nameEn: null, category: "CAFE", localScore: 0.31, imageUrl: null } },
  ], {});
  const after = scorePlaces(organicPlaces, ["hidden_local"], 100000, 1, [], options).map((item) => item.id);
  assert.equal(ads[0].id, "high");
  assert.deepEqual(after, before);
});

test("음식점·카페·기념품샵 광고는 화이트리스트에서 제외한다", () => {
  const ads = rankEligibleAds([{ id: "blocked", name: "맛집", serviceCategory: "RESTAURANT", bidCpc: 99999, budget: 100000, spent: 0, targetingModes: "[]", targetingLanguage: null, place: { id: "p", nameKo: "맛집", nameEn: null, category: "RESTAURANT", localScore: 1, imageUrl: null } }], {});
  assert.equal(ads.length, 0);
});
