package com.gabolle.backend.place;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;

import com.gabolle.backend.place.api.PlacePageResponse;
import com.gabolle.backend.place.service.OpeningHoursFilterPort;
import com.gabolle.backend.place.service.OpeningHoursValue;
import com.gabolle.backend.place.service.PlaceSearchService;
import com.gabolle.backend.place.support.PlacePostgresIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 관광공사 무슬림 친화 식당 14곳을 넣는 마이그레이션(S15P21E201-1857)과 그 목록에 할랄 표식을 붙이는 마이그레이션
 * (S15P21E201-1873), 조사 결과를 붙이는 마이그레이션(S15P21E201-1873)을 본다.
 *
 * <p>{@link MoreCuratedLandmarksMigrationIntegrationTest} 와 같은 이유로 검사 직전에 그 SQL 을 다시 돌린다.
 * 통합 시험들이 한 DB 를 같이 쓰고 그중 하나가 {@code TRUNCATE place CASCADE} 를 돌려서, 마이그레이션이 넣은
 * 행이 이 시험 차례에 없을 수 있다.
 */
class KtoMuslimFriendlyRestaurantsMigrationTest extends PlacePostgresIntegrationTest {

	private static final String DATASET = "kto-muslim-friendly-2021-12";

	private static final String BOKGUK_PLACE_ID = "80f22b36-4381-5415-88fe-b0a64a914f76";

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private PlaceSearchService placeSearchService;

	@BeforeEach
	void reapplyMigration() {
		this.jdbcTemplate.execute(migrationSql());
	}

	private String migrationSql() {
		return migrationSql("kto_muslim_friendly_restaurants");
	}

