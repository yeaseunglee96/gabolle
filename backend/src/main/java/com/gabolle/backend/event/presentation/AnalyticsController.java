package com.gabolle.backend.event.presentation;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.gabolle.backend.common.api.ApiResponse;
import com.gabolle.backend.event.application.AnalyticsQueryService;
import com.gabolle.backend.event.presentation.dto.AnalyticsKpiResponse;

/**
 * 지표 조회. 운영자 전용이다 — 내부 운영 숫자는 서비스 규모의 단서라 익명 출입증으로 읽히면
 * 안 된다.
 *
 * <p>인가는 이 컨트롤러가 하지 않고 경로 앞자리가 한다. 이 저장소는 메서드 보안이 꺼져 있어
 * {@code @PreAuthorize} 를 붙여도 조용히 무시된다. 실제로 막는 것은 {@code SecurityConfig} 의
 * {@code requestMatchers("/api/v1/admin/**").hasRole("ADMIN")} 한 줄뿐이므로, 이 클래스를
 * {@code /api/v1/admin/} 밖으로 옮기면 잠금이 풀린다.
 */
@RestController
@RequestMapping("/api/v1/admin/analytics")
@Profile({ "db", "dev" })
public class AnalyticsController {

	private final AnalyticsQueryService service;

	public AnalyticsController(AnalyticsQueryService service) {
		this.service = service;
	}

	/**
	 * @param from 생략하면 {@code to - 24시간}.
	 * @param to 생략하면 지금.
	 */
	@GetMapping("/kpis")
	public ApiResponse<AnalyticsKpiResponse> kpis(
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime from,
			@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) OffsetDateTime to,
			@RequestHeader(value = "X-Request-Id", required = false) String requestId) {

		AnalyticsKpiResponse body = this.service.kpis(from, to);
		return ApiResponse.success(body, resolveRequestId(requestId));
	}

	private String resolveRequestId(String requestId) {
		return requestId == null || requestId.isBlank() ? "req_" + UUID.randomUUID() : requestId;
	}
}
