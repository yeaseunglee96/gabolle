package com.gabolle.backend.place.service;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;

/**
 * 영업시간 값 하나를 읽어 "그 시각에 여는가" 를 판정한다 — S15P21E201-852.
 *
 * <h2>왜 DB 와 떨어져 있나</h2>
 * 판정에 필요한 것은 값 문자열과 시각 둘뿐이다. 떼어 두면 <b>DB 없이 검사할 수 있다</b> — 이
 * 저장소에서 값 모양을 잘못 읽는 종류의 고장은 통합 테스트에서만 드러나곤 했고, 그 테스트는
 * 노트북에 DB 가 없는 사람에게는 CI 에서 처음 돈다.
 *
 * <h2>값의 모양</h2>
 * {@code bigData/process/opening-hours.mjs} 가 낸 것을 그대로 담는다.
 *
 * <pre>
 * {"status":"PARSED",
 *  "byDay":{"mon":[["10:00","18:00"]], …, "sun":[]},
 *  "closedDays":["매주 일요일 / 법정공휴일"],
 *  "notes":["- 평일 10:00~18:00 (입장마감 16:00)"],
 *  "raw":{"hoursValue":"10:00~18:00", …}}
 * </pre>
 *
 * <h2>🔴 모르는 것을 "열려 있다" 로 바꾸지 않는다</h2>
 * 판정은 셋이다 — 연다 · 닫는다 · <b>모른다</b>. 모른다가 나오는 자리가 셋 있다.
 *
 * <ul>
 * <li>값이 깨져 있다. 던지지 않고 모른다로 답한다 — 자료 한 줄 때문에 일정 편집이 실패하면
 *     사용자는 자기가 뭘 잘못했는지 알 수 없다</li>
 * <li>{@code byDay} 가 없다. 계절로만 온 4곳이 그렇다({@code {"하절기":…,"동절기":…}}) —
 *     <b>절기의 경계 날짜를 원천이 말해 주지 않아서</b> 오늘이 어느 절기인지 우리가 정할 수 없다.
 *     지어내지 않고 값은 그대로 보관한다</li>
 * <li>그 요일 칸이 아예 없다. 빈 배열({@code []})과 다르다 — 빈 배열은 <b>"그날은 닫는다"</b> 고
 *     원천이 말한 것이다</li>
 * </ul>
 *
 * <h2>🔴 한 요일에 구간이 여럿이면 합집합으로 본다</h2>
 * 실측에서 574개 요일 칸에 구간이 둘 이상이었고, 한 곳은 같은 수요일에 네 구간
 * ({@code 09:00~18:00 · 08:00~20:00 · 09:00~18:00 · 09:30~17:00})이 붙어 있었다. 원천이 한
 * 레코드 안에 부속 시설의 시간을 함께 적어 둔 것이고 <b>어느 것이 본관인지 값에 없다.</b>
 *
 * <p>그래서 가장 넓은 구간을 택한다. 좁게 잡으면 실제로 열려 있는 시각에 "문 닫았다" 경고가
 * 나가고, 사용자가 한 번 그것을 겪으면 <b>그 뒤의 모든 경고를 믿지 않는다.</b> 반대 방향의
 * 잘못(열려 있다고 넘어간 것)은 놓친 경고 하나로 끝난다 — 비대칭이라 넓은 쪽을 고른다.
 *
 * <h2>자정을 넘기는 구간</h2>
 * 실측에 81개 있다. 새벽 4시까지 여는 시장이 {@code ["24:00","04:00"]} 로 온다. 두 가지를
 * 처리한다 — 시작이 {@code 24:00} 이면 그날 0시로 읽고, 끝이 시작보다 앞이면 <b>다음 날로
 * 넘어간다</b>고 읽는다. 그래서 화요일 새벽 1시를 물으면 <b>월요일 칸의 넘어가는 구간</b>까지
 * 함께 본다 — 안 보면 새벽에 여는 집이 전부 닫힌 것으로 나온다.
 *
 * <h2>공휴일은 판정하지 않는다</h2>
 * {@code closedDays} 에 "법정공휴일" 이 글로 적혀 있지만 어느 날이 공휴일인지 이 서비스는
 * 모른다. 그 글은 값에 보관만 하고 판정에 쓰지 않는다 — 화면이 사람에게 보여 주면 된다.
 */
public final class OpeningHoursValue {

	/** 값 문자열을 읽는 데만 쓴다. 이 클래스는 상태가 없어 하나를 공유한다. */
	private static final ObjectMapper MAPPER = JsonMapper.builder().build();

	private static final String STATUS_ALWAYS_OPEN = "ALWAYS_OPEN";

	/** 일정의 시각은 날짜와 시:분만 있어 지역이 값에 없다. 국내 여행만 다루므로 서울로 읽는다. */
	private static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

	private static final int MINUTES_PER_DAY = 24 * 60;

	private OpeningHoursValue() {
	}

	/**
	 * 그 시각에 여는가.
	 *
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
			// 🔴 던지지 않는다. 자료 한 줄이 일정 편집 전체를 실패시키면 안 된다.
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
		// "24:00" 으로 시작하는 것은 그날 0시를 뜻한다 — 실측에 새벽까지 여는 시장이 그렇게 온다.
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
