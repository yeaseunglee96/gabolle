package com.gabolle.backend.exchangerate.application;

import java.time.LocalDate;

/**
 * 한국수출입은행 환율 정보(AP01) 호출 자리 — S15P21E201-1079.
 *
 * <p>🔴 {@code WeatherVendorPort}와 같은 이유로 <b>실패를 빈 값이 아니라 예외로 던진다.</b>
 *
 * <p>원문 JSON을 그대로 돌려주고 파싱은 부르는 쪽({@code ExchangeRateService})이 한다.
 */
public interface ExchangeRateVendorPort {

	/**
	 * @param searchDate 이 날짜(영업일 기준)의 환율. 은행이 그날 아직 안 냈으면(주말·휴일 등)
	 *     가장 최근 영업일 값을 대신 준다 — 벤더의 동작이라 여기서 바꾸지 않는다.
	 * @return 한국수출입은행 {@code exchangeJSON} 응답 원문(JSON)
	 * @throws ExchangeRateVendorException 키가 없거나, 호출이 실패했거나, 업체가 결과를 못 줬다
	 */
	String fetchRatesJson(LocalDate searchDate);

	/** 로그와 응답 진단에 남길 이름. */
	String providerName();
}
