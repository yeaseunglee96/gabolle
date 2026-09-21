package com.gabolle.backend.exchangerate.application;

import java.time.LocalDate;

/**
 * 한국수출입은행 환율 정보(AP01) 호출 자리. 실패는 빈 값이 아니라 예외로 던진다.
 * 원문 JSON을 그대로 돌려주고 파싱은 부르는 쪽({@code ExchangeRateService})이 한다.
 */
public interface ExchangeRateVendorPort {

	/**
	 * 은행이 그날 값을 안 냈으면(주말·휴일·고시 전) 빈 배열이 온다. 가장 최근 영업일로 되감는 것은
	 * {@link ExchangeRateService} 가 한다.
	 *
	 * @throws ExchangeRateVendorException 키가 없거나, 호출이 실패했거나, 업체가 결과를 못 줬다
	 */
	String fetchRatesJson(LocalDate searchDate);

	/** 로그와 응답 진단에 남길 이름. */
	String providerName();
}
