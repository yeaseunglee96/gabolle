package com.gabolle.backend.calibration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
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
import com.gabolle.testslice.TripSliceApplication;

/**
 * 보정 틀의 DB 쪽 (S15P21E201-1692) — 계산이 읽는 창구 뷰와 엔진이 읽는 결과 표.
 *
 * <p>뷰는 실제 DB 에서만 확인된다. 도착·출발은 판을 건너 {@code item_key} 로 이어지고, 추정은 다음 곳 도착과 두 곳 사이
 * 이동을 이어 붙여야 나온다 — 조인 하나가 틀려도 오류가 아니라 「행이 적게 나온다」로 나타난다.
 *
 * <p>다른 시험도 일정을 만들므로 갈래 이름을 이 실행에만 있는 것으로 지어 그것만 센다.
 */
@SpringBootTest(classes = TripSliceApplication.class, properties = {
		"spring.profiles.active=db",
		"spring.jpa.hibernate.ddl-auto=none",
		"spring.flyway.enabled=true"
})
@ExtendWith(PostgresAvailableCondition.class)
class CalibrationStayIntegrationTest {

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	@DynamicPropertySource
	static void datasourceProperties(DynamicPropertyRegistry registry) {
		TestDatabase.registerDatasource(registry);
	}

	@Autowired
	private JdbcTemplate jdbc;

	private String cafe;

	private String sea;

	private UUID userId;

	@BeforeEach
	void setUp() {
		String run = UUID.randomUUID().toString().substring(0, 8).toUpperCase();
		this.cafe = "T1692_CAFE_" + run;
		this.sea = "T1692_SEA_" + run;
		this.userId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		this.jdbc.update("INSERT INTO app_user (user_id, display_name, language, personalization_mode, status, "
				+ "created_at, updated_at) VALUES (?, 'test', 'ko', 'EXPLICIT_ONLY', 'ACTIVE', ?, ?)", this.userId, now, now);
		this.jdbc.update("DELETE FROM calibration_value WHERE job = ?", CalibrationValueStayCalibration.JOB);
	}

	@Test
	@DisplayName("🔴 도착·출발을 둘 다 찍은 곳은 측정, 출발이 없으면 다음 곳 도착 − 이동 시간으로 추정")
	void measuredAndEstimatedStays() {
		UUID cafePlace = place(this.cafe);
		UUID seaPlace = place(this.sea);
		UUID lastPlace = place(this.sea);
		Itinerary it = itinerary(List.of(cafePlace, seaPlace, lastPlace));
		leg(it.versionId(), seaPlace, lastPlace, 20);

		// 카페: 10:00 도착 · 10:50 출발 → 측정 50분
		actual(it, 0, at(10, 0), at(10, 50));
		// 바다: 11:10 도착, 출발 없음 → 다음 곳 13:00 도착 − 이동 20분 → 추정 90분
		actual(it, 1, at(11, 10), null);
		// 마지막 곳: 도착만, 다음 곳이 없어 추정도 못 한다 → 안 나온다
		actual(it, 2, at(13, 0), null);

		assertThat(rowsOf(this.cafe)).singleElement().satisfies((row) -> {
			assertThat(row.get("source")).isEqualTo("MEASURED");
			assertThat(((Number) row.get("stay_minutes")).doubleValue()).isEqualTo(50.0);
		});
		assertThat(rowsOf(this.sea)).singleElement().satisfies((row) -> {
			assertThat(row.get("source")).isEqualTo("ESTIMATED");
			assertThat(((Number) row.get("stay_minutes")).doubleValue()).isEqualTo(90.0);
		});
	}

	@Test
	@DisplayName("🔴 뷰는 갈래 · 머문 분 · 측정/추정 · 주만 내준다 — 사람·일정·장소 번호와 정확한 시각은 없다")
	void theViewCarriesNoIdentifiers() {
		Itinerary it = itinerary(List.of(place(this.cafe)));
		actual(it, 0, at(10, 0), at(10, 45));

		Map<String, Object> row = rowsOf(this.cafe).get(0);
		assertThat(row).containsOnlyKeys("category", "source", "stay_minutes", "visit_week");
		// 주는 그 주 월요일로 뭉갠다 — 2026-10-15(목) → 2026-10-12(월).
		assertThat(row.get("visit_week").toString()).isEqualTo("2026-10-12");
	}

