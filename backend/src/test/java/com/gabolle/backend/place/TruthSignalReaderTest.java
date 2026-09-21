package com.gabolle.backend.place;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.gabolle.backend.place.loader.ListRarity;
import com.gabolle.backend.place.loader.TruthSignalReader;
import com.gabolle.backend.place.loader.TruthSignalRow;

/**
 * 목록 근거 파일을 읽는 규칙. 상주 표본 77줄은 실제 파일
 * ({@code bigData/data/staged/truth-linked.ndjson})에서 다섯 목록을 전부 덮도록 뽑았다 —
 * 목록마다 무게가 다르므로 어휘를 다 덮지 않으면 무게를 재는 검사가 성립하지 않는다.
 *
 * <p>전체 파일로 재려면 {@code GABOLLE_TRUTH_SIGNALS} 에 경로를 준다.
 */
class TruthSignalReaderTest {

	private static final String SAMPLE = "/research/truth-sample.ndjson";

	@TempDir
	Path tempDir;

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

	private List<TruthSignalRow> readAll(Path file, TruthSignalReader.Counts[] out) {
		TruthSignalReader.Loaded loaded = TruthSignalReader.read(file);
		out[0] = loaded.counts();
		return loaded.rows();
	}

	@Test
	@DisplayName("완료 기준 — 오른 목록이 있으면 넘어온다. 글 지목 수가 없어도 마찬가지다")
	void everyRowWithAListIsHandedOver() {
		TruthSignalReader.Counts[] counts = new TruthSignalReader.Counts[1];
		List<TruthSignalRow> rows = readAll(sampleFile(), counts);

		// 표본 77줄 전부 오른 목록이 있다. sourceCount 칸은 「공개글448」에만 붙으므로
		// 그 칸만 보면 다른 목록에만 오른 곳이 통째로 빠진다.
		assertThat(counts[0].total()).isEqualTo(77);
		assertThat(counts[0].usable()).isEqualTo(77);
		assertThat(counts[0].skippedNoSignal()).isZero();
		assertThat(counts[0].skippedBroken()).isZero();
		assertThat(rows).hasSize(77);
		assertThat(rows.stream().filter(row -> row.mentions() == 0))
				.as("글 지목 수가 없는 줄도 넘어와야 한다 — 그것이 이 정정의 요점이다")
				.isNotEmpty();
	}

	@Test
	@DisplayName("오른 목록이 없는 줄에 0 점을 매기지 않는다 — 행 자체를 안 만든다")
	void aPlaceWithoutAListGetsNoRowRatherThanZero() throws Exception {
		Path file = this.tempDir.resolve("no-signal.ndjson");
		Files.writeString(file, """
				{"id":"MA0000000000000001","name":"목록에없는집","truth":{"lists":[],"sourceCount":null}}
				{"id":"MA0000000000000002","name":"truth가아예없는집"}
				""", StandardCharsets.UTF_8);

		TruthSignalReader.Counts[] counts = new TruthSignalReader.Counts[1];
		List<TruthSignalRow> rows = readAll(file, counts);

		// 점수기는 값이 없으면 그 축을 빼고 0 이면 0 을 곱한다 — 둘은 다른 결과를 낸다.
		assertThat(rows).isEmpty();
		assertThat(counts[0].skippedNoSignal()).isEqualTo(2);
	}

	@Test
	@DisplayName("드문 목록이 흔한 목록보다 무겁다 — 무게를 사람이 안 고른다")
	void aRareListWeighsMoreThanACommonOne() {
		// 흔한 목록에 90곳, 드문 목록에 10곳이 올랐다고 하자.
		List<TruthSignalRow> rows = new java.util.ArrayList<>();
		for (int i = 0; i < 90; i++) {
			rows.add(new TruthSignalRow("common-" + i, List.of("흔한목록"), 0));
		}
		for (int i = 0; i < 10; i++) {
			rows.add(new TruthSignalRow("rare-" + i, List.of("드문목록"), 0));
		}
		ListRarity rarity = ListRarity.from(rows);

		assertThat(rarity.weightOf("드문목록")).isEqualTo(1.0);
		assertThat(rarity.weightOf("흔한목록")).isLessThan(rarity.weightOf("드문목록"));
		// 모르는 목록에 무게를 지어내지 않는다.
		assertThat(rarity.weightOf("본적없는목록")).isZero();
	}

