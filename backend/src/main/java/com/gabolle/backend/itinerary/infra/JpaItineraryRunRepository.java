package com.gabolle.backend.itinerary.infra;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.itinerary.domain.ItineraryRun;
import com.gabolle.backend.itinerary.domain.ItineraryRunRepository;
import com.gabolle.backend.itinerary.domain.ItineraryStopEvent;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * 일정 진행 저장소.
 *
 * <p>덮어쓰기를 {@code ON CONFLICT DO UPDATE} 한 문장으로 한다. 출발을 두 번 누르는 것이
 * 정상 경로라 요청이 반드시 겹치고, PostgreSQL 은 트랜잭션 안에서 문장 하나가 실패하면
 * 그 트랜잭션 전체를 못 쓰게 만들어 같은 트랜잭션 안에서 재시도할 수 없다.
 *
 * <p>{@code started_at} 은 {@code DO UPDATE} 에서 건드리지 않는다. 처음 출발한 시각은 한
 * 번만 정해지고, 다시 출발해도 바뀌지 않는다.
 */
@Repository
@Profile({ "db", "dev" })
public class JpaItineraryRunRepository implements ItineraryRunRepository {

	private static final String UPSERT = """
			INSERT INTO itinerary_run
			    (itinerary_id, trip_id, status, current_stop_index, started_at, updated_at)
			VALUES (?1, ?2, ?3, ?4, ?5, ?6)
			ON CONFLICT (itinerary_id) DO UPDATE
			   SET status             = EXCLUDED.status,
			       current_stop_index = EXCLUDED.current_stop_index,
			       updated_at         = EXCLUDED.updated_at
			""";

	private final ItineraryRunJpaRepository runs;

	private final ItineraryStopEventJpaRepository events;

	@PersistenceContext
	private EntityManager entityManager;

	public JpaItineraryRunRepository(ItineraryRunJpaRepository runs, ItineraryStopEventJpaRepository events) {
		this.runs = runs;
		this.events = events;
	}

	@Override
	@Transactional(readOnly = true)
	public Optional<ItineraryRun> find(String itineraryId) {
		return this.runs.findById(UUID.fromString(itineraryId)).map(JpaItineraryRunRepository::toDomain);
	}

	@Override
	@Transactional
	public ItineraryRun upsert(ItineraryRun run) {
		this.entityManager.createNativeQuery(UPSERT)
				.setParameter(1, UUID.fromString(run.itineraryId()))
				.setParameter(2, UUID.fromString(run.tripId()))
				.setParameter(3, run.status().name())
				.setParameter(4, run.currentStopIndex())
				.setParameter(5, toOffset(run.startedAt()))
				.setParameter(6, toOffset(run.updatedAt()))
				.executeUpdate();
		return run;
	}

	@Override
	@Transactional
	public ItineraryStopEvent append(ItineraryStopEvent event) {
		this.events.save(new ItineraryStopEventJpaEntity(
				UUID.fromString(event.eventId()),
				UUID.fromString(event.itineraryId()),
				event.itemKey() == null ? null : UUID.fromString(event.itemKey()),
				event.type().name(),
				toOffset(event.occurredAt()),
				UUID.fromString(event.recordedBy()),
				toOffset(event.createdAt())));
		return event;
	}

	@Override
	@Transactional(readOnly = true)
	public List<ItineraryStopEvent> findEvents(String itineraryId) {
		return this.events.findByItineraryIdOrderByOccurredAtAsc(UUID.fromString(itineraryId)).stream()
				.map(JpaItineraryRunRepository::toDomain)
				.toList();
	}

	private static ItineraryRun toDomain(ItineraryRunJpaEntity e) {
		return new ItineraryRun(
				e.itineraryId().toString(),
				e.tripId().toString(),
				ItineraryRun.Status.valueOf(e.status()),
				e.currentStopIndex(),
				toInstant(e.startedAt()),
				toInstant(e.updatedAt()));
	}

	private static ItineraryStopEvent toDomain(ItineraryStopEventJpaEntity e) {
		return new ItineraryStopEvent(
				e.eventId().toString(),
				e.itineraryId().toString(),
				e.itemKey() == null ? null : e.itemKey().toString(),
				ItineraryStopEvent.Type.valueOf(e.eventType()),
				toInstant(e.occurredAt()),
				e.recordedBy().toString(),
				toInstant(e.createdAt()));
	}

	private static OffsetDateTime toOffset(Instant instant) {
		return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
	}

	private static Instant toInstant(OffsetDateTime offsetDateTime) {
		return offsetDateTime == null ? null : offsetDateTime.toInstant();
	}
}
