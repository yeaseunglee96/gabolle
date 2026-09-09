package com.gabolle.backend.place.domain;

/**
 * 대조표 왼쪽이 취향인가 제약인가. {@code user_place_code_map.user_input_kind} 의 두 값이다.
 *
 * <p>취향은 점수에 반영되고 제약은 후보를 거른다. 같은 표에 있어도 계산이 다르므로 나눠 둔다.
 */
public enum UserInputKind {

	/** 취향 여덟 차원. {@code preference_answer.dimension} 과 같은 코드다. */
	PREFERENCE,

	/** 제약 세 종류. {@code constraint_answer.constraint_type} 과 같은 코드다. */
	CONSTRAINT
}
