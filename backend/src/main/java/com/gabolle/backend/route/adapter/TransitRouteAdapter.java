package com.gabolle.backend.route.adapter;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.gabolle.backend.route.application.RouteProviderPort;
import com.gabolle.backend.route.domain.RouteLeg;
import com.gabolle.backend.route.domain.RouteQuery;
import com.gabolle.backend.route.domain.TravelMode;
import com.gabolle.backend.route.transit.RaptorPlanner;
import com.gabolle.backend.route.transit.TransitNetwork;
import com.gabolle.backend.route.transit.TransitNetworkPort;
import com.gabolle.backend.route.transit.TransitProperties;

/**
 * 대중교통 경로를 우리 노선망에서 직접 찾는다 — S15P21E201-1104.
 *
 * <h2>🔴 왜 업체에 안 묻고 직접 찾나</h2>
 *
 * 부산 지하철은 <b>실시간 경로 탐색 공개 API 가 없다.</b> 카카오·네이버는 2025-05 에
 * 부산교통공사와 개별 제휴를 맺어 받는다 — 그 전에는 그들도 시각표 기반이었다. 우리에게는
 * 그 제휴가 없고, 카카오모빌리티 길찾기는 자동차만 준다
 * ({@link KakaoMobilityRouteAdapter#supports} 가 {@code CAR} 에만 참을 주는 이유).
 *
 * <p>그래서 노선망을 우리가 들고 탐색한다. 경로 탐색은 API 가 아니라 그래프 문제다.
 *
 * <h2>이 어댑터가 하는 일은 셋뿐이다</h2>
 *
 * <ol>
 *   <li>출발·도착 좌표에서 <b>걸어갈 만한 정류장</b>을 고른다</li>
 *   <li>{@link RaptorPlanner} 에 넘긴다 — 탐색 자체는 순수 계산이라 여기 없다</li>
 *   <li>찾은 것을 {@link RouteLeg} 로 접는다</li>
 * </ol>
 *
 * <h2>🔴 모른다를 안다로 바꿔 말하지 않는다</h2>
 *
 * 출발 시각을 알면 시각표에서 실제로 탈 수 있는 차를 찾고, 모르면 하루의 여러 시각을 재서
 * 가운데 값을 준다. <b>뒤쪽은 "이 시각에 가면 이렇다" 가 아니라 "보통 이 정도 걸린다" 라서
 * {@code estimated=true} 와 이유를 함께 싣는다.</b> 표시 없이 내보내면 그건 추정이 아니라
 * 창작이고, 화면은 그것을 실제 소요시간으로 그린다 —
 * {@code StraightLineRouteEstimator} 가 같은 규칙을 지킨다.
 */
@Component
public class TransitRouteAdapter implements RouteProviderPort {

	/** 이 값이 {@code RouteLeg.provider} 에 실린다 — 화면이 「무엇이 답했나」를 알 수 있게. */
	public static final String PROVIDER_TRANSIT_NETWORK = "TRANSIT_NETWORK";

	static final String REASON_NO_DEPARTURE_TIME =
			"출발 시각을 몰라 하루의 여러 시각을 재서 가운데 값으로 답했습니다.";

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	private static final double EARTH_RADIUS_M = 6_371_000;

	private static final Logger log = LoggerFactory.getLogger(TransitRouteAdapter.class);

	private final TransitNetworkPort networkPort;

	private final TransitProperties properties;

	public TransitRouteAdapter(TransitNetworkPort networkPort, TransitProperties properties) {
		this.networkPort = networkPort;
		this.properties = properties;
	}

	@Override
	public boolean supports(TravelMode mode) {
		return mode == TravelMode.TRANSIT;
	}

	@Override
	public String providerName() {
		return PROVIDER_TRANSIT_NETWORK;
	}

