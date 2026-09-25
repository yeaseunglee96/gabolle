package com.gabolle.backend.place;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.gabolle.backend.place.service.PlaceDessertOnlyPort;
import com.gabolle.backend.place.support.PlacePostgresIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 디저트 가게는 카페다 — 마이그레이션과 조립이 쓰는 창구 (S15P21E201-1635).
 *
 * <p>🔴 시험 DB 에는 디저트 가게가 없어 Flyway 가 돈 것만으로는 0건을 고친다. 그래서 행을 직접 심고 <b>같은 파일의 SQL 을
 * 읽어</b> 돌린다 — SQL 을 여기 다시 적으면 파일을 고쳐도 이 시험이 옛 SQL 을 계속 통과시킨다.
 */
class DessertCafeMigrationTest extends PlacePostgresIntegrationTest {

	private static final Path MIGRATION =
			Path.of("src/main/resources/db/migration/V20260925060000__dessert_shops_are_cafes.sql");

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private PlaceDessertOnlyPort dessertOnly;

	private final List<UUID> seeded = new ArrayList<>();

	@AfterEach
	void cleanUp() {
		this.seeded.forEach((placeId) -> {
			this.jdbc.update("DELETE FROM place_feature WHERE place_id = ?", placeId);
			this.jdbc.update("DELETE FROM place WHERE place_id = ?", placeId);
		});
		this.seeded.clear();
	}

	@Test
	@DisplayName("🔴 음식 종류가 디저트뿐인 밥집은 갈래와 갈래 표식이 카페가 된다 — 「점심으로 젤라또」")
	void dessertOnlyFoodBecomesCafe() {
		UUID gelato = place("젤라또부 시험", "FOOD", "CAFE_DESSERT");

		runMigration();

		assertThat(categoryOf(gelato)).isEqualTo("CAFE_HEALING");
		assertThat(categoryTagsOf(gelato)).containsExactly("CAFE_HEALING");
	}

	@Test
	@DisplayName("디저트도 파는 식당(음식 종류가 섞임)과 보통 식당은 그대로다")
	void mixedAndPlainRestaurantsStay() {
		UUID mixed = place("횟집 겸 빙수 시험", "FOOD", "CAFE_DESSERT", "SEAFOOD");
		UUID plain = place("횟집 시험", "FOOD", "SEAFOOD");

		runMigration();

		assertThat(categoryOf(mixed)).isEqualTo("FOOD");
		assertThat(categoryOf(plain)).isEqualTo("FOOD");
		assertThat(categoryTagsOf(mixed)).containsExactly("FOOD");
	}

	@Test
	@DisplayName("🔴 조립이 쓰는 창구도 같은 규칙이다 — 다음 적재 때 다시 밥집으로 들어와도 끼니로 안 센다")
	void thePortAnswersTheSameRule() {
		UUID gelato = place("젤라또조이 시험", "FOOD", "CAFE_DESSERT");
		UUID mixed = place("횟집 겸 빙수 시험", "FOOD", "CAFE_DESSERT", "SEAFOOD");
		UUID untagged = place("표식 없는 식당 시험", "FOOD");

		Set<UUID> answer = this.dessertOnly.dessertOnly(List.of(gelato, mixed, untagged));

		assertThat(answer).containsExactly(gelato);
	}

	private void runMigration() {
		try {
			this.jdbc.execute("BEGIN; " + Files.readString(MIGRATION) + " COMMIT;");
		}
		catch (java.io.IOException ex) {
			throw new AssertionError(ex);
		}
	}

	private UUID place(String name, String category, String... cuisines) {
		UUID id = UUID.randomUUID();
		this.jdbc.update("""
				INSERT INTO place (place_id, name_ko, category, lat, lng, source_type, source_id, created_at)
				VALUES (?, ?, ?, 35.16, 129.16, 'SBIZ', ?, now())
				""", id, name, category, "t-1635-" + id);
		this.seeded.add(id);
		tag(id, "CATEGORY_TAG", category);
		for (String cuisine : cuisines) {
			tag(id, "CUISINE_TAG", cuisine);
		}
		return id;
	}

	private void tag(UUID placeId, String type, String key) {
		this.jdbc.update("""
				INSERT INTO place_feature (place_feature_id, place_id, feature_type, feature_key, value, evidence_status,
				                           source_type, created_at)
				VALUES (?, ?, ?, ?, 'true'::jsonb, 'ESTIMATED', 'TEST', now())
				""", UUID.randomUUID(), placeId, type, key);
	}

	private String categoryOf(UUID placeId) {
		return this.jdbc.queryForObject("SELECT category FROM place WHERE place_id = ?", String.class, placeId);
	}

	private List<String> categoryTagsOf(UUID placeId) {
		return this.jdbc.queryForList(
				"SELECT feature_key FROM place_feature WHERE place_id = ? AND feature_type = 'CATEGORY_TAG'",
				String.class, placeId);
	}
}
