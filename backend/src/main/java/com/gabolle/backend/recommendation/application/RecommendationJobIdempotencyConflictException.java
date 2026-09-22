package com.gabolle.backend.recommendation.application;

/**
 * 같은 {@code Idempotency-Key} 를 다른 본문으로 재사용했다.
 * {@code TripRepository.IdempotencyKeyConflictException} 과 뜻이 같지만, recommendation
 * 패키지가 trip 도메인 예외에 기대지 않도록 따로 둔다.
 */
public class RecommendationJobIdempotencyConflictException extends RuntimeException {

	public RecommendationJobIdempotencyConflictException(String idempotencyKey) {
		super("이 Idempotency-Key 는 이미 다른 요청에 쓰였다: " + idempotencyKey);
	}
}
