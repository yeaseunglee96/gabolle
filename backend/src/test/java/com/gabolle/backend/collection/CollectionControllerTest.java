package com.gabolle.backend.collection;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.gabolle.backend.collection.application.CollectionService;
import com.gabolle.backend.collection.domain.Collection;
import com.gabolle.backend.collection.domain.CollectionItem;
import com.gabolle.backend.collection.presentation.CollectionController;
import com.gabolle.backend.collection.presentation.CollectionExceptionHandler;
import com.gabolle.backend.collection.repository.CollectionItemRepository;
import com.gabolle.backend.collection.repository.CollectionRepository;
import com.gabolle.backend.place.repository.PlaceRepository;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 컬렉션. DB 없이 도는 슬라이스이고 저장소만 mock 으로 세운다 — 주인 검사와 두 종류 처리를
 * 재야 하므로 서비스는 진짜를 쓴다.
 */
class CollectionControllerTest {

	private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-09-16T12:00:00Z");

	private CollectionRepository collections;
	private CollectionItemRepository items;
	private PlaceRepository places;
	private MockMvc mockMvc;

	private final UUID ownerId = UUID.randomUUID();
	private final UUID collectionId = UUID.randomUUID();
	private final UUID placeId = UUID.randomUUID();

	@BeforeEach
	void setUp() {
		this.collections = mock(CollectionRepository.class);
		this.items = mock(CollectionItemRepository.class);
		this.places = mock(PlaceRepository.class);

		CollectionService service = new CollectionService(this.collections, this.items, this.places,
				Clock.fixed(Instant.parse("2026-09-16T12:00:00Z"), ZoneOffset.UTC));

		this.mockMvc = MockMvcBuilders.standaloneSetup(new CollectionController(service))
				.setControllerAdvice(new CollectionExceptionHandler())
				.build();
	}

	private static Authentication principal(UUID userId) {
		return new UsernamePasswordAuthenticationToken(userId.toString(), null, List.of());
	}

	private Collection mine() {
		return Collection.of(this.collectionId, this.ownerId, "부산 가고 싶은 곳", null, NOW);
	}

	// ── 주인 검사 ────────────────────────────────────────────────────────────

