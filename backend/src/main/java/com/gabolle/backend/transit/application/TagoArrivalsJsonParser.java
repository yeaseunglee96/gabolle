package com.gabolle.backend.transit.application;

import java.util.ArrayList;
import java.util.List;

import com.gabolle.backend.transit.domain.BusArrival;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * TAGO {@code getSttnAcctoArvlPrearngeInfoList}(버스도착정보) 응답 원문(JSON)을
 * {@link BusArrival} 목록으로 바꾼다. 곧 올 버스가 하나도 없는 것은 실패가 아니라 빈 목록이다.
 */
final class TagoArrivalsJsonParser {

	private TagoArrivalsJsonParser() {
	}

	/** @throws TransitVendorException 응답을 못 읽었거나, resultCode가 정상이 아니다 */
	static List<BusArrival> parse(String rawJson, ObjectMapper objectMapper) {
		JsonNode response = TagoResponseValidator.readValidatedResponse(rawJson, objectMapper);

		JsonNode itemNode = response.path("body").path("items").path("item");
		List<BusArrival> arrivals = new ArrayList<>();
		for (JsonNode item : TagoResponseValidator.itemsOf(itemNode)) {
			String routeNo = item.path("routeno").asText(null);
			if (routeNo == null) {
				continue;
			}
			Integer arrivalSeconds = item.has("arrtime") ? item.path("arrtime").asInt() : null;
			Integer remainingStops = item.has("arrprevstationcnt") ? item.path("arrprevstationcnt").asInt() : null;
			String vehicleType = item.path("vehicletp").asText(null);
			arrivals.add(new BusArrival(routeNo, arrivalSeconds, remainingStops, vehicleType));
		}
		return arrivals;
	}
}
