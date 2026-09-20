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
import com.gabolle.backend.route.transit.HeadwayJourneyPlanner;
import com.gabolle.backend.route.transit.RaptorPlanner;
import com.gabolle.backend.route.transit.TransitNetwork;
import com.gabolle.backend.route.transit.TransitFareCalculator;
import com.gabolle.backend.route.transit.TransitNetworkPort;
import com.gabolle.backend.route.transit.TransitProperties;

/**
 * 대중교통 경로를 우리 노선망에서 직접 찾는다. 부산 지하철에는 경로 탐색 공개 API 가 없고
 * 카카오모빌리티 길찾기는 자동차만 주기 때문이다.
 *
 * 좌표에서 걸어갈 만한 정류장을 고르고, RaptorPlanner 에 넘기고, 결과를 RouteLeg 로 접는다.
 *
 * 출발 시각을 모르고 낸 답에는 estimated=true 와 이유를 함께 싣는다 — 표시 없이 내보내면
 * 화면이 그것을 실제 소요시간으로 그린다.
 */
@Component
public class TransitRouteAdapter implements RouteProviderPort {

	/** RouteLeg.provider 에 실려 화면이 무엇이 답했는지 알게 한다. */
	public static final String PROVIDER_TRANSIT_NETWORK = "TRANSIT_NETWORK";

	static final String REASON_NO_DEPARTURE_TIME =
			"출발 시각을 몰라 하루의 여러 시각을 재서 가운데 값으로 답했습니다.";

	/** 배차간격으로 낸 값이라는 것을 화면까지 들고 간다 — 도착 시각이 아니라 평균 소요시간이다. */
	static final String REASON_HEADWAY_ESTIMATE =
			"시각표가 없어 노선의 평균 배차간격과 정거장 수로 계산한 값입니다.";

	/**
	 * 출발 시각을 모를 때 기준으로 삼는 시각 — 13:00. 앱 기본 일정 시간대 09:00-18:00 의
	 * 한가운데다. 기준이 없으면 심야버스가 낮 경로로 추천된다.
	 */
	static final int TYPICAL_DAYTIME_MINUTE = 13 * 60;

	private static final ZoneId KST = ZoneId.of("Asia/Seoul");

	private static final double EARTH_RADIUS_M = 6_371_000;

	private static final Logger log = LoggerFactory.getLogger(TransitRouteAdapter.class);

	private final TransitNetworkPort networkPort;

	private final TransitProperties properties;

	private final TransitFareCalculator fareCalculator;

	public TransitRouteAdapter(TransitNetworkPort networkPort, TransitProperties properties,
			TransitFareCalculator fareCalculator) {
		this.networkPort = networkPort;
		this.properties = properties;
		this.fareCalculator = fareCalculator;
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
			// 노선망이 아직 없다. 고장이 아니라 정상 흐름이다 — 호출자가 어림값으로 간다.
			return Optional.empty();
		}

		Map<String, Integer> origins = nearestStops(network, query.originLat(), query.originLng());
		Map<String, Integer> destinations = nearestStops(network, query.destLat(), query.destLng());
		if (origins.isEmpty() || destinations.isEmpty()) {
			// 걸어갈 만한 정류장이 없는 것도 답이다. 억지로 먼 정류장을 붙이면
			// "걸어서 20분 + 지하철" 이 대중교통 경로로 나온다.
			return Optional.empty();
		}

		RaptorPlanner planner = new RaptorPlanner(this.properties.getMaxRides());
		boolean exact = query.hasDepartureTime();
		Optional<RaptorPlanner.Journey> journey = exact
				? planner.plan(network, origins, destinations, minuteOfDay(query))
				: planner.planTypical(network, origins, destinations);

		if (journey.isPresent()) {
			return Optional.of(toLeg(network, journey.get(), exact ? null : REASON_NO_DEPARTURE_TIME));
		}

		// 시각표가 없는 노선(부산 버스 — BIMS 가 시각표를 안 준다)은 위의 탐색기가 못 찾으므로
		// 배차간격으로 낸다. 시각표가 있으면 위쪽이 먼저 답해 여기까지 오지 않는다.
		//
		// 출발 시각을 아는 요청에는 이 길을 쓰지 않는다. 그때 위가 빈 값이면 답은 "그 시각에는
		// 못 간다" 인데, 평균값으로 덮으면 없는 차를 타라고 말하는 것이 된다.
		if (!exact) {
			Optional<RaptorPlanner.Journey> byHeadway =
					new HeadwayJourneyPlanner(this.properties.getRideSpeedKmh())
							.plan(network, origins, destinations, TYPICAL_DAYTIME_MINUTE);
			if (byHeadway.isPresent()) {
				return Optional.of(toLeg(network, byHeadway.get(), REASON_HEADWAY_ESTIMATE));
			}
		}

		// 좌표는 로그에 남기지 않는다 — 사용자가 어디에 있었는지가 로그에 쌓인다.
		log.debug("대중교통 경로를 못 찾았다 (정류장 {}곳 → {}곳)", origins.size(), destinations.size());
		return Optional.empty();
	}

	/** 걸어갈 만한 정류장 → 거기까지 걷는 분. 가까운 것부터 maxAccessStops 개만 본다. */
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

	/** estimateReason 은 어림값일 때만 채우고, 실제 시각표로 잰 것이면 null 이다. */
	private RouteLeg toLeg(TransitNetwork network, RaptorPlanner.Journey journey, String estimateReason) {
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

		// 요금은 구간별 합이 아니라 여정 전체다 — 환승 할인·차액이 그 안에서 끝나기 때문이다.
		// 요금을 모르는 노선이 끼면 null 이 오고, 그대로 싣는다.
		Integer fareKrw = this.fareCalculator.fareKrw(journey, network);
		return new RouteLeg(TravelMode.TRANSIT, distanceM, journey.durationMin(), null, null,
				journey.transferCount(), estimateReason != null, estimateReason,
				PROVIDER_TRANSIT_NETWORK, List.of(), List.copyOf(steps), fareKrw);
	}

	/**
	 * 두 좌표 사이의 큰원 거리(m). 걸어가는 거리가 아니라 직선거리라 실제보다 짧다 —
	 * RouteLeg.distanceM 에만 들어가고 도보 합계로는 쓰지 않는다.
	 */
	private static double haversineM(double lat1, double lng1, double lat2, double lng2) {
		double dLat = Math.toRadians(lat2 - lat1);
		double dLng = Math.toRadians(lng2 - lng1);
		double a = Math.sin(dLat / 2) * Math.sin(dLat / 2) + Math.cos(Math.toRadians(lat1))
				* Math.cos(Math.toRadians(lat2)) * Math.sin(dLng / 2) * Math.sin(dLng / 2);
		return EARTH_RADIUS_M * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
	}
}
