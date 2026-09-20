package com.gabolle.backend.common.security;

import java.security.SecureRandom;
import java.util.Base64;

/**
 * URL 에 실리는 추측 불가 표(token) — 초대 링크·공유 주소가 쓴다. 32바이트 난수를 base64url(패딩 없음)로
 * 적어 43글자다.
 *
 * <p>순번·시각·UUID 에서 파생한 값을 대신 넣지 않는다. UUID 는 식별자로 여기저기 응답에 실리는 값이라
 * 잠금 역할을 하는 값과 섞이면 어느 것이 비밀인지 흐려진다.
 */
public final class OpaqueTokens {

	private static final SecureRandom RANDOM = new SecureRandom();

	private static final int BYTES = 32;

	private OpaqueTokens() {
	}

	/** 43글자 base64url. 매번 새 값이다. */
	public static String generate() {
		byte[] bytes = new byte[BYTES];
		RANDOM.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}
}
