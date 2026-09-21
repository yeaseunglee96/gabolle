package com.gabolle.backend.optimizer;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.gabolle.backend.itinerary.application.port.RouteOrderPort;
import com.gabolle.backend.place.domain.Place;
import com.gabolle.backend.place.repository.PlaceRepository;
import com.gabolle.backend.route.adapter.StraightLineRouteEstimator;
import com.gabolle.backend.route.domain.RouteLeg;
import com.gabolle.backend.route.domain.RouteQuery;
import com.gabolle.backend.route.domain.TravelMode;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * 일정이 정의한 동선 포트를 파이썬 경로 최적화(OR-Tools)로 채운다.
 *
 * <p><b>왜 이 클래스가 생겼나.</b> 풀이기({@code backend/solver/route_optimizer.py})와 배포
 * 이미지의 실행 환경은 전부터 있었는데 <b>자바에서 부르는 곳이 한 군데도 없었다.</b> 그래서
 * 하루의 차례를 정할 때 거리를 한 번도 안 봤다 — 「동선이 개판」의 코드상 원인이 이것이다.
 *
 * <p><b>이 판의 약속은 하나다 — 차례만 바꾸고 장소를 빼지 않는다.</b> 풀이기는 점수가 낮은
 * 곳을 빼는 것도 할 수 있지만, 그러려면 점수·1인당 비용·예산 환산이 필요한데 지금 자료에
 * 그 셋이 없다. 없는 값을 지어 넣으면 확인도 안 해 본 곳이 조용히 일정에서 빠진다.
 * 그래서 모든 곳을 {@code mustVisit} 으로 넘긴다.
 *
 * <p><b>길찾기 업체를 부르지 않는다.</b> 거리·시간 행렬은 (곳 수+1)² 칸이라 업체에 물으면
 * 호출이 제곱으로 는다. 이미 있는 직선거리 어림({@link StraightLineRouteEstimator})으로 채운다 —
 * 실제 이동시간은 지금처럼 <b>차례가 정해진 뒤에</b> 구간마다 따로 잰다.
 */
@Component
@Profile({ "db", "dev" })
public class OrToolsRouteOrderAdapter implements RouteOrderPort {

	private static final Logger log = LoggerFactory.getLogger(OrToolsRouteOrderAdapter.class);

	/** 자동차로 계산하는 이동수단. {@code RouteTravelTimeAdapter} 와 같은 갈래다. */
	private static final Set<String> CAR_MODES = Set.of("TAXI", "PRIVATE_CAR", "RENTAL_CAR");

	private static final Set<String> TRANSIT_MODES = Set.of("BUS", "SUBWAY");

	/**
	 * 풀이기에게 주는 생각할 시간.
	 *
	 * <p>🔴 <b>이 값이 곧 실행 시간이다.</b> 예산 중 남는 것을 돌려주지 않는다 —
	 * {@code solver/route_optimizer.py} 의 탐색 설정에 멈출 조건이 시간 제한 하나뿐이고,
	 * {@code GUIDED_LOCAL_SEARCH} 는 스스로 멈추지 않기 때문이다. 5000ms 를 주면 5015ms,
	 * 20ms 를 주면 22ms 가 걸린다. 버그가 아니라 시킨 대로 하는 것이다.
	 *
	 * <p>🔴 <b>운영에서 실측했다 (2026-09-22, S15P21E201-1464).</b> 같은 문제를 예산만 바꿔
	 * 풀려 경로 길이를 견줬다 — <b>250배 더 생각해도 답이 한 톨도 안 좋아진다.</b>
	 *
	 * <table>
	 *   <tr><th>장소</th><th>5000ms</th><th>20ms</th><th>차이</th></tr>
	 *   <tr><td>5곳</td><td>25,889m</td><td>25,889m</td><td>0.0%</td></tr>
	 *   <tr><td>8곳</td><td>31,642m</td><td>31,642m</td><td>0.0%</td></tr>
	 *   <tr><td>12곳</td><td>33,918m</td><td>33,918m</td><td>0.0%</td></tr>
	 * </table>
	 *
	 * <p>첫 해법 전략({@code PARALLEL_CHEAPEST_INSERTION})이 이미 좋은 답을 내고, 우리 크기의
	 * 문제는 그것이 사실상 최적이다. 그 뒤는 개선 없는 탐색을 반복하는 <b>순수한 대기</b>였다.
	 *
	 * <p>🔴 <b>답이 나오는 20ms 를 쓰지 않는 이유.</b> 그 측정은 제약이 느슨한 경우다 —
	 * 장소가 더 많거나 영업시간·이동수단 제약이 빡빡하면 더 걸릴 수 있다. 300ms 는 지금보다
	 * 5배 빠르면서 답이 나오는 시점에 15배 여유를 둔 값이다.
	 *
	 * <p>프로세스를 죽이는 시간보다 반드시 짧아야 한다 — 길면 답을 내는 중에 우리가 먼저 죽인다.
	 */
	private static final long SOLVER_BUDGET_MS = 300;

