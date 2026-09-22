package com.gabolle.backend.recommendation.presentation;

import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.common.security.AuthenticatedUsers;
import com.gabolle.backend.recommendation.application.RecommendationActionService;
import com.gabolle.backend.recommendation.domain.RecommendationPlaceAction;
import com.gabolle.backend.recommendation.presentation.dto.RecommendationActionResponse;

/**
 * 추천 후보의 담아두기·빼기.
 *
 * POST 가 아니라 PUT 인 것은 화면의 하트를 연타할 수 있고 통신이 끊기면 앱이 재시도하기
 * 때문이다 — 판단을 통째로 적으면 몇 번을 보내도 같은 결과다.
 */
@RestController
@Profile({ "db", "dev" })
@ConditionalOnBean(RecommendationActionService.class)
public class RecommendationActionController {

	private final RecommendationActionService service;

	public RecommendationActionController(RecommendationActionService service) {
		this.service = service;
	}

	/**
	 * 없는 여행·남의 여행은 404 지만, 내 여행인데 아직 아무것도 안 눌렀으면 200 과 빈 목록이다 —
	 * 아직 안 눌렀다와 그런 여행이 없다는 화면이 다르게 그린다.
	 */
	@GetMapping("/api/v1/trips/{tripId}/recommendation-actions")
	public ApiResponse<RecommendationActionResponse.Page> list(@PathVariable String tripId,
			Authentication authentication) {

		String requester = AuthenticatedUsers.requireId(authentication).toString();
		return ApiResponse.success(RecommendationActionResponse.Page.of(this.service.list(tripId, requester)),
				"req_" + UUID.randomUUID());
	}

	@PutMapping("/api/v1/trips/{tripId}/recommendation-actions/{placeId}")
	public ApiResponse<RecommendationActionResponse> put(@PathVariable String tripId, @PathVariable String placeId,
			@RequestBody PutRequest request, Authentication authentication) {

		String requester = AuthenticatedUsers.requireId(authentication).toString();
		if (request == null || request.action() == null) {
			// 판단 없는 요청을 받아 주면 무엇으로 적혔는지 아무도 모르는 행이 생긴다.
			throw new IllegalArgumentException("action 은 필수다 (SAVED 또는 EXCLUDED)");
		}

		RecommendationPlaceAction saved = this.service.put(tripId, requester, placeId, request.action());
		return ApiResponse.success(RecommendationActionResponse.of(saved), "req_" + UUID.randomUUID());
	}

	/** 없는 판단을 거둬도 성공이다 — 이유는 {@link RecommendationActionService#remove}. */
	@DeleteMapping("/api/v1/trips/{tripId}/recommendation-actions/{placeId}")
	public ResponseEntity<Void> remove(@PathVariable String tripId, @PathVariable String placeId,
			Authentication authentication) {

		String requester = AuthenticatedUsers.requireId(authentication).toString();
		this.service.remove(tripId, requester, placeId);
		return ResponseEntity.noContent().build();
	}

	/** {@code {"action":"SAVED"}} 또는 {@code {"action":"EXCLUDED"}}. */
	public record PutRequest(RecommendationPlaceAction.Action action) {
	}
}
