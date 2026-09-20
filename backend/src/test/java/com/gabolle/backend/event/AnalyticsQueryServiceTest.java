package com.gabolle.backend.event;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.event.application.AnalyticsQueryService;
import com.gabolle.backend.event.domain.EventOutbox;
import com.gabolle.backend.event.domain.OutboxPublishStatus;
import com.gabolle.backend.event.presentation.dto.AnalyticsKpiResponse;
import com.gabolle.backend.event.repository.EventOutboxRepository;
import com.gabolle.backend.event.repository.EventOutboxRepository.EventTypeCount;
import com.gabolle.backend.recommendation.domain.JobStatus;
import com.gabolle.backend.recommendation.repository.RecommendationJobRepository;
import com.gabolle.backend.recommendation.repository.RecommendationJobRepository.ErrorCodeCount;
import com.gabolle.backend.recommendation.repository.RecommendationJobRepository.JobStatusCount;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.springframework.data.domain.PageRequest.of;

/**
 * {@code GET /api/v1/admin/analytics/kpis} 의 응용 계층.
 *
 * <p>무엇을 KPI 로 삼을지가 아직 정해지지 않아, 사업 판단이 필요 없는 값만 검증한다 —
 * 종류별 건수와 Outbox 가 실제로 밀리고 있는가.
 */
class AnalyticsQueryServiceTest {

	private static final Instant NOW = Instant.parse("2026-09-08T12:00:00Z");

	private EventOutboxRepository repository;
	private RecommendationJobRepository recommendationJobRepository;
	private AnalyticsQueryService service;

	@BeforeEach
	void setUp() {
		this.repository = mock(EventOutboxRepository.class);
		this.recommendationJobRepository = mock(RecommendationJobRepository.class);
		this.service = new AnalyticsQueryService(this.repository, this.recommendationJobRepository,
				Clock.fixed(NOW, ZoneOffset.UTC));
		given(this.repository.countByEventTypeBetween(any(), any())).willReturn(List.of());
		given(this.repository.findByPublishedAtIsNullOrderBySeqAsc(any())).willReturn(List.of());
		given(this.recommendationJobRepository.countByJobStatusBetween(any(), any())).willReturn(List.of());
		given(this.recommendationJobRepository.countFailuresByErrorCodeBetween(any(), any())).willReturn(List.of());
	}

	private static OffsetDateTime at(String iso) {
		return OffsetDateTime.parse(iso);
	}

	private static EventTypeCount countOf(String eventType, long count) {
		return new EventTypeCount() {
			@Override
			public String getEventType() {
				return eventType;
			}

			@Override
			public long getCount() {
				return count;
			}
		};
	}

	private static JobStatusCount jobStatusCountOf(JobStatus status, long count) {
		return new JobStatusCount() {
			@Override
			public JobStatus getJobStatus() {
				return status;
			}

			@Override
			public long getCount() {
				return count;
			}
		};
	}

	private static ErrorCodeCount errorCodeCountOf(String errorCode, long count) {
		return new ErrorCodeCount() {
			@Override
			public String getErrorCode() {
				return errorCode;
			}

			@Override
			public long getCount() {
				return count;
			}
		};
	}

	// ── 기간 기본값·검증 ─────────────────────────────────────────────

	@Test
	@DisplayName("from·to 를 둘 다 안 주면 (지금-24시간, 지금) 이다")
	void defaultsToLast24Hours() {
		AnalyticsKpiResponse response = this.service.kpis(null, null);

		assertThat(response.to()).isEqualTo(OffsetDateTime.now(Clock.fixed(NOW, ZoneOffset.UTC)));
		assertThat(response.from()).isEqualTo(response.to().minusHours(24));
	}

	@Test
	@DisplayName("to 만 주면 from 은 그 to 기준 24시간 전이다 — now 기준이 아니다")
	void fromDefaultsRelativeToGivenTo() {
		OffsetDateTime to = at("2026-09-01T00:00:00Z");

		AnalyticsKpiResponse response = this.service.kpis(null, to);

		assertThat(response.to()).isEqualTo(to);
		assertThat(response.from()).isEqualTo(to.minusHours(24));
	}

