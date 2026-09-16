package com.gabolle.backend.transit.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.gabolle.backend.transit.config.TransitProperties;
import com.gabolle.backend.transit.domain.NearbyBusArrivalsQuery;
import com.gabolle.backend.transit.domain.NearbyBusArrivalsResult;

import tools.jackson.databind.ObjectMapper;

/**
 * {@link TransitService} 검증 — S15P21E201-988.
 *
 * <p>실제 Gemini 호출·구조화 출력 파싱은 어댑터 몫이라 여기서는 벤더를 스텁으로 대신한다
 * ({@code WeatherServiceTest}와 같은 방식).
 */
class TransitServiceTest {

	private static final String STOPS_JSON = """
			{"response":{"header":{"resultCode":"00"},"body":{"items":{"item":[
			{"nodeid":"N1","nodenm":"정류소1","citycode":"25","gpslati":35.1,"gpslong":129.1},
			{"nodeid":"N2","nodenm":"정류소2","citycode":"25","gpslati":35.2,"gpslong":129.2},
			{"nodeid":"N3","nodenm":"정류소3","citycode":"25","gpslati":35.3,"gpslong":129.3},
			{"nodeid":"N4","nodenm":"정류소4","citycode":"25","gpslati":35.4,"gpslong":129.4}
			]}}}}""";

	private static final String EMPTY_ARRIVALS_JSON = """
			{"response":{"header":{"resultCode":"00"},"body":{"totalCount":0,"items":""}}}""";

	@Test
	@DisplayName("근처 정류소마다 도착정보를 조회해 합친다")
	void aggregatesArrivalsPerStop() {
		RecordingVendor vendor = new RecordingVendor();
		TransitProperties properties = new TransitProperties();
		properties.setMaxStops(3);
		TransitService service = new TransitService(vendor, properties, new ObjectMapper());

		NearbyBusArrivalsResult result = service.nearbyArrivals(new NearbyBusArrivalsQuery(35.1, 129.1));

		assertThat(result.stops()).hasSize(3);
		assertThat(result.stops().get(0).stop().nodeId()).isEqualTo("N1");
	}

	@Test
	@DisplayName("🔴 근처 정류소가 properties.maxStops 보다 많아도 그만큼만 조회한다")
	void limitsToConfiguredMaxStops() {
		RecordingVendor vendor = new RecordingVendor();
		TransitProperties properties = new TransitProperties();
		properties.setMaxStops(2);
		TransitService service = new TransitService(vendor, properties, new ObjectMapper());

		service.nearbyArrivals(new NearbyBusArrivalsQuery(35.1, 129.1));

		assertThat(vendor.arrivalCalls).containsExactly("N1", "N2");
	}

	@Test
	@DisplayName("🔴 정류소 하나의 도착정보 조회가 실패하면 전체 요청이 실패한다 — 일부만 조용히 빼지 않는다")
	void oneStopFailurePropagatesEntireRequest() {
		FailingSecondArrivalVendor vendor = new FailingSecondArrivalVendor();
		TransitProperties properties = new TransitProperties();
		properties.setMaxStops(3);
		TransitService service = new TransitService(vendor, properties, new ObjectMapper());

		assertThatThrownBy(() -> service.nearbyArrivals(new NearbyBusArrivalsQuery(35.1, 129.1)))
				.isInstanceOf(TransitVendorException.class);
	}

	private static final class RecordingVendor implements TransitVendorPort {

		final List<String> arrivalCalls = new ArrayList<>();

		@Override
		public String fetchNearbyStopsJson(double lat, double lng) {
			return STOPS_JSON;
		}

		@Override
		public String fetchArrivalsJson(String cityCode, String nodeId) {
			this.arrivalCalls.add(nodeId);
			return EMPTY_ARRIVALS_JSON;
		}

		@Override
		public String providerName() {
			return "TEST_VENDOR";
		}
	}

	private static final class FailingSecondArrivalVendor implements TransitVendorPort {

		private int calls = 0;

		@Override
		public String fetchNearbyStopsJson(double lat, double lng) {
			return STOPS_JSON;
		}

		@Override
		public String fetchArrivalsJson(String cityCode, String nodeId) {
			this.calls++;
			if (this.calls == 2) {
				throw new TransitVendorException("TRANSIT_VENDOR_UNAVAILABLE", "실패", HttpStatus.BAD_GATEWAY);
			}
			return EMPTY_ARRIVALS_JSON;
		}

		@Override
		public String providerName() {
			return "TEST_VENDOR";
		}
	}
}
