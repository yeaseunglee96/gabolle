package com.gabolle.backend.auth.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * {@code POST /api/v1/auth/oauth/link} — 연결 티켓 + 기존 계정의 비밀번호로 소셜 신원을 붙인다 (S15P21E201-690).
 *
 * <p>비밀번호를 받는 이유가 이 흐름의 전부다 — 이메일이 같다는 것만으로 붙이면 남의 이메일로 소셜 계정을 만든
 * 사람이 기존 계정에 들어간다({@code DEC-AUTH-010}).
 */
public record OAuthLinkRequest(
		@NotBlank String linkTicket,
		@NotBlank @Size(max = 100) String password,
		@Size(max = 255) String deviceId) {
}
