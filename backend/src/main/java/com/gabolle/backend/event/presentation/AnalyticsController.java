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
 * 지표 조회 — S15P21E201-160 작업 내용 5번.
 *
 * <p>🔴 이 응답에 있는 값의 범위는 {@link AnalyticsQueryService} 주석에 적었다 — 사업
 * KPI 를 아직 안 담는다.
 *
 * <p>🔴 <b>이 저장소에는 관리자 역할이 없다.</b> {@code SecurityConfig} 의 허용 목록에
 * 이 경로를 안 넣었으므로 다른 모든 API 와 같이 <b>로그인만 하면</b> 볼 수 있다. 집계값이라
 * 개인정보는 아니지만, "운영 지표는 관리자만" 이 필요해지면 그때 역할 체계부터 새로 만들어야
 * 한다 — 지금은 그런 체계 자체가 저장소에 없다.
 */
@RestController
@RequestMapping("/api/v1/analytics")
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
