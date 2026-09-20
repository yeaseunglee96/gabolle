package com.gabolle.backend.privacy.domain;

/**
 * {@link com.gabolle.backend.privacy.application.PrivacyCleanupService#cleanup()} 한 번의 카테고리별
 * 삭제 건수.
 *
 * <p>{@code storyViewsDeleted} 는 {@code story_view} 에서 지운 낱개 행 수다. 글의 누적 조회수
 * ({@code story.view_count})는 내려가지 않는다. {@code story_link_copy} 도 같다.
 */
public record PrivacyCleanupResult(int sessionsDeleted, int refreshTokensDeleted, int eventsDeleted,
		int storyViewsDeleted, int storyLinkCopiesDeleted) {

	public int total() {
		return sessionsDeleted + refreshTokensDeleted + eventsDeleted + storyViewsDeleted + storyLinkCopiesDeleted;
	}
}
