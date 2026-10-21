package com.gabolle.backend.facility.domain;

import java.time.DayOfWeek;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 요일별 운영시간 한 벌과 「지금 여는가」 판정 (S15P21E201-2052). 주차장과 약국이 같은 틀을 쓴다. DB·스프링 없이 값과 시각만 있으면
 * 되도록 떼어 뒀다 — 자정을 넘기는 시간대와 공휴일 칸을 시험하기 쉽다.
 *
 * <h2>하루는 셋 중 하나다</h2>
 * <ul>
 *   <li><b>시간대가 있다</b> — {@code {"open":"09:00","close":"18:00"}}. 끝이 {@code 24:00} 이상이면 다음 날로 넘어간다
 *       ({@code 25:00} = 다음 날 새벽 1시). 공공 약국 자료가 이렇게 적는다</li>
 *   <li><b>모른다</b> — 값이 {@code null}. 원천이 비었거나(시작·끝이 null) 말이 안 되는 값이라(시작 = 끝인 「00:00~00:00」, 끝이
 *       시작보다 앞, 30분도 안 되는 시간대) 적재기가 비운 날이다. 「닫는다」와 다르다 — 모르는 날을 닫았다고 말하면 열려 있는 곳을
 *       돌려보낸다</li>
 *   <li><b>칸이 없다</b> — 다른 요일은 적혀 있는데 이 날만 없다. 원천이 「그날은 쉰다」는 뜻으로 비운 것이라 <b>닫는다</b>로 읽는다.
 *       (약국 자료는 일요일·공휴일 칸을 쉬는 날에 아예 안 적는다)</li>
 * </ul>
 * 모든 날이 모르거나 없으면(시간대가 하나도 없으면) 판정하지 않는다 — {@code openNow} 가 {@code null} 이다.
 *
 * <h2>🔴 공휴일</h2>
 * 칸 이름은 {@code Holiday} 다. 어느 날이 공휴일인지는 이 클래스가 모른다 — 부르는 쪽이 {@code holiday} 를 알려 줘야 한다. 이
 * 프로젝트에는 공휴일 달력이 아직 없어서 서비스는 늘 {@code false} 를 준다(요일만 본다). 그래서 평일에 낀 공휴일에는 평일 시간으로
 * 판정된다 — 화면이 「방문 전 전화로 확인」을 함께 적는 이유다.
 */
public final class WeeklyHours {

	/** 저장·원천의 칸 이름 — 월요일부터 일요일, 그리고 공휴일. */
	public static final List<String> DAYS = List.of("Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday",
			"Sunday", "Holiday");

	public static final String HOLIDAY = "Holiday";

	private static final int MINUTES_PER_DAY = 24 * 60;

	/** 이보다 짧은 시간대는 시간대가 아니라 자료의 찌꺼기로 본다(실제 자료의 「00:00~00:01」). */
	public static final int MIN_WINDOW_MINUTES = 30;

	/** 시간대의 끝으로 받는 한계 — 다음 날 정오까지. 그보다 큰 값은 자료가 깨진 것이다. */
	private static final int MAX_CLOSE_MINUTE = 2 * MINUTES_PER_DAY;

	private static final Pattern CLOCK = Pattern.compile("^(\\d{1,2}):(\\d{2})$");

	/** 칸 이름 → 시간대. 값이 {@code null} 이면 그날은 모른다, 키가 없으면 그날은 쉰다. */
	private final Map<String, Window> days;

	private WeeklyHours(Map<String, Window> days) {
		this.days = days;
	}

	/**
	 * 시간대 하나. {@code closeMinute} 가 1440 이상이면 다음 날로 넘어간다.
	 *
	 * @param openMinute 0 이상 1440 미만 — 자정부터 센 분
	 * @param closeMinute {@code openMinute} 보다 크고 2880 이하
	 */
	public record Window(int openMinute, int closeMinute) {

		public Window {
			if (openMinute < 0 || openMinute >= MINUTES_PER_DAY || closeMinute <= openMinute
					|| closeMinute > MAX_CLOSE_MINUTE) {
				throw new IllegalArgumentException("시간대가 올바르지 않다: " + openMinute + "~" + closeMinute);
			}
		}

		/** 「00:00~24:00」 이상 — 하루 종일. */
		public boolean allDay() {
			return this.openMinute == 0 && this.closeMinute >= MINUTES_PER_DAY;
		}

		public String openText() {
			return clock(this.openMinute);
		}

		/** 화면에 보일 끝 시각. 24:00 은 그대로 두고, 그보다 늦으면 다음 날의 시각으로 접는다(25:00 → 01:00). */
		public String closeText() {
			return closeTextOf(this.closeMinute);
		}

		/** 저장 모양의 끝 — 하루를 넘으면 그대로 두어(25:00) 다시 읽을 때 넘기는지 안다. */
		String storedClose() {
			return clock(this.closeMinute);
		}
	}

