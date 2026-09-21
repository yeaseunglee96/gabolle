package com.gabolle.backend.place.service;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 영업시간 값 하나를 읽어 그 시각에 여는가를 판정한다. 값 문자열과 시각만 있으면 되므로 DB
 * 없이 검사할 수 있도록 떼어 뒀다.
 *
 * <p>값은 {@code bigData/process/opening-hours.mjs} 가 낸 모양 그대로다.
 *
 * <pre>
 * {"status":"PARSED",
 *  "byDay":{"mon":[["10:00","18:00"]], …, "sun":[]},
 *  "closedDays":["매주 일요일 / 법정공휴일"],
 *  "notes":["- 평일 10:00~18:00 (입장마감 16:00)"],
 *  "raw":{"hoursValue":"10:00~18:00", …}}
 * </pre>
 *
 * <p>모른다로 답하는 자리가 셋이다 — 값이 깨졌을 때(던지지 않는다), {@code byDay} 가 없을 때
 * (계절로만 온 값은 절기 경계를 원천이 말해 주지 않는다), 그 요일 칸이 아예 없을 때. 요일 칸이
 * 없는 것과 빈 배열은 다르다. 빈 배열은 원천이 "그날은 닫는다" 고 말한 것이다.
 *
 * <p>한 요일에 구간이 여럿이면 합집합, 즉 가장 넓게 본다. 원천이 부속 시설 시간까지 한 레코드에
 * 적어 두고 어느 것이 본관인지 말하지 않기 때문이다. 좁게 잡아 열려 있는 곳에 "문 닫았다"
 * 경고를 내는 쪽이, 경고 하나를 놓치는 쪽보다 나쁘다.
 *
 * <p>자정을 넘기는 구간이 있다. 시작이 {@code 24:00} 이면 그날 0시로 읽고, 끝이 시작보다 앞이면
 * 다음 날로 넘어간다고 읽는다. 그래서 어느 시각을 물어도 어제 칸의 넘어가는 구간까지 함께 본다.
 *
 * <p>공휴일은 판정하지 않는다. {@code closedDays} 의 글은 보관만 하고 화면이 사람에게 보여 준다.
 */
public final class OpeningHoursValue {

	private static final ObjectMapper MAPPER = JsonMapper.builder().build();

	private static final String STATUS_ALWAYS_OPEN = "ALWAYS_OPEN";

	/** 일정의 시각은 날짜와 시:분만 있어 지역이 값에 없다. 국내 여행만 다루므로 서울로 읽는다. */
	private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

	private static final int MINUTES_PER_DAY = 24 * 60;

	private OpeningHoursValue() {
	}

	/**
	 * @param valueJson {@code place_feature.value} 에 담긴 문자열. {@code null} 이면 행이 없다는
	 *     뜻이라 모른다로 답한다
	 */
	public static OpeningHoursFilterPort.Answer answerAt(String valueJson, OffsetDateTime at) {
		if (valueJson == null || valueJson.isBlank()) {
			return OpeningHoursFilterPort.Answer.NOT_COLLECTED;
		}
		JsonNode root;
		try {
			root = MAPPER.readTree(valueJson);
		}
		catch (RuntimeException ex) {
			// 던지지 않는다. 자료 한 줄이 일정 편집 전체를 실패시키면 안 된다.
			return OpeningHoursFilterPort.Answer.NOT_COLLECTED;
		}
		if (STATUS_ALWAYS_OPEN.equals(root.path("status").asString())) {
			return OpeningHoursFilterPort.Answer.OPEN;
		}
		JsonNode byDay = root.get("byDay");
		if (byDay == null || !byDay.isObject()) {
			return OpeningHoursFilterPort.Answer.NOT_COLLECTED;
		}

		LocalDateTime local = at.atZoneSameInstant(ZONE).toLocalDateTime();
		int minuteOfDay = local.getHour() * 60 + local.getMinute();
		DayOfWeek day = local.getDayOfWeek();

		JsonNode today = byDay.get(keyOf(day));
		JsonNode yesterday = byDay.get(keyOf(day.minus(1)));
		if (today == null || !today.isArray()) {
			return OpeningHoursFilterPort.Answer.NOT_COLLECTED;
		}
		if (coversToday(today, minuteOfDay) || spillsIntoToday(yesterday, minuteOfDay)) {
			return OpeningHoursFilterPort.Answer.OPEN;
		}
		return OpeningHoursFilterPort.Answer.CLOSED;
	}

	/** 그날 칸의 구간 중 하나라도 그 시각을 덮는가. 자정을 넘기는 구간은 그날의 남은 부분까지 본다. */
	private static boolean coversToday(JsonNode intervals, int minuteOfDay) {
		for (JsonNode interval : intervals) {
			int start = startMinute(interval);
			int end = endMinute(interval);
			if (start < 0 || end < 0) {
				continue;
			}
			if (end <= start ? minuteOfDay >= start : (minuteOfDay >= start && minuteOfDay < end)) {
				return true;
			}
		}
		return false;
	}

	/** 어제 칸에 자정을 넘기는 구간이 있어 지금까지 이어지는가. */
	private static boolean spillsIntoToday(JsonNode intervals, int minuteOfDay) {
		if (intervals == null || !intervals.isArray()) {
			return false;
		}
		for (JsonNode interval : intervals) {
			int start = startMinute(interval);
			int end = endMinute(interval);
			if (start < 0 || end < 0 || end > start) {
				continue;
			}
			if (minuteOfDay < end) {
				return true;
			}
		}
		return false;
	}

	private static int startMinute(JsonNode interval) {
		int start = minuteOf(interval.path(0).asString());
		// "24:00" 으로 시작하는 것은 그날 0시를 뜻한다.
		return start == MINUTES_PER_DAY ? 0 : start;
	}

	private static int endMinute(JsonNode interval) {
		return minuteOf(interval.path(1).asString());
	}

	/** {@code "09:30"} 을 570 으로. 읽을 수 없으면 -1 을 내고 그 구간은 건너뛴다. */
	private static int minuteOf(String time) {
		if (time == null) {
			return -1;
		}
		int colon = time.indexOf(':');
		if (colon < 1) {
			return -1;
		}
		try {
			int hour = Integer.parseInt(time.substring(0, colon).trim());
			int minute = Integer.parseInt(time.substring(colon + 1).trim());
			if (hour < 0 || hour > 24 || minute < 0 || minute > 59) {
				return -1;
			}
			return hour * 60 + minute;
		}
		catch (NumberFormatException ex) {
			return -1;
		}
	}

	/** {@code bigData} 정규화기가 쓰는 요일 이름. */
	static String keyOf(DayOfWeek day) {
		return switch (day) {
			case MONDAY -> "mon";
			case TUESDAY -> "tue";
			case WEDNESDAY -> "wed";
			case THURSDAY -> "thu";
			case FRIDAY -> "fri";
			case SATURDAY -> "sat";
			case SUNDAY -> "sun";
		};
	}
}
