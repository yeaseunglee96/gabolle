package com.gabolle.backend.event.application;

import java.time.Clock;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.event.domain.EventOutbox;
import com.gabolle.backend.event.domain.OutboxPublishStatus;
import com.gabolle.backend.event.presentation.dto.AnalyticsKpiResponse;
import com.gabolle.backend.event.presentation.dto.AnalyticsKpiResponse.ErrorCodeCountEntry;
import com.gabolle.backend.event.presentation.dto.AnalyticsKpiResponse.EventTypeCountEntry;
import com.gabolle.backend.event.presentation.dto.AnalyticsKpiResponse.JobStatusCountEntry;
import com.gabolle.backend.event.presentation.dto.AnalyticsKpiResponse.OutboxHealthEntry;
import com.gabolle.backend.event.presentation.dto.AnalyticsKpiResponse.RecommendationJobHealthEntry;
import com.gabolle.backend.event.repository.EventOutboxRepository;
import com.gabolle.backend.recommendation.domain.JobStatus;
import com.gabolle.backend.recommendation.repository.RecommendationJobRepository;

/**
 * 지표 조회. {@code no-db} 프로필에는 이 빈이 없다 — DB 없이 셀 수 있는 값이 아니라서,
 * 있는 척하며 빈 값을 내는 대신 아예 없앤다.
 */
@Service
@Profile({ "db", "dev" })
public class AnalyticsQueryService {

	private final EventOutboxRepository repository;

	private final RecommendationJobRepository recommendationJobRepository;

	private final Clock clock;

	public AnalyticsQueryService(EventOutboxRepository repository,
			RecommendationJobRepository recommendationJobRepository, Clock clock) {
		this.repository = repository;
		this.recommendationJobRepository = recommendationJobRepository;
		this.clock = clock;
	}

	/**
	 * @param from 비어 있으면 {@code to - 24시간}. 이벤트 수집이 아직 하루가 안 된 초기에도
	 *     "최근 하루" 라는 뜻이 흔들리지 않게 {@code to} 기준으로 잡는다 — {@code now} 기준으로
	 *     따로 잡으면 {@code to} 를 과거로 지정해 조회할 때 창이 어긋난다.
	 * @param to 비어 있으면 지금.
	 */
	@Transactional(readOnly = true)
	public AnalyticsKpiResponse kpis(OffsetDateTime from, OffsetDateTime to) {

		OffsetDateTime resolvedTo = to != null ? to : OffsetDateTime.now(this.clock);
		OffsetDateTime resolvedFrom = from != null ? from : resolvedTo.minusHours(24);

		if (!resolvedFrom.isBefore(resolvedTo)) {
			throw new IllegalArgumentException("from(" + resolvedFrom + ") 은 to(" + resolvedTo + ") 보다 앞이어야 한다");
		}

		List<EventTypeCountEntry> eventCounts = this.repository.countByEventTypeBetween(resolvedFrom, resolvedTo)
				.stream()
				.map(row -> new EventTypeCountEntry(row.getEventType(), row.getCount()))
				.toList();

		OutboxHealthEntry outboxHealth = new OutboxHealthEntry(
				this.repository.countByPublishedAtIsNull(),
				oldestPendingAgeSeconds(),
				this.repository.countByPublishedAtBetween(resolvedFrom, resolvedTo),
				this.repository.countByPublishStatus(OutboxPublishStatus.FAILED));

		RecommendationJobHealthEntry recommendationJobHealth = recommendationJobHealth(resolvedFrom, resolvedTo);

		return new AnalyticsKpiResponse(resolvedFrom, resolvedTo, eventCounts, outboxHealth, recommendationJobHealth);
	}

	private RecommendationJobHealthEntry recommendationJobHealth(OffsetDateTime resolvedFrom,
			OffsetDateTime resolvedTo) {

		List<RecommendationJobRepository.JobStatusCount> statusCounts = this.recommendationJobRepository
				.countByJobStatusBetween(resolvedFrom, resolvedTo);

		long succeededCount = statusCounts.stream()
				.filter(row -> row.getJobStatus() == JobStatus.SUCCEEDED)
				.mapToLong(RecommendationJobRepository.JobStatusCount::getCount)
				.sum();
		long failedCount = statusCounts.stream()
				.filter(row -> row.getJobStatus() == JobStatus.FAILED)
				.mapToLong(RecommendationJobRepository.JobStatusCount::getCount)
				.sum();
		long terminalCount = succeededCount + failedCount;

		Double successRatePercent = terminalCount == 0 ? null : succeededCount * 100.0 / terminalCount;

		List<JobStatusCountEntry> statusCountEntries = statusCounts.stream()
				.map(row -> new JobStatusCountEntry(row.getJobStatus().name(), row.getCount()))
				.toList();

		List<ErrorCodeCountEntry> failureBreakdown = this.recommendationJobRepository
				.countFailuresByErrorCodeBetween(resolvedFrom, resolvedTo)
				.stream()
				.map(row -> new ErrorCodeCountEntry(row.getErrorCode(), row.getCount()))
				.toList();

		Double averageLatencyMsForSucceeded = this.recommendationJobRepository
				.averageLatencyMsForSucceededBetween(resolvedFrom, resolvedTo);

		return new RecommendationJobHealthEntry(statusCountEntries, successRatePercent, averageLatencyMsForSucceeded,
				failureBreakdown);
	}

	/**
	 * {@code receivedAt} 기준이다 — {@code occurredAt} 을 쓰면 기기가 늦게 보낸 오래된 이벤트가
	 * 방금 밀리기 시작한 것처럼 보인다.
	 */
	private Long oldestPendingAgeSeconds() {
		List<EventOutbox> oldest = this.repository.findByPublishedAtIsNullOrderBySeqAsc(PageRequest.of(0, 1));
		if (oldest.isEmpty()) {
			return null;
		}
		return Duration.between(oldest.get(0).getReceivedAt(), OffsetDateTime.now(this.clock)).getSeconds();
	}
}
