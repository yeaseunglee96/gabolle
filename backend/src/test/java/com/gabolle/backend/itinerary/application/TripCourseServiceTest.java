package com.gabolle.backend.itinerary.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
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
import com.gabolle.backend.recommendation.application.RecommendationJobSucceeded;
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
		when(this.job.getUserId()).thenReturn(UUID.fromString(USER));
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
						"2026-10-04T09:46:00+09:00", "2026-10-04T10:46:00+09:00", "장소", null, 5_000, 300, false, "ESTIMATED", null, null,
						place.toString(), 10, "ESTIMATED", null, null, List.of(), List.of(), null, 35.1, 129.0))
				.toList();
		return new ItineraryDetailResponse(id, "부산 여행", 1, List.of(new ItineraryDetailResponse.Day("2026-10-04", items)),
				places.isEmpty() ? null : 5_000 * places.size(), places.isEmpty() ? null : 300 * places.size(), null,
				"OWNER", true, List.of(), TRIP, 0, 2, null);
	}

	/** 순위 번호로 정해지는 장소 번호 — 같은 순위는 늘 같은 장소다. */
	private static UUID place(int rank) {
		return new UUID(0L, rank);
	}

	// ── 추천이 끝나면 미리 짠다 (S15P21E201-1604) ─────────────────────────

	/** 추천 직후 처음 여는 화면이 코스 목록이라, 거기서 짜면 첫 부름이 약 3초였다. */
	@Test
	@DisplayName("🔴 추천 성공 알림을 받으면 2안·3안을 짜 두고, 이어서 연 목록은 다시 짜지 않는다")
	void prewarmBuildsOnceAndTheListReusesIt() {
		givenRankedPool(9);

		this.service.prewarm(new RecommendationJobSucceeded(REQUEST));
		int afterPrewarm = this.assembled.size();
		this.service.list(TRIP, USER);

		assertThat(afterPrewarm).as("알림을 받고도 안 짰다").isPositive();
		assertThat(this.assembled).as("목록이 또 짰다 — 미리 짠 것을 안 썼다").hasSize(afterPrewarm);
	}

	@Test
	@DisplayName("🔴 미리 짜기가 실패해도 예외가 새지 않는다 — 추천은 이미 성공했고 첫 부름에 다시 짠다")
	void aFailingPrewarmIsSwallowed() {
		givenRankedPool(9);
		when(this.tripQueryService.get(TRIP, USER)).thenThrow(new IllegalStateException("여행을 못 읽음"));

		this.service.prewarm(new RecommendationJobSucceeded(REQUEST));

		assertThat(this.assembled).isEmpty();
	}

	@Test
	@DisplayName("모르는 추천 판이면 아무것도 안 한다")
	void anUnknownRequestIsIgnored() {
		this.service.prewarm(new RecommendationJobSucceeded(UUID.randomUUID()));

		assertThat(this.assembled).isEmpty();
	}

	// ── 2안·3안 초안을 한 번만 짠다 (S15P21E201-1598) ─────────────────────

	/** 손으로 옮기는 시계 — 30분이 지난 뒤를 재현한다. */
	private static final class MovableClock extends Clock {

		private Instant now = Instant.parse("2026-09-25T00:00:00Z");

		void advance(Duration by) {
			this.now = this.now.plus(by);
		}

		@Override
		public ZoneOffset getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(java.time.ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return this.now;
		}
	}

	private TripCourseService serviceWith(Clock clock) {
		return new TripCourseService(this.tripQueryService, this.jobRepository, this.candidateRepository,
				this.itineraryRepository, this.draftService, this.queryService, mock(PlaceRepository.class),
				mock(TripSeedPlaceRepository.class), clock);
	}

	/**
	 * 운영에서 코스 목록이 3.7초, 겹쳐 부르면 12.8초였다 — 부를 때마다 2안·3안을 처음부터 다시 짰고, 조립은 날마다
	 * 경로 최적화기(파이썬)를 띄운다. 같은 추천 판이면 초안은 같으니 한 번만 짠다.
	 */
	@Test
	@DisplayName("🔴 코스 목록을 두 번 불러도 2안·3안은 한 번만 짠다 — 같은 추천 판이면 초안이 같다")
	void listAssemblesAlternativesOnlyOnce() {
		givenRankedPool(9);

		TripCoursesResponse first = this.service.list(TRIP, USER);
		int afterFirst = this.assembled.size();
		TripCoursesResponse second = this.service.list(TRIP, USER);

		assertThat(afterFirst).isPositive();
		assertThat(this.assembled).as("두 번째 부름에서 조립이 또 돌았다").hasSize(afterFirst);
		assertThat(second.courses()).extracting(TripCoursesResponse.Course::id)
				.containsExactlyElementsOf(first.courses().stream().map(TripCoursesResponse.Course::id).toList());
	}

	/** 초안에 실린 요청자가 고른 안의 만든 사람이 된다 — 남이 짠 초안을 내 것으로 저장하면 안 된다. */
	@Test
	@DisplayName("요청자가 다르면 따로 짠다")
	void anotherRequesterGetsItsOwnDrafts() {
		givenRankedPool(9);
		String other = UUID.randomUUID().toString();
		TripQueryService.View sameTrip = this.tripQueryService.get(TRIP, USER);
		when(this.tripQueryService.get(TRIP, other)).thenReturn(sameTrip);
		when(this.queryService.getDetail(anyString(), eq(other)))
				.thenAnswer(invocation -> detail(invocation.getArgument(0), List.of(place(1), place(2), place(3))));

		this.service.list(TRIP, USER);
		int afterFirst = this.assembled.size();
		this.service.list(TRIP, other);

		assertThat(this.assembled).hasSize(afterFirst * 2);
		assertThat(this.assembled.get(afterFirst).userId()).isEqualTo(other);
	}

	@Test
	@DisplayName("30분이 지나면 다시 짠다 — 이동 시간·영업시간 같은 바깥 자료가 새로 들어오면 언젠가는 반영된다")
	void draftsAreRebuiltAfterTheTtl() {
		givenRankedPool(9);
		MovableClock clock = new MovableClock();
		TripCourseService timed = serviceWith(clock);

		timed.list(TRIP, USER);
		int afterFirst = this.assembled.size();
		clock.advance(TripCourseService.ALTERNATIVES_TTL.minusSeconds(1));
		timed.list(TRIP, USER);
		assertThat(this.assembled).as("30분 안에는 다시 안 짠다").hasSize(afterFirst);

		clock.advance(Duration.ofSeconds(2));
		timed.list(TRIP, USER);
		assertThat(this.assembled).hasSize(afterFirst * 2);
	}

	@Test
	@DisplayName("🔴 목록을 본 뒤 고르면 새로 짜지 않고 미리 본 그 초안을 저장한다")
	void choosingAfterListingSavesThePreviewedDraft() {
		givenRankedPool(9);
		this.service.list(TRIP, USER);
		int afterList = this.assembled.size();

		this.service.choose(TRIP, REQUEST + ":1", USER);

		assertThat(this.assembled).as("고를 때 조립이 또 돌았다").hasSize(afterList);
		ArgumentCaptor<ItineraryDraft> saved = ArgumentCaptor.forClass(ItineraryDraft.class);
		verify(this.draftService).persistAlternative(saved.capture(), eq("course:" + REQUEST + ":1"));
		assertThat(saved.getValue().items()).extracting(ItineraryDraft.DraftItem::placeId)
				.containsExactly(place(4), place(5), place(6));
	}

	/** 앱이 같은 요청을 겹쳐 보내면 같은 계산이 동시에 여러 번 돌아 12.8초까지 늘었다. */
	@Test
	@DisplayName("🔴 동시에 온 첫 요청들도 한 번만 짠다 — 늦게 온 쪽은 먼저 시작한 계산을 기다린다")
	void concurrentFirstRequestsAssembleOnce() throws Exception {
		givenRankedPool(9);
		CountDownLatch entered = new CountDownLatch(1);
		CountDownLatch release = new CountDownLatch(1);
		// when(...) 로 다시 걸면 기존 가짜가 빈 인자로 한 번 불린다 — doAnswer 로 건다.
		doAnswer(invocation -> {
			ItineraryDraftCommand command = invocation.getArgument(0);
			synchronized (this.assembled) {
				this.assembled.add(command);
			}
			entered.countDown();
			release.await(5, TimeUnit.SECONDS);
			return draftOf(command.places().stream().limit(SLOTS).map(PlannedPlace::placeId).toList());
		}).when(this.draftService).assemble(any());
		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			Future<TripCoursesResponse> a = pool.submit(() -> this.service.list(TRIP, USER));
			assertThat(entered.await(5, TimeUnit.SECONDS)).isTrue();
			Future<TripCoursesResponse> b = pool.submit(() -> this.service.list(TRIP, USER));
			Thread.sleep(200);
			release.countDown();

			assertThat(a.get(5, TimeUnit.SECONDS).courses()).hasSize(b.get(5, TimeUnit.SECONDS).courses().size());
		}
		finally {
			pool.shutdownNow();
		}
		assertThat(this.assembled).as("2안·3안 각 한 번씩만").hasSize(TripCourseService.ALTERNATIVES);
	}

	// ── 지금 확정된 일정 (S15P21E201-1602) ─────────────────────────────

	@Test
	@DisplayName("🔴 A안을 (다시) 고르면 A 가 확정이 된다 — 고른 시각을 지금으로 옮긴다")
	void choosingTheFirstCourseMarksItChosen() {
		this.service.choose(TRIP, REQUEST + ":0", USER);

		verify(this.itineraryRepository).markChosen(eq(BASE.toString()), any());
	}

	@Test
	@DisplayName("🔴 이미 만든 C안을 다시 고르면 C 가 확정이 된다")
	void choosingAnExistingCourseMarksItChosen() {
		givenRankedPool(9);
		givenAlreadyChosen("chosen-1", "course:" + REQUEST + ":1");

		this.service.choose(TRIP, REQUEST + ":1", USER);

		verify(this.itineraryRepository).markChosen(eq("chosen-1"), any());
	}

	@Test
	@DisplayName("새로 만드는 안은 따로 표시하지 않는다 — 만들 때 DB 기본값이 그 시각을 채운다")
	void aNewlyMadeCourseIsChosenByItsCreation() {
		givenRankedPool(9);

		this.service.choose(TRIP, REQUEST + ":1", USER);

		verify(this.itineraryRepository, never()).markChosen(anyString(), any());
		verify(this.draftService).persistAlternative(any(), eq("course:" + REQUEST + ":1"));
	}
}
