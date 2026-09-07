package com.gabolle.backend.review.presentation.dto;

import java.time.Instant;
import java.util.UUID;

import com.gabolle.backend.review.domain.PlaceReview;

/**
 * 리뷰 하나의 응답 모양.
 *
 * @param verified 위치로 방문이 확인된 평가인가. 목록 화면이 이 값으로 인증 배지를 그린다
 */
public record PlaceReviewResponse(UUID placeReviewId, Short foodScore, Short priceScore, Short accessibilityScore,
		Short onsiteScore, String body, boolean verified, String region, Instant createdAt, Instant updatedAt) {

	public static PlaceReviewResponse from(PlaceReview review) {
		return new PlaceReviewResponse(review.getPlaceReviewId(), review.getFoodScore(), review.getPriceScore(),
				review.getAccessibilityScore(), review.getOnsiteScore(), review.getBody(), review.isVerified(),
				review.getRegion(), review.getCreatedAt(), review.getUpdatedAt());
	}
}
