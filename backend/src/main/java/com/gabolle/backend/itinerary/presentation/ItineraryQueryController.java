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
 * 완성된 일정표 조회 — S15P21E201-604.
 *
 * <p>🔴 경로 충돌 없음. 기존 {@code ItineraryEditController} 는
 * {@code @RequestMapping("/api/v1/itineraries/{itineraryId}")} + {@code @PostMapping(...)}
 * 이고, 이 컨트롤러는 {@code @RequestMapping("/api/v1/itineraries")} +
 * {@code @GetMapping("/{itineraryId}")} 다 — 최종 경로는 같지만 매핑 방식이 겹치지 않는다.
 * 그 컨트롤러는 다른 사람이 다른 브랜치에서 고치는 중이라 이 작업에서 열지 않았다.
 *
 * <p>🔴 {@code @Profile({"db","dev"})} · {@code @ConditionalOnBean(TripQueryService.class)} —
 * {@link ItineraryQueryService} 의 javadoc 과 같은 이유다. {@code ItinerarySliceApplication}
 * (일정 도메인만 스캔)은 {@code trip}·{@code place} 패키지를 스캔하지 않는다.
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
	 * 🔴 권한 검증 — {@code itinerary.tripId()} 로 그 여행의 회원인지 본다
	 * ({@link ItineraryQueryService} 참고). 없는 일정과 회원이 아닌 일정을 <b>같은 404</b> 로
	 * 답한다 — {@code TripQueryService} 가 여행 조회에서 쓰는 것과 같은 논리다.
	 */
	@GetMapping("/{itineraryId}")
	public ApiResponse<ItineraryDetailResponse> get(@PathVariable String itineraryId,
			Authentication authentication) {

		// 🔴 S15P21E201-604 — 요청자를 X-User-Id 헤더가 아니라 인증 주체에서 정한다.
		//    헤더는 부르는 쪽이 정하는 값이라 여행 참여 검사가 그 주장 위에서 돌고,
		//    앱은 그 헤더를 보내지도 않는다(Authorization 만 싣는다).
		String requester = AuthenticatedUsers.requireId(authentication).toString();
		ItineraryDetailResponse response = this.queryService.getDetail(itineraryId, requester);
		return ApiResponse.success(response, "req_" + UUID.randomUUID());
	}

	/**
	 * 🔴 S15P21E201-284 — 판 목록, API 명세 ITN-02. 되돌리기 화면이 "어느 판으로
	 * 돌아갈지" 고르는 목록을 그린다. 권한 판정은 {@link #get} 과 같다 — 회원이면 누구나
	 * 볼 수 있고, 회원이 아니면 존재 자체를 404 로 감춘다.
	 */
	/**
	 * 🔴 S15P21E201-1011 — <b>응답이 배열에서 봉투로 바뀌었다.</b> 판은 일정을 고칠 때마다
	 * 쌓여 끝이 없는데 상한도 쪽 나눔도 없었다. 상한을 두려면 "더 있다" 를 말할 자리가 필요하고,
	 * 배열에는 그 자리가 없다 — 이유는 {@link ItineraryVersionsResponse} 에 적었다.
	 * 배열을 읽던 화면은 이제 {@code data.items} 를 읽어야 한다.
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

	/** 없는 일정이거나(또는 요청자가 그 일정이 속한 여행의 회원이 아니다 — 둘을 구분해 응답하지 않는다). */
	public static class ItineraryNotFoundException extends RuntimeException {
		public ItineraryNotFoundException(String itineraryId) {
			super("일정을 찾을 수 없습니다: " + itineraryId);
		}
	}
}
