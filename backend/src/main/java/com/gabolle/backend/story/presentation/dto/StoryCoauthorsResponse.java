package com.gabolle.backend.story.presentation.dto;

import java.util.List;

/**
 * 기록 참여자 목록 응답 — S15P21E201-770. 만든 사람과 공동 작성자를 함께 담는다.
 */
public record StoryCoauthorsResponse(List<Coauthor> coauthors) {

	/**
	 * 참여자 한 명. {@code isAuthor} 로 만든 사람인지 표시한다.
	 *
	 * <p>만든 사람의 {@code joinedAt} 은 기록이 만들어진 시각이다 — {@code story_coauthor} 행이
	 * 없어서 별도의 합류 시각을 갖지 않는다.
	 */
	public record Coauthor(
			String userId,
			/** {@code app_user} 행이 없으면(탈퇴 등) {@code null}. */
			String displayName,
			boolean isAuthor,
			String joinedAt) {
	}
}
