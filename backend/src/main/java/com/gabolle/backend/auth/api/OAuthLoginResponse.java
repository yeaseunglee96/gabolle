package com.gabolle.backend.auth.api;

import java.time.Instant;
import java.util.UUID;

/**
 * {@code POST /api/v1/auth/oauth/{provider}} 와 {@code /oauth/signup} · {@code /oauth/link} 의 응답 —
 * S15P21E201-689 · -690.
 *
 * <p>{@link #status} 가 셋 중 하나다.
 * <ul>
 *   <li>{@code LOGGED_IN}(200·201) — 토큰 칸이 채워진다. 🔴 {@code accessToken}·{@code refreshToken}·
 *       {@code expiresIn}·{@code sessionId}·{@code user} 는 {@link AuthTokenResponse} 와 같은 이름·같은 자리라
 *       지금 배포된 앱이 {@code data.accessToken} 을 읽는 코드가 그대로 동작한다.</li>
 *   <li>{@code SIGNUP_REQUIRED}(200) — 처음 보는 소셜 계정. <b>계정을 만들지 않았다.</b> {@link #signupTicket} 과
 *       {@link #prefill} 로 회원가입 화면을 채우고 완료하면 {@code POST /auth/oauth/signup} 을 부른다.</li>
 *   <li>{@code LINK_REQUIRED}(409) — 같은 이메일의 기존 계정이 있다. {@link #linkTicket} 과 비밀번호로
 *       {@code POST /auth/oauth/link} 를 부른다. 이 응답만 {@code data} 와 {@code error} 를 함께 싣는다 —
 *       옛 앱은 {@code error.code}({@code OAUTH_ACCOUNT_LINK_REQUIRED})만 보고 안내를 띄우고, 새 앱은
 *       {@code data.linkTicket} 을 쓴다.</li>
 * </ul>
 *
 * <p>세 갈래를 한 record 로 둔 이유 — 화면은 {@code status} 하나만 보고 분기하면 된다. 갈래마다 record 를 따로
 * 두면 FE 가 응답 모양을 먼저 판별해야 하고, 그 판별 기준이 문서에만 남는다.
 */
public record OAuthLoginResponse(
		String status,
		String accessToken,
		String refreshToken,
		Long expiresIn,
		UUID sessionId,
		AuthUserResponse user,
		String signupTicket,
		Prefill prefill,
		String linkTicket,
		String maskedEmail,
		String provider,
		Instant ticketExpiresAt) {

	public static final String LOGGED_IN = "LOGGED_IN";
	public static final String SIGNUP_REQUIRED = "SIGNUP_REQUIRED";
	public static final String LINK_REQUIRED = "LINK_REQUIRED";

	/**
	 * 회원가입 화면에 미리 채울 값. 전부 provider 가 준 그대로라 사용자가 고칠 수 있다.
	 *
	 * @param emailProvided provider 가 이메일을 줬는가. 카카오 기본 동의는 안 준다 — 그때 화면은 "이메일 없이 가입" 을
	 *     알리고 비밀번호 재설정 같은 메일 기능을 쓸 수 없다고 고지한다
	 */
	public record Prefill(String email, String displayName, String language, boolean emailProvided) {
	}

	public static OAuthLoginResponse loggedIn(AuthTokenResponse tokens) {
		return new OAuthLoginResponse(LOGGED_IN, tokens.accessToken(), tokens.refreshToken(), tokens.expiresIn(),
				tokens.sessionId(), tokens.user(), null, null, null, null, null, null);
	}

	public static OAuthLoginResponse signupRequired(String signupTicket, Instant ticketExpiresAt, Prefill prefill) {
		return new OAuthLoginResponse(SIGNUP_REQUIRED, null, null, null, null, null, signupTicket, prefill, null, null,
				null, ticketExpiresAt);
	}

	public static OAuthLoginResponse linkRequired(String linkTicket, Instant ticketExpiresAt, String maskedEmail,
			String provider) {
		return new OAuthLoginResponse(LINK_REQUIRED, null, null, null, null, null, null, null, linkTicket, maskedEmail,
				provider, ticketExpiresAt);
	}

	/**
	 * {@code traveler@example.com} → {@code t***@example.com}. 로컬 부분이 한 글자면 {@code *@...}.
	 *
	 * <p>🔴 가리는 이유 — 이 값은 "provider 가 준 이메일로 우리 표를 찔러 본 결과" 다. 그대로 돌려주면 아무나 남의
	 * 이메일 가입 여부를 확인할 수 있다. 사용자에게는 "어느 계정에 붙는지" 를 알려 줄 만큼만 남긴다.
	 */
	public static String mask(String email) {
		if (email == null || !email.contains("@")) {
			return null;
		}
		int at = email.indexOf('@');
		String local = email.substring(0, at);
		String head = local.length() > 1 ? local.substring(0, 1) + "***" : "*";
		return head + email.substring(at);
	}
}
