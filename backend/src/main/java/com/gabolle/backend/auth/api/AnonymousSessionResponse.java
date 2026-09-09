package com.gabolle.backend.auth.api;

import com.gabolle.backend.auth.service.AnonymousSessionService.IssuedAnonymousSession;
import java.time.Instant;
import java.util.UUID;

/**
 * {@code POST /api/v1/auth/anonymous} 응답 — S15P21E201-303.
 *
 * <p>{@code sessionToken} 이 원본 출입증이다. 이 응답이 유일하게 원본을 담는 자리이고,
 * 서버는 이 값을 저장하지 않는다 — 다음부터는 {@code X-Session-Token} 헤더로 이 값을 그대로
 * 실어 보내야 같은 세션의 주인으로 인식된다.
 */
public record AnonymousSessionResponse(UUID sessionId, String sessionToken, Instant issuedAt) {

	public static AnonymousSessionResponse from(IssuedAnonymousSession issued) {
		return new AnonymousSessionResponse(issued.sessionId(), issued.token(), issued.issuedAt());
	}
}
