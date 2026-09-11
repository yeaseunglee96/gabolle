package com.gabolle.backend.story.presentation;

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
import com.gabolle.backend.story.application.TripStoryService;
import com.gabolle.backend.story.presentation.dto.TripStoriesResponse;
import com.gabolle.backend.trip.application.TripQueryService;

/**
 * {@code GET /api/v1/trips/{tripId}/stories} — 그 여행에 달린 기록 (S15P21E201-829).
 *
 * <p>경로는 여행 밑에 있지만 읽는 것은 기록이라 {@code story} 패키지에 둔다 —
 * {@code TripItineraryController} 가 같은 이유로 {@code itinerary} 에 있다.
 * {@code TripController}({@code /api/v1/trips}) 와 매핑이 겹치지 않는다: 그쪽은 목록과
 * {@code /{tripId}} 만 받고 여기는 {@code /{tripId}/stories} 다.
 *
 * <p>오류 번역은 {@link TripStoryExceptionHandler} 가 맡는다. {@link StoryExceptionHandler} 에
 * 끼우지 않은 이유는 이 경로가 내는 404 의 주체가 <b>기록이 아니라 여행</b>이기 때문이다 —
 * 같은 advice 에 넣으면 {@code STORY_NOT_FOUND} 와 {@code TRIP_NOT_FOUND} 중 무엇을 낼지가
 * 예외 종류에만 달리게 되고, 화면이 "없는 여행" 과 "없는 기록" 을 구분할 근거가 흐려진다.
 */
@RestController
@RequestMapping("/api/v1/trips/{tripId}")
@Profile({ "db", "dev" })
@ConditionalOnBean(TripQueryService.class)
public class TripStoryController {

	private final TripStoryService tripStoryService;

	public TripStoryController(TripStoryService tripStoryService) {
		this.tripStoryService = tripStoryService;
	}

	@GetMapping("/stories")
	public ApiResponse<TripStoriesResponse> stories(@PathVariable String tripId, Authentication authentication) {
		UUID viewer = AuthenticatedUsers.requireId(authentication);
		return ApiResponse.success(this.tripStoryService.list(tripId, viewer), "req_" + UUID.randomUUID());
	}
}
