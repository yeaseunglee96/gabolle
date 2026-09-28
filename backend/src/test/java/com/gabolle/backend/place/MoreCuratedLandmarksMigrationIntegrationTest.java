package com.gabolle.backend.place;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

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
 * 부산 명소 26곳을 넣는 마이그레이션(S15P21E201-1745)이 돌고, 앱의 검색이 그것을 정확일치로 찾는지 본다.
 *
 * <p>🔴 바탕을 남에게 맡기지 않는다. 통합 시험들이 한 DB 를 같이 쓰고 그중 하나가
 * {@code TRUNCATE place CASCADE} 를 돌려서, 마이그레이션이 넣은 행이 이 시험 차례에 없을 수 있다
 * (처음에는 그대로 믿었다가 CI 에서만 「부산역」이 0건이었다). 그래서 검사 직전에 그 SQL 을 다시
 * 돌린다 — {@code WHERE NOT EXISTS} 가드가 있어 두 번 돌려도 안전하다.
 * {@link CuratedLandmarkMigrationIntegrationTest} 와 같은 방식이다.
 */
class MoreCuratedLandmarksMigrationIntegrationTest extends PlacePostgresIntegrationTest {

	private static final List<String> SAMPLE = List.of("부산역", "BIFF광장", "부산타워", "더베이101", "부산시민공원", "동백섬");

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private PlaceSearchService placeSearchService;

	@BeforeEach
	void reapplyMigration() {
		this.jdbcTemplate.execute(migrationSql());
	}

	/** 마이그레이션 번호는 머지 순서로 바뀔 수 있어 파일 이름에 박지 않고 패턴으로 찾는다. */
	private String migrationSql() {
		try {
			Resource[] found = new PathMatchingResourcePatternResolver()
					.getResources("classpath*:db/migration/V*__curated_more_busan_landmarks.sql");
			assertThat(found).as("명소 26곳 마이그레이션 파일이 정확히 하나 있어야 한다").hasSize(1);
			return found[0].getContentAsString(StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new IllegalStateException("명소 마이그레이션 파일을 읽지 못했다", ex);
		}
	}

	@Test
	@DisplayName("🔴 정본에 넣은 명소가 정확일치로 첫 줄에 나온다 — 부산역을 치면 호텔만 나왔다")
	void landmarksAreFoundByExactName() {
		for (String name : SAMPLE) {
			PlacePageResponse page = this.placeSearchService.search(name, null, null, null);
			assertThat(page.items()).as(name).isNotEmpty();
			assertThat(page.items().get(0).nameKo()).as(name).isEqualTo(name);
		}
	}

	@Test
	@DisplayName("넣은 행은 전부 카카오 출처·좌표를 갖고 정본(CURATED)이다")
	void insertedRowsAreTraceableAndCurated() {
		List<java.util.Map<String, Object>> rows = this.jdbcTemplate.queryForList("""
				SELECT name_ko, source_type, source_id, lat, lng, curation_status
				FROM place WHERE dataset_version = 'KAKAO_LOCAL_2026-09-26'
				""");
		assertThat(rows).isNotEmpty();
		assertThat(rows).allSatisfy(row -> {
			assertThat(row.get("source_type")).isEqualTo("KAKAO_LOCAL");
			assertThat(row.get("source_id")).isNotNull();
			assertThat(row.get("lat")).as("%s 위도", row.get("name_ko")).isNotNull();
			assertThat(row.get("lng")).as("%s 경도", row.get("name_ko")).isNotNull();
			assertThat(row.get("curation_status")).isEqualTo("CURATED");
		});
	}

	@Test
	@DisplayName("🔴 사용자가 먼저 붙여 USER_SUBMITTED 로 있던 같은 카카오 장소는 정본으로 올라간다")
	void userSubmittedDuplicateIsPromoted() {
		// 부산역(카카오 8329752)을 누군가 기록에 먼저 붙였다고 친다 — 우리 행은 지우고 사용자 행을 둔다.
		this.jdbcTemplate.update("DELETE FROM place WHERE source_type = 'KAKAO_LOCAL' AND source_id = '8329752'");
		this.jdbcTemplate.update("""
				INSERT INTO place (place_id, name_ko, address, lat, lng, created_at, source_type, source_id,
				                   collected_at, curation_status)
				VALUES (gen_random_uuid(), '부산역', '부산 동구 중앙대로 206', 35.1152, 129.0415, now(),
				        'KAKAO_LOCAL', '8329752', now(), 'USER_SUBMITTED')
				""");

		this.jdbcTemplate.execute(migrationSql());

		assertThat(this.jdbcTemplate.queryForList(
				"SELECT curation_status FROM place WHERE source_type = 'KAKAO_LOCAL' AND source_id = '8329752'",
				String.class)).containsExactly("CURATED");
	}

	@Test
	@DisplayName("두 번 돌려도 같은 장소가 두 벌 생기지 않는다")
	void reapplyingDoesNotDuplicate() {
		Integer before = this.jdbcTemplate.queryForObject(
				"SELECT count(*) FROM place WHERE dataset_version = 'KAKAO_LOCAL_2026-09-26'", Integer.class);
		this.jdbcTemplate.execute(migrationSql());
		Integer after = this.jdbcTemplate.queryForObject(
				"SELECT count(*) FROM place WHERE dataset_version = 'KAKAO_LOCAL_2026-09-26'", Integer.class);
		assertThat(after).isEqualTo(before);
	}
}
