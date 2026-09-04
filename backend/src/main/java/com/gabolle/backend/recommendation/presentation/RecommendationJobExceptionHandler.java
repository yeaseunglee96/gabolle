package com.gabolle.backend.recommendation.presentation;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.gabolle.backend.common.api.ApiError;
import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.trip.application.TripQueryService;

/**
 * 추천 Job 생성·조회의 오류를 HTTP 로 번역한다 — {@code TripExceptionHandler} 와 같은
 * 패턴(개발계획서 4.2 "세밀한 예외 계층을 두지 않는다").
 */
@RestControllerAdvice(assignableTypes = RecommendationJobController.class)
public class RecommendationJobExceptionHandler {

	/** 여행이 없거나 요청자가 그 여행의 회원이 아니다 — 둘 다 404 (FR-SEC-01). */
	@ExceptionHandler(TripQueryService.TripNotFoundException.class)
	public ResponseEntity<ApiResponse<Void>> handleTripNotFound(TripQueryService.TripNotFoundException e) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(ApiResponse.failure(new ApiError("TRIP_NOT_FOUND", "error.trip.notFound"), requestId()));
	}

	/** 없는 작업 번호. */
	@ExceptionHandler(RecommendationJobController.JobNotFoundException.class)
	public ResponseEntity<ApiResponse<Void>> handleJobNotFound(
			RecommendationJobController.JobNotFoundException e) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(ApiResponse.failure(new ApiError("JOB_NOT_FOUND", "error.job.notFound"), requestId()));
	}

	/**
	 * {@code preferenceSnapshotVersion} 을 못 찾았거나, 이 여행이 제약을 하나도 답하지 않아
	 * constraint_snapshot 이 없거나, {@code jobId}/{@code tripId} 가 UUID 모양이 아니거나 —
	 * 요청 자체가 성립하지 않는다.
	 */
	@ExceptionHandler({ IllegalArgumentException.class, IllegalStateException.class })
	public ResponseEntity<ApiResponse<Void>> handleInvalid(RuntimeException e) {
		return ResponseEntity.badRequest().body(ApiResponse.failure(
				new ApiError("RECOMMENDATION_JOB_VALIDATION_FAILED", "error.recommendationJob.validation",
						List.of(e.getMessage())),
				requestId()));
	}

	private String requestId() {
		return "req_" + UUID.randomUUID();
	}
}