	@Test
	@DisplayName("🔴 from 이 to 보다 뒤거나 같으면 거부한다")
	void rejectsNonPositiveRange() {
		OffsetDateTime same = at("2026-09-08T00:00:00Z");

		assertThatThrownBy(() -> this.service.kpis(same, same))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> this.service.kpis(same.plusHours(1), same))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("주어진 범위 그대로 리포지토리에 넘긴다")
	void passesResolvedRangeToRepository() {
		OffsetDateTime from = at("2026-09-07T00:00:00Z");
		OffsetDateTime to = at("2026-09-08T00:00:00Z");

		this.service.kpis(from, to);

		verify(this.repository).countByEventTypeBetween(from, to);
		verify(this.repository).countByPublishedAtBetween(from, to);
	}

	// ── 종류별 건수 ─────────────────────────────────────────────────

	@Test
	@DisplayName("리포지토리가 낸 그룹 결과를 그대로 옮긴다")
	void mapsEventTypeCounts() {
		given(this.repository.countByEventTypeBetween(any(), any()))
				.willReturn(List.of(countOf("recommendation_impression", 3L), countOf("trip_created", 1L)));

		AnalyticsKpiResponse response = this.service.kpis(null, null);

		assertThat(response.eventCounts()).containsExactlyInAnyOrder(
				new AnalyticsKpiResponse.EventTypeCountEntry("recommendation_impression", 3L),
				new AnalyticsKpiResponse.EventTypeCountEntry("trip_created", 1L));
	}

	// ── Outbox 상태 ─────────────────────────────────────────────────

	@Test
	@DisplayName("🔴 pendingCount·failedCount 는 기간과 무관하게 지금 값을 그대로 낸다")
	void pendingAndFailedAreRealTimeNotWindowed() {
		given(this.repository.countByPublishedAtIsNull()).willReturn(5L);
		given(this.repository.countByPublishStatus(OutboxPublishStatus.FAILED)).willReturn(2L);

		AnalyticsKpiResponse response = this.service.kpis(at("2026-01-01T00:00:00Z"), at("2026-01-02T00:00:00Z"));

		assertThat(response.outboxHealth().pendingCount()).isEqualTo(5L);
		assertThat(response.outboxHealth().failedCount()).isEqualTo(2L);
	}

	@Test
	@DisplayName("밀린 게 없으면 oldestPendingAgeSeconds 는 0 이 아니라 null 이다")
	void oldestPendingAgeIsNullWhenNothingPending() {
		AnalyticsKpiResponse response = this.service.kpis(null, null);

		assertThat(response.outboxHealth().oldestPendingAgeSeconds()).isNull();
	}

	@Test
	@DisplayName("가장 오래 밀린 건의 receivedAt 기준으로 경과 초를 잰다")
	void oldestPendingAgeIsSecondsSinceReceivedAt() {
		EventOutbox oldest = mock(EventOutbox.class);
		given(oldest.getReceivedAt()).willReturn(at("2026-09-08T11:59:00Z"));
		given(this.repository.findByPublishedAtIsNullOrderBySeqAsc(of(0, 1))).willReturn(List.of(oldest));

		AnalyticsKpiResponse response = this.service.kpis(null, null);

		assertThat(response.outboxHealth().oldestPendingAgeSeconds()).isEqualTo(60L);
	}

	@Test
	@DisplayName("publishedCount 는 그 기간에 실제로 나간 건수다")
	void publishedCountIsWindowed() {
		given(this.repository.countByPublishedAtBetween(any(), any())).willReturn(9L);

		AnalyticsKpiResponse response = this.service.kpis(null, null);

		assertThat(response.outboxHealth().publishedCount()).isEqualTo(9L);
	}

	// ── 추천 요청(Job) 건강도 ─────────────────────────────────────────

