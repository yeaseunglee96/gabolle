package com.gabolle.backend.transit.application;

// 패키지 전용(package-private) 파서와 같은 패키지에 둔다.

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.transit.domain.NearbyBusStop;

import tools.jackson.databind.ObjectMapper;

class TagoNearbyStopsJsonParserTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	@DisplayName("정류소가 여럿이면(배열) 전부 옮긴다")
	void parsesArrayOfStops() {
		String json = """
				{"response":{"header":{"resultCode":"00"},"body":{"items":{"item":[
				{"nodeid":"DJB8001793","nodenm":"해운대해수욕장","citycode":"25","gpslati":35.1587,"gpslong":129.1604},
				{"nodeid":"DJB8001794","nodenm":"해운대역","citycode":"25","gpslati":35.1631,"gpslong":129.1633}
				]}}}}""";

		List<NearbyBusStop> stops = TagoNearbyStopsJsonParser.parse(json, this.objectMapper);

		assertThat(stops).containsExactly(
				new NearbyBusStop("DJB8001793", "해운대해수욕장", "25", 35.1587, 129.1604),
				new NearbyBusStop("DJB8001794", "해운대역", "25", 35.1631, 129.1633));
	}

	@Test
	@DisplayName("🔴 정류소가 정확히 하나면(단일 객체) 그것도 목록 하나로 옮긴다")
	void parsesSingleStopObject() {
		String json = """
				{"response":{"header":{"resultCode":"00"},"body":{"items":{"item":
				{"nodeid":"DJB8001793","nodenm":"해운대해수욕장","citycode":"25","gpslati":35.1587,"gpslong":129.1604}
				}}}}""";

		List<NearbyBusStop> stops = TagoNearbyStopsJsonParser.parse(json, this.objectMapper);

		assertThat(stops).containsExactly(
				new NearbyBusStop("DJB8001793", "해운대해수욕장", "25", 35.1587, 129.1604));
	}

	@Test
	@DisplayName("근처에 정류소가 하나도 없으면 빈 목록이다 — 실패가 아니다")
	void emptyResultIsNotAFailure() {
		String json = """
				{"response":{"header":{"resultCode":"00"},"body":{"totalCount":0,"items":""}}}""";

		List<NearbyBusStop> stops = TagoNearbyStopsJsonParser.parse(json, this.objectMapper);

		assertThat(stops).isEmpty();
	}

	@Test
	@DisplayName("🔴 resultCode 가 정상이 아니면 실패다")
	void nonNormalResultCodeIsAFailure() {
		String json = """
				{"response":{"header":{"resultCode":"30","resultMsg":"SERVICE_KEY_IS_NOT_REGISTERED_ERROR"}}}""";

		assertThatThrownBy(() -> TagoNearbyStopsJsonParser.parse(json, this.objectMapper))
				.isInstanceOf(TransitVendorException.class)
				.satisfies(exception -> assertThat(((TransitVendorException) exception).getCode())
						.isEqualTo("TRANSIT_VENDOR_ERROR"));
	}
}
