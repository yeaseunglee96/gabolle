package com.gabolle.backend.route;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.route.adapter.StraightLineRouteEstimator;
import com.gabolle.backend.route.application.RouteCache;
import com.gabolle.backend.route.application.RouteProviderPort;
import com.gabolle.backend.route.application.RouteQueryService;
import com.gabolle.backend.route.config.RouteProperties;
import com.gabolle.backend.route.domain.RouteLeg;
import com.gabolle.backend.route.domain.RouteQuery;
import com.gabolle.backend.route.domain.TravelMode;

class RouteQueryServiceTest {

	private static final RouteQuery CAR_QUERY =
			new RouteQuery(35.1587, 129.1604, 35.1796, 129.0756, TravelMode.CAR);

	private final RouteProperties properties = new RouteProperties();

	private RouteQueryService serviceWith(RouteProviderPort... providers) {
		return new RouteQueryService(List.of(providers), new StraightLineRouteEstimator(this.properties),
				new RouteCache(this.properties, Clock.fixed(Instant.parse("2026-09-08T00:00:00Z"), ZoneOffset.UTC)));
	}

	private static RouteLeg realLeg() {
		return new RouteLeg(TravelMode.CAR, 11132, 44, 15700, 0, null, false, null,
				RouteLeg.PROVIDER_KAKAO_MOBILITY, List.of(), List.of());
	}

	@Test
	@DisplayName("업체가 경로를 주면 그대로 답한다")
	void returnsProviderResult() {
		RouteQueryService service = serviceWith(new StubProvider(TravelMode.CAR, Optional.of(realLeg())));

		RouteLeg leg = service.find(CAR_QUERY);

		assertThat(leg.estimated()).isFalse();
		assertThat(leg.distanceM()).isEqualTo(11132);
		assertThat(leg.taxiFareKrw()).isEqualTo(15700);
	}

	@Test
	@DisplayName("🔴 업체가 답을 못 주면 오류가 아니라 추정이 온다 — 한 구간 때문에 일정 전체가 안 보이면 안 된다")
	void fallsBackToEstimateWhenProviderFails() {
		RouteQueryService service = serviceWith(new StubProvider(TravelMode.CAR, Optional.empty()));

		RouteLeg leg = service.find(CAR_QUERY);

		assertThat(leg.estimated()).isTrue();
		assertThat(leg.provider()).isEqualTo(RouteLeg.PROVIDER_STRAIGHT_LINE);
		assertThat(leg.distanceM()).isPositive();
	}

	@Test
	@DisplayName("🔴 대중교통은 물어볼 업체가 없어 추정으로 답하고, 환승 수가 비어 있다")
	void transitHasNoProviderYet() {
		RouteQueryService service = serviceWith(new StubProvider(TravelMode.CAR, Optional.of(realLeg())));

		RouteLeg leg = service.find(new RouteQuery(35.1587, 129.1604, 35.1796, 129.0756, TravelMode.TRANSIT));

		assertThat(leg.estimated()).isTrue();
		assertThat(leg.transferCount()).as("없는 것을 지어내지 않는다").isNull();
		assertThat(leg.steps()).isEmpty();
	}

	@Test
	@DisplayName("업체가 하나도 없어도 답이 나온다 — 키가 안 들어간 배포에서도 화면이 살아 있어야 한다")
	void worksWithNoProviderAtAll() {
		RouteQueryService service = serviceWith();

		assertThat(service.find(CAR_QUERY).estimated()).isTrue();
	}

	@Test
	@DisplayName("🔴 같은 경로를 두 번 물으면 업체는 한 번만 부른다 — 캐시가 실제로 돈다")
	void callsProviderOnlyOncePerRoute() {
		StubProvider provider = new StubProvider(TravelMode.CAR, Optional.of(realLeg()));
		RouteQueryService service = serviceWith(provider);

		service.find(CAR_QUERY);
		service.find(CAR_QUERY);

		assertThat(provider.calls.get()).isEqualTo(1);
	}

