package com.gabolle.backend.place.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

/**
 * 이름 검색의 이어받기 커서 — {@code "<fingerprint>:<offset>"} 을 base64url 로 감싼 것뿐이다.
 * 서버가 상태를 남기지 않으므로 서버를 늘리거나 재시작해도 그대로 동작한다.
 *
 * <p>스프링 빈이 아닌 순수 자바로 둔다. 커서 검사를 DB 도 스프링도 없이 돌리기 위해서다.
 *
 * <p>fingerprint 는 검색 조건이 바뀐 채로 이어받는 실수를 거르는 체크섬이다. 위조 방지가
 * 아니다 — 요청자도 {@link #fingerprint} 로 직접 계산할 수 있다. 큰 offset 으로 표 전체를 훑는
 * 것은 {@code PlaceSearchService} 의 {@code MAX_OFFSET} 상한이 막는다.
 */
public final class SearchCursor {

	private static final String SEPARATOR = ":";

	private final String fingerprint;

	private final int offset;

	private SearchCursor(String fingerprint, int offset) {
		this.fingerprint = fingerprint;
		this.offset = offset;
	}

	public static SearchCursor of(String fingerprint, int offset) {
		return new SearchCursor(fingerprint, offset);
	}

	public String fingerprint() {
		return this.fingerprint;
	}

	public int offset() {
		return this.offset;
	}

	/** 지금 검색 조건의 지문. SHA-256 앞 16자(hex) — 커서 안에 검색어 원문을 남기지 않는다. */
	public static String fingerprint(String query, String category, int limit) {
		String source = query + "|" + (category == null ? "" : category) + "|" + limit;
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			byte[] hash = digest.digest(source.getBytes(StandardCharsets.UTF_8));
			StringBuilder hex = new StringBuilder();
			for (byte b : hash) {
				hex.append(String.format(Locale.ROOT, "%02x", b));
			}
			return hex.substring(0, 16);
		}
		catch (NoSuchAlgorithmException ex) {
			throw new IllegalStateException("SHA-256 을 사용할 수 없습니다.", ex);
		}
	}

	public String encode() {
		String raw = this.fingerprint + SEPARATOR + this.offset;
		return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
	}

	/**
	 * 형식만 본다. fingerprint 가 지금 검색 조건과 맞는지는 부르는 서비스가 새로 계산해
	 * 비교해야 한다.
	 */
	public static SearchCursor decode(String cursor) {
		try {
			byte[] decoded = Base64.getUrlDecoder().decode(cursor);
			String raw = new String(decoded, StandardCharsets.UTF_8);
			int separatorIndex = raw.indexOf(SEPARATOR);
			if (separatorIndex < 0) {
				throw invalidCursor();
			}
			String fingerprint = raw.substring(0, separatorIndex);
			String offsetPart = raw.substring(separatorIndex + 1);
			if (fingerprint.isEmpty()) {
				throw invalidCursor();
			}
			int offset = Integer.parseInt(offsetPart);
			if (offset < 0) {
				throw invalidCursor();
			}
			return new SearchCursor(fingerprint, offset);
		}
		catch (IllegalArgumentException ex) {
			// Base64 디코딩 실패와 Integer.parseInt 실패가 둘 다 이 타입이다.
			throw invalidCursor();
		}
	}

	/**
	 * 파싱 실패와 fingerprint 불일치를 같은 코드·메시지로 던진다. 프런트에게는 "이어받을 수
	 * 없다" 는 사실만 필요하고, 갈라 두면 둘을 구분해야 하는 부담만 생긴다.
	 */
	private static PlaceRequestException invalidCursor() {
		return new PlaceRequestException("INVALID_CURSOR", "검색 조건이 바뀌어 이어받을 수 없습니다.", List.of("cursor"));
	}
}
