package com.gabolle.backend.personalization;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.gabolle.backend.recommendation.support.PostgresIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 장소 코드와 사용자 입력 코드가 실제로 맞는가 — S15P21E201-545.
 *
 * <p>티켓 완료 기준 그대로다 — <b>"장소 코드와 사용자 입력 코드가 자동 검사에서 일치한다."</b>
 * 그 검사가 이 클래스다.
 *
 * <h2>왜 문서가 아니라 검사인가</h2>
 * 명세 4.3 이 <b>"사용자 입력 코드와 장소 피처 코드가 달라지면 안 된다"</b> 고 한다. 그런데
 * 문서에만 적어 두면 코드를 하나 더할 때 아무것도 막지 않는다. 취향 차원을 하나 추가하고
 * 장소 쪽 짝을 안 만들면, 그 취향은 <b>영원히 아무 장소와도 매칭되지 않으면서 화면에는
 * 나타난다.</b> 오류도 아니고 빈 결과도 아니라서 아무도 못 본다.
 *
 * <p>그래서 <b>빠짐을 실패로 만든다.</b> 검사가 빨개지는 것이 유일하게 사람이 알아차리는 길이다.
 */
class PlaceFeatureCodeMapTest extends PostgresIntegrationTest {

	/**
	 * 사용자가 직접 고르는 화면이 없는 피처. 짝이 없는 것이 정상이다.
	 *
	 * <p>🔴 이 목록이 <b>"빠뜨렸다" 와 "짝이 없다" 를 가른다.</b> 이것 없이 검사를 만들면
	 * 둘을 구별할 수 없어서, 검사를 통과시키려고 아무 대조나 넣게 된다.
	 * 명세 4.3 의 M1 확정 취향 차원 여덟에 인기·혼잡이 없다 — 랭킹 가중치로만 쓰인다.
	 */
	private static final List<String> UNPAIRED_BY_DESIGN = List.of("POPULARITY_SCORE", "CROWDING_SCORE");

	/**
	 * 추정값을 저장할 수 없는 피처 — S15P21E201-666.
	 *
	 * <p>🔴 이 목록은 마이그레이션 {@code V20260907003000} 의 CHECK 와 <b>같아야 한다.</b>
	 * 갈리면 대조표에는 하드 필터로 적혀 있는데 추정값이 들어올 수 있는 종류가 생기고,
	 * 그건 아무 오류도 내지 않는다. 여기 적어 두고 아래 검사가 양쪽을 대조한다.
	 */
	private static final List<String> SAFETY_FEATURE_TYPES = List.of("ALLERGEN_TAG", "DIETARY_SUPPORT_TAG",
			"ACCESSIBILITY_TAG", "STAIRS_PRESENT");

	@Autowired
	private JdbcTemplate jdbcTemplate;

	// ── 완료 기준: 코드가 일치한다 ────────────────────────────────────────────

	@Test
	@DisplayName("🔴 취향 차원 여덟이 전부 장소 피처와 짝이 있다 — 하나라도 빠지면 그 취향은 영영 매칭되지 않는다")
	void everyPreferenceDimensionHasAPlaceFeature() {
		// 🔴 S15P21E201-709 — SPEND_PROFILE(아홉째 차원)은 일부러 뺐다. 씀씀이(가격대)를
		// 나타내는 place_feature_type 이 온톨로지에 아직 없어서, 짝지을 종류를 정하는 것은
		// place 담당의 결정이다. 그 결정 전에 여기 넣으면 이 검사가 늘 실패하고, 실패가
		// "빠뜨렸다"인지 "아직 결정 전이다"인지 이 검사만 봐서는 구분이 안 된다 — 그래서
		// 결정이 나올 때까지는 이 검사 대상에서 뺀다(TasteDimension.SPEND_PROFILE javadoc 참고).
		List<String> unmapped = this.jdbcTemplate.queryForList("""
				SELECT d.dimension FROM (VALUES
				    ('CATEGORY'), ('ATMOSPHERE'), ('LOCALITY'), ('QUIETNESS'),
				    ('TOURIST_PREFERENCE'), ('FOOD_PREFERENCE'),
				    ('SLOPE_PREFERENCE'), ('SHADE_PREFERENCE')
				) AS d(dimension)
				WHERE NOT EXISTS (
				    SELECT 1 FROM user_place_code_map m
				    WHERE m.user_input_kind = 'PREFERENCE' AND m.user_input_code = d.dimension)
				""", String.class);

		assertThat(unmapped)
				.as("장소 피처와 짝이 없는 취향 차원 — 화면에는 나오는데 아무 장소와도 안 맞는다")
				.isEmpty();
	}

