package com.gabolle.backend.dataquality;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.OffsetDateTime;

/**
 * 장소 데이터 품질 조회 셋. 전부 같은 표를 같은 방식(search_path 정렬 + JdbcTemplate 직접 SQL)
 * 으로 재는 조회라 한 클래스에 뒀다 — 쪼개면 그 정렬 로직이 여러 번 복사된다.
 *
 * <p>{@code EventQualityGate} 와 달리 이 클래스는 아무것도 저장하지 않는다. 기준선 수치는
 * 실행한 사람이 문서에 옮겨 적는다. 돌리는 법은 스프링 컨텍스트에서 이 빈을 주입받아 메서드를
 * 부르는 것이고, {@code PlaceDataQualityServiceTest} 가 그 재현 경로다.
 */
@Component
@Profile({ "db", "dev" })
public class PlaceDataQualityService {

	/**
	 * 지금 스키마에 실제로 있는 종류만 담는다. 마이그레이션의 {@code ck_place_feature_type} 을
	 * 그대로 옮긴 것이라, 새 종류가 생기면 여기도 늘려야 기준선에 잡힌다 — 안 늘려도 검사가
	 * 실패하지 않고 그 종류가 조용히 안 세어질 뿐이다.
	 */
	private static final List<String> KNOWN_FEATURE_TYPES = List.of(
			"INTEREST_TAG", "ATMOSPHERE_TAG", "CUISINE_TAG",
			"ALLERGEN_TAG", "DIETARY_SUPPORT_TAG", "ACCESSIBILITY_TAG",
			"LOCALITY_SCORE", "QUIETNESS_SCORE", "TOURIST_RATIO",
			"POPULARITY_SCORE", "CROWDING_SCORE", "SHADE_SCORE", "SLOPE_PERCENT",
			"STAIRS_PRESENT",
			"OPENING_HOURS", "PRICE_LEVEL",
			"SOLO_FRIENDLY", "BREAK_TIME", "LAST_ORDER_TIME",
			"CHECK_IN_OUT",
			"CATEGORY_TAG");

	/**
	 * 안전 정보로 다루는 종류. 마이그레이션이 이 넷에 ESTIMATED 를 막아 뒀지만 그 제약은
	 * 출처 없이 VERIFIED 로 적어 넣는 것까지는 막지 않는다 — 그 구멍을 여기서 잡는다.
	 */
	private static final List<String> SAFETY_FEATURE_TYPES = List.of(
			"ALLERGEN_TAG", "DIETARY_SUPPORT_TAG", "ACCESSIBILITY_TAG", "STAIRS_PRESENT");

	/** 한글 완성형·자모 범위. {@code name_en} 같은 "영문" 칸에 섞여 들어온 한글을 찾는다. */
	private static final Pattern HANGUL = Pattern.compile("[\\uAC00-\\uD7A3\\u3131-\\u318E]");

	private static final Pattern SAFE_SCHEMA = Pattern.compile("^[A-Za-z_][A-Za-z0-9_]*$");

	private final JdbcTemplate jdbcTemplate;

	private final Clock clock;

	/** {@link EventQualityGate#defaultSchema} 와 같은 이유로 같은 값을 읽는다. */
	private final String defaultSchema;

	public PlaceDataQualityService(JdbcTemplate jdbcTemplate, Clock clock,
			@Value("${spring.jpa.properties.hibernate.default_schema:}") String defaultSchema) {
		this.jdbcTemplate = jdbcTemplate;
		this.clock = clock;
		this.defaultSchema = (defaultSchema == null) ? "" : defaultSchema.trim();
	}

	private void alignSearchPath() {
		if (this.defaultSchema.isEmpty()) {
			return;
		}
		if (!SAFE_SCHEMA.matcher(this.defaultSchema).matches()) {
			throw new IllegalStateException("schema 이름으로 쓸 수 없는 값이다 (영문자·숫자·밑줄만): " + this.defaultSchema);
		}
		this.jdbcTemplate.execute("SET LOCAL search_path TO \"" + this.defaultSchema + "\", public");
	}

	// ── 칼럼별 채움률 기준선 ─────────────────────────────────────────────────────

