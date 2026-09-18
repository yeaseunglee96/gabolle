package com.gabolle.backend.route.presentation.dto;

import java.util.List;

import com.gabolle.backend.route.domain.RouteLeg;

/**
 * 경로 조회 응답 — S15P21E201-184. <b>프론트가 이 모양만 보고 화면을 만들 수 있어야 한다</b>는
 * 것이 이 티켓의 완료 기준이라, 칸마다 무엇이 들어오고 언제 비는지를 여기 적는다.
 *
 * @param mode {@code CAR} · {@code TRANSIT} · {@code WALK}
 * @param distanceM 미터
 * @param durationMin 분. 아주 가까워도 최소 1이다
 * @param taxiFareKrw 택시 요금. 🔴 <b>추정 응답에는 없다</b>({@code null}). {@code 0} 과 다르다 —
 *        0 은 "돈이 안 든다" 는 사실이고 {@code null} 은 "모른다" 다
 * @param tollFareKrw 통행료. 같은 규칙
 * @param transferCount 환승 수. 🔴 <b>지금은 항상 {@code null}</b> — 대중교통 경로를 물어볼
 *        업체가 정해지지 않았다
 * @param estimated 🔴 <b>화면이 반드시 봐야 하는 칸.</b> 참이면 실제 경로가 아니라 직선거리로
 *        지어낸 값이라, "예상" 이라고 밝혀야 한다
 * @param estimateReason 왜 추정인지. {@code estimated} 가 거짓이면 {@code null}
 * @param provider {@code KAKAO_MOBILITY} 또는 {@code STRAIGHT_LINE}
 * @param path 경로 좌표. 🔴 <b>{@code [경도, 위도]} 순서</b>다 — GeoJSON 과 같고, 지도
 *        라이브러리 대부분이 그 순서를 기대한다. 추정이면 출발·도착 두 점뿐이다
 * @param steps 단계별 안내. 추정이면 <b>빈 배열</b>이지 {@code null} 이 아니다
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
		 * 🔴 S15P21E201-1291 — 대중교통 요금(원). 환승 할인·차액이 반영된 <b>이 여정 전체</b>의 금액이다.
		 *
		 * <p>🔴 <b>{@code null} 과 {@code 0} 이 다르다.</b> {@code null} 은 「모른다」(요금표에 없는
		 * 노선이 끼었다), {@code 0} 은 「걷기만 해서 공짜다」이다. <b>화면이 둘을 같게 그리면 안 된다</b> —
		 * 모르는 것을 0 으로 더하면 그 여정이 「무료」로 보인다.
		 */
		Integer transitFareKrw) {

	/** 안내 한 줄 — 예: {@code name="해운대해수욕장삼거리"}, {@code guidance="송정 방면으로 우회전"}. */
	public record Step(String name, String guidance, int distanceM, int durationMin) {
	}

	public static RouteDirectionsResponse from(RouteLeg leg) {
		List<Step> steps = leg.steps().stream()
				.map(step -> new Step(step.name(), step.guidance(), step.distanceM(), step.durationMin()))
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
				leg.transitFareKrw());
	}
}
