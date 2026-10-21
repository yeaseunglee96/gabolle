package com.gabolle.testslice;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

import com.gabolle.backend.feedback.application.FeedbackIssueTracker;
import com.gabolle.backend.feedback.application.FeedbackNotifier;
import com.gabolle.backend.feedback.application.FeedbackService.Feedback;

/**
 * 의견 보내기(S15P21E201-2049)를 진짜 PostgreSQL 위에서 보는 슬라이스.
 *
 * <p>{@code feedback.adapter} 는 훑지 않는다 — 푸시는 알림 토큰 저장소를, Jira 는 바깥 HTTP 를 끌고 온다. 대신 무엇을
 * 보냈는지 적어 두는 대역({@link Recorder})을 올린다.
 */
@SpringBootApplication(scanBasePackages = { "com.gabolle.backend.common", "com.gabolle.backend.feedback.application",
		"com.gabolle.backend.feedback.presentation" })
public class FeedbackSliceApplication {

	@Bean
	Recorder feedbackRecorder() {
		return new Recorder();
	}

	/** Jira 이슈를 만들지 않고 번호만 지어 주고, 푸시를 보내지 않고 적어 둔다. */
	public static class Recorder implements FeedbackIssueTracker, FeedbackNotifier {

		public final List<UUID> issued = new ArrayList<>();

		public final List<UUID> replied = new ArrayList<>();

		@Override
		public String create(Feedback feedback) {
			this.issued.add(feedback.feedbackId());
			return "TEST-" + this.issued.size();
		}

		@Override
		public void replied(UUID userId, String language) {
			this.replied.add(userId);
		}
	}
}
