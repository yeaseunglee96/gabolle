package com.gabolle.backend.review.presentation;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.common.security.AuthenticatedUsers;
import com.gabolle.backend.review.application.PlaceReviewService;
import com.gabolle.backend.review.domain.PlaceReview;
import com.gabolle.backend.review.presentation.dto.PlaceReviewListResponse;
import com.gabolle.backend.review.presentation.dto.PlaceReviewRequest;
import com.gabolle.backend.review.presentation.dto.PlaceReviewResponse;

/**
 * 장소 리뷰 — S15P21E201-287 · -408.
 *
 * <p>🔴 사용자를 <b>인증 principal</b> 에서 얻는다({@link AuthenticatedUsers#requireId}) — 요청
 * 본문에 인증 여부나 사용자 아이디를 실어 보내지 않는다.
 */
@RestController
@RequestMapping("/api/v1/places/{placeId}/reviews")
@Profile({ "db", "dev" })
public class PlaceReviewController {

	private final PlaceReviewService placeReviewService;

	public PlaceReviewController(PlaceReviewService placeReviewService) {
		this.placeReviewService = placeReviewService;
	}

	@PostMapping
	public ApiResponse<PlaceReviewResponse> write(@PathVariable UUID placeId,
			@Valid @RequestBody PlaceReviewRequest request,
			Authentication authentication,
			@RequestHeader(value = "X-Request-Id", required = false) String requestId) {

		UUID userId = AuthenticatedUsers.requireId(authentication);
		// 🔴 범위 검사는 이 record 의 컴팩트 생성자에서 일어난다 — PlaceReviewRequest javadoc 참고.
		PlaceReview.Scores scores = new PlaceReview.Scores(request.foodScore(), request.priceScore(),
				request.accessibilityScore(), request.onsiteScore());
		PlaceReview saved = this.placeReviewService.write(placeId, userId, scores, request.body(), request.region());
		// 🔴 방금 쓴 사람이 곧 작성자라 true 가 나올 것이지만, 손으로 true 를 박지 않고 목록과
		// 같은 판정 함수(PlaceReviewResponse.from)를 통과시킨다 — 쓰기와 목록이 다른 규칙을
		// 쓰면 언젠가 갈라진다.
		return ApiResponse.success(PlaceReviewResponse.from(saved, userId), resolveRequestId(requestId));
	}

	/** 🔴 인증 여부와 무관하게 전부 보여준다 — 목록에서 걸러지는 것은 로컬 점수 계산뿐이다. */
	@GetMapping
	public ApiResponse<PlaceReviewListResponse> list(@PathVariable UUID placeId, Authentication authentication,
			@RequestHeader(value = "X-Request-Id", required = false) String requestId) {

		UUID userId = AuthenticatedUsers.requireId(authentication);
		PlaceReviewService.ListResult result = this.placeReviewService.list(placeId);
		return ApiResponse.success(PlaceReviewListResponse.from(result, userId), resolveRequestId(requestId));
	}

	private String resolveRequestId(String requestId) {
		return requestId == null || requestId.isBlank() ? UUID.randomUUID().toString() : requestId;
	}
}
