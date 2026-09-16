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
 * 장소 데이터 품질 조회 셋 — S15P21E201-278 · -315 · -328.
 *
 * <p>세 티켓을 한 클래스에 묶었다. {@link com.gabolle.backend.dataquality.EventQualityGate}
 * 가 이미 같은 이유로 여러 규칙(고아 노출·순위 불일치·버전 누락·PII)을 한 클래스에 두고
 * 있다 — 전부 <b>같은 표(place · place_feature)를 같은 방식(search_path 정렬 + JdbcTemplate
 * 직접 SQL)으로 재는 조회</b>라서 셋으로 쪼개면 그 정렬 로직이 세 번 복사된다.
 *
 * <p>🔴 <b>이 클래스는 아무것도 저장하지 않는다.</b> {@code EventQualityGate} 와 달리 이력을
 * 표에 쌓지 않는다 — 완료 기준이 요구하는 것은 "조회가 저장돼 다시 돌릴 수 있다" 지 "결과가
 * 매일 쌓인다" 가 아니다. 저장되는 것은 이 클래스(코드) 자체이고, 기준선 수치는 이 클래스를
 * 실행한 사람이 문서에 옮겨 적는다.
 *
 * <p><b>돌리는 법</b> — 스프링 컨텍스트에서 이 빈을 주입받아 메서드를 부른다. 이 저장소에서는
 * {@code PlaceDataQualityServiceTest}(실제 PostgreSQL 위에서 도는 통합 테스트, Docker 없으면
 * 건너뜀)가 세 메서드 전부를 실행하는 예시이자 재현 경로다 — 기준선을 다시 잴 때 그 테스트를
 * 참고하거나, 같은 방식으로 별도 러너를 만들어 이 빈을 부르면 된다.
 */
@Component
@Profile({ "db", "dev" })
public class PlaceDataQualityService {

	/**
	 * S15P21E201-278 완료 기준의 "최소한 이 칼럼들" 중 지금 스키마에 실제로 있는 것만 남겼다.
	 * 나머지(영문 주소·카카오 평점·야경 사진·체험 시간·기념품)는 place·place_feature 어디에도
	 * 자리가 없다 — {@link PlaceCompletenessReport} 클래스 주석에 근거를 적어 두었다.
	 *
	 * <p>🔴 이 목록은 {@code V20260913210000__place_feature_category_tag.sql} 의
	 * {@code ck_place_feature_type} 을 그대로 옮긴 것이다. 새 종류가 생기면(새 마이그레이션이
	 * CHECK 에 값을 더하면) 여기도 함께 늘려야 그 종류가 기준선에 잡힌다 — 안 늘려도 검사가
	 * 실패하지는 않는다. 그저 새 종류가 조용히 안 세어질 뿐이다.
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
	 * S15P21E201-328 이 "안전 정보" 라고 부르는 범위 — {@code V20260907003000} 이 ESTIMATED 를
	 * 이미 막아 둔 바로 그 네 종류다. 그 제약은 "추측을 저장하지 못하게" 만 막고, "출처 없이
	 * VERIFIED 로 적어 넣는 것" 은 막지 않는다 — 그 구멍을 여기서 잡는다.
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

	// ── S15P21E201-278 — 칼럼별 채움률 기준선 ────────────────────────────────────

	@Transactional(readOnly = true)
	public PlaceCompletenessReport measureCompleteness() {
		alignSearchPath();
		long placeTotal = count("SELECT count(*) FROM place");
		long nameEnFilled = count("SELECT count(*) FROM place WHERE name_en IS NOT NULL AND btrim(name_en) <> ''");

		Map<String, PlaceCompletenessReport.FillCount> byType = new LinkedHashMap<>();
		for (String type : KNOWN_FEATURE_TYPES) {
			// 🔴 evidence_status <> 'UNKNOWN' 로 센다. UNKNOWN 은 value 가 비어 있어야 한다는
			//    제약(ck_place_feature_unknown_has_no_value)이 이미 있어서, UNKNOWN 행은
			//    "채워졌다" 가 아니라 "모른다고 확인했다" 다.
			long filled = this.jdbcTemplate.queryForObject("""
					SELECT count(DISTINCT place_id) FROM place_feature
					WHERE feature_type = ? AND evidence_status <> 'UNKNOWN'
					""", Long.class, type);
			byType.put(type, new PlaceCompletenessReport.FillCount(filled, placeTotal));
		}

		return new PlaceCompletenessReport(OffsetDateTime.now(this.clock), placeTotal, nameEnFilled, byType);
	}

	// ── S15P21E201-315 — 영문 칸 번역 품질 ────────────────────────────────────────

	/**
	 * @return 한글이 섞였거나 비어 있는 장소의 목록. 비어 있으면 통과다.
	 *
	 * <p>🔴 {@code PlaceTranslation} 표는 이 저장소에 아직 없다(티켓이 상정한 다국어 번역
	 * 표는 만들어지지 않았다) — 그래서 {@code place.name_en} 하나만 본다. 표가 생기면 같은
	 * 방식으로 검사를 더한다.
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

	// ── S15P21E201-328 — 안전 정보 출처 누락 ─────────────────────────────────────

	/**
	 * @return 값은 있는데 출처({@code source_type})가 비어 있는 안전 정보 행의 목록.
	 *     비어 있으면 통과다.
	 *
	 * <p>0건이 아닐 때 할 일 — 그 행의 {@code source_type}·{@code source_id} 를 실제 출처로
	 * 채우거나, 출처를 확인할 수 없으면 그 행을 지운다({@code evidence_status='UNKNOWN'} 으로
	 * 낮추지 않는다 — UNKNOWN 은 "몰라서 안 채웠다" 인데 이 행은 "채웠지만 어디서 왔는지 안
	 * 남겼다" 라 뜻이 다르다. 출처를 모르면 그 값 자체를 신뢰할 수 없으므로 지우는 것이 맞다).
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
