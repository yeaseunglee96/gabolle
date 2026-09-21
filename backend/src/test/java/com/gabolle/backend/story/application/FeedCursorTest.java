package com.gabolle.backend.story.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Base64;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/** 커서가 정렬 갈래마다 모양이 다르고, 그 모양이 왕복해도 그대로인지 본다. */
class FeedCursorTest {

	private static final Instant AT = Instant.parse("2026-09-21T03:04:05.123456Z");

	private static final UUID ID = UUID.fromString("11111111-2222-3333-4444-555555555555");

	@Test
	@DisplayName("최신순 커서는 좋아요 수를 안 싣는다")
	void recentCursorCarriesNoLikeCount() {
		FeedCursor decoded = FeedCursor.decode(new FeedCursor(AT, ID).encode());

		assertThat(decoded.publishAt()).isEqualTo(AT);
		assertThat(decoded.storyId()).isEqualTo(ID);
		assertThat(decoded.likeCount()).isNull();
	}

	@Test
	@DisplayName("인기순 커서는 좋아요 수까지 싣고 왕복해도 그대로다")
	void popularCursorRoundTripsLikeCount() {
		FeedCursor decoded = FeedCursor.decode(new FeedCursor(AT, ID, 7).encode());

		assertThat(decoded.publishAt()).isEqualTo(AT);
		assertThat(decoded.storyId()).isEqualTo(ID);
		assertThat(decoded.likeCount()).isEqualTo(7);
	}

	@Test
	@DisplayName("좋아요가 0 인 것과 최신순인 것은 다른 커서다 — 0 을 null 로 접으면 갈래를 못 가른다")
	void zeroLikesIsNotTheSameAsNoLikes() {
		assertThat(FeedCursor.decode(new FeedCursor(AT, ID, 0).encode()).likeCount()).isZero();
		assertThat(FeedCursor.decode(new FeedCursor(AT, ID).encode()).likeCount()).isNull();
	}

	@Test
	@DisplayName("인기순의 첫 쪽은 좋아요 수의 상한에서 내려온다")
	void popularStartsAtTheCeiling() {
		assertThat(FeedCursor.nonePopular().likeCount()).isEqualTo(Integer.MAX_VALUE);
	}

	@Test
	@DisplayName("나노초가 살아 있다 — 잘리면 같은 밀리초의 기록 하나를 건너뛴다")
	void keepsSubMillisecondPrecision() {
		assertThat(FeedCursor.decode(new FeedCursor(AT, ID).encode()).publishAt()).isEqualTo(AT);
	}

	@Test
	@DisplayName("우리가 만든 모양이 아니면 거절한다")
	void rejectsForeignShapes() {
		assertThatThrownBy(() -> FeedCursor.decode(encode("바|가|셋|넷")))
				.isInstanceOf(FeedCursor.InvalidCursorException.class);
		assertThatThrownBy(() -> FeedCursor.decode(encode("막대가없다")))
				.isInstanceOf(FeedCursor.InvalidCursorException.class);
		assertThatThrownBy(() -> FeedCursor.decode(encode(AT + "|" + ID + "|숫자가아님")))
				.isInstanceOf(FeedCursor.InvalidCursorException.class);
	}

	@Test
	@DisplayName("빈 커서는 첫 쪽이다")
	void blankMeansFirstPage() {
		assertThat(FeedCursor.decode(null)).isEqualTo(FeedCursor.NONE);
		assertThat(FeedCursor.decode("  ")).isEqualTo(FeedCursor.NONE);
	}

	private static String encode(String raw) {
		return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes(java.nio.charset.StandardCharsets.UTF_8));
	}
}
