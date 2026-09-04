package com.gabolle.backend.itinerary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.gabolle.backend.itinerary.domain.ItineraryRepository;
import com.gabolle.backend.itinerary.domain.ItineraryVersion;
import com.gabolle.backend.itinerary.domain.StaleItineraryVersionException;
import com.gabolle.backend.recommendation.support.PostgresAvailableCondition;
import com.gabolle.backend.recommendation.support.TestDatabase;
import com.gabolle.testslice.ItinerarySliceApplication;

/**
 * {@code itineraries}·{@code itinerary_versions} 가 실제 PostgreSQL 위에서 도는지 —
 * S15P21E201-313.
 *
 * <p>🔴 <b>이 테스트가 증명하는 핵심</b>은 {@code UNIQUE (itinerary_id, version)} 위반이
 * 진짜 PostgreSQL 에서 {@link StaleItineraryVersionException}(409)으로 바뀌는가다.
 * H2 로는 이 계약을 진짜로 검증할 수 없다.
 */
@SpringBootTest(classes = ItinerarySliceApplication.class, properties = {
		"spring.profiles.active=db",
		"spring.jpa.hibernate.ddl-auto=none",
		"spring.flyway.enabled=true"
})
@ExtendWith(PostgresAvailableCondition.class)
class ItineraryPersistenceIntegrationTest {

	@DynamicPropertySource
	static void datasourceProperties(DynamicPropertyRegistry registry) {
		TestDatabase.registerDatasource(registry);
	}

	@Autowired
	private ItineraryRepository itineraryRepository;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private String itineraryId;
	private String tripId;
	private String userId;

	@BeforeEach
	void seedItineraryAtVersion1() {
		userId = UUID.randomUUID().toString();
		tripId = UUID.randomUUID().toString();
		itineraryId = UUID.randomUUID().toString();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);

		jdbcTemplate.update(
				"INSERT INTO app_user (user_id, display_name, language, personalization_mode, status, created_at, updated_at) "
						+ "VALUES (?, 'test', 'ko', 'PERSONALIZED', 'ACTIVE', ?, ?)",
				UUID.fromString(userId), now, now);
		jdbcTemplate.update(
				"INSERT INTO trip (trip_id, owner_user_id, start_date, end_date, party_size, created_at, updated_at) "
						+ "VALUES (?, ?, '2026-09-06', '2026-09-08', 1, ?, ?)",
				UUID.fromString(tripId), UUID.fromString(userId), now, now);
		jdbcTemplate.update(
				"INSERT INTO itineraries (itinerary_id, trip_id, latest_version, created_at) VALUES (?, ?, 1, ?)",
				UUID.fromString(itineraryId), UUID.fromString(tripId), now);
	}

	private ItineraryVersion versionCandidate(int version, Integer baseVersion) {
		Instant now = Instant.now().truncatedTo(ChronoUnit.MICROS);
		return new ItineraryVersion(
				UUID.randomUUID().toString(), itineraryId, version, baseVersion,
				ItineraryVersion.Operation.REPLACE_ITEM, userId, "req_edit_" + UUID.randomUUID(),
				new ItineraryVersion.Versions(null, null, null, null, null), now);
	}

	@Test
	void appendMovesLatestVersionPointer() {
		itineraryRepository.append(versionCandidate(2, 1));

		var itinerary = itineraryRepository.findById(itineraryId).orElseThrow();
		assertThat(itinerary.latestVersion()).isEqualTo(2);

		var saved = itineraryRepository.findVersion(itineraryId, 2).orElseThrow();
		assertThat(saved.operation()).isEqualTo(ItineraryVersion.Operation.REPLACE_ITEM);
		assertThat(saved.createdBy()).isEqualTo(userId);
	}

	@Test
	void duplicateVersionIsRejectedAsConflict() {
		itineraryRepository.append(versionCandidate(2, 1));

		assertThrows(StaleItineraryVersionException.class,
				() -> itineraryRepository.append(versionCandidate(2, 1)));

		// 🔴 진 쪽 시도로 포인터가 어긋나지 않는다 — 여전히 2다.
		assertThat(itineraryRepository.findById(itineraryId).orElseThrow().latestVersion()).isEqualTo(2);
	}
}
