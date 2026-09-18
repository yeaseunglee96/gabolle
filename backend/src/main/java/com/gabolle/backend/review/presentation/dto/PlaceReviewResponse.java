package com.gabolle.backend.review.presentation.dto;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

import com.gabolle.backend.common.security.HtmlOutputEncoder;
import com.gabolle.backend.review.domain.PlaceReview;

/**
 * 리뷰 하나의 응답 모양.
 *
 * @param verified 위치로 방문이 확인된 평가인가. 목록 화면이 이 값으로 인증 배지를 그린다
 * @param mine 🔴 S15P21E201-745 — 맨 뒤에 더한 칸이다. 요청자가 쓴 평가인가. 앱이 "수정" 버튼을
 *        켤지 정한다. 이 화면은 원래 익명이라 <b>작성자가 누구인지는 여전히 싣지 않는다</b> —
 *        서버가 판정한 참/거짓 하나만 나간다. {@code StoryResponse.mine} 과 같은 이름·같은 뜻이다
 */
public record PlaceReviewResponse(UUID placeReviewId, Short foodScore, Short priceScore, Short accessibilityScore,
		Short onsiteScore, String body, boolean verified, String region, Instant createdAt, Instant updatedAt,
		boolean mine) {

	/**
	 * @param viewerId 요청자 아이디. {@code null} 이면(지금 경로에서는 오지 않지만, 나중에 인증
	 *        없는 경로가 붙을 수 있으므로) {@link #mine} 은 항상 {@code false} 다.
	 */
	public static PlaceReviewResponse from(PlaceReview review, UUID viewerId) {
		return new PlaceReviewResponse(review.getPlaceReviewId(), review.getFoodScore(), review.getPriceScore(),
				review.getAccessibilityScore(), review.getOnsiteScore(),
				// 🔴 S15P21E201-835 — story.body 와 같은 종류의 자유 입력이다. 응답으로 나가는
				//    여기서만 인코딩한다(HtmlOutputEncoder 클래스 주석 참고).
				HtmlOutputEncoder.forHtml(review.getBody()), review.isVerified(),
				review.getRegion(), review.getCreatedAt(), review.getUpdatedAt(),
				Objects.equals(review.getUserId(), viewerId));
	}
}
