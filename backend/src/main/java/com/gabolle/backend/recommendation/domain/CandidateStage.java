package com.gabolle.backend.recommendation.domain;

/** 후보가 파이프라인의 어디까지 갔다가 멈췄는지. */
public enum CandidateStage {
	/** 생성만 됐다. */
	GENERATED,
	/** 품질 기준(데이터 결측·신뢰도 등)에서 걸러졌다. */
	QUALITY_FILTERED,
	/** 하드 제약 위반으로 걸러졌다. */
	HARD_FILTERED,
	/** 점수를 받고 순위가 매겨졌다. */
	RANKED,
	/** 재정렬까지 거쳤다. */
	RERANKED,
	/** 실제로 응답에 담겨 나갔다. */
	RETURNED
}
