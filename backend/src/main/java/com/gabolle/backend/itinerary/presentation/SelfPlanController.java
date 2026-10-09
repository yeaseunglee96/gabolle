package com.gabolle.backend.itinerary.presentation;

import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.common.security.AuthenticatedUsers;
import com.gabolle.backend.itinerary.application.SelfPlanItineraryService;
import com.gabolle.backend.itinerary.presentation.dto.TripCoursesResponse;
import com.gabolle.backend.trip.application.TripQueryService;

/**
 * {@code POST /api/v1/trips/{tripId}/itineraries/blank} — 직접 짜기용 빈 일정 (S15P21E201-2018).
 * 오류 번역은 {@link TripActivityExceptionHandler} 가 맡는다(없는 여행·비회원 = 404, 보기만 하는 회원 = 403).
 */
@RestController
@RequestMapping("/api/v1/trips/{tripId}")
@Profile({ "db", "dev" })
@ConditionalOnBean(TripQueryService.class)
public class SelfPlanController {

	private final SelfPlanItineraryService selfPlanService;

	public SelfPlanController(SelfPlanItineraryService selfPlanService) {
		this.selfPlanService = selfPlanService;
	}

	@PostMapping("/itineraries/blank")
	public ResponseEntity<ApiResponse<TripCoursesResponse.Chosen>> createBlank(@PathVariable String tripId,
			Authentication authentication) {
		String requester = AuthenticatedUsers.requireTripActor(authentication);
		String itineraryId = this.selfPlanService.createBlank(tripId, requester);
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(ApiResponse.success(new TripCoursesResponse.Chosen(itineraryId), "req_" + UUID.randomUUID()));
	}
}
