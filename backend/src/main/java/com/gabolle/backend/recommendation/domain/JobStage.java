package com.gabolle.backend.recommendation.domain;

/**
 * 추천 파이프라인의 단계. 실패했을 때 어디서 멈췄는지를 가리키는 데도 쓴다.
 *
 * <p>GB-API-001 4.2 JobDto 의 {@code progress.stage} 로 나간다. 명세서가 값을 열거하지는
 * 않고 예시로 {@code "ROUTE_OPTIMIZATION"} 하나를 보여 주므로 그 이름에 맞춰 뒀다.
 *
 * <h2>2026-09-09 (S15P21E201-193) — 단계마다 진행률을 붙였다</h2>
 * 진행률을 화면에 밀어 보내려면 단계와 퍼센트가 <b>한 곳에서</b> 나와야 한다. 두 곳에
 * 적으면 단계를 하나 더할 때 한쪽만 고쳐지고, 그러면 진행률이 뒤로 가거나 제자리에 선다.
 *
 * <p>🔴 <b>퍼센트는 선언 순서가 아니라 파이프라인이 실제로 지나가는 순서를 따른다.</b>
 * {@code RecommendationService.continueJob} 은 후보 생성을 먼저 하고 그 응답에 실린 버전을
 * 그다음에 확인한다 — 즉 {@code CANDIDATE_GENERATION} 이 {@code VERSION_RESOLUTION} 보다
 * 앞이다. 선언 순서대로 퍼센트를 매기면 35에서 20으로 내려가는 구간이 생긴다.
 *
 * <p>{@code CONSTRAINT_EVALUATION} · {@code FEATURE_LOOKUP} 은 지금 진행률 단계로는 지나가지
 * 않는다 — 실패 지점을 가리키는 데만 쓰인다. 그래서 두 값은 앞뒤 단계 사이에만 있으면 되고,
 * 나중에 실제로 지나가게 되어도 순서가 깨지지 않는다.
 */
public enum JobStage {

	CREATED(0),
	CANDIDATE_GENERATION(20),
	VERSION_RESOLUTION(35),
	CONSTRAINT_EVALUATION(50),
	FEATURE_LOOKUP(60),
	RANKING(70),
	ROUTE_OPTIMIZATION(85),
	PERSISTENCE(95),
	COMPLETED(100);

	private final int percent;

	JobStage(int percent) {
		this.percent = percent;
	}

	/** 이 단계에 도달했을 때의 진행률. 0~100. */
	public int percent() {
		return this.percent;
	}
}
