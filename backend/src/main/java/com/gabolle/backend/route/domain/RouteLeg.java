package com.gabolle.backend.route.domain;

import java.util.List;

/**
 * 두 좌표 사이의 경로 하나. 카카오가 준 실제 경로도, 직선거리로 지어낸 추정도 같은 모양으로
 * 돌아온다 — 모양이 갈리면 추정을 받은 화면이 실제 경로인 줄 알고 그린다.
 *
 * @param distanceM 미터. 실제 경로면 도로를 따라 잰 값이고, 추정이면 직선거리에 우회 비율을
 *        곱한 값이다
 * @param durationMin 분. {@code 0} 이 아니라 최소 {@code 1} 이다 — "0분" 이면 화면이 이동이
 *        없다고 읽는다
 * @param taxiFareKrw 택시 요금(원). {@link TravelMode#CAR} 이고 실제 경로일 때만 값이 있다.
 *        없으면 {@code null} 이고 {@code 0}("공짜")과 다르다
 * @param tollFareKrw 통행료(원). 같은 규칙이다
 * @param transferCount 환승 수. 지금은 항상 {@code null} 이다 — 경로를 물어볼 업체가 없다
 * @param estimated 실제 경로 응답이 아니라 추정인가. 참이면 화면이 "예상" 이라고 밝혀야 한다
 * @param estimateReason 왜 추정인가. {@code estimated} 가 거짓이면 {@code null}
 * @param provider 값을 만든 곳 — {@code KAKAO_MOBILITY} 또는 {@code STRAIGHT_LINE}
 * @param path 경로 좌표. {@code [경도, 위도]} 순서다(GeoJSON 과 같은 순서 — 지도 라이브러리가
 *        그 순서를 기대한다). 추정이면 출발·도착 두 점뿐이다
 * @param steps 단계별 안내. 추정이면 빈 목록이지 {@code null} 이 아니다
 * @param transitFareKrw 대중교통 요금(원). 환승 할인·차액이 반영된 이 여정 전체의 금액이고
 *        구간별 합이 아니다. 요금을 모르는 노선이 하나라도 끼면 {@code null} 이다 — 아는 것만
 *        더하면 실제보다 싸다. {@code 0} 은 「걷기만 해서 공짜다」라는 다른 사실이다
 * @param pieces 경로를 경사·계단이 같은 조각으로 나눈 것 — S15P21E201-1630. 우리 보행 그래프가 찾은 걷기에만
 *        있고 나머지는 빈 목록이다(모른다를 지어내지 않는다)
 */
public record RouteLeg(
		TravelMode mode,
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
		Integer transitFareKrw,
		List<Piece> pieces) {

	public RouteLeg {
		pieces = (pieces == null) ? List.of() : List.copyOf(pieces);
	}

	/** 경사 조각이 없는 경로(업체·직선 어림·대중교통)용 — {@code pieces} 를 빈 목록으로 둔다. */
	public RouteLeg(TravelMode mode, int distanceM, int durationMin, Integer taxiFareKrw, Integer tollFareKrw,
			Integer transferCount, boolean estimated, String estimateReason, String provider,
			List<double[]> path, List<Step> steps, Integer transitFareKrw) {
		this(mode, distanceM, durationMin, taxiFareKrw, tollFareKrw, transferCount, estimated, estimateReason,
				provider, path, steps, transitFareKrw, List.of());
	}

	/** 대중교통 요금이라는 것이 없는 경로(자동차·직선거리)용 — {@code transitFareKrw} 를 {@code null} 로 둔다. */
	public RouteLeg(TravelMode mode, int distanceM, int durationMin, Integer taxiFareKrw, Integer tollFareKrw,
			Integer transferCount, boolean estimated, String estimateReason, String provider,
			List<double[]> path, List<Step> steps) {
		this(mode, distanceM, durationMin, taxiFareKrw, tollFareKrw, transferCount, estimated, estimateReason,
				provider, path, steps, null, List.of());
	}

	/**
	 * 단계별 안내 한 줄.
	 *
	 * @param stops 이 단계에서 지나는 정류장·역 — 타는 곳부터 내리는 곳까지 노선 순서대로(둘 다 포함). S15P21E201-1836.
	 *        대중교통 단계에만 있고, 걷기·자동차 단계와 다른 길찾기는 빈 목록이다. 앱의 「탑승 중」 화면이 남은 정류장과
	 *        내릴 곳을 알리는 데 쓴다 — 전에는 이 목록을 계산해 경로선 좌표로만 쓰고 이름을 버렸다
	 */
	public record Step(String name, String guidance, int distanceM, int durationMin, List<StopPoint> stops) {

		public Step {
			stops = (stops == null) ? List.of() : List.copyOf(stops);
		}

		/** 지나는 정류장을 모르는(대중교통이 아닌) 단계용 — {@code stops} 를 빈 목록으로 둔다. */
		public Step(String name, String guidance, int distanceM, int durationMin) {
			this(name, guidance, distanceM, durationMin, List.of());
		}
	}

	/** 지나는 정류장·역 하나. */
	public record StopPoint(String name, double lat, double lng) {
	}

	/**
	 * 경로의 한 조각 — S15P21E201-1630. {@code path[from]} 부터 {@code path[to]} 까지(둘 다 포함) 경사·계단이 같다.
	 *
	 * @param slopePercent 그 길의 보통 기울기(%, 방향 없는 크기 — 오르막·내리막을 안 가른다). 모르면 {@code null} —
	 *        30m 미만 조각·다리·터널·길 밖(출발·도착을 길에 붙이는 토막)이다. {@code 0}(평지)과 다르다
	 * @param stairs 계단인가
	 */
	public record Piece(int from, int to, Double slopePercent, boolean stairs) {
	}

	public static final String PROVIDER_KAKAO_MOBILITY = "KAKAO_MOBILITY";

	public static final String PROVIDER_STRAIGHT_LINE = "STRAIGHT_LINE";

	/** 우리 보행 그래프(오픈스트리트맵 걷는 길)가 찾은 걷기 — S15P21E201-1630. */
	public static final String PROVIDER_WALK_GRAPH = "OSM_WALK_GRAPH";
}
