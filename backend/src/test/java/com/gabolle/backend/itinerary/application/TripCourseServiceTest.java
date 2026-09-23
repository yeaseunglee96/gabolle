package com.gabolle.backend.itinerary.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.gabolle.backend.itinerary.domain.Itinerary;
import com.gabolle.backend.itinerary.domain.ItineraryContent;
import com.gabolle.backend.itinerary.domain.ItineraryItem;
import com.gabolle.backend.itinerary.domain.ItineraryRepository;
import com.gabolle.backend.itinerary.domain.ItineraryVersion;
import com.gabolle.backend.itinerary.presentation.dto.ItineraryDetailResponse;
import com.gabolle.backend.itinerary.presentation.dto.TripCoursesResponse;
import com.gabolle.backend.place.repository.PlaceRepository;
import com.gabolle.backend.recommendation.application.port.ItineraryDraft;
import com.gabolle.backend.recommendation.application.port.ItineraryDraftCommand;
import com.gabolle.backend.recommendation.application.port.ItineraryDraftCommand.PlannedPlace;
import com.gabolle.backend.recommendation.application.port.ItineraryHandle;
import com.gabolle.backend.recommendation.domain.JobStatus;
import com.gabolle.backend.recommendation.domain.JobType;
import com.gabolle.backend.recommendation.domain.RecommendationCandidate;
import com.gabolle.backend.recommendation.domain.RecommendationJob;
import com.gabolle.backend.recommendation.repository.RecommendationCandidateRepository;
import com.gabolle.backend.recommendation.repository.RecommendationJobRepository;
import com.gabolle.backend.trip.application.TripQueryService;
import com.gabolle.backend.trip.domain.Trip;
import com.gabolle.backend.trip.domain.TripMember;
import com.gabolle.backend.trip.domain.TripSeedPlaceRepository;

/**
 * 추천 코스 3안 (S15P21E201-1454) — 저장소는 가짜로 바꾸고 <b>판단만</b> 잰다.
 *
 * <p>가짜 조립기는 진짜처럼 <b>받은 목록의 앞에서부터</b> 자리 수만큼 앉힌다. 그래야 「안 쓴 곳을 앞에
 * 두면 다른 코스가 나온다」가 여기서 재진다. DB 에 닿는 쪽(2안 저장이 1안이 차지한 추천 번호와 부딪히지
 * 않는가)은 {@code TripCourseIntegrationTest} 가 잰다.
 */
class TripCourseServiceTest {

	private static final String TRIP = UUID.randomUUID().toString();

	private static final String USER = UUID.randomUUID().toString();

	private static final UUID REQUEST = UUID.randomUUID();

	private static final UUID BASE = UUID.randomUUID();

	/** 가짜 조립기가 앉히는 자리 수. */
	private static final int SLOTS = 3;

	private final TripQueryService tripQueryService = mock(TripQueryService.class);

	private final RecommendationJobRepository jobRepository = mock(RecommendationJobRepository.class);

	private final RecommendationCandidateRepository candidateRepository = mock(RecommendationCandidateRepository.class);

	private final ItineraryRepository itineraryRepository = mock(ItineraryRepository.class);

	private final ItineraryDraftService draftService = mock(ItineraryDraftService.class);

	private final ItineraryQueryService queryService = mock(ItineraryQueryService.class);

	private final RecommendationJob job = mock(RecommendationJob.class);

	/** 조립기가 받은 명령들 — 부른 차례대로. */
	private final List<ItineraryDraftCommand> assembled = new ArrayList<>();

	private TripCourseService service;

