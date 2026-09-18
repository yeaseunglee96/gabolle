package com.gabolle.backend.route.domain;

import java.util.List;

/**
 * 두 좌표 사이의 경로 하나 — S15P21E201-184.
 *
 * <p>이 record 는 <b>어디서 왔는지와 무관하게 같은 모양</b>이다. 카카오가 준 실제 경로도,
 * 직선거리로 지어낸 추정도 이것으로 돌아온다. 다른 모양으로 두면 부르는 쪽이 두 갈래를
 * 각각 다루게 되고, 그러면 추정을 받은 화면이 실제 경로인 줄 알고 그리는 날이 온다.
 *
 * @param distanceM 미터. 실제 경로면 도로를 따라 잰 값이고, 추정이면 직선거리에 우회 비율을
 *        곱한 값이다
 * @param durationMin 분. 🔴 <b>{@code 0} 이 아니라 최소 {@code 1} 이다</b> — 아주 가까운 두
 *        지점도 "0분" 이라고 답하면 화면이 이동이 없다고 읽는다
 * @param taxiFareKrw 택시 요금. 이동수단이 {@link TravelMode#CAR} 이고 실제 경로일 때만 값이
 *        있다. 없으면 {@code null} 이고 {@code 0} 이 아니다 — 0 은 "공짜" 라는 다른 사실이다
 * @param tollFareKrw 통행료. 같은 규칙이다
 * @param transferCount 환승 수. 대중교통 경로에서만 의미가 있고, 지금은 <b>항상</b>
 *        {@code null} 이다 — 경로를 물어볼 업체가 없어서다
 * @param estimated 실제 경로 응답이 아니라 추정인가. 🔴 이 칸이 이 응답에서 가장 중요하다.
 *        화면은 이 값이 참이면 "예상" 이라고 밝혀야 한다
 * @param estimateReason 왜 추정인가. {@code estimated} 가 거짓이면 {@code null}
 * @param provider 값을 만든 곳 — {@code KAKAO_MOBILITY} 또는 {@code STRAIGHT_LINE}
 * @param path 경로 좌표. <b>{@code [경도, 위도]} 순서</b>다(GeoJSON 과 같은 순서 — 지도
 *        라이브러리가 그 순서를 기대한다). 추정이면 출발·도착 두 점뿐이다
 * @param steps 단계별 안내. 추정이면 빈 목록이지 {@code null} 이 아니다
 * @param transitFareKrw 대중교통 요금(원) — S15P21E201-1291. 환승 할인·차액이 반영된 <b>이 여정
 *        전체의 금액</b>이고 구간별 합이 아니다. 🔴 <b>요금을 모르는 노선이 하나라도 끼면
 *        {@code null}</b> 이다 — 아는 것만 더하면 실제보다 싸고, 그것은 틀린 값을 자신 있게
 *        보여주는 것이다. 🔴 <b>{@code 0} 과 다르다.</b> 0 은 「걷기만 해서 공짜다」라는 다른 사실이다
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

	/**
	 * 🔴 S15P21E201-1291 — {@code transitFareKrw} 없이 만들던 기존 자리를 위해 둔다.
	 *
	 * <p>대중교통 요금을 모르는 것으로 본다({@code null}). 자동차·직선거리 경로가 이 자리를 쓴다 —
	 * 그쪽에는 대중교통 요금이라는 것이 없다.
	 */
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
