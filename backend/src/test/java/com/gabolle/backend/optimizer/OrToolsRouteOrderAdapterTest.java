package com.gabolle.backend.optimizer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.gabolle.backend.itinerary.application.port.RouteOrderPort;
import com.gabolle.backend.place.domain.Place;
import com.gabolle.backend.place.repository.PlaceRepository;
import com.gabolle.backend.place.service.GeoDistance;
import com.gabolle.backend.route.adapter.StraightLineRouteEstimator;
import com.gabolle.backend.route.config.RouteProperties;

import tools.jackson.databind.ObjectMapper;

/**
 * 파이썬 경로 최적화(OR-Tools)를 <b>실제로 불러서</b> 차례가 정말 바뀌는지 본다.
 *
 * <p>일부러 {@code Assumptions}/{@code @EnabledIf} 를 쓰지 않는다 —
 * {@link RouteOptimizerAvailabilityCheckTest} 와 같은 이유다. 라이브러리가 빠지면 건너뛰는
 * 것이 아니라 빨갛게 실패해야 한다. 건너뛰게 만들면 "초록인데 동선은 그대로" 가 된다.
 *
 * <p>실행 파일 경로는 {@code GABOLLE_ROUTE_OPTIMIZER_PYTHON}(+{@code _SCRIPT}) 환경변수를 먼저
 * 본다 — CI 는 시스템 {@code python3} 대신 uv 로 받은 3.13 을 이 변수로 가리킨다.
 */
class OrToolsRouteOrderAdapterTest {

	/** 부산역. 하루가 여기서 시작해서 여기로 돌아온다. */
	private static final double ORIGIN_LAT = 35.1151;

	private static final double ORIGIN_LNG = 129.0413;

	/**
	 * 일부러 동서로 오가게 세운 네 곳. 추천 순위는 "얼마나 잘 맞는가" 라서 이런 차례가 실제로
	 * 나온다 — 동쪽(해운대) → 서쪽(감천) → 동쪽(광안리) → 서쪽(자갈치).
	 */
	private static final List<Stop> STOPS = List.of(
			new Stop("해운대해수욕장", 35.1587, 129.1604),
			new Stop("감천문화마을", 35.0975, 129.0106),
			new Stop("광안리해수욕장", 35.1532, 129.1186),
			new Stop("자갈치시장", 35.0966, 129.0306));

	private record Stop(String name, double lat, double lng) {
	}

	@Test
	void reordersAZigzagDayIntoAShorterLoop() {
		Map<UUID, Stop> stopsById = new LinkedHashMap<>();
		for (Stop stop : STOPS) {
			stopsById.put(UUID.randomUUID(), stop);
		}
		List<UUID> rankOrder = List.copyOf(stopsById.keySet());

		OrToolsRouteOrderAdapter adapter = adapterWith(stopsById);

		List<UUID> routeOrder = adapter.shortestOrder(new RouteOrderPort.RouteOrderRequest(
				ORIGIN_LAT, ORIGIN_LNG, rankOrder, "WALK"));

		assertThat(routeOrder)
				.as("받은 곳이 그대로 다 있어야 한다 — 이 판은 차례만 바꾼다")
				.containsExactlyInAnyOrderElementsOf(rankOrder);

		double rankMeters = loopMeters(rankOrder, stopsById);
		double routeMeters = loopMeters(routeOrder, stopsById);
		assertThat(routeMeters)
				.as("순위 차례 %.0fm → 최적화 차례 %.0fm (%s)", rankMeters, routeMeters, nameOf(routeOrder, stopsById))
				.isLessThan(rankMeters);
	}

