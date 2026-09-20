package com.gabolle.backend.weather.application;

import com.gabolle.backend.weather.domain.KmaBaseTime;

/**
 * 기상청 단기예보 조회서비스(getVilageFcst)를 부르는 자리. 실패를 빈 값이 아니라 예외로
 * 던진다 — 날씨는 대신할 추정이 없고, 기온을 지어내면 화면이 실제 예보인 줄 안다.
 */
public interface WeatherVendorPort {

	/**
	 * 기상청이 준 원문 JSON 그대로 돌려준다. 파싱은 부르는 쪽이 한다 — 캐시에 원문을 담아
	 * 캐시 히트든 새로 받았든 같은 파싱 경로를 타게 하기 위해서다.
	 */
	String fetchForecastJson(int nx, int ny, KmaBaseTime baseTime);

	/** 로그와 응답 진단에 남길 이름. */
	String providerName();
}
