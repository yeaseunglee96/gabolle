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
import com.gabolle.backend.event.presentation.dto.AnalyticsKpiResponse.EventTypeCountEntry;
import com.gabolle.backend.event.presentation.dto.AnalyticsKpiResponse.OutboxHealthEntry;
import com.gabolle.backend.event.repository.EventOutboxRepository;

/**
 * 지표 조회 — S15P21E201-160 작업 내용 5번.
 *
 * <p>🔴 <b>"무엇을 KPI 로 삼을 것인가" 는 이 서비스가 정하지 않는다.</b> 그 결정은 이
 * 티켓의 2026-09-03 코멘트가 팀에 넘긴 채로 남아 있다. 여기서는 그 결정과 무관하게 이미
 * 참인 값 둘만 낸다 — {@link #kpis} 의 클래스 주석에 이유를 적었다.
 *
 * <p>{@code no-db} 프로필에는 이 빈이 없다 — {@link EventIngestService} 와 같은 규칙이다.
 * DB 없이 셀 수 있는 값이 아니라서, 있는 척하며 빈 값을 내는 대신 아예 없앤다.
 */
@Service
@Profile({ "db", "dev" })
public class AnalyticsQueryService {

	private final EventOutboxRepository repository;

	private final Clock clock;

	public AnalyticsQueryService(EventOutboxRepository repository, Clock clock) {
		this.repository = repository;
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

		return new AnalyticsKpiResponse(resolvedFrom, resolvedTo, eventCounts, outboxHealth);
	}

	/**
	 * 🔴 {@code receivedAt} 기준이다 — Outbox 가 실제로 받은 순간부터 얼마나 밀렸는가를 잰다.
	 * {@code occurredAt} 을 쓰면 기기 시계가 늦게 보낸 오래된 이벤트가 "방금 밀리기 시작한 것"
	 * 처럼 보일 수 있다.
	 */
	private Long oldestPendingAgeSeconds() {
		List<EventOutbox> oldest = this.repository.findByPublishedAtIsNullOrderBySeqAsc(PageRequest.of(0, 1));
		if (oldest.isEmpty()) {
			return null;
		}
		return Duration.between(oldest.get(0).getReceivedAt(), OffsetDateTime.now(this.clock)).getSeconds();
	}
}
