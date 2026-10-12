package com.gabolle.backend.feedback.application;

/**
 * 받은 의견을 일감 관리(Jira)에 이슈로 올린다 — S15P21E201-2049. 접수가 커밋된 뒤 부른다.
 */
public interface FeedbackIssueTracker {

	/** @return 만든 이슈 키(예: S15P21E201-123). 만들지 않았으면 {@code null} */
	String create(FeedbackService.Feedback feedback);
}
