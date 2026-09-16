package com.gabolle.backend.route.transit;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 시각표 없이 <b>배차간격</b>으로 대중교통 시간을 낸다 — S15P21E201-1123.
 *
 * <h2>무엇을 더하나</h2>
 *
 * <pre>
 * 걸리는 시간 = 정류장까지 걷기 + 기다리기 + 타고 가기 + 내려서 걷기
 *              (부르는 쪽이 줌)   (배차/2)   (정거장 사이 거리 합 ÷ 속도
 *                                            + 서는 시간 × 중간 정류장 수)
 * </pre>
 *
 * <h2>🔴 왜 이것이 직선 어림값보다 나은가</h2>
 *
 * 지금까지 대중교통 시간은 {@code 직선거리 ÷ 평균속도 18km/h} 하나였다. 그 식에는
 * <b>상수항이 없다.</b> 그래서 거리가 0에 가까우면 시간도 0에 가까워지고, 운영에서
 * <b>219m 를 버스로 1분</b>에 가는 값이 나갔다 — 걸어도 3분이고 기다리는 시간까지 넣으면
 * 10분쯤 걸리는 거리다. 사람이 보면 바로 아는 종류의 틀린 값이다.
 *
 * <p>여기서는 기다리는 시간이 <b>거리와 무관하게</b> 붙는다. 그리고 "이 노선이 실제로
 * 저기까지 가는가" 를 정류장 순번으로 확인한다 — 직선 어림값은 그것을 아예 안 본다.
 *
 * <h2>🔴 아직 한 번 타는 길만 본다</h2>
 *
 * 갈아타는 길은 안 찾는다. 환승을 넣으려면 "어느 정류장에서 갈아탈 수 있나" 를 먼저
 * 만들어야 하고(정류장 8,315곳의 이웃 계산), 그건 이 걸음의 다음이다. 못 찾으면 빈 값을
 * 주고 부르는 쪽이 어림값으로 간다 — <b>없는 답보다 나쁘지 않다.</b>
 *
 * <h2>🔴 시각표를 지어내지 않는다</h2>
 *
 * 돌려주는 {@link RaptorPlanner.Journey} 의 시각은 <b>0분부터 세는 상대 시각</b>이다.
 * "몇 시 몇 분 차" 가 아니다. {@code RouteLeg} 에 절대 시각 칸이 없어서 화면까지 샐
 * 구조도 아니지만, 그 전에 <b>여기서 만들지 않는다.</b>
 */
public final class HeadwayJourneyPlanner {

	/**
	 * 정류장 사이를 달리는 속도(km/h). 🔴 잰 값이 아니다 — {@code RouteProperties} 의
	 * 속도들과 같은 성격이고 같은 이유로 언젠가 실제 이동 기록으로 맞춰야 한다.
	 * 서는 시간은 아래에서 따로 더하므로 이것은 <b>달리는 동안의</b> 속도다.
	 */
	private final double rideSpeedKmh;

	/** 정류장 한 곳에 서느라 드는 초. 중간에 지나는 정류장 수만큼 붙는다. */
	private final int dwellSecondsPerStop;

	private static final double EARTH_RADIUS_M = 6_371_000;

	/**
	 * 정류장 사이 직선거리에 곱하는 값. 정류장은 서로 가까워서 직선과 도로가 크게 다르지
	 * 않지만 같지도 않다. 🔴 잰 값이 아니다.
	 */
	private static final double SEGMENT_DETOUR = 1.15;

	public HeadwayJourneyPlanner(double rideSpeedKmh, int dwellSecondsPerStop) {
		if (rideSpeedKmh <= 0) {
			throw new IllegalArgumentException("차내 속도는 0보다 커야 한다: " + rideSpeedKmh);
		}
		if (dwellSecondsPerStop < 0) {
			throw new IllegalArgumentException("정차 시간은 0 이상이어야 한다: " + dwellSecondsPerStop);
		}
		this.rideSpeedKmh = rideSpeedKmh;
		this.dwellSecondsPerStop = dwellSecondsPerStop;
	}

	/**
	 * 한 번 타서 갈 수 있는 길 중 가장 빠른 것.
	 *
	 * @param network 노선망
	 * @param originAccessMin 출발 후보 정류장 → 거기까지 걸어가는 분
	 * @param destinationAccessMin 도착 후보 정류장 → 거기서 목적지까지 걸어가는 분
	 * @return 경로. 한 번에 가는 노선이 없으면 빈 값
	 */
	public Optional<RaptorPlanner.Journey> plan(TransitNetwork network,
			Map<String, Integer> originAccessMin, Map<String, Integer> destinationAccessMin) {

		RaptorPlanner.Journey best = null;

		for (Map.Entry<String, Integer> origin : originAccessMin.entrySet()) {
			String fromStopId = origin.getKey();
			int accessMin = origin.getValue();

			for (String routeId : network.routesAt(fromStopId)) {
				TransitNetwork.Route route = network.route(routeId);
				if (route == null) {
					continue;
				}
				int fromIndex = network.sequenceOf(routeId, fromStopId);
				if (fromIndex < 0) {
					continue;
				}

				for (Map.Entry<String, Integer> destination : destinationAccessMin.entrySet()) {
					String toStopId = destination.getKey();
					int toIndex = network.sequenceOf(routeId, toStopId);
					// 🔴 같거나 뒤면 이 방향으로는 못 간다. 반대 방향 갈래가 따로 있고
					//    (id 뒤의 #1·#2) 그쪽이 이 반복에서 따로 걸린다.
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
	 *
	 * <p>🔴 기다리는 시간은 <b>탄 구간에 포함시킨다.</b> 따로 떼어 "정류장에서 정류장으로
	 * 7분" 같은 이동을 만들면 화면이 그것을 걷는 것으로 그린다 — 실제로는 서서 기다린 것이다.
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
		double runningSec = (meters * SEGMENT_DETOUR) / (this.rideSpeedKmh * 1000.0 / 3600.0);
		// 마지막 정류장은 내리는 곳이라 서는 시간을 안 센다.
		int intermediateStops = Math.max(0, to - from - 1);
		double dwellSec = (double) intermediateStops * this.dwellSecondsPerStop;
		return Math.max(1, (int) Math.round((runningSec + dwellSec) / 60.0));
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
