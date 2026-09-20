package com.gabolle.backend.place;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.gabolle.backend.place.loader.PopularityScoreLoader;
import com.gabolle.backend.place.loader.ListRarity;
import com.gabolle.backend.place.loader.SbizPlaceLoader;
import com.gabolle.backend.place.loader.SbizRow;
import com.gabolle.backend.place.loader.TruthSignalReader;
import com.gabolle.backend.place.support.PlacePostgresIntegrationTest;

/**
 * 목록 근거가 인기도 점수 행이 되는지 잰다. 표본은 실제 파일에서 뜬 59줄이다
 * ({@code TruthSignalReaderTest} 와 같은 파일).
 */
class PopularityScoreLoaderIntegrationTest extends PlacePostgresIntegrationTest {

	private static final String DATASET = "truth-busan-test";

	private static final String SAMPLE = "/research/truth-sample.ndjson";

	/** 표본에 실제로 들어 있는 가게. 목록 하나에 오르고 글이 두 번 지목했다. */
	private static final String LISTED_STORE = "MA010120220811437741";

	@Autowired
	private JdbcTemplate jdbcTemplate;

	@Autowired
	private SbizPlaceLoader placeLoader;

	@Autowired
	private PopularityScoreLoader popularityLoader;

	@TempDir
	Path tempDir;

	@BeforeEach
	@AfterEach
	void cleanUp() {
		this.jdbcTemplate.update("DELETE FROM place_feature WHERE source_type IN ('SBIZ', 'TRUTH_LIST')");
		this.jdbcTemplate.update("DELETE FROM place WHERE source_type = 'SBIZ'");
	}

	private Path sampleFile() {
		try (InputStream in = getClass().getResourceAsStream(SAMPLE)) {
			assertThat(in).as("표본 파일이 없다: %s", SAMPLE).isNotNull();
			Path file = this.tempDir.resolve("truth-sample.ndjson");
			Files.write(file, in.readAllBytes());
			return file;
		}
		catch (IOException exception) {
			throw new UncheckedIOException(exception);
		}
	}

	/** 표본에 있는 가게들을 장소로 먼저 넣는다. 점수는 장소에 붙는 값이라 장소가 먼저다. */
	private void loadPlaces() {
		List<SbizRow> rows = List.of(
				new SbizRow(LISTED_STORE, "덩굴아나고구이", null, "한식", "부산광역시 중구", 35.10, 129.03),
				new SbizRow("MA0101202511A0024557", "편의방", null, "중국집", "부산광역시 중구 해관로 64-1", 35.1046, 129.0357));
		this.placeLoader.saveChunk(rows, "sbiz-test", OffsetDateTime.now());
	}

	private int load(Path file) {
		TruthSignalReader.Loaded loaded = TruthSignalReader.read(file);
		ListRarity rarity = ListRarity.from(loaded.rows());
		return this.popularityLoader.saveChunk(loaded.rows(), rarity, DATASET, OffsetDateTime.now());
	}

	@Test
	@DisplayName("완료 기준 — 목록에 오른 장소에 인기도 점수가 붙는다")
	void aListedPlaceGetsAPopularityScore() {
		loadPlaces();

		assertThat(load(sampleFile())).isPositive();

		UUID placeId = SbizPlaceLoader.placeIdOf(LISTED_STORE);
		String value = this.jdbcTemplate.queryForObject(
				"SELECT value::text FROM place_feature WHERE place_id = ? AND feature_type = 'POPULARITY_SCORE'",
				String.class, placeId);
		assertThat(value).contains("\"score\"").contains("\"lists_count\"").contains("\"mentions\"").contains("\"lists\"");
	}

	@Test
	@DisplayName("완료 기준 — 두 번 돌려도 행이 안 는다")
	void loadingTwiceDoesNotDuplicate() {
		loadPlaces();
		int first = load(sampleFile());

		assertThat(load(sampleFile())).isZero();
		Integer rows = this.jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM place_feature WHERE feature_type = 'POPULARITY_SCORE'", Integer.class);
		assertThat(rows).isEqualTo(first);
	}

	@Test
	@DisplayName("장소가 없으면 점수만 따로 넣지 않는다 — 외래키가 덩어리 전체를 되돌린다")
	void aScoreIsNeverStoredWithoutItsPlace() {
		// 장소를 안 넣고 바로 점수를 넣어 본다.
		assertThat(load(sampleFile())).isZero();

		Integer rows = this.jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM place_feature WHERE feature_type = 'POPULARITY_SCORE'", Integer.class);
		assertThat(rows).isZero();
	}

	@Test
	@DisplayName("증거 등급이 ESTIMATED 다 — 목록에 올랐다는 사실이지 잰 인기도가 아니다")
	void theEvidenceIsMarkedEstimated() {
		loadPlaces();
		load(sampleFile());

		Integer notEstimated = this.jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM place_feature WHERE feature_type = 'POPULARITY_SCORE' "
						+ "AND evidence_status <> 'ESTIMATED'",
				Integer.class);
		assertThat(notEstimated).isZero();
	}

	@Test
	@DisplayName("수집분이 모든 행에 적힌다 — 이 점수로 만든 추천을 되짚을 수 있어야 한다")
	void everyRowCarriesTheDatasetVersion() {
		loadPlaces();
		load(sampleFile());

		Integer missing = this.jdbcTemplate.queryForObject(
				"SELECT COUNT(*) FROM place_feature WHERE feature_type = 'POPULARITY_SCORE' "
						+ "AND (source_version IS NULL OR source_version <> ?)",
				Integer.class, DATASET);
		assertThat(missing).isZero();
	}

	@Test
	@DisplayName("점수기가 읽는 자리에 그대로 들어간다 — score 를 가진 객체다")
	void theValueMatchesWhatTheScorerReads() {
		loadPlaces();
		load(sampleFile());

		UUID placeId = SbizPlaceLoader.placeIdOf(LISTED_STORE);
		// BaselineCandidateScorer.extractPlaceScore 가 value.score 를 읽는다.
		Double score = this.jdbcTemplate.queryForObject(
				"SELECT (value->>'score')::float8 FROM place_feature "
						+ "WHERE place_id = ? AND feature_type = 'POPULARITY_SCORE'",
				Double.class, placeId);
		// 이 가게가 오른 목록은 표본 안에서 가장 흔해 무게가 작다 — 희소성 방식의 요점이다.
		assertThat(score).isBetween(0.0, 1.0).isNotNull();
	}
}
