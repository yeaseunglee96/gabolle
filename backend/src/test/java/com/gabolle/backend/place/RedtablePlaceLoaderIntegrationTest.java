package com.gabolle.backend.place;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.gabolle.backend.place.loader.RedtablePlaceLoader;
import com.gabolle.backend.place.loader.RedtablePlaceRow;
import com.gabolle.backend.place.loader.SamePlaceReport;
import com.gabolle.backend.place.support.PlacePostgresIntegrationTest;

/** 레드테이블 장소가 실제 DB 에 들어가는지 (S15P21E201-1897). */
class RedtablePlaceLoaderIntegrationTest extends PlacePostgresIntegrationTest {

	private static final String DATASET = "survey-redtable-test";

	/** 이 시험의 번호 머리. 실제 레드테이블 번호(6자리 이하)와 안 겹친다. */
	private static final String ID_PREFIX = "9189700";

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private RedtablePlaceLoader loader;

	@BeforeEach
	@AfterEach
	void cleanUp() {
		this.jdbcTemplate.update("""
				DELETE FROM place_feature WHERE place_id IN (
				    SELECT place_id FROM place WHERE source_type = 'REDTABLE' AND source_id LIKE ?)
				""", ID_PREFIX + "%");
		this.jdbcTemplate.update("DELETE FROM place WHERE source_type = 'REDTABLE' AND source_id LIKE ?", ID_PREFIX + "%");
	}

	@Test
	@DisplayName("🔴 넣고, 갈래·출처·수집분이 찍히고, 사진은 비고, 두 번 돌려도 행이 안 는다")
	void insertsOnceWithCategoryAndNoPhoto() {
		List<RedtablePlaceRow> rows = List.of(
				row("1", "시험국밥집", "한식", "일반음식점", 35.3001, 129.3001),
				row("2", "시험커피", "커피숍", "휴게음식점", 35.3011, 129.3011));

		assertThat(load(rows)).isEqualTo(2);
		assertThat(load(rows)).isZero();

		List<Map<String, Object>> places = this.jdbcTemplate.queryForList("""
				SELECT source_id, name_ko, category, address, dataset_version, photo_url, photo_source, curation_status
				  FROM place WHERE source_type = 'REDTABLE' AND source_id LIKE ? ORDER BY source_id
				""", ID_PREFIX + "%");
		assertThat(places).hasSize(2);
		assertThat(places.get(0).get("category")).isEqualTo("FOOD");
		assertThat(places.get(1).get("category")).isEqualTo("CAFE_HEALING");
		assertThat(places.get(0).get("dataset_version")).isEqualTo(DATASET);
		assertThat(places.get(0).get("address")).isEqualTo("부산광역시 기장군 시험로 1");
		assertThat(places.get(0).get("photo_url")).isNull();
		assertThat(places.get(0).get("curation_status")).isEqualTo("CURATED");

		Integer tags = this.jdbcTemplate.queryForObject("""
				SELECT count(*) FROM place_feature f JOIN place p ON p.place_id = f.place_id
				 WHERE p.source_type = 'REDTABLE' AND p.source_id LIKE ? AND f.feature_type = 'CATEGORY_TAG'
				   AND f.feature_key = p.category
				""", Integer.class, ID_PREFIX + "%");
		assertThat(tags).isEqualTo(2);
	}

	@Test
	@DisplayName("🔴 번호가 달라도 같은 이름이 바로 옆에 있으면 넣지 않는다")
	void samePlaceIsBlocked() {
		load(List.of(row("3", "시험횟집", "회집", "일반음식점", 35.3021, 129.3021)));
		SamePlaceReport report = new SamePlaceReport();

		int inserted = this.loader.saveChunk(List.of(row("4", "시험횟집", "회집", "일반음식점", 35.30211, 129.30211)),
				DATASET, OffsetDateTime.now(), report);

		assertThat(inserted).isZero();
		assertThat(report.blocked()).isEqualTo(1);
	}

	private int load(List<RedtablePlaceRow> rows) {
		return this.loader.saveChunk(rows, DATASET, OffsetDateTime.now(), new SamePlaceReport());
	}

	private static RedtablePlaceRow row(String suffix, String name, String businessType, String licenseType,
			double lat, double lng) {
		return new RedtablePlaceRow(ID_PREFIX + suffix, name, lat, lng, "부산광역시 기장군 시험로 " + suffix, null,
				businessType, licenseType, null, null, "https://example.invalid/a.jpg", 1);
	}
}