	@Test
	@DisplayName("🔴 추정이 나온 뒤에 다시 물으면 업체를 또 부른다 — 실패를 캐시하면 되살아나도 계속 추정이 나간다")
	void retriesProviderAfterAnEstimate() {
		StubProvider provider = new StubProvider(TravelMode.CAR, Optional.empty());
		RouteQueryService service = serviceWith(provider);

		service.find(CAR_QUERY);
		service.find(CAR_QUERY);

		assertThat(provider.calls.get()).isEqualTo(2);
	}

	/** 해운대 해리단길의 두 식당 — 운영에서 대중교통 178분이 나왔던 구간. 직선 약 655m. */
	private static final RouteQuery SHORT_TRANSIT =
			new RouteQuery(35.1600, 129.1554, 35.1653, 129.1586, TravelMode.TRANSIT);

	private static RouteLeg transitLeg(int durationMin) {
		return new RouteLeg(TravelMode.TRANSIT, 838, durationMin, null, null, 1, true, "배차간격",
				"TRANSIT_NETWORK", List.of(), List.of(), 1550);
	}

	@Test
	@DisplayName("🔴 대중교통이 걷기보다 느리면 도보로 답한다 — 650m 가 178분으로 나오던 것 (S15P21E201-1564)")
	void walksWhenWalkingBeatsTransit() {
		RouteQueryService service = serviceWith(new StubProvider(TravelMode.TRANSIT, Optional.of(transitLeg(178))));

		RouteLeg leg = service.find(SHORT_TRANSIT);

		assertThat(leg.mode()).isEqualTo(TravelMode.WALK);
		assertThat(leg.durationMin()).as("655m × 우회 1.3 ÷ 시속 4km ≈ 13분").isBetween(10, 16);
		assertThat(leg.estimated()).as("도보 쪽도 어림값이다 — 표시 없이 나가면 화면이 잰 값처럼 그린다").isTrue();
		assertThat(leg.transitFareKrw()).as("걷는 데는 요금이 없다 — 버스 요금을 옮겨 적지 않는다").isNull();
	}

	@Test
	@DisplayName("대중교통이 더 빠르면 그대로 대중교통이다 — 먼 구간(해운대→서면)을 걷게 하지 않는다")
	void keepsTransitWhenItIsFaster() {
		RouteQuery far = new RouteQuery(35.1587, 129.1604, 35.1578, 129.0592, TravelMode.TRANSIT);
		RouteQueryService service = serviceWith(new StubProvider(TravelMode.TRANSIT, Optional.of(transitLeg(45))));

		RouteLeg leg = service.find(far);

		assertThat(leg.mode()).isEqualTo(TravelMode.TRANSIT);
		assertThat(leg.durationMin()).isEqualTo(45);
		assertThat(leg.transitFareKrw()).isEqualTo(1550);
	}

	@Test
	@DisplayName("자동차는 걷기와 견주지 않는다 — 고른 것이 차면 차로 답한다")
	void carIsNeverSwappedForWalking() {
		RouteLeg slowCar = new RouteLeg(TravelMode.CAR, 838, 178, 5000, 0, null, false, null,
				RouteLeg.PROVIDER_KAKAO_MOBILITY, List.of(), List.of());
		RouteQueryService service = serviceWith(new StubProvider(TravelMode.CAR, Optional.of(slowCar)));

		RouteLeg leg = service.find(new RouteQuery(35.1600, 129.1554, 35.1653, 129.1586, TravelMode.CAR));

		assertThat(leg.mode()).isEqualTo(TravelMode.CAR);
	}

	/** 정해진 답만 돌려주고 호출 횟수를 센다. */
	private static final class StubProvider implements RouteProviderPort {

		private final TravelMode supported;

		private final Optional<RouteLeg> answer;

		private final AtomicInteger calls = new AtomicInteger();

		private StubProvider(TravelMode supported, Optional<RouteLeg> answer) {
			this.supported = supported;
			this.answer = answer;
		}

		@Override
		public boolean supports(TravelMode mode) {
			return mode == this.supported;
		}

		@Override
		public Optional<RouteLeg> find(RouteQuery query) {
			this.calls.incrementAndGet();
			return this.answer;
		}

		@Override
		public String providerName() {
			return "STUB";
		}
	}
}
