package com.gabolle.backend.calibration;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.gabolle.backend.itinerary.domain.ItineraryItem;
import com.gabolle.backend.itinerary.domain.ItineraryLeg;
import com.gabolle.backend.itinerary.domain.ItineraryRepository;
import com.gabolle.backend.itinerary.domain.ItineraryVersion;
import com.gabolle.backend.recommendation.support.PostgresAvailableCondition;
import com.gabolle.backend.recommendation.support.TestDatabase;
import com.gabolle.testslice.ItinerarySliceApplication;

/**
 * 이동 보정의 DB 쪽 (S15P21E201-1700) — 보정 전 이동 분 칸, 계산이 읽는 창구 뷰, 엔진이 읽는 배율.
 *
 * <p>뷰의 규칙: 같은 날 실제로 도착한 순서에서 앞 곳 출발 → 다음 곳 도착. 앞 곳 출발이 비면 빼고, 계획에서 붙어 있지 않은
 * 쌍(건너뛴 곳 · 순서를 바꿔 다닌 곳)은 어림이 없어 빠진다. 다른 시험도 구간을 만들므로 이 실행에만 있는 어림 분을 써서
 * 그것만 센다.
 */
@SpringBootTest(classes = ItinerarySliceApplication.class, properties = {
		"spring.profiles.active=db",
		"spring.jpa.hibernate.ddl-auto=none",
		"spring.flyway.enabled=true"
})
@ExtendWith(PostgresAvailableCondition.class)
class CalibrationTravelIntegrationTest {

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	@DynamicPropertySource
	static void datasourceProperties(DynamicPropertyRegistry registry) {
		TestDatabase.registerDatasource(registry);
	}

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private ItineraryRepository itineraryRepository;

	private UUID userId;

	/** 이 실행에만 있는 어림 분의 바탕. */
	private int base;

	@BeforeEach
	void setUp() {
		this.base = 1_000 + ThreadLocalRandom.current().nextInt(50_000);
		this.userId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		this.jdbc.update("INSERT INTO app_user (user_id, display_name, language, personalization_mode, status, "
				+ "created_at, updated_at) VALUES (?, 'test', 'ko', 'EXPLICIT_ONLY', 'ACTIVE', ?, ?)", this.userId, now, now);
		this.jdbc.update("DELETE FROM calibration_value WHERE job = ?", CalibrationValueTravelCalibration.JOB);
	}

	@Test
	@DisplayName("🔴 도착한 순서대로 앞 곳 출발 → 다음 곳 도착. 앞 곳 출발이 비면 그 구간은 뺀다")
	void actualTravelFollowsArrivalOrderAndSkipsMissingDepartures() {
		Plan plan = plan(4);
		int e1 = this.base;
		int e2 = this.base + 1;
		int e3 = this.base + 2;
		leg(plan, 0, 1, e1, null);
		leg(plan, 1, 2, e2, null);
		leg(plan, 2, 3, e3, null);

		OffsetDateTime t = at(9, 0);
		actual(plan, 0, t, t.plusMinutes(30));
		OffsetDateTime bArrive = t.plusMinutes(30 + e1 + 5);
		actual(plan, 1, bArrive, null); // B 출발을 안 찍었다 → B→C 는 뺀다
		OffsetDateTime cArrive = bArrive.plusMinutes(60);
		actual(plan, 2, cArrive, cArrive.plusMinutes(30));
		actual(plan, 3, cArrive.plusMinutes(30 + e3 + 7), null);

		assertThat(rows(e1, e2, e3)).extracting((r) -> ((Number) r.get("estimated_minutes")).intValue() + "→"
				+ Math.round(((Number) r.get("actual_minutes")).doubleValue()))
				.containsExactlyInAnyOrder(e1 + "→" + (e1 + 5), e3 + "→" + (e3 + 7));
	}

	@Test
	@DisplayName("🔴 건너뛴 곳 · 순서를 바꿔 다닌 곳은 계획에 그 쌍의 어림이 없어 안 센다 — 지어내지 않는다")
	void pairsThatWereNotAdjacentInThePlanAreLeftOut() {
		Plan skipped = plan(3);
		leg(skipped, 0, 1, this.base, null);
		leg(skipped, 1, 2, this.base + 1, null);
		OffsetDateTime t = at(9, 0);
		actual(skipped, 0, t, t.plusMinutes(30));
		actual(skipped, 2, t.plusMinutes(120), null); // 가운데를 건너뜀 → A→C

		Plan swapped = plan(3);
		leg(swapped, 0, 1, this.base + 2, null);
		leg(swapped, 1, 2, this.base + 3, null);
		actual(swapped, 0, t, t.plusMinutes(30));
		actual(swapped, 2, t.plusMinutes(60), t.plusMinutes(90)); // A → C → B 로 다녔다
		actual(swapped, 1, t.plusMinutes(120), null);

		assertThat(rows(this.base, this.base + 1, this.base + 2, this.base + 3)).isEmpty();
	}

