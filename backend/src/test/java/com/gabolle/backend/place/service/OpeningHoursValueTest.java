package com.gabolle.backend.place.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.place.service.OpeningHoursFilterPort.Answer;

/**
 * 영업시간 값 판정. 값 모양은 실제 정규화 출력({@code bigData/process/opening-hours.mjs})에서
 * 그대로 옮겼다 — 지어낸 모양으로 재면 이 검사가 통과하고 운영에서만 틀린다.
 *
 * <p>아래 시각의 기준 날짜 2026-09-10 은 목요일이라 byDay 의 {@code thu} 칸이 골라진다.
 */
class OpeningHoursValueTest {

	/** 한국 시각 2026-09-10(목) 10:00. */
	private static final OffsetDateTime THU_10AM =
			OffsetDateTime.of(2026, 9, 10, 10, 0, 0, 0, ZoneOffset.ofHours(9));

	@Test
	@DisplayName("상시 개방이면 아무 시각에나 연다")
	void alwaysOpenIsOpenAtAnyTime() {
		String value = """
				{"status":"ALWAYS_OPEN","byDay":null,"closedDays":[],"notes":[]}""";

		assertThat(OpeningHoursValue.answerAt(value, THU_10AM)).isEqualTo(Answer.OPEN);
		assertThat(OpeningHoursValue.answerAt(value, THU_10AM.withHour(3))).isEqualTo(Answer.OPEN);
	}

	@Test
	@DisplayName("그 요일 구간 안이면 열고 밖이면 닫는다")
	void insideTheIntervalOpensOutsideCloses() {
		String value = """
				{"status":"PARSED","byDay":{"thu":[["10:00","18:00"]]}}""";

		assertThat(OpeningHoursValue.answerAt(value, THU_10AM)).isEqualTo(Answer.OPEN);
		assertThat(OpeningHoursValue.answerAt(value, THU_10AM.withHour(17))).isEqualTo(Answer.OPEN);
		assertThat(OpeningHoursValue.answerAt(value, THU_10AM.withHour(9))).isEqualTo(Answer.CLOSED);
		// 끝 시각은 포함하지 않는다.
		assertThat(OpeningHoursValue.answerAt(value, THU_10AM.withHour(18))).isEqualTo(Answer.CLOSED);
	}

	@Test
	@DisplayName("🔴 빈 배열은 '그날은 닫는다' 다 — 칸이 없는 것과 다르다")
	void emptyDayMeansClosedButMissingDayMeansUnknown() {
		assertThat(OpeningHoursValue.answerAt("""
				{"status":"PARSED","byDay":{"thu":[]}}""", THU_10AM))
				.as("원천이 그날은 닫는다고 말했다")
				.isEqualTo(Answer.CLOSED);

		assertThat(OpeningHoursValue.answerAt("""
				{"status":"PARSED","byDay":{"fri":[["10:00","18:00"]]}}""", THU_10AM))
				.as("목요일 칸이 없으면 모른다 — 닫혔다고 답하면 없는 사실을 지어내는 것이다")
				.isEqualTo(Answer.NOT_COLLECTED);
	}

	@Test
	@DisplayName("🔴 계절로만 온 값은 모른다로 답한다 — 절기 경계를 원천이 말해 주지 않는다")
	void seasonalOnlyValueIsNotDecidable() {
		String value = """
				{"status":"PARSED","byDay":null,"seasonal":{\
				"하절기":{"thu":[["09:00","18:00"]]},\
				"동절기":{"thu":[["09:00","17:00"]]}}}""";

		assertThat(OpeningHoursValue.answerAt(value, THU_10AM)).isEqualTo(Answer.NOT_COLLECTED);
	}

