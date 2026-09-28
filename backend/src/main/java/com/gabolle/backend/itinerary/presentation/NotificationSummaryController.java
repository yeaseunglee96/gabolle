package com.gabolle.backend.itinerary.presentation;

import java.time.Instant;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.common.security.AuthenticatedUsers;
import com.gabolle.backend.itinerary.application.NotificationSummaryService;
import com.gabolle.backend.itinerary.presentation.dto.NotificationSummaryResponse;

/**
 * {@code GET /api/v1/me/notification-summary?since=…} — 종 점 (S15P21E201-1699). 경로는 {@code /me} 밑이지만 읽는 것은
 * 일정의 판이라 여행 활동 조회와 같이 {@code itinerary} 패키지에 둔다.
 */
@RestController
@RequestMapping("/api/v1/me")
@Profile({ "db", "dev" })
public class NotificationSummaryController {

	private final NotificationSummaryService summaryService;

	public NotificationSummaryController(NotificationSummaryService summaryService) {
		this.summaryService = summaryService;
	}

	/**
	 * @param since 앱이 기기에 둔 「마지막으로 본 시각」(ISO-8601). 한 번도 안 봤으면 빼고 부른다
	 */
	@GetMapping("/notification-summary")
	public ApiResponse<NotificationSummaryResponse> summary(@RequestParam(name = "since", required = false) Instant since,
			Authentication authentication) {
		UUID requester = AuthenticatedUsers.requireId(authentication);
		return ApiResponse.success(this.summaryService.summarize(requester, since), "req_" + UUID.randomUUID());
	}
}
