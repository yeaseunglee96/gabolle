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
		Integer transitFareKrw) {

	/** 대중교통 요금이라는 것이 없는 경로(자동차·직선거리)용 — {@code transitFareKrw} 를 {@code null} 로 둔다. */
	public RouteLeg(TravelMode mode, int distanceM, int durationMin, Integer taxiFareKrw, Integer tollFareKrw,
			Integer transferCount, boolean estimated, String estimateReason, String provider,
			List<double[]> path, List<Step> steps) {
		this(mode, distanceM, durationMin, taxiFareKrw, tollFareKrw, transferCount, estimated, estimateReason,
				provider, path, steps, null);
	}

	/** 단계별 안내 한 줄. */
	public record Step(String name, String guidance, int distanceM, int durationMin) {
	}

	public static final String PROVIDER_KAKAO_MOBILITY = "KAKAO_MOBILITY";

	public static final String PROVIDER_STRAIGHT_LINE = "STRAIGHT_LINE";
}
