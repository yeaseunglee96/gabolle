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
 */
class PlaceDetailAcceptLanguageWiringTest {

	private PlaceDetailService placeDetailService;

	private MockMvc mockMvc;

	private final UUID placeId = UUID.randomUUID();

	@BeforeEach
	void setUp() {
		this.placeDetailService = mock(PlaceDetailService.class);
		when(this.placeDetailService.get(any(UUID.class), any(), any())).thenReturn(stubResponse());
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

		verify(this.placeDetailService).get(eq(this.placeId), any(), eq("en-US,en;q=0.9,ko;q=0.8"));
	}

	@Test
	@DisplayName("헤더가 없으면 null 로 넘어간다 — 요청이 실패하지 않는다")
	void missingHeaderIsPassedAsNull() throws Exception {
		this.mockMvc.perform(get("/api/v1/places/{placeId}", this.placeId))
				.andExpect(status().isOk());

		verify(this.placeDetailService).get(eq(this.placeId), any(), eq(null));
	}

	private PlaceDetailResponse stubResponse() {
		return new PlaceDetailResponse(this.placeId, "해운대해수욕장", null, null, null, null, null,
				new PlaceDetailResponse.Provenance(null, null, null, null, null),
				List.of(),
				new PlaceDetailResponse.ItineraryInclusion("UNAVAILABLE", "NO_ITEM_TABLE"),
				null, null, null, null, null, "ko");
	}
}
