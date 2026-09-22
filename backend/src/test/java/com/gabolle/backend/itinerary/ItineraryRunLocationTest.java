package com.gabolle.backend.itinerary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.gabolle.backend.itinerary.application.ItineraryAccess;
import com.gabolle.backend.itinerary.application.ItineraryRunService;
import com.gabolle.backend.itinerary.domain.Itinerary;
import com.gabolle.backend.itinerary.domain.ItineraryContent;
import com.gabolle.backend.itinerary.domain.ItineraryItem;
import com.gabolle.backend.itinerary.domain.ItineraryItemActual;
import com.gabolle.backend.itinerary.domain.ItineraryItemActualRepository;
import com.gabolle.backend.itinerary.domain.ItineraryRepository;
import com.gabolle.backend.itinerary.domain.ItineraryRun;
import com.gabolle.backend.itinerary.domain.ItineraryRunPing;
import com.gabolle.backend.itinerary.domain.ItineraryRunRepository;
import com.gabolle.backend.itinerary.domain.ItineraryStopEvent;
import com.gabolle.backend.itinerary.domain.ItineraryVersion;
import com.gabolle.backend.place.domain.Place;
import com.gabolle.backend.place.repository.PlaceRepository;
import com.gabolle.backend.trip.domain.Trip;
import com.gabolle.backend.trip.domain.TripMember;

/**
 * GPS 배치로 지금 몇 번째인지를 서버가 판정한다.
 *
 * <p>재는 것은 셋이다 — <b>반경 안에 들면 스스로 도착을 찍는가</b>, <b>모를 때 지어내지
 * 않는가</b>, <b>멈춘 여행의 위치를 받지 않는가</b>. 셋 다 틀려도 화면은 멀쩡해 보인다.
 */
class ItineraryRunLocationTest {

	private static final String ITINERARY_ID = UUID.randomUUID().toString();

	private static final String TRIP_ID = UUID.randomUUID().toString();

	private static final String USER_ID = UUID.randomUUID().toString();

	private static final String FIRST_STOP = UUID.randomUUID().toString();

	private static final String FIRST_PLACE = UUID.randomUUID().toString();

	/** 해운대해수욕장. 첫 정차지가 여기 있다. */
	private static final double STOP_LAT = 35.1587;

	private static final double STOP_LNG = 129.1604;

	private static final Instant NOW = Instant.parse("2026-10-03T09:00:00Z");

	private final ItineraryAccess access = mock(ItineraryAccess.class);

	private final ItineraryRepository itineraries = mock(ItineraryRepository.class);

	private final ItineraryRunRepository runs = mock(ItineraryRunRepository.class);

	private final ItineraryItemActualRepository actuals = mock(ItineraryItemActualRepository.class);

	private final PlaceRepository places = mock(PlaceRepository.class);

	private ItineraryRunService service;

	@BeforeEach
	void setUp() {
		this.service = new ItineraryRunService(this.access, this.itineraries, this.runs, this.actuals, this.places,
				Clock.fixed(NOW, ZoneOffset.UTC));

		Itinerary itinerary = new Itinerary(ITINERARY_ID, TRIP_ID, 1);
		when(this.access.requireEditor(anyString(), anyString()))
				.thenReturn(new ItineraryAccess.Access(itinerary, trip(), TripMember.Role.OWNER));
		when(this.access.requireMember(anyString(), anyString()))
				.thenReturn(new ItineraryAccess.Access(itinerary, trip(), TripMember.Role.OWNER));
		when(this.itineraries.findById(ITINERARY_ID)).thenReturn(Optional.of(itinerary));
		when(this.itineraries.findContent(ITINERARY_ID, 1)).thenReturn(Optional.of(content()));

		when(this.runs.findEvents(anyString())).thenReturn(List.of());
		when(this.actuals.findByItineraryId(anyString())).thenReturn(List.of());
		when(this.runs.upsert(any())).thenAnswer((call) -> call.getArgument(0));
		when(this.runs.append(any())).thenAnswer((call) -> call.getArgument(0));
		when(this.places.findByPlaceIdIn(anyCollection()))
				.thenReturn(List.of(place(STOP_LAT, STOP_LNG)));

		givenRun(ItineraryRun.planned(ITINERARY_ID, TRIP_ID, NOW).start(NOW));
	}

