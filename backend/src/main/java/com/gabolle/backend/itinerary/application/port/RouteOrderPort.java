package com.gabolle.backend.itinerary.application.port;

import java.util.List;
import java.util.UUID;

/**
 * 일정 → 동선 방향의 유일한 문.
 * 쓰는 쪽인 일정이 이 인터페이스를 정의하고 제공하는 쪽인 최적화가 구현한다. 일정 계층은
 * OR-Tools 도 파이썬도 직선거리 어림도 모른다 — 묻는 것은 "이 자리들을 도는 가장 가까운
 * 차례가 무엇이냐" 하나다. {@link TravelTimePort} 와 같은 이유로 같은 모양이다.
 * <p>
 * 실패를 예외로 알리지 않는다. 이 문 너머는 언제든 빈다(풀이기 미설치, 시간 초과, 좌표 없음).
 * 그것을 예외로 만들면 동선 하나 때문에 일정 생성 전체가 멈춘다. 답을 못 내면 빈 목록이고,
 * 그때 부르는 쪽은 <b>오늘 하던 차례 그대로</b> 간다.
 */
public interface RouteOrderPort {

	/**
	 * 출발점에서 시작해 이 장소들을 모두 도는, 이동이 가장 적은 차례.
	 * <p>
	 * 🔴 <b>묻는 것은 차례뿐이다.</b> "하루 안에 들어가느냐" 는 묻지 않는다 — 그건 차례가
	 * 정해진 뒤에 시각을 까는 쪽이 정한다. 그래서 몇 시부터 몇 시까지인지도 넘기지 않는다.
	 * <p>
	 * 돌려주는 목록은 {@code request.placeIds()} 와 <b>같은 것이 같은 수만큼</b> 들어 있어야
	 * 한다 — 이 판은 차례만 바꾸고 장소를 빼거나 더하지 않는다. 그 약속이 깨진 답은 부르는
	 * 쪽이 통째로 버리고 원래 차례를 쓴다.
	 *
	 * @return 다시 세운 차례. 못 냈으면 <b>빈 목록</b> ({@code null} 이 아니다)
	 */
	List<UUID> shortestOrder(RouteOrderRequest request);

	/**
	 * @param originLat 하루가 시작되는 자리({@code Trip.originLat}). 없으면 {@code null} 이고
	 *     그때 구현은 최적화를 건너뛴다 — 출발점을 지어내면 그 지점을 중심으로 차례가 뒤틀린다
	 * @param placeIds 그 날 다녀올 장소. 지금 차례(순위 순)대로 들어온다
	 * @param travelMode {@code trip.travel_modes} 의 값 하나(WALK·BUS·SUBWAY·TAXI·…).
	 *     구현이 자기 갈래로 옮겨 이해한다 — 일정은 그 대응표를 몰라도 된다
	 */
	record RouteOrderRequest(Double originLat, Double originLng, List<UUID> placeIds, String travelMode) {
	}
}
