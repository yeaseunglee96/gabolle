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
 * 하트가 실제로 {@code event_outbox} 까지 닿는가 — S15P21E201-1080.
 *
 * <h2>🔴 왜 이 검사가 따로 필요한가</h2>
 *
 * {@code SavedPlaceControllerTest} 는 {@code EventIngestService} 를 <b>목</b>으로 세우고
 * "불렸는가" 만 본다. 그건 부르는 쪽의 약속이고, <b>그 호출이 DB 에 행을 남기는지는 아무도
 * 안 보고 있었다.</b>
 *
 * <p>2026-09-17 배포 실측에서 그 공백이 드러났다. {@code event_outbox} 에 행동 이벤트가
 * 0건인데, 그것이 <b>기능이 안 도는 것인지 아무도 하트를 안 누른 것인지 가릴 수가 없었다</b> —
 * {@code saved_place} 에 행이 딱 하나 있었고 그마저 이 기능이 머지되기 <b>한 시간 전</b>에
 * 눌린 것이었다. 즉 운영 데이터로는 증명도 반증도 안 됐다.
 *
 * <p>이 검사가 그 질문을 영구히 닫는다. 진짜 PostgreSQL 에 하트를 누르고 행을 세어 본다.
 *
 * <h2>🔴 목이 아니라 진짜 DB 인 것이 핵심이다</h2>
 *
 * 목으로는 못 잡는 것이 여기 있다 — Outbox 는 {@code MANDATORY} 트랜잭션을 요구하고,
 * {@code aggregate_id} 는 {@code UUID NOT NULL} 이며, 개인정보 검사와 멱등 처리가 그 경로에
 * 끼어 있다. 목은 그 전부를 건너뛴다.
 */
@SpringBootTest(classes = ItinerarySliceApplication.class, properties = {
		"spring.profiles.active=db",
		"spring.jpa.hibernate.ddl-auto=none",
		"spring.flyway.enabled=true"
})
@ExtendWith(PostgresAvailableCondition.class)
class SavedPlaceEventIntegrationTest {

	// 🔴 이것이 없으면 컨텍스트가 "적절한 드라이버를 못 찾겠다" 로 죽는다. 조건(PostgresAvailableCondition)
	//    은 DB 가 있는지만 보고, 그 주소를 스프링에 넘기는 것은 이쪽 몫이다 — 둘 다 있어야 한다.
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
		// 🔴 기본 fixture 는 EXPLICIT_ONLY(행동 개인화 OFF)로 넣는다. 이 검사의 주제는
		//    "켠 사람은 남는가" 이므로 켠다. 끈 사람 쪽은 아래에 따로 있다.
		enableBehavior(this.userId);
		this.placeId = insertPlace();
	}

	/**
	 * 🔴 이 검사 하나가 배포에서 못 가린 질문에 답한다 — 하트는 이벤트를 남기는가.
	 */
	@Test
	@DisplayName("🔴 하트를 누르면 place_like 가 event_outbox 에 실제로 남는다")
	void pressingHeartWritesPlaceLike() {
		this.savedPlaces.save(this.userId, this.placeId);

		List<Map<String, Object>> rows = this.jdbc.queryForList(
				"SELECT aggregate_type, aggregate_id, user_id, producer, payload::text AS payload "
						+ "FROM event_outbox WHERE event_type = ? AND user_id = ?",
				"place_like", this.userId);

		assertThat(rows).as("하트는 눌렸는데 이벤트가 없다 — 배관이 끊긴 것이다").hasSize(1);
		// 축이 USER 인 것이 중요하다. 여행 밖 화면에서도 눌리므로 TRIP 축이면 적을 수가 없다(-735).
		assertThat(rows.get(0).get("aggregate_type")).isEqualTo("user");
		assertThat(rows.get(0).get("aggregate_id")).hasToString(this.userId.toString());
		assertThat(rows.get(0).get("producer")).hasToString("SERVER");
		assertThat((String) rows.get(0).get("payload")).contains(this.placeId.toString());
	}

	/**
	 * 🔴 연타가 취향을 부풀리면 안 된다. 하트는 켜짐/꺼짐이라 "두 번 켠 상태" 가 없다.
	 *
	 * <p>단위 검사도 같은 것을 보지만 거기서는 {@code insertIfAbsent} 가 목이라 <b>0 을
	 * 돌려주라고 우리가 시킨 것</b>이다. 여기서는 진짜 {@code ON CONFLICT DO NOTHING} 이
	 * 판정한다 — 그 판정이 실제로 0 을 내는지는 DB 만 답할 수 있다.
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

	/**
	 * 🔴 개인화를 끈 사람은 안 남긴다 — S15P21E201-549 의 규칙이 이 경로에서도 지켜지는가.
	 *
	 * <p>그리고 <b>하트 자체는 저장돼야 한다.</b> "안 모은다" 가 "동작을 막는다" 가 되면
	 * 사용자는 저장이 안 되는 것으로 본다.
	 */
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
