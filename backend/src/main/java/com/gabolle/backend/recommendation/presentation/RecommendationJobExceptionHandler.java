package com.gabolle.backend.recommendation.presentation;

import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import com.gabolle.backend.common.api.ApiError;
import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.recommendation.application.RecommendationJobIdempotencyConflictException;
import com.gabolle.backend.trip.application.TripQueryService;

/**
 * 추천 Job 생성·조회의 오류를 HTTP 로 번역한다 — {@code TripExceptionHandler} 와 같은
 * 패턴(개발계획서 4.2 "세밀한 예외 계층을 두지 않는다").
 */
// 🔴 S15P21E201-1013 — RecommendationActionController 를 더했다. 안 더하면 남의 여행에
//    판단을 적으려 할 때 TripQueryService.TripNotFoundException 이 아무에게도 안 잡혀
//    404 가 아니라 500 이 나간다. 그러면 "없는 여행" 과 "서버 고장" 이 구분되지 않는다.
@RestControllerAdvice(assignableTypes = { RecommendationJobController.class, RecommendationResultController.class,
		RecommendationActionController.class })
public class RecommendationJobExceptionHandler {

	/** 여행이 없거나 요청자가 그 여행의 회원이 아니다 — 둘 다 404 (FR-SEC-01). */
	@ExceptionHandler(TripQueryService.TripNotFoundException.class)
	public ResponseEntity<ApiResponse<Void>> handleTripNotFound(TripQueryService.TripNotFoundException e) {
		// 🔴 아래 handleInvalid와 같은 이유로 번역 키 대신 문장을 넣는다.
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(ApiResponse.failure(new ApiError("TRIP_NOT_FOUND", "해당 여행을 찾을 수 없어요."), requestId()));
	}

	/** 없는 작업 번호(또는 남의 작업 번호 — 둘을 구분해 응답하지 않는다). */
	@ExceptionHandler(RecommendationJobController.JobNotFoundException.class)
	public ResponseEntity<ApiResponse<Void>> handleJobNotFound(
			RecommendationJobController.JobNotFoundException e) {
		// 🔴 아래 handleInvalid와 같은 이유로 번역 키 대신 문장을 넣는다 — 프론트가 이 코드를
		// 따로 처리하지 않으면 message가 그대로 화면에 뜬다.
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(ApiResponse.failure(new ApiError("JOB_NOT_FOUND", "해당 작업을 찾을 수 없어요."), requestId()));
	}

	/**
	 * 🔴 S15P21E201-604 — Job 이 아직 {@code PENDING}·{@code RUNNING} 이라 결과가 없다.
	 * 여기 message 는 한국어 문장이다 — {@code PlaceExceptionHandler} 의 javadoc이 남긴 실측대로
	 * 프론트가 {@code error.message} 를 그대로 화면에 띄운다.
	 */
	@ExceptionHandler(RecommendationResultController.JobNotReadyException.class)
	public ResponseEntity<ApiResponse<Void>> handleJobNotReady(
			RecommendationResultController.JobNotReadyException e) {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.failure(
				new ApiError("RECOMMENDATION_NOT_READY", "추천 결과가 아직 준비되지 않았습니다. 잠시 후 다시 시도해 주세요."),
				requestId()));
	}

	/**
	 * {@code preferenceSnapshotVersion} 을 못 찾았거나, 이 여행이 제약을 하나도 답하지 않아
	 * constraint_snapshot 이 없거나, {@code jobId}/{@code tripId} 가 UUID 모양이 아니거나 —
	 * 요청 자체가 성립하지 않는다.
	 */
	@ExceptionHandler({ IllegalArgumentException.class, IllegalStateException.class })
	public ResponseEntity<ApiResponse<Void>> handleInvalid(RuntimeException e) {
		// 🔴 message는 번역 키가 아니라 사람이 읽는 문장이어야 한다 — 위 handleJobNotReady와
		// 같은 원칙이다. "error.recommendationJob.validation" 을 그대로 넣어 뒀던 것을
		// 프론트가 번역 없이 화면에 그대로 띄워, 사용자에게 문자열 코드가 노출됐다
		// (S15P21E201 사용자 리포트). 자세한 원인(e.getMessage())은 fields에만 싣는다.
		return ResponseEntity.badRequest().body(ApiResponse.failure(
				new ApiError("RECOMMENDATION_JOB_VALIDATION_FAILED",
						"입력한 조건을 확인할 수 없어요. 조건을 다시 확인한 뒤 시도해 주세요.",
						List.of(e.getMessage())),
				requestId()));
	}

	/** 🔴 S15P21E201-944 — 같은 Idempotency-Key 를 다른 본문으로 재사용했다. */
	@ExceptionHandler(RecommendationJobIdempotencyConflictException.class)
	public ResponseEntity<ApiResponse<Void>> handleIdempotencyConflict(
			RecommendationJobIdempotencyConflictException e) {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(ApiResponse.failure(
				new ApiError("RECOMMENDATION_JOB_IDEMPOTENCY_CONFLICT",
						"같은 Idempotency-Key 가 다른 요청 내용으로 이미 쓰였어요."),
				requestId()));
	}

	private String requestId() {
		return "req_" + UUID.randomUUID();
	}
}
