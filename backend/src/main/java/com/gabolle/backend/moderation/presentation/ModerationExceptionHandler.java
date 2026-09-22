package com.gabolle.backend.moderation.presentation;

import java.util.List;
import java.util.UUID;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import com.gabolle.backend.common.api.ApiError;
import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.moderation.application.ModerationQueueService;
import com.gabolle.backend.moderation.application.StoryReportService;
import com.gabolle.backend.moderation.domain.StoryReport;

/**
 * 신고·검토 컨트롤러의 오류 응답. 모양은 팀 공용
 * {@code ApiResponse.failure(ApiError(code, message, fields))} 다.
 *
 * {@code assignableTypes} 와 {@code @Order(HIGHEST_PRECEDENCE)} 를 빠뜨리면 더 넓은 advice 가 먼저
 * 잡아 없는 자원이 500 으로 나간다.
 */
@RestControllerAdvice(assignableTypes = { StoryReportController.class, AdminModerationController.class })
@Order(Ordered.HIGHEST_PRECEDENCE)
public class ModerationExceptionHandler {

	@ExceptionHandler({ StoryReportService.StoryNotFoundException.class,
			ModerationQueueService.StoryNotFoundException.class })
	public ResponseEntity<ApiResponse<Void>> handleNotFound(RuntimeException e) {
		return ResponseEntity.status(HttpStatus.NOT_FOUND)
				.body(ApiResponse.failure(new ApiError("STORY_NOT_FOUND", e.getMessage(), List.of()), requestId()));
	}

	@ExceptionHandler(StoryReportService.InvalidReasonException.class)
	public ResponseEntity<ApiResponse<Void>> handleInvalidReason(StoryReportService.InvalidReasonException e) {
		return ResponseEntity.badRequest().body(ApiResponse
				.failure(new ApiError("STORY_REPORT_REASON_INVALID", e.getMessage(), List.of("reason")), requestId()));
	}

	/**
	 * 신고 한 건 단위({@link StoryReport.AlreadyResolvedException})와 기록 단위
	 * ({@link ModerationQueueService.NoPendingReportsException})를 같은 코드로 답한다 — 화면에는 둘 다
	 * "더 처리할 것이 없다" 는 같은 사실이다.
	 */
	@ExceptionHandler({ StoryReport.AlreadyResolvedException.class,
			ModerationQueueService.NoPendingReportsException.class })
	public ResponseEntity<ApiResponse<Void>> handleAlreadyResolved(RuntimeException e) {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(
				ApiResponse.failure(new ApiError("STORY_REPORT_ALREADY_RESOLVED", e.getMessage(), List.of()),
						requestId()));
	}

	/**
	 * {@code IllegalArgumentException} 을 여기 더하지 않는다. Spring 이 예외의 원인 사슬까지 훑어
	 * 핸들러를 고르므로, 저장소 안쪽에서 난 IAE 가 400 으로 둔갑해 서버 결함을 감춘다.
	 */
	@ExceptionHandler({ HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class })
	public ResponseEntity<ApiResponse<Void>> handleBadInput(Exception e) {
		return ResponseEntity.badRequest().body(ApiResponse
				.failure(new ApiError("STORY_REPORT_VALIDATION_FAILED", "요청 값을 읽을 수 없습니다.", List.of()), requestId()));
	}

	private static String requestId() {
		return "req_" + UUID.randomUUID();
	}
}