	@Test
	@DisplayName("🔴 한 요일에 구간이 여럿이면 합집합으로 본다")
	void manyIntervalsOnOneDayAreUnioned() {
		// 한 레코드에 부속 시설의 시간까지 들어오는 경우가 있고, 어느 것이 본관인지는 값에 없다.
		String value = """
				{"status":"PARSED","byDay":{"thu":[\
				["09:00","18:00"],["08:00","20:00"],["09:30","17:00"]]}}""";

		assertThat(OpeningHoursValue.answerAt(value, THU_10AM.withHour(8).withMinute(30)))
				.as("가장 넓은 구간이 8시부터라 열려 있다 — 좁게 잡아 거짓 경고를 내는 쪽이 더 나쁘다")
				.isEqualTo(Answer.OPEN);
		assertThat(OpeningHoursValue.answerAt(value, THU_10AM.withHour(19)))
				.isEqualTo(Answer.OPEN);
		assertThat(OpeningHoursValue.answerAt(value, THU_10AM.withHour(21)))
				.isEqualTo(Answer.CLOSED);
	}

	@Test
	@DisplayName("24:00 으로 시작하는 구간은 그날 0시로 읽는다 — 실측에 새벽까지 여는 시장이 그렇게 온다")
	void twentyFourAsStartMeansMidnight() {
		String value = """
				{"status":"PARSED","byDay":{"thu":[["24:00","04:00"]]}}""";

		assertThat(OpeningHoursValue.answerAt(value, THU_10AM.withHour(1))).isEqualTo(Answer.OPEN);
		assertThat(OpeningHoursValue.answerAt(value, THU_10AM.withHour(5))).isEqualTo(Answer.CLOSED);
	}

	@Test
	@DisplayName("🔴 자정을 넘기는 구간은 다음 날 새벽까지 이어진다 — 어제 칸까지 본다")
	void intervalCrossingMidnightSpillsIntoTheNextDay() {
		// 수요일 18:00~02:00 인 집에 목요일 새벽 1시를 묻는다 — 어제 칸을 안 보면 닫힌 것으로 나온다.
		String value = """
				{"status":"PARSED","byDay":{"wed":[["18:00","02:00"]],"thu":[]}}""";

		assertThat(OpeningHoursValue.answerAt(value, THU_10AM.withHour(1))).isEqualTo(Answer.OPEN);
		assertThat(OpeningHoursValue.answerAt(value, THU_10AM.withHour(3))).isEqualTo(Answer.CLOSED);
		assertThat(OpeningHoursValue.answerAt(value, THU_10AM))
				.as("목요일 낮은 그날 칸이 비어 있으니 닫힌다")
				.isEqualTo(Answer.CLOSED);
	}

	@Test
	@DisplayName("🔴 값이 깨져 있어도 던지지 않고 모른다로 답한다")
	void brokenValueDoesNotThrow() {
		assertThat(OpeningHoursValue.answerAt("{이건 JSON 이 아니다", THU_10AM))
				.isEqualTo(Answer.NOT_COLLECTED);
		assertThat(OpeningHoursValue.answerAt(null, THU_10AM)).isEqualTo(Answer.NOT_COLLECTED);
		assertThat(OpeningHoursValue.answerAt("  ", THU_10AM)).isEqualTo(Answer.NOT_COLLECTED);
	}

	@Test
	@DisplayName("읽을 수 없는 시각이 섞이면 그 구간만 건너뛴다")
	void unparsableTimeSkipsOnlyThatInterval() {
		String value = """
				{"status":"PARSED","byDay":{"thu":[["연중무휴","x"],["10:00","18:00"]]}}""";

		assertThat(OpeningHoursValue.answerAt(value, THU_10AM)).isEqualTo(Answer.OPEN);
	}

	@Test
	@DisplayName("🔴 시각은 서울 기준으로 읽는다 — 세계시로 온 값이 하루 앞으로 밀리지 않는다")
	void timeIsReadInSeoul() {
		// 세계시 목요일 22:00 은 서울에서 금요일 07:00 이다.
		String value = """
				{"status":"PARSED","byDay":{"thu":[["10:00","18:00"]],"fri":[]}}""";
		OffsetDateTime thursdayNightUtc =
				OffsetDateTime.of(2026, 9, 10, 22, 0, 0, 0, ZoneOffset.UTC);

		assertThat(OpeningHoursValue.answerAt(value, thursdayNightUtc)).isEqualTo(Answer.CLOSED);
	}
}
