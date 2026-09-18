/**
 * 취향을 심을 축 — **장소에 실제로 값이 붙어 있고, 그 값이 장소마다 다른 것만.**
 *
 * ═══════════════════════════════════════════════════════════════════════════
 * 🔴 이 파일은 두 번 틀렸다. 두 번 다 "재는 곳" 을 잘못 봐서다
 * ═══════════════════════════════════════════════════════════════════════════
 *
 * **첫 판** — 점수형 다섯에 취향을 심고 CATEGORY 를 뺐다. 근거는 개발 DB 와
 * `SbizPlaceLoader` 를 읽은 것. 개발 DB 는 장소가 0곳이었다.
 *
 * **둘째 판** — 배포 `GET /api/v1/places/facets` 가 여섯 축을 0으로 내길래 그 여섯을
 * 전부 뺐다. 그중 `SLOPE_PREFERENCE` 는 **틀렸다.**
 *
 * **셋째 판(지금)** — 배포 DB 를 직접 봤다.
 *
 * ```
 * place_feature 실측 (app_db, 2026-09-17)
 *   CATEGORY_TAG    2694   feature_key 있음
 *   SLOPE_PERCENT   2682   feature_key NULL   ← facets 가 0 으로 세던 것
 *   CUISINE_TAG      873   feature_key 있음
 *   INTEREST_TAG     106   feature_key 있음
 * ```
 *
 * 🔴 **facets 엔드포인트는 점수형 축을 셀 수 없다.** `FacetKeyCount` 가 `featureKey` 로
 *    묶는데, `SCORE_COMPARE` 피처는 `feature_key` 가 **NULL** 이고 값이 JSON 안에 있다
 *    (`{"score": 26.6, "radiusM": 200, "walkLengthM": 4636}`). 그래서 **아무리 꽉 차
 *    있어도 0 으로 나온다.** 데이터가 없는 것과 못 세는 것이 같은 0 으로 보인다.
 *
 * 🔴 그리고 경사는 **실제로 순위에 들어가고 있다** — 채점 결과 실측:
 *
 * ```
 *   2026-09-17   후보 276개 중 274개가 slopePercent 값을 가짐
 *   2026-09-16   1816개 중 597개      (이날 적재됨)
 *   2026-09-15   1062개 중 0개
 * ```
 *
 * **교훈: 축이 살아 있는지는 facets 가 아니라 `place_feature` 로 확인한다.**
 */

/**
 * 태그형 — 겹치면 점수가 붙는다.
 *
 * 🔴 `CUISINE_TAG` 가 태그 쪽에서 제대로 변별하는 유일한 축이다(873곳, 값이 갈린다).
 * 🔴 `CATEGORY_TAG` 는 2694곳으로 수는 많지만 대부분 `FOOD` 이고 그건 전부에 붙으므로
 *    변별력이 없다. 실질은 `CAFE_HEALING` 하나다 — S15P21E201-1108.
 */
export const TAG_AXES = [
	{ key: "CUISINE_TAG", label: "음식", dimension: "FOOD_PREFERENCE", placeCount: 873,
	  values: ["MILMYEON", "PORK_SOUP", "SEAFOOD"] },
	{ key: "CATEGORY_TAG", label: "갈래", dimension: "CATEGORY", placeCount: 2694,
	  values: ["CAFE_HEALING", "FOOD"],
	  caveat: "FOOD 는 전부에 붙어 변별력이 없다. 실질은 CAFE_HEALING 하나" },
];

/**
 * 점수형 — 사용자의 선호값(0~1)과 장소의 값을 견준다.
 *
 * 🔴 지금 쓸 수 있는 것은 **경사 하나**다. 나머지 넷은 `place_feature` 에 행이 아예 없다.
 *    `scale` 은 장소 값의 단위를 0~1 로 맞추는 나눗수다 — 경사는 퍼센트(0~100)로 들어온다
 *    (채점기 `applyAlignmentDimension` 의 `slopePercent` 처리와 같다).
 */
export const SCORE_AXES = [
	{ key: "slopePercent", label: "경사", dimension: "SLOPE_PREFERENCE", featureType: "SLOPE_PERCENT",
	  placeCount: 2682, scale: 100 },
];

/**
 * 🔴 왜 뺐는지를 남긴다. 안 적으면 다음 사람이 빠뜨린 줄 알고 조용히 다시 넣는다.
 *
 * 🔴 근거를 **facets 가 아니라 place_feature** 로 적는다. facets 0 은 "없다" 가 아니라
 *    "그 엔드포인트가 못 센다" 일 수 있다 — 경사가 정확히 그랬다.
 */
export const EXCLUDED = {
	ATMOSPHERE: "place_feature 에 ATMOSPHERE_TAG 행이 0 (2026-09-17 배포 DB 직접 확인)",
	LOCALITY: "place_feature 에 LOCALITY_SCORE 행이 0. bigData/process 산출물이 안 들어갔다",
	QUIETNESS: "place_feature 에 QUIETNESS_SCORE 행이 0",
	SHADE_PREFERENCE: "place_feature 에 SHADE_SCORE 행이 0. shade.mjs·shadow.mjs 는 있다",
	TOURIST_PREFERENCE: "place_feature 에 TOURIST_RATIO 행이 0",
	POPULARITY_SCORE: "사용자가 고르는 화면이 없다. 취향이 아니라 장소의 속성이다",
	distance: "취향이 아니다. 오히려 취향 신호를 덮는 쪽이라 이 실험의 대조군이다",
};

/** 실측 출처 — 숫자를 옮겨 적을 때 어디서 왔는지가 같이 가야 한다. */
export const FACET_SOURCE = {
	measuredAt: "2026-09-17",
	how: "배포 DB(app_db) 의 gabolle.place_feature 를 직접 집계",
	note: "GET /api/v1/places/facets 는 점수형 축을 0 으로 낸다 — featureKey 로 묶기 때문이다. 축 생사 판단에 쓰지 않는다",
	priorReading: "같은 날 facets 로는 SLOPE_PREFERENCE 가 0 이었다(진미리 님 실측). 엔드포인트의 한계였고 데이터는 2682곳에 있었다",
};
