package com.gabolle.backend.trip;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.gabolle.backend.recommendation.support.PostgresAvailableCondition;
import com.gabolle.backend.recommendation.support.TestDatabase;
import com.gabolle.backend.trip.domain.PersonalizationScope;
import com.gabolle.backend.trip.domain.PreferenceSnapshot;
import com.gabolle.backend.trip.domain.Trip;
import com.gabolle.backend.trip.domain.TripMember;
import com.gabolle.backend.trip.domain.TripRepository;
import com.gabolle.testslice.TripSliceApplication;

/**
 * {@code trip} 표가 실제 PostgreSQL 에 저장·복원되는지 — S15P21E201-461.
 *
 * <p>🔴 <b>범위를 일부러 좁혔다.</b> 멤버·취향 스냅샷·제약은 아직 메모리다
 * ({@link com.gabolle.backend.trip.infra.JpaTripRepository} 주석 참고) — 그래서 이
 * 테스트는 {@code trip} 한 줄의 왕복만 본다. H2 가 아니라 진짜 PostgreSQL 을 쓰는 이유는
 * {@code TIMESTAMPTZ}·{@code UUID}·CHECK 제약이 PostgreSQL 에서만 진짜로 검증되기
 * 때문이다(recommendation 쪽 통합 테스트와 같은 근거).
 */
@SpringBootTest(classes = TripSliceApplication.class, properties = {
		"spring.profiles.active=db",
		"spring.jpa.hibernate.ddl-auto=none",
		"spring.flyway.enabled=true"
})
@ExtendWith(PostgresAvailableCondition.class)
class TripPersistenceIntegrationTest {

	@DynamicPropertySource
	static void datasourceProperties(DynamicPropertyRegistry registry) {
		TestDatabase.registerDatasource(registry);
	}

	@Autowired
	private TripRepository tripRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private String userId;

	@BeforeEach
	void seedOwner() {
		// trip.owner_user_id 가 app_user 를 외래키로 참조한다 — 먼저 있어야 한다.
		userId = UUID.randomUUID().toString();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		jdbcTemplate.update(
				"INSERT INTO app_user (user_id, display_name, language, personalization_mode, status, created_at, updated_at) "
						+ "VALUES (?, 'test', 'ko', 'PERSONALIZED', 'ACTIVE', ?, ?)",
				UUID.fromString(userId), now, now);
	}

	@Test
	void tripRoundTripsThroughPostgres() {
		Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
		String tripId = UUID.randomUUID().toString();

		Trip trip = new Trip(tripId, userId,
				LocalDate.of(2026, 9, 6), LocalDate.of(2026, 9, 8),
				35.1587, 129.1604, 300000, 2,
				"MORNING_TO_EVENING", "Asia/Seoul", now);

		TripMember owner = TripMember.owner(UUID.randomUUID().toString(), tripId, userId, now);
		PreferenceSnapshot snapshot = new PreferenceSnapshot(
				UUID.randomUUID().toString(), tripId, 1, List.of(), PersonalizationScope.TRIP, List.of(), now);

		tripRepository.save(trip, List.of(), owner, snapshot);

		Trip found = tripRepository.findById(tripId).orElseThrow();

		assertThat(found.tripId()).isEqualTo(tripId);
		assertThat(found.createdBy()).isEqualTo(userId);
		assertThat(found.startDate()).isEqualTo(LocalDate.of(2026, 9, 6));
		assertThat(found.finishDate()).isEqualTo(LocalDate.of(2026, 9, 8));
		assertThat(found.originLat()).isEqualTo(35.1587);
		assertThat(found.originLng()).isEqualTo(129.1604);
		assertThat(found.budgetKrw()).isEqualTo(300000);
		assertThat(found.partySize()).isEqualTo(2);
		assertThat(found.timeWindow()).isEqualTo("MORNING_TO_EVENING");
		assertThat(found.timezone()).isEqualTo("Asia/Seoul");
		assertThat(found.status()).isEqualTo(Trip.Status.PLANNING);
		assertThat(found.createdAt()).isEqualTo(now);
		assertThat(found.updatedAt()).isEqualTo(now);
		assertThat(found.deletedAt()).isNull();
	}

	@Test
	void unknownTripIdIsEmpty() {
		assertThat(tripRepository.findById(UUID.randomUUID().toString())).isEmpty();
	}
}
