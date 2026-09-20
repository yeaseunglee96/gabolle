package com.gabolle.backend.event;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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

import com.gabolle.backend.event.application.EventIngestService;
import com.gabolle.backend.event.domain.EventType;
import com.gabolle.backend.event.presentation.EventIngestController;
import com.gabolle.backend.event.presentation.EventIngestExceptionHandler;

/**
 * 행동 이벤트의 주체를 인증에서만 읽는지 본다. 요청 본문의 {@code userId} 를 그대로
 * 믿으면 로그인한 사람 누구나 남의 이름으로 이벤트를 넣을 수 있고, 행동 이벤트는 개인화
 * 입력이라 남의 추천 결과가 오염된다.
 *
 * <p>HTTP 층을 지난다. 서비스를 직접 부르면 컨트롤러가 신원을 어디서 읽는지 검증되지 않는다.
 */
class EventSubjectAuthorizationTest {

	private EventIngestService service;

	private MockMvc mockMvc;

	private final UUID me = UUID.randomUUID();

	private final UUID someoneElse = UUID.randomUUID();

	@BeforeEach
	void setUp() {
		this.service = mock(EventIngestService.class);
		when(this.service.ingestFromClient(any(), any(EventType.class), anyInt(), any(), any(), any(), any(),
				any())).thenReturn(EventIngestService.Outcome.STORED);
		this.mockMvc = MockMvcBuilders
				.standaloneSetup(new EventIngestController(this.service))
				.setControllerAdvice(new EventIngestExceptionHandler())
				.build();
	}

	@Test
	@DisplayName("🔴 남의 userId 를 실어 보내면 403 이고 저장되지 않는다")
	void foreignSubjectIsRejected() throws Exception {
		this.mockMvc.perform(post("/api/v1/events")
				.contentType(MediaType.APPLICATION_JSON)
				.principal(asMe())
				.content(body(this.someoneElse)))
				.andExpect(status().isForbidden())
				// 500 이 아니어야 한다. 처리기가 없으면 이 예외가 500 으로 나간다.
				.andExpect(jsonPath("$.error.code").value("EVENT_SUBJECT_MISMATCH"));

		verify(this.service, never()).ingestFromClient(any(), any(EventType.class), anyInt(), any(), any(),
				any(), any(), any());
	}

	@Test
	@DisplayName("본문에 userId 가 없으면 인증 주체로 저장된다")
	void missingSubjectFallsBackToTheAuthenticatedUser() throws Exception {
		this.mockMvc.perform(post("/api/v1/events")
				.contentType(MediaType.APPLICATION_JSON)
				.principal(asMe())
				.content(body(null)))
				.andExpect(status().isAccepted());

		verify(this.service).ingestFromClient(any(), any(EventType.class), anyInt(), eq(this.me), any(),
				any(), any(), any());
	}

	@Test
	@DisplayName("본문의 userId 가 나 자신이면 그대로 통과한다 — 지금 앱이 보내는 형태다")
	void ownSubjectPasses() throws Exception {
		this.mockMvc.perform(post("/api/v1/events")
				.contentType(MediaType.APPLICATION_JSON)
				.principal(asMe())
				.content(body(this.me)))
				.andExpect(status().isAccepted());

		verify(this.service).ingestFromClient(any(), any(EventType.class), anyInt(), eq(this.me), any(),
				any(), any(), any());
	}

	@Test
	@DisplayName("주체가 인증에서 오므로 두 요청의 주체가 서로 다르다 — 본문으로 바꿀 수 없다")
	void subjectCannotBeSwappedByBody() throws Exception {
		UUID other = UUID.randomUUID();
		Authentication asOther = new UsernamePasswordAuthenticationToken(other.toString(), null, List.of());

		this.mockMvc.perform(post("/api/v1/events")
				.contentType(MediaType.APPLICATION_JSON)
				.principal(asMe())
				.content(body(null)))
				.andExpect(status().isAccepted());
		this.mockMvc.perform(post("/api/v1/events")
				.contentType(MediaType.APPLICATION_JSON)
				.principal(asOther)
				.content(body(null)))
				.andExpect(status().isAccepted());

		verify(this.service).ingestFromClient(any(), any(EventType.class), anyInt(), eq(this.me), any(),
				any(), any(), any());
		verify(this.service).ingestFromClient(any(), any(EventType.class), anyInt(), eq(other), any(),
				any(), any(), any());
	}

	private Authentication asMe() {
		return new UsernamePasswordAuthenticationToken(this.me.toString(), null, List.of());
	}

	/** {@code userId} 가 {@code null} 이면 그 키를 아예 안 싣는다 — 앱이 안 보내는 경우다. */
	private static String body(UUID userId) {
		String subject = (userId == null) ? "" : "\"userId\":\"" + userId + "\",";
		return """
				{"eventId":"%s",%s"eventType":"place_view","requestId":"%s",
				 "occurredAt":"2026-09-07T00:00:00Z","payload":{}}
				""".formatted(UUID.randomUUID(), subject, UUID.randomUUID());
	}

	@Test
	@DisplayName("본문 형태가 이 테스트의 가정과 맞는다 — userId 를 뺀 본문이 실제로 그 키가 없다")
	void bodyHelperOmitsTheKey() {
		assertThat(body(null)).doesNotContain("userId");
		assertThat(body(this.me)).contains("userId");
	}
}
