package com.gabolle.backend.place;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import com.gabolle.backend.place.domain.PlaceEventPeriod;
import com.gabolle.backend.place.loader.FestivalPeriodReader;

/**
 * 축제 수집본을 읽는 규칙. 지키는 약속은 하나다 — 기간을 지어내지 않는다. DB 없이 돈다.
 */
class FestivalPeriodReaderTest {

	@TempDir
	Path tempDir;

	private Path write(String... lines) throws IOException {
		Path file = this.tempDir.resolve("festival-" + UUID.randomUUID() + ".ndjson");
		Files.writeString(file, String.join("\n", lines) + "\n", StandardCharsets.UTF_8);
		return file;
	}

	@Test
	@DisplayName("기간이 있는 축제를 읽는다")
	void readsAFestivalWithItsPeriod() throws IOException {
		Path file = write("""
				{"contentid":"3576410","title":"광복로 겨울빛 트리축제","eventstartdate":"20251205","eventenddate":"20260222"}""");

		FestivalPeriodReader.Loaded loaded = FestivalPeriodReader.read(file);

		assertThat(loaded.rows()).singleElement().satisfies((row) -> {
			assertThat(row.contentId()).isEqualTo("3576410");
			assertThat(row.title()).isEqualTo("광복로 겨울빛 트리축제");
			assertThat(row.startDate()).isEqualTo(LocalDate.of(2025, 12, 5));
			assertThat(row.endDate()).isEqualTo(LocalDate.of(2026, 2, 22));
		});
	}

	@Test
	@DisplayName("🔴 기간이 비어 있으면 넣지 않는다 — 날짜를 지어내면 안 열리는 축제를 보러 간다")
	void skipsFestivalsWithoutAPeriod() throws IOException {
		Path file = write(
				"""
						{"contentid":"1","title":"날짜 없는 축제","eventstartdate":"","eventenddate":""}""",
				"""
						{"contentid":"2","title":"끝을 모르는 축제","eventstartdate":"20260301"}""");

		FestivalPeriodReader.Loaded loaded = FestivalPeriodReader.read(file);

		assertThat(loaded.rows()).isEmpty();
		assertThat(loaded.counts().skippedNoPeriod()).isEqualTo(2);
	}

	@Test
	@DisplayName("종료일이 시작일보다 빠르면 넣지 않는다 — 어느 쪽이 틀렸는지 여기서 알 수 없다")
	void skipsReversedPeriods() throws IOException {
		Path file = write("""
				{"contentid":"3","title":"뒤집힌 축제","eventstartdate":"20260310","eventenddate":"20260301"}""");

		FestivalPeriodReader.Loaded loaded = FestivalPeriodReader.read(file);

		assertThat(loaded.rows()).isEmpty();
		assertThat(loaded.counts().skippedBroken()).isEqualTo(1);
	}

	@Test
	@DisplayName("같은 축제·같은 기간이 두 번 나오면 한 번만 읽는다")
	void keepsOnlyOneOfADuplicatedRow() throws IOException {
		String line = """
				{"contentid":"4","title":"같은 축제","eventstartdate":"20260501","eventenddate":"20260503"}""";
		Path file = write(line, line);

		FestivalPeriodReader.Loaded loaded = FestivalPeriodReader.read(file);

		assertThat(loaded.rows()).hasSize(1);
		assertThat(loaded.counts().skippedDuplicate()).isEqualTo(1);
	}

	@Test
	@DisplayName("깨진 줄과 식별자 없는 줄은 버리고 나머지는 계속 읽는다")
	void brokenLinesDoNotStopTheRest() throws IOException {
		Path file = write(
				"{ 이건 JSON 이 아니다",
				"""
						{"title":"식별자 없는 축제","eventstartdate":"20260601","eventenddate":"20260602"}""",
				"""
						{"contentid":"5","title":"멀쩡한 축제","eventstartdate":"20260601","eventenddate":"20260602"}""");

		FestivalPeriodReader.Loaded loaded = FestivalPeriodReader.read(file);

		assertThat(loaded.rows()).singleElement()
				.satisfies((row) -> assertThat(row.contentId()).isEqualTo("5"));
		assertThat(loaded.counts().skippedBroken()).isEqualTo(2);
	}

	@Test
	@DisplayName("🔴 같은 회차는 언제 만들어도 같은 식별자다 — 다시 적재해도 행이 안 는다")
	void theSameOccurrenceAlwaysGetsTheSameId() {
		UUID placeId = UUID.randomUUID();
		OffsetDateTime first = OffsetDateTime.parse("2026-09-14T12:00:00Z");
		OffsetDateTime later = OffsetDateTime.parse("2026-10-01T09:30:00Z");

		PlaceEventPeriod a = PlaceEventPeriod.of(placeId, "축제", LocalDate.of(2026, 5, 1), LocalDate.of(2026, 5, 3),
				"TOURAPI", "4", first);
		PlaceEventPeriod b = PlaceEventPeriod.of(placeId, "이름이 바뀐 축제", LocalDate.of(2026, 5, 1),
				LocalDate.of(2026, 5, 3), "TOURAPI", "4", later);

		assertThat(b.getPlaceEventPeriodId()).isEqualTo(a.getPlaceEventPeriodId());
	}

	@Test
	@DisplayName("기간 없는 회차는 만들 수 없다 — 판독기를 건너뛴 호출도 여기서 막힌다")
	void anOccurrenceCannotBeCreatedWithoutAPeriod() {
		UUID placeId = UUID.randomUUID();
		OffsetDateTime now = OffsetDateTime.parse("2026-09-14T12:00:00Z");

		assertThatThrownBy(() -> PlaceEventPeriod.of(placeId, "축제", null, LocalDate.of(2026, 5, 3), "TOURAPI", "4", now))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> PlaceEventPeriod.of(placeId, "축제", LocalDate.of(2026, 5, 5),
				LocalDate.of(2026, 5, 3), "TOURAPI", "4", now))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