	private String migrationSql(String description) {
		try {
			Resource[] found = new PathMatchingResourcePatternResolver()
					.getResources("classpath*:db/migration/V*__" + description + ".sql");
			assertThat(found).as(description + " 마이그레이션 파일이 정확히 하나 있어야 한다").hasSize(1);
			return found[0].getContentAsString(StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new IllegalStateException("무슬림 친화 식당 마이그레이션 파일을 읽지 못했다", ex);
		}
	}

	private int datasetCount() {
		return this.jdbcTemplate.queryForObject(
				"SELECT count(*) FROM place WHERE dataset_version = ?", Integer.class, DATASET);
	}

	@Test
	@DisplayName("영업이 확인된 14곳이 출처·좌표·정본 상태를 갖고 들어간다")
	void insertsFourteenTraceableRestaurants() {
		List<Map<String, Object>> rows = this.jdbcTemplate.queryForList("""
				SELECT name_ko, category, source_type, source_id, lat, lng, curation_status
				FROM place WHERE dataset_version = ?
				""", DATASET);

		assertThat(rows).hasSize(14);
		assertThat(rows).allSatisfy(row -> {
			assertThat(row.get("category")).isEqualTo("FOOD");
			assertThat(row.get("source_type")).isEqualTo("KTO_MUSLIM_FRIENDLY");
			assertThat(row.get("source_id")).as("%s 목록 번호", row.get("name_ko")).isNotNull();
			assertThat((Double) row.get("lat")).as("%s 위도", row.get("name_ko")).isBetween(34.9, 35.4);
			assertThat((Double) row.get("lng")).as("%s 경도", row.get("name_ko")).isBetween(128.7, 129.4);
			assertThat(row.get("curation_status")).isEqualTo("CURATED");
		});
	}

	@Test
	@DisplayName("영업을 확인하지 못한 셋(106·110·246)은 넣지 않는다")
	void skipsUnconfirmedRestaurants() {
		assertThat(this.jdbcTemplate.queryForList(
				"SELECT source_id FROM place WHERE dataset_version = ? AND source_id IN ('106', '110', '246')",
				String.class, DATASET)).isEmpty();
	}

	@Test
	@DisplayName("앱의 장소 검색이 넣은 식당을 이름으로 찾는다")
	void restaurantsAreSearchable() {
		for (String name : List.of("라마앤바바나 서면점", "흙시루", "산골애")) {
			PlacePageResponse page = this.placeSearchService.search(name, null, null, null);
			assertThat(page.items()).as(name).isNotEmpty();
			assertThat(page.items().get(0).nameKo()).as(name).isEqualTo(name);
		}
	}

	@Test
	@DisplayName("복국집 하나에만 음식 태그가 붙는다")
	void onlyBokgukGetsFoodTags() {
		List<Map<String, Object>> tags = this.jdbcTemplate.queryForList("""
				SELECT place_id::text AS place_id, feature_type, feature_key
				FROM place_feature WHERE source_version = ? AND feature_type <> 'DIETARY_SUPPORT_TAG'
				""", DATASET);

		assertThat(tags).extracting(row -> row.get("feature_type") + ":" + row.get("feature_key"))
				.containsExactlyInAnyOrder("CUISINE_TAG:SEAFOOD", "DESIRED_FOOD_TAG:BOKGUK");
		assertThat(tags).allSatisfy(row -> assertThat(row.get("place_id")).isEqualTo(BOKGUK_PLACE_ID));
	}

	@Test
	@DisplayName("목록 식당에는 등급을 나누지 않고 전부 할랄 지원 표식이 붙는다(S15P21E201-1873)")
	void everyListedRestaurantGetsHalalTag() {
		this.jdbcTemplate.execute(migrationSql("kto_halal_support_tag"));

		List<Map<String, Object>> untagged = this.jdbcTemplate.queryForList("""
				SELECT p.name_ko FROM place p
				WHERE p.dataset_version = ?
				  AND NOT EXISTS (SELECT 1 FROM place_feature f WHERE f.place_id = p.place_id
				                  AND f.feature_type = 'DIETARY_SUPPORT_TAG' AND f.feature_key = 'HALAL'
				                  AND f.evidence_status = 'VERIFIED')
				""", DATASET);
		assertThat(untagged).as("표식이 빠진 목록 식당").isEmpty();
	}

	@Test
	@DisplayName("할랄 표식 마이그레이션을 두 번 돌려도 한 곳에 표식은 하나다")
	void halalTagIsIdempotent() {
		this.jdbcTemplate.execute(migrationSql("kto_halal_support_tag"));
		this.jdbcTemplate.execute(migrationSql("kto_halal_support_tag"));

		assertThat(this.jdbcTemplate.queryForObject("""
				SELECT count(*) FROM place_feature
				WHERE feature_type = 'DIETARY_SUPPORT_TAG' AND feature_key = 'HALAL'
				GROUP BY place_id ORDER BY count(*) DESC LIMIT 1
				""", Integer.class)).isEqualTo(1);
	}

	@Test
	@DisplayName("원래 있던 목록 식당도 영문 이름 행까지 찾아 표식을 붙이고, 목록 밖의 옆 가게에는 안 붙인다")
	void existingRowsAreTaggedByNameNearby() {
		this.jdbcTemplate.update("""
				INSERT INTO place (place_id, name_ko, category, lat, lng, created_at, source_type, source_id, collected_at)
				VALUES (gen_random_uuid(), 'Hello India Al-Waha', 'FOOD', 35.16180, 129.16070, now(), 'OSM', 'test-hello', now()),
				       (gen_random_uuid(), '밀양순대돼지국밥', 'FOOD', 35.16150, 129.16040, now(), 'SBIZ', 'test-neighbor', now())
				""");

		this.jdbcTemplate.execute(migrationSql("kto_halal_support_tag"));

		assertThat(this.jdbcTemplate.queryForList("""
				SELECT p.source_id FROM place p JOIN place_feature f ON f.place_id = p.place_id
				WHERE p.source_id IN ('test-hello', 'test-neighbor')
				  AND f.feature_type = 'DIETARY_SUPPORT_TAG' AND f.feature_key = 'HALAL'
				""", String.class)).containsExactly("test-hello");
		this.jdbcTemplate.update("DELETE FROM place_feature WHERE place_id IN (SELECT place_id FROM place WHERE source_id IN ('test-hello', 'test-neighbor'))");
		this.jdbcTemplate.update("DELETE FROM place WHERE source_id IN ('test-hello', 'test-neighbor')");
	}

	@Test
	@DisplayName("150m 안에 같은 가게가 영문 이름으로 이미 있으면 새로 만들지 않는다")
	void existingNearbyShopUnderEnglishNameBlocksInsert() {
		this.jdbcTemplate.update("DELETE FROM place_feature WHERE place_id IN (SELECT place_id FROM place WHERE dataset_version = ? AND source_id = '324')", DATASET);
		this.jdbcTemplate.update("DELETE FROM place WHERE dataset_version = ? AND source_id = '324'", DATASET);
		this.jdbcTemplate.update("""
				INSERT INTO place (place_id, name_ko, address, lat, lng, created_at, source_type, source_id, collected_at)
				VALUES (gen_random_uuid(), 'Seogane Ori Gaya', '부산 부산진구 가야공원로 83-33', 35.14510, 129.02885, now(),
				        'SBIZ', 'test-seogane', now())
				""");

		this.jdbcTemplate.execute(migrationSql());

		assertThat(this.jdbcTemplate.queryForObject(
				"SELECT count(*) FROM place WHERE dataset_version = ? AND source_id = '324'", Integer.class, DATASET))
				.isZero();
		this.jdbcTemplate.update("DELETE FROM place_feature WHERE place_id IN (SELECT place_id FROM place WHERE source_type = 'SBIZ' AND source_id = 'test-seogane')");
		this.jdbcTemplate.update("DELETE FROM place WHERE source_type = 'SBIZ' AND source_id = 'test-seogane'");
	}

	@Test
	@DisplayName("사용자가 먼저 붙여 USER_SUBMITTED 로 있던 같은 가게는 정본으로 올라간다")
	void userSubmittedDuplicateIsPromoted() {
		this.jdbcTemplate.update("DELETE FROM place_feature WHERE place_id IN (SELECT place_id FROM place WHERE dataset_version = ? AND source_id = '322')", DATASET);
		this.jdbcTemplate.update("DELETE FROM place WHERE dataset_version = ? AND source_id = '322'", DATASET);
		this.jdbcTemplate.update("""
				INSERT INTO place (place_id, name_ko, address, lat, lng, created_at, source_type, source_id,
				                   collected_at, curation_status)
				VALUES (gen_random_uuid(), '흙시루', '부산 기장군 기장읍 차성로451번길 28', 35.25710, 129.21645, now(),
				        'KAKAO_LOCAL', 'test-heuksiru', now(), 'USER_SUBMITTED')
				""");

		this.jdbcTemplate.execute(migrationSql());

		assertThat(this.jdbcTemplate.queryForList(
				"SELECT curation_status FROM place WHERE source_type = 'KAKAO_LOCAL' AND source_id = 'test-heuksiru'",
				String.class)).containsExactly("CURATED");
		assertThat(this.jdbcTemplate.queryForObject(
				"SELECT count(*) FROM place WHERE dataset_version = ? AND source_id = '322'", Integer.class, DATASET))
				.isZero();
		this.jdbcTemplate.update("DELETE FROM place_feature WHERE place_id IN (SELECT place_id FROM place WHERE source_type = 'KAKAO_LOCAL' AND source_id = 'test-heuksiru')");
		this.jdbcTemplate.update("DELETE FROM place WHERE source_type = 'KAKAO_LOCAL' AND source_id = 'test-heuksiru'");
	}

	@Test
	@DisplayName("조사 결과가 적재기와 같은 모양으로 붙는다 — 새로 넣은 14곳 모두 방문 이유와 영업시간")
	void researchFactsAttachToNewRestaurants() {
		this.jdbcTemplate.execute(migrationSql("kto_research_facts"));

		List<Map<String, Object>> counts = this.jdbcTemplate.queryForList("""
				SELECT f.feature_type, count(DISTINCT f.place_id) AS places
				FROM place_feature f JOIN place p ON p.place_id = f.place_id
				WHERE p.dataset_version = ? AND f.source_version = 'kto-muslim-friendly-research-20260930'
				GROUP BY f.feature_type
				""", DATASET);
		assertThat(counts).extracting(row -> row.get("feature_type") + "=" + row.get("places"))
				.containsExactlyInAnyOrder("WHY_VISIT=14", "OPENING_HOURS=14", "MENU_PRICE_WON=3");
	}

	@Test
	@DisplayName("정규화한 영업시간을 판정기가 읽는다 — 흙시루는 월요일 휴무, 화요일 낮에는 연다")
	void openingHoursAreReadableByTheFilter() {
		this.jdbcTemplate.execute(migrationSql("kto_research_facts"));
		String value = this.jdbcTemplate.queryForObject("""
				SELECT f.value::text FROM place_feature f JOIN place p ON p.place_id = f.place_id
				WHERE p.dataset_version = ? AND p.source_id = '322' AND f.feature_type = 'OPENING_HOURS'
				""", String.class, DATASET);

		java.time.ZoneOffset kst = java.time.ZoneOffset.ofHours(9);
		assertThat(OpeningHoursValue.answerAt(value, java.time.OffsetDateTime.of(2026, 10, 5, 13, 0, 0, 0, kst)))
				.as("월요일 13시").isEqualTo(OpeningHoursFilterPort.Answer.CLOSED);
		assertThat(OpeningHoursValue.answerAt(value, java.time.OffsetDateTime.of(2026, 10, 6, 13, 0, 0, 0, kst)))
				.as("화요일 13시").isEqualTo(OpeningHoursFilterPort.Answer.OPEN);
	}

	@Test
	@DisplayName("조사 결과 마이그레이션을 두 번 돌려도 종류마다 한 행이다")
	void researchFactsAreIdempotent() {
		this.jdbcTemplate.execute(migrationSql("kto_research_facts"));
		this.jdbcTemplate.execute(migrationSql("kto_research_facts"));

		assertThat(this.jdbcTemplate.queryForObject("""
				SELECT count(*) FROM (
				  SELECT place_id, feature_type FROM place_feature
				  WHERE source_version = 'kto-muslim-friendly-research-20260930'
				  GROUP BY place_id, feature_type HAVING count(*) > 1) dup
				""", Integer.class)).isZero();
	}

	@Test
	@DisplayName("두 번 돌려도 같은 식당이 두 벌 생기지 않는다")
	void reapplyingDoesNotDuplicate() {
		int before = datasetCount();
		this.jdbcTemplate.execute(migrationSql());
		assertThat(datasetCount()).isEqualTo(before);
	}
}
