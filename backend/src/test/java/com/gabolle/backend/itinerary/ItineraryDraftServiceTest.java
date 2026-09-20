package com.gabolle.backend.itinerary;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.beans.factory.ObjectProvider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.gabolle.backend.itinerary.application.ItineraryDraftService;
import com.gabolle.backend.place.service.OpeningHoursFilterPort;
import com.gabolle.backend.place.service.PlaceTimeFactFilterPort;
import com.gabolle.backend.itinerary.application.ItineraryLegPlanner;
import com.gabolle.backend.itinerary.application.port.TravelTime;
import com.gabolle.backend.itinerary.domain.ItineraryItem;
import com.gabolle.backend.itinerary.application.port.TravelTimePort;
import com.gabolle.backend.itinerary.domain.ItineraryRepository;
import com.gabolle.backend.itinerary.domain.ItineraryWarningCodes;
import com.gabolle.backend.place.repository.PlaceRepository;
import com.gabolle.backend.recommendation.application.port.ItineraryDraft;
import com.gabolle.backend.recommendation.application.port.ItineraryDraftCommand;
import com.gabolle.backend.trip.domain.Trip;
import com.gabolle.backend.trip.domain.TripRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 추천 결과를 날짜·구간으로 조립하는 규칙.
 * Spring 컨텍스트를 띄우지 않는다 — 도메인 계산 규칙을 보는 것이고 {@link TripRepository}·
 * {@link PlaceRepository} 는 Mockito 로 대신한다.
 */
class ItineraryDraftServiceTest {

	/**
	 * 영업시간을 모른다고만 답하는 문. 이 검사들이 재는 것은 날짜 배분과 시각 배정이고, 모름은
	 * 자리 배정을 안 바꾼다.
	 */
	private static final OpeningHoursFilterPort ALWAYS_UNKNOWN =
			(placeId, at) -> OpeningHoursFilterPort.Answer.NOT_COLLECTED;

	/** 브레이크타임·라스트오더를 모른다고만 답하는 문. */
	private static final PlaceTimeFactFilterPort ALWAYS_UNKNOWN_TIME_FACT = new PlaceTimeFactFilterPort() {
		@Override
		public OpeningHoursFilterPort.Answer breakTimeAt(UUID placeId, java.time.OffsetDateTime at) {
			return OpeningHoursFilterPort.Answer.NOT_COLLECTED;
		}

		@Override
		public OpeningHoursFilterPort.Answer lastOrderAt(UUID placeId, java.time.OffsetDateTime at) {
			return OpeningHoursFilterPort.Answer.NOT_COLLECTED;
		}
	};

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-05T00:00:00Z"), ZoneOffset.UTC);

	private TripRepository tripRepository;

	private PlaceRepository placeRepository;

	private ItineraryDraftService service;

	@BeforeEach
	void setUp() {
		this.tripRepository = mock(TripRepository.class);
		this.placeRepository = mock(PlaceRepository.class);
		ItineraryRepository itineraryRepository = mock(ItineraryRepository.class);
		// 이동시간 포트를 "없는" 상태로 준다. 이 검사들이 재는 것은 날짜 배분과 시각 배정이지
		//    바깥 길찾기가 아니고, 포트가 없으면 직선거리만 채우는 갈래로 떨어진다 — 그 갈래도
		//    살아 있어야 한다.
		//    직선거리만 채우는 갈래로 떨어진다 — 그 갈래도 살아 있어야 한다.
		@SuppressWarnings("unchecked")
		ObjectProvider<TravelTimePort> noTravelTime = mock(ObjectProvider.class);
		when(noTravelTime.getIfAvailable()).thenReturn(null);

		ItineraryLegPlanner legPlanner = new ItineraryLegPlanner(this.placeRepository, noTravelTime);
		this.service = new ItineraryDraftService(this.tripRepository, itineraryRepository, CLOCK, 4, 3, "FOOD", legPlanner, ALWAYS_UNKNOWN, ALWAYS_UNKNOWN_TIME_FACT);

		// 좌표를 모르는 장소만 다루는 테스트들이 기본으로 쓴다 — 거리는 항상 null 이 된다.
		when(this.placeRepository.findByPlaceIdIn(anyCollection())).thenReturn(List.of());
	}

