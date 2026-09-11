package com.gabolle.backend.preference.domain;

/**
 * 취향 차원 여덟. 수집 명세(S15P21E201-542) 4.3 이 M1 에서 확정한 목록이다.
 *
 * <p>🔴 <b>이 목록은 세 곳에 같이 적혀 있고, 셋이 어긋나면 조용히 깨진다.</b>
 * <ul>
 * <li>{@code preference_answer.dimension} 의 CHECK — 사람이 고른 답</li>
 * <li>{@code user_place_code_map.user_input_code} 의 CHECK — 장소 피처와 잇는 대조표</li>
 * <li>{@code user_taste_weight.dimension} 의 CHECK — 접어 둔 벡터(이 열거형)</li>
 * </ul>
 * 어긋나면 대조표에 없는 코드를 가리키게 되고, 그건 대조표가 아니라 오해의 근원이 된다.
 * 차원을 추가할 때는 <b>세 곳을 같은 MR 에서</b> 고친다.
 *
 * <p>🔴 값을 여기 자바에만 두지 않는 이유 — 분석 쿼리는 자바를 안 지나간다. DB 가 직접
 * 막지 않으면 {@code 'atmosphere'} 와 {@code 'ATMOSPHERE'} 가 같이 들어와 다른 것이 된다.
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
	 * 씀씀이 성향 — S15P21E201-709.
	 *
	 * <p>🔴 아직 {@code user_place_code_map} 에 실제 대조 행이 없다. 씀씀이(가격대)를 나타내는
	 * {@code place_feature_type} 이 온톨로지에 아직 없어서다 — 짝지을 종류를 정하는 것은 place
	 * 담당의 결정이라 여기서 지어내지 않는다. 그때까지 {@code PlaceFeatureCodeMapTest} 의
	 * {@code UNPAIRED_BY_DESIGN} 목록에 있다. 같은 이유로 {@code PreferenceAlignmentWeights}
	 * 에도 없다 — 짝이 없으면 채점기가 애초에 이 차원의 점수를 구할 일이 없다.
	 */
	SPEND_PROFILE
}
