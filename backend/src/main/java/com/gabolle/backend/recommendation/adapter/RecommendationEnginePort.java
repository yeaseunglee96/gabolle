package com.gabolle.backend.recommendation.adapter;

/**
 * 추천 후보를 만들어 주는 바깥 세계.
 *
 * <p>🔴 이 티켓(S15P21E201-543)은 <b>인터페이스만</b> 정의한다. 실제 추천 엔진·온톨로지·
 * Python 서버의 계약이 아직 없으므로 구체 구현을 지어내지 않는다. 지어낸 구현은 진짜 계약이
 * 오면 전부 버려지는데, 그 사이에 그것을 진짜라고 믿는 코드가 붙는다.
 *
 * <p>테스트는 이 인터페이스의 fake(**진짜처럼 동작하지만 테스트에서만 쓰는 대역**) 구현으로
 * 돈다.
 */
public interface RecommendationEnginePort {

	/**
	 * @throws RecommendationEngineException 엔진이 결과를 만들지 못했을 때. 이 예외는
	 *     삼켜지지 않고 Job 을 FAILED 로 남기는 경로로 간다
	 */
	EngineCandidateBatch generate(EngineRequest request);
}
