package com.gabolle.backend.route.adapter;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.route.domain.RouteLeg;
import com.gabolle.backend.route.domain.RouteQuery;
import com.gabolle.backend.route.domain.TravelMode;
import com.gabolle.backend.route.transit.TransitNetwork;
import com.gabolle.backend.route.transit.TransitNetwork.Kind;
import tools.jackson.databind.ObjectMapper;

import com.gabolle.backend.route.transit.TransitFareCalculator;
import com.gabolle.backend.route.transit.TransitFareTable;
import com.gabolle.backend.route.transit.TransitNetworkPort;
import com.gabolle.backend.route.transit.TransitProperties;

/**
 * {@link TransitRouteAdapter} 검증. 좌표는 실제 부산 값을 쓴다 — 서면(35.1580, 129.0594)과
 * 전포(35.1520, 129.0648)는 약 700m 떨어져 있어, 걸어갈 만한 거리 기본값(800m) 안팎을 실제로
 * 시험할 수 있다.
 */
class TransitRouteAdapterTest {

	private static final double SEOMYEON_LAT = 35.1580;

	private static final double SEOMYEON_LNG = 129.0594;

	private static final double JEONPO_LAT = 35.1520;

	private static final double JEONPO_LNG = 129.0648;

	private static final double BUJEON_LAT = 35.1449;

	private static final double BUJEON_LNG = 129.0621;

	private TransitProperties properties() {
		return new TransitProperties();
	}

	/** 서면 → 전포 → 부전으로 가는 1호선. 09:00 에 출발해 역마다 2분. */
	private TransitNetwork line1() {
		TransitNetwork.Stop seomyeon = new TransitNetwork.Stop("S", "서면", SEOMYEON_LAT, SEOMYEON_LNG,
				Kind.SUBWAY);
		TransitNetwork.Stop jeonpo = new TransitNetwork.Stop("J", "전포", JEONPO_LAT, JEONPO_LNG, Kind.SUBWAY);
		TransitNetwork.Stop bujeon = new TransitNetwork.Stop("B", "부전", BUJEON_LAT, BUJEON_LNG, Kind.SUBWAY);
		TransitNetwork.Route route = new TransitNetwork.Route("L1", "1호선 다대포해수욕장행", Kind.SUBWAY,
				List.of("S", "J", "B"), 6);
		List<TransitNetwork.Trip> trips = new java.util.ArrayList<>();
		// 06:00 부터 23:00 까지 6분 간격 — 실제 지하철에 가깝게 촘촘히 둔다.
		for (int minute = 360; minute <= 1380; minute += 6) {
			trips.add(new TransitNetwork.Trip("L1", List.of(minute, minute + 2, minute + 4)));
		}
		return TransitNetwork.of(List.of(seomyeon, jeonpo, bujeon), List.of(route), trips, List.of());
	}

	private TransitRouteAdapter adapter(TransitNetwork network) {
		TransitNetworkPort port = () -> network;
		// 요금 계산기가 생성자에 있지만 이 파일이 재는 것은 경로다 — 요금은
		// TransitFareCalculatorTest 가 따로 잰다.
		return new TransitRouteAdapter(port, properties(),
				new TransitFareCalculator(new TransitFareTable(new ObjectMapper())));
	}

	@Test
	@DisplayName("대중교통만 맡는다 — 자동차·도보는 다른 어댑터의 몫이다")
	void supportsOnlyTransit() {
		TransitRouteAdapter adapter = adapter(line1());

		assertThat(adapter.supports(TravelMode.TRANSIT)).isTrue();
		assertThat(adapter.supports(TravelMode.CAR)).isFalse();
		assertThat(adapter.supports(TravelMode.WALK)).isFalse();
	}

	@Test
	@DisplayName("노선망이 비어 있으면 빈 값 — 고장이 아니라 정상 흐름이다")
	void returnsEmptyWithoutNetwork() {
		TransitRouteAdapter adapter = adapter(TransitNetwork.empty());

		Optional<RouteLeg> found = adapter.find(new RouteQuery(SEOMYEON_LAT, SEOMYEON_LNG, BUJEON_LAT,
				BUJEON_LNG, TravelMode.TRANSIT));

		assertThat(found).isEmpty();
	}

