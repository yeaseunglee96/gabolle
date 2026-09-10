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
 * <h2>불리언을 감싼 타입으로 두는 이유 — S15P21E201-816</h2>
 * 전에는 {@code ageGateAccepted} 와 {@code behaviorPersonalizationEnabled} 가 원시
 * {@code boolean} 이었다. JSON 에서 그 키를 빼면 Jackson 이 record 생성자에 {@code null} 을
 * 넘기다 {@code Cannot map null into type boolean} 으로 실패하고, 그러면 <b>검증이 시작도 못
 * 한다.</b> 응답은 어느 칸이 문제인지 없는 {@code INVALID_REQUEST} 하나뿐이라 부르는 쪽이
 * 원인을 찾을 방법이 없었고, 아래 나이 확인 문구는 어떤 경우에도 화면에 닿지 못했다.
 *
 * <p>{@link OAuthSignupRequest} 가 2026-09-07 에 같은 증상으로 먼저 고쳐졌다. 소셜 가입만
 * 고쳐지고 이메일 가입이 남아 있었다.
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
		/** 선택 항목이다. 비우면 개인화를 끄는 것으로 본다. */
		Boolean behaviorPersonalizationEnabled) {

	/** 비우면 개인화를 끄는 것으로 본다. */
	public boolean behaviorPersonalizationEnabledOrFalse() {
		return Boolean.TRUE.equals(behaviorPersonalizationEnabled);
	}

	/** 나이 확인. 검증을 지나온 요청에서는 언제나 참이다. */
	public boolean ageGateAcceptedOrFalse() {
		return Boolean.TRUE.equals(ageGateAccepted);
	}
}
