package com.gabolle.backend.place.loader;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import tools.jackson.databind.ObjectMapper;

/** {@link SubwayExitReader} 검증 — S15P21E201-479. */
class SubwayExitReaderTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@TempDir
	Path tempDir;

	private Path file(String... lines) {
		try {
			Path path = this.tempDir.resolve("subway-exit.ndjson");
			Files.writeString(path, String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
			return path;
		}
		catch (IOException exception) {
			throw new UncheckedIOException(exception);
		}
	}

	@Test
	@DisplayName("namespace·storeId·subwayExit 을 그대로 옮긴다")
	void 읽은_줄을_그대로_옮긴다() throws IOException {
		Path path = file(
				"{\"namespace\":\"TOURAPI\",\"storeId\":\"129156\",\"subwayExit\":\"2호선 강남역 3번 출구\"}");

		List<SubwayExitRow> rows = new SubwayExitReader(this.objectMapper).read(path);

		assertThat(rows).hasSize(1);
		assertThat(rows.get(0).namespace()).isEqualTo("TOURAPI");
		assertThat(rows.get(0).storeId()).isEqualTo("129156");
		assertThat(rows.get(0).subwayExit()).isEqualTo("2호선 강남역 3번 출구");
	}

	@Test
	@DisplayName("🔴 subwayExit 이 빠지면 조용히 건너뛰지 않고 멈춘다")
	void 빠진_칸은_멈춘다() {
		Path path = file("{\"namespace\":\"TOURAPI\",\"storeId\":\"129156\"}");

		assertThatThrownBy(() -> new SubwayExitReader(this.objectMapper).read(path))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("1 번째 줄");
	}

	@Test
	@DisplayName("🔴 모르는 namespace 면 멈춘다")
	void 모르는_namespace는_멈춘다() {
		Path path = file(
				"{\"namespace\":\"KAKAO\",\"storeId\":\"129156\",\"subwayExit\":\"2호선 강남역 3번 출구\"}");

		assertThatThrownBy(() -> new SubwayExitReader(this.objectMapper).read(path))
				.isInstanceOf(IllegalStateException.class)
				.hasMessageContaining("KAKAO");
	}
}
