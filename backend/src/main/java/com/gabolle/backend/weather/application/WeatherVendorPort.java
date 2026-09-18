package com.gabolle.backend.weather.application;

import com.gabolle.backend.weather.domain.KmaBaseTime;

/**
 * 기상청 단기예보 조회서비스({@code getVilageFcst})를 부르는 자리 — S15P21E201-366.
 *
 * <p>🔴 {@code TranslationVendorPort} 와 같은 이유로 <b>실패를 빈 값이 아니라 예외로 던진다.</b>
 * 날씨는 대신할 추정이 없다 — 업체가 없는데 기온을 지어내면 그건 예보가 아니라 창작이고,
 * 화면은 그것을 실제 예보인 줄 안다.
 */
public interface WeatherVendorPort {

	/**
	 * @return 기상청이 준 원문 JSON 그대로(파싱은 부르는 쪽이 한다 — 캐시에는 원문을 그대로
	 *         담아 두고, 캐시 히트든 새로 받았든 같은 파싱 경로를 타게 하기 위해서다)
	 * @throws WeatherVendorException 키가 없거나, 호출이 실패했거나, 업체가 결과를 못 줬다
	 */
	String fetchForecastJson(int nx, int ny, KmaBaseTime baseTime);

	/** 로그와 응답 진단에 남길 이름. */
	String providerName();
}