	/**
	 * 밥집 상한은 설정값이 아니라 그 여행의 활동 시간대에 실제로 들어가는 끼니 수다.
	 * 09:00~18:00 은 점심과 저녁만 들어가므로 2 다 — 9시에 시작하는 여행에 아침을 끼워 넣지 않는다.
	 */
	@Test
	@DisplayName("09~18시 여행은 끼니가 둘이라 밥집이 순위를 다 차지해도 둘까지만 들어간다")
	void foodIsCappedByTheMealsThatFitInTheWindow() {
		Trip trip = tripOf(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 10),
				LocalTime.of(9, 0), LocalTime.of(18, 0));
		when(this.tripRepository.findById("trip_1")).thenReturn(Optional.of(trip));

		// 순위 상위가 전부 밥집이고 명소는 뒤에 있다 — 운영 후보 분포와 같은 모양이다.
		ItineraryDraft draft = this.service.assemble(commandOf("trip_1", plannedPlacesOf(
				"FOOD", "FOOD", "FOOD", "FOOD", "FOOD", "CULTURE_TEMPLE", "NATURE_WALK")));

		assertThat(draft.items()).hasSize(4);
		List<String> categories = draft.items().stream()
				.map(this::categoryOfItem)
				.toList();
		assertThat(categories.stream().filter("FOOD"::equals).count()).isEqualTo(2);
	}

	/**
	 * 밥집은 밥 때에 놓는다. 자리 순서가 아니라 그 칸의 시각이 정한다.
	 * 09:00~18:00 에 네 곳이면 칸이 2시간 15분씩 넷이고, 점심·저녁에 60분 이상 걸리는 것은
	 * 둘째와 넷째다. 첫 칸(09:00~11:15)은 아침에 30분 걸치지만 스친 것이라 명소 자리다.
	 */
	@Test
	@DisplayName("밥집은 점심·저녁 칸에만 들어가고 오전 첫 칸은 명소가 차지한다")
	void foodIsSeatedOnlyIntoMealSlots() {
		Trip trip = tripOf(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 10),
				LocalTime.of(9, 0), LocalTime.of(18, 0));
		when(this.tripRepository.findById("trip_1")).thenReturn(Optional.of(trip));

		ItineraryDraft draft = this.service.assemble(commandOf("trip_1", plannedPlacesOf(
				"FOOD", "FOOD", "FOOD", "FOOD", "FOOD", "CULTURE_TEMPLE", "NATURE_WALK")));

		List<ItineraryDraft.DraftItem> today = draft.items().stream()
				.sorted((a, b) -> Integer.compare(a.sequence(), b.sequence()))
				.toList();
		assertThat(today.stream().map(ItineraryDraft.DraftItem::startTime))
				.containsExactly(LocalTime.of(9, 0), LocalTime.of(11, 15),
						LocalTime.of(13, 30), LocalTime.of(15, 45));
		assertThat(today.stream().map(this::categoryOfItem).map("FOOD"::equals))
				.containsExactly(false, true, false, true);
	}

	/**
	 * 빈 자리를 밥집으로 메우지 않는다. 메우면 명소 데이터가 모자라다는 사실이 화면에도 팀에게도
	 * 안 보여서, 비워 두고 판에 {@code SIGHT_SLOT_UNFILLED} 를 남긴다.
	 */
	@Test
	@DisplayName("명소가 없으면 빈 자리를 밥집으로 메우지 않고 그 사실을 판에 남긴다")
	void unfilledSightSlotsAreLeftEmptyAndReported() {
		Trip trip = tripOf(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 10),
				LocalTime.of(9, 0), LocalTime.of(18, 0));
		when(this.tripRepository.findById("trip_1")).thenReturn(Optional.of(trip));

		ItineraryDraft draft = this.service.assemble(commandOf("trip_1",
				plannedPlacesOf("FOOD", "FOOD", "FOOD", "FOOD", "FOOD", "FOOD")));

		// 하루 상한은 4 지만 끼니가 둘뿐이라 둘만 들어간다. 나머지 넷은 버린다.
		assertThat(draft.items()).hasSize(2);
		assertThat(draft.warningCodes()).containsExactly(ItineraryWarningCodes.SIGHT_SLOT_UNFILLED);
	}

	/** 아침까지 들어가는 긴 여행은 끼니가 셋이다 — 설정 상한(3)이 그 위를 막는다. */
	@Test
	@DisplayName("07~21시 여행은 아침까지 끼니가 셋이다")
	void aLongWindowAdmitsBreakfastToo() {
		Trip trip = tripOf(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 10),
				LocalTime.of(7, 0), LocalTime.of(21, 0));
		when(this.tripRepository.findById("trip_1")).thenReturn(Optional.of(trip));

		ItineraryDraft draft = this.service.assemble(commandOf("trip_1", plannedPlacesOf(
				"FOOD", "FOOD", "FOOD", "FOOD", "FOOD", "CULTURE_TEMPLE", "NATURE_WALK")));

		assertThat(draft.items()).hasSize(4);
		assertThat(draft.items().stream().map(this::categoryOfItem).filter("FOOD"::equals).count())
				.isEqualTo(3);
	}

	/** 갈래를 모르면 밥집으로 세지 않는다. */
	@Test
	@DisplayName("갈래를 모르는 장소는 끼니로 세지 않는다")
	void unknownCategoryIsNotCountedAsFood() {
		Trip trip = tripOf(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 10));
		when(this.tripRepository.findById("trip_1")).thenReturn(Optional.of(trip));

		ItineraryDraft draft = this.service.assemble(commandOf("trip_1", plannedPlaces(6)));

		assertThat(draft.items()).hasSize(4);
	}

	@Test
	@DisplayName("하루짜리 여행에 10곳을 줘도 하루 상한(4)만 들어간다")
	void singleDayTripNeverExceedsTheDailyCap() {
		Trip trip = tripOf(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 10));
		when(this.tripRepository.findById("trip_1")).thenReturn(Optional.of(trip));

		ItineraryDraft draft = this.service.assemble(commandOf("trip_1", plannedPlaces(10)));

		assertThat(draft.items()).hasSize(4);
		assertThat(draft.items()).allMatch((item) -> item.dayIndex() == 0);
	}

	@Test
	@DisplayName("모든 날이 차면 남는 후보는 일정에 안 넣는다 — 마지막 날에 쌓지 않는다")
	void placesBeyondEveryDayCapAreDropped() {
		Trip trip = tripOf(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 12));
		when(this.tripRepository.findById("trip_1")).thenReturn(Optional.of(trip));

		ItineraryDraft draft = this.service.assemble(commandOf("trip_1", plannedPlaces(20)));

		Map<Integer, Long> countByDay = draft.items().stream()
				.collect(Collectors.groupingBy(ItineraryDraft.DraftItem::dayIndex, Collectors.counting()));
		assertThat(draft.items()).hasSize(12);
		assertThat(countByDay).containsEntry(0, 4L).containsEntry(1, 4L).containsEntry(2, 4L);
	}

	@Test
	@DisplayName("순위대로 날짜에 배분한다 — 하루 4개를 채우면 다음 날로 넘긴다")
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

		// 이동시간을 아는 포트를 끼운다. 좌표가 없어도 포트가 답을 주면 그 값이 그대로 구간에
		// 실려야 한다 — 이 검사가 보는 것은 배선이지 거리 계산이 아니다.
		TravelTimePort port = (fromLat, fromLng, toLat, toLng, mode) ->
				new TravelTime(1234, 25, ItineraryItem.DataStatus.VERIFIED);
		@SuppressWarnings("unchecked")
		ObjectProvider<TravelTimePort> provider = mock(ObjectProvider.class);
		when(provider.getIfAvailable()).thenReturn(port);
		ItineraryLegPlanner legPlanner = new ItineraryLegPlanner(this.placeRepository, provider);
		ItineraryDraftService withTravelTime = new ItineraryDraftService(this.tripRepository,
				mock(ItineraryRepository.class), CLOCK, 4, 3, "FOOD", legPlanner, ALWAYS_UNKNOWN,
				ALWAYS_UNKNOWN_TIME_FACT);

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
		// buildLegs() 는 Trip 이 이동수단을 아직 노출하지 않아 항상 WALK 만 만든다 — 그래서 이
		//    규칙 자체는 ItineraryLegPlanner.walkingMetersFor(...) 를 직접 불러 확인한다.
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

	@Test
	@DisplayName("그 시각에 닫는 곳은 그 자리에 안 놓는다 — 다음 후보가 앞으로 온다")
	void closedPlaceIsNotSeatedInThatSlot() {
		Trip trip = tripWithWindow(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 10));
		when(this.tripRepository.findById("trip_1")).thenReturn(Optional.of(trip));

		List<ItineraryDraftCommand.PlannedPlace> places = plannedPlaces(2);
		UUID first = places.get(0).placeId();
		// 순위 1등이 09:00 에 닫는다. 그 자리는 2등이 받아야 한다.
		ItineraryDraftService service = serviceWith((placeId, at) ->
				placeId.equals(first) && at.toLocalTime().equals(LocalTime.of(9, 0))
						? OpeningHoursFilterPort.Answer.CLOSED
						: OpeningHoursFilterPort.Answer.OPEN);

		ItineraryDraft draft = service.assemble(commandOf("trip_1", places));

		assertThat(draft.items()).extracting(ItineraryDraft.DraftItem::placeId)
				.containsExactly(places.get(1).placeId(), first);
		assertThat(draft.items().get(0).warningCodes()).doesNotContain("OPENING_HOURS_CLOSED");
	}

	@Test
	@DisplayName("영업시간을 모르는 곳은 순위 그대로 앉는다 — 모름을 닫힘처럼 다루면 2,355곳이 통째로 빠진다")
	void unknownHoursKeepTheRankOrder() {
		Trip trip = tripWithWindow(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 10));
		when(this.tripRepository.findById("trip_1")).thenReturn(Optional.of(trip));

		List<ItineraryDraftCommand.PlannedPlace> places = plannedPlaces(3);
		ItineraryDraft draft = serviceWith((placeId, at) -> OpeningHoursFilterPort.Answer.NOT_COLLECTED)
				.assemble(commandOf("trip_1", places));

		assertThat(draft.items()).extracting(ItineraryDraft.DraftItem::placeId)
				.containsExactly(places.get(0).placeId(), places.get(1).placeId(), places.get(2).placeId());
	}

	@Test
	@DisplayName("모르는 곳이 아는 곳에 자리를 안 뺏긴다 — 모름과 열림이 섞여도 순위가 유지된다")
	void unknownIsSeatedAheadOfAKnownOpenPlace() {
		Trip trip = tripWithWindow(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 10));
		when(this.tripRepository.findById("trip_1")).thenReturn(Optional.of(trip));

		List<ItineraryDraftCommand.PlannedPlace> places = plannedPlaces(2);
		UUID first = places.get(0).placeId();
		// 1등은 영업시간을 모르고 2등은 연다. 모름을 닫힘처럼 다루면 2등이 앞으로 온다.
		ItineraryDraft draft = serviceWith((placeId, at) -> placeId.equals(first)
				? OpeningHoursFilterPort.Answer.NOT_COLLECTED
				: OpeningHoursFilterPort.Answer.OPEN)
				.assemble(commandOf("trip_1", places));

		assertThat(draft.items()).extracting(ItineraryDraft.DraftItem::placeId)
				.as("모르는 것과 닫힌 것은 다르다")
				.containsExactly(first, places.get(1).placeId());
	}

	@Test
	@DisplayName("남은 후보가 전부 닫혀 있으면 그대로 놓고 경고를 붙인다 — 빈 자리를 남기지 않는다")
	void allClosedStillSeatsThePlaceWithAWarning() {
		Trip trip = tripWithWindow(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 10));
		when(this.tripRepository.findById("trip_1")).thenReturn(Optional.of(trip));

		List<ItineraryDraftCommand.PlannedPlace> places = plannedPlaces(2);
		ItineraryDraft draft = serviceWith((placeId, at) -> OpeningHoursFilterPort.Answer.CLOSED)
				.assemble(commandOf("trip_1", places));

		assertThat(draft.items()).hasSize(2);
		assertThat(draft.items()).allSatisfy((item) ->
				assertThat(item.warningCodes()).contains("OPENING_HOURS_CLOSED"));
	}

	@Test
	@DisplayName("활동 시간대가 없으면 문을 묻지 않는다 — 시각이 없으면 판정할 수가 없다")
	void withoutATimeWindowNothingIsAsked() {
		Trip trip = tripOf(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 10));
		when(this.tripRepository.findById("trip_1")).thenReturn(Optional.of(trip));

		List<ItineraryDraftCommand.PlannedPlace> places = plannedPlaces(2);
		ItineraryDraft draft = serviceWith((placeId, at) -> {
			throw new AssertionError("시각이 없는데 문에 물었다");
		}).assemble(commandOf("trip_1", places));

		assertThat(draft.items()).extracting(ItineraryDraft.DraftItem::placeId)
				.containsExactly(places.get(0).placeId(), places.get(1).placeId());
	}

	@Test
	@DisplayName("🔴 S15P21E201-94 — 브레이크타임인 곳은 그 자리에 안 놓는다")
	void breakTimePlaceIsNotSeatedInThatSlot() {
		Trip trip = tripWithWindow(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 10));
		when(this.tripRepository.findById("trip_1")).thenReturn(Optional.of(trip));

		List<ItineraryDraftCommand.PlannedPlace> places = plannedPlaces(2);
		UUID first = places.get(0).placeId();
		// 순위 1등이 09:00 에 브레이크타임이다. 그 자리는 2등이 받아야 한다.
		ItineraryDraftService service = serviceWith(ALWAYS_UNKNOWN, new PlaceTimeFactFilterPort() {
			@Override
			public OpeningHoursFilterPort.Answer breakTimeAt(UUID placeId, java.time.OffsetDateTime at) {
				return placeId.equals(first) && at.toLocalTime().equals(LocalTime.of(9, 0))
						? OpeningHoursFilterPort.Answer.CLOSED
						: OpeningHoursFilterPort.Answer.OPEN;
			}

			@Override
			public OpeningHoursFilterPort.Answer lastOrderAt(UUID placeId, java.time.OffsetDateTime at) {
				return OpeningHoursFilterPort.Answer.OPEN;
			}
		});

		ItineraryDraft draft = service.assemble(commandOf("trip_1", places));

		assertThat(draft.items()).extracting(ItineraryDraft.DraftItem::placeId)
				.containsExactly(places.get(1).placeId(), first);
		assertThat(draft.items().get(0).warningCodes()).doesNotContain("BREAK_TIME_CLOSED");
	}

	@Test
	@DisplayName("🔴 S15P21E201-94 — 라스트오더를 지난 곳은 그 자리에 안 놓는다")
	void lastOrderPassedPlaceIsNotSeatedInThatSlot() {
		Trip trip = tripWithWindow(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 10));
		when(this.tripRepository.findById("trip_1")).thenReturn(Optional.of(trip));

		List<ItineraryDraftCommand.PlannedPlace> places = plannedPlaces(2);
		UUID first = places.get(0).placeId();
		ItineraryDraftService service = serviceWith(ALWAYS_UNKNOWN, new PlaceTimeFactFilterPort() {
			@Override
			public OpeningHoursFilterPort.Answer breakTimeAt(UUID placeId, java.time.OffsetDateTime at) {
				return OpeningHoursFilterPort.Answer.OPEN;
			}

			@Override
			public OpeningHoursFilterPort.Answer lastOrderAt(UUID placeId, java.time.OffsetDateTime at) {
				return placeId.equals(first) && at.toLocalTime().equals(LocalTime.of(9, 0))
						? OpeningHoursFilterPort.Answer.CLOSED
						: OpeningHoursFilterPort.Answer.OPEN;
			}
		});

		ItineraryDraft draft = service.assemble(commandOf("trip_1", places));

		assertThat(draft.items()).extracting(ItineraryDraft.DraftItem::placeId)
				.containsExactly(places.get(1).placeId(), first);
		assertThat(draft.items().get(0).warningCodes()).doesNotContain("LAST_ORDER_PASSED");
	}

	@Test
	@DisplayName("🔴 S15P21E201-94 — 남은 후보가 전부 브레이크타임이어도 그대로 놓고 경고를 붙인다")
	void allBreakTimeStillSeatsThePlaceWithAWarning() {
		Trip trip = tripWithWindow(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 10));
		when(this.tripRepository.findById("trip_1")).thenReturn(Optional.of(trip));

		List<ItineraryDraftCommand.PlannedPlace> places = plannedPlaces(2);
		ItineraryDraftService service = serviceWith(ALWAYS_UNKNOWN, new PlaceTimeFactFilterPort() {
			@Override
			public OpeningHoursFilterPort.Answer breakTimeAt(UUID placeId, java.time.OffsetDateTime at) {
				return OpeningHoursFilterPort.Answer.CLOSED;
			}

			@Override
			public OpeningHoursFilterPort.Answer lastOrderAt(UUID placeId, java.time.OffsetDateTime at) {
				return OpeningHoursFilterPort.Answer.OPEN;
			}
		});

		ItineraryDraft draft = service.assemble(commandOf("trip_1", places));

		assertThat(draft.items()).hasSize(2);
		assertThat(draft.items()).allSatisfy((item) ->
				assertThat(item.warningCodes()).contains("BREAK_TIME_CLOSED"));
	}

	private ItineraryDraftService serviceWith(OpeningHoursFilterPort openingHours) {
		return serviceWith(openingHours, ALWAYS_UNKNOWN_TIME_FACT);
	}

	private ItineraryDraftService serviceWith(OpeningHoursFilterPort openingHours, PlaceTimeFactFilterPort timeFact) {
		@SuppressWarnings("unchecked")
		ObjectProvider<TravelTimePort> noTravelTime = mock(ObjectProvider.class);
		when(noTravelTime.getIfAvailable()).thenReturn(null);
		return new ItineraryDraftService(this.tripRepository, mock(ItineraryRepository.class), CLOCK, 4, 3, "FOOD",
				new ItineraryLegPlanner(this.placeRepository, noTravelTime), openingHours, timeFact);
	}

	@Test
	@DisplayName("일정을 처음 저장하면 여행이 준비 중에서 일정 있음으로 바뀐다")
	void persistMovesTripFromPlanningToReady() {
		Trip trip = tripOf(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 11));
		when(this.tripRepository.findById("trip_1")).thenReturn(Optional.of(trip));

		this.service.persist(this.service.assemble(commandOf("trip_1", plannedPlaces(2))));

		ArgumentCaptor<Trip> saved = ArgumentCaptor.forClass(Trip.class);
		verify(this.tripRepository).updateStatus(saved.capture());
		assertThat(saved.getValue().status()).isEqualTo(Trip.Status.READY);
	}

	/** PLANNING 일 때만 옮긴다. 여행 중인 여행의 일정을 다시 만들 때 READY 로 쓰면 진행 단계가 뒤로 밀린다. */
	@Test
	@DisplayName("여행 중인 여행은 일정을 다시 만들어도 단계가 뒤로 밀리지 않는다")
	void persistDoesNotDowngradeTripAlreadyUnderway() {
		Trip trip = Trip.builder()
				.tripId("itn_trip_1")
				.createdBy("usr_1")
				.ownerType(Trip.OwnerType.USER)
				.startDate(LocalDate.of(2026, 9, 10))
				.finishDate(LocalDate.of(2026, 9, 11))
				.partySize(2)
				.timezone("Asia/Seoul")
				.status(Trip.Status.IN_PROGRESS)
				.createdAt(Instant.now())
				.build();
		when(this.tripRepository.findById("trip_1")).thenReturn(Optional.of(trip));

		this.service.persist(this.service.assemble(commandOf("trip_1", plannedPlaces(2))));

		verify(this.tripRepository, never()).updateStatus(any());
		assertThat(trip.status()).isEqualTo(Trip.Status.IN_PROGRESS);
	}

	/** 09:00~17:00 활동 시간대를 가진 여행. 두 항목이면 칸이 09:00 과 13:00 이다. */
	private Trip tripWithWindow(LocalDate startDate, LocalDate finishDate) {
		return new Trip("itn_trip_1", "usr_1", startDate, finishDate, null, null, null, 2, null, "Asia/Seoul",
				null, LocalTime.of(9, 0), LocalTime.of(17, 0), Instant.now());
	}

	private Trip tripOf(LocalDate startDate, LocalDate finishDate) {
		return new Trip("itn_trip_1", "usr_1", startDate, finishDate, null, null, null, 2, null, "Asia/Seoul",
				Instant.now());
	}

	/** 활동 시간대까지 정한 여행 — 끼니가 몇 번인지는 이 두 값이 정한다. */
	private Trip tripOf(LocalDate startDate, LocalDate finishDate, LocalTime windowStart, LocalTime windowEnd) {
		return new Trip("itn_trip_1", "usr_1", startDate, finishDate, null, null, null, 2,
				windowStart + "-" + windowEnd, "Asia/Seoul", null, windowStart, windowEnd, Instant.now());
	}

	/** {@link #plannedPlacesOf} 가 만든 장소의 갈래 — 초안 항목에는 placeId 만 있어서 되짚는다. */
	private final Map<UUID, String> categoryByPlaceId = new HashMap<>();

	private String categoryOfItem(ItineraryDraft.DraftItem item) {
		return this.categoryByPlaceId.get(item.placeId());
	}

	private List<ItineraryDraftCommand.PlannedPlace> plannedPlacesOf(String... categories) {
		List<ItineraryDraftCommand.PlannedPlace> places = new ArrayList<>();
		for (int i = 0; i < categories.length; i++) {
			UUID placeId = UUID.randomUUID();
			this.categoryByPlaceId.put(placeId, categories[i]);
			places.add(new ItineraryDraftCommand.PlannedPlace(placeId, i + 1, List.of("REASON"),
					List.of(), categories[i]));
		}
		return places;
	}

	/**
	 * 시각은 이동 시간을 빼고 깐다. 활동 시간대를 항목 수로 그냥 나누면 칸과 칸이 딱 붙어,
	 * 두 번째 장소부터 계획보다 늦게 도착하고 그 지각이 하루 끝까지 쌓인다.
	 * 아래 셋이 그것을 막는다.
	 */
	@Test
	@DisplayName("🔴 S15P21E201-1130 — 시각이 이동 시간을 비켜 간다: 머무는 시간이 줄고 사이가 벌어진다")
	void stayTimeMakesRoomForTravelBetweenPlaces() {
		Trip trip = tripWithWindow(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 10));
		when(this.tripRepository.findById("trip_1")).thenReturn(Optional.of(trip));

		// 구간마다 25분. 장소 3곳이면 구간도 3개다 — 출발지에서 첫 장소로 가는 길이 첫 구간이다.
		ItineraryDraft draft = serviceWithTravelMinutes(25)
				.assemble(commandOf("trip_1", plannedPlaces(3)));

		List<ItineraryDraft.DraftItem> items = draft.items();
		assertThat(items).hasSize(3);

		// 활동 시간대 09:00~17:00 = 480분. 이동 75분을 빼고 3으로 나누면 135분씩이다.
		assertThat(items).allSatisfy((item) ->
				assertThat(item.stayMinutes())
						.as("머무는 시간에서 이동 시간을 빼지 않으면 그만큼 매번 늦는다")
						.isEqualTo(135));

		// 첫 장소도 09:00 에 시작하지 않는다 — 출발지에서 거기까지 25분이 걸린다.
		assertThat(items.get(0).startTime()).isEqualTo(LocalTime.of(9, 25));

		// 항목과 항목 사이가 정확히 이동 시간만큼 벌어져 있다.
		for (int i = 1; i < items.size(); i++) {
			assertThat(Duration.between(items.get(i - 1).endTime(), items.get(i).startTime()).toMinutes())
					.as("앞 장소가 끝난 뒤 다음 장소가 시작하기까지 이동할 시간이 있어야 한다")
					.isEqualTo(25);
		}

		// 그러고도 활동 시간대를 넘지 않는다.
		assertThat(items.get(items.size() - 1).endTime()).isEqualTo(LocalTime.of(17, 0));
	}

	@Test
	@DisplayName("🔴 이동 시간을 모르면 예전과 똑같이 나눈다 — 모르는 값을 지어내지 않는다")
	void withoutMeasuredTravelTheLayoutIsUnchanged() {
		Trip trip = tripWithWindow(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 10));
		when(this.tripRepository.findById("trip_1")).thenReturn(Optional.of(trip));

		// this.service 는 이동시간 포트가 없는 갈래다 — durationMin 이 null 이다.
		ItineraryDraft draft = this.service.assemble(commandOf("trip_1", plannedPlaces(3)));

		assertThat(draft.items()).allSatisfy((item) -> assertThat(item.stayMinutes()).isEqualTo(160));
		assertThat(draft.items().get(0).startTime()).isEqualTo(LocalTime.of(9, 0));
		assertThat(draft.items().get(2).endTime()).isEqualTo(LocalTime.of(17, 0));
	}

	@Test
	@DisplayName("🔴 이동만으로 하루가 다 차면 시각을 아예 안 준다 — 되지도 않는 일정을 그리지 않는다")
	void whenTravelEatsTheWholeDayTheTimesAreUnknown() {
		Trip trip = tripWithWindow(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 10));
		when(this.tripRepository.findById("trip_1")).thenReturn(Optional.of(trip));

		// 구간 3개 × 200분 = 600분 > 활동 시간대 480분.
		ItineraryDraft draft = serviceWithTravelMinutes(200)
				.assemble(commandOf("trip_1", plannedPlaces(3)));

		assertThat(draft.items()).allSatisfy((item) -> {
			assertThat(item.startTime()).isNull();
			assertThat(item.endTime()).isNull();
			assertThat(item.stayMinutes()).isNull();
			assertThat(item.dataStatus()).isEqualTo("UNKNOWN");
		});
	}

	/** 구간마다 같은 소요를 돌려주는 서비스를 만든다. */
	private ItineraryDraftService serviceWithTravelMinutes(int minutes) {
		TravelTimePort port = (fromLat, fromLng, toLat, toLng, mode) ->
				new TravelTime(1000, minutes, ItineraryItem.DataStatus.VERIFIED);
		@SuppressWarnings("unchecked")
		ObjectProvider<TravelTimePort> provider = mock(ObjectProvider.class);
		when(provider.getIfAvailable()).thenReturn(port);
		return new ItineraryDraftService(this.tripRepository, mock(ItineraryRepository.class), CLOCK,
				4, 3, "FOOD", new ItineraryLegPlanner(this.placeRepository, provider),
				ALWAYS_UNKNOWN, ALWAYS_UNKNOWN_TIME_FACT);
	}

	private List<ItineraryDraftCommand.PlannedPlace> plannedPlaces(int count) {
		List<ItineraryDraftCommand.PlannedPlace> places = new ArrayList<>();
		for (int i = 1; i <= count; i++) {
			places.add(new ItineraryDraftCommand.PlannedPlace(UUID.randomUUID(), i, List.of("REASON"), List.of(), null));
		}
		return places;
	}

	private ItineraryDraftCommand commandOf(String tripId, List<ItineraryDraftCommand.PlannedPlace> places) {
		return new ItineraryDraftCommand(UUID.randomUUID(), tripId, "usr_1", places,
				"model-1", "feature-1", "ontology-1", "policy-1", "dataset-1");
	}
}
