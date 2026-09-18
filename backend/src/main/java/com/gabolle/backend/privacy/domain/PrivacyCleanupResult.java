package com.gabolle.backend.privacy.domain;

/**
 * {@link com.gabolle.backend.privacy.application.PrivacyCleanupService#cleanup()} 한 번의 카테고리별
 * 삭제 건수.
 *
 * <h2>🔴 조회·복사 낱개는 「지웠다」는 뜻이지 「수가 줄었다」가 아니다 — S15P21E201-1216</h2>
 *
 * {@code storyViewsDeleted} 는 {@code story_view} 에서 지운 <b>낱개 행</b> 수다. 글의 누적 조회수
 * ({@code story.view_count})는 <b>한 톨도 안 내려간다.</b> 낱개는 「사람 × 글 × 하루 한 번」을
 * 지키려고 두는 것이고 누적은 누적이다 — 같이 내리면 어제까지의 조회가 사라진다.
 * {@code story_link_copy} 도 같다.
 */
public record PrivacyCleanupResult(int sessionsDeleted, int refreshTokensDeleted, int eventsDeleted,
		int storyViewsDeleted, int storyLinkCopiesDeleted) {

	public int total() {
		return sessionsDeleted + refreshTokensDeleted + eventsDeleted + storyViewsDeleted + storyLinkCopiesDeleted;
	}
}
