package com.gabolle.backend.itinerary.application;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import com.gabolle.backend.itinerary.application.port.TravelTime;
import com.gabolle.backend.itinerary.application.port.TravelTimePort;
import com.gabolle.backend.itinerary.domain.ItineraryItem;
import com.gabolle.backend.place.domain.Place;
import com.gabolle.backend.place.repository.PlaceRepository;
import com.gabolle.backend.place.service.GeoDistance;
import com.gabolle.backend.recommendation.application.port.ItineraryDraft;
import com.gabolle.backend.trip.domain.Trip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 숙소가 있으면 둘째 날부터 숙소에서 나선다 (2026-09-23, S15P21E201-1547).
 *
 * <p>전에는 매일 여행 출발지(역·집)에서 시작했고, 숙소는 저장만 되고 일정이 한 줄도 안 바뀌었다.
 * 경로 계층은 없는 채로 돈다 — 그러면 구간 거리는 출발점에서 잰 직선거리라, 어디서 출발했는지가
 * 거리로 그대로 드러난다.
 */
class ItineraryLegPlannerLodgingTest {

	private static final double ORIGIN_LAT = 35.1152;   // 부산역
	private static final double ORIGIN_LNG = 129.0422;
	private static final double LODGING_LAT = 35.1587;  // 해운대
	private static final double LODGING_LNG = 129.1604;

	private final PlaceRepository places = mock(PlaceRepository.class);

	@SuppressWarnings("unchecked")
	private final ItineraryLegPlanner planner = new ItineraryLegPlanner(this.places,
			(ObjectProvider<TravelTimePort>) mock(ObjectProvider.class));

	private final UUID lodgingId = UUID.randomUUID();

	private final Place stop = place(UUID.randomUUID(), 35.1631, 129.1638);

	@Test
	@DisplayName("🔴 둘째 날 첫 구간은 숙소에서 잰다 — 첫날은 출발지 그대로")
	void 둘째날부터_숙소에서_출발한다() {
		when(this.places.findById(this.lodgingId)).thenReturn(Optional.of(place(this.lodgingId, LODGING_LAT, LODGING_LNG)));
		when(this.places.findByPlaceIdIn(any())).thenReturn(List.of(this.stop));

		List<ItineraryDraft.DraftLeg> legs = this.planner.buildLegs(trip(this.lodgingId.toString()),
				List.of(List.of(this.stop.getPlaceId()), List.of(this.stop.getPlaceId())));

		assertThat(legs.get(0).distanceM()).isEqualTo(meters(ORIGIN_LAT, ORIGIN_LNG));
		assertThat(legs.get(1).distanceM()).isEqualTo(meters(LODGING_LAT, LODGING_LNG));
	}

	@Test
	@DisplayName("숙소가 없으면 매일 출발지 — 모르는 자리를 지어내지 않는다")
	void 숙소가_없으면_매일_출발지() {
		when(this.places.findByPlaceIdIn(any())).thenReturn(List.of(this.stop));

		List<ItineraryDraft.DraftLeg> legs = this.planner.buildLegs(trip(null),
				List.of(List.of(this.stop.getPlaceId()), List.of(this.stop.getPlaceId())));

		assertThat(legs.get(1).distanceM()).isEqualTo(meters(ORIGIN_LAT, ORIGIN_LNG));
	}

	@Test
	@DisplayName("숙소를 우리 표에서 못 찾거나 번호가 이상하면 출발지 — 실패로 일정을 막지 않는다")
	void 숙소를_못찾으면_출발지() {
		when(this.places.findById(this.lodgingId)).thenReturn(Optional.empty());

		assertThat(this.planner.dayStart(trip(this.lodgingId.toString()), 1, this.planner.lodgingOf(trip(this.lodgingId.toString()))))
				.containsExactly(ORIGIN_LAT, ORIGIN_LNG);
		assertThat(this.planner.lodgingOf(trip("not-a-uuid"))).isNull();
	}

	@Test
	@DisplayName("🔴 동네를 숙소로 골라도 숙소다 — 둘째 날은 그 동네 중심에서 나선다 (S15P21E201-1565)")
	void 동네_숙소도_둘째날_출발점이다() {
		Trip trip = tripWithArea(null, "HAEUNDAE");

		ItineraryLegPlanner.Anchor lodging = this.planner.lodgingOf(trip);

		assertThat(lodging).isNotNull();
		assertThat(lodging.label()).isEqualTo("해운대");
		assertThat(this.planner.dayStart(trip, 1, lodging)).containsExactly(LODGING_LAT, LODGING_LNG);
	}

