package com.gabolle.backend.itinerary.application.port;

/**
 * 일정 → 경로 방향의 유일한 문.
 * 쓰는 쪽인 일정이 이 인터페이스를 정의하고 제공하는 쪽인 경로가 구현한다. 일정 계층은 길찾기
 * 업체도 캐시도 추정 규칙도 모른다 — 묻는 것은 "여기서 저기까지 얼마나 걸리느냐" 하나다.
 * 실패를 예외로 알리지 않는다. 이 문 너머는 언제든 빈다(호출 한도, 업체 장애, 권역 밖 좌표).
 * 그것을 예외로 만들면 일정 생성 전체가 구간 하나 때문에 멈춘다. 그래서 언제나 답이 오고,
 * 그 답이 어림값인지는 {@link TravelTime#dataStatus()} 가 말한다.
 */
public interface TravelTimePort {

	/**
	 * 두 좌표 사이의 이동 거리와 시간.
	 * 좌표가 하나라도 없으면 {@link TravelTime#unknown()} 이다 — {@code 0} 을 돌려주지 않는다.
	 * 0 은 "붙어 있다" 는 다른 사실이다.
	 *
	 * @param travelMode {@code trip.travel_modes} 의 값 하나(WALK·BUS·SUBWAY·TAXI·…).
	 *     경로 계층이 자기 갈래로 옮겨 이해한다 — 일정은 그 대응표를 몰라도 된다
	 */
	TravelTime between(Double fromLat, Double fromLng, Double toLat, Double toLng, String travelMode);
}
