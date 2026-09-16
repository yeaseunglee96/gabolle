package com.gabolle.backend.place;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.gabolle.backend.place.api.PlaceExceptionHandler;
import com.gabolle.backend.place.api.SavedPlaceController;
import com.gabolle.backend.place.domain.SavedPlace;
import com.gabolle.backend.place.repository.PlaceRepository;
import com.gabolle.backend.place.repository.SavedPlaceRepository;
import com.gabolle.backend.place.service.SavedPlaceService;

import static org.mockito.ArgumentMatchers.any;
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
 * S15P21E201-1013 — 저장한 장소(하트)를 서버에 남기는 경로.
 *
 * <p>DB 없이 도는 슬라이스다. 저장소를 mock 으로 세우고 서비스는 진짜를 쓴다 — 이 작업에서
 * 틀리기 쉬운 것이 <b>멱등성</b>(같은 요청을 두 번 보내도 결과가 같은가)이라, 그 판단을
 * 하는 서비스를 가짜로 바꾸면 검사가 아무것도 안 재게 된다.
 */
class SavedPlaceControllerTest {

	private SavedPlaceRepository savedPlaces;
	private PlaceRepository places;
	private MockMvc mockMvc;

	private final UUID userId = UUID.randomUUID();
	private final UUID placeId = UUID.randomUUID();

	@BeforeEach
	void setUp() {
		this.savedPlaces = mock(SavedPlaceRepository.class);
		this.places = mock(PlaceRepository.class);
		SavedPlaceService service = new SavedPlaceService(this.savedPlaces, this.places,
				Clock.fixed(Instant.parse("2026-09-16T12:00:00Z"), ZoneOffset.UTC));

		this.mockMvc = MockMvcBuilders.standaloneSetup(new SavedPlaceController(service))
				.setControllerAdvice(new PlaceExceptionHandler())
				.build();
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

		this.mockMvc.perform(put("/api/v1/me/saved-places/{placeId}", this.placeId)
						.principal(principal(this.userId)))
				.andExpect(status().isNoContent());

		// 「있는지 보고 없으면 넣는」 대신 한 문장으로 넣는다 — 연타의 사이를 없애려고.
		verify(this.savedPlaces).insertIfAbsent(any(), eq(this.userId), eq(this.placeId), any());
		verify(this.savedPlaces, never()).save(any());
	}

	/**
	 * 🔴 이 작업에서 가장 틀리기 쉬운 자리다. 하트는 켜짐/꺼짐이라 "두 번 켠 상태" 가 없다.
	 * 누를 때마다 행을 더하면 {@code uk_saved_place} 에 걸려 저장이 실패하거나, 제약이
	 * 없었다면 목록에 같은 장소가 여러 번 뜬다.
	 */
	@Test
	@DisplayName("🔴 이미 켜진 하트를 다시 켜도 행이 더 생기지 않는다")
	void savingTwiceDoesNotDuplicate() throws Exception {
		when(this.places.existsById(this.placeId)).thenReturn(true);
		when(this.savedPlaces.existsByUserIdAndPlaceId(this.userId, this.placeId)).thenReturn(true);

		this.mockMvc.perform(put("/api/v1/me/saved-places/{placeId}", this.placeId)
						.principal(principal(this.userId)))
				.andExpect(status().isNoContent());

		verify(this.savedPlaces, never()).save(any());
	}

	/**
	 * 🔴 이것을 안 막으면 외래키 위반이 그대로 올라와 500 이 나간다. 그러면 화면은
	 * "서버가 고장났다" 와 "그런 장소가 없다" 를 구분하지 못한다.
	 */
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
