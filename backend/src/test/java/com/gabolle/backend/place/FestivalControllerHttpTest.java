package com.gabolle.backend.place;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.gabolle.backend.place.api.FestivalController;
import com.gabolle.backend.place.api.PlaceExceptionHandler;
import com.gabolle.backend.place.domain.Place;
import com.gabolle.backend.place.domain.PlaceEventPeriod;
import com.gabolle.backend.place.domain.PlaceEvidenceStatus;
import com.gabolle.backend.place.domain.PlaceFeature;
import com.gabolle.backend.place.repository.PlaceEventPeriodRepository;
import com.gabolle.backend.place.repository.PlaceFeatureRepository;
import com.gabolle.backend.place.repository.PlaceRepository;
import com.gabolle.backend.place.service.FestivalQueryService;

import tools.jackson.databind.json.JsonMapper;

/**
 * {@code GET /api/v1/festivals} 를 HTTP 로 검증한다 — S15P21E201-465.
 *
 * <p>겹침 판정과 장소 배치 조회는 {@code FestivalIntegrationTest} 가 실제 DB 로 이미 본다. 여기서는
 * 그 결과가 <b>JSON 으로 어떻게 나가는가</b> 만 본다 — 특히 {@code title}·{@code photoUrl}·
 * {@code priceLevel} 이 없을 때 칸 자체가 빠지는지는 실제 직렬화를 거쳐야 확인되므로 DB 없이 여기서
 * 본다. {@code TripControllerGetTest} 와 같은 모양으로 {@link MockMvcBuilders#standaloneSetup} 을
 * 쓰고, 리포지토리는 Mockito 로 흉내 낸다.
 *
 * <p>🔴 경로는 {@code /api/v1/festivals} 다 — 처음에는 {@code /api/v1/places/festivals} 로 만들었으나,
 * 감독자가 Jira 티켓 완료 기준과 프런트 계약을 확인하고 정정했다({@code FestivalController} javadoc
 * 참고). 자바 패키지는 여전히 {@code place} 다.
 */
class FestivalControllerHttpTest {

	private final PlaceEventPeriodRepository eventPeriodRepository = mock(PlaceEventPeriodRepository.class);

	private final PlaceRepository placeRepository = mock(PlaceRepository.class);

	private final PlaceFeatureRepository placeFeatureRepository = mock(PlaceFeatureRepository.class);

	private MockMvc mockMvc;

	/**
	 * 회차 목록을 <b>더 없는 한 쪽</b>으로 감싼다 (S15P21E201-1011). 대부분의 검사는 쪽 나눔과
	 * 무관해서 "이게 전부다" 인 쪽이면 충분하다 — 더 있는 경우는
	 * {@link #reportsHasMoreWhenPageIsNotTheLast} 가 따로 본다.
	 */
	private static Page<PlaceEventPeriod> pageOf(PlaceEventPeriod... periods) {
		return new PageImpl<>(List.of(periods));
	}

	@BeforeEach
	void setUp() {
		// 대부분의 테스트는 입장료 유무와 무관하다 — 기본값은 "행 없음"(빈 목록)으로 두고, 그것을
		// 확인하는 테스트만 따로 stub 한다.
		when(this.placeFeatureRepository.findByPlaceIdIn(any())).thenReturn(List.of());

		FestivalQueryService service = new FestivalQueryService(this.eventPeriodRepository, this.placeRepository,
				this.placeFeatureRepository, JsonMapper.builder().build());
		FestivalController controller = new FestivalController(service);
		this.mockMvc = MockMvcBuilders.standaloneSetup(controller)
				.setControllerAdvice(new PlaceExceptionHandler())
				.build();
	}

	@Test
	@DisplayName("🔴 회차 이름이 없으면 title 칸 자체가 응답에서 빠진다")
	void missingTitleIsOmittedFromJson() throws Exception {
		UUID placeId = UUID.randomUUID();
		// 🔴 fakePlace(...) 를 thenReturn(...) 안에서 만들지 않는다 — fakePlace 자체가 mock 을
		//    stubbing 하는데, 그 시점에 바깥 when(...) 이 아직 안 끝나 있어서 Mockito 가
		//    UnfinishedStubbingException 을 던진다(OriginSearchFallbackTest 와 같은 함정).
		PlaceEventPeriod period = fakePeriod(placeId, null, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 5));
		Place place = fakePlace(placeId, "이름없는축제", null, "부산 어딘가", 35.1, 129.0, null);
		when(this.eventPeriodRepository.findOverlapping(any(), any(), any())).thenReturn(pageOf(period));
		when(this.placeRepository.findAllById(any())).thenReturn(List.of(place));

