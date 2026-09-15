package com.gabolle.backend.event;

import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.gabolle.backend.event.application.AnalyticsQueryService;
import com.gabolle.backend.event.presentation.AnalyticsController;
import com.gabolle.backend.event.presentation.AnalyticsExceptionHandler;
import com.gabolle.backend.event.presentation.dto.AnalyticsKpiResponse;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * S15P21E201-160 — {@code GET /api/v1/analytics/kpis} 의 표현 계층.
 *
 * <p>DB 없이 도는 슬라이스 테스트다 — {@link AnalyticsQueryService} 를 mock 으로 세운다
 * ({@code RecommendationResultAuthorizationTest} 와 같은 방식).
 */
class AnalyticsControllerTest {

	private AnalyticsQueryService service;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		this.service = mock(AnalyticsQueryService.class);
		this.mockMvc = MockMvcBuilders.standaloneSetup(new AnalyticsController(this.service))
				.setControllerAdvice(new AnalyticsExceptionHandler())
				.build();
	}

	private static AnalyticsKpiResponse emptyResponse(OffsetDateTime from, OffsetDateTime to) {
		return new AnalyticsKpiResponse(from, to, List.of(),
				new AnalyticsKpiResponse.OutboxHealthEntry(0, null, 0, 0),
				new AnalyticsKpiResponse.RecommendationJobHealthEntry(List.of(), null, null, List.of()));
	}

	@Test
	@DisplayName("from·to 없이 부르면 서비스에 null 을 그대로 넘긴다 — 기본값은 서비스가 정한다")
	void callsServiceWithNullWhenRangeOmitted() throws Exception {
		given(this.service.kpis(null, null)).willReturn(
				emptyResponse(OffsetDateTime.parse("2026-09-07T12:00:00Z"), OffsetDateTime.parse("2026-09-08T12:00:00Z")));

		this.mockMvc.perform(get("/api/v1/analytics/kpis"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.from").value("2026-09-07T12:00:00Z"))
				.andExpect(jsonPath("$.data.to").value("2026-09-08T12:00:00Z"));

		verify(this.service).kpis(null, null);
	}

	@Test
	@DisplayName("from·to 쿼리 파라미터를 그대로 서비스에 넘긴다")
	void passesQueryParamsThrough() throws Exception {
		OffsetDateTime from = OffsetDateTime.parse("2026-09-01T00:00:00Z");
		OffsetDateTime to = OffsetDateTime.parse("2026-09-02T00:00:00Z");
		given(this.service.kpis(from, to)).willReturn(emptyResponse(from, to));

		this.mockMvc.perform(get("/api/v1/analytics/kpis")
						.param("from", "2026-09-01T00:00:00Z")
						.param("to", "2026-09-02T00:00:00Z"))
				.andExpect(status().isOk());

		verify(this.service).kpis(from, to);
	}

	@Test
	@DisplayName("종류별 건수와 outboxHealth 가 응답에 그대로 실린다")
	void returnsCountsAndOutboxHealth() throws Exception {
		OffsetDateTime from = OffsetDateTime.parse("2026-09-07T12:00:00Z");
		OffsetDateTime to = OffsetDateTime.parse("2026-09-08T12:00:00Z");
		given(this.service.kpis(any(), any())).willReturn(new AnalyticsKpiResponse(from, to,
				List.of(new AnalyticsKpiResponse.EventTypeCountEntry("trip_created", 4L)),
				new AnalyticsKpiResponse.OutboxHealthEntry(3, 120L, 10, 1),
				new AnalyticsKpiResponse.RecommendationJobHealthEntry(
						List.of(new AnalyticsKpiResponse.JobStatusCountEntry("SUCCEEDED", 8L)), 80.0, 842.5,
						List.of(new AnalyticsKpiResponse.ErrorCodeCountEntry("TIMEOUT", 2L)))));

		this.mockMvc.perform(get("/api/v1/analytics/kpis"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.eventCounts[0].eventType").value("trip_created"))
				.andExpect(jsonPath("$.data.eventCounts[0].count").value(4))
				.andExpect(jsonPath("$.data.outboxHealth.pendingCount").value(3))
				.andExpect(jsonPath("$.data.outboxHealth.oldestPendingAgeSeconds").value(120))
				.andExpect(jsonPath("$.data.outboxHealth.publishedCount").value(10))
				.andExpect(jsonPath("$.data.outboxHealth.failedCount").value(1))
				.andExpect(jsonPath("$.data.recommendationJobHealth.statusCounts[0].status").value("SUCCEEDED"))
				.andExpect(jsonPath("$.data.recommendationJobHealth.statusCounts[0].count").value(8))
				.andExpect(jsonPath("$.data.recommendationJobHealth.successRatePercent").value(80.0))
				.andExpect(jsonPath("$.data.recommendationJobHealth.averageLatencyMsForSucceeded").value(842.5))
				.andExpect(jsonPath("$.data.recommendationJobHealth.failureBreakdown[0].errorCode").value("TIMEOUT"))
				.andExpect(jsonPath("$.data.recommendationJobHealth.failureBreakdown[0].count").value(2));
	}

	@Test
	@DisplayName("🔴 잘못된 범위(IllegalArgumentException)는 500 이 아니라 400 이다")
	void invalidRangeIsBadRequestNotServerError() throws Exception {
		given(this.service.kpis(any(), any())).willThrow(new IllegalArgumentException("from 은 to 보다 앞이어야 한다"));

		this.mockMvc.perform(get("/api/v1/analytics/kpis")
						.param("from", "2026-09-08T00:00:00Z")
						.param("to", "2026-09-01T00:00:00Z"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_KPI_RANGE"));
	}

	@Test
	@DisplayName("requestId 헤더를 안 주면 새로 만든다")
	void generatesRequestIdWhenMissing() throws Exception {
		given(this.service.kpis(null, null)).willReturn(
				emptyResponse(OffsetDateTime.now(), OffsetDateTime.now()));

		this.mockMvc.perform(get("/api/v1/analytics/kpis"))
				.andExpect(jsonPath("$.meta.requestId").isNotEmpty());
	}
}
