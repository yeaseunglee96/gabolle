package com.gabolle.backend.moderation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.gabolle.backend.moderation.domain.StoryReport;
import com.gabolle.backend.moderation.domain.StoryReportReason;
import com.gabolle.backend.moderation.domain.StoryReportResolution;

/** {@link StoryReport} 도메인 규칙의 단위 테스트 — DB 없이 돈다. */
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
