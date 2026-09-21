package com.gabolle.backend.review.presentation.dto;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.gabolle.backend.common.security.HtmlOutputEncoder;
import com.gabolle.backend.review.domain.PlaceReview;

/**
 * @param mine 요청자가 쓴 평가인가. 이 화면은 익명이라 작성자 아이디는 싣지 않고 서버가 판정한
 *        참/거짓만 나간다
 */
public record PlaceReviewResponse(UUID placeReviewId, Short foodScore, Short priceScore, Short accessibilityScore,
		Short onsiteScore, String body, boolean verified, String region, Instant createdAt, Instant updatedAt,
		boolean mine) {

	/** @param viewerId {@code null} 이면 {@link #mine} 은 항상 {@code false} 다 */
	public static PlaceReviewResponse from(PlaceReview review, UUID viewerId) {
		return new PlaceReviewResponse(review.getPlaceReviewId(), review.getFoodScore(), review.getPriceScore(),
				review.getAccessibilityScore(), review.getOnsiteScore(),
				// 자유 입력이라 인코딩한다. 저장 시점이 아니라 응답으로 나가는 여기서만 한다.
				HtmlOutputEncoder.forHtml(review.getBody()), review.isVerified(),
				review.getRegion(), review.getCreatedAt(), review.getUpdatedAt(),
				Objects.equals(review.getUserId(), viewerId));
	}
}