	@Test
	@DisplayName("🔴 반경 안에 들면 서버가 스스로 도착을 찍는다 — 사람이 아무것도 안 눌러도 된다")
	void arrivesAutomaticallyInsideTheRadius() {
		// 정차지에서 30m 쯤. 위도 0.0002도는 약 22m 다.
		this.service.recordLocations(ITINERARY_ID, USER_ID,
				List.of(point(STOP_LAT + 0.0002, STOP_LNG, NOW)));

		ArgumentCaptor<ItineraryStopEvent> event = ArgumentCaptor.forClass(ItineraryStopEvent.class);
		verify(this.runs).append(event.capture());
		assertThat(event.getValue().type()).isEqualTo(ItineraryStopEvent.Type.ARRIVE_AUTO);
		assertThat(event.getValue().itemKey()).isEqualTo(FIRST_STOP);

		ArgumentCaptor<ItineraryItemActual> actual = ArgumentCaptor.forClass(ItineraryItemActual.class);
		verify(this.actuals).upsert(actual.capture());
		assertThat(actual.getValue().arrivedAt())
				.as("도착 시각은 기기가 찍은 시각이다 — 서버가 받은 시각이 아니다")
				.isEqualTo(NOW);
	}

	@Test
	@DisplayName("🔴 반경 밖이면 아무것도 안 찍는다 — 지나가기만 한 것을 다녀왔다고 하지 않는다")
	void staysSilentOutsideTheRadius() {
		// 정차지에서 약 2km.
		this.service.recordLocations(ITINERARY_ID, USER_ID,
				List.of(point(STOP_LAT + 0.02, STOP_LNG, NOW)));

		verify(this.runs, never()).append(any());
		verify(this.actuals, never()).upsert(any());
	}

	@Test
	@DisplayName("🔴 정차지 좌표를 모르면 판정을 보류한다 — 모름을 「멀다」로도 「가깝다」로도 치지 않는다")
	void holdsJudgementWhenThePlaceHasNoCoordinates() {
		when(this.places.findByPlaceIdIn(anyCollection())).thenReturn(List.of(place(null, null)));

		this.service.recordLocations(ITINERARY_ID, USER_ID, List.of(point(STOP_LAT, STOP_LNG, NOW)));

		verify(this.runs, never()).append(any());
	}

	@Test
	@DisplayName("궤적은 보낸 점을 전부 쌓는다 — 판정에 안 쓴 중간 점도 남는다")
	void keepsEveryPointInTheTrail() {
		this.service.recordLocations(ITINERARY_ID, USER_ID, List.of(
				point(35.10, 129.00, NOW.minusSeconds(60)),
				point(35.12, 129.05, NOW.minusSeconds(30)),
				point(35.14, 129.10, NOW)));

		@SuppressWarnings("unchecked")
		ArgumentCaptor<List<ItineraryRunPing>> pings = ArgumentCaptor.forClass(List.class);
		verify(this.runs).saveAllPings(pings.capture());
		assertThat(pings.getValue()).hasSize(3);
		assertThat(pings.getValue().get(0).receivedAt())
				.as("받은 시각은 서버 시계다 — 기기가 찍은 시각과 따로 남는다")
				.isEqualTo(NOW);
	}

	@Test
	@DisplayName("🔴 판정은 배치에서 가장 최근 점으로 한다 — 보낸 차례가 뒤죽박죽이어도 마찬가지다")
	void judgesByTheNewestPointNotTheLastInTheList() {
		// 목록의 마지막은 먼 곳이지만, 시각이 가장 최근인 것은 정차지 옆이다.
		this.service.recordLocations(ITINERARY_ID, USER_ID, List.of(
				point(STOP_LAT + 0.0002, STOP_LNG, NOW),
				point(35.00, 128.90, NOW.minusSeconds(120))));

		verify(this.runs).append(any());
	}

