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
 * {@code GET /api/v1/trips/{tripId}/stories} — 그 여행에 달린 기록.
 *
 * <p>경로는 여행 밑에 있지만 읽는 것은 기록이라 {@code story} 패키지에 둔다.
 *
 * <p>오류 번역을 {@link StoryExceptionHandler} 에 끼우지 않은 이유는 이 경로가 내는 404 의 주체가 기록이
 * 아니라 여행이기 때문이다 — 화면이 「없는 여행」과 「없는 기록」을 가를 수 있어야 한다.
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
