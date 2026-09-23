package com.gabolle.backend.itinerary.presentation;

import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.common.security.AuthenticatedUsers;
import com.gabolle.backend.itinerary.application.TripCourseService;
import com.gabolle.backend.itinerary.presentation.dto.TripCoursesResponse;
import com.gabolle.backend.trip.application.TripQueryService;

/**
 * 추천 코스 3안 (S15P21E201-1454).
 *
 * <ul>
 *   <li>{@code GET /api/v1/trips/{tripId}/recommendations} — 견줄 코스 안들</li>
 *   <li>{@code POST /api/v1/trips/{tripId}/course} — 고른 안을 일정으로 만들고 그 번호를 준다</li>
 * </ul>
 *
 * <p>경로는 여행 밑이고 이름은 「추천」이지만 돌려주는 것은 일정 모양이라 {@code itinerary} 패키지에 둔다
 * ({@link TripItineraryController} 와 같은 이유). 오류 번역도 {@link TripActivityExceptionHandler} 가
 * 함께 맡는다 — 없는 여행과 비회원을 같은 404 로 답하는 규칙을 한 곳에만 둔다.
 */
@RestController
@RequestMapping("/api/v1/trips/{tripId}")
@Profile({ "db", "dev" })
@ConditionalOnBean(TripQueryService.class)
public class TripCourseController {

	private final TripCourseService courseService;

	public TripCourseController(TripCourseService courseService) {
		this.courseService = courseService;
	}

	@GetMapping("/recommendations")
	public ApiResponse<TripCoursesResponse> courses(@PathVariable String tripId, Authentication authentication) {
		String requester = AuthenticatedUsers.requireId(authentication).toString();
		return ApiResponse.success(this.courseService.list(tripId, requester), "req_" + UUID.randomUUID());
	}

	@PostMapping("/course")
	public ApiResponse<TripCoursesResponse.Chosen> choose(@PathVariable String tripId,
			@RequestBody TripCoursesResponse.ChooseRequest request, Authentication authentication) {
		String requester = AuthenticatedUsers.requireId(authentication).toString();
		String itineraryId = this.courseService.choose(tripId, request.courseId(), requester);
		return ApiResponse.success(new TripCoursesResponse.Chosen(itineraryId), "req_" + UUID.randomUUID());
	}
}
