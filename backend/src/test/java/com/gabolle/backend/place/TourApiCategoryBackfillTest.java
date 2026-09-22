package com.gabolle.backend.place;

import java.time.OffsetDateTime;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.gabolle.backend.place.loader.TourApiPlaceLoader;
import com.gabolle.backend.place.loader.TourApiPlaceRow;
import com.gabolle.backend.place.support.PlacePostgresIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 적재 규칙이 바뀌었을 때 <b>이미 들어와 있던 행</b>이 따라오는가.
 *
 * <p>🔴 이 시험이 생긴 이유. 2026-09-21 에 숙박 대분류를 {@code LODGING} 으로 옮기기로 하고
 * {@code TourApiCategory} 를 고쳤는데, 그것만으로는 화면이 그대로 0곳이었다. 적재기가 이미 있는
 * 장소를 {@code contentid} 로 알아보고 <b>통째로 건너뛰기</b> 때문이다. 자료를 다시 돌려도
 * 「건너뛴 65곳」이라고만 찍히고 빈 갈래는 그대로 남는다 (S15P21E201-1383).
 *
 * <p>「코드 몇 줄이면 된다」가 반만 맞았던 자리다. 규칙을 고치는 것과 이미 들어온 자료가 그
 * 규칙을 따라가는 것은 다른 일이고, 둘째를 안 하면 아무것도 안 바뀐다.
 */
class TourApiCategoryBackfillTest extends PlacePostgresIntegrationTest {

	private static final String DATASET = "tourapi-backfill-test";

	/** 관광공사 숙박 대분류. 호텔·모텔·펜션이 전부 이 하나로 들어온다. */
	private static final String CAT1_LODGING = "B02";

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private TourApiPlaceLoader loader;

	@BeforeEach
	@AfterEach
	void cleanUp() {
		// place_feature 를 place_id 로 지운다. source_type 으로 지우면 다른 적재가 붙인 표식이
		// 남아 외래키에 걸린다 — 같은 장소에 출처가 다른 표식이 함께 달리기 때문이다.
		this.jdbcTemplate.update("""
				DELETE FROM place_feature WHERE place_id IN
					(SELECT place_id FROM place WHERE source_type = 'TOURAPI' AND dataset_version = ?)
				""", DATASET);
		this.jdbcTemplate.update("DELETE FROM place WHERE source_type = 'TOURAPI' AND dataset_version = ?", DATASET);
	}

	@Test
	@DisplayName("🔴 갈래가 비어 있던 장소는 다시 돌리면 채워진다 — 건너뛰기만 하면 규칙을 고쳐도 화면이 안 바뀐다")
	void emptyCategoryIsFilledOnReload() {
		// 1. 갈래를 못 내던 시절처럼, 갈래가 빈 채로 한 곳을 넣는다.
		this.loader.saveChunk(List.of(row("9000001", "C09", null)), DATASET, OffsetDateTime.now());
		assertThat(categoryOf("9000001")).as("준비가 틀렸다 — 빈 갈래로 들어가야 한다").isNull();

		// 2. 규칙이 바뀐 뒤 같은 자료를 다시 돌린다.
		int inserted = this.loader.saveChunk(List.of(row("9000001", CAT1_LODGING, "B02010100")),
				DATASET, OffsetDateTime.now());

		assertThat(inserted).as("새로 넣은 것이 아니라 고친 것이다 — 두 수를 합치면 안 된다").isZero();
		assertThat(categoryOf("9000001")).isEqualTo("LODGING");
		assertThat(categoryTagsOf("9000001"))
				.as("갈래를 채웠으면 CATEGORY_TAG 도 같이 생겨야 한다")
				.containsExactly("LODGING");
	}

	@Test
	@DisplayName("🔴 이미 값이 있는 갈래는 안 덮는다 — 덮으면 손으로 고친 갈래가 적재 때마다 조용히 돌아간다")
	void existingCategoryIsNeverOverwritten() {
		this.loader.saveChunk(List.of(row("9000002", "A02", "A02010100")), DATASET, OffsetDateTime.now());
		assertThat(categoryOf("9000002")).isEqualTo("CULTURE_TEMPLE");

		// 같은 contentid 가 이번엔 다른 대분류로 온다. 덮어쓰면 안 된다.
		this.loader.saveChunk(List.of(row("9000002", CAT1_LODGING, "B02010100")), DATASET, OffsetDateTime.now());

		assertThat(categoryOf("9000002")).isEqualTo("CULTURE_TEMPLE");
	}

	@Test
	@DisplayName("옮길 낱말이 없으면 빈 채로 둔다 — 지어내서 채우지 않는다")
	void unmappableCategoryStaysEmpty() {
		this.loader.saveChunk(List.of(row("9000003", "A03", "A03021600")), DATASET, OffsetDateTime.now());
		// 레포츠는 앱의 낱말에 대응하는 것이 없어 비우는 것이 맞다.
		this.loader.saveChunk(List.of(row("9000003", "A03", "A03021600")), DATASET, OffsetDateTime.now());

		assertThat(categoryOf("9000003")).isNull();
	}

	private static TourApiPlaceRow row(String contentId, String cat1, String cat3) {
		return new TourApiPlaceRow(contentId, "32", cat1, cat3, "숙소 " + contentId,
				"부산광역시 어딘가", 35.1, 129.0, null, null);
	}

	private String categoryOf(String contentId) {
		return this.jdbcTemplate.queryForObject(
				"SELECT category FROM place WHERE source_type = 'TOURAPI' AND source_id = ?",
				String.class, contentId);
	}

	private List<String> categoryTagsOf(String contentId) {
		return this.jdbcTemplate.queryForList("""
				SELECT f.feature_key FROM place_feature f
				JOIN place p ON p.place_id = f.place_id
				WHERE p.source_type = 'TOURAPI' AND p.source_id = ? AND f.feature_type = 'CATEGORY_TAG'
				""", String.class, contentId);
	}
}
