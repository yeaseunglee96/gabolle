package com.gabolle.backend.trip;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import com.gabolle.backend.recommendation.support.PostgresAvailableCondition;
import com.gabolle.backend.recommendation.support.TestDatabase;
import com.gabolle.backend.trip.application.TravelConstraintService;
import com.gabolle.backend.trip.domain.TravelConstraintAnswer;
import com.gabolle.backend.trip.domain.TravelConstraintStatus;
import com.gabolle.testslice.TripSliceApplication;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * S15P21E201-1231 — 여행 조건 모달의 답이 <b>상태 넷</b>으로 오가는가.
 *
 * <h2>재는 것 — 완료 기준 그대로</h2>
 *
 * <ul>
 *   <li>한 번도 저장 안 했으면 <b>비어 있다</b> — 부르는 쪽이 그것을 {@code status:null} 200 으로 낸다</li>
 *   <li><b>{@code SAVED}</b> 는 값이 따라온다</li>
 *   <li><b>{@code LATER}</b> · <b>{@code NEVER}</b> 는 값 없이 저장되고, 둘이 <b>서로 구분된다</b></li>
 *   <li>🔴 {@code SAVED} 였다가 {@code LATER} 로 바꾸면 <b>값이 지워진다</b></li>
 *   <li>🔴 값 없이 {@code SAVED} 는 <b>거부된다</b></li>
 *   <li>🔴 {@code LATER} 인데 값을 보내면 <b>거부가 아니라 값만 버린다</b></li>
 *   <li>사람당 <b>한 줄</b>이다 — 두 번 저장해도 줄이 안 는다</li>
 *   <li>🔴 DB 가 스스로 막는다 — 응용을 건너뛰고 넣어도 제약에 걸린다</li>
 * </ul>
 *
 * <h2>DB 가 없으면 건너뛴다</h2>
 *
 * 🔴 {@code PostgresAvailableCondition} 이 붙으므로 도커가 꺼진 PC 에서는 건너뛴 채 초록이다.
 * <b>진짜 판정은 CI 다.</b>
 */
@SpringBootTest(classes = TripSliceApplication.class, properties = {
		"spring.profiles.active=db",
		"spring.jpa.hibernate.ddl-auto=none",
		"spring.flyway.enabled=true"
})
@ExtendWith(PostgresAvailableCondition.class)
class TravelConstraintIntegrationTest {

	private static final String VALUE = "{\"allergies\":[\"peanut\"],\"maxWalkMeters\":800,\"avoidSlope\":true}";

	@DynamicPropertySource
	static void datasourceProperties(DynamicPropertyRegistry registry) {
		TestDatabase.registerDatasource(registry);
	}

	@Autowired
	private TravelConstraintService service;

	@Autowired
	private JdbcTemplate jdbc;

	private UUID userId;

	@BeforeEach
	void setUp() {
		this.userId = insertUser();
	}

	@AfterEach
	void tearDown() {
		// 🔴 표를 비우지 않는다. 내가 만든 것만 지운다 — 같은 DB 를 여러 검사가 함께 쓴다.
		this.jdbc.update("DELETE FROM user_travel_constraint WHERE user_id = ?", this.userId);
		this.jdbc.update("DELETE FROM event_outbox WHERE aggregate_id = ?", this.userId);
		this.jdbc.update("DELETE FROM app_user WHERE user_id = ?", this.userId);
	}

	@Test
	@DisplayName("한 번도 저장 안 했으면 비어 있다 — 오류가 아니라 「안 물어봄」이다")
	void neverAskedIsEmptyNotAnError() {
		assertThat(this.service.find(this.userId)).as("저장한 적 없는데 무언가가 나왔다").isEmpty();
	}

	@Test
	@DisplayName("「저장하고 시작」은 값이 따라온다")
	void savedCarriesTheValue() {
		this.service.put(this.userId, VALUE, "SAVED", UUID.randomUUID());

		TravelConstraintAnswer found = this.service.find(this.userId).orElseThrow();
		assertThat(found.status()).isEqualTo(TravelConstraintStatus.SAVED);
		assertThat(found.valueJson()).as("저장했다는데 값이 안 따라왔다").contains("peanut");
	}

	@Test
	@DisplayName("🔴 「나중에」와 「다시 묻지 않기」가 서로 구분된다 — 합치면 안 되는 이유가 이것이다")
	void laterAndNeverAreDifferentAnswers() {
		this.service.put(this.userId, null, "LATER", UUID.randomUUID());
		assertThat(this.service.find(this.userId).orElseThrow().status())
				.isEqualTo(TravelConstraintStatus.LATER);

		this.service.put(this.userId, null, "NEVER", UUID.randomUUID());
		assertThat(this.service.find(this.userId).orElseThrow().status())
				.as("「다시 묻지 않기」가 「나중에」로 읽혔다 — 모달이 영영 안 뜨거나 매번 뜬다")
				.isEqualTo(TravelConstraintStatus.NEVER);
	}