	@BeforeEach
	void setUp() {
		Trip trip = mock(Trip.class);
		when(trip.tripId()).thenReturn(TRIP);
		when(this.tripQueryService.get(TRIP, USER))
				.thenReturn(new TripQueryService.View(trip, List.of(), null, TripMember.Role.OWNER));

		when(this.job.getJobType()).thenReturn(JobType.ITINERARY_GENERATION);
		when(this.job.getJobStatus()).thenReturn(JobStatus.SUCCEEDED);
		when(this.job.getItineraryId()).thenReturn(BASE);
		when(this.job.getRequestId()).thenReturn(REQUEST);
		when(this.job.getTripId()).thenReturn(UUID.fromString(TRIP));
		when(this.jobRepository.findByTripIdOrderByCreatedAtDesc(eq(UUID.fromString(TRIP)), any()))
				.thenReturn(List.of(this.job));
		when(this.jobRepository.findByRequestId(REQUEST)).thenReturn(Optional.of(this.job));

		// 1안의 첫 판은 순위 1·2·3 을 썼다.
		givenFirstCourseUses(1, 2, 3);

		when(this.draftService.assemble(any())).thenAnswer(invocation -> {
			ItineraryDraftCommand command = invocation.getArgument(0);
			this.assembled.add(command);
			return draftOf(command.places().stream().limit(SLOTS).map(PlannedPlace::placeId).toList());
		});
		when(this.draftService.contentOf(any(), anyString(), any()))
				.thenReturn(new ItineraryDraftService.DraftContent(List.of(), List.of()));
		when(this.draftService.persistAlternative(any(), anyString())).thenReturn(new ItineraryHandle("made", 1));
		when(this.queryService.getDetail(anyString(), eq(USER)))
				.thenAnswer(invocation -> detail(invocation.getArgument(0), List.of(place(1), place(2), place(3))));
		when(this.queryService.preview(anyString(), any(), any(), any(), any(), any()))
				.thenAnswer(invocation -> detail(invocation.getArgument(0), List.of()));

		this.service = new TripCourseService(this.tripQueryService, this.jobRepository, this.candidateRepository,
				this.itineraryRepository, this.draftService, this.queryService, mock(PlaceRepository.class),
				mock(TripSeedPlaceRepository.class), Clock.systemUTC());
	}

	@Test
	@DisplayName("🔴 2안·3안은 앞 코스가 안 쓴 곳부터 넣는다 — 셋이 서로 다른 곳으로 채워진다")
	void alternativesStartFromPlacesTheEarlierCoursesDidNotUse() {
		givenRankedPool(9);

		TripCoursesResponse response = this.service.list(TRIP, USER);

		assertThat(response.courses()).hasSize(3);
		assertThat(firstRanksOf(this.assembled.get(0))).containsExactly(4, 5, 6);
		assertThat(firstRanksOf(this.assembled.get(1))).containsExactly(7, 8, 9);
		assertThat(response.courses()).extracting(TripCoursesResponse.Course::id)
				.containsExactly(REQUEST + ":0", REQUEST + ":1", REQUEST + ":2");
	}

	@Test
	@DisplayName("1안은 만들어진 일정, 2안·3안은 아직 일정이 없어 미리보기를 싣는다 — 화면은 그것으로 카드를 그린다")
	void onlyTheFirstCourseHasAnItineraryUntilChosen() {
		givenRankedPool(9);

		List<TripCoursesResponse.Course> courses = this.service.list(TRIP, USER).courses();

		assertThat(courses.get(0).itineraryId()).isEqualTo(BASE.toString());
		assertThat(courses.get(0).preview()).isNull();
		assertThat(courses.subList(1, 3)).allSatisfy(course -> {
			assertThat(course.itineraryId()).isNull();
			assertThat(course.preview()).isNotNull();
		});
	}

	@Test
	@DisplayName("🔴 새로 들어간 곳이 없으면 거기서 멈춘다 — 같은 코스를 이름만 바꿔 한 번 더 보여 주지 않는다")
	void stopsWhenAnAlternativeWouldAddNoNewPlace() {
		// 후보가 넷뿐이다. 2안은 4위를 새로 넣지만, 3안은 넣을 새 곳이 없다.
		givenRankedPool(4);

		TripCoursesResponse response = this.service.list(TRIP, USER);

		assertThat(response.courses()).hasSize(2);
	}

