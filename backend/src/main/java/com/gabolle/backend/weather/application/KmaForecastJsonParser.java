package com.gabolle.backend.weather.application;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;

import com.gabolle.backend.weather.domain.KmaForecastItem;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 기상청 {@code getVilageFcst} 응답 원문(JSON)을 {@link KmaForecastItem} 목록으로 바꾼다 —
 * S15P21E201-366.
 *
 * <p>캐시 히트든 방금 받은 응답이든 같은 경로를 타야 하므로 {@code WeatherService} 가 두 경우
 * 모두 이 파서를 쓴다 — {@code WeatherVendorPort} 는 원문 그대로만 돌려준다.
 *
 * <p>🔴 기상청은 HTTP 200 이어도 {@code header.resultCode} 가 {@code "00"} 이 아니면 실패다
 * (예: 키가 안 맞으면 별도 코드로 응답한다) — {@code KakaoMobilityRouteAdapter} 가 카카오의
 * {@code result_code} 를 반드시 보는 것과 같은 이유로, 여기서도 반드시 확인한다.
 */
final class KmaForecastJsonParser {

	private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;

	private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HHmm");

	private KmaForecastJsonParser() {
	}

	/** @throws WeatherVendorException 응답을 못 읽었거나, resultCode 가 정상이 아니다 */
	static List<KmaForecastItem> parse(String rawJson, ObjectMapper objectMapper) {
		JsonNode root;
		try {
			root = objectMapper.readTree(rawJson);
		}
		catch (JacksonException exception) {
			throw new WeatherVendorException("WEATHER_VENDOR_UNAVAILABLE", "기상청 응답을 해석하지 못했습니다.",
					HttpStatus.BAD_GATEWAY, exception);
		}

		JsonNode response = root.path("response");
		String resultCode = response.path("header").path("resultCode").asText(null);
		if (!"00".equals(resultCode)) {
			String resultMsg = response.path("header").path("resultMsg").asText("알 수 없는 오류");
			throw new WeatherVendorException("WEATHER_VENDOR_ERROR", "기상청 조회 실패: " + resultMsg,
					HttpStatus.BAD_GATEWAY);
		}

		JsonNode itemNode = response.path("body").path("items").path("item");
		List<KmaForecastItem> items = new ArrayList<>();
		if (itemNode.isArray()) {
			for (JsonNode item : itemNode) {
				String category = item.path("category").asText(null);
				String fcstDate = item.path("fcstDate").asText(null);
				String fcstTime = item.path("fcstTime").asText(null);
				String value = item.path("fcstValue").asText(null);
				if (category == null || fcstDate == null || fcstTime == null || value == null) {
					continue;
				}
				items.add(new KmaForecastItem(category, LocalDate.parse(fcstDate, DATE_FORMAT),
						LocalTime.parse(fcstTime, TIME_FORMAT), value));
			}
		}
		if (items.isEmpty()) {
			throw new WeatherVendorException("WEATHER_VENDOR_ERROR", "기상청이 예보 항목을 하나도 주지 않았습니다.",
					HttpStatus.BAD_GATEWAY);
		}
		return items;
	}
}
