package com.gabolle.backend.itinerary;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.context.ApplicationEventPublisher;
import org.springframework.beans.factory.ObjectProvider;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.gabolle.backend.itinerary.application.ItineraryDraftService;
import com.gabolle.backend.place.domain.Place;
import com.gabolle.backend.place.service.OpeningHoursFilterPort;
import com.gabolle.backend.place.service.PlaceTimeFactFilterPort;
import com.gabolle.backend.place.service.PlaceTimeTablePort;
import com.gabolle.backend.itinerary.application.ItineraryLegPlanner;
import com.gabolle.backend.itinerary.application.port.RouteOrderPort;
import com.gabolle.backend.itinerary.application.port.TravelTime;
import com.gabolle.backend.itinerary.domain.ItineraryItem;
import com.gabolle.backend.itinerary.application.port.TravelTimePort;
import com.gabolle.backend.itinerary.domain.ItineraryLeg;
import com.gabolle.backend.itinerary.domain.ItineraryRepository;
import com.gabolle.backend.itinerary.domain.ItineraryWarningCodes;
import com.gabolle.backend.place.repository.PlaceRepository;
import com.gabolle.backend.recommendation.application.port.ItineraryDraft;
import com.gabolle.backend.recommendation.application.port.ItineraryDraftCommand;
import com.gabolle.backend.itinerary.application.port.PlaceEventSchedule;
import com.gabolle.backend.itinerary.application.port.PlaceEventSchedulePort;
import com.gabolle.backend.itinerary.domain.Itinerary;
import com.gabolle.backend.itinerary.domain.ItineraryContent;
import com.gabolle.backend.itinerary.domain.ItineraryVersion;
import com.gabolle.backend.recommendation.adapter.SeedBoost;
import com.gabolle.backend.recommendation.application.port.ItineraryRevisionCommand;
import com.gabolle.backend.recommendation.application.port.ItineraryRevisionDraft;
import com.gabolle.backend.recommendation.domain.JobType;
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

	/**
	 * 시각마다 답하는 가짜 문 둘을 조립기가 받는 영업표 문으로 잇는다(S15P21E201-1663). 가짜는 (장소, 시각)으로 답을
	 * 정하므로 시각마다 그대로 물어 옮긴다 — 영업표를 한 번 읽는 것은 DB 구현의 일이고 이 파일의 주제가 아니다.
	 */
	private static PlaceTimeTablePort tables(OpeningHoursFilterPort openingHours, PlaceTimeFactFilterPort timeFact) {
		return (placeId) -> new PlaceTimeTablePort.PlaceTimeTable() {
			@Override
			public OpeningHoursFilterPort.Answer openAt(java.time.OffsetDateTime at) {
				return openingHours.openAt(placeId, at);
			}

			@Override
			public OpeningHoursFilterPort.Answer breakTimeAt(java.time.OffsetDateTime at) {
				return timeFact.breakTimeAt(placeId, at);
			}

			@Override
			public OpeningHoursFilterPort.Answer lastOrderAt(java.time.OffsetDateTime at) {
				return timeFact.lastOrderAt(placeId, at);
			}
		};
	}

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-05T00:00:00Z"), ZoneOffset.UTC);

	private TripRepository tripRepository;

	private PlaceRepository placeRepository;

	/**
	 * 알림 사건을 버리는 자리 — S15P21E201-1391.
	 *
	 * <p>이 검사가 보는 것은 초안과 저장이지 알림이 아니다. 알림 자체는
	 * {@code TripPushNotifierTest} 가 본다.
	 */
	private static ApplicationEventPublisher noEvents() {
		return (event) -> { };
	}

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
		this.service = new ItineraryDraftService(this.tripRepository, itineraryRepository, CLOCK, 4, 3, "FOOD", 1, legPlanner, tables(ALWAYS_UNKNOWN, ALWAYS_UNKNOWN_TIME_FACT), noRouteOrder(), this.placeRepository, noEvents());

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
	 *
	 * <p>실제 시각은 칸이 아니라 곳마다 머무는 시간으로 깐다(S15P21E201-1667). 60분씩 넷이면 300분이 남는데, 저녁이
	 * 17:00 에 오려면 300분을 다 저녁 앞에 넣어야 한다. 점심 앞에는 고르게 나눈 몫(사이 셋에 100분씩)을 넣어 11:40 —
	 * 점심 시각대(11:30~14:00) 안이라 그대로 둔다.
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
				.containsExactly(LocalTime.of(9, 0), LocalTime.of(11, 40),
						LocalTime.of(14, 20), LocalTime.of(17, 0));
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

	/**
	 * 운영 일정 201개 중 4개가 같은 체인의 다른 지점을 두 번 넣었다 (S15P21E201-1616). 이름은 운영에 실린 모양 그대로
	 * — 붙여 쓴 「배스킨라빈스광안역점」, 영어 「Starbucks」 — 이다. 띄어쓰기로 가르면 이 둘을 놓친다.
	 */
	@Test
	@DisplayName("🔴 한 일정에 같은 상표는 한 번만 — 순위가 높은 지점만 남고, 체인 아닌 곳은 그대로다")
	void aBrandAppearsOnceInAnItinerary() {
		Trip trip = tripOf(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 12));
		when(this.tripRepository.findById("trip_1")).thenReturn(Optional.of(trip));
		List<ItineraryDraftCommand.PlannedPlace> places = plannedPlacesNamed(List.of(
				"배스킨라빈스광안역점", "해운대해수욕장", "베스킨라빈스 해운대점", "동백섬", "Starbucks", "스타벅스 해운대달맞이길점"));

		ItineraryDraft draft = this.service.assemble(commandOf("trip_1", places));

		List<UUID> placed = draft.items().stream().map(ItineraryDraft.DraftItem::placeId).toList();
		assertThat(placed)
				.contains(places.get(0).placeId(), places.get(1).placeId(), places.get(3).placeId(),
						places.get(4).placeId())
				.as("같은 상표의 뒤 지점은 빠진다")
				.doesNotContain(places.get(2).placeId(), places.get(5).placeId());
	}

	/**
	 * 🔴 운영(2026-09-25) — 일정 161개 중 3개에 젤라또부(400m 떨어진 두 지점)·젤라또조이(5km)가 두 번씩 들어갔다.
	 * 등록된 상표가 아니어도 이름이 같으면 한 곳만 — 띄어쓰기·대소문자는 무시한다(S15P21E201-1631).
	 */
	@Test
	@DisplayName("🔴 등록 상표가 아니어도 이름이 같은 가게는 한 일정에 한 곳만 — 순위가 높은 쪽이 남는다")
	void sameNameAppearsOnceEvenOffTheBrandList() {
		Trip trip = tripOf(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 12));
		when(this.tripRepository.findById("trip_1")).thenReturn(Optional.of(trip));
		List<ItineraryDraftCommand.PlannedPlace> places = plannedPlacesNamed(List.of(
				"젤라또부", "해운대해수욕장", "젤라또 부", "동백섬", "Gelato Joy", "gelato joy"));

		ItineraryDraft draft = this.service.assemble(commandOf("trip_1", places));

		List<UUID> placed = draft.items().stream().map(ItineraryDraft.DraftItem::placeId).toList();
		assertThat(placed)
				.contains(places.get(0).placeId(), places.get(1).placeId(), places.get(3).placeId(),
						places.get(4).placeId())
				.as("이름이 같은 뒤 지점은 빠진다")
				.doesNotContain(places.get(2).placeId(), places.get(5).placeId());
	}

	/**
	 * 🔴 시연 점검(2026-09-26) — 광안리 드론쇼가 한 일정에 두 번 앉았다. 운영에 오픈스트리트맵 줄과 TourAPI 줄이 약 400m
	 * 떨어져 따로 있고, 이름은 괄호 하나만 다르다(S15P21E201-1758). 좌표는 운영 두 줄 그대로다.
	 */
	@Test
	@DisplayName("🔴 괄호 안만 다른 같은 이름이 1km 안이면 한 곳만 — 광안리 드론쇼 두 줄")
	void parenVariantWithinOneKmAppearsOnce() {
		Trip trip = tripOf(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 12));
		when(this.tripRepository.findById("trip_1")).thenReturn(Optional.of(trip));
		List<ItineraryDraftCommand.PlannedPlace> places = plannedPlacesNamedAt(
				List.of("광안리 M 드론 라이트쇼", "해운대해수욕장", "광안리 M(Marvelous) 드론 라이트쇼", "동백섬"),
				List.of(new double[] { 35.15485, 129.12273 }, new double[] { 35.1587, 129.1604 },
						new double[] { 35.15377, 129.11852 }, new double[] { 35.1530, 129.1520 }));

		ItineraryDraft draft = this.service.assemble(commandOf("trip_1", places));

		List<UUID> placed = draft.items().stream().map(ItineraryDraft.DraftItem::placeId).toList();
		assertThat(placed)
				.contains(places.get(0).placeId(), places.get(1).placeId(), places.get(3).placeId())
				.as("400m 떨어진 뒤 줄은 빠진다")
				.doesNotContain(places.get(2).placeId());
	}

	@Test
	@DisplayName("괄호 안만 다른 이름이라도 1km 밖이면 둘 다 남는다 — 괄호가 곳을 가르는 말이다")
	void parenVariantFartherThanOneKmStays() {
		Trip trip = tripOf(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 12));
		when(this.tripRepository.findById("trip_1")).thenReturn(Optional.of(trip));
		List<ItineraryDraftCommand.PlannedPlace> places = plannedPlacesNamedAt(
				List.of("전망대(황령산)", "광안리해수욕장", "전망대(이기대)"),
				List.of(new double[] { 35.1573, 129.0829 }, new double[] { 35.1532, 129.1187 },
						new double[] { 35.1336, 129.1225 }));

		ItineraryDraft draft = this.service.assemble(commandOf("trip_1", places));

		assertThat(draft.items().stream().map(ItineraryDraft.DraftItem::placeId).toList())
				.contains(places.get(0).placeId(), places.get(1).placeId(), places.get(2).placeId());
	}

	/**
	 * 운영의 갈맷길이 이 모양이다 — 「갈맷길」 여러 줄과 「갈맷길 (녹산)」. 견주는 상대는 남긴 곳뿐이라, 1km 안이라 빠진
	 * 「갈맷길」이 3km 떨어진 「갈맷길」까지 끌고 가지 않는다.
	 */
	@Test
	@DisplayName("빠진 곳은 다른 곳을 끌고 가지 않는다 — 견주는 상대는 남긴 곳뿐")
	void droppedParenVariantDoesNotDragAFarSameName() {
		Trip trip = tripOf(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 12));
		when(this.tripRepository.findById("trip_1")).thenReturn(Optional.of(trip));
		List<ItineraryDraftCommand.PlannedPlace> places = plannedPlacesNamedAt(
				List.of("갈맷길 (녹산)", "해운대해수욕장", "갈맷길", "갈맷길"),
				List.of(new double[] { 35.1532, 129.1187 }, new double[] { 35.1587, 129.1604 },
						new double[] { 35.1559, 129.1187 }, new double[] { 35.1800, 129.1187 }));

		ItineraryDraft draft = this.service.assemble(commandOf("trip_1", places));

		List<UUID> placed = draft.items().stream().map(ItineraryDraft.DraftItem::placeId).toList();
		assertThat(placed)
				.contains(places.get(0).placeId(), places.get(1).placeId(), places.get(3).placeId())
				.as("300m 안의 「갈맷길」만 빠진다")
				.doesNotContain(places.get(2).placeId());
	}

	@Test
	@DisplayName("좌표를 모르면 괄호 안만 다른 이름이라도 빼지 않는다 — 1km 안인지 모른다")
	void parenVariantWithoutCoordinatesStays() {
		Trip trip = tripOf(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 12));
		when(this.tripRepository.findById("trip_1")).thenReturn(Optional.of(trip));
		// 앞 곳은 좌표가 있고 뒤 곳만 모른다 — 모르는 쪽을 「가깝다」로 치면 빠진다.
		List<ItineraryDraftCommand.PlannedPlace> places = plannedPlacesNamedAt(
				List.of("몰운대", "해운대해수욕장", "몰운대(부산)"),
				Arrays.asList(new double[] { 35.1532, 129.1187 }, new double[] { 35.1587, 129.1604 }, null));

		ItineraryDraft draft = this.service.assemble(commandOf("trip_1", places));

		assertThat(draft.items().stream().map(ItineraryDraft.DraftItem::placeId).toList())
				.contains(places.get(0).placeId(), places.get(1).placeId(), places.get(2).placeId());
	}

	/** 이름과 좌표를 붙인 후보. 갈래는 비워 둔다 — 끼니 상한과 섞이지 않게. 좌표가 null 이면 모르는 곳이다. */
	private List<ItineraryDraftCommand.PlannedPlace> plannedPlacesNamedAt(List<String> names,
			List<double[]> coordinates) {
		List<ItineraryDraftCommand.PlannedPlace> places = new ArrayList<>();
		List<com.gabolle.backend.place.domain.Place> rows = new ArrayList<>();
		for (int i = 0; i < names.size(); i++) {
			UUID placeId = UUID.randomUUID();
			double[] at = coordinates.get(i);
			if (at != null) {
				this.coordinateByPlaceId.put(placeId, at);
			}
			places.add(new ItineraryDraftCommand.PlannedPlace(placeId, i + 1, List.of("REASON"), List.of(), null));
			rows.add(com.gabolle.backend.place.domain.Place.imported(placeId, names.get(i), null, "부산",
					at == null ? null : at[0], at == null ? null : at[1], "TEST", "test-" + i, null, null, "v1"));
		}
		when(this.placeRepository.findByPlaceIdIn(anyCollection())).thenReturn(rows);
		return places;
	}

	/** 이름을 붙인 후보. 갈래·좌표는 비워 둔다 — 끼니 상한·지역 가르기와 섞이지 않게. */
	private List<ItineraryDraftCommand.PlannedPlace> plannedPlacesNamed(List<String> names) {
		List<ItineraryDraftCommand.PlannedPlace> places = new ArrayList<>();
		List<com.gabolle.backend.place.domain.Place> rows = new ArrayList<>();
		for (int i = 0; i < names.size(); i++) {
			UUID placeId = UUID.randomUUID();
			places.add(new ItineraryDraftCommand.PlannedPlace(placeId, i + 1, List.of("REASON"), List.of(), null));
			rows.add(com.gabolle.backend.place.domain.Place.imported(placeId, names.get(i), null, "부산", null, null,
					"TEST", "test-" + i, null, null, "v1"));
		}
		when(this.placeRepository.findByPlaceIdIn(anyCollection())).thenReturn(rows);
		return places;
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
	@DisplayName("🔴 S15P21E201-1565 — 마지막 방문지는 돌아갈 시간만큼 일찍 끝난다(당일치기 = 출발지로)")
	void lastStopLeavesRoomToGoBack() {
		LocalDate day = LocalDate.of(2026, 9, 10);
		Trip trip = new Trip("trip_1", "usr_1", day, day, 35.1152, 129.0422, null, 2, "09:00-18:00", "Asia/Seoul",
				new String[] { "BUS" }, LocalTime.of(9, 0), LocalTime.of(18, 0), Instant.now());
		when(this.tripRepository.findById("trip_1")).thenReturn(Optional.of(trip));
		// 방문지에 좌표가 있어야 돌아가는 길을 잰다.
		when(this.placeRepository.findById(org.mockito.ArgumentMatchers.any(UUID.class))).thenAnswer((call) ->
				Optional.of(Place.imported(call.getArgument(0), "장소", "CAFE_HEALING", null, 35.16, 129.16,
						"FIXTURE", call.getArgument(0).toString(), null, null, "v1")));
		TravelTimePort port = (fromLat, fromLng, toLat, toLng, mode) ->
				new TravelTime(5000, 25, ItineraryItem.DataStatus.ESTIMATED);
		@SuppressWarnings("unchecked")
		ObjectProvider<TravelTimePort> provider = mock(ObjectProvider.class);
		when(provider.getIfAvailable()).thenReturn(port);
		ItineraryDraftService withTravelTime = new ItineraryDraftService(this.tripRepository,
				mock(ItineraryRepository.class), CLOCK, 4, 3, "FOOD", 1,
				new ItineraryLegPlanner(this.placeRepository, provider), tables(ALWAYS_UNKNOWN,
				ALWAYS_UNKNOWN_TIME_FACT), noRouteOrder(), this.placeRepository, noEvents());

		ItineraryDraft draft = withTravelTime.assemble(commandOf("trip_1", plannedPlaces(3)));

		LocalTime lastEnd = draft.items().get(draft.items().size() - 1).endTime();
		assertThat(lastEnd).as("18:00 에서 돌아가는 25분을 먼저 뗀다 — 나눗셈 나머지만큼 더 이를 수 있다")
				.isBetween(LocalTime.of(17, 30), LocalTime.of(17, 35));
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
				mock(ItineraryRepository.class), CLOCK, 4, 3, "FOOD", 1, legPlanner, tables(ALWAYS_UNKNOWN,
				ALWAYS_UNKNOWN_TIME_FACT), noRouteOrder(), this.placeRepository, noEvents());

		ItineraryDraft draft = withTravelTime.assemble(commandOf("trip_1", plannedPlaces(3)));

		assertThat(draft.legs()).isNotEmpty();
		assertThat(draft.legs()).allSatisfy((leg) -> {
			assertThat(leg.durationMin()).as("이동시간이 비어 있으면 화면이 그 사이를 말할 수 없다").isEqualTo(25);
			assertThat(leg.distanceM()).isEqualTo(1234);
			assertThat(leg.dataStatus()).isEqualTo(ItineraryItem.DataStatus.VERIFIED);
		});
	}

	@Test
	@DisplayName("🔴 S15P21E201-1498 — 일정을 처음 만들 때 구간 요금이 저장까지 간다")
	void legFareSurvivesPersist() {
		// 이 시험이 생긴 이유. 요금은 여기까지 제대로 실려 왔다가 persist 가 ItineraryLeg 로
		// 옮길 때 통째로 버려졌다. 운영 실측(2026-09-22)으로 자동차 구간 33건이 전부
		// data_status=VERIFIED 인데 요금은 0건이었다 — 업체가 답을 준 구간인데도 그랬다.
		// 그래서 초안(draft)이 아니라 «저장소에 넘어간 것» 을 본다. 초안만 보면 이 버그를
		// 다시 놓친다.
		Trip trip = tripOf(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 10));
		when(this.tripRepository.findById("trip_1")).thenReturn(Optional.of(trip));

		TravelTimePort port = (fromLat, fromLng, toLat, toLng, mode) ->
				new TravelTime(1234, 25, ItineraryItem.DataStatus.VERIFIED, 12_800);
		@SuppressWarnings("unchecked")
		ObjectProvider<TravelTimePort> provider = mock(ObjectProvider.class);
		when(provider.getIfAvailable()).thenReturn(port);

		ItineraryRepository repository = mock(ItineraryRepository.class);
		ItineraryDraftService service = new ItineraryDraftService(this.tripRepository, repository, CLOCK,
				4, 3, "FOOD", 1, new ItineraryLegPlanner(this.placeRepository, provider), tables(ALWAYS_UNKNOWN,
				ALWAYS_UNKNOWN_TIME_FACT), noRouteOrder(), this.placeRepository, noEvents());

		service.persist(service.assemble(commandOf("trip_1", plannedPlaces(3))));

		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<ItineraryLeg>> savedLegs = ArgumentCaptor.forClass(List.class);
		verify(repository).create(any(), any(), any(), savedLegs.capture());

		assertThat(savedLegs.getValue()).isNotEmpty();
		assertThat(savedLegs.getValue()).allSatisfy((leg) -> assertThat(leg.fareKrw())
				.as("요금을 받아 놓고 저장에서 흘리면 화면이 비용 줄을 아예 안 그린다")
				.isEqualTo(12_800));
	}

	/**
	 * 실제 이동으로 고친 배율(S15P21E201-1700) — 저장·화면·시각 깔기는 고친 값을 쓰고, 옆 칸에 엔진의 어림을 남긴다. 어림을
	 * 흘리면 다음 보정 계산이 고친 값을 어림으로 읽어 배율이 겹쳐 곱해진다.
	 */
	@Test
	@DisplayName("🔴 S15P21E201-1700 — 새 일정 저장: 이동 시간은 배율을 곱한 값, 옆 칸에는 엔진의 어림이 남는다")
	void persistKeepsTheUncalibratedTravelTime() {
		Trip trip = tripWithWindow(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 10));
		when(this.tripRepository.findById("trip_1")).thenReturn(Optional.of(trip));
		ItineraryRepository repository = mock(ItineraryRepository.class);
		ItineraryLegPlanner planner = new ItineraryLegPlanner(this.placeRepository, travelMinutes(25));
		planner.setTravelCalibration((mode) -> OptionalDouble.of(1.2));
		ItineraryDraftService service = new ItineraryDraftService(this.tripRepository, repository, CLOCK, 4, 3, "FOOD", 1,
				planner, tables(ALWAYS_UNKNOWN, ALWAYS_UNKNOWN_TIME_FACT), noRouteOrder(), this.placeRepository, noEvents());

		service.persist(service.assemble(commandOf("trip_1", plannedPlaces(3))));

		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<ItineraryLeg>> savedLegs = ArgumentCaptor.forClass(List.class);
		verify(repository).create(any(), any(), any(), savedLegs.capture());
		assertThat(savedLegs.getValue()).isNotEmpty().allSatisfy((leg) -> {
			assertThat(leg.durationMin()).isEqualTo(30);
			assertThat(leg.uncalibratedDurationMin()).isEqualTo(25);
		});
	}

	@Test
	@DisplayName("보정이 꺼져 있으면 두 칸이 같다 — 어림이 곧 이동 시간")
	void withoutCalibrationBothFieldsHoldTheEstimate() {
		Trip trip = tripWithWindow(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 10));
		ItineraryLegPlanner planner = new ItineraryLegPlanner(this.placeRepository, travelMinutes(25));

		assertThat(planner.buildLegs(trip, List.of(List.of(UUID.randomUUID(), UUID.randomUUID())))).allSatisfy((leg) -> {
			assertThat(leg.durationMin()).isEqualTo(25);
			assertThat(leg.uncalibratedDurationMin()).isEqualTo(25);
		});
	}

	@Test
	@DisplayName("배율은 그 수단 것만 곱한다 — 다른 수단의 배율만 있으면 어림 그대로")
	void aMultiplierForAnotherModeIsNotApplied() {
		Trip trip = tripWithWindow(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 10));
		ItineraryLegPlanner planner = new ItineraryLegPlanner(this.placeRepository, travelMinutes(25));
		planner.setTravelCalibration((mode) -> "BUS".equals(mode) ? OptionalDouble.of(2.0) : OptionalDouble.empty());

		// 이 여행은 이동 수단을 안 골라 걷기(WALK)로 짠다.
		assertThat(planner.buildLegs(trip, List.of(List.of(UUID.randomUUID())))).singleElement().satisfies((leg) -> {
			assertThat(leg.travelMode()).isEqualTo("WALK");
			assertThat(leg.durationMin()).isEqualTo(25);
		});
	}

	private static ObjectProvider<TravelTimePort> travelMinutes(int minutes) {
		TravelTimePort port = (fromLat, fromLng, toLat, toLng, mode) ->
				new TravelTime(1000, minutes, ItineraryItem.DataStatus.ESTIMATED);
		@SuppressWarnings("unchecked")
		ObjectProvider<TravelTimePort> provider = mock(ObjectProvider.class);
		when(provider.getIfAvailable()).thenReturn(port);
		return provider;
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

	// ── 디저트 가게는 끼니가 아니다 (S15P21E201-1635) ──────────────────────

	/**
	 * 09:00~18:00 · 하루 4곳이면 끼니 칸은 둘째(점심)·넷째(저녁)다.
	 * 순위 1위가 디저트 표식만 있는 「밥집」(젤라또)이다 —
	 * 전에는 점심 칸에 앉았다. 이제는 카페로 읽혀 진짜 밥집 둘이 끼니 칸을 맡는다.
	 */
	@Test
	@DisplayName("🔴 디저트 표식만 있는 밥집은 끼니로 안 센다 — 점심 칸에 젤라또가 앉던 것")
	void dessertOnlyFoodIsNotAMeal() {
		when(this.tripRepository.findById("trip_1")).thenReturn(Optional.of(
				tripOf(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 10), LocalTime.of(9, 0), LocalTime.of(18, 0))));
		List<ItineraryDraftCommand.PlannedPlace> places = plannedPlacesOf(
				"FOOD", "FOOD", "FOOD", "CULTURE_TEMPLE", "NATURE_WALK");
		UUID gelato = places.get(0).placeId();
		this.service.setDessertOnly(ids -> ids.contains(gelato) ? java.util.Set.of(gelato) : java.util.Set.of());

		ItineraryDraft draft = this.service.assemble(commandOf("trip_1", places));

		List<UUID> atMeals = draft.items().stream()
				.filter(item -> item.sequence() == 2 || item.sequence() == 4)
				.map(ItineraryDraft.DraftItem::placeId)
				.toList();
		assertThat(atMeals).as("끼니 칸은 진짜 밥집 둘").containsExactlyInAnyOrder(places.get(1).placeId(),
				places.get(2).placeId());
	}

	// ── 걷기만 고른 여행의 첫날 (S15P21E201-1634) ──────────────────────────

	private static final double[] BUSAN_STATION = { 35.1152, 129.0403 };

	/**
	 * 운영 여행 54854ec1 모양 — 출발 부산역, 여행 범위 해운대, 이틀 · 하루 4곳. 순위 앞쪽이 해운대(15km) {@code haeundae}곳,
	 * 뒤쪽이 부산역 둘레(1km 안) {@code near}곳이다. 둘레 곳은 엔진이 「범위 밖인데 출발지 둘레라서 들어온 곳」으로 표시해 넘긴다.
	 */
	private List<ItineraryDraftCommand.PlannedPlace> walkOnlyCase(int haeundae, int near, String... modes) {
		when(this.tripRepository.findById("trip_1")).thenReturn(Optional.of(Trip.builder()
				.tripId("trip_1").createdBy("usr_1")
				.startDate(LocalDate.of(2026, 10, 1)).finishDate(LocalDate.of(2026, 10, 2))
				.partySize(2).timezone("Asia/Seoul").travelModes(modes)
				.originLat(BUSAN_STATION[0]).originLng(BUSAN_STATION[1])
				.timeWindowStart(LocalTime.of(9, 0)).timeWindowEnd(LocalTime.of(18, 0))
				.createdAt(Instant.now())
				.build()));
		List<double[]> coords = new ArrayList<>();
		for (int i = 0; i < haeundae; i++) {
			coords.add(new double[] { 35.1587 + i * 0.001, 129.1604 + i * 0.001 });
		}
		for (int i = 0; i < near; i++) {
			coords.add(new double[] { 35.1152 + i * 0.0015, 129.0403 + i * 0.0005 }); // 0.2~1km
		}
		List<ItineraryDraftCommand.PlannedPlace> places = new ArrayList<>(plannedPlacesAt(
				java.util.Collections.nCopies(haeundae + near, "CULTURE_TEMPLE"), coords));
		for (int i = haeundae; i < haeundae + near; i++) {
			ItineraryDraftCommand.PlannedPlace p = places.get(i);
			places.set(i, new ItineraryDraftCommand.PlannedPlace(p.placeId(), p.rank(),
					List.of("REASON", com.gabolle.backend.trip.domain.WalkOnlyFirstDay.REASON_CODE), List.of(),
					p.category()));
		}
		return places;
	}

	private static List<UUID> dayOf(ItineraryDraft draft, int dayIndex) {
		return draft.items().stream().filter(item -> item.dayIndex() == dayIndex)
				.map(ItineraryDraft.DraftItem::placeId).toList();
	}

	@Test
	@DisplayName("🔴 걷기만 고른 여행의 첫날은 출발지에서 걸어서 30분 안의 곳만 — 첫날에 빈자리가 남아도 해운대(15km)를 안 넣는다")
	void walkOnlyFirstDayStaysNearTheOrigin() {
		// 해운대 여섯 중 넷이 둘째 날을 채우고 둘이 남는다. 규칙이 없으면 그 둘이 첫날 빈자리로 간다.
		List<ItineraryDraftCommand.PlannedPlace> places = walkOnlyCase(6, 2, "WALK");

		ItineraryDraft draft = this.service.assemble(commandOf("trip_1", places));

		List<UUID> nearOrigin = places.subList(6, 8).stream().map(ItineraryDraftCommand.PlannedPlace::placeId).toList();
		assertThat(dayOf(draft, 0)).as("첫날은 부산역 둘레뿐").containsExactlyInAnyOrderElementsOf(nearOrigin);
	}

	@Test
	@DisplayName("🔴 범위 밖인데 출발지 둘레라서 들어온 곳은 첫날에만 — 둘째 날은 고른 범위(해운대)")
	void firstDayOnlyPlacesStayOffOtherDays() {
		// 둘레 여섯 중 넷이 첫날을 채우고 둘이 남는다. 규칙이 없으면 그 둘이 둘째 날 빈자리로 간다.
		List<ItineraryDraftCommand.PlannedPlace> places = walkOnlyCase(2, 6, "WALK");

		ItineraryDraft draft = this.service.assemble(commandOf("trip_1", places));

		List<UUID> nearOrigin = places.subList(2, 8).stream().map(ItineraryDraftCommand.PlannedPlace::placeId).toList();
		assertThat(dayOf(draft, 1)).as("둘째 날에 출발지 둘레 곳이 없다").isNotEmpty().noneMatch(nearOrigin::contains);
	}

	@Test
	@DisplayName("걷기만이 아니면 지금처럼 — 첫날도 순위대로 고른 범위에서")
	void otherTripsKeepTheRankOrder() {
		List<ItineraryDraftCommand.PlannedPlace> places = walkOnlyCase(6, 2, "BUS", "SUBWAY");

		ItineraryDraft draft = this.service.assemble(commandOf("trip_1", places));

		assertThat(dayOf(draft, 0)).contains(places.get(0).placeId());
	}

	// ── 그날 남은 곳이 다 닫혔을 때 (S15P21E201-1632) ──────────────────────

	/**
	 * 09:00~11:00 · 하루 4곳 — 끼니 칸이 없어 명소만 앉는다. 후보 다섯 중 넷이 그날 자리를 받고 다섯째가 남는다.
	 * 넷째(D)는 늘 닫혀 있어 마지막 칸(10:30)에서 남은 곳이 D 하나뿐이다.
	 */
	private List<ItineraryDraftCommand.PlannedPlace> closedLastSlotCase(double[] spareAt) {
		when(this.tripRepository.findById("trip_1")).thenReturn(Optional.of(
				tripOf(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 10), LocalTime.of(9, 0), LocalTime.of(11, 0))));
		return plannedPlacesAt(List.of("CULTURE_TEMPLE", "CULTURE_TEMPLE", "CULTURE_TEMPLE", "CULTURE_TEMPLE",
				"CULTURE_TEMPLE"), List.of(new double[] { 35.100, 129.030 }, new double[] { 35.101, 129.031 },
				new double[] { 35.102, 129.032 }, new double[] { 35.103, 129.033 }, spareAt));
	}

	@Test
	@DisplayName("🔴 그날 남은 곳이 그 시각에 다 닫혔으면 안 쓴 후보 중 여는 곳으로 바꾼다 — 경고 없이")
	void aClosedLastPlaceIsSwappedForAnOpenSpare() {
		List<ItineraryDraftCommand.PlannedPlace> places = closedLastSlotCase(new double[] { 35.104, 129.034 });
		UUID closed = places.get(3).placeId();
		ItineraryDraftService service = serviceWith((placeId, at) -> placeId.equals(closed)
				? OpeningHoursFilterPort.Answer.CLOSED
				: OpeningHoursFilterPort.Answer.OPEN);

		ItineraryDraft draft = service.assemble(commandOf("trip_1", places));

		assertThat(draft.items()).extracting(ItineraryDraft.DraftItem::placeId)
				.hasSize(4)
				.contains(places.get(4).placeId())
				.doesNotContain(closed);
		assertThat(draft.items()).allSatisfy(item ->
				assertThat(item.warningCodes()).doesNotContain("OPENING_HOURS_CLOSED"));
	}

	@Test
	@DisplayName("안 쓴 후보도 그 시각에 다 닫혔으면 지금처럼 앉히고 경고를 단다")
	void withoutAnOpenSpareTheClosedPlaceStaysWithAWarning() {
		List<ItineraryDraftCommand.PlannedPlace> places = closedLastSlotCase(new double[] { 35.104, 129.034 });
		UUID closed = places.get(3).placeId();
		UUID spare = places.get(4).placeId();
		ItineraryDraftService service = serviceWith((placeId, at) -> placeId.equals(closed) || placeId.equals(spare)
				? OpeningHoursFilterPort.Answer.CLOSED
				: OpeningHoursFilterPort.Answer.OPEN);

		ItineraryDraft draft = service.assemble(commandOf("trip_1", places));

		assertThat(draft.items()).extracting(ItineraryDraft.DraftItem::placeId).contains(closed).doesNotContain(spare);
		assertThat(draft.items()).filteredOn(item -> item.placeId().equals(closed))
				.allSatisfy(item -> assertThat(item.warningCodes()).contains("OPENING_HOURS_CLOSED"));
	}

	@Test
	@DisplayName("연 후보가 그날 동선에서 8km 넘게 떨어져 있으면 바꾸지 않는다 — 문 연 곳 하나 때문에 도시를 가로지르지 않게")
	void aFarSpareIsNotUsed() {
		List<ItineraryDraftCommand.PlannedPlace> places = closedLastSlotCase(new double[] { 35.180, 129.200 }); // 약 18km
		UUID closed = places.get(3).placeId();
		ItineraryDraftService service = serviceWith((placeId, at) -> placeId.equals(closed)
				? OpeningHoursFilterPort.Answer.CLOSED
				: OpeningHoursFilterPort.Answer.OPEN);

		ItineraryDraft draft = service.assemble(commandOf("trip_1", places));

		assertThat(draft.items()).extracting(ItineraryDraft.DraftItem::placeId)
				.contains(closed)
				.doesNotContain(places.get(4).placeId());
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

	@Test
	@DisplayName("브레이크타임과 라스트오더가 함께 걸리면 브레이크타임 경고 하나만 — 영업시간 → 브레이크타임 → 라스트오더 차례")
	void breakTimeIsReportedBeforeLastOrder() {
		Trip trip = tripWithWindow(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 10));
		when(this.tripRepository.findById("trip_1")).thenReturn(Optional.of(trip));

		ItineraryDraftService service = serviceWith(ALWAYS_UNKNOWN, new PlaceTimeFactFilterPort() {
			@Override
			public OpeningHoursFilterPort.Answer breakTimeAt(UUID placeId, java.time.OffsetDateTime at) {
				return OpeningHoursFilterPort.Answer.CLOSED;
			}

			@Override
			public OpeningHoursFilterPort.Answer lastOrderAt(UUID placeId, java.time.OffsetDateTime at) {
				return OpeningHoursFilterPort.Answer.CLOSED;
			}
		});

		ItineraryDraft draft = service.assemble(commandOf("trip_1", plannedPlaces(2)));

		assertThat(draft.items()).hasSize(2);
		assertThat(draft.items()).allSatisfy((item) -> assertThat(item.warningCodes())
				.contains("BREAK_TIME_CLOSED").doesNotContain("LAST_ORDER_PASSED"));
	}

	private ItineraryDraftService serviceWith(OpeningHoursFilterPort openingHours) {
		return serviceWith(openingHours, ALWAYS_UNKNOWN_TIME_FACT);
	}

	private ItineraryDraftService serviceWith(OpeningHoursFilterPort openingHours, PlaceTimeFactFilterPort timeFact) {
		@SuppressWarnings("unchecked")
		ObjectProvider<TravelTimePort> noTravelTime = mock(ObjectProvider.class);
		when(noTravelTime.getIfAvailable()).thenReturn(null);
		return new ItineraryDraftService(this.tripRepository, mock(ItineraryRepository.class), CLOCK, 4, 3, "FOOD", 1,
				new ItineraryLegPlanner(this.placeRepository, noTravelTime), tables(openingHours, timeFact),
				noRouteOrder(), this.placeRepository, noEvents());
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

	// ── 지역으로 날을 가른다 (S15P21E201-1493) ──────────────────────────────

	/** 영도 — 실제 좌표다. 아래 시험이 운영에서 난 사례를 그대로 옮긴 것이라 값도 그대로 쓴다. */
	private static final double[] YEONGDO_KKOSHONE = { 35.0903, 129.0579 };

	private static final double[] YEONGDO_CHEONGHAK = { 35.0966, 129.0604 };

	private static final double[] YEONGDO_PIYAK = { 35.0864, 129.0767 };

	/** 해운대 — 영도에서 약 17km 다. */
	private static final double[] HAEUNDAE_SHOJIN = { 35.1633, 129.1596 };

	private static final double[] HAEUNDAE_DONGBAEK = { 35.1537, 129.1523 };

	private static final double[] HAEUNDAE_DOPHINE = { 35.1662, 129.1578 };

	private static final double[] HAEUNDAE_HAEPARANG = { 35.1585, 129.1707 };

	private static final double[] HAEUNDAE_GWANGWANG = { 35.1584, 129.1599 };

	private static final double[] YEONGDO_DARIFESTIVAL = { 35.0786, 129.0803 };

	/** 순위 차례로 좌표를 붙인 후보를 만들고, 저장소 목이 그 좌표를 주게 한다. */
	private List<ItineraryDraftCommand.PlannedPlace> plannedPlacesAt(List<String> categories,
			List<double[]> coordinates) {

		List<ItineraryDraftCommand.PlannedPlace> places = new ArrayList<>();
		List<com.gabolle.backend.place.domain.Place> rows = new ArrayList<>();
		for (int i = 0; i < categories.size(); i++) {
			UUID placeId = UUID.randomUUID();
			this.categoryByPlaceId.put(placeId, categories.get(i));
			this.coordinateByPlaceId.put(placeId, coordinates.get(i));
			places.add(new ItineraryDraftCommand.PlannedPlace(placeId, i + 1, List.of("REASON"),
					List.of(), categories.get(i)));
			rows.add(com.gabolle.backend.place.domain.Place.imported(placeId, "장소" + (i + 1),
					categories.get(i), "부산", coordinates.get(i)[0], coordinates.get(i)[1],
					"TEST", "test-" + i, null, null, "v1"));
		}
		when(this.placeRepository.findByPlaceIdIn(anyCollection())).thenReturn(rows);
		return places;
	}

	/**
	 * 🔴 2026-09-22 운영에서 난 사례를 그대로 옮겼다.
	 *
	 * <p>순위가 <b>1위 쇼진(해운대) · 2~4위 영도 셋 · 5~8위 해운대 넷</b> 이었다. 예전 코드는
	 * 「자리 있는 첫 날」에 앉혀서 1일차가 <b>영도·해운대·영도·영도</b> 가 됐고, 그 하루에
	 * 16km 를 갔다 돌아왔다(그 두 구간만 왕복 104분).
	 *
	 * <p>여기서 보는 것은 <b>하루가 한 지역인가</b> 하나다. 정확히 어느 날에 어느 지역이
	 * 가는지는 안 본다 — 그건 순위가 정하는 것이고, 이 시험이 묶어 두면 순위 규칙을 바꿀 때
	 * 이 시험이 엉뚱하게 빨개진다.
	 *
	 * <p>🔴 후보를 지역마다 <b>자리 수만큼</b> 준다(영도 4 · 해운대 4). 그래야 순수한 배정이
	 * 가능하다. 한쪽이 모자라면 코드가 어떻게 해도 섞일 수밖에 없고, 그때는 아래
	 * {@link #warnsWhenARegionCannotBeKeptTogether()} 가 보는 대로 <b>경고를 남긴다.</b>
	 */
	@Test
	@DisplayName("🔴 하루에 한 지역만 담는다 — 영도 셋 사이에 해운대 하나가 끼지 않는다")
	void eachDayStaysInOneRegion() {
		Trip trip = tripOf(LocalDate.of(2026, 9, 22), LocalDate.of(2026, 9, 23));
		when(this.tripRepository.findById("trip_1")).thenReturn(Optional.of(trip));

		ItineraryDraft draft = this.service.assemble(commandOf("trip_1", plannedPlacesAt(
				List.of("FOOD", "CAFE_HEALING", "CITY", "FOOD",
						"CULTURE_TEMPLE", "FOOD", "NATURE_WALK", "CULTURE_TEMPLE"),
				List.of(HAEUNDAE_SHOJIN, YEONGDO_KKOSHONE, YEONGDO_CHEONGHAK, YEONGDO_PIYAK,
						YEONGDO_DARIFESTIVAL, HAEUNDAE_DOPHINE, HAEUNDAE_HAEPARANG, HAEUNDAE_GWANGWANG))));

		Map<Integer, List<double[]>> byDay = new HashMap<>();
		for (ItineraryDraft.DraftItem item : draft.items()) {
			byDay.computeIfAbsent(item.dayIndex(), key -> new ArrayList<>())
					.add(coordinateOf(item.placeId()));
		}
		assertThat(byDay).hasSize(2);

		for (Map.Entry<Integer, List<double[]>> day : byDay.entrySet()) {
			for (double[] a : day.getValue()) {
				for (double[] b : day.getValue()) {
					assertThat(kmBetween(a, b))
							.as("%d일차 안의 두 곳이 8km 넘게 떨어졌다 — 지역이 섞였다", day.getKey())
							.isLessThan(8.0);
				}
			}
		}
	}

	/**
	 * 🔴 S15P21E201-1494 — <b>운영 사례 그대로.</b> 이것이 이 변경의 진짜 증명이다.
	 *
	 * <p>순위 1~8 이 해운대 5 · 영도 3 이라 그 여덟으로 8자리를 채우면 <b>반드시 섞인다.</b>
	 * 여벌(9~11위)에 영도가 하나 있는데, 예전 코드는 순위대로 무조건 앉혀서 8자리가 1~8로
	 * 다 차고 <b>11위의 차례가 오지 않았다.</b>
	 *
	 * <p>두 번 훑기가 그것을 고친다. 첫 훑기가 <b>지역이 맞는 것만</b> 앉히므로 8위 해운대는
	 * 영도 날에 못 들어가고 미뤄지며, 11위 영도가 그 자리를 얻는다.
	 */
	@Test
	@DisplayName("🔴 지역이 맞는 후보가 뒤에 있으면 순위를 건너뛴다 — 8위 해운대 대신 11위 영도")
	void skipsAheadToACandidateThatFitsTheRegion() {
		Trip trip = tripOf(LocalDate.of(2026, 9, 22), LocalDate.of(2026, 9, 23));
		when(this.tripRepository.findById("trip_1")).thenReturn(Optional.of(trip));

		// 순위 1~8 은 운영과 같다(해운대 5 · 영도 3). 9~11 이 여벌이고 11위만 영도다.
		ItineraryDraft draft = this.service.assemble(commandOf("trip_1", plannedPlacesAt(
				List.of("FOOD", "CAFE_HEALING", "CITY", "FOOD", "CULTURE_TEMPLE", "FOOD",
						"NATURE_WALK", "CULTURE_TEMPLE", "CITY", "CULTURE_TEMPLE", "CULTURE_TEMPLE"),
				List.of(HAEUNDAE_SHOJIN, YEONGDO_KKOSHONE, YEONGDO_CHEONGHAK, YEONGDO_PIYAK,
						HAEUNDAE_DONGBAEK, HAEUNDAE_DOPHINE, HAEUNDAE_HAEPARANG, HAEUNDAE_GWANGWANG,
						HAEUNDAE_SHOJIN, HAEUNDAE_DONGBAEK, YEONGDO_DARIFESTIVAL))));

		Map<Integer, List<double[]>> byDay = new HashMap<>();
		for (ItineraryDraft.DraftItem item : draft.items()) {
			byDay.computeIfAbsent(item.dayIndex(), key -> new ArrayList<>())
					.add(coordinateOf(item.placeId()));
		}

		for (Map.Entry<Integer, List<double[]>> day : byDay.entrySet()) {
			for (double[] a : day.getValue()) {
				for (double[] b : day.getValue()) {
					assertThat(kmBetween(a, b))
							.as("%d일차가 섞였다 — 여벌에 그 지역 후보가 있는데 안 썼다", day.getKey())
							.isLessThan(8.0);
				}
			}
		}
		assertThat(draft.warningCodes())
				.as("바꿔 넣었으니 경고가 없어야 한다")
				.doesNotContain(com.gabolle.backend.itinerary.domain.ItineraryWarningCodes.DAY_REGION_MIXED);
	}

	@Test
	@DisplayName("🔴 지역이 섞일 수밖에 없으면 조용히 두지 않고 경고를 남긴다")
	void warnsWhenARegionCannotBeKeptTogether() {
		// 하루 4곳인데 한 지역에 3곳뿐이다 — 넷째 자리는 먼 곳으로 채울 수밖에 없다.
		Trip trip = tripOf(LocalDate.of(2026, 9, 22), LocalDate.of(2026, 9, 22));
		when(this.tripRepository.findById("trip_1")).thenReturn(Optional.of(trip));

		ItineraryDraft draft = this.service.assemble(commandOf("trip_1", plannedPlacesAt(
				List.of("CAFE_HEALING", "CITY", "FOOD", "CULTURE_TEMPLE"),
				List.of(YEONGDO_KKOSHONE, YEONGDO_CHEONGHAK, YEONGDO_PIYAK, HAEUNDAE_SHOJIN))));

		assertThat(draft.warningCodes())
				.as("바꿀 수 없으면 바꾸지 않고 말한다")
				.contains(com.gabolle.backend.itinerary.domain.ItineraryWarningCodes.DAY_REGION_MIXED);
	}

	private final Map<UUID, double[]> coordinateByPlaceId = new HashMap<>();

	private double[] coordinateOf(UUID placeId) {
		return this.coordinateByPlaceId.get(placeId);
	}

	/** 시험이 쓰는 거리. 본 코드와 같은 식이라야 같은 잣대로 본다. */
	private static double kmBetween(double[] a, double[] b) {
		double earthRadiusKm = 6371.0;
		double dLat = Math.toRadians(b[0] - a[0]);
		double dLng = Math.toRadians(b[1] - a[1]);
		double s = Math.sin(dLat / 2) * Math.sin(dLat / 2)
				+ Math.cos(Math.toRadians(a[0])) * Math.cos(Math.toRadians(b[0]))
						* Math.sin(dLng / 2) * Math.sin(dLng / 2);
		return 2 * earthRadiusKm * Math.asin(Math.min(1.0, Math.sqrt(s)));
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
	@DisplayName("🔴 S15P21E201-1130 — 시각이 이동 시간을 비켜 간다: 곳마다 갈래만큼 머물고 사이에 이동 시간과 빈 시각")
	void stayTimeMakesRoomForTravelBetweenPlaces() {
		Trip trip = tripWithWindow(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 10));
		when(this.tripRepository.findById("trip_1")).thenReturn(Optional.of(trip));

		// 구간마다 25분. 장소 3곳이면 구간도 3개다 — 출발지에서 첫 장소로 가는 길이 첫 구간이다.
		ItineraryDraft draft = serviceWithTravelMinutes(25)
				.assemble(commandOf("trip_1", plannedPlaces(3)));

		List<ItineraryDraft.DraftItem> items = draft.items();
		assertThat(items).hasSize(3);

		// 갈래를 모르는 곳은 60분 머문다(S15P21E201-1667). 480분 - 이동 75분 - 체류 180분 = 빈 시각 225분.
		assertThat(items).allSatisfy((item) -> assertThat(item.stayMinutes()).isEqualTo(60));

		// 첫 장소도 09:00 에 시작하지 않는다 — 출발지에서 거기까지 25분이 걸린다.
		assertThat(items.get(0).startTime()).isEqualTo(LocalTime.of(9, 25));

		// 항목과 항목 사이는 이동 25분 + 빈 시각. 빈 시각 225분을 사이 둘에 112·113분.
		assertThat(Duration.between(items.get(0).endTime(), items.get(1).startTime()).toMinutes())
				.as("앞 장소가 끝난 뒤 다음 장소가 시작하기까지 이동할 시간이 있어야 한다")
				.isEqualTo(25 + 112);
		assertThat(Duration.between(items.get(1).endTime(), items.get(2).startTime()).toMinutes()).isEqualTo(25 + 113);

		// 그러고도 활동 시간대를 넘지 않는다.
		assertThat(items.get(items.size() - 1).endTime()).isEqualTo(LocalTime.of(17, 0));
	}

	@Test
	@DisplayName("🔴 S15P21E201-1692 — 실제 체류로 고친 값이 있는 갈래는 그 값, 없는 갈래는 기본값")
	void calibratedStayMinutesWinOverDefaults() {
		Trip trip = tripWithWindow(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 10));
		when(this.tripRepository.findById("trip_1")).thenReturn(Optional.of(trip));
		this.service.setStayCalibration((category) -> "CULTURE_TEMPLE".equals(category)
				? OptionalInt.of(75) : OptionalInt.empty());

		ItineraryDraft draft = this.service.assemble(commandOf("trip_1", plannedPlacesOf("CULTURE_TEMPLE", "NATURE_WALK")));

		assertThat(draft.items()).extracting((item) -> categoryOfItem(item) + ":" + item.stayMinutes())
				.containsExactlyInAnyOrder("CULTURE_TEMPLE:75", "NATURE_WALK:60");
	}

	@Test
	@DisplayName("🔴 이동 시간을 모르면 0분으로 본다 — 모르는 값을 지어내지 않는다")
	void withoutMeasuredTravelTheLayoutIsUnchanged() {
		Trip trip = tripWithWindow(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 10));
		when(this.tripRepository.findById("trip_1")).thenReturn(Optional.of(trip));

		// this.service 는 이동시간 포트가 없는 갈래다 — durationMin 이 null 이다.
		ItineraryDraft draft = this.service.assemble(commandOf("trip_1", plannedPlaces(3)));

		// 곳마다 60분, 남는 300분은 사이 둘에 150분씩.
		assertThat(draft.items()).allSatisfy((item) -> assertThat(item.stayMinutes()).isEqualTo(60));
		assertThat(draft.items()).extracting(ItineraryDraft.DraftItem::startTime)
				.containsExactly(LocalTime.of(9, 0), LocalTime.of(12, 30), LocalTime.of(16, 0));
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
				4, 3, "FOOD", 1, new ItineraryLegPlanner(this.placeRepository, provider),
				tables(ALWAYS_UNKNOWN, ALWAYS_UNKNOWN_TIME_FACT), noRouteOrder(), this.placeRepository, noEvents());
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

	/**
	 * 동선 최적화가 붙어 있지 않은 판. 이 파일의 다른 시험들이 전부 이 상태이고, 그때 차례는
	 * 순위 그대로다 — 최적화가 없어도 일정이 오늘처럼 나온다는 것을 그 시험들이 같이 지킨다.
	 */
	private static ObjectProvider<RouteOrderPort> noRouteOrder() {
		@SuppressWarnings("unchecked")
		ObjectProvider<RouteOrderPort> provider = mock(ObjectProvider.class);
		when(provider.getIfAvailable()).thenReturn(null);
		return provider;
	}

	/** 동선 최적화가 정해진 답을 내는 판. 파이썬을 부르지 않는다 — 배선만 본다. */
	private ItineraryDraftService serviceWithRouteOrder(RouteOrderPort routeOrder) {
		@SuppressWarnings("unchecked")
		ObjectProvider<RouteOrderPort> provider = mock(ObjectProvider.class);
		when(provider.getIfAvailable()).thenReturn(routeOrder);
		@SuppressWarnings("unchecked")
		ObjectProvider<TravelTimePort> noTravelTime = mock(ObjectProvider.class);
		when(noTravelTime.getIfAvailable()).thenReturn(null);
		return new ItineraryDraftService(this.tripRepository, mock(ItineraryRepository.class), CLOCK,
				4, 3, "FOOD", 1, new ItineraryLegPlanner(this.placeRepository, noTravelTime),
				tables(ALWAYS_UNKNOWN, ALWAYS_UNKNOWN_TIME_FACT), provider, this.placeRepository, noEvents());
	}

	private static List<UUID> placeIdsOf(List<ItineraryDraftCommand.PlannedPlace> places) {
		return places.stream().map(ItineraryDraftCommand.PlannedPlace::placeId).collect(Collectors.toList());
	}

	private static List<UUID> placeIdsOfItems(ItineraryDraft draft) {
		return draft.items().stream().map(ItineraryDraft.DraftItem::placeId).collect(Collectors.toList());
	}

	@Test
	@DisplayName("동선 최적화가 낸 차례대로 일정에 앉는다 — 추천 순위 차례가 아니라")
	void seatsInRouteOrderNotRankOrder() {
		Trip trip = tripOf(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 10));
		when(this.tripRepository.findById("itn_trip_1")).thenReturn(Optional.of(trip));

		List<ItineraryDraftCommand.PlannedPlace> places = plannedPlaces(4);
		List<UUID> rankOrder = placeIdsOf(places);
		List<UUID> routeOrder = new ArrayList<>(rankOrder);
		Collections.reverse(routeOrder);

		ItineraryDraftService service = serviceWithRouteOrder(request -> routeOrder);

		ItineraryDraft draft = service.assemble(commandOf("itn_trip_1", places));

		assertThat(placeIdsOfItems(draft))
				.as("최적화가 낸 차례가 그대로 일정의 차례가 되어야 한다")
				.containsExactlyElementsOf(routeOrder);
	}

	@Test
	@DisplayName("최적화가 답을 못 내면 순위 차례 그대로 간다 — 일정이 멈추지 않는다")
	void keepsRankOrderWhenRouteOrderIsEmpty() {
		Trip trip = tripOf(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 10));
		when(this.tripRepository.findById("itn_trip_1")).thenReturn(Optional.of(trip));

		List<ItineraryDraftCommand.PlannedPlace> places = plannedPlaces(4);
		List<UUID> rankOrder = placeIdsOf(places);

		ItineraryDraftService service = serviceWithRouteOrder(request -> List.of());

		ItineraryDraft draft = service.assemble(commandOf("itn_trip_1", places));

		assertThat(placeIdsOfItems(draft)).containsExactlyElementsOf(rankOrder);
	}

	@Test
	@DisplayName("최적화가 받은 적 없는 장소를 돌려주면 통째로 버린다 — 한 톨이라도 어긋나면 순위 차례다")
	void keepsRankOrderWhenRouteOrderReturnsUnknownPlace() {
		Trip trip = tripOf(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 10));
		when(this.tripRepository.findById("itn_trip_1")).thenReturn(Optional.of(trip));

		List<ItineraryDraftCommand.PlannedPlace> places = plannedPlaces(4);
		List<UUID> rankOrder = placeIdsOf(places);
		// 수는 맞는데 마지막 하나가 우리가 준 적 없는 장소다.
		List<UUID> broken = new ArrayList<>(rankOrder.subList(0, 3));
		broken.add(UUID.randomUUID());

		ItineraryDraftService service = serviceWithRouteOrder(request -> broken);

		ItineraryDraft draft = service.assemble(commandOf("itn_trip_1", places));

		assertThat(placeIdsOfItems(draft))
				.as("차례만 바꾼다는 약속이 깨진 답은 쓰지 않는다")
				.containsExactlyElementsOf(rankOrder);
	}

	@Test
	@DisplayName("여행이 고른 이동수단이 최적화에 그대로 간다 — 차례를 정한 잣대와 구간을 잰 잣대가 같아야 한다")
	void passesTripTravelModeToRouteOrder() {
		Trip trip = tripOf(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 10));
		when(this.tripRepository.findById("itn_trip_1")).thenReturn(Optional.of(trip));

		List<ItineraryDraftCommand.PlannedPlace> places = plannedPlaces(3);
		List<String> seen = new ArrayList<>();

		ItineraryDraftService service = serviceWithRouteOrder(request -> {
			seen.add(request.travelMode());
			return List.of();
		});
		service.assemble(commandOf("itn_trip_1", places));

		assertThat(seen).as("이동수단을 안 고른 여행은 WALK 로 떨어진다 — ItineraryLegPlanner 와 같은 규칙")
				.containsExactly("WALK");
	}

	// ── 카페는 하루 한 곳 (S15P21E201-1573) ─────────────────────────────

	@Test
	@DisplayName("🔴 S15P21E201-1573 — 카페는 하루 한 곳, 남는 자리는 명소가 먼저 — 카테고리를 안 골라도 자연·문화가 낀다")
	void oneCafePerDayLeavesRoomForSights() {
		Trip trip = tripOf(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 10), LocalTime.of(9, 0), LocalTime.of(18, 0));
		when(this.tripRepository.findById("trip_1")).thenReturn(Optional.of(trip));

		// 운영 후보 모양 — 순위 위쪽에 카페가 몰려 있고 명소는 그 뒤다.
		ItineraryDraft draft = this.service.assemble(commandOf("trip_1", plannedPlacesOf(
				"CAFE_HEALING", "CAFE_HEALING", "CAFE_HEALING", "CULTURE_TEMPLE", "NATURE_WALK", "FOOD", "FOOD")));

		List<String> categories = draft.items().stream().map(this::categoryOfItem).toList();
		assertThat(categories.stream().filter("CAFE_HEALING"::equals).count()).as("카페는 하루 한 곳").isEqualTo(1);
		assertThat(categories).contains("CULTURE_TEMPLE", "NATURE_WALK");
	}

	@Test
	@DisplayName("명소가 없으면 카페로 빈 자리를 채운다 — 상한 때문에 하루를 비우지 않는다")
	void cafesFillTheDayWhenThereAreNoSights() {
		Trip trip = tripOf(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 10), LocalTime.of(9, 0), LocalTime.of(18, 0));
		when(this.tripRepository.findById("trip_1")).thenReturn(Optional.of(trip));

		ItineraryDraft draft = this.service.assemble(commandOf("trip_1", plannedPlacesOf(
				"CAFE_HEALING", "CAFE_HEALING", "CAFE_HEALING", "CAFE_HEALING")));

		assertThat(draft.items()).hasSize(4);
	}

	// ── 예산 상한 (S15P21E201-1572) ─────────────────────────────────────

	private Trip tripWithBudget(Integer budgetKrw, int partySize) {
		return Trip.builder()
				.tripId("itn_trip_1").createdBy("usr_1")
				.startDate(LocalDate.of(2026, 9, 10)).finishDate(LocalDate.of(2026, 9, 10))
				.budgetKrw(budgetKrw).partySize(partySize).timezone("Asia/Seoul")
				.createdAt(Instant.now())
				.build();
	}

	/**
	 * 순위 1~5 의 명소. 1인분 가격은 A 9만 · B 5만 · C 1만 · D 모름 · E 5천.
	 * 아래 1인 시험들의 셈은 인원 1명이라 1인분 값이 곧 비용이다(S15P21E201-1579 이후 비용 = 1인분 × 인원).
	 */
	private List<UUID> placeBudgetCase(ItineraryDraftService service, Integer budgetKrw) {
		return placeBudgetCase(service, budgetKrw, 1);
	}

	private List<UUID> placeBudgetCase(ItineraryDraftService service, Integer budgetKrw, int partySize) {
		when(this.tripRepository.findById("itn_trip_1"))
				.thenReturn(Optional.of(tripWithBudget(budgetKrw, partySize)));
		List<ItineraryDraftCommand.PlannedPlace> places = plannedPlacesOf("CITY", "CITY", "CITY", "CITY", "CITY");
		Map<UUID, Integer> prices = new HashMap<>();
		prices.put(places.get(0).placeId(), 90_000);
		prices.put(places.get(1).placeId(), 50_000);
		prices.put(places.get(2).placeId(), 10_000);
		prices.put(places.get(4).placeId(), 5_000);
		service.setMenuPrice(ids -> prices);
		this.budgetCase = places;
		return placeIdsOfItems(service.assemble(commandOf("itn_trip_1", places)));
	}

	private List<ItineraryDraftCommand.PlannedPlace> budgetCase;

	@Test
	@DisplayName("🔴 S15P21E201-1572 — 합계가 예산의 120% 를 넘게 만드는 곳은 건너뛰고 다음 후보가 앉는다")
	void budgetCapSkipsThePlaceThatWouldBreakIt() {
		// 예산 10만 → 상한 12만. A(9만) 앉고, B(5만)면 14만이라 건너뛴다. C(1만)·D(모름)·E(5천)가 채운다 = 10.5만.
		List<UUID> placed = placeBudgetCase(this.service, 100_000);

		assertThat(placed).containsExactlyInAnyOrder(this.budgetCase.get(0).placeId(), this.budgetCase.get(2).placeId(),
				this.budgetCase.get(3).placeId(), this.budgetCase.get(4).placeId());
	}

	@Test
	@DisplayName("값을 모르는 곳은 막지 않는다 — 합계에서 빠지는 것과 같은 규칙(0 원으로 세지 않는다)")
	void unknownPricesAreNotBlocked() {
		List<UUID> placed = placeBudgetCase(this.service, 100_000);

		assertThat(placed).contains(this.budgetCase.get(3).placeId());
	}

	@Test
	@DisplayName("예산이 없으면 상한도 없다 — 순위 1~4 그대로")
	void noBudgetMeansNoCap() {
		List<UUID> placed = placeBudgetCase(this.service, null);

		assertThat(placed).containsExactlyInAnyOrder(this.budgetCase.get(0).placeId(), this.budgetCase.get(1).placeId(),
				this.budgetCase.get(2).placeId(), this.budgetCase.get(3).placeId());
	}

	/**
	 * 메뉴 값은 한 그릇이고 예산은 여행 전체 총액이다. 곱하지 않으면 3명 여행에서 실제 식비의 1/3 만 세어,
	 * 1명일 때와 같은 곳이 앉는다 — 상한이 사실상 안 걸린다.
	 */
	@Test
	@DisplayName("🔴 S15P21E201-1579 — 같은 예산이라도 3명이면 1인분 값 × 3 으로 센다")
	void budgetCapCountsEveryoneInTheParty() {
		// 예산 10만 → 상한 12만. 3명: A 27만·B 15만은 혼자서 넘는다. C 3만 + D(모름) + E 1.5만 = 4.5만.
		List<UUID> forThree = placeBudgetCase(this.service, 100_000, 3);

		assertThat(forThree).containsExactlyInAnyOrder(this.budgetCase.get(2).placeId(),
				this.budgetCase.get(3).placeId(), this.budgetCase.get(4).placeId());

		// 같은 예산·같은 가격인데 1명이면 A(9만)가 앉는다 — 인원만 다르다.
		assertThat(placeBudgetCase(this.service, 100_000, 1)).contains(this.budgetCase.get(0).placeId());
	}

	/** 「여행 기분」을 고른 여행. 나머지 조건은 {@link #tripOf} 와 같다. */
	private Trip tripWithPace(String pace) {
		return Trip.builder()
				.tripId("itn_trip_1").createdBy("usr_1")
				.startDate(LocalDate.of(2026, 9, 10)).finishDate(LocalDate.of(2026, 9, 10))
				.partySize(2).timezone("Asia/Seoul").pace(pace)
				.createdAt(Instant.now())
				.build();
	}

	private int placedCountFor(String pace, int candidates) {
		Trip trip = tripWithPace(pace);
		when(this.tripRepository.findById("itn_trip_1")).thenReturn(Optional.of(trip));
		return this.service.assemble(commandOf("itn_trip_1", plannedPlaces(candidates))).items().size();
	}

	@Test
	@DisplayName("🔴 「여행 기분」이 하루 곳 수를 정한다 — 화면은 「하루 2–3곳」이라 약속하는데 서버는 전원 4곳이었다")
	void paceDecidesHowManyPlacesPerDay() {
		// 후보는 넉넉히 같은 수로 주고 고른 값만 바꾼다. 달라지는 것이 기분뿐이어야 뜻이 있다.
		assertThat(placedCountFor("RELAXED", 8)).as("여유롭게 — 하루 2–3곳").isEqualTo(3);
		assertThat(placedCountFor("BALANCED", 8)).as("균형 있게 — 하루 3–4곳").isEqualTo(4);
		assertThat(placedCountFor("PACKED", 8)).as("알차게 — 하루 5곳 이상").isEqualTo(5);
	}

	@Test
	@DisplayName("기분을 안 고른 여행은 지금까지처럼 설정 기본값을 쓴다 — null 은 「보통」이 아니라 「모른다」다")
	void unsetPaceKeepsTheConfiguredDefault() {
		assertThat(placedCountFor(null, 8))
				.as("이 시험이 만든 서비스의 설정값은 4다")
				.isEqualTo(4);
	}

	@Test
	@DisplayName("후보가 모자라면 기분이 정한 수보다 적게 들어간다 — 없는 곳을 지어내지 않는다")
	void fewerCandidatesThanThePaceAsksFor() {
		assertThat(placedCountFor("PACKED", 2)).isEqualTo(2);
	}

	// ── 필요한 자리 수 (S15P21E201-1450) ──────────────────────────────────

	@Test
	@DisplayName("🔴 필요한 자리 수는 날 수 × 하루 곳 수다 — 응답 개수(기본 10)로 어림해 3일 여행이 두 자리 모자랐다")
	void placesNeededCountsEverySeat() {
		Trip threeDays = Trip.builder()
				.tripId("itn_trip_3d").createdBy("usr_1")
				.startDate(LocalDate.of(2026, 9, 10)).finishDate(LocalDate.of(2026, 9, 12))
				.partySize(2).timezone("Asia/Seoul")
				.createdAt(Instant.now())
				.build();
		when(this.tripRepository.findById("itn_trip_3d")).thenReturn(Optional.of(threeDays));

		// 기분을 안 골랐으니 하루 4곳(이 시험이 만든 서비스의 설정값) × 3일.
		// 이 시험의 서비스는 여벌 배수가 1 이라 자리 수와 같다.
		assertThat(this.service.placesNeeded("itn_trip_3d"))
				.as("추천이 10개만 주면 이 여행은 두 자리를 못 채운다")
				.isEqualTo(12);
	}

	/**
	 * 🔴 S15P21E201-1494 — 자리 수만큼만 받으면 <b>고를 여지가 없다.</b>
	 *
	 * <p>2026-09-22 운영 사례: 2일 × 4곳 = 8자리에 여벌이 <b>둘</b> 있었는데 그 9·10위가
	 * <b>둘 다 해운대</b>였다. 영도는 11위가 처음이라 영도 날을 채울 것이 없었다. 여벌이
	 * 아주 없었던 것이 아니라 <b>여벌이 한 지역에 몰려 있었다</b> — 그래서 배수가 필요하다.
	 */
	@Test
	@DisplayName("🔴 여벌 배수만큼 더 받는다 — 자리 수와 후보 수가 같으면 바꿔 넣을 것이 없다")
	void placesNeededAsksForHeadroom() {
		Trip twoDays = Trip.builder()
				.tripId("itn_trip_2d").createdBy("usr_1")
				.startDate(LocalDate.of(2026, 9, 22)).finishDate(LocalDate.of(2026, 9, 23))
				.partySize(2).timezone("Asia/Seoul")
				.createdAt(Instant.now())
				.build();
		when(this.tripRepository.findById("itn_trip_2d")).thenReturn(Optional.of(twoDays));

		// 자리는 2일 × 4곳 = 8. 배수 2 면 16 을 받아야 한다 — 운영 사례에서 영도가 처음
		// 나오는 11위가 그 안에 들어온다.
		ItineraryDraftService withHeadroom = new ItineraryDraftService(this.tripRepository,
				mock(ItineraryRepository.class), CLOCK, 4, 3, "FOOD", 2,
				new ItineraryLegPlanner(this.placeRepository, noTravelTimeProvider()),
				tables(ALWAYS_UNKNOWN, ALWAYS_UNKNOWN_TIME_FACT), noRouteOrder(), this.placeRepository, noEvents());

		assertThat(withHeadroom.placesNeeded("itn_trip_2d"))
				.as("8자리 × 배수 2")
				.isEqualTo(16);
	}

	@Test
	@DisplayName("여벌 배수를 0 이하로 줘도 1 아래로는 안 내려간다 — 후보가 0이면 일정이 통째로 빈다")
	void headroomNeverDropsBelowOne() {
		Trip oneDay = Trip.builder()
				.tripId("itn_trip_1d").createdBy("usr_1")
				.startDate(LocalDate.of(2026, 9, 22)).finishDate(LocalDate.of(2026, 9, 22))
				.partySize(2).timezone("Asia/Seoul")
				.createdAt(Instant.now())
				.build();
		when(this.tripRepository.findById("itn_trip_1d")).thenReturn(Optional.of(oneDay));

		ItineraryDraftService zeroHeadroom = new ItineraryDraftService(this.tripRepository,
				mock(ItineraryRepository.class), CLOCK, 4, 3, "FOOD", 0,
				new ItineraryLegPlanner(this.placeRepository, noTravelTimeProvider()),
				tables(ALWAYS_UNKNOWN, ALWAYS_UNKNOWN_TIME_FACT), noRouteOrder(), this.placeRepository, noEvents());

		assertThat(zeroHeadroom.placesNeeded("itn_trip_1d"))
				.as("0 을 곱하면 후보가 0이 되고, 그 증상은 설정 오타와 구별되지 않는다")
				.isEqualTo(4);
	}

	@SuppressWarnings("unchecked")
	private ObjectProvider<TravelTimePort> noTravelTimeProvider() {
		ObjectProvider<TravelTimePort> provider = mock(ObjectProvider.class);
		when(provider.getIfAvailable()).thenReturn(null);
		return provider;
	}

	@Test
	@DisplayName("기분이 하루 곳 수를 정하므로 필요한 자리 수도 따라 바뀐다 — 규칙을 한 벌로 둔 값어치")
	void placesNeededFollowsThePace() {
		assertThat(placesNeededFor("RELAXED")).as("여유롭게 — 하루 3곳 × 1일").isEqualTo(3);
		assertThat(placesNeededFor("BALANCED")).as("균형 있게 — 하루 4곳 × 1일").isEqualTo(4);
		assertThat(placesNeededFor("PACKED")).as("알차게 — 하루 5곳 × 1일").isEqualTo(5);
	}

	@Test
	@DisplayName("여행을 못 찾으면 1 이다 — 이 값 때문에 추천이 실패하면 안 된다")
	void placesNeededFallsBackToOneForAnUnknownTrip() {
		when(this.tripRepository.findById("itn_trip_none")).thenReturn(Optional.empty());

		assertThat(this.service.placesNeeded("itn_trip_none")).isEqualTo(1);
	}

	private int placesNeededFor(String pace) {
		when(this.tripRepository.findById("itn_trip_1")).thenReturn(Optional.of(tripWithPace(pace)));
		return this.service.placesNeeded("itn_trip_1");
	}

	// ── 긴 여행에서 날이 갈리나 (인수인계 §7 「7일 여행이 실제로 어떻게 나오나」) ────────

	/**
	 * 부산의 실제 권역 일곱. 서로 떨어진 곳을 골랐다 — 부산에 7일치 «다른 동네»가 실제로
	 * 있는지부터가 질문이라, 지어낸 좌표가 아니라 실제 위치를 쓴다.
	 */
	private static final double[][] BUSAN_DISTRICTS = {
			{ 35.1587, 129.1604 }, // 해운대
			{ 35.0903, 129.0579 }, // 영도
			{ 35.1579, 129.0594 }, // 서면
			{ 35.1532, 129.1186 }, // 광안리
			{ 35.0966, 129.0306 }, // 남포동·자갈치
			{ 35.2445, 129.2222 }, // 기장
			{ 35.1784, 129.1996 }, // 송정
	};

	/** 권역 하나 안에서 조금씩 흩뜨린다 — 같은 동네라도 같은 점은 아니다. */
	private static double[] near(double[] district, int nth) {
		return new double[] { district[0] + (nth * 0.004), district[1] + (nth * 0.004) };
	}

	/**
	 * 🔴 인수인계 문서가 「확인 못 했다」로 남긴 것을 확인한다.
	 *
	 * <p>문서는 <i>「날 중심 잡기가 7일에선 흔들린다 — 2~3일에는 맞지만 7일이면 부산 안에서
	 * 억지로 벌어진다」</i> 고 적었고, 그 근거로 숙소를 날 중심으로 쓰자는 결정(§6-4)이
	 * 걸려 있다. 그런데 <b>운영에 7일짜리 일정이 한 번도 만들어진 적이 없다</b> —
	 * 2026-09-22 실측으로 가장 긴 것이 5일이고, 7일 여행 한 건은 엔진에 닿기도 전에
	 * (VERSION_RESOLUTION, 후보 0곳) 실패했다. 그래서 운영 자료로는 확인할 수가 없다.
	 *
	 * <p>여기서 대신 확인한다. 날 중심은 {@code seedDayAnchors} 가 <b>이미 잡힌 중심에서
	 * 가장 먼 후보</b>를 차례로 고르는 방식이라(farthest-point sampling), 날이 늘수록
	 * 고를 수 있는 «먼 곳»이 줄어 바깥쪽 외톨이를 집게 된다 — 그것이 문서가 걱정한 「억지로
	 * 벌어진다」의 기계적 정체다.
	 *
	 * <p>보는 것은 2일 시험과 <b>같은 성질 하나</b>다: 하루 안의 두 곳이 8km 넘게 떨어지지
	 * 않는가. 어느 날에 어느 권역이 가는지는 안 본다 — 그건 순위가 정한다.
	 */
	@Test
	@DisplayName("🔴 7일도 하루는 한 권역이다 — 권역이 일곱이면 억지로 벌어지지 않는다")
	void sevenDayTripKeepsEachDayInOneRegion() {
		Trip trip = tripOf(LocalDate.of(2026, 9, 22), LocalDate.of(2026, 9, 28));
		when(this.tripRepository.findById("trip_1")).thenReturn(Optional.of(trip));

		// 하루 4곳 × 7일 = 28자리. 권역마다 딱 4곳씩 준다 — 한쪽이 모자라면 코드가 어떻게
		// 해도 섞일 수밖에 없고, 그때 보이는 것은 배정이 아니라 후보 부족이다.
		List<String> categories = new ArrayList<>();
		List<double[]> coordinates = new ArrayList<>();
		for (double[] district : BUSAN_DISTRICTS) {
			for (int nth = 0; nth < 4; nth++) {
				categories.add(nth == 0 ? "FOOD" : "CITY");
				coordinates.add(near(district, nth));
			}
		}

		ItineraryDraft draft = this.service.assemble(
				commandOf("trip_1", plannedPlacesAt(categories, coordinates)));

		Map<Integer, List<double[]>> byDay = new HashMap<>();
		for (ItineraryDraft.DraftItem item : draft.items()) {
			byDay.computeIfAbsent(item.dayIndex(), key -> new ArrayList<>())
					.add(coordinateOf(item.placeId()));
		}

		assertThat(byDay).as("7일이면 7일치가 나와야 한다").hasSize(7);
		for (Map.Entry<Integer, List<double[]>> day : byDay.entrySet()) {
			for (double[] a : day.getValue()) {
				for (double[] b : day.getValue()) {
					assertThat(kmBetween(a, b))
							.as("%d일차 안의 두 곳이 8km 넘게 떨어졌다 — 긴 여행에서 날이 안 갈린다",
									day.getKey())
							.isLessThan(8.0);
				}
			}
		}
	}

	/**
	 * 🔴 앞 시험의 <b>반대쪽</b>. 권역이 날 수만큼 없을 때 무슨 일이 나는가.
	 *
	 * <p>앞 시험은 부산에 <b>일곱 권역이 실제로 있을 때</b>를 봤고 잘 갈렸다. 그런데 인수인계
	 * 문서가 걱정한 것은 그쪽이 아니라 <i>「부산 안에서 억지로 벌어진다」</i> 쪽이다 — 좋은
	 * 후보가 두세 동네에 몰려 있는데 이레를 채워야 하는 경우다. 운영 자료를 봐도 후보는 고르게
	 * 퍼져 있지 않다.
	 *
	 * <p>그때 코드는 <b>억지로 섞지 않고 말한다.</b> {@code regionMixed} 가 하루 안에 먼 곳이
	 * 섞인 것을 보면 {@code DAY_REGION_MIXED} 경고를 남긴다. 순위를 버리면서까지 지역을
	 * 맞추지 않는 것이 이 코드의 결정이고({@code regionMixed} 주석), 이 시험은 <b>그 결정이
	 * 긴 여행에서도 유지되는지</b>를 지킨다.
	 *
	 * <p>즉 여기서 보는 것은 「섞이지 않는다」가 아니라 <b>「섞였으면 반드시 말한다」</b>이다.
	 * 조용히 섞이는 것이 유일하게 나쁜 결과다 — 화면이 그것을 그대로 좋은 일정으로 그린다.
	 */
	@Test
	@DisplayName("🔴 권역이 모자라면 조용히 섞지 않고 «말한다» — DAY_REGION_MIXED")
	void longTripWithTooFewRegionsWarnsInsteadOfMixingSilently() {
		Trip trip = tripOf(LocalDate.of(2026, 9, 22), LocalDate.of(2026, 9, 28));
		when(this.tripRepository.findById("trip_1")).thenReturn(Optional.of(trip));

		// 28자리인데 권역은 셋뿐이다 — 이레를 채우려면 한 권역을 여러 날에 쪼개야 한다.
		List<String> categories = new ArrayList<>();
		List<double[]> coordinates = new ArrayList<>();
		for (int nth = 0; nth < 28; nth++) {
			categories.add(nth % 4 == 0 ? "FOOD" : "CITY");
			coordinates.add(near(BUSAN_DISTRICTS[nth % 3], nth / 3));
		}

		ItineraryDraft draft = this.service.assemble(
				commandOf("trip_1", plannedPlacesAt(categories, coordinates)));

		Map<Integer, List<double[]>> byDay = new HashMap<>();
		for (ItineraryDraft.DraftItem item : draft.items()) {
			byDay.computeIfAbsent(item.dayIndex(), key -> new ArrayList<>())
					.add(coordinateOf(item.placeId()));
		}

		boolean mixed = false;
		for (List<double[]> day : byDay.values()) {
			for (double[] a : day) {
				for (double[] b : day) {
					if (kmBetween(a, b) >= 8.0) {
						mixed = true;
					}
				}
			}
		}

		// 🔴 조건부로 두지 않는다. 권역 셋에 이레를 채우면 «반드시» 섞이고, 그것이 이 시험의
		//    전제다. if 로 감싸 두면 나중에 배정이 바뀌어 안 섞이게 됐을 때 이 시험이 아무것도
		//    안 보면서 초록으로 남는다 — 그때는 전제가 바뀐 것이니 시험이 그것을 알려야 한다.
		assertThat(mixed).as("권역 셋에 7일을 채웠는데 안 섞였다 — 이 시험의 전제가 바뀌었다").isTrue();
		assertThat(draft.warningCodes())
				.as("하루가 여러 권역에 걸쳤는데 경고가 없다 — 화면이 그냥 좋은 일정으로 그린다")
				.contains(ItineraryWarningCodes.DAY_REGION_MIXED);
	}

	/**
	 * 🔴 2026-09-23 운영 일정(eb0d0494) 그대로 — 하루가 남포 카페 → <b>해운대</b> 밥집 → 영도 시장 → 영도 밥집이었다.
	 *
	 * <p>점심·저녁 칸은 «목록에서 처음 나오는 밥집» 을 앉혀서, 먼 밥집이 한가운데 끼면 남포·해운대·영도를
	 * 오간다. 끼니 칸 규칙을 지키는 차례 중 가장 짧은 것을 고르면 해운대는 맨 끝(또는 맨 앞)으로 간다.
	 */
	@Test
	@DisplayName("🔴 끼니 칸이 동선을 섞지 않는다 — 먼 밥집이 하루 한가운데 끼지 않는다(S15P21E201-1547)")
	void mealSlotsDoNotZigzagTheDay() {
		Trip trip = tripOf(LocalDate.of(2026, 9, 22), LocalDate.of(2026, 9, 22), LocalTime.of(9, 0), LocalTime.of(18, 0));
		when(this.tripRepository.findById("trip_1")).thenReturn(Optional.of(trip));

		double[] nampoCafe = { 35.0903, 129.0579 };
		double[] haeundaeFood = { 35.1633, 129.1596 };
		double[] yeongdoMarket = { 35.0966, 129.0604 };
		double[] yeongdoFood = { 35.0864, 129.0767 };
		List<ItineraryDraftCommand.PlannedPlace> places = plannedPlacesAt(
				List.of("CAFE_HEALING", "FOOD", "CITY", "FOOD"),
				List.of(nampoCafe, haeundaeFood, yeongdoMarket, yeongdoFood));

		// 운영에서는 최적화기가 여행 출발지(해운대)에서 최단 차례를 잡아 해운대 밥집을 맨 앞에 뒀다.
		// 첫 칸은 밥 때가 아니라 그 밥집을 건너뛰고, 점심 칸이 «목록의 첫 밥집» 인 그것을 앉혔다.
		List<UUID> fromHaeundae = List.of(places.get(1).placeId(), places.get(0).placeId(),
				places.get(2).placeId(), places.get(3).placeId());
		ItineraryDraftService service = serviceWithRouteOrder(request -> fromHaeundae);

		ItineraryDraft draft = service.assemble(commandOf("trip_1", places));

		List<ItineraryDraft.DraftItem> day = draft.items().stream()
				.sorted(java.util.Comparator.comparing(ItineraryDraft.DraftItem::startTime))
				.toList();
		double total = 0;
		for (int i = 1; i < day.size(); i++) {
			total += kmBetween(coordinateOf(day.get(i - 1).placeId()), coordinateOf(day.get(i).placeId()));
		}
		UUID haeundae = places.get(1).placeId();
		int haeundaeAt = day.stream().map(ItineraryDraft.DraftItem::placeId).toList().indexOf(haeundae);

		assertThat(haeundaeAt).as("해운대 밥집이 남포·영도 사이에 끼었다").isIn(0, day.size() - 1);
		// 옛 차례(남포→해운대→영도→영도)는 약 25km 였다.
		assertThat(total).isLessThan(18.0);
		// 끼니 칸 규칙은 그대로다 — 두 밥집이 점심·저녁 자리에 있다.
		assertThat(day.stream().filter(item -> "FOOD".equals(this.categoryByPlaceId.get(item.placeId()))).count()).isEqualTo(2);
	}

	/**
	 * 🔴 2026-09-25 운영 일정(55476311) 2일차 그대로 — 09~21시 · 하루 5곳(알차게).
	 *
	 * <p>칸이 144분씩이라 저녁 시각대(17~20시)가 넷째 칸(16:12~)과 다섯째 칸(18:36~)에 다 걸려 밥 칸이 셋이었다.
	 * 밥집은 둘이라 동선을 줄이는 단계가 둘을 저녁 두 칸으로 옮겼다 — 광안리해수욕장 → 카페오뜨 → 동경밥상 16:15
	 * → 한끼맛있다 18:44. 점심이 사라지고 저녁을 두 번 먹었다.
	 */
	@Test
	@DisplayName("🔴 하루 5곳이어도 점심 1번·저녁 1번이 각 시각대에 들어가고 밥집이 연달아 붙지 않는다(S15P21E201-1624)")
	void oneLunchAndOneDinnerInTheirBands() {
		Trip trip = Trip.builder()
				.tripId("trip_1").createdBy("usr_1")
				.startDate(LocalDate.of(2026, 10, 17)).finishDate(LocalDate.of(2026, 10, 17))
				.partySize(4).timezone("Asia/Seoul").pace("PACKED")
				.timeWindowStart(LocalTime.of(9, 0)).timeWindowEnd(LocalTime.of(21, 0))
				.createdAt(Instant.now())
				.build();
		when(this.tripRepository.findById("trip_1")).thenReturn(Optional.of(trip));
		List<ItineraryDraftCommand.PlannedPlace> places = plannedPlacesAt(
				List.of("NATURE_WALK", "SEA_BEACH", "CAFE_HEALING", "FOOD", "FOOD"),
				List.of(new double[] { 35.1618, 129.1325 },   // 부산 갈맷길 2코스
						new double[] { 35.1532, 129.1190 },   // 광안리해수욕장
						new double[] { 35.1528, 129.1176 },   // 카페오뜨
						new double[] { 35.1484, 129.1139 },   // 동경밥상
						new double[] { 35.1546, 129.0619 })); // 한끼맛있다

		ItineraryDraft draft = this.service.assemble(commandOf("trip_1", places));

		List<ItineraryDraft.DraftItem> day = draft.items().stream()
				.sorted(java.util.Comparator.comparing(ItineraryDraft.DraftItem::sequence))
				.toList();
		assertThat(day).hasSize(5);
		List<LocalTime> meals = day.stream()
				.filter(item -> "FOOD".equals(categoryOfItem(item)))
				.map(ItineraryDraft.DraftItem::startTime)
				.toList();
		assertThat(meals).as("밥 먹는 시각").hasSize(2);
		assertThat(meals.get(0)).as("점심 — 11시~14시 사이에 닿는다").isBetween(LocalTime.of(11, 0), LocalTime.of(14, 0));
		assertThat(meals.get(1)).as("저녁 — 17시~20시 사이에 닿는다").isBetween(LocalTime.of(17, 0), LocalTime.of(20, 0));
		for (int i = 1; i < day.size(); i++) {
			assertThat("FOOD".equals(categoryOfItem(day.get(i - 1))) && "FOOD".equals(categoryOfItem(day.get(i))))
					.as("%d·%d번째가 둘 다 밥집", i, i + 1)
					.isFalse();
		}
	}

	/**
	 * 같은 병이 다른 기분에도 있었다. 09~21시를 나누면 — 하루 4곳(균형)은 칸이 180분씩이라 저녁 칸이 15:00·18:00 둘,
	 * 하루 3곳(여유)은 240분씩이라 09:00 칸이 점심에 90분 걸려 <b>아침 9시에 점심</b>을 먹을 수 있었다.
	 */
	@Test
	@DisplayName("🔴 기분이 무엇이든 09~21시 여행은 점심은 점심 때·저녁은 저녁 때 한 번씩 먹는다(S15P21E201-1624)")
	void everyPaceEatsLunchAndDinnerInTheirBands() {
		Map<String, List<String>> dayOf = Map.of(
				"RELAXED", List.of("FOOD", "FOOD", "SEA_BEACH"),
				"BALANCED", List.of("FOOD", "FOOD", "SEA_BEACH", "CULTURE_TEMPLE"),
				"PACKED", List.of("FOOD", "FOOD", "SEA_BEACH", "CULTURE_TEMPLE", "NATURE_WALK"));
		for (Map.Entry<String, List<String>> pace : dayOf.entrySet()) {
			Trip trip = Trip.builder()
					.tripId("trip_1").createdBy("usr_1")
					.startDate(LocalDate.of(2026, 10, 17)).finishDate(LocalDate.of(2026, 10, 17))
					.partySize(2).timezone("Asia/Seoul").pace(pace.getKey())
					.timeWindowStart(LocalTime.of(9, 0)).timeWindowEnd(LocalTime.of(21, 0))
					.createdAt(Instant.now())
					.build();
			when(this.tripRepository.findById("trip_1")).thenReturn(Optional.of(trip));

			ItineraryDraft draft = this.service.assemble(
					commandOf("trip_1", plannedPlacesOf(pace.getValue().toArray(String[]::new))));

			List<LocalTime> meals = draft.items().stream()
					.filter(item -> "FOOD".equals(categoryOfItem(item)))
					.map(ItineraryDraft.DraftItem::startTime)
					.sorted()
					.toList();
			assertThat(meals).as(pace.getKey()).hasSize(2);
			assertThat(meals.get(0)).as(pace.getKey() + " 점심").isBetween(LocalTime.of(11, 0), LocalTime.of(14, 0));
			assertThat(meals.get(1)).as(pace.getKey() + " 저녁").isBetween(LocalTime.of(17, 0), LocalTime.of(20, 0));
		}
	}

	// ── 기간이 정해진 장소 — 축제·박람회 ───────────────────────────────

	private static final LocalDate FESTIVAL_TRIP_START = LocalDate.of(2026, 9, 10);

	/** 사흘짜리 여행(9/10~9/12)의 후보 여섯. 순위 1위가 축제다 — 고치기 전에는 첫날 첫 자리에 앉는다. */
	private List<ItineraryDraftCommand.PlannedPlace> festivalCase(List<String> festivalReasons) {
		when(this.tripRepository.findById("itn_trip_1"))
				.thenReturn(Optional.of(tripOf(FESTIVAL_TRIP_START, FESTIVAL_TRIP_START.plusDays(2))));
		List<ItineraryDraftCommand.PlannedPlace> places = new ArrayList<>(
				plannedPlacesOf("CULTURE_TEMPLE", "CITY", "CITY", "CITY", "CITY", "CITY"));
		ItineraryDraftCommand.PlannedPlace festival = places.get(0);
		places.set(0, new ItineraryDraftCommand.PlannedPlace(festival.placeId(), festival.rank(), festivalReasons,
				List.of(), festival.category()));
		return places;
	}

	/** 그 장소 하나만 기간이 정해져 있고 {@code openDates} 에만 연다. 나머지는 기간이 없는 장소다. */
	private static PlaceEventSchedulePort eventOnly(UUID placeId, LocalDate... openDates) {
		return (id, from, to) -> placeId.toString().equals(id)
				? PlaceEventSchedule.openOn(List.of(openDates))
				: PlaceEventSchedule.unscheduled();
	}

	private static List<String> daysAndPlaces(ItineraryDraft draft) {
		return draft.items().stream().map((item) -> item.dayIndex() + ":" + item.placeId()).toList();
	}

	/** 운영(2026-09-24)에서 회차 14개가 전부 끝났는데 「카운트다운 부산」이 9월 여행에 17번 들어갔다. */
	@Test
	@DisplayName("🔴 여행 중 한 날도 안 여는 축제(끝난 축제)는 추천 일정에 안 들어간다")
	void anEndedFestivalIsNotSeated() {
		List<ItineraryDraftCommand.PlannedPlace> places = festivalCase(List.of("REASON"));
		// 기간 기록은 있는데 이 여행 날짜에 여는 날이 없다.
		this.service.setEventSchedule(eventOnly(places.get(0).placeId()));

		List<UUID> placed = placeIdsOfItems(this.service.assemble(commandOf("itn_trip_1", places)));

		assertThat(placed).doesNotContain(places.get(0).placeId());
		assertThat(placed).containsAll(placeIdsOf(places.subList(1, places.size())));
	}

	@Test
	@DisplayName("🔴 여행 둘째 날만 여는 축제는 둘째 날에만 앉는다")
	void aFestivalOpenOnlyOnTheSecondDaySitsThere() {
		List<ItineraryDraftCommand.PlannedPlace> places = festivalCase(List.of("REASON"));
		this.service.setEventSchedule(eventOnly(places.get(0).placeId(), FESTIVAL_TRIP_START.plusDays(1)));

		ItineraryDraft draft = this.service.assemble(commandOf("itn_trip_1", places));

		assertThat(draft.items()).filteredOn((item) -> item.placeId().equals(places.get(0).placeId()))
				.singleElement().extracting(ItineraryDraft.DraftItem::dayIndex).isEqualTo(1);
	}

	@Test
	@DisplayName("기간 기록이 없는 장소는 전과 같다 — 기간을 묻는 문이 붙어도 일정이 한 줄도 안 바뀐다")
	void placesWithoutPeriodsAreUnaffected() {
		List<ItineraryDraftCommand.PlannedPlace> places = festivalCase(List.of("REASON"));
		ItineraryDraft without = this.service.assemble(commandOf("itn_trip_1", places));

		this.service.setEventSchedule((id, from, to) -> PlaceEventSchedule.unscheduled());
		ItineraryDraft with = this.service.assemble(commandOf("itn_trip_1", places));

		assertThat(daysAndPlaces(with)).containsExactlyElementsOf(daysAndPlaces(without));
	}

	@Test
	@DisplayName("🔴 사용자가 직접 고른 꼭 갈 장소는 기간을 안 본다 — 사용자의 선택이다")
	void aMustVisitFestivalIsLeftAlone() {
		List<ItineraryDraftCommand.PlannedPlace> places = festivalCase(List.of(SeedBoost.REASON_CODE_MUST_VISIT));
		this.service.setEventSchedule(eventOnly(places.get(0).placeId()));

		assertThat(placeIdsOfItems(this.service.assemble(commandOf("itn_trip_1", places))))
				.contains(places.get(0).placeId());
	}

	/**
	 * 하루 다시 짜기는 순위 풀에서 빈자리를 채운다. 풀이 넷(축제 + 명소 셋)이고 비어 있던 날의 목표도 넷이라, 축제로
	 * 채우지 않으면 셋만 채워지고 「일부만 채웠다」 경고가 남는다 — 모자라도 안 여는 곳으로 억지로 채우지 않는다.
	 */
	@Test
	@DisplayName("🔴 하루 다시 짜기도 그 날 안 여는 축제로 빈자리를 채우지 않는다")
	void dayRecalculationSkipsAFestivalClosedThatDay() {
		ItineraryRepository itineraries = mock(ItineraryRepository.class);
		@SuppressWarnings("unchecked")
		ObjectProvider<TravelTimePort> noTravelTime = mock(ObjectProvider.class);
		ItineraryDraftService reviser = new ItineraryDraftService(this.tripRepository, itineraries, CLOCK, 4, 3, "FOOD", 1,
				new ItineraryLegPlanner(this.placeRepository, noTravelTime), tables(ALWAYS_UNKNOWN, ALWAYS_UNKNOWN_TIME_FACT),
				noRouteOrder(), this.placeRepository, noEvents());
		List<ItineraryDraftCommand.PlannedPlace> pool = festivalCase(List.of("REASON")).subList(0, 4);
		// 축제는 셋째 날만 연다 — 첫째 날을 다시 짜면 못 들어간다.
		reviser.setEventSchedule(eventOnly(pool.get(0).placeId(), FESTIVAL_TRIP_START.plusDays(2)));
		ItineraryVersion base = new ItineraryVersion(UUID.randomUUID().toString(), "itn_1", 1, null,
				ItineraryVersion.Operation.CREATE, "usr_1", "req_1", null, Instant.now());
		when(itineraries.findContent("itn_1", 1))
				.thenReturn(Optional.of(new ItineraryContent(base, List.of(), List.of(), List.of())));
		when(itineraries.findById("itn_1")).thenReturn(Optional.of(new Itinerary("itn_1", "itn_trip_1", 1)));

		ItineraryRevisionDraft draft = reviser.revise(new ItineraryRevisionCommand(UUID.randomUUID(), "itn_1", 1,
				"usr_1", JobType.ITINERARY_RECALCULATE, 0, null, List.of(), null, pool, "m", "f", "o", "p", "d"));

		assertThat(draft.filledCount()).as("축제로 채웠다면 넷이다").isEqualTo(3);
		assertThat(draft.warningCodes()).contains(ItineraryWarningCodes.RECALC_DAY_PARTIALLY_FILLED);
	}

	@Test
	@DisplayName("🔴 S15P21E201-1692 — 하루 다시 짜기도 실제 체류로 고친 값을 쓴다")
	void dayRecalculationUsesCalibratedStayMinutes() {
		ItineraryRepository itineraries = mock(ItineraryRepository.class);
		@SuppressWarnings("unchecked")
		ObjectProvider<TravelTimePort> noTravelTime = mock(ObjectProvider.class);
		ItineraryDraftService reviser = new ItineraryDraftService(this.tripRepository, itineraries, CLOCK, 4, 3, "FOOD", 1,
				new ItineraryLegPlanner(this.placeRepository, noTravelTime), tables(ALWAYS_UNKNOWN, ALWAYS_UNKNOWN_TIME_FACT),
				noRouteOrder(), this.placeRepository, noEvents());
		// 이 경로는 갈래를 장소 표에서 읽는데 여기 장소 표는 비어 있다 — 갈래를 몰라도 고친 값을 쓰는지만 본다(기본값은 60분).
		reviser.setStayCalibration((category) -> OptionalInt.of(75));
		List<ItineraryDraftCommand.PlannedPlace> pool = festivalCase(List.of("REASON")).subList(1, 4);
		// 시각을 깔려면 활동 시간대가 있어야 한다 — 없으면 체류도 비운다.
		when(this.tripRepository.findById("itn_trip_1")).thenReturn(Optional.of(tripOf(FESTIVAL_TRIP_START,
				FESTIVAL_TRIP_START.plusDays(2), LocalTime.of(9, 0), LocalTime.of(18, 0))));
		ItineraryVersion base = new ItineraryVersion(UUID.randomUUID().toString(), "itn_1", 1, null,
				ItineraryVersion.Operation.CREATE, "usr_1", "req_1", null, Instant.now());
		when(itineraries.findContent("itn_1", 1))
				.thenReturn(Optional.of(new ItineraryContent(base, List.of(), List.of(), List.of())));
		when(itineraries.findById("itn_1")).thenReturn(Optional.of(new Itinerary("itn_1", "itn_trip_1", 1)));

		reviser.publish(reviser.revise(new ItineraryRevisionCommand(UUID.randomUUID(), "itn_1", 1,
				"usr_1", JobType.ITINERARY_RECALCULATE, 0, null, List.of(), null, pool, "m", "f", "o", "p", "d")));

		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<ItineraryItem>> items = ArgumentCaptor.forClass(List.class);
		verify(itineraries).appendVersion(any(), items.capture(), any(), any());
		assertThat(items.getValue()).isNotEmpty()
				.allSatisfy((item) -> assertThat(item.stayMinutes()).isEqualTo(75));
	}

	@Test
	@DisplayName("🔴 S15P21E201-1700 — 하루 다시 짜기의 새 판도 이동 시간은 고친 값, 옆 칸에는 엔진의 어림")
	void dayRecalculationKeepsTheUncalibratedTravelTime() {
		ItineraryRepository itineraries = mock(ItineraryRepository.class);
		ItineraryLegPlanner planner = new ItineraryLegPlanner(this.placeRepository, travelMinutes(20));
		planner.setTravelCalibration((mode) -> OptionalDouble.of(1.5));
		ItineraryDraftService reviser = new ItineraryDraftService(this.tripRepository, itineraries, CLOCK, 4, 3, "FOOD", 1,
				planner, tables(ALWAYS_UNKNOWN, ALWAYS_UNKNOWN_TIME_FACT), noRouteOrder(), this.placeRepository, noEvents());
		List<ItineraryDraftCommand.PlannedPlace> pool = festivalCase(List.of("REASON")).subList(1, 4);
		ItineraryVersion base = new ItineraryVersion(UUID.randomUUID().toString(), "itn_1", 1, null,
				ItineraryVersion.Operation.CREATE, "usr_1", "req_1", null, Instant.now());
		when(itineraries.findContent("itn_1", 1))
				.thenReturn(Optional.of(new ItineraryContent(base, List.of(), List.of(), List.of())));
		when(itineraries.findById("itn_1")).thenReturn(Optional.of(new Itinerary("itn_1", "itn_trip_1", 1)));

		reviser.publish(reviser.revise(new ItineraryRevisionCommand(UUID.randomUUID(), "itn_1", 1,
				"usr_1", JobType.ITINERARY_RECALCULATE, 0, null, List.of(), null, pool, "m", "f", "o", "p", "d")));

		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<ItineraryLeg>> legs = ArgumentCaptor.forClass(List.class);
		verify(itineraries).appendVersion(any(), any(), legs.capture(), any());
		assertThat(legs.getValue()).isNotEmpty().allSatisfy((leg) -> {
			assertThat(leg.durationMin()).isEqualTo(30);
			assertThat(leg.uncalibratedDurationMin()).isEqualTo(20);
		});
	}

	/** 판을 옮길 때(고정·빼기·되돌리기 등) 보정 전 이동 분을 흘리면 다음 보정이 고친 값을 어림으로 읽는다. */
	@Test
	@DisplayName("🔴 S15P21E201-1700 — 판을 그대로 옮길 때도 보정 전 이동 분이 따라간다")
	void copyingAVersionKeepsTheUncalibratedTravelTime() {
		ItineraryVersion base = new ItineraryVersion(UUID.randomUUID().toString(), "itn_1", 1, null,
				ItineraryVersion.Operation.CREATE, "usr_1", "req_1", null, Instant.now());
		ItineraryLeg leg = new ItineraryLeg(UUID.randomUUID().toString(), base.itineraryVersionId(), 0, 1,
				UUID.randomUUID().toString(), UUID.randomUUID().toString(), "BUS", 3000, 30, null, null, null,
				ItineraryItem.DataStatus.ESTIMATED, null, null, 20, Instant.now());

		com.gabolle.backend.itinerary.domain.ItineraryRevision.Draft copied =
				com.gabolle.backend.itinerary.domain.ItineraryRevision.copyOf(
						new ItineraryContent(base, List.of(), List.of(leg), List.of()), UUID.randomUUID().toString(),
						Instant.now());

		assertThat(copied.legs()).singleElement().satisfies((c) -> {
			assertThat(c.durationMin()).isEqualTo(30);
			assertThat(c.uncalibratedDurationMin()).isEqualTo(20);
		});
	}

	/**
	 * 빼기는 판을 옮기지 않고 그날 구간을 새로 잰다 (S15P21E201-1706). B 를 빼면 A→C 라는 새 쌍이 생기는데, 그 쌍의 길을
	 * 경로 쪽이 모르면 비워 둔다 — 바탕 판의 A→B · B→C 길이나 요금을 옮겨 붙이면 지도에 틀린 길이 그려진다.
	 */
	@Test
	@DisplayName("🔴 S15P21E201-1706 — 빼기로 생긴 새 쌍(A→C)은 다른 쌍의 길 선 · 요금을 받지 않는다")
	void removingAStopNeverBorrowsAnotherPairsPathOrFare() {
		double[] a = { 35.10, 129.01 };
		double[] b = { 35.11, 129.02 };
		double[] c = { 35.12, 129.03 };
		List<Place> rows = new ArrayList<>();
		List<String> placeIds = new ArrayList<>();
		for (double[] at : List.of(a, b, c)) {
			UUID placeId = UUID.randomUUID();
			placeIds.add(placeId.toString());
			rows.add(Place.imported(placeId, "곳", null, "부산", at[0], at[1], "TEST", "t-" + placeId, null, null, "v1"));
		}
		when(this.placeRepository.findByPlaceIdIn(anyCollection())).thenReturn(rows);
		when(this.tripRepository.findById("itn_trip_1"))
				.thenReturn(Optional.of(tripWithWindow(LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 10))));

		// 경로 쪽은 A→C 길을 모른다(선형 · 요금 없음). 출발지가 없는 첫 구간도 모른다. 나머지 쌍은 자기 길을 준다.
		TravelTimePort port = (fromLat, fromLng, toLat, toLng, mode) -> {
			boolean aToC = fromLat != null && fromLat == a[0] && toLat == c[0];
			if (fromLat == null || aToC) {
				return new TravelTime(2_000, 15, ItineraryItem.DataStatus.ESTIMATED);
			}
			return new TravelTime(1_000, 10, ItineraryItem.DataStatus.VERIFIED, 3_300,
					List.of(new double[] { fromLng, fromLat }, new double[] { toLng, toLat }));
		};
		@SuppressWarnings("unchecked")
		ObjectProvider<TravelTimePort> provider = mock(ObjectProvider.class);
		when(provider.getIfAvailable()).thenReturn(port);
		ItineraryRepository itineraries = mock(ItineraryRepository.class);
		ItineraryDraftService reviser = new ItineraryDraftService(this.tripRepository, itineraries, CLOCK, 4, 3, "FOOD", 1,
				new ItineraryLegPlanner(this.placeRepository, provider), tables(ALWAYS_UNKNOWN, ALWAYS_UNKNOWN_TIME_FACT),
				noRouteOrder(), this.placeRepository, noEvents());

		ItineraryVersion base = new ItineraryVersion(UUID.randomUUID().toString(), "itn_1", 1, null,
				ItineraryVersion.Operation.CREATE, "usr_1", "req_1", null, Instant.now());
		List<ItineraryItem> baseItems = new ArrayList<>();
		for (int i = 0; i < 3; i++) {
			baseItems.add(new ItineraryItem(UUID.randomUUID().toString(), base.itineraryVersionId(), "key_" + i, 0,
					LocalDate.of(2026, 9, 10), i + 1, placeIds.get(i), null, null, null, false, null,
					ItineraryItem.DataStatus.VERIFIED, List.of(), List.of(), null, Instant.now()));
		}
		List<double[]> storedPath = List.of(new double[] { 1.0, 1.0 }, new double[] { 2.0, 2.0 });
		List<ItineraryLeg> baseLegs = List.of(
				new ItineraryLeg(UUID.randomUUID().toString(), base.itineraryVersionId(), 0, 2, placeIds.get(0), placeIds.get(1),
						"WALK", 1_000, 10, null, null, null, ItineraryItem.DataStatus.VERIFIED, 9_900, storedPath, 10, Instant.now()),
				new ItineraryLeg(UUID.randomUUID().toString(), base.itineraryVersionId(), 0, 3, placeIds.get(1), placeIds.get(2),
						"WALK", 1_000, 10, null, null, null, ItineraryItem.DataStatus.VERIFIED, 9_900, storedPath, 10, Instant.now()));
		when(itineraries.findContent("itn_1", 1))
				.thenReturn(Optional.of(new ItineraryContent(base, baseItems, baseLegs, List.of())));
		when(itineraries.findById("itn_1")).thenReturn(Optional.of(new Itinerary("itn_1", "itn_trip_1", 1)));

		reviser.publish(reviser.revise(new ItineraryRevisionCommand(UUID.randomUUID(), "itn_1", 1, "usr_1",
				JobType.ITEM_REMOVE, 0, "key_1", List.of(), null, List.of(), "m", "f", "o", "p", "d")));

		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<ItineraryLeg>> legs = ArgumentCaptor.forClass(List.class);
		verify(itineraries).appendVersion(any(), any(), legs.capture(), any());
		ItineraryLeg aToC = legs.getValue().stream()
				.filter((leg) -> placeIds.get(0).equals(leg.fromPlaceId()) && placeIds.get(2).equals(leg.toPlaceId()))
				.findFirst().orElseThrow();
		assertThat(aToC.path()).as("A→C 는 경로 쪽이 모른다 — 바탕 판의 길을 옮겨 붙이지 않는다").isNull();
		assertThat(aToC.fareKrw()).isNull();
		assertThat(legs.getValue()).noneSatisfy((leg) -> assertThat(leg.fareKrw()).isEqualTo(9_900));
	}

	// ── 오늘 출발하는 여행 (S15P21E201-1734) ─────────────────────────────
	//
	// 시각은 전부 부산 시각으로 적고 Instant 로 바꿔 시계에 넣는다. 서버 기본 시간대(CI 러너는 UTC)와 상관없이 같은 답이어야 한다.

	private static final LocalDate TODAY = LocalDate.of(2026, 9, 26);

	private static Instant kst(LocalDate date, String time) {
		return LocalDateTime.of(date, LocalTime.parse(time)).atZone(ZoneId.of("Asia/Seoul")).toInstant();
	}

	/** 부산 시각 {@code time} 에 일정을 만드는(또는 고치는) 서비스. */
	private ItineraryDraftService serviceAt(String time, ItineraryRepository itineraries) {
		return serviceAt(time, itineraries, ALWAYS_UNKNOWN);
	}

	private ItineraryDraftService serviceAt(String time, ItineraryRepository itineraries, OpeningHoursFilterPort openingHours) {
		@SuppressWarnings("unchecked")
		ObjectProvider<TravelTimePort> noTravelTime = mock(ObjectProvider.class);
		return new ItineraryDraftService(this.tripRepository, itineraries, Clock.fixed(kst(TODAY, time), ZoneOffset.UTC), 4,
				3, "FOOD", 1, new ItineraryLegPlanner(this.placeRepository, noTravelTime),
				tables(openingHours, ALWAYS_UNKNOWN_TIME_FACT), noRouteOrder(), this.placeRepository, noEvents());
	}

	private ItineraryDraft madeAt(String time, LocalDate start, LocalDate finish, String... categories) {
		when(this.tripRepository.findById("trip_1"))
				.thenReturn(Optional.of(tripOf(start, finish, LocalTime.of(9, 0), LocalTime.of(18, 0))));
		return serviceAt(time, mock(ItineraryRepository.class)).assemble(commandOf("trip_1", plannedPlacesOf(categories)));
	}

	private static List<ItineraryDraft.DraftItem> itemsOfDay(ItineraryDraft draft, int dayIndex) {
		return draft.items().stream().filter((item) -> item.dayIndex() == dayIndex).toList();
	}

	private static final String[] MIXED = { "CULTURE_TEMPLE", "FOOD", "CAFE_HEALING", "NATURE_WALK", "FOOD", "CITY",
			"CULTURE_TEMPLE", "SEA_BEACH", "CAFE_HEALING", "NATURE_WALK" };

	@Test
	@DisplayName("🔴 오늘 여행을 14:07 에 만들면 첫날은 14:30 부터다 — 09:00 부터 짜서 「예정보다 5시간 늦음」으로 시작하지 않는다")
	void aTripForTodayMadeInTheAfternoonStartsFromNow() {
		List<ItineraryDraft.DraftItem> today = itemsOfDay(madeAt("14:07", TODAY, TODAY, MIXED), 0);

		// 앱은 「예정보다 n분」을 지금과 항목 시각의 차이로 센다 — 첫 항목이 지금(올림) 뒤에 있어야 늦음으로 시작하지 않는다.
		assertThat(today).isNotEmpty().allSatisfy((item) -> {
			assertThat(item.startTime()).isAfterOrEqualTo(LocalTime.of(14, 30));
			assertThat(item.endTime()).isBeforeOrEqualTo(LocalTime.of(18, 0));
		});
		// 하루 4곳 × 남은 210분 / 540분 = 1.6 → 2곳. 줄이지 않으면 시각표에 안 들어가 그날 시각이 빈다.
		assertThat(today).hasSize(2);
	}

	@Test
	@DisplayName("오늘이 아닌 여행은 그대로다 — 같은 14:07 에 만든 내일 여행은 활동 시작부터 하루치를 짠다")
	void aTripStartingTomorrowIsUnchanged() {
		ItineraryDraft draft = madeAt("14:07", TODAY.plusDays(1), TODAY.plusDays(1), MIXED);

		assertThat(itemsOfDay(draft, 0)).hasSize(4);
		assertThat(itemsOfDay(draft, 0).get(0).startTime()).isBefore(LocalTime.of(12, 0));
	}

	@Test
	@DisplayName("🔴 17:40 에 만들면 저녁 — 22:00 까지 늦춰 식당·밤에 볼 만한 곳으로 1~2곳, 절·산길·카페는 안 넣는다")
	void aTripMadeInTheEveningGetsAnEveningOfFoodAndNightViews() {
		List<ItineraryDraft.DraftItem> today = itemsOfDay(madeAt("17:40", TODAY, TODAY, MIXED), 0);

		assertThat(today).hasSizeBetween(1, 2).allSatisfy((item) -> {
			assertThat(item.startTime()).isAfterOrEqualTo(LocalTime.of(18, 0));
			assertThat(item.endTime()).isBeforeOrEqualTo(LocalTime.of(22, 0));
		});
		assertThat(today).extracting(this::categoryOfItem).allMatch((category) -> java.util.Set.of("FOOD", "SEA_BEACH", "CITY")
				.contains(category));
	}

	@Test
	@DisplayName("저녁 날에 앉힌 곳이 그 시각에 닫혀 있으면 바꿔 넣는 곳도 식당·밤에 볼 만한 곳에서만 고른다")
	void anEveningSubstituteIsAlsoFoodOrANightView() {
		when(this.tripRepository.findById("trip_1"))
				.thenReturn(Optional.of(tripOf(TODAY, TODAY, LocalTime.of(9, 0), LocalTime.of(18, 0))));
		List<ItineraryDraftCommand.PlannedPlace> places = plannedPlacesOf("CULTURE_TEMPLE", "FOOD", "SEA_BEACH", "FOOD",
				"CULTURE_TEMPLE");
		UUID beach = places.get(2).placeId();
		// 해변이 저녁에 닫혔다고 답한다 — 그 자리를 여벌 후보로 바꿔 넣는다. 절은 열려 있지만 저녁 날에 넣을 곳이 아니다.
		ItineraryDraft draft = serviceAt("17:40", mock(ItineraryRepository.class),
				(placeId, at) -> placeId.equals(beach) ? OpeningHoursFilterPort.Answer.CLOSED
						: OpeningHoursFilterPort.Answer.OPEN)
				.assemble(commandOf("trip_1", places));

		assertThat(itemsOfDay(draft, 0)).isNotEmpty().extracting(this::categoryOfItem)
				.as("여벌 거르기가 없으면 열려 있는 절이 해변 자리에 들어온다").doesNotContain("CULTURE_TEMPLE");
	}

	@Test
	@DisplayName("🔴 22:10 에 만든 여러 날 여행은 첫날을 비우고 다음 날부터 짠다")
	void aMultiDayTripMadeTooLateLeavesTheFirstDayEmpty() {
		ItineraryDraft draft = madeAt("22:10", TODAY, TODAY.plusDays(1), MIXED);

		assertThat(itemsOfDay(draft, 0)).isEmpty();
		assertThat(itemsOfDay(draft, 1)).hasSize(4);
		assertThat(itemsOfDay(draft, 1).get(0).startTime()).isBefore(LocalTime.of(12, 0));
	}

	@Test
	@DisplayName("🔴 22:10 에 만든 당일치기는 만들지 않는다 — 「오늘은 남은 시간이 없어요」로 알린다")
	void aDayTripMadeTooLateIsRefused() {
		assertThatThrownBy(() -> madeAt("22:10", TODAY, TODAY, MIXED))
				.isInstanceOf(com.gabolle.backend.recommendation.application.port.ItineraryDraftPort.NoTimeLeftTodayException.class);
	}

	@Test
	@DisplayName("🔴 오늘 일정을 나중에 고쳐도 첫날은 처음 만든 시각부터다 — 고치는 지금부터 깔면 오전에 들른 곳이 밀린다")
	void recalculatingTodayKeepsTheStartOfWhenTheItineraryWasMade() {
		when(this.tripRepository.findById("itn_trip_1"))
				.thenReturn(Optional.of(tripOf(TODAY, TODAY, LocalTime.of(9, 0), LocalTime.of(18, 0))));
		ItineraryRepository itineraries = mock(ItineraryRepository.class);
		// 14:07 에 만든 일정 — 첫날은 14:30 부터 두 곳이었다.
		ItineraryVersion first = new ItineraryVersion(UUID.randomUUID().toString(), "itn_1", 1, null,
				ItineraryVersion.Operation.CREATE, "usr_1", "req_1", null, kst(TODAY, "14:07"));
		List<ItineraryItem> baseItems = List.of(
				new ItineraryItem("id-a", first.itineraryVersionId(), "a", 0, TODAY, 1, UUID.randomUUID().toString(),
						LocalTime.of(14, 30), LocalTime.of(15, 30), 60, false, null, ItineraryItem.DataStatus.ESTIMATED,
						List.of(), List.of(), null, kst(TODAY, "14:07")),
				new ItineraryItem("id-b", first.itineraryVersionId(), "b", 0, TODAY, 2, UUID.randomUUID().toString(),
						LocalTime.of(16, 0), LocalTime.of(17, 0), 60, false, null, ItineraryItem.DataStatus.ESTIMATED,
						List.of(), List.of(), null, kst(TODAY, "14:07")));
		when(itineraries.findContent("itn_1", 1))
				.thenReturn(Optional.of(new ItineraryContent(first, baseItems, List.of(), List.of())));
		when(itineraries.findVersion("itn_1", 1)).thenReturn(Optional.of(first));
		when(itineraries.findById("itn_1")).thenReturn(Optional.of(new Itinerary("itn_1", "itn_trip_1", 1)));

		// 16:40 에 고친다. 「지금」을 쓰면 17:00 부터라 남은 60분 — 저녁으로 넘어가 22:00 까지 깔린다.
		ItineraryDraftService reviser = serviceAt("16:40", itineraries);
		reviser.publish(reviser.revise(new ItineraryRevisionCommand(UUID.randomUUID(), "itn_1", 1, "usr_1",
				JobType.ITINERARY_RECALCULATE, 0, null, List.of(), null,
				plannedPlacesOf("CULTURE_TEMPLE", "CAFE_HEALING", "NATURE_WALK"), "m", "f", "o", "p", "d")));
		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<ItineraryItem>> saved = ArgumentCaptor.forClass(List.class);
		verify(itineraries).appendVersion(any(), saved.capture(), any(), any());

		List<ItineraryItem> today = saved.getValue().stream().filter((item) -> item.dayIndex() == 0).toList();
		assertThat(today).isNotEmpty();
		assertThat(today.get(0).startTime()).as("처음 만든 14:07 → 14:30 부터").isEqualTo(LocalTime.of(14, 30));
		assertThat(today).allSatisfy((item) -> assertThat(item.endTime()).isBeforeOrEqualTo(LocalTime.of(18, 0)));
	}

}
