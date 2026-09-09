package com.gabolle.backend.route.adapter;

import java.util.List;

import org.springframework.stereotype.Component;

import com.gabolle.backend.place.service.GeoDistance;
import com.gabolle.backend.route.config.RouteProperties;
import com.gabolle.backend.route.domain.RouteLeg;
import com.gabolle.backend.route.domain.RouteQuery;
import com.gabolle.backend.route.domain.TravelMode;

/**
 * 실제 경로를 못 받았을 때 직선거리로 지어내는 답 — S15P21E201-189.
 *
 * <h2>🔴 왜 오류를 돌려주지 않고 지어내는가</h2>
 *
 * 경로를 못 구하는 이유는 대부분 우리 잘못이 아니다 — 업체가 잠깐 느리거나, 그 이동수단을
 * 지원하지 않거나, 키가 아직 안 들어갔다. 그때 화면에 오류를 띄우면 <b>일정 전체가 안
 * 보인다.</b> 방문지 여덟 곳 중 한 구간의 경로를 못 구했다고 그날 일정을 못 그리는 것은
 * 사용자에게 훨씬 나쁘다.
 *
 * <p>그래서 답은 준다. 대신 <b>지어냈다는 사실을 응답에 싣는다</b>({@code estimated=true} 와
 * 이유). 표시 없이 내보내면 그건 추정이 아니라 창작이고, 화면은 그것을 실제 소요시간으로
 * 그린다 — {@code SbizPlaceLoader} 가 비운 칸을 채우지 않는 것과 같은 규칙이다.
 *
 * <h2>어떻게 지어내나</h2>
 *
 * 직선거리에 우회 비율을 곱해 이동 거리로 보고, 이동수단별 평균 속도로 나눠 시간을 낸다.
 * 🔴 <b>그 숫자들은 아무도 재지 않았다</b>({@link RouteProperties} javadoc). 실제 이동 기록이
 * 쌓이면 맞출 수 있게 설정으로 빼 두었다.
 *
 * <p>요금은 <b>절대 지어내지 않는다.</b> 택시 요금은 거리·시간·심야 할증·기본요금이 얽혀
 * 있어서 평균 속도로 지어낸 시간에 곱하면 그럴듯하게 틀린 금액이 나온다. 그럴듯하게 틀린
 * 값은 비어 있는 값보다 나쁘다 — 사용자가 그 금액을 믿고 지갑을 연다.
 */
@Component
public class StraightLineRouteEstimator {

	private final RouteProperties properties;

	public StraightLineRouteEstimator(RouteProperties properties) {
		this.properties = properties;
	}

	/**
	 * @param reason 왜 추정으로 내려왔는지. 응답에 그대로 실려 화면과 로그가 같은 문장을 본다
	 */
	public RouteLeg estimate(RouteQuery query, String reason) {
		double straightMeters = GeoDistance.meters(query.originLat(), query.originLng(),
				query.destLat(), query.destLng());
		int distanceM = (int) Math.round(straightMeters * this.properties.getDetourFactor());

		double speedKmh = speedFor(query.mode());
		// 0으로 나누는 것을 막는다 — 설정을 0으로 주면 시간이 무한이 되고, 그러면 화면에
		// "Infinity 분" 이 뜬다. 설정 실수가 화면까지 가지 않게 여기서 막는다.
		double safeSpeedKmh = speedKmh > 0 ? speedKmh : 1;
		int durationMin = Math.max(1, (int) Math.round(distanceM / (safeSpeedKmh * 1000.0 / 60.0)));

		return new RouteLeg(
				query.mode(),
				distanceM,
				durationMin,
				null, // 요금은 지어내지 않는다 — 클래스 javadoc 참고
				null,
				null, // 환승 수도 지어내지 않는다. 대중교통 경로를 물어볼 업체가 없다
				true,
				reason,
				RouteLeg.PROVIDER_STRAIGHT_LINE,
				// 🔴 경로 좌표는 출발·도착 두 점뿐이다. 그 사이를 곧은 선으로 채워 넣지 않는다 —
				//    점이 많으면 화면이 "길을 아는 경로" 처럼 그린다.
				List.of(new double[] { query.originLng(), query.originLat() },
						new double[] { query.destLng(), query.destLat() }),
				List.of());
	}

	private double speedFor(TravelMode mode) {
		return switch (mode) {
			case CAR -> this.properties.getCarSpeedKmh();
			case WALK -> this.properties.getWalkSpeedKmh();
			case TRANSIT -> this.properties.getTransitSpeedKmh();
		};
	}
}
