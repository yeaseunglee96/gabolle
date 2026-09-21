package com.gabolle.backend.itinerary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.RecordComponent;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.itinerary.application.ActorNames;
import com.gabolle.backend.itinerary.application.ItineraryAccess;
import com.gabolle.backend.itinerary.application.ItineraryActualTimeService;
import com.gabolle.backend.itinerary.application.ItineraryQueryService;
import com.gabolle.backend.itinerary.domain.Itinerary;
import com.gabolle.backend.itinerary.domain.ItineraryItem;
import com.gabolle.backend.itinerary.domain.ItineraryItemActual;
import com.gabolle.backend.itinerary.domain.ItineraryRevision;
import com.gabolle.backend.itinerary.domain.ItineraryVersion;
import com.gabolle.backend.itinerary.infra.InMemoryItineraryRepository;
import com.gabolle.backend.itinerary.presentation.ItineraryQueryController;
import com.gabolle.backend.itinerary.presentation.dto.ItineraryDetailResponse;
import com.gabolle.backend.itinerary.support.FakeItineraryItemActualRepository;
import com.gabolle.backend.place.domain.Place;
import com.gabolle.backend.place.repository.PlaceRepository;
import com.gabolle.backend.recommendation.repository.RecommendationJobRepository;
import com.gabolle.backend.trip.application.TripQueryService;
import com.gabolle.backend.trip.domain.Trip;
import com.gabolle.backend.trip.domain.TripMember;

/**
 * 방문지 실제 도착·출발 시각. 기록과 조회를 이어서 본다.
 *
 * <p>Spring 컨텍스트도 DB 도 띄우지 않는다. 저장소는 {@link InMemoryItineraryRepository} 와
 * {@link FakeItineraryItemActualRepository} 를 쓰고, {@link ItineraryAccess} 는 진짜
 * 객체다 — 권한 판정 자체가 이 테스트로 검증된다.
 *
 * <p>검증하지 못하는 것: HTTP 상태·오류 코드, {@code INSERT ... ON CONFLICT} SQL,
 * TIMESTAMPTZ 왕복. 그쪽은 {@link ItineraryActualTimeIntegrationTest} 가 본다.
 */
class ItineraryActualTimeServiceTest {

	/** 여행 시작일. 오늘보다 지난 날짜여야 한다. */
	private static final LocalDate PAST_DAY = LocalDate.of(2026, 9, 1);

	/** 마지막 날. 기록이 없는 방문지를 여기 둔다. */
	private static final LocalDate LAST_DAY = LocalDate.of(2026, 9, 10);

	private final String tripId = "trip_293";

	private final String ownerId = "usr_owner";

	private final String viewerId = "usr_viewer";

	private final String strangerId = "usr_stranger";

	private final UUID placeId = UUID.randomUUID();

	private final String visitedItemKey = "itemkey_visited";

	private final String untouchedItemKey = "itemkey_untouched";

	private InMemoryItineraryRepository itineraryRepository;

	private FakeItineraryItemActualRepository actualRepository;

	private ItineraryActualTimeService service;

	private ItineraryQueryService queryService;

	private String itineraryId;

	@BeforeEach
	void setUp() {
		this.itineraryRepository = new InMemoryItineraryRepository();
		this.actualRepository = new FakeItineraryItemActualRepository();

		TripQueryService tripQueryService = mock(TripQueryService.class);
		ItineraryAccess itineraryAccess = new ItineraryAccess(this.itineraryRepository, tripQueryService);

		PlaceRepository placeRepository = mock(PlaceRepository.class);
		Place place = mock(Place.class);
		when(place.getPlaceId()).thenReturn(this.placeId);
		when(place.getNameKo()).thenReturn("해운대해수욕장");
		when(placeRepository.findByPlaceIdIn(any())).thenReturn(List.of(place));

		this.service = new ItineraryActualTimeService(itineraryAccess, this.itineraryRepository,
				this.actualRepository);
		this.queryService = new ItineraryQueryService(this.itineraryRepository, itineraryAccess, placeRepository,
				mock(RecommendationJobRepository.class), mock(ActorNames.class), this.actualRepository);

		Trip trip = new Trip(this.tripId, this.ownerId, PAST_DAY, LAST_DAY,
				null, null, null, 1, null, "Asia/Seoul", Instant.now());

		when(tripQueryService.get(this.tripId, this.ownerId))
				.thenReturn(new TripQueryService.View(trip, List.of(), null, TripMember.Role.OWNER));
		when(tripQueryService.get(this.tripId, this.viewerId))
				.thenReturn(new TripQueryService.View(trip, List.of(), null, TripMember.Role.VIEWER));
		// 비회원은 여행 조회 자체가 404 다 — 그 일정이 있다는 사실도 알려주지 않는다.
		when(tripQueryService.get(this.tripId, this.strangerId))
				.thenThrow(new TripQueryService.TripNotFoundException(this.tripId));

		this.itineraryId = seedItineraryWithTwoItems();
	}

