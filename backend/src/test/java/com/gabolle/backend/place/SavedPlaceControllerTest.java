package com.gabolle.backend.place;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.gabolle.backend.event.application.EventIngestService;
import com.gabolle.backend.event.domain.EventType;
import com.gabolle.backend.place.api.PlaceExceptionHandler;
import com.gabolle.backend.place.api.SavedPlaceController;
import com.gabolle.backend.place.domain.SavedPlace;
import com.gabolle.backend.place.repository.PlaceRepository;
import com.gabolle.backend.place.repository.SavedPlaceRepository;
import com.gabolle.backend.place.service.SavedPlaceService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 저장한 장소(하트)를 서버에 남기는 경로. DB 없이 도는 슬라이스이고, 저장소만 mock 이고 서비스는
 * 진짜다 — 멱등성 판단이 서비스에 있어 그것까지 가짜로 바꾸면 아무것도 안 재게 된다.
 */
class SavedPlaceControllerTest {

	private SavedPlaceRepository savedPlaces;
	private PlaceRepository places;
	private EventIngestService events;
	private MockMvc mockMvc;

	private final UUID userId = UUID.randomUUID();
	private final UUID placeId = UUID.randomUUID();

	@BeforeEach
	void setUp() {
		this.savedPlaces = mock(SavedPlaceRepository.class);
		this.places = mock(PlaceRepository.class);
		this.events = mock(EventIngestService.class);
		SavedPlaceService service = new SavedPlaceService(this.savedPlaces, this.places,
				providerOf(this.events), Clock.fixed(Instant.parse("2026-09-16T12:00:00Z"), ZoneOffset.UTC));

		this.mockMvc = MockMvcBuilders.standaloneSetup(new SavedPlaceController(service))
				.setControllerAdvice(new PlaceExceptionHandler())
				.build();
	}

	/** {@code ObjectProvider} 를 흉내 낸다. {@code null} 을 주면 빈이 없는 슬라이스가 된다. */
	private static ObjectProvider<EventIngestService> providerOf(EventIngestService bean) {
		@SuppressWarnings("unchecked")
		ObjectProvider<EventIngestService> provider = mock(ObjectProvider.class);
		when(provider.getIfAvailable()).thenReturn(bean);
		return provider;
	}

	private static Authentication principal(UUID userId) {
		return new UsernamePasswordAuthenticationToken(userId.toString(), null, List.of());
	}

