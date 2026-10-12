package com.gabolle.backend.feedback.application;

import java.util.UUID;

/** 운영자가 답했다고 의견을 보낸 사람에게 알린다 — S15P21E201-2049. 던지지 않는다. */
public interface FeedbackNotifier {

	/** @param language 받는 사람의 앱 언어. 모르면 {@code null} */
	void replied(UUID userId, String language);
}
