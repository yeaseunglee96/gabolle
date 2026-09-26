package com.gabolle.backend.transit.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.gabolle.backend.transit.config.TransitProperties;
import com.gabolle.backend.transit.domain.NearbyBusArrivalsQuery;
import com.gabolle.backend.transit.domain.NearbyBusArrivalsResult;

import tools.jackson.databind.ObjectMapper;

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

	/** 덤(4·5번째)과 그 너머(6번째 — 불리면 안 된다)까지 보려고 여섯 곳. */
	private static final String SIX_STOPS_JSON = """
			{"response":{"header":{"resultCode":"00"},"body":{"items":{"item":[
			{"nodeid":"N1","nodenm":"정류소1","citycode":"25","gpslati":35.1,"gpslong":129.1},
			{"nodeid":"N2","nodenm":"정류소2","citycode":"25","gpslati":35.2,"gpslong":129.2},
			{"nodeid":"N3","nodenm":"정류소3","citycode":"25","gpslati":35.3,"gpslong":129.3},
			{"nodeid":"N4","nodenm":"정류소4","citycode":"25","gpslati":35.4,"gpslong":129.4},
			{"nodeid":"N5","nodenm":"정류소5","citycode":"25","gpslati":35.5,"gpslong":129.5},
			{"nodeid":"N6","nodenm":"정류소6","citycode":"25","gpslati":35.6,"gpslong":129.6}
			]}}}}""";

	private static final String ONE_ARRIVAL_JSON = """
			{"response":{"header":{"resultCode":"00"},"body":{"totalCount":1,"items":{"item":
			{"routeno":"139","arrtime":420,"arrprevstationcnt":3,"vehicletp":"저상버스"}
			}}}}""";

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
	@DisplayName("🔴 돌려주는 수는 maxStops, 보는 곳은 candidateStops 까지 — 그 너머는 부르지 않는다")
	void limitsToConfiguredMaxStops() {
		ScriptedVendor vendor = new ScriptedVendor(Set.of(), Set.of());
		TransitProperties properties = new TransitProperties();
		properties.setMaxStops(2);
		properties.setCandidateStops(3);
		TransitService service = new TransitService(vendor, properties, new ObjectMapper());

		NearbyBusArrivalsResult result = service.nearbyArrivals(new NearbyBusArrivalsQuery(35.1, 129.1));

		assertThat(vendor.arrivalCalls).containsExactly("N1", "N2");
		assertThat(vendor.extraCalls).containsExactly("N3");
		assertThat(nodeIds(result)).containsExactly("N1", "N2");
	}

	@Test
	@DisplayName("가까운 3곳에 모두 버스가 오면 덤을 부르지 않는다 — 호출 수가 전과 같다")
	void noExtraCallsWhenNearestAllHaveBuses() {
		ScriptedVendor vendor = new ScriptedVendor(Set.of("N1", "N2", "N3"), Set.of());
		TransitService service = new TransitService(vendor, new TransitProperties(), new ObjectMapper());

		NearbyBusArrivalsResult result = service.nearbyArrivals(new NearbyBusArrivalsQuery(35.1, 129.1));

		assertThat(vendor.extraCalls).isEmpty();
		assertThat(nodeIds(result)).containsExactly("N1", "N2", "N3");
	}

	@Test
	@DisplayName("덤도 비었으면 가까운 3곳을 가까운 순 그대로 돌려준다 — 5번째에서 멈춘다")
	void emptyExtrasKeepNearestThree() {
		ScriptedVendor vendor = new ScriptedVendor(Set.of("N1"), Set.of());
		TransitService service = new TransitService(vendor, new TransitProperties(), new ObjectMapper());

		NearbyBusArrivalsResult result = service.nearbyArrivals(new NearbyBusArrivalsQuery(35.1, 129.1));

		assertThat(vendor.extraCalls).containsExactly("N4", "N5");
		assertThat(nodeIds(result)).containsExactly("N1", "N2", "N3");
	}

	@Test
	@DisplayName("🔴 덤이 늦거나 실패하면 버리고 가까운 3곳으로 답한다 — 요청 전체를 502 로 만들지 않는다")
	void slowExtrasAreDroppedNotPropagated() {
		ScriptedVendor vendor = new ScriptedVendor(Set.of("N1", "N4", "N5"), Set.of("N4", "N5"));
		TransitService service = new TransitService(vendor, new TransitProperties(), new ObjectMapper());

		NearbyBusArrivalsResult result = service.nearbyArrivals(new NearbyBusArrivalsQuery(35.1, 129.1));

		assertThat(vendor.extraCalls).containsExactly("N4", "N5");
		assertThat(nodeIds(result)).containsExactly("N1", "N2", "N3");
	}

	@Test
	@DisplayName("🔴 덤에 버스가 오면 앞으로 올라오고, 가장 먼 빈 곳이 빠진다 — 돌려주는 수는 그대로 3")
	void extraWithBusesMovesToFront() {
		ScriptedVendor vendor = new ScriptedVendor(Set.of("N4"), Set.of());
		TransitService service = new TransitService(vendor, new TransitProperties(), new ObjectMapper());

		NearbyBusArrivalsResult result = service.nearbyArrivals(new NearbyBusArrivalsQuery(35.1, 129.1));

		assertThat(nodeIds(result)).containsExactly("N4", "N1", "N2");
	}

	@Test
	@DisplayName("버스 오는 곳이 3곳 차면 남은 덤은 부르지 않는다")
	void stopsCallingExtrasOnceFull() {
		ScriptedVendor vendor = new ScriptedVendor(Set.of("N2", "N3", "N4", "N5"), Set.of());
		TransitService service = new TransitService(vendor, new TransitProperties(), new ObjectMapper());

		NearbyBusArrivalsResult result = service.nearbyArrivals(new NearbyBusArrivalsQuery(35.1, 129.1));

		assertThat(vendor.extraCalls).containsExactly("N4");
		assertThat(nodeIds(result)).containsExactly("N2", "N3", "N4");
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

	private static List<String> nodeIds(NearbyBusArrivalsResult result) {
		return result.stops().stream().map((s) -> s.stop().nodeId()).toList();
	}

	/** 정류소마다 버스가 오는지, 덤 호출이 늦는지를 정해 두는 대역. 보통 호출과 덤 호출을 따로 적는다. */
	private static final class ScriptedVendor implements TransitVendorPort {

		final List<String> arrivalCalls = new ArrayList<>();

		final List<String> extraCalls = new ArrayList<>();

		private final Set<String> withBuses;

		private final Set<String> slowExtras;

		ScriptedVendor(Set<String> withBuses, Set<String> slowExtras) {
			this.withBuses = withBuses;
			this.slowExtras = slowExtras;
		}

		@Override
		public String fetchNearbyStopsJson(double lat, double lng) {
			return SIX_STOPS_JSON;
		}

		@Override
		public String fetchArrivalsJson(String cityCode, String nodeId) {
			this.arrivalCalls.add(nodeId);
			return arrivalsOf(nodeId);
		}

		@Override
		public String fetchExtraArrivalsJson(String cityCode, String nodeId) {
			this.extraCalls.add(nodeId);
			if (this.slowExtras.contains(nodeId)) {
				// 어댑터가 짧은 읽기 제한에 걸렸을 때 던지는 것과 같은 모양
				throw new TransitVendorException("TRANSIT_VENDOR_UNAVAILABLE", "버스 도착정보(덤) 호출에 실패했습니다.",
						HttpStatus.BAD_GATEWAY);
			}
			return arrivalsOf(nodeId);
		}

		private String arrivalsOf(String nodeId) {
			return this.withBuses.contains(nodeId) ? ONE_ARRIVAL_JSON : EMPTY_ARRIVALS_JSON;
		}

		@Override
		public String providerName() {
			return "TEST_VENDOR";
		}
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