	@Test
	@DisplayName("상태별 건수를 그대로 옮긴다 — 진행 중인 것도 포함한다")
	void mapsJobStatusCounts() {
		given(this.recommendationJobRepository.countByJobStatusBetween(any(), any())).willReturn(List.of(
				jobStatusCountOf(JobStatus.SUCCEEDED, 8L),
				jobStatusCountOf(JobStatus.FAILED, 2L),
				jobStatusCountOf(JobStatus.RUNNING, 3L)));

		AnalyticsKpiResponse response = this.service.kpis(null, null);

		assertThat(response.recommendationJobHealth().statusCounts()).containsExactlyInAnyOrder(
				new AnalyticsKpiResponse.JobStatusCountEntry("SUCCEEDED", 8L),
				new AnalyticsKpiResponse.JobStatusCountEntry("FAILED", 2L),
				new AnalyticsKpiResponse.JobStatusCountEntry("RUNNING", 3L));
	}

	@Test
	@DisplayName("성공률은 SUCCEEDED/(SUCCEEDED+FAILED) 이고, 진행 중·취소·만료는 분모에 안 넣는다")
	void successRateExcludesNonTerminalStatuses() {
		given(this.recommendationJobRepository.countByJobStatusBetween(any(), any())).willReturn(List.of(
				jobStatusCountOf(JobStatus.SUCCEEDED, 3L),
				jobStatusCountOf(JobStatus.FAILED, 1L),
				jobStatusCountOf(JobStatus.RUNNING, 5L),
				jobStatusCountOf(JobStatus.CANCELLED, 2L)));

		AnalyticsKpiResponse response = this.service.kpis(null, null);

		assertThat(response.recommendationJobHealth().successRatePercent()).isEqualTo(75.0);
	}

	@Test
	@DisplayName("🔴 그 기간에 끝난 Job 이 하나도 없으면 성공률은 0 이 아니라 null 이다")
	void successRateIsNullWhenNoTerminalJobs() {
		given(this.recommendationJobRepository.countByJobStatusBetween(any(), any()))
				.willReturn(List.of(jobStatusCountOf(JobStatus.RUNNING, 4L)));

		AnalyticsKpiResponse response = this.service.kpis(null, null);

		assertThat(response.recommendationJobHealth().successRatePercent()).isNull();
	}

	@Test
	@DisplayName("성공한 Job 의 평균 처리 시간을 그대로 옮긴다")
	void mapsAverageLatency() {
		given(this.recommendationJobRepository.averageLatencyMsForSucceededBetween(any(), any())).willReturn(842.5);

		AnalyticsKpiResponse response = this.service.kpis(null, null);

		assertThat(response.recommendationJobHealth().averageLatencyMsForSucceeded()).isEqualTo(842.5);
	}

	@Test
	@DisplayName("성공한 Job 이 없으면 평균 처리 시간은 null 이다")
	void averageLatencyIsNullWhenNoSucceededJobs() {
		given(this.recommendationJobRepository.averageLatencyMsForSucceededBetween(any(), any())).willReturn(null);

		AnalyticsKpiResponse response = this.service.kpis(null, null);

		assertThat(response.recommendationJobHealth().averageLatencyMsForSucceeded()).isNull();
	}

	@Test
	@DisplayName("실패 사유별 건수를 그대로 옮긴다")
	void mapsFailureBreakdown() {
		given(this.recommendationJobRepository.countFailuresByErrorCodeBetween(any(), any()))
				.willReturn(List.of(errorCodeCountOf("TIMEOUT", 4L), errorCodeCountOf("UPSTREAM_ERROR", 1L)));

		AnalyticsKpiResponse response = this.service.kpis(null, null);

		assertThat(response.recommendationJobHealth().failureBreakdown()).containsExactlyInAnyOrder(
				new AnalyticsKpiResponse.ErrorCodeCountEntry("TIMEOUT", 4L),
				new AnalyticsKpiResponse.ErrorCodeCountEntry("UPSTREAM_ERROR", 1L));
	}

	@Test
	@DisplayName("주어진 범위 그대로 추천 Job 리포지토리에 넘긴다")
	void passesResolvedRangeToRecommendationJobRepository() {
		OffsetDateTime from = at("2026-09-07T00:00:00Z");
		OffsetDateTime to = at("2026-09-08T00:00:00Z");

		this.service.kpis(from, to);

		verify(this.recommendationJobRepository).countByJobStatusBetween(from, to);
		verify(this.recommendationJobRepository).averageLatencyMsForSucceededBetween(from, to);
		verify(this.recommendationJobRepository).countFailuresByErrorCodeBetween(from, to);
	}
}
