package com.gabolle.backend.recommendation.application;

/**
 * 같은 {@code Idempotency-Key} 를 <b>다른 본문</b>으로 재사용했다 — S15P21E201-944.
 *
 * <p>{@code TripRepository.IdempotencyKeyConflictException} 과 같은 뜻이다. 여기서 새로
 * 만든 이유는 recommendation 패키지가 trip 도메인 예외에 기대면 안 되기 때문이다 —
 * 계층은 독립적으로 유지한다.
 */
public class RecommendationJobIdempotencyConflictException extends RuntimeException {

	public RecommendationJobIdempotencyConflictException(String idempotencyKey) {
		super("이 Idempotency-Key 는 이미 다른 요청에 쓰였다: " + idempotencyKey);
	}
}
