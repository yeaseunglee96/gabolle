package com.gabolle.backend.itinerary;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.ApplicationEventPublisher;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import com.gabolle.backend.itinerary.application.ItineraryDraftService;
import com.gabolle.backend.itinerary.application.ItineraryLegPlanner;
import com.gabolle.backend.itinerary.application.port.RouteOrderPort;
import com.gabolle.backend.itinerary.application.port.TravelTimePort;
import com.gabolle.backend.itinerary.domain.ItineraryRepository;
import com.gabolle.backend.place.domain.PlaceEvidenceStatus;
import com.gabolle.backend.place.domain.PlaceFeature;
import com.gabolle.backend.place.repository.PlaceFeatureRepository;
import com.gabolle.backend.place.repository.PlaceRepository;
import com.gabolle.backend.place.service.OpeningHoursFilterPort;
import com.gabolle.backend.place.service.PlaceTimeFactFilterPort;
import com.gabolle.backend.place.service.PlaceTimeTablePort;
import com.gabolle.backend.recommendation.application.port.ItineraryDraft;
import com.gabolle.backend.recommendation.application.port.ItineraryDraftCommand;
import com.gabolle.backend.recommendation.application.port.ItineraryDraftCommand.PlannedPlace;
import com.gabolle.backend.trip.domain.PersonalizationScope;
import com.gabolle.backend.trip.domain.PreferenceSnapshot;
import com.gabolle.backend.trip.domain.Trip;
import com.gabolle.backend.trip.domain.TripRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 사용자가 고른 음식 취향이 일정에 <b>최소 하나는</b> 들어간다 — S15P21E201-2061.
 *
 * <p>🔴 사용자 제보(2026-10-16): 음식 취향으로 돼지국밥을 골랐는데 일정에 돼지국밥집이 없었다. 밥집 자리는 하루 끼니 수(09~18시면
 * 둘)뿐이라, 채점에서 가산점만 받은 돼지국밥집이 앞 순위의 다른 밥집 둘에 밀렸다. 규칙: 고른 음식마다 «그 음식 후보 하나를 먼저
 * 앉히고», 그 집이 앉은 날 쉬는 날이라 밀려났으면 영업하는 같은 음식 후보로 바꿔 넣는다. 후보가 하나도 없으면 로그만 남기고 일정은
 * 그대로 나간다.
 *
 * <p>스프링 컨텍스트를 띄우지 않는다 — {@code ItineraryDraftServiceTest} 와 같은 방식으로 저장소만 Mockito 로 대신한다. 장소에는
 * 좌표가 없어(전부 «지역을 모름») 날 배정은 «자리 있는 첫 날» 로 떨어지고, 이 시험이 보는 것은 끼니 칸을 누가 받느냐다.
 */
class ItineraryDraftFoodPreferenceTest {

	private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-05T00:00:00Z"), ZoneOffset.UTC);

	private static final String TRIP_ID = "trip_1";

	private static final OpeningHoursFilterPort ALWAYS_UNKNOWN =
			(placeId, at) -> OpeningHoursFilterPort.Answer.NOT_COLLECTED;

	private static final PlaceTimeFactFilterPort ALWAYS_UNKNOWN_TIME_FACT = new PlaceTimeFactFilterPort() {
		@Override
		public OpeningHoursFilterPort.Answer breakTimeAt(UUID placeId, OffsetDateTime at) {
			return OpeningHoursFilterPort.Answer.NOT_COLLECTED;
		}

		@Override
		public OpeningHoursFilterPort.Answer lastOrderAt(UUID placeId, OffsetDateTime at) {
			return OpeningHoursFilterPort.Answer.NOT_COLLECTED;
		}
	};

	private TripRepository tripRepository;

	private PlaceRepository placeRepository;

	private PlaceFeatureRepository placeFeatures;

	/** 장소에 붙는 음식 종류 표식 — 시험이 {@link #tag} 로 채운다. */
	private final List<PlaceFeature> features = new ArrayList<>();

	/** 읽기 쉬운 실패 메시지를 위한 이름. */
	private final Map<UUID, String> names = new HashMap<>();

	private ch.qos.logback.classic.Logger logbackLogger;

	private ListAppender<ILoggingEvent> logs;

	private Level originalLevel;

