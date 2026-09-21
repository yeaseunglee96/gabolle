package com.gabolle.backend.recommendation.domain;

/**
 * 이 결과를 무엇이 순위 매겼는가. {@link SourceMode} 와 다른 질문에 답한다 — 이 열거형은 왜
 * 그것을 보여줬는지를, {@code SourceMode} 는 무엇을 보여줬는지를 말한다.
 */
public enum FallbackMode {
	/** 학습 모델이 순위를 매겼다. */
	MODEL,
	/** 규칙 기반 점수가 순위를 매겼다. */
	RULE,
	/** 모델·규칙을 쓰지 못해 최소 기준으로 대체했다. */
	BASELINE,
	/**
	 * 편집자가 미리 고른 목록({@code editorial_pick})으로 대체했다. 순서는
	 * {@code editorial_pick_place.pick_rank} 에 사람이 적어 둔 것이다.
	 *
	 * 이 값만 보고 장애라고 읽으면 틀린다 — 신규 계정에게 Pick 을 주는 것은 정상 경로이고,
	 * 장애로 인한 대체와 가르는 것은 {@code fallbackReason} 이다.
	 */
	EDITORIAL_PICK
}
