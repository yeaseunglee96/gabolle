package com.gabolle.backend.place.support;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;

/**
 * 장소 테스트가 쓸 행을 넣고, <b>자기가 넣은 것만</b> 지운다.
 *
 * <h2>🔴 왜 {@code deleteAll} 을 쓰지 않는가</h2>
 *
 * 이 저장소의 다른 통합 테스트는 {@code @BeforeEach} 에서 표를 비우는 관례를 쓴다. 장소에는 그대로
 * 쓸 수 없다. 같은 DB 에서 도는 {@code PlaceFeatureCodeMapTest}(S15P21E201-545)가 {@code place} 와
 * {@code place_feature} 에 행을 넣고 <b>정리하지 않기</b> 때문이다. 표를 비우면 그쪽 행이 사라지거나
 * 외래키 위반이 난다 — 그리고 그 실패는 내 테스트가 아니라 남의 테스트에서 나타난다.
 *
 * <p>그래서 세 가지를 지킨다. 넣은 행의 id 를 들고 있다가 그것만 지우고, 이름에 이 실행만의 표를
 * 붙여 검색 테스트가 남의 행에 안 걸리게 하고, 건수는 절대값이 아니라 증분으로 본다.
 *
 * <h2>🔴 왜 JPA 가 아니라 raw SQL 인가</h2>
 *
 * 엔티티를 거치면 자바 쪽 검증만 통과하고 끝난다. 여기서 확인하고 싶은 것은 <b>DB 제약 자체</b>다 —
 * 태그형은 키가 반드시 있고({@code ck_place_feature_key_shape}), UNKNOWN 은 값을 가질 수 없다
 * ({@code ck_place_feature_unknown_has_no_value}). 픽스처가 그 제약을 실제로 통과해야 테스트가
 * 진짜 스키마 위에서 도는 것이 된다.
 *
 * <p>운영 코드에서 raw SQL 을 금지한 것과 어긋나지 않는다. 그 금지는 {@code gabolle} schema 를
 * 못 찾는 함정 때문인데, 테스트 DataSource 에는 {@code default_schema} 가 없어 표가 {@code public}
 * 에 있다.
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
				category, prefix() + "주소", lat, lng, this.token);
		this.placeIds.push(placeId);
		return placeId;
	}

	/**
	 * 태그형 피처. {@code featureKey} 가 반드시 있어야 한다 ({@code ck_place_feature_key_shape}).
	 *
	 * <p>{@code evidenceStatus} 가 {@code UNKNOWN} 이면 {@code value} 는 {@code null} 로 들어간다 —
	 * DB CHECK 가 그것을 강제하고, 그 강제가 곧 "정보 없음" 과 "해당 없음" 을 가르는 근거다.
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
