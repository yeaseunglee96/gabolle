package com.gabolle.backend.moderation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.gabolle.backend.moderation.domain.StoryReport;
import com.gabolle.backend.moderation.domain.StoryReportReason;
import com.gabolle.backend.moderation.domain.StoryReportResolution;

/**
 * {@link StoryReport} 도메인 규칙의 순수 단위 테스트 — DB 없이 돈다.
 *
 * <p>"이미 처리된 신고를 다시 처리하면 409" 라는 완료 기준의 뿌리가 여기 있다. HTTP 계층에서는
 * {@code AdminModerationQueueIntegrationTest.reprocessingAlreadyResolvedStoryIsConflict} 가
 * {@code ModerationQueueService.NoPendingReportsException}(기록 단위) 경로로 같은 결과(409)를
 * 확인하고, 이 테스트는 그 바탕이 되는 {@link StoryReport#resolve} 자체(신고 한 건 단위)를 본다.
 */
class StoryReportDomainTest {

	@Test
	void resolvingTwiceThrowsAlreadyResolved() {
		StoryReport report = StoryReport.file(UUID.randomUUID(), UUID.randomUUID(), StoryReportReason.SPAM, null,
				Instant.now());
		UUID admin = UUID.randomUUID();

		report.resolve(StoryReportResolution.DISMISSED, admin, Instant.now());

		assertThatThrownBy(() -> report.resolve(StoryReportResolution.REMOVED, admin, Instant.now()))
				.isInstanceOf(StoryReport.AlreadyResolvedException.class);
	}

	@Test
	void otherDetailIsKeptOnlyForOtherReason() {
		StoryReport withDetail = StoryReport.file(UUID.randomUUID(), UUID.randomUUID(), StoryReportReason.OTHER,
				" 자유 입력 ", Instant.now());
		StoryReport withoutDetail = StoryReport.file(UUID.randomUUID(), UUID.randomUUID(), StoryReportReason.SPAM,
				"이건 버려진다", Instant.now());

		assertThat(withDetail.getDetail()).isEqualTo("자유 입력");
		assertThat(withoutDetail.getDetail()).isNull();
	}
}
