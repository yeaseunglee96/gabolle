package com.gabolle.backend.trip;

import static com.gabolle.backend.trip.support.TripCommands.withLodging;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
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
import com.gabolle.backend.trip.domain.TripConstraint;
import com.gabolle.testslice.TripSliceApplication;

/**
 * 여행을 만들 때 사람이 직접 넣은 것이 이벤트로 남는다 (S15P21E201-1689) — 계획서 P0 의 {@code trip_created} ·
 * {@code preference_set} · {@code constraint_set}. 전에는 종류만 있고 내는 곳이 없어 운영에 0건이었다.
 *
 * <p>이 사람은 행동 기반 개인화를 안 켰다(EXPLICIT_ONLY) — 명시 입력은 동의와 무관하게 적혀야 한다.
 */
@SpringBootTest(classes = TripSliceApplication.class, properties = {
		"spring.profiles.active=db",
		"spring.jpa.hibernate.ddl-auto=none",
		"spring.flyway.enabled=true"
})
@ExtendWith(PostgresAvailableCondition.class)
class TripCreationEventsIntegrationTest {

	@DynamicPropertySource
	static void datasourceProperties(DynamicPropertyRegistry registry) {
		TestDatabase.registerDatasource(registry);
	}

	@Autowired
	private TripCreationService creationService;

	@Autowired
	private JdbcTemplate jdbc;

	private String userId;

	@BeforeEach
	void seedOwner() {
		this.userId = UUID.randomUUID().toString();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		this.jdbc.update("INSERT INTO app_user (user_id, display_name, language, personalization_mode, status, "
				+ "created_at, updated_at) VALUES (?, 'test', 'ko', 'EXPLICIT_ONLY', 'ACTIVE', ?, ?)",
				UUID.fromString(this.userId), now, now);
		// 알레르기 제약을 넣으려면 건강·식이 동의가 있어야 한다.
		this.jdbc.update("INSERT INTO user_consent (consent_id, user_id, consent_type, status, policy_version, decided_at) "
				+ "VALUES (?, ?, 'HEALTH_CONSTRAINTS', 'GRANTED', '2026-01', ?)",
				UUID.randomUUID(), UUID.fromString(this.userId), now);
	}

	@Test
	@DisplayName("🔴 여행 하나를 만들면 trip_created 1 · 답한 취향마다 preference_set · 답한 제약마다 constraint_set — 동의와 무관")
	void creatingATripRecordsTheExplicitInputs() {
		var result = this.creationService.create(command(), null);
		String tripId = result.trip().tripId();

		assertThat(countOf(tripId, "trip_created")).isEqualTo(1);
		// 이동 수단·여행 기분은 여행의 모양이라 trip_created 에 들어가고 취향 이벤트로는 안 남는다.
		assertThat(countOf(tripId, "preference_set")).isEqualTo(1);
		assertThat(countOf(tripId, "constraint_set")).isEqualTo(2);

		Map<String, Object> created = payloadOf(tripId, "trip_created").get(0);
		assertThat(created).containsEntry("party_size", 2).containsEntry("pace", "PACKED")
				.containsEntry("travel_modes", List.of("BUS", "SUBWAY")).containsEntry("has_origin", true)
				.containsEntry("trip_version", 1);
		// 🔴 좌표는 싣지 않는다.
		assertThat(created.toString()).doesNotContain("35.15").doesNotContain("129.16");

		Map<String, Object> preference = payloadOf(tripId, "preference_set").get(0);
		assertThat(preference).containsEntry("scope", "TRIP").containsEntry("dimension", "CATEGORY")
				.containsEntry("answer_status", "SELECTED");
	}

	@Test
	@DisplayName("🔴 제약 이벤트에는 값을 싣지 않고, 알레르기는 항목 이름도 싣지 않는다 — 건강 정보다")
	void constraintEventsCarryNoValues() {
		var result = this.creationService.create(command(), null);
		List<Map<String, Object>> constraints = payloadOf(result.trip().tripId(), "constraint_set");

		Map<String, Object> walking = constraints.stream()
				.filter((p) -> "MOBILITY".equals(p.get("constraint_type"))).findFirst().orElseThrow();
		assertThat(walking).containsEntry("constraint_key", "MAX_WALKING_METERS").containsEntry("hard", true)
				.containsEntry("has_value", true).doesNotContainKey("threshold").doesNotContainKey("value");

		Map<String, Object> allergy = constraints.stream()
				.filter((p) -> "ALLERGY".equals(p.get("constraint_type"))).findFirst().orElseThrow();
		assertThat(allergy.get("constraint_key")).isNull();
		assertThat(allergy.toString()).doesNotContain("PEANUT");
	}

	@Test
	@DisplayName("같은 키로 다시 온 요청은 여행을 안 만들었으므로 이벤트도 다시 안 남긴다")
	void idempotentRetryRecordsNothingNew() {
		var first = this.creationService.create(command(), "same-key");
		this.creationService.create(command(), "same-key");

		assertThat(countOf(first.trip().tripId(), "trip_created")).isEqualTo(1);
	}

	private TripCreationService.Command command() {
		TripCreationService.Command base = new TripCreationService.Command(this.userId,
				LocalDate.of(2026, 10, 15), LocalDate.of(2026, 10, 16), 35.1587, 129.1604, 300000, 2, "09:00-21:00",
				"Asia/Seoul",
				List.of(new PreferenceSnapshot.PreferenceAnswer("CATEGORY", "{\"codes\": [\"SEA_BEACH\"]}",
								PreferenceSnapshot.AnswerStatus.SELECTED),
						new PreferenceSnapshot.PreferenceAnswer("pace", "\"PACKED\"",
								PreferenceSnapshot.AnswerStatus.SELECTED),
						new PreferenceSnapshot.PreferenceAnswer("transport", "\"TRANSIT\"",
								PreferenceSnapshot.AnswerStatus.SELECTED)),
				List.of(new TripCreationService.Command.ConstraintInput("MOBILITY", "MAX_WALKING_METERS",
								TripConstraint.Severity.HARD, "LTE", null, 1000.0, null, TripConstraint.AnswerStatus.SELECTED,
								null),
						new TripCreationService.Command.ConstraintInput("ALLERGY", "PEANUT",
								TripConstraint.Severity.HARD, "EQ", "true", null, null, TripConstraint.AnswerStatus.SELECTED,
								null)));
		return withLodging(base);
	}

	private int countOf(String tripId, String type) {
		return this.jdbc.queryForObject("SELECT count(*) FROM event_outbox WHERE trip_id = ?::uuid AND event_type = ?",
				Integer.class, tripId, type);
	}

	private List<Map<String, Object>> payloadOf(String tripId, String type) {
		return this.jdbc.queryForList("SELECT payload::text FROM event_outbox WHERE trip_id = ?::uuid AND event_type = ?",
				String.class, tripId, type).stream().map(TripCreationEventsIntegrationTest::parse).toList();
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> parse(String json) {
		return tools.jackson.databind.json.JsonMapper.builder().build().readValue(json, Map.class);
	}
}
