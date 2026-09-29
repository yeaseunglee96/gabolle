package com.gabolle.backend.route.adapter;

import java.util.List;
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
 * 일정이 정의한 이동시간 포트를 경로 조회로 채운다.
 *
 * 여행은 아홉 갈래(WALK·BUS·SUBWAY·TAXI·PRIVATE_CAR·RENTAL_CAR·BICYCLE·FERRY·OTHER)를 고를
 * 수 있고 경로는 셋(자동차·대중교통·도보)으로 계산한다. 그 대응을 여기서 한다 — 일정이 그 표를
 * 알면 길찾기 업체 사정이 일정 코드로 새어 든다.
 *
 * 자전거·배·기타는 도보로 본다. 정확해서가 아니라 그 셋에 맞는 계산이 없기 때문이고, 그래서
 * 결과는 언제나 어림값으로 나간다.
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
		return between(fromLat, fromLng, toLat, toLng, travelMode, false);
	}

	/** 계단·급경사를 피하는 길은 경로 질문에 그대로 싣는다 — 실제로 길을 고르는 것은 보행 그래프다. */
	@Override
	public TravelTime between(Double fromLat, Double fromLng, Double toLat, Double toLng, String travelMode,
			boolean stepFree) {
		if (fromLat == null || fromLng == null || toLat == null || toLng == null) {
			// 좌표가 없으면 아무것도 지어내지 않는다. 0 을 돌려주면 "붙어 있다" 가 된다.
			return TravelTime.unknown();
		}

		RouteLeg leg;
		try {
			leg = this.routeQueryService.find(
					new RouteQuery(fromLat, fromLng, toLat, toLng, modeOf(travelMode), null, stepFree));
		}
		catch (IllegalArgumentException invalidCoordinate) {
			// 좌표 범위를 벗어났다 — 저장된 값이 어긋난 것이고, 일정 생성을 멈출 이유는 아니다.
			return TravelTime.unknown();
		}

		return new TravelTime(leg.distanceM(), leg.durationMin(),
				leg.estimated() ? ItineraryItem.DataStatus.ESTIMATED : ItineraryItem.DataStatus.VERIFIED,
				fareOf(leg), pathOf(leg));
	}

	/**
	 * 이 구간이 지나는 길의 좌표 목록.
	 *
	 * <p>🔴 <b>어림잡은 구간의 선형은 버린다.</b> 직선거리로 어림잡을 때
	 * {@code StraightLineRouteEstimator} 는 출발·도착 두 점을 그대로 {@code path} 에 넣는데,
	 * 그것은 「어느 길로 가는지」가 아니라 <b>두 점을 이은 직선</b>이다. 그대로 저장하면 실제로
	 * 잰 길과 한 칸에 섞여, 화면이 직선을 실선으로 그리게 된다 — 고치려던 그림이 바로 그것이다
	 * (S15P21E201-1251·1234 — 「실제 길은 실선, 추정은 점선」).
	 *
	 * <p>점이 둘 미만이면 선이 아니라 점이므로 역시 비운다.
	 *
	 * @return 실제 길찾기 응답에서 온 좌표 목록. 없거나 어림값이면 {@code null}
	 */
	private static List<double[]> pathOf(RouteLeg leg) {
		if (leg.estimated()) {
			return null;
		}
		List<double[]> path = leg.path();
		return (path == null || path.size() < 2) ? null : path;
	}

	/**
	 * 이 구간의 이동 요금(원).
	 *
	 * 카카오모빌리티는 자동차 경로만 주므로 도보 구간에는 요금이 아예 없다. 그때 {@code 0} 을
	 * 넣지 않는다 — 0 은 「공짜」라는 다른 주장이고, 비워 두면 화면이 줄을 안 만든다.
	 * 대중교통도 값이 없을 수 있다. 고시에 요금이 없는 종류가 끼면 계산기가 {@code null} 을
	 * 내는데, 그것을 여기서 0 으로 바꾸면 계산기가 일부러 안 한 일을 대신 해 주는 꼴이 된다.
	 */
	private static Integer fareOf(RouteLeg leg) {
		if (leg.mode() == TravelMode.TRANSIT) {
			// 계산기가 낸 값을 그대로 옮긴다. null 이면 null 이다. 환승 할인·차액은 이미 이 숫자
			// 안에 들어 있다.
			return leg.transitFareKrw();
		}
		if (leg.mode() != TravelMode.CAR) {
			return null;
		}
		Integer taxi = leg.taxiFareKrw();
		if (taxi == null) {
			// 자동차인데 업체가 요금을 안 준 경우다(경로는 줬지만 요금 칸이 빈 응답). 지어내지 않는다.
			return null;
		}
		Integer toll = leg.tollFareKrw();
		return (toll == null) ? taxi : taxi + toll;
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
