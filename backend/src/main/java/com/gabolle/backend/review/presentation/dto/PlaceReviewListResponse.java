package com.gabolle.backend.review.presentation.dto;

import java.util.List;
import java.util.UUID;

import com.gabolle.backend.review.application.PlaceReviewService.ListResult;

/**
 * @param averageScore 인증된 평가만으로 낸 평균. 인증된 평가가 없으면 {@code null} 이다(0 이
 *        아니다 — 0 은 최하점과 구분이 안 된다). 저장하지 않고 응답을 만들 때마다 다시 계산한다
 */
public record PlaceReviewListResponse(List<PlaceReviewResponse> reviews, Double averageScore) {

	public static PlaceReviewListResponse from(ListResult result, UUID viewerId) {
		List<PlaceReviewResponse> responses = result.reviews().stream()
				.map(review -> PlaceReviewResponse.from(review, viewerId))
				.toList();
		return new PlaceReviewListResponse(responses, result.averageScore());
	}
}