	@Test
	@DisplayName("2안 조립이 실패해도 화면 전체를 오류로 만들지 않는다 — 1안은 그대로 보인다")
	void aFailedAlternativeStillShowsTheFirstCourse() {
		givenRankedPool(9);
		// 이미 답을 정해 둔 가짜라 when(...) 으로 다시 정하면 앞의 답이 한 번 돈다 — doThrow 로 바꾼다.
		doThrow(new IllegalStateException("경로 계산 실패")).when(this.draftService).assemble(any());

		TripCoursesResponse response = this.service.list(TRIP, USER);

		assertThat(response.courses()).hasSize(1);
		assertThat(response.courses().get(0).itineraryId()).isEqualTo(BASE.toString());
	}

	@Test
	@DisplayName("요약 숫자는 화면이 일정에서 세던 방식과 같다 — 1안의 숫자가 이 경로 때문에 달라지면 안 된다")
	void summaryIsCountedTheWayTheAppCountedIt() {
		givenRankedPool(3);

		TripCoursesResponse.Course first = this.service.list(TRIP, USER).courses().get(0);

		// 곳마다 이동 10분 · 걷기 300m · 5,000원 (detail 참고)
		assertThat(first.summary()).isEqualTo(new TripCoursesResponse.Summary(3, 30, 0.9, 15_000));
		assertThat(first.days().get(0).stops().get(0).time()).isEqualTo("09:46");
		assertThat(first.title()).as("서버는 제목을 비운다 — 화면이 사용자 언어로 「코스 A」를 붙인다").isEmpty();
	}

	@Test
	@DisplayName("고르면 미리 본 것과 같은 입력으로 조립해 저장한다 — 판에 이 안의 꼬리표를 적는다")
	void choosingBuildsTheSameDraftThatWasPreviewed() {
		givenRankedPool(9);

		String itineraryId = this.service.choose(TRIP, REQUEST + ":2", USER);

		assertThat(itineraryId).isEqualTo("made");
		ArgumentCaptor<ItineraryDraft> saved = ArgumentCaptor.forClass(ItineraryDraft.class);
		verify(this.draftService).persistAlternative(saved.capture(), eq("course:" + REQUEST + ":2"));
		assertThat(saved.getValue().items()).extracting(ItineraryDraft.DraftItem::placeId)
				.containsExactly(place(7), place(8), place(9));
	}

	@Test
	@DisplayName("🔴 같은 안을 두 번 고르면 새로 만들지 않고 이미 만든 일정을 준다 — 목록도 그 일정으로 그린다")
	void choosingTwiceReturnsTheItineraryAlreadyMade() {
		givenRankedPool(9);
		givenAlreadyChosen("chosen-1", "course:" + REQUEST + ":1");

		assertThat(this.service.choose(TRIP, REQUEST + ":1", USER)).isEqualTo("chosen-1");
		verify(this.draftService, never()).persistAlternative(any(), anyString());

		TripCoursesResponse.Course second = this.service.list(TRIP, USER).courses().get(1);
		assertThat(second.itineraryId()).isEqualTo("chosen-1");
		assertThat(second.preview()).isNull();
	}

	@Test
	@DisplayName("1안을 고르면 추천이 만든 일정을 그대로 준다")
	void choosingTheFirstCourseReturnsTheRecommendedItinerary() {
		assertThat(this.service.choose(TRIP, REQUEST + ":0", USER)).isEqualTo(BASE.toString());
		verify(this.draftService, never()).persistAlternative(any(), anyString());
	}

	@Test
	@DisplayName("🔴 남의 여행의 코스 번호와 엉터리 번호는 둘 다 없는 코스다")
	void aCourseOfAnotherTripIsNotFound() {
		givenRankedPool(9);
		when(this.job.getTripId()).thenReturn(UUID.randomUUID());

		assertThatThrownBy(() -> this.service.choose(TRIP, REQUEST + ":1", USER))
				.isInstanceOf(TripCourseService.CourseNotFoundException.class);
		assertThatThrownBy(() -> this.service.choose(TRIP, "엉터리", USER))
				.isInstanceOf(TripCourseService.CourseNotFoundException.class);
		assertThatThrownBy(() -> this.service.choose(TRIP, REQUEST + ":9", USER))
				.isInstanceOf(TripCourseService.CourseNotFoundException.class);
		verify(this.draftService, never()).persistAlternative(any(), anyString());
	}

