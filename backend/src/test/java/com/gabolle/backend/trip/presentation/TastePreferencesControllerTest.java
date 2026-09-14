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

import com.gabolle.backend.trip.application.PreferenceDefaultsService;
import com.gabolle.backend.trip.domain.PreferenceSnapshot;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * S15P21E201-639 — {@code /api/v1/me/preferences/taste} 표현 계층.
 *
 * <p>🔴 대상이 요청 본문이나 헤더가 아니라 <b>인증 주체로만</b> 정해지는지 확인한다.
 * {@code SpendProfileControllerTest} 와 같은 방식이고, 그래서
 * {@code RouteAuthorizationRegistryTest} 에 {@code OWNED} 로 등록되어 있다.
 */
class TastePreferencesControllerTest {

	private static final UUID USER_ID = UUID.randomUUID();

	private PreferenceDefaultsService service;

	private MockMvc mockMvc;

	@BeforeEach
	void setUp() {
		this.service = mock(PreferenceDefaultsService.class);
		this.mockMvc = MockMvcBuilders.standaloneSetup(new TastePreferencesController(this.service)).build();
	}

	private static Authentication principal() {
		return new UsernamePasswordAuthenticationToken(USER_ID.toString(), null, List.of());
	}

	@Test
	@DisplayName("🔴 한 번도 저장한 적 없으면 404 가 아니라 빈 목록 200 이다 — 첫 실행은 오류가 아니다")
	void 없으면_빈_목록_200() throws Exception {
		given(this.service.findTaste(USER_ID.toString())).willReturn(List.of());

		this.mockMvc.perform(get("/api/v1/me/preferences/taste").principal(principal()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.answers").isEmpty());
	}

	@Test
	@DisplayName("저장된 취향을 그대로 돌려준다")
	void 저장된_것을_돌려준다() throws Exception {
		given(this.service.findTaste(USER_ID.toString())).willReturn(List.of(
				new PreferenceSnapshot.PreferenceAnswer("LOCALITY", "0.6",
						PreferenceSnapshot.AnswerStatus.SELECTED)));

		this.mockMvc.perform(get("/api/v1/me/preferences/taste").principal(principal()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.answers[0].dimension").value("LOCALITY"))
				.andExpect(jsonPath("$.data.answers[0].status").value("SELECTED"))
				.andExpect(jsonPath("$.data.answers[0].value").value("0.6"));
	}

	@Test
	@DisplayName("🔴 PUT 은 인증 주체의 것만 바꾼다 — 본문에 남의 id 를 적을 자리가 없다")
	void 본인_것만_바꾼다() throws Exception {
		given(this.service.findTaste(USER_ID.toString())).willReturn(List.of());

		this.mockMvc.perform(put("/api/v1/me/preferences/taste")
						.principal(principal())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"answers":[{"dimension":"locality","value":"0.6","answerStatus":"SELECTED"}]}
								"""))
				.andExpect(status().isOk());

		// 서비스에 넘어간 사용자 id 가 인증 주체와 같아야 한다.
		then(this.service).should().putTaste(eq(USER_ID.toString()), any());
	}

	@Test
	@DisplayName("PUT 응답은 바꾼 뒤의 전부다 — 바뀐 것만 주면 화면이 나머지를 또 읽어야 한다")
	void 바꾼_뒤_전부를_돌려준다() throws Exception {
		given(this.service.findTaste(USER_ID.toString())).willReturn(List.of(
				new PreferenceSnapshot.PreferenceAnswer("LOCALITY", "0.6",
						PreferenceSnapshot.AnswerStatus.SELECTED),
				new PreferenceSnapshot.PreferenceAnswer("QUIETNESS", "0.8",
						PreferenceSnapshot.AnswerStatus.SELECTED)));

		this.mockMvc.perform(put("/api/v1/me/preferences/taste")
						.principal(principal())
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"answers":[{"dimension":"locality","value":"0.6","answerStatus":"SELECTED"}]}
								"""))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.answers.length()").value(2));
	}
}
