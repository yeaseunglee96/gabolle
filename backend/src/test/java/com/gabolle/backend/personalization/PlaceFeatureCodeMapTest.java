package com.gabolle.backend.personalization;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import com.gabolle.backend.place.domain.InterestTagCode;
import com.gabolle.backend.recommendation.support.PostgresIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 장소 코드와 사용자 입력 코드가 실제로 맞는가. 취향 차원을 추가하고 장소 쪽 짝을 안 만들면
 * 그 취향은 화면에 나타나면서 영원히 아무 장소와도 매칭되지 않는데, 오류도 빈 결과도 아니라서
 * 아무도 못 본다. 그래서 빠짐을 실패로 만든다.
 */
class PlaceFeatureCodeMapTest extends PostgresIntegrationTest {

	/**
	 * 사용자가 직접 고르는 화면이 없는 피처. 짝이 없는 것이 정상이다. 이 목록이 「빠뜨렸다」와
	 * 「짝이 없다」를 가른다 — 없으면 검사를 통과시키려고 아무 대조나 넣게 된다.
	 *
	 * <p>인기·혼잡은 랭킹 가중치로만 쓴다. INTEREST_TAG(탐색 아코디언)는 취향 차원이 아니라
	 * 그 자리에서 훑어보는 목록이라 여기 있다 — 짝을 두면 온보딩 낱말과 탐색 낱말이 한
	 * 서랍에 섞인다.
	 */
	private static final List<String> UNPAIRED_BY_DESIGN = List.of("POPULARITY_SCORE", "CROWDING_SCORE",
			"INTEREST_TAG");

	/**
	 * 추정값을 저장할 수 없는 피처. 이 목록은 마이그레이션 {@code V20260907003000} 의
	 * CHECK 와 같아야 한다 — 갈리면 대조표에는 하드 필터인데 추정값이 들어올 수 있는 종류가
	 * 생기고, 그건 아무 오류도 내지 않는다. 아래 검사가 양쪽을 대조한다.
	 */
	private static final List<String> SAFETY_FEATURE_TYPES = List.of("ALLERGEN_TAG", "DIETARY_SUPPORT_TAG",
			"ACCESSIBILITY_TAG", "STAIRS_PRESENT");

	@Autowired
	private JdbcTemplate jdbcTemplate;

	// ── 취향 차원과 장소 피처의 코드가 일치한다 ────────────────────────────

