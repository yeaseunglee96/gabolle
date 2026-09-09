package com.gabolle.backend.review.presentation.dto;

import jakarta.validation.constraints.Size;

/**
 * 리뷰 작성 요청 — S15P21E201-287 · -408.
 *
 * <h2>🔴 인증 여부를 여기서 받지 않는다</h2>
 *
 * {@code verified} 필드가 이 record 에 없는 것이 의도다. 서버가
 * {@code PlaceReviewService.write} 안에서 인증 기록을 조회해 정한다. 요청이 정하게 하면
 * 아무나 인증된 평가를 쓸 수 있다.
 *
 * <p>점수 범위(1~5) 검사는 여기서 Bean Validation 으로 하지 않는다 —
 * {@code PlaceReview.Scores} 의 컴팩트 생성자가 검사하고 {@code ScoreOutOfRangeException} 으로
 * 어느 항목인지까지 응답에 담는다. 같은 검사를 두 번 다른 모양으로 하면 응답이 자리마다
 * 달라진다.
 */
public record PlaceReviewRequest(Short foodScore, Short priceScore, Short accessibilityScore, Short onsiteScore,
		@Size(max = 1000) String body, @Size(max = 100) String region) {
}
