package com.gabolle.backend.route.transit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.route.transit.TransitNetwork.Kind;

/**
 * {@link RaptorPlanner} 검증.
 *
 * 정류장 넷짜리 노선망을 손으로 만들고 답을 미리 계산해 단정한다 — 진짜 부산 노선망으로는
 * 나온 답이 맞는지 사람이 검산할 수 없고, 틀려도 그럴듯해 보인다.
 *
 * 여기서 쓰는 시각은 자정부터의 분이다. 600 = 10:00.
 */
class RaptorPlannerTest {

	private static final TransitNetwork.Stop A = new TransitNetwork.Stop("A", "가역", 35.15, 129.05, Kind.SUBWAY);

	private static final TransitNetwork.Stop B = new TransitNetwork.Stop("B", "나역", 35.16, 129.06, Kind.SUBWAY);

	private static final TransitNetwork.Stop C = new TransitNetwork.Stop("C", "다역", 35.17, 129.07, Kind.SUBWAY);

	private static final TransitNetwork.Stop D = new TransitNetwork.Stop("D", "라정류장", 35.18, 129.08, Kind.BUS);

	private final RaptorPlanner planner = new RaptorPlanner(4);

	/**
	 * 가 → 나 → 다 로 가는 지하철 R1 과, 다 → 라 로 가는 버스 R2. 각각 하루 두 번 다닌다 —
	 * R1 은 600·630 출발로 역마다 2분, R2 는 610·640 출발로 5분.
	 */
	private TransitNetwork twoLegNetwork() {
		TransitNetwork.Route r1 = new TransitNetwork.Route("R1", "1호선", Kind.SUBWAY, List.of("A", "B", "C"), 30);
		TransitNetwork.Route r2 = new TransitNetwork.Route("R2", "1001번", Kind.BUS, List.of("C", "D"), 30);
		return TransitNetwork.of(List.of(A, B, C, D), List.of(r1, r2),
				List.of(new TransitNetwork.Trip("R1", List.of(600, 602, 604)),
						new TransitNetwork.Trip("R1", List.of(630, 632, 634)),
						new TransitNetwork.Trip("R2", List.of(610, 615)),
						new TransitNetwork.Trip("R2", List.of(640, 645))),
				List.of());
	}

	@Test
	@DisplayName("한 번도 안 갈아타면 탄 것 하나 · 환승 0")
	void findsDirectRide() {
		Optional<RaptorPlanner.Journey> found = this.planner.plan(twoLegNetwork(), Map.of("A", 0), Map.of("C", 0),
				600);

		assertThat(found).isPresent();
		RaptorPlanner.Journey journey = found.get();
		assertThat(journey.arriveMinOfDay()).isEqualTo(604);
		assertThat(journey.transferCount()).isZero();
		assertThat(journey.rides()).singleElement()
				.satisfies(ride -> assertThat(ride.routeId()).isEqualTo("R1"));
	}

	@Test
	@DisplayName("갈아타야 하면 탄 것 둘 · 환승 1 — 손으로 센 도착시각과 같다")
	void findsJourneyWithOneTransfer() {
		Optional<RaptorPlanner.Journey> found = this.planner.plan(twoLegNetwork(), Map.of("A", 0), Map.of("D", 0),
				600);

		assertThat(found).isPresent();
		RaptorPlanner.Journey journey = found.get();
		// 가 600 출발 → 다 604 도착 → 다에서 610 버스 → 라 615.
		assertThat(journey.arriveMinOfDay()).isEqualTo(615);
		assertThat(journey.durationMin()).isEqualTo(15);
		assertThat(journey.transferCount()).isEqualTo(1);
		assertThat(journey.rides()).extracting(RaptorPlanner.Ride::routeId).containsExactly("R1", "R2");
	}

	@Test
	@DisplayName("차가 아직 안 다니면 첫차를 기다린다 — 없는 차를 지어내지 않는다")
	void waitsForTheFirstTrip() {
		// 05:00 에 떠나려 하지만 첫차는 10:00 이다.
		Optional<RaptorPlanner.Journey> found = this.planner.plan(twoLegNetwork(), Map.of("A", 0), Map.of("C", 0),
				300);

		assertThat(found).isPresent();
		assertThat(found.get().arriveMinOfDay()).isEqualTo(604);
		// 걸린 시간에 기다린 시간이 들어 있다. 이것을 빼면 "5시에 출발해 5시 4분에 도착" 이 된다.
		assertThat(found.get().durationMin()).isEqualTo(304);
	}

	@Test
	@DisplayName("막차가 지났으면 빈 값 — 지어낸 시간으로 답하지 않는다")
	void returnsEmptyAfterTheLastTrip() {
		Optional<RaptorPlanner.Journey> found = this.planner.plan(twoLegNetwork(), Map.of("A", 0), Map.of("D", 0),
				1400);

		assertThat(found).isEmpty();
	}

	@Test
	@DisplayName("노선망이 비어 있으면 빈 값")
	void returnsEmptyOnEmptyNetwork() {
		assertThat(this.planner.plan(TransitNetwork.empty(), Map.of("A", 0), Map.of("D", 0), 600)).isEmpty();
	}

	@Test
	@DisplayName("노선망에 없는 정류장을 주면 빈 값 — 조용히 아무 정류장이나 고르지 않는다")
	void returnsEmptyWhenOriginIsUnknown() {
		assertThat(this.planner.plan(twoLegNetwork(), Map.of("없는정류장", 0), Map.of("D", 0), 600)).isEmpty();
	}

