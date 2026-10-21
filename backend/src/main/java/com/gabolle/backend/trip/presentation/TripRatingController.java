package com.gabolle.backend.trip.presentation;

import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.common.security.AuthenticatedUsers;
import com.gabolle.backend.trip.application.TripQueryService;
import com.gabolle.backend.trip.application.TripRatingService;

/**
 * 여행 별점 — {@code /api/v1/trips/{tripId}/rating}. 세 경로 모두 같은 모양(내 점수·평균·개수)을
 * 돌려준다 — 화면이 응답 하나로 줄을 다시 그린다.
 */
@RestController
@RequestMapping("/api/v1/trips/{tripId}/rating")
@Profile({ "db", "dev" })
@ConditionalOnBean(TripQueryService.class)
public class TripRatingController {

	private final TripRatingService service;

	public TripRatingController(TripRatingService service) {
		this.service = service;
	}

	@GetMapping
	public ApiResponse<TripRatingService.Rating> get(@PathVariable String tripId, Authentication authentication) {
		return ok(this.service.find(tripId, AuthenticatedUsers.requireId(authentication)));
	}

	@PutMapping
	public ApiResponse<TripRatingService.Rating> put(@PathVariable String tripId,
			@RequestBody(required = false) RateRequest request, Authentication authentication) {
		return ok(this.service.rate(tripId, AuthenticatedUsers.requireId(authentication),
				request == null ? null : request.score()));
	}

	@DeleteMapping
	public ApiResponse<TripRatingService.Rating> delete(@PathVariable String tripId, Authentication authentication) {
		return ok(this.service.clear(tripId, AuthenticatedUsers.requireId(authentication)));
	}

	private static ApiResponse<TripRatingService.Rating> ok(TripRatingService.Rating rating) {
		return ApiResponse.success(rating, "req_" + UUID.randomUUID());
	}

	/** {@code {"score": 1~5}}. */
	public record RateRequest(Integer score) {
	}
}
