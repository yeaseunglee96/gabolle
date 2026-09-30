package com.gabolle.backend.place.support;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 장소 테스트가 쓸 행을 넣고, 자기가 넣은 것만 지운다. 쓰는 쪽은 세 가지를 지킨다 —
 * 정리는 {@link #cleanUp()} 에 맡기고 표를 통째로 비우지 않는다(다른 통합 테스트가 같은 표에
 * 행을 남기고 정리하지 않는다), 검색어는 {@link #prefix()}·{@link #token()} 을 붙여 만든다,
 * 건수는 절대값이 아니라 증분으로 본다.
 *
 * <p>JPA 가 아니라 raw SQL 인 것은 DB 제약 자체를 통과시켜 보기 위해서다 — 태그형은 키가 반드시
 * 있고({@code ck_place_feature_key_shape}), UNKNOWN 은 값을 가질 수 없다
 * ({@code ck_place_feature_unknown_has_no_value}). 운영 코드의 raw SQL 금지는 {@code gabolle}
 * schema 를 못 찾는 함정 때문인데, 테스트 DataSource 에는 {@code default_schema} 가 없어 표가
 * {@code public} 에 있다.
 */
public final class PlaceFixture {

	private final JdbcTemplate jdbcTemplate;

	/** 이 실행만의 표. 이름 검색 테스트가 남이 남긴 행에 걸리지 않게 한다. */
	private final String token = UUID.randomUUID().toString().substring(0, 8);

	/** 넣은 순서의 역순으로 지우려고 스택으로 들고 있다 — 피처가 장소를 참조하기 때문이다. */
	private final Deque<UUID> placeIds = new ArrayDeque<>();

	private final Deque<UUID> featureIds = new ArrayDeque<>();

	public PlaceFixture(JdbcTemplate jdbcTemplate) {
		this.jdbcTemplate = jdbcTemplate;
	}

	/** 이 실행의 표. 검색어를 만들 때 쓴다. */
	public String token() {
		return this.token;
	}

	/** 이름 앞에 붙는 표. 예: {@code "ZZT-3f2a1b9c-"}. */
	public String prefix() {
		return "ZZT-" + this.token + "-";
	}

	public UUID insertPlace(String nameKo, String nameEn, String category, Double lat, Double lng) {
		UUID placeId = UUID.randomUUID();
		this.jdbcTemplate.update("""
				INSERT INTO place (place_id, name_ko, name_en, category, address, lat, lng,
				                   created_at, source_type, source_id, collected_at, observed_at, dataset_version)
				VALUES (?, ?, ?, ?, ?, ?, ?, now(), 'FIXTURE', ?, now(), now(), 'fixture-test')
				""",
				placeId, prefix() + nameKo,
				nameEn == null ? null : prefix() + nameEn,
				// 🔴 source_id 를 장소마다 다르게 준다. 전에는 이 실행의 token 하나를 모든 장소에
				//    똑같이 넣었는데, uq_place_source(S15P21E201-1426)가 생기면서 한 시험이 장소를
				//    둘 넣는 순간 두 번째가 제약에 걸린다. 같은 (출처, 식별자)를 여러 장소에 주는
				//    것은 원래도 「서로 다른 장소가 같은 원천 기록에서 왔다」는 거짓말이었다.
				category, prefix() + "주소", lat, lng, this.token + "-" + placeId);
		this.placeIds.push(placeId);
		return placeId;
	}

	/** 관광공사 일본어·중국어 공식 이름을 채운다. 다른 시험과 안 섞이게 앞에 {@link #prefix()} 를 붙인다. */
	public void setLocalNames(UUID placeId, String nameJa, String nameZhHans, String nameZhHant) {
		this.jdbcTemplate.update("UPDATE place SET name_ja = ?, name_zh_hans = ?, name_zh_hant = ? WHERE place_id = ?",
				nameJa == null ? null : prefix() + nameJa, nameZhHans == null ? null : prefix() + nameZhHans,
				nameZhHant == null ? null : prefix() + nameZhHant, placeId);
	}

	/**
	 * 태그형 피처. {@code featureKey} 가 반드시 있어야 한다 ({@code ck_place_feature_key_shape}).
	 * {@code evidenceStatus} 가 {@code UNKNOWN} 이면 {@code value} 는 {@code null} 로 들어간다.
	 */
	public UUID insertTagFeature(UUID placeId, String featureType, String featureKey,
			String evidenceStatus, String valueJson) {
		return insertFeature(placeId, featureType, featureKey, evidenceStatus, valueJson);
	}

	/** 점수형·참거짓형 피처. 키가 없어야 한다. */
	public UUID insertValueFeature(UUID placeId, String featureType, String evidenceStatus, String valueJson) {
		return insertFeature(placeId, featureType, null, evidenceStatus, valueJson);
	}

	private UUID insertFeature(UUID placeId, String featureType, String featureKey,
			String evidenceStatus, String valueJson) {
		UUID featureId = UUID.randomUUID();
		String effectiveValue = "UNKNOWN".equals(evidenceStatus) ? null : valueJson;
		this.jdbcTemplate.update("""
				INSERT INTO place_feature (place_feature_id, place_id, feature_type, feature_key, value,
				                           evidence_status, source_type, source_id, observed_at,
				                           source_version, created_at)
				VALUES (?, ?, ?, ?, CAST(? AS jsonb), ?, 'FIXTURE', ?, now(), 'fixture-v1', now())
				""",
				featureId, placeId, featureType, featureKey, effectiveValue, evidenceStatus, this.token);
		this.featureIds.push(featureId);
		return featureId;
	}

	/** 넣은 것만 지운다. 피처를 먼저 지워야 외래키에 안 걸린다. */
	public void cleanUp() {
		while (!this.featureIds.isEmpty()) {
			this.jdbcTemplate.update("DELETE FROM place_feature WHERE place_feature_id = ?", this.featureIds.pop());
		}
		while (!this.placeIds.isEmpty()) {
			this.jdbcTemplate.update("DELETE FROM place WHERE place_id = ?", this.placeIds.pop());
		}
	}
}
