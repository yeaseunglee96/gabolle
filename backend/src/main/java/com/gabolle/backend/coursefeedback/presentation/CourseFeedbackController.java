package com.gabolle.backend.coursefeedback.presentation;

import java.util.List;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.gabolle.backend.common.api.ApiError;
import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.common.security.AuthenticatedUsers;
import com.gabolle.backend.coursefeedback.application.CourseFeedbackService;
import com.gabolle.backend.coursefeedback.application.CourseFeedbackService.CourseFeedback;
import com.gabolle.backend.coursefeedback.application.CourseFeedbackService.SubmitCommand;
import com.gabolle.backend.coursefeedback.application.CourseFeedbackService.Summary;

/**
 * 코스별 품질 피드백 — S15P21E201-2020. 평가는 여행 회원만, 집계는 운영자(ADMIN)만 —
 * 경로({@code /api/v1/admin/**})와 {@code @PreAuthorize} 두 겹으로 막는다.
 */
@RestController
@Profile({ "db", "dev" })
public class CourseFeedbackController {

	private final CourseFeedbackService service;

	public CourseFeedbackController(CourseFeedbackService service) {
		this.service = service;
	}

	public record SubmitRequest(String rating, List<String> reasons, String comment, String courseLabel) {
	}

	/** 아직 평가가 없으면 {@code feedback} 이 비어 있다. */
	public record Mine(CourseFeedback feedback) {
	}

	@GetMapping("/api/v1/itineraries/{itineraryId}/course-feedback")
	public ApiResponse<Mine> mine(@PathVariable UUID itineraryId, Authentication authentication,
			@RequestHeader(value = "X-Request-Id", required = false) String requestId) {
		UUID user = AuthenticatedUsers.requireId(authentication);
		return ApiResponse.success(new Mine(this.service.mine(user, itineraryId).orElse(null)), rid(requestId));
	}

	@PutMapping("/api/v1/itineraries/{itineraryId}/course-feedback")
	public ApiResponse<CourseFeedback> submit(@PathVariable UUID itineraryId, @RequestBody SubmitRequest body,
			Authentication authentication, @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
		UUID user = AuthenticatedUsers.requireId(authentication);
		return ApiResponse.success(this.service.submit(user, itineraryId,
				new SubmitCommand(body.rating(), body.reasons(), body.comment(), body.courseLabel())), rid(requestId));
	}

	@DeleteMapping("/api/v1/itineraries/{itineraryId}/course-feedback")
	public ResponseEntity<Void> delete(@PathVariable UUID itineraryId, Authentication authentication) {
		this.service.delete(AuthenticatedUsers.requireId(authentication), itineraryId);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/api/v1/admin/course-feedback/summary")
	@PreAuthorize("hasRole('ADMIN')")
	public ApiResponse<Summary> summary(@RequestParam(value = "days", defaultValue = "7") int days,
			Authentication authentication, @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
		AuthenticatedUsers.requireId(authentication);
		return ApiResponse.success(this.service.summary(days), rid(requestId));
	}

	@ExceptionHandler(CourseFeedbackService.InvalidException.class)
	public ResponseEntity<ApiResponse<Void>> invalid(CourseFeedbackService.InvalidException e) {
		return fail(HttpStatus.BAD_REQUEST, "COURSE_FEEDBACK_INVALID", e.getMessage(), List.of(e.field()));
	}

	@ExceptionHandler(CourseFeedbackService.NotFoundException.class)
	public ResponseEntity<ApiResponse<Void>> notFound(RuntimeException e) {
		return fail(HttpStatus.NOT_FOUND, "ITINERARY_NOT_FOUND", e.getMessage(), List.of());
	}

	@ExceptionHandler(CourseFeedbackService.NotRecommendedException.class)
	public ResponseEntity<ApiResponse<Void>> selfPlan(RuntimeException e) {
		return fail(HttpStatus.CONFLICT, "COURSE_FEEDBACK_SELF_PLAN", e.getMessage(), List.of());
	}

	private static ResponseEntity<ApiResponse<Void>> fail(HttpStatus status, String code, String message,
			List<String> fields) {
		return ResponseEntity.status(status)
			.body(ApiResponse.failure(new ApiError(code, message, fields), "req_" + UUID.randomUUID()));
	}

	static String rid(String requestId) {
		return requestId == null || requestId.isBlank() ? UUID.randomUUID().toString() : requestId;
	}
}
