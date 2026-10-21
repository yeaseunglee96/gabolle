package com.gabolle.backend.feedback.presentation;

import java.util.List;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.common.security.AuthenticatedUsers;
import com.gabolle.backend.feedback.application.FeedbackService;
import com.gabolle.backend.feedback.application.FeedbackService.Feedback;
import com.gabolle.backend.feedback.application.FeedbackService.SubmitCommand;

import jakarta.servlet.http.HttpServletRequest;

/**
 * 의견 보내기 — S15P21E201-2049. 접수는 로그인 없이도 열려 있고(SecurityConfig), 목록·처리는 운영자(ADMIN) 전용이다 —
 * 경로({@code /api/v1/admin/**})와 {@code @PreAuthorize} 두 겹으로 막는다.
 */
@RestController
@Profile({ "db", "dev" })
public class FeedbackController {

	private final FeedbackService service;

	public FeedbackController(FeedbackService service) {
		this.service = service;
	}

	public record SubmitRequest(String type, String content, String replyEmail, List<String> screenshotUrls,
			String appVersion, String platform, String osVersion, String language, String screen, String tripId) {
	}

	public record UpdateRequest(String status, String adminReply) {
	}

	public record Items<T>(List<T> items) {
	}

	@PostMapping("/api/v1/feedback")
	@ResponseStatus(HttpStatus.CREATED)
	public ApiResponse<Feedback> submit(@RequestBody SubmitRequest body, Authentication authentication,
			HttpServletRequest request, @RequestHeader(value = "X-Device-Id", required = false) String deviceId,
			@RequestHeader(value = "X-Request-Id", required = false) String requestId) {
		return ApiResponse.success(this.service.submit(AuthenticatedUsers.optionalId(authentication), deviceId,
				clientIp(request), new SubmitCommand(body.type(), body.content(), body.replyEmail(),
						body.screenshotUrls(), body.appVersion(), body.platform(), body.osVersion(), body.language(),
						body.screen(), body.tripId())),
				rid(requestId));
	}

	@GetMapping("/api/v1/admin/feedback")
	@PreAuthorize("hasRole('ADMIN')")
	public ApiResponse<Items<Feedback>> list(@RequestParam(value = "status", required = false) String status,
			Authentication authentication, @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
		AuthenticatedUsers.requireId(authentication);
		return ApiResponse.success(new Items<>(this.service.list(status)), rid(requestId));
	}

	@PatchMapping("/api/v1/admin/feedback/{feedbackId}")
	@PreAuthorize("hasRole('ADMIN')")
	public ApiResponse<Feedback> update(@PathVariable UUID feedbackId, @RequestBody UpdateRequest body,
			Authentication authentication, @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
		UUID admin = AuthenticatedUsers.requireId(authentication);
		return ApiResponse.success(this.service.update(feedbackId, admin, body.status(), body.adminReply()),
				rid(requestId));
	}

	static String clientIp(HttpServletRequest request) {
		String forwardedFor = request.getHeader("X-Forwarded-For");
		if (forwardedFor != null && !forwardedFor.isBlank()) {
			return forwardedFor.split(",")[0].trim();
		}
		return String.valueOf(request.getRemoteAddr());
	}

	static String rid(String requestId) {
		return requestId == null || requestId.isBlank() ? UUID.randomUUID().toString() : requestId;
	}
}
