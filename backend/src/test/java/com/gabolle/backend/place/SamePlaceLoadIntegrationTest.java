package com.gabolle.backend.place;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.gabolle.backend.place.loader.OsmPlaceLoader;
import com.gabolle.backend.place.loader.OsmPoiRow;
import com.gabolle.backend.place.loader.SamePlaceReport;
import com.gabolle.backend.place.loader.SamePlaceRule;
import com.gabolle.backend.place.loader.SbizPlaceLoader;
import com.gabolle.backend.place.loader.SbizRow;
import com.gabolle.backend.place.support.PlacePostgresIntegrationTest;

/**
 * 적재기가 같은 장소를 다시 넣지 않는다 (S15P21E201-1620) — 진짜 DB 에서 두 적재기를 그대로 돌린다.
 *
 * <p>운영 중복 113줄이 오픈스트리트맵 적재가 상가·관광공사에 이미 있던 곳을 또 넣어 생겼다(장소 합치기 S15P21E201-1619).
 * 번호가 다르니 번호로는 못 막는다 — 이름과 거리로 막는다. 좌표는 해운대 한 점에서 북쪽으로 미터만큼 옮겨 만든다.
 */
class SamePlaceLoadIntegrationTest extends PlacePostgresIntegrationTest {

	private static final double LAT = 35.1600;

	private static final double LNG = 129.1600;

	/** 이 시험이 쓰는 오픈스트리트맵 번호 — 운영 번호와 안 겹치게 크게 잡는다. */
	private static final long OSM = 91_620_000_000L;

	@Autowired
	private OsmPlaceLoader osmLoader;

	@Autowired
	private SbizPlaceLoader sbizLoader;

	@Autowired
	private JdbcTemplate jdbc;

	@AfterEach
	void cleanUp() {
		this.jdbc.update("UPDATE place SET merged_into = NULL, curation_status = 'CURATED' WHERE source_id LIKE 't-1620-%' "
				+ "OR (source_type = 'OSM' AND source_id LIKE '916200000%')");
		this.jdbc.update("DELETE FROM place_feature WHERE place_id IN (SELECT place_id FROM place WHERE source_id LIKE "
				+ "'t-1620-%' OR (source_type = 'OSM' AND source_id LIKE '916200000%'))");
		this.jdbc.update("DELETE FROM place WHERE source_id LIKE 't-1620-%' OR (source_type = 'OSM' AND source_id LIKE "
				+ "'916200000%')");
	}

	@Test
	@DisplayName("🔴 상가에 있는 가게를 오픈스트리트맵이 또 가져와도 한 줄 — 운영 「송정3대국밥」(19m)")
	void aShopAlreadyFromSbizIsNotAddedAgainByOsm() {
		this.sbizLoader.saveChunk(List.of(sbiz("t-1620-1", "송정3대국밥", 0)), "test-1620", OffsetDateTime.now());
		SamePlaceReport report = new SamePlaceReport();

		int saved = this.osmLoader.saveChunk(List.of(osm(1, "송정 3대 국밥", "FOOD", 19)), "test-1620",
				OffsetDateTime.now(), report);

		assertThat(saved).isZero();
		assertThat(report.blocked()).isEqualTo(1);
		assertThat(countNamed("송정%3대%국밥")).isEqualTo(1);
	}

	@Test
	@DisplayName("🔴 합쳐진 줄은 같은 번호로 다시 와도 되살아나지 않고, 새 번호의 같은 곳도 안 들어온다")
	void aMergedRowStaysMergedAndBlocksNewcomers() {
		this.sbizLoader.saveChunk(List.of(sbiz("t-1620-2", "흰여울문화마을", 0)), "test-1620", OffsetDateTime.now());
		this.osmLoader.saveChunk(List.of(osm(2, "흰여울문화마을", "CULTURE_TEMPLE", 500)), "test-1620", OffsetDateTime.now());
		UUID merged = OsmPlaceLoader.placeIdOf(OSM + 2);
		UUID keeper = SbizPlaceLoader.placeIdOf("t-1620-2");
		// 장소 합치기가 한 것과 같은 표시 — 합쳐짐 + 남는 줄.
		this.jdbc.update("UPDATE place SET curation_status = 'MERGED', merged_into = ? WHERE place_id = ?", keeper, merged);

		SamePlaceReport report = new SamePlaceReport();
		int saved = this.osmLoader.saveChunk(List.of(osm(2, "흰여울문화마을", "CULTURE_TEMPLE", 500),
				osm(3, "흰여울문화마을", "CULTURE_TEMPLE", 520)), "test-1620", OffsetDateTime.now(), report);

		assertThat(saved).isZero();
		assertThat(this.jdbc.queryForObject("SELECT curation_status FROM place WHERE place_id = ?", String.class, merged))
				.isEqualTo("MERGED");
		assertThat(this.jdbc.queryForObject("SELECT merged_into FROM place WHERE place_id = ?", UUID.class, merged))
				.isEqualTo(keeper);
		assertThat(report.blocked()).as("같은 번호는 번호로 건너뛰고, 새 번호는 합쳐진 줄과 20m 라 막힌다").isEqualTo(1);
		assertThat(countNamed("흰여울문화마을")).isEqualTo(2);
	}

