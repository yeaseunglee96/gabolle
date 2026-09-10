package com.gabolle.backend.place;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.gabolle.backend.place.loader.TruthSignalReader;
import com.gabolle.backend.place.loader.TruthSignalRow;

/**
 * 목록 근거 파일을 읽는 규칙 — S15P21E201-826.
 *
 * <h2>표본 59줄 상주</h2>
 * 실제 파일({@code bigData/data/staged/truth-linked.ndjson}, 417줄)에서 출처 수가 골고루
 * 섞이도록 뽑았다 — 근거가 없는 줄 10, 한 곳 20, 두 곳 12, 세 곳 8, 네 곳 4, 다섯 곳 4,
 * 여섯 곳 1. 지어낸 모양으로 재면 실제 파일이 조금만 달라도 통과하는 검사가 된다.
 *
 * <p>이 저장소는 건너뛴 검사가 하나라도 있으면 CI 가 빨개지므로 조건부로 건너뛰지 않는다.
 * 전체 파일로 재고 싶으면 {@code GABOLLE_TRUTH_SIGNALS} 에 경로를 준다.
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
		List<TruthSignalRow> rows = new ArrayList<>();
		out[0] = TruthSignalReader.read(file, 500, rows::addAll);
		return rows;
	}

	@Test
	@DisplayName("완료 기준 — 목록에 오른 줄만 넘어온다")
	void onlyRowsWithAListAreHandedOver() {
		TruthSignalReader.Counts[] counts = new TruthSignalReader.Counts[1];
		List<TruthSignalRow> rows = readAll(sampleFile(), counts);

		assertThat(counts[0].total()).isEqualTo(59);
		assertThat(counts[0].usable()).isEqualTo(49);
		assertThat(counts[0].skippedNoSignal()).isEqualTo(10);
		assertThat(counts[0].skippedBroken()).isZero();
		assertThat(rows).hasSize(49);
	}

	@Test
	@DisplayName("근거가 없는 줄에 0 점을 매기지 않는다 — 행 자체를 안 만든다")
	void aPlaceWithoutAListGetsNoRowRatherThanZero() throws Exception {
		Path file = this.tempDir.resolve("no-signal.ndjson");
		Files.writeString(file, """
				{"id":"MA0000000000000001","name":"목록에없는집","truth":{"lists":[],"sourceCount":null}}
				{"id":"MA0000000000000002","name":"truth가아예없는집"}
				""", StandardCharsets.UTF_8);

		TruthSignalReader.Counts[] counts = new TruthSignalReader.Counts[1];
		List<TruthSignalRow> rows = readAll(file, counts);

		// 0 은 "안 유명하다" 는 관측이고 없는 것은 "안 세어 봤다" 는 무지다. 점수기는
		// 값이 없으면 그 축을 빼고, 0 이면 0 을 곱한다 — 둘은 다른 결과를 낸다.
		assertThat(rows).isEmpty();
		assertThat(counts[0].skippedNoSignal()).isEqualTo(2);
	}

	@Test
	@DisplayName("목록 수가 점수가 된다 — 다섯 곳부터 1.0 이고 그 위는 더 안 올라간다")
	void theListCountBecomesAScore() {
		assertThat(new TruthSignalRow("x", 1, List.of()).score()).isCloseTo(0.2, within(1e-9));
		assertThat(new TruthSignalRow("x", 3, List.of()).score()).isCloseTo(0.6, within(1e-9));
		assertThat(new TruthSignalRow("x", 5, List.of()).score()).isCloseTo(1.0, within(1e-9));
		assertThat(new TruthSignalRow("x", 6, List.of()).score()).isCloseTo(1.0, within(1e-9));
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
			// 전체 파일은 bigData 쪽 브랜치에 있어 대부분의 PC 와 CI 에는 없다. 없으면
			// 표본으로 대신 재고, 이 검사가 조건부로 건너뛰지 않게 한다.
			file = sampleFile();
		}
		TruthSignalReader.Counts[] counts = new TruthSignalReader.Counts[1];
		List<TruthSignalRow> rows = readAll(file, counts);

		assertThat(counts[0].skippedBroken()).isZero();
		assertThat(rows).isNotEmpty();
		assertThat(rows).allSatisfy(row -> {
			assertThat(row.storeId()).isNotBlank();
			assertThat(row.sourceCount()).isPositive();
			assertThat(row.score()).isBetween(0.0, 1.0);
		});
	}
}
