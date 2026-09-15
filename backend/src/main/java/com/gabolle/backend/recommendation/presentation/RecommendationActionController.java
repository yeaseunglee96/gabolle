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
 * 추천 후보의 담아두기·빼기 — S15P21E201-1013.
 *
 * <pre>
 * GET    /api/v1/trips/{tripId}/recommendation-actions            이 여행에서 내가 내린 판단 전부
 * PUT    /api/v1/trips/{tripId}/recommendation-actions/{placeId}  한 장소의 판단을 적는다(덮어쓴다)
 * DELETE /api/v1/trips/{tripId}/recommendation-actions/{placeId}  그 판단을 거둔다
 * </pre>
 *
 * <p>🔴 <b>PUT 이고 POST 가 아닌 이유.</b> 화면의 하트는 연타할 수 있고 통신이 끊기면 앱이
 * 재시도한다. "눌렀다" 를 더하는 모양이면 그때마다 행이 쌓이거나 상태가 뒤집힌다.
 * "이 장소의 판단은 이것이다" 를 통째로 적으면 몇 번을 보내도 같은 결과다.
 *
 * <p>배선은 {@link RecommendationJobController} 와 같다 — {@code @Profile({"db","dev"})} ·
 * {@code @ConditionalOnBean}. 이유는 그 클래스와 {@link RecommendationActionService} 의
 * javadoc 에 있다.
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
	 * 없는 여행·남의 여행은 404 지만, 내 여행인데 아직 아무것도 안 눌렀으면
	 * <b>200 + 빈 목록</b>이다 — 「아직 안 눌렀다」와 「그런 여행이 없다」는 화면이 다르게 그린다.
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
			// 🔴 판단 없는 요청을 조용히 받아 주지 않는다. 받아 주면 "무엇으로 적혔는지" 를
			//    부르는 쪽도 서버도 모르는 행이 생긴다.
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
