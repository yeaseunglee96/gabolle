package com.gabolle.backend.itinerary.presentation;

import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.common.security.AuthenticatedUsers;
import com.gabolle.backend.itinerary.application.TripActivityService;
import com.gabolle.backend.itinerary.presentation.dto.TripActivityResponse;
import com.gabolle.backend.trip.application.TripQueryService;

/**
 * {@code GET /api/v1/trips/{tripId}/activity} — 여행의 최근 변경.
 * 경로는 여행 밑에 있지만 읽는 것은 일정의 판이라 {@code itinerary} 패키지에 둔다.
 * {@code TripController} 와 매핑이 겹치지 않는다 — 그쪽은 {@code /{tripId}} 만 받는다.
 */
@RestController
@RequestMapping("/api/v1/trips/{tripId}")
@Profile({ "db", "dev" })
@ConditionalOnBean(TripQueryService.class)
public class TripActivityController {

	private final TripActivityService activityService;

	public TripActivityController(TripActivityService activityService) {
		this.activityService = activityService;
	}

	@GetMapping("/activity")
	public ApiResponse<TripActivityResponse> activity(@PathVariable String tripId,
			@RequestParam(name = "limit", defaultValue = "" + TripActivityService.DEFAULT_LIMIT) int limit,
			Authentication authentication) {

		String requester = AuthenticatedUsers.requireTripActor(authentication);
		TripActivityResponse response = this.activityService.list(tripId, requester, limit);
		return ApiResponse.success(response, "req_" + UUID.randomUUID());
	}
}