	/**
	 * 지금의 판정.
	 *
	 * @param openNow 지금 여는가. 판정할 수 없으면 {@code null}
	 * @param today 오늘의 시간대. 오늘 쉬거나 모르면 {@code null}
	 * @param closesAtMinute 지금 열려 있을 때 닫는 시각(자정부터 센 분 — 25:00 이면 1500). 열려 있지 않으면 {@code null}
	 */
	public record Status(Boolean openNow, Window today, Integer closesAtMinute) {

		/** 「18:00」 꼴의 닫는 시각. 열려 있지 않으면 {@code null}. */
		public String closesAtText() {
			return this.closesAtMinute == null ? null : closeTextOf(this.closesAtMinute);
		}
	}

	/** 원천 한 칸을 읽은 결과 — 시간대이거나, 왜 비웠는지의 낱말. */
	public record Parsed(Window window, String dropped) {

		static Parsed ok(Window window) {
			return new Parsed(window, null);
		}

		static Parsed drop(String reason) {
			return new Parsed(null, reason);
		}
	}

	/** 비운 이유 — 적재기 로그가 이 낱말로 센다. */
	public static final String DROP_EMPTY = "EMPTY";

	public static final String DROP_SAME_TIME = "SAME_TIME";

	public static final String DROP_REVERSED = "REVERSED";

	public static final String DROP_TOO_SHORT = "TOO_SHORT";

	public static final String DROP_OUT_OF_RANGE = "OUT_OF_RANGE";

	// ── 원천 → 시간대 ───────────────────────────────────────────────────────

	/**
	 * 주차장 원천 한 칸. 이 자료는 자정을 넘기는 시간대를 24시간제 시각 그대로 적는다({@code 10:00~02:00}) — 끝이 시작보다 앞이면
	 * 다음 날 끝으로 읽는다. 끝이 {@code 00:00} 이고 시작이 그보다 늦으면 자정에 닫는 것({@code 09:00~00:00} = 24:00)이다.
	 *
	 * <p>🔴 시작과 끝이 같은 {@code 00:00~00:00} 은 시간대로 안 읽는다. 「하루 종일 연다」인지 「운영 안 한다」인지 자료가 말해 주지
	 * 않고, 둘은 정반대다 — 모른다.
	 *
	 * @throws IllegalArgumentException 시각 모양이 깨졌을 때 — 자료 파일이 틀린 것이라 적재를 세운다
	 */
	public static Parsed parkingWindow(String open, String close) {
		if (open == null || close == null) {
			return Parsed.drop(DROP_EMPTY);
		}
		int start = minuteOf(open);
		int end = minuteOf(close);
		if (start == end) {
			return Parsed.drop(DROP_SAME_TIME);
		}
		if (end == 0) {
			end = MINUTES_PER_DAY;
		}
		else if (end < start) {
			end += MINUTES_PER_DAY;
		}
		return build(start, end);
	}

	/**
	 * 약국 원천 한 칸. 이 자료는 자정을 넘기면 끝을 24 이상으로 적는다({@code 22:00~25:00}). 그래서 끝이 시작보다 앞이거나 같은 칸은
	 * 넘기는 시간대가 아니라 <b>틀린 값</b>이다(실제 자료의 「토요일 19:00~14:30」) — 비운다.
	 *
	 * @throws IllegalArgumentException 시각 모양이 깨졌을 때
	 */
	public static Parsed pharmacyWindow(String open, String close) {
		if (open == null || close == null) {
			return Parsed.drop(DROP_EMPTY);
		}
		int start = minuteOf(open);
		int end = minuteOf(close);
		if (start == end) {
			return Parsed.drop(DROP_SAME_TIME);
		}
		if (end < start) {
			return Parsed.drop(DROP_REVERSED);
		}
		return build(start, end);
	}

	private static Parsed build(int start, int end) {
		if (start >= MINUTES_PER_DAY || end > MAX_CLOSE_MINUTE) {
			return Parsed.drop(DROP_OUT_OF_RANGE);
		}
		if (end - start < MIN_WINDOW_MINUTES) {
			return Parsed.drop(DROP_TOO_SHORT);
		}
		return Parsed.ok(new Window(start, end));
	}

	/** 「09:30」 → 570. 모양이 깨졌으면 던진다. 시간은 두 자리(0~47), 분은 0~59. */
	static int minuteOf(String clock) {
		Matcher matcher = CLOCK.matcher(clock.trim());
		if (!matcher.matches()) {
			throw new IllegalArgumentException("시각 모양이 올바르지 않다(HH:MM): " + clock);
		}
		int hour = Integer.parseInt(matcher.group(1));
		int minute = Integer.parseInt(matcher.group(2));
		if (hour > 47 || minute > 59) {
			throw new IllegalArgumentException("시각 값이 올바르지 않다: " + clock);
		}
		return hour * 60 + minute;
	}

	// ── 만들기·저장 ─────────────────────────────────────────────────────────

	/** 칸 이름 → 시간대(모르는 날은 null 값, 쉬는 날은 키 없음). */
	public static WeeklyHours of(Map<String, Window> days) {
		for (String key : days.keySet()) {
			if (!DAYS.contains(key)) {
				throw new IllegalArgumentException("모르는 요일 칸이다: " + key);
			}
		}
		return new WeeklyHours(new LinkedHashMap<>(days));
	}

