package com.gabolle.backend.editorial.domain;

/**
 * 이 Pick 을 누구에게 보여줄 수 있는가.
 *
 * <p>값 이름이 {@code editorial_pick} 의 {@code ck_editorial_pick_scope} 와 같아야 한다.
 * 한쪽만 바뀌면 저장은 되는데 조회가 빈 결과를 내고 아무 오류도 안 난다.
 */
public enum EditorialPickScope {

	/**
	 * 누구에게나 보여줄 수 있다. 신규 계정과 fallback 요청이 받는 것이 이것이다 —
	 * 그 두 경우에는 사용자에 대해 아는 것이 없어서 지역으로 고를 근거가 없다.
	 */
	GLOBAL,

	/**
	 * 지역이 맞는 사람에게만 의미가 있다. {@code localityCode} 가 반드시 있다
	 * (스키마의 {@code ck_editorial_pick_scope_locality} 가 강제한다).
	 */
	LOCAL
}
