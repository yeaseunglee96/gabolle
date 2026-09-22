package com.gabolle.backend.itinerary;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.gabolle.backend.itinerary.application.ItineraryAccess;
import com.gabolle.backend.itinerary.application.ActorNames;
import com.gabolle.backend.itinerary.application.ItineraryQueryService;
import com.gabolle.backend.itinerary.domain.Itinerary;
import com.gabolle.backend.itinerary.domain.ItineraryItem;
import com.gabolle.backend.itinerary.domain.ItineraryVersion;
import com.gabolle.backend.itinerary.infra.InMemoryItineraryRepository;
import com.gabolle.backend.itinerary.presentation.ItineraryQueryController;
import com.gabolle.backend.itinerary.presentation.dto.ItineraryDetailResponse;
import com.gabolle.backend.itinerary.support.FakeItineraryItemActualRepository;
import com.gabolle.backend.place.domain.Place;
import com.gabolle.backend.place.repository.PlaceRepository;
import com.gabolle.backend.recommendation.application.RecommendationCodes;
import com.gabolle.backend.recommendation.repository.RecommendationJobRepository;
import com.gabolle.backend.trip.application.TripQueryService;
import com.gabolle.backend.trip.domain.Trip;
import com.gabolle.backend.trip.domain.TripMember;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 완성된 일정표 조회. 항목이 0개인 날을 감추지 않는가, 도보 거리가 없는 것을 0 으로
 * 지어내지 않는가, {@code item_key} 를 PK 대신 쓰는가를 본다.
 *
 * <p>Spring 컨텍스트를 띄우지 않는다. {@link ItineraryRepository} 는
 * {@link InMemoryItineraryRepository} 를 실제로 쓰고,
 * {@link TripQueryService}·{@link PlaceRepository}·{@link RecommendationJobRepository} 만
 * Mockito 로 대신한다.
 *
 * <p>{@link ItineraryAccess} 는 진짜 객체다 — 「일정을 찾고 그 일정의 여행 회원인지 본다」는
 * 판정 자체가 이 테스트로 검증된다. {@code stubTripMembership} 이 {@code tripId} 기준으로
 * 스텁하는 것은 {@code itineraryId} 가 테스트마다 새로 만들어져 미리 알 수 없기 때문이다.
 */
class ItineraryQueryServiceTest {

	private InMemoryItineraryRepository itineraryRepository;
	private TripQueryService tripQueryService;
	private ItineraryAccess itineraryAccess;
	private PlaceRepository placeRepository;
	private RecommendationJobRepository recommendationJobRepository;
	private ItineraryQueryService service;
	/** 항목이 가리키는 장소. 좌표 시험이 이 흉내를 바꿔 쓴다. */
	private Place place;

	private final String tripId = "trip_1";
	private final String requesterId = "usr_a";
	private final UUID placeId = UUID.randomUUID();

	@BeforeEach
	void setUp() {
		this.itineraryRepository = new InMemoryItineraryRepository();
		this.tripQueryService = mock(TripQueryService.class);
		this.itineraryAccess = new ItineraryAccess(this.itineraryRepository, this.tripQueryService);
		this.placeRepository = mock(PlaceRepository.class);
		this.recommendationJobRepository = mock(RecommendationJobRepository.class);
		// 실제 도착·출발 시각 저장소. 이 테스트들은 기록이 하나도 없는 상태를 보므로 빈
		// 대역이면 충분하다 — 기록이 있을 때는 ItineraryActualTimeServiceTest 가 본다.
		this.service = new ItineraryQueryService(this.itineraryRepository, this.itineraryAccess,
				this.placeRepository, this.recommendationJobRepository, mock(ActorNames.class),
				new FakeItineraryItemActualRepository());

		this.place = mock(Place.class);
		when(this.place.getPlaceId()).thenReturn(this.placeId);
		when(this.place.getNameKo()).thenReturn("해운대 해수욕장");
		when(this.place.getLat()).thenReturn(35.1587);
		when(this.place.getLng()).thenReturn(129.1604);
		when(this.placeRepository.findByPlaceIdIn(any())).thenReturn(List.of(this.place));
	}

