package com.gabolle.backend.story.application;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * 피드 커서 — "마지막으로 본 기록의 (공개 시각, 식별자)". 페이지 번호 대신 이것을 쓰는 이유는
 * {@code StoryRepository} javadoc 에 있다. 앱에는 불투명한 문자열(base64url)이다 — 안의 모양을 앱이
 * 알면 그것이 계약이 되어 서버가 바꿀 수 없다.
 *
 * <p>시각은 {@link Instant#toString()} 으로 나노초까지 적는다. 밀리초로 줄이면 같은 밀리초에 올라온
 * 두 기록 사이에서 커서가 어긋나 하나를 건너뛴다 — timestamptz 는 마이크로초까지 있다.
 */
public record FeedCursor(Instant publishAt, UUID storyId) {

	/** 커서가 없을 때 — "가장 최신부터". timestamptz 가 담을 수 있는 먼 미래와 가장 큰 UUID 다. */
	public static final FeedCursor NONE = new FeedCursor(Instant.parse("9999-12-31T23:59:59Z"),
			UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"));

	public String encode() {
		String raw = publishAt.toString() + "|" + storyId;
		return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
	}

	/**
	 * @param encoded 앱이 돌려준 문자열. {@code null}·빈 문자열이면 {@link #NONE}
	 * @throws InvalidCursorException 우리가 만든 모양이 아니다 — 400 으로 답할 자리다
	 */
	public static FeedCursor decode(String encoded) {
		if (encoded == null || encoded.isBlank()) {
			return NONE;
		}
		try {
			String raw = new String(Base64.getUrlDecoder().decode(encoded), StandardCharsets.UTF_8);
			int bar = raw.indexOf('|');
			if (bar < 0) {
				throw new InvalidCursorException(encoded);
			}
			return new FeedCursor(Instant.parse(raw.substring(0, bar)), UUID.fromString(raw.substring(bar + 1)));
		}
		catch (IllegalArgumentException | java.time.format.DateTimeParseException ex) {
			throw new InvalidCursorException(encoded);
		}
	}

	public static class InvalidCursorException extends RuntimeException {

		public InvalidCursorException(String cursor) {
			super("커서가 올바르지 않습니다. 이전 응답의 nextCursor 를 그대로 보내 주세요.");
		}
	}
}
