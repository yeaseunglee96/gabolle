package com.gabolle.backend.route.adapter;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.ObjectMapper;

import com.gabolle.backend.route.domain.RouteLeg;
import com.gabolle.backend.route.transit.BusanTransitNetworkPort;
import com.gabolle.backend.route.transit.TransitFareCalculator;
import com.gabolle.backend.route.transit.TransitFareTable;
import com.gabolle.backend.route.transit.HeadwayJourneyPlanner;
import com.gabolle.backend.route.transit.RaptorPlanner;
import com.gabolle.backend.route.transit.TransitNetwork;
import com.gabolle.backend.route.transit.TransitProperties;
import com.gabolle.backend.route.domain.RouteQuery;
import com.gabolle.backend.route.domain.TravelMode;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 실제 부산 노선망을 올려서 <b>나오는 값이 말이 되는지</b> 본다 — S15P21E201-1123 · S15P21E201-1310.
 *
 * <p>이 검사가 지키는 것은 "코드가 돈다" 가 아니라 <b>"사람이 보고 이상하다고 하지 않는
 * 값이 나온다"</b> 이다. 운영에서 <b>219m 를 버스로 1분</b>에 가는 값이 나갔던 것이 이
 * 티켓의 출발점이고, 그건 코드가 안 돌아서가 아니라 <b>도는데 틀린 값</b>이었다.
 *
 * <p>🔴 2026-09-19 에 <b>지하철이 들어왔다</b>(S15P21E201-1310). 아래쪽 절이 그것을 잰다 —
 * 특히 <b>버스와 지하철이 한 지도 위에 있는가</b>다. 따로 올라가 있으면 각각은 멀쩡히 돌면서
 * <b>갈아타는 경로만 조용히 안 나온다.</b>
 */
class BusanTransitNetworkIntegrationTest {

	/** 부산역 앞. */
	private static final double BUSAN_STATION_LAT = 35.115;

	private static final double BUSAN_STATION_LNG = 129.042;

	/** 서면역. 부산역과 같은 1호선이라 갈아타지 않고 간다. */
	private static final double SEOMYEON_LAT = 35.1578;

	private static final double SEOMYEON_LNG = 129.0594;

	/** 해운대해수욕장. */
	private static final double HAEUNDAE_LAT = 35.1585;

	private static final double HAEUNDAE_LNG = 129.1598;

	private static TransitNetwork network() {
		return new BusanTransitNetworkPort(new ObjectMapper()).network();
	}

