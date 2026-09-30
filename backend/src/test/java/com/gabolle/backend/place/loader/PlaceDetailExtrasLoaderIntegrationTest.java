package com.gabolle.backend.place.loader;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.gabolle.backend.place.api.PlaceDetailResponse;
import com.gabolle.backend.place.service.PlaceDetailService;
import com.gabolle.backend.place.support.PlaceFixture;
import com.gabolle.backend.place.support.PlacePostgresIntegrationTest;

/**
 * 장소 상세 사실 적재기를 진짜 PostgreSQL(Flyway 마이그레이션까지 적용) 위에서 돌린다 — S15P21E201-1886.
 *
 * <p>🔴 2026-09-22 에 적재기가 머지됐는데 {@code ck_place_feature_type} 에 갈래가 없어 운영에서 행이 전부 거절됐다.
 * 여기서 새 여섯 갈래를 실제로 넣어 보므로, 마이그레이션이 빠지면 이 시험이 먼저 빨개진다.
 */
class PlaceDetailExtrasLoaderIntegrationTest extends PlacePostgresIntegrationTest {

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private PlaceDetailExtrasLoader loader;

	@Autowired
	private PlaceDetailService placeDetailService;

	private PlaceFixture fixture;

	private UUID placeId;

	@BeforeEach
	void setUp() {
		this.fixture = new PlaceFixture(this.jdbcTemplate);
		this.placeId = this.fixture.insertPlace("상세사실", null, "ATTRACTION", 35.1, 129.0);
	}

	@AfterEach
	void tearDown() {
		// 적재기가 넣은 행은 PlaceFixture 가 모른다. 장소보다 먼저 지워야 외래키에 안 걸린다.
		this.jdbcTemplate.update("DELETE FROM place_feature WHERE place_id = ?", this.placeId);
		this.fixture.cleanUp();
	}

	private List<PlaceDetailExtrasLoader.Row> sixNewTypes(UUID target) {
		String[][] specs = {
				{ "MENU_ITEMS", "{\"items\":[{\"nameKo\":\"밀면\",\"nameEn\":\"Milmyeon\",\"priceWon\":9000,"
						+ "\"ingredientsKo\":\"밀가루\",\"ingredientsEn\":\"wheat flour\",\"signature\":true}]}" },
				{ "FOREIGN_MENU", "{\"available\":true}" },
				{ "AMENITIES", "{\"wifi\":true,\"parking\":false,\"restroom\":null,\"reservation\":null,\"homepage\":\"https://example.com\"}" },
				{ "ADMISSION_FEE", "{\"raw\":\"무료\"}" },
				{ "NEARBY_LANDMARK", "{\"name\":\"광안대교\",\"distanceM\":420}" },
				{ "BEST_TIME", "{\"day\":2,\"night\":5,\"any\":1}" } };
		return java.util.Arrays.stream(specs)
				.map(spec -> PlaceDetailExtrasLoader.parse(PlaceDetailExtrasLoaderTest.line(spec[0], spec[1])
						.replace("3f2a1b9c-0000-4000-8000-000000000001", target.toString())))
				.toList();
	}

	@Test
	@DisplayName("🔴 새 여섯 갈래가 CHECK 제약을 지나 들어간다")
	void allSixNewTypesAreAccepted() {
		PlaceDetailExtrasLoader.Result result = this.loader.saveChunk(sixNewTypes(this.placeId), OffsetDateTime.now());

		assertThat(result).isEqualTo(new PlaceDetailExtrasLoader.Result(6, 0, 0));
		assertThat(this.jdbcTemplate.queryForList(
				"SELECT feature_type FROM place_feature WHERE place_id = ? ORDER BY feature_type", String.class,
				this.placeId))
				.containsExactly("ADMISSION_FEE", "AMENITIES", "BEST_TIME", "FOREIGN_MENU", "MENU_ITEMS",
						"NEARBY_LANDMARK");
		assertThat(this.jdbcTemplate.queryForObject(
				"SELECT source_version FROM place_feature WHERE place_id = ? AND feature_type = 'MENU_ITEMS'",
				String.class, this.placeId)).isEqualTo("detail-202609");
	}

	@Test
	@DisplayName("🔴 두 번 돌려도 행이 늘지 않는다 — 두 번째는 전부 「이미 있어 건너뜀」")
	void secondRunInsertsNothing() {
		this.loader.saveChunk(sixNewTypes(this.placeId), OffsetDateTime.now());
		PlaceDetailExtrasLoader.Result second = this.loader.saveChunk(sixNewTypes(this.placeId), OffsetDateTime.now());

		assertThat(second).isEqualTo(new PlaceDetailExtrasLoader.Result(0, 6, 0));
		assertThat(this.jdbcTemplate.queryForObject("SELECT count(*) FROM place_feature WHERE place_id = ?",
				Integer.class, this.placeId)).isEqualTo(6);
	}

	@Test
	@DisplayName("다른 출처가 이미 넣은 갈래도 덮지 않는다")
	void anExistingRowFromAnotherSourceIsKept() {
		this.fixture.insertValueFeature(this.placeId, "ADMISSION_FEE", "ESTIMATED", "{\"raw\":\"옛 값\"}");

		PlaceDetailExtrasLoader.Result result = this.loader.saveChunk(sixNewTypes(this.placeId), OffsetDateTime.now());

		assertThat(result).isEqualTo(new PlaceDetailExtrasLoader.Result(5, 1, 0));
		assertThat(this.jdbcTemplate.queryForObject(
				"SELECT value->>'raw' FROM place_feature WHERE place_id = ? AND feature_type = 'ADMISSION_FEE'",
				String.class, this.placeId)).isEqualTo("옛 값");
	}

	@Test
	@DisplayName("없는 장소 번호는 실패하지 않고 센다")
	void aMissingPlaceIsCounted() {
		PlaceDetailExtrasLoader.Result result = this.loader.saveChunk(sixNewTypes(UUID.randomUUID()),
				OffsetDateTime.now());

		assertThat(result).isEqualTo(new PlaceDetailExtrasLoader.Result(0, 0, 6));
	}

	@Test
	@DisplayName("🔴 넣은 메뉴가 장소 상세의 features 에 그대로 실린다 — 새 칸 없이")
	void menuItemsAppearInDetailFeatures() {
		this.loader.saveChunk(sixNewTypes(this.placeId), OffsetDateTime.now());

		PlaceDetailResponse detail = this.placeDetailService.get(this.placeId, null);

		assertThat(detail.features())
				.filteredOn(view -> "MENU_ITEMS".equals(view.featureType()))
				.singleElement()
				.satisfies(view -> {
					assertThat(view.evidenceStatus()).isEqualTo("VERIFIED");
					assertThat(view.value().get("items").get(0).get("nameEn").asString()).isEqualTo("Milmyeon");
				});
		assertThat(detail.features()).extracting(view -> view.featureType())
				.contains("FOREIGN_MENU", "AMENITIES", "ADMISSION_FEE", "NEARBY_LANDMARK", "BEST_TIME");
	}
}
