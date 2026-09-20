package com.gabolle.backend.transit.application;

import java.util.ArrayList;
import java.util.List;

import org.springframework.http.HttpStatus;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * TAGO 응답 공통 검증. 정류소 응답·도착정보 응답이 같은 봉투 모양
 * ({@code response.header.resultCode}, {@code response.body.items.item})을 쓴다.
 */
final class TagoResponseValidator {

	private TagoResponseValidator() {
	}

	/** @throws TransitVendorException 응답을 못 읽었거나, resultCode가 정상이 아니다 */
	static JsonNode readValidatedResponse(String rawJson, ObjectMapper objectMapper) {
		JsonNode root;
		try {
			root = objectMapper.readTree(rawJson);
		}
		catch (JacksonException exception) {
			throw new TransitVendorException("TRANSIT_VENDOR_UNAVAILABLE", "대중교통 응답을 해석하지 못했습니다.",
					HttpStatus.BAD_GATEWAY, exception);
		}

		JsonNode response = root.path("response");
		String resultCode = response.path("header").path("resultCode").asText(null);
		if (!"00".equals(resultCode)) {
			String resultMsg = response.path("header").path("resultMsg").asText("알 수 없는 오류");
			throw new TransitVendorException("TRANSIT_VENDOR_ERROR", "대중교통 조회 실패: " + resultMsg,
					HttpStatus.BAD_GATEWAY);
		}
		return response;
	}

	/**
	 * 공공데이터포털 XML→JSON 변환은 결과가 정확히 하나면 {@code item}을 배열이 아니라 객체
	 * 하나로 준다 — 배열·단일 객체·없음(빈 결과) 셋 다 같은 모양으로 돌려준다.
	 */
	static List<JsonNode> itemsOf(JsonNode itemNode) {
		if (itemNode.isObject()) {
			return List.of(itemNode);
		}
		List<JsonNode> items = new ArrayList<>();
		for (JsonNode item : itemNode) {
			items.add(item);
		}
		return items;
	}
}
