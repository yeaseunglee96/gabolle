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
 * {@code Accept-Language} 가 <b>HTTP 요청에서 서비스까지 실제로 전달되는지</b> 본다
 * (S15P21E201-430, 부분).
 *
 * <h2>🔴 왜 서비스 테스트만으로는 부족한가</h2>
 * 언어 선택 자체는 서비스 계층 테스트가 이미 잰다. 그런데 그 테스트들은 서비스를 <b>직접</b>
 * 부르기 때문에, 컨트롤러가 헤더를 안 받거나 받아서 안 넘기면 <b>전부 초록인 채로 기능이 죽어
 * 있다.</b> 실제로 이 묶음에서 그 상태로 한 바퀴 돌았다 — 서비스는 3인자 오버로드까지 준비돼
 * 있었는데 컨트롤러가 2인자로 부르고 있었다.
 *
 * <p>같은 종류를 오늘 한 번 더 겪었다. 소셜 가입 요청 본문의 필수 여부가 서비스 테스트로는
 * 전부 초록이었는데, HTTP 본문 경로를 지나지 않아 역직렬화 실패를 아무도 못 봤다
 * ({@code INC-AUTH-008}). <b>계약이 HTTP 층에 있으면 테스트도 HTTP 층을 지나야 한다.</b>
 *
 * <p>그래서 뒤에 붙은 {@code itineraryId} 질의 파라미터(S15P21E201-476)의 배선도 여기서 본다 —
 * 헤더든 질의 파라미터든 같은 종류의 배선이고, 같은 방식으로 조용히 끊긴다.
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

	/**
	 * 위 두 테스트와 같은 이유로 HTTP 층을 지난다 (S15P21E201-476). 포함 여부 판정은 서비스·포트
	 * 테스트가 이미 재지만, 컨트롤러가 질의 파라미터를 안 받거나 받아서 안 넘기면 그 테스트들이
	 * 전부 초록인 채로 기능이 죽는다 — 그리고 그 죽음은 언제나 {@code UNAVAILABLE} 로 나가서
	 * "아직 못 만든 것" 과 구분되지 않는다.
	 */
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
				null, null, null, null, null, null, "ko");
	}
}
