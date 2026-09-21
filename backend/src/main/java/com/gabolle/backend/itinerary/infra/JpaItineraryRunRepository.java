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
import com.gabolle.backend.itinerary.domain.ItineraryRunPing;
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
			    (itinerary_id, trip_id, status, current_stop_index, started_at, updated_at,
			     last_lat, last_lng, last_location_at)
			VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8, ?9)
			ON CONFLICT (itinerary_id) DO UPDATE
			   SET status             = EXCLUDED.status,
			       current_stop_index = EXCLUDED.current_stop_index,
			       updated_at         = EXCLUDED.updated_at,
			       last_lat           = EXCLUDED.last_lat,
			       last_lng           = EXCLUDED.last_lng,
			       last_location_at   = EXCLUDED.last_location_at
			""";

	/**
	 * 궤적은 같은 점이 두 번 올라오는 것이 정상 경로다(배치 재시도). 기본키 위반 한 번이면
	 * PostgreSQL 이 트랜잭션 전체를 못 쓰게 만들므로 {@code DO NOTHING} 으로 흘려보낸다 —
	 * 먼저 들어온 점을 그대로 둔다. 나중 것이 더 정확할 이유가 없다.
	 */
	private static final String INSERT_PING = """
			INSERT INTO itinerary_run_ping
			    (itinerary_run_ping_id, itinerary_id, lat, lng, recorded_at, received_at)
			VALUES (?1, ?2, ?3, ?4, ?5, ?6)
			ON CONFLICT (itinerary_id, recorded_at) DO NOTHING
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
				.setParameter(7, run.lastLocation() == null ? null : run.lastLocation().lat())
				.setParameter(8, run.lastLocation() == null ? null : run.lastLocation().lng())
				.setParameter(9, run.lastLocation() == null ? null : toOffset(run.lastLocation().at()))
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
	@Transactional
	public int saveAllPings(List<ItineraryRunPing> pings) {
		int saved = 0;
		for (ItineraryRunPing ping : pings) {
			saved += this.entityManager.createNativeQuery(INSERT_PING)
					.setParameter(1, UUID.fromString(ping.pingId()))
					.setParameter(2, UUID.fromString(ping.itineraryId()))
					.setParameter(3, ping.lat())
					.setParameter(4, ping.lng())
					.setParameter(5, toOffset(ping.recordedAt()))
					.setParameter(6, toOffset(ping.receivedAt()))
					.executeUpdate();
		}
		return saved;
	}

	@Override
	@Transactional
	public int deletePingsOfUser(String userId) {
		// 궤적에는 사용자 칸이 없다 — 일정을 거쳐 간다. 여행의 주인이 아니라 일정에 달려
		// 있으므로, 그 사람의 여행에 달린 일정의 궤적을 지운다.
		return this.entityManager.createNativeQuery("""
				DELETE FROM itinerary_run_ping p
				 USING itineraries i, trip t
				 WHERE p.itinerary_id = i.itinerary_id
				   AND i.trip_id = t.trip_id
				   AND t.owner_user_id = ?1
				""")
				.setParameter(1, UUID.fromString(userId))
				.executeUpdate();
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
				toInstant(e.updatedAt()),
				// 좌표가 하나라도 비면 「모른다」로 읽는다. 나머지 하나를 0 으로 채우지 않는다.
				(e.lastLat() == null || e.lastLng() == null) ? null
						: new ItineraryRun.LastLocation(e.lastLat(), e.lastLng(), toInstant(e.lastLocationAt())));
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