	/**
	 * 저장된 모양({@link #toDetail})을 읽는다. 모양이 틀린 칸은 <b>모르는 날</b>로 읽는다 — DB 의 값 하나가 깨졌다고 조회 전체가
	 * 터지면 안 된다. 객체가 아니면 아무것도 모르는 한 벌이다.
	 */
	public static WeeklyHours fromDetail(Object node) {
		Map<String, Window> days = new LinkedHashMap<>();
		if (node instanceof Map<?, ?> map) {
			for (String key : DAYS) {
				if (!map.containsKey(key)) {
					continue;
				}
				days.put(key, windowOf(map.get(key)));
			}
		}
		return new WeeklyHours(days);
	}

	private static Window windowOf(Object node) {
		if (!(node instanceof Map<?, ?> map) || !(map.get("open") instanceof String open)
				|| !(map.get("close") instanceof String close)) {
			return null;
		}
		try {
			int start = minuteOf(open);
			int end = minuteOf(close);
			return new Window(start, end);
		}
		catch (IllegalArgumentException broken) {
			return null;
		}
	}

	/** 저장 모양 — 칸 이름 → {@code {"open","close"}} 또는 {@code null}(모르는 날). 쉬는 날은 키가 없다. */
	public Map<String, Object> toDetail() {
		Map<String, Object> out = new LinkedHashMap<>();
		for (String key : DAYS) {
			if (!this.days.containsKey(key)) {
				continue;
			}
			Window window = this.days.get(key);
			if (window == null) {
				out.put(key, null);
			}
			else {
				Map<String, Object> pair = new LinkedHashMap<>();
				pair.put("open", window.openText());
				pair.put("close", window.storedClose());
				out.put(key, pair);
			}
		}
		return out;
	}

	// ── 묻기 ────────────────────────────────────────────────────────────────

	/** 시간대가 하나라도 있는가 — 없으면 이 곳의 운영시간은 모르는 것이다. */
	public boolean hasAnyWindow() {
		return this.days.values().stream().anyMatch(window -> window != null);
	}

	/** 그 칸에 시간대가 있는가(그날 연다고 적혀 있는가). */
	public boolean opensOn(String dayKey) {
		return this.days.get(dayKey) != null;
	}

	/** 어느 날이든 닫는 시각이 {@code minuteOfDay}(자정부터 센 분) 이후인 시간대가 있는가 — 늦게까지 여는 곳을 가린다. */
	public boolean anyCloseAtOrAfter(int minuteOfDay) {
		return this.days.values().stream().anyMatch(window -> window != null && window.closeMinute() >= minuteOfDay);
	}

	/**
	 * 그 시각의 판정.
	 *
	 * @param now 서울 시각이다 — 부르는 쪽이 옮겨 준다
	 * @param holiday 오늘이 공휴일인가. true 면 오늘 칸은 {@code Holiday} 다
	 */
	public Status at(LocalDateTime now, boolean holiday) {
		if (!hasAnyWindow()) {
			return new Status(null, null, null);
		}
		int minute = now.getHour() * 60 + now.getMinute();
		String todayKey = holiday ? HOLIDAY : dayKey(now.getDayOfWeek());
		// 어제가 공휴일이었는지는 모른다 — 어제 칸은 요일로만 본다.
		String yesterdayKey = dayKey(now.getDayOfWeek().minus(1));
		Window today = this.days.get(todayKey);
		Window yesterday = this.days.get(yesterdayKey);

		if (today != null && minute >= today.openMinute() && minute < today.closeMinute()) {
			return new Status(true, today, today.closeMinute());
		}
		// 어제 시작해 자정을 넘긴 시간대가 아직 이어지는가.
		if (yesterday != null && yesterday.closeMinute() > MINUTES_PER_DAY
				&& minute < yesterday.closeMinute() - MINUTES_PER_DAY) {
			return new Status(true, today, yesterday.closeMinute() - MINUTES_PER_DAY);
		}
		// 지금 열려 있지 않다. 오늘 칸이 「모른다」면 닫았다고 말하지 않는다.
		if (this.days.containsKey(todayKey) && today == null) {
			return new Status(null, null, null);
		}
		return new Status(false, today, null);
	}

	/** 월요일 → {@code "Monday"}. */
	public static String dayKey(DayOfWeek day) {
		return switch (day) {
			case MONDAY -> "Monday";
			case TUESDAY -> "Tuesday";
			case WEDNESDAY -> "Wednesday";
			case THURSDAY -> "Thursday";
			case FRIDAY -> "Friday";
			case SATURDAY -> "Saturday";
			case SUNDAY -> "Sunday";
		};
	}

	// ── 글자 ────────────────────────────────────────────────────────────────

	private static String clock(int minute) {
		return String.format("%02d:%02d", minute / 60, minute % 60);
	}

	/** 24:00 은 그대로, 그보다 늦은 끝은 다음 날의 시각으로 접는다. */
	private static String closeTextOf(int minute) {
		if (minute == MINUTES_PER_DAY) {
			return "24:00";
		}
		return clock(minute % MINUTES_PER_DAY);
	}
}