	@Test
	@DisplayName("저장한 것이 없으면 빈 목록이다 — 404 가 아니다")
	void emptyListIsOkNotNotFound() throws Exception {
		when(this.savedPlaces.findByUserIdOrderByCreatedAtDesc(eq(this.userId), any())).thenReturn(List.of());

		this.mockMvc.perform(get("/api/v1/me/saved-places").principal(principal(this.userId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items").isArray())
				.andExpect(jsonPath("$.data.items").isEmpty())
				.andExpect(jsonPath("$.data.count").value(0));
	}

	@Test
	@DisplayName("저장한 장소가 최근 순으로 나온다")
	void listReturnsSavedPlaces() throws Exception {
		SavedPlace saved = SavedPlace.of(UUID.randomUUID(), this.userId, this.placeId,
				OffsetDateTime.parse("2026-09-16T12:00:00Z"));
		when(this.savedPlaces.findByUserIdOrderByCreatedAtDesc(eq(this.userId), any())).thenReturn(List.of(saved));

		this.mockMvc.perform(get("/api/v1/me/saved-places").principal(principal(this.userId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.count").value(1))
				.andExpect(jsonPath("$.data.items[0].placeId").value(this.placeId.toString()));
	}

	@Test
	@DisplayName("하트를 켜면 204 이고 행이 하나 생긴다")
	void saveCreatesRow() throws Exception {
		when(this.places.existsById(this.placeId)).thenReturn(true);
		when(this.savedPlaces.insertIfAbsent(any(), eq(this.userId), eq(this.placeId), any())).thenReturn(1);

		this.mockMvc.perform(put("/api/v1/me/saved-places/{placeId}", this.placeId)
						.principal(principal(this.userId)))
				.andExpect(status().isNoContent());

		// 「있는지 보고 없으면 넣는」 대신 한 문장으로 넣어야 연타 사이에 틈이 안 생긴다.
		verify(this.savedPlaces).insertIfAbsent(any(), eq(this.userId), eq(this.placeId), any());
		verify(this.savedPlaces, never()).save(any());
	}

	@Test
	@DisplayName("🔴 이미 켜진 하트를 다시 켜도 행이 더 생기지 않는다")
	void savingTwiceDoesNotDuplicate() throws Exception {
		when(this.places.existsById(this.placeId)).thenReturn(true);
		when(this.savedPlaces.insertIfAbsent(any(), eq(this.userId), eq(this.placeId), any())).thenReturn(0);

		this.mockMvc.perform(put("/api/v1/me/saved-places/{placeId}", this.placeId)
						.principal(principal(this.userId)))
				.andExpect(status().isNoContent());

		verify(this.savedPlaces, never()).save(any());
	}

	@Test
	@DisplayName("🔴 하트를 새로 켜면 place_like 를 남긴다 — 벡터가 셀 행동 신호")
	void savingRecordsPlaceLike() throws Exception {
		when(this.places.existsById(this.placeId)).thenReturn(true);
		when(this.savedPlaces.insertIfAbsent(any(), eq(this.userId), eq(this.placeId), any())).thenReturn(1);

		this.mockMvc.perform(put("/api/v1/me/saved-places/{placeId}", this.placeId)
						.principal(principal(this.userId)))
				.andExpect(status().isNoContent());

		verify(this.events).recordFromServer(any(), eq(EventType.PLACE_LIKE), eq(1),
				eq(this.userId), eq(null), eq(null), eq(Map.of("placeId", this.placeId.toString())));
	}

	/**
	 * 신호를 남길지는 자바에서 다시 판정하지 않고 {@code insertIfAbsent} 가 돌려준 값으로 정한다 —
	 * 그 판정이 {@code ON CONFLICT DO NOTHING} 안에 있어 동시 요청에서도 한쪽만 1 을 받는다.
	 */
	@Test
	@DisplayName("🔴 이미 켜진 하트를 다시 켜면 place_like 를 남기지 않는다")
	void savingAgainDoesNotRecordAnotherLike() throws Exception {
		when(this.places.existsById(this.placeId)).thenReturn(true);
		when(this.savedPlaces.insertIfAbsent(any(), eq(this.userId), eq(this.placeId), any())).thenReturn(0);

		this.mockMvc.perform(put("/api/v1/me/saved-places/{placeId}", this.placeId)
						.principal(principal(this.userId)))
				.andExpect(status().isNoContent());

		verify(this.events, never()).recordFromServer(any(), any(), anyInt(), any(), any(), any(), any());
	}

	@Test
	@DisplayName("🔴 없는 장소면 place_like 도 안 남는다")
	void unknownPlaceRecordsNothing() throws Exception {
		when(this.places.existsById(this.placeId)).thenReturn(false);

		this.mockMvc.perform(put("/api/v1/me/saved-places/{placeId}", this.placeId)
						.principal(principal(this.userId)))
				.andExpect(status().isNotFound());

		verify(this.events, never()).recordFromServer(any(), any(), anyInt(), any(), any(), any(), any());
	}

	/**
	 * {@code PlaceSliceApplication} 은 장소만 스캔해서 {@code EventIngestService} 빈이 없다.
	 * 그 상태({@code getIfAvailable() == null})에서 저장은 되고 이벤트만 안 남는 것을 고정한다.
	 */
	@Test
	@DisplayName("🔴 이벤트 빈이 없는 슬라이스에서도 하트는 저장된다 — 신호만 안 남는다")
	void savingWorksWhenEventBeanIsAbsent() throws Exception {
		SavedPlaceService noEvents = new SavedPlaceService(this.savedPlaces, this.places,
				providerOf(null), Clock.fixed(Instant.parse("2026-09-16T12:00:00Z"), ZoneOffset.UTC));
		MockMvc mvc = MockMvcBuilders.standaloneSetup(new SavedPlaceController(noEvents))
				.setControllerAdvice(new PlaceExceptionHandler())
				.build();

		when(this.places.existsById(this.placeId)).thenReturn(true);
		when(this.savedPlaces.insertIfAbsent(any(), eq(this.userId), eq(this.placeId), any())).thenReturn(1);

		mvc.perform(put("/api/v1/me/saved-places/{placeId}", this.placeId)
						.principal(principal(this.userId)))
				.andExpect(status().isNoContent());

		verify(this.savedPlaces).insertIfAbsent(any(), eq(this.userId), eq(this.placeId), any());
	}

	/**
	 * {@code PLACE_DISLIKE} 는 명시적인 불호 신호라 하트 해제를 거기에 섞지 않는다.
	 * 해제를 신호로 남길지는 아직 정하지 않았다.
	 */
	@Test
	@DisplayName("🔴 하트를 꺼도 dislike 로 적지 않는다")
	void removingDoesNotRecordDislike() throws Exception {
		this.mockMvc.perform(delete("/api/v1/me/saved-places/{placeId}", this.placeId)
						.principal(principal(this.userId)))
				.andExpect(status().isNoContent());

		verify(this.events, never()).recordFromServer(any(), any(), anyInt(), any(), any(), any(), any());
	}

	/** 안 막으면 외래키 위반이 그대로 올라와 500 이 나간다. */
	@Test
	@DisplayName("🔴 없는 장소를 저장하려 하면 500 이 아니라 404 다")
	void savingUnknownPlaceIsNotFound() throws Exception {
		when(this.places.existsById(this.placeId)).thenReturn(false);

		this.mockMvc.perform(put("/api/v1/me/saved-places/{placeId}", this.placeId)
						.principal(principal(this.userId)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("PLACE_NOT_FOUND"));

		verify(this.savedPlaces, never()).save(any());
	}

	@Test
	@DisplayName("🔴 안 켜져 있던 하트를 꺼도 성공이다 — 재시도가 흔한 자리다")
	void removingWhatIsNotThereStillSucceeds() throws Exception {
		this.mockMvc.perform(delete("/api/v1/me/saved-places/{placeId}", this.placeId)
						.principal(principal(this.userId)))
				.andExpect(status().isNoContent());

		verify(this.savedPlaces).deleteByUserIdAndPlaceId(this.userId, this.placeId);
	}

	@Test
	@DisplayName("🔴 목록은 언제나 요청자 자신의 것만 읽는다 — 경로에 남의 번호를 넣을 자리가 없다")
	void listAlwaysReadsTheRequestersOwnList() throws Exception {
		UUID other = UUID.randomUUID();
		when(this.savedPlaces.findByUserIdOrderByCreatedAtDesc(eq(other), any())).thenReturn(List.of());

		this.mockMvc.perform(get("/api/v1/me/saved-places").principal(principal(other)))
				.andExpect(status().isOk());

		verify(this.savedPlaces).findByUserIdOrderByCreatedAtDesc(eq(other), any());
		verify(this.savedPlaces, never()).findByUserIdOrderByCreatedAtDesc(eq(this.userId), any());
	}
}
