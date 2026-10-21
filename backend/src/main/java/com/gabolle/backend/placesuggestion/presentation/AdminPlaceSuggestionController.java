package com.gabolle.backend.placesuggestion.presentation;

import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.common.security.AuthenticatedUsers;
import com.gabolle.backend.placesuggestion.application.PlaceSuggestionService;
import com.gabolle.backend.placesuggestion.application.PlaceSuggestionService.Suggestion;
import com.gabolle.backend.placesuggestion.presentation.PlaceSuggestionController.Items;

/**
 * 장소 제안 검토 — 운영자(ADMIN, 배포 설정 {@code GABOLLE_ADMIN_EMAILS}) 전용(S15P21E201-2031).
 * 신고 검토와 같이 경로({@code /api/v1/admin/**})와 {@code @PreAuthorize} 두 겹으로 막는다.
 */
@RestController
@RequestMapping("/api/v1/admin/place-suggestions")
@PreAuthorize("hasRole('ADMIN')")
@Profile({ "db", "dev" })
public class AdminPlaceSuggestionController {

	private final PlaceSuggestionService service;

	public AdminPlaceSuggestionController(PlaceSuggestionService service) {
		this.service = service;
	}

	public record RejectRequest(String reason) {
	}

	@GetMapping
	public ApiResponse<Items<Suggestion>> list(@RequestParam(value = "status", required = false) String status,
			Authentication authentication, @RequestHeader(value = "X-Request-Id", required = false) String requestId) {
		AuthenticatedUsers.requireId(authentication);
		return ApiResponse.success(new Items<>(this.service.review(status)), PlaceSuggestionController.rid(requestId));
	}

	@PostMapping("/{suggestionId}/approve")
	public ApiResponse<Suggestion> approve(@PathVariable UUID suggestionId, Authentication authentication,
			@RequestHeader(value = "X-Request-Id", required = false) String requestId) {
		UUID admin = AuthenticatedUsers.requireId(authentication);
		return ApiResponse.success(this.service.approve(suggestionId, admin), PlaceSuggestionController.rid(requestId));
	}

	@PostMapping("/{suggestionId}/reject")
	public ApiResponse<Suggestion> reject(@PathVariable UUID suggestionId,
			@RequestBody(required = false) RejectRequest body, Authentication authentication,
			@RequestHeader(value = "X-Request-Id", required = false) String requestId) {
		UUID admin = AuthenticatedUsers.requireId(authentication);
		return ApiResponse.success(this.service.reject(suggestionId, admin, body == null ? null : body.reason()),
				PlaceSuggestionController.rid(requestId));
	}
}