	@Test
	@DisplayName("🔴 저장했다가 「나중에」로 바꾸면 값이 지워진다 — 안 지우면 출처 없는 값이 남는다")
	void switchingAwayFromSavedClearsTheValue() {
		this.service.put(this.userId, VALUE, "SAVED", UUID.randomUUID());
		this.service.put(this.userId, null, "LATER", UUID.randomUUID());

		TravelConstraintAnswer found = this.service.find(this.userId).orElseThrow();
		assertThat(found.status()).isEqualTo(TravelConstraintStatus.LATER);
		assertThat(found.valueJson()).as("「나중에」인데 값이 남아 있다").isNull();
	}

	@Test
	@DisplayName("🔴 「나중에」인데 값을 보내면 거부가 아니라 값만 버린다 — 정상 흐름이다")
	void valueSentWithLaterIsDroppedNotRejected() {
		this.service.put(this.userId, VALUE, "LATER", UUID.randomUUID());

		TravelConstraintAnswer found = this.service.find(this.userId).orElseThrow();
		assertThat(found.status()).isEqualTo(TravelConstraintStatus.LATER);
		assertThat(found.valueJson())
				.as("모달 내용을 들고 「나중에」를 누른 것은 정상인데 값이 남았다").isNull();
	}

	@Test
	@DisplayName("🔴 값 없이 「저장하고 시작」은 거부된다")
	void savedWithoutValueIsRejected() {
		assertThatThrownBy(() -> this.service.put(this.userId, null, "SAVED", UUID.randomUUID()))
				.as("값 없는 저장이 통과했다 — 화면이 빈 모달을 「저장됨」으로 그린다")
				.isInstanceOf(IllegalArgumentException.class);

		assertThat(this.service.find(this.userId)).as("거부됐는데 줄이 남았다").isEmpty();
	}

	@Test
	@DisplayName("모르는 상태는 거부된다 — 넷 말고는 없다")
	void unknownStatusIsRejected() {
		assertThatThrownBy(() -> this.service.put(this.userId, null, "SKIPPED", UUID.randomUUID()))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("사람당 한 줄이다 — 세 번 저장해도 줄이 안 는다")
	void oneRowPerPerson() {
		this.service.put(this.userId, VALUE, "SAVED", UUID.randomUUID());
		this.service.put(this.userId, null, "LATER", UUID.randomUUID());
		this.service.put(this.userId, VALUE, "SAVED", UUID.randomUUID());

		assertThat(rows()).as("같은 사람의 답이 여러 줄이 됐다 — 어느 것이 최신인지 판단할 일이 생긴다")
				.isEqualTo(1L);
	}

	@Test
	@DisplayName("처음 답한 시각은 안 덮인다 — 바꿔도 created_at 은 그대로다")
	void createdAtSurvivesAnUpdate() {
		this.service.put(this.userId, VALUE, "SAVED", UUID.randomUUID());
		OffsetDateTime first = createdAt();

		this.service.put(this.userId, null, "NEVER", UUID.randomUUID());

		assertThat(createdAt()).as("바꿨더니 처음 답한 시각이 덮였다").isEqualTo(first);
	}

	@Test
	@DisplayName("🔴 응용을 건너뛰어도 DB 가 막는다 — 값과 상태가 어긋난 줄은 안 들어간다")
	void theDatabaseItselfRefusesAMismatchedRow() {
		assertThatThrownBy(() -> this.jdbc.update(
				"INSERT INTO user_travel_constraint (user_id, status, value, created_at, updated_at) "
						+ "VALUES (?, 'LATER', ?::jsonb, now(), now())", this.userId, VALUE))
				.as("「나중에」인데 값이 있는 줄이 들어갔다")
				.isInstanceOf(DataIntegrityViolationException.class);

		assertThatThrownBy(() -> this.jdbc.update(
				"INSERT INTO user_travel_constraint (user_id, status, value, created_at, updated_at) "
						+ "VALUES (?, 'SAVED', NULL, now(), now())", this.userId))
				.as("「저장함」인데 값이 없는 줄이 들어갔다")
				.isInstanceOf(DataIntegrityViolationException.class);

		assertThatThrownBy(() -> this.jdbc.update(
				"INSERT INTO user_travel_constraint (user_id, status, value, created_at, updated_at) "
						+ "VALUES (?, 'SKIPPED', NULL, now(), now())", this.userId))
				.as("넷에 없는 상태가 들어갔다")
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	private long rows() {
		Long n = this.jdbc.queryForObject("SELECT count(*) FROM user_travel_constraint WHERE user_id = ?",
				Long.class, this.userId);
		return n == null ? 0 : n;
	}

	private OffsetDateTime createdAt() {
		return this.jdbc.queryForObject("SELECT created_at FROM user_travel_constraint WHERE user_id = ?",
				OffsetDateTime.class, this.userId);
	}

	private UUID insertUser() {
		UUID id = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.now(ZoneOffset.UTC);
		this.jdbc.update("INSERT INTO app_user "
				+ "(user_id, display_name, language, personalization_mode, status, created_at, updated_at) "
				+ "VALUES (?, ?, 'ko', 'EXPLICIT_ONLY', 'ACTIVE', ?, ?)", id, "조건 답하는 사람", now, now);
		return id;
	}
}