	@BeforeEach
	void setUp() {
		this.tripRepository = mock(TripRepository.class);
		this.placeRepository = mock(PlaceRepository.class);
		this.placeFeatures = mock(PlaceFeatureRepository.class);
		when(this.placeFeatures.findByPlaceIdIn(anyCollection())).thenReturn(this.features);
		// 좌표를 모르는 장소만 다룬다 — 거리는 전부 «모름» 이다.
		when(this.placeRepository.findByPlaceIdIn(anyCollection())).thenReturn(List.of());
		when(this.tripRepository.findById(TRIP_ID)).thenReturn(Optional.of(oneDayTrip()));

		this.logbackLogger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(ItineraryDraftService.class);
		this.originalLevel = this.logbackLogger.getLevel();
		this.logbackLogger.setLevel(Level.INFO);
		this.logs = new ListAppender<>();
		this.logs.start();
		this.logbackLogger.addAppender(this.logs);
	}

	@AfterEach
	void tearDown() {
		this.logbackLogger.detachAppender(this.logs);
		this.logbackLogger.setLevel(this.originalLevel);
	}

	// ── 시험 재료 ────────────────────────────────────────────────────────────

	/** 09~18시 하루 여행 — 끼니는 점심·저녁 둘이다. */
	private static Trip oneDayTrip() {
		return new Trip(TRIP_ID, "usr_1", LocalDate.of(2026, 9, 18), LocalDate.of(2026, 9, 18), null, null, null, 2,
				"09:00-18:00", "Asia/Seoul", null, LocalTime.of(9, 0), LocalTime.of(18, 0), Instant.now());
	}

	private ItineraryDraftService service(OpeningHoursFilterPort openingHours, boolean withFeatureReader) {
		@SuppressWarnings("unchecked")
		ObjectProvider<TravelTimePort> noTravelTime = mock(ObjectProvider.class);
		when(noTravelTime.getIfAvailable()).thenReturn(null);
		@SuppressWarnings("unchecked")
		ObjectProvider<RouteOrderPort> noRouteOrder = mock(ObjectProvider.class);
		when(noRouteOrder.getIfAvailable()).thenReturn(null);
		ApplicationEventPublisher noEvents = (event) -> { };
		PlaceTimeTablePort tables = (placeId) -> new PlaceTimeTablePort.PlaceTimeTable() {
			@Override
			public OpeningHoursFilterPort.Answer openAt(OffsetDateTime at) {
				return openingHours.openAt(placeId, at);
			}

			@Override
			public OpeningHoursFilterPort.Answer breakTimeAt(OffsetDateTime at) {
				return ALWAYS_UNKNOWN_TIME_FACT.breakTimeAt(placeId, at);
			}

			@Override
			public OpeningHoursFilterPort.Answer lastOrderAt(OffsetDateTime at) {
				return ALWAYS_UNKNOWN_TIME_FACT.lastOrderAt(placeId, at);
			}
		};
		ItineraryDraftService service = new ItineraryDraftService(this.tripRepository, mock(ItineraryRepository.class), CLOCK,
				4, 3, "FOOD", 1, new ItineraryLegPlanner(this.placeRepository, noTravelTime), tables, noRouteOrder,
				this.placeRepository, noEvents);
		if (withFeatureReader) {
			service.setPlaceFeatures(this.placeFeatures);
		}
		return service;
	}

	private ItineraryDraftService service() {
		return service(ALWAYS_UNKNOWN, true);
	}

	private PlannedPlace place(String name, int rank, String category) {
		UUID id = UUID.randomUUID();
		this.names.put(id, name);
		return new PlannedPlace(id, rank, List.of("REASON"), List.of(), category);
	}

	/** 순위 차례의 후보 — 이름이 곧 갈래·번호다. */
	private List<PlannedPlace> places(String... categories) {
		List<PlannedPlace> out = new ArrayList<>();
		for (int i = 0; i < categories.length; i++) {
			out.add(place(categories[i] + "-" + (i + 1), i + 1, categories[i]));
		}
		return out;
	}