	@Test
	@DisplayName("🔴 보정 전 칸이 있으면 그것이 어림이다 — 고친 이동 시간과 견주지 않는다")
	void theUncalibratedMinutesAreTheEstimate() {
		Plan plan = plan(2);
		int estimate = this.base;
		leg(plan, 0, 1, estimate + 999_000, estimate); // 이동 시간 칸은 고친 값, 옆 칸이 어림
		OffsetDateTime t = at(9, 0);
		actual(plan, 0, t, t.plusMinutes(30));
		actual(plan, 1, t.plusMinutes(30 + estimate + 3), null);

		assertThat(rows(estimate)).singleElement()
				.satisfies((r) -> assertThat(((Number) r.get("estimated_minutes")).intValue()).isEqualTo(estimate));
	}

	@Test
	@DisplayName("🔴 뷰는 수단 · 거리 구간 · 어림 분 · 실제 분 · 주만 내준다 — 사람·일정·장소 번호와 정확한 시각은 없다")
	void theViewCarriesNoIdentifiers() {
		Plan plan = plan(2);
		leg(plan, 0, 1, this.base, null);
		OffsetDateTime t = at(9, 0);
		actual(plan, 0, t, t.plusMinutes(30));
		actual(plan, 1, t.plusMinutes(30 + this.base + 1), null);

		Map<String, Object> row = rows(this.base).get(0);
		assertThat(row).containsOnlyKeys("mode", "distance_band", "estimated_minutes", "actual_minutes", "visit_week");
		assertThat(row).containsEntry("mode", "BUS").containsEntry("distance_band", "1_3KM");
	}

	@Test
	@DisplayName("보정 전 이동 분이 저장했다 읽어도 그대로다")
	void uncalibratedMinutesSurviveTheRoundTrip() {
		Plan plan = plan(2);
		ItineraryVersion next = new ItineraryVersion(UUID.randomUUID().toString(), plan.itineraryId().toString(), 2, 1,
				ItineraryVersion.Operation.REORDER, this.userId.toString(), "req_" + UUID.randomUUID(),
				new ItineraryVersion.Versions(null, null, null, null, null), Instant.now().truncatedTo(ChronoUnit.MICROS));
		ItineraryLeg leg = new ItineraryLeg(UUID.randomUUID().toString(), null, 0, 1, plan.places().get(0).toString(),
				plan.places().get(1).toString(), "BUS", 2_000, 30, null, null, null, ItineraryItem.DataStatus.ESTIMATED,
				null, null, 25, Instant.now().truncatedTo(ChronoUnit.MICROS));

		this.itineraryRepository.appendVersion(next, List.of(), List.of(leg), List.of());
		ItineraryLeg read = this.itineraryRepository.findContent(plan.itineraryId().toString(), 2).orElseThrow().legs().get(0);

		assertThat(read.durationMin()).isEqualTo(30);
		assertThat(read.uncalibratedDurationMin()).isEqualTo(25);
	}

	@Test
	@DisplayName("🔴 엔진은 수단마다 가장 최근 PASSED 배율만 쓴다 — 보류는 직전 판 유지, 되돌리면 직전 판")
	void theReaderTakesTheLatestPassedPerMode() {
		value(1, "BUS", "PASSED", 1.1);
		value(2, "BUS", "PASSED", 1.25);
		value(1, "WALK", "PASSED", 0.9);
		value(2, "WALK", "HELD", 2.4);

		MutableClock clock = new MutableClock(Instant.parse("2026-10-01T00:00:00Z"));
		CalibrationValueTravelCalibration reader = new CalibrationValueTravelCalibration(this.jdbc, clock);

		assertThat(reader.multiplierFor("BUS")).isEqualTo(OptionalDouble.of(1.25));
		assertThat(reader.multiplierFor("walk")).isEqualTo(OptionalDouble.of(0.9));
		assertThat(reader.multiplierFor("PRIVATE_CAR")).isEmpty();
		assertThat(reader.multiplierFor(null)).isEmpty();

		this.jdbc.update("UPDATE calibration_value SET status = 'REVOKED' WHERE job = ? AND version = 2 AND status = 'PASSED'",
				CalibrationValueTravelCalibration.JOB);
		clock.advance(LatestPassedValues.REFRESH.plusSeconds(1));
		assertThat(reader.multiplierFor("BUS")).isEqualTo(OptionalDouble.of(1.1));
	}

