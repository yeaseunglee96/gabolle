package com.gabolle.backend.route;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.route.adapter.StraightLineRouteEstimator;
import com.gabolle.backend.route.config.RouteProperties;
import com.gabolle.backend.route.domain.RouteLeg;
import com.gabolle.backend.route.domain.RouteQuery;
import com.gabolle.backend.route.domain.TravelMode;

class StraightLineRouteEstimatorTest {

	/** 해운대해수욕장 → 서면 부근. 직선으로 약 8km 다. */
	private static final RouteQuery HAEUNDAE_TO_SEOMYEON =
			new RouteQuery(35.1587, 129.1604, 35.1796, 129.0756, TravelMode.CAR);

	private final StraightLineRouteEstimator estimator = new StraightLineRouteEstimator(new RouteProperties());

	@Test
	@DisplayName("🔴 추정이라고 밝히고 이유를 함께 싣는다 — 이 표시가 없으면 화면이 실제 경로로 그린다")
	void marksItselfAsEstimated() {
		RouteLeg leg = this.estimator.estimate(HAEUNDAE_TO_SEOMYEON, "업체가 답을 못 줬다");

		assertThat(leg.estimated()).isTrue();
		assertThat(leg.estimateReason()).isEqualTo("업체가 답을 못 줬다");
		assertThat(leg.provider()).isEqualTo(RouteLeg.PROVIDER_STRAIGHT_LINE);
	}

	@Test
	@DisplayName("🔴 요금과 환승 수는 지어내지 않는다 — 그럴듯하게 틀린 금액은 빈 값보다 나쁘다")
	void neverInventsFareOrTransfers() {
		RouteLeg leg = this.estimator.estimate(HAEUNDAE_TO_SEOMYEON, "이유");

		assertThat(leg.taxiFareKrw()).isNull();
		assertThat(leg.tollFareKrw()).isNull();
		assertThat(leg.transferCount()).isNull();
	}

	@Test
	@DisplayName("거리는 직선거리에 우회 비율을 곱한 값이고, 안내는 빈 목록이지 null 이 아니다")
	void appliesDetourFactorAndLeavesStepsEmpty() {
		RouteProperties properties = new RouteProperties();
		properties.setDetourFactor(2.0);
		RouteLeg doubled = new StraightLineRouteEstimator(properties).estimate(HAEUNDAE_TO_SEOMYEON, "이유");
		RouteLeg plain = this.estimator.estimate(HAEUNDAE_TO_SEOMYEON, "이유");

		// 우회 비율 1.3 → 2.0 이면 거리가 그 비율만큼 늘어난다.
		assertThat(doubled.distanceM()).isGreaterThan(plain.distanceM());
		assertThat(doubled.steps()).isNotNull().isEmpty();
	}

	@Test
	@DisplayName("이동수단마다 걸리는 시간이 다르다 — 걷는 쪽이 자차보다 오래 걸린다")
	void slowerModeTakesLonger() {
		RouteLeg byCar = this.estimator.estimate(HAEUNDAE_TO_SEOMYEON, "이유");
		RouteLeg onFoot = this.estimator.estimate(
				new RouteQuery(35.1587, 129.1604, 35.1796, 129.0756, TravelMode.WALK), "이유");

		assertThat(onFoot.durationMin()).isGreaterThan(byCar.durationMin());
	}

	@Test
	@DisplayName("🔴 아주 가까운 두 지점도 0분이 아니라 1분이다 — 0분은 화면이 '이동 없음' 으로 읽는다")
	void neverReportsZeroMinutes() {
		RouteLeg leg = this.estimator.estimate(
				new RouteQuery(35.1587, 129.1604, 35.15871, 129.16041, TravelMode.WALK), "이유");

		assertThat(leg.durationMin()).isEqualTo(1);
	}

	@Test
	@DisplayName("🔴 속도를 0으로 설정해도 무한대가 새어 나가지 않는다 — 설정 실수가 화면까지 가면 안 된다")
	void zeroSpeedDoesNotLeakInfinity() {
		RouteProperties broken = new RouteProperties();
		broken.setCarSpeedKmh(0);

		RouteLeg leg = new StraightLineRouteEstimator(broken).estimate(HAEUNDAE_TO_SEOMYEON, "이유");

		assertThat(leg.durationMin()).isPositive();
	}

	@Test
	@DisplayName("경로 좌표는 출발·도착 두 점뿐이고 경도가 앞이다 — 사이를 채우면 길을 아는 척이 된다")
	void pathIsJustTheTwoEndsInLngLatOrder() {
		RouteLeg leg = this.estimator.estimate(HAEUNDAE_TO_SEOMYEON, "이유");

		assertThat(leg.path()).hasSize(2);
		assertThat(leg.path().get(0)[0]).isEqualTo(129.1604);
		assertThat(leg.path().get(0)[1]).isEqualTo(35.1587);
	}
}
