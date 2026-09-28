package com.gabolle.backend.moderation.presentation;

import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.common.security.AuthenticatedUsers;
import com.gabolle.backend.moderation.application.ModerationQueueService;
import com.gabolle.backend.moderation.presentation.dto.ModerationActionResponse;
import com.gabolle.backend.moderation.presentation.dto.ModerationQueueResponse;

/**
 * 신고 검토 목록과 삭제·기각. 운영자(ADMIN) 전용.
 *
 * 🔴 S15P21E201-1548 — 인가는 이제 **이중**이다. {@code SecurityConfig} 의
 * {@code requestMatchers("/api/v1/admin/**").hasRole("ADMIN")} 이 경로로 막고, 이 클래스의
 * {@code @PreAuthorize} 가 메서드 보안({@code @EnableMethodSecurity}, {@code SecurityConfig} 에서
 * 켜짐)으로 한 번 더 막는다. 둘 중 하나만 있어도 막히므로, 이 컨트롤러가 실수로
 * {@code /api/v1/admin/} 밖으로 옮겨지거나 애너테이션이 지워져도 나머지 하나가 여전히 막는다.
 * {@link AuthenticatedUsers#requireId} 는 "누구인가" 만 얻는다.
 */
@RestController
@RequestMapping("/api/v1/admin/story-reports")
@PreAuthorize("hasRole('ADMIN')")
@Profile({ "db", "dev" })
public class AdminModerationController {

	private final ModerationQueueService moderationQueueService;

	public AdminModerationController(ModerationQueueService moderationQueueService) {
		this.moderationQueueService = moderationQueueService;
	}

	@GetMapping
	public ApiResponse<ModerationQueueResponse> queue(@RequestParam(value = "limit", required = false) Integer limit,
			Authentication authentication, @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
		AuthenticatedUsers.requireId(authentication);
		return ApiResponse.success(this.moderationQueueService.list(limit), resolveRequestId(requestId));
	}

	@PostMapping("/{storyId}/remove")
	public ApiResponse<ModerationActionResponse> remove(@PathVariable UUID storyId, Authentication authentication,
			@RequestHeader(value = "X-Request-Id", required = false) String requestId) {
		UUID admin = AuthenticatedUsers.requireId(authentication);
		return ApiResponse.success(this.moderationQueueService.remove(storyId, admin), resolveRequestId(requestId));
	}

	@PostMapping("/{storyId}/dismiss")
	public ApiResponse<ModerationActionResponse> dismiss(@PathVariable UUID storyId, Authentication authentication,
			@RequestHeader(value = "X-Request-Id", required = false) String requestId) {
		UUID admin = AuthenticatedUsers.requireId(authentication);
		return ApiResponse.success(this.moderationQueueService.dismiss(storyId, admin), resolveRequestId(requestId));
	}

	/** 클라이언트가 준 추적 아이디를 그대로 쓰고, 없을 때만 새로 만든다. */
	private static String resolveRequestId(String requestId) {
		return requestId == null || requestId.isBlank() ? UUID.randomUUID().toString() : requestId;
	}
}
