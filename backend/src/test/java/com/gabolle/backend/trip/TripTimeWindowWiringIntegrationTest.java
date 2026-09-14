package com.gabolle.backend.trip;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Array;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.gabolle.backend.recommendation.support.PostgresAvailableCondition;
import com.gabolle.backend.recommendation.support.TestDatabase;
import com.gabolle.backend.trip.application.TripCreationService;
import com.gabolle.backend.trip.domain.PreferenceSnapshot;
import com.gabolle.backend.trip.domain.TimeWindows;
import com.gabolle.backend.trip.domain.TravelModes;
import com.gabolle.testslice.TripSliceApplication;

/**
 * S15P21E201-664 — {@code TripCreationService.create} 가 하루 활동 시간대·이동수단을
 * 실제로 {@code trip} 표(time_window_start · time_window_end · travel_modes)에
 * 저장하는지 진짜 PostgreSQL 로 검증한다.
 *
 * <p>{@link TripPersistenceIntegrationTest} 와 같은 이유로 H2 가 아니라 진짜
 * PostgreSQL 을 쓴다 — 배열 컬럼(travel_modes)과 CHECK 제약이 PostgreSQL 에서만
 * 진짜로 검증되기 때문이다.
 */
@SpringBootTest(classes = TripSliceApplication.class, properties = {
		"spring.profiles.active=db",
		"spring.jpa.hibernate.ddl-auto=none",
		"spring.flyway.enabled=true"
})
@ExtendWith(PostgresAvailableCondition.class)
class TripTimeWindowWiringIntegrationTest {

	@DynamicPropertySource
	static void datasourceProperties(DynamicPropertyRegistry registry) {
		TestDatabase.registerDatasource(registry);
	}

	@Autowired
	private TripCreationService creationService;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private String userId;

	@BeforeEach
	void seedOwner() {
		userId = UUID.randomUUID().toString();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		jdbcTemplate.update(
				"INSERT INTO app_user (user_id, display_name, language, personalization_mode, status, created_at, updated_at) "
						+ "VALUES (?, 'test', 'ko', 'EXPLICIT_ONLY', 'ACTIVE', ?, ?)",
				UUID.fromString(userId), now, now);
	}

	private TripCreationService.Command command(
			String timeWindow, List<PreferenceSnapshot.PreferenceAnswer> preferences) {
		return new TripCreationService.Command(
				userId,
				LocalDate.of(2026, 9, 6), LocalDate.of(2026, 9, 8),
				35.1587, 129.1604, null, 1, // 출발지 좌표 - 이 검사들이 재는 것은 시간대·이동수단 배선이지 좌표가 아니다
				timeWindow, "Asia/Seoul",
				preferences, List.of());
	}

	private Map<String, Object> readTripRow(String tripId) {
		List<Map<String, Object>> rows = jdbcTemplate.queryForList(
				"SELECT time_window, time_window_start, time_window_end, travel_modes "
						+ "FROM trip WHERE trip_id = ?::uuid",
				tripId);
		return rows.isEmpty() ? null : rows.get(0);
	}

	private static String[] travelModesOf(Map<String, Object> row) throws Exception {
		Array array = (Array) row.get("travel_modes");
		return (String[]) array.getArray();
	}

	@Test
	@DisplayName("실제 시각 범위 timeWindow 와 TRANSIT 이 그대로 저장된다")
	void realTimeRangeAndTransitArePersisted() throws Exception {
		var result = creationService.create(
				command("09:00-18:00", List.of(new PreferenceSnapshot.PreferenceAnswer(
						"transport", "\"TRANSIT\"", PreferenceSnapshot.AnswerStatus.SELECTED))),
				null);

		Map<String, Object> row = readTripRow(result.trip().tripId());
		assertEquals("09:00-18:00", row.get("time_window"), "원문이 그대로 보존된다");
		assertEquals(LocalTime.of(9, 0), ((java.sql.Time) row.get("time_window_start")).toLocalTime());
		assertEquals(LocalTime.of(18, 0), ((java.sql.Time) row.get("time_window_end")).toLocalTime());
		assertEquals(List.of("BUS", "SUBWAY"), List.of(travelModesOf(row)));
	}

	@Test
	@DisplayName("아직 확정 안 된 프리셋은 저장은 되지만 시각 두 칸은 비어 있다")
	void unresolvedPresetSavesWithoutTimes() {
		var result = creationService.create(
				command("MORNING_TO_EVENING", List.of()), null);

		Map<String, Object> row = readTripRow(result.trip().tripId());
		assertEquals("MORNING_TO_EVENING", row.get("time_window"));
		assertNull(row.get("time_window_start"));
		assertNull(row.get("time_window_end"));
	}

	@Test
	@DisplayName("범위 모양인데 끝이 시작보다 앞이면 거부되고 행이 생기지 않는다")
	void invalidRangeIsRejectedAndNoRowIsCreated() {
		int before = countTrips();

		assertThrows(TimeWindows.InvalidTimeWindowException.class,
				() -> creationService.create(command("18:00-09:00", List.of()), null));

		assertEquals(before, countTrips(), "실패한 생성은 행을 남기지 않는다");
	}

	@Test
	@DisplayName("이해 못 하는 이동수단은 거부되고 행이 생기지 않는다")
	void unsupportedTravelModeIsRejectedAndNoRowIsCreated() {
		int before = countTrips();

		assertThrows(TravelModes.UnsupportedTravelModeException.class,
				() -> creationService.create(
						command("09:00-18:00", List.of(new PreferenceSnapshot.PreferenceAnswer(
								"transport", "\"HELICOPTER\"", PreferenceSnapshot.AnswerStatus.SELECTED))),
						null));

		assertEquals(before, countTrips(), "실패한 생성은 행을 남기지 않는다");
	}

	@Test
	@DisplayName("같은 Idempotency-Key 로 두 번 보내도 같은 여행이 돌아온다 - 파싱 규칙은 지문에 영향 없다")
	void sameIdempotencyKeyReturnsSameTrip() {
		var command = command("09:00-18:00", List.of(new PreferenceSnapshot.PreferenceAnswer(
				"transport", "\"TRANSIT\"", PreferenceSnapshot.AnswerStatus.SELECTED)));

		var first = creationService.create(command, "key_wiring_1");
		var second = creationService.create(command, "key_wiring_1");

		assertTrue(first.created());
		assertEquals(first.trip().tripId(), second.trip().tripId());
	}

	private int countTrips() {
		Integer count = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM trip", Integer.class);
		return count == null ? 0 : count;
	}
}
