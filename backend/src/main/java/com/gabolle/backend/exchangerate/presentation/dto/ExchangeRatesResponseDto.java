package com.gabolle.backend.exchangerate.presentation.dto;

import java.time.LocalDate;
import java.util.List;

import com.gabolle.backend.exchangerate.domain.ExchangeRatesResult;

/** {@code GET /api/v1/exchange-rates} 응답 — S15P21E201-1079. */
public record ExchangeRatesResponseDto(LocalDate asOf, List<RateDto> rates) {

	public static ExchangeRatesResponseDto from(ExchangeRatesResult result) {
		return new ExchangeRatesResponseDto(result.asOf(), result.rates().stream().map(RateDto::from).toList());
	}

	/**
	 * @param baseRate 매매기준율 — 흔히 말하는 "환율"
	 * @param buyingRate 전신환매입율 — 은행이 외화를 사들일 때 적용하는 값
	 * @param sellingRate 전신환매도율 — 은행이 외화를 팔 때 적용하는 값(환전 시 실제로 받는 값에 더 가깝다)
	 */
	public record RateDto(String currencyCode, String currencyName, double baseRate, double buyingRate,
			double sellingRate) {

		static RateDto from(com.gabolle.backend.exchangerate.domain.ExchangeRate rate) {
			return new RateDto(rate.currencyCode(), rate.currencyName(), rate.baseRate(), rate.buyingRate(),
					rate.sellingRate());
		}
	}
}
