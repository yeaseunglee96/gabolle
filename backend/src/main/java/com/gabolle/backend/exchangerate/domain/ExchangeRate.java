package com.gabolle.backend.exchangerate.domain;

/**
 * 통화 하나의 환율 — S15P21E201-1079.
 *
 * <p>한국수출입은행 {@code exchangeJSON} 응답 한 행. {@code baseRate}(매매기준율)가 흔히 말하는
 * "환율"이다. {@code buyingRate}(전신환매입율)·{@code sellingRate}(전신환매도율)는 은행이
 * 실제로 사고팔 때 적용하는 값이라 매매기준율과 다르다 — 여행자가 환전소에서 보는 값에 더
 * 가깝다.
 */
public record ExchangeRate(
		String currencyCode,
		String currencyName,
		double baseRate,
		double buyingRate,
		double sellingRate) {
}
