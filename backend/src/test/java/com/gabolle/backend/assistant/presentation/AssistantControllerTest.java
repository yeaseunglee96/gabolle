package com.gabolle.backend.assistant.presentation;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
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
import com.gabolle.backend.assistant.application.AssistantVendorException;
import com.gabolle.backend.assistant.application.AssistantVendorPort;
import com.gabolle.backend.assistant.domain.AssistantActionKind;
import com.gabolle.backend.assistant.domain.AssistantReply;
import com.gabolle.backend.assistant.domain.PlanPatch;

/**
 * {@code POST /api/v1/assistant/messages} 의 HTTP 경계 — S15P21E201-802.
 *
 * <p>{@code TranslateControllerTest} 와 같은 방식으로 컨트롤러+예외 처리기만 세워 HTTP 계약을
 * 잰다. 실제 Claude 호출·구조화 출력 파싱은 어댑터 쪽 몫이라 여기서는 벤더를 스텁으로 대신한다.
 */
class AssistantControllerTest {

	private MockMvc mockMvc;
	private StubVendor vendor;

	@BeforeEach
	void setUp() {
		this.vendor = new StubVendor();
		AssistantChatService service = new AssistantChatService(this.vendor);

		this.mockMvc = MockMvcBuilders.standaloneSetup(new AssistantController(service))
				.setControllerAdvice(new AssistantExceptionHandler())
				.build();
	}

	private static Authentication asUser() {
		return new TestingAuthenticationToken(UUID.randomUUID().toString(), null);
	}

	@Test
	@DisplayName("일정 요청은 200 과 kind=plan, patch 를 담아 온다")
	void planRequestSucceeds() throws Exception {
		this.mockMvc.perform(post("/api/v1/assistant/messages")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"message\":\"해운대와 광안리 2명 맛집 일정 짜줘\"}")
						.principal(asUser()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.kind").value("plan"))
				.andExpect(jsonPath("$.data.patch.travelAreas[0]").value("해운대"))
				.andExpect(jsonPath("$.data.patch.travelers").value(2));
	}

	@Test
	@DisplayName("🔴 patch 에는 알레르기·접근성 같은 안전 필드를 실을 자리가 아예 없다")
	void planResponseHasNoSafetyFields() throws Exception {
		this.mockMvc.perform(post("/api/v1/assistant/messages")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"message\":\"해운대와 광안리 2명 맛집 일정 짜줘\"}")
						.principal(asUser()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.data.patch.allergies").doesNotExist())
				.andExpect(jsonPath("$.data.patch.accessibilityNeeds").doesNotExist())
				.andExpect(jsonPath("$.data.patch.wheelchair").doesNotExist());
	}

	@Test
	@DisplayName("🔴 업체 호출이 실패하면 502 이고 응답에 실패가 분명히 담긴다 — 200 으로 숨기지 않는다")
	void vendorFailureIsNotHiddenAs200() throws Exception {
		this.vendor.shouldFail = true;

		this.mockMvc.perform(post("/api/v1/assistant/messages")
						.contentType(MediaType.APPLICATION_JSON)
						.content("{\"message\":\"해운대와 광안리 2명 맛집 일정 짜줘\"}")
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

	private static final class StubVendor implements AssistantVendorPort {

		boolean shouldFail = false;

		@Override
		public AssistantReply reply(String message) {
			if (this.shouldFail) {
				throw new AssistantVendorException("ASSISTANT_VENDOR_UNAVAILABLE", "AI 여행 도우미 호출에 실패했습니다.",
						HttpStatus.BAD_GATEWAY);
			}
			PlanPatch patch = new PlanPatch(null, null, 2, null, null, List.of("해운대", "광안리"),
					List.of("맛집"), null, null, null);
			return new AssistantReply(AssistantActionKind.PLAN, "해운대·광안리 맛집 일정으로 반영했어요.",
					List.of("지역: 해운대, 광안리", "인원: 2명"), patch, null, null, null, null);
		}

		@Override
		public String providerName() {
			return "STUB_VENDOR";
		}
	}
}
