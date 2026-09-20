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
 * 계정 기본 씀씀이 성향. 요청자 본인의 값만 다룬다 — 대상이 경로에 없어
 * {@code RouteAuthorizationRegistryTest} 에는 {@code OWNED} 로 등록한다.
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

		// 이벤트 계약의 request_id 는 늘 순수 UUID 다. 응답 envelope 의 요청 식별자와는
		// 다른 값이라(그쪽은 "req_" 가 붙는 자유 문자열) 여기서 새로 만든다.
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