	@Test
	@DisplayName("🔴 제약 셋이 전부 장소 피처와 짝이 있다 — 짝이 없으면 하드 제약을 계산할 수 없다")
	void everyConstraintTypeHasAPlaceFeature() {
		List<String> unmapped = this.jdbcTemplate.queryForList("""
				SELECT c.type FROM (VALUES ('ALLERGY'), ('DIET'), ('MOBILITY')) AS c(type)
				WHERE NOT EXISTS (
				    SELECT 1 FROM user_place_code_map m
				    WHERE m.user_input_kind = 'CONSTRAINT' AND m.user_input_code = c.type)
				""", String.class);

		assertThat(unmapped)
				.as("장소 피처와 짝이 없는 제약 — 위반을 판정할 근거가 장소 쪽에 없다")
				.isEmpty();
	}

	@Test
	@DisplayName("대조표가 가리키는 장소 피처는 전부 실제로 저장할 수 있는 종류다")
	void mappedPlaceFeatureTypesAreStorable() {
		List<String> types = this.jdbcTemplate
				.queryForList("SELECT DISTINCT place_feature_type FROM user_place_code_map", String.class);

		UUID placeId = insertPlace();
		for (String type : types) {
			// 태그형이면 키가 필요하고, 그 밖은 키가 없어야 한다. CHECK 가 그것을 가른다.
			boolean tagLike = type.endsWith("_TAG");
			// 🔴 안전 피처는 ESTIMATED 로 저장할 수 없다(S15P21E201-666). 그래서 여기서
			//    "저장할 수 있는가" 를 물을 때도 그 종류만 VERIFIED 로 넣는다 — 안 그러면
			//    이 검사가 막으려는 것에 스스로 걸린다.
			String status = SAFETY_FEATURE_TYPES.contains(type) ? "VERIFIED" : "ESTIMATED";
			assertThatCode(() -> insertFeature(placeId, type, tagLike ? "PROBE" : null, status))
					.as("대조표에 있는데 저장할 수 없는 피처 종류: " + type)
					.doesNotThrowAnyException();
		}
	}

	@Test
	@DisplayName("🔴 짝 없는 피처는 인기·혼잡 둘뿐이다 — 새로 늘어나면 실패한다")
	void onlyKnownFeaturesAreUnpaired() {
		List<String> unpaired = this.jdbcTemplate.queryForList("""
				SELECT f.type FROM (VALUES
				    ('INTEREST_TAG'), ('ATMOSPHERE_TAG'), ('CUISINE_TAG'),
				    ('ALLERGEN_TAG'), ('DIETARY_SUPPORT_TAG'), ('ACCESSIBILITY_TAG'),
				    ('LOCALITY_SCORE'), ('QUIETNESS_SCORE'), ('TOURIST_RATIO'),
				    ('POPULARITY_SCORE'), ('CROWDING_SCORE'), ('SHADE_SCORE'),
				    ('SLOPE_PERCENT'), ('STAIRS_PRESENT')
				) AS f(type)
				WHERE NOT EXISTS (
				    SELECT 1 FROM user_place_code_map m WHERE m.place_feature_type = f.type)
				""", String.class);

		assertThat(unpaired)
				.as("사용자 입력과 짝이 없는 피처. 늘어났다면 대조표에 넣을지 UNPAIRED_BY_DESIGN 에 넣을지 정해야 한다")
				.containsExactlyInAnyOrderElementsOf(UNPAIRED_BY_DESIGN);
	}

	@Test
	@DisplayName("🔴 알레르기·식단·이동은 하드 필터다 — 점수로 상쇄되면 안 된다 (FR-REC-02)")
	void safetyConstraintsAreHardFilters() {
		List<String> notHard = this.jdbcTemplate.queryForList("""
				SELECT user_input_code || '/' || place_feature_type FROM user_place_code_map
				WHERE user_input_kind = 'CONSTRAINT'
				  AND place_feature_type IN ('ALLERGEN_TAG', 'DIETARY_SUPPORT_TAG', 'ACCESSIBILITY_TAG')
				  AND match_kind <> 'HARD_FILTER'
				""", String.class);

		assertThat(notHard)
				.as("안전 제약이 하드 필터가 아니다 — 점수로 상쇄될 수 있는 상태")
				.isEmpty();
	}

	// ── 코드 목록이 실제로 막히는가 ───────────────────────────────────────────

