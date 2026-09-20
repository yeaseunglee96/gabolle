package com.gabolle.backend.auth.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.gabolle.backend.auth.service.AnonymousSessionService;
import com.gabolle.backend.auth.service.AnonymousSessionService.IssuedAnonymousSession;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * {@code POST /api/v1/auth/anonymous} 의 HTTP 계약.
 *
 * <p>발급 로직 자체는 {@code AnonymousSessionServiceTest} 가 재고, 여기는 경로와 응답 바디에
 * 원본 출입증이 실제로 실려 나가는지만 본다.
 */
class AnonymousAuthControllerTest {

	@Test
	void issuingReturnsTheRawTokenInTheResponseBody() throws Exception {
		AnonymousSessionService service = mock(AnonymousSessionService.class);
		UUID sessionId = UUID.randomUUID();
		Instant issuedAt = Instant.parse("2026-01-01T00:00:00Z");
		when(service.issue()).thenReturn(new IssuedAnonymousSession(sessionId, "raw-anonymous-token", issuedAt));

		MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AnonymousAuthController(service)).build();

		String body = mockMvc.perform(post("/api/v1/auth/anonymous"))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString();

		assertThat(body).contains("raw-anonymous-token").contains(sessionId.toString());
	}

	@Test
	void issuingTwiceCallsTheServiceEachTimeSoDifferentTokensComeBack() throws Exception {
		AnonymousSessionService service = mock(AnonymousSessionService.class);
		when(service.issue())
				.thenReturn(new IssuedAnonymousSession(UUID.randomUUID(), "token-one", Instant.now()))
				.thenReturn(new IssuedAnonymousSession(UUID.randomUUID(), "token-two", Instant.now()));
		MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new AnonymousAuthController(service)).build();

		String first = mockMvc.perform(post("/api/v1/auth/anonymous")).andReturn().getResponse().getContentAsString();
		String second = mockMvc.perform(post("/api/v1/auth/anonymous")).andReturn().getResponse().getContentAsString();

		assertThat(first).contains("token-one");
		assertThat(second).contains("token-two");
		assertThat(first).isNotEqualTo(second);
	}
}
