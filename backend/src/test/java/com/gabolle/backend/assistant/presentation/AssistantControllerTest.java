package com.gabolle.backend.assistant.presentation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import com.gabolle.backend.assistant.application.AssistantChatService;
import com.gabolle.backend.assistant.application.AssistantRateLimiter;
import com.gabolle.backend.assistant.application.AssistantTripContextBuilder;
import com.gabolle.backend.assistant.application.AssistantVendorException;
import com.gabolle.backend.assistant.application.AssistantVendorPort;
import com.gabolle.backend.assistant.config.AssistantProperties;
import com.gabolle.backend.assistant.domain.AssistantActionKind;
import com.gabolle.backend.assistant.domain.AssistantChatRequest;
import com.gabolle.backend.assistant.domain.AssistantReply;
import com.gabolle.backend.user.application.ConsentGuard;

/**
 * {@code POST /api/v1/assistant/messages} 의 HTTP 경계 — S15P21E201-802.
 *
 * <p>{@code TranslateControllerTest} 와 같은 방식으로 컨트롤러+예외 처리기만 세워 HTTP 계약을
 * 잰다. 실제 Gemini 호출·구조화 출력 파싱은 어댑터 쪽 몫이라 여기서는 벤더를 스텁으로 대신한다.
 *
 * <p>MVP 범위는 은행 앱 챗봇처럼 관련 화면으로 안내하는 것까지다 — 여기서는 그중 navigate 를
 * 검증한다.
 */
class AssistantControllerTest {

	private MockMvc mockMvc;
	private StubVendor vendor;
	private AssistantProperties properties;
	private AssistantTripContextBuilder tripContextBuilder;

	@BeforeEach
	void setUp() {
		this.vendor = new StubVendor();
		this.properties = new AssistantProperties();
		Clock clock = Clock.fixed(Instant.parse("2026-09-11T00:00:00Z"), ZoneOffset.UTC);
		AssistantRateLimiter rateLimiter = new AssistantRateLimiter(this.properties, clock);
		ConsentGuard consentGuard = mock(ConsentGuard.class);
		this.tripContextBuilder = mock(AssistantTripContextBuilder.class);
		AssistantChatService service = new AssistantChatService(this.vendor, rateLimiter, this.properties,
				consentGuard, this.tripContextBuilder);

		this.mockMvc = MockMvcBuilders.standaloneSetup(new AssistantController(service))
				.setControllerAdvice(new AssistantExceptionHandler())
				.build();
	}

	private static Authentication asUser() {
		return new TestingAuthenticationToken(UUID.randomUUID().toString(), null);
	}

	@Test
	@DisplayName("여행 만들기 요청은 200 과 kind=navigate, href=/plan 을 담아 온다")
	void tripCreationRequestNavigatesToPlanBasic() throws Exception {
		this.mockMvc.perform(post("/api/v1/assistant/messages")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"message\":\"여행 추천 경로 짜고 싶어\"}")
						.principal(asUser()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.kind").value("navigate"))
				.andExpect(jsonPath("$.data.href").value("/plan"))
				.andExpect(jsonPath("$.data.label").value("여행 만들기"));
	}

	@Test
	@DisplayName("history 를 함께 보내도 그대로 받아 벤더에 전달한다")
	void historyIsForwardedToVendor() throws Exception {
		this.mockMvc.perform(post("/api/v1/assistant/messages")
						.contentType(MediaType.APPLICATION_JSON)
						.content("""
								{"message":"거기로 갈래", "history":[
									{"role":"user","text":"부산 여행 만들고 싶어"},
									{"role":"assistant","text":"새 여행 만들기로 안내할게요."}
								]}""")
						.principal(asUser()))
				.andExpect(status().isOk());

		assertThatHistoryHasSize(2);
	}

	private void assertThatHistoryHasSize(int size) {
		assertThat(this.vendor.lastRequest.history()).hasSize(size);
	}

	@Test
	@DisplayName("🔴 업체 호출이 실패하면 502 이고 응답에 실패가 분명히 담긴다 — 200 으로 숨기지 않는다")
	void vendorFailureIsNotHiddenAs200() throws Exception {
		this.vendor.shouldFail = true;

		this.mockMvc.perform(post("/api/v1/assistant/messages")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"message\":\"여행 추천 경로 짜고 싶어\"}")
						.principal(asUser()))
				.andExpect(status().isBadGateway())
				.andExpect(jsonPath("$.error.code").value("ASSISTANT_VENDOR_UNAVAILABLE"))
				.andExpect(jsonPath("$.data").doesNotExist());
	}

	@Test
	@DisplayName("빈 메시지는 400 이다")
	void blankMessageIsRejected() throws Exception {
		this.mockMvc.perform(post("/api/v1/assistant/messages")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"message\":\"\"}")
						.principal(asUser()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.error.code").value("ASSISTANT_INVALID_REQUEST"));
	}

	@Test
	@DisplayName("🔴 1분 한도를 넘기면 429 다")
	void rateLimitExceededReturns429() throws Exception {
		this.properties.setMaxRequestsPerMinute(1);
		Authentication user = asUser();

		this.mockMvc.perform(post("/api/v1/assistant/messages")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"message\":\"하나\"}")
						.principal(user))
				.andExpect(status().isOk());

		this.mockMvc.perform(post("/api/v1/assistant/messages")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"message\":\"둘\"}")
						.principal(user))
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.error.code").value("ASSISTANT_RATE_LIMITED"));
	}

	@Test
	@DisplayName("itineraryId·dayIndex 를 보내면 동의 확인 후 일정 요약을 벤더에 실어 보낸다")
	void itineraryContextIsForwardedToVendor() throws Exception {
		given(this.tripContextBuilder.build(eq("itin-1"), eq(0), any())).willReturn("첫날 일정 요약");
		Authentication user = asUser();

		this.mockMvc.perform(post("/api/v1/assistant/messages")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"message\":\"오늘 뭐 챙겨야 해?\", \"itineraryId\":\"itin-1\", \"dayIndex\":0}")
						.principal(user))
				.andExpect(status().isOk());

		assertThat(this.vendor.lastRequest.tripContext()).isEqualTo("첫날 일정 요약");
	}

	private static final class StubVendor implements AssistantVendorPort {

		boolean shouldFail = false;
		AssistantChatRequest lastRequest;

		@Override
		public AssistantReply reply(AssistantChatRequest request) {
			this.lastRequest = request;
			if (this.shouldFail) {
				throw new AssistantVendorException("ASSISTANT_VENDOR_UNAVAILABLE", "AI 여행 도우미 호출에 실패했습니다.",
						HttpStatus.BAD_GATEWAY);
			}
			return new AssistantReply(AssistantActionKind.NAVIGATE, "새 여행 만들기로 안내할게요.", null, null, "여행 만들기",
					"/plan");
		}

		@Override
		public String providerName() {
			return "STUB_VENDOR";
		}
	}
}
