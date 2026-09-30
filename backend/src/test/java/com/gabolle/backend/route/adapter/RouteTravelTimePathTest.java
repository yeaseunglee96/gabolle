package com.gabolle.backend.route.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.itinerary.application.port.TravelTime;
import com.gabolle.backend.route.application.RouteQueryService;
import com.gabolle.backend.route.domain.RouteLeg;
import com.gabolle.backend.route.domain.TravelMode;

/**
 * 구간 선형(「어느 길로 가는지」)이 어디까지 오고 어디서 비는가 — S15P21E201-1251.
 *
 * <p>이 시험이 생긴 이유. 선형은 업체 응답에 <b>이미 들어 있었고</b>
 * {@code KakaoMobilityRouteAdapter} 가 {@code RouteLeg.path} 에 담기까지 했는데,
 * {@link RouteTravelTimeAdapter} 가 {@link TravelTime} 으로 옮길 때 거리·시간·요금만
 * 가져가고 떨어뜨렸다. 포트가 좁아서 생긴 손실이지 자료가 없어서가 아니다 — 요금이 꼭
 * 같은 자리에서 같은 이유로 새고 있었다({@code RouteTravelTimeFareTest}).
 *
 * <p>🔴 그리고 <b>어림값의 선형은 실제 길이 아니다.</b> 직선거리로 어림잡을 때도
 * {@code path} 에 두 점이 들어오는데, 그것은 「어느 길로 가는지」가 아니라 출발·도착을
 * 이은 직선이다. 한 칸에 섞으면 화면이 직선을 실선으로 그린다 — 고치려던 그림이 바로
 * 그것이다.
 */
class RouteTravelTimePathTest {

	private static final double FROM_LAT = 35.1587;

	private static final double FROM_LNG = 129.1604;

	private static final double TO_LAT = 35.1796;

	private static final double TO_LNG = 129.0756;

	/** 해운대 → 광안리 쪽으로 굽어 가는 길. {@code [경도, 위도]} 순서다. */
	private static final List<double[]> ROAD = List.of(
			new double[] { 129.1604, 35.1587 },
			new double[] { 129.1204, 35.1553 },
			new double[] { 129.0756, 35.1796 });

	private final RouteQueryService routeQueryService = mock(RouteQueryService.class);

	private final RouteTravelTimeAdapter adapter = new RouteTravelTimeAdapter(this.routeQueryService);

	private TravelTime measure(RouteLeg leg) {
		when(this.routeQueryService.find(any())).thenReturn(leg);
		return this.adapter.between(FROM_LAT, FROM_LNG, TO_LAT, TO_LNG, "WALK");
	}

	/** 실제 길찾기 응답. {@code estimated=false} 다. */
	private static RouteLeg routed(List<double[]> path) {
		return new RouteLeg(TravelMode.WALK, 8_400, 21, null, null, null, false, null,
				RouteLeg.PROVIDER_KAKAO_MOBILITY, path, List.of());
	}

	/** 직선거리 어림값. {@code estimated=true} 이고 두 점이 들어 있다. */
	private static RouteLeg estimated(List<double[]> path) {
		return new RouteLeg(TravelMode.WALK, 8_400, 21, null, null, null, true, "STRAIGHT_LINE",
				RouteLeg.PROVIDER_STRAIGHT_LINE, path, List.of());
	}

	@Test
	@DisplayName("🔴 실제로 잰 길이면 선형이 실린다 — 받아 놓고 버리던 값이다")
	void carriesTheRoadItActuallyTakes() {
		TravelTime measured = measure(routed(ROAD));

		assertThat(measured.hasPath()).isTrue();
		assertThat(measured.path()).hasSize(3);
		// 순서는 [경도, 위도] 다. 부산은 위도 35·경도 129 라 뒤집혀도 숫자가 그럴듯해 보여서,
		// 화면에 그려 보기 전까지 아무도 못 알아챈다. 그래서 여기서 못 박는다.
		assertThat(measured.path().get(0)[0]).as("첫 점의 경도").isEqualTo(129.1604);
		assertThat(measured.path().get(0)[1]).as("첫 점의 위도").isEqualTo(35.1587);
	}

