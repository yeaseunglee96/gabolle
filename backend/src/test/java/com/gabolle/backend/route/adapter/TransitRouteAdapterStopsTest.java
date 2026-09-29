package com.gabolle.backend.route.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.route.domain.RouteLeg;
import com.gabolle.backend.route.domain.RouteQuery;
import com.gabolle.backend.route.domain.TravelMode;
import com.gabolle.backend.route.presentation.dto.RouteDirectionsResponse;
import com.gabolle.backend.route.transit.TransitFareCalculator;
import com.gabolle.backend.route.transit.TransitFareTable;
import com.gabolle.backend.route.transit.TransitNetwork;
import com.gabolle.backend.route.transit.TransitNetwork.Kind;
import com.gabolle.backend.route.transit.TransitNetworkPort;
import com.gabolle.backend.route.transit.TransitProperties;
import tools.jackson.databind.ObjectMapper;

/**
 * 대중교통 단계에 싣는 「지나는 정류장」 — S15P21E201-1836.
 *
 * 앱의 「탑승 중」 화면이 남은 정류장과 내릴 곳을 알리려면 이름이 필요하다. 전에는 이 목록을 계산해 경로선 좌표로만 쓰고
 * 이름을 버렸다. 좌표는 {@link TransitRouteAdapterTest} 와 같은 실제 부산 값(서면 → 전포 → 부전)을 쓴다.
 */
class TransitRouteAdapterStopsTest {

	private static final double SEOMYEON_LAT = 35.1580;

	private static final double SEOMYEON_LNG = 129.0594;

	private static final double JEONPO_LAT = 35.1520;

	private static final double JEONPO_LNG = 129.0648;

	private static final double BUJEON_LAT = 35.1449;

	private static final double BUJEON_LNG = 129.0621;

	/** 서면 → 전포 → 부전으로 가는 1호선. 06:00~23:00 6분 간격, 역마다 2분. */
	private TransitNetwork line1() {
		TransitNetwork.Stop seomyeon = new TransitNetwork.Stop("S", "서면", SEOMYEON_LAT, SEOMYEON_LNG, Kind.SUBWAY);
		TransitNetwork.Stop jeonpo = new TransitNetwork.Stop("J", "전포", JEONPO_LAT, JEONPO_LNG, Kind.SUBWAY);
		TransitNetwork.Stop bujeon = new TransitNetwork.Stop("B", "부전", BUJEON_LAT, BUJEON_LNG, Kind.SUBWAY);
		TransitNetwork.Route route = new TransitNetwork.Route("L1", "1호선", Kind.SUBWAY, List.of("S", "J", "B"), 6);
		List<TransitNetwork.Trip> trips = new ArrayList<>();
		for (int minute = 360; minute <= 1380; minute += 6) {
			trips.add(new TransitNetwork.Trip("L1", List.of(minute, minute + 2, minute + 4)));
		}
		return TransitNetwork.of(List.of(seomyeon, jeonpo, bujeon), List.of(route), trips, List.of());
	}

	private RouteLeg find() {
		TransitNetworkPort port = this::line1;
		TransitRouteAdapter adapter = new TransitRouteAdapter(port, new TransitProperties(),
				new TransitFareCalculator(new TransitFareTable(new ObjectMapper())));
		OffsetDateTime nineAmKst = OffsetDateTime.of(2026, 9, 16, 0, 0, 0, 0, ZoneOffset.UTC);
		return adapter.find(new RouteQuery(SEOMYEON_LAT, SEOMYEON_LNG, BUJEON_LAT, BUJEON_LNG, TravelMode.TRANSIT,
				nineAmKst)).orElseThrow();
	}

	@Test
	@DisplayName("탄 단계에 지나는 정류장을 노선 순서대로 — 타는 곳부터 내리는 곳까지, 이름과 좌표")
	void ridingStepCarriesStopsInLineOrder() {
		RouteLeg leg = find();

		RouteLeg.Step ride = leg.steps().stream().filter(step -> !step.name().equals("도보")).findFirst().orElseThrow();
		assertThat(ride.stops()).extracting(RouteLeg.StopPoint::name).containsExactly("서면", "전포", "부전");
		assertThat(ride.stops().get(1).lat()).isEqualTo(JEONPO_LAT);
		assertThat(ride.stops().get(1).lng()).isEqualTo(JEONPO_LNG);
		// 안내 문장의 타는 곳·내리는 곳과 같은 이름으로 시작하고 끝난다 — 앱이 두 칸을 맞춰 본다.
		assertThat(ride.guidance()).startsWith("서면에서").contains("부전에서 내립니다");
	}

	@Test
	@DisplayName("응답에도 그대로 실린다 — 기존 칸은 바뀌지 않는다")
	void responseCarriesStops() {
		RouteDirectionsResponse response = RouteDirectionsResponse.from(find());

		RouteDirectionsResponse.Step ride = response.steps().stream()
				.filter(step -> !step.name().equals("도보")).findFirst().orElseThrow();
		assertThat(ride.stops()).extracting(RouteDirectionsResponse.Stop::name).containsExactly("서면", "전포", "부전");
		assertThat(ride.name()).isEqualTo("1호선");
		assertThat(response.mode()).isEqualTo("TRANSIT");
	}

	@Test
	@DisplayName("대중교통이 아닌 단계는 빈 목록 — 모르는 것을 지어내지 않는다")
	void otherStepsHaveNoStops() {
		RouteLeg.Step turn = new RouteLeg.Step("해운대해수욕장삼거리", "송정 방면으로 우회전", 38, 0);
		assertThat(turn.stops()).isEmpty();
		assertThat(new RouteLeg.Step("도보", "걸어갑니다", 10, 1, null).stops()).isEmpty();
	}
}
