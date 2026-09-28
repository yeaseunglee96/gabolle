package com.gabolle.backend.place;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.gabolle.backend.place.api.PlaceDetailController;
import com.gabolle.backend.place.api.PlaceDetailResponse;
import com.gabolle.backend.place.api.PlaceExceptionHandler;
import com.gabolle.backend.place.service.PlaceDetailService;

/**
 * {@code Accept-Language} 헤더와 {@code itineraryId} 질의 파라미터가 HTTP 요청에서 서비스까지
 * 실제로 전달되는지 본다. 언어 선택과 포함 여부 판정 자체는 서비스 계층 테스트가 재지만,
 * 그쪽은 서비스를 직접 부르므로 컨트롤러가 값을 안 받거나 받아서 안 넘겨도 전부 초록이다.
 */
class PlaceDetailAcceptLanguageWiringTest {

	private PlaceDetailService placeDetailService;

	private MockMvc mockMvc;

	private final UUID placeId = UUID.randomUUID();

	@BeforeEach
	void setUp() {
		this.placeDetailService = mock(PlaceDetailService.class);
		when(this.placeDetailService.get(any(UUID.class), any(), any(), any())).thenReturn(stubResponse());
		this.mockMvc = MockMvcBuilders
				.standaloneSetup(new PlaceDetailController(this.placeDetailService))
				.setControllerAdvice(new PlaceExceptionHandler())
				.build();
	}

	@Test
	@DisplayName("🔴 Accept-Language 헤더가 서비스까지 그대로 전달된다")
	void headerReachesTheService() throws Exception {
		this.mockMvc.perform(get("/api/v1/places/{placeId}", this.placeId)
				.header("Accept-Language", "en-US,en;q=0.9,ko;q=0.8"))
				.andExpect(status().isOk());

		verify(this.placeDetailService).get(eq(this.placeId), any(), eq("en-US,en;q=0.9,ko;q=0.8"), eq(null));
	}

	@Test
	@DisplayName("헤더가 없으면 null 로 넘어간다 — 요청이 실패하지 않는다")
	void missingHeaderIsPassedAsNull() throws Exception {
		this.mockMvc.perform(get("/api/v1/places/{placeId}", this.placeId))
				.andExpect(status().isOk());

		verify(this.placeDetailService).get(eq(this.placeId), any(), eq(null), eq(null));
	}

	@Test
	@DisplayName("itineraryId 질의 파라미터가 서비스까지 그대로 전달된다")
	void itineraryIdParameterReachesTheService() throws Exception {
		UUID itineraryId = UUID.randomUUID();

		this.mockMvc.perform(get("/api/v1/places/{placeId}", this.placeId)
				.param("itineraryId", itineraryId.toString()))
				.andExpect(status().isOk());

		verify(this.placeDetailService).get(eq(this.placeId), any(), eq(null), eq(itineraryId));
	}

	@Test
	@DisplayName("itineraryId 형식이 UUID 가 아니면 400 이다 — 일정 존재 여부와 무관한 형식 오류다")
	void malformedItineraryIdIsRejectedAsBadRequest() throws Exception {
		this.mockMvc.perform(get("/api/v1/places/{placeId}", this.placeId)
				.param("itineraryId", "not-a-uuid"))
				.andExpect(status().isBadRequest());
	}

	private PlaceDetailResponse stubResponse() {
		return new PlaceDetailResponse(this.placeId, "해운대해수욕장", null, null, null, null, null,
				new PlaceDetailResponse.Provenance(null, null, null, null, null),
				List.of(),
				new PlaceDetailResponse.ItineraryInclusion("UNAVAILABLE", "ITINERARY_NOT_SPECIFIED"),
				null, null, null, null, null, null, "ko", null);
	}
}
