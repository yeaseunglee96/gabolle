package com.gabolle.backend.route.transit;

import java.util.List;

import tools.jackson.databind.ObjectMapper;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 대중교통 요금.
 *
 * 요금표의 환승 절은 「도시철도↔일반버스」·「도시철도↔좌석버스」 네 줄뿐이다. 계산기는 그 네
 * 줄을 특수 사례로 옮겨 적지 않고 규칙 하나로 낸다 — 「지금까지 낸 것보다 비싸면 그 차액만」.
 * 그 규칙이 맞다는 증거가 네 줄이 전부 맞아떨어지는 것이고, 맞아떨어지면 표에 없는
 * 조합(버스↔버스)도 같은 규칙으로 답이 나온다.
 */
class TransitFareCalculatorTest {

	private static final String 일반버스 = "일반버스";

	private static final String 마을버스 = "마을버스";

	private static final String 좌석버스 = "좌석버스(산성)";

	private static final String 급행버스 = "급행버스";

	private TransitFareCalculator calculator;

	private TransitNetwork network;

	@BeforeEach
	void setUp() {
		this.calculator = new TransitFareCalculator(new TransitFareTable(new ObjectMapper()));
		this.network = networkWith(
				busRoute("B-일반", 일반버스),
				busRoute("B-마을", 마을버스),
				busRoute("B-좌석", 좌석버스),
				busRoute("B-급행", 급행버스),
				subwayRoute("S-1호선"));
	}

	// 공식 환승표 네 줄

	@Test
	@DisplayName("🔴 일반버스 → 도시철도 = 1,600 (공식표: 버스 1,550 + 철도추가 50)")
	void busThenSubwayMatchesOfficialTable() {
		Integer fare = this.calculator.fareKrw(journey(ride("B-일반", 0, 10), ride("S-1호선", 15, 30)), this.network);

		assertThat(fare).isEqualTo(1600);
	}

	@Test
	@DisplayName("🔴 도시철도 → 좌석버스 = 2,100 (공식표: 철도 1,600 + 좌석추가 500) — 사장님이 물으신 차액")
	void subwayThenSeatBusMatchesOfficialTable() {
		Integer fare = this.calculator.fareKrw(journey(ride("S-1호선", 0, 20), ride("B-좌석", 25, 45)), this.network);

		assertThat(fare).isEqualTo(2100);
	}

	@Test
	@DisplayName("🔴 도시철도 → 일반버스 = 1,600 (공식표: 버스추가 0 — 싼 쪽으로 갈아타면 안 낸다)")
	void subwayThenOrdinaryBusAddsNothing() {
		Integer fare = this.calculator.fareKrw(journey(ride("S-1호선", 0, 20), ride("B-일반", 25, 40)), this.network);

		assertThat(fare).isEqualTo(1600);
	}

	@Test
	@DisplayName("🔴 좌석버스 → 도시철도 = 2,100 (공식표: 철도추가 0)")
	void seatBusThenSubwayAddsNothing() {
		Integer fare = this.calculator.fareKrw(journey(ride("B-좌석", 0, 20), ride("S-1호선", 25, 40)), this.network);

		assertThat(fare).isEqualTo(2100);
	}

	// 표에 없는 조합 — 같은 규칙으로 답이 나온다

	@Test
	@DisplayName("표에 없는 버스↔버스도 규칙이 답한다 — 마을 1,480 → 일반 1,550 은 차액 70 이라 총 1,550")
	void villageThenOrdinaryBusPaysTheDifference() {
		Integer fare = this.calculator.fareKrw(journey(ride("B-마을", 0, 10), ride("B-일반", 15, 30)), this.network);

		assertThat(fare).isEqualTo(1550);
	}

	@Test
	@DisplayName("같은 종류끼리 갈아타면 더 안 낸다 — 일반 → 일반 = 1,550")
	void sameKindTransferIsFree() {
		Integer fare = this.calculator.fareKrw(journey(ride("B-일반", 0, 10), ride("B-일반", 15, 30)), this.network);

		assertThat(fare).isEqualTo(1550);
	}

	// 환승이 끊기는 조건 — 걷기·30분·횟수

	@Test
	@DisplayName("🔴 도보 → 버스 → 환승 → 지하철 → 도보 : 걷기가 끼어도 환승이다 (1,600)")
	void walkingBetweenRidesDoesNotBreakTheTransfer() {
		Integer fare = this.calculator.fareKrw(
				journey(walk(0, 5), ride("B-일반", 5, 15), walk(15, 20), ride("S-1호선", 20, 35), walk(35, 40)),
				this.network);

		assertThat(fare).as("걷기는 환승을 안 끊는다 — 끊는 것은 시간과 횟수다").isEqualTo(1600);
	}

	@Test
	@DisplayName("🔴 30분을 넘기면 환승이 끊기고 각자 낸다 — 1,550 + 1,600 = 3,150")
	void exceedingThirtyMinutesStartsANewFare() {
		Integer fare = this.calculator.fareKrw(
				journey(ride("B-일반", 0, 10), ride("S-1호선", 45, 60)), this.network);

		assertThat(fare).as("내린 뒤 35분이 지났다 — 환승이 아니다").isEqualTo(3150);
	}

