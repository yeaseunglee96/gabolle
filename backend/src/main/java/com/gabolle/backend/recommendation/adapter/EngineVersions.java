package com.gabolle.backend.recommendation.adapter;

import java.util.ArrayList;
import java.util.List;

/**
 * 이 결과를 만든 것이 무엇이었는지. 추천 엔진과 온톨로지가 <b>보고</b>하는 값이고 백엔드가
 * 지어내지 않는다. GB-API-001 6장이 요구하는 대로 내부 API 를 양방향으로 오간다.
 *
 * <p>🔴 값을 못 구했다고 {@code "unknown"} · {@code "v1"} · {@code "default"} 같은 것을 넣지
 * 않는다. 그 순간 그 요청은 영원히 재현 불가능해지고, 더 나쁘게는 <b>재현 가능한 것처럼</b>
 * 보인다. 못 구하면 요청을 실패로 남긴다 — {@link RecommendationVersionsMissingException}.
 *
 * @param modelVersion 규칙 또는 모델 버전
 * @param featureVersion 피처 정의·계산 버전
 * @param ontologyVersion 제약 <b>어휘·규칙</b> 버전
 * @param policyVersion 제약 <b>정책</b> 버전. 어휘가 그대로여도 정책이 바뀌면 판정이 바뀐다
 *     (GB-API-001 4.3 · INT-ONT-01)
 * @param datasetVersion 장소·경로 데이터 버전
 */
public record EngineVersions(
		String modelVersion,
		String featureVersion,
		String ontologyVersion,
		String policyVersion,
		String datasetVersion) {

	/** 비어 있는 항목의 이름들. 하나라도 있으면 이 요청은 성공으로 남을 수 없다. */
	public List<String> missingFieldNames() {
		List<String> missing = new ArrayList<>();
		if (isBlank(this.modelVersion)) {
			missing.add("model_version");
		}
		if (isBlank(this.featureVersion)) {
			missing.add("feature_version");
		}
		if (isBlank(this.ontologyVersion)) {
			missing.add("ontology_version");
		}
		if (isBlank(this.policyVersion)) {
			missing.add("policy_version");
		}
		if (isBlank(this.datasetVersion)) {
			missing.add("dataset_version");
		}
		return missing;
	}

	private static boolean isBlank(String value) {
		return value == null || value.isBlank();
	}
}
