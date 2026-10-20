package com.gabolle.backend.route.transit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

/**
 * 배차간격 계산의 한 번 환승 (S15P21E201-2067). 갈아타야 하는 길(명지→삼광사)이 직선 어림으로 떨어졌다.
 *
 * <pre>
 *  A 버스: a1 ─ a2 ─ a3
 *  B 버스:           a3 ─ b2          (같은 정류장 a3 에서 갈아타기)
 *  C 지하철:   c1 ─ c2                 (a2 에서 c1 까지 걸어서 3분)
 * </pre>
 */
class HeadwayOneTransferTest {

	private static TransitNetwork.Stop stop(String id, double lng, TransitNetwork.Kind kind) {
		return new TransitNetwork.Stop(id, id, 35.1, lng, kind);
	}

	private static final TransitNetwork.Kind BUS = TransitNetwork.Kind.values()[0];

	private final HeadwayJourneyPlanner planner = new HeadwayJourneyPlanner(14.4);

	@Test
	void 한_번에_가는_노선이_없으면_같은_정류장에서_갈아탄다() {
		TransitNetwork network = TransitNetwork.of(
				List.of(stop("a1", 129.00, BUS), stop("a2", 129.01, BUS), stop("a3", 129.02, BUS), stop("b2", 129.03, BUS)),
				List.of(new TransitNetwork.Route("A", "A", BUS, List.of("a1", "a2", "a3"), 10),
						new TransitNetwork.Route("B", "B", BUS, List.of("a3", "b2"), 20)),
				List.of(), List.of());

		assertThat(this.planner.plan(network, Map.of("a1", 2), Map.of("b2", 1), 13 * 60)).isEmpty();
		Optional<RaptorPlanner.Journey> found = this.planner.planWithOneTransfer(network, Map.of("a1", 2), Map.of("b2", 1),
				13 * 60);

		assertThat(found).isPresent();
		RaptorPlanner.Journey journey = found.get();
		assertThat(journey.rides()).extracting(RaptorPlanner.Ride::routeId).containsExactly("A", "B");
		assertThat(journey.transferCount()).isEqualTo(1);
		// 걷기 2 + 기다림 5(10/2) + A 타기 + 기다림 10(20/2) + B 타기 + 걷기 1 — 기다림이 둘 다 들어간다
		assertThat(journey.durationMin()).isGreaterThanOrEqualTo(2 + 5 + 10 + 1 + 2);
	}

	@Test
	void 걸어서_옆_정류장으로_갈아타면_걷는_구간이_들어간다() {
		TransitNetwork network = TransitNetwork.of(
				List.of(stop("a1", 129.00, BUS), stop("a2", 129.01, BUS), stop("c1", 129.011, BUS), stop("c2", 129.03, BUS)),
				List.of(new TransitNetwork.Route("A", "A", BUS, List.of("a1", "a2"), 10),
						new TransitNetwork.Route("C", "C", BUS, List.of("c1", "c2"), 6)),
				List.of(), List.of(new TransitNetwork.Transfer("a2", "c1", 3)));

		RaptorPlanner.Journey journey = this.planner
				.planWithOneTransfer(network, Map.of("a1", 1), Map.of("c2", 1), 13 * 60).orElseThrow();

		assertThat(journey.rides()).extracting(RaptorPlanner.Ride::routeId).containsExactly("A", null, "C");
		assertThat(journey.rides().get(1).isWalk()).isTrue();
	}

	@Test
	void 갈아탈_곳이_없으면_빈_값() {
		TransitNetwork network = TransitNetwork.of(
				List.of(stop("a1", 129.00, BUS), stop("a2", 129.01, BUS), stop("x1", 129.05, BUS), stop("x2", 129.06, BUS)),
				List.of(new TransitNetwork.Route("A", "A", BUS, List.of("a1", "a2"), 10),
						new TransitNetwork.Route("X", "X", BUS, List.of("x1", "x2"), 10)),
				List.of(), List.of());

		assertThat(this.planner.planWithOneTransfer(network, Map.of("a1", 1), Map.of("x2", 1), 13 * 60)).isEmpty();
	}
}