	@Test
	void skipsWhenOriginIsUnknown() {
		PlaceRepository placeRepository = mock(PlaceRepository.class);
		OrToolsRouteOrderAdapter adapter = new OrToolsRouteOrderAdapter(properties(), new ObjectMapper(),
				placeRepository, new StraightLineRouteEstimator(new RouteProperties()));

		List<UUID> placeIds = List.of(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

		List<UUID> routeOrder = adapter.shortestOrder(
				new RouteOrderPort.RouteOrderRequest(null, null, placeIds, "WALK"));

		assertThat(routeOrder).as("출발점이 없으면 좌표를 지어내지 않고 건너뛴다").isEmpty();
		verifyNoInteractions(placeRepository);
	}

	@Test
	void skipsWhenAnyPlaceHasNoCoordinates() {
		Map<UUID, Stop> stopsById = new LinkedHashMap<>();
		for (Stop stop : STOPS) {
			stopsById.put(UUID.randomUUID(), stop);
		}
		List<UUID> placeIds = new ArrayList<>(stopsById.keySet());
		UUID noCoordinates = UUID.randomUUID();
		placeIds.add(noCoordinates);

		OrToolsRouteOrderAdapter adapter = adapterWith(stopsById);

		List<UUID> routeOrder = adapter.shortestOrder(new RouteOrderPort.RouteOrderRequest(
				ORIGIN_LAT, ORIGIN_LNG, placeIds, "WALK"));

		assertThat(routeOrder)
				.as("한 곳이라도 좌표를 모르면 통째로 건너뛴다 — 그 곳만 빼면 차례만 바꾼다는 약속이 깨진다")
				.isEmpty();
	}

	@Test
	void skipsWhenThereIsNothingToReorder() {
		Map<UUID, Stop> stopsById = new LinkedHashMap<>();
		stopsById.put(UUID.randomUUID(), STOPS.get(0));
		stopsById.put(UUID.randomUUID(), STOPS.get(1));

		OrToolsRouteOrderAdapter adapter = adapterWith(stopsById);

		List<UUID> routeOrder = adapter.shortestOrder(new RouteOrderPort.RouteOrderRequest(
				ORIGIN_LAT, ORIGIN_LNG, List.copyOf(stopsById.keySet()), "WALK"));

		assertThat(routeOrder)
				.as("출발점으로 돌아오는 한 바퀴라 두 곳이면 차례가 하나뿐이다 — 파이썬을 부를 이유가 없다")
				.isEmpty();
	}

	private OrToolsRouteOrderAdapter adapterWith(Map<UUID, Stop> stopsById) {
		List<Place> places = new ArrayList<>();
		stopsById.forEach((placeId, stop) -> places.add(Place.imported(placeId, stop.name(), "TOURIST",
				"부산", stop.lat(), stop.lng(), "TEST", stop.name(), OffsetDateTime.now(), null, "test-1")));

		PlaceRepository placeRepository = mock(PlaceRepository.class);
		when(placeRepository.findByPlaceIdIn(anyCollection())).thenReturn(places);

		return new OrToolsRouteOrderAdapter(properties(), new ObjectMapper(), placeRepository,
				new StraightLineRouteEstimator(new RouteProperties()));
	}

	/** 출발점에서 나가 차례대로 들렀다가 출발점으로 돌아오는 한 바퀴의 직선거리 합. */
	private static double loopMeters(List<UUID> order, Map<UUID, Stop> stopsById) {
		double total = 0;
		double lat = ORIGIN_LAT;
		double lng = ORIGIN_LNG;
		for (UUID placeId : order) {
			Stop stop = stopsById.get(placeId);
			total += GeoDistance.meters(lat, lng, stop.lat(), stop.lng());
			lat = stop.lat();
			lng = stop.lng();
		}
		return total + GeoDistance.meters(lat, lng, ORIGIN_LAT, ORIGIN_LNG);
	}

	/** 실패 메시지에 어떤 차례가 나왔는지 그대로 남긴다 — 숫자만 보면 무엇이 일어났는지 모른다. */
	private static String nameOf(List<UUID> order, Map<UUID, Stop> stopsById) {
		List<String> names = new ArrayList<>();
		for (UUID placeId : order) {
			names.add(stopsById.get(placeId).name());
		}
		return String.join(" → ", names);
	}

	private static RouteOptimizerProperties properties() {
		RouteOptimizerProperties properties = new RouteOptimizerProperties();
		String python = setting("GABOLLE_ROUTE_OPTIMIZER_PYTHON");
		if (python != null) {
			properties.setPythonExecutable(python);
		}
		String script = setting("GABOLLE_ROUTE_OPTIMIZER_SCRIPT");
		if (script != null) {
			properties.setScriptPath(script);
		}
		return properties;
	}

	/** 환경변수를 먼저 보고, 없으면 같은 이름의 시스템 프로퍼티({@code -D})를 본다. */
	private static String setting(String key) {
		String value = System.getenv(key);
		if (value == null) {
			value = System.getProperty(key);
		}
		return (value == null || value.isBlank()) ? null : value;
	}
}
