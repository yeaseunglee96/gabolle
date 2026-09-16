package com.gabolle.backend.assistant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.gabolle.backend.assistant.config.AssistantProperties;
import com.gabolle.backend.assistant.domain.AssistantActionKind;
import com.gabolle.backend.assistant.domain.AssistantChatRequest;
import com.gabolle.backend.assistant.domain.AssistantReply;
import com.gabolle.backend.assistant.domain.AssistantTurn;
import com.gabolle.backend.auth.service.AuthException;
import com.gabolle.backend.user.application.ConsentGuard;

/**
 * {@link AssistantChatService} 검증 — S15P21E201-802.
 *
 * <p>{@code TranslationServiceTest} 와 같은 자리다 — 캐시가 없어 규칙이 더 단순하다. 이
 * 클래스가 재는 것은 "메시지 검증"·"요청 빈도 제한"·"히스토리 다듬기"·"벤더 실패를 숨기지
 * 않고 그대로 올린다" 다. 실제 Gemini 호출·구조화 출력 파싱은 어댑터 몫이라 여기서는 벤더를
 * 스텁으로 대신한다.
 *
 * <p>S15P21E201-987 — 일정 참조 관련 동작(동의 확인·컨텍스트 조회)은
 * {@link ConsentGuard}·{@link AssistantTripContextBuilder} 를 mock 으로 세워 확인한다.
 */
class AssistantChatServiceTest {

	private static final UUID USER_ID = UUID.randomUUID();

	private CountingVendor vendor;
	private AssistantChatService service;
	private AssistantProperties properties;
	private ConsentGuard consentGuard;
	private AssistantTripContextBuilder tripContextBuilder;

	@BeforeEach
	void setUp() {
		this.vendor = new CountingVendor();
		this.properties = new AssistantProperties();
		Clock clock = Clock.fixed(Instant.parse("2026-09-11T00:00:00Z"), ZoneOffset.UTC);
		AssistantRateLimiter rateLimiter = new AssistantRateLimiter(this.properties, clock);
		this.consentGuard = mock(ConsentGuard.class);
		this.tripContextBuilder = mock(AssistantTripContextBuilder.class);
		this.service = new AssistantChatService(this.vendor, rateLimiter, this.properties, this.consentGuard,
				this.tripContextBuilder);
	}

	@Test
	@DisplayName("정상 메시지는 벤더를 한 번 불러 답을 그대로 돌려준다")
	void chatCallsVendorOnce() {
		AssistantReply reply = this.service.chat(USER_ID, "여행 만들고 싶어", "ko", List.of(), null, null);

		assertThat(reply.kind()).isEqualTo(AssistantActionKind.NAVIGATE);
		assertThat(this.vendor.callCount).isEqualTo(1);
	}

	@Test
	@DisplayName("빈 메시지는 벤더를 부르지 않고 바로 거부한다")
	void blankMessageIsRejectedBeforeCallingVendor() {
		assertThatThrownBy(() -> this.service.chat(USER_ID, "   ", "ko", List.of(), null, null))
				.isInstanceOf(IllegalArgumentException.class);

		assertThat(this.vendor.callCount).isZero();
	}

	@Test
	@DisplayName("너무 긴 메시지는 벤더를 부르지 않고 바로 거부한다")
	void tooLongMessageIsRejectedBeforeCallingVendor() {
		String tooLong = "가".repeat(1001);

		assertThatThrownBy(() -> this.service.chat(USER_ID, tooLong, "ko", List.of(), null, null))
				.isInstanceOf(IllegalArgumentException.class);

		assertThat(this.vendor.callCount).isZero();
	}

	@Test
	@DisplayName("🔴 업체 호출이 실패하면 그대로 위로 올라간다 — 미리 정해 둔 답으로 숨기지 않는다")
	void vendorFailurePropagatesInsteadOfBeingHidden() {
		this.vendor.shouldFail = true;

		assertThatThrownBy(() -> this.service.chat(USER_ID, "여행 만들고 싶어", "ko", List.of(), null, null))
				.isInstanceOf(AssistantVendorException.class);
	}

