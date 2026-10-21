package com.gabolle.backend.transit.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.gabolle.backend.transit.config.TransitProperties;
import com.gabolle.backend.transit.domain.NearbyBusArrivalsQuery;
import com.gabolle.backend.transit.domain.NearbyBusArrivalsResult;

import tools.jackson.databind.ObjectMapper;

/**
 * S15P21E201-1956 — 운영(10/2)에서 버스 도착이 자주 502 였다. 원인은 업체의 근처 정류소 호출이 1.3~5초로 느려
 * 읽기 제한(5초)에 걸린 것. 정류소 목록은 움직이지 않으니 담아 두고, 도착은 잠깐 담아 두며, 업체가 한 번 실패해도
 * 조금 전에 받은 값으로 답한다.
 */
class TransitServiceCacheTest {

	private static final String STOPS_JSON = """
			{"response":{"header":{"resultCode":"00"},"body":{"items":{"item":[
			{"nodeid":"N1","nodenm":"정류소1","citycode":"25","gpslati":35.1,"gpslong":129.1},
			{"nodeid":"N2","nodenm":"정류소2","citycode":"25","gpslati":35.2,"gpslong":129.2}
			]}}}}""";

	private static final String ONE_ARRIVAL_JSON = """
			{"response":{"header":{"resultCode":"00"},"body":{"totalCount":1,"items":{"item":
			{"routeno":"139","arrtime":420,"arrprevstationcnt":3,"vehicletp":"저상버스"}
			}}}}""";

	@Test
	@DisplayName("🔴 근처 정류소는 같은 자리면 다시 부르지 않는다 — 느린 호출을 한 번만 겪는다")
	void nearbyStopsAreCached() {
		FakeVendor vendor = new FakeVendor();
		MutableClock clock = new MutableClock();
		TransitService service = service(vendor, clock);

		service.nearbyArrivals(new NearbyBusArrivalsQuery(35.15321, 129.11861));
		clock.advance(Duration.ofMinutes(2));
		service.nearbyArrivals(new NearbyBusArrivalsQuery(35.15322, 129.11862));

		assertThat(vendor.stopCalls).isEqualTo(1);
	}

	@Test
	@DisplayName("🔴 근처 정류소 호출이 실패해도 전에 받은 목록이 있으면 그것으로 답한다")
	void nearbyStopsFallBackToEarlierList() {
		FakeVendor vendor = new FakeVendor();
		MutableClock clock = new MutableClock();
		TransitService service = service(vendor, clock);
		service.nearbyArrivals(new NearbyBusArrivalsQuery(35.1532, 129.1186));

		clock.advance(Duration.ofHours(1));
		vendor.failStops = true;
		NearbyBusArrivalsResult result = service.nearbyArrivals(new NearbyBusArrivalsQuery(35.1532, 129.1186));

		assertThat(result.stops()).hasSize(2);
	}

	@Test
	@DisplayName("🔴 도착 호출이 한 번 실패해도 90초 안에 받은 값이 있으면 그것으로 답한다")
	void arrivalsFallBackWithinStaleWindow() {
		FakeVendor vendor = new FakeVendor();
		MutableClock clock = new MutableClock();
		TransitService service = service(vendor, clock);
		service.nearbyArrivals(new NearbyBusArrivalsQuery(35.1532, 129.1186));

		clock.advance(Duration.ofSeconds(60));
		vendor.failArrivals.add("N2");
		NearbyBusArrivalsResult result = service.nearbyArrivals(new NearbyBusArrivalsQuery(35.1532, 129.1186));

		assertThat(result.stops()).hasSize(2);
		assertThat(result.stops().get(1).arrivals()).hasSize(1);
	}

	@Test
	@DisplayName("도착은 20초 안이면 다시 부르지 않는다")
	void arrivalsAreBrieflyCached() {
		FakeVendor vendor = new FakeVendor();
		MutableClock clock = new MutableClock();
		TransitService service = service(vendor, clock);
		service.nearbyArrivals(new NearbyBusArrivalsQuery(35.1532, 129.1186));
		clock.advance(Duration.ofSeconds(10));
		service.nearbyArrivals(new NearbyBusArrivalsQuery(35.1532, 129.1186));

		assertThat(vendor.arrivalCalls).containsExactly("N1", "N2");
	}

	@Test
	@DisplayName("오래된 값(90초 넘음)으로는 답하지 않는다 — 틀린 도착 시간을 보이느니 실패를 알린다")
	void staleArrivalsBeyondWindowStillFail() {
		FakeVendor vendor = new FakeVendor();
		MutableClock clock = new MutableClock();
		TransitService service = service(vendor, clock);
		service.nearbyArrivals(new NearbyBusArrivalsQuery(35.1532, 129.1186));

		clock.advance(Duration.ofSeconds(120));
		vendor.failArrivals.add("N2");

		assertThatThrownBy(() -> service.nearbyArrivals(new NearbyBusArrivalsQuery(35.1532, 129.1186)))
				.isInstanceOf(TransitVendorException.class);
	}

	private static TransitService service(FakeVendor vendor, Clock clock) {
		TransitProperties properties = new TransitProperties();
		properties.setMaxStops(2);
		properties.setCandidateStops(2);
		return new TransitService(vendor, properties, new ObjectMapper(), clock);
	}

	private static final class MutableClock extends Clock {

		private Instant now = Instant.parse("2026-10-02T09:00:00Z");

		void advance(Duration d) {
			this.now = this.now.plus(d);
		}

		@Override
		public ZoneOffset getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(java.time.ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return this.now;
		}
	}

	private static final class FakeVendor implements TransitVendorPort {

		int stopCalls;

		boolean failStops;

		final List<String> arrivalCalls = new ArrayList<>();

		final Set<String> failArrivals = new HashSet<>();

		@Override
		public String fetchNearbyStopsJson(double lat, double lng) {
			this.stopCalls++;
			if (this.failStops) {
				throw new TransitVendorException("TRANSIT_VENDOR_UNAVAILABLE", "실패", HttpStatus.BAD_GATEWAY);
			}
			return STOPS_JSON;
		}

		@Override
		public String fetchArrivalsJson(String cityCode, String nodeId) {
			this.arrivalCalls.add(nodeId);
			if (this.failArrivals.contains(nodeId)) {
				throw new TransitVendorException("TRANSIT_VENDOR_UNAVAILABLE", "실패", HttpStatus.BAD_GATEWAY);
			}
			return ONE_ARRIVAL_JSON;
		}

		@Override
		public String providerName() {
			return "TEST_VENDOR";
		}
	}
}
