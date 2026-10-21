package com.gabolle.backend.place;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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
import com.gabolle.backend.place.repository.CertifiedRestaurantRepository.AreaPoint;
import com.gabolle.backend.place.repository.PlaceFeatureRepository;
import com.gabolle.backend.place.service.CertifiedRestaurantQueryService;
import com.gabolle.backend.place.service.PlaceCategoryService;
import com.gabolle.backend.place.service.PlaceFacetService;
import com.gabolle.backend.place.service.PlaceSearchService;

/**
 * 「맛집」·「카페」 끝점 둘의 JSON 모양과 400 응답 (S15P21E201-2051, 카페·내 위치는 S15P21E201-2059) — DB 없이 실제 직렬화를 거친다. 거르고 세는 JPQL 은
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

	// ── 카페 · 내 위치 (S15P21E201-2059) ─────────────────────────────────────

	@Test
	@DisplayName("🔴 kind=CAFE 면 카페 낱말(LOCAL_CAFE)로 부른다 — 소문자도 받고, 안 주면 맛집이다(옛 앱이 그대로 돈다)")
	void kindSelectsTheExploreKey() throws Exception {
		when(this.repository.findInGu(any(), any(), any(), any(), any(Limit.class))).thenReturn(List.of());
		when(this.repository.findInArea(any(), any(), any(), any(), any(Limit.class))).thenReturn(List.of());
		when(this.repository.findAreaPlaces(any(), any(), any())).thenReturn(List.of());

		this.mockMvc.perform(get("/api/v1/places/restaurants").param("kind", "CAFE").param("gu", "해운대구")).andExpect(status().isOk());
		verify(this.repository).findInGu(eq("INTEREST_TAG"), eq("LOCAL_CAFE"), eq("ADMIN_AREA_TAG"), eq("해운대구/%"), any(Limit.class));

		this.mockMvc.perform(get("/api/v1/places/restaurants").param("kind", "cafe").param("gu", "해운대구").param("dong", "우동"))
				.andExpect(status().isOk());
		verify(this.repository).findInArea(eq("INTEREST_TAG"), eq("LOCAL_CAFE"), eq("ADMIN_AREA_TAG"), eq("해운대구/우동"), any(Limit.class));

		this.mockMvc.perform(get("/api/v1/places/restaurants/areas").param("kind", "CAFE")).andExpect(status().isOk());
		verify(this.repository).findAreaPlaces("INTEREST_TAG", "LOCAL_CAFE", "ADMIN_AREA_TAG");

		// kind 가 없으면 예전 그대로 맛집이다.
		this.mockMvc.perform(get("/api/v1/places/restaurants/areas")).andExpect(status().isOk());
		verify(this.repository).findAreaPlaces("INTEREST_TAG", "LOCAL_RESTAURANT", "ADMIN_AREA_TAG");
	}

	@Test
	@DisplayName("🔴 모르는 kind 는 400 이다 — 오타를 맛집으로 읽어 엉뚱한 목록을 주지 않는다")
	void unknownKindIs400() throws Exception {
		this.mockMvc.perform(get("/api/v1/places/restaurants").param("kind", "BAR").param("gu", "해운대구"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"))
				.andExpect(jsonPath("$.error.fields[0]").value("kind"));
		this.mockMvc.perform(get("/api/v1/places/restaurants/areas").param("kind", "RESTAURANTS")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.fields[0]").value("kind"));
	}

	@Test
	@DisplayName("🔴 좌표를 주면 가장 가까운 곳의 구가 nearestGu 로 온다 — 구 목록은 그대로다")
	void nearestGuComesWithTheAreaList() throws Exception {
		when(this.repository.findAreaPlaces("INTEREST_TAG", "LOCAL_CAFE", "ADMIN_AREA_TAG")).thenReturn(List.of(
				areaPlace(UUID.randomUUID(), "해운대구/우동"), areaPlace(UUID.randomUUID(), "중구/남포동")));
		// 내 위치(35.1631, 129.1636)에서 우동 카페는 약 90m, 남포동 카페는 5km 를 훨씬 넘는다(가짜 응답이 상자 거르기를 흉내 내지 않으므로 거리로 다시 거른다) — 가까운 쪽의 구가 답이다.
		when(this.repository.findAreaPointsWithin(eq("INTEREST_TAG"), eq("LOCAL_CAFE"), eq("ADMIN_AREA_TAG"), anyDouble(), anyDouble(),
				anyDouble(), anyDouble(), any(Limit.class))).thenReturn(List.of(
						areaPoint(UUID.randomUUID(), "중구/남포동", 35.0980, 129.0320),
						areaPoint(UUID.randomUUID(), "해운대구/우동", 35.1639, 129.1636)));

		this.mockMvc.perform(get("/api/v1/places/restaurants/areas").param("kind", "CAFE").param("lat", "35.1631").param("lng", "129.1636"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.nearestGu").value("해운대구"))
				.andExpect(jsonPath("$.data.areas.length()").value(2))
				.andExpect(jsonPath("$.data.generatedAt").exists());
	}

	@Test
	@DisplayName("🔴 가장 가까운 곳이 5km 를 넘거나 후보가 없으면 nearestGu 는 null 이다 — 칸은 그대로 있고 값만 비었다")
	void nearestGuIsNullWhenNothingIsNear() throws Exception {
		when(this.repository.findAreaPlaces(any(), any(), any())).thenReturn(List.of(areaPlace(UUID.randomUUID(), "중구/남포동")));
		// 상자에는 들었지만 직선거리가 5km 를 넘는 모서리 후보뿐이다(위도 0.044° · 경도 0.054° ≈ 6.9km).
		when(this.repository.findAreaPointsWithin(any(), any(), any(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), any(Limit.class)))
				.thenReturn(List.of(areaPoint(UUID.randomUUID(), "중구/남포동", 35.1440, 129.0540)));

		this.mockMvc.perform(get("/api/v1/places/restaurants/areas").param("lat", "35.1000").param("lng", "129.0000"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.nearestGu").value(nullValue()));

		when(this.repository.findAreaPointsWithin(any(), any(), any(), anyDouble(), anyDouble(), anyDouble(), anyDouble(), any(Limit.class)))
				.thenReturn(List.of());
		this.mockMvc.perform(get("/api/v1/places/restaurants/areas").param("lat", "35.1000").param("lng", "129.0000"))
				.andExpect(status().isOk()).andExpect(jsonPath("$.data.nearestGu").value(nullValue()));
	}

	@Test
	@DisplayName("🔴 좌표를 안 주면 위치 질의를 아예 안 한다 — nearestGu 는 null 이다(위치를 거부한 사람의 길은 예전 그대로)")
	void noCoordinatesMeansNoLocationQuery() throws Exception {
		when(this.repository.findAreaPlaces(any(), any(), any())).thenReturn(List.of(areaPlace(UUID.randomUUID(), "중구/남포동")));

		this.mockMvc.perform(get("/api/v1/places/restaurants/areas")).andExpect(status().isOk())
				.andExpect(jsonPath("$.data.nearestGu").value(nullValue()));

		verify(this.repository, never()).findAreaPointsWithin(any(), any(), any(), anyDouble(), anyDouble(), anyDouble(), anyDouble(),
				any(Limit.class));
	}

	@Test
	@DisplayName("🔴 좌표가 하나만 오거나 범위 밖이면 400 이고 어느 칸이 문제인지 알린다")
	void badCoordinatesAre400() throws Exception {
		this.mockMvc.perform(get("/api/v1/places/restaurants/areas").param("lat", "35.1")).andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_REQUEST")).andExpect(jsonPath("$.error.fields[0]").value("lat"))
				.andExpect(jsonPath("$.error.fields[1]").value("lng"));
		this.mockMvc.perform(get("/api/v1/places/restaurants/areas").param("lat", "95").param("lng", "129"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields[0]").value("lat"));
		this.mockMvc.perform(get("/api/v1/places/restaurants/areas").param("lat", "35").param("lng", "-181"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields[0]").value("lng"));
		this.mockMvc.perform(get("/api/v1/places/restaurants/areas").param("lat", "NaN").param("lng", "129"))
				.andExpect(status().isBadRequest()).andExpect(jsonPath("$.error.fields[0]").value("lat"));

		verify(this.repository, never()).findAreaPointsWithin(any(), any(), any(), anyDouble(), anyDouble(), anyDouble(), anyDouble(),
				any(Limit.class));
	}

	@Test
	@DisplayName("🔴 카페 카드는 소개한 목록을 낱말로 싣고, 레드테이블의 방송·수상 표시는 true 일 때만 broadcastOrAward 로 나온다 — 깨진 값은 거짓이다")
	void cafeItemsCarryTheirSourcesAndTheBroadcastFlag() throws Exception {
		Place media = place("방송카페", null, null);
		Place plain = place("평범카페", null, null);
		Place broken = place("깨진값카페", null, null);
		when(this.repository.findInArea(eq("INTEREST_TAG"), eq("LOCAL_CAFE"), eq("ADMIN_AREA_TAG"), eq("중구/남포동"), any(Limit.class)))
				.thenReturn(List.of(media, plain, broken));
		when(this.placeFeatureRepository.findByPlaceIdIn(any())).thenReturn(List.of(
				area(media, "중구/남포동"), source(media, "LOCAL_SURVEY", "{\"source\":\"현지인 추천\",\"recommenders\":3}"),
				source(media, "KTO_CAFE", "{\"source\":\"한국관광공사\"}"),
				source(media, "REDTABLE_FEATURED", "{\"source\":\"레드테이블\",\"broadcastOrAward\":true}"),
				area(plain, "중구/남포동"), source(plain, "REDTABLE_FEATURED", "{\"source\":\"레드테이블\"}"),
				area(broken, "중구/남포동"), source(broken, "REDTABLE_FEATURED", "이건 JSON 이 아니다")));

		this.mockMvc.perform(get("/api/v1/places/restaurants").param("kind", "CAFE").param("gu", "중구").param("dong", "남포동"))
				.andExpect(status().isOk())
				// 소개한 목록이 많은 카페가 먼저, 낱말은 선언 순서(관광공사 → 레드테이블 → 현지인 추천)다.
				.andExpect(jsonPath("$.data.items[0].nameKo").value("방송카페"))
				.andExpect(jsonPath("$.data.items[0].certifiedSources.length()").value(3))
				.andExpect(jsonPath("$.data.items[0].certifiedSources[0]").value("KTO_CAFE"))
				.andExpect(jsonPath("$.data.items[0].certifiedSources[1]").value("REDTABLE_FEATURED"))
				.andExpect(jsonPath("$.data.items[0].certifiedSources[2]").value("LOCAL_SURVEY"))
				.andExpect(jsonPath("$.data.items[0].broadcastOrAward").value(true))
				// 표시가 없거나 값을 읽을 수 없으면 칸째 없다 — 모르는 것을 방송·수상으로 부풀리지 않는다.
				.andExpect(jsonPath("$.data.items[1].nameKo").value("깨진값카페"))
				.andExpect(jsonPath("$.data.items[1].broadcastOrAward").doesNotExist())
				.andExpect(jsonPath("$.data.items[2].nameKo").value("평범카페"))
				.andExpect(jsonPath("$.data.items[2].broadcastOrAward").doesNotExist())
				.andExpect(jsonPath("$.data.items[2].certifiedSources[0]").value("REDTABLE_FEATURED"));
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
		return source(place, key, "{\"source\":\"s\"}");
	}

	private static PlaceFeature source(Place place, String key, String valueJson) {
		return PlaceFeature.imported(UUID.randomUUID(), place.getPlaceId(), CertifiedRestaurant.SOURCE_FEATURE_TYPE, key,
				valueJson, PlaceEvidenceStatus.VERIFIED, CertifiedRestaurant.SOURCE_TYPE, "x", null, "test",
				OffsetDateTime.now());
	}

	private static AreaPoint areaPoint(UUID placeId, String areaKey, double lat, double lng) {
		return new AreaPoint() {

			@Override
			public UUID getPlaceId() {
				return placeId;
			}

			@Override
			public String getAreaKey() {
				return areaKey;
			}

			@Override
			public Double getLat() {
				return lat;
			}

			@Override
			public Double getLng() {
				return lng;
			}
		};
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
