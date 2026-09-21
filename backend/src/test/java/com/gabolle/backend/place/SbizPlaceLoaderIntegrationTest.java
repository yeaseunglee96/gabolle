package com.gabolle.backend.place;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.gabolle.backend.place.loader.SbizCsvReader;
import com.gabolle.backend.place.loader.SbizPlaceLoader;
import com.gabolle.backend.place.loader.SbizRow;
import com.gabolle.backend.place.support.PlacePostgresIntegrationTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 상가정보 CSV 가 {@code place}·{@code place_feature} 행이 되는지 잰다. 재는 것은 셋이다 —
 * 무엇이 들어가나(음식만·좌표 있는 것만), 무엇이 안 들어가나(근거 없는 피처),
 * 두 번 돌리면 어떻게 되나(안 늘어난다).
 */
class SbizPlaceLoaderIntegrationTest extends PlacePostgresIntegrationTest {

	private static final String DATASET = "sbiz-test-202606";

	/** 상가정보 CSV 의 칸 수(0부터 세어 38번이 위도). 실제 판과 같은 폭으로 만든다. */
	private static final int COLUMNS = 39;

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private SbizPlaceLoader loader;

	@TempDir
	Path tempDir;

	@BeforeEach
	@AfterEach
	void cleanUp() {
		this.jdbcTemplate.update("DELETE FROM place_feature WHERE source_type = 'SBIZ'");
		this.jdbcTemplate.update("DELETE FROM place WHERE source_type = 'SBIZ'");
	}

	@Test
	@DisplayName("음식 대분류만, 좌표가 있는 것만 들어간다")
	void 음식이고_좌표가_있는_행만_들어간다() {
		Path csv = csv(
				row("MA001", "돼지국밥집", "", "음식", "백반/한정식", "부산광역시 부산진구 중앙대로 1", "129.05", "35.15"),
				row("MA002", "밀면집", "서면점", "음식", "냉면/밀면", "부산광역시 부산진구 중앙대로 2", "129.06", "35.16"),
				// 숙박이라 후보가 아니다
				row("MA003", "어느 호텔", "", "숙박", "호텔/리조트", "부산광역시 해운대구", "129.16", "35.16"),
				// 좌표가 0 이다 — 0,0 은 좌표가 아니라 빈 칸이다
				row("MA004", "좌표없는집", "", "음식", "일식 회/초밥", "부산광역시 중구", "0", "0"));

		SbizCsvReader.Counts counts = load(csv);

		assertThat(counts.food()).isEqualTo(2);
		assertThat(counts.notFood()).isEqualTo(1);
		assertThat(counts.noCoordinate()).isEqualTo(1);
		assertThat(placeNames()).containsExactlyInAnyOrder("돼지국밥집", "밀면집 서면점");
	}

	@Test
	@DisplayName("🔴 음식 태그는 앱이 보내는 코드다 — 서버 낱말(한식)이 아니라 PORK_SOUP 이다")
	void 피처는_둘뿐이고_추정으로_들어간다() {
		load(csv(row("MA001", "돼지국밥집", "", "음식", "백반/한정식", "부산광역시 부산진구 중앙대로 1", "129.05", "35.15")));

		List<Map<String, Object>> features = this.jdbcTemplate.queryForList("""
				SELECT feature_type, feature_key, evidence_status, source_version
				FROM place_feature WHERE source_type = 'SBIZ' ORDER BY feature_type
				""");

		assertThat(features).hasSize(2);
		assertThat(features).extracting(f -> f.get("feature_type") + ":" + f.get("feature_key"))
				// 채점기가 앱이 보낸 코드와 이 값을 글자 그대로 비교한다 — 서버 낱말이면 한 건도 안 맞는다.
				.containsExactly("CATEGORY_TAG:FOOD", "CUISINE_TAG:PORK_SOUP");
		assertThat(features).allSatisfy(f -> {
			// 업종 칸에서 옮긴 것이지 가게에 직접 확인한 것이 아니다.
			assertThat(f.get("evidence_status")).isEqualTo("ESTIMATED");
			assertThat(f.get("source_version")).isEqualTo(DATASET);
		});
	}

