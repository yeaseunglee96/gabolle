package com.gabolle.backend.recommendation.domain;

/**
 * 미확인({@link ConstraintVerdict#UNKNOWN})인 제약이 <b>어느 등급 이상</b>일 때 그 후보를
 * 결과에서 뺄 것인가.
 *
 * <p>기준선 하나로 정책 전체가 표현된다. 데이터가 채워지는 만큼 기준선을 아래로 내리면 되고,
 * 그때 코드는 건드리지 않는다.
 *
 * <p>🔴 어느 값을 고르든 <b>판정 자체는 UNKNOWN 그대로 저장된다.</b> UNKNOWN 이 PASS 로
 * 바뀌는 일은 없고, 후보 행도 사라지지 않는다. 이 값이 정하는 것은 "결과에 담을 것인가" 하나다.
 */
public enum UnknownExclusionThreshold {

	/**
	 * 기본값. 안전 제약(REQUIRED)이 미확인인 후보만 뺀다. 그 아래 등급은 경고를 달고 내보낸다.
	 *
	 * <p>"확인 필요" 경고는 정보를 넘기는 것이 아니라 <b>책임을 넘기는 것</b>이다 — 사용자가
	 * 식당에 전화해 땅콩기름을 쓰는지 확인할 수는 없다. 행동으로 이어지지 않는 경고는 쌓이면
	 * 모든 경고를 무시하게 만든다. 그래서 안전 제약만은 경고가 아니라 제외로 다룬다.
	 */
	REQUIRED(ConstraintSeverity.REQUIRED),

	/** 안전 제약과 이동·분위기 선호까지 미확인이면 뺀다. */
	PREFERRED(ConstraintSeverity.PREFERRED),

	/** 등급을 가리지 않고 미확인이면 전부 뺀다. 후보가 크게 줄어든다. */
	OPTIONAL(ConstraintSeverity.OPTIONAL),

	/**
	 * 아무것도 빼지 않는다. 미확인은 전부 경고만 달고 나간다.
	 *
	 * <p>기능·화면 상세설계서 FR-REC-02 가 원래 적어 둔 방향이다 — 그 문서를 그대로 따르려면
	 * 이 값을 쓴다.
	 */
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
