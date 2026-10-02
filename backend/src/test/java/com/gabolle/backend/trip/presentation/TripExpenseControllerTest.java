package com.gabolle.backend.trip.presentation;

import java.time.OffsetDateTime;
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

import com.gabolle.backend.trip.application.TripExpenseService;
import com.gabolle.backend.trip.application.TripQueryService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** {@code /api/v1/trips/{tripId}/expenses}·{@code /budget} 표현 계층 (S15P21E201-1935). */
class TripExpenseControllerTest {

	private static final UUID USER_ID = UUID.randomUUID();

	private static final String TRIP_ID = UUID.randomUUID().toString();

	private TripExpenseService service;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		this.service = mock(TripExpenseService.class);
		this.mockMvc = MockMvcBuilders.standaloneSetup(new TripExpenseController(this.service))
				.setControllerAdvice(new TripExpenseExceptionHandler())
				.build();
	}

	private static Authentication principal() {
		return new UsernamePasswordAuthenticationToken(USER_ID.toString(), null, List.of());
	}

	private static TripExpenseService.Ledger ledger(Integer budget) {
		TripExpenseService.Expense line = new TripExpenseService.Expense(UUID.randomUUID(), USER_ID, USER_ID, 18000,
				"FOOD", "광안리 밀면집", null, true, OffsetDateTime.parse("2026-10-03T12:40:00+09:00"));
		return new TripExpenseService.Ledger(budget, 18000, List.of(line));
	}

	@Test
	@DisplayName("GET 이 예산·합계·줄을 돌려준다")
	void list() throws Exception {
		given(this.service.find(TRIP_ID, USER_ID)).willReturn(ledger(300000));

		this.mockMvc.perform(get("/api/v1/trips/" + TRIP_ID + "/expenses").principal(principal()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.budgetKrw").value(300000))
				.andExpect(jsonPath("$.data.totalKrw").value(18000))
				.andExpect(jsonPath("$.data.items[0].category").value("FOOD"))
				.andExpect(jsonPath("$.data.items[0].placeName").value("광안리 밀면집"));
	}

	@Test
	@DisplayName("POST 가 본문을 그대로 넘긴다")
	void add() throws Exception {
		given(this.service.add(eq(TRIP_ID), eq(USER_ID), any())).willReturn(ledger(null));

		this.mockMvc.perform(post("/api/v1/trips/" + TRIP_ID + "/expenses").principal(principal())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"amountKrw\":18000,\"category\":\"FOOD\",\"placeName\":\"광안리 밀면집\",\"splitEven\":true}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.totalKrw").value(18000));
	}

	@Test
	@DisplayName("🔴 금액이 맞지 않으면 400")
	void invalidIs400() throws Exception {
		given(this.service.add(eq(TRIP_ID), eq(USER_ID), any())).willThrow(new IllegalArgumentException("금액은 1원~"));

		this.mockMvc.perform(post("/api/v1/trips/" + TRIP_ID + "/expenses").principal(principal())
				.contentType(MediaType.APPLICATION_JSON).content("{\"amountKrw\":0,\"category\":\"FOOD\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("TRIP_EXPENSE_INVALID"));
	}

	@Test
	@DisplayName("🔴 남이 적은 줄 지우기는 403")
	void forbiddenIs403() throws Exception {
		UUID line = UUID.randomUUID();
		given(this.service.remove(TRIP_ID, USER_ID, line))
				.willThrow(new TripExpenseService.ExpenseForbiddenException("적은 사람이나 여행을 만든 사람만 지울 수 있어요."));

		this.mockMvc.perform(delete("/api/v1/trips/" + TRIP_ID + "/expenses/" + line).principal(principal()))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.error.code").value("TRIP_EXPENSE_FORBIDDEN"));
	}

	@Test
	@DisplayName("🔴 구성원이 아니면 404 — 여행이 있는지 알려 주지 않는다")
	void notMemberIs404() throws Exception {
		given(this.service.find(TRIP_ID, USER_ID)).willThrow(new TripQueryService.TripNotFoundException(TRIP_ID));

		this.mockMvc.perform(get("/api/v1/trips/" + TRIP_ID + "/expenses").principal(principal()))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.error.code").value("TRIP_NOT_FOUND"));
	}

	@Test
	@DisplayName("PUT /budget 이 예산을 정한다")
	void budget() throws Exception {
		given(this.service.setBudget(TRIP_ID, USER_ID, 300000)).willReturn(ledger(300000));

		this.mockMvc.perform(put("/api/v1/trips/" + TRIP_ID + "/budget").principal(principal())
				.contentType(MediaType.APPLICATION_JSON).content("{\"amountKrw\":300000}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.budgetKrw").value(300000));
	}
}
