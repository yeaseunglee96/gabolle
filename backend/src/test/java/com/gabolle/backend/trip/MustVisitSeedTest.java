package com.gabolle.backend.trip;

import static com.gabolle.backend.trip.support.TripCommands.withLodging;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.trip.application.PreferenceDefaultsService;
import com.gabolle.backend.trip.application.TripCreationService;
import com.gabolle.backend.trip.domain.PreferenceSnapshot;
import com.gabolle.backend.trip.domain.TravelArea;
import com.gabolle.backend.trip.domain.TripSeedPlace;
import com.gabolle.backend.trip.domain.TripSeedPlaceRepository;
import com.gabolle.backend.trip.domain.TripTravelAreaRepository;
import com.gabolle.backend.trip.infra.InMemoryTripRepository;
import com.gabolle.backend.user.support.ConsentGuards;

import static org.assertj.core.api.Assertions.assertThat;

/** 고른 장소를 {@code trip_seed_place} 에 적어 두면 추천 엔진이 그 표를 읽어 후보를 앞세운다. */
class MustVisitSeedTest {

	private static final Instant NOW = Instant.parse("2026-09-15T00:00:00Z");

	/** 저장된 씨앗을 그대로 들고 있는 가짜 저장소 — 무엇을 넘기는가만 본다. */
	private static final class RecordingSeeds implements TripSeedPlaceRepository {

		private final List<TripSeedPlace> saved = new ArrayList<>();

		@Override
		public void saveAll(List<TripSeedPlace> seeds) {
			this.saved.addAll(seeds);
		}

		@Override
		public List<TripSeedPlace> findByTripId(String tripId) {
			return this.saved.stream().filter((seed) -> seed.tripId().equals(tripId)).toList();
		}
	}

	/** 저장된 범위를 그대로 들고 있는 가짜 저장소. */
	private static final class RecordingAreas implements TripTravelAreaRepository {

		private final java.util.Map<String, List<TravelArea>> saved = new java.util.LinkedHashMap<>();

		@Override
		public void saveAll(String tripId, List<TravelArea> areas) {
			this.saved.put(tripId, List.copyOf(areas));
		}

		@Override
		public List<TravelArea> findByTripId(String tripId) {
			return this.saved.getOrDefault(tripId, List.of());
		}
	}

	private InMemoryTripRepository repository;
	private RecordingSeeds seeds;
	private RecordingAreas areas;
	private TripCreationService service;

	@BeforeEach
	void setUp() {
		this.repository = new InMemoryTripRepository();
		this.seeds = new RecordingSeeds();
		this.areas = new RecordingAreas();
		Clock clock = Clock.fixed(NOW, ZoneOffset.UTC);
		this.service = new TripCreationService(this.repository, clock,
				new PreferenceDefaultsService(this.repository, clock), ConsentGuards.granting(),
				Optional.of(this.seeds), Optional.of(this.areas));
	}

	@Test
	@DisplayName("고른 순서대로 씨앗이 남는다")
	void chosenPlacesAreStoredAsSeedsInOrder() {
		String first = UUID.randomUUID().toString();
		String second = UUID.randomUUID().toString();

		TripCreationService.Result result = this.service.create(command(List.of(first, second)), null);

		assertThat(this.seeds.findByTripId(result.trip().tripId()))
				.extracting(TripSeedPlace::placeId, TripSeedPlace::sequence)
				.containsExactly(org.assertj.core.api.Assertions.tuple(first, 1),
						org.assertj.core.api.Assertions.tuple(second, 2));
	}

	/** 표의 기본키가 (여행, 장소)라 같은 장소가 두 번 들어가면 여행 생성 자체가 실패한다. */
	@Test
	@DisplayName("같은 장소를 두 번 골라도 한 번만 적는다")
	void duplicatedChoiceIsStoredOnce() {
		String placeId = UUID.randomUUID().toString();

		TripCreationService.Result result = this.service.create(command(List.of(placeId, placeId)), null);

		assertThat(this.seeds.findByTripId(result.trip().tripId()))
				.extracting(TripSeedPlace::placeId)
				.containsExactly(placeId);
	}

	/** 같은 멱등 키로 다시 오면 기존 여행을 돌려주는 자리다. 그 여행에는 씨앗이 이미 있다. */
	@Test
	@DisplayName("같은 멱등 키로 다시 불러도 씨앗이 늘지 않는다")
	void retryWithSameIdempotencyKeyDoesNotDuplicateSeeds() {
		String placeId = UUID.randomUUID().toString();
		TripCreationService.Command command = command(List.of(placeId));

		TripCreationService.Result first = this.service.create(command, "key-1");
		TripCreationService.Result again = this.service.create(command, "key-1");

		assertThat(again.created()).isFalse();
		assertThat(this.seeds.findByTripId(first.trip().tripId())).hasSize(1);
	}

	/** 안 고르고 넘어가는 것이 보통이다 — 그때 씨앗 저장소를 부르지 않는다. */
	@Test
	@DisplayName("꼭 가고 싶은 장소가 없으면 아무것도 안 적는다")
	void noChoiceStoresNothing() {
		TripCreationService.Result result = this.service.create(command(List.of()), null);

		assertThat(this.seeds.findByTripId(result.trip().tripId())).isEmpty();
	}


	@Test
	@DisplayName("고른 여행 범위가 순서대로 남는다")
	void chosenTravelAreasAreStoredInOrder() {
		TripCreationService.Result result =
				this.service.create(command(List.of(), List.of("SONGJEONG", "HAEUNDAE")), null);

		assertThat(this.areas.findByTripId(result.trip().tripId()))
				.containsExactly(TravelArea.SONGJEONG, TravelArea.HAEUNDAE);
	}

	/** 앱이 새 지역을 먼저 내보내도 여행 생성이 막히면 안 된다 — 모르는 지역은 안 고른 것과 같게 다룬다. */
	@Test
	@DisplayName("모르는 지역 코드는 버리고 나머지는 저장한다")
	void unknownAreaCodeIsDroppedWithoutFailing() {
		TripCreationService.Result result =
				this.service.create(command(List.of(), List.of("GIJANG", "haeundae")), null);

		assertThat(this.areas.findByTripId(result.trip().tripId())).containsExactly(TravelArea.HAEUNDAE);
	}

	@Test
	@DisplayName("범위를 안 고르면 아무것도 안 적는다")
	void noAreaStoresNothing() {
		TripCreationService.Result result = this.service.create(command(List.of(), List.of()), null);

		assertThat(this.areas.findByTripId(result.trip().tripId())).isEmpty();
	}

	private TripCreationService.Command command(List<String> mustVisitPlaceIds) {
		return command(mustVisitPlaceIds, List.of());
	}

	private TripCreationService.Command command(List<String> mustVisitPlaceIds, List<String> travelAreas) {
		return withLodging(new TripCreationService.Command(
				"usr_1",
				LocalDate.of(2026, 10, 12), LocalDate.of(2026, 10, 13),
				35.1587, 129.1604,
				100000, 2,
				"MORNING_TO_EVENING", "Asia/Seoul",
				List.of(new PreferenceSnapshot.PreferenceAnswer(
						"quietness", "3", PreferenceSnapshot.AnswerStatus.SELECTED)),
				List.of(),
				com.gabolle.backend.trip.domain.Trip.OwnerType.USER,
				null, false, false, false, null,
				mustVisitPlaceIds,
				travelAreas));
	}
}