	// ── 자료 만들기 ─────────────────────────────────────────────

	/** 하루짜리 일정 판 1 — 곳마다 항목 하나, 2026-10-15. */
	private record Plan(UUID itineraryId, UUID versionId, List<UUID> itemKeys, List<UUID> places) {
	}

	private Plan plan(int count) {
		UUID tripId = UUID.randomUUID();
		UUID itineraryId = UUID.randomUUID();
		UUID versionId = UUID.randomUUID();
		this.jdbc.update("INSERT INTO trip (trip_id, owner_user_id, start_date, end_date, created_at, updated_at) "
				+ "VALUES (?, ?, DATE '2026-10-15', DATE '2026-10-15', now(), now())", tripId, this.userId);
		this.jdbc.update("INSERT INTO itineraries (itinerary_id, trip_id, latest_version, created_at) VALUES (?, ?, 1, now())",
				itineraryId, tripId);
		this.jdbc.update("INSERT INTO itinerary_versions (itinerary_version_id, itinerary_id, version, operation, "
				+ "created_by, request_id, created_at) VALUES (?, ?, 1, 'CREATE', ?, 'test', now())", versionId, itineraryId,
				this.userId);
		List<UUID> keys = new java.util.ArrayList<>();
		List<UUID> places = new java.util.ArrayList<>();
		for (int i = 0; i < count; i++) {
			UUID place = UUID.randomUUID();
			this.jdbc.update("INSERT INTO place (place_id, name_ko, created_at) VALUES (?, 'test', now())", place);
			UUID key = UUID.randomUUID();
			this.jdbc.update("INSERT INTO itinerary_item (itinerary_item_id, itinerary_version_id, item_key, day_index, "
					+ "visit_date, sequence, place_id, data_status, created_at) "
					+ "VALUES (?, ?, ?, 0, DATE '2026-10-15', ?, ?, 'VERIFIED', now())", UUID.randomUUID(), versionId, key,
					i + 1, place);
			keys.add(key);
			places.add(place);
		}
		return new Plan(itineraryId, versionId, keys, places);
	}

	private void leg(Plan plan, int from, int to, int durationMin, Integer uncalibrated) {
		this.jdbc.update("INSERT INTO itinerary_leg (itinerary_leg_id, itinerary_version_id, day_index, sequence, "
				+ "from_place_id, to_place_id, travel_mode, distance_m, duration_min, uncalibrated_duration_min, created_at) "
				+ "VALUES (?, ?, 0, ?, ?, ?, 'BUS', 2000, ?, ?, now())", UUID.randomUUID(), plan.versionId(), to + 1,
				plan.places().get(from), plan.places().get(to), durationMin, uncalibrated);
	}

	private void actual(Plan plan, int index, OffsetDateTime arrived, OffsetDateTime departed) {
		this.jdbc.update("INSERT INTO itinerary_item_actual (itinerary_item_actual_id, itinerary_id, item_key, arrived_at, "
				+ "departed_at, recorded_by) VALUES (?, ?, ?, ?, ?, ?)", UUID.randomUUID(), plan.itineraryId(),
				plan.itemKeys().get(index), arrived, departed, this.userId);
	}

	private static OffsetDateTime at(int hour, int minute) {
		return LocalDate.of(2026, 10, 15).atTime(hour, minute).atZone(SEOUL).toOffsetDateTime();
	}

	private List<Map<String, Object>> rows(Integer... estimates) {
		String in = String.join(",", java.util.Collections.nCopies(estimates.length, "?"));
		return this.jdbc.queryForList("SELECT * FROM calibration_travel_source WHERE estimated_minutes IN (" + in + ")",
				(Object[]) java.util.Arrays.stream(estimates).map(Integer::doubleValue).toArray(Double[]::new));
	}

	private void value(int version, String key, String status, double value) {
		this.jdbc.update("INSERT INTO calibration_value (job, version, key, basis, status, value, sample_size, window_from, "
				+ "window_to) VALUES (?, ?, ?, 'MEASURED', ?, ?, 40, DATE '2026-07-01', DATE '2026-09-30')",
				CalibrationValueTravelCalibration.JOB, version, key, status, value);
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
