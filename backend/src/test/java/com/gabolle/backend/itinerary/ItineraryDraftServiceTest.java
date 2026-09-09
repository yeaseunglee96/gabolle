package com.gabolle.backend.itinerary;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.ObjectProvider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.itinerary.application.ItineraryDraftService;
import com.gabolle.backend.itinerary.application.ItineraryLegPlanner;
import com.gabolle.backend.itinerary.application.port.TravelTime;
import com.gabolle.backend.itinerary.domain.ItineraryItem;
import com.gabolle.backend.itinerary.application.port.TravelTimePort;
import com.gabolle.backend.itinerary.domain.ItineraryRepository;
import com.gabolle.backend.place.repository.PlaceRepository;
import com.gabolle.backend.recommendation.application.port.ItineraryDraft;
import com.gabolle.backend.recommendation.application.port.ItineraryDraftCommand;
import com.gabolle.backend.trip.domain.Trip;
import com.gabolle.backend.trip.domain.TripRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 추천 결과를 날짜·구간으로 조립하는 규칙 — S15P21E201-604.
 *
 * <p>🔴 Spring 컨텍스트를 띄우지 않는다({@code ItineraryVersionConflictTest} 와 같은 판단) —
 * 도메인 계산 규칙을 보는 것이고, {@link TripRepository}·{@link PlaceRepository} 는
 * Mockito 로 대신한다.
 */
class ItineraryDraftServiceTest {

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-05T00:00:00Z"), ZoneOffset.UTC);

	private TripRepository tripRepository;

	private PlaceRepository placeRepository;

	private ItineraryDraftService service;

	@BeforeEach
	void setUp() {
		this.tripRepository = mock(TripRepository.class);
		this.placeRepository = mock(PlaceRepository.class);
		ItineraryRepository itineraryRepository = mock(ItineraryRepository.class);
		// 🔴 이동시간 포트를 "없는" 상태로 준다(S15P21E201-179). 이 검사들이 재는 것은
		//    날짜 배분과 시각 배정이지 바깥 길찾기가 아니고, 포트가 없으면 예전처럼
		//    직선거리만 채우는 갈래로 떨어진다 — 그 갈래도 살아 있어야 한다.
		@SuppressWarnings("unchecked")
		ObjectProvider<TravelTimePort> noTravelTime = mock(ObjectProvider.class);
		when(noTravelTime.getIfAvailable()).thenReturn(null);

		ItineraryLegPlanner legPlanner = new ItineraryLegPlanner(this.placeRepository, noTravelTime);
		this.service = new ItineraryDraftService(this.tripRepository, itineraryRepository, CLOCK, 4, legPlanner);

		// 좌표를 모르는 장소만 다루는 테스트들이 기본으로 쓴다 — 거리는 항상 null 이 된다.
		when(this.placeRepository.findByPlaceIdIn(anyCollection())).thenReturn(List.of());
	}

	@Test
	@DisplayName("순위대로 날짜에 배분한다 — 하루 4개를 채우면 다음 날로, 마지막 날은 넘치는 만큼 그대로 쌓인다")
	void distributesPlacesAcrossDaysByRankAndCarriesOverflowForward() {
		// 3박4일 → days() = nights(2) + 1 = 3
		Trip trip = tripOf(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 12));
		when(this.tripRepository.findById("trip_1")).thenReturn(Optional.of(trip));

		List<ItineraryDraftCommand.PlannedPlace> places = plannedPlaces(9);
		ItineraryDraft draft = this.service.assemble(commandOf("trip_1", places));

		assertThat(draft.items()).hasSize(9);
		Map<Integer, Long> countByDay = draft.items().stream()
				.collect(Collectors.groupingBy(ItineraryDraft.DraftItem::dayIndex, Collectors.counting()));
		assertThat(countByDay).containsEntry(0, 4L).containsEntry(1, 4L).containsEntry(2, 1L);

