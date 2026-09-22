package com.gabolle.backend.itinerary.infra;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.itinerary.domain.ItineraryItemActual;
import com.gabolle.backend.itinerary.domain.ItineraryItemActualRepository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

/**
 * 방문지 실제 시각 저장소.
 * 덮어쓰기를 {@code ON CONFLICT DO UPDATE} 한 문장으로 한다. 같은 방문지에 다시 보내는 것이
 * 예외가 아니라 정상 경로(도착을 찍고 나중에 출발을 찍는다)라, "찾아보고 없으면 넣고 있으면
 * 고친다" 로 쓰면 두 요청이 겹치는 순간 {@code uq_itinerary_item_actual} 위반이 난다.
 * PostgreSQL 은 트랜잭션 안에서 문장 하나가 실패하면 그 트랜잭션 전체를 못 쓰게 만들어,
 * 실패를 잡아 다시 시도하는 것이 같은 트랜잭션 안에서는 불가능하다.
 * 이 문장은 충돌해도 오류를 내지 않고 기존 행의 시각을 바꾼다 — 두 요청이 정말 동시에 와도
 * 마지막에 커밋된 값이 남는다.
 * {@code created_at} 은 {@code DO UPDATE} 에서 건드리지 않는다. 처음 기록한 시각과 마지막에
 * 고친 시각을 구분해야 "언제부터 이 방문지를 다녀온 것으로 적혀 있었나" 를 답할 수 있다.
 */
@Repository
@Profile({ "db", "dev" })
public class JpaItineraryItemActualRepository implements ItineraryItemActualRepository {

	/**
	 * {@code EXCLUDED} 는 "넣으려던 행" 을 가리키는 PostgreSQL 의 이름이다. 값을 두 번 바인딩하지
	 * 않으려고 쓴다 — 같은 값을 {@code VALUES} 와 {@code SET} 에 각각 적으면 나중에 한쪽만
	 * 고치는 날이 온다.
	 */
	private static final String UPSERT = """
			INSERT INTO itinerary_item_actual
			    (itinerary_item_actual_id, itinerary_id, item_key, arrived_at, departed_at, recorded_by,
			     created_at, updated_at)
			VALUES (?1, ?2, ?3, ?4, ?5, ?6, ?7, ?8)
			ON CONFLICT (itinerary_id, item_key) DO UPDATE
			   SET arrived_at  = EXCLUDED.arrived_at,
			       departed_at = EXCLUDED.departed_at,
			       recorded_by = EXCLUDED.recorded_by,
			       updated_at  = EXCLUDED.updated_at
			""";

	private final ItineraryItemActualJpaRepository jpaRepository;

	@PersistenceContext
	private EntityManager entityManager;

	public JpaItineraryItemActualRepository(ItineraryItemActualJpaRepository jpaRepository) {
		this.jpaRepository = jpaRepository;
	}

	@Override
	@Transactional
	public ItineraryItemActual upsert(ItineraryItemActual actual) {
		OffsetDateTime recordedAt = toOffset(actual.recordedAt());

		this.entityManager.createNativeQuery(UPSERT)
				// 새 행이 될 때만 쓰이는 대리 키다. 충돌하면 기존 행의 키가 그대로 남는다 —
				// 그래서 이 값은 도메인 record 에 실려 있지 않다.
				.setParameter(1, UUID.randomUUID())
				.setParameter(2, UUID.fromString(actual.itineraryId()))
				.setParameter(3, UUID.fromString(actual.itemKey()))
				.setParameter(4, toOffset(actual.arrivedAt()))
				.setParameter(5, toOffset(actual.departedAt()))
				.setParameter(6, UUID.fromString(actual.recordedBy()))
				.setParameter(7, recordedAt)
				.setParameter(8, recordedAt)
				.executeUpdate();

		return actual;
	}

	@Override
	@Transactional(readOnly = true)
	public List<ItineraryItemActual> findByItineraryId(String itineraryId) {
		return this.jpaRepository.findByItineraryId(UUID.fromString(itineraryId)).stream()
				.map(JpaItineraryItemActualRepository::toDomain)
				.toList();
	}

	/**
	 * {@code updated_at} 을 도메인의 {@code recordedAt} 으로 옮긴다 — 마지막으로 적은 시각이
	 * 화면이 보여줄 "언제 기록했나" 다.
	 */
	private static ItineraryItemActual toDomain(ItineraryItemActualJpaEntity e) {
		return new ItineraryItemActual(
				e.itineraryId().toString(),
				e.itemKey().toString(),
				toInstant(e.arrivedAt()),
				toInstant(e.departedAt()),
				e.recordedBy().toString(),
				toInstant(e.updatedAt()));
	}

	private static OffsetDateTime toOffset(Instant instant) {
		return instant == null ? null : instant.atOffset(ZoneOffset.UTC);
	}

	private static Instant toInstant(OffsetDateTime offsetDateTime) {
		return offsetDateTime == null ? null : offsetDateTime.toInstant();
	}
}