	private Trip threeDayTrip() {
		// 3박4일이 아니라 2026-09-10~09-12, 즉 days() = nights(2) + 1 = 3.
		return new Trip(this.tripId, this.requesterId, LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 12),
				null, null, null, 1, null, "Asia/Seoul", Instant.now());
	}

	private void stubTripMembership(Trip trip) {
		// 이 테스트들은 role 자체를 검사하지 않으므로 기본값으로 OWNER 를 준다.
		stubTripMembership(trip, TripMember.Role.OWNER);
	}

	private void stubTripMembership(Trip trip, TripMember.Role role) {
		when(this.tripQueryService.get(this.tripId, this.requesterId))
				.thenReturn(new TripQueryService.View(trip, List.of(), null, role));
	}

	@Test
	@DisplayName("🔴 항목이 0개인 날도 빈 items 배열로 포함된다")
	void includesEmptyDays() {
		stubTripMembership(threeDayTrip());
		String itineraryId = seedItinerary(1,
				List.of(itemOf("item_key_1", 0, LocalDate.of(2026, 9, 10), 1, null, null)));

		ItineraryDetailResponse response = this.service.getDetail(itineraryId, this.requesterId);

		assertThat(response.days()).hasSize(3);
		assertThat(response.days().get(0).items()).hasSize(1);
		assertThat(response.days().get(1).items()).isEmpty();
		assertThat(response.days().get(2).items()).isEmpty();
	}

	@Test
	@DisplayName("items[].id 는 item_key 다 — PK 가 아니다")
	void itemIdIsItemKey() {
		stubTripMembership(threeDayTrip());
		String itemKey = "itemkey_" + UUID.randomUUID();
		String itineraryId = seedItinerary(1,
				List.of(itemOf(itemKey, 0, LocalDate.of(2026, 9, 10), 1, null, null)));

		ItineraryDetailResponse response = this.service.getDetail(itineraryId, this.requesterId);

		assertThat(response.days().get(0).items().get(0).id()).isEqualTo(itemKey);
	}

	@Test
	@DisplayName("🔴 도보 구간 데이터가 하나도 없으면 totalWalkingMeters 는 0 이 아니라 null")
	void totalWalkingMetersNullWhenNoData() {
		stubTripMembership(threeDayTrip());
		String itineraryId = seedItinerary(1,
				List.of(itemOf("item_key_1", 0, LocalDate.of(2026, 9, 10), 1, null, null)));

		ItineraryDetailResponse response = this.service.getDetail(itineraryId, this.requesterId);

		assertThat(response.totalWalkingMeters()).isNull();
		assertThat(response.totalEstimatedCostKrw()).isNull();
	}

	@Test
	@DisplayName("🔴 최신 판 포인터가 가리키는 내용이 없으면 조용히 대체하지 않고 시끄럽게 실패한다")
	void missingLatestVersionContentFailsLoudly() {
		stubTripMembership(threeDayTrip());
		// latestVersion 을 2 로 심어 두되 실제 내용은 1번 판만 저장한다 — 포인터와 실제가 어긋난 상태.
		Itinerary itinerary = new Itinerary("itn_broken", this.tripId, 2);
		ItineraryVersion v1 = new ItineraryVersion(UUID.randomUUID().toString(), "itn_broken", 1, null,
				ItineraryVersion.Operation.CREATE, this.requesterId, "req_1", null, Instant.now());
		this.itineraryRepository.create(itinerary, v1, List.of(), List.of());

		assertThatThrownBy(() -> this.service.getDetail("itn_broken", this.requesterId))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	@DisplayName("🔴 S15P21E201-218 — 판의 warningCodes 가 응답에 그대로 실린다")
	void warningCodesAreIncluded() {
		stubTripMembership(threeDayTrip());
		String itineraryId = "itn_" + UUID.randomUUID();
		Itinerary itinerary = new Itinerary(itineraryId, this.tripId, 1);
		ItineraryVersion v = new ItineraryVersion(UUID.randomUUID().toString(), itineraryId, 1, null,
				ItineraryVersion.Operation.CREATE, this.requesterId, "req_1", null, Instant.now(),
				null, List.of("UNKNOWN_SHADE"));
		this.itineraryRepository.create(itinerary, v, List.of(), List.of());

		ItineraryDetailResponse response = this.service.getDetail(itineraryId, this.requesterId);

		assertThat(response.warningCodes()).containsExactly("UNKNOWN_SHADE");
	}

	@Test
	@DisplayName("🔴 S15P21E201-1158 — 항목에 붙은 경고가 응답에 실린다. 저장만 되고 안 나가던 값이다")
	void itemWarningCodesAreIncluded() {
		stubTripMembership(threeDayTrip());
		LocalDate day = LocalDate.parse("2026-10-01");
		String itineraryId = seedItinerary(1, List.of(
				itemOf("k1", 0, day, 1, LocalTime.of(10, 0), LocalTime.of(11, 0),
						List.of(RecommendationCodes.WARNING_ACCESSIBILITY_UNVERIFIED))));

		ItineraryDetailResponse response = this.service.getDetail(itineraryId, this.requesterId);

		ItineraryDetailResponse.Item item = response.days().stream()
				.flatMap(d -> d.items().stream()).findFirst().orElseThrow();
		assertThat(item.warningCodes()).containsExactly("ACCESSIBILITY_UNVERIFIED");
	}

	@Test
	@DisplayName("🔴 S15P21E201-1158 — 경고가 없는 항목의 warningCodes 는 null 이 아니라 빈 배열")
	void itemWarningCodesEmptyNotNull() {
		stubTripMembership(threeDayTrip());
		LocalDate day = LocalDate.parse("2026-10-01");
		String itineraryId = seedItinerary(1, List.of(
				itemOf("k1", 0, day, 1, LocalTime.of(10, 0), LocalTime.of(11, 0))));

		ItineraryDetailResponse response = this.service.getDetail(itineraryId, this.requesterId);

		ItineraryDetailResponse.Item item = response.days().stream()
				.flatMap(d -> d.items().stream()).findFirst().orElseThrow();
		assertThat(item.warningCodes()).isNotNull().isEmpty();
	}

	@Test
	@DisplayName("🔴 S15P21E201-1158 — accessibilityUnverifiedCount 는 확인 안 된 '곳' 수다")
	void accessibilityUnverifiedCountCountsPlaces() {
		stubTripMembership(threeDayTrip());
		LocalDate day = LocalDate.parse("2026-10-01");
		String unverified = RecommendationCodes.WARNING_ACCESSIBILITY_UNVERIFIED;
		String itineraryId = seedItinerary(1, List.of(
				itemOf("k1", 0, day, 1, LocalTime.of(10, 0), LocalTime.of(11, 0), List.of(unverified)),
				// 경고가 둘 붙은 곳도 '한 곳'이다. 건수가 아니라 곳을 센다.
				itemOf("k2", 0, day, 2, LocalTime.of(12, 0), LocalTime.of(13, 0),
						List.of("WALKING_OVER_LIMIT", unverified)),
				// 접근성과 무관한 경고만 붙은 곳은 안 센다.
				itemOf("k3", 0, day, 3, LocalTime.of(14, 0), LocalTime.of(15, 0),
						List.of("WALKING_OVER_LIMIT")),
				itemOf("k4", 0, day, 4, LocalTime.of(16, 0), LocalTime.of(17, 0))));

		ItineraryDetailResponse response = this.service.getDetail(itineraryId, this.requesterId);

		assertThat(response.accessibilityUnverifiedCount()).isEqualTo(2);
	}

	@Test
	@DisplayName("🔴 S15P21E201-1158 — 확인 안 된 곳이 없으면 0 이다. 모달이 안 뜨는 근거다")
	void accessibilityUnverifiedCountIsZeroWhenAllVerified() {
		stubTripMembership(threeDayTrip());
		LocalDate day = LocalDate.parse("2026-10-01");
		String itineraryId = seedItinerary(1, List.of(
				itemOf("k1", 0, day, 1, LocalTime.of(10, 0), LocalTime.of(11, 0))));

		ItineraryDetailResponse response = this.service.getDetail(itineraryId, this.requesterId);

		assertThat(response.accessibilityUnverifiedCount()).isZero();
	}

	@Test
	@DisplayName("🔴 경고가 없으면 warningCodes 는 null 이 아니라 빈 배열")
	void warningCodesEmptyNotNull() {
		stubTripMembership(threeDayTrip());
		String itineraryId = seedItinerary(1, List.of());

		ItineraryDetailResponse response = this.service.getDetail(itineraryId, this.requesterId);

		assertThat(response.warningCodes()).isEmpty();
	}

	@Test
	@DisplayName("여행 회원이 아니면 ItineraryNotFoundException(404) — 있다는 사실도 알려주지 않는다")
	void nonMemberGetsNotFound() {
		when(this.tripQueryService.get(this.tripId, this.requesterId))
				.thenThrow(new TripQueryService.TripNotFoundException(this.tripId));
		String itineraryId = seedItinerary(1, List.of());

		assertThatThrownBy(() -> this.service.getDetail(itineraryId, this.requesterId))
				.isInstanceOf(ItineraryQueryController.ItineraryNotFoundException.class);
	}

	/** 여행표의 「인원」이 초안 기본값 1 에 가려 언제나 1명으로 나오던 자리를 채운다. */
	@Test
	@DisplayName("🔴 티켓 완료 기준 — 일정 응답이 여행 인원을 싣는다")
	void detailCarriesPartySize() {
		// 일부러 1 이 아닌 수로 만든다. 1 로 두면 「값을 옮겼나」와 「1 을 박아 넣었나」가
		// 구분이 안 된다.
		Trip trip = new Trip(this.tripId, this.requesterId, LocalDate.of(2026, 9, 10), LocalDate.of(2026, 9, 12),
				null, null, null, 3, null, "Asia/Seoul", Instant.now());
		stubTripMembership(trip);
		String itineraryId = seedItinerary(1, List.of());

		assertThat(this.service.getDetail(itineraryId, this.requesterId).partySize()).isEqualTo(3);
	}

	// ── 방문지 좌표 ─────────────────────────────────────────────────────────

	/** 좌표가 없으면 코스 화면이 동선을 글로만 세운다. */
	@Test
	@DisplayName("🔴 티켓 완료 기준 — 방문지에 좌표가 실린다")
	void itemCarriesCoordinates() {
		Trip trip = threeDayTrip();
		stubTripMembership(trip);
		String itineraryId = seedItinerary(1, List.of(
				itemOf("item_1", 0, LocalDate.of(2026, 9, 10), 1, LocalTime.of(9, 30), LocalTime.of(11, 0))));

		ItineraryDetailResponse.Item item = this.service.getDetail(itineraryId, this.requesterId).days().stream()
				.flatMap(day -> day.items().stream()).findFirst().orElseThrow();

		assertThat(item.lat()).isEqualTo(35.1587);
		assertThat(item.lng()).isEqualTo(129.1604);
	}

	/**
	 * 모르면 {@code null} 이지 {@code 0} 이 아니다. 위도 0·경도 0 은 기니만 한가운데라,
	 * 0 으로 채우면 「없다」가 지도 위의 점으로 바뀐다.
	 */
	@Test
	@DisplayName("🔴 좌표를 모르는 장소는 null 이다 — 0 으로 채우면 지도에 기니만이 찍힌다")
	void unknownCoordinatesStayNull() {
		when(this.place.getLat()).thenReturn(null);
		when(this.place.getLng()).thenReturn(null);
		Trip trip = threeDayTrip();
		stubTripMembership(trip);
		String itineraryId = seedItinerary(1, List.of(
				itemOf("item_1", 0, LocalDate.of(2026, 9, 10), 1, LocalTime.of(9, 30), LocalTime.of(11, 0))));

		ItineraryDetailResponse.Item item = this.service.getDetail(itineraryId, this.requesterId).days().stream()
				.flatMap(day -> day.items().stream()).findFirst().orElseThrow();

		assertThat(item.lat()).isNull();
		assertThat(item.lng()).isNull();
	}

	private String seedItinerary(int version, List<ItineraryItem> items) {
		String itineraryId = "itn_" + UUID.randomUUID();
		Itinerary itinerary = new Itinerary(itineraryId, this.tripId, version);
		ItineraryVersion v = new ItineraryVersion(UUID.randomUUID().toString(), itineraryId, version, null,
				ItineraryVersion.Operation.CREATE, this.requesterId, "req_1", null, Instant.now());
		this.itineraryRepository.create(itinerary, v, items, List.of());
		return itineraryId;
	}

	private ItineraryItem itemOf(String itemKey, int dayIndex, LocalDate visitDate, int sequence,
			LocalTime startTime, LocalTime endTime) {
		return itemOf(itemKey, dayIndex, visitDate, sequence, startTime, endTime, List.of());
	}

	/** 경고가 붙은 항목. */
	private ItineraryItem itemOf(String itemKey, int dayIndex, LocalDate visitDate, int sequence,
			LocalTime startTime, LocalTime endTime, List<String> warningCodes) {
		return new ItineraryItem(UUID.randomUUID().toString(), "version-placeholder", itemKey, dayIndex, visitDate,
				sequence, this.placeId.toString(), startTime, endTime, null, false, null,
				ItineraryItem.DataStatus.VERIFIED, List.of(), warningCodes, null, Instant.now());
	}
}
