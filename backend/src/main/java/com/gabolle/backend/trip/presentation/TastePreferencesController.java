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
 * 계정 기본 취향 — 온보딩과 마이페이지가 쓰는 경로 (S15P21E201-639).
 *
 * <h2>왜 이제 만드나</h2>
 *
 * <p>{@code PreferenceDefaultsService} javadoc 이 <i>"쓰기 쪽이 비어 있다 — 경로를 일부러
 * 만들지 않았다"</i> 고 적어 두었던 자리다. 까닭은 <i>"계정 기본 취향을 저장하는 경로가 API
 * 명세에 아직 없다"</i> 였다.
 *
 * <p>그 판단을 뒤집는 것이 아니라 <b>이미 난 길을 잇는다.</b>
 * {@code /api/v1/me/preferences/spend} 가 같은 일을 소비 성향 하나에 대해 하고 있고
 * ({@code SpendProfileController}, S15P21E201-709), 이것은 그 옆자리에
 * {@code /taste} 를 더하는 것이다. 경로 이름·응답 모양·인가 정책을 그쪽에서 그대로 가져온다.
 *
 * <h2>🔴 대상이 요청 경로에 없다</h2>
 *
 * <p>요청자 본인의 값만 다룬다 — 남의 것을 지정할 방법이 없으므로
 * {@code RouteAuthorizationRegistryTest} 에는 {@code OWNED} 로 등록한다
 * ({@code /api/v1/me/preferences/spend} 와 같은 근거).
 *
 * <h2>무엇을 담고 무엇을 안 담나</h2>
 *
 * <p>{@code PreferenceDefaultsService} 의 {@code CARRY_OVER} 다섯만 다룬다 — 로컬성 ·
 * 조용함 · 관광지 선호 · 음식 취향 · 경사. 그 밖의 차원은 <b>400 으로 거절한다.</b>
 * 조용히 버리면 화면은 저장된 줄 알고 다음에 빈칸을 본다.
 *
 * <p>🔴 {@code SPEND_PROFILE} 도 여기서는 거절한다. 그 차원은 {@code /spend} 가 맡고 있고,
 * 두 경로가 같은 차원을 각자 쓰면 나중에 온 쪽이 앞의 것을 덮는다.
 */
@RestController
@RequestMapping("/api/v1/me/preferences")
@Profile({ "db", "dev" })
public class TastePreferencesController {

	private final PreferenceDefaultsService service;

	public TastePreferencesController(PreferenceDefaultsService service) {
		this.service = service;
	}

	/**
	 * 보낸 차원만 바꾼다. 안 보낸 차원은 그대로 남는다.
	 *
	 * <p>응답은 <b>바꾼 뒤의 전부</b>다 — 바뀐 것만 주면 화면이 나머지를 따로 또 읽어야 한다.
	 */
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
	 * 🔴 {@code CreateTripRequestMapper} 의 같은 이름 메서드와 문구까지 같다. 그쪽이
	 * {@code private} 이라 부를 수 없어 옮겨 적었다 — 한쪽만 고치면 같은 잘못된 값이 경로에
	 * 따라 다른 말을 듣게 되므로, 고칠 때는 둘 다 고친다.
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
