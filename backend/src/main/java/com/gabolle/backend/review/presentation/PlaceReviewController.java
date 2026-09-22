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
 * 장소 리뷰.
 *
 * 사용자는 인증 principal 에서만 얻는다 — 요청 본문에 인증 여부나 사용자 아이디를 싣지 않는다.
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
		// 점수 범위 검사는 이 record 의 컴팩트 생성자에서 일어난다.
		PlaceReview.Scores scores = new PlaceReview.Scores(request.foodScore(), request.priceScore(),
				request.accessibilityScore(), request.onsiteScore());
		PlaceReview saved = this.placeReviewService.write(placeId, userId, scores, request.body(), request.region());
		// 작성자 여부를 손으로 true 로 박지 않고 목록과 같은 판정을 통과시킨다 — 쓰기와 목록이
		// 다른 규칙을 쓰면 언젠가 갈라진다.
		return ApiResponse.success(PlaceReviewResponse.from(saved, userId), resolveRequestId(requestId));
	}

	/** 방문 인증 여부와 무관하게 전부 보여준다. 인증으로 걸러지는 것은 평균 계산뿐이다. */
	@GetMapping
	public ApiResponse<PlaceReviewListResponse> list(@PathVariable UUID placeId, Authentication authentication,
			@RequestHeader(value = "X-Request-Id", required = false) String requestId) {

		// 🔴 읽기는 로그인 없이 연다 — S15P21E201-1373. 장소 상세는 이미 익명에게
		//    열려 있는데 그 안의 리뷰만 401 이었다. 쓰기(POST)는 아래 그대로 로그인이 필요하다.
		//    viewer 가 없으면 «내가 쓴 리뷰» 표시가 전부 거짓으로 나간다(Objects.equals 가 그렇게 한다).
		UUID userId = AuthenticatedUsers.optionalId(authentication).orElse(null);
		PlaceReviewService.ListResult result = this.placeReviewService.list(placeId);
		return ApiResponse.success(PlaceReviewListResponse.from(result, userId), resolveRequestId(requestId));
	}

	private String resolveRequestId(String requestId) {
		return requestId == null || requestId.isBlank() ? UUID.randomUUID().toString() : requestId;
	}
}
