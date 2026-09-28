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

/** 진짜 PostgreSQL 에서 돈다. */
class SubwayExitLoaderTest extends PlacePostgresIntegrationTest {

	private static final String DATASET = "staged-test-202609";

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private TourApiPlaceLoader tourApiPlaceLoader;

	@Autowired
	private SubwayExitLoader subwayExitLoader;

	/** 이 시험이 쓰는 contentid 머리. 정본의 contentid(숫자)와도, 다른 시험의 머리와도 안 겹친다. */
	private static final String ID_PREFIX = "test-1748-subway-";

	private static final String CONTENT_ID = ID_PREFIX + "5290001";

	@BeforeEach
	@AfterEach
	void cleanUp() {
		// TourApiPlaceLoader.saveChunk 가 장소를 만들면서 CATEGORY_TAG 를 같이 넣는다 — 표식부터 지운다.
		// 🔴 이 시험이 넣은 행만 지운다(S15P21E201-1748). 전에는 TOURAPI 행을 통째로 지웠는데, 마이그레이션이
		//    넣은 TOURAPI 정본(도시 탐험 시장 등)에 MANUAL 출처 태그가 붙은 뒤로(V20260918160000) 외래키
		//    fk_place_feature_place 에 막혔다. 통째로 지우면 남의 시험이 믿는 정본도 사라진다.
		//    태그는 출처값이 아니라 장소로 찾아 지운다 — 적재기가 붙이는 표식의 출처가 바뀌어도 안 막힌다.
		this.jdbcTemplate.update("""
				DELETE FROM place_feature WHERE place_id IN (
				    SELECT place_id FROM place WHERE source_type = 'TOURAPI' AND source_id LIKE ?)
				""", ID_PREFIX + "%");
		this.jdbcTemplate.update("DELETE FROM place WHERE source_type = 'TOURAPI' AND source_id LIKE ?", ID_PREFIX + "%");
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
		this.givenTourApiPlace(CONTENT_ID);

		SubwayExitLoader.Result result = this.subwayExitLoader
				.load(List.of(new SubwayExitRow("TOURAPI", CONTENT_ID, "2호선 강남역 3번 출구")));

		assertThat(result.attached()).isEqualTo(1);
		assertThat(result.noPlace()).isZero();
		Map<String, Object> row = this.jdbcTemplate.queryForMap(
				"SELECT subway_exit FROM place WHERE source_type = 'TOURAPI' AND source_id = ?", CONTENT_ID);
		assertThat(row.get("subway_exit")).isEqualTo("2호선 강남역 3번 출구");
	}

	@Test
	@DisplayName("🔴 장소가 없으면 실패하지 않고 넘긴 수로 세어진다 — 장소 적재 순서 문제를 숫자로 보여준다")
	void 장소가_없으면_세어서_건너뛴다() {
		SubwayExitLoader.Result result = this.subwayExitLoader
				.load(List.of(new SubwayExitRow("TOURAPI", ID_PREFIX + "999999", "2호선 강남역 3번 출구")));

		assertThat(result.attached()).isZero();
		assertThat(result.noPlace()).isEqualTo(1);
	}
}
