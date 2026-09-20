package com.gabolle.backend.place.service;

import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 브레이크타임·라스트오더 값 하나를 읽어 그 시각에 걸리는가를 판정한다.
 *
 * <pre>
 * BREAK_TIME      value = {"start": "15:00", "end": "17:00"}
 * LAST_ORDER_TIME value = {"time": "21:30"}
 * </pre>
 *
 * <p>값이 없거나 깨졌으면 예외가 아니라 {@link OpeningHoursFilterPort.Answer#NOT_COLLECTED} 다.
 * 값 한 줄 때문에 일정 편집 전체가 실패하면 안 된다.
 */
public final class TimeFactValue {

	private static final ObjectMapper MAPPER = JsonMapper.builder().build();

	/** 일정의 시각은 날짜와 시:분만 있어 지역이 값에 없다. 국내 여행만 다루므로 서울로 읽는다. */
	private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

	private TimeFactValue() {
	}

	/** 끝이 시작보다 작거나 같으면 자정을 넘긴 구간으로 읽는다. */
	public static OpeningHoursFilterPort.Answer answerBreakTimeAt(String valueJson, OffsetDateTime at) {
		JsonNode root = parse(valueJson);
		if (root == null) {
			return OpeningHoursFilterPort.Answer.NOT_COLLECTED;
		}
		int start = minuteOf(root.path("start").asString());
		int end = minuteOf(root.path("end").asString());
		if (start < 0 || end < 0) {
			return OpeningHoursFilterPort.Answer.NOT_COLLECTED;
		}
		int minuteOfDay = minuteOfDay(at);
		boolean within = end <= start ? minuteOfDay >= start : (minuteOfDay >= start && minuteOfDay < end);
		return within ? OpeningHoursFilterPort.Answer.CLOSED : OpeningHoursFilterPort.Answer.OPEN;
	}

	public static OpeningHoursFilterPort.Answer answerLastOrderAt(String valueJson, OffsetDateTime at) {
		JsonNode root = parse(valueJson);
		if (root == null) {
			return OpeningHoursFilterPort.Answer.NOT_COLLECTED;
		}
		int lastOrder = minuteOf(root.path("time").asString());
		if (lastOrder < 0) {
			return OpeningHoursFilterPort.Answer.NOT_COLLECTED;
		}
		return minuteOfDay(at) >= lastOrder
				? OpeningHoursFilterPort.Answer.CLOSED
				: OpeningHoursFilterPort.Answer.OPEN;
	}

	private static JsonNode parse(String valueJson) {
		if (valueJson == null || valueJson.isBlank()) {
			return null;
		}
		try {
			return MAPPER.readTree(valueJson);
		}
		catch (RuntimeException ex) {
			return null;
		}
	}

	private static int minuteOfDay(OffsetDateTime at) {
		LocalDateTime local = at.atZoneSameInstant(ZONE).toLocalDateTime();
		return local.getHour() * 60 + local.getMinute();
	}

	/** {@code "15:00"} 을 900 으로. 읽을 수 없으면 -1. */
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
}
