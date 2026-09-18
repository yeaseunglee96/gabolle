package com.gabolle.backend.assistant.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

import com.gabolle.backend.assistant.domain.AssistantActionKind;
import com.gabolle.backend.assistant.domain.AssistantReply;

/**
 * {@link AssistantChatService} 검증 — S15P21E201-802.
 *
 * <p>{@code TranslationServiceTest} 와 같은 자리다 — 캐시가 없어 규칙이 더 단순하다. 이
 * 클래스가 재는 것은 "메시지 검증"과 "벤더 실패를 숨기지 않고 그대로 올린다" 둘뿐이다.
 * 실제 Claude 호출·구조화 출력 파싱은 어댑터 몫이라 여기서는 벤더를 스텁으로 대신한다.
 */
class AssistantChatServiceTest {

	private CountingVendor vendor;
	private AssistantChatService service;

	@BeforeEach
	void setUp() {
		this.vendor = new CountingVendor();
		this.service = new AssistantChatService(this.vendor);
	}

	@Test
	@DisplayName("정상 메시지는 벤더를 한 번 불러 답을 그대로 돌려준다")
	void chatCallsVendorOnce() {
		AssistantReply reply = this.service.chat("여행 만들고 싶어");

		assertThat(reply.kind()).isEqualTo(AssistantActionKind.NAVIGATE);
		assertThat(this.vendor.callCount).isEqualTo(1);
	}

	@Test
	@DisplayName("빈 메시지는 벤더를 부르지 않고 바로 거부한다")
	void blankMessageIsRejectedBeforeCallingVendor() {
		assertThatThrownBy(() -> this.service.chat("   "))
				.isInstanceOf(IllegalArgumentException.class);

		assertThat(this.vendor.callCount).isZero();
	}

	@Test
	@DisplayName("너무 긴 메시지는 벤더를 부르지 않고 바로 거부한다")
	void tooLongMessageIsRejectedBeforeCallingVendor() {
		String tooLong = "가".repeat(1001);

		assertThatThrownBy(() -> this.service.chat(tooLong))
				.isInstanceOf(IllegalArgumentException.class);

		assertThat(this.vendor.callCount).isZero();
	}

	@Test
	@DisplayName("🔴 업체 호출이 실패하면 그대로 위로 올라간다 — 미리 정해 둔 답으로 숨기지 않는다")
	void vendorFailurePropagatesInsteadOfBeingHidden() {
		this.vendor.shouldFail = true;

		assertThatThrownBy(() -> this.service.chat("여행 만들고 싶어"))
				.isInstanceOf(AssistantVendorException.class);
	}

	private static final class CountingVendor implements AssistantVendorPort {

		int callCount = 0;
		boolean shouldFail = false;

		@Override
		public AssistantReply reply(String message) {
			this.callCount++;
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
