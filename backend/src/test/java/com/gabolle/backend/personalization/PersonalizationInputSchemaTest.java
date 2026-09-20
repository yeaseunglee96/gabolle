package com.gabolle.backend.personalization;

import java.time.LocalDate;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.gabolle.backend.recommendation.support.PersonalizationFixture;
import com.gabolle.backend.recommendation.support.PostgresIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 개인화 입력을 애플리케이션을 우회해서 넣어도 DB 가 막는가. 애플리케이션 코드로만 지키는
 * 규칙은 배치나 손으로 쓴 SQL 이 옆으로 걸어 들어오므로 원시 SQL 로 넣어 본다.
 *
 * <p>이 표들의 엔티티가 아직 없어서 {@link JdbcTemplate} 로 직접 넣는다.
 */
class PersonalizationInputSchemaTest extends PostgresIntegrationTest {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private PersonalizationFixture.Ids references;

	@BeforeEach
	void insertReferences() {
		this.references = PersonalizationFixture.insert(this.jdbcTemplate);
	}

	// ── 취향: 값과 응답 상태 ──────────────────────────────────────────────────

	@Test
	@DisplayName("🔴 고른 답(SELECTED)에 값이 없으면 DB 가 거부한다")
	void databaseRefusesSelectedWithoutValue() {
		assertThatThrownBy(() -> insertPreferenceAnswer("CATEGORY", null, "SELECTED"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("🔴 건너뛴 답(SKIPPED)이 값을 싣고 있으면 DB 가 거부한다 — 이것이 0 으로 새어 들어오는 경로다")
	void databaseRefusesSkippedCarryingAValue() {
		assertThatThrownBy(() -> insertPreferenceAnswer("CATEGORY", "{\"codes\": []}", "SKIPPED"))
				.isInstanceOf(DataIntegrityViolationException.class);

		// 값이 없는 건너뜀은 정상이다. "안 좋아한다" 가 아니라 "안 골랐다" 로 남는다.
		assertThatCode(() -> insertPreferenceAnswer("ATMOSPHERE", null, "SKIPPED"))
				.doesNotThrowAnyException();
	}

	@Test
	@DisplayName("명세에 없는 취향 차원은 DB 가 거부한다")
	void databaseRefusesUnknownPreferenceDimension() {
		assertThatThrownBy(() -> insertPreferenceAnswer("PET_FRIENDLINESS", "{\"v\": 1}", "SELECTED"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("같은 스냅샷에 같은 차원이 두 번 들어오면 DB 가 거부한다")
	void databaseRefusesDuplicateDimension() {
		insertPreferenceAnswer("LOCALITY", "{\"v\": 1}", "SELECTED");
		assertThatThrownBy(() -> insertPreferenceAnswer("LOCALITY", "{\"v\": 2}", "SELECTED"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	// ── 취향: CATEGORY 낱말은 사전에 있어야 한다 ─────────────────────────────
	//
	// 막는 것은 "틀린 낱말" 이 아니라 조용한 0건이다. 사전 밖 낱말이 들어가면 장소 쪽 낱말과
	// 교집합이 언제나 비고, 그 결과는 오류가 아니라 "맞는 장소 없음" 으로 나타난다.

	@Test
	@DisplayName("🔴 CATEGORY 에 사전에 없는 낱말을 넣으면 DB 가 거부한다 — 맨 배열 모양")
	void databaseRefusesUnknownCategoryCodeInBareArray() {
		assertThatThrownBy(() -> insertPreferenceAnswer("CATEGORY", "[\"ACTIVE\"]", "SELECTED"))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("ACTIVE");
	}

	@Test
	@DisplayName("🔴 CATEGORY 에 사전에 없는 낱말을 넣으면 DB 가 거부한다 — {\"codes\":[…]} 모양")
	void databaseRefusesUnknownCategoryCodeInCodesObject() {
		assertThatThrownBy(() -> insertPreferenceAnswer("CATEGORY", "{\"codes\": [\"SEA\"]}", "SELECTED"))
				.isInstanceOf(DataIntegrityViolationException.class)
				.hasMessageContaining("SEA");
	}

	@Test
	@DisplayName("🔴 값 모양 둘을 다 받는다 — 한쪽만 받으면 멀쩡한 저장이 거부된다")
	void databaseAcceptsBothValueShapes() {
		// 배포된 앱이 보내는 모양.
		assertThatCode(() -> insertPreferenceAnswer("CATEGORY", "[\"SEA_BEACH\",\"FOOD\"]", "SELECTED"))
				.doesNotThrowAnyException();

		// 기존 시험과 다른 클라이언트가 쓰는 모양.
		assertThatCode(() -> insertPreferenceAnswer("ATMOSPHERE", "{\"codes\": [\"ANYTHING\"]}", "SELECTED"))
				.doesNotThrowAnyException();
	}

	@Test
	@DisplayName("🔴 CATEGORY 값이 낱말 목록이 아니면 DB 가 거부한다 — 안 막으면 아무것도 안 보고 지나간다")
	void databaseRefusesCategoryValueThatIsNotACodeList() {
		assertThatThrownBy(() -> insertPreferenceAnswer("CATEGORY", "{\"v\": 1}", "SELECTED"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("🔴 사전이 없는 차원은 막지 않는다 — 모르는 것을 아는 척하지 않는다")
	void databaseDoesNotGuardDimensionsWithoutADictionary() {
		// 분위기·음식종류의 낱말 목록은 아무도 정한 적이 없다. 여기서 지어내면 실제로 쓰이던
		// 값이 배포 때 거부된다.
		assertThatCode(() -> insertPreferenceAnswer("FOOD_PREFERENCE", "[\"WHATEVER\"]", "SELECTED"))
				.doesNotThrowAnyException();

		// 점수형은 사전 문제가 아니다.
		assertThatCode(() -> insertPreferenceAnswer("QUIETNESS", "3", "SELECTED"))
				.doesNotThrowAnyException();
	}

	@Test
	@DisplayName("🔴 사전에 낱말을 더하는 데 스키마 변경이 필요 없다 — 행 하나로 끝난다")
	void addingAWordToTheDictionaryNeedsNoSchemaChange() {
		assertThatThrownBy(() -> insertPreferenceAnswer("CATEGORY", "[\"ZZT_NEW_TASTE\"]", "SELECTED"))
				.isInstanceOf(DataIntegrityViolationException.class);

		this.jdbcTemplate.update("""
				INSERT INTO place_feature_code (feature_type, feature_key, label_ko, note)
				VALUES ('CATEGORY_TAG', 'ZZT_NEW_TASTE', '시험용', '이 검사가 넣는다')
				""");

		assertThatCode(() -> insertPreferenceAnswer("CATEGORY", "[\"ZZT_NEW_TASTE\"]", "SELECTED"))
				.doesNotThrowAnyException();
	}

	@Test
	@DisplayName("🔴 사용자 쪽 낱말과 장소 쪽 낱말이 같은 사전을 본다 — 교집합이 비면 실패한다")
	void userAndPlaceSidesShareOneDictionary() {
		// 장소 쪽과 사용자 쪽이 같은 표를 본다. 그 표가 비거나 둘이 다른 표를 보게 되는
		// 순간 빨개진다 — 그때가 "조용한 0건" 이 돌아오는 때다.
		Integer shared = this.jdbcTemplate.queryForObject("""
				SELECT count(*) FROM place_feature_code WHERE feature_type = 'CATEGORY_TAG'
				""", Integer.class);
		assertThat(shared).isPositive();

		String placeSideType = this.jdbcTemplate.queryForObject("""
				SELECT place_feature_type FROM user_place_code_map
				 WHERE user_input_kind = 'PREFERENCE' AND user_input_code = 'CATEGORY'
				""", String.class);
		assertThat(placeSideType).isEqualTo("CATEGORY_TAG");
	}

	// ── 제약: 알레르기는 하드다 ───────────────────────────────────────────────

	@Test
	@DisplayName("🔴 알레르기를 hard=false 로 저장하려 하면 DB 가 거부한다 — 안전 제약이 점수로 상쇄될 수 없다")
	void databaseRefusesSoftAllergy() {
		assertThatThrownBy(() -> insertConstraintAnswer("ALLERGY", "PEANUT",
				"{\"severity\": \"HARD\"}", false, "SELECTED"))
				.isInstanceOf(DataIntegrityViolationException.class);

		assertThatCode(() -> insertConstraintAnswer("ALLERGY", "PEANUT",
				"{\"severity\": \"HARD\"}", true, "SELECTED"))
				.doesNotThrowAnyException();
	}

	@Test
	@DisplayName("🔴 제약에 SKIPPED 는 없다 — 없으면 NONE 을 명시해야 한다")
	void databaseRefusesSkippedConstraint() {
		assertThatThrownBy(() -> insertConstraintAnswer("DIET", "HALAL", null, false, "SKIPPED"))
				.isInstanceOf(DataIntegrityViolationException.class);

		// "해당 없음" 은 값 없이 NONE 으로 남는다. 안 물어본 것(UNKNOWN)과 구분된다.
		assertThatCode(() -> insertConstraintAnswer("DIET", "HALAL", null, false, "NONE"))
				.doesNotThrowAnyException();
		assertThatCode(() -> insertConstraintAnswer("DIET", "VEGAN", null, false, "UNKNOWN"))
				.doesNotThrowAnyException();
	}

	@Test
	@DisplayName("🔴 자유 입력 알레르기 암호문만 있고 키 판 번호가 없으면 DB 가 거부한다 — 그건 저장이 아니라 유실이다")
	void databaseRefusesCiphertextWithoutKeyVersion() {
		assertThatThrownBy(() -> this.jdbcTemplate.update("""
				INSERT INTO constraint_answer (
				    constraint_answer_id, constraint_snapshot_id, constraint_type, constraint_key,
				    value, hard, answer_status, other_allergy_ciphertext, created_at)
				VALUES (?, ?, 'ALLERGY', 'OTHER', '{"free": true}'::jsonb, TRUE, 'SELECTED', ?, now())
				""", UUID.randomUUID(), this.references.constraintSnapshotId(), "cipher".getBytes()))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("보행 상한이 숫자가 아니면 DB 가 거부한다")
	void databaseRefusesNonNumericWalkingLimit() {
		assertThatThrownBy(() -> insertConstraintAnswer("MOBILITY", "MAX_WALKING_METERS",
				"{\"meters\": \"많이\"}", true, "SELECTED"))
				.isInstanceOf(DataIntegrityViolationException.class);

		assertThatCode(() -> insertConstraintAnswer("MOBILITY", "MAX_WALKING_METERS",
				"{\"meters\": 1500}", true, "SELECTED"))
				.doesNotThrowAnyException();
	}

	@Test
	@DisplayName("이동 제약이 아닌 이름은 DB 가 거부한다 — 경사·그늘은 취향 쪽이다")
	void databaseRefusesUnknownMobilityKey() {
		assertThatThrownBy(() -> insertConstraintAnswer("MOBILITY", "SHADE_PREFERENCE",
				"{\"v\": 1}", false, "SELECTED"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	// ── 제약 값은 종류마다 필요 여부가 다르다 ────────────────────────────────

	@Test
	@DisplayName("🔴 알레르기·식단은 값 없이 저장된다 — 코드가 constraint_key 에 있고 value 는 담을 것이 없다")
	void allergyAndDietAreStoredWithoutValue() {
		assertThatCode(() -> insertConstraintAnswer("ALLERGY", "PEANUT", null, true, "SELECTED"))
				.doesNotThrowAnyException();
		assertThatCode(() -> insertConstraintAnswer("DIET", "HALAL", null, false, "SELECTED"))
				.doesNotThrowAnyException();
	}

	@Test
	@DisplayName("🔴 이동 제약은 여전히 값을 요구한다 — 보행 상한에 숫자가 없으면 제약이 아니다")
	void mobilityStillRequiresValue() {
		assertThatThrownBy(() -> insertConstraintAnswer("MOBILITY", "MAX_WALKING_METERS", null, true, "SELECTED"))
				.isInstanceOf(DataIntegrityViolationException.class);

		assertThatCode(() -> insertConstraintAnswer("MOBILITY", "MAX_WALKING_METERS",
				"{\"meters\": 1500}", true, "SELECTED")).doesNotThrowAnyException();
	}

	@Test
	@DisplayName("🔴 안 고른 답은 여전히 값을 실을 수 없다 — 이쪽 규칙은 완화하지 않았다")
	void unselectedStillCannotCarryValue() {
		assertThatThrownBy(() -> insertConstraintAnswer("DIET", "VEGAN", "{\"x\": 1}", false, "NONE"))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> insertConstraintAnswer("DIET", "KOSHER", "{\"x\": 1}", false, "UNKNOWN"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	// ── 여행 시간대 프리셋 ───────────────────────────────────────────────────

	@Test
	@DisplayName("시간대 프리셋 원본이 저장된다 — 시각 두 칸이 비어 있어도 사용자가 고른 것은 남는다")
	void timeWindowPresetIsPreservedWithoutDerivedTimes() {
		UUID tripId = UUID.randomUUID();
		this.jdbcTemplate.update("""
				INSERT INTO trip (
				    trip_id, owner_user_id, start_date, end_date, time_window_preset, created_at, updated_at)
				VALUES (?, ?, ?, ?, 'MORNING_TO_EVENING', now(), now())
				""", tripId, this.references.userId(), LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 11));

		String preset = this.jdbcTemplate.queryForObject(
				"SELECT time_window_preset FROM trip WHERE trip_id = ?", String.class, tripId);
		assertThat(preset).isEqualTo("MORNING_TO_EVENING");

		// 파생값은 아직 비어 있다. 프리셋 목록이 확정되면 그때 채운다 — 비어 있는 것이 정상이다.
		Integer derived = this.jdbcTemplate.queryForObject("""
				SELECT count(*) FROM trip
				WHERE trip_id = ? AND time_window_start IS NULL AND time_window_end IS NULL
				""", Integer.class, tripId);
		assertThat(derived).isEqualTo(1);
	}

	// ── 스냅샷: 판 번호와 적용 범위 ───────────────────────────────────────────

	@Test
	@DisplayName("🔴 같은 여행에 같은 판 번호가 두 번 들어오면 DB 가 거부한다")
	void databaseRefusesDuplicateTripVersion() {
		assertThatThrownBy(() -> this.jdbcTemplate.update("""
				INSERT INTO preference_snapshot (
				    preference_snapshot_id, user_id, trip_id, version, scope, created_at)
				VALUES (?, ?, ?, 1, 'TRIP', now())
				""", UUID.randomUUID(), this.references.userId(), this.references.tripId()))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("🔴 계정 기본값(scope=USER)도 판 번호가 겹치면 거부된다 — 부분 색인이 실제로 도는지 본다")
	void databaseRefusesDuplicateUserScopedVersion() {
		UUID userId = PersonalizationFixture.insertUser(this.jdbcTemplate);
		insertUserScopedSnapshot(userId, 1);

		// NULL 을 서로 다르게 보는 PostgreSQL 특성 때문에, 조건 없는 UNIQUE 제약이었다면
		// 이 두 번째 행이 조용히 들어간다. 그것을 막는 것이 부분 색인이다.
		assertThatThrownBy(() -> insertUserScopedSnapshot(userId, 1))
				.isInstanceOf(DataIntegrityViolationException.class);

		assertThatCode(() -> insertUserScopedSnapshot(userId, 2)).doesNotThrowAnyException();
	}

	@Test
	@DisplayName("scope 와 trip_id 가 어긋나면 DB 가 거부한다")
	void databaseRefusesScopeMismatch() {
		// TRIP 인데 여행이 없다.
		assertThatThrownBy(() -> this.jdbcTemplate.update("""
				INSERT INTO preference_snapshot (
				    preference_snapshot_id, user_id, trip_id, version, scope, created_at)
				VALUES (?, ?, NULL, 9, 'TRIP', now())
				""", UUID.randomUUID(), this.references.userId()))
				.isInstanceOf(DataIntegrityViolationException.class);

		// USER 인데 여행이 붙어 있다.
		assertThatThrownBy(() -> this.jdbcTemplate.update("""
				INSERT INTO preference_snapshot (
				    preference_snapshot_id, user_id, trip_id, version, scope, created_at)
				VALUES (?, ?, ?, 9, 'USER', now())
				""", UUID.randomUUID(), this.references.userId(), this.references.tripId()))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	// ── 여행 조건 ─────────────────────────────────────────────────────────────

	@Test
	@DisplayName("끝나는 날이 시작하는 날보다 빠르면 DB 가 거부한다")
	void databaseRefusesReversedDates() {
		assertThatThrownBy(() -> insertTrip(LocalDate.of(2026, 9, 11), LocalDate.of(2026, 9, 10),
				"ARRAY['WALK']::VARCHAR(30)[]"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("🔴 명세에 없는 이동 수단이 배열에 섞여 있으면 DB 가 거부한다")
	void databaseRefusesUnknownTravelMode() {
		assertThatThrownBy(() -> insertTrip(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 11),
				"ARRAY['WALK', 'HELICOPTER']::VARCHAR(30)[]"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("여행 상태는 정해진 넷뿐이다 — 🔴 DELETED 는 없다. 삭제는 deleted_at 이 말한다")
	void databaseRefusesUnknownTripStatus() {
		assertThatThrownBy(() -> this.jdbcTemplate.update("""
				INSERT INTO trip (
				    trip_id, owner_user_id, start_date, end_date, status, created_at, updated_at)
				VALUES (?, ?, ?, ?, 'DELETED', now(), now())
				""", UUID.randomUUID(), this.references.userId(),
				LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 11)))
				.isInstanceOf(DataIntegrityViolationException.class);

		assertThatCode(() -> this.jdbcTemplate.update("""
				INSERT INTO trip (
				    trip_id, owner_user_id, start_date, end_date, status, created_at, updated_at)
				VALUES (?, ?, ?, ?, 'IN_PROGRESS', now(), now())
				""", UUID.randomUUID(), this.references.userId(),
				LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 11)))
				.doesNotThrowAnyException();
	}

	@Test
	@DisplayName("좌표가 한쪽만 있으면 DB 가 거부한다 — 반쪽 좌표는 오류가 아니라 틀린 답을 만든다")
	void databaseRefusesHalfCoordinate() {
		assertThatThrownBy(() -> this.jdbcTemplate.update("""
				INSERT INTO trip (
				    trip_id, owner_user_id, start_date, end_date, origin_lat, created_at, updated_at)
				VALUES (?, ?, ?, ?, 35.1587, now(), now())
				""", UUID.randomUUID(), this.references.userId(),
				LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 11)))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	// ── 외래키 ───────────────────────────────────────────────────────────────

	@Test
	@DisplayName("🔴 없는 스냅샷을 가리키는 추천 Job 은 DB 가 거부한다 — 543 이 이름만 맞춰 두고 남긴 자리다")
	void databaseRefusesJobPointingAtMissingSnapshot() {
		assertThatThrownBy(() -> this.jdbcTemplate.update("""
				INSERT INTO recommendation_job (
				    job_id, request_id, user_id, job_type, job_status,
				    preference_snapshot_id, constraint_snapshot_id,
				    model_version, feature_version, ontology_version, policy_version, dataset_version,
				    service_version, deployment_environment, created_at)
				VALUES (?, ?, ?, 'ITINERARY_GENERATION', 'SUCCEEDED', ?, ?,
				        'm', 'f', 'o', 'p', 'd', 's', 'test', now())
				""", UUID.randomUUID(), UUID.randomUUID(), this.references.userId(),
				UUID.randomUUID(), UUID.randomUUID()))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("후보 → 노출을 잇는 축이 실제로 조인된다 — 명세 14장 마지막 항목")
	void candidateJoinsBackToItsInputSnapshot() {
		UUID requestId = UUID.randomUUID();
		// SUCCEEDED 인 ITINERARY_GENERATION Job 은 itinerary_id·itinerary_version 이 있어야
		// 한다. 여기서 쓸 최소 일정 하나를 함께 만든다.
		UUID itineraryId = insertItinerary(this.references.tripId(), this.references.userId());

		this.jdbcTemplate.update("""
				INSERT INTO recommendation_job (
				    job_id, request_id, user_id, trip_id, trip_version, job_type, job_status,
				    preference_snapshot_id, constraint_snapshot_id, itinerary_id, itinerary_version,
				    model_version, feature_version, ontology_version, policy_version, dataset_version,
				    service_version, deployment_environment, created_at)
				VALUES (?, ?, ?, ?, ?, 'ITINERARY_GENERATION', 'SUCCEEDED', ?, ?, ?, 1,
				        'm', 'f', 'o', 'p', 'd', 's', 'test', now())
				""", UUID.randomUUID(), requestId, this.references.userId(), this.references.tripId(),
				this.references.tripVersion(), this.references.preferenceSnapshotId(),
				this.references.constraintSnapshotId(), itineraryId);

		UUID placeId = UUID.randomUUID();
		this.jdbcTemplate.update("""
				INSERT INTO recommendation_candidate (
				    candidate_id, request_id, place_id, candidate_source, candidate_stage,
				    eligible, constraint_verdict, final_score, final_rank, returned, created_at)
				VALUES (?, ?, ?, 'ONTOLOGY_SEED', 'RETURNED', TRUE, 'PASS', 0.9, 1, TRUE, now())
				""", UUID.randomUUID(), requestId, placeId);

		// 이 검사가 보는 것은 낱말이 아니라 조인이 끝까지 닿는가다. 다만 CATEGORY 는 사전을
		// 강제하므로 사전에 있는 낱말을 써야 한다.
		insertPreferenceAnswer("CATEGORY", "{\"codes\": [\"SEA_BEACH\", \"NATURE_WALK\"]}", "SELECTED");
		insertPreferenceAnswer("QUIETNESS", null, "SKIPPED");

		// 건너뛴 답도 함께 나와야 한다 — 분석에서 "안 물어봤다" 를 세려면 그 행이 있어야 한다.
		Integer joinedAnswers = this.jdbcTemplate.queryForObject("""
				SELECT count(*)
				FROM recommendation_candidate c
				JOIN recommendation_job j ON j.request_id = c.request_id
				JOIN preference_snapshot ps ON ps.preference_snapshot_id = j.preference_snapshot_id
				JOIN preference_answer pa ON pa.preference_snapshot_id = ps.preference_snapshot_id
				WHERE c.request_id = ? AND c.place_id = ?
				""", Integer.class, requestId, placeId);

		assertThat(joinedAnswers).isEqualTo(2);
	}

	// ── 넣는 도구들 ───────────────────────────────────────────────────────────

	/** {@code recommendation_job.itinerary_id} 의 FK 대상. */
	private UUID insertItinerary(UUID tripId, UUID createdBy) {
		UUID itineraryId = UUID.randomUUID();
		this.jdbcTemplate.update(
				"INSERT INTO itineraries (itinerary_id, trip_id, latest_version, created_at) VALUES (?, ?, 1, now())",
				itineraryId, tripId);
		this.jdbcTemplate.update("""
				INSERT INTO itinerary_versions
				    (itinerary_version_id, itinerary_id, version, operation, created_by, request_id, created_at)
				VALUES (?, ?, 1, 'CREATE', ?, ?, now())
				""", UUID.randomUUID(), itineraryId, createdBy, "req_" + UUID.randomUUID());
		return itineraryId;
	}

	private void insertPreferenceAnswer(String dimension, String valueJson, String answerStatus) {
		this.jdbcTemplate.update("""
				INSERT INTO preference_answer (
				    preference_answer_id, preference_snapshot_id, dimension, value, answer_status, created_at)
				VALUES (?, ?, ?, CAST(? AS jsonb), ?, now())
				""", UUID.randomUUID(), this.references.preferenceSnapshotId(), dimension, valueJson,
				answerStatus);
	}

	private void insertConstraintAnswer(String type, String key, String valueJson, boolean hard,
			String answerStatus) {
		this.jdbcTemplate.update("""
				INSERT INTO constraint_answer (
				    constraint_answer_id, constraint_snapshot_id, constraint_type, constraint_key,
				    value, hard, answer_status, created_at)
				VALUES (?, ?, ?, ?, CAST(? AS jsonb), ?, ?, now())
				""", UUID.randomUUID(), this.references.constraintSnapshotId(), type, key, valueJson, hard,
				answerStatus);
	}

	private void insertUserScopedSnapshot(UUID userId, int version) {
		this.jdbcTemplate.update("""
				INSERT INTO preference_snapshot (
				    preference_snapshot_id, user_id, trip_id, version, scope, created_at)
				VALUES (?, ?, NULL, ?, 'USER', now())
				""", UUID.randomUUID(), userId, version);
	}

	private void insertTrip(LocalDate startDate, LocalDate endDate, String travelModesSql) {
		this.jdbcTemplate.update("""
				INSERT INTO trip (
				    trip_id, owner_user_id, start_date, end_date, travel_modes, created_at, updated_at)
				VALUES (?, ?, ?, ?, %s, now(), now())
				""".formatted(travelModesSql), UUID.randomUUID(), this.references.userId(), startDate,
				endDate);
	}
}
