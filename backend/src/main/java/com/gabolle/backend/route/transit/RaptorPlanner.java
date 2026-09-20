package com.gabolle.backend.route.transit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * 대중교통 경로 탐색 — RAPTOR.
 *
 * 라운드 k 는 "k번 타고 갈 수 있는 가장 이른 도착시각" 이다. 환승 횟수가 라운드 자체라서
 * 따로 세거나 벌점을 매기지 않고, 도착시각이 같으면 먼저 끝난 라운드가 답이 된다.
 *
 * planTypical 이 주는 값은 "이 시각에 가면 이렇다" 가 아니라 "보통 이 정도 걸린다" 다.
 * 부르는 쪽이 그 차이를 응답에 실어야 한다.
 */
public final class RaptorPlanner {

	/** 하루를 이 개수로 나눠 대표 시각을 뽑는다. 홀수라 중앙값이 실제 표본 하나가 된다. */
	static final int TYPICAL_SAMPLES = 9;

	/** 탄 것 한 번 또는 걸은 것 한 번. routeId 가 null 이면 걸은 것이다. 시각은 자정부터의 분. */
	public record Ride(String routeId, String fromStopId, String toStopId, int departMinOfDay,
			int arriveMinOfDay) {

		public boolean isWalk() {
			return this.routeId == null;
		}

		public int durationMin() {
			return this.arriveMinOfDay - this.departMinOfDay;
		}
	}

	/** 찾은 경로 하나. transferCount 는 탄 횟수 - 1 이고 걷기는 세지 않는다. */
	public record Journey(List<Ride> rides, int departMinOfDay, int arriveMinOfDay, int transferCount) {

		public Journey {
			rides = List.copyOf(Objects.requireNonNull(rides, "경로에 이동이 필요하다"));
		}

		public int durationMin() {
			return this.arriveMinOfDay - this.departMinOfDay;
		}
	}

	private final int maxRounds;

	/**
	 * maxRounds 는 최대 몇 번까지 타 볼 것인가이고, 환승 횟수는 이보다 하나 적다.
	 * 성능이 아니라 사람 때문에 제한한다 — 네 번 갈아타는 길은 짧아도 아무도 안 쓴다.
	 */
	public RaptorPlanner(int maxRounds) {
		if (maxRounds < 1) {
			throw new IllegalArgumentException("최대 탑승 횟수는 1 이상이어야 한다: " + maxRounds);
		}
		this.maxRounds = maxRounds;
	}

	/**
	 * 정해진 시각에 떠날 때의 가장 이른 도착. 못 가면 빈 값.
	 *
	 * 두 access 맵은 정류장 id → 걸어가는 분이다 — origin 쪽은 출발지에서 그 정류장까지,
	 * destination 쪽은 그 정류장에서 목적지까지.
	 */
	public Optional<Journey> plan(TransitNetwork network, Map<String, Integer> originAccessMin,
			Map<String, Integer> destinationAccessMin, int departMinOfDay) {
		Objects.requireNonNull(network, "노선망이 필요하다");
		Objects.requireNonNull(originAccessMin, "출발 정류장이 필요하다");
		Objects.requireNonNull(destinationAccessMin, "도착 정류장이 필요하다");
		if (network.isEmpty() || originAccessMin.isEmpty() || destinationAccessMin.isEmpty()) {
			return Optional.empty();
		}

		List<Map<String, Integer>> arrivalByRound = new ArrayList<>();
		List<Map<String, Ride>> rideByRound = new ArrayList<>();
		// 라운드를 가로지르는 최선 — 가지치기에 쓴다.
		Map<String, Integer> best = new HashMap<>();

		Map<String, Integer> round0 = new HashMap<>();
		for (Map.Entry<String, Integer> entry : originAccessMin.entrySet()) {
			if (network.stop(entry.getKey()) == null) {
				continue;
			}
			int arrival = departMinOfDay + Math.max(0, entry.getValue());
			round0.put(entry.getKey(), arrival);
			best.merge(entry.getKey(), arrival, Math::min);
		}
		if (round0.isEmpty()) {
			return Optional.empty();
		}
		arrivalByRound.add(round0);
		rideByRound.add(new HashMap<>());

		Set<String> marked = new HashSet<>(round0.keySet());
		// 목적지 가지치기 — 목적지에 이미 이보다 늦게 닿는 길은 볼 필요가 없다.
		int bestAtDestination = Integer.MAX_VALUE;
		String bestDestinationStop = null;
		int bestDestinationRound = -1;

		for (int round = 1; round <= this.maxRounds && !marked.isEmpty(); round++) {
			Map<String, Integer> previous = arrivalByRound.get(round - 1);
			Map<String, Integer> current = new HashMap<>();
			Map<String, Ride> rides = new HashMap<>();
			Set<String> nextMarked = new HashSet<>();

			// 표시된 정류장을 지나는 노선마다, 그 노선에서 가장 앞선 표시 정류장을 찾는다.
			Map<String, String> boardStopByRoute = new LinkedHashMap<>();
			for (String stopId : marked) {
				for (String routeId : network.routesAt(stopId)) {
					String existing = boardStopByRoute.get(routeId);
					if (existing == null
							|| network.sequenceOf(routeId, stopId) < network.sequenceOf(routeId, existing)) {
						boardStopByRoute.put(routeId, stopId);
					}
				}
			}

			for (Map.Entry<String, String> entry : boardStopByRoute.entrySet()) {
				String routeId = entry.getKey();
				TransitNetwork.Route route = network.route(routeId);
				if (route == null) {
					continue;
				}
				int startIndex = network.sequenceOf(routeId, entry.getValue());
				TransitNetwork.Trip boarded = null;
				String boardedAt = null;
				int boardedDepart = 0;

				List<String> stopIds = route.stopIds();
				for (int index = startIndex; index < stopIds.size(); index++) {
					String stopId = stopIds.get(index);

					if (boarded != null) {
						int arrival = boarded.departuresMinOfDay().get(index);
						int ceiling = Math.min(best.getOrDefault(stopId, Integer.MAX_VALUE), bestAtDestination);
						if (arrival < ceiling) {
							current.put(stopId, arrival);
							rides.put(stopId, new Ride(routeId, boardedAt, stopId, boardedDepart, arrival));
							best.put(stopId, arrival);
							nextMarked.add(stopId);
							Integer egress = destinationAccessMin.get(stopId);
							if (egress != null && arrival + egress < bestAtDestination) {
								bestAtDestination = arrival + egress;
								bestDestinationStop = stopId;
								bestDestinationRound = round;
							}
						}
					}

					// 여기서 더 이른 차를 탈 수 있으면 갈아탄다. 빼면 앞에서 탄 차에 갇혀
					// 뒤쪽 정류장에서 먼저 오는 차를 놓친다.
					Integer readyAt = previous.get(stopId);
					if (readyAt != null) {
						TransitNetwork.Trip earlier = earliestTripFrom(network, routeId, index, readyAt);
						if (earlier != null && (boarded == null
								|| earlier.departuresMinOfDay().get(index) < boarded.departuresMinOfDay()
										.get(index))) {
							boarded = earlier;
							boardedAt = stopId;
							boardedDepart = earlier.departuresMinOfDay().get(index);
						}
					}
				}
			}

			// 걸어서 갈아타기. 같은 라운드 안에서만 — 걷기는 "탄 횟수" 를 늘리지 않는다.
			for (String stopId : new ArrayList<>(nextMarked)) {
				Integer arrival = current.get(stopId);
				if (arrival == null) {
					continue;
				}
				for (TransitNetwork.Transfer transfer : network.transfersFrom(stopId)) {
					int walked = arrival + transfer.walkMin();
					String target = transfer.toStopId();
					if (walked < Math.min(best.getOrDefault(target, Integer.MAX_VALUE), bestAtDestination)) {
						current.put(target, walked);
						rides.put(target, new Ride(null, stopId, target, arrival, walked));
						best.put(target, walked);
						nextMarked.add(target);
						Integer egress = destinationAccessMin.get(target);
						if (egress != null && walked + egress < bestAtDestination) {
							bestAtDestination = walked + egress;
							bestDestinationStop = target;
							bestDestinationRound = round;
						}
					}
				}
			}

			arrivalByRound.add(current);
			rideByRound.add(rides);
			marked = nextMarked;
		}

		if (bestDestinationStop == null) {
			return Optional.empty();
		}
		return Optional.of(rebuild(rideByRound, bestDestinationRound, bestDestinationStop, departMinOfDay,
				destinationAccessMin.get(bestDestinationStop)));
	}

