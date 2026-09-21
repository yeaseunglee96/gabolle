package com.gabolle.backend.weather.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * 지금 시각에 부를 수 있는 가장 최근 단기예보 발표 회차를 고른다.
 *
 * 기상청 단기예보는 하루 8번, 02·05·08·11·14·17·20·23시에 발표된다. 발표 자료가 API 서버에
 * 반영되기까지 지연이 있어 발표시각 + 10분을 기준으로 고른다 — 08:05 면 08:00 회차가 아직
 * 없을 수 있으므로 05:00 을 쓴다.
 *
 * 오늘 몫이 전부 아직이면(자정 ~ 02:09) 전날 23:00 회차로 넘어간다.
 */
public final class KmaBaseTimeCalculator {

	/** 하루 8회 발표 시각 — 기상청 단기예보 공식 발표 스케줄. */
	private static final List<LocalTime> ANNOUNCE_TIMES = List.of(
			LocalTime.of(2, 0), LocalTime.of(5, 0), LocalTime.of(8, 0), LocalTime.of(11, 0),
			LocalTime.of(14, 0), LocalTime.of(17, 0), LocalTime.of(20, 0), LocalTime.of(23, 0));

	/** 발표 후 API 반영까지의 지연. 기상청 문서가 안내하는 여유 시간이다. */
	private static final int AVAILABILITY_DELAY_MINUTES = 10;

	private KmaBaseTimeCalculator() {
	}

	/** now 는 한국 표준시 기준이어야 한다 — 시간대는 호출부가 맞춘다. */
	public static KmaBaseTime calculate(LocalDateTime now) {
		LocalDate today = now.toLocalDate();

		// 오늘 회차를 최신 순으로 훑어, 반영 시각(발표 + 10분)이 지난 첫 회차를 쓴다.
		for (int i = ANNOUNCE_TIMES.size() - 1; i >= 0; i--) {
			LocalTime announceTime = ANNOUNCE_TIMES.get(i);
			LocalDateTime availableAt = LocalDateTime.of(today, announceTime).plusMinutes(AVAILABILITY_DELAY_MINUTES);
			if (!now.isBefore(availableAt)) {
				return new KmaBaseTime(today, announceTime);
			}
		}

		// 오늘 몫이 전부 아직이면(자정~02:09) 전날 23:00 회차를 쓴다.
		return new KmaBaseTime(today.minusDays(1), ANNOUNCE_TIMES.get(ANNOUNCE_TIMES.size() - 1));
	}

	/**
	 * 한 회차 앞. 하루 첫 회차(02시)면 전날 마지막 회차(23시)로 넘어간다.
	 * 23시 발표가 오늘을 안 담아서 밤에 「오늘」을 찾을 때 이전 회차를 거슬러 보는 데 쓴다.
	 */
	public static KmaBaseTime previous(KmaBaseTime baseTime) {
		int index = ANNOUNCE_TIMES.indexOf(baseTime.baseTime());
		// 회차 시각은 기상청이 정한 여덟 개뿐이다. 그 밖의 값이면 부르는 쪽이 이미 틀렸다.
		if (index < 0) {
			throw new IllegalArgumentException("기상청 발표 회차가 아닌 시각이다: " + baseTime.baseTime());
		}
		if (index > 0) {
			return new KmaBaseTime(baseTime.baseDate(), ANNOUNCE_TIMES.get(index - 1));
		}
		return new KmaBaseTime(baseTime.baseDate().minusDays(1),
				ANNOUNCE_TIMES.get(ANNOUNCE_TIMES.size() - 1));
	}

	/** 하루 발표 횟수. 거슬러 볼 수 있는 한계를 정할 때 쓴다. */
	public static int announcementsPerDay() {
		return ANNOUNCE_TIMES.size();
	}
}