	@Test
	@DisplayName("도착 시각을 보내면 일정 조회 결과에 실제 도착 시각이 들어 있다")
	void recordedArrivalAppearsInDetail() {
		Instant arrivedAt = instant("2026-09-01T10:30:15+09:00");

		this.service.record(this.itineraryId, this.visitedItemKey, arrivedAt, null, this.ownerId);

		ItineraryDetailResponse.Item item = visitedItem();
		assertThat(item.actualArrivedAt()).isNotNull();
		assertThat(OffsetDateTime.parse(item.actualArrivedAt()).toInstant()).isEqualTo(arrivedAt);
		// 시간대는 항상 Asia/Seoul 이다(API-03) — UTC 로 적히면 화면이 9시간 어긋난 값을 보여준다.
		assertThat(item.actualArrivedAt()).endsWith("+09:00");
		// 출발은 안 보냈으므로 비어 있다. 부분 갱신이 아니라 보낸 것이 전체 상태다.
		assertThat(item.actualDepartedAt()).isNull();
	}

	@Test
	@DisplayName("같은 방문지에 다시 보내면 마지막 값이 남는다 — 행이 늘지 않는다")
	void secondRecordOverwritesTheFirst() {
		Instant firstArrival = instant("2026-09-01T10:30:15+09:00");
		Instant correctedArrival = instant("2026-09-01T11:05:20+09:00");
		Instant departure = instant("2026-09-01T12:40:00+09:00");

		this.service.record(this.itineraryId, this.visitedItemKey, firstArrival, null, this.ownerId);
		this.service.record(this.itineraryId, this.visitedItemKey, correctedArrival, departure, this.ownerId);

		ItineraryDetailResponse.Item item = visitedItem();
		assertThat(OffsetDateTime.parse(item.actualArrivedAt()).toInstant()).isEqualTo(correctedArrival);
		assertThat(OffsetDateTime.parse(item.actualDepartedAt()).toInstant()).isEqualTo(departure);
		// 두 번 보냈는데 기록이 둘로 늘면 조회가 어느 쪽을 보여줄지 알 수 없게 된다.
		assertThat(this.actualRepository.rowCount()).isEqualTo(1);
	}

	@Test
	@DisplayName("도착만 다시 보내면 앞서 적은 출발 시각은 지워진다 — 부분 갱신이 아니다")
	void resendingOnlyArrivalClearsDeparture() {
		this.service.record(this.itineraryId, this.visitedItemKey, instant("2026-09-01T10:00:10+09:00"),
				instant("2026-09-01T12:00:10+09:00"), this.ownerId);

		this.service.record(this.itineraryId, this.visitedItemKey, instant("2026-09-01T10:20:10+09:00"),
				null, this.ownerId);

		assertThat(visitedItem().actualDepartedAt()).isNull();
	}