	@Test
	@DisplayName("걸어서 갈아타는 것을 쓴다 — 같은 역의 호선 간 환승이 이 모양이다")
	void usesFootTransfer() {
		// 가 → 나 지하철과, 다 → 라 버스. 나와 다는 3분 거리로 걸어서 잇는다.
		TransitNetwork.Route r1 = new TransitNetwork.Route("R1", "1호선", Kind.SUBWAY, List.of("A", "B"), 30);
		TransitNetwork.Route r2 = new TransitNetwork.Route("R2", "1001번", Kind.BUS, List.of("C", "D"), 30);
		TransitNetwork network = TransitNetwork.of(List.of(A, B, C, D), List.of(r1, r2),
				List.of(new TransitNetwork.Trip("R1", List.of(600, 602)),
						new TransitNetwork.Trip("R2", List.of(610, 615))),
				List.of(new TransitNetwork.Transfer("B", "C", 3)));

		Optional<RaptorPlanner.Journey> found = this.planner.plan(network, Map.of("A", 0), Map.of("D", 0), 600);

		assertThat(found).isPresent();
		// 가 600 → 나 602 → (걸어서 3분) 다 605 → 610 버스 → 라 615.
		assertThat(found.get().arriveMinOfDay()).isEqualTo(615);
		assertThat(found.get().rides()).extracting(RaptorPlanner.Ride::routeId)
				.containsExactly("R1", null, "R2");
		// 걷기는 "탄 횟수" 가 아니다. 탄 것이 둘이므로 환승은 하나다.
		assertThat(found.get().transferCount()).isEqualTo(1);
	}

	@Test
	@DisplayName("도착시각이 같으면 덜 갈아타는 쪽을 고른다 — 라운드가 먼저 끝난 쪽이 답이다")
	void prefersFewerTransfersWhenArrivalTies() {
		// 가에서 라까지 두 길: 직행 R3(600 → 615), 갈아타는 길 R1+R2(600 → 615). 도착이 같다.
		TransitNetwork.Route r1 = new TransitNetwork.Route("R1", "1호선", Kind.SUBWAY, List.of("A", "C"), 30);
		TransitNetwork.Route r2 = new TransitNetwork.Route("R2", "1001번", Kind.BUS, List.of("C", "D"), 30);
		TransitNetwork.Route r3 = new TransitNetwork.Route("R3", "직행", Kind.BUS, List.of("A", "D"), 30);
		TransitNetwork network = TransitNetwork.of(List.of(A, B, C, D), List.of(r1, r2, r3),
				List.of(new TransitNetwork.Trip("R1", List.of(600, 604)),
						new TransitNetwork.Trip("R2", List.of(610, 615)),
						new TransitNetwork.Trip("R3", List.of(600, 615))),
				List.of());

		Optional<RaptorPlanner.Journey> found = this.planner.plan(network, Map.of("A", 0), Map.of("D", 0), 600);

		assertThat(found).isPresent();
		assertThat(found.get().arriveMinOfDay()).isEqualTo(615);
		assertThat(found.get().transferCount()).isZero();
		assertThat(found.get().rides()).extracting(RaptorPlanner.Ride::routeId).containsExactly("R3");
	}

	@Test
	@DisplayName("정류장까지 걸어가는 시간이 도착시각에 들어간다")
	void countsWalkToAndFromStops() {
		Optional<RaptorPlanner.Journey> found = this.planner.plan(twoLegNetwork(), Map.of("A", 0), Map.of("C", 7),
				600);

		assertThat(found).isPresent();
		// 다 604 도착 + 목적지까지 7분.
		assertThat(found.get().arriveMinOfDay()).isEqualTo(611);
	}

	@Test
	@DisplayName("출발 시각을 모르면 여러 시각을 재서 가운데 값을 준다")
	void planTypicalReturnsAMedianJourney() {
		Optional<RaptorPlanner.Journey> found = this.planner.planTypical(twoLegNetwork(), Map.of("A", 0),
				Map.of("C", 0));

		assertThat(found).isPresent();
		// 이 노선망은 하루에 두 번만 다닌다. 06:00~22:00 표본 대부분이 첫차를 기다리거나
		// 막차를 놓치므로, 중앙값은 "많이 기다리는 경로" 다 — 그것이 사실이다.
		assertThat(found.get().durationMin()).isPositive();
	}

	@Test
	@DisplayName("한 번도 못 가면 보통 걸리는 시간도 빈 값이다")
	void planTypicalReturnsEmptyWhenNeverReachable() {
		TransitNetwork.Route r1 = new TransitNetwork.Route("R1", "1호선", Kind.SUBWAY, List.of("A", "B"), 30);
		TransitNetwork network = TransitNetwork.of(List.of(A, B, C, D), List.of(r1),
				List.of(new TransitNetwork.Trip("R1", List.of(600, 602))), List.of());

		assertThat(this.planner.planTypical(network, Map.of("A", 0), Map.of("D", 0))).isEmpty();
	}

	@Test
	@DisplayName("탈 수 있는 횟수를 넘는 길은 안 찾는다 — 네 번 갈아타는 길은 아무도 안 쓴다")
	void respectsMaxRounds() {
		RaptorPlanner oneRide = new RaptorPlanner(1);

		// 가 → 라 는 두 번 타야 한다. 한 번만 탈 수 있으면 못 간다.
		assertThat(oneRide.plan(twoLegNetwork(), Map.of("A", 0), Map.of("D", 0), 600)).isEmpty();
	}
}
