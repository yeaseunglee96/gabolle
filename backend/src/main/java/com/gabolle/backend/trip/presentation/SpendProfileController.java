package com.gabolle.backend.trip.presentation;

import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.common.security.AuthenticatedUsers;
import com.gabolle.backend.trip.application.SpendProfileService;
import com.gabolle.backend.trip.domain.PreferenceSnapshot;
import com.gabolle.backend.trip.presentation.dto.SpendProfileAnswerInput;
import com.gabolle.backend.trip.presentation.dto.SpendProfileResponse;

import jakarta.validation.Valid;

/**
 * 계정 기본 씀씀이 성향 — S15P21E201-709.
 *
 * <p>🔴 대상이 요청 경로에 없다. 요청자 본인의 값만 다룬다 — 남의 것을 지정할 방법이
 * 없으므로 {@code RouteAuthorizationRegistryTest} 에는 {@code OWNED} 로 등록한다
 * ({@code /api/v1/auth/me} 계열과 같은 근거).
 */
@RestController
@RequestMapping("/api/v1/me/preferences")
@Profile({ "db", "dev" })
public class SpendProfileController {

	private final SpendProfileService service;

	public SpendProfileController(SpendProfileService service) {
		this.service = service;
	}

	@PutMapping("/spend")
	public ApiResponse<SpendProfileResponse> put(Authentication authentication,
			@Valid @RequestBody SpendProfileAnswerInput request,
			@RequestHeader(value = "X-Request-Id", required = false) String requestId) {

		UUID userId = AuthenticatedUsers.requireId(authentication);

		// 🔴 이벤트 계약의 request_id(UUID 칸, API-07)와 HTTP 응답 envelope 의 요청 식별자는
		// 다른 것이다 — 뒤엣것은 사람이 읽는 자유 문자열이고("req_" 접두사가 붙을 수 있다),
		// 앞엣것은 늘 순수 UUID 다. 이 이벤트는 무엇을 노출했는지와 잇는 대상이 없어서(추천
		// 요청과 달리) 매번 새로 만든다 — 두 값을 같은 것으로 착각하면 안 된다.
		PreferenceSnapshot.PreferenceAnswer saved = this.service.put(userId, request.value(),
				request.answerStatus(), UUID.randomUUID());

		return ApiResponse.success(toResponse(saved), resolveRequestId(requestId));
	}

	@GetMapping("/spend")
	public ApiResponse<SpendProfileResponse> get(Authentication authentication,
			@RequestHeader(value = "X-Request-Id", required = false) String requestId) {

		UUID userId = AuthenticatedUsers.requireId(authentication);

		SpendProfileResponse body = this.service.find(userId)
				.map(SpendProfileController::toResponse)
				.orElseGet(SpendProfileResponse::unknown);

		return ApiResponse.success(body, resolveRequestId(requestId));
	}

	private static SpendProfileResponse toResponse(PreferenceSnapshot.PreferenceAnswer answer) {
		return new SpendProfileResponse(answer.status().name(), answer.valueJson());
	}

	private String resolveRequestId(String requestId) {
		return requestId == null || requestId.isBlank() ? "req_" + UUID.randomUUID() : requestId;
	}
}