	/** 이보다 적으면 부르지 않는다. 출발점으로 돌아오는 한 바퀴라 두 곳 이하는 차례가 하나뿐이다. */
	private static final int MIN_STOPS_TO_REORDER = 3;

	private static final int MINUTES_IN_DAY = 24 * 60;

	/** 어림값이라는 사실이 로그와 응답에 같은 문장으로 남게 한다. */
	private static final String ESTIMATE_REASON = "동선 차례를 정하려고 직선거리로 어림잡았습니다.";

	private final RouteOptimizerProperties properties;

	private final ObjectMapper objectMapper;

	private final PlaceRepository placeRepository;

	private final StraightLineRouteEstimator estimator;

	public OrToolsRouteOrderAdapter(RouteOptimizerProperties properties, ObjectMapper objectMapper,
			PlaceRepository placeRepository, StraightLineRouteEstimator estimator) {
		this.properties = properties;
		this.objectMapper = objectMapper;
		this.placeRepository = placeRepository;
		this.estimator = estimator;
	}

	@Override
	public List<UUID> shortestOrder(RouteOrderRequest request) {
		if (request == null || request.placeIds() == null
				|| request.placeIds().size() < MIN_STOPS_TO_REORDER) {
			return List.of();
		}
		if (request.originLat() == null || request.originLng() == null) {
			// 출발점이 없으면 건너뛴다. 좌표를 지어내면 그 지점을 중심으로 차례가 뒤틀린다.
			return List.of();
		}

		List<UUID> placeIds = request.placeIds();
		Map<UUID, Place> placesById = lookup(placeIds);
		double[][] coordinates = coordinatesOf(request, placeIds, placesById);
		if (coordinates == null) {
			// 좌표를 모르는 곳이 하나라도 있으면 통째로 건너뛴다. 그 곳만 빼고 풀면
			// "차례만 바꾼다" 는 약속이 깨진다.
			return List.of();
		}

		try {
			String payload = this.objectMapper.writeValueAsString(payloadOf(request, placeIds, coordinates));
			RouteOptimizerProcess.Result result = new RouteOptimizerProcess(this.properties).run(payload);
			if (!result.ok()) {
				log.warn("경로 최적화를 못 불렀다 — 순위 차례 그대로 간다. 이유={}", result.detail());
				return List.of();
			}
			return orderFrom(result.stdout(), placeIds);
		}
		catch (InterruptedException interrupted) {
			// 인터럽트 표시를 먹지 않는다 — 삼키면 위에서 멈추라는 신호가 사라진다.
			Thread.currentThread().interrupt();
			return List.of();
		}
		catch (Exception ex) {
			log.warn("경로 최적화 중 예외 — 순위 차례 그대로 간다.", ex);
			return List.of();
		}
	}

	private Map<UUID, Place> lookup(List<UUID> placeIds) {
		Map<UUID, Place> byId = new HashMap<>();
		for (Place place : this.placeRepository.findByPlaceIdIn(placeIds)) {
			byId.put(place.getPlaceId(), place);
		}
		return byId;
	}

	/**
	 * 0번은 출발점, 그 뒤가 장소들. 풀이기의 자리 번호와 같은 차례다.
	 *
	 * @return {@code {위도, 경도}} 목록. 좌표를 모르는 곳이 하나라도 있으면 {@code null}
	 */
	private static double[][] coordinatesOf(RouteOrderRequest request, List<UUID> placeIds,
			Map<UUID, Place> placesById) {

		double[][] coordinates = new double[placeIds.size() + 1][];
		coordinates[0] = new double[] { request.originLat(), request.originLng() };
		for (int i = 0; i < placeIds.size(); i++) {
			Place place = placesById.get(placeIds.get(i));
			if (place == null || !place.hasCoordinates()) {
				return null;
			}
			coordinates[i + 1] = new double[] { place.getLat(), place.getLng() };
		}
		return coordinates;
	}

