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
import com.gabolle.backend.place.service.PlaceSearchService;
import com.gabolle.backend.place.support.PlacePostgresIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 관광공사 무슬림 친화 식당 14곳을 넣는 마이그레이션(S15P21E201-1857)을 본다.
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
		try {
			Resource[] found = new PathMatchingResourcePatternResolver()
					.getResources("classpath*:db/migration/V*__kto_muslim_friendly_restaurants.sql");
			assertThat(found).as("무슬림 친화 식당 마이그레이션 파일이 정확히 하나 있어야 한다").hasSize(1);
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
				FROM place_feature WHERE source_version = ?
				""", DATASET);

		assertThat(tags).extracting(row -> row.get("feature_type") + ":" + row.get("feature_key"))
				.containsExactlyInAnyOrder("CUISINE_TAG:SEAFOOD", "DESIRED_FOOD_TAG:BOKGUK");
		assertThat(tags).allSatisfy(row -> assertThat(row.get("place_id")).isEqualTo(BOKGUK_PLACE_ID));
	}

	@Test
	@DisplayName("할랄 등급 표식은 붙이지 않는다")
	void addsNoDietaryTags() {
		assertThat(this.jdbcTemplate.queryForObject("""
				SELECT count(*) FROM place_feature f JOIN place p ON p.place_id = f.place_id
				WHERE p.dataset_version = ? AND f.feature_type = 'DIETARY_SUPPORT_TAG'
				""", Integer.class, DATASET)).isZero();
	}

	@Test
	@DisplayName("150m 안에 같은 가게가 영문 이름으로 이미 있으면 새로 만들지 않는다")
	void existingNearbyShopUnderEnglishNameBlocksInsert() {
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
		this.jdbcTemplate.update("DELETE FROM place WHERE source_type = 'SBIZ' AND source_id = 'test-seogane'");
	}

	@Test
	@DisplayName("사용자가 먼저 붙여 USER_SUBMITTED 로 있던 같은 가게는 정본으로 올라간다")
	void userSubmittedDuplicateIsPromoted() {
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
		this.jdbcTemplate.update("DELETE FROM place WHERE source_type = 'KAKAO_LOCAL' AND source_id = 'test-heuksiru'");
	}

	@Test
	@DisplayName("두 번 돌려도 같은 식당이 두 벌 생기지 않는다")
	void reapplyingDoesNotDuplicate() {
		int before = datasetCount();
		this.jdbcTemplate.execute(migrationSql());
		assertThat(datasetCount()).isEqualTo(before);
	}
}
