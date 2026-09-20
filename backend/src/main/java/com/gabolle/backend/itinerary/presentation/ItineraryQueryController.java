package com.gabolle.backend.itinerary.presentation;

import java.util.List;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.core.Authentication;

import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.common.security.AuthenticatedUsers;
import com.gabolle.backend.itinerary.application.ItineraryQueryService;
import com.gabolle.backend.itinerary.presentation.dto.ItineraryDetailResponse;
import com.gabolle.backend.itinerary.presentation.dto.ItineraryVersionSummaryResponse;
import com.gabolle.backend.itinerary.presentation.dto.ItineraryVersionsResponse;
import com.gabolle.backend.trip.application.TripQueryService;

/**
 * 완성된 일정표 조회.
 * {@code ItineraryEditController} 와 최종 경로는 같지만 매핑 방식이 겹치지 않는다 — 그쪽은
 * {@code @RequestMapping(".../{itineraryId}")} + {@code @PostMapping}, 이쪽은
 * {@code @RequestMapping("/api/v1/itineraries")} + {@code @GetMapping("/{itineraryId}")} 다.
 * {@code @Profile({"db","dev"})} · {@code @ConditionalOnBean(TripQueryService.class)} 인 이유는
 * {@link ItineraryQueryService} 의 javadoc 과 같다.
 */
@RestController
@RequestMapping("/api/v1/itineraries")
@Profile({ "db", "dev" })
@ConditionalOnBean(TripQueryService.class)
public class ItineraryQueryController {

	private final ItineraryQueryService queryService;

	public ItineraryQueryController(ItineraryQueryService queryService) {
		this.queryService = queryService;
	}

	/**
	 * 권한 검증 — {@code itinerary.tripId()} 로 그 여행의 회원인지 본다. 없는 일정과 회원이 아닌
	 * 일정을 같은 404 로 답한다.
	 */
	@GetMapping("/{itineraryId}")
	public ApiResponse<ItineraryDetailResponse> get(@PathVariable String itineraryId,
			Authentication authentication) {

		// 요청자를 X-User-Id 헤더가 아니라 인증 주체에서 정한다. 헤더는 부르는 쪽이 정하는
		//    값이라 여행 참여 검사가 그 주장 위에서 돌고, 앱은 그 헤더를 보내지도 않는다.
		String requester = AuthenticatedUsers.requireId(authentication).toString();
		ItineraryDetailResponse response = this.queryService.getDetail(itineraryId, requester);
		return ApiResponse.success(response, "req_" + UUID.randomUUID());
	}

	/**
	 * 판 목록. 되돌리기 화면이 "어느 판으로 돌아갈지" 고르는 목록을 그린다. 권한 판정은
	 * {@link #get} 과 같다 — 회원이면 누구나 볼 수 있고, 회원이 아니면 존재 자체를 404 로 감춘다.
	 */
	/**
	 * 응답이 배열이 아니라 봉투다. 판은 일정을 고칠 때마다 쌓여 끝이 없는데, 상한을 두려면
	 * "더 있다" 를 말할 자리가 필요하고 배열에는 그 자리가 없다 — {@link ItineraryVersionsResponse}
	 * 참고. 배열을 읽던 화면은 {@code data.items} 를 읽어야 한다.
	 */
	@GetMapping("/{itineraryId}/versions")
	public ApiResponse<ItineraryVersionsResponse> versions(@PathVariable String itineraryId,
			@RequestParam(required = false) Integer page,
			@RequestParam(required = false) Integer size,
			Authentication authentication) {

		String requester = AuthenticatedUsers.requireId(authentication).toString();
		// 기본 쪽 크기를 아는 곳은 서비스 하나뿐이다 — 안 준 값은 null 그대로 넘긴다.
		ItineraryVersionsResponse response = this.queryService.listVersions(itineraryId, requester, page, size);
		return ApiResponse.success(response, "req_" + UUID.randomUUID());
	}

	/** 없는 일정이거나, 요청자가 그 일정이 속한 여행의 회원이 아니다 — 둘을 구분해 응답하지 않는다. */
	public static class ItineraryNotFoundException extends RuntimeException {
		public ItineraryNotFoundException(String itineraryId) {
			super("일정을 찾을 수 없습니다: " + itineraryId);
		}
	}
}
