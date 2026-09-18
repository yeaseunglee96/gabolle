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
 * 여행 조건 모달의 답 — S15P21E201-1231.
 *
 * <pre>
 * GET /api/v1/me/preferences/constraints   →  { status, value }
 * PUT /api/v1/me/preferences/constraints   ←  { answerStatus, value }
 * </pre>
 *
 * <p>🔴 대상이 요청 경로에 없다. 요청자 본인의 값만 다룬다 — 남의 것을 지정할 방법이 없으므로
 * {@code RouteAuthorizationRegistryTest} 에는 {@code OWNED} 로 등록한다
 * ({@link SpendProfileController} 와 같은 근거).
 *
 * <h2>🔴 한 번도 저장 안 했으면 404 가 아니라 200 이다</h2>
 *
 * {@code {status: null, value: null}} 을 낸다. 「안 물어봤다」는 오류가 아니라 정상 상태이고,
 * 404 로 답하면 화면이 그것을 오류로 다뤄야 한다.
 *
 * <h2>상태 넷 중 셋만 이 경로로 들어온다</h2>
 *
 * 넷째인 「안 물어봄」은 사람이 고르는 답이 아니라 <b>행이 없는 것</b>이라, 요청으로 보낼 수
 * 없다. 보낼 수 있게 두면 앱이 「안 물어본 상태로 되돌리기」를 할 수 있게 되는데, 그건
 * 모달을 다시 띄우는 일이지 답이 아니다.
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

		// 🔴 이벤트 계약의 request_id(UUID 칸)와 HTTP 응답 envelope 의 요청 식별자는 다른 것이다 —
		//    뒤엣것은 사람이 읽는 자유 문자열이고("req_" 접두사가 붙을 수 있다), 앞엣것은 늘
		//    순수 UUID 다. SpendProfileController 가 같은 자리에 같은 주석을 달고 있다.
		TravelConstraintAnswer saved = this.service.put(userId, request.value(), request.answerStatus(),
				UUID.randomUUID());

		return ApiResponse.success(toResponse(saved), resolveRequestId(requestId));
	}

	// 🔴 여기가 표와 화면 계약이 갈리는 자리다. 컨트롤러는 JPA 엔티티를 안 본다 —
	//    LayeringArchitectureTest 가 그것을 막고, 근거는 TravelConstraintAnswer 주석에 있다.
	private static TravelConstraintResponse toResponse(TravelConstraintAnswer answer) {
		return new TravelConstraintResponse(answer.status().name(), answer.valueJson());
	}

	private String resolveRequestId(String requestId) {
		return requestId == null || requestId.isBlank() ? "req_" + UUID.randomUUID() : requestId;
	}
}
