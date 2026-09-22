package com.gabolle.backend.recommendation.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 규칙 기반 BASELINE 추천 엔진 설정.
 *
 * <p>버전 넷은 이 설정이 담지만 {@code datasetVersion} 은 여기 없다. 그 값은
 * {@code PlaceCandidateResponse.datasetVersions()} 에서 가져온다 — 장소 데이터가 실제로 어느
 * 수집분에서 왔는지는 설정이 아니라 조회 결과가 말해 준다.
 *
 * @param modelVersion 규칙 버전
 * @param featureVersion 피처 계산 버전
 * @param ontologyVersion 제약 어휘·규칙 버전
 * @param policyVersion 제약 정책 버전
 * @param radiusM 후보 질의 반경(m) 기본값
 * @param candidateScanLimit 채점 대상 상한 — 장소 조회에서 받아 올 후보 수
 * @param candidateLimit 채점을 마친 뒤 남기는 후보 수. 이 값을 장소 조회에 그대로 넘기면
 *     "가까운 순 N곳만 채점 대상" 이 되어 반경 밖 취향이 맞는 장소가 점수를 받을 기회조차
 *     잃는다. 그래서 {@code candidateScanLimit} 까지 받아 전부 채점한 뒤 여기서 자른다
 * @param tasteVectorMultiplier 접힌 취향 벡터가 CATEGORY 겹침에 더하는 배수. 성분의 근거가
 *     아직 전부 설문이라 값을 크게 잡으면 개인화가 세지는 것이 아니라 설문이 두 번 세어진다 —
 *     성분의 {@code evidence} 가 {@code INTERACTION}·{@code BLENDED} 로 바뀌면 다시 본다.
 *     0 이면 이 기능이 완전히 꺼진다(배포 없이 되돌리는 손잡이)
 * @param weights 점수 가중치
 */
@ConfigurationProperties(prefix = "gabolle.recommendation.baseline")
public record BaselineEngineProperties(
		String modelVersion,
		String featureVersion,
		String ontologyVersion,
		String policyVersion,
		Integer radiusM,
		Integer candidateScanLimit,
		Integer candidateLimit,
		Double tasteVectorMultiplier,
		Weights weights) {

	public BaselineEngineProperties {
		radiusM = (radiusM == null) ? 5000 : radiusM;
		candidateScanLimit = (candidateScanLimit == null) ? 20000 : candidateScanLimit;
		candidateLimit = (candidateLimit == null) ? 200 : candidateLimit;
		tasteVectorMultiplier = (tasteVectorMultiplier == null) ? 0.05 : tasteVectorMultiplier;
		weights = (weights == null) ? new Weights(null, null, null, null, null, null) : weights;
		if (tasteVectorMultiplier < 0) {
			throw new IllegalArgumentException(
					"gabolle.recommendation.baseline.taste-vector-multiplier 는 0 이상이어야 한다: "
							+ tasteVectorMultiplier);
		}
		if (radiusM < 100) {
			throw new IllegalArgumentException("gabolle.recommendation.baseline.radius-m 은 100 이상이어야 한다");
		}
		if (candidateLimit < 1) {
			throw new IllegalArgumentException(
					"gabolle.recommendation.baseline.candidate-limit 은 1 이상이어야 한다");
		}
		if (candidateScanLimit < candidateLimit) {
			// 채점 대상보다 남길 수가 많으면 "채점한 뒤에 자른다" 가 아무 일도 안 하는데,
			// 설정만 보면 그렇게 안 보인다. 조용한 무효화보다 기동 실패가 낫다.
			throw new IllegalArgumentException(
					"gabolle.recommendation.baseline.candidate-scan-limit(" + candidateScanLimit
							+ ") 은 candidate-limit(" + candidateLimit + ") 이상이어야 한다");
		}
	}

	/**
	 * 점수 가중치. 여섯 구성요소의 합이 1 이어야 한다는 강제는 두지 않는다 — 가중치 실험은
	 * 값을 조정하며 진행하고, 코드가 합계를 강제하면 그 실험을 매번 막는다.
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
			// 이 0.15 는 지금 언제나 0 을 기여한다. 장소 쪽 ATMOSPHERE_TAG 가 0행이라 짝지을
			// 것이 없고, 그래서 분위기 문항을 추천 흐름에서 뺐다. 그래도 값을 고치지 않는다 —
			// 다른 조각에 나눠 주면 순위가 실제로 바뀌고, 조용함 점수로 대신 채우면 조용함이
			// preferenceAlignment 와 여기에 두 번 세어진다. 빠진 항은 모든 후보에서 똑같이
			// 빠지므로 총점만 작아지고 순위는 안 바뀌며, 자리를 비워 두면 나중에 다시 켤 때
			// 되돌릴 것이 없다. ATMOSPHERE 차원 자체도 안 지운다 — 배포된 앱이 아직 보낸다.
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
