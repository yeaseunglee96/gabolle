package com.gabolle.backend.place;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Limit;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.gabolle.backend.place.api.PlaceExceptionHandler;
import com.gabolle.backend.place.api.PlaceQueryController;
import com.gabolle.backend.place.domain.CertifiedRestaurant;
import com.gabolle.backend.place.domain.Place;
import com.gabolle.backend.place.domain.PlaceEvidenceStatus;
import com.gabolle.backend.place.domain.PlaceFeature;
import com.gabolle.backend.place.repository.CertifiedRestaurantRepository;
import com.gabolle.backend.place.repository.CertifiedRestaurantRepository.AreaPlace;
import com.gabolle.backend.place.repository.PlaceFeatureRepository;
import com.gabolle.backend.place.service.CertifiedRestaurantQueryService;
import com.gabolle.backend.place.service.PlaceCategoryService;
import com.gabolle.backend.place.service.PlaceFacetService;
import com.gabolle.backend.place.service.PlaceSearchService;

/**
 * 「맛집」 끝점 둘의 JSON 모양과 400 응답 (S15P21E201-2051) — DB 없이 실제 직렬화를 거친다. 거르고 세는 JPQL 은
 * {@code CertifiedRestaurantApiIntegrationTest} 가 진짜 PostgreSQL 로 본다.
 *
 * <p>특히 {@code @JsonUnwrapped}(장소 기본 칸을 한 겹 풀어 싣기)가 실제로 풀리는지, 값이 없는 사진 칸이 빠지는지는 직렬화를 거쳐야 알 수 있다.
 */
class CertifiedRestaurantControllerHttpTest {

	private final CertifiedRestaurantRepository repository = mock(CertifiedRestaurantRepository.class);