	@Test
	@DisplayName("기록이 없는 방문지는 계획 시각만 나오고 실제 시각 자리가 비어 있다")
	void itemWithoutRecordKeepsPlannedTimeAndEmptyActualSlots() {
		this.service.record(this.itineraryId, this.visitedItemKey, instant("2026-09-01T10:30:15+09:00"),
				null, this.ownerId);

		ItineraryDetailResponse response = detail();
		// "비어 있다" 를 재기 전에 응답 자체가 비지 않았음을 먼저 단정한다. 목록이 비어 있으면
		//    그것은 "문제가 없다" 가 아니라 "아무것도 안 봤다" 다.
		assertThat(response.days()).hasSize(10);
		List<ItineraryDetailResponse.Item> lastDayItems = response.days().get(9).items();
		assertThat(lastDayItems).hasSize(1);

		ItineraryDetailResponse.Item untouched = lastDayItems.get(0);
		assertThat(untouched.id()).isEqualTo(this.untouchedItemKey);
		assertThat(untouched.title()).isEqualTo("해운대해수욕장");
		// 계획 시각은 그대로 있다 — 실제 시각이 없다는 것이 항목이 비었다는 뜻은 아니다.
		assertThat(untouched.startsAt()).isNotNull();
		assertThat(untouched.actualArrivedAt()).isNull();
		assertThat(untouched.actualDepartedAt()).isNull();

		// 같은 응답의 다른 방문지에는 기록이 들어 있다 — 위의 null 이 "이 기능이 아예 안 붙었다"
		// 가 아니라 "이 방문지에만 기록이 없다" 임을 증명한다.
		assertThat(visitedItem().actualArrivedAt()).isNotNull();
	}

	@Test
	@DisplayName("🔴 응답 record 에 실제 시각 칸이 실제로 있다 — 칸을 안 만들어도 null 확인은 통과한다")
	void detailItemActuallyDeclaresTheTwoFields() {
		// jsonPath(...).doesNotExist() 나 isNull() 만으로는 "칸이 없다" 와 "칸이 있고 값이 null 이다"
		// 를 구분할 수 없다. 칸의 존재는 여기서 이름으로 못 박는다.
		List<String> names = Arrays.stream(ItineraryDetailResponse.Item.class.getRecordComponents())
				.map(RecordComponent::getName)
				.toList();
		assertThat(names).contains("actualArrivedAt", "actualDepartedAt");
	}

	@Test
	@DisplayName("🔴 응답 record 에 placeId 칸이 실제로 있다 — S15P21E201-744")
	void detailItemActuallyDeclaresThePlaceIdField() {
		List<String> names = Arrays.stream(ItineraryDetailResponse.Item.class.getRecordComponents())
				.map(RecordComponent::getName)
				.toList();
		assertThat(names).contains("placeId");
	}

	@Test
	@DisplayName("🔴 응답의 placeId 는 그 항목이 실제로 가리키는 장소의 번호와 같다 — S15P21E201-744")
	void detailItemPlaceIdMatchesTheSeededPlace() {
		// 칸만 만들고 null 을 넣어도 위 테스트는 통과한다. 여기서 값이 씨앗 장소(this.placeId)와
		// 같은지 비교해야 진짜로 배선이 맞았는지 알 수 있다.
		assertThat(visitedItem().placeId()).isEqualTo(this.placeId.toString());
	}

	@Test
	@DisplayName("남의 여행 방문지에 기록을 보내면 거부된다 — 비회원은 404, VIEWER 는 403")
	void strangerAndViewerAreRejected() {
		Instant arrivedAt = instant("2026-09-01T10:30:15+09:00");

		assertThatThrownBy(() -> this.service.record(this.itineraryId, this.visitedItemKey, arrivedAt, null,
				this.strangerId))
						.isInstanceOf(ItineraryQueryController.ItineraryNotFoundException.class);

		assertThatThrownBy(() -> this.service.record(this.itineraryId, this.visitedItemKey, arrivedAt, null,
				this.viewerId))
						.isInstanceOf(ItineraryAccess.ItineraryForbiddenException.class);

		// 둘 다 거부됐으니 기록은 하나도 남지 않았다.
		assertThat(this.actualRepository.rowCount()).isZero();
	}

	@Test
	@DisplayName("지나간 날짜의 방문지에도 뒤늦게 기록할 수 있다")
	void pastVisitCanBeRecordedLate() {
		// 이 단정이 없으면 나중에 누가 씨앗 날짜를 오늘 이후로 바꿔도 이 테스트는 초록으로
		// 남는다 — 그러면 "지난 날짜여도 된다" 를 아무도 확인하지 않는 상태가 된다.
		assertThat(PAST_DAY).isBefore(LocalDate.now());

		Instant arrivedThatDay = instant("2026-09-01T09:05:30+09:00");
		Instant departedThatDay = instant("2026-09-01T11:45:30+09:00");

		ItineraryItemActual saved = this.service.record(this.itineraryId, this.visitedItemKey,
				arrivedThatDay, departedThatDay, this.ownerId);

		assertThat(saved.arrivedAt()).isEqualTo(arrivedThatDay);
		// 적은 시각(recordedAt)은 다녀온 시각보다 훨씬 늦다 — 그것이 정상이다.
		assertThat(saved.recordedAt()).isAfter(arrivedThatDay);
		assertThat(OffsetDateTime.parse(visitedItem().actualDepartedAt()).toInstant()).isEqualTo(departedThatDay);
	}

