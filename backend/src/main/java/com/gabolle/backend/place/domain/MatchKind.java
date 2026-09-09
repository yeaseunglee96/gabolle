package com.gabolle.backend.place.domain;

/**
 * 사용자 입력과 장소 피처를 <b>어떻게</b> 맞추는가. {@code user_place_code_map.match_kind} 의 네 값이다.
 *
 * <p>🔴 여기만 enum 인 이유가 있다. {@code feature_type} 과 {@code feature_key} 는 값이 늘어도
 * 코드가 그대로여야 하지만(-473 의 완료 기준), match_kind 가 늘어난다는 것은 <b>새 비교 알고리즘이
 * 생긴다</b>는 뜻이라 어차피 코드가 필요하다. 데이터로 늘어나는 것은 String, 동작이 바뀌는 것은 enum.
 */
public enum MatchKind {

	/** 태그가 겹치는가. 태그형 피처(feature_key 가 있는 것)에 쓴다. */
	TAG_OVERLAP,

	/** 점수를 선호와 비교한다. 점수형 피처에 쓴다. */
	SCORE_COMPARE,

	/** 참거짓을 비교한다. {@code STAIRS_PRESENT} 같은 것. */
	FLAG_COMPARE,

	/**
	 * 🔴 위반이면 후보에서 제거한다. 알레르기·필수 식단·검증된 접근 불가가 여기다.
	 * 점수로 상쇄되지 않는다 (FR-REC-02). 정보가 없으면 통과가 아니라 UNKNOWN 이다.
	 */
	HARD_FILTER
}
