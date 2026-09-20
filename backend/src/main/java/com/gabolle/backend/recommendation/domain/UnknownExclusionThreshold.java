package com.gabolle.backend.recommendation.domain;

/**
 * 미확인({@link ConstraintVerdict#UNKNOWN})인 제약이 어느 등급 이상일 때 그 후보를 결과에서
 * 뺄 것인가. 데이터가 채워지는 만큼 기준선을 내리면 되고 그때 코드는 건드리지 않는다.
 *
 * 어느 값을 고르든 판정 자체는 UNKNOWN 그대로 저장되고 후보 행도 사라지지 않는다 — 이 값이
 * 정하는 것은 결과에 담을 것인가 하나다.
 */
public enum UnknownExclusionThreshold {

	/**
	 * 기본값. 안전 제약(REQUIRED)이 미확인인 후보만 뺀다. 그 아래 등급은 경고를 달고
	 * 내보낸다 — 사용자가 행동으로 옮길 수 없는 경고는 쌓이면 모든 경고를 무시하게 만들어서,
	 * 안전 제약만은 경고가 아니라 제외로 다룬다.
	 */
	REQUIRED(ConstraintSeverity.REQUIRED),

	/** 안전 제약과 이동·분위기 선호까지 미확인이면 뺀다. */
	PREFERRED(ConstraintSeverity.PREFERRED),

	/** 등급을 가리지 않고 미확인이면 전부 뺀다. 후보가 크게 줄어든다. */
	OPTIONAL(ConstraintSeverity.OPTIONAL),

	/** 아무것도 빼지 않는다. 미확인은 전부 경고만 달고 나간다. */
	NONE(null);

	private final ConstraintSeverity floor;

	UnknownExclusionThreshold(ConstraintSeverity floor) {
		this.floor = floor;
	}

	/** 이 등급의 미확인 제약을 가진 후보를 결과에서 빼야 하는가. */
	public boolean excludes(ConstraintSeverity severity) {
		if (this.floor == null || severity == null) {
			return false;
		}
		return severity.rank() >= this.floor.rank();
	}
}