		this.mockMvc.perform(get("/api/v1/festivals")
						.param("startDate", "2026-10-01")
						.param("endDate", "2026-10-05"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[0].placeId").value(placeId.toString()))
				.andExpect(jsonPath("$.data.items[0].title").doesNotExist())
				.andExpect(jsonPath("$.data.items[0].nameKo").value("이름없는축제"))
				.andExpect(jsonPath("$.data.count").value(1));
	}

	@Test
	@DisplayName("회차 이름이 있으면 그대로 나온다")
	void presentTitleIsIncluded() throws Exception {
		UUID placeId = UUID.randomUUID();
		PlaceEventPeriod period = fakePeriod(placeId, "2026 진주 남강유등축제",
				LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 10));
		Place place = fakePlace(placeId, "남강", "Namgang", "경남 진주시 어딘가", 35.1, 128.0, null);
		when(this.eventPeriodRepository.findOverlapping(any(), any(), any())).thenReturn(pageOf(period));
		when(this.placeRepository.findAllById(any())).thenReturn(List.of(place));

		this.mockMvc.perform(get("/api/v1/festivals")
						.param("startDate", "2026-10-01")
						.param("endDate", "2026-10-05"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[0].title").value("2026 진주 남강유등축제"))
				.andExpect(jsonPath("$.data.items[0].nameEn").value("Namgang"))
				.andExpect(jsonPath("$.data.items[0].overlapDates[0]").value("2026-10-01"))
				.andExpect(jsonPath("$.data.items[0].overlapDates[4]").value("2026-10-05"));
	}

	@Test
	@DisplayName("🔴 photoUrl 이 없으면(항상 이 상태다) 칸 자체가 빠진다")
	void missingPhotoUrlIsOmittedFromJson() throws Exception {
		UUID placeId = UUID.randomUUID();
		PlaceEventPeriod period = fakePeriod(placeId, "사진없음", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 5));
		Place place = fakePlace(placeId, "사진없는장소", null, "어딘가", 35.1, 129.0, null);
		when(this.eventPeriodRepository.findOverlapping(any(), any(), any())).thenReturn(pageOf(period));
		when(this.placeRepository.findAllById(any())).thenReturn(List.of(place));

		this.mockMvc.perform(get("/api/v1/festivals")
						.param("startDate", "2026-10-01")
						.param("endDate", "2026-10-05"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[0].photoUrl").doesNotExist());
	}

	@Test
	@DisplayName("photoUrl 이 있으면(장차 채워질 경로) 그대로 나온다")
	void presentPhotoUrlIsIncluded() throws Exception {
		UUID placeId = UUID.randomUUID();
		PlaceEventPeriod period = fakePeriod(placeId, "사진있음", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 5));
		Place place = fakePlace(placeId, "사진있는장소", null, "어딘가", 35.1, 129.0, "https://example.com/photo.jpg");
		when(this.eventPeriodRepository.findOverlapping(any(), any(), any())).thenReturn(pageOf(period));
		when(this.placeRepository.findAllById(any())).thenReturn(List.of(place));

		this.mockMvc.perform(get("/api/v1/festivals")
						.param("startDate", "2026-10-01")
						.param("endDate", "2026-10-05"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[0].photoUrl").value("https://example.com/photo.jpg"));
	}

	@Test
	@DisplayName("🔴 입장료 행이 없으면 priceLevel 칸 자체가 빠진다")
	void missingPriceLevelIsOmittedFromJson() throws Exception {
		UUID placeId = UUID.randomUUID();
		PlaceEventPeriod period = fakePeriod(placeId, "입장료없음", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 5));
		Place place = fakePlace(placeId, "입장료없는장소", null, "어딘가", 35.1, 129.0, null);
		when(this.eventPeriodRepository.findOverlapping(any(), any(), any())).thenReturn(pageOf(period));
		when(this.placeRepository.findAllById(any())).thenReturn(List.of(place));
		// setUp() 의 기본 stub(빈 목록)을 그대로 쓴다 — 입장료 행이 없는 상태다.

		this.mockMvc.perform(get("/api/v1/festivals")
						.param("startDate", "2026-10-01")
						.param("endDate", "2026-10-05"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[0].priceLevel").doesNotExist());
	}

	@Test
	@DisplayName("입장료 행이 있으면 값과 evidenceStatus 가 함께 나온다 — 회차마다 따로 조회하지 않는다")
	void presentPriceLevelIncludesValueAndEvidenceStatus() throws Exception {
		UUID placeId = UUID.randomUUID();
		PlaceEventPeriod period = fakePeriod(placeId, "입장료있음", LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 5));
		Place place = fakePlace(placeId, "입장료있는장소", null, "어딘가", 35.1, 129.0, null);
		PlaceFeature priceLevel = fakePriceLevelFeature(placeId, "{\"level\": 2}", PlaceEvidenceStatus.ESTIMATED);
		when(this.eventPeriodRepository.findOverlapping(any(), any(), any())).thenReturn(pageOf(period));
		when(this.placeRepository.findAllById(any())).thenReturn(List.of(place));
		when(this.placeFeatureRepository.findByPlaceIdIn(any())).thenReturn(List.of(priceLevel));

		this.mockMvc.perform(get("/api/v1/festivals")
						.param("startDate", "2026-10-01")
						.param("endDate", "2026-10-05"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items[0].priceLevel.value.level").value(2))
				.andExpect(jsonPath("$.data.items[0].priceLevel.evidenceStatus").value("ESTIMATED"));
	}

	@Test
	@DisplayName("종료일이 시작일보다 빠르면 400 INVALID_REQUEST 이고 fields 가 비어 있지 않다")
	void endDateBeforeStartDateIs400() throws Exception {
		this.mockMvc.perform(get("/api/v1/festivals")
						.param("startDate", "2026-10-10")
						.param("endDate", "2026-10-01"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"))
				.andExpect(jsonPath("$.error.fields").isNotEmpty());
	}

	@Test
	@DisplayName("367일 범위는 400 INVALID_REQUEST 다")
	void rangeOver366DaysIs400() throws Exception {
		this.mockMvc.perform(get("/api/v1/festivals")
						.param("startDate", "2026-01-01")
						.param("endDate", "2027-01-02")) // 2026-01-01 ~ 2027-01-02 = 포함 367일
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
	}

	@Test
	@DisplayName("필수 날짜 파라미터가 없으면 400 INVALID_REQUEST 다")
	void missingRequiredParameterIs400() throws Exception {
		this.mockMvc.perform(get("/api/v1/festivals").param("startDate", "2026-10-01"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("INVALID_REQUEST"));
	}

	@Test
	@DisplayName("겹치는 축제가 없으면 404 가 아니라 200 과 빈 목록이다")
	void emptyResultIs200WithEmptyList() throws Exception {
		when(this.eventPeriodRepository.findOverlapping(any(), any(), any())).thenReturn(pageOf());

		this.mockMvc.perform(get("/api/v1/festivals")
						.param("startDate", "2026-10-01")
						.param("endDate", "2026-10-05"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.items").isArray())
				.andExpect(jsonPath("$.data.items").isEmpty())
				.andExpect(jsonPath("$.data.count").value(0));
	}

	/**
	 * 🔴 S15P21E201-1011 — 상한에 걸렸다는 사실이 응답에 실린다. 이것이 없으면 목록이
	 * <b>조용히 잘리고</b>, 사용자에게는 있던 축제가 사라진 것으로 보인다.
	 */
	@Test
	@DisplayName("🔴 더 있는데 상한에 걸리면 hasMore 가 참이다 — 조용히 자르지 않는다")
	void reportsHasMoreWhenPageIsNotTheLast() throws Exception {
		UUID placeId = UUID.randomUUID();
		PlaceEventPeriod period = fakePeriod(placeId, "첫 쪽 축제",
				LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 5));
		Place place = fakePlace(placeId, "어딘가", null, "부산 어딘가", 35.1, 129.0, null);
		// 한 쪽에 1건씩인데 전체가 2건 — 즉 다음 쪽이 있다.
		when(this.eventPeriodRepository.findOverlapping(any(), any(), any()))
				.thenReturn(new PageImpl<>(List.of(period), PageRequest.of(0, 1), 2));
		when(this.placeRepository.findAllById(any())).thenReturn(List.of(place));

		this.mockMvc.perform(get("/api/v1/festivals")
						.param("startDate", "2026-10-01")
						.param("endDate", "2026-10-05")
						.param("size", "1"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.count").value(1))
				.andExpect(jsonPath("$.data.hasMore").value(true));
	}

	@Test
	@DisplayName("다음 쪽이 없으면 hasMore 가 거짓이다")
	void hasMoreIsFalseOnTheLastPage() throws Exception {
		when(this.eventPeriodRepository.findOverlapping(any(), any(), any())).thenReturn(pageOf());

		this.mockMvc.perform(get("/api/v1/festivals")
						.param("startDate", "2026-10-01")
						.param("endDate", "2026-10-05"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.hasMore").value(false));
	}

	private PlaceEventPeriod fakePeriod(UUID placeId, String title, LocalDate startDate, LocalDate endDate) {
		PlaceEventPeriod period = mock(PlaceEventPeriod.class);
		when(period.getPlaceId()).thenReturn(placeId);
		when(period.getTitle()).thenReturn(title);
		when(period.getStartDate()).thenReturn(startDate);
		when(period.getEndDate()).thenReturn(endDate);
		return period;
	}

	private Place fakePlace(UUID placeId, String nameKo, String nameEn, String address, double lat, double lng,
			String photoUrl) {
		Place place = mock(Place.class);
		when(place.getPlaceId()).thenReturn(placeId);
		when(place.getNameKo()).thenReturn(nameKo);
		when(place.getNameEn()).thenReturn(nameEn);
		when(place.getAddress()).thenReturn(address);
		when(place.getLat()).thenReturn(lat);
		when(place.getLng()).thenReturn(lng);
		when(place.getPhotoUrl()).thenReturn(photoUrl);
		return place;
	}

	private PlaceFeature fakePriceLevelFeature(UUID placeId, String valueJson, PlaceEvidenceStatus evidenceStatus) {
		PlaceFeature feature = mock(PlaceFeature.class);
		when(feature.getPlaceId()).thenReturn(placeId);
		when(feature.getFeatureType()).thenReturn("PRICE_LEVEL");
		when(feature.getValue()).thenReturn(valueJson);
		when(feature.getEvidenceStatus()).thenReturn(evidenceStatus);
		return feature;
	}
}
