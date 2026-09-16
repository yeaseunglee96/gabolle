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
 * @param candidateScanLimit <b>채점 대상</b> 상한 — 장소 조회에서 받아 올 후보 수.
 *     기본값을 크게 둔 이유는 아래 {@code candidateLimit} 설명에 있다 (S15P21E201-724)
 * @param candidateLimit <b>채점을 마친 뒤</b> 남기는 후보 수.
 *     🔴 <b>2026-09-07 에 뜻이 바뀌었다.</b> 전에는 이 값이 장소 조회에 그대로 넘어가서
 *     "가까운 순 200곳만 채점 대상" 이라는 뜻이었다 — 그 밖의 장소는 아무리 좋아도 점수를
 *     매길 기회조차 없었고, 부산에서는 그 200곳이 중앙값 <b>304m</b> 안에서 끊겼다(반경은
 *     5km 인데). 지금은 반경 안 후보를 {@code candidateScanLimit} 까지 받아 <b>전부 채점한 뒤</b>
 *     점수 높은 순으로 이만큼만 남긴다. 저장되는 후보 행 수는 예전과 같고, <b>어느 200곳이
 *     남는가</b>만 달라진다
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
			// 🔴 채점 대상보다 남길 수가 많으면 "채점한 뒤에 자른다" 가 아무 일도 안 하는데,
			//    설정만 보면 그렇게 안 보인다. 조용한 무효화보다 기동 실패가 낫다.
			throw new IllegalArgumentException(
					"gabolle.recommendation.baseline.candidate-scan-limit(" + candidateScanLimit
							+ ") 은 candidate-limit(" + candidateLimit + ") 이상이어야 한다");
		}
	}

	/**
	 * 접힌 취향 벡터가 CATEGORY 겹침에 더하는 배수 — S15P21E201-943.
	 *
	 * <h2>🔴 0.05 는 「영향이 작아서」가 아니라 「같은 근거를 두 번 세는 동안의 임시값」이다</h2>
	 *
	 * 2026-09-16 현재 {@code user_taste_weight} 의 성분은 <b>전부 {@code evidence=SURVEY}</b> 다.
	 * 즉 지금 벡터는 설문 답을 다시 적어 둔 것이고, 채점기는 그 설문({@code PreferenceSnapshot})을
	 * 이미 {@code weights.interest}(기본 0.20)로 읽고 있다. 그래서 이 항을 크게 잡으면
	 * <b>개인화가 세진 것이 아니라 설문이 두 번 세어진다.</b>
	 *
	 * <h2>🔴 언제 올려도 되나</h2>
	 *
	 * 행동 근거가 쌓여 성분의 {@code evidence} 가 {@code INTERACTION}·{@code BLENDED} 로 바뀌면,
	 * 그때부터 벡터는 설문이 말하지 않는 것을 말한다. <b>그 시점에 이 값을 다시 본다.</b>
	 * 행동 이벤트({@code place_like}·{@code itinerary_remove})는 S15P21E201-1080 이 2026-09-16 에
	 * 처음 남기기 시작했다.
	 *
	 * <p>0 으로 두면 이 기능이 완전히 꺼진다 — 벡터가 이상할 때 배포 없이 되돌리는 손잡이다.
	 */
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
