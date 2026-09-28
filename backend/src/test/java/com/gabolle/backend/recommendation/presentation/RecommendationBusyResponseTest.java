package com.gabolle.backend.recommendation.presentation;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.gabolle.backend.recommendation.application.BlockingConstraintAnalyzer;
import com.gabolle.backend.recommendation.application.JobProgressBroker;
import com.gabolle.backend.recommendation.application.RecommendationBusyException;
import com.gabolle.backend.recommendation.application.RecommendationJobRunner;

/**
 * 추천 실행기가 꽉 차면 사용자는 503 · 우리 봉투 · 「지금 요청이 많아요」를 받는다 (S15P21E201-1685).
 *
 * <p>고치기 전에는 스프링 기본 500 본문({@code "error":"Internal Server Error"})이었다 — 이 PC 로컬에서 무거운 추천 70명을
 * 한꺼번에 넣어 12명이 받았다. 추천 컨트롤러 전용 핸들러({@link RecommendationJobExceptionHandler})와 함께 올려, 그쪽이
 * 가로채지 않고 이 핸들러가 답하는지 본다.
 */
class RecommendationBusyResponseTest {

	@Test
	@DisplayName("🔴 실행기가 꽉 차면 503 · SERVER_BUSY · 「지금 요청이 많아요. 잠시 뒤 다시 시도해 주세요.」")
	void busyRunnerAnswers503InOurEnvelope() throws Exception {
		RecommendationJobRunner runner = mock(RecommendationJobRunner.class);
		when(runner.enqueue(anyString(), anyString(), any(), any(), any()))
				.thenThrow(new RecommendationBusyException(UUID.randomUUID(), null));
		MockMvc mvc = MockMvcBuilders
				.standaloneSetup(new RecommendationJobController(runner, mock(JobProgressBroker.class),
						mock(BlockingConstraintAnalyzer.class)))
				.setControllerAdvice(new RecommendationJobExceptionHandler(), new RecommendationBusyExceptionHandler())
				.build();

		mvc.perform(post("/api/v1/trips/" + UUID.randomUUID() + "/recommendation-jobs")
				.principal(new UsernamePasswordAuthenticationToken(UUID.randomUUID().toString(), null, List.of())))
				.andExpect(status().isServiceUnavailable())
				.andExpect(jsonPath("$.data").doesNotExist())
				.andExpect(jsonPath("$.error.code").value("SERVER_BUSY"))
				.andExpect(jsonPath("$.error.message").value("지금 요청이 많아요. 잠시 뒤 다시 시도해 주세요."))
				.andExpect(jsonPath("$.meta.requestId").exists());
	}
}