	@Test
	@DisplayName("🔴 직선거리로 어림잡은 구간은 선형을 버린다 — 직선은 「어느 길」이 아니다")
	void dropsTheStraightLineOfAnEstimate() {
		TravelTime measured = measure(estimated(List.of(
				new double[] { FROM_LNG, FROM_LAT },
				new double[] { TO_LNG, TO_LAT })));

		// 거리·시간은 그대로 쓴다. 버리는 것은 선형뿐이다.
		assertThat(measured.distanceM()).isEqualTo(8_400);
		assertThat(measured.path()).as("직선을 저장하면 화면이 그것을 실선으로 그린다").isNull();
		assertThat(measured.hasPath()).isFalse();
	}

	@Test
	@DisplayName("점이 둘 미만이면 선이 아니라 점이다 — 비운다")
	void oneOrZeroPointsIsNotALine() {
		assertThat(measure(routed(List.of(new double[] { 129.1604, 35.1587 }))).path()).isNull();
		assertThat(measure(routed(List.of())).path()).isNull();
	}

	@Test
	@DisplayName("좌표가 없으면 잴 수 없음이고 선형도 없다")
	void unknownWhenCoordinatesMissing() {
		TravelTime measured = this.adapter.between(null, null, TO_LAT, TO_LNG, "WALK");

		assertThat(measured.known()).isFalse();
		assertThat(measured.path()).isNull();
	}

	// ── 경사·계단 조각 ────────────────────────────────────────────────────────

	private static final List<RouteLeg.Piece> PIECES = List.of(
			new RouteLeg.Piece(0, 1, 2.5, false, 0.4), new RouteLeg.Piece(1, 2, null, true, null));

	@Test
	@DisplayName("🔴 보행 그래프가 준 경사·계단 조각이 일정 쪽까지 온다 — 예전에는 선형만 오고 조각은 버렸다")
	void carriesPiecesWithTheRoad() {
		RouteLeg graphWalk = new RouteLeg(TravelMode.WALK, 8_400, 21, null, null, null, false, null,
				RouteLeg.PROVIDER_WALK_GRAPH, ROAD, List.of(), null, PIECES);

		TravelTime measured = measure(graphWalk);

		assertThat(measured.pieces()).extracting(piece -> piece.from() + "-" + piece.to())
				.containsExactly("0-1", "1-2");
		assertThat(measured.pieces().get(0).slopePercent()).isEqualTo(2.5);
		assertThat(measured.pieces().get(1).slopePercent()).as("모르는 경사는 모른다로 남는다").isNull();
		assertThat(measured.pieces().get(1).stairs()).isTrue();
		// 그늘도 같이 온다 — 예전에는 조각을 옮기면서 네 칸만 복사해 그늘이 여기서 사라졌을 것이다 (S15P21E201-1895).
		assertThat(measured.pieces().get(0).shade()).isEqualTo(0.4);
		assertThat(measured.pieces().get(1).shade()).as("모르는 그늘은 모른다로 남는다").isNull();
	}

	@Test
	@DisplayName("선형을 버리는 어림 구간은 조각도 버린다 — 가리킬 자리가 없다. 조각이 없는 길(자동차)도 null 이다")
	void dropsPiecesWhenThereIsNoRoad() {
		RouteLeg estimatedWithPieces = new RouteLeg(TravelMode.WALK, 8_400, 21, null, null, null, true, "어림",
				RouteLeg.PROVIDER_STRAIGHT_LINE, ROAD, List.of(), null, PIECES);

		assertThat(measure(estimatedWithPieces).pieces()).isNull();
		assertThat(measure(routed(ROAD)).pieces()).as("조각이 없는 실제 길").isNull();
	}
}