	@Test
	@DisplayName("일정의 가장 최근 판에 없는 항목은 안 센다 — 뺀 곳에 찍힌 도착·출발")
	void onlyTheLatestVersionCounts() {
		UUID kept = place(this.cafe);
		UUID removed = place(this.sea);
		Itinerary first = itinerary(List.of(kept, removed));
		actual(first, 0, at(10, 0), at(10, 40));
		actual(first, 1, at(11, 0), at(12, 0));
		// 판 2 에서 두 번째 곳을 뺐다 — 같은 item_key 로 첫 곳만 남는다.
		nextVersionKeepingOnly(first, 0);

		assertThat(rowsOf(this.cafe)).hasSize(1);
		assertThat(rowsOf(this.sea)).isEmpty();
	}

	@Test
	@DisplayName("🔴 엔진은 갈래마다 가장 최근 PASSED 만 쓴다 — 보류는 직전 판 유지, 추정은 안 씀, 되돌리면 직전 판")
	void theReaderTakesTheLatestPassedPerCategory() {
		value(1, "CAFE_HEALING", "MEASURED", "PASSED", 50.0);
		value(2, "CAFE_HEALING", "MEASURED", "PASSED", 55.4);
		value(1, "FOOD", "MEASURED", "PASSED", 70.0);
		value(2, "FOOD", "MEASURED", "HELD", 12.0);
		value(2, "SEA_BEACH", "ESTIMATED", "REFERENCE", 140.0);

		MutableClock clock = new MutableClock(Instant.parse("2026-10-01T00:00:00Z"));
		CalibrationValueStayCalibration reader = new CalibrationValueStayCalibration(this.jdbc, clock);

		assertThat(reader.minutesFor("CAFE_HEALING")).isEqualTo(OptionalInt.of(55));
		assertThat(reader.minutesFor("food")).isEqualTo(OptionalInt.of(70));
		assertThat(reader.minutesFor("SEA_BEACH")).isEmpty();
		assertThat(reader.minutesFor(null)).isEmpty();

		// 판 2 를 되돌린다 — 기억해 둔 동안은 그대로, 기억이 지나면 직전 PASSED.
		this.jdbc.update("UPDATE calibration_value SET status = 'REVOKED' WHERE job = ? AND version = 2 AND status = 'PASSED'",
				CalibrationValueStayCalibration.JOB);
		assertThat(reader.minutesFor("CAFE_HEALING")).isEqualTo(OptionalInt.of(55));
		clock.advance(CalibrationValueStayCalibration.REFRESH.plusSeconds(1));
		assertThat(reader.minutesFor("CAFE_HEALING")).isEqualTo(OptionalInt.of(50));
	}

	// ── 자료 만들기 ─────────────────────────────────────────────

	private record Itinerary(UUID itineraryId, UUID versionId, List<UUID> itemKeys, List<UUID> places) {
	}

	private UUID place(String category) {
		UUID id = UUID.randomUUID();
		this.jdbc.update("INSERT INTO place (place_id, name_ko, category, created_at) VALUES (?, 'test', ?, now())", id,
				category);
		return id;
	}

	/** 하루짜리 일정 판 1 — 곳마다 항목 하나, 2026-10-15. */
	private Itinerary itinerary(List<UUID> places) {
		UUID tripId = UUID.randomUUID();
		UUID itineraryId = UUID.randomUUID();
		UUID versionId = UUID.randomUUID();
		this.jdbc.update("INSERT INTO trip (trip_id, owner_user_id, start_date, end_date, created_at, updated_at) "
				+ "VALUES (?, ?, DATE '2026-10-15', DATE '2026-10-15', now(), now())", tripId, this.userId);
		this.jdbc.update("INSERT INTO itineraries (itinerary_id, trip_id, latest_version, created_at) VALUES (?, ?, 1, now())",
				itineraryId, tripId);
		version(itineraryId, versionId, 1);
		List<UUID> keys = places.stream().map((p) -> UUID.randomUUID()).toList();
		for (int i = 0; i < places.size(); i++) {
			item(versionId, keys.get(i), i + 1, places.get(i));
		}
		return new Itinerary(itineraryId, versionId, keys, places);
	}

