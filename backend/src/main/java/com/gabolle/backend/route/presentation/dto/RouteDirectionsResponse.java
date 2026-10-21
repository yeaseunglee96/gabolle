package com.gabolle.backend.route.presentation.dto;

import java.util.List;

import com.gabolle.backend.route.domain.RouteLeg;

/**
 * 경로 조회 응답. 칸의 뜻은 {@link RouteLeg} 와 같다.
 * 요금 칸은 값이 없으면 {@code null} 이고, 0("돈이 안 든다")과 다르다.
 */
public record RouteDirectionsResponse(
		String mode,
		int distanceM,
		int durationMin,
		Integer taxiFareKrw,
		Integer tollFareKrw,
		Integer transferCount,
		boolean estimated,
		String estimateReason,
		String provider,
		List<double[]> path,
		List<Step> steps,

		/**
		 * 대중교통 요금(원). 환승 할인·차액이 반영된 이 여정 전체의 금액이다.
		 * {@code null} 은 「모른다」(요금표에 없는 노선이 끼었다), {@code 0} 은 「걷기만 해서
		 * 공짜다」로 서로 다르다 — 화면이 둘을 같게 그리면 모르는 여정이 무료로 보인다.
		 */
		Integer transitFareKrw,

		/**
		 * 경사 조각 — S15P21E201-1630. 우리 보행 그래프가 찾은 걷기({@code provider=OSM_WALK_GRAPH})에만 있고 나머지는
		 * 빈 목록이다. 조각마다 {@code path[from]..path[to]}(둘 다 포함)의 경사(%, 방향 없음 · 모르면 null)와 계단 여부.
		 */
		List<Piece> pieces,

		/**
		 * 계단 피하기({@code stepFree=true})를 들어준 걷기인가. {@code false} 면 계단 없는 길을 못 찾아 가장 짧은 길로
		 * 대신 답한 것이라 계단이 섞였을 수 있다 — {@code pieces} 의 {@code stairs} 가 어디인지 알려 준다. 부탁이 없었거나
		 * 보행 그래프가 찾은 걷기가 아니면 {@code null}.
		 */
		Boolean stepFreeHonored) {

	/**
	 * 경사 조각 하나. {@code slopePercent} 가 {@code null} 이면 모른다 — 0(평지)과 다르다.
	 * {@code shade} 는 그늘(0~1, 1 이 하루 종일 그늘, 0.1 단위)이고 {@code null} 이면 모른다 — 0(볕)과 다르다
	 * (S15P21E201-1895). 지도가 경사·그늘을 합쳐 선을 칠하는 데 쓴다.
	 */
	public record Piece(int from, int to, Double slopePercent, boolean stairs, Double shade) {

		/** 그늘 칸이 생기기 전의 모양 — 그늘을 모르는 조각이다. */
		public Piece(int from, int to, Double slopePercent, boolean stairs) {
			this(from, to, slopePercent, stairs, null);
		}
	}

	/**
	 * 안내 한 줄 — 예: {@code name="해운대해수욕장삼거리"}, {@code guidance="송정 방면으로 우회전"}.
	 * {@code stops} 는 대중교통 단계에서 지나는 정류장·역(타는 곳~내리는 곳, 노선 순서). 그 밖의 단계는 빈 목록 — S15P21E201-1836.
	 */
	public record Step(String name, String guidance, int distanceM, int durationMin, List<Stop> stops) {
	}

	/** 지나는 정류장·역 하나. */
	public record Stop(String name, double lat, double lng) {
	}

	public static RouteDirectionsResponse from(RouteLeg leg) {
		List<Step> steps = leg.steps().stream()
				.map(step -> new Step(step.name(), step.guidance(), step.distanceM(), step.durationMin(),
						step.stops().stream().map(stop -> new Stop(stop.name(), stop.lat(), stop.lng())).toList()))
				.toList();
		return new RouteDirectionsResponse(
				leg.mode().name(),
				leg.distanceM(),
				leg.durationMin(),
				leg.taxiFareKrw(),
				leg.tollFareKrw(),
				leg.transferCount(),
				leg.estimated(),
				leg.estimateReason(),
				leg.provider(),
				leg.path(),
				steps,
				leg.transitFareKrw(),
				leg.pieces().stream()
						.map(piece -> new Piece(piece.from(), piece.to(), piece.slopePercent(), piece.stairs(),
								piece.shade()))
						.toList(),
				leg.stepFreeHonored());
	}
}
