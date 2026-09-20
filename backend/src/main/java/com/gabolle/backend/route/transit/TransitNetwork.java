package com.gabolle.backend.route.transit;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * 부산 대중교통 노선망.
 *
 * 부산 지하철에는 경로 탐색 공개 API 가 없어서 그래프를 우리가 들고 있다 — 정류장·역이
 * 꼭짓점이고 "이 노선이 A 다음 B 에 선다" 가 간선이면 나머지는 계산이다.
 *
 * 이 표현은 RAPTOR 가 읽기 좋게 색인돼 있다. 탐색기({@link RaptorPlanner})는 라운드마다 "이
 * 정류장을 지나는 노선이 무엇인가" 와 "그 노선에서 이 정류장이 몇 번째인가" 를 수없이 묻는데,
 * 그때마다 훑으면 정류장 수 곱하기 노선 수만큼 걸린다. 만들 때 한 번 뒤집어 둔 것이
 * {@link #routesAt(String)} 과 {@link #sequenceOf(String, String)} 이다.
 *
 * 불변이다. 노선망은 운행 개편이 아니면 바뀌지 않으므로 한 번 만들어 계속 쓴다.
 */
public final class TransitNetwork {

	/** 하루는 1,440분이다. 운행 시간대를 분으로 다룰 때 쓴다. */
	public static final int MINUTES_PER_DAY = 24 * 60;

	private static void requireMinuteOfDay(int value, String what) {
		if (value < 0 || value >= MINUTES_PER_DAY) {
			throw new IllegalArgumentException(what + " 시각이 하루 범위를 벗어났다: " + value);
		}
	}

	/** 대중교통 종류. 요금·환승 규칙이 달라서 섞지 않는다. */
	public enum Kind {

		BUS, SUBWAY

	}

	/**
	 * 정류장 또는 역.
	 *
	 * @param id 원천이 준 식별자. 버스는 BIMS 정류소번호, 지하철은 역코드
	 */
	public record Stop(String id, String name, double lat, double lng, Kind kind) {

		public Stop {
			requireText(id, "정류장 id");
			requireText(name, "정류장 이름");
			if (Double.isNaN(lat) || lat < -90 || lat > 90) {
				throw new IllegalArgumentException("정류장 위도 범위를 벗어났다: " + lat);
			}
			if (Double.isNaN(lng) || lng < -180 || lng > 180) {
				throw new IllegalArgumentException("정류장 경도 범위를 벗어났다: " + lng);
			}
			Objects.requireNonNull(kind, "정류장 종류가 필요하다");
		}
	}

	/**
	 * 한 방향으로 도는 노선 하나. 상행과 하행은 서로 다른 노선으로 둔다 — 정류장 순서가 단순히
	 * 뒤집힌 것이 아니라 서로 다른 정류장에 서는 경우가 흔하다(회차 구간·편도 정류장).
	 *
	 * @param id 노선 식별자. 방향까지 포함한 값이어야 한다
	 * @param name 사람에게 보여줄 이름 (예: "1호선 다대포해수욕장행", "1001번")
	 * @param stopIds 서는 순서대로의 정류장 id. 순서가 곧 그래프의 간선이다
	 * @param headwayMin 배차간격(분). 출발 시각을 모를 때 평균 대기를 내는 데 쓴다
	 * @param firstMinOfDay 첫차 시각(자정부터 분)
	 * @param lastMinOfDay 막차 시각(자정부터 분). {@code firstMinOfDay} 보다 작을 수 있다 —
	 *     자정을 넘겨 다니는 노선이다(예: 23:30~01:10). {@link #runsAt(int)} 가 그것을 본다
	 * @param fareType 요금표에서 이 노선을 가리키는 이름 — {@code "일반버스"}·{@code "마을버스"}
	 *     같은 원천의 값 그대로다. 모르면 {@code null} 이고, 그러면 이 노선이 낀 여정은 요금을
	 *     못 낸다. 지하철은 종류가 하나뿐이라 {@code null} 이어도 된다
	 */
	public record Route(String id, String name, Kind kind, List<String> stopIds, int headwayMin,
			int firstMinOfDay, int lastMinOfDay, String fareType) {

		/**
		 * {@code fareType} 없이 만드는 자리 — 요금을 모르는 노선이 된다({@code null}). 종류를
		 * 모르면 어느 요금표 줄을 볼지도 모르는 것이라 지어내지 않는다.
		 */
		public Route(String id, String name, Kind kind, List<String> stopIds, int headwayMin,
				int firstMinOfDay, int lastMinOfDay) {
			this(id, name, kind, stopIds, headwayMin, firstMinOfDay, lastMinOfDay, null);
		}

		/**
		 * 운행 시간대를 모르는 노선 — 하루 종일 다니는 것으로 본다. 모르는 것을 "안 다닌다" 로
		 * 두면 그 노선이 조용히 사라진다.
		 */
		public Route(String id, String name, Kind kind, List<String> stopIds, int headwayMin) {
			this(id, name, kind, stopIds, headwayMin, 0, MINUTES_PER_DAY - 1, null);
		}

		/**
		 * 그 시각에 이 노선이 다니는가. 이것이 없으면 배차가 짧은 심야버스가 낮 경로로 추천된다 —
		 * 대기 시간만 보면 이기지만 사람에게는 오지 않는 버스다.
		 */
		public boolean runsAt(int minuteOfDay) {
			if (this.firstMinOfDay <= this.lastMinOfDay) {
				return minuteOfDay >= this.firstMinOfDay && minuteOfDay <= this.lastMinOfDay;
			}
			// 자정을 넘겨 다닌다 — 첫차 이후이거나 막차 이전이면 운행 중이다.
			return minuteOfDay >= this.firstMinOfDay || minuteOfDay <= this.lastMinOfDay;
		}

		public Route {
			requireText(id, "노선 id");
			requireText(name, "노선 이름");
			Objects.requireNonNull(kind, "노선 종류가 필요하다");
			Objects.requireNonNull(stopIds, "노선의 정류장 목록이 필요하다");
			if (stopIds.size() < 2) {
				// 정류장이 하나뿐인 노선은 탈 수 없다. 그런 줄이 섞여 들어오면 탐색기가 "탔는데
				// 못 내린다" 는 상태를 만든다.
				throw new IllegalArgumentException("노선 " + id + " 의 정류장이 " + stopIds.size()
						+ "개다. 두 곳 이상이어야 탈 수 있다");
			}
			if (headwayMin <= 0) {
				throw new IllegalArgumentException("노선 " + id + " 의 배차간격은 1분 이상이어야 한다: " + headwayMin);
			}
			requireMinuteOfDay(firstMinOfDay, "노선 " + id + " 의 첫차");
			requireMinuteOfDay(lastMinOfDay, "노선 " + id + " 의 막차");
			stopIds = List.copyOf(stopIds);
		}
	}

	/**
	 * 그 노선을 실제로 도는 차 한 대 — 시각표의 한 줄. 지하철 시각표 API 의 열차번호 하나가
	 * 여기 대응하고, 연속한 두 도착시각의 차가 곧 역간 소요시간이다.
	 *
	 * @param departuresMinOfDay 노선의 정류장 순서와 같은 길이의 출발시각(자정부터 분).
	 *     막차가 자정을 넘으면 1440 을 넘는 값이 된다 — 날짜를 쪼개지 않는 편이 비교가 쉽다
	 */
	public record Trip(String routeId, List<Integer> departuresMinOfDay) {

		public Trip {
			requireText(routeId, "운행의 노선 id");
			Objects.requireNonNull(departuresMinOfDay, "운행의 출발시각 목록이 필요하다");
			if (departuresMinOfDay.size() < 2) {
				throw new IllegalArgumentException("운행 " + routeId + " 의 출발시각이 " + departuresMinOfDay.size()
						+ "개다. 두 곳 이상이어야 한다");
			}
			for (int i = 1; i < departuresMinOfDay.size(); i++) {
				Integer previous = departuresMinOfDay.get(i - 1);
				Integer current = departuresMinOfDay.get(i);
				Objects.requireNonNull(previous, "운행 " + routeId + " 에 빈 출발시각이 있다");
				Objects.requireNonNull(current, "운행 " + routeId + " 에 빈 출발시각이 있다");
				if (current < previous) {
					// 시각이 거꾸로 가면 탐색기가 "타기 전에 내린다" 를 정상으로 본다. 자정을 넘는
					// 운행은 1440 을 더해서 넣으므로 여기서는 거꾸로 갈 수 없다.
					throw new IllegalArgumentException("운행 " + routeId + " 의 출발시각이 거꾸로 간다: "
							+ previous + " 다음에 " + current);
				}
			}
			departuresMinOfDay = List.copyOf(departuresMinOfDay);
		}
	}

	/**
	 * 정류장 사이를 걸어서 갈아타는 것. 같은 역의 호선 간 환승도 여기 들어간다 — 환승을 걷기로
	 * 표현하면 "다른 역까지 걸어가서 타는 것" 과 계산이 하나가 된다.
	 */
	public record Transfer(String fromStopId, String toStopId, int walkMin) {

		public Transfer {
			requireText(fromStopId, "환승 출발 정류장");
			requireText(toStopId, "환승 도착 정류장");
			if (fromStopId.equals(toStopId)) {
				throw new IllegalArgumentException("같은 정류장으로의 환승은 뜻이 없다: " + fromStopId);
			}
			if (walkMin < 0) {
				throw new IllegalArgumentException("환승 걷는 시간은 0 이상이어야 한다: " + walkMin);
			}
		}
	}

	private final Map<String, Stop> stops;

	private final Map<String, Route> routes;

	private final Map<String, List<Trip>> tripsByRoute;

	private final Map<String, List<String>> routeIdsByStop;

	private final Map<String, Integer> sequenceByRouteAndStop;

	private final Map<String, List<Transfer>> transfersByStop;

	private TransitNetwork(Map<String, Stop> stops, Map<String, Route> routes, Map<String, List<Trip>> tripsByRoute,
			Map<String, List<String>> routeIdsByStop, Map<String, Integer> sequenceByRouteAndStop,
			Map<String, List<Transfer>> transfersByStop) {
		this.stops = stops;
		this.routes = routes;
		this.tripsByRoute = tripsByRoute;
		this.routeIdsByStop = routeIdsByStop;
		this.sequenceByRouteAndStop = sequenceByRouteAndStop;
		this.transfersByStop = transfersByStop;
	}

	/** 비어 있는 노선망. 자료가 아직 없을 때 쓴다 — 탐색기는 빈 답을 돌려주고 호출자가 어림값으로 간다. */
	public static TransitNetwork empty() {
		return new TransitNetwork(Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of());
	}

	/**
	 * 노선망을 만든다. 어긋남은 전부 여기서 잡는다 — 노선이 가리키는 정류장이 없거나, 운행의
	 * 시각 개수가 노선의 정류장 수와 다르면 만들 때 터진다. 탐색 중에 발견하면 이미 답이 반쯤
	 * 만들어진 뒤라 조용히 빠진 경로로 나온다.
	 */
	public static TransitNetwork of(List<Stop> stops, List<Route> routes, List<Trip> trips,
			List<Transfer> transfers) {
		Objects.requireNonNull(stops, "정류장 목록이 필요하다");
		Objects.requireNonNull(routes, "노선 목록이 필요하다");
		Objects.requireNonNull(trips, "운행 목록이 필요하다");
		Objects.requireNonNull(transfers, "환승 목록이 필요하다");

		Map<String, Stop> stopById = new LinkedHashMap<>();
		for (Stop stop : stops) {
			if (stopById.put(stop.id(), stop) != null) {
				throw new IllegalArgumentException("정류장 id 가 겹친다: " + stop.id());
			}
		}

		Map<String, Route> routeById = new LinkedHashMap<>();
		Map<String, List<String>> routeIdsByStop = new HashMap<>();
		Map<String, Integer> sequenceByRouteAndStop = new HashMap<>();
		for (Route route : routes) {
			if (routeById.put(route.id(), route) != null) {
				throw new IllegalArgumentException("노선 id 가 겹친다: " + route.id());
			}
			List<String> stopIds = route.stopIds();
			for (int index = 0; index < stopIds.size(); index++) {
				String stopId = stopIds.get(index);
				if (!stopById.containsKey(stopId)) {
					throw new IllegalArgumentException(
							"노선 " + route.id() + " 이 없는 정류장을 가리킨다: " + stopId);
				}
				routeIdsByStop.computeIfAbsent(stopId, key -> new ArrayList<>()).add(route.id());
				// 같은 노선이 한 정류장에 두 번 서면(순환) 앞선 것만 남긴다. 뒤에 것으로 타면
				// 한 바퀴를 더 도는 셈이라 언제나 손해다.
				sequenceByRouteAndStop.putIfAbsent(key(route.id(), stopId), index);
			}
		}

		Map<String, List<Trip>> tripsByRoute = new HashMap<>();
		for (Trip trip : trips) {
			Route route = routeById.get(trip.routeId());
			if (route == null) {
				throw new IllegalArgumentException("운행이 없는 노선을 가리킨다: " + trip.routeId());
			}
			if (trip.departuresMinOfDay().size() != route.stopIds().size()) {
				throw new IllegalArgumentException("운행 " + trip.routeId() + " 의 출발시각이 "
						+ trip.departuresMinOfDay().size() + "개인데 노선의 정류장은 " + route.stopIds().size()
						+ "곳이다. 개수가 같아야 한다");
			}
			tripsByRoute.computeIfAbsent(trip.routeId(), key -> new ArrayList<>()).add(trip);
		}
		// 먼저 떠나는 차부터 본다. 탐색기가 "탈 수 있는 가장 이른 차" 를 찾을 때 이 순서에
		// 기댄다 — 정렬을 탐색기에 맡기면 라운드마다 다시 정렬하게 된다.
		tripsByRoute.replaceAll((routeId, list) -> {
			list.sort((left, right) -> Integer.compare(left.departuresMinOfDay().get(0),
					right.departuresMinOfDay().get(0)));
			return List.copyOf(list);
		});

		Map<String, List<Transfer>> transfersByStop = new HashMap<>();
		for (Transfer transfer : transfers) {
			if (!stopById.containsKey(transfer.fromStopId()) || !stopById.containsKey(transfer.toStopId())) {
				throw new IllegalArgumentException("환승이 없는 정류장을 가리킨다: " + transfer.fromStopId() + " → "
						+ transfer.toStopId());
			}
			transfersByStop.computeIfAbsent(transfer.fromStopId(), key -> new ArrayList<>()).add(transfer);
		}

		routeIdsByStop.replaceAll((stopId, list) -> List.copyOf(list));
		transfersByStop.replaceAll((stopId, list) -> List.copyOf(list));
		return new TransitNetwork(Map.copyOf(stopById), Map.copyOf(routeById), Map.copyOf(tripsByRoute),
				Map.copyOf(routeIdsByStop), Map.copyOf(sequenceByRouteAndStop), Map.copyOf(transfersByStop));
	}

	public boolean isEmpty() {
		return this.stops.isEmpty();
	}

	public Stop stop(String stopId) {
		return this.stops.get(stopId);
	}

	public Route route(String routeId) {
		return this.routes.get(routeId);
	}

	public java.util.Collection<Stop> stops() {
		return this.stops.values();
	}

	/** 이 정류장을 지나는 노선들. 없으면 빈 목록 — RAPTOR 가 라운드마다 부른다. */
	public List<String> routesAt(String stopId) {
		return this.routeIdsByStop.getOrDefault(stopId, Collections.emptyList());
	}

	/** 그 노선에서 이 정류장이 몇 번째인가. 안 서면 {@code -1}. */
	public int sequenceOf(String routeId, String stopId) {
		Integer index = this.sequenceByRouteAndStop.get(key(routeId, stopId));
		return (index == null) ? -1 : index;
	}

	/** 그 노선의 운행들. 첫 정류장 출발시각 오름차순이다. */
	public List<Trip> tripsOf(String routeId) {
		return this.tripsByRoute.getOrDefault(routeId, Collections.emptyList());
	}

	/** 이 정류장에서 걸어서 갈 수 있는 다른 정류장들. */
	public List<Transfer> transfersFrom(String stopId) {
		return this.transfersByStop.getOrDefault(stopId, Collections.emptyList());
	}

	private static String key(String routeId, String stopId) {
		return routeId + " " + stopId;
	}

	private static void requireText(String value, String field) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(field + " 이(가) 비어 있다");
		}
	}
}
