package com.gabolle.backend.facility.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.facility.domain.WeeklyHours.Window;

/**
 * 요일별 운영시간과 「지금 여는가」 판정 (S15P21E201-2052) — 자정을 넘기는 시간대·공휴일 칸·요일·모르는 날. DB 없이 값과 시각만으로
 * 돈다. 2026-10-12 는 월요일이다.
 */
class WeeklyHoursTest {

	/** 2026-10-12(월) 을 기준으로 요일·시각을 만든다. */
	private static LocalDateTime at(DayOfWeek day, int hour, int minute) {
		return LocalDateTime.of(2026, 10, 12, hour, minute).plusDays(day.getValue() - 1);
	}

	private static Window window(String open, String close) {
		return new Window(minute(open), minute(close));
	}

	private static int minute(String clock) {
		String[] parts = clock.split(":");
		return Integer.parseInt(parts[0]) * 60 + Integer.parseInt(parts[1]);
	}

	private static WeeklyHours of(Object... dayAndWindow) {
		Map<String, Window> days = new LinkedHashMap<>();
		for (int i = 0; i < dayAndWindow.length; i += 2) {
			days.put((String) dayAndWindow[i], (Window) dayAndWindow[i + 1]);
		}
		return WeeklyHours.of(days);
	}

	// ── 평범한 하루 ─────────────────────────────────────────────────────────

	@Test
	@DisplayName("여는 시각은 포함, 닫는 시각은 제외 — 09:00 은 열렸고 18:00 은 닫혔다")
	void openIsInclusiveAndCloseIsExclusive() {
		WeeklyHours hours = of("Monday", window("09:00", "18:00"));

		assertThat(hours.at(at(DayOfWeek.MONDAY, 8, 59), false).openNow()).isFalse();
		assertThat(hours.at(at(DayOfWeek.MONDAY, 9, 0), false).openNow()).isTrue();
		assertThat(hours.at(at(DayOfWeek.MONDAY, 17, 59), false).openNow()).isTrue();
		assertThat(hours.at(at(DayOfWeek.MONDAY, 18, 0), false).openNow()).isFalse();
	}

	@Test
	@DisplayName("열려 있으면 오늘 시간대와 닫는 시각을 주고, 닫혀 있으면 오늘 시간대만 준다")
	void statusCarriesTodayWindowAndClosingTime() {
		WeeklyHours hours = of("Monday", window("09:00", "18:00"));

		WeeklyHours.Status open = hours.at(at(DayOfWeek.MONDAY, 12, 0), false);
		assertThat(open.today()).isEqualTo(window("09:00", "18:00"));
		assertThat(open.closesAtText()).isEqualTo("18:00");

		WeeklyHours.Status closed = hours.at(at(DayOfWeek.MONDAY, 19, 0), false);
		assertThat(closed.openNow()).isFalse();
		assertThat(closed.today()).isEqualTo(window("09:00", "18:00"));
		assertThat(closed.closesAtText()).isNull();
	}

	@Test
	@DisplayName("다른 요일은 적혀 있는데 오늘 칸만 없으면 쉬는 날이다 — 닫힘(false)이지 모름(null)이 아니다")
	void missingDayMeansClosedWhenOtherDaysAreReported() {
		WeeklyHours hours = of("Monday", window("09:00", "18:00"));

		WeeklyHours.Status sunday = hours.at(at(DayOfWeek.SUNDAY, 12, 0), false);
		assertThat(sunday.openNow()).isFalse();
		assertThat(sunday.today()).isNull();
	}

	@Test
	@DisplayName("시간대가 하나도 없으면 판정하지 않는다 — openNow 가 null")
	void noWindowsAtAllIsUnknown() {
		assertThat(of().at(at(DayOfWeek.MONDAY, 12, 0), false).openNow()).isNull();
		// 모르는 날(null)만 있어도 같다
		assertThat(of("Monday", null, "Sunday", null).at(at(DayOfWeek.MONDAY, 12, 0), false).openNow()).isNull();
		assertThat(of().hasAnyWindow()).isFalse();
	}

	// ── 모르는 날 ───────────────────────────────────────────────────────────

