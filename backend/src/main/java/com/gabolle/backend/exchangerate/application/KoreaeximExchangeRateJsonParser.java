package com.gabolle.backend.exchangerate.application;

import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;

import com.gabolle.backend.exchangerate.domain.ExchangeRate;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 한국수출입은행 {@code exchangeJSON}(AP01) 응답 원문을 {@link ExchangeRate} 목록으로 바꾼다.
 * 이 API 는 KMA·TAGO 와 달리 {@code response.header} 봉투가 없다 — 배열을 그대로 주고
 * {@code result} 코드가 각 원소에 붙어 있다. 1=성공, 2=DATA 코드 오류, 3=인증키 오류,
 * 4=일일 제한횟수 초과.
 * 영업일이 아닌 날을 물으면 결과가 비어 온다. 가장 최근 영업일로 되감는 것은
 * {@link ExchangeRateService} 가 한다.
 */
final class KoreaeximExchangeRateJsonParser {

	/** 그날 값이 없다(주말·휴일·고시 전). 되감아 다시 물어볼 수 있는 유일한 실패다. */
	static final String NO_DATA_FOR_DATE = "EXCHANGE_RATE_NO_DATA_FOR_DATE";

	private KoreaeximExchangeRateJsonParser() {
	}

	/** @throws ExchangeRateVendorException 응답을 못 읽었거나, 결과가 비었거나, result 가 실패다 */
	static List<ExchangeRate> parse(String rawJson, ObjectMapper objectMapper) {
		JsonNode root;
		try {
			root = objectMapper.readTree(rawJson);
		}
		catch (JacksonException exception) {
			throw new ExchangeRateVendorException("EXCHANGE_RATE_VENDOR_UNAVAILABLE", "환율 응답을 해석하지 못했습니다.",
					HttpStatus.BAD_GATEWAY, exception);
		}

		if (!root.isArray() || root.isEmpty()) {
			// 이 하나만 「그날 값이 없다」이고 나머지 실패와 뜻이 다르다. 부르는 쪽이 이 코드일
			// 때만 하루씩 되감는다 — 인증키 오류·한도 초과까지 되감으면 실패 한 번이 벤더를 여러 번
			// 더 부른다(하루 1,000회 제한).
			throw new ExchangeRateVendorException(NO_DATA_FOR_DATE,
					"그 날짜의 환율 정보가 없습니다 — 영업일이 아닐 수 있습니다.", HttpStatus.BAD_GATEWAY);
		}

		int resultCode = root.get(0).path("result").asInt(-1);
		if (resultCode != 1) {
			throw new ExchangeRateVendorException("EXCHANGE_RATE_VENDOR_ERROR",
					"환율 조회 실패: " + resultMessage(resultCode), HttpStatus.BAD_GATEWAY);
		}

		List<ExchangeRate> rates = new ArrayList<>();
		for (JsonNode item : root) {
			String currencyCode = item.path("cur_unit").asText(null);
			String currencyName = item.path("cur_nm").asText(null);
			Double baseRate = parseRate(item.path("deal_bas_r").asText(null));
			Double buyingRate = parseRate(item.path("ttb").asText(null));
			Double sellingRate = parseRate(item.path("tts").asText(null));
			if (currencyCode == null || currencyName == null || baseRate == null || buyingRate == null
					|| sellingRate == null) {
				continue;
			}
			rates.add(new ExchangeRate(currencyCode, currencyName, baseRate, buyingRate, sellingRate));
		}
		return rates;
	}

	/** 한국수출입은행 환율 값은 "1,378.60" 처럼 천 단위 쉼표가 섞여 온다 — 그대로 파싱하면 안 된다. */
	private static Double parseRate(String raw) {
		if (raw == null || raw.isBlank()) {
			return null;
		}
		try {
			return Double.parseDouble(raw.replace(",", ""));
		}
		catch (NumberFormatException exception) {
			return null;
		}
	}

	private static String resultMessage(int resultCode) {
		return switch (resultCode) {
			case 2 -> "DATA 코드 오류";
			case 3 -> "인증키 오류";
			case 4 -> "일일 호출 제한 초과";
			default -> "알 수 없는 오류(코드 " + resultCode + ")";
		};
	}
}