	/**
	 * 풀이기에 줄 값 한 벌.
	 *
	 * <p>🔴 여기 들어가는 상수들은 「모른다」를 「제약 없음」으로 옮긴 것이지 실제 값이 아니다.
	 * 좁게 넣으면 풀이기가 못 푼다고 답하고, 그러면 최적화가 조용히 한 번도 안 돈다.
	 */
	private Map<String, Object> payloadOf(RouteOrderRequest request, List<UUID> placeIds,
			double[][] coordinates) {

		TravelMode mode = modeOf(request.travelMode());
		int[][] travelMinutes = new int[coordinates.length][coordinates.length];
		int[][] distanceMeters = new int[coordinates.length][coordinates.length];
		long totalMeters = fillMatrices(coordinates, mode, travelMinutes, distanceMeters);

		List<Map<String, Object>> places = new ArrayList<>(placeIds.size());
		for (UUID placeId : placeIds) {
			Map<String, Object> place = new LinkedHashMap<>();
			place.put("id", placeId.toString());
			// 갈래는 밥집 상한·관광지 하한을 세는 데만 쓰이는데 그 둘을 이 판에서는 안 건다.
			place.put("category", "TOURIST");
			place.put("score", 0);
			// 머무는 시간을 0으로 넘긴다. 이 판은 차례만 쓰고 풀이기가 낸 도착 시각은 안 쓴다.
			// 실제 값을 넣으면 하루에 안 들어간다며 못 푼다고 답할 뿐, 차례는 달라지지 않는다.
			place.put("stayMinutes", 0);
			// 영업시간은 원시 시각 칸이 아직 없다 → 하루 전체로 넣는다(제약 없음).
			place.put("openMin", 0);
			place.put("closeMin", MINUTES_IN_DAY);
			place.put("cost", 0);
			place.put("mustVisit", true);
			places.add(place);
		}

		Map<String, Object> payload = new LinkedHashMap<>();
		payload.put("places", places);
		payload.put("travelMinutes", travelMinutes);
		payload.put("distanceMeters", distanceMeters);
		payload.put("dayStartMin", 0);
		payload.put("dayEndMin", MINUTES_IN_DAY);
		// 값을 전부 0으로 넘겼으니 한도도 0이면 된다. 이 판은 돈을 안 본다.
		payload.put("dayBudget", 0);
		payload.put("hasCar", mode == TravelMode.CAR);
		// 걷는 거리 상한은 안 건다. 행렬 전체를 더한 값은 어떤 한 바퀴보다도 크다.
		payload.put("maxWalkingM", totalMeters);
		payload.put("maxItems", placeIds.size());
		payload.put("maxFoodStops", placeIds.size());
		payload.put("maxRestaurantStops", placeIds.size());
		payload.put("timeoutMs", solverBudgetMs());
		return payload;
	}

	/** @return 행렬에 들어간 미터의 총합 — 걷는 거리 상한을 안 거는 데 쓴다 */
	private long fillMatrices(double[][] coordinates, TravelMode mode, int[][] travelMinutes,
			int[][] distanceMeters) {

		long totalMeters = 0;
		for (int from = 0; from < coordinates.length; from++) {
			for (int to = 0; to < coordinates.length; to++) {
				if (from == to) {
					continue;
				}
				RouteLeg leg = this.estimator.estimate(
						new RouteQuery(coordinates[from][0], coordinates[from][1],
								coordinates[to][0], coordinates[to][1], mode),
						ESTIMATE_REASON);
				travelMinutes[from][to] = leg.durationMin();
				distanceMeters[from][to] = leg.distanceM();
				totalMeters += leg.distanceM();
			}
		}
		return totalMeters;
	}

	/**
	 * 풀이기가 낸 차례를 읽는다.
	 * 받은 것과 <b>같은 것이 같은 수만큼</b> 들어 있을 때만 쓴다 — 하나라도 어긋나면 이 판의
	 * 약속(차례만 바꾼다)이 깨진 것이므로 통째로 버린다.
	 */
	private List<UUID> orderFrom(String stdout, List<UUID> placeIds) {
		JsonNode node = this.objectMapper.readTree(stdout);
		String status = node.path("status").asText("");
		if (!"FEASIBLE".equals(status) && !"OPTIMAL".equals(status)) {
			log.warn("경로 최적화가 차례를 못 냈다 — 순위 차례 그대로 간다. 답={}", status.isBlank() ? stdout.strip() : status);
			return List.of();
		}

		JsonNode ordered = node.path("orderedPlaceIds");
		if (!ordered.isArray() || ordered.size() != placeIds.size()) {
			return List.of();
		}
		Map<UUID, Integer> remaining = new HashMap<>();
		for (UUID placeId : placeIds) {
			remaining.merge(placeId, 1, Integer::sum);
		}
		List<UUID> result = new ArrayList<>(placeIds.size());
		for (JsonNode entry : ordered) {
			UUID placeId;
			try {
				placeId = UUID.fromString(entry.asText(""));
			}
			catch (IllegalArgumentException notAnId) {
				return List.of();
			}
			Integer left = remaining.get(placeId);
			if (left == null || left == 0) {
				return List.of();
			}
			remaining.put(placeId, left - 1);
			result.add(placeId);
		}
		return List.copyOf(result);
	}

	/** 프로세스를 죽이는 시간의 절반까지만 준다. 설정을 아주 짧게 줘도 답을 받아 볼 수 있게. */
	private long solverBudgetMs() {
		long halfOfProcessTimeout = this.properties.getTimeoutSeconds() * 1_000 / 2;
		return Math.max(200, Math.min(SOLVER_BUDGET_MS, halfOfProcessTimeout));
	}

	/**
	 * 여행이 고른 아홉 갈래를 경로 계산의 셋으로 옮긴다.
	 * 자전거·배·기타는 도보로 본다 — 정확해서가 아니라 그 셋에 맞는 계산이 없기 때문이다.
	 * ({@code RouteTravelTimeAdapter} 가 같은 표를 쓴다.)
	 */
	private static TravelMode modeOf(String travelMode) {
		if (travelMode == null) {
			return TravelMode.WALK;
		}
		String upper = travelMode.toUpperCase(Locale.ROOT);
		if (CAR_MODES.contains(upper)) {
			return TravelMode.CAR;
		}
		return TRANSIT_MODES.contains(upper) ? TravelMode.TRANSIT : TravelMode.WALK;
	}
}
