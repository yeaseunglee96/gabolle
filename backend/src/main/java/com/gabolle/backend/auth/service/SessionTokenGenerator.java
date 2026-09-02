package com.gabolle.backend.auth.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;

@Component
public class SessionTokenGenerator {

	private static final int TOKEN_BYTES = 32;
	private final SecureRandom secureRandom;

	public SessionTokenGenerator() {
		this(new SecureRandom());
	}

	SessionTokenGenerator(SecureRandom secureRandom) {
		this.secureRandom = secureRandom;
	}

	public String issue() {
		byte[] bytes = new byte[TOKEN_BYTES];
		secureRandom.nextBytes(bytes);
		return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
	}

	public String hash(String token) {
		try {
			byte[] digest = MessageDigest.getInstance("SHA-256")
					.digest(token.getBytes(StandardCharsets.UTF_8));
			return toHex(digest);
		} catch (NoSuchAlgorithmException exception) {
			throw new IllegalStateException("SHA-256 is required by the runtime", exception);
		}
	}

	private String toHex(byte[] bytes) {
		StringBuilder result = new StringBuilder(bytes.length * 2);
		for (byte value : bytes) {
			result.append(String.format("%02x", value));
		}
		return result.toString();
	}
}