	@Test
	@DisplayName("🔴 오늘 칸이 «모른다»(null)면 닫았다고 하지 않는다 — 모름(null). 쉬는 날(칸 없음)과 다르다")
	void unknownDayIsNotClosed() {
		WeeklyHours hours = of("Monday", window("09:00", "18:00"), "Sunday", null);

		assertThat(hours.at(at(DayOfWeek.SUNDAY, 12, 0), false).openNow()).isNull();
		// 같은 한 벌에서 칸이 없는 화요일은 쉬는 날이다
		assertThat(hours.at(at(DayOfWeek.TUESDAY, 12, 0), false).openNow()).isFalse();
	}

	// ── 자정을 넘기는 시간대 ────────────────────────────────────────────────

	@Test
	@DisplayName("🔴 자정을 넘기는 시간대 — 금요일 22:00~02:00 은 토요일 새벽 01:59 까지 열려 있고 02:00 에 닫는다")
	void windowPastMidnightSpillsIntoTheNextDay() {
		WeeklyHours hours = of("Friday", window("22:00", "26:00"), "Saturday", window("10:00", "12:00"));

		WeeklyHours.Status friday = hours.at(at(DayOfWeek.FRIDAY, 23, 0), false);
		assertThat(friday.openNow()).isTrue();
		// 다음 날 새벽 2시 — 25:00 이상은 하루 안으로 접어 보인다
		assertThat(friday.closesAtText()).isEqualTo("02:00");

		WeeklyHours.Status saturdayDawn = hours.at(at(DayOfWeek.SATURDAY, 1, 59), false);
		assertThat(saturdayDawn.openNow()).isTrue();
		assertThat(saturdayDawn.closesAtText()).isEqualTo("02:00");
		// 오늘(토요일)의 시간대는 따로 있다
		assertThat(saturdayDawn.today()).isEqualTo(window("10:00", "12:00"));

		assertThat(hours.at(at(DayOfWeek.SATURDAY, 2, 0), false).openNow()).isFalse();
		assertThat(hours.at(at(DayOfWeek.SATURDAY, 10, 0), false).openNow()).isTrue();
	}

	@Test
	@DisplayName("끝이 정확히 24:00 이면 자정에 닫는다 — 다음 날로 넘어가지 않고, 화면에는 24:00 으로 보인다")
	void closingAtMidnightDoesNotSpill() {
		WeeklyHours hours = of("Monday", window("09:00", "24:00"), "Tuesday", window("09:00", "18:00"));

		WeeklyHours.Status lateMonday = hours.at(at(DayOfWeek.MONDAY, 23, 59), false);
		assertThat(lateMonday.openNow()).isTrue();
		assertThat(lateMonday.closesAtText()).isEqualTo("24:00");
		assertThat(hours.at(at(DayOfWeek.TUESDAY, 0, 0), false).openNow()).isFalse();
	}

	@Test
	@DisplayName("하루 종일(00:00~24:00)은 allDay 이고 어느 시각에도 열려 있다")
	void allDayWindow() {
		WeeklyHours hours = of("Monday", window("00:00", "24:00"));

		assertThat(window("00:00", "24:00").allDay()).isTrue();
		assertThat(window("00:00", "23:00").allDay()).isFalse();
		assertThat(hours.at(at(DayOfWeek.MONDAY, 0, 0), false).openNow()).isTrue();
		assertThat(hours.at(at(DayOfWeek.MONDAY, 23, 59), false).openNow()).isTrue();
	}

	// ── 공휴일 ──────────────────────────────────────────────────────────────

	@Test
	@DisplayName("🔴 공휴일이면 오늘 칸은 Holiday 다 — 평일 시간이 아니라 공휴일 시간으로 판정한다")
	void holidayUsesTheHolidayColumn() {
		WeeklyHours hours = of("Monday", window("09:00", "18:00"), "Holiday", window("10:00", "15:00"));
		LocalDateTime mondayAfternoon = at(DayOfWeek.MONDAY, 16, 0);

		assertThat(hours.at(mondayAfternoon, false).openNow()).isTrue();
		WeeklyHours.Status onHoliday = hours.at(mondayAfternoon, true);
		assertThat(onHoliday.openNow()).isFalse();
		assertThat(onHoliday.today()).isEqualTo(window("10:00", "15:00"));
		assertThat(hours.at(at(DayOfWeek.MONDAY, 11, 0), true).closesAtText()).isEqualTo("15:00");
	}