	/** 이 장소에 음식 종류 표식(「있다고 확인됨」)을 붙인다. */
	private void tag(PlannedPlace place, String... cuisines) {
		for (String cuisine : cuisines) {
			this.features.add(PlaceFeature.imported(UUID.randomUUID(), place.placeId(), "CUISINE_TAG", cuisine, "true",
					PlaceEvidenceStatus.VERIFIED, "TEST", "t-1", null, null, OffsetDateTime.parse("2026-09-05T00:00:00Z")));
		}
	}

	/** 여행의 선호 판에 음식 취향을 «골랐다» 고 남긴다. */
	private void choose(String... foods) {
		String json = "[" + String.join(",", java.util.Arrays.stream(foods).map((f) -> "\"" + f + "\"").toList()) + "]";
		PreferenceSnapshot snapshot = new PreferenceSnapshot("snap-1", TRIP_ID, 1,
				List.of(new PreferenceSnapshot.PreferenceAnswer("FOOD_PREFERENCE", json, PreferenceSnapshot.AnswerStatus.SELECTED)),
				PersonalizationScope.TRIP, List.of(), Instant.parse("2026-09-05T00:00:00Z"));
		when(this.tripRepository.findLatestSnapshot(TRIP_ID)).thenReturn(Optional.of(snapshot));
	}

	private static ItineraryDraftCommand commandOf(List<PlannedPlace> places) {
		return new ItineraryDraftCommand(UUID.randomUUID(), TRIP_ID, "usr_1", places,
				"model-1", "feature-1", "ontology-1", "policy-1", "dataset-1");
	}

	private static List<UUID> idsOf(ItineraryDraft draft) {
		return draft.items().stream().map(ItineraryDraft.DraftItem::placeId).toList();
	}

	private long foodCount(ItineraryDraft draft, List<PlannedPlace> places) {
		Map<UUID, String> category = new HashMap<>();
		places.forEach((p) -> category.put(p.placeId(), p.category()));
		return draft.items().stream().filter((item) -> "FOOD".equals(category.get(item.placeId()))).count();
	}

	private String named(List<UUID> ids) {
		return ids.stream().map((id) -> this.names.getOrDefault(id, "?")).toList().toString();
	}

	private List<String> warnings() {
		return this.logs.list.stream().filter((e) -> e.getLevel() == Level.WARN).map(ILoggingEvent::getFormattedMessage).toList();
	}

	// ── 핵심: 고른 음식이 밥 칸을 받는다 ─────────────────────────────────────

	@Test
	@DisplayName("🔴 돼지국밥을 골랐으면 앞 순위 밥집 넷이 있어도 돼지국밥집이 끼니 칸을 받는다 — 밥집은 여전히 둘이다")
	void aChosenCuisineGetsAMealSlotEvenWhenHigherRankedRestaurantsWouldFillThem() {
		List<PlannedPlace> places = places("FOOD", "FOOD", "FOOD", "FOOD", "FOOD", "CULTURE_TEMPLE", "NATURE_WALK");
		PlannedPlace porkSoup = places.get(4); // 순위 5위 — 끼니 칸 둘은 순위 1·2위 밥집 몫이었다
		tag(porkSoup, "PORK_SOUP");
		choose("PORK_SOUP");

		ItineraryDraft draft = service().assemble(commandOf(places));

		assertThat(idsOf(draft)).as(named(idsOf(draft))).contains(porkSoup.placeId());
		assertThat(foodCount(draft, places)).as("끼니 칸은 그대로 둘").isEqualTo(2);
		assertThat(draft.items()).hasSize(4);
		assertThat(idsOf(draft)).as("순위 1위 밥집도 남은 칸을 받는다").contains(places.get(0).placeId());
		assertThat(warnings()).as("덮였으니 로그가 없다").isEmpty();
	}

	@Test
	@DisplayName("(기준선) 음식을 안 골랐으면 전과 똑같다 — 순위 5위 돼지국밥집은 밀려난다")
	void withoutAChoiceTheSeatingIsExactlyAsBefore() {
		List<PlannedPlace> places = places("FOOD", "FOOD", "FOOD", "FOOD", "FOOD", "CULTURE_TEMPLE", "NATURE_WALK");
		tag(places.get(4), "PORK_SOUP");
		// choose() 를 안 부른다 — 선호 판이 없다.

		ItineraryDraft draft = service().assemble(commandOf(places));

		assertThat(idsOf(draft)).doesNotContain(places.get(4).placeId());
		assertThat(foodCount(draft, places)).isEqualTo(2);
	}

