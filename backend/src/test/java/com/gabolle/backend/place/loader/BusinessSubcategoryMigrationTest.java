package com.gabolle.backend.place.loader;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;

import com.gabolle.backend.place.support.PlacePostgresIntegrationTest;

/**
 * 상가정보 업종 소분류를 싣는 마이그레이션(S15P21E201-1873)을 본다.
 *
 * <p>행 아이디가 {@link SbizPlaceLoader#featureIdOf} 와 같아야 적재기를 다시 돌려도 같은 가게에 업종이 두 줄 생기지 않는다.
 * 마이그레이션은 파이썬으로 뽑은 값이라 자바 계산과 어긋나면 조용히 두 벌이 된다 — 그것을 여기서 막는다.
 */
class BusinessSubcategoryMigrationTest extends PlacePostgresIntegrationTest {

	private static final Pattern FIRST_ROW = Pattern.compile("\\(UUID '([0-9a-f-]{36})', '([^']+)', '([^']+)'\\)");

	@Autowired
	private JdbcTemplate jdbcTemplate;

	private static String migrationSql() {
		try {
			Resource[] found = new PathMatchingResourcePatternResolver()
					.getResources("classpath*:db/migration/V*__business_subcategory.sql");
			assertThat(found).as("업종 소분류 마이그레이션 파일이 정확히 하나 있어야 한다").hasSize(1);
			return found[0].getContentAsString(StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new IllegalStateException("업종 소분류 마이그레이션 파일을 읽지 못했다", ex);
		}
	}

	@Test
	@DisplayName("마이그레이션의 행 아이디가 적재기의 계산과 같다 — 다시 적재해도 두 벌이 안 생긴다")
	void rowIdsMatchTheLoader() {
		Matcher row = FIRST_ROW.matcher(migrationSql());
		int checked = 0;
		while (row.find() && checked < 50) {
			assertThat(UUID.fromString(row.group(1))).as(row.group(2))
					.isEqualTo(SbizPlaceLoader.featureIdOf(row.group(2), SbizPlaceLoader.BUSINESS_SUBCATEGORY, null));
			checked++;
		}
		assertThat(checked).isEqualTo(50);
	}

	@Test
	@DisplayName("상가정보 장소에 업종 소분류가 붙고, 두 번 돌려도 한 줄이다")
	void attachesSubcategoryToSbizPlaces() {
		Matcher row = FIRST_ROW.matcher(migrationSql());
		assertThat(row.find()).isTrue();
		String storeId = row.group(2);
		String subCategory = row.group(3);
		UUID placeId = SbizPlaceLoader.placeIdOf(storeId);
		this.jdbcTemplate.update("DELETE FROM place_feature WHERE place_id = ?", placeId);
		this.jdbcTemplate.update("DELETE FROM place WHERE place_id = ?", placeId);
		this.jdbcTemplate.update("""
				INSERT INTO place (place_id, name_ko, category, lat, lng, created_at, source_type, source_id, collected_at,
				                   dataset_version)
				VALUES (?, '시험 식당', 'FOOD', 35.1, 129.0, now(), 'SBIZ', ?, now(), 'test')
				""", placeId, storeId);

		// 🔴 INSERT 부분만 다시 돌린다. 파일 앞의 ALTER(허용 목록 DROP+ADD)까지 돌리면 공유 시험 DB 의
		//    ck_place_feature_type 이 이 파일 시점의 목록으로 되돌아가, 뒤에 더한 갈래(S15P21E201-1886 의
		//    MENU_ITEMS 등)를 넣는 다른 시험이 CI 에서 제약 위반으로 깨진다. 목록은 Flyway 가 이미 최신으로 만들었다.
		String sql = migrationSql();
		String insertOnly = sql.substring(sql.indexOf("INSERT INTO place_feature"));
		this.jdbcTemplate.execute(insertOnly);
		this.jdbcTemplate.execute(insertOnly);

		assertThat(this.jdbcTemplate.queryForList(
				"SELECT value ->> 'name' FROM place_feature WHERE place_id = ? AND feature_type = 'BUSINESS_SUBCATEGORY'",
				String.class, placeId)).containsExactly(subCategory);

		this.jdbcTemplate.update("DELETE FROM place_feature WHERE place_id = ?", placeId);
		this.jdbcTemplate.update("DELETE FROM place WHERE place_id = ?", placeId);
	}
}
