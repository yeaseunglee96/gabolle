package com.gabolle.backend.place.loader;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * 이음 파일에서 폐업만 뽑아 읽는다 — S15P21E201-1341.
 *
 * <p>🔴 이 시험이 지키는 것은 <b>「닫았다」를 지어내지 않는가</b>다. 상태만 「폐업」이고 날짜가
 * 없는 줄을 오늘 날짜로 채우면, 나중에 <b>언제 닫았는지 영영 모르는 채로</b> 남는다.
 */
class PlaceClosureReaderTest {

	@TempDir
	Path dir;

	private Path write(String... lines) throws IOException {
		Path file = this.dir.resolve("place-link.ndjson");
		Files.writeString(file, String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
		return file;
	}

	@Test
	@DisplayName("폐업은 날짜와 함께 담는다")
	void closedRowsCarryTheDate() throws IOException {
		Path file = write("""
				{"sbizId":"S1","name":"쿱스토어","permit":{"state":"폐업","closedOn":"2016-01-11"}}""");

		PlaceClosureReader.Loaded loaded = PlaceClosureReader.read(file);

		assertThat(loaded.rows()).singleElement()
				.satisfies((row) -> {
					assertThat(row.storeId()).isEqualTo("S1");
					assertThat(row.closedOn()).isEqualTo(LocalDate.of(2016, 1, 11));
				});
		assertThat(loaded.closed()).isEqualTo(1);
	}

	/**
	 * 🔴 <b>「영업」도 담는다.</b> 담아야 지우는 쪽이 생긴다 — 잘못 이어졌던 가게 하나가 영영
	 * 추천에서 사라지지 않으려면, 다음 판에서 「안 닫았다」로 되돌릴 수 있어야 한다.
	 */
	@Test
	@DisplayName("🔴 영업 중인 곳도 담는다 — 되돌릴 수 있어야 한다")
	void openRowsAreCarriedWithNullDate() throws IOException {
		Path file = write("""
				{"sbizId":"S2","permit":{"state":"영업/정상","closedOn":null}}""");

		PlaceClosureReader.Loaded loaded = PlaceClosureReader.read(file);

		assertThat(loaded.rows()).singleElement()
				.satisfies((row) -> assertThat(row.closedOn()).isNull());
		assertThat(loaded.closed()).isZero();
	}

	@Test
	@DisplayName("🔴 「폐업」인데 날짜가 없으면 버리고 센다 — 오늘로 채우지 않는다")
	void closedWithoutADateIsDroppedAndCounted() throws IOException {
		Path file = write("""
				{"sbizId":"S3","permit":{"state":"폐업","closedOn":null}}""",
				"""
				{"sbizId":"S4","permit":{"state":"폐업","closedOn":"어제"}}""");

		PlaceClosureReader.Loaded loaded = PlaceClosureReader.read(file);

		assertThat(loaded.rows()).isEmpty();
		assertThat(loaded.closedWithoutDate()).isEqualTo(2);
	}

	@Test
	@DisplayName("이음 정보나 가게 번호가 없는 줄은 버린다")
	void unusableRowsAreSkipped() throws IOException {
		Path file = write("""
				{"sbizId":"S5","permit":null}""",
				"""
				{"name":"번호 없음","permit":{"state":"폐업","closedOn":"2020-01-01"}}""");

		PlaceClosureReader.Loaded loaded = PlaceClosureReader.read(file);

		assertThat(loaded.rows()).isEmpty();
		assertThat(loaded.skipped()).isEqualTo(2);
	}

	@Test
	@DisplayName("빈 줄이 섞여도 안 깨진다")
	void blankLinesAreIgnored() throws IOException {
		Path file = write("""
				{"sbizId":"S6","permit":{"state":"영업/정상"}}""", "", "  ");

		assertThat(PlaceClosureReader.read(file).rows()).hasSize(1);
	}
}
