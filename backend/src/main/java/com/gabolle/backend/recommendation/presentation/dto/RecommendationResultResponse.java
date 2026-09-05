package com.gabolle.backend.recommendation.presentation.dto;

import java.util.List;

import com.gabolle.backend.recommendation.domain.FallbackMode;

/**
 * 추천 결과 응답 — S15P21E201-604. 프론트가 이미 코드에 박아 놓고 부르는 모양을 그대로
 * 따른다({@code RecommendationResultController} 의 javadoc 참고). 필드를 추가·삭제하지
 * 않는다.
 */
public record RecommendationResultResponse(
		/** {@code COMPLETED} · {@code PARTIAL} · {@code FAILED}. */
		String status,
		List<Item> items,
		String itineraryId,
		FallbackMode fallbackMode,
		List<String> conflicts,
		String errorMessage) {

	public record Item(
			/** {@code place_id}. */
			String id,
			/** {@code place.name_ko}. */
			String title,
			/** 🔴 항상 {@code null} — {@code place} 표에 이미지 칸이 없다. */
			String imageUrl,
			List<String> reasonCodes,
			/** 🔴 항상 {@code null} — 비용 데이터가 없다. {@code 0} 을 넣으면 "공짜"로 읽힌다. */
			Integer estimatedCostKrw,
			/** {@code LOW} · {@code MEDIUM} · {@code HIGH}. 근거가 없으면 {@code null}. */
			String crowdLevel,
			/** 이동 관련 경고만. 없으면 {@code null}(빈 배열이 아니다). */
			List<String> mobilityWarnings,
			/** {@code VERIFIED} · {@code ESTIMATED} · {@code UNKNOWN}. */
			String dataStatus,
			FallbackMode fallbackMode,
			String itineraryId) {
	}
}
