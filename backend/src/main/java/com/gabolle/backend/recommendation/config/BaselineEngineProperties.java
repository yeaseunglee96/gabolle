package com.gabolle.backend.recommendation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 규칙 기반 BASELINE 추천 엔진 설정 (S15P21E201-604).
 *
 * <p>🔴 버전 넷({@code modelVersion}·{@code featureVersion}·{@code ontologyVersion}·
 * {@code policyVersion})은 이 설정이 담아 두지만 {@code datasetVersion} 은 여기 없다.
 * {@code datasetVersion} 은 {@code PlaceCandidateResponse.datasetVersions()} 에서 가져온다
 * ({@link com.gabolle.backend.recommendation.adapter.BaselineRecommendationEngine} 참고) —
 * 장소 데이터가 실제로 어느 수집분에서 왔는지는 설정이 아니라 조회 결과가 말해 준다.
 *
 * @param modelVersion 규칙 버전. 다른 에이전트가 {@code application.properties} 에 값을 넣는다
 * @param featureVersion 피처 계산 버전
 * @param ontologyVersion 제약 어휘·규칙 버전
 * @param policyVersion 제약 정책 버전
 * @param radiusM 후보 질의 반경(m) 기본값
 * @param candidateLimit 후보 상한 기본값
 * @param weights 점수 가중치
 */
@ConfigurationProperties(prefix = "gabolle.recommendation.baseline")
public record BaselineEngineProperties(
		String modelVersion,
		String featureVersion,
		String ontologyVersion,
		String policyVersion,
		Integer radiusM,
		Integer candidateLimit,
		Weights weights) {

	public BaselineEngineProperties {
		radiusM = (radiusM == null) ? 5000 : radiusM;
		candidateLimit = (candidateLimit == null) ? 200 : candidateLimit;
		weights = (weights == null) ? new Weights(null, null, null, null, null, null) : weights;
		if (radiusM < 100) {
			throw new IllegalArgumentException("gabolle.recommendation.baseline.radius-m 은 100 이상이어야 한다");
		}
		if (candidateLimit < 1) {
			throw new IllegalArgumentException(
					"gabolle.recommendation.baseline.candidate-limit 은 1 이상이어야 한다");
		}
	}

	/**
	 * 점수 가중치. 여섯 구성요소의 합이 1 이어야 한다는 강제는 두지 않는다 — 가중치 실험은
	 * 데이터 담당이 값을 조정하며 진행하고, 코드가 합계를 강제하면 그 실험을 매번 막는다.
	 */
	public record Weights(
			Double distance,
			Double interest,
			Double atmosphere,
			Double cuisine,
			Double preferenceAlignment,
			Double popularity) {

		public Weights {
			distance = defaultIfNull(distance, 0.30);
			interest = defaultIfNull(interest, 0.20);
			atmosphere = defaultIfNull(atmosphere, 0.15);
			cuisine = defaultIfNull(cuisine, 0.15);
			preferenceAlignment = defaultIfNull(preferenceAlignment, 0.10);
			popularity = defaultIfNull(popularity, 0.10);
		}

		private static double defaultIfNull(Double value, double fallback) {
			return value == null ? fallback : value;
		}
	}
}
