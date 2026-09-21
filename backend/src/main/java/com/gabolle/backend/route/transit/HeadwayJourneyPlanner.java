package com.gabolle.backend.route.transit;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 시각표 없이 배차간격으로 대중교통 시간을 낸다.
 * 걸리는 시간 = 정류장까지 걷기 + 배차/2 만큼 기다리기 + 타고 가기 + 내려서 걷기.
 *
 * 기다리는 시간이 거리와 무관하게 붙는 것이 직선 어림값과 다른 점이다. 상수항이 없으면
 * 짧은 거리에서 "219m 를 버스로 1분" 같은 값이 나온다.
 *
 * 한 번 타는 길만 본다 — 환승은 정류장 이웃 계산이 먼저 필요하다. 못 찾으면 빈 값이고
 * 부르는 쪽이 어림값으로 간다.
 *
 * 돌려주는 Journey 의 시각은 0분부터 세는 상대 시각이다. "몇 시 몇 분 차" 가 아니다.
 */
public final class HeadwayJourneyPlanner {

	/** 정차까지 포함한 표정속도(km/h). 근거는 TransitProperties.rideSpeedKmh 에 있다. */
	private final double rideSpeedKmh;

	private static final double EARTH_RADIUS_M = 6_371_000;

	public HeadwayJourneyPlanner(double rideSpeedKmh) {
		if (rideSpeedKmh <= 0) {
			throw new IllegalArgumentException("표정속도는 0보다 커야 한다: " + rideSpeedKmh);
		}
		this.rideSpeedKmh = rideSpeedKmh;
	}

	/**
	 * 한 번 타서 갈 수 있는 길 중 가장 빠른 것. 그 시각에 한 번에 가는 노선이 없으면 빈 값.
	 * referenceMinuteOfDay(자정부터 분)에 다니는 노선만 본다.
	 */
	public Optional<RaptorPlanner.Journey> plan(TransitNetwork network,
			Map<String, Integer> originAccessMin, Map<String, Integer> destinationAccessMin,
			int referenceMinuteOfDay) {

		RaptorPlanner.Journey best = null;

		for (Map.Entry<String, Integer> origin : originAccessMin.entrySet()) {
			String fromStopId = origin.getKey();
			int accessMin = origin.getValue();

			for (String routeId : network.routesAt(fromStopId)) {
				TransitNetwork.Route route = network.route(routeId);
				if (route == null) {
					continue;
				}
				// 그 시각에 안 다니는 노선은 안 본다. 없으면 배차가 짧은 심야버스가
				// 대기 시간 계산에서 이겨 낮 경로로 추천된다.
				if (!route.runsAt(referenceMinuteOfDay)) {
					continue;
				}
				int fromIndex = network.sequenceOf(routeId, fromStopId);
				if (fromIndex < 0) {
					continue;
				}

				for (Map.Entry<String, Integer> destination : destinationAccessMin.entrySet()) {
					String toStopId = destination.getKey();
					int toIndex = network.sequenceOf(routeId, toStopId);
					// 같거나 뒤면 이 방향으로는 못 간다. 반대 방향은 별도 노선(id 뒤의 #1·#2)이라
					// 이 반복에서 따로 걸린다.
					if (toIndex <= fromIndex) {
						continue;
					}

					int rideMin = rideMinutes(network, route, fromIndex, toIndex);
					// 배차간격의 절반이 평균 대기다 — 언제 나갈지 모르고 차는 고르게 온다고 본다.
					int waitMin = Math.max(1, (int) Math.round(route.headwayMin() / 2.0));
					int totalMin = accessMin + waitMin + rideMin + destination.getValue();

					if (best == null || totalMin < best.durationMin()) {
						best = journey(routeId, fromStopId, toStopId, accessMin, waitMin, rideMin,
								destination.getValue());
					}
				}
			}
		}
		return Optional.ofNullable(best);
	}

	/**
	 * 걸어가고, 기다리고, 타고, 걸어 나오는 것을 하나의 상대 시간표로 접는다.
	 * 기다리는 시간은 탄 구간에 포함시킨다 — 따로 떼면 화면이 그것을 걷는 것으로 그린다.
	 */
	private RaptorPlanner.Journey journey(String routeId, String fromStopId, String toStopId,
			int accessMin, int waitMin, int rideMin, int egressMin) {

		int boardAt = accessMin;
		int alightAt = boardAt + waitMin + rideMin;
		List<RaptorPlanner.Ride> rides = List.of(
				new RaptorPlanner.Ride(routeId, fromStopId, toStopId, boardAt, alightAt));
		return new RaptorPlanner.Journey(rides, 0, alightAt + egressMin, 0);
	}

	/** 정류장 순번 {@code from} 에서 {@code to} 까지 달리는 데 걸리는 분. */
	private int rideMinutes(TransitNetwork network, TransitNetwork.Route route, int from, int to) {
		List<String> stopIds = route.stopIds();
		double meters = 0;
		for (int i = from; i < to; i++) {
			TransitNetwork.Stop a = network.stop(stopIds.get(i));
			TransitNetwork.Stop b = network.stop(stopIds.get(i + 1));
			if (a == null || b == null) {
				continue;
			}
			meters += haversineM(a.lat(), a.lng(), b.lat(), b.lng());
		}
		// 우회 계수를 곱하지 않는다. 표정속도를 정류장 좌표의 직선 합으로 쟀기 때문에
		// 여기서 또 늘리면 같은 보정을 두 번 한다.
		double seconds = meters / (this.rideSpeedKmh * 1000.0 / 3600.0);
		return Math.max(1, (int) Math.round(seconds / 60.0));
	}

	private static double haversineM(double lat1, double lng1, double lat2, double lng2) {
		double dLat = Math.toRadians(lat2 - lat1);
		double dLng = Math.toRadians(lng2 - lng1);
		double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
				+ Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
						* Math.sin(dLng / 2) * Math.sin(dLng / 2);
		return EARTH_RADIUS_M * 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
	}
}
