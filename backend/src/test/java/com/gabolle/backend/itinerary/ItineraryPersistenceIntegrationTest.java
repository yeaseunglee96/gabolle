package com.gabolle.backend.itinerary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.time.Instant;
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

import com.gabolle.backend.itinerary.domain.ItineraryRepository;
import com.gabolle.backend.itinerary.domain.ItineraryVersion;
import com.gabolle.backend.itinerary.domain.StaleItineraryVersionException;
import com.gabolle.backend.recommendation.support.PostgresAvailableCondition;
import com.gabolle.backend.recommendation.support.TestDatabase;
import com.gabolle.testslice.ItinerarySliceApplication;

/**
 * {@code itineraries}·{@code itinerary_versions} 가 실제 PostgreSQL 위에서 도는지.
 *
 * <p>증명하는 핵심은 {@code UNIQUE (itinerary_id, version)} 위반이 진짜 PostgreSQL 에서
 * {@link StaleItineraryVersionException}(409)으로 바뀌는가다. H2 로는 이 계약을 검증할 수
 * 없다.
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
						+ "VALUES (?, 'test', 'ko', 'EXPLICIT_ONLY', 'ACTIVE', ?, ?)",
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
		itineraryRepository.appendVersion(versionCandidate(2, 1), List.of(), List.of(), List.of());

		var itinerary = itineraryRepository.findById(itineraryId).orElseThrow();
		assertThat(itinerary.latestVersion()).isEqualTo(2);

		var saved = itineraryRepository.findVersion(itineraryId, 2).orElseThrow();
		assertThat(saved.operation()).isEqualTo(ItineraryVersion.Operation.REPLACE_ITEM);
		assertThat(saved.createdBy()).isEqualTo(userId);
	}

	/**
	 * 판 번호는 비어 있는데 포인터가 그 사이 움직인 경우.
	 *
	 * <p>다른 세션이 최신 포인터를 3으로 옮겨 놓은 상태에서 baseVersion 1 로 2번 판을
	 * 만들려 하면, {@code itinerary_versions} INSERT 는 통과한다(2번 판은 아직 없으니까).
	 * 막는 것은 {@code UPDATE ... WHERE latest_version = 1} 이 0행을 반영하는 것뿐이다 —
	 * 판 번호 UNIQUE 는 번호를 지키는 제약이지 포인터를 지키는 제약이 아니다.
	 *
	 * <p>그리고 되돌려지는지도 함께 본다. 실패한 편집이 2번 판 행을 남기면 "판은 있는데
	 * 아무도 안 가리키는" 찌꺼기가 된다.
	 */
	@Test
	void pointerMovedElsewhereIsRejectedAndRollsBack() {
		jdbcTemplate.update("UPDATE itineraries SET latest_version = 3 WHERE itinerary_id = ?",
				UUID.fromString(itineraryId));

		assertThrows(StaleItineraryVersionException.class,
				() -> itineraryRepository.appendVersion(versionCandidate(2, 1), List.of(), List.of(), List.of()));

		Integer rows = jdbcTemplate.queryForObject(
				"SELECT count(*) FROM itinerary_versions WHERE itinerary_id = ? AND version = 2",
				Integer.class, UUID.fromString(itineraryId));
		assertThat(rows).isZero();
		assertThat(itineraryRepository.findById(itineraryId).orElseThrow().latestVersion()).isEqualTo(3);
	}

	@Test
	void duplicateVersionIsRejectedAsConflict() {
		itineraryRepository.appendVersion(versionCandidate(2, 1), List.of(), List.of(), List.of());

		assertThrows(StaleItineraryVersionException.class,
				() -> itineraryRepository.appendVersion(versionCandidate(2, 1), List.of(), List.of(), List.of()));

		// 진 쪽 시도로 포인터가 어긋나지 않는다 — 여전히 2다.
		assertThat(itineraryRepository.findById(itineraryId).orElseThrow().latestVersion()).isEqualTo(2);
	}
}
