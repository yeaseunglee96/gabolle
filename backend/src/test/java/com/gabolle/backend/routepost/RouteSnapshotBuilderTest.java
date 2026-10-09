package com.gabolle.backend.routepost;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.itinerary.presentation.dto.ItineraryDetailResponse;

/** 일정 → 루트 사본(-2027). 숙소가 정류장으로도, 선의 끝점으로도 새지 않는지가 핵심이다. */
class RouteSnapshotBuilderTest {

	private static final UUID HOTEL = UUID.randomUUID();

	private static final double[] HOTEL_AT = { 35.1000, 129.1000 };

	private static ItineraryDetailResponse.Item item(UUID place, String name, double lat, double lng,
			List<double[]> pathIn, Integer travelMin, String start, String end) {
		return new ItineraryDetailResponse.Item(UUID.randomUUID().toString(), start, end, name, null, null, null,
				false, "OK", null, null, place.toString(), travelMin, null, null, pathIn, null, null, null, null, lat,
				lng, null, null);
	}

	private static ItineraryDetailResponse detail(List<ItineraryDetailResponse.Day> days) {
		return new ItineraryDetailResponse("it-1", "부산", 1, days, null, null, null, "OWNER", true, List.of(),
				UUID.randomUUID().toString(), 0, 1, null);
	}

	private static List<ItineraryDetailResponse.Day> twoDaysWithHotel() {
		UUID a = UUID.randomUUID();
		UUID b = UUID.randomUUID();
		UUID c = UUID.randomUUID();
		List<ItineraryDetailResponse.Item> d1 = new ArrayList<>();
		// 숙소에서 나오는 선 — 버려야 한다.
		d1.add(item(a, "해운대", 35.16, 129.16, List.of(HOTEL_AT, new double[] { 35.16, 129.16 }), 20,
				"2026-10-10T10:00:00+09:00", "2026-10-10T11:00:00+09:00"));
		d1.add(item(b, "동백섬", 35.15, 129.15, List.of(new double[] { 35.16, 129.16 }, new double[] { 35.15, 129.15 }), 10,
				"2026-10-10T11:10:00+09:00", "2026-10-10T12:00:00+09:00"));
		// 숙소로 들어가는 선이 붙은 숙소 정류장 — 통째로 버려야 한다.
		d1.add(item(HOTEL, "그랜드호텔", HOTEL_AT[0], HOTEL_AT[1],
				List.of(new double[] { 35.15, 129.15 }, HOTEL_AT), 15, null, null));
		List<ItineraryDetailResponse.Item> d2 = new ArrayList<>();
		d2.add(item(c, "감천문화마을", 35.09, 129.01, List.of(HOTEL_AT, new double[] { 35.09, 129.01 }), 30,
				"2026-10-11T10:00:00+09:00", "2026-10-11T11:30:00+09:00"));
		d2.add(item(a, "해운대", 35.16, 129.16, List.of(new double[] { 35.09, 129.01 }, new double[] { 35.16, 129.16 }),
				40, null, null));
		return List.of(new ItineraryDetailResponse.Day("2026-10-10", d1), new ItineraryDetailResponse.Day("2026-10-11", d2));
	}

	@Test
	@DisplayName("🔴 숙소는 정류장에도 선에도 없다 — 선 끝점이 곧 숙소 위치다")
	void lodgingNeverLeaks() {
		RouteSnapshot snap = RouteSnapshotBuilder.build(detail(twoDaysWithHotel()), Set.of(HOTEL), null);

		assertThat(snap.stops()).extracting(RouteSnapshot.Stop::name)
				.containsExactly("해운대", "동백섬", "감천문화마을", "해운대");
		assertThat(snap.path()).noneMatch(p -> p[0] == HOTEL_AT[0] && p[1] == HOTEL_AT[1]);
		assertThat(snap.dayCount()).isEqualTo(2);
	}

	@Test
	@DisplayName("하루만 고르면 그날 정류장만, 시간은 이동+머문 시간의 합이다")
	void singleDay() {
		RouteSnapshot snap = RouteSnapshotBuilder.build(detail(twoDaysWithHotel()), Set.of(HOTEL), 0);

		assertThat(snap.stops()).extracting(RouteSnapshot.Stop::name).containsExactly("해운대", "동백섬");
		assertThat(snap.dayIndex()).isZero();
		// 머문 60 + 50, 이동은 숙소에서 온 20 을 빼고 10
		assertThat(snap.durationMin()).isEqualTo(120);
		assertThat(snap.distanceM()).isPositive();
	}

	@Test
	@DisplayName("숙소를 빼고 두 곳이 안 되면 올릴 수 없다")
	void tooFewStops() {
		UUID only = UUID.randomUUID();
		var day = new ItineraryDetailResponse.Day("2026-10-10",
				List.of(item(only, "한 곳", 35.1, 129.1, null, null, null, null),
						item(HOTEL, "호텔", HOTEL_AT[0], HOTEL_AT[1], null, null, null, null)));

		assertThatThrownBy(() -> RouteSnapshotBuilder.build(detail(List.of(day)), Set.of(HOTEL), null))
				.isInstanceOf(RoutePostExceptions.InvalidRouteException.class).hasMessage("TOO_FEW_STOPS");
	}

	@Test
	@DisplayName("없는 날을 고르면 거절한다")
	void missingDay() {
		assertThatThrownBy(() -> RouteSnapshotBuilder.build(detail(twoDaysWithHotel()), Set.of(HOTEL), 5))
				.hasMessage("DAY_NOT_FOUND");
	}

	@Test
	@DisplayName("긴 선은 썸네일에 충분한 만큼만 남긴다")
	void thinsLongPath() {
		List<double[]> many = new ArrayList<>();
		for (int i = 0; i < 5000; i++) {
			many.add(new double[] { 35 + i * 1e-5, 129 });
		}
		assertThat(RouteSnapshotBuilder.thin(many)).hasSize(RouteSnapshotBuilder.MAX_PATH_POINTS);
	}
}
