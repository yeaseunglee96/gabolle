package com.gabolle.backend.place.api;

import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.place.service.PlaceCandidateQueryService;

import jakarta.validation.Valid;

/**
 * 추천 후보 사전 필터.
 *
 * <p>조회인데 {@code POST} 인 이유는 조건이 중첩된 객체 배열이라 질의 문자열에 담기 어렵기
 * 때문이다. 서버 상태를 바꾸지 않으므로 멱등하다.
 *
 * <p>부르는 것은 사용자 화면이 아니라 추천 계산이다. 그래서 응답에 랭킹 점수가 없다 — 점수는 이
 * 뒤 단계의 몫이고, 여기서는 무엇을 후보로 볼 것인가만 정한다.
 */
@RestController
@RequestMapping("/api/v1/places")
@Profile({"db", "dev"})
public class PlaceCandidateController {

	private final PlaceCandidateQueryService placeCandidateQueryService;

	public PlaceCandidateController(PlaceCandidateQueryService placeCandidateQueryService) {
		this.placeCandidateQueryService = placeCandidateQueryService;
	}

	@PostMapping("/candidates")
	public ApiResponse<PlaceCandidateResponse> findCandidates(
			@Valid @RequestBody PlaceCandidateRequest request,
			@RequestHeader(value = "X-Request-Id", required = false) String requestId) {

		return ApiResponse.success(this.placeCandidateQueryService.findCandidates(request),
				resolveRequestId(requestId));
	}

	private String resolveRequestId(String requestId) {
		return requestId == null || requestId.isBlank() ? UUID.randomUUID().toString() : requestId;
	}
}