		// 각 날 안에서는 sequence 가 1부터 다시 매겨진다.
		List<Integer> day0Sequences = draft.items().stream()
				.filter((item) -> item.dayIndex() == 0)
				.map(ItineraryDraft.DraftItem::sequence)
				.sorted()
				.toList();
		assertThat(day0Sequences).containsExactly(1, 2, 3, 4);
	}

	@Test
	@DisplayName("🔴 시각 정보가 없으면 startTime·endTime 은 NULL 이고 dataStatus 는 UNKNOWN 이다 — 지어내지 않는다")
	void timesAreNullAndStatusUnknownWhenNoActualTimeWindowExists() {
		Trip trip = tripOf(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 10));
		when(this.tripRepository.findById("trip_1")).thenReturn(Optional.of(trip));

		ItineraryDraft draft = this.service.assemble(commandOf("trip_1", plannedPlaces(2)));

		assertThat(draft.items()).allSatisfy((item) -> {
			assertThat(item.startTime()).isNull();
			assertThat(item.endTime()).isNull();
			assertThat(item.stayMinutes()).isNull();
			assertThat(item.dataStatus()).isEqualTo("UNKNOWN");
		});
	}

	@Test
	@DisplayName("🔴 S15P21E201-179 — 구간에 실제 이동시간과 그 값의 출처가 실린다")
	void legsCarryMeasuredTravelTimeAndItsDataStatus() {
		Trip trip = tripOf(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 10));
		when(this.tripRepository.findById("trip_1")).thenReturn(Optional.of(trip));

		// 이동시간을 아는 포트를 끼운다. 좌표가 없어도 포트가 답을 주면 그 값이 그대로
		// 구간에 실려야 한다 — 이 검사가 보는 것은 배선이지 거리 계산이 아니다.
		TravelTimePort port = (fromLat, fromLng, toLat, toLng, mode) ->
				new TravelTime(1234, 25, ItineraryItem.DataStatus.VERIFIED);
		@SuppressWarnings("unchecked")
		ObjectProvider<TravelTimePort> provider = mock(ObjectProvider.class);
		when(provider.getIfAvailable()).thenReturn(port);
		ItineraryLegPlanner legPlanner = new ItineraryLegPlanner(this.placeRepository, provider);
		ItineraryDraftService withTravelTime = new ItineraryDraftService(this.tripRepository,
				mock(ItineraryRepository.class), CLOCK, 4, legPlanner);

		ItineraryDraft draft = withTravelTime.assemble(commandOf("trip_1", plannedPlaces(3)));

		assertThat(draft.legs()).isNotEmpty();
		assertThat(draft.legs()).allSatisfy((leg) -> {
			assertThat(leg.durationMin()).as("이동시간이 비어 있으면 화면이 그 사이를 말할 수 없다").isEqualTo(25);
			assertThat(leg.distanceM()).isEqualTo(1234);
			assertThat(leg.dataStatus()).isEqualTo(ItineraryItem.DataStatus.VERIFIED);
		});
	}

	@Test
	@DisplayName("🔴 이동시간을 물어볼 곳이 없으면 UNKNOWN 으로 남는다 — 0 분으로 지어내지 않는다")
	void withoutATravelTimePortTheLegStaysUnknown() {
		Trip trip = tripOf(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 10));
		when(this.tripRepository.findById("trip_1")).thenReturn(Optional.of(trip));

		ItineraryDraft draft = this.service.assemble(commandOf("trip_1", plannedPlaces(3)));

		assertThat(draft.legs()).allSatisfy((leg) -> {
			assertThat(leg.durationMin()).isNull();
			assertThat(leg.dataStatus()).isEqualTo(ItineraryItem.DataStatus.UNKNOWN);
		});
	}

	@Test
	@DisplayName("각 날의 첫 구간은 fromPlaceId 가 NULL 이다 — 여행 출발지에서 출발한다")
	void firstLegOfEachDayStartsFromTheTripOrigin() {
		Trip trip = tripOf(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 11));
		when(this.tripRepository.findById("trip_1")).thenReturn(Optional.of(trip));

		// 하루 최대 4개 · 3개 후보 → 전부 첫날에 들어가 구간이 2개(1→2, 2→3) 생긴다.
		ItineraryDraft draft = this.service.assemble(commandOf("trip_1", plannedPlaces(3)));

		assertThat(draft.legs()).isNotEmpty();
		assertThat(draft.legs()).filteredOn((leg) -> leg.sequence() == 1)
				.allSatisfy((leg) -> assertThat(leg.fromPlaceId()).isNull());
		assertThat(draft.legs()).filteredOn((leg) -> leg.sequence() > 1)
				.allSatisfy((leg) -> assertThat(leg.fromPlaceId()).isNotNull());
	}

	@Test
	@DisplayName("🔴 대중교통 구간은 walkingMeters 가 NULL 이다 — 직선거리를 지하철 이동거리인 척하지 않는다")
	void walkingMetersIsOnlyFilledForWalkMode() {
		// 🔴 지금 buildLegs()는 Trip 이 이동수단을 아직 노출하지 않아 항상 WALK 만 만든다 —
		//    그래서 이 규칙 자체는 ItineraryLegPlanner.walkingMetersFor(...) 를 직접
		//    불러 확인한다. Trip 이 실제 이동수단을 노출하게 되면 buildLegs() 를 통해서도
		//    이 갈래가 나오게 된다.
		assertThat(ItineraryLegPlanner.walkingMetersFor("WALK", 350)).isEqualTo(350);
		assertThat(ItineraryLegPlanner.walkingMetersFor("SUBWAY", 350)).isNull();
		assertThat(ItineraryLegPlanner.walkingMetersFor("BUS", 350)).isNull();
	}

	@Test
	@DisplayName("장소가 하나도 없으면 조립할 수 없다 — RecommendationService 가 먼저 걸러야 하는 상태다")
	void emptyPlacesIsRejected() {
		assertThatThrownBy(() -> this.service.assemble(commandOf("trip_1", List.of())))
				.isInstanceOf(IllegalStateException.class);
	}

	private Trip tripOf(LocalDate startDate, LocalDate finishDate) {
		return new Trip("itn_trip_1", "usr_1", startDate, finishDate, null, null, null, 2, null, "Asia/Seoul",
				Instant.now());
	}

	private List<ItineraryDraftCommand.PlannedPlace> plannedPlaces(int count) {
		List<ItineraryDraftCommand.PlannedPlace> places = new ArrayList<>();
		for (int i = 1; i <= count; i++) {
			places.add(new ItineraryDraftCommand.PlannedPlace(UUID.randomUUID(), i, List.of("REASON"), List.of()));
		}
		return places;
	}

	private ItineraryDraftCommand commandOf(String tripId, List<ItineraryDraftCommand.PlannedPlace> places) {
		return new ItineraryDraftCommand(UUID.randomUUID(), tripId, "usr_1", places,
				"model-1", "feature-1", "ontology-1", "policy-1", "dataset-1");
	}
}
