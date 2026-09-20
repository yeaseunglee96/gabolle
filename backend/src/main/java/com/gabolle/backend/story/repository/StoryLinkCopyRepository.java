package com.gabolle.backend.story.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.story.domain.StoryLinkCopy;

/**
 * 링크 복사 낱개. {@link StoryViewRepository} 와 같은 모양이고, 조회와 표를 나눈 이유는
 * {@link com.gabolle.backend.story.domain.StoryLinkCopy} 에 있다.
 */
public interface StoryLinkCopyRepository extends JpaRepository<StoryLinkCopy, UUID> {

	/**
	 * 오늘 이 사람의 링크 복사를 한 번만 남긴다. 「오늘 것이 있나」를 먼저 읽으면 같은 사람의
	 * 동시 요청이 둘 다 통과하므로, 넣어 보고 돌아온 행 수로 판정한다. 막는 것은 조건부 유일 색인
	 * 둘({@code ux_story_link_copy_member} · {@code ux_story_link_copy_anonymous})이고,
	 * 조건까지 옮겨 적으면 색인 정의와 두 벌이 되므로 {@code ON CONFLICT} 에 대상을 안 적는다.
	 * {@code clearAutomatically} 는 쓰지 않는다 — 비우면 부르는 쪽이 들고 있던 {@code Story} 가
	 * 떨어져 나가 {@code linkCopyCount} 증가가 오류 없이 사라진다.
	 *
	 * @return 오늘 처음이면 1, 이미 셌으면 0
	 */
	@Transactional
	@Modifying(flushAutomatically = true)
	@Query(value = """
			INSERT INTO story_link_copy
			    (story_link_copy_id, story_id, user_id, anonymous_session_id, copied_on, created_at)
			VALUES (:storyLinkCopyId, :storyId, :userId, :anonymousSessionId, :copiedOn, :now)
			ON CONFLICT DO NOTHING
			""", nativeQuery = true)
	int insertIfAbsent(@Param("storyLinkCopyId") UUID storyLinkCopyId, @Param("storyId") UUID storyId,
			@Param("userId") UUID userId, @Param("anonymousSessionId") UUID anonymousSessionId,
			@Param("copiedOn") LocalDate copiedOn, @Param("now") Instant now);

}
