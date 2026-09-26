package com.gabolle.backend.itinerary.application;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import com.gabolle.backend.trip.domain.Trip;

/**
 * 그날의 활동 시간대 — 일정을 까는 시작과 끝, 그리고 그날이 어떤 날인지.
 *
 * <p>🔴 S15P21E201-1734 — <b>오늘 출발하는 여행을 오후에 만들면 첫날은 그 시각부터 짠다.</b> 전에는 모든 날이 여행의
 * 활동 시작(예: 09:00)부터 짜여서, 14시에 만든 오늘 여행이 「예정보다 5시간 37분 늦음」으로 시작했다(2026-09-26 시연
 * 점검). 사용자 결정(2026-09-26):
 * <ul>
 * <li>부산 시각의 「지금」을 다음 30분 단위로 올린 시각부터 짠다({@link Kind#FROM_NOW}).</li>
 * <li>남은 시간이 {@link #MIN_REMAINING_MINUTES}분보다 짧으면 그날만 끝을 {@link #EVENING_END} 까지 늦춰 식당·야경 위주로
 * 1~2곳({@link Kind#EVENING}).</li>
 * <li>그마저 안 되면 첫날을 비운다({@link Kind#NO_TIME_LEFT}). 당일치기면 일정을 만들지 않는다 — 부르는 쪽이 판단한다.</li>
 * </ul>
 *
 * <p>「지금」은 <b>일정을 처음 만든 시각</b>이다. 만들 때는 그 순간이고, 나중에 고칠 때(재계산)도 처음 만든 시각을 쓴다.
 * 고치는 시각을 쓰면 오전에 들른 곳까지 오후로 밀린다 — 재계산은 들렀는지를 모른 채 그날을 통째로 다시 깐다.
 */
record DayWindow(LocalTime start, LocalTime end, Kind kind) {

	enum Kind {
		/** 여행의 활동 시간대 그대로. */
		USUAL,
		/** 오늘 만든 오늘 여행 — 지금(올림)부터 활동 끝까지. */
		FROM_NOW,
		/** 남은 시간이 모자라 끝을 늦춘 저녁 — 식당·야경 위주로 1~2곳. */
		EVENING,
		/** 저녁으로 늦춰도 넣을 시간이 없다 — 이 날은 비운다. */
		NO_TIME_LEFT
	}

	static final ZoneId ZONE = ZoneId.of("Asia/Seoul");

	/** 「지금」을 이 단위로 올린다 — 나설 준비와 첫 이동의 여유. */
	static final int ROUND_MINUTES = 30;

	/** 남은 시간이 이보다 짧으면 한 곳도 제대로 못 넣는다고 본다 — 체류 60분 + 이동 여유. */
	static final int MIN_REMAINING_MINUTES = 90;

	/** 저녁 날의 끝 — 여행의 활동 끝이 이보다 이르면 이 시각까지 늦춘다. */
	static final LocalTime EVENING_END = LocalTime.of(22, 0);

	/** 저녁 날에 넣는 곳의 상한 — 「1~2곳」. */
	static final int EVENING_MAX_ITEMS = 2;

	/** 여행 전체의 활동 시간대 — 둘째 날부터와 오늘이 아닌 여행의 모든 날. */
	static DayWindow of(Trip trip) {
		return new DayWindow(trip.timeWindowStart(), trip.timeWindowEnd(), Kind.USUAL);
	}

	/**
	 * {@code dayIndex} 째 날의 활동 시간대. 첫날이 일정을 만든 날(부산 시각)과 같고 만든 시각(30분 올림)이 활동 시작보다
	 * 늦을 때만 달라진다.
	 *
	 * @param madeAt 일정을 처음 만든 순간
	 */
	static DayWindow of(Trip trip, int dayIndex, Instant madeAt) {
		DayWindow full = of(trip);
		if (dayIndex != 0 || madeAt == null || !full.known() || trip.startDate() == null) {
			return full;
		}
		ZonedDateTime made = madeAt.atZone(ZONE);
		if (!made.toLocalDate().equals(trip.startDate())) {
			return full;
		}
		int minuteOfDay = made.getHour() * 60 + made.getMinute() + (made.getSecond() > 0 || made.getNano() > 0 ? 1 : 0);
		int rounded = (minuteOfDay + ROUND_MINUTES - 1) / ROUND_MINUTES * ROUND_MINUTES;
		if (rounded >= 24 * 60) {
			return empty(); // 올리니 자정을 넘긴다
		}
		LocalTime now = LocalTime.of(rounded / 60, rounded % 60);
		if (!now.isAfter(full.start())) {
			return full;
		}
		if (enough(now, full.end())) {
			return new DayWindow(now, full.end(), Kind.FROM_NOW);
		}
		LocalTime eveningEnd = full.end().isBefore(EVENING_END) ? EVENING_END : full.end();
		if (enough(now, eveningEnd)) {
			return new DayWindow(now, eveningEnd, Kind.EVENING);
		}
		return empty();
	}

	private static boolean enough(LocalTime from, LocalTime to) {
		return to.isAfter(from) && Duration.between(from, to).toMinutes() >= MIN_REMAINING_MINUTES;
	}

	private static DayWindow empty() {
		return new DayWindow(null, null, Kind.NO_TIME_LEFT);
	}

	boolean known() {
		return this.start != null && this.end != null && this.end.isAfter(this.start);
	}

	boolean evening() {
		return this.kind == Kind.EVENING;
	}

	boolean noTimeLeft() {
		return this.kind == Kind.NO_TIME_LEFT;
	}

	long minutes() {
		return known() ? Duration.between(this.start, this.end).toMinutes() : 0;
	}

	/**
	 * 이 날에 넣을 곳의 수. 여행 전체 시간대보다 짧아진 만큼 줄인다(반올림, 적어도 1). 저녁은 {@link #EVENING_MAX_ITEMS} 를
	 * 넘지 않고, 시간이 없는 날은 0 이다. 보통 날은 그대로다.
	 *
	 * <p>안 줄이면 짧아진 첫날에 하루치를 다 넣으려다 시각표에 안 들어가고, 그러면 그날은 시각이 아예 빈다
	 * ({@code DayTimeLayout} 이 하루에 안 들어가면 시각을 안 준다).
	 */
	int scaledItems(int itemsPerDay, DayWindow full) {
		switch (this.kind) {
			case NO_TIME_LEFT:
				return 0;
			case USUAL:
				return itemsPerDay;
			default:
				break;
		}
		int scaled = full.known()
				? Math.max(1, (int) Math.round((double) itemsPerDay * minutes() / full.minutes()))
				: itemsPerDay;
		scaled = Math.min(scaled, itemsPerDay);
		return this.kind == Kind.EVENING ? Math.min(scaled, EVENING_MAX_ITEMS) : scaled;
	}
}
