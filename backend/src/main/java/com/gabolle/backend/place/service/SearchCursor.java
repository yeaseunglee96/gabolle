package com.gabolle.backend.place.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Base64;
import java.util.List;
import java.util.Locale;

/**
 * 이름 검색(-462)의 이어받기 커서.
 *
 * <p>겉보기엔 불투명하지만 안에는 {@code fingerprint}(검색 조건의 지문)와 {@code offset}(다음에
 * 읽을 위치) 뿐이다. 서버가 상태를 어디에도 남기지 않는다 — 그래서 서버를 여러 대로 늘려도,
 * 재시작해도 커서가 그대로 동작한다.
 *
 * <h2>🔴 왜 {@code JsonPayloads} 를 쓰지 않는가</h2>
 *
 * {@code JsonPayloads} 는 JSONB 컬럼에 쓸 값을 만드는 쪽으로 짜여 있다 — 스프링에서
 * {@code ObjectMapper} 를 주입받고, 쓰기 전에 개인정보 검사를 통과시키는 것이 목적이라 읽기(파싱)
 * 메서드가 아예 없다. 커서는 그 반대로 클라이언트가 들고 있다가 되돌려주는 값이라 파싱이
 * 필요하고, 이 클래스는 스프링 빈이 아닌 순수 자바로 남겨 {@code SearchCursorTest} 가 DB 도
 * 스프링도 없이 돌게 하고 싶었다. 담을 값이 문자열 둘뿐이라 JSON 이 주는 이득도 없어서
 * {@code "<fingerprint>:<offset>"} 을 base64url 로 감싸는 손수 구현으로 충분하다.
 *
 * <h2>🔴 fingerprint 가 왜 필요한가</h2>
 *
 * 커서는 offset 만 담는다. 그런데 사용자가 검색어나 종류를 바꾼 채로 이전 커서를 그대로
 * 보내면, 그 offset 은 <b>다른 질의의 몇 번째 결과</b>를 가리키게 된다 — 결과가 조용히
 * 뒤섞인다. fingerprint 로 "이 커서가 어느 조건에서 나왔는가" 를 같이 담아서, 서비스가 지금
 * 조건과 비교해 다르면 거부한다.
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
			// SHA-256 은 모든 JVM 이 표준으로 제공해야 한다. 여기 오면 실행 환경이 망가진 것이다.
			throw new IllegalStateException("SHA-256 을 사용할 수 없습니다.", ex);
		}
	}

	public String encode() {
		String raw = this.fingerprint + SEPARATOR + this.offset;
		return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
	}

	/**
	 * 커서를 되돌린다. 🔴 여기서는 <b>형식</b>만 본다 — fingerprint 가 지금 검색 조건과 맞는지는
	 * 호출하는 서비스가 새로 계산한 값과 비교해야 한다. 이 메서드는 그 비교에 쓸 두 값을
	 * 꺼내는 것까지만 책임진다.
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
			// Base64 디코딩 실패와 Integer.parseInt 실패가 둘 다 이 타입이라 한 번에 잡는다.
			throw invalidCursor();
		}
	}

	/**
	 * 🔴 파싱 실패와 fingerprint 불일치를 같은 코드·메시지로 던진다. 클라이언트에게는 "어느
	 * 쪽이 문제인지" 가 아니라 "이어받을 수 없다" 는 사실만 중요하고, 문구를 갈라 두면 조건이
	 * 바뀐 것과 커서를 손으로 조작한 것을 프런트가 구분해야 하는 부담만 생긴다.
	 */
	private static PlaceRequestException invalidCursor() {
		return new PlaceRequestException("INVALID_CURSOR", "검색 조건이 바뀌어 이어받을 수 없습니다.", List.of("cursor"));
	}
}