	@Test
	@DisplayName("🔴 취향 차원 여덟이 전부 장소 피처와 짝이 있다 — 하나라도 빠지면 그 취향은 영영 매칭되지 않는다")
	void everyPreferenceDimensionHasAPlaceFeature() {
		// SPEND_PROFILE(아홉째 차원)은 일부러 뺐다. 씀씀이를 나타내는 place_feature_type 이
		// 온톨로지에 아직 없어, 넣어 두면 「빠뜨렸다」와 「아직 결정 전이다」가 구분되지 않는다.
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
			// 안전 피처는 ESTIMATED 로 저장할 수 없으므로 그 종류만 VERIFIED 로 넣는다 —
			// 안 그러면 이 검사가 막으려는 것에 스스로 걸린다.
			String status = SAFETY_FEATURE_TYPES.contains(type) ? "VERIFIED" : "ESTIMATED";
			assertThatCode(() -> insertFeature(placeId, type, tagLike ? probeKey(type) : null, status))
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

		insertFeature(placeId, "INTEREST_TAG", "WALK");
		assertThatThrownBy(() -> insertFeature(placeId, "INTEREST_TAG", "WALK"))
				.isInstanceOf(DataIntegrityViolationException.class);
		// 다른 태그는 들어간다
		assertThatCode(() -> insertFeature(placeId, "INTEREST_TAG", "NATURE")).doesNotThrowAnyException();
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

	// ── 출처·데이터 판 ───────────────────────────────────────────────────

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

		// 확인 안 된 피처에 값을 실을 수 없다는 CHECK 가 DB 에 걸려 있다.
		assertThatThrownBy(() -> this.jdbcTemplate.update("""
				INSERT INTO place_feature (
				    place_feature_id, place_id, feature_type, value, evidence_status,
				    source_type, source_version, created_at)
				VALUES (?, ?, 'SHADE_SCORE', '{"score": 0}'::jsonb, 'UNKNOWN', 'GUESS', 'v1', now())
				""", UUID.randomUUID(), placeId))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	// ── 안전 피처는 추측할 수 없다 ────────────────────────────────────────

	@Test
	@DisplayName("🔴 알레르기·식단·접근성·계단을 ESTIMATED 로 저장하면 DB 가 거부한다 — 추측이 안전 판정이 되는 경로")
	void safetyFeatureCannotBeEstimated() {
		UUID placeId = insertPlace();

		for (String type : SAFETY_FEATURE_TYPES) {
			String key = type.endsWith("_TAG") ? probeKey(type) : null;
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
			String key = type.endsWith("_TAG") ? probeKey(type) : null;

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

	// ── 검사를 낱말 수준까지 내린다 ───────────────────────────────────────
	//
	// 위 검사들은 「차원 ↔ 표식 종류」 짝까지만 본다. 짝이 있어 초록인데 그 서랍 안에서
	// 온보딩 낱말과 탐색 낱말이 섞일 수 있어, 아래가 그 층을 막는다.

	@Test
	@DisplayName("🔴 온보딩 사전과 탐색 사전은 낱말이 하나도 겹치지 않는다")
	void categoryAndInterestDictionariesAreDisjoint() {
		List<String> shared = this.jdbcTemplate.queryForList("""
				SELECT c.feature_key FROM place_feature_code c
				WHERE c.feature_type = 'CATEGORY_TAG'
				  AND EXISTS (SELECT 1 FROM place_feature_code i
				               WHERE i.feature_type = 'INTEREST_TAG'
				                 AND i.feature_key = c.feature_key)
				""", String.class);

		assertThat(shared)
				.as("두 사전에 같은 낱말이 있으면 그 낱말은 쓴 사람에 따라 두 뜻이 된다 — "
						+ "데이터만 봐서는 구별할 수 없고 되돌릴 수도 없다")
				.isEmpty();
	}

	@Test
	@DisplayName("🔴 탐색 여덟 갈래가 자바 목록과 DB 사전에 똑같이 있다")
	void interestTagDictionaryMatchesTheEnum() {
		List<String> inDb = this.jdbcTemplate.queryForList(
				"SELECT feature_key FROM place_feature_code WHERE feature_type = 'INTEREST_TAG'",
				String.class);

		assertThat(inDb)
				.as("자바 enum 과 DB 사전은 자동으로 이어지지 않는다 — 같은 목록을 두 곳에 "
						+ "따로 적는 것이고, 어긋나면 알려주는 것이 이 검사뿐이다")
				.containsExactlyInAnyOrderElementsOf(
						InterestTagCode.displayOrder().stream().map(Enum::name).toList());
	}

	@Test
	@DisplayName("🔴 사전에 없는 낱말은 DB 가 거부한다 — 둘러보기 낱말을 온보딩 서랍에 넣으려 하면 막힌다")
	void wordsOutsideTheDictionaryAreRejected() {
		UUID placeId = insertPlace();

		// 지금 강제되는 것은 CATEGORY_TAG 하나다. INTEREST_TAG 는 이미 쓰인 검사 여럿이 아무
		// 낱말이나 넣고 있어 아직 강제하지 않고, 그 갈래는 아래 두 검사가 잡는다.
		assertThatThrownBy(() -> insertFeature(placeId, "CATEGORY_TAG", "FESTIVAL", "VERIFIED"))
				.as("FESTIVAL 은 둘러보기 사전의 낱말이다. 온보딩 서랍에 들어가면 "
						+ "두 사전이 다시 섞인다")
				.isInstanceOf(DataIntegrityViolationException.class);

		assertThatThrownBy(() -> insertFeature(placeId, "CATEGORY_TAG", "NOT_A_REAL_CODE", "VERIFIED"))
				.as("사전에 없는 낱말은 오타든 새 낱말이든 일단 막는다 — 새 낱말이면 "
						+ "place_feature_code 에 행을 더한다(스키마는 안 고친다)")
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	@DisplayName("이미 쌓인 표식의 낱말이 전부 사전에 있다 — 강제 대상 갈래만 본다")
	void everyStoredWordIsInTheDictionary() {
		List<String> orphans = this.jdbcTemplate.queryForList("""
				SELECT DISTINCT f.feature_type || ':' || f.feature_key
				  FROM place_feature f
				 WHERE f.dictionary_key IS NOT NULL
				   AND NOT EXISTS (SELECT 1 FROM place_feature_code c
				                    WHERE c.feature_type = f.feature_type
				                      AND c.feature_key = f.feature_key)
				""", String.class);

		assertThat(orphans).as("외래키가 막고 있어야 한다. 비어 있지 않다면 제약이 빠진 것이다").isEmpty();
	}

	/**
	 * 시험이 넣어 볼 낱말. 사전이 있는 갈래는 사전에 있는 낱말이어야 한다 — 아무 낱말이나
	 * 쓰면 외래키가 막는다. 사전이 아직 없는 갈래는 강제 대상이 아니다.
	 *
	 * <p>시험들이 DB 를 나눠 쓰므로, 다른 시험이 건수를 단언하는 낱말
	 * (FESTIVAL · SOUVENIR_SHOP — PlaceFacetInterestTagIntegrationTest)은 쓰지 않는다.
	 */
	private static String probeKey(String featureType) {
		return switch (featureType) {
			case "CATEGORY_TAG" -> "FOOD";
			case "INTEREST_TAG" -> "WALK";
			default -> "PROBE";
		};
	}

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
	 * {@code evidenceStatus} 를 받는 판. 안전 피처의 {@code ESTIMATED} 를 막으면서 필요해졌다 —
	 * 그 전에는 이 도구가 항상 {@code ESTIMATED} 를 넣었다.
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
