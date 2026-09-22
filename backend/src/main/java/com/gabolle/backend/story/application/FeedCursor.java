package com.gabolle.backend.story.application;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

/**
 * 피드 커서 — "마지막으로 본 기록의 (공개 시각, 식별자)", 인기순에서는 좋아요 수와 <b>창 기준
 * 시각</b>까지. 정렬 열 전부가 커서에 있어야 다음 쪽이 그 자리에서 이어진다. 페이지 번호 대신
 * 이것을 쓰는 이유는 {@code StoryRepository} javadoc 에 있다. 앱에는 불투명한
 * 문자열(base64url)이다 — 안의 모양을 앱이 알면 그것이 계약이 되어 서버가 바꿀 수 없다.
 *
 * <p>시각은 {@link Instant#toString()} 으로 나노초까지 적는다. 밀리초로 줄이면 같은 밀리초에 올라온
 * 두 기록 사이에서 커서가 어긋나 하나를 건너뛴다 — timestamptz 는 마이크로초까지 있다.
 *
 * <h2>🔴 창 기준 시각이 왜 커서에 실리나</h2>
 *
 * 인기순은 «최근 24시간 안에 받은 좋아요» 로 센다({@code StoryFeedService.POPULAR_WINDOW}).
 * 그 24시간을 쪽마다 새로 계산하면 <b>창이 요청 사이에 조금씩 밀린다</b> — 1쪽을 그린 뒤 2쪽을
 * 부르는 사이에 창의 왼쪽 끝을 지나간 좋아요가 사라지므로, 경계에 있던 글의 점수가 내려가
 * 이미 본 글이 다시 나오거나 못 본 글이 건너뛰어진다.
 *
 * <p>그래서 첫 쪽에서 한 번 정한 시각을 커서에 실어 <b>끝까지 같은 것을 쓴다.</b> 쪽을 넘기는
 * 동안 창은 고정이고, 목록을 처음부터 다시 부를 때 새 창이 잡힌다.
 */
public record FeedCursor(Instant publishAt, UUID storyId, Integer likeCount, Instant windowStart) {

	/**
	 * 인기순 커서는 좋아요 수와 창 기준 시각이 <b>둘 다</b> 있어야 한다. 하나만 있는 커서를 만들 수
	 * 있게 두면 {@link #encode()} 가 그중 하나를 말없이 빠뜨리고, 그 커서를 받은 다음 쪽은 창이
	 * 다시 밀린 채로 돈다 — 화면에서는 「가끔 글이 겹친다」로만 보이고 원인이 안 드러난다.
	 */
	public FeedCursor {
		if ((likeCount == null) != (windowStart == null)) {
			throw new IllegalArgumentException("인기순 커서는 좋아요 수와 창 기준 시각이 둘 다 있어야 합니다.");
		}
	}

	/** 커서가 없을 때 — "가장 최신부터". timestamptz 가 담을 수 있는 먼 미래와 가장 큰 UUID 다. */
	public static final FeedCursor NONE = new FeedCursor(Instant.parse("9999-12-31T23:59:59Z"),
			UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff"), null, null);

	/**
	 * 최신순 커서. 좋아요 수도 창도 안 싣는다 — 그 정렬은 좋아요를 안 보므로, 실으면 안 쓰는 값이
	 * 계약에 들어간다.
	 */
	public FeedCursor(Instant publishAt, UUID storyId) {
		this(publishAt, storyId, null, null);
	}

	/**
	 * 인기순에서 커서가 없을 때 — 좋아요 수의 상한에서 내려온다. 첫 쪽은 어떤 글의 좋아요 수도
	 * 이보다 클 수 없어야 하므로 {@link Integer#MAX_VALUE} 다.
	 *
	 * @param windowStart 이 목록이 끝까지 쓸 창의 왼쪽 끝. 부르는 쪽이 «지금 - 24시간» 으로 한 번
	 *     정하고, 이후 쪽은 이 값을 커서에서 받아 그대로 쓴다
	 */
	public static FeedCursor nonePopular(Instant windowStart) {
		return new FeedCursor(NONE.publishAt(), NONE.storyId(), Integer.MAX_VALUE, windowStart);
	}

	/** 인기순 커서인가. 두 값은 함께 있거나 함께 없으므로 하나만 봐도 된다. */
	public boolean isPopular() {
		return this.likeCount != null;
	}

	public String encode() {
		String raw = this.publishAt.toString() + "|" + this.storyId
				+ (isPopular() ? "|" + this.likeCount + "|" + this.windowStart : "");
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
			String[] parts = raw.split("\\|");
			// 🔴 칸이 둘(최신순) 아니면 넷(인기순)이다. 셋짜리는 창 기준 시각이 생기기 전에 나간
			//    옛 인기순 커서라 거절한다 — 받아 주면 창을 이 요청에서 새로 잡게 되고, 그것이
			//    바로 이 커서가 막으려는 «창이 밀리는» 상태다. 앱은 400 을 받고 첫 쪽부터 간다.
			if (parts.length != 2 && parts.length != 4) {
				throw new InvalidCursorException(encoded);
			}
			if (parts.length == 2) {
				return new FeedCursor(Instant.parse(parts[0]), UUID.fromString(parts[1]));
			}
			return new FeedCursor(Instant.parse(parts[0]), UUID.fromString(parts[1]), Integer.valueOf(parts[2]),
					Instant.parse(parts[3]));
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