	@Override
	public Optional<RouteLeg> find(RouteQuery query) {
		if (!supports(query.mode())) {
			return Optional.empty();
		}
		TransitNetwork network = this.networkPort.network();
		if (network.isEmpty()) {
			// 노선망이 아직 없다. 고장이 아니라 정상 흐름의 한 갈래다 — 호출자가 어림값으로 간다.
			return Optional.empty();
		}

		Map<String, Integer> origins = nearestStops(network, query.originLat(), query.originLng());
		Map<String, Integer> destinations = nearestStops(network, query.destLat(), query.destLng());
		if (origins.isEmpty() || destinations.isEmpty()) {
			// 🔴 걸어갈 만한 정류장이 없다. 이것도 답이다 — 억지로 먼 정류장을 붙이면
			//    "걸어서 20분 + 지하철" 이 대중교통 경로로 나오고, 사람은 그 길을 안 쓴다.
			return Optional.empty();
		}

		RaptorPlanner planner = new RaptorPlanner(this.properties.getMaxRides());
		boolean exact = query.hasDepartureTime();
		Optional<RaptorPlanner.Journey> journey = exact
				? planner.plan(network, origins, destinations, minuteOfDay(query))
				: planner.planTypical(network, origins, destinations);

		if (journey.isEmpty()) {
			// 🔴 좌표를 로그에 남기지 않는다 — 사용자가 어디에 있었는지가 로그에 쌓인다.
			log.debug("대중교통 경로를 못 찾았다 (정류장 {}곳 → {}곳)", origins.size(), destinations.size());
			return Optional.empty();
		}
		return Optional.of(toLeg(network, journey.get(), exact));
	}

	/**
	 * 걸어갈 만한 정류장 → 거기까지 걷는 분.
	 *
	 * <p>가까운 것부터 정해진 개수만 본다. 반경 안에 스무 곳이 있어도 먼 것을 넣으면 답이
	 * 좋아지지 않고 계산만 는다.
	 */
	private Map<String, Integer> nearestStops(TransitNetwork network, double lat, double lng) {
		int radius = this.properties.getAccessRadiusM();
		record Candidate(String stopId, double distanceM) {
		}
		List<Candidate> near = new ArrayList<>();
		for (TransitNetwork.Stop stop : network.stops()) {
			double distance = haversineM(lat, lng, stop.lat(), stop.lng());
			if (distance <= radius) {
				near.add(new Candidate(stop.id(), distance));
			}
		}
		near.sort(Comparator.comparingDouble(Candidate::distanceM));

		Map<String, Integer> access = new LinkedHashMap<>();
		double speedMPerMin = (this.properties.getAccessWalkSpeedKmh() * 1000) / 60.0;
		for (Candidate candidate : near) {
			if (access.size() >= this.properties.getMaxAccessStops()) {
				break;
			}
			access.put(candidate.stopId(), (int) Math.ceil(candidate.distanceM() / speedMPerMin));
		}
		return access;
	}

	private int minuteOfDay(RouteQuery query) {
		ZonedDateTime kst = query.departureAt().atZoneSameInstant(KST);
		return kst.getHour() * 60 + kst.getMinute();
	}

	private RouteLeg toLeg(TransitNetwork network, RaptorPlanner.Journey journey, boolean exact) {
		List<RouteLeg.Step> steps = new ArrayList<>();
		int distanceM = 0;
		for (RaptorPlanner.Ride ride : journey.rides()) {
			TransitNetwork.Stop from = network.stop(ride.fromStopId());
			TransitNetwork.Stop to = network.stop(ride.toStopId());
			int segmentM = (int) Math.round(haversineM(from.lat(), from.lng(), to.lat(), to.lng()));
			distanceM += segmentM;

			String name;
			String guidance;
			if (ride.isWalk()) {
				name = "도보";
				guidance = from.name() + "에서 " + to.name() + "까지 걸어서 갈아탑니다.";
			}
			else {
				TransitNetwork.Route route = network.route(ride.routeId());
				name = route.name();
				guidance = from.name() + "에서 " + route.name() + "을(를) 타고 " + to.name() + "에서 내립니다.";
			}
			steps.add(new RouteLeg.Step(name, guidance, segmentM, ride.durationMin()));
		}

		return new RouteLeg(TravelMode.TRANSIT, distanceM, journey.durationMin(), null, null,
				journey.transferCount(), !exact, exact ? null : REASON_NO_DEPARTURE_TIME,
				PROVIDER_TRANSIT_NETWORK, List.of(), List.copyOf(steps));
	}

	/**
	 * 두 좌표 사이의 큰원 거리(m).
	 *
	 * <p>🔴 <b>이것은 걸어가는 거리가 아니라 직선거리다.</b> 정류장이 가까운지 고르는 데는
	 * 충분하지만, 화면에 "도보 거리" 로 쓰면 실제보다 짧다 — 그래서 여기서 나온 값은
	 * {@code RouteLeg.distanceM} 에만 들어가고 <b>도보 합계로는 쓰이지 않는다.</b>
	 */
	private static double haversineM(double lat1, double lng1, double lat2, double lng2) {
		double dLat = Math.toRadians(lat2 - lat1);
		double dLng = Math.toRadians(lng2 - lng1);
		double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(Math.toRadians(lat1))
				* Math.cos(Math.toRadians(lat2)) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
		return EARTH_RADIUS_M * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
	}
}
