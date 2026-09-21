package com.gabolle.backend.recommendation.adapter;

import java.util.List;

/** 필수 버전 값을 못 구했다. 기본값으로 때우지 않고 요청을 실패로 남기기 위한 예외다. */
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
