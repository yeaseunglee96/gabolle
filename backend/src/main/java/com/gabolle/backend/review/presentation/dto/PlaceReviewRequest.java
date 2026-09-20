package com.gabolle.backend.review.presentation.dto;

import jakarta.validation.constraints.Size;

/**
 * 리뷰 작성 요청.
 *
 * {@code verified} 필드가 없는 것이 의도다. 서버가 인증 기록을 조회해 정한다.
 *
 * 점수 범위 검사도 여기서 하지 않는다 — {@code PlaceReview.Scores} 의 컴팩트 생성자가 검사해
 * 어느 항목인지까지 응답에 담는다. 같은 검사를 두 곳에서 다른 모양으로 하면 응답이 자리마다
 * 달라진다.
 */
public record PlaceReviewRequest(Short foodScore, Short priceScore, Short accessibilityScore, Short onsiteScore,
		@Size(max = 1000) String body, @Size(max = 100) String region) {
}
