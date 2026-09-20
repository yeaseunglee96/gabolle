package com.gabolle.backend.itinerary.presentation;

import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.common.security.AuthenticatedUsers;
import com.gabolle.backend.itinerary.application.TripItineraryService;
import com.gabolle.backend.itinerary.presentation.dto.TripItineraryResponse;
import com.gabolle.backend.trip.application.TripQueryService;

/**
 * {@code GET /api/v1/trips/{tripId}/itineraries} — 그 여행의 일정 목록.
 * 경로는 여행 밑에 있지만 읽는 것은 일정이라 {@code itinerary} 패키지에 둔다.
 * {@code TripController} 와 매핑이 겹치지 않는다 — 그쪽은 목록과 {@code /{tripId}} 만 받고
 * 여기는 {@code /{tripId}/itineraries} 다.
 * 오류 번역은 {@link TripActivityExceptionHandler} 가 함께 맡는다. 없는 여행과 비회원을 같은
 * 404 로 답하는 규칙이 두 경로에서 같아야 하는데, 여기에 advice 를 따로 두면 한쪽만 고쳐진다.
 */
@RestController
@RequestMapping("/api/v1/trips/{tripId}")
@Profile({ "db", "dev" })
@ConditionalOnBean(TripQueryService.class)
public class TripItineraryController {

	private final TripItineraryService itineraryService;

	public TripItineraryController(TripItineraryService itineraryService) {
		this.itineraryService = itineraryService;
	}

	@GetMapping("/itineraries")
	public ApiResponse<TripItineraryResponse> itineraries(@PathVariable String tripId,
			Authentication authentication) {

		String requester = AuthenticatedUsers.requireId(authentication).toString();
		return ApiResponse.success(this.itineraryService.list(tripId, requester), "req_" + UUID.randomUUID());
	}
}