	@Test
	// containsExactly 는 순서까지 본다 — 갈래 이름이 바뀌면 가나다 정렬 순서도 달라진다.
	@DisplayName("카페는 관심 태그가 둘이다 — FOOD 하나뿐이면 순서를 못 바꾼다")
	void 카페는_CAFE_HEALING_도_붙는다() {
		load(csv(row("MA010", "어느 커피", "", "음식", "카페", "부산광역시 해운대구 구남로 1", "129.16", "35.16")));

		List<String> keys = this.jdbcTemplate.queryForList("""
				SELECT feature_type || ':' || feature_key FROM place_feature
				WHERE source_type = 'SBIZ' ORDER BY feature_type, feature_key
				""", String.class);

		// CATEGORY_TAG:FOOD 하나만 붙으면 후보가 전부 똑같이 맞아 겹침 비율이 다 같아진다.
		assertThat(keys).containsExactly(
				"CATEGORY_TAG:CAFE_HEALING", "CATEGORY_TAG:FOOD", "CUISINE_TAG:CAFE_DESSERT");
	}

	@Test
	@DisplayName("🔴 카페의 place.category 는 CAFE_HEALING 이다 — FOOD 로 두면 음식점 후보에 카페가 섞인다 (S15P21E201-106)")
	void 카페의_카테고리는_CAFE_HEALING_이다() {
		load(csv(row("MA010", "어느 커피", "", "음식", "카페", "부산광역시 해운대구 구남로 1", "129.16", "35.16")));

		// 후보 필터(PlaceCandidateQueryService)는 place.category 한 칸만 본다 —
		// place_feature 에 CATEGORY_TAG:FOOD 가 남아 있어도 이 칸이 갈래를 정한다.
		List<String> categories = this.jdbcTemplate.queryForList(
				"SELECT category FROM place WHERE source_type = 'SBIZ'", String.class);

		assertThat(categories).containsExactly("CAFE_HEALING");
	}

	@Test
	@DisplayName("🔴 가를 수 없는 업종에는 음식 태그를 안 붙인다 — 억지로 분류하지 않는다")
	void 애매하면_안_붙인다() {
		load(csv(row("MA011", "어느 백반집", "", "음식", "백반/한정식", "부산광역시 중구 광복로 1", "129.03", "35.10")));

		List<String> keys = this.jdbcTemplate.queryForList(
				"SELECT feature_type || ':' || feature_key FROM place_feature WHERE source_type = 'SBIZ'",
				String.class);

		// 「백반/한정식」에는 서로 다른 음식이 섞여 있어 통째로 붙이면 대부분이 오답이 된다.
		assertThat(keys).containsExactly("CATEGORY_TAG:FOOD");
	}

	@Test
	@DisplayName("🔴 근거가 없는 피처는 아예 안 넣는다 — 인기 점수를 지어내지 않는다")
	void 근거가_없는_피처는_안_넣는다() {
		load(csv(row("MA001", "돼지국밥집", "", "음식", "백반/한정식", "부산광역시 부산진구 중앙대로 1", "129.05", "35.15")));

		List<String> types = this.jdbcTemplate.queryForList(
				"SELECT DISTINCT feature_type FROM place_feature WHERE source_type = 'SBIZ'", String.class);

		// 아래 축들은 상가정보 CSV 에 자료가 없다.
		assertThat(types).doesNotContain("POPULARITY_SCORE", "LOCALITY_SCORE", "QUIETNESS_SCORE",
				"TOURIST_RATIO", "SHADE_SCORE", "SLOPE_PERCENT", "STAIRS_PRESENT",
				"ALLERGEN_TAG", "DIETARY_SUPPORT_TAG", "ACCESSIBILITY_TAG", "ATMOSPHERE_TAG");
	}

