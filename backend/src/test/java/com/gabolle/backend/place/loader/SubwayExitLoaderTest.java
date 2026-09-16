package com.gabolle.backend.place.loader;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.gabolle.backend.place.support.PlacePostgresIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SubwayExitLoader} 가 진짜 PostgreSQL 에서 이미 있는 장소에 지하철 출구를 붙이는지
 * 잰다 — S15P21E201-479.
 */
class SubwayExitLoaderTest extends PlacePostgresIntegrationTest {

	private static final String DATASET = "staged-test-202609";

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private TourApiPlaceLoader tourApiPlaceLoader;

	@Autowired
	private SubwayExitLoader subwayExitLoader;

	@BeforeEach
	@AfterEach
	void cleanUp() {
		this.jdbcTemplate.update("DELETE FROM place WHERE source_type = 'TOURAPI'");
	}

	private void givenTourApiPlace(String contentId) {
		this.tourApiPlaceLoader.saveChunk(
				List.of(new TourApiPlaceRow(contentId, "12", "A01", null, "가덕도 등대", "부산광역시 강서구 외양포로 10",
						35.10, 129.03, null, null)),
				DATASET, OffsetDateTime.now());
	}

	@Test
	@DisplayName("이미 있는 장소에 지하철 출구를 붙인다")
	void 지하철_출구를_붙인다() {
		this.givenTourApiPlace("129156");

		SubwayExitLoader.Result result = this.subwayExitLoader
				.load(List.of(new SubwayExitRow("TOURAPI", "129156", "2호선 강남역 3번 출구")));

		assertThat(result.attached()).isEqualTo(1);
		assertThat(result.noPlace()).isZero();
		Map<String, Object> row = this.jdbcTemplate
				.queryForMap("SELECT subway_exit FROM place WHERE source_type = 'TOURAPI' AND source_id = '129156'");
		assertThat(row.get("subway_exit")).isEqualTo("2호선 강남역 3번 출구");
	}

	@Test
	@DisplayName("🔴 장소가 없으면 실패하지 않고 넘긴 수로 세어진다 — 장소 적재 순서 문제를 숫자로 보여준다")
	void 장소가_없으면_세어서_건너뛴다() {
		SubwayExitLoader.Result result = this.subwayExitLoader
				.load(List.of(new SubwayExitRow("TOURAPI", "999999", "2호선 강남역 3번 출구")));

		assertThat(result.attached()).isZero();
		assertThat(result.noPlace()).isEqualTo(1);
	}
}
