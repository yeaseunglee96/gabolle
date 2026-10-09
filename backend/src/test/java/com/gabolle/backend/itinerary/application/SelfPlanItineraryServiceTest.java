package com.gabolle.backend.itinerary.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.gabolle.backend.itinerary.domain.Itinerary;
import com.gabolle.backend.itinerary.domain.ItineraryRepository;
import com.gabolle.backend.itinerary.domain.ItineraryVersion;
import com.gabolle.backend.trip.application.TripQueryService;
import com.gabolle.backend.trip.domain.Trip;
import com.gabolle.backend.trip.domain.TripMember;
import com.gabolle.backend.trip.domain.TripRepository;

/**
 * 직접 짜기 — AI 추천 없이 빈 일정을 만든다 (S15P21E201-2018).
 */
class SelfPlanItineraryServiceTest {

	private static final String TRIP = "11111111-1111-1111-1111-111111111111";
	private static final String USER = "22222222-2222-2222-2222-222222222222";

	private TripQueryService tripQueryService;
	private ItineraryRepository itineraryRepository;
	private TripRepository tripRepository;
	private SelfPlanItineraryService service;
	private Trip trip;

	@BeforeEach
	void setUp() {
		this.tripQueryService = mock(TripQueryService.class);
		this.itineraryRepository = mock(ItineraryRepository.class);
		this.tripRepository = mock(TripRepository.class);
		Clock clock = Clock.fixed(Instant.parse("2026-10-09T00:00:00Z"), ZoneOffset.UTC);
		this.service = new SelfPlanItineraryService(this.tripQueryService, this.itineraryRepository,
				this.tripRepository, clock);
		this.trip = Trip.builder().tripId(TRIP).createdBy(USER).startDate(LocalDate.of(2026, 10, 20))
				.finishDate(LocalDate.of(2026, 10, 21)).partySize(2).createdAt(Instant.parse("2026-10-08T00:00:00Z"))
				.build();
		when(this.tripRepository.findById(TRIP)).thenReturn(Optional.of(this.trip));
	}

	private void as(TripMember.Role role) {
		when(this.tripQueryService.get(TRIP, USER))
				.thenReturn(new TripQueryService.View(this.trip, List.of(), null, role));
	}

	@Test
	@DisplayName("일정이 없는 여행이면 곳이 하나도 없는 1판 일정을 만들고 여행을 준비됨으로 바꾼다")
	void createsAnEmptyFirstVersion() {
		as(TripMember.Role.OWNER);
		when(this.itineraryRepository.findByTripId(TRIP)).thenReturn(List.of());

		String itineraryId = this.service.createBlank(TRIP, USER);

		ArgumentCaptor<Itinerary> itinerary = ArgumentCaptor.forClass(Itinerary.class);
		ArgumentCaptor<ItineraryVersion> version = ArgumentCaptor.forClass(ItineraryVersion.class);
		verify(this.itineraryRepository).create(itinerary.capture(), version.capture(), any(), any());
		assertThat(itinerary.getValue().itineraryId()).isEqualTo(itineraryId);
		assertThat(itinerary.getValue().tripId()).isEqualTo(TRIP);
		assertThat(version.getValue().version()).isEqualTo(1);
		assertThat(version.getValue().operation()).isEqualTo(ItineraryVersion.Operation.CREATE);
		assertThat(version.getValue().createdBy()).isEqualTo(USER);
		assertThat(this.trip.status()).isEqualTo(Trip.Status.READY);
		verify(this.tripRepository).updateStatus(this.trip);
	}

	@Test
	@DisplayName("이미 일정이 있으면 새로 만들지 않고 그 일정을 준다 — 두 번 눌러도 일정이 둘 생기지 않는다")
	void returnsExistingItinerary() {
		as(TripMember.Role.EDITOR);
		when(this.itineraryRepository.findByTripId(TRIP)).thenReturn(List.of(new Itinerary("it-1", TRIP, 3)));

		assertThat(this.service.createBlank(TRIP, USER)).isEqualTo("it-1");
		verify(this.itineraryRepository, never()).create(any(), any(), anyList(), anyList());
	}

	@Test
	@DisplayName("보기만 하는 회원은 만들 수 없다")
	void viewerCannotCreate() {
		as(TripMember.Role.VIEWER);
		assertThatThrownBy(() -> this.service.createBlank(TRIP, USER))
				.isInstanceOf(SelfPlanItineraryService.NotEditableException.class);
		verify(this.itineraryRepository, never()).create(any(), any(), anyList(), anyList());
	}
}