	@Test
	@DisplayName("🔴 숨긴 곳도 새 번호로 돌아오지 못한다")
	void aHiddenPlaceDoesNotComeBackUnderANewNumber() {
		this.osmLoader.saveChunk(List.of(osm(4, "부산은행 본점", "CULTURE_TEMPLE", 0)), "test-1620", OffsetDateTime.now());
		this.jdbc.update("UPDATE place SET curation_status = 'HIDDEN' WHERE place_id = ?", OsmPlaceLoader.placeIdOf(OSM + 4));
		SamePlaceReport report = new SamePlaceReport();

		this.osmLoader.saveChunk(List.of(osm(5, "부산은행 본점", "CULTURE_TEMPLE", 40)), "test-1620", OffsetDateTime.now(),
				report);

		assertThat(report.blocked()).isEqualTo(1);
		assertThat(countNamed("부산은행 본점")).isEqualTo(1);
	}

	@Test
	@DisplayName("🔴 해변 같은 이름 381m 는 넣지 않고 사람 확인 목록에 남긴다 — CSV 로도 떨어진다")
	void aWideSameNameGoesToTheReviewListAndTheCsv(@TempDir Path dir) throws Exception {
		this.osmLoader.saveChunk(List.of(osm(6, "송정해수욕장", "SEA_BEACH", 0)), "test-1620", OffsetDateTime.now());
		SamePlaceReport report = new SamePlaceReport();

		int saved = this.osmLoader.saveChunk(List.of(osm(7, "송정해수욕장", "SEA_BEACH", 381)), "test-1620",
				OffsetDateTime.now(), report);

		assertThat(saved).isZero();
		assertThat(report.blocked()).isZero();
		assertThat(report.reviews()).singleElement().satisfies((row) -> {
			assertThat(row.kind()).isEqualTo(SamePlaceRule.Kind.REVIEW_WIDE);
			assertThat(row.verdict().existingPlaceId()).isEqualTo(OsmPlaceLoader.placeIdOf(OSM + 6));
			assertThat(Math.round(row.verdict().distanceM())).isBetween(379L, 383L);
		});

		Path csv = dir.resolve("review.csv");
		report.writeCsv(csv);
		List<String> lines = Files.readAllLines(csv, StandardCharsets.UTF_8);
		assertThat(lines).hasSize(2);
		assertThat(lines.get(0)).startsWith("﻿번호,왜 사람이 보나").endsWith("판정(같음/다름)");
		assertThat(lines.get(1)).contains("해변·산책로·공원 같은 이름 150m~1km", "송정해수욕장", String.valueOf(OSM + 7))
				.endsWith(",");
	}

	@Test
	@DisplayName("🔴 체인은 30m 안만 막고, 30~150m 는 사람 확인, 그보다 멀면 다른 지점으로 넣는다")
	void chainsFollowTheBrandRule() {
		this.sbizLoader.saveChunk(List.of(sbiz("t-1620-3", "스타벅스", 0)), "test-1620", OffsetDateTime.now());
		SamePlaceReport report = new SamePlaceReport();

		int saved = this.osmLoader.saveChunk(List.of(osm(8, "스타벅스", "CAFE_HEALING", 20),
				osm(9, "스타벅스", "CAFE_HEALING", 80), osm(10, "스타벅스", "CAFE_HEALING", 400)), "test-1620",
				OffsetDateTime.now(), report);

		assertThat(saved).as("400m 떨어진 것만 들어간다").isEqualTo(1);
		assertThat(report.blocked()).isEqualTo(1);
		assertThat(report.reviews()).extracting((row) -> row.kind()).containsExactly(SamePlaceRule.Kind.REVIEW_CHAIN);
	}

	@Test
	@DisplayName("🔴 같은 덩어리 안의 같은 곳도 한 줄 — 앞 줄을 받아들였으면 뒤 줄은 그것과 견준다")
	void duplicatesWithinOneChunkBecomeOneRow() {
		SamePlaceReport report = new SamePlaceReport();

		int saved = this.osmLoader.saveChunk(List.of(osm(11, "광안다이닝", "FOOD", 0), osm(12, "광안다이닝", "FOOD", 10)),
				"test-1620", OffsetDateTime.now(), report);

		assertThat(saved).isEqualTo(1);
		assertThat(report.blocked()).isEqualTo(1);
		assertThat(countNamed("광안다이닝")).isEqualTo(1);
	}

	@Test
	@DisplayName("흔한 이름이라도 150m 밖이면 넣는다 — 다른 가게를 막으면 없는 장소를 만드는 것보다 나쁘다")
	void aCommonNameFarAwayIsStillAdded() {
		this.sbizLoader.saveChunk(List.of(sbiz("t-1620-4", "해운대횟집", 0)), "test-1620", OffsetDateTime.now());
		SamePlaceReport report = new SamePlaceReport();

		int saved = this.osmLoader.saveChunk(List.of(osm(13, "해운대횟집", "FOOD", 200)), "test-1620", OffsetDateTime.now(),
				report);

		assertThat(saved).isEqualTo(1);
		assertThat(report.notInserted()).isZero();
	}

	/** 기준점에서 북쪽으로 {@code metersNorth} 옮긴 오픈스트리트맵 장소. */
	private static OsmPoiRow osm(int n, String name, String category, double metersNorth) {
		return new OsmPoiRow(OSM + n, name, category, null, LAT + metersNorth / 111_195.0, LNG);
	}

	private static SbizRow sbiz(String storeId, String name, double metersNorth) {
		return new SbizRow(storeId, name, "", "한식", "부산광역시 해운대구 어딘가 1", LAT + metersNorth / 111_195.0, LNG);
	}

	private int countNamed(String likeName) {
		return this.jdbc.queryForObject("SELECT count(*) FROM place WHERE name_ko LIKE ? AND (source_id LIKE 't-1620-%' "
				+ "OR (source_type = 'OSM' AND source_id LIKE '916200000%'))", Integer.class, likeName);
	}
}