	// ── 값 검증과 대상 확인 ───────────────────────────────────────────────────

	@Test
	@DisplayName("도착도 출발도 없으면 거부된다 — 아무 내용이 없는 기록은 만들지 않는다")
	void bothTimesMissingIsRejected() {
		assertThatThrownBy(() -> this.service.record(this.itineraryId, this.visitedItemKey, null, null,
				this.ownerId))
						.isInstanceOf(ItineraryItemActual.NoTimeGivenException.class);

		assertThat(this.actualRepository.rowCount()).isZero();
	}

	@Test
	@DisplayName("출발이 도착보다 앞서면 거부된다")
	void departureBeforeArrivalIsRejected() {
		assertThatThrownBy(() -> this.service.record(this.itineraryId, this.visitedItemKey,
				instant("2026-09-01T12:00:10+09:00"), instant("2026-09-01T10:00:10+09:00"), this.ownerId))
						.isInstanceOf(ItineraryItemActual.DepartedBeforeArrivedException.class);

		assertThat(this.actualRepository.rowCount()).isZero();
	}

	@Test
	@DisplayName("최신 판에 없는 방문지에 보내면 404 — 어디에도 안 보이는 기록을 성공으로 답하지 않는다")
	void unknownItemKeyIsRejected() {
		assertThatThrownBy(() -> this.service.record(this.itineraryId, "itemkey_deleted",
				instant("2026-09-01T10:30:15+09:00"), null, this.ownerId))
						.isInstanceOf(ItineraryRevision.ItemNotFoundException.class);

		assertThat(this.actualRepository.rowCount()).isZero();
	}

	// ── 도움 메서드 ───────────────────────────────────────────────────────────

	private ItineraryDetailResponse detail() {
		return this.queryService.getDetail(this.itineraryId, this.ownerId);
	}

	private ItineraryDetailResponse.Item visitedItem() {
		List<ItineraryDetailResponse.Item> firstDayItems = detail().days().get(0).items();
		assertThat(firstDayItems).hasSize(1);
		return firstDayItems.get(0);
	}

	private static Instant instant(String isoOffsetDateTime) {
		return OffsetDateTime.parse(isoOffsetDateTime).toInstant();
	}

	/**
	 * 첫날에 방문지 하나, 마지막 날에 방문지 하나. 둘 다 계획 시각이 있다 — 계획 시각이
	 * 없으면 「계획 시각만 나오고 실제 시각 자리가 빈다」는 확인이 성립하지 않는다.
	 */
	private String seedItineraryWithTwoItems() {
		String id = "itn_" + UUID.randomUUID();
		String versionId = UUID.randomUUID().toString();

		Itinerary itinerary = new Itinerary(id, this.tripId, 1);
		ItineraryVersion version = new ItineraryVersion(versionId, id, 1, null,
				ItineraryVersion.Operation.CREATE, this.ownerId, "req_seed", null, Instant.now());

		List<ItineraryItem> items = List.of(
				item(versionId, this.visitedItemKey, 0, PAST_DAY, LocalTime.of(10, 0), LocalTime.of(12, 0)),
				item(versionId, this.untouchedItemKey, 9, LAST_DAY, LocalTime.of(9, 0), LocalTime.of(10, 0)));

		this.itineraryRepository.create(itinerary, version, items, List.of());
		return id;
	}

	private ItineraryItem item(String versionId, String itemKey, int dayIndex, LocalDate visitDate,
			LocalTime startTime, LocalTime endTime) {
		return new ItineraryItem(UUID.randomUUID().toString(), versionId, itemKey, dayIndex, visitDate,
				1, this.placeId.toString(), startTime, endTime, null, false, null,
				ItineraryItem.DataStatus.VERIFIED, List.of(), List.of(), null, Instant.now());
	}
}
