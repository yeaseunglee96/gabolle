package com.gabolle.backend.weather.domain;

import java.util.Optional;

/**
 * 기상청 단기예보 PTY(강수형태) 항목 코드를 옮긴다 —
 * 0=없음, 1=비, 2=비/눈, 3=눈, 4=소나기.
 *
 * 5~7(빗방울·빗방울눈날림·눈날림)은 초단기예보에만 오는 코드라 단기예보(getVilageFcst)에는
 * 안 온다. 그래서 여기 없다 — 온다면 모르는 코드로 본다.
 */
public enum PrecipitationType {

	NONE,
	RAIN,
	RAIN_SNOW,
	SNOW,
	SHOWER;

	/**
	 * 모르는 코드는 빈 값이다. 지어내지 않되 실패로 올리지도 않는다 — 이 칸은 하루 요약에
	 * 나중에 더한 시간별 칸이라, 모르는 코드 하나가 기존 응답(하루 요약)까지 죽이면 안 된다.
	 */
	public static Optional<PrecipitationType> fromKmaCode(String code) {
		return switch (code) {
			case "0" -> Optional.of(NONE);
			case "1" -> Optional.of(RAIN);
			case "2" -> Optional.of(RAIN_SNOW);
			case "3" -> Optional.of(SNOW);
			case "4" -> Optional.of(SHOWER);
			default -> Optional.empty();
		};
	}
}
