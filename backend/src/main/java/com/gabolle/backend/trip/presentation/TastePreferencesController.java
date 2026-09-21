package com.gabolle.backend.trip.presentation;

import java.util.List;
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
import com.gabolle.backend.trip.application.PreferenceDefaultsService;
import com.gabolle.backend.trip.domain.PreferenceSnapshot;
import com.gabolle.backend.trip.presentation.dto.CreateTripRequest;
import com.gabolle.backend.trip.presentation.dto.TastePreferencesRequest;
import com.gabolle.backend.trip.presentation.dto.TastePreferencesResponse;

import jakarta.validation.Valid;

/**
 * 계정 기본 취향. 요청자 본인의 값만 다룬다 — 대상이 경로에 없어
 * {@code RouteAuthorizationRegistryTest} 에는 {@code OWNED} 로 등록한다.
 *
 * <p>{@code PreferenceDefaultsService} 의 {@code CARRY_OVER} 다섯(로컬성·조용함·관광지
 * 선호·음식 취향·경사)만 받고 그 밖의 차원은 400 으로 거절한다 — 조용히 버리면 화면은
 * 저장된 줄 알고 다음에 빈칸을 본다. {@code SPEND_PROFILE} 도 거절한다. 그 차원은
 * {@code /spend} 가 맡고 있고, 두 경로가 같은 차원을 쓰면 나중에 온 쪽이 앞의 것을 덮는다.
 */
@RestController
@RequestMapping("/api/v1/me/preferences")
@Profile({ "db", "dev" })
public class TastePreferencesController {

	private final PreferenceDefaultsService service;

	public TastePreferencesController(PreferenceDefaultsService service) {
		this.service = service;
	}

	/** 보낸 차원만 바꾸고 안 보낸 것은 그대로 둔다. 응답은 바꾼 뒤의 전부다. */
	@PutMapping("/taste")
	public ApiResponse<TastePreferencesResponse> put(Authentication authentication,
			@Valid @RequestBody TastePreferencesRequest request,
			@RequestHeader(value = "X-Request-Id", required = false) String requestId) {

		UUID userId = AuthenticatedUsers.requireId(authentication);

		this.service.putTaste(userId.toString(), toAnswers(request.answers()));

		return ApiResponse.success(TastePreferencesResponse.of(this.service.findTaste(userId.toString())),
				resolveRequestId(requestId));
	}

	@GetMapping("/taste")
	public ApiResponse<TastePreferencesResponse> get(Authentication authentication,
			@RequestHeader(value = "X-Request-Id", required = false) String requestId) {

		UUID userId = AuthenticatedUsers.requireId(authentication);

		return ApiResponse.success(TastePreferencesResponse.of(this.service.findTaste(userId.toString())),
				resolveRequestId(requestId));
	}

	private static List<PreferenceSnapshot.PreferenceAnswer> toAnswers(
			List<CreateTripRequest.PreferenceAnswerInput> inputs) {

		return inputs == null ? List.of() : inputs.stream()
				.map(p -> new PreferenceSnapshot.PreferenceAnswer(
						p.dimension(), p.value(), parseAnswerStatus(p.answerStatus())))
				.toList();
	}

	/**
	 * {@code CreateTripRequestMapper} 의 같은 이름 메서드를 문구까지 베껴 둔 것이다(그쪽이
	 * {@code private} 이라 못 부른다). 한쪽만 고치면 같은 값이 경로에 따라 다른 말을 듣는다.
	 */
	private static PreferenceSnapshot.AnswerStatus parseAnswerStatus(String raw) {
		try {
			return PreferenceSnapshot.AnswerStatus.valueOf(raw.toUpperCase());
		}
		catch (IllegalArgumentException | NullPointerException notAStatus) {
			throw new IllegalArgumentException(
					"취향의 answerStatus 는 SELECTED · SKIPPED · UNKNOWN 중 하나여야 한다: " + raw);
		}
	}

	private String resolveRequestId(String requestId) {
		return requestId == null || requestId.isBlank() ? "req_" + UUID.randomUUID() : requestId;
	}
}