	/** 남의 컬렉션과 없는 컬렉션을 같은 404 로 답한다. 구분하면 있다는 사실이 새어 나간다. */
	@Test
	@DisplayName("🔴 남의 컬렉션은 없는 것과 같은 404 다")
	void othersCollectionIsNotFound() throws Exception {
		// 주인이 아니면 저장소가 애초에 빈 값을 낸다 — findByIdAndUserId 라서.
		when(this.collections.findByIdAndUserId(this.collectionId, this.ownerId)).thenReturn(Optional.empty());

		this.mockMvc.perform(get("/api/v1/me/collections/{id}", this.collectionId)
						.principal(principal(this.ownerId)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("COLLECTION_NOT_FOUND"));
	}

	@Test
	@DisplayName("컬렉션이 없으면 빈 목록이다 — 404 가 아니다")
	void emptyListIsOk() throws Exception {
		when(this.collections.findByUserIdOrderByUpdatedAtDesc(eq(this.ownerId), any())).thenReturn(List.of());

		this.mockMvc.perform(get("/api/v1/me/collections").principal(principal(this.ownerId)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items").isEmpty())
				.andExpect(jsonPath("$.data.count").value(0));
	}

	// ── 두 종류 ──────────────────────────────────────────────────────────────

	@Test
	@DisplayName("우리가 아는 곳을 담으면 kind 가 PLACE 이고 placeId 가 실린다")
	void addPlaceItem() throws Exception {
		when(this.collections.findByIdAndUserId(this.collectionId, this.ownerId)).thenReturn(Optional.of(mine()));
		when(this.places.existsById(this.placeId)).thenReturn(true);
		// 업서트는 넣었는지(1) 이미 있었는지(0) 만 알려 주고, 돌려줄 항목은 도로 읽어 온다.
		when(this.items.insertPlaceItemIfAbsent(any(), eq(this.collectionId), eq(this.placeId), any(),
				anyInt(), any())).thenReturn(1);
		CollectionItem stored = CollectionItem.ofPlace(UUID.randomUUID(), this.collectionId, this.placeId,
				null, 0, NOW);
		when(this.items.findByCollectionIdAndPlaceId(this.collectionId, this.placeId))
				.thenReturn(Optional.of(stored));
		// 응답은 컬렉션 전체를 도로 읽어 싣는다 — 그 조회도 같이 세워 둔다.
		when(this.items.findByCollectionIdOrderByPositionAscCreatedAtAsc(eq(this.collectionId), any()))
				.thenReturn(List.of(stored));

		this.mockMvc.perform(post("/api/v1/me/collections/{id}/items", this.collectionId)
						.contentType("application/json")
						.content("{\"kind\":\"PLACE\",\"placeId\":\"" + this.placeId + "\"}")
						.principal(principal(this.ownerId)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.data.items[0].kind").value("PLACE"))
				.andExpect(jsonPath("$.data.items[0].placeId").value(this.placeId.toString()));

		verify(this.items).insertPlaceItemIfAbsent(any(), eq(this.collectionId), eq(this.placeId), any(),
				anyInt(), any());
	}

	/** 우리 목록에 없는 동네 가게를 담는 유일한 길이다. */
	@Test
	@DisplayName("🔴 직접 적은 것도 담긴다 — 우리 목록에 없는 곳")
	void addCustomItem() throws Exception {
		when(this.collections.findByIdAndUserId(this.collectionId, this.ownerId)).thenReturn(Optional.of(mine()));
		when(this.items.save(any())).thenAnswer((call) -> call.getArgument(0));

		this.mockMvc.perform(post("/api/v1/me/collections/{id}/items", this.collectionId)
						.contentType("application/json")
						.content("{\"kind\":\"CUSTOM\",\"name\":\"동네 빵집\",\"note\":\"소금빵\"}")
						.principal(principal(this.ownerId)))
				.andExpect(status().isCreated());

		verify(this.items).save(any());
		verify(this.places, never()).existsById(any());
	}

	@Test
	@DisplayName("🔴 kind 가 없으면 400 이다 — placeId 가 비었는지로 추론하지 않는다")
	void missingKindIsRejected() throws Exception {
		this.mockMvc.perform(post("/api/v1/me/collections/{id}/items", this.collectionId)
						.contentType("application/json")
						.content("{\"name\":\"이름만 있다\"}")
						.principal(principal(this.ownerId)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("COLLECTION_VALIDATION_FAILED"));
	}

	@Test
	@DisplayName("🔴 없는 장소를 담으려 하면 500 이 아니라 404 다")
	void unknownPlaceIsNotFound() throws Exception {
		when(this.collections.findByIdAndUserId(this.collectionId, this.ownerId)).thenReturn(Optional.of(mine()));
		when(this.places.existsById(this.placeId)).thenReturn(false);

		this.mockMvc.perform(post("/api/v1/me/collections/{id}/items", this.collectionId)
						.contentType("application/json")
						.content("{\"kind\":\"PLACE\",\"placeId\":\"" + this.placeId + "\"}")
						.principal(principal(this.ownerId)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("PLACE_NOT_FOUND"));

		verify(this.items, never()).save(any());
	}

	/**
	 * 화면에서 연타할 수 있고 통신이 끊기면 앱이 재시도한다. 두 번째가
	 * {@code uk_collection_item_place} 에 걸려 실패하면 안 된다.
	 */
	@Test
	@DisplayName("🔴 이미 담긴 장소를 또 담아도 성공이고 두 번 들어가지 않는다")
	void addingSamePlaceTwiceIsIdempotent() throws Exception {
		CollectionItem already = CollectionItem.ofPlace(UUID.randomUUID(), this.collectionId, this.placeId,
				null, 0, NOW);
		when(this.collections.findByIdAndUserId(this.collectionId, this.ownerId)).thenReturn(Optional.of(mine()));
		when(this.places.existsById(this.placeId)).thenReturn(true);
		// 이미 있으면 업서트가 0 을 돌려준다 — 그래도 응답은 성공이고, 돌려주는 것은
		//    「지금 담겨 있는 그 항목」이다. 그전에는 목록을 통째로 읽어 비교했다.
		when(this.items.insertPlaceItemIfAbsent(any(), eq(this.collectionId), eq(this.placeId), any(),
				anyInt(), any())).thenReturn(0);
		when(this.items.findByCollectionIdAndPlaceId(this.collectionId, this.placeId))
				.thenReturn(Optional.of(already));

		this.mockMvc.perform(post("/api/v1/me/collections/{id}/items", this.collectionId)
						.contentType("application/json")
						.content("{\"kind\":\"PLACE\",\"placeId\":\"" + this.placeId + "\"}")
						.principal(principal(this.ownerId)))
				.andExpect(status().isCreated());

		verify(this.items, never()).save(any());
	}

	// ── 도메인 규칙 ──────────────────────────────────────────────────────────

	/** 장소 항목의 이름·좌표·사진은 장소 표의 것이다. 여기서 고치면 값이 두 곳에 생긴다. */
	@Test
	@DisplayName("🔴 장소 항목의 이름은 컬렉션에서 못 고친다")
	void placeItemNameIsNotEditableHere() {
		CollectionItem item = CollectionItem.ofPlace(UUID.randomUUID(), this.collectionId, this.placeId, null, 0,
				NOW);

		org.assertj.core.api.Assertions
				.assertThatThrownBy(() -> item.editCustom("바꾼 이름", null, null, null, null, null, null, NOW))
				.isInstanceOf(IllegalStateException.class);
	}

	@Test
	@DisplayName("이름 없는 컬렉션은 만들 수 없다")
	void collectionNameIsRequired() {
		org.assertj.core.api.Assertions
				.assertThatThrownBy(() -> Collection.of(UUID.randomUUID(), this.ownerId, "  ", null, NOW))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("직접 적은 항목에는 이름이 있어야 한다")
	void customItemNameIsRequired() {
		org.assertj.core.api.Assertions
				.assertThatThrownBy(() -> CollectionItem.ofCustom(UUID.randomUUID(), this.collectionId, null,
						null, null, null, null, null, 0, NOW))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("안 적은 설명과 빈 칸을 적은 것을 같게 둔다")
	void blankDescriptionBecomesNull() {
		assertThat(Collection.of(UUID.randomUUID(), this.ownerId, "이름", "   ", NOW).getDescription()).isNull();
	}
}