	@Test
	@DisplayName("🔴 멈춘 여행의 위치는 안 받는다 — 「멈췄다」가 거짓말이 되면 안 된다")
	void refusesLocationWhileNotRunning() {
		givenRun(ItineraryRun.planned(ITINERARY_ID, TRIP_ID, NOW).start(NOW).pause(NOW));

		assertThatThrownBy(() -> this.service.recordLocations(ITINERARY_ID, USER_ID,
				List.of(point(STOP_LAT, STOP_LNG, NOW))))
				.isInstanceOf(ItineraryRunService.NotRunningException.class);

		verify(this.runs, never()).saveAllPings(any());
	}

	@Test
	@DisplayName("빈 묶음은 오류가 아니다 — 망이 끊겼다 돌아온 기기가 빈 것을 한 번 보낸다")
	void emptyBatchIsFine() {
		ItineraryRunService.View view = this.service.recordLocations(ITINERARY_ID, USER_ID, List.of());

		assertThat(view.run().status()).isEqualTo(ItineraryRun.Status.RUNNING);
		verify(this.runs, never()).saveAllPings(any());
	}

	@Test
	@DisplayName("마지막 위치를 남긴다 — 앱을 껐다 켜도 지도를 어디에 놓을지 안다")
	void remembersTheLastLocation() {
		this.service.recordLocations(ITINERARY_ID, USER_ID, List.of(point(35.11, 129.02, NOW)));

		ArgumentCaptor<ItineraryRun> saved = ArgumentCaptor.forClass(ItineraryRun.class);
		verify(this.runs, org.mockito.Mockito.atLeastOnce()).upsert(saved.capture());
		ItineraryRun withLocation = saved.getAllValues().stream()
				.filter((run) -> run.lastLocation() != null)
				.findFirst()
				.orElseThrow(() -> new AssertionError("마지막 위치를 저장하지 않았다"));
		assertThat(withLocation.lastLocation().lat()).isEqualTo(35.11);
		assertThat(withLocation.lastLocation().at()).isEqualTo(NOW);
	}

	@Test
	@DisplayName("완료하면 다 돈 것이 된다")
	void completeEndsTheRun() {
		ItineraryRunService.View view = this.service.complete(ITINERARY_ID, USER_ID);

		assertThat(view.run().status()).isEqualTo(ItineraryRun.Status.DONE);
	}

	@Test
	@DisplayName("🔴 출발한 적 없는 여행은 완료할 수 없다")
	void cannotCompleteWhatNeverStarted() {
		givenRun(ItineraryRun.planned(ITINERARY_ID, TRIP_ID, NOW));

		assertThatThrownBy(() -> this.service.complete(ITINERARY_ID, USER_ID))
				.isInstanceOf(ItineraryRunService.NotRunningException.class);
	}

	// ── 도구 ──────────────────────────────────────────────────────────────────

	private void givenRun(ItineraryRun run) {
		when(this.runs.find(ITINERARY_ID)).thenReturn(Optional.of(run));
	}

	private static ItineraryRunService.LocationPoint point(double lat, double lng, Instant at) {
		return new ItineraryRunService.LocationPoint(lat, lng, at);
	}

	private static ItineraryContent content() {
		List<ItineraryItem> items = new ArrayList<>();
		items.add(new ItineraryItem(UUID.randomUUID().toString(), UUID.randomUUID().toString(), FIRST_STOP,
				0, LocalDate.of(2026, 10, 3), 1, FIRST_PLACE, null, null, null, false, null,
				ItineraryItem.DataStatus.ESTIMATED, List.of(), List.of(), null, NOW));
		return new ItineraryContent(mock(ItineraryVersion.class), items, List.of(), List.of());
	}

	private static Place place(Double lat, Double lng) {
		return Place.imported(UUID.fromString(FIRST_PLACE), "해운대해수욕장", "TOURIST", "부산 해운대구",
				lat, lng, "TEST", FIRST_PLACE, OffsetDateTime.now(), null, "fixture");
	}

	private static Trip trip() {
		return Trip.builder()
				.tripId(TRIP_ID).createdBy(USER_ID)
				.startDate(LocalDate.of(2026, 10, 3)).finishDate(LocalDate.of(2026, 10, 3))
				.partySize(2).timezone("Asia/Seoul").createdAt(NOW)
				.build();
	}
}
