package com.gabolle.backend.place;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.gabolle.backend.place.loader.SbizPlaceLoader;
import com.gabolle.backend.place.loader.SbizRow;
import com.gabolle.backend.place.support.PlacePostgresIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 세미콜론 이름 — 마이그레이션과 상가 적재기 (S15P21E201-1637).
 *
 * <p>시험 DB 에는 운영의 다섯 곳이 없어 Flyway 가 돈 것만으로는 0건을 고친다. 같은 모양으로 심고 <b>같은 파일의 SQL 을
 * 읽어</b> 돌린다. 합쳐진 점은 운영과 같은 번호로 심는다 — 파일이 그 번호를 숨긴다.
 */
class SemicolonNameMigrationTest extends PlacePostgresIntegrationTest {

	private static final Path MIGRATION = Path.of("src/main/resources/db/migration/V20260925080000__semicolon_names.sql");

	private static final UUID MERGED_POINT = UUID.fromString("1f94aa60-8c2a-3f5f-8aef-2cd025fcc882");

	@Autowired
	private JdbcTemplate jdbc;

	@Autowired
	private SbizPlaceLoader sbizLoader;

	private final List<UUID> seeded = new ArrayList<>();

	@AfterEach
	void cleanUp() {
		this.seeded.forEach(id -> this.jdbc.update("DELETE FROM place WHERE place_id = ?", id));
		// 상가 적재기는 표식 행도 같이 넣는다 — 표식부터 지운다.
		this.jdbc.update("DELETE FROM place_feature WHERE place_id IN "
				+ "(SELECT place_id FROM place WHERE source_type = 'SBIZ' AND source_id LIKE 't-1637-%')");
		this.jdbc.update("DELETE FROM place WHERE source_type = 'SBIZ' AND source_id LIKE 't-1637-%'");
		this.seeded.clear();
	}

	@Test
	@DisplayName("🔴 이름은 한 이름만 — 앞 이름, 한 글자 머리말이면 뒤 이름 · 두 가게가 합쳐진 점은 숨긴다")
	void namesBecomeSingleAndTheMergedPointIsHidden() throws Exception {
		UUID nanari = place(UUID.randomUUID(), "남나리전복;엠아이알오");
		UUID gyeongpo = place(UUID.randomUUID(), "구;경포횟집");
		UUID motel = place(UUID.randomUUID(), "선모텔;코리아나모텔");
		UUID plain = place(UUID.randomUUID(), "해운대해수욕장 시험");
		place(MERGED_POINT, "삼구유통광장마트;대성당");

		this.jdbc.execute(Files.readString(MIGRATION));

		assertThat(nameOf(nanari)).isEqualTo("남나리전복");
		assertThat(nameOf(gyeongpo)).isEqualTo("경포횟집");
		assertThat(nameOf(motel)).isEqualTo("선모텔");
		assertThat(nameOf(plain)).isEqualTo("해운대해수욕장 시험");
		assertThat(this.jdbc.queryForObject("SELECT curation_status FROM place WHERE place_id = ?", String.class,
				MERGED_POINT)).isEqualTo("HIDDEN");
		assertThat(nameOf(MERGED_POINT)).as("주인을 모르니 이름은 그대로 둔다").isEqualTo("삼구유통광장마트;대성당");
	}

	@Test
	@DisplayName("🔴 상가 적재기도 같은 규칙이다 — 다음 적재 때 다시 생기지 않는다")
	void theSbizLoaderKeepsOneName() {
		this.sbizLoader.saveChunk(List.of(
				new SbizRow("t-1637-1", "구;경포횟집", "", "횟집", "부산광역시 해운대구 어딘가 1", 35.16, 129.16)),
				"test-1637", OffsetDateTime.now());

		assertThat(this.jdbc.queryForObject(
				"SELECT name_ko FROM place WHERE source_type = 'SBIZ' AND source_id = 't-1637-1'", String.class))
				.isEqualTo("경포횟집");
	}

	private UUID place(UUID id, String name) {
		this.jdbc.update("""
				INSERT INTO place (place_id, name_ko, category, lat, lng, source_type, source_id, created_at)
				VALUES (?, ?, 'FOOD', 35.16, 129.16, 'TEST', ?, now())
				""", id, name, "t-1637-" + id);
		this.seeded.add(id);
		return id;
	}

	private String nameOf(UUID id) {
		return this.jdbc.queryForObject("SELECT name_ko FROM place WHERE place_id = ?", String.class, id);
	}
}
