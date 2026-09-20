package com.gabolle.backend.place;

import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.gabolle.backend.place.api.PlaceExceptionHandler;
import com.gabolle.backend.place.api.TaxiCardController;
import com.gabolle.backend.place.api.TaxiCardResponse;
import com.gabolle.backend.place.service.PlaceNotFoundException;
import com.gabolle.backend.place.service.TaxiCardService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 택시 목적지 카드의 HTTP 계약. standalone MockMvc 에 서비스는 mock 이라 라우팅·직렬화·예외
 * 매핑만 본다 — 문장 조립과 언어 판정은 {@code TaxiCardServiceIntegrationTest} 가 진짜 DB 로 잰다.
 */
class TaxiCardControllerTest {

	private TaxiCardService taxiCardService;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		this.taxiCardService = mock(TaxiCardService.class);
		TaxiCardController controller = new TaxiCardController(this.taxiCardService);
		this.mockMvc = MockMvcBuilders.standaloneSetup(controller)
				.setControllerAdvice(new PlaceExceptionHandler())
				.build();
	}

	@Test
	@DisplayName("🔴 영문 주소가 없으면 응답에서 addressEn 키 자체가 빠진다")
	void addressEnKeyAbsentWhenNull() throws Exception {
		UUID placeId = UUID.randomUUID();
		when(this.taxiCardService.get(eq(placeId), any())).thenReturn(new TaxiCardResponse(
				placeId, "남포동", "부산 중구 남포동 12-3", null, "ko", "이 주소로 가주세요, 부산 중구 남포동 12-3"));

		MvcResult result = this.mockMvc.perform(get("/api/v1/places/{placeId}/taxi-card", placeId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.addressEn").doesNotExist())
				.andExpect(jsonPath("$.data.nameKo").value("남포동"))
				.andExpect(jsonPath("$.data.driverSentence").value("이 주소로 가주세요, 부산 중구 남포동 12-3"))
				.andReturn();

		// jsonPath().doesNotExist() 는 "키가 없다" 와 "키는 있는데 값이 null 이다" 를 구분하지
		// 못해 둘 다 통과시킨다. 그래서 원본 JSON 문자열에 키 이름이 있는지까지 본다.
		String body = result.getResponse().getContentAsString();
		assertThat(body).doesNotContain("addressEn");
	}

	@Test
	@DisplayName("영문 주소가 있으면 addressEn 키가 값과 함께 온다")
	void addressEnKeyPresentWhenSet() throws Exception {
		UUID placeId = UUID.randomUUID();
		when(this.taxiCardService.get(eq(placeId), any())).thenReturn(new TaxiCardResponse(
				placeId, "남포동", "부산 중구 남포동 12-3", "12-3 Nampo-dong, Busan", "ko",
				"이 주소로 가주세요, 부산 중구 남포동 12-3"));

		this.mockMvc.perform(get("/api/v1/places/{placeId}/taxi-card", placeId))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.addressEn").value("12-3 Nampo-dong, Busan"));
	}

	@Test
	@DisplayName("🔴 없는 장소는 404 다 — 500 이 나가면 안 된다")
	void unknownPlaceIsNotFoundNotServerError() throws Exception {
		UUID placeId = UUID.randomUUID();
		when(this.taxiCardService.get(eq(placeId), any())).thenThrow(new PlaceNotFoundException(placeId));

		this.mockMvc.perform(get("/api/v1/places/{placeId}/taxi-card", placeId))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("PLACE_NOT_FOUND"))
				.andExpect(jsonPath("$.data").doesNotExist());
	}

	@Test
	@DisplayName("Accept-Language 헤더를 서비스에 그대로 넘긴다")
	void forwardsAcceptLanguageHeaderToService() throws Exception {
		UUID placeId = UUID.randomUUID();
		when(this.taxiCardService.get(eq(placeId), eq("en-US,en;q=0.9"))).thenReturn(new TaxiCardResponse(
				placeId, "남포동", "부산 중구 남포동 12-3", null, "ko", "이 주소로 가주세요, 부산 중구 남포동 12-3"));

		this.mockMvc.perform(get("/api/v1/places/{placeId}/taxi-card", placeId)
					.header("Accept-Language", "en-US,en;q=0.9"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.resolvedLanguage").value("ko"));

		verify(this.taxiCardService).get(placeId, "en-US,en;q=0.9");
	}

	@Test
	@DisplayName("Accept-Language 헤더가 없으면 null 을 그대로 서비스에 넘긴다")
	void passesNullWhenHeaderMissing() throws Exception {
		UUID placeId = UUID.randomUUID();
		when(this.taxiCardService.get(eq(placeId), isNull())).thenReturn(new TaxiCardResponse(
				placeId, "남포동", "부산 중구 남포동 12-3", null, "ko", "이 주소로 가주세요, 부산 중구 남포동 12-3"));

		this.mockMvc.perform(get("/api/v1/places/{placeId}/taxi-card", placeId))
				.andExpect(status().isOk());

		verify(this.taxiCardService).get(eq(placeId), isNull());
	}

	@Test
	@DisplayName("X-User-Id 없이도 200 이 온다 — 이 조회는 사용자 식별이 필요 없다")
	void doesNotRequireUserIdHeader() throws Exception {
		UUID placeId = UUID.randomUUID();
		when(this.taxiCardService.get(eq(placeId), any())).thenReturn(new TaxiCardResponse(
				placeId, "남포동", "부산 중구 남포동 12-3", null, "ko", "이 주소로 가주세요, 부산 중구 남포동 12-3"));

		this.mockMvc.perform(get("/api/v1/places/{placeId}/taxi-card", placeId))
				.andExpect(status().isOk());
	}
}
