package com.gabolle.backend.recommendation.domain;

/**
 * 후보 하나에 대한 하드 제약 판정. {@link #UNKNOWN} 을 {@link #PASS} 로 바꾸지 않는다 —
 * 둘을 섞으면 알레르기·휠체어 같은 안전 제약이 조용히 무력화된다.
 */
public enum ConstraintVerdict {
	/** 하드 제약을 모두 만족한다. */
	PASS,
	/** 하드 제약을 하나 이상 위반한다. 어떤 경로로도 노출되지 않는다. */
	FAIL,
	/** 판정에 필요한 사실을 확인할 수 없었다. */
	UNKNOWN
}
