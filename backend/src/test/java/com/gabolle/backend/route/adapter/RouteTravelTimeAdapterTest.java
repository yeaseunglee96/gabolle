package com.gabolle.backend.route.adapter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.gabolle.backend.itinerary.application.port.TravelTime;
import com.gabolle.backend.itinerary.domain.ItineraryItem;
import com.gabolle.backend.route.application.RouteQueryService;
import com.gabolle.backend.route.domain.RouteLeg;
import com.gabolle.backend.route.domain.RouteQuery;
import com.gabolle.backend.route.domain.TravelMode;

class RouteTravelTimeAdapterTest {

	private final RouteQueryService routeQueryService = mock(RouteQueryService.class);

	private final RouteTravelTimeAdapter adapter = new RouteTravelTimeAdapter(this.routeQueryService);

	private static RouteLeg leg(boolean estimated) {
		return new RouteLeg(TravelMode.CAR, 11132, 44, null, null, null, estimated,
				estimated ? "업체가 답을 못 줬다" : null,
				estimated ? RouteLeg.PROVIDER_STRAIGHT_LINE : RouteLeg.PROVIDER_KAKAO_MOBILITY,
				List.of(), List.of());
	}

	@Test
	@DisplayName("실제 경로를 받으면 VERIFIED 로 옮긴다")
	void realRouteBecomesVerified() {
		when(this.routeQueryService.find(any())).thenReturn(leg(false));

		TravelTime measured = this.adapter.between(35.1587, 129.1604, 35.1796, 129.0756, "WALK");

		assertThat(measured.distanceM()).isEqualTo(11132);
		assertThat(measured.durationMin()).isEqualTo(44);
		assertThat(measured.dataStatus()).isEqualTo(ItineraryItem.DataStatus.VERIFIED);
	}

	@Test
	@DisplayName("🔴 어림값을 받으면 ESTIMATED 로 옮긴다 — 이 표시가 없으면 화면이 실제 소요시간으로 그린다")
	void estimatedRouteBecomesEstimated() {
		when(this.routeQueryService.find(any())).thenReturn(leg(true));

		TravelTime measured = this.adapter.between(35.1587, 129.1604, 35.1796, 129.0756, "WALK");

		assertThat(measured.dataStatus()).isEqualTo(ItineraryItem.DataStatus.ESTIMATED);
	}

	@Test
	@DisplayName("🔴 좌표가 하나라도 없으면 묻지도 않고 UNKNOWN 이다 — 0 은 '붙어 있다' 는 다른 사실이다")
	void missingCoordinateIsUnknownAndAsksNothing() {
		TravelTime measured = this.adapter.between(35.1587, 129.1604, null, 129.0756, "WALK");

		assertThat(measured.dataStatus()).isEqualTo(ItineraryItem.DataStatus.UNKNOWN);
		assertThat(measured.distanceM()).isNull();
		assertThat(measured.durationMin()).isNull();
		verify(this.routeQueryService, never()).find(any());
	}

	@Test
	@DisplayName("🔴 여행이 고른 아홉 갈래를 경로가 아는 셋으로 옮긴다 — 이 대응표는 일정이 몰라도 된다")
	void tripTravelModesMapToRouteModes() {
		when(this.routeQueryService.find(any())).thenReturn(leg(false));
		ArgumentCaptor<RouteQuery> captor = ArgumentCaptor.forClass(RouteQuery.class);

		this.adapter.between(35.1, 129.1, 35.2, 129.2, "TAXI");
		this.adapter.between(35.1, 129.1, 35.2, 129.2, "PRIVATE_CAR");
		this.adapter.between(35.1, 129.1, 35.2, 129.2, "SUBWAY");
		this.adapter.between(35.1, 129.1, 35.2, 129.2, "BUS");
		this.adapter.between(35.1, 129.1, 35.2, 129.2, "WALK");
		// 자전거·배·기타는 맞는 계산이 없어 도보로 본다 — 그래서 답은 늘 어림값이 된다.
		this.adapter.between(35.1, 129.1, 35.2, 129.2, "BICYCLE");
		this.adapter.between(35.1, 129.1, 35.2, 129.2, null);

		verify(this.routeQueryService, org.mockito.Mockito.times(7)).find(captor.capture());
		assertThat(captor.getAllValues()).extracting(RouteQuery::mode).containsExactly(
				TravelMode.CAR, TravelMode.CAR, TravelMode.TRANSIT, TravelMode.TRANSIT,
				TravelMode.WALK, TravelMode.WALK, TravelMode.WALK);
	}

	@Test
	@DisplayName("좌표가 범위를 벗어나 있어도 일정 생성을 멈추지 않는다 — 그 구간만 못 잰 것이 된다")
	void anOutOfRangeCoordinateDoesNotStopTheItinerary() {
		TravelTime measured = this.adapter.between(935.0, 129.1604, 35.1796, 129.0756, "WALK");

		assertThat(measured.dataStatus()).isEqualTo(ItineraryItem.DataStatus.UNKNOWN);
	}
}
