package com.gabolle.backend.route.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.itinerary.application.port.TravelTime;
import com.gabolle.backend.route.application.RouteQueryService;
import com.gabolle.backend.route.domain.RouteLeg;
import com.gabolle.backend.route.domain.TravelMode;

/**
 * 구간 요금이 어디까지 오고 어디서 비는가 — S15P21E201-1109.
 *
 * <h2>🔴 이 시험이 막는 것</h2>
 *
 * 요금은 <b>모르는 것과 0원인 것이 다르다.</b> {@code null} 은 "얼마인지 모른다" 이고
 * {@code 0} 은 "공짜다" 다. 둘을 같게 다루면 <b>요금 출처가 없는 이동수단이 화면에서 전부
 * 「무료」로 보인다.</b> 카카오모빌리티는 자동차 경로만 주므로 도보·대중교통에는 요금이
 * 아예 없고, 그 자리를 0 으로 메우면 정확히 그 일이 일어난다.
 *
 * <p>그리고 그 고장은 <b>아무 오류도 안 낸다</b> — 숫자가 들어 있고 화면도 잘 그려진다.
 */
class RouteTravelTimeFareTest {

	private static final double FROM_LAT = 35.1587;

	private static final double FROM_LNG = 129.1604;

	private static final double TO_LAT = 35.1796;

	private static final double TO_LNG = 129.0756;

	private final RouteQueryService routeQueryService = mock(RouteQueryService.class);

	private final RouteTravelTimeAdapter adapter = new RouteTravelTimeAdapter(this.routeQueryService);

	private RouteLeg leg(TravelMode mode, Integer taxiFareKrw, Integer tollFareKrw) {
		return new RouteLeg(mode, 8_400, 21, taxiFareKrw, tollFareKrw, null, false, null, "TEST", List.of(),
				List.of());
	}

	private TravelTime measure(String travelMode, RouteLeg leg) {
		when(this.routeQueryService.find(any())).thenReturn(leg);
		return this.adapter.between(FROM_LAT, FROM_LNG, TO_LAT, TO_LNG, travelMode);
	}

	@Test
	@DisplayName("택시면 요금이 실린다 — 받아 놓고 버리던 값이다")
	void carriesTaxiFare() {
		TravelTime measured = measure("TAXI", leg(TravelMode.CAR, 12_800, null));

		assertThat(measured.fareKrw()).isEqualTo(12_800);
		assertThat(measured.hasFare()).isTrue();
	}

	@Test
	@DisplayName("통행료가 있으면 더한다 — 둘 다 내는 돈이다")
	void addsTollToTaxiFare() {
		TravelTime measured = measure("PRIVATE_CAR", leg(TravelMode.CAR, 12_800, 1_200));

		assertThat(measured.fareKrw()).isEqualTo(14_000);
	}

	@Test
	@DisplayName("🔴 도보는 비운다 — 0 이 아니다. 0 은 「무료」라는 주장이다")
	void leavesWalkFareEmpty() {
		TravelTime measured = measure("WALK", leg(TravelMode.WALK, null, null));

		assertThat(measured.fareKrw()).isNull();
		assertThat(measured.hasFare()).isFalse();
	}

	@Test
	@DisplayName("🔴 대중교통도 비운다 — 업체가 안 주는 것을 지어내지 않는다")
	void leavesTransitFareEmpty() {
		// 카카오모빌리티는 자동차 경로만 준다. 대중교통 요금은 우리에게 아예 없다.
		TravelTime measured = measure("SUBWAY", leg(TravelMode.TRANSIT, null, null));

		assertThat(measured.fareKrw()).isNull();
	}

	@Test
	@DisplayName("🔴 자동차인데 업체가 요금을 안 주면 비운다 — 거리로 지어내지 않는다")
	void leavesEmptyWhenProviderGaveNoFare() {
		TravelTime measured = measure("TAXI", leg(TravelMode.CAR, null, null));

		assertThat(measured.fareKrw()).isNull();
	}

	@Test
	@DisplayName("좌표가 없으면 요금도 비어 있다")
	void unknownHasNoFare() {
		TravelTime measured = this.adapter.between(null, FROM_LNG, TO_LAT, TO_LNG, "TAXI");

		assertThat(measured.known()).isFalse();
		assertThat(measured.fareKrw()).isNull();
	}
}
