package com.gabolle.backend.place.loader;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** 줄 모양은 실제 수집본에서 옮겼다 — {@code raw} 가 문자열이고 접근성 칸은 상세 줄에만 있다. */
class BarrierFreeReaderTest {

	@TempDir
	Path dir;

	@Test
	@DisplayName("상세 줄에서 접근성을 말한 장소만 뽑는다")
	void picksOnlyPlacesThatStateAccessibility() throws IOException {
		Path file = write(
				detail("849929", "주출입구는 턱이 없어 휠체어 접근 가능함", ""),
				detail("849930", "여닫이문", ""));

		BarrierFreeReader.Loaded loaded = BarrierFreeReader.read(file);

		assertThat(loaded.rows()).extracting(BarrierFreeRow::contentId).containsExactly("849929");
		assertThat(loaded.counts().noCode()).isEqualTo(1);
		assertThat(loaded.counts().byCode()).containsEntry("WHEELCHAIR", 1);
	}

	@Test
	@DisplayName("목록 줄은 읽지 않는다 — 접근성 칸이 거기 없다")
	void listLinesAreIgnored() throws IOException {
		Path file = write(
				"{\"stage\":\"list\",\"raw\":\"{}\"}",
				detail("1", "주출입구는 턱이 없어 휠체어 접근 가능함", ""));

		BarrierFreeReader.Loaded loaded = BarrierFreeReader.read(file);

		assertThat(loaded.rows()).hasSize(1);
		assertThat(loaded.counts().totalLines()).isEqualTo(2);
		assertThat(loaded.counts().detailLines()).isEqualTo(1);
		assertThat(loaded.counts().broken()).isZero();
	}

	@Test
	@DisplayName("한 줄이 깨져도 나머지는 읽고 버린 줄을 세어 올린다")
	void brokenLineIsCountedNotFatal() throws IOException {
		Path file = write(
				"{이건 JSON 이 아니다",
				detail("2", "주출입구는 턱이 없어 휠체어 접근 가능함", ""));

		BarrierFreeReader.Loaded loaded = BarrierFreeReader.read(file);

		assertThat(loaded.rows()).hasSize(1);
		assertThat(loaded.counts().broken()).isEqualTo(1);
	}

	@Test
	@DisplayName("같은 식별자가 두 번 오면 먼저 본 것만 남긴다")
	void duplicateIsCounted() throws IOException {
		Path file = write(
				detail("3", "주출입구는 턱이 없어 휠체어 접근 가능함", ""),
				detail("3", "주출입구는 턱이 없어 휠체어 접근 가능함", "대여가능"));

		BarrierFreeReader.Loaded loaded = BarrierFreeReader.read(file);

		assertThat(loaded.rows()).hasSize(1);
		assertThat(loaded.rows().get(0).codes()).containsExactly("WHEELCHAIR");
		assertThat(loaded.counts().duplicate()).isEqualTo(1);
	}

	@Test
	@DisplayName("유아차 대여 안내는 표식이 되지 않는다 — 휠체어만 붙고 세는 칸에도 STROLLER 가 없다")
	void strollerRentalAddsNoCode() throws IOException {
		Path file = write(detail("4", "주출입구는 턱이 없어 휠체어 접근 가능함", "대여가능"));

		BarrierFreeReader.Loaded loaded = BarrierFreeReader.read(file);

		assertThat(loaded.rows().get(0).codes()).containsExactly("WHEELCHAIR");
		assertThat(loaded.counts().byCode()).containsEntry("WHEELCHAIR", 1).doesNotContainKey("STROLLER");
	}

	private Path write(String... lines) throws IOException {
		Path file = this.dir.resolve("barrier-free.ndjson");
		Files.write(file, List.of(lines), StandardCharsets.UTF_8);
		return file;
	}

	/** 실제 수집본과 같은 모양 — {@code raw} 가 문자열이고 항목이 배열로 온다. */
	private static String detail(String contentId, String exit, String stroller) {
		String raw = "{\"response\":{\"body\":{\"items\":{\"item\":[{"
				+ "\"contentid\":\"" + contentId + "\","
				+ "\"exit\":\"" + exit + "\","
				+ "\"stroller\":\"" + stroller + "\"}]}}}}";
		return "{\"stage\":\"detail\",\"contentid\":\"" + contentId + "\",\"raw\":" + quote(raw) + "}";
	}

	private static String quote(String value) {
		return '"' + value.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
	}
}
