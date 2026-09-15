package com.gabolle.backend.place;

import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.gabolle.backend.place.api.PlaceSummaryResponse;
import com.gabolle.backend.place.service.PlaceSearchService;
import com.gabolle.backend.place.support.PlacePostgresIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;

/** 운영 마이그레이션 뒤 앱의 대표 검색어가 실제 일정용 place_id로 해석되는지 확인한다. */
class CuratedLandmarkMigrationIntegrationTest extends PlacePostgresIntegrationTest {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private PlaceSearchService placeSearchService;

	@Test
	@DisplayName("부산 대표 명소 네 곳은 출처와 좌표를 가진 정식 장소로 적재된다")
	void migrationSeedsTraceableCoreLandmarks() {
		Map<String, String> expectedSources = Map.of(
				"감천문화마을", "21362956",
				"해운대해수욕장", "7913306",
				"범어사", "26884008",
				"광안리해수욕장", "8202423");

		for (Map.Entry<String, String> expected : expectedSources.entrySet()) {
			Map<String, Object> row = this.jdbcTemplate.queryForMap("""
					SELECT source_type, source_id, lat, lng, dataset_version
					FROM place
					WHERE name_ko = ?
					""", expected.getKey());
			assertThat(row.get("source_type")).isEqualTo("KAKAO_LOCAL");
			assertThat(row.get("source_id")).isEqualTo(expected.getValue());
			assertThat(row.get("lat")).isNotNull();
			assertThat(row.get("lng")).isNotNull();
			assertThat(row.get("dataset_version")).isEqualTo("KAKAO_LOCAL_2026-09-15");
		}
	}

	@Test
	@DisplayName("감천문화마을 정확 검색은 유사 상호가 아니라 대표 명소를 첫 결과로 돌려준다")
	void exactGamcheonSearchReturnsTheLandmarkFirst() {
		PlaceSummaryResponse first = this.placeSearchService.search("감천문화마을", null, 8, null)
				.items().get(0);

		assertThat(first.nameKo()).isEqualTo("감천문화마을");
		assertThat(first.placeId()).isNotNull();
		assertThat(first.lat()).isCloseTo(35.09740872250286, org.assertj.core.data.Offset.offset(0.000001));
		assertThat(first.lng()).isCloseTo(129.01056080474402, org.assertj.core.data.Offset.offset(0.000001));
	}
}