	private static TransitRouteAdapter adapter() {
		// 🔴 S15P21E201-1291 — 요금 계산기가 생성자에 늘었다. 이 파일이 재는 것(노선망으로
		//    경로가 나오는가)은 그대로다 — 진짜 요금표를 넣어 두면 요금도 함께 나온다.
		return new TransitRouteAdapter(new BusanTransitNetworkPort(new ObjectMapper()), new TransitProperties(),
				new TransitFareCalculator(new TransitFareTable(new ObjectMapper())));
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

		Optional<RaptorPlanner.Journey> journey = new HeadwayJourneyPlanner(14.4)
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

	// ── 지하철 — S15P21E201-1310 ────────────────────────────────────────────

	@Test
	@DisplayName("🔴 버스와 지하철이 한 지도 위에 있다 — 따로 올라가면 갈아타는 경로만 조용히 안 나온다")
	void busAndSubwayShareOneNetwork() {
		TransitNetwork loaded = network();

		long busStops = loaded.stops().stream().filter(stop -> stop.kind() == TransitNetwork.Kind.BUS).count();
		long subwayStops = loaded.stops().stream().filter(stop -> stop.kind() == TransitNetwork.Kind.SUBWAY).count();

		assertThat(busStops).as("버스 정류장이 안 올라왔다").isGreaterThan(5_000);
		assertThat(subwayStops).as("지하철 역이 안 올라왔다 — 부산 도시철도는 114역이다").isEqualTo(114);
	}

	@Test
	@DisplayName("🔴 지하철은 평일 것만 올린다 — 셋을 다 올리면 화요일 일정에 일요일 배차가 섞인다")
	void onlyWeekdaySubwayRoutesAreLoaded() {
		TransitNetwork loaded = network();

		List<TransitNetwork.Route> subwayRoutes = subwayRoutesOf(loaded);

		// 4호선 × 상행·하행 = 8. 요일(평일·토·일)을 안 거르면 24가 된다.
		assertThat(subwayRoutes)
				.as("지하철 노선이 %d갈래다 — 8이 아니면 요일 거르기가 안 먹은 것이다", subwayRoutes.size())
				.hasSize(8);
	}

	@Test
	@DisplayName("🔴 식별자가 안 겹친다 — 겹치면 한쪽이 조용히 덮어써지고 「갈 수는 있는데 못 찾는」 경로가 생긴다")
	void theTwoSourcesDoNotShareIds() {
		TransitNetwork loaded = network();

		assertThat(loaded.stops()).as("정류장·역 식별자가 겹쳤다")
				.extracting(TransitNetwork.Stop::id).doesNotHaveDuplicates();
	}

	@Test
	@DisplayName("지하철 이름에 「번」을 안 붙인다 — 붙이면 「1호선번」이 된다")
	void subwayRoutesAreNotCalledNumbers() {
		TransitNetwork loaded = network();

		assertThat(subwayRoutesOf(loaded))
				.extracting(TransitNetwork.Route::name)
				.allSatisfy(name -> assertThat(name).doesNotContain("번"))
				.contains("1호선", "2호선", "3호선", "4호선");
	}

	@Test
	@DisplayName("🔴 소수 배차간격을 잘라 버리지 않는다 — 1호선 6.5분이 6이 되면 기다리는 시간을 낮잡는다")
	void fractionalHeadwayIsRoundedNotTruncated() {
		TransitNetwork loaded = network();

		TransitNetwork.Route line1 = subwayRoutesOf(loaded).stream()
				.filter(route -> route.name().equals("1호선"))
				.findFirst().orElseThrow();

		// 원천이 6.5 다. 자르면 6, 반올림하면 7.
		assertThat(line1.headwayMin()).as("6 이면 잘라 버린 것이다 — 언제나 짧은 쪽으로만 틀린다").isEqualTo(7);
	}

	@Test
	@DisplayName("지하철도 첫차·막차를 싣는다 — 자정을 넘겨 다니는 것이 뒤집히지 않는다")
	void subwayCarriesItsServiceWindow() {
		TransitNetwork loaded = network();

		TransitNetwork.Route line1 = subwayRoutesOf(loaded).stream()
				.filter(route -> route.name().equals("1호선"))
				.findFirst().orElseThrow();

		// 1호선은 05시대에 시작해 자정을 넘겨 끝난다 — first > last 가 정상이다.
		assertThat(line1.firstMinOfDay()).as("첫차가 안 실렸다").isGreaterThan(4 * 60);
		assertThat(line1.runsAt(12 * 60)).as("낮에 안 다니는 것으로 읽혔다").isTrue();
		assertThat(line1.runsAt(3 * 60)).as("새벽 3시에 다니는 것으로 읽혔다").isFalse();
	}

	@Test
	@DisplayName("🔴 서면에서 지하철을 탈 수 있다 — 역과 노선이 실제로 이어져 있다")
	void seomyeonIsServedBySubwayRoutes() {
		TransitNetwork loaded = network();

		List<String> stationIds = loaded.stops().stream()
				.filter(stop -> stop.kind() == TransitNetwork.Kind.SUBWAY)
				.filter(stop -> stop.name().contains("서면"))
				.map(TransitNetwork.Stop::id)
				.toList();
		assertThat(stationIds).as("서면역이 없다").isNotEmpty();

		long linesThere = stationIds.stream()
				.flatMap(id -> loaded.routesAt(id).stream())
				.distinct().count();
		// 서면은 1호선·2호선 환승역이라 방향까지 세면 넷이다.
		assertThat(linesThere).as("서면에 지하철 노선이 %d갈래다 — 환승역인데 너무 적다", linesThere)
				.isGreaterThanOrEqualTo(4);
	}

	@Test
	@DisplayName("🔴 지하철 구간에 요금이 붙는다 — 요금 계산기는 있었는데 잴 대상이 없었다")
	void aSubwayJourneyHasAFare() {
		TransitNetwork loaded = network();
		TransitFareCalculator calculator = new TransitFareCalculator(new TransitFareTable(new ObjectMapper()));

		TransitNetwork.Route line1 = subwayRoutesOf(loaded).stream()
				.filter(route -> route.name().equals("1호선"))
				.findFirst().orElseThrow();
		String from = line1.stopIds().get(0);
		String to = line1.stopIds().get(line1.stopIds().size() - 1);

		RaptorPlanner.Ride ride = new RaptorPlanner.Ride(line1.id(), from, to, 9 * 60, 10 * 60);
		Integer fare = calculator.fareKrw(
				new RaptorPlanner.Journey(List.of(ride), 9 * 60, 10 * 60, 0), loaded);

		// 1호선 끝에서 끝은 10km 를 훨씬 넘는다 — 2구간이다.
		assertThat(fare).as("지하철 구간에 요금이 안 붙었다").isEqualTo(1800);
	}

	@Test
	@DisplayName("🔴 한 노선으로 가는 길은 지하철이 후보가 된다 — 부산역 → 서면은 1호선 한 번이다")
	void aSingleLineTripCanNowGoBySubway() {
		Optional<RouteLeg> leg = adapter().find(new RouteQuery(
				BUSAN_STATION_LAT, BUSAN_STATION_LNG, SEOMYEON_LAT, SEOMYEON_LNG, TravelMode.TRANSIT));

		assertThat(leg).as("부산역에서 서면까지 대중교통 경로를 못 찾았다").isPresent();
		System.out.println("### 부산역 → 서면 " + leg.get().durationMin() + "분 · " + leg.get().steps());

		assertThat(leg.get().steps())
				.as("1호선 한 번이면 가는 길인데 지하철이 안 나왔다: %s", leg.get().steps())
				.anyMatch(step -> step.name().contains("호선"));
	}

	/**
	 * 🔴 <b>갈아타는 길은 아직 안 나온다.</b> 부산역 → 해운대는 1호선에서 2호선으로 갈아타야
	 * 하는데, 탐색기가 <b>한 번 타는 길만</b> 본다({@code HeadwayJourneyPlanner} 주석). 그래서
	 * 그 구간은 지하철이 있어도 버스로 답한다.
	 *
	 * <p>이 시험은 <b>그 한계를 적어 두는 것</b>이다. 못 하는 것을 시험으로 남기지 않으면,
	 * 나중에 환승이 들어왔을 때 <b>무엇이 달라졌는지</b>를 아무도 못 가리킨다. 환승이 되면
	 * 이 시험이 먼저 빨개진다 — 그때 지우면 된다.
	 */
	@Test
	@DisplayName("⚠️ 갈아타야 하는 길은 아직 지하철이 안 나온다 — 환승이 들어오면 이 시험이 먼저 빨개진다")
	void aTripNeedingATransferStillFallsBackToBus() {
		Optional<RouteLeg> leg = adapter().find(new RouteQuery(
				BUSAN_STATION_LAT, BUSAN_STATION_LNG, HAEUNDAE_LAT, HAEUNDAE_LNG, TravelMode.TRANSIT));

		assertThat(leg).isPresent();
		assertThat(leg.get().steps())
				.as("갈아타는 길이 나왔다 — 환승이 들어온 것이라면 이 시험을 지워라: %s", leg.get().steps())
				.hasSize(1);
	}

	private static List<TransitNetwork.Route> subwayRoutesOf(TransitNetwork loaded) {
		return loaded.stops().stream()
				.flatMap(stop -> loaded.routesAt(stop.id()).stream())
				.distinct()
				.map(loaded::route)
				.filter(route -> route != null && route.kind() == TransitNetwork.Kind.SUBWAY)
				.toList();
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
