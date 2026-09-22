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
import com.gabolle.backend.trip.application.TravelConstraintService;
import com.gabolle.backend.trip.domain.TravelConstraintAnswer;
import com.gabolle.backend.trip.presentation.dto.TravelConstraintInput;
import com.gabolle.backend.trip.presentation.dto.TravelConstraintResponse;

import jakarta.validation.Valid;

/**
 * 여행 조건 모달의 답. 요청자 본인의 값만 다룬다 — 대상이 경로에 없어
 * {@code RouteAuthorizationRegistryTest} 에는 {@code OWNED} 로 등록한다.
 *
 * <p>한 번도 저장 안 했으면 404 가 아니라 {@code {status: null, value: null}} 로 200 이다.
 * 안 물어본 것은 오류가 아니라 정상 상태다.
 *
 * <p>상태 넷 중 셋만 이 경로로 들어온다. 넷째인 안 물어봄은 행이 없는 것이라 요청으로 보낼
 * 수 없다 — 보낼 수 있게 두면 모달을 다시 띄우는 일이 답인 척하게 된다.
 */
@RestController
@RequestMapping("/api/v1/me/preferences")
@Profile({ "db", "dev" })
public class TravelConstraintController {

	private final TravelConstraintService service;

	public TravelConstraintController(TravelConstraintService service) {
		this.service = service;
	}

	@GetMapping("/constraints")
	public ApiResponse<TravelConstraintResponse> get(Authentication authentication,
			@RequestHeader(value = "X-Request-Id", required = false) String requestId) {

		UUID userId = AuthenticatedUsers.requireId(authentication);

		TravelConstraintResponse body = this.service.find(userId)
				.map(TravelConstraintController::toResponse)
				.orElseGet(TravelConstraintResponse::neverAsked);

		return ApiResponse.success(body, resolveRequestId(requestId));
	}

	@PutMapping("/constraints")
	public ApiResponse<TravelConstraintResponse> put(Authentication authentication,
			@Valid @RequestBody TravelConstraintInput request,
			@RequestHeader(value = "X-Request-Id", required = false) String requestId) {

		UUID userId = AuthenticatedUsers.requireId(authentication);

		// 이벤트 계약의 request_id 는 늘 순수 UUID 다. 응답 envelope 의 요청 식별자와는
		// 다른 값이라(그쪽은 "req_" 가 붙는 자유 문자열) 여기서 새로 만든다.
		TravelConstraintAnswer saved = this.service.put(userId, request.value(), request.answerStatus(),
				UUID.randomUUID());

		return ApiResponse.success(toResponse(saved), resolveRequestId(requestId));
	}

	// 컨트롤러는 JPA 엔티티를 안 본다 — LayeringArchitectureTest 가 그것을 막는다.
	private static TravelConstraintResponse toResponse(TravelConstraintAnswer answer) {
		return new TravelConstraintResponse(answer.status().name(), answer.valueJson());
	}

	private String resolveRequestId(String requestId) {
		return requestId == null || requestId.isBlank() ? "req_" + UUID.randomUUID() : requestId;
	}
}
