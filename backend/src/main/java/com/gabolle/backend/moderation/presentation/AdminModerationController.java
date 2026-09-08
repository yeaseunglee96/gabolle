package com.gabolle.backend.moderation.presentation;

import java.util.UUID;

import org.springframework.context.annotation.Profile;
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
 * 신고 검토 목록과 삭제·기각 — S15P21E201-267. 운영자(ADMIN) 전용.
 *
 * <pre>
 * GET  /api/v1/admin/story-reports                신고된 기록 목록(오래된 신고 순)
 * POST /api/v1/admin/story-reports/{storyId}/remove   삭제로 처리(기록·사진 함께 제거)
 * POST /api/v1/admin/story-reports/{storyId}/dismiss  기각(기록이 다시 보인다)
 * </pre>
 *
 * <h2>🔴 인가는 이 컨트롤러가 하지 않는다 — {@code @PreAuthorize} 를 쓰지 않는 이유</h2>
 * 이 저장소는 메서드 보안({@code @EnableMethodSecurity})이 <b>꺼져 있다.</b> 그 상태에서
 * {@code @PreAuthorize} 를 붙이면 컴파일도 되고 코드 리뷰에서도 "막혀 있다" 로 보이지만 실제로는
 * <b>조용히 무시되어 아무도 막지 않는다</b> — 붙여 놓고 안 막히는 것이 인가 코드가 아예 없는 것보다
 * 나쁘다. 그래서 이 경로의 인가는 전부 {@code SecurityConfig} 의
 * {@code requestMatchers("/api/v1/admin/**").hasRole("ADMIN")} 한 줄이 한다. 그 규칙이 요청을
 * 여기까지 들여보낸 시점에는 이미 ADMIN 임이 확인된 것이고, {@link AuthenticatedUsers#requireId} 는
 * "누구인가" 만 얻는다(role 을 다시 확인하지 않는다).
 */
@RestController
@RequestMapping("/api/v1/admin/story-reports")
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

	/** 클라이언트가 준 추적 아이디를 그대로 쓴다 — {@code PlaceDetailController} 와 같은 패턴이다. */
	private static String resolveRequestId(String requestId) {
		return requestId == null || requestId.isBlank() ? UUID.randomUUID().toString() : requestId;
	}
}
