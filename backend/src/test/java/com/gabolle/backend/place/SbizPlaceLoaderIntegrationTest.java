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
 * 🔴 <b>장소를 채우는 코드가 저장소 어디에도 없었다</b> — S15P21E201-636.
 *
 * <p>표도 제약도 대조표도 다 있는데 {@code place} · {@code place_feature} 에 행을 넣는 운영
 * 코드가 하나도 없어서, 추천 요청이 전부 후보 0건으로 끝났다. 유일한 INSERT 는 테스트
 * 픽스처였다.
 *
 * <p>여기서 재는 것은 세 가지다 — <b>무엇이 들어가나</b>(음식만·좌표 있는 것만),
 * <b>무엇이 안 들어가나</b>(근거 없는 피처), <b>두 번 돌리면 어떻게 되나</b>(안 늘어난다).
 * 마지막 것이 특히 중요하다. 같은 가게가 두 행이 되면 후보 수가 부풀어 백분위가 좋아
 * <b>보이고</b>, 정답이 한 행에만 붙어 나머지가 오답으로 학습된다.
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
				row("MA001", "돼지국밥집", "", "음식", "한식", "부산광역시 부산진구 중앙대로 1", "129.05", "35.15"),
				row("MA002", "밀면집", "서면점", "음식", "한식", "부산광역시 부산진구 중앙대로 2", "129.06", "35.16"),
				// 🔴 숙박이라 후보가 아니다
				row("MA003", "어느 호텔", "", "숙박", "호텔/리조트", "부산광역시 해운대구", "129.16", "35.16"),
				// 🔴 좌표가 0 이다 — 0,0 은 좌표가 아니라 빈 칸이다
				row("MA004", "좌표없는집", "", "음식", "일식", "부산광역시 중구", "0", "0"));

		SbizCsvReader.Counts counts = load(csv);

		assertThat(counts.food()).isEqualTo(2);
		assertThat(counts.notFood()).isEqualTo(1);
		assertThat(counts.noCoordinate()).isEqualTo(1);
		assertThat(placeNames()).containsExactlyInAnyOrder("돼지국밥집", "밀면집 서면점");
	}

	@Test
	@DisplayName("피처는 관심 태그 FOOD 와 음식 태그(중분류) 둘뿐이고 둘 다 ESTIMATED 다")
	void 피처는_둘뿐이고_추정으로_들어간다() {
		load(csv(row("MA001", "돼지국밥집", "", "음식", "한식", "부산광역시 부산진구 중앙대로 1", "129.05", "35.15")));

		List<Map<String, Object>> features = this.jdbcTemplate.queryForList("""
				SELECT feature_type, feature_key, evidence_status, source_version
				FROM place_feature WHERE source_type = 'SBIZ' ORDER BY feature_type
				""");

		assertThat(features).hasSize(2);
		assertThat(features).extracting(f -> f.get("feature_type") + ":" + f.get("feature_key"))
				.containsExactly("CUISINE_TAG:한식", "INTEREST_TAG:FOOD");
		assertThat(features).allSatisfy(f -> {
			// 🔴 업종 칸에서 옮긴 것이지 가게에 직접 확인한 것이 아니다.
			assertThat(f.get("evidence_status")).isEqualTo("ESTIMATED");
			assertThat(f.get("source_version")).isEqualTo(DATASET);
		});
	}

	@Test
	@DisplayName("🔴 근거가 없는 피처는 아예 안 넣는다 — 인기 점수를 지어내지 않는다")
	void 근거가_없는_피처는_안_넣는다() {
		load(csv(row("MA001", "돼지국밥집", "", "음식", "한식", "부산광역시 부산진구 중앙대로 1", "129.05", "35.15")));

		List<String> types = this.jdbcTemplate.queryForList(
				"SELECT DISTINCT feature_type FROM place_feature WHERE source_type = 'SBIZ'", String.class);

		// 인기·로컬성·조용함·관광객비율·그늘·경사, 그리고 안전 셋 — 어느 것도 자료가 없다.
		assertThat(types).doesNotContain("POPULARITY_SCORE", "LOCALITY_SCORE", "QUIETNESS_SCORE",
				"TOURIST_RATIO", "SHADE_SCORE", "SLOPE_PERCENT", "STAIRS_PRESENT",
				"ALLERGEN_TAG", "DIETARY_SUPPORT_TAG", "ACCESSIBILITY_TAG", "ATMOSPHERE_TAG");
	}

	@Test
	@DisplayName("🔴 같은 파일을 두 번 돌려도 행이 두 배가 되지 않는다")
	void 두_번_돌려도_안_늘어난다() {
		Path csv = csv(
				row("MA001", "돼지국밥집", "", "음식", "한식", "부산광역시 부산진구 중앙대로 1", "129.05", "35.15"),
				row("MA002", "밀면집", "", "음식", "한식", "부산광역시 부산진구 중앙대로 2", "129.06", "35.16"));

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
		load(csv(row("MA001", "돼지국밥집", "", "음식", "한식", "부산광역시 부산진구 중앙대로 1", "129.05", "35.15")));

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

	// ── 도구 ──────────────────────────────────────────────────────────────────

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

	/** 실제 판과 같은 39칸짜리 줄을 만든다 — 우리가 읽는 칸에만 값을 넣는다. */
	private static String row(String storeId, String name, String branch, String dae, String jung,
			String roadAddress, String lng, String lat) {
		String[] cols = new String[COLUMNS];
		java.util.Arrays.fill(cols, "");
		cols[0] = storeId;
		cols[1] = name;
		cols[2] = branch;
		cols[4] = dae;
		cols[6] = jung;
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
