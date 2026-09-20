package com.gabolle.backend.exchangerate.presentation.dto;

import java.time.LocalDate;
import java.util.List;

import com.gabolle.backend.exchangerate.domain.ExchangeRatesResult;

/** {@code GET /api/v1/exchange-rates} 응답. */
public record ExchangeRatesResponseDto(LocalDate asOf, List<RateDto> rates) {

	public static ExchangeRatesResponseDto from(ExchangeRatesResult result) {
		return new ExchangeRatesResponseDto(result.asOf(), result.rates().stream().map(RateDto::from).toList());
	}

	/** {@code baseRate} 매매기준율, {@code buyingRate} 전신환매입율, {@code sellingRate} 전신환매도율. */
	public record RateDto(String currencyCode, String currencyName, double baseRate, double buyingRate,
			double sellingRate) {

		static RateDto from(com.gabolle.backend.exchangerate.domain.ExchangeRate rate) {
			return new RateDto(rate.currencyCode(), rate.currencyName(), rate.baseRate(), rate.buyingRate(),
					rate.sellingRate());
		}
	}
}
