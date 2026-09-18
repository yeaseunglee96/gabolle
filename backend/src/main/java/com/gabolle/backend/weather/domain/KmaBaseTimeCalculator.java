package com.gabolle.backend.weather.domain;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

/**
 * "지금 시각에 부를 수 있는 가장 최근 단기예보 발표 회차" 를 고른다 — S15P21E201-366.
 *
 * <p>기상청 단기예보는 하루 8번, 02·05·08·11·14·17·20·23시에 발표된다 — 기상청 단기예보
 * 조회서비스 공식 문서가 명시하는 발표 스케줄이다.
 *
 * <p>🔴 <b>발표 시각이라고 그 순간 바로 조회할 수 있는 것이 아니다.</b> 기상청이 발표 자료를
 * 실제로 API 서버에 반영하기까지 지연이 있어, 발표시각 + 10분 이후에 조회하라는 것이
 * 기상청 문서와 이 API 를 다루는 공개 자료가 공통으로 안내하는 관례다. 그래서 "지금 이
 * 순간 반영이 끝났다고 볼 수 있는 가장 최근 회차" 는 각 발표시각에 10분을 더한 시각을
 * 기준으로 고른다 — 예를 들어 지금이 08:05 면 아직 08:00 회차가 반영되지 않았을 수 있으니
 * 그 이전 회차인 05:00 을 쓴다.
 *
 * <p>오늘 몫 중 아직 반영 시각이 안 지난 것뿐이면(자정 ~ 02:09) 전날 23:00 회차로 넘어간다.
 */
public final class KmaBaseTimeCalculator {

	/** 하루 8회 발표 시각 — 기상청 단기예보 공식 발표 스케줄. */
	private static final List<LocalTime> ANNOUNCE_TIMES = List.of(
			LocalTime.of(2, 0), LocalTime.of(5, 0), LocalTime.of(8, 0), LocalTime.of(11, 0),
			LocalTime.of(14, 0), LocalTime.of(17, 0), LocalTime.of(20, 0), LocalTime.of(23, 0));

	/** 발표 후 API 반영까지의 지연 — 기상청 문서·공개 자료가 공통으로 안내하는 여유 시간. */
	private static final int AVAILABILITY_DELAY_MINUTES = 10;

	private KmaBaseTimeCalculator() {
	}

	/**
	 * @param now 지금 시각(한국 표준시 기준으로 준 값이어야 한다 — 호출부가 시간대를 맞춘다)
	 * @return 지금 조회해도 반영이 끝났다고 볼 수 있는 가장 최근 발표 회차
	 */
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
}
