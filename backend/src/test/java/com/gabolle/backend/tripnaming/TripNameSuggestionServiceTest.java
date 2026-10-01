package com.gabolle.backend.tripnaming;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.trip.application.TripQueryService;
import com.gabolle.backend.trip.domain.Trip;
import com.gabolle.backend.trip.domain.TripMember;
import com.gabolle.backend.tripnaming.adapter.GmsTripNamer;
import com.gabolle.backend.tripnaming.application.TripNameSuggestionService;
import com.gabolle.backend.tripnaming.application.TripPlaceVocabulary;
import com.gabolle.backend.tripnaming.config.TripNamingProperties;
import com.gabolle.backend.tripnaming.presentation.dto.TripNameSuggestionsResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 여행 이름 짓기. 모델이 지어낼 수 있는 것은 가 보지도 않은 장소 이름이고, 그것이 제목에
 * 박히면 사용자는 자기가 만들지 않은 일정을 자기 것으로 기억한다.
 */
class TripNameSuggestionServiceTest {

	private static final String TRIP_ID = "trip_1";

	private static final List<String> ITINERARY = List.of(
			"해운대 해수욕장", "광안리 해수욕장", "감천문화마을");

	private final String requester = UUID.randomUUID().toString();

	private TripQueryService queryService;
	private TripPlaceVocabulary vocabulary;
	private GmsTripNamer namer;
	private TripNamingProperties properties;
	private TripNameSuggestionService service;

	@BeforeEach
	void setUp() {
		this.queryService = mock(TripQueryService.class);
		this.vocabulary = mock(TripPlaceVocabulary.class);
		this.namer = mock(GmsTripNamer.class);
		this.properties = new TripNamingProperties();
		this.service = new TripNameSuggestionService(this.queryService, this.vocabulary, this.namer,
				this.properties, Clock.fixed(Instant.parse("2026-09-16T03:00:00Z"), ZoneOffset.UTC));

		Trip trip = new Trip(TRIP_ID, UUID.randomUUID().toString(),
				LocalDate.of(2026, 9, 19), LocalDate.of(2026, 9, 20),
				null, null, null, 2, null, "Asia/Seoul", Instant.parse("2026-09-01T00:00:00Z"));
		when(this.queryService.get(eq(TRIP_ID), any()))
				.thenReturn(new TripQueryService.View(trip, List.of(), null, TripMember.Role.OWNER));
		when(this.vocabulary.placeNamesOf(TRIP_ID)).thenReturn(ITINERARY);
		when(this.namer.isConfigured()).thenReturn(true);
	}

	private TripNameSuggestionsResponse suggest() {
		return this.service.suggest(TRIP_ID, this.requester);
	}

	// ── 지어낸 장소는 화면까지 못 간다 ────────────────────────────────────

	/**
	 * 지시만으로는 못 막는다 — 지켜졌는지 확인하지 않으면 안 지켜진 것을 알 방법이 없다.
	 */
	@Test
	@DisplayName("🔴 일정에 없는 장소가 든 이름은 버리고, 몇 개 버렸는지 말한다")
	void discardsInventedPlaces() {
		when(this.namer.suggest(any(), anyInt(), anyInt())).thenReturn(List.of(
				"불국사에서 보낸 이틀",      // 이 여행에 없다
				"제주 바다 이틀",            // 다른 지역이다
				"해운대에서 보낸 이틀"));    // 멀쩡하다

		TripNameSuggestionsResponse response = suggest();

		assertThat(response.suggestions()).containsExactly("해운대에서 보낸 이틀");
		assertThat(response.source()).isEqualTo(TripNameSuggestionsResponse.MODEL);
		assertThat(response.discardedCount()).isEqualTo(2);
	}

	/**
	 * 전부 버려져도 빈 목록을 주지 않는다. 아무것도 안 나오면 사용자는 고장이라고 읽으므로,
	 * 일정에서 그대로 만든 이름을 준다.
	 */
	@Test
	@DisplayName("🔴 전부 버려져도 이름은 나온다 — 다만 모델이 지었다고 말하지 않는다")
	void fallsBackToTemplateWhenEverythingIsDiscarded() {
		when(this.namer.suggest(any(), anyInt(), anyInt()))
				.thenReturn(List.of("불국사 하루", "경주 나들이"));

		TripNameSuggestionsResponse response = suggest();

		assertThat(response.suggestions()).isNotEmpty();
		assertThat(response.source()).isEqualTo(TripNameSuggestionsResponse.TEMPLATE);
		assertThat(response.discardedCount()).isEqualTo(2);
		assertThat(response.suggestions().get(0)).isEqualTo("해운대 해수욕장 외 2곳 · 2일");
	}