	@Test
	@DisplayName("음식 표식을 읽는 문이 없는 컨텍스트(시험용 조립)면 아무것도 안 바뀐다 — 고른 음식이 있어도 던지지 않는다")
	void withoutAFeatureReaderNothingChanges() {
		List<PlannedPlace> places = places("FOOD", "FOOD", "FOOD", "FOOD", "FOOD", "CULTURE_TEMPLE", "NATURE_WALK");
		tag(places.get(4), "PORK_SOUP");
		choose("PORK_SOUP");

		ItineraryDraft draft = service(ALWAYS_UNKNOWN, false).assemble(commandOf(places));

		assertThat(idsOf(draft)).doesNotContain(places.get(4).placeId());
		assertThat(draft.items()).hasSize(4);
	}

	@Test
	@DisplayName("🔴 음식 둘을 골랐고 끼니가 둘이면 둘 다 일정에 든다 — 순위 1위 밥집이 밀려도 고른 것이 먼저다")
	void bothChosenCuisinesAreSeatedWhenThereAreEnoughMeals() {
		List<PlannedPlace> places = places("FOOD", "FOOD", "FOOD", "FOOD", "FOOD", "FOOD", "CULTURE_TEMPLE", "NATURE_WALK");
		PlannedPlace porkSoup = places.get(4);
		PlannedPlace milmyeon = places.get(5);
		tag(porkSoup, "PORK_SOUP");
		tag(milmyeon, "MILMYEON");
		choose("PORK_SOUP", "MILMYEON");

		ItineraryDraft draft = service().assemble(commandOf(places));

		assertThat(idsOf(draft)).contains(porkSoup.placeId(), milmyeon.placeId());
		assertThat(foodCount(draft, places)).isEqualTo(2);
		assertThat(warnings()).isEmpty();
	}

	@Test
	@DisplayName("한 곳이 고른 음식 둘의 표식을 다 가졌으면 한 자리로 둘 다 덮고, 남은 끼니 칸은 순위대로 간다")
	void onePlaceWithTwoTagsCoversBothAndTheOtherMealGoesByRank() {
		List<PlannedPlace> places = places("FOOD", "FOOD", "FOOD", "FOOD", "FOOD", "CULTURE_TEMPLE", "NATURE_WALK");
		PlannedPlace both = places.get(4);
		tag(both, "PORK_SOUP", "MILMYEON");
		choose("PORK_SOUP", "MILMYEON");

		ItineraryDraft draft = service().assemble(commandOf(places));

		assertThat(idsOf(draft)).contains(both.placeId(), places.get(0).placeId());
		assertThat(foodCount(draft, places)).isEqualTo(2);
		assertThat(warnings()).as("둘 다 덮였다").isEmpty();
	}

	@Test
	@DisplayName("고른 음식이 끼니 수보다 많으면 고른 차례대로 앉고, 못 앉은 음식은 «자리가 없어서» 라고 로그에 남는다")
	void moreChosenCuisinesThanMealsKeepTheFirstChosenAndLogTheRest() {
		List<PlannedPlace> places = places("FOOD", "FOOD", "FOOD", "FOOD", "FOOD", "FOOD", "CULTURE_TEMPLE", "NATURE_WALK");
		PlannedPlace seafood = places.get(3);
		PlannedPlace porkSoup = places.get(4);
		PlannedPlace milmyeon = places.get(5);
		tag(seafood, "SEAFOOD");
		tag(porkSoup, "PORK_SOUP");
		tag(milmyeon, "MILMYEON");
		choose("SEAFOOD", "PORK_SOUP", "MILMYEON");

		ItineraryDraft draft = service().assemble(commandOf(places));

		assertThat(idsOf(draft)).contains(seafood.placeId(), porkSoup.placeId()).doesNotContain(milmyeon.placeId());
		assertThat(foodCount(draft, places)).isEqualTo(2);
		assertThat(warnings()).hasSize(1).first().asString().contains("MILMYEON").contains("자리");
	}

	// ── 후보가 없으면 지어내지 않는다 ────────────────────────────────────────

