package com.gabolle.backend.place;

import java.time.OffsetDateTime;
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

import com.gabolle.backend.place.service.SavedPlaceService;
import com.gabolle.backend.recommendation.support.PersonalizationFixture;
import com.gabolle.backend.recommendation.support.PostgresAvailableCondition;
import com.gabolle.backend.recommendation.support.TestDatabase;
import com.gabolle.testslice.ItinerarySliceApplication;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 하트가 실제로 {@code event_outbox} 까지 닿는가. {@code SavedPlaceControllerTest} 는
 * {@code EventIngestService} 를 목으로 세워 "불렸는가" 만 보므로 진짜 DB 가 따로 필요하다 —
 * Outbox 의 {@code MANDATORY} 트랜잭션, {@code aggregate_id} 의 NOT NULL, 개인정보 검사와
 * 멱등 처리는 목이 전부 건너뛴다.
 */
@SpringBootTest(classes = ItinerarySliceApplication.class, properties = {
		"spring.profiles.active=db",
		"spring.jpa.hibernate.ddl-auto=none",
		"spring.flyway.enabled=true"
})
@ExtendWith(PostgresAvailableCondition.class)
class SavedPlaceEventIntegrationTest {

	// PostgresAvailableCondition 은 DB 가 있는지만 보고, 그 주소를 스프링에 넘기는 것은 이쪽이다.
	// 없으면 컨텍스트가 "적절한 드라이버를 못 찾겠다" 로 죽는다.
	@DynamicPropertySource
	static void datasourceProperties(DynamicPropertyRegistry registry) {
		TestDatabase.registerDatasource(registry);
	}

	@Autowired
	private SavedPlaceService savedPlaces;

	@Autowired
	private JdbcTemplate jdbc;

	private UUID userId;

	private UUID placeId;

	@BeforeEach
	void setUp() {
		this.userId = PersonalizationFixture.insertUser(this.jdbc);
		// 기본 fixture 는 EXPLICIT_ONLY(행동 개인화 OFF)라 켜 준다. 끈 사람 쪽은 따로 잰다.
		enableBehavior(this.userId);
		this.placeId = insertPlace();
	}

	@Test
	@DisplayName("🔴 하트를 누르면 place_like 가 event_outbox 에 실제로 남는다")
	void pressingHeartWritesPlaceLike() {
		this.savedPlaces.save(this.userId, this.placeId);

		List<Map<String, Object>> rows = this.jdbc.queryForList(
				"SELECT aggregate_type, aggregate_id, user_id, producer, payload::text AS payload "
						+ "FROM event_outbox WHERE event_type = ? AND user_id = ?",
				"place_like", this.userId);

		assertThat(rows).as("하트는 눌렸는데 이벤트가 없다 — 배관이 끊긴 것이다").hasSize(1);
		// 축이 user 여야 한다 — 하트는 여행 밖 화면에서도 눌리므로 trip 축이면 적을 수가 없다.
		assertThat(rows.get(0).get("aggregate_type")).isEqualTo("user");
		assertThat(rows.get(0).get("aggregate_id")).hasToString(this.userId.toString());
		assertThat(rows.get(0).get("producer")).hasToString("SERVER");
		assertThat((String) rows.get(0).get("payload")).contains(this.placeId.toString());
	}

	/**
	 * 단위 검사도 같은 것을 보지만 거기서는 {@code insertIfAbsent} 가 목이라 0 을 돌려주라고
	 * 시킨 것이다. 여기서는 진짜 {@code ON CONFLICT DO NOTHING} 이 판정한다.
	 */
	@Test
	@DisplayName("🔴 하트를 두 번 눌러도 이벤트는 하나다 — DB 가 판정한다")
	void pressingTwiceWritesOnlyOneEvent() {
		this.savedPlaces.save(this.userId, this.placeId);
		this.savedPlaces.save(this.userId, this.placeId);

		Integer events = this.jdbc.queryForObject(
				"SELECT count(*) FROM event_outbox WHERE event_type = ? AND user_id = ?",
				Integer.class, "place_like", this.userId);
		Integer saved = this.jdbc.queryForObject(
				"SELECT count(*) FROM saved_place WHERE user_id = ? AND place_id = ?",
				Integer.class, this.userId, this.placeId);

		assertThat(saved).as("하트 자체가 둘이 되면 안 된다").isEqualTo(1);
		assertThat(events).as("두 번째 누름이 신호를 하나 더 만들면 손가락 빠른 사람의 취향이 세진다").isEqualTo(1);
	}

	@Test
	@DisplayName("🔴 개인화를 끈 사람은 이벤트가 안 남지만 하트는 저장된다")
	void optedOutUserSavesWithoutEvent() {
		UUID optedOut = PersonalizationFixture.insertUser(this.jdbc);   // 기본값이 EXPLICIT_ONLY 다

		this.savedPlaces.save(optedOut, this.placeId);

		Integer events = this.jdbc.queryForObject(
				"SELECT count(*) FROM event_outbox WHERE event_type = ? AND user_id = ?",
				Integer.class, "place_like", optedOut);
		Integer saved = this.jdbc.queryForObject(
				"SELECT count(*) FROM saved_place WHERE user_id = ? AND place_id = ?",
				Integer.class, optedOut, this.placeId);

		assertThat(events).as("껐는데 남으면 동의를 어긴 것이다").isZero();
		assertThat(saved).as("안 모으는 것이 저장을 막는 것이 되면 안 된다").isEqualTo(1);
	}

	private void enableBehavior(UUID userId) {
		this.jdbc.update("UPDATE app_user SET personalization_mode = ? WHERE user_id = ?",
				"BEHAVIOR_ENABLED", userId);
	}

	private UUID insertPlace() {
		UUID id = UUID.randomUUID();
		this.jdbc.update("""
				INSERT INTO place (place_id, name_ko, category, lat, lng, created_at, dataset_version)
				VALUES (?, ?, ?, ?, ?, ?, ?)
				""", id, "하트 검사용 장소", "FOOD", 35.16, 129.06, OffsetDateTime.now(), "test-v1");
		return id;
	}
}