	@Test
	@DisplayName("출발 시각을 알면 시각표로 찾고 추정 표시를 안 단다")
	void findsExactJourneyWhenDepartureIsKnown() {
		TransitRouteAdapter adapter = adapter(line1());
		// 09:00 KST = 00:00 UTC.
		OffsetDateTime nineAmKst = OffsetDateTime.of(2026, 9, 16, 0, 0, 0, 0, ZoneOffset.UTC);

		Optional<RouteLeg> found = adapter.find(new RouteQuery(SEOMYEON_LAT, SEOMYEON_LNG, BUJEON_LAT, BUJEON_LNG,
				TravelMode.TRANSIT, nineAmKst));

		assertThat(found).isPresent();
		RouteLeg leg = found.get();
		assertThat(leg.mode()).isEqualTo(TravelMode.TRANSIT);
		assertThat(leg.provider()).isEqualTo(TransitRouteAdapter.PROVIDER_TRANSIT_NETWORK);
		// 시각표로 실제 차를 찾았으므로 추정이 아니다.
		assertThat(leg.estimated()).isFalse();
		assertThat(leg.estimateReason()).isNull();
		assertThat(leg.transferCount()).isZero();
		assertThat(leg.durationMin()).isPositive();
	}

	@Test
	@DisplayName("🔴 출발 시각을 모르면 추정 표시와 이유를 반드시 싣는다")
	void marksEstimatedWhenDepartureIsUnknown() {
		TransitRouteAdapter adapter = adapter(line1());

		Optional<RouteLeg> found = adapter.find(new RouteQuery(SEOMYEON_LAT, SEOMYEON_LNG, BUJEON_LAT, BUJEON_LNG,
				TravelMode.TRANSIT));

		assertThat(found).isPresent();
		// 표시 없이 내보내면 그건 추정이 아니라 창작이고, 화면은 그것을 실제 소요시간으로 그린다.
		assertThat(found.get().estimated()).isTrue();
		assertThat(found.get().estimateReason()).isEqualTo(TransitRouteAdapter.REASON_NO_DEPARTURE_TIME);
	}

	@Test
	@DisplayName("탄 구간마다 안내를 남긴다 — 화면이 「무엇을 타라」를 그릴 수 있게")
	void buildsStepsForEachRide() {
		TransitRouteAdapter adapter = adapter(line1());
		OffsetDateTime nineAmKst = OffsetDateTime.of(2026, 9, 16, 0, 0, 0, 0, ZoneOffset.UTC);

		Optional<RouteLeg> found = adapter.find(new RouteQuery(SEOMYEON_LAT, SEOMYEON_LNG, BUJEON_LAT, BUJEON_LNG,
				TravelMode.TRANSIT, nineAmKst));

		assertThat(found).isPresent();
		assertThat(found.get().steps()).isNotEmpty();
		assertThat(found.get().steps()).allSatisfy(step -> {
			assertThat(step.name()).isNotBlank();
			assertThat(step.guidance()).isNotBlank();
		});
		assertThat(found.get().steps().get(0).name()).isEqualTo("1호선 다대포해수욕장행");
	}

	@Test
	@DisplayName("걸어갈 만한 정류장이 없으면 빈 값 — 먼 정류장을 억지로 붙이지 않는다")
	void returnsEmptyWhenNoStopIsWalkable() {
		TransitRouteAdapter adapter = adapter(line1());

		// 해운대 앞바다 — 노선망의 정류장에서 10km 넘게 떨어져 있다.
		Optional<RouteLeg> found = adapter.find(new RouteQuery(35.1586, 129.1603, BUJEON_LAT, BUJEON_LNG,
				TravelMode.TRANSIT));

		assertThat(found).isEmpty();
	}

	@Test
	@DisplayName("막차가 지난 시각이면 빈 값 — 지어낸 시간으로 답하지 않는다")
	void returnsEmptyAfterLastTrain() {
		TransitRouteAdapter adapter = adapter(line1());
		// 23:50 KST = 14:50 UTC.
		OffsetDateTime lateKst = OffsetDateTime.of(2026, 9, 16, 14, 50, 0, 0, ZoneOffset.UTC);

		Optional<RouteLeg> found = adapter.find(new RouteQuery(SEOMYEON_LAT, SEOMYEON_LNG, BUJEON_LAT, BUJEON_LNG,
				TravelMode.TRANSIT, lateKst));

		assertThat(found).isEmpty();
	}
}
