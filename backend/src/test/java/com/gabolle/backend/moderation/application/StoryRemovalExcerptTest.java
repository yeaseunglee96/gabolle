package com.gabolle.backend.moderation.application;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * 알림에 싣는 본문 앞부분 — S15P21E201-794.
 *
 * <p>전문을 싣지 않는 이유는 목적이 "어느 기록인지 알아보게 하는 것" 이기 때문이다. 길게 실으면
 * 신고까지 받은 글이 메일 서버와 우편함에 한 벌 더 남는다.
 */
class StoryRemovalExcerptTest {

	@Test
	@DisplayName("짧은 본문은 그대로 간다")
	void shortBodyUnchanged() {
		assertThat(ModerationQueueService.excerptOf("부산 여행 첫째 날")).isEqualTo("부산 여행 첫째 날");
	}

	@Test
	@DisplayName("긴 본문은 잘리고, 잘렸다는 것이 보인다")
	void longBodyIsTruncatedVisibly() {
		String body = "가".repeat(ModerationQueueService.EXCERPT_LENGTH + 10);

		String excerpt = ModerationQueueService.excerptOf(body);

		assertThat(excerpt).hasSize(ModerationQueueService.EXCERPT_LENGTH + 1);
		assertThat(excerpt).endsWith("…");
	}

	@Test
	@DisplayName("딱 상한 길이면 말줄임을 안 붙인다")
	void exactlyAtLimitIsNotTruncated() {
		String body = "나".repeat(ModerationQueueService.EXCERPT_LENGTH);

		assertThat(ModerationQueueService.excerptOf(body)).isEqualTo(body).doesNotEndWith("…");
	}

	@Test
	@DisplayName("앞뒤 공백은 떼고 센다 — 공백만 있는 본문이 상한을 채운 것처럼 보이지 않게")
	void whitespaceIsStripped() {
		assertThat(ModerationQueueService.excerptOf("   부산   ")).isEqualTo("부산");
	}

	@Test
	@DisplayName("본문이 없어도 터지지 않는다")
	void nullBodyBecomesEmpty() {
		assertThat(ModerationQueueService.excerptOf(null)).isEmpty();
	}
}
