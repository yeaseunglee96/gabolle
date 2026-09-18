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

	/**
	 * 한 회차 앞 — S15P21E201-1207.
	 *
	 * <p>🔴 <b>23시 발표는 오늘을 안 담는다.</b> 다음 날부터다. 그래서 밤 23시 10분이 지나면
	 * {@link #calculate} 가 고르는 회차에 <b>오늘이 없다.</b> 그런데 <b>20시 발표에는 있고,
	 * 그것은 이미 받아 둔 상태다</b>(미리 받아 두는 작업이 채우고 캐시가 하루 산다).
	 *
	 * <p>이 함수는 그 자료를 <b>찾아가는 길</b>이다. 거슬러 갈 뿐 새로 받지 않는다 —
	 * 「더 길게 받는다」와 「받아 둔 것을 쓴다」는 다른 일이고 이것은 뒤쪽이다.
	 *
	 * <p>하루 첫 회차(02시)면 <b>전날 마지막 회차</b>(23시)로 넘어간다.
	 *
	 * @param baseTime 지금 보고 있는 회차
	 * @return 그 바로 앞 회차
	 */
	public static KmaBaseTime previous(KmaBaseTime baseTime) {
		int index = ANNOUNCE_TIMES.indexOf(baseTime.baseTime());
		// 🔴 목록에 없는 시각이 들어오면 지어내지 않고 멈춘다. 회차 시각은 기상청이 정한
		//    여덟 개뿐이고, 그 밖의 값이 왔다는 것은 부르는 쪽이 이미 틀렸다는 뜻이다.
		if (index < 0) {
			throw new IllegalArgumentException("기상청 발표 회차가 아닌 시각이다: " + baseTime.baseTime());
		}
		if (index > 0) {
			return new KmaBaseTime(baseTime.baseDate(), ANNOUNCE_TIMES.get(index - 1));
		}
		return new KmaBaseTime(baseTime.baseDate().minusDays(1),
				ANNOUNCE_TIMES.get(ANNOUNCE_TIMES.size() - 1));
	}

	/** 하루 발표 횟수 — 거슬러 볼 수 있는 한계를 정할 때 쓴다. 캐시가 하루라 그보다 오래된 것은 없다. */
	public static int announcementsPerDay() {
		return ANNOUNCE_TIMES.size();
	}
}
