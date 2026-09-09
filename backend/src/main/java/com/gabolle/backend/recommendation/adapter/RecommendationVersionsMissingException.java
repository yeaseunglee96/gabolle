package com.gabolle.backend.recommendation.adapter;

import java.util.List;

/**
 * 필수 버전 값을 못 구했다.
 *
 * <p>🔴 이때 기본값으로 때우지 않는 것이 이 예외의 존재 이유 전부다. 버전이 없는 추천 결과는
 * 나중에 "그때 무엇으로 계산했는가" 를 물을 수 없고, 그러면 그 데이터로 학습도 비교도 못 한다.
 */
public class RecommendationVersionsMissingException extends RuntimeException {

	private static final long serialVersionUID = 1L;

	/** Job 의 {@code error_code} 로 남는 값. */
	public static final String ERROR_CODE = "VERSION_UNRESOLVED";

	private final List<String> missingFieldNames;

	public RecommendationVersionsMissingException(List<String> missingFieldNames) {
		super("추천 결과에 필요한 버전 값이 비어 있다: " + String.join(", ", missingFieldNames));
		this.missingFieldNames = List.copyOf(missingFieldNames);
	}

	public List<String> getMissingFieldNames() {
		return this.missingFieldNames;
	}
}
