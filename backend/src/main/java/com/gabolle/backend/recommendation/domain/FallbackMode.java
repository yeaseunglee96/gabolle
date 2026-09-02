package com.gabolle.backend.recommendation.domain;

/** 이 결과를 무엇이 만들었는가. */
public enum FallbackMode {
	/** 학습 모델이 순위를 매겼다. */
	MODEL,
	/** 규칙 기반 점수가 순위를 매겼다. */
	RULE,
	/** 모델·규칙을 쓰지 못해 최소 기준으로 대체했다. */
	BASELINE
}
