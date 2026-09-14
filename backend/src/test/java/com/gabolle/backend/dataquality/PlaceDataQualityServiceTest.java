package com.gabolle.backend.dataquality;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import com.gabolle.backend.recommendation.support.PostgresIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 세 조회가 정상 fixture 는 통과시키고 오염 fixture 는 잡는가 — S15P21E201-278 · -315 · -328.
 *
 * <p>완료 기준 그대로다 — 일부러 문제 있는 값을 하나 넣고 돌리면 그 행이 결과에 잡히는지,
 * 정상값만 있으면 결과가 0건인지를 본다. {@link EventQualityGateTest} 와 같은 이유로 이
 * 테스트가 <b>기준선을 다시 재는 방법의 예시</b>이기도 하다 — {@code backend/docs
 * /PLACE-DATA-QUALITY.md} 가 이 클래스를 가리킨다.
 */
@Import(PlaceDataQualityService.class)
class PlaceDataQualityServiceTest extends PostgresIntegrationTest {

	@Autowired
	private PlaceDataQualityService service;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private Clock clock;

	@BeforeEach
	void clean() {
		this.jdbcTemplate.update("DELETE FROM place_feature");
		this.jdbcTemplate.update("DELETE FROM place");
	}

	private UUID insertPlace(String nameEn) {
		UUID placeId = UUID.randomUUID();
		this.jdbcTemplate.update(
				"INSERT INTO place (place_id, name_ko, name_en, created_at) VALUES (?, ?, ?, ?)",
				placeId, "해운대해수욕장", nameEn, java.time.OffsetDateTime.now(this.clock));
		return placeId;
	}

	/**
	 * 🔴 ALLERGEN_TAG · CATEGORY_TAG 는 태그형이라 {@code ck_place_feature_key_shape} 가
	 * {@code feature_key NOT NULL} 을 요구한다(V20260913210000) — 이 테스트가 쓰는 두 종류가
	 * 전부 태그형이라 항상 채운다.
	 *
	 * <p>🔴 키는 {@code "FOOD"} 다 — 임의 문자열이 아니다. {@code CATEGORY_TAG} 는
	 * {@code fk_place_feature_code} 가 {@code place_feature_code} 사전에 있는 키만 강제한다
	 * (V20260913210000). {@code "TEST_KEY"} 처럼 사전에 없는 값을 넣으면 그 갈래에서는 외래키
	 * 위반으로 INSERT 자체가 실패한다 — {@code ALLERGEN_TAG} 는 그 사전 강제 대상이 아니라서
	 * 임의 문자열도 통과하지만, 같은 키를 두 갈래에 함께 쓰려면 둘 다 통과하는 값이어야 한다.
	 * {@code FOOD} 는 온보딩 여섯 갈래에 실제로 있는 값이다.
	 */
	private void insertFeature(UUID placeId, String featureType, String evidenceStatus, String sourceType,
			boolean hasValue) {
		this.jdbcTemplate.update("""
				INSERT INTO place_feature (place_feature_id, place_id, feature_type, feature_key, value,
				    evidence_status, source_type, created_at)
				VALUES (?, ?, ?, ?, ?::jsonb, ?, ?, ?)
				""", UUID.randomUUID(), placeId, featureType, "FOOD", hasValue ? "{\"present\": true}" : null,
				evidenceStatus, sourceType, java.time.OffsetDateTime.now(this.clock));
	}

	// ── S15P21E201-278 ───────────────────────────────────────────────────────

	@Test
	@DisplayName("칼럼별 채움 개수/전체 개수를 정확히 센다")
	void completenessCountsFilledAndTotal() {
		UUID withNameEn = insertPlace("Haeundae Beach");
		insertPlace(null);
		insertFeature(withNameEn, "ALLERGEN_TAG", "VERIFIED", "MANUAL", true);

		PlaceCompletenessReport report = this.service.measureCompleteness();

		assertThat(report.placeTotal()).isEqualTo(2);
		assertThat(report.nameEnFilled()).isEqualTo(1);
		assertThat(report.featureTypeFilled().get("ALLERGEN_TAG").filled()).isEqualTo(1);
		assertThat(report.featureTypeFilled().get("ALLERGEN_TAG").total()).isEqualTo(2);
		// 아무 행도 없는 종류는 0/전체다 — 지어낸 칼럼이 아니라 실제 0건이다.
		assertThat(report.featureTypeFilled().get("CATEGORY_TAG").filled()).isZero();
	}