	@Test
	@DisplayName("🔴 같은 파일을 두 번 돌려도 행이 두 배가 되지 않는다")
	void 두_번_돌려도_안_늘어난다() {
		Path csv = csv(
				row("MA001", "돼지국밥집", "", "음식", "백반/한정식", "부산광역시 부산진구 중앙대로 1", "129.05", "35.15"),
				row("MA002", "밀면집", "", "음식", "냉면/밀면", "부산광역시 부산진구 중앙대로 2", "129.06", "35.16"));

		load(csv);
		long afterFirst = placeCount();
		load(csv);

		assertThat(afterFirst).isEqualTo(2);
		assertThat(placeCount()).isEqualTo(2);
		assertThat(featureCount()).isEqualTo(4);
	}

	@Test
	@DisplayName("카테고리는 앱이 보내는 코드 FOOD 다 — 그래야 카테고리 필터에 걸린다")
	void 카테고리는_앱_코드다() {
		load(csv(row("MA001", "돼지국밥집", "", "음식", "백반/한정식", "부산광역시 부산진구 중앙대로 1", "129.05", "35.15")));

		List<String> categories = this.jdbcTemplate.queryForList(
				"SELECT category FROM place WHERE source_type = 'SBIZ'", String.class);

		assertThat(categories).containsExactly("FOOD");
	}

	@Test
	@DisplayName("🔴 칸 수가 아는 판과 다르면 한 행도 안 넣고 멈춘다")
	void 판이_다르면_멈춘다() {
		Path csv = write("상가업소번호,상호명\nMA001,어느집\n");

		assertThatThrownBy(() -> load(csv))
				.isInstanceOf(IllegalArgumentException.class)
				.hasMessageContaining("칸 수");
		assertThat(placeCount()).isZero();
	}

	// 도구

	private SbizCsvReader.Counts load(Path csv) {
		OffsetDateTime collectedAt = OffsetDateTime.now();
		return SbizCsvReader.readFoodRows(csv, 100,
				(List<SbizRow> chunk) -> this.loader.saveChunk(chunk, DATASET, collectedAt));
	}

	private long placeCount() {
		return this.jdbcTemplate.queryForObject(
				"SELECT count(*) FROM place WHERE source_type = 'SBIZ'", Long.class);
	}

	private long featureCount() {
		return this.jdbcTemplate.queryForObject(
				"SELECT count(*) FROM place_feature WHERE source_type = 'SBIZ'", Long.class);
	}

	private List<String> placeNames() {
		return this.jdbcTemplate.queryForList(
				"SELECT name_ko FROM place WHERE source_type = 'SBIZ'", String.class);
	}

	/**
	 * 실제 판과 같은 39칸짜리 줄을 만든다 — 우리가 읽는 칸에만 값을 넣는다.
	 * {@code so} 는 상권업종 소분류명이고, 중분류(칸 6)에는 일부러 다른 값을 넣어 둔다 —
	 * 적재가 그 칸을 다시 읽기 시작하면 이 테스트가 깨져야 한다.
	 */
	private static String row(String storeId, String name, String branch, String dae, String so,
			String roadAddress, String lng, String lat) {
		String[] cols = new String[COLUMNS];
		java.util.Arrays.fill(cols, "");
		cols[0] = storeId;
		cols[1] = name;
		cols[2] = branch;
		cols[4] = dae;
		cols[6] = "중분류는_이제_안_읽는다";
		cols[8] = so;
		cols[24] = roadAddress + " (지번)";
		cols[31] = roadAddress;
		cols[37] = lng;
		cols[38] = lat;
		return String.join(",", cols);
	}

	private Path csv(String... rows) {
		String[] header = new String[COLUMNS];
		java.util.Arrays.fill(header, "칸");
		List<String> lines = new ArrayList<>();
		lines.add(String.join(",", header));
		lines.addAll(List.of(rows));
		return write(String.join("\n", lines) + "\n");
	}

	private Path write(String content) {
		try {
			Path path = this.tempDir.resolve("sbiz-" + System.nanoTime() + ".csv");
			Files.writeString(path, content, StandardCharsets.UTF_8);
			return path;
		}
		catch (IOException exception) {
			throw new UncheckedIOException(exception);
		}
	}
}
