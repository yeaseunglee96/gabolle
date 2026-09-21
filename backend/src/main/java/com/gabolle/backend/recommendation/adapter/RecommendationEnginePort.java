package com.gabolle.backend.recommendation.adapter;

/** 추천 후보를 만들어 주는 바깥 세계. */
public interface RecommendationEnginePort {

	/**
	 * @throws RecommendationEngineException 엔진이 결과를 만들지 못했을 때. 이 예외는
	 *     삼켜지지 않고 Job 을 FAILED 로 남기는 경로로 간다
	 */
	EngineCandidateBatch generate(EngineRequest request);
}