	/** 고른 순간 저장이 400 으로 튕기는 이름을 후보로 내놓지 않는다. */
	@Test
	@DisplayName("🔴 저장할 수 없는 이름은 후보에서 뺀다")
	void dropsNamesThatCouldNotBeSaved() {
		when(this.namer.suggest(any(), anyInt(), anyInt())).thenReturn(List.of(
				"해운대\n두 줄짜리",
				"해".repeat(Trip.TITLE_MAX_LENGTH + 1),
				"광안리 밤"));

		TripNameSuggestionsResponse response = suggest();

		assertThat(response.suggestions()).containsExactly("광안리 밤");
		assertThat(response.discardedCount()).isEqualTo(2);
	}

	// ── 모델을 아예 안 부르는 자리 ─────────────────────────────────────────

	/**
	 * 일정이 비어 있으면 기댈 낱말이 없다. 그 상태로 부르면 나오는 이름이 전부 지어낸
	 * 것이 되므로 모델을 부르지 않는다.
	 */
	@Test
	@DisplayName("🔴 일정이 없는 여행은 모델을 아예 안 부른다")
	void doesNotCallTheModelWithoutAnItinerary() {
		when(this.vocabulary.placeNamesOf(TRIP_ID)).thenReturn(List.of());

		TripNameSuggestionsResponse response = suggest();

		verify(this.namer, never()).suggest(any(), anyInt(), anyInt());
		assertThat(response.source()).isEqualTo(TripNameSuggestionsResponse.TEMPLATE);
		assertThat(response.suggestions()).containsExactly("2026-09-19 ~ 2026-09-20");
	}

	@Test
	@DisplayName("키가 없으면 모델을 안 부르고 템플릿으로 답한다 — 기동도 호출도 실패가 아니다")
	void withoutAKeyItStillAnswers() {
		when(this.namer.isConfigured()).thenReturn(false);

		TripNameSuggestionsResponse response = suggest();

		verify(this.namer, never()).suggest(any(), anyInt(), anyInt());
		assertThat(response.source()).isEqualTo(TripNameSuggestionsResponse.TEMPLATE);
		assertThat(response.suggestions()).isNotEmpty();
	}

	/**
	 * 중계가 죽어도 실패로 끝내지 않는다. 메뉴판 읽기는 503 을 내는데, 거기서 빈 결과는
	 * 「알레르기가 없다」로 읽히기 때문이다. 여기서 템플릿 이름은 덜 멋진 이름일 뿐이다.
	 */
	@Test
	@DisplayName("🔴 중계가 죽어도 이름은 나온다 — 메뉴판과 규칙이 다른 이유가 있다")
	void vendorFailureIsNotAFailureHere() {
		when(this.namer.suggest(any(), anyInt(), anyInt())).thenReturn(List.of());

		TripNameSuggestionsResponse response = suggest();

		assertThat(response.source()).isEqualTo(TripNameSuggestionsResponse.TEMPLATE);
		assertThat(response.suggestions()).isNotEmpty();
	}

	@Test
	@DisplayName("1분 한도를 넘기면 거절하지 않고 템플릿으로 간다")
	void overTheLimitFallsBackInsteadOfFailing() {
		this.properties.setPerMinuteLimit(1);
		when(this.namer.suggest(any(), anyInt(), anyInt())).thenReturn(List.of("해운대에서 보낸 이틀"));

		assertThat(suggest().source()).isEqualTo(TripNameSuggestionsResponse.MODEL);
		assertThat(suggest().source()).isEqualTo(TripNameSuggestionsResponse.TEMPLATE);
	}

	// ── 남의 여행 ───────────────────────────────────────────────────────────

	/**
	 * 남의 여행 ID 로 부를 수 있으면 그 여행의 장소 목록이 이름 후보에 실려 새어 나간다.
	 * 이름 짓기는 읽기처럼 안 보이지만 읽기와 같은 문을 지나야 한다.
	 */
	@Test
	@DisplayName("🔴 참여자가 아니면 존재부터 감춘다 — 모델도 안 부른다")
	void nonMemberSeesNothing() {
		when(this.queryService.get(eq(TRIP_ID), any()))
				.thenThrow(new TripQueryService.TripNotFoundException(TRIP_ID));

		assertThatThrownBy(this::suggest)
				.isInstanceOf(TripQueryService.TripNotFoundException.class);

		verify(this.namer, never()).suggest(any(), anyInt(), anyInt());
	}

	// ── 영어 화면 — S15P21E201-1780(고지혁 QA) ────────────────────────────

	@Test
	@DisplayName("🔴 영어 화면이면 영어 이름으로 영어 후보를 준다 — 한국어 조사·「외 N곳」이 안 섞인다")
	void englishScreenGetsEnglishNames() {
		when(this.vocabulary.placeNamesOf(TRIP_ID, true))
				.thenReturn(List.of("Haeundae Beach", "광안리 해수욕장", "Gamcheon Culture Village"));

		TripNameSuggestionsResponse response = this.service.suggest(TRIP_ID, this.requester, true);

		assertThat(response.suggestions()).containsExactly(
				"Haeundae Beach + 2 more · 2 days", "2 days at Haeundae Beach", "2026-09-19 ~ 2026-09-20");
		assertThat(response.suggestions()).noneMatch((name) -> name.contains("외 ") || name.contains("일"));
		assertThat(response.source()).isEqualTo(TripNameSuggestionsResponse.TEMPLATE);
	}

