package com.gabolle.backend.preference.domain;

/**
 * 취향 차원.
 *
 * <p>이 목록은 DB CHECK 세 곳({@code preference_answer.dimension} ·
 * {@code user_place_code_map.user_input_code} · {@code user_taste_weight.dimension})에도 같이
 * 적혀 있다. 어긋나면 대조표에 없는 코드를 가리키게 되므로, 차원을 추가할 때 넷을 같이 고친다.
 *
 * <p>자바에만 두지 않는 것은 분석 쿼리가 자바를 안 지나가기 때문이다 — DB 가 직접 막지 않으면
 * 대소문자만 다른 값이 같이 들어와 다른 것이 된다.
 */
public enum TasteDimension {

	/** 장소 갈래 — 카페·전시·시장 등. */
	CATEGORY,

	/** 분위기 — 활기·차분함 등. */
	ATMOSPHERE,

	/** 현지스러움 — 관광지와 동네 사이 어디를 좋아하는가. */
	LOCALITY,

	/** 조용함. */
	QUIETNESS,

	/** 유명 관광지를 선호하는 정도. */
	TOURIST_PREFERENCE,

	/** 음식 취향. */
	FOOD_PREFERENCE,

	/** 경사 — 오르막을 얼마나 감수하는가. 빅데이터 파트의 경사 계산과 짝이다. */
	SLOPE_PREFERENCE,

	/** 그늘 — 여름 부산에서 그늘진 길을 얼마나 원하는가. */
	SHADE_PREFERENCE,

	/**
	 * 씀씀이 성향. 가격대를 나타내는 {@code place_feature_type} 이 온톨로지에 아직 없어서
	 * {@code user_place_code_map} 에 대조 행이 없고, 짝이 없으니 채점기도 이 차원의 점수를
	 * 구하지 않는다. 그동안 {@code PlaceFeatureCodeMapTest} 의 {@code UNPAIRED_BY_DESIGN} 에 있다.
	 */
	SPEND_PROFILE
}
