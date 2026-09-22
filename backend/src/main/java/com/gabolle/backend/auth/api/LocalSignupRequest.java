package com.gabolle.backend.auth.api;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.Map;

/**
 * {@code POST /api/v1/auth/signup} — 이메일·비밀번호 회원가입.
 *
 * <p>불리언은 원시형이 아니라 {@code Boolean} 이어야 한다. JSON 에서 그 키를 빼면 Jackson 이
 * record 생성자에 {@code null} 을 넘기다 실패해 검증이 시작도 못 하고, 응답에 어느 칸이
 * 문제인지 남지 않는다.
 */
public record LocalSignupRequest(
		@NotBlank @Email @Size(max = 254) String email,
		@NotBlank @Size(min = 8, max = 100) String password,
		@NotBlank @Size(max = 50) String displayName,
		@NotBlank @Pattern(regexp = "KO|EN") String language,
		/**
		 * {@code @AssertTrue} 는 {@code null} 을 통과시키므로 {@code @NotNull} 이 함께 있어야 한다 —
		 * 하나만 두면 나이 확인 없이 가입이 된다.
		 */
		@NotNull(message = "14세 이상 확인이 필요합니다.")
		@AssertTrue(message = "14세 이상 확인이 필요합니다.") Boolean ageGateAccepted,
		@Size(max = 255) String deviceId,
		Map<String, Boolean> consents,
		Boolean behaviorPersonalizationEnabled) {

	/** 비우면 개인화를 끄는 것으로 본다. */
	public boolean behaviorPersonalizationEnabledOrFalse() {
		return Boolean.TRUE.equals(behaviorPersonalizationEnabled);
	}

	/** 검증을 지나온 요청에서는 언제나 참이다. */
	public boolean ageGateAcceptedOrFalse() {
		return Boolean.TRUE.equals(ageGateAccepted);
	}
}
