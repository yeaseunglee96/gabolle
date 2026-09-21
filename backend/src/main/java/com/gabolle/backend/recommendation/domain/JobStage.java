package com.gabolle.backend.recommendation.domain;

/**
 * 추천 파이프라인의 단계. 실패했을 때 어디서 멈췄는지를 가리키는 데도 쓰고, 진행률
 * {@code progress.stage} 로 나간다. 단계와 퍼센트를 두 곳에 적으면 단계를 더할 때 한쪽만
 * 고쳐져 진행률이 뒤로 가므로 여기 한 곳에 둔다.
 *
 * 퍼센트는 선언 순서가 아니라 파이프라인이 실제로 지나가는 순서를 따른다 — 후보 생성이
 * 버전 확인보다 앞이다. {@code CONSTRAINT_EVALUATION}·{@code FEATURE_LOOKUP} 은 지금 진행률
 * 단계로 지나가지 않고 실패 지점을 가리키는 데만 쓰인다.
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