	@Test
	@DisplayName("명세에 없는 피처 종류는 DB 가 거부한다")
	void databaseRefusesUnknownFeatureType() {
		UUID placeId = insertPlace();
		assertThatThrownBy(() -> insertFeature(placeId, "VIBE_LEVEL", null))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("🔴 태그형에 키가 없거나, 점수형에 키가 있으면 DB 가 거부한다 — 섞이면 유일성이 무의미해진다")
	void databaseRefusesMismatchedKeyShape() {
		UUID placeId = insertPlace();

		assertThatThrownBy(() -> insertFeature(placeId, "INTEREST_TAG", null))
				.isInstanceOf(DataIntegrityViolationException.class);
		assertThatThrownBy(() -> insertFeature(placeId, "SHADE_SCORE", "SOMETHING"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("🔴 같은 장소의 같은 피처를 두 번 넣을 수 없다 — 이력은 여기가 아니라 후보 로그에 쌓인다")
	void databaseRefusesDuplicateFeature() {
		UUID placeId = insertPlace();

		insertFeature(placeId, "SHADE_SCORE", null);
		// 키 없는 피처 — 부분 색인이 NULL 을 서로 다르게 보지 않는지 확인한다
		assertThatThrownBy(() -> insertFeature(placeId, "SHADE_SCORE", null))
				.isInstanceOf(DataIntegrityViolationException.class);

		insertFeature(placeId, "INTEREST_TAG", "SEA");
		assertThatThrownBy(() -> insertFeature(placeId, "INTEREST_TAG", "SEA"))
				.isInstanceOf(DataIntegrityViolationException.class);
		// 다른 태그는 들어간다
		assertThatCode(() -> insertFeature(placeId, "INTEREST_TAG", "ALLEY")).doesNotThrowAnyException();
	}

	@Test
	@DisplayName("대조표에 명세 밖 사용자 코드를 넣으면 DB 가 거부한다")
	void databaseRefusesUnknownUserInputCode() {
		assertThatThrownBy(() -> this.jdbcTemplate.update("""
				INSERT INTO user_place_code_map
				    (user_input_kind, user_input_code, place_feature_type, match_kind)
				VALUES ('PREFERENCE', 'PET_FRIENDLINESS', 'INTEREST_TAG', 'TAG_OVERLAP')
				"""))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	// ── 출처·데이터 판 (완료 기준 1) ──────────────────────────────────────────

	@Test
	@DisplayName("장소와 피처 양쪽에서 출처와 데이터 판을 찾을 수 있다")
	void provenanceIsFindableOnBothPlaceAndFeature() {
		UUID placeId = UUID.randomUUID();
		this.jdbcTemplate.update("""
				INSERT INTO place (
				    place_id, name_ko, category, created_at,
				    source_type, source_id, collected_at, observed_at, dataset_version)
				VALUES (?, '송도 해상 케이블카', 'ATTRACTION', now(),
				        'TOURAPI', 'ta-12345', now(), now(), 'places-2026-09-04')
				""", placeId);
		insertFeature(placeId, "SHADE_SCORE", null);

		String datasetVersion = this.jdbcTemplate.queryForObject(
				"SELECT dataset_version FROM place WHERE place_id = ?", String.class, placeId);
		assertThat(datasetVersion).isEqualTo("places-2026-09-04");

		Integer withProvenance = this.jdbcTemplate.queryForObject("""
				SELECT count(*) FROM place_feature
				WHERE place_id = ? AND source_type IS NOT NULL AND source_version IS NOT NULL
				""", Integer.class, placeId);
		assertThat(withProvenance).isEqualTo(1);
	}

	@Test
	@DisplayName("🔴 확인 안 된 피처는 값을 가질 수 없다 — 결측이 안전값으로 바뀌지 않는다 (완료 기준 2)")
	void unknownFeatureCannotCarryAValue() {
		UUID placeId = insertPlace();

		// 모진성 님이 -262 에 넣은 CHECK 다. 완료 기준 2 가 이것에 걸려 있으니 여기서도 지킨다.
		assertThatThrownBy(() -> this.jdbcTemplate.update("""
				INSERT INTO place_feature (
				    place_feature_id, place_id, feature_type, value, evidence_status,
				    source_type, source_version, created_at)
				VALUES (?, ?, 'SHADE_SCORE', '{"score": 0}'::jsonb, 'UNKNOWN', 'GUESS', 'v1', now())
				""", UUID.randomUUID(), placeId))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	// ── 안전 피처는 추측할 수 없다 (S15P21E201-666) ───────────────────────────

	@Test
	@DisplayName("🔴 알레르기·식단·접근성·계단을 ESTIMATED 로 저장하면 DB 가 거부한다 — 추측이 안전 판정이 되는 경로")
	void safetyFeatureCannotBeEstimated() {
		UUID placeId = insertPlace();

		for (String type : SAFETY_FEATURE_TYPES) {
			String key = type.endsWith("_TAG") ? "PROBE" : null;
			assertThatThrownBy(() -> insertFeature(placeId, type, key, "ESTIMATED"))
					.as("추정값이 저장돼 버리는 안전 피처: " + type
							+ " — 하드 필터가 이 행을 근거로 후보를 통과시킨다")
					.isInstanceOf(DataIntegrityViolationException.class);
		}
	}

	@Test
	@DisplayName("🔴 같은 네 종이 VERIFIED·UNKNOWN 은 여전히 받는다 — UNKNOWN 은 지울 상태가 아니라 남길 사실이다")
	void safetyFeatureStillAcceptsVerifiedAndUnknown() {
		for (String type : SAFETY_FEATURE_TYPES) {
			String key = type.endsWith("_TAG") ? "PROBE" : null;

			UUID verifiedPlace = insertPlace();
			assertThatCode(() -> insertFeature(verifiedPlace, type, key, "VERIFIED"))
					.as("확인된 안전 정보를 저장할 수 없다 — 제약을 과하게 조였다: " + type)
					.doesNotThrowAnyException();

			// UNKNOWN 은 값을 실을 수 없다(ck_place_feature_unknown_has_no_value).
			// 그 규칙과 새 제약이 서로 부딪히지 않는지 함께 본다.
			UUID unknownPlace = insertPlace();
			assertThatCode(() -> insertFeatureWithoutValue(unknownPlace, type, key, "UNKNOWN"))
					.as("모른다는 사실을 저장할 수 없다 — 그건 명세가 요구하는 것의 반대다: " + type)
					.doesNotThrowAnyException();
		}
	}

	@Test
	@DisplayName("🔴 추정 금지 목록과 대조표의 하드 판정 목록이 갈리지 않는다 — 갈리면 아무 오류도 안 난다")
	void safetyListMatchesHardJudgedCodeMapEntries() {
		List<String> hardJudged = this.jdbcTemplate.queryForList("""
				SELECT DISTINCT place_feature_type FROM user_place_code_map
				WHERE match_kind IN ('HARD_FILTER', 'FLAG_COMPARE')
				""", String.class);

		assertThat(hardJudged)
				.as("대조표가 하드로 판정하는 피처와 추정 금지 목록이 다르다 — "
						+ "한쪽에만 있는 종류는 하드로 쓰이면서 추측값을 받거나, 반대로 막혀서 못 채운다")
				.containsExactlyInAnyOrderElementsOf(SAFETY_FEATURE_TYPES);
	}

	// ── 넣는 도구들 ───────────────────────────────────────────────────────────

	private UUID insertPlace() {
		UUID placeId = UUID.randomUUID();
		this.jdbcTemplate.update("""
				INSERT INTO place (place_id, name_ko, category, created_at, dataset_version)
				VALUES (?, '테스트 장소', 'ATTRACTION', now(), 'places-test')
				""", placeId);
		return placeId;
	}

	private void insertFeature(UUID placeId, String featureType, String featureKey) {
		insertFeature(placeId, featureType, featureKey, "ESTIMATED");
	}

	/**
	 * 🔴 {@code evidenceStatus} 를 받는 판. S15P21E201-666 이 안전 피처의 {@code ESTIMATED}
	 * 를 막으면서 필요해졌다 — 그 전에는 이 도구가 항상 {@code ESTIMATED} 를 넣었고,
	 * 그래서 안전 피처까지 추정값으로 저장하고 있었다.
	 */
	private void insertFeature(UUID placeId, String featureType, String featureKey, String evidenceStatus) {
		this.jdbcTemplate.update("""
				INSERT INTO place_feature (
				    place_feature_id, place_id, feature_type, feature_key, value,
				    evidence_status, source_type, source_id, observed_at, source_version, created_at)
				VALUES (?, ?, ?, ?, '{"probe": true}'::jsonb,
				        ?, 'TOURAPI', 'ta-1', now(), 'v1', now())
				""", UUID.randomUUID(), placeId, featureType, featureKey, evidenceStatus);
	}

	/** {@code UNKNOWN} 은 값을 실을 수 없다 — {@code ck_place_feature_unknown_has_no_value}. */
	private void insertFeatureWithoutValue(UUID placeId, String featureType, String featureKey,
			String evidenceStatus) {
		this.jdbcTemplate.update("""
				INSERT INTO place_feature (
				    place_feature_id, place_id, feature_type, feature_key, value,
				    evidence_status, source_type, source_id, observed_at, source_version, created_at)
				VALUES (?, ?, ?, ?, NULL,
				        ?, 'TOURAPI', 'ta-1', now(), 'v1', now())
				""", UUID.randomUUID(), placeId, featureType, featureKey, evidenceStatus);
	}
}
