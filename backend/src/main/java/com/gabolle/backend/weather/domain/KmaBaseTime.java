package com.gabolle.backend.weather.domain;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * 기상청 단기예보 발표 회차 하나 — S15P21E201-366.
 *
 * <p>기상청 API 의 {@code base_date}(yyyyMMdd)·{@code base_time}(HHmm) 파라미터 값을 만든다.
 */
public record KmaBaseTime(LocalDate baseDate, LocalTime baseTime) {

	private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyyMMdd");

	private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HHmm");

	public String baseDateParam() {
		return this.baseDate.format(DATE_FORMAT);
	}

	public String baseTimeParam() {
		return this.baseTime.format(TIME_FORMAT);
	}

	/** 캐시 열쇠 등에서 이 회차를 구분하는 자리 — 순서를 안 바꾸면 그대로 표에 넣어도 된다. */
	public String key() {
		return baseDateParam() + baseTimeParam();
	}
}
