package com.gabolle.backend.recommendation.domain;

/**
 * 추천 파이프라인의 단계. 실패했을 때 어디서 멈췄는지를 가리키는 데도 쓴다.
 *
 * <p>GB-API-001 4.2 JobDto 의 {@code progress.stage} 로 나간다. 명세서가 값을 열거하지는
 * 않고 예시로 {@code "ROUTE_OPTIMIZATION"} 하나를 보여 주므로 그 이름에 맞춰 뒀다.
 */
public enum JobStage {
	CREATED,
	VERSION_RESOLUTION,
	CANDIDATE_GENERATION,
	CONSTRAINT_EVALUATION,
	FEATURE_LOOKUP,
	RANKING,
	ROUTE_OPTIMIZATION,
	PERSISTENCE,
	COMPLETED
}
