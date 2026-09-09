package com.gabolle.backend.route.adapter;

import java.util.Locale;
import java.util.Set;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.gabolle.backend.itinerary.application.port.TravelTime;
import com.gabolle.backend.itinerary.application.port.TravelTimePort;
import com.gabolle.backend.itinerary.domain.ItineraryItem;
import com.gabolle.backend.route.application.RouteQueryService;
import com.gabolle.backend.route.domain.RouteLeg;
import com.gabolle.backend.route.domain.RouteQuery;
import com.gabolle.backend.route.domain.TravelMode;

/**
 * 일정이 정의한 이동시간 포트를 경로 조회로 채운다 — S15P21E201-179.
 *
 * <p>{@code PlaceEventSchedulePort} 를 장소가 구현하는 것과 같은 방향이다 — 쓰는 쪽(일정)이
 * 문을 정의하고 제공하는 쪽(경로)이 그 문을 연다.
 *
 * <h2>🔴 여행이 고른 이동수단과 경로가 계산하는 갈래는 목록이 다르다</h2>
 *
 * 여행은 아홉 갈래를 고를 수 있다(WALK·BUS·SUBWAY·TAXI·PRIVATE_CAR·RENTAL_CAR·BICYCLE·
 * FERRY·OTHER). 경로는 셋으로 계산한다(자동차·대중교통·도보). 그 대응을 <b>여기서</b> 한다 —
 * 일정이 그 표를 알면 길찾기 업체 사정이 일정 코드로 새어 든다.
 *
 * <p>자전거·배·기타는 어느 쪽으로도 옮기지 않고 <b>도보로 본다.</b> 정확해서가 아니라
 * 그 셋에 맞는 계산이 없기 때문이고, 그래서 결과는 언제나 어림값으로 나간다 — 실제 경로인
 * 척하지 않는다.
 */
@Component
public class RouteTravelTimeAdapter implements TravelTimePort {

	private static final Set<String> CAR_MODES = Set.of("TAXI", "PRIVATE_CAR", "RENTAL_CAR");

	private static final Set<String> TRANSIT_MODES = Set.of("BUS", "SUBWAY");

	private final RouteQueryService routeQueryService;

	@Autowired
	public RouteTravelTimeAdapter(RouteQueryService routeQueryService) {
		this.routeQueryService = routeQueryService;
	}

	@Override
	public TravelTime between(Double fromLat, Double fromLng, Double toLat, Double toLng, String travelMode) {
		if (fromLat == null || fromLng == null || toLat == null || toLng == null) {
			// 🔴 좌표가 없으면 아무것도 지어내지 않는다. 0 을 돌려주면 "붙어 있다" 가 된다.
			return TravelTime.unknown();
		}

		RouteLeg leg;
		try {
			leg = this.routeQueryService.find(
					new RouteQuery(fromLat, fromLng, toLat, toLng, modeOf(travelMode)));
		}
		catch (IllegalArgumentException invalidCoordinate) {
			// 좌표 범위를 벗어났다 — 저장된 값이 어긋난 것이고, 일정 생성을 멈출 이유는 아니다.
			return TravelTime.unknown();
		}

		return new TravelTime(leg.distanceM(), leg.durationMin(),
				leg.estimated() ? ItineraryItem.DataStatus.ESTIMATED : ItineraryItem.DataStatus.VERIFIED);
	}

	private static TravelMode modeOf(String travelMode) {
		if (travelMode == null) {
			return TravelMode.WALK;
		}
		String normalized = travelMode.trim().toUpperCase(Locale.ROOT);
		if (CAR_MODES.contains(normalized)) {
			return TravelMode.CAR;
		}
		if (TRANSIT_MODES.contains(normalized)) {
			return TravelMode.TRANSIT;
		}
		// WALK·BICYCLE·FERRY·OTHER 와 모르는 값은 전부 도보로 본다 — 클래스 주석 참고.
		return TravelMode.WALK;
	}
}
