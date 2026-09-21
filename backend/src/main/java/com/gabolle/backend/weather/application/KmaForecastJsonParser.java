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
 * 기상청 getVilageFcst 응답 원문(JSON)을 KmaForecastItem 목록으로 바꾼다. 캐시 히트든 방금
 * 받은 응답이든 같은 경로를 탄다.
 *
 * 기상청은 HTTP 200 이어도 header.resultCode 가 "00" 이 아니면 실패다 — 반드시 확인한다.
 */
final class KmaForecastJsonParser {

	private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;

	private static final DateTimeFormatter TIME_FORMAT = DateTimeFormatter.ofPattern("HHmm");

	private KmaForecastJsonParser() {
	}

	/** 응답을 못 읽었거나 resultCode 가 정상이 아니면 WeatherVendorException. */
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
