package com.gabolle.backend.metrics;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.gabolle.backend.common.api.ApiResponse;

/**
 * 운영 지표 — MAU·DAU·가입·일정·재방문(S15P21E201-2010). 운영자(ADMIN) 전용: 경로 규칙({@code /api/v1/admin/**})과
 * {@code @PreAuthorize} 두 겹으로 막는다.
 */
@RestController
@RequestMapping("/api/v1/admin/metrics")
@PreAuthorize("hasRole('ADMIN')")
@Profile({ "db", "dev" })
public class AdminMetricsController {

	private final MetricsQueryService service;

	public AdminMetricsController(MetricsQueryService service) {
		this.service = service;
	}

	@GetMapping
	public ApiResponse<MetricsQueryService.Report> report(@RequestParam(defaultValue = "90") int days,
			@RequestHeader(value = "X-Request-Id", required = false) String requestId) {
		return ApiResponse.success(this.service.report(days),
				requestId == null || requestId.isBlank() ? "req_" + UUID.randomUUID() : requestId);
	}

	/** 하루 한 줄 CSV. 시트에 붙여 매주 쌓을 때 쓴다. */
	@GetMapping(value = "/daily.csv")
	public ResponseEntity<String> csv(@RequestParam(defaultValue = "90") int days) {
		return ResponseEntity.ok()
			.header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"gabolle-metrics-daily.csv\"")
			.contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
			.body(this.service.csv(days));
	}
}