	@Test
	@DisplayName("공휴일 칸이 없으면 공휴일에 쉰다 — 약국 자료는 쉬는 날의 칸을 안 적는다")
	void noHolidayColumnMeansClosedOnHolidays() {
		WeeklyHours hours = of("Monday", window("09:00", "18:00"));

		assertThat(hours.at(at(DayOfWeek.MONDAY, 12, 0), true).openNow()).isFalse();
	}

	@Test
	@DisplayName("공휴일 칸이 «모른다»면 공휴일에는 모름이다")
	void unknownHolidayColumn() {
		WeeklyHours hours = of("Monday", window("09:00", "18:00"), "Holiday", null);

		assertThat(hours.at(at(DayOfWeek.MONDAY, 12, 0), true).openNow()).isNull();
	}

	// ── 묻기 ────────────────────────────────────────────────────────────────

	@Test
	@DisplayName("일요일·공휴일에 연다고 적혀 있는가, 늦게까지 여는가")
	void asksAboutSundayHolidayAndLateClosing() {
		WeeklyHours hours = of("Monday", window("09:00", "23:00"), "Sunday", window("10:00", "20:00"),
				"Holiday", null);

		assertThat(hours.opensOn("Sunday")).isTrue();
		assertThat(hours.opensOn("Holiday")).isFalse();
		assertThat(hours.opensOn("Saturday")).isFalse();
		assertThat(hours.anyCloseAtOrAfter(23 * 60)).isTrue();
		assertThat(hours.anyCloseAtOrAfter(23 * 60 + 1)).isFalse();
		assertThat(of("Monday", window("22:00", "25:00")).anyCloseAtOrAfter(23 * 60)).isTrue();
	}

	// ── 원천 → 시간대 ───────────────────────────────────────────────────────

	@Test
	@DisplayName("주차장 원천: 09:00~00:00 은 자정에 닫는 것, 10:00~02:00 은 다음 날 02:00, 21:00~07:00 은 다음 날 아침")
	void parkingSourceConventions() {
		assertThat(WeeklyHours.parkingWindow("09:00", "00:00").window()).isEqualTo(window("09:00", "24:00"));
		assertThat(WeeklyHours.parkingWindow("10:00", "02:00").window()).isEqualTo(window("10:00", "26:00"));
		assertThat(WeeklyHours.parkingWindow("21:00", "07:00").window()).isEqualTo(window("21:00", "31:00"));
		assertThat(WeeklyHours.parkingWindow("00:00", "23:00").window()).isEqualTo(window("00:00", "23:00"));
		assertThat(WeeklyHours.parkingWindow("00:00", "24:00").window()).isEqualTo(window("00:00", "24:00"));
	}

	@Test
	@DisplayName("🔴 주차장 원천: 00:00~00:00 은 하루 종일인지 운영 안 하는지 모른다 — 시간대로 안 읽고 비운다")
	void parkingMidnightToMidnightIsUnknown() {
		WeeklyHours.Parsed parsed = WeeklyHours.parkingWindow("00:00", "00:00");

		assertThat(parsed.window()).isNull();
		assertThat(parsed.dropped()).isEqualTo(WeeklyHours.DROP_SAME_TIME);
	}

	@Test
	@DisplayName("원천 시각이 비었거나(null) 30분도 안 되는 시간대는 비운다")
	void emptyAndTooShortWindowsAreDropped() {
		assertThat(WeeklyHours.parkingWindow(null, null).dropped()).isEqualTo(WeeklyHours.DROP_EMPTY);
		assertThat(WeeklyHours.parkingWindow("09:00", null).dropped()).isEqualTo(WeeklyHours.DROP_EMPTY);
		assertThat(WeeklyHours.parkingWindow("08:00", "08:20").dropped()).isEqualTo(WeeklyHours.DROP_TOO_SHORT);
		assertThat(WeeklyHours.pharmacyWindow("00:00", "00:01").dropped()).isEqualTo(WeeklyHours.DROP_TOO_SHORT);
		// 정확히 30분은 시간대다
		assertThat(WeeklyHours.pharmacyWindow("09:00", "09:30").window()).isNotNull();
	}