	@Test
	@DisplayName("여러 목록에 오르면 더해지고 1.0 에서 잘린다")
	void weightsAddUpAndAreCapped() {
		List<TruthSignalRow> rows = new java.util.ArrayList<>();
		for (int i = 0; i < 50; i++) {
			rows.add(new TruthSignalRow("a-" + i, List.of("A"), 0));
		}
		for (int i = 0; i < 5; i++) {
			rows.add(new TruthSignalRow("b-" + i, List.of("B"), 0));
		}
		ListRarity rarity = ListRarity.from(rows);

		double both = rarity.scoreOf(new TruthSignalRow("x", List.of("A", "B"), 0));
		assertThat(both).isGreaterThan(rarity.scoreOf(new TruthSignalRow("x", List.of("A"), 0)));
		assertThat(both).isBetween(0.0, 1.0);
	}

	@Test
	@DisplayName("실제 표본에서 백년가게가 공개글448보다 무겁다")
	void theRealSampleRanksTheCuratedListHigher() {
		ListRarity rarity = ListRarity.from(readAll(sampleFile(), new TruthSignalReader.Counts[1]));

		assertThat(rarity.weightOf("백년가게")).isGreaterThan(rarity.weightOf("공개글448"));
	}

	@Test
	@DisplayName("형식이 깨진 줄이 있어도 나머지는 넘어온다")
	void oneBrokenLineDoesNotStopTheRest() throws Exception {
		Path file = this.tempDir.resolve("broken.ndjson");
		Files.writeString(file, "{이건 JSON 이 아니다\n"
				+ "{\"id\":\"MA0000000000000003\",\"truth\":{\"lists\":[\"블루리본\"],\"sourceCount\":1}}\n",
				StandardCharsets.UTF_8);

		TruthSignalReader.Counts[] counts = new TruthSignalReader.Counts[1];
		List<TruthSignalRow> rows = readAll(file, counts);

		assertThat(rows).hasSize(1);
		assertThat(counts[0].skippedBroken()).isEqualTo(1);
	}

	@Test
	@DisplayName("목록 이름이 함께 넘어온다 — 왜 그 점수인지 되짚을 수 있어야 한다")
	void theListNamesComeAlong() {
		List<TruthSignalRow> rows = readAll(sampleFile(), new TruthSignalReader.Counts[1]);

		assertThat(rows).allSatisfy(row -> assertThat(row.lists()).isNotEmpty());
		assertThat(rows.stream().flatMap(row -> row.lists().stream()).distinct())
				.containsAnyOf("블루리본", "백년가게", "택슐랭", "블로그100", "공개글448");
	}

	@Test
	@DisplayName("전체 파일을 주면 그대로 읽는다")
	void theWholeFileIsUsableWhenProvided() {
		String path = System.getenv("GABOLLE_TRUTH_SIGNALS");
		Path file = (path == null || path.isBlank()) ? null : Path.of(path);
		if (file == null || !Files.isRegularFile(file)) {
			// 전체 파일은 대부분의 PC 와 CI 에 없다. 건너뛰지 않고 표본으로 대신 잰다.
			file = sampleFile();
		}
		TruthSignalReader.Counts[] counts = new TruthSignalReader.Counts[1];
		List<TruthSignalRow> rows = readAll(file, counts);

		assertThat(counts[0].skippedBroken()).isZero();
		assertThat(rows).isNotEmpty();
		ListRarity rarity = ListRarity.from(rows);
		assertThat(rows).allSatisfy(row -> {
			assertThat(row.storeId()).isNotBlank();
			assertThat(row.lists()).isNotEmpty();
			assertThat(row.mentions()).isNotNegative();
			assertThat(rarity.scoreOf(row)).isBetween(0.0, 1.0);
		});
	}
}