	/**
	 * 출발 시각을 모를 때 보통 걸리는 시간. 여러 시각에서 탐색해 걸린 시간의 중앙값을 고른다.
	 *
	 * 못 간 시각은 표본에서 뺀다 — 막차 뒤를 "매우 오래 걸린다" 로 섞으면 낮 시간의 답까지
	 * 길어진다. 전부 못 가면 빈 값이다.
	 */
	public Optional<Journey> planTypical(TransitNetwork network, Map<String, Integer> originAccessMin,
			Map<String, Integer> destinationAccessMin) {
		List<Journey> found = new ArrayList<>();
		for (int sample = 0; sample < TYPICAL_SAMPLES; sample++) {
			// 첫차 전과 막차 뒤를 피해 하루의 가운데 구간을 훑는다 — 06:00 ~ 22:00.
			int minute = 360 + (960 * sample) / (TYPICAL_SAMPLES - 1);
			plan(network, originAccessMin, destinationAccessMin, minute).ifPresent(found::add);
		}
		if (found.isEmpty()) {
			return Optional.empty();
		}
		found.sort((left, right) -> Integer.compare(left.durationMin(), right.durationMin()));
		return Optional.of(found.get(found.size() / 2));
	}

	private TransitNetwork.Trip earliestTripFrom(TransitNetwork network, String routeId, int stopIndex,
			int notBefore) {
		for (TransitNetwork.Trip trip : network.tripsOf(routeId)) {
			if (trip.departuresMinOfDay().get(stopIndex) >= notBefore) {
				return trip;
			}
		}
		return null;
	}

	private Journey rebuild(List<Map<String, Ride>> rideByRound, int round, String stopId, int departMinOfDay,
			int egressMin) {
		List<Ride> reversed = new ArrayList<>();
		String cursor = stopId;
		int cursorRound = round;
		while (cursorRound > 0) {
			Ride ride = rideByRound.get(cursorRound).get(cursor);
			if (ride == null) {
					// 이 라운드에서 온 것이 아니면 앞 라운드에서 왔다.
				cursorRound--;
				continue;
			}
			reversed.add(ride);
			cursor = ride.fromStopId();
			if (!ride.isWalk()) {
				cursorRound--;
			}
			else if (rideByRound.get(cursorRound).get(cursor) == null) {
				cursorRound--;
			}
		}
		Collections.reverse(reversed);
		int rideCount = (int) reversed.stream().filter(ride -> !ride.isWalk()).count();
		int arrive = reversed.isEmpty() ? departMinOfDay
				: reversed.get(reversed.size() - 1).arriveMinOfDay() + Math.max(0, egressMin);
		return new Journey(reversed, departMinOfDay, arrive, Math.max(0, rideCount - 1));
	}
}