	// ---- 준비 ----

	private void givenRankedPool(int count) {
		List<RecommendationCandidate> rows = new ArrayList<>();
		for (int rank = 1; rank <= count; rank++) {
			RecommendationCandidate row = mock(RecommendationCandidate.class);
			when(row.isEligible()).thenReturn(true);
			when(row.getFinalRank()).thenReturn(rank);
			when(row.getPlaceId()).thenReturn(place(rank));
			when(row.getReasonCodes()).thenReturn(new String[0]);
			when(row.getWarningCodes()).thenReturn(new String[0]);
			rows.add(row);
		}
		when(this.candidateRepository.findByRequestIdOrderByFinalRankAscPlaceIdAsc(REQUEST)).thenReturn(rows);
	}

	private void givenFirstCourseUses(int... ranks) {
		List<ItineraryItem> items = new ArrayList<>();
		for (int rank : ranks) {
			ItineraryItem item = mock(ItineraryItem.class);
			when(item.placeId()).thenReturn(place(rank).toString());
			items.add(item);
		}
		when(this.itineraryRepository.findContent(BASE.toString(), 1))
				.thenReturn(Optional.of(new ItineraryContent(null, items, List.of(), List.of())));
	}

	private void givenAlreadyChosen(String itineraryId, String label) {
		when(this.itineraryRepository.findByTripId(TRIP)).thenReturn(List.of(new Itinerary(itineraryId, TRIP, 1)));
		ItineraryVersion version = mock(ItineraryVersion.class);
		when(version.requestId()).thenReturn(label);
		when(this.itineraryRepository.findVersion(itineraryId, 1)).thenReturn(Optional.of(version));
	}

	private static List<Integer> firstRanksOf(ItineraryDraftCommand command) {
		return command.places().stream().limit(SLOTS).map(PlannedPlace::rank).toList();
	}

	private static ItineraryDraft draftOf(List<UUID> places) {
		List<ItineraryDraft.DraftItem> items = new ArrayList<>();
		for (int i = 0; i < places.size(); i++) {
			items.add(new ItineraryDraft.DraftItem(0, LocalDate.of(2026, 10, 4), i + 1, places.get(i), UUID.randomUUID(),
					LocalTime.of(9, 0), LocalTime.of(10, 0), 60, "ESTIMATED", List.of(), List.of()));
		}
		return new ItineraryDraft(TRIP, USER, REQUEST, null, null, null, null, null, items, List.of(), List.of());
	}

	/** 곳마다 이동 10분 · 걷기 300m · 5,000원. */
	private static ItineraryDetailResponse detail(String id, List<UUID> places) {
		List<ItineraryDetailResponse.Item> items = places.stream()
				.map(place -> new ItineraryDetailResponse.Item(UUID.randomUUID().toString(),
						"2026-10-04T09:46:00+09:00", "장소", null, 5_000, 300, false, "ESTIMATED", null, null,
						place.toString(), 10, "ESTIMATED", null, null, List.of(), 35.1, 129.0))
				.toList();
		return new ItineraryDetailResponse(id, "부산 여행", 1, List.of(new ItineraryDetailResponse.Day("2026-10-04", items)),
				places.isEmpty() ? null : 5_000 * places.size(), places.isEmpty() ? null : 300 * places.size(), null,
				"OWNER", true, List.of(), TRIP, 0, 2);
	}

	/** 순위 번호로 정해지는 장소 번호 — 같은 순위는 늘 같은 장소다. */
	private static UUID place(int rank) {
		return new UUID(0L, rank);
	}
}