	@Test
	@DisplayName("🔴 1분 한도를 넘기면 벤더를 부르지 않고 거부한다")
	void rateLimitIsEnforcedPerUser() {
		this.properties.setMaxRequestsPerMinute(2);

		this.service.chat(USER_ID, "하나", "ko", List.of(), null, null);
		this.service.chat(USER_ID, "둘", "ko", List.of(), null, null);

		assertThatThrownBy(() -> this.service.chat(USER_ID, "셋", "ko", List.of(), null, null))
				.isInstanceOf(AssistantRateLimitExceededException.class);

		assertThat(this.vendor.callCount).isEqualTo(2);
	}

	@Test
	@DisplayName("다른 사용자는 서로의 한도에 영향을 주지 않는다")
	void rateLimitIsIsolatedPerUser() {
		this.properties.setMaxRequestsPerMinute(1);

		this.service.chat(USER_ID, "하나", "ko", List.of(), null, null);
		this.service.chat(UUID.randomUUID(), "다른 사람", "ko", List.of(), null, null);

		assertThat(this.vendor.callCount).isEqualTo(2);
	}

	@Test
	@DisplayName("히스토리는 설정된 개수만큼만 벤더에 전달된다")
	void historyIsTrimmedToConfiguredTurnLimit() {
		this.properties.setMaxHistoryTurns(2);
		List<AssistantTurn> longHistory = List.of(
				new AssistantTurn("user", "1"),
				new AssistantTurn("assistant", "2"),
				new AssistantTurn("user", "3"),
				new AssistantTurn("assistant", "4"));

		this.service.chat(USER_ID, "다섯", "ko", longHistory, null, null);

		assertThat(this.vendor.lastRequest.history()).extracting(AssistantTurn::text)
				.containsExactly("3", "4");
	}

	// ── 일정 참조 (S15P21E201-987) ──────────────────────────────────────

	@Test
	@DisplayName("itineraryId 가 없으면 동의를 확인하지도, 일정을 조회하지도 않는다")
	void noItineraryIdSkipsConsentAndContext() {
		this.service.chat(USER_ID, "여행 만들고 싶어", "ko", List.of(), null, null);

		verifyNoInteractions(this.consentGuard);
		verifyNoInteractions(this.tripContextBuilder);
		assertThat(this.vendor.lastRequest.tripContext()).isNull();
	}

	@Test
	@DisplayName("itineraryId 가 있으면 동의부터 확인하고, 통과하면 일정을 조회해 벤더에 얹는다")
	void itineraryIdTriggersConsentCheckAndContextLookup() {
		given(this.tripContextBuilder.build("itin-1", 0, USER_ID)).willReturn("첫날 일정 요약");

		this.service.chat(USER_ID, "오늘 뭐 챙겨야 해?", "ko", List.of(), "itin-1", 0);

		verify(this.consentGuard).requireAiAssistantAccess(USER_ID);
		assertThat(this.vendor.lastRequest.tripContext()).isEqualTo("첫날 일정 요약");
	}

	@Test
	@DisplayName("🔴 동의가 없으면 벤더를 부르지 않고 그대로 막는다")
	void missingConsentBlocksBeforeCallingVendor() {
		doThrow(new AuthException("AI_ASSISTANT_ACCESS_CONSENT_REQUIRED", "동의 필요", HttpStatus.FORBIDDEN))
				.when(this.consentGuard).requireAiAssistantAccess(USER_ID);

		assertThatThrownBy(() -> this.service.chat(USER_ID, "오늘 뭐 챙겨야 해?", "ko", List.of(), "itin-1", 0))
				.isInstanceOf(AuthException.class);

		verifyNoInteractions(this.tripContextBuilder);
		assertThat(this.vendor.callCount).isZero();
	}

	private static final class CountingVendor implements AssistantVendorPort {

		int callCount = 0;
		boolean shouldFail = false;
		AssistantChatRequest lastRequest;

		@Override
		public AssistantReply reply(AssistantChatRequest request) {
			this.callCount++;
			this.lastRequest = request;
			if (this.shouldFail) {
				throw new AssistantVendorException("ASSISTANT_VENDOR_UNAVAILABLE", "실패", HttpStatus.BAD_GATEWAY);
			}
			return new AssistantReply(AssistantActionKind.NAVIGATE, "새 여행 만들기로 안내할게요.", null, null, "여행 만들기",
					"/plan/basic");
		}

		@Override
		public String providerName() {
			return "TEST_VENDOR";
		}
	}
}
