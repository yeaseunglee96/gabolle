package com.gabolle.backend.trip.domain;

/**
 * 여행 조건 모달에 사람이 준 답. 「한 번도 안 물어봤다」는 이 enum 의 값이 아니라 행이 없는
 * 것이고, 응답에서 {@code status: null} 로 나간다.
 */
public enum TravelConstraintStatus {

	/** 「저장하고 시작」. 이 상태일 때만 값이 채워진다 — DB 제약이 그 짝을 강제한다. */
	SAVED,

	/** 「나중에」. 값이 없고, 「일정 물어보기」를 누를 때마다 다시 묻는다. */
	LATER,

	/** 「다시 묻지 않기」 — 값이 없다. 더 안 묻고, 마이페이지에서만 고친다. */
	NEVER
}