	@Test
	@DisplayName("🔴 UNKNOWN 행은 채워진 것으로 세지 않는다 — 모른다고 확인한 것과 실제로 채운 것은 다르다")
	void unknownEvidenceIsNotCountedAsFilled() {
		UUID placeId = insertPlace("Place");
		insertFeature(placeId, "ALLERGEN_TAG", "UNKNOWN", null, false);

		PlaceCompletenessReport report = this.service.measureCompleteness();

		assertThat(report.featureTypeFilled().get("ALLERGEN_TAG").filled()).isZero();
	}

	// ── S15P21E201-315 ───────────────────────────────────────────────────────

	@Test
	@DisplayName("정상 fixture — 영문 이름에 한글이 없고 비어 있지 않으면 통과한다")
	void cleanNameEnPasses() {
		insertPlace("Haeundae Beach");

		assertThat(this.service.checkTranslationQuality()).isEmpty();
	}

	@Test
	@DisplayName("🔴 영문 이름에 한글이 섞여 있으면 그 장소가 결과에 잡힌다")
	void hangulInNameEnIsCaught() {
		UUID placeId = insertPlace("해운대 Beach");

		List<PlaceDataQualityService.TranslationIssue> issues = this.service.checkTranslationQuality();

		assertThat(issues).hasSize(1);
		assertThat(issues.get(0).placeId()).isEqualTo(placeId.toString());
		assertThat(issues.get(0).kind()).isEqualTo(PlaceDataQualityService.TranslationIssue.Kind.HANGUL_REMAINS);
	}

	@Test
	@DisplayName("🔴 영문 이름이 비어 있으면(NULL) 그 장소가 결과에 잡힌다")
	void emptyNameEnIsCaught() {
		insertPlace(null);

		List<PlaceDataQualityService.TranslationIssue> issues = this.service.checkTranslationQuality();

		assertThat(issues).hasSize(1);
		assertThat(issues.get(0).kind()).isEqualTo(PlaceDataQualityService.TranslationIssue.Kind.EMPTY);
	}

	// ── S15P21E201-328 ───────────────────────────────────────────────────────

	@Test
	@DisplayName("정상 fixture — 값이 있는 안전 정보에 출처가 있으면 통과한다")
	void safetyValueWithSourcePasses() {
		UUID placeId = insertPlace("Place");
		insertFeature(placeId, "ALLERGEN_TAG", "VERIFIED", "MANUAL", true);

		assertThat(this.service.checkSafetyProvenance()).isEmpty();
	}

	@Test
	@DisplayName("🔴 값은 있는데 출처가 없는 안전 정보가 결과에 잡힌다")
	void safetyValueWithoutSourceIsCaught() {
		UUID placeId = insertPlace("Place");
		insertFeature(placeId, "ALLERGEN_TAG", "VERIFIED", null, true);

		List<PlaceDataQualityService.SafetyProvenanceIssue> issues = this.service.checkSafetyProvenance();

		assertThat(issues).hasSize(1);
		assertThat(issues.get(0).placeId()).isEqualTo(placeId.toString());
		assertThat(issues.get(0).featureType()).isEqualTo("ALLERGEN_TAG");
	}

	@Test
	@DisplayName("안전 정보가 아닌 종류는(예: CATEGORY_TAG) 출처가 없어도 잡히지 않는다")
	void nonSafetyFeatureIsIgnoredByProvenanceCheck() {
		UUID placeId = insertPlace("Place");
		insertFeature(placeId, "CATEGORY_TAG", "VERIFIED", null, true);

		assertThat(this.service.checkSafetyProvenance()).isEmpty();
	}
}