	private void nextVersionKeepingOnly(Itinerary it, int index) {
		UUID versionId = UUID.randomUUID();
		this.jdbc.update("INSERT INTO itinerary_versions (itinerary_version_id, itinerary_id, version, base_version, "
				+ "operation, created_by, request_id, created_at) VALUES (?, ?, 2, 1, 'REMOVE_ITEM', ?, 'test', now())",
				versionId, it.itineraryId(), this.userId);
		item(versionId, it.itemKeys().get(index), 1, it.places().get(index));
		this.jdbc.update("UPDATE itineraries SET latest_version = 2 WHERE itinerary_id = ?", it.itineraryId());
	}

	private void version(UUID itineraryId, UUID versionId, int version) {
		this.jdbc.update("INSERT INTO itinerary_versions (itinerary_version_id, itinerary_id, version, operation, "
				+ "created_by, request_id, created_at) VALUES (?, ?, ?, 'CREATE', ?, 'test', now())", versionId,
				itineraryId, version, this.userId);
	}

	private void item(UUID versionId, UUID itemKey, int sequence, UUID placeId) {
		this.jdbc.update("INSERT INTO itinerary_item (itinerary_item_id, itinerary_version_id, item_key, day_index, "
				+ "visit_date, sequence, place_id, data_status, created_at) "
				+ "VALUES (?, ?, ?, 0, DATE '2026-10-15', ?, ?, 'VERIFIED', now())", UUID.randomUUID(), versionId, itemKey,
				sequence, placeId);
	}

	private void leg(UUID versionId, UUID from, UUID to, int minutes) {
		this.jdbc.update("INSERT INTO itinerary_leg (itinerary_leg_id, itinerary_version_id, day_index, sequence, "
				+ "from_place_id, to_place_id, travel_mode, duration_min, created_at) VALUES (?, ?, 0, 1, ?, ?, 'WALK', ?, now())",
				UUID.randomUUID(), versionId, from, to, minutes);
	}

	private void actual(Itinerary it, int index, OffsetDateTime arrived, OffsetDateTime departed) {
		this.jdbc.update("INSERT INTO itinerary_item_actual (itinerary_item_actual_id, itinerary_id, item_key, arrived_at, "
				+ "departed_at, recorded_by) VALUES (?, ?, ?, ?, ?, ?)", UUID.randomUUID(), it.itineraryId(),
				it.itemKeys().get(index), arrived, departed, this.userId);
	}

	private static OffsetDateTime at(int hour, int minute) {
		return LocalDate.of(2026, 10, 15).atTime(hour, minute).atZone(SEOUL).toOffsetDateTime();
	}

	private List<Map<String, Object>> rowsOf(String category) {
		return this.jdbc.queryForList("SELECT * FROM calibration_stay_source WHERE category = ?", category);
	}

	private void value(int version, String key, String basis, String status, double value) {
		this.jdbc.update("INSERT INTO calibration_value (job, version, key, basis, status, value, sample_size, window_from, "
				+ "window_to, computed_at) VALUES (?, ?, ?, ?, ?, ?, 40, DATE '2026-07-01', DATE '2026-09-30', now())",
				CalibrationValueStayCalibration.JOB, version, key, basis, status, value);
	}

	private static final class MutableClock extends Clock {

		private Instant now;

		MutableClock(Instant now) {
			this.now = now;
		}

		void advance(Duration duration) {
			this.now = this.now.plus(duration);
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return this.now;
		}
	}
}