	@Transactional(readOnly = true)
	public PlaceCompletenessReport measureCompleteness() {
		alignSearchPath();
		long placeTotal = count("SELECT count(*) FROM place");
		long nameEnFilled = count("SELECT count(*) FROM place WHERE name_en IS NOT NULL AND btrim(name_en) <> ''");

		Map<String, PlaceCompletenessReport.FillCount> byType = new LinkedHashMap<>();
		for (String type : KNOWN_FEATURE_TYPES) {
			// UNKNOWN 은 value 가 비어 있어야 한다는 제약이 이미 있어서, UNKNOWN 행은
			// "채워졌다" 가 아니라 "모른다고 확인했다" 다.
			long filled = this.jdbcTemplate.queryForObject("""
					SELECT count(DISTINCT place_id) FROM place_feature
					WHERE feature_type = ? AND evidence_status <> 'UNKNOWN'
					""", Long.class, type);
			byType.put(type, new PlaceCompletenessReport.FillCount(filled, placeTotal));
		}

		return new PlaceCompletenessReport(OffsetDateTime.now(this.clock), placeTotal, nameEnFilled, byType);
	}

	// ── 영문 칸 번역 품질 ───────────────────────────────────────────────────────

	/**
	 * @return 한글이 섞였거나 비어 있는 장소의 목록. 비어 있으면 통과다.
	 *
	 * <p>다국어 번역 표가 아직 없어서 {@code place.name_en} 하나만 본다.
	 */
	@Transactional(readOnly = true)
	public List<TranslationIssue> checkTranslationQuality() {
		alignSearchPath();
		List<TranslationIssue> issues = new ArrayList<>();
		List<Map<String, Object>> rows = this.jdbcTemplate.queryForList(
				"SELECT place_id, name_en FROM place");
		for (Map<String, Object> row : rows) {
			String nameEn = (String) row.get("name_en");
			String placeId = String.valueOf(row.get("place_id"));
			if (nameEn == null || nameEn.isBlank()) {
				issues.add(new TranslationIssue(placeId, "name_en", TranslationIssue.Kind.EMPTY, nameEn));
			}
			else if (HANGUL.matcher(nameEn).find()) {
				issues.add(new TranslationIssue(placeId, "name_en", TranslationIssue.Kind.HANGUL_REMAINS, nameEn));
			}
		}
		return issues;
	}

	public record TranslationIssue(String placeId, String column, Kind kind, String value) {
		public enum Kind { EMPTY, HANGUL_REMAINS }
	}

	// ── 안전 정보 출처 누락 ─────────────────────────────────────────────────────

	/**
	 * @return 값은 있는데 출처({@code source_type})가 비어 있는 안전 정보 행의 목록.
	 *     비어 있으면 통과다.
	 *
	 * <p>0건이 아니면 그 행의 출처를 채우거나, 확인할 수 없으면 행을 지운다.
	 * {@code evidence_status='UNKNOWN'} 으로 낮추지 않는다 — UNKNOWN 은 "몰라서 안 채웠다" 인데
	 * 이 행은 "채웠지만 어디서 왔는지 안 남겼다" 라 뜻이 다르다.
	 */
	@Transactional(readOnly = true)
	public List<SafetyProvenanceIssue> checkSafetyProvenance() {
		alignSearchPath();
		String placeholders = String.join(",", SAFETY_FEATURE_TYPES.stream().map((t) -> "?").toList());
		List<Map<String, Object>> rows = this.jdbcTemplate.queryForList("""
				SELECT place_feature_id, place_id, feature_type FROM place_feature
				WHERE feature_type IN (%s)
				  AND value IS NOT NULL
				  AND (source_type IS NULL OR btrim(source_type) = '')
				""".formatted(placeholders), SAFETY_FEATURE_TYPES.toArray());

		List<SafetyProvenanceIssue> issues = new ArrayList<>();
		for (Map<String, Object> row : rows) {
			issues.add(new SafetyProvenanceIssue(String.valueOf(row.get("place_feature_id")),
					String.valueOf(row.get("place_id")), String.valueOf(row.get("feature_type"))));
		}
		return issues;
	}

	public record SafetyProvenanceIssue(String placeFeatureId, String placeId, String featureType) {
	}

	private long count(String sql) {
		Long value = this.jdbcTemplate.queryForObject(sql, Long.class);
		return (value == null) ? 0L : value;
	}
}