	@Test
	@DisplayName("정확히 30분이면 아직 환승이다 — 경계는 넘어야 끊긴다")
	void exactlyThirtyMinutesIsStillATransfer() {
		Integer fare = this.calculator.fareKrw(
				journey(ride("B-일반", 0, 10), ride("S-1호선", 40, 55)), this.network);

		assertThat(fare).isEqualTo(1600);
	}

	@Test
	@DisplayName("🔴 세 번째 갈아탐부터는 할인이 없다 — 「3번째로 갈아탄 교통수단의 요금할인 혜택은 없습니다」")
	void thirdTransferPaysFullFare() {
		Integer fare = this.calculator.fareKrw(
				journey(ride("B-일반", 0, 10), ride("B-일반", 12, 20), ride("B-일반", 22, 30),
						ride("B-일반", 32, 40)),
				this.network);

		assertThat(fare).as("앞 세 번은 한 묶음(1,550), 네 번째 탑승이 새 묶음(1,550)").isEqualTo(3100);
	}

	// 모르는 것은 모른다고 한다

	@Test
	@DisplayName("🔴 요금을 모르는 노선이 끼면 전체가 null 이다 — 아는 것만 더하면 실제보다 싸다")
	void unknownFareMakesTheWholeJourneyUnknown() {
		Integer fare = this.calculator.fareKrw(
				journey(ride("B-일반", 0, 10), ride("B-급행", 15, 30)), this.network);

		assertThat(fare).as("급행버스는 고시에 이름이 없어 요금표가 비어 있다").isNull();
	}

	@Test
	@DisplayName("🔴 모르는 노선 하나 때문에 0 이 되지 않는다 — 0 은 「공짜」라는 다른 사실이다")
	void unknownIsNotZero() {
		Integer fare = this.calculator.fareKrw(journey(ride("B-급행", 0, 10)), this.network);

		// isNull() 하나로 충분하다. isNotEqualTo(0) 은 null 에 대해 「null 이 아님」을 먼저
		// 요구해서 뜻과 반대로 동작한다 — 재려는 것은 「0 으로 메우지 않았다」이고 그것이 곧 null 이다.
		assertThat(fare).as("0 으로 메우면 화면이 「무료」로 그린다").isNull();
	}

	@Test
	@DisplayName("걷기만 한 여정은 0 이다 — 여기서는 0 이 맞다")
	void walkOnlyJourneyIsFree() {
		Integer fare = this.calculator.fareKrw(journey(walk(0, 12)), this.network);

		assertThat(fare).isZero();
	}

	@Test
	@DisplayName("노선망에 없는 노선이면 모른다고 한다 — 아무 요금이나 고르지 않는다")
	void unknownRouteIsUnknownFare() {
		Integer fare = this.calculator.fareKrw(journey(ride("없는노선", 0, 10)), this.network);

		assertThat(fare).isNull();
	}

	// 시드

	private static RaptorPlanner.Journey journey(RaptorPlanner.Ride... rides) {
		List<RaptorPlanner.Ride> list = List.of(rides);
		return new RaptorPlanner.Journey(list, list.get(0).departMinOfDay(),
				list.get(list.size() - 1).arriveMinOfDay(), 0);
	}

	private static RaptorPlanner.Ride ride(String routeId, int departMin, int arriveMin) {
		return new RaptorPlanner.Ride(routeId, "정류장A", "정류장B", departMin, arriveMin);
	}

	/** 걷기는 {@code routeId} 가 없는 이동이다 — {@code Ride.isWalk()} 가 그렇게 본다. */
	private static RaptorPlanner.Ride walk(int departMin, int arriveMin) {
		return new RaptorPlanner.Ride(null, "정류장A", "정류장B", departMin, arriveMin);
	}

	private static TransitNetwork.Route busRoute(String id, String fareType) {
		return new TransitNetwork.Route(id, id, TransitNetwork.Kind.BUS, List.of("정류장A", "정류장B"), 10, 0,
				TransitNetwork.MINUTES_PER_DAY - 1, fareType);
	}

	/** 지하철은 종류가 하나라 요금을 거리로 가른다 — 아래 두 역은 1구간 안쪽이다. */
	private static TransitNetwork.Route subwayRoute(String id) {
		return new TransitNetwork.Route(id, id, TransitNetwork.Kind.SUBWAY, List.of("정류장A", "정류장B"), 5, 0,
				TransitNetwork.MINUTES_PER_DAY - 1, null);
	}

	private static TransitNetwork networkWith(TransitNetwork.Route... routes) {
		// 두 정류장을 약 1.1km 떨어뜨린다 — 지하철 1구간(10km 이하)에 들어간다.
		List<TransitNetwork.Stop> stops = List.of(
				new TransitNetwork.Stop("정류장A", "가", 35.1600, 129.1600, TransitNetwork.Kind.BUS),
				new TransitNetwork.Stop("정류장B", "나", 35.1700, 129.1600, TransitNetwork.Kind.BUS));
		return TransitNetwork.of(stops, List.of(routes), List.of(), List.of());
	}
}
