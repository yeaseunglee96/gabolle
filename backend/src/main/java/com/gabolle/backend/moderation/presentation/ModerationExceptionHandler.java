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
 * 신고·검토 컨트롤러의 오류 응답. 모양은 팀 공용 {@code ApiResponse.failure(ApiError(code, message, fields))} 다.
 *
 * <p>🔴 {@code @RestControllerAdvice(assignableTypes = …)} + {@code @Order(HIGHEST_PRECEDENCE)} 로
 * 대상 컨트롤러와 우선순위를 명시한다 — 이 저장소는 이것을 빠뜨려 없는 자원이 500 으로 나간 사고를
 * 네 번 겪었다({@code StoryExceptionHandler} 의 같은 주석 참고).
 *
 * <p>{@link StoryReportService.StoryNotFoundException} 과 {@link ModerationQueueService.StoryNotFoundException}
 * 은 서로 다른 클래스지만 같은 뜻(대상 기록이 없다)이라 한 메서드에서 함께 받는다.
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
	 * 이미 처리된 신고를 다시 처리하려 했다 — 409. {@link StoryReport.AlreadyResolvedException}(신고
	 * 한 건 단위)과 {@link ModerationQueueService.NoPendingReportsException}(기록 단위 — 처리할
	 * 미처리 신고가 이미 없다)을 같은 코드로 답한다. 화면 입장에서는 둘 다 "이 기록은 더 처리할
	 * 것이 없다" 는 같은 사실이다.
	 */
	@ExceptionHandler({ StoryReport.AlreadyResolvedException.class,
			ModerationQueueService.NoPendingReportsException.class })
	public ResponseEntity<ApiResponse<Void>> handleAlreadyResolved(RuntimeException e) {
		return ResponseEntity.status(HttpStatus.CONFLICT).body(
				ApiResponse.failure(new ApiError("STORY_REPORT_ALREADY_RESOLVED", e.getMessage(), List.of()),
						requestId()));
	}

	/**
	 * 🔴 {@code IllegalArgumentException} 을 여기 넣지 않는다 — {@code StoryExceptionHandler} 의 같은
	 * 주석과 같은 이유다. Spring 이 예외의 원인 사슬까지 훑어 핸들러를 고르므로, 저장소 안쪽에서 난
	 * IAE 가 400 으로 둔갑해 진짜 서버 결함을 감출 수 있다.
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
