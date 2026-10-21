package com.gabolle.backend.trip.presentation;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.gabolle.backend.trip.application.TripQueryService;
import com.gabolle.backend.trip.application.TripRatingService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** {@code /api/v1/trips/{tripId}/rating} 표현 계층 (S15P21E201-1908). */
class TripRatingControllerTest {

	private static final UUID USER_ID = UUID.randomUUID();

	private static final String TRIP_ID = UUID.randomUUID().toString();

	private TripRatingService service;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		this.service = mock(TripRatingService.class);
		this.mockMvc = MockMvcBuilders.standaloneSetup(new TripRatingController(this.service))
				.setControllerAdvice(new TripRatingExceptionHandler())
				.build();
	}

	private static Authentication principal() {
		return new UsernamePasswordAuthenticationToken(USER_ID.toString(), null, List.of());
	}

	@Test
	@DisplayName("GET 이 내 점수·평균·개수를 돌려준다")
	void getReturnsRating() throws Exception {
		given(this.service.find(TRIP_ID, USER_ID)).willReturn(new TripRatingService.Rating(4, 4.5, 2));

		this.mockMvc.perform(get("/api/v1/trips/" + TRIP_ID + "/rating").principal(principal()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.myScore").value(4))
				.andExpect(jsonPath("$.data.average").value(4.5))
				.andExpect(jsonPath("$.data.count").value(2));
	}

	@Test
	@DisplayName("PUT 이 인증 주체로 본문의 점수를 매긴다")
	void putRates() throws Exception {
		given(this.service.rate(TRIP_ID, USER_ID, 5)).willReturn(new TripRatingService.Rating(5, 5.0, 1));

		this.mockMvc.perform(put("/api/v1/trips/" + TRIP_ID + "/rating").principal(principal())
				.contentType(MediaType.APPLICATION_JSON).content("{\"score\":5}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.myScore").value(5));
	}

	@Test
	@DisplayName("🔴 범위 밖 점수는 400")
	void putOutOfRangeIs400() throws Exception {
		given(this.service.rate(eq(TRIP_ID), eq(USER_ID), any()))
				.willThrow(new IllegalArgumentException("별점은 1~5 사이여야 합니다: 6"));

		this.mockMvc.perform(put("/api/v1/trips/" + TRIP_ID + "/rating").principal(principal())
				.contentType(MediaType.APPLICATION_JSON).content("{\"score\":6}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("TRIP_RATING_INVALID"));
	}

	@Test
	@DisplayName("🔴 구성원이 아니면 404 — 여행이 있는지 알려 주지 않는다")
	void notMemberIs404() throws Exception {
		given(this.service.find(TRIP_ID, USER_ID)).willThrow(new TripQueryService.TripNotFoundException(TRIP_ID));

		this.mockMvc.perform(get("/api/v1/trips/" + TRIP_ID + "/rating").principal(principal()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("TRIP_NOT_FOUND"));
	}

	@Test
	@DisplayName("DELETE 가 내 별점을 지운다")
	void deleteClears() throws Exception {
		given(this.service.clear(TRIP_ID, USER_ID)).willReturn(new TripRatingService.Rating(null, null, 0));

		this.mockMvc.perform(delete("/api/v1/trips/" + TRIP_ID + "/rating").principal(principal()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.count").value(0));
	}
}
