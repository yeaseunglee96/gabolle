package com.gabolle.backend.route.adapter;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

import com.gabolle.backend.route.domain.RouteLeg;
import com.gabolle.backend.route.transit.BusanBusNetworkPort;
import com.gabolle.backend.route.transit.HeadwayJourneyPlanner;
import com.gabolle.backend.route.transit.RaptorPlanner;
import com.gabolle.backend.route.transit.TransitNetwork;
import com.gabolle.backend.route.transit.TransitProperties;
import com.gabolle.backend.route.domain.RouteQuery;
import com.gabolle.backend.route.domain.TravelMode;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 부산 노선망을 올려서 <b>나오는 값이 말이 되는지</b> 본다 — S15P21E201-1123.
 *
 * <p>이 검사가 지키는 것은 "코드가 돈다" 가 아니라 <b>"사람이 보고 이상하다고 하지 않는
 * 값이 나온다"</b> 이다. 운영에서 <b>219m 를 버스로 1분</b>에 가는 값이 나갔던 것이 이
 * 티켓의 출발점이고, 그건 코드가 안 돌아서가 아니라 <b>도는데 틀린 값</b>이었다.
 */
class BusanBusNetworkIntegrationTest {

	/** 부산역 앞. */
	private static final double BUSAN_STATION_LAT = 35.115;

	private static final double BUSAN_STATION_LNG = 129.042;

	/** 해운대해수욕장. */
	private static final double HAEUNDAE_LAT = 35.1585;

	private static final double HAEUNDAE_LNG = 129.1598;

	private static TransitNetwork network() {
		return new BusanBusNetworkPort(new ObjectMapper()).network();
	}

	private static TransitRouteAdapter adapter() {
		return new TransitRouteAdapter(new BusanBusNetworkPort(new ObjectMapper()), new TransitProperties());
	}

	@Test
	@DisplayName("노선망 파일이 실제로 올라간다 — 정류장 수천 곳과 노선 수백 갈래")
	void theBundledNetworkLoads() {
		TransitNetwork loaded = network();

		assertThat(loaded.isEmpty()).as("노선망이 비어 있다 — 자원 파일을 못 읽은 것이다").isFalse();
		assertThat(loaded.stops())
				.as("부산 정류장은 8천 곳대다. 이보다 훨씬 적으면 파일이 잘린 것이다")
				.hasSizeGreaterThan(5_000);
	}

	@Test
	@DisplayName("부산역에는 여러 노선이 선다 — 정류장과 노선이 실제로 이어져 있다")
	void busanStationIsServedByManyRoutes() {
		TransitNetwork loaded = network();

		List<String> stopIds = loaded.stops().stream()
				.filter(stop -> stop.name().contains("부산역"))
				.map(TransitNetwork.Stop::id)
				.toList();
		assertThat(stopIds).as("부산역이라는 이름의 정류장이 없다").isNotEmpty();

		long routesThere = stopIds.stream().mapToLong(id -> loaded.routesAt(id).size()).sum();
		assertThat(routesThere)
				.as("부산역에 서는 노선 갈래가 너무 적다 — 정류장과 노선을 잇는 순번이 깨진 것이다")
				.isGreaterThan(10);
	}

	@Test
	@DisplayName("🔴 부산역 → 해운대는 자로 잰 시간이 아니라 사람이 납득할 시간으로 나온다")
	void busanStationToHaeundaeTakesAPlausibleTime() {
		Optional<RouteLeg> leg = adapter().find(new RouteQuery(
				BUSAN_STATION_LAT, BUSAN_STATION_LNG, HAEUNDAE_LAT, HAEUNDAE_LNG, TravelMode.TRANSIT));

		assertThat(leg).as("부산역에서 해운대까지 대중교통 경로를 못 찾았다").isPresent();
		RouteLeg found = leg.get();

		// 🔴 이 구간은 직선으로도 15km 가 넘는다. 예전 식(직선 ÷ 18km/h)은 여기서 50분쯤을
		//    냈는데, 그건 기다리는 시간도 서는 시간도 안 센 값이다. 실제로는 한 시간 안팎이다.
		assertThat(found.durationMin())
				.as("부산역→해운대가 %d분으로 나왔다. 사람이 보면 바로 이상한 값이다", found.durationMin())
				.isBetween(30, 120);
		// 값이 얼마로 나왔는지 로그에 남긴다 — 이 검사가 통과해도 사람이 한 번은 눈으로
		// 봐야 하는 종류의 숫자다(RecommendationWithRealPlacesFunctionalTest 와 같은 이유).
		System.out.println("### 부산역 → 해운대 " + found.durationMin() + "분 · 구간 "
				+ found.steps().size() + "개 · " + found.steps());
		assertThat(found.estimated()).as("배차간격으로 낸 값은 추정이라고 말해야 한다").isTrue();
		assertThat(found.estimateReason()).isEqualTo(TransitRouteAdapter.REASON_HEADWAY_ESTIMATE);
		assertThat(found.provider()).isEqualTo(TransitRouteAdapter.PROVIDER_TRANSIT_NETWORK);
	}

