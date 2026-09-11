package com.gabolle.backend.assistant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

/**
 * {@link AssistantChatService} 검증 — S15P21E201-802.
 *
 * <p>{@code TranslationServiceTest} 와 같은 자리다 — 캐시가 없어 규칙이 더 단순하다. 이
 * 클래스가 재는 것은 "메시지 검증"·"요청 빈도 제한"·"히스토리 다듬기"·"벤더 실패를 숨기지
 * 않고 그대로 올린다" 다. 실제 Gemini 호출·구조화 출력 파싱은 어댑터 몫이라 여기서는 벤더를
 * 스텁으로 대신한다.
 */
class AssistantChatServiceTest {

	private static final UUID USER_ID = UUID.randomUUID();

	private CountingVendor vendor;
	private AssistantChatService service;
	private AssistantProperties properties;

	@BeforeEach
	void setUp() {
		this.vendor = new CountingVendor();
		this.properties = new AssistantProperties();
		Clock clock = Clock.fixed(Instant.parse("2026-09-11T00:00:00Z"), ZoneOffset.UTC);
		AssistantRateLimiter rateLimiter = new AssistantRateLimiter(this.properties, clock);
		this.service = new AssistantChatService(this.vendor, rateLimiter, this.properties);
	}

	@Test
	@DisplayName("정상 메시지는 벤더를 한 번 불러 답을 그대로 돌려준다")
	void chatCallsVendorOnce() {
		AssistantReply reply = this.service.chat(USER_ID, "여행 만들고 싶어", "ko", List.of());

		assertThat(reply.kind()).isEqualTo(AssistantActionKind.NAVIGATE);
		assertThat(this.vendor.callCount).isEqualTo(1);
	}

	@Test
	@DisplayName("빈 메시지는 벤더를 부르지 않고 바로 거부한다")
	void blankMessageIsRejectedBeforeCallingVendor() {
		assertThatThrownBy(() -> this.service.chat(USER_ID, "   ", "ko", List.of()))
				.isInstanceOf(IllegalArgumentException.class);

		assertThat(this.vendor.callCount).isZero();
	}

	@Test
	@DisplayName("너무 긴 메시지는 벤더를 부르지 않고 바로 거부한다")
	void tooLongMessageIsRejectedBeforeCallingVendor() {
		String tooLong = "가".repeat(1001);

		assertThatThrownBy(() -> this.service.chat(USER_ID, tooLong, "ko", List.of()))
				.isInstanceOf(IllegalArgumentException.class);

		assertThat(this.vendor.callCount).isZero();
	}

	@Test
	@DisplayName("🔴 업체 호출이 실패하면 그대로 위로 올라간다 — 미리 정해 둔 답으로 숨기지 않는다")
	void vendorFailurePropagatesInsteadOfBeingHidden() {
		this.vendor.shouldFail = true;

		assertThatThrownBy(() -> this.service.chat(USER_ID, "여행 만들고 싶어", "ko", List.of()))
				.isInstanceOf(AssistantVendorException.class);
	}

	@Test
	@DisplayName("🔴 1분 한도를 넘기면 벤더를 부르지 않고 거부한다")
	void rateLimitIsEnforcedPerUser() {
		this.properties.setMaxRequestsPerMinute(2);

		this.service.chat(USER_ID, "하나", "ko", List.of());
		this.service.chat(USER_ID, "둘", "ko", List.of());

		assertThatThrownBy(() -> this.service.chat(USER_ID, "셋", "ko", List.of()))
				.isInstanceOf(AssistantRateLimitExceededException.class);

		assertThat(this.vendor.callCount).isEqualTo(2);
	}

	@Test
	@DisplayName("다른 사용자는 서로의 한도에 영향을 주지 않는다")
	void rateLimitIsIsolatedPerUser() {
		this.properties.setMaxRequestsPerMinute(1);

		this.service.chat(USER_ID, "하나", "ko", List.of());
		this.service.chat(UUID.randomUUID(), "다른 사람", "ko", List.of());

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

		this.service.chat(USER_ID, "다섯", "ko", longHistory);

		assertThat(this.vendor.lastRequest.history()).extracting(AssistantTurn::text)
				.containsExactly("3", "4");
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
