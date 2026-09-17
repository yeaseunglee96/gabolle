/**
 * 취향을 심을 축 — **장소마다 값이 실제로 다른 것만** 고른다.
 *
 * 🔴 `CATEGORY` 를 뺀 이유가 이 파일의 전부다. 온보딩 취향 여섯 중 장소에 실제로
 *    붙는 낱말은 `FOOD`(모든 장소에 붙어서 변별력이 0)와 `CAFE_HEALING` 둘뿐이다
 *    (S15P21E201-1108). 거기에 취향을 심으면 가상 사용자 전원이 사실상 같은 취향이
 *    되고, 시뮬레이션은 **아무것도 못 재면서 돌아간다.**
 *
 *    -1108 이 풀려서 네 낱말에 장소가 붙으면 그때 CATEGORY 를 여기 더한다.
 */

/** 점수형 — 장소가 0~1 값을 갖고, 사용자 선호와의 거리로 잰다. */
export const SCORE_AXES = [
	{ key: "localityScore", label: "로컬성" },
	{ key: "quietnessScore", label: "조용함" },
	{ key: "touristRatio", label: "관광객 비율" },
	{ key: "shadeScore", label: "그늘" },
	{ key: "slopePercent", label: "경사", scale: 100 },
];

/** 태그형 — 겹치면 점수가 붙는다. 실제 적재에서 값이 갈리는 둘만. */
export const TAG_AXES = [
	{ key: "ATMOSPHERE_TAG", label: "분위기" },
	{ key: "CUISINE_TAG", label: "음식" },
];

/**
 * 🔴 여기 없는 것도 기록해 둔다 — 왜 뺐는지를 남기지 않으면 다음 사람이
 *    "빠뜨렸나" 하고 되묻거나, 더 나쁘게는 조용히 다시 넣는다.
 */
export const EXCLUDED = {
	CATEGORY_TAG: "붙는 낱말이 FOOD(전부)·CAFE_HEALING 둘뿐이라 변별력이 없다 — S15P21E201-1108",
	POPULARITY_SCORE: "사용자가 고르는 화면이 없다. 취향이 아니라 장소의 속성이다",
	distance: "취향이 아니다. 오히려 취향 신호를 덮는 쪽이라 이 실험의 대조군이다",
};