	@Test
	@DisplayName("🔴 몇백 미터를 버스로 1분에 가지 않는다 — 기다리는 시간이 거리와 무관하게 붙는다")
	void aShortHopIsNeverOneMinute() {
		TransitNetwork loaded = network();
		// 부산역 정류장 하나를 잡고, 그 노선의 바로 다음 정류장으로 간다 — 몇백 미터다.
		TransitNetwork.Stop from = loaded.stops().stream()
				.filter(stop -> !loaded.routesAt(stop.id()).isEmpty())
				.filter(stop -> stop.name().contains("부산역"))
				.findFirst().orElseThrow();
		String routeId = loaded.routesAt(from.id()).get(0);
		TransitNetwork.Route route = loaded.route(routeId);
		int index = loaded.sequenceOf(routeId, from.id());
		// 맨 끝이면 앞쪽으로 하나 물러선다.
		int nextIndex = (index + 1 < route.stopIds().size()) ? index + 1 : index;
		assertThat(nextIndex).as("이 노선에 다음 정류장이 없다").isNotEqualTo(index);
		TransitNetwork.Stop to = loaded.stop(route.stopIds().get(nextIndex));

		Optional<RaptorPlanner.Journey> journey = new HeadwayJourneyPlanner(22.0, 20)
				.plan(loaded, Map.of(from.id(), 0), Map.of(to.id(), 0),
						TransitRouteAdapter.TYPICAL_DAYTIME_MINUTE);

		assertThat(journey).as("한 정거장 옆인데 경로를 못 찾았다").isPresent();
		// 🔴 기다리는 시간이 없으면 한 정거장은 1분이 된다. 배차간격의 절반이 붙어야 한다 —
		//    부산 버스 배차 중앙값이 10~12분이므로 최소 몇 분은 나온다.
		assertThat(journey.get().durationMin())
				.as("한 정거장 옆인데 %d분이다 — 기다리는 시간이 안 붙었다", journey.get().durationMin())
				.isGreaterThanOrEqualTo(3);
	}

	@Test
	@DisplayName("🔴 낮 경로에 심야버스를 추천하지 않는다 — 오지 않는 버스를 타라고 말하면 안 된다")
	void aNightOnlyRouteIsNeverSuggestedForADaytimeTrip() {
		// 🔴 2026-09-16 에 실제로 났다. 부산역→해운대를 물었더니 `1003(심야)`(22:40~23:45)이
		//    나왔다 — 그 노선의 배차가 실측 10분이라, 채운 값 20분을 쓰는 낮 노선보다 대기가
		//    짧게 계산돼 이겼다. 숫자만 보면 합리적이었지만 사람에게는 오지 않는 버스다.
		Optional<RouteLeg> leg = adapter().find(new RouteQuery(
				BUSAN_STATION_LAT, BUSAN_STATION_LNG, HAEUNDAE_LAT, HAEUNDAE_LNG, TravelMode.TRANSIT));

		assertThat(leg).isPresent();
		assertThat(leg.get().steps()).isNotEmpty();
		assertThat(leg.get().steps())
				.as("낮에 다니지 않는 노선을 추천했다: %s", leg.get().steps())
				.noneMatch(step -> step.name().contains("심야"));
	}

	@Test
	@DisplayName("노선망의 운행 시간대가 실제로 실린다 — 안 실으면 위 검사가 우연히 통과할 수 있다")
	void routesCarryTheirServiceWindow() {
		TransitNetwork loaded = network();

		// 하루 종일(0~1439)로만 채워져 있으면 첫차·막차를 안 읽은 것이다.
		long withRealWindow = loaded.stops().stream().limit(0).count()
				+ countRoutesWithRealServiceWindow(loaded);
		assertThat(withRealWindow)
				.as("첫차·막차가 실린 노선이 없다 — 파일에서 안 읽고 있다")
				.isGreaterThan(100);
	}

	private static long countRoutesWithRealServiceWindow(TransitNetwork loaded) {
		return loaded.stops().stream()
				.flatMap(stop -> loaded.routesAt(stop.id()).stream())
				.distinct()
				.map(loaded::route)
				.filter(route -> route != null)
				.filter(route -> route.firstMinOfDay() != 0
						|| route.lastMinOfDay() != TransitNetwork.MINUTES_PER_DAY - 1)
				.count();
	}

	@Test
	@DisplayName("정류장이 멀면 빈 답을 준다 — 억지로 붙이지 않는다")
	void aPlaceWithNoStopsNearbyGetsNoTransitAnswer() {
		// 남해 먼바다. 걸어갈 만한 정류장이 없다.
		Optional<RouteLeg> leg = adapter().find(new RouteQuery(
				34.60, 128.40, 34.61, 128.41, TravelMode.TRANSIT));

		assertThat(leg).as("정류장이 없는 곳에 대중교통 경로를 만들어 냈다").isEmpty();
	}
}
