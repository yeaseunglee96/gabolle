package com.gabolle.backend.auth.api;

import com.gabolle.backend.auth.service.AnonymousSessionService.IssuedAnonymousSession;
import java.time.Instant;
import java.util.UUID;

/**
 * {@code POST /api/v1/auth/anonymous} 응답.
 *
 * <p>{@code sessionToken} 원본이 실리는 유일한 자리이고 서버는 이 값을 저장하지 않는다.
 * 이후 요청은 이 값을 {@code X-Session-Token} 헤더에 그대로 실어야 같은 세션으로 인식된다.
 */
public record AnonymousSessionResponse(UUID sessionId, String sessionToken, Instant issuedAt) {

	public static AnonymousSessionResponse from(IssuedAnonymousSession issued) {
		return new AnonymousSessionResponse(issued.sessionId(), issued.token(), issued.issuedAt());
	}
}