	private final PlaceFeatureRepository placeFeatureRepository = mock(PlaceFeatureRepository.class);

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		CertifiedRestaurantQueryService service = new CertifiedRestaurantQueryService(this.repository,
				this.placeFeatureRepository);
		PlaceQueryController controller = new PlaceQueryController(mock(PlaceSearchService.class),
				mock(PlaceFacetService.class), mock(PlaceCategoryService.class), service);
		this.mockMvc = MockMvcBuilders.standaloneSetup(controller).setControllerAdvice(new PlaceExceptionHandler()).build();
	}

	@Test
	@DisplayName("🔴 맛집 목록 한 줄은 장소 기본 칸을 한 겹 풀어 싣고 gu·dong·certifiedSources 를 더한다 — 인증 목록이 많은 집이 먼저")
	void listItemsAreFlatAndSortedByCertificationCount() throws Exception {
		Place plain = place("나식당", null, null);
		Place photographed = place("가식당", "https://example.invalid/a.jpg", "출처 : 시험");
		when(this.repository.findInArea(eq("INTEREST_TAG"), eq("LOCAL_RESTAURANT"), eq("ADMIN_AREA_TAG"),
				eq("해운대구/우동"), any(Limit.class))).thenReturn(List.of(plain, photographed));
		when(this.placeFeatureRepository.findByPlaceIdIn(any())).thenReturn(List.of(
				area(plain, "해운대구/우동"), source(plain, "MODEL_RESTAURANT"),
				area(photographed, "해운대구/우동"), source(photographed, "TAXI_DRIVER_PICK"),
				source(photographed, "BUSAN_FOOD_INFO")));

		this.mockMvc.perform(get("/api/v1/places/restaurants").param("gu", "해운대구").param("dong", "우동"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.limit").value(100))
				.andExpect(jsonPath("$.data.hasNext").value(false))
				// 둘 인증한 가식당이 앞이다.
				.andExpect(jsonPath("$.data.items[0].placeId").value(photographed.getPlaceId().toString()))
				.andExpect(jsonPath("$.data.items[0].nameKo").value("가식당"))
				.andExpect(jsonPath("$.data.items[0].category").value("FOOD"))
				.andExpect(jsonPath("$.data.items[0].gu").value("해운대구"))
				.andExpect(jsonPath("$.data.items[0].dong").value("우동"))
				.andExpect(jsonPath("$.data.items[0].certifiedSources[0]").value("BUSAN_FOOD_INFO"))
				.andExpect(jsonPath("$.data.items[0].certifiedSources[1]").value("TAXI_DRIVER_PICK"))
				.andExpect(jsonPath("$.data.items[0].photoUrl").value("https://example.invalid/a.jpg"))
				.andExpect(jsonPath("$.data.items[0].photoSource").value("출처 : 시험"))
				// 한 겹 풀어 실었다 — place 라는 안쪽 칸이 없다.
				.andExpect(jsonPath("$.data.items[0].place").doesNotExist())
				.andExpect(jsonPath("$.data.items[1].nameKo").value("나식당"))
				.andExpect(jsonPath("$.data.items[1].certifiedSources.length()").value(1))
				// 사진이 없으면 칸째 빠진다 — 기존 목록과 같은 규칙이다.
				.andExpect(jsonPath("$.data.items[1].photoUrl").doesNotExist())
				.andExpect(jsonPath("$.data.items[1].photoSource").doesNotExist());
	}

	@Test
	@DisplayName("동을 빼면 구 전체를 부르고, 동을 주면 「구/동」 열쇠로 부른다 — 한글 값이 그대로 닿는다")
	void dongSelectsBetweenGuAndArea() throws Exception {
		when(this.repository.findInGu(any(), any(), any(), any(), any(Limit.class))).thenReturn(List.of());
		when(this.repository.findInArea(any(), any(), any(), any(), any(Limit.class))).thenReturn(List.of());

		this.mockMvc.perform(get("/api/v1/places/restaurants").param("gu", "해운대구")).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items.length()").value(0));
		verify(this.repository).findInGu(eq("INTEREST_TAG"), eq("LOCAL_RESTAURANT"), eq("ADMIN_AREA_TAG"),
				eq("해운대구/%"), any(Limit.class));

		this.mockMvc.perform(get("/api/v1/places/restaurants").param("gu", "해운대구").param("dong", "우동"))
				.andExpect(status().isOk());
		verify(this.repository).findInArea(eq("INTEREST_TAG"), eq("LOCAL_RESTAURANT"), eq("ADMIN_AREA_TAG"),
				eq("해운대구/우동"), any(Limit.class));
	}

	@Test
	@DisplayName("🔴 구가 없거나 limit 이 범위 밖이면 400 INVALID_REQUEST 이고 어느 칸이 문제인지 알린다")
	void badRequestsAre400() throws Exception {
		this.mockMvc.perform(get("/api/v1/places/restaurants")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"))
				.andExpect(jsonPath("$.error.fields[0]").value("gu"));

		this.mockMvc.perform(get("/api/v1/places/restaurants").param("gu", "해운대구").param("limit", "0"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields[0]").value("limit"));
		this.mockMvc.perform(get("/api/v1/places/restaurants").param("gu", "해운대구").param("limit", "201"))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("구·동 목록은 구마다 동을 안고, 식당이 많은 곳이 먼저이며, 모양이 틀린 열쇠는 건너뛴다")
	void areasNestDongsUnderGu() throws Exception {
		UUID a = UUID.randomUUID();
		UUID b = UUID.randomUUID();
		UUID c = UUID.randomUUID();
		UUID d = UUID.randomUUID();
		when(this.repository.findAreaPlaces("INTEREST_TAG", "LOCAL_RESTAURANT", "ADMIN_AREA_TAG")).thenReturn(List.of(
				areaPlace(a, "중구/남포동"), areaPlace(b, "해운대구/우동"), areaPlace(c, "해운대구/우동"),
				areaPlace(d, "해운대구/중동"), areaPlace(UUID.randomUUID(), "열쇠모양이틀림")));

		this.mockMvc.perform(get("/api/v1/places/restaurants/areas"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.areas.length()").value(2))
				.andExpect(jsonPath("$.data.areas[0].gu").value("해운대구"))
				.andExpect(jsonPath("$.data.areas[0].placeCount").value(3))
				.andExpect(jsonPath("$.data.areas[0].dongs[0].dong").value("우동"))
				.andExpect(jsonPath("$.data.areas[0].dongs[0].placeCount").value(2))
				.andExpect(jsonPath("$.data.areas[0].dongs[1].dong").value("중동"))
				.andExpect(jsonPath("$.data.areas[1].gu").value("중구"))
				.andExpect(jsonPath("$.data.areas[1].placeCount").value(1))
				.andExpect(jsonPath("$.data.generatedAt").exists());
	}

	@Test
	@DisplayName("🔴 한 장소가 구·동 표식을 둘 가져도 구 수는 장소를 중복 없이 센다")
	void aPlaceWithTwoAreaMarksIsCountedOncePerGu() throws Exception {
		UUID twice = UUID.randomUUID();
		when(this.repository.findAreaPlaces(any(), any(), any())).thenReturn(List.of(
				areaPlace(twice, "해운대구/우동"), areaPlace(twice, "해운대구/중동")));

		this.mockMvc.perform(get("/api/v1/places/restaurants/areas"))
				.andExpect(jsonPath("$.data.areas[0].placeCount").value(1))
				.andExpect(jsonPath("$.data.areas[0].dongs.length()").value(2));
	}

	@Test
	@DisplayName("자료가 아직 없으면 빈 목록이다 — 오류가 아니다")
	void noDataIsAnEmptyList() throws Exception {
		when(this.repository.findAreaPlaces(any(), any(), any())).thenReturn(List.of());

		this.mockMvc.perform(get("/api/v1/places/restaurants/areas")).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.areas.length()").value(0));
	}

	// ── 도우미 ────────────────────────────────────────────────────────────

	private static Place place(String name, String photoUrl, String photoSource) {
		return Place.imported(UUID.randomUUID(), name, "FOOD", "부산광역시 해운대구 시험로 1", 35.16, 129.16,
				CertifiedRestaurant.SOURCE_TYPE, "T-" + name, OffsetDateTime.now(), null, "test", photoUrl, photoSource);
	}

	private static PlaceFeature area(Place place, String key) {
		return PlaceFeature.imported(UUID.randomUUID(), place.getPlaceId(), CertifiedRestaurant.AREA_FEATURE_TYPE, key,
				"{\"gu\":\"g\",\"dong\":\"d\"}", PlaceEvidenceStatus.ESTIMATED, CertifiedRestaurant.SOURCE_TYPE, "x", null,
				"test", OffsetDateTime.now());
	}

	private static PlaceFeature source(Place place, String key) {
		return PlaceFeature.imported(UUID.randomUUID(), place.getPlaceId(), CertifiedRestaurant.SOURCE_FEATURE_TYPE, key,
				"{\"source\":\"s\"}", PlaceEvidenceStatus.VERIFIED, CertifiedRestaurant.SOURCE_TYPE, "x", null, "test",
				OffsetDateTime.now());
	}

	private static AreaPlace areaPlace(UUID placeId, String areaKey) {
		return new AreaPlace() {

			@Override
			public UUID getPlaceId() {
				return placeId;
			}

			@Override
			public String getAreaKey() {
				return areaKey;
			}
		};
	}
}
