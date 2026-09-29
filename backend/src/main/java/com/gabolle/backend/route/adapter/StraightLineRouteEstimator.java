package com.gabolle.backend.route.adapter;

import java.util.List;

import org.springframework.stereotype.Component;

import com.gabolle.backend.place.service.GeoDistance;
import com.gabolle.backend.route.config.RouteProperties;
import com.gabolle.backend.route.domain.RouteLeg;
import com.gabolle.backend.route.domain.RouteQuery;
import com.gabolle.backend.route.domain.TravelMode;

/**
 * 실제 경로를 못 받았을 때 직선거리로 내는 어림값. 한 구간 때문에 일정 전체가 안 보이는
 * 것보다 낫기 때문에 오류 대신 답을 준다.
 *
 * 대신 estimated=true 와 이유를 응답에 싣는다 — 표시 없이 나가면 화면이 실제 소요시간으로
 * 그린다.
 *
 * 직선거리에 우회 비율을 곱해 거리로 보고 이동수단별 평균 속도로 나눈다. 그 숫자들은
 * 실측이 아니라 설정값이다 — RouteProperties 참고.
 *
 * 요금은 지어내지 않는다. 택시 요금은 거리·시간·심야 할증·기본요금이 얽혀 있어 어림 시간에
 * 곱하면 그럴듯하게 틀린 금액이 나오고, 사용자는 그 금액을 믿고 지갑을 연다.
 */
@Component
public class StraightLineRouteEstimator {

	private final RouteProperties properties;

	public StraightLineRouteEstimator(RouteProperties properties) {
		this.properties = properties;
	}

	/** reason 은 응답에 그대로 실려 화면과 로그가 같은 문장을 본다. */
	public RouteLeg estimate(RouteQuery query, String reason) {
		double straightMeters = GeoDistance.meters(query.originLat(), query.originLng(),
				query.destLat(), query.destLng());
		int distanceM = (int) Math.round(straightMeters * this.properties.getDetourFactor());

		double speedKmh = speedFor(query.mode());
		// 설정을 0으로 주면 시간이 무한이 되어 화면에 "Infinity 분" 이 뜬다.
		double safeSpeedKmh = speedKmh > 0 ? speedKmh : 1;
		int durationMin = Math.max(1, (int) Math.round(distanceM / (safeSpeedKmh * 1000.0 / 60.0)));

		return new RouteLeg(
				query.mode(),
				distanceM,
				durationMin,
				null, // 요금은 지어내지 않는다
				null,
				null, // 환승 수도 지어내지 않는다
				true,
				reason,
				RouteLeg.PROVIDER_STRAIGHT_LINE,
				// 경로 좌표는 두 점뿐이다. 사이를 채우면 화면이 길을 아는 경로처럼 그린다.
				List.of(new double[] { query.originLng(), query.originLat() },
						new double[] { query.destLng(), query.destLat() }),
				List.of());
	}

	/**
	 * 우회 없이 직선으로 곧장 걸어도 걸리는 분 — 실제로 걷는 시간은 이보다 짧을 수 없다(아래 한계).
	 *
	 * <p>대중교통과 걷기를 견줄 때 보행 그래프 탐색을 아끼는 데 쓴다. 이 값조차 대중교통보다 느리면 실제 길을 찾아
	 * 봐도 걷기가 이길 수 없다. 반올림하지 않고 내림한다 — 한계가 실제보다 커지면 안 된다.
	 */
	public int straightWalkMinutesFloor(RouteQuery query) {
		double straightMeters = GeoDistance.meters(query.originLat(), query.originLng(),
				query.destLat(), query.destLng());
		double speedKmh = this.properties.getWalkSpeedKmh() > 0 ? this.properties.getWalkSpeedKmh() : 1;
		return (int) Math.floor(straightMeters / (speedKmh * 1000.0 / 60.0));
	}

	private double speedFor(TravelMode mode) {
		return switch (mode) {
			case CAR -> this.properties.getCarSpeedKmh();
			case WALK -> this.properties.getWalkSpeedKmh();
			case TRANSIT -> this.properties.getTransitSpeedKmh();
		};
	}
}
