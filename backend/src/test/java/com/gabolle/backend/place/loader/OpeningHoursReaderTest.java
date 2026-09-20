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

/** 줄 모양은 실제 정규화 출력에서 옮겨 적었다. */
class OpeningHoursReaderTest {

	@TempDir
	Path dir;

	@Test
	@DisplayName("🔴 시각이 없는 줄은 행을 만들지 않는다 — 지어내면 '확인했고 문제 없음' 이 된다")
	void unknownStatusMakesNoRow() throws IOException {
		Path file = write(
				"""
						{"contentid":"1","status":"UNKNOWN","byDay":null,"raw":{"hoursValue":"점포별 상이"}}""",
				"""
						{"contentid":"2","status":"PARSED","byDay":{"mon":[["10:00","18:00"]]}}""");

		OpeningHoursReader.Loaded loaded = OpeningHoursReader.read(file);

		assertThat(loaded.rows()).extracting(OpeningHoursRow::contentId).containsExactly("2");
		assertThat(loaded.counts().skippedUnknown()).isEqualTo(1);
		assertThat(loaded.counts().totalLines()).isEqualTo(2);
	}

	@Test
	@DisplayName("🔴 숙박은 CHECK_IN_OUT 으로 보낸다 — 영업시간과 묻는 질문이 다르다")
	void lodgingGoesToItsOwnFeatureType() throws IOException {
		Path file = write(
				"""
						{"contentid":"2818890","status":"LODGING","byDay":null,\
						"checkIn":"15:00","checkOut":"11:00","notes":[]}""");

		OpeningHoursReader.Loaded loaded = OpeningHoursReader.read(file);

		assertThat(loaded.rows()).hasSize(1);
		OpeningHoursRow row = loaded.rows().get(0);
		assertThat(row.featureType()).isEqualTo("CHECK_IN_OUT");
		assertThat(row.value()).contains("\"checkIn\":\"15:00\"").contains("\"checkOut\":\"11:00\"");
		assertThat(loaded.counts().byType()).containsEntry("CHECK_IN_OUT", 1);
	}

	@Test
	@DisplayName("숙박인데 두 시각이 다 없으면 넣지 않는다 — 껍데기 행은 값이 있는 것처럼 읽힌다")
	void lodgingWithoutAnyTimeMakesNoRow() throws IOException {
		Path file = write(
				"""
						{"contentid":"9","status":"LODGING","byDay":null,"checkIn":null,"checkOut":null}""");

		OpeningHoursReader.Loaded loaded = OpeningHoursReader.read(file);

		assertThat(loaded.rows()).isEmpty();
		assertThat(loaded.counts().skippedUnknown()).isEqualTo(1);
	}

	@Test
	@DisplayName("🔴 요일 구간·부가 조건·원문을 버리지 않고 값에 담는다")
	void everyFieldTheNormalizerProducedSurvives() throws IOException {
		Path file = write(
				"""
						{"contentid":"2755676","status":"PARSED",\
						"byDay":{"mon":[["10:00","18:00"]],"sun":[]},\
						"seasonal":null,\
						"closedDays":["매주 일요일 / 법정공휴일"],\
						"notes":["- 평일 10:00~18:00 (입장마감 16:00)"],\
						"raw":{"hoursField":"usetime","hoursValue":"10:00~18:00"}}""");

		String value = OpeningHoursReader.read(file).rows().get(0).value();

		assertThat(value).contains("\"byDay\"").contains("\"mon\"")
				// "입장마감 16:00" 을 떼어내면 16:45 에 도착하는 일정이 만들어진다.
				.contains("입장마감 16:00")
				.contains("법정공휴일")
				.contains("usetime");
		// 값이 null 인 칸은 아예 넣지 않는다 — 넣으면 판정기가 칸이 있는 줄 알고 읽는다.
		assertThat(value).doesNotContain("\"seasonal\"");
	}

	@Test
	@DisplayName("한 줄이 깨져도 나머지는 읽고, 식별자가 없는 줄도 세어 올린다")
	void brokenLinesAreCountedNotFatal() throws IOException {
		Path file = write(
				"{이건 JSON 이 아니다",
				"""
						{"status":"PARSED","byDay":{"mon":[["10:00","18:00"]]}}""",
				"""
						{"contentid":"7","status":"ALWAYS_OPEN","byDay":null}""");

		OpeningHoursReader.Loaded loaded = OpeningHoursReader.read(file);

		assertThat(loaded.rows()).extracting(OpeningHoursRow::contentId).containsExactly("7");
		assertThat(loaded.counts().skippedBroken()).isEqualTo(2);
	}

	@Test
	@DisplayName("같은 식별자가 두 번 나오면 먼저 본 것만 남긴다")
	void duplicateContentIdIsCounted() throws IOException {
		Path file = write(
				"""
						{"contentid":"5","status":"ALWAYS_OPEN","byDay":null}""",
				"""
						{"contentid":"5","status":"PARSED","byDay":{"mon":[]}}""");

		OpeningHoursReader.Loaded loaded = OpeningHoursReader.read(file);

		assertThat(loaded.rows()).hasSize(1);
		assertThat(loaded.rows().get(0).value()).contains("ALWAYS_OPEN");
		assertThat(loaded.counts().skippedDuplicate()).isEqualTo(1);
	}

	private Path write(String... lines) throws IOException {
		Path file = this.dir.resolve("opening-hours.ndjson");
		Files.write(file, List.of(lines), StandardCharsets.UTF_8);
		return file;
	}
}