	@Test
	@DisplayName("약국 원천: 자정을 넘기면 끝을 25:00 처럼 적는다. 끝이 시작보다 빠르면 넘기는 것이 아니라 틀린 값이다")
	void pharmacySourceConventions() {
		assertThat(WeeklyHours.pharmacyWindow("22:00", "25:00").window()).isEqualTo(window("22:00", "25:00"));
		assertThat(WeeklyHours.pharmacyWindow("09:00", "24:00").window()).isEqualTo(window("09:00", "24:00"));

		WeeklyHours.Parsed reversed = WeeklyHours.pharmacyWindow("19:00", "14:30");
		assertThat(reversed.window()).isNull();
		assertThat(reversed.dropped()).isEqualTo(WeeklyHours.DROP_REVERSED);
	}

	@Test
	@DisplayName("시각 모양이 깨졌으면 비우지 않고 던진다 — 파일이 틀린 것이다")
	void brokenClockThrows() {
		assertThatThrownBy(() -> WeeklyHours.pharmacyWindow("9시", "18:00")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> WeeklyHours.pharmacyWindow("09:60", "18:00")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> WeeklyHours.parkingWindow("09:00", "99:00")).isInstanceOf(IllegalArgumentException.class);
	}

	// ── 저장 모양 ───────────────────────────────────────────────────────────

	@Test
	@DisplayName("저장 모양으로 내보냈다 다시 읽어도 같다 — 모르는 날(null)과 쉬는 날(칸 없음)이 구분되어 남는다")
	void storedFormRoundTrips() {
		WeeklyHours original = of("Monday", window("09:00", "18:00"), "Friday", window("22:00", "26:00"), "Sunday", null);

		Map<String, Object> stored = original.toDetail();
		assertThat(stored).containsOnlyKeys("Monday", "Friday", "Sunday");
		assertThat(stored.get("Sunday")).isNull();
		assertThat(stored.get("Friday")).isEqualTo(Map.of("open", "22:00", "close", "26:00"));

		WeeklyHours restored = WeeklyHours.fromDetail(stored);
		assertThat(restored.toDetail()).isEqualTo(stored);
		assertThat(restored.at(at(DayOfWeek.SATURDAY, 1, 0), false).openNow()).isTrue();
		assertThat(restored.at(at(DayOfWeek.SUNDAY, 12, 0), false).openNow()).isNull();
		assertThat(restored.at(at(DayOfWeek.TUESDAY, 12, 0), false).openNow()).isFalse();
	}

	@Test
	@DisplayName("저장된 값이 깨져 있어도 조회가 터지지 않는다 — 깨진 칸은 모르는 날, 객체가 아니면 아무것도 모르는 한 벌")
	void brokenStoredValueIsTreatedAsUnknown() {
		assertThat(WeeklyHours.fromDetail(null).hasAnyWindow()).isFalse();
		assertThat(WeeklyHours.fromDetail("not an object").hasAnyWindow()).isFalse();
		assertThat(WeeklyHours.fromDetail(List.of()).hasAnyWindow()).isFalse();

		Map<String, Object> broken = new LinkedHashMap<>();
		broken.put("Monday", Map.of("open", "09:00", "close", "18:00"));
		broken.put("Tuesday", Map.of("open", "고장", "close", "18:00"));
		broken.put("Wednesday", Map.of("open", "18:00", "close", "09:00"));
		broken.put("Thursday", "문자열");
		WeeklyHours hours = WeeklyHours.fromDetail(broken);
		assertThat(hours.at(at(DayOfWeek.TUESDAY, 12, 0), false).openNow()).isNull();
		assertThat(hours.at(at(DayOfWeek.WEDNESDAY, 12, 0), false).openNow()).isNull();
		assertThat(hours.at(at(DayOfWeek.THURSDAY, 12, 0), false).openNow()).isNull();
		assertThat(hours.at(at(DayOfWeek.MONDAY, 12, 0), false).openNow()).isTrue();
	}

	@Test
	@DisplayName("모르는 요일 칸 이름은 만들 때 거절한다")
	void unknownDayNameIsRejected() {
		assertThatThrownBy(() -> WeeklyHours.of(Map.of("Funday", window("09:00", "18:00"))))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("시간대는 시작보다 끝이 늦어야 하고 시작은 하루 안이다")
	void windowInvariants() {
		assertThatThrownBy(() -> new Window(600, 600)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new Window(600, 500)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new Window(1440, 1500)).isInstanceOf(IllegalArgumentException.class);
		assertThat(new Window(600, 1500).closeText()).isEqualTo("01:00");
		assertThat(new Window(600, 1440).closeText()).isEqualTo("24:00");
	}
}
