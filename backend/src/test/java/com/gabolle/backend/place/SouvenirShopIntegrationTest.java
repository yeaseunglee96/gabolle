package com.gabolle.backend.place;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.gabolle.backend.place.loader.PlaceFeatureLoader;
import com.gabolle.backend.place.loader.PlaceFeatureNdjsonReader;
import com.gabolle.backend.place.loader.SouvenirShopLoaderRunner;
import com.gabolle.backend.place.loader.TourApiPlaceLoader;
import com.gabolle.backend.place.loader.TourApiPlaceRow;
import com.gabolle.backend.place.support.PlacePostgresIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SouvenirShopLoaderRunner}가 실제로 장소와 표식을 만드는지 잰다 — S15P21E201-471.
 *
 * <p>러너 자체는 {@code gabolle.place.loader.souvenir-shops=true} 프로퍼티로만 켜지는
 * {@link org.springframework.boot.ApplicationRunner}라 단위 호출이 어렵다 — 그래서
 * {@link TourApiPlaceLoader}·{@link PlaceFeatureLoader}로 러너와 같은 순서를 그대로
 * 재현한다({@code PlaceFeatureLoaderIntegrationTest}의 관행과 같다).
 */
class SouvenirShopIntegrationTest extends PlacePostgresIntegrationTest {

	private static final String DATASET = "staged-test-202609";
	private static final String CONTENT_ID = "1013461";

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private TourApiPlaceLoader placeLoader;

	@Autowired
	private PlaceFeatureLoader featureLoader;

	@BeforeEach
	@AfterEach
	void cleanUp() {
		// 🔴 TourApiPlaceLoader.saveChunk 가 장소를 만들면서 CATEGORY_TAG 를
		// source_type=TOURAPI 로 같이 넣는다 — place 를 지우기 전에 그것부터 지워야
		// fk_place_feature_place 위반이 안 난다 (2026-09-16 CI 실측).
		this.jdbcTemplate.update("DELETE FROM place_feature WHERE source_type = 'TOURAPI'");
		this.jdbcTemplate.update("DELETE FROM place_feature WHERE source_type = '" + SouvenirShopLoaderRunner.SOURCE_TYPE + "'");
		this.jdbcTemplate.update("DELETE FROM place WHERE source_id = '" + CONTENT_ID + "'");
	}

	@Test
	@DisplayName("아이하시가 장소로 들어가고 SOUVENIR_SHOP·HANDMADE_CHOPSTICKS 표식이 붙는다")
	void 아이하시가_들어간다() {
		OffsetDateTime now = OffsetDateTime.now();
		TourApiPlaceRow row = new TourApiPlaceRow(CONTENT_ID, "38", "A04", "A04010700",
				"아이하시 (수제젓가락공예)", "부산광역시 중구 국제시장2길 33 (신창동4가)", 35.1024004807, 129.0283125960, null, null);

		int placesInserted = this.placeLoader.saveChunk(List.of(row), DATASET, now);
		assertThat(placesInserted).isEqualTo(1);

		List<PlaceFeatureNdjsonReader.Fact> facts = List.of(
				new PlaceFeatureNdjsonReader.Fact(CONTENT_ID, "CATEGORY_TAG", "true", "TOURAPI", "SOUVENIR_SHOP"),
				new PlaceFeatureNdjsonReader.Fact(CONTENT_ID, "SOUVENIR_ITEM_TAG", "true", "TOURAPI",
						"HANDMADE_CHOPSTICKS"));
		PlaceFeatureLoader.Saved saved = this.featureLoader.saveChunk(facts, SouvenirShopLoaderRunner.SOURCE_TYPE,
				DATASET, now);

		assertThat(saved.inserted()).isEqualTo(2);
		assertThat(saved.missingPlace()).isZero();

		List<Map<String, Object>> rows = this.jdbcTemplate.queryForList("""
				SELECT feature_type, feature_key
				FROM place_feature WHERE source_type = ?
				""", SouvenirShopLoaderRunner.SOURCE_TYPE);
		Set<String> keys = rows.stream().map(r -> r.get("feature_type") + ":" + r.get("feature_key"))
				.collect(java.util.stream.Collectors.toSet());
		assertThat(keys).containsExactlyInAnyOrder("CATEGORY_TAG:SOUVENIR_SHOP", "SOUVENIR_ITEM_TAG:HANDMADE_CHOPSTICKS");
	}
}
