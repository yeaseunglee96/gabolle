package com.gabolle.backend.tools.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 원문+방향을 캐시 열쇠로 바꾼다 — S15P21E201-343.
 *
 * <p>🔴 <b>원문 자체는 어디에도 남기지 않는다.</b> "번역한 문장을 데이터베이스나 로그에
 * 남기지 않는다" 는 요구를 지키려면 캐시 열쇠부터 원문을 되돌릴 수 없어야 한다 — 그래서
 * 원문을 그대로 쓰지 않고 SHA-256 해시만 쓴다. {@code auth.service.SessionTokenGenerator.hash}
 * 와 같은 알고리즘·같은 16진 인코딩이지만, domain 계층은 Spring 을 import 하지 않으므로
 * ({@code SessionTokenGenerator} 는 {@code @Component}) 여기서는 별도로 둔다.
 */
public final class TranslationHash {

	private TranslationHash() {
	}

	/** 같은 원문이라도 방향이 다르면 다른 열쇠다 — 번역 결과가 다르기 때문이다. */
	public static String of(String sourceText, TranslationDirection direction) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			digest.update(direction.name().getBytes(StandardCharsets.UTF_8));
			digest.update((byte) '|');
			digest.update(sourceText.getBytes(StandardCharsets.UTF_8));
			return toHex(digest.digest());
		}
		catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 은 이 실행 환경에 항상 있어야 한다", exception);
		}
	}

	private static String toHex(byte[] bytes) {
		StringBuilder result = new StringBuilder(bytes.length * 2);
		for (byte value : bytes) {
			result.append(String.format("%02x", value));
		}
		return result.toString();
	}
}
