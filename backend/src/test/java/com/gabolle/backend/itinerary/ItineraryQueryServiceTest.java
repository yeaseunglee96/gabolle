package com.gabolle.backend.itinerary;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.itinerary.application.ItineraryQueryService;
import com.gabolle.backend.itinerary.domain.Itinerary;
import com.gabolle.backend.itinerary.domain.ItineraryItem;
import com.gabolle.backend.itinerary.domain.ItineraryVersion;
import com.gabolle.backend.itinerary.infra.InMemoryItineraryRepository;
import com.gabolle.backend.itinerary.presentation.ItineraryQueryController;
import com.gabolle.backend.itinerary.presentation.dto.ItineraryDetailResponse;
import com.gabolle.backend.place.domain.Place;
import com.gabolle.backend.place.repository.PlaceRepository;
import com.gabolle.backend.recommendation.repository.RecommendationJobRepository;
import com.gabolle.backend.trip.application.TripQueryService;
import com.gabolle.backend.trip.domain.Trip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * S15P21E201-604 — 완성된 일정표 조회. 항목이 0개인 날을 감추지 않는가, 도보 거리가 없는
 * 것을 0으로 지어내지 않는가, {@code item_key} 를 PK 대신 쓰는가를 본다.
 *
 * <p>🔴 Spring 컨텍스트를 띄우지 않는다 — {@link ItineraryDraftServiceTest} 와 같은 판단.
 * {@link ItineraryRepository} 는 {@link InMemoryItineraryRepository} 를 실제로 쓰고,
 * {@link TripQueryService}·{@link PlaceRepository}·{@link RecommendationJobRepository} 만
 * Mockito 로 대신한다.
 */
class ItineraryQueryServiceTest {

	private InMemoryItineraryRepository itineraryRepository;
	private TripQueryService tripQueryService;
	private PlaceRepository placeRepository;
	private RecommendationJobRepository recommendationJobRepository;
	private ItineraryQueryService service;

	private final String tripId = "trip_1";
	private final String requesterId = "usr_a";
	private final UUID placeId = UUID.randomUUID();

	@BeforeEach
	void setUp() {
		this.itineraryRepository = new InMemoryItineraryRepository();
		this.tripQueryService = mock(TripQueryService.class);
		this.placeRepository = mock(PlaceRepository.class);
		this.recommendationJobRepository = mock(RecommendationJobRepository.class);
		this.service = new ItineraryQueryService(this.itineraryRepository, this.tripQueryService,
				this.placeRepository, this.recommendationJobRepository);

		Place place = mock(Place.class);
		when(place.getPlaceId()).thenReturn(this.placeId);
		when(place.getNameKo()).thenReturn("해운대 해수욕장");
		when(this.placeRepository.findByPlaceIdIn(any())).thenReturn(List.of(place));
	}

	private Trip threeDayTrip() {
		// 3박4일이 아니라 2026-09-10~09-12, 즉 days() = nights(2) + 1 = 3.
		return new Trip(this.tripId, this.requesterId, LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 12),
				null, null, null, 1, null, "Asia/Seoul", Instant.now());
	}

	private void stubTripMembership(Trip trip) {
		when(this.tripQueryService.get(this.tripId, this.requesterId))
				.thenReturn(new TripQueryService.View(trip, List.of(), null));
	}

	@Test
	@DisplayName("🔴 항목이 0개인 날도 빈 items 배열로 포함된다")
	void includesEmptyDays() {
		stubTripMembership(threeDayTrip());
		String itineraryId = seedItinerary(1,
				List.of(itemOf("item_key_1", 0, LocalDate.of(2026, 9, 10), 1, null, null)));

		ItineraryDetailResponse response = this.service.getDetail(itineraryId, this.requesterId);

		assertThat(response.days()).hasSize(3);
		assertThat(response.days().get(0).items()).hasSize(1);
		assertThat(response.days().get(1).items()).isEmpty();
		assertThat(response.days().get(2).items()).isEmpty();
	}

	@Test
	@DisplayName("items[].id 는 item_key 다 — PK 가 아니다")
	void itemIdIsItemKey() {
		stubTripMembership(threeDayTrip());
		String itemKey = "itemkey_" + UUID.randomUUID();
		String itineraryId = seedItinerary(1,
				List.of(itemOf(itemKey, 0, LocalDate.of(2026, 9, 10), 1, null, null)));

		ItineraryDetailResponse response = this.service.getDetail(itineraryId, this.requesterId);

		assertThat(response.days().get(0).items().get(0).id()).isEqualTo(itemKey);
	}

	@Test
	@DisplayName("🔴 도보 구간 데이터가 하나도 없으면 totalWalkingMeters 는 0 이 아니라 null")
	void totalWalkingMetersNullWhenNoData() {
		stubTripMembership(threeDayTrip());
		String itineraryId = seedItinerary(1,
				List.of(itemOf("item_key_1", 0, LocalDate.of(2026, 9, 10), 1, null, null)));

		ItineraryDetailResponse response = this.service.getDetail(itineraryId, this.requesterId);

		assertThat(response.totalWalkingMeters()).isNull();
		assertThat(response.totalEstimatedCostKrw()).isNull();
	}

	@Test
	@DisplayName("🔴 최신 판 포인터가 가리키는 내용이 없으면 조용히 대체하지 않고 시끄럽게 실패한다")
	void missingLatestVersionContentFailsLoudly() {
		stubTripMembership(threeDayTrip());
		// latestVersion 을 2 로 심어 두되 실제 내용은 1번 판만 저장한다 — 포인터와 실제가 어긋난 상태.
		Itinerary itinerary = new Itinerary("itn_broken", this.tripId, 2);
		ItineraryVersion v1 = new ItineraryVersion(UUID.randomUUID().toString(), "itn_broken", 1, null,
				ItineraryVersion.Operation.CREATE, this.requesterId, "req_1", null, Instant.now());
		this.itineraryRepository.create(itinerary, v1, List.of(), List.of());

		assertThatThrownBy(() -> this.service.getDetail("itn_broken", this.requesterId))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	@DisplayName("여행 회원이 아니면 ItineraryNotFoundException(404) — 있다는 사실도 알려주지 않는다")
	void nonMemberGetsNotFound() {
		when(this.tripQueryService.get(this.tripId, this.requesterId))
				.thenThrow(new TripQueryService.TripNotFoundException(this.tripId));
		String itineraryId = seedItinerary(1, List.of());

		assertThatThrownBy(() -> this.service.getDetail(itineraryId, this.requesterId))
				.isInstanceOf(ItineraryQueryController.ItineraryNotFoundException.class);
	}

	private String seedItinerary(int version, List<ItineraryItem> items) {
		String itineraryId = "itn_" + UUID.randomUUID();
		Itinerary itinerary = new Itinerary(itineraryId, this.tripId, version);
		ItineraryVersion v = new ItineraryVersion(UUID.randomUUID().toString(), itineraryId, version, null,
				ItineraryVersion.Operation.CREATE, this.requesterId, "req_1", null, Instant.now());
		this.itineraryRepository.create(itinerary, v, items, List.of());
		return itineraryId;
	}

	private ItineraryItem itemOf(String itemKey, int dayIndex, LocalDate visitDate, int sequence,
			LocalTime startTime, LocalTime endTime) {
		return new ItineraryItem(UUID.randomUUID().toString(), "version-placeholder", itemKey, dayIndex, visitDate,
				sequence, this.placeId.toString(), startTime, endTime, null, false, null,
				ItineraryItem.DataStatus.VERIFIED, List.of(), List.of(), null, Instant.now());
	}
}
