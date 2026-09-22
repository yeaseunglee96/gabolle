package com.gabolle.backend.recommendation.adapter;

import java.util.ArrayList;
import java.util.List;

/**
 * 이 결과를 만든 것이 무엇이었는지. 추천 엔진과 온톨로지가 보고하는 값이고 백엔드가
 * 지어내지 않는다.
 *
 * 값을 못 구했다고 {@code "unknown"} · {@code "v1"} · {@code "default"} 같은 것을 넣지
 * 않는다 — 그 순간 그 요청은 재현 불가능해지면서 재현 가능한 것처럼 보인다. 못 구하면
 * 요청을 실패로 남긴다 ({@link RecommendationVersionsMissingException}).
 * {@code ontologyVersion} 은 제약 어휘·규칙이고 {@code policyVersion} 은 제약 정책이다 —
 * 어휘가 그대로여도 정책이 바뀌면 판정이 바뀐다.
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