	@Test
	@DisplayName("우리 표의 숙소가 있으면 그것이 먼저다 — 동네보다 정확하다")
	void 표의_숙소가_동네보다_먼저() {
		when(this.places.findById(this.lodgingId)).thenReturn(Optional.of(place(this.lodgingId, 35.1601, 129.1631)));

		ItineraryLegPlanner.Anchor lodging = this.planner.lodgingOf(tripWithArea(this.lodgingId.toString(), "SEOMYEON"));

		assertThat(lodging.lat()).isEqualTo(35.1601);
		assertThat(lodging.label()).isEqualTo("장소");
	}

	@Test
	@DisplayName("🔴 하루 끝은 숙소, 마지막 날은 출발지로 돌아간다 — 1박 2일 (S15P21E201-1565)")
	void 하루_끝은_숙소_마지막날은_출발지() {
		Trip trip = tripWithArea(null, "HAEUNDAE");
		ItineraryLegPlanner.Anchor lodging = this.planner.lodgingOf(trip);

		ItineraryLegPlanner.Anchor night = this.planner.dayEnd(trip, 0, lodging);
		ItineraryLegPlanner.Anchor last = this.planner.dayEnd(trip, 1, lodging);

		assertThat(night.kind()).isEqualTo(ItineraryLegPlanner.Anchor.LODGING);
		assertThat(night.lat()).isEqualTo(LODGING_LAT);
		assertThat(last.kind()).isEqualTo(ItineraryLegPlanner.Anchor.ORIGIN);
		assertThat(last.lat()).isEqualTo(ORIGIN_LAT);
	}

	@Test
	@DisplayName("숙소를 모르는 밤에는 돌아갈 자리가 없다 — 지어내지 않는다")
	void 숙소를_모르면_밤에_돌아갈_자리가_없다() {
		assertThat(this.planner.dayEnd(trip(null), 0, null)).isNull();
		assertThat(this.planner.dayEnd(trip(null), 1, null).kind()).as("마지막 날의 출발지는 안다")
				.isEqualTo(ItineraryLegPlanner.Anchor.ORIGIN);
	}

	@Test
	@DisplayName("돌아가는 이동은 그날 마지막 방문지에서 숙소까지 잰다")
	void 돌아가는_이동은_마지막_방문지에서_잰다() {
		double[] asked = new double[4];
		TravelTimePort port = (fromLat, fromLng, toLat, toLng, mode) -> {
			asked[0] = fromLat; asked[1] = fromLng; asked[2] = toLat; asked[3] = toLng;
			return new TravelTime(2100, 18, ItineraryItem.DataStatus.ESTIMATED);
		};
		@SuppressWarnings("unchecked")
		ObjectProvider<TravelTimePort> provider = mock(ObjectProvider.class);
		when(provider.getIfAvailable()).thenReturn(port);
		ItineraryLegPlanner measuring = new ItineraryLegPlanner(this.places, provider);
		when(this.places.findById(this.stop.getPlaceId())).thenReturn(Optional.of(this.stop));
		Trip trip = tripWithArea(null, "HAEUNDAE");

		ItineraryLegPlanner.DayReturn back = measuring.returnFor(trip, 0, measuring.lodgingOf(trip), this.stop.getPlaceId());

		assertThat(back.travel().durationMin()).isEqualTo(18);
		assertThat(asked).containsExactly(this.stop.getLat(), this.stop.getLng(), LODGING_LAT, LODGING_LNG);
	}

	private int meters(double fromLat, double fromLng) {
		return (int) Math.round(GeoDistance.meters(fromLat, fromLng, this.stop.getLat(), this.stop.getLng()));
	}

	private static Place place(UUID id, double lat, double lng) {
		return Place.imported(id, "장소", "FOOD", null, lat, lng, "FIXTURE", id.toString(), null, null, "v1");
	}

	private static Trip tripWithArea(String accommodationPlaceId, String accommodationArea) {
		return new Trip("trip_1", "usr_1", Trip.OwnerType.USER, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 2),
				ORIGIN_LAT, ORIGIN_LNG, 300_000, 2, "09:00-18:00", "Asia/Seoul",
				new String[] { "WALK" }, null, null,
				accommodationPlaceId, false, false, false, null, null, Instant.now(), accommodationArea);
	}

	private static Trip trip(String accommodationPlaceId) {
		return new Trip("trip_1", "usr_1", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 2),
				ORIGIN_LAT, ORIGIN_LNG, 300_000, 2, "09:00-18:00", "Asia/Seoul",
				new String[] { "WALK" }, null, null,
				accommodationPlaceId, false, false, false, null, Instant.now());
	}
}
