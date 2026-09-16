package com.gabolle.backend.transit.application;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.transit.domain.BusArrival;

import tools.jackson.databind.ObjectMapper;

/** {@link TagoArrivalsJsonParser} 검증 — S15P21E201-988. */
class TagoArrivalsJsonParserTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	@DisplayName("도착 예정 버스가 여럿이면 전부 옮긴다")
	void parsesArrayOfArrivals() {
		String json = """
				{"response":{"header":{"resultCode":"00"},"body":{"items":{"item":[
				{"routeno":"139","arrtime":420,"arrprevstationcnt":3,"vehicletp":"저상버스"},
				{"routeno":"1003","arrtime":900,"arrprevstationcnt":6,"vehicletp":"일반버스"}
				]}}}}""";

		List<BusArrival> arrivals = TagoArrivalsJsonParser.parse(json, this.objectMapper);

		assertThat(arrivals).containsExactly(
				new BusArrival("139", 420, 3, "저상버스"),
				new BusArrival("1003", 900, 6, "일반버스"));
	}

	@Test
	@DisplayName("그 정류소에 올 버스가 하나도 없으면 빈 목록이다 — 실패가 아니다")
	void emptyResultIsNotAFailure() {
		String json = """
				{"response":{"header":{"resultCode":"00"},"body":{"totalCount":0,"items":""}}}""";

		List<BusArrival> arrivals = TagoArrivalsJsonParser.parse(json, this.objectMapper);

		assertThat(arrivals).isEmpty();
	}
}
