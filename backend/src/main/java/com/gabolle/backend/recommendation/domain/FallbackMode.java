package com.gabolle.backend.recommendation.domain;

/**
 * 이 결과를 무엇이 만들었는가.
 *
 * <p>🔴 <b>{@link SourceMode} 와 다른 질문에 답한다.</b> 이 열거형은 <b>왜</b> 그것을
 * 보여줬는지(무엇이 순위를 매겼는지)를 말하고, {@code SourceMode} 는 <b>무엇을</b>
 * 보여줬는지(개인화 추천인가 편집자가 고른 목록인가)를 말한다. 둘을 한 칸으로 합치면
 * 답할 수 없는 것이 생긴다 — 자세한 것은 {@link SourceMode} javadoc.
 */
public enum FallbackMode {
	/** 학습 모델이 순위를 매겼다. */
	MODEL,
	/** 규칙 기반 점수가 순위를 매겼다. */
	RULE,
	/** 모델·규칙을 쓰지 못해 최소 기준으로 대체했다. */
	BASELINE,
	/**
	 * 편집자가 미리 고른 목록({@code editorial_pick})으로 대체했다 (S15P21E201-555).
	 *
	 * <p>🔴 이 값이 붙었다는 것은 <b>순위를 우리가 매기지 않았다</b>는 뜻이다 — 순서는
	 * {@code editorial_pick_place.pick_rank} 에 사람이 적어 둔 것이다.
	 *
	 * <p>🔴 <b>이 값만 보고 "장애" 라고 읽으면 틀린다.</b> 신규 계정에게 Pick 을 주는 것은
	 * 정상 경로다 — 그 사용자에 대해 아는 것이 없으니 그것이 맞는 답이다. 장애로 인한
	 * 대체와 가르는 것은 {@code fallbackReason} 이다({@code COLD_START_NO_PREFERENCE} 대
	 * {@code ENGINE_UNAVAILABLE} 등).
	 */
	EDITORIAL_PICK
}
