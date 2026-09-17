/**
 * 취향을 심을 축 — **장소에 실제로 값이 붙어 있고, 그 값이 장소마다 다른 것만.**
 *
 * 🔴 이 목록은 추측이 아니라 **배포 서버 실측**으로 정한다. 2026-09-17 진미리 님이
 *    `GET /api/v1/places/facets` 를 익명 세션으로 찔러 얻은 수다.
 *
 *      CATEGORY            2694
 *      FOOD_PREFERENCE      873
 *      EXPLORE              106
 *      ATMOSPHERE             0   🔴
 *      LOCALITY               0   🔴
 *      QUIETNESS              0   🔴
 *      SHADE_PREFERENCE       0   🔴
 *      SLOPE_PREFERENCE       0   🔴
 *      TOURIST_PREFERENCE     0   🔴
 *
 *    **아홉 축 중 여섯이 0곳이다.** 하필 그 여섯이 「초개인화」라고 부르던 축들이다 —
 *    그늘·경사·조용함·로컬·관광객 비율. 계산하는 코드는 `bigData/process/` 에 다 있는데
 *    산출물이 `place_feature` 로 안 들어갔다.
 *
 * 🔴 <b>그래서 이 파일의 첫 판이 정확히 거꾸로였다.</b> 처음에는 점수형 다섯에 취향을
 *    심고 CATEGORY 를 뺐다. 값이 0인 축에 취향을 심으면 가상 사용자가 무엇을 좋아하든
 *    맞출 장소가 하나도 없어서, **시뮬레이터는 아무것도 못 재면서 초록으로 돈다.**
 *    이 저장소가 오늘 하루 종일 부딪힌 그 모양이다.
 */

/**
 * 태그형 — 겹치면 점수가 붙는다.
 *
 * 🔴 `CUISINE_TAG` 가 지금 **유일하게 제대로 변별하는 축**이다. 873곳에 붙어 있고
 *    값도 갈린다(밀면·돼지국밥·해산물…).
 *
 * 🔴 `CATEGORY_TAG` 는 2694곳으로 수는 제일 많지만 대부분 `FOOD` 이고, 그건 모든
 *    장소에 붙으므로 **변별력이 없다**. 실제로 가르는 값은 `CAFE_HEALING` 하나다
 *    (S15P21E201-1108). 그래서 넣되 기대를 걸지 않는다 — 시뮬레이터가 「카페인가
 *    아닌가」 하나로 사람을 가르는 셈이 된다.
 */
export const TAG_AXES = [
	{ key: "CUISINE_TAG", label: "음식", dimension: "FOOD_PREFERENCE", placeCount: 873,
	  values: ["MILMYEON", "PORK_SOUP", "SEAFOOD"] },
	{ key: "CATEGORY_TAG", label: "갈래", dimension: "CATEGORY", placeCount: 2694,
	  values: ["CAFE_HEALING", "FOOD"],
	  caveat: "FOOD 는 전부에 붙어 변별력이 없다. 실질은 CAFE_HEALING 하나" },
];

/**
 * 점수형 — **지금은 하나도 못 쓴다.**
 *
 * 비워 두는 것이 이 배열의 내용이다. 여기에 축을 넣고 싶어지면 먼저
 * `/api/v1/places/facets` 로 placeCount 가 0이 아닌지 확인한다.
 */
export const SCORE_AXES = [];

/** 🔴 왜 뺐는지를 남긴다. 안 적으면 다음 사람이 빠뜨린 줄 알고 조용히 다시 넣는다. */
export const EXCLUDED = {
	LOCALITY: "배포 실측 0곳 (2026-09-17). bigData/process 산출물이 place_feature 로 안 들어갔다",
	QUIETNESS: "배포 실측 0곳 (2026-09-17)",
	TOURIST_PREFERENCE: "배포 실측 0곳 (2026-09-17)",
	SHADE_PREFERENCE: "배포 실측 0곳 (2026-09-17). shade.mjs·shadow.mjs 는 있다",
	SLOPE_PREFERENCE: "배포 실측 0곳 (2026-09-17). slope.mjs·calibrate-slope.mjs 는 있다",
	ATMOSPHERE: "배포 실측 0곳 (2026-09-17)",
	POPULARITY_SCORE: "사용자가 고르는 화면이 없다. 취향이 아니라 장소의 속성이다",
	distance: "취향이 아니다. 오히려 취향 신호를 덮는 쪽이라 이 실험의 대조군이다",
};

/** 실측 출처 — 숫자를 옮겨 적을 때 어디서 왔는지가 같이 가야 한다. */
export const FACET_SOURCE = {
	measuredAt: "2026-09-17",
	by: "jinmiri",
	how: "배포 서버 j15e201 · 익명 세션 · GET /api/v1/places/facets",
};
