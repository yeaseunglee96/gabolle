package com.gabolle.backend.recommendation.domain;

/**
 * 이 결과가 무엇을 재료로 만들어졌는가. {@link FallbackMode} 와 다른 질문에 답한다 — 이쪽은
 * 무엇을 보여줬는지(개인화 추천인가 편집자가 고른 목록인가)를, 저쪽은 왜 그것을
 * 보여줬는지(무엇이 순위를 매겼는지)를 말한다.
 *
 * 둘을 한 칸으로 합칠 수 없는 것은 같은 Pick 이 정상 경로일 수도 사고일 수도 있기 때문이다.
 * 신규 계정에게 Pick 을 주는 것은 정상이고 엔진이 죽어서 Pick 을 준 것은 장애인데, 화면에
 * 나간 것은 같다. 한 칸만 남기면 장애율을 재거나 Pick 을 본 사람 수를 세는 일 중 하나를
 * 못 하게 된다.
 */
public enum SourceMode {

	/** 이 사용자의 취향·제약으로 후보를 만들고 점수를 매겼다. */
	PERSONALIZED,

	/**
	 * 편집자가 미리 고른 목록({@code editorial_pick})을 그대로 보여줬다. 순위는 편집자가
	 * 정한 {@code pick_rank} 이고 점수로 다시 매기지 않지만, 하드 제약은 그대로 적용된다 —
	 * 같은 {@code CandidateAssembler} 를 지나므로 그 판정이 걸린다.
	 */
	EDITORIAL_PICK
}
