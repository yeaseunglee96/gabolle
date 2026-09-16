package com.gabolle.backend.transit.application;

import java.util.ArrayList;
import java.util.List;

import com.gabolle.backend.transit.domain.NearbyBusStop;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * TAGO {@code getCrdntPrxmtSttnList}(좌표기반근접정류소목록조회) 응답 원문(JSON)을
 * {@link NearbyBusStop} 목록으로 바꾼다 — S15P21E201-988.
 *
 * <p>🔴 공공데이터포털은 HTTP 200이어도 {@code header.resultCode}가 {@code "00"}이 아니면
 * 실패다({@code KmaForecastJsonParser}와 같은 이유로 반드시 확인한다).
 *
 * <p>🔴 근처에 정류소가 하나도 없는 것은 <b>실패가 아니다</b> — {@code item}이 아예 없으면
 * 빈 목록을 돌려준다. 또한 공공데이터포털 XML→JSON 변환은 결과가 정확히 하나면 {@code item}을
 * 배열이 아니라 객체 하나로 준다 — 배열·단일 객체 둘 다 받는다.
 */
final class TagoNearbyStopsJsonParser {

	private TagoNearbyStopsJsonParser() {
	}

	/** @throws TransitVendorException 응답을 못 읽었거나, resultCode가 정상이 아니다 */
	static List<NearbyBusStop> parse(String rawJson, ObjectMapper objectMapper) {
		JsonNode response = TagoResponseValidator.readValidatedResponse(rawJson, objectMapper);

		JsonNode itemNode = response.path("body").path("items").path("item");
		List<NearbyBusStop> stops = new ArrayList<>();
		for (JsonNode item : TagoResponseValidator.itemsOf(itemNode)) {
			String nodeId = item.path("nodeid").asText(null);
			String nodeName = item.path("nodenm").asText(null);
			String cityCode = item.path("citycode").asText(null);
			if (nodeId == null || nodeName == null || cityCode == null) {
				continue;
			}
			double lat = item.path("gpslati").asDouble();
			double lng = item.path("gpslong").asDouble();
			stops.add(new NearbyBusStop(nodeId, nodeName, cityCode, lat, lng));
		}
		return stops;
	}
}
