package com.gabolle.backend.trip;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.gabolle.backend.place.api.PlaceSnapshotRequest;
import com.gabolle.backend.place.domain.Place;
import com.gabolle.backend.place.repository.PlaceRepository;
import com.gabolle.backend.place.service.UserSubmittedPlaceService;
import com.gabolle.backend.trip.application.PreferenceDefaultsService;
import com.gabolle.backend.trip.application.TripCreationService;
import com.gabolle.backend.trip.domain.PreferenceSnapshot;
import com.gabolle.backend.trip.domain.Trip;
import com.gabolle.backend.trip.infra.InMemoryTripRepository;
import com.gabolle.backend.user.support.ConsentGuards;

/**
 * 앱이 보낸 숙소가 {@code trip.accommodation_place_id} 로 남는가 — S15P21E201-1522.
 *
 * <p><b>왜 이 검사가 있나.</b> S15P21E201-1511 이 홈 화면에 숙소 칸을 넣으면서 앱이 숙소를
 * 보내기 시작했는데, 서버에 받을 칸이 없어 <b>조용히 버려지고 있었다.</b> Spring 은 모르는
 * JSON 칸을 말없이 버리므로 오류도 안 났다 — 그래서 이런 결함은 시험으로만 막힌다.
 */
class AccommodationSnapshotTest {

	private static final Instant NOW = Instant.parse("2026-09-23T00:00:00Z");

	private InMemoryTripRepository repository;

	private PlaceRepository places;

	private TripCreationService service;

	@BeforeEach
	void setUp() {
		this.repository = new InMemoryTripRepository();
		this.places = mock(PlaceRepository.class);
		Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
		when(this.places.save(any())).thenAnswer((call) -> call.getArgument(0));
		when(this.places.findBySourceTypeAndSourceId(any(), any())).thenReturn(Optional.empty());
		this.service = newService(Optional.of(new UserSubmittedPlaceService(this.places, clock)));
	}

	private TripCreationService newService(Optional<UserSubmittedPlaceService> resolver) {
		Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
		return new TripCreationService(this.repository, clock,
				new PreferenceDefaultsService(this.repository, clock), ConsentGuards.granting(),
				Optional.empty(), Optional.empty(), resolver);
	}

	private static PlaceSnapshotRequest snapshot() {
		return new PlaceSnapshotRequest("KAKAO_LOCAL", "7913306", "해운대 어느 호텔",
				"부산 해운대구 우동", 35.1585, 129.1598, "LODGING");
	}

	private TripCreationService.Command command(String accommodationPlaceId, PlaceSnapshotRequest accommodation) {
		return command(accommodationPlaceId, accommodation, LocalDate.of(2026, 9, 27));
	}

	/** 당일치기는 {@code finish} 를 가는 날(9/25)과 같게 준다 — 숙소 없이 만들어진다(S15P21E201-1585). */
	private TripCreationService.Command command(String accommodationPlaceId, PlaceSnapshotRequest accommodation,
			LocalDate finish) {
		return new TripCreationService.Command("usr_1",
				LocalDate.of(2026, 9, 25), finish,
				35.1587, 129.1604, 300000, 2, "MORNING_TO_EVENING", "Asia/Seoul",
				List.of(), List.of(), Trip.OwnerType.USER,
				accommodationPlaceId, false, false, false, null,
				List.of(), List.of(), accommodation, null);
	}

	private static final LocalDate DAY_TRIP = LocalDate.of(2026, 9, 25);

	private Trip create(TripCreationService.Command command, String key) {
		return this.repository.findById(this.service.create(command, key).trip().tripId()).orElseThrow();
	}

	@Test
	@DisplayName("🔴 스냅샷만 와도 숙소가 남는다 — 지금까지는 통째로 버려졌다")
	void aSnapshotBecomesTheAccommodation() {
		Trip saved = create(command(null, snapshot()), "key_1");

		assertThat(saved.accommodationPlaceId()).as("버려지면 여기가 null 이다").isNotNull();

		// 만들어진 장소와 여행이 가리키는 id 가 같은지 본다 — 둘이 어긋나면 외래키가 깨진다.
		ArgumentCaptor<Place> created = ArgumentCaptor.forClass(Place.class);
		verify(this.places).save(created.capture());
		assertThat(created.getValue().getSourceType()).isEqualTo("KAKAO_LOCAL");
		assertThat(created.getValue().getSourceId()).isEqualTo("7913306");
		assertThat(saved.accommodationPlaceId()).isEqualTo(created.getValue().getPlaceId().toString());
	}

	@Test
	@DisplayName("🔴 이미 있는 장소면 그 행을 쓴다 — 큐레이션된 숙소를 두고 새로 만들지 않는다")
	void anExistingPlaceIsReused() {
		UUID existing = UUID.randomUUID();
		Place curated = Place.imported(existing, "이미 있는 호텔", "LODGING", "부산", 35.1, 129.1,
				"KAKAO_LOCAL", "7913306", null, null, "loader-v1");
		when(this.places.findBySourceTypeAndSourceId("KAKAO_LOCAL", "7913306"))
				.thenReturn(Optional.of(curated));

		assertThat(create(command(null, snapshot()), "key_2").accommodationPlaceId())
				.isEqualTo(existing.toString());
	}

	@Test
	@DisplayName("placeId 를 직접 보내면 스냅샷은 «안 본다» — 둘이 어긋날 때 서버가 정하지 않는다")
	void anExplicitPlaceIdWins() {
		String chosen = UUID.randomUUID().toString();

		assertThat(create(command(chosen, snapshot()), "key_3").accommodationPlaceId()).isEqualTo(chosen);
	}

	@Test
	@DisplayName("둘 다 없으면 숙소 없는 여행이다 — 당일치기는 숙소가 필요 없어 오류가 아니다")
	void noAccommodationIsFine() {
		// 1박 이상이면 숙소가 있어야 한다(S15P21E201-1585) — 그 거부는 TripConditionRulesTest 가 잰다.
		assertThat(create(command(null, null, DAY_TRIP), "key_4").accommodationPlaceId()).isNull();
	}

	@Test
	@DisplayName("🔴 스냅샷이 잘못돼도 «여행 생성이 500 으로 죽지 않는다» — 400 이 되는 예외다")
	void anInvalidSnapshotIsARequestError() {
		PlaceSnapshotRequest nameless = new PlaceSnapshotRequest("KAKAO_LOCAL", "999", "  ",
				null, null, null, null);

		assertThatThrownBy(() -> this.service.create(command(null, nameless), "key_5"))
				.as("TripExceptionHandler 가 IllegalArgumentException 을 400 으로 바꾼다")
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("🔴 장소 해석기 빈이 없어도 여행은 만들어진다 — 숙소 하나 때문에 전체가 실패하면 안 된다 (당일치기)")
	void aMissingResolverDoesNotFailTheTrip() {
		TripCreationService withoutResolver = newService(Optional.empty());

		// 해석기가 없으면 스냅샷이 장소가 못 되어 숙소가 없는 것이 된다. 1박 이상이면 그래서 거부되고
		// (S15P21E201-1585 — 스냅샷을 장소로 바꾼 뒤에 판정한다), 당일치기는 숙소가 필요 없어 그대로 만들어진다.
		// 운영에는 해석기가 늘 있다.
		var result = withoutResolver.create(command(null, snapshot(), DAY_TRIP), "key_6");

		Trip saved = this.repository.findById(result.trip().tripId()).orElseThrow();
		assertThat(saved.accommodationPlaceId()).isNull();
	}
}
