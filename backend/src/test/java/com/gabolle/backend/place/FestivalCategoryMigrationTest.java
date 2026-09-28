package com.gabolle.backend.place;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.gabolle.backend.place.support.PlacePostgresIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 축제 갈래 마이그레이션이 채울 것만 채우는가 (S15P21E201-1618).
 *
 * <p>🔴 시험 DB 에는 갈래가 빈 축제가 없어 Flyway 가 돈 것만으로는 0건을 고친다. 그래서 행을 직접 심고 <b>같은 파일의
 * SQL 을 읽어</b> 돌린다 — SQL 을 여기 다시 적으면 파일을 고쳐도 이 시험이 옛 SQL 을 계속 통과시킨다.
 */
class FestivalCategoryMigrationTest extends PlacePostgresIntegrationTest {

	private static final Path MIGRATION =
			Path.of("src/main/resources/db/migration/V20260925040000__festival_category.sql");

	@Autowired
	private JdbcTemplate jdbc;

	private final List<UUID> seeded = new ArrayList<>();

	@AfterEach
	void cleanUp() {
		// 같은 DB 를 다른 시험 클래스도 쓴다. 심은 것만 지운다(표식·기간표는 장소를 지우면 같이 지워진다).
		this.seeded.forEach((placeId) -> {
			this.jdbc.update("DELETE FROM place_feature WHERE place_id = ?", placeId);
			this.jdbc.update("DELETE FROM place WHERE place_id = ?", placeId);
		});
		this.seeded.clear();
	}

	@Test
	@DisplayName("사전에 FESTIVAL_EVENT 「축제·행사」가 있다 — 취향 답이 이 낱말을 받으려면 여기 있어야 한다")
	void theDictionaryHasFestival() {
		assertThat(this.jdbc.queryForObject(
				"SELECT label_ko FROM place_feature_code WHERE feature_type = 'CATEGORY_TAG' AND feature_key = 'FESTIVAL_EVENT'",
				String.class)).isEqualTo("축제·행사");
	}

	@Test
	@DisplayName("🔴 기간표가 있는 빈 갈래 장소는 FESTIVAL_EVENT 가 되고 갈래 표식도 붙는다")
	void anEventWithoutCategoryBecomesFestival() {
		UUID festival = place("부산불꽃축제", null, "TOURAPI", "t-1618-festival");
		period(festival);

		runMigration();

		assertThat(categoryOf(festival)).isEqualTo("FESTIVAL_EVENT");
		assertThat(categoryTagsOf(festival)).containsExactly("FESTIVAL_EVENT");
	}

	@Test
	@DisplayName("기간표가 없는 빈 갈래 장소(레포츠 등)는 그대로 비워 둔다 — 사용자 결정")
	void aPlaceWithoutPeriodsStaysEmpty() {
		UUID camping = place("대저캠핑장", null, "TOURAPI", "t-1618-camping");

		runMigration();

		assertThat(categoryOf(camping)).isNull();
		assertThat(categoryTagsOf(camping)).isEmpty();
	}

	@Test
	@DisplayName("🔴 갈래가 이미 있는 장소는 기간표가 있어도 안 건드린다 — 모름 → 앎 한 방향")
	void anExistingCategoryIsKept() {
		UUID market = place("야시장", "CITY", "TOURAPI", "t-1618-market");
		period(market);

		runMigration();

		assertThat(categoryOf(market)).isEqualTo("CITY");
		assertThat(categoryTagsOf(market)).isEmpty();
	}

	@Test
	@DisplayName("카카오 숙소 두 곳은 LODGING — 원천(카카오)의 분류가 숙박이다")
	void theTwoKakaoHotelsBecomeLodging() {
		UUID paradise = place("파라다이스호텔부산", null, "KAKAO_LOCAL", "8625845");

		runMigration();

		assertThat(categoryOf(paradise)).isEqualTo("LODGING");
		assertThat(categoryTagsOf(paradise)).containsExactly("LODGING");
	}

	@Test
	@DisplayName("두 번 돌려도 갈래 표식이 두 줄이 되지 않는다")
	void runningTwiceAddsNothing() {
		UUID festival = place("부산항축제", null, "TOURAPI", "t-1618-twice");
		period(festival);

		runMigration();
		runMigration();

		assertThat(categoryTagsOf(festival)).containsExactly("FESTIVAL_EVENT");
	}

	private UUID place(String name, String category, String sourceType, String sourceId) {
		UUID placeId = UUID.randomUUID();
		this.jdbc.update("""
				INSERT INTO place (place_id, name_ko, category, lat, lng, source_type, source_id, created_at)
				VALUES (?, ?, ?, 35.15, 129.11, ?, ?, now())
				""", placeId, name, category, sourceType, sourceId);
		this.seeded.add(placeId);
		return placeId;
	}

	private void period(UUID placeId) {
		this.jdbc.update("""
				INSERT INTO place_event_period (place_event_period_id, place_id, start_date, end_date)
				VALUES (?, ?, ?, ?)
				""", UUID.randomUUID(), placeId, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 3));
	}

	private String categoryOf(UUID placeId) {
		return this.jdbc.queryForObject("SELECT category FROM place WHERE place_id = ?", String.class, placeId);
	}

	private List<String> categoryTagsOf(UUID placeId) {
		return this.jdbc.queryForList(
				"SELECT feature_key FROM place_feature WHERE place_id = ? AND feature_type = 'CATEGORY_TAG'",
				String.class, placeId);
	}

	private void runMigration() {
		String text;
		try {
			text = Files.readString(MIGRATION, StandardCharsets.UTF_8);
		}
		catch (IOException ex) {
			throw new UncheckedIOException("마이그레이션 파일을 못 읽었다 — 이름이 바뀌었나: " + MIGRATION, ex);
		}
		String withoutComments = text.lines()
				.map((line) -> line.replaceFirst("--.*$", ""))
				.reduce("", (a, b) -> a + "\n" + b);
		List<String> statements = Arrays.stream(withoutComments.split(";"))
				.map(String::trim)
				.filter((s) -> !s.isEmpty())
				.toList();
		assertThat(statements).as("마이그레이션에서 실행할 문장을 하나도 못 찾았다").isNotEmpty();
		statements.forEach(this.jdbc::execute);
	}
}
