package com.gabolle.backend.trip.presentation;

import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.gabolle.backend.trip.application.SpendProfileService;
import com.gabolle.backend.trip.domain.PreferenceSnapshot;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * S15P21E201-709 — {@code /api/v1/me/preferences/spend} 표현 계층.
 *
 * <p>🔴 대상이 요청 헤더가 아니라 인증 주체로만 정해지는지 확인한다 —
 * {@code RecommendationResultAuthorizationTest} 와 같은 방식.
 */
class SpendProfileControllerTest {

	private static final UUID USER_ID = UUID.randomUUID();

	private SpendProfileService service;
	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		this.service = mock(SpendProfileService.class);
		this.mockMvc = MockMvcBuilders.standaloneSetup(new SpendProfileController(this.service))
				.setControllerAdvice(new SpendProfileExceptionHandler())
				.build();
	}

	private static Authentication principal() {
		return new UsernamePasswordAuthenticationToken(USER_ID.toString(), null, java.util.List.of());
	}

	@Test
	@DisplayName("🔴 저장한 적 없으면 GET 은 404 가 아니라 status:UNKNOWN 200 이다")
	void getReturnsUnknownWhenNeverSaved() throws Exception {
		given(this.service.find(USER_ID)).willReturn(Optional.empty());

		this.mockMvc.perform(get("/api/v1/me/preferences/spend").principal(principal()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("UNKNOWN"))
				.andExpect(jsonPath("$.data.value").doesNotExist());
	}

	@Test
	@DisplayName("저장된 값이 있으면 GET 이 그대로 돌려준다")
	void getReturnsSavedValue() throws Exception {
		given(this.service.find(USER_ID)).willReturn(Optional.of(
				new PreferenceSnapshot.PreferenceAnswer("SPEND_PROFILE", "\"MODERATE\"",
						PreferenceSnapshot.AnswerStatus.SELECTED)));

		this.mockMvc.perform(get("/api/v1/me/preferences/spend").principal(principal()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("SELECTED"))
				.andExpect(jsonPath("$.data.value").value("\"MODERATE\""));
	}

	@Test
	@DisplayName("PUT 이 인증 주체의 UUID 로 서비스를 부른다 — 본문의 다른 사용자 지정 경로가 없다")
	void putUsesAuthenticatedSubject() throws Exception {
		given(this.service.put(eq(USER_ID), any(), any(), any())).willReturn(
				new PreferenceSnapshot.PreferenceAnswer("SPEND_PROFILE", "\"LUXURY\"",
						PreferenceSnapshot.AnswerStatus.SELECTED));

		this.mockMvc.perform(put("/api/v1/me/preferences/spend")
						.principal(principal())
						.contentType("application/json")
						.content("{\"value\":\"\\\"LUXURY\\\"\",\"answerStatus\":\"SELECTED\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.status").value("SELECTED"))
				.andExpect(jsonPath("$.data.value").value("\"LUXURY\""));
	}

	@Test
	@DisplayName("🔴 잘못된 answerStatus(IllegalArgumentException)는 500 이 아니라 400 이다")
	void invalidAnswerStatusIsBadRequestNotServerError() throws Exception {
		given(this.service.put(any(), any(), any(), any()))
				.willThrow(new IllegalArgumentException("answerStatus 를 모른다"));

		this.mockMvc.perform(put("/api/v1/me/preferences/spend")
						.principal(principal())
						.contentType("application/json")
						.content("{\"value\":\"\\\"X\\\"\",\"answerStatus\":\"MAYBE\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("SPEND_PROFILE_REJECTED"));
	}
}
