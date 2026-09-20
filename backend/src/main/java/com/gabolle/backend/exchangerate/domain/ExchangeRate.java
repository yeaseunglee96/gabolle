package com.gabolle.backend.exchangerate.domain;

/**
 * 한국수출입은행 {@code exchangeJSON} 응답 한 행.
 * {@code baseRate}는 매매기준율, {@code buyingRate}는 전신환매입율, {@code sellingRate}는
 * 전신환매도율 — 뒤의 둘은 은행이 실제로 사고팔 때 적용하는 값이라 매매기준율과 다르다.
 */
public record ExchangeRate(
		String currencyCode,
		String currencyName,
		double baseRate,
		double buyingRate,
		double sellingRate) {
}