	@Test
	@DisplayName("🔴 영어 화면이면 모델을 안 부른다 — 지어낸 장소 거르기가 한글만 보므로 영어 이름은 거를 수 없다")
	void englishScreenDoesNotCallTheModel() {
		when(this.vocabulary.placeNamesOf(TRIP_ID, true)).thenReturn(List.of("Haeundae Beach"));

		TripNameSuggestionsResponse response = this.service.suggest(TRIP_ID, this.requester, true);

		verify(this.namer, never()).suggest(any(), anyInt(), anyInt());
		assertThat(response.suggestions()).first().isEqualTo("Haeundae Beach · 2 days");
	}

	@Test
	@DisplayName("영어 화면이라도 남의 여행이면 아무것도 안 보인다")
	void englishScreenStillChecksMembership() {
		when(this.queryService.get(eq(TRIP_ID), any())).thenThrow(new TripQueryService.TripNotFoundException(TRIP_ID));

		assertThatThrownBy(() -> this.service.suggest(TRIP_ID, this.requester, true))
				.isInstanceOf(TripQueryService.TripNotFoundException.class);
	}

	// ── 일본어·중국어 화면 — S15P21E201-1916 ─────────────────────────────

	@Test
	@DisplayName("🔴 일본어 화면이면 일본어 틀 — 영어 「+ 2 more · 2 days」가 안 나온다")
	void japaneseScreenGetsJapaneseNames() {
		when(this.vocabulary.placeNamesOf(TRIP_ID, true))
				.thenReturn(List.of("Haeundae Beach", "광안리 해수욕장", "Gamcheon Culture Village"));

		TripNameSuggestionsResponse response = this.service.suggest(TRIP_ID, this.requester,
				TripNameSuggestionService.NameLanguage.JA);

		assertThat(response.suggestions()).containsExactly(
				"Haeundae Beach ほか2か所 · 2日間", "Haeundae Beach 2日間の旅", "2026-09-19 ~ 2026-09-20");
		verify(this.namer, never()).suggest(any(), anyInt(), anyInt());
	}

	@Test
	@DisplayName("🔴 간체·번체 화면이면 각자의 글자로")
	void chineseScreensGetChineseNames() {
		when(this.vocabulary.placeNamesOf(TRIP_ID, true)).thenReturn(List.of("Haeundae Beach", "광안리 해수욕장"));

		assertThat(this.service.suggest(TRIP_ID, this.requester, TripNameSuggestionService.NameLanguage.ZH_HANS)
				.suggestions()).containsExactly("Haeundae Beach 等2处 · 2天", "Haeundae Beach 2日游", "2026-09-19 ~ 2026-09-20");
		assertThat(this.service.suggest(TRIP_ID, this.requester, TripNameSuggestionService.NameLanguage.ZH_HANT)
				.suggestions()).containsExactly("Haeundae Beach 等2處 · 2天", "Haeundae Beach 2日遊", "2026-09-19 ~ 2026-09-20");
	}

	@Test
	@DisplayName("Accept-Language 첫 태그로 언어를 고른다 — zh-TW 는 번체, zh 는 간체, 모르면 한국어")
	void readsTheHeader() {
		assertThat(TripNameSuggestionService.NameLanguage.of("ja")).isEqualTo(TripNameSuggestionService.NameLanguage.JA);
		assertThat(TripNameSuggestionService.NameLanguage.of("zh-Hans")).isEqualTo(TripNameSuggestionService.NameLanguage.ZH_HANS);
		assertThat(TripNameSuggestionService.NameLanguage.of("zh")).isEqualTo(TripNameSuggestionService.NameLanguage.ZH_HANS);
		assertThat(TripNameSuggestionService.NameLanguage.of("zh-Hant")).isEqualTo(TripNameSuggestionService.NameLanguage.ZH_HANT);
		assertThat(TripNameSuggestionService.NameLanguage.of("zh-TW,en;q=0.5")).isEqualTo(TripNameSuggestionService.NameLanguage.ZH_HANT);
		assertThat(TripNameSuggestionService.NameLanguage.of("en-US")).isEqualTo(TripNameSuggestionService.NameLanguage.EN);
		assertThat(TripNameSuggestionService.NameLanguage.of(null)).isEqualTo(TripNameSuggestionService.NameLanguage.KO);
		assertThat(TripNameSuggestionService.NameLanguage.of("fr")).isEqualTo(TripNameSuggestionService.NameLanguage.KO);
	}
}