	@Test
	@DisplayName("🔴 고른 음식의 후보가 하나도 없으면 일정은 전과 똑같이 나가고, 그 사실이 로그에 남는다")
	void noCandidateOfTheChosenCuisineLeavesTheItineraryUnchangedAndLogsIt() {
		List<PlannedPlace> places = places("FOOD", "FOOD", "FOOD", "FOOD", "FOOD", "CULTURE_TEMPLE", "NATURE_WALK");
		tag(places.get(4), "PORK_SOUP"); // 다른 음식 표식은 있지만 고른 시장 먹거리는 없다
		ItineraryDraftService service = service();
		List<UUID> baseline = idsOf(service.assemble(commandOf(places)));

		choose("MARKET");
		ItineraryDraft draft = service.assemble(commandOf(places));

		assertThat(idsOf(draft)).isEqualTo(baseline);
		assertThat(warnings()).hasSize(1).first().asString().contains("MARKET").contains("하나도 없어");
	}

	@Test
	@DisplayName("식단 낱말(채식)만 고른 여행은 «찾을 음식» 이 없다 — 전과 같고 로그도 없다")
	void dietWordsAloneChangeNothing() {
		List<PlannedPlace> places = places("FOOD", "FOOD", "FOOD", "FOOD", "FOOD", "CULTURE_TEMPLE", "NATURE_WALK");
		ItineraryDraftService service = service();
		List<UUID> baseline = idsOf(service.assemble(commandOf(places)));

		choose("VEGETARIAN");

		assertThat(idsOf(service.assemble(commandOf(places)))).isEqualTo(baseline);
		assertThat(warnings()).isEmpty();
	}

	// ── 앉은 날 쉬는 집은 영업하는 같은 음식으로 바꾼다 ──────────────────────

	@Test
	@DisplayName("🔴 먼저 앉힌 돼지국밥집이 그 날 쉬어서 칸에서 밀려나면, 영업하는 다른 돼지국밥집으로 바꿔 넣는다")
	void aClosedChosenPlaceIsReplacedByAnOpenOneOfTheSameCuisine() {
		List<PlannedPlace> places = places("FOOD", "FOOD", "FOOD", "FOOD", "FOOD", "FOOD", "CULTURE_TEMPLE", "NATURE_WALK");
		PlannedPlace closedPorkSoup = places.get(4); // 순위가 높은 쪽이 먼저 앉지만 쉬는 날이다
		PlannedPlace openPorkSoup = places.get(5);
		tag(closedPorkSoup, "PORK_SOUP");
		tag(openPorkSoup, "PORK_SOUP");
		choose("PORK_SOUP");

		ItineraryDraft draft = service((placeId, at) -> placeId.equals(closedPorkSoup.placeId())
				? OpeningHoursFilterPort.Answer.CLOSED : OpeningHoursFilterPort.Answer.OPEN, true)
				.assemble(commandOf(places));

		assertThat(idsOf(draft)).as(named(idsOf(draft))).contains(openPorkSoup.placeId()).doesNotContain(closedPorkSoup.placeId());
		assertThat(foodCount(draft, places)).isEqualTo(2);
		assertThat(draft.items()).allSatisfy((item) -> assertThat(item.warningCodes()).doesNotContain("OPENING_HOURS_CLOSED"));
		assertThat(warnings()).isEmpty();
	}

	@Test
	@DisplayName("같은 음식 후보가 전부 쉬는 날이면 억지로 바꾸지 않는다 — 일정은 나가고 «자리가 없어서» 로그만 남는다")
	void ifEveryCandidateIsClosedNothingIsForced() {
		List<PlannedPlace> places = places("FOOD", "FOOD", "FOOD", "FOOD", "FOOD", "CULTURE_TEMPLE", "NATURE_WALK");
		PlannedPlace closedPorkSoup = places.get(4);
		tag(closedPorkSoup, "PORK_SOUP");
		choose("PORK_SOUP");

		ItineraryDraft draft = service((placeId, at) -> placeId.equals(closedPorkSoup.placeId())
				? OpeningHoursFilterPort.Answer.CLOSED : OpeningHoursFilterPort.Answer.OPEN, true)
				.assemble(commandOf(places));

		assertThat(idsOf(draft)).doesNotContain(closedPorkSoup.placeId());
		assertThat(draft.items()).hasSize(4);
		assertThat(warnings()).hasSize(1).first().asString().contains("PORK_SOUP");
	}
}
