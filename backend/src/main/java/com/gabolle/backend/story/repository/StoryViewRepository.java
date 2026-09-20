package com.gabolle.backend.story.repository;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.story.domain.StoryView;

/** 조회 낱개. */
public interface StoryViewRepository extends JpaRepository<StoryView, UUID> {

	/**
	 * 오늘 이 사람의 조회를 한 번만 남긴다. 「오늘 것이 있나」를 먼저 읽으면 같은 사람의 동시 요청이
	 * 둘 다 통과하므로, 넣어 보고 돌아온 행 수로 판정한다. 막는 것은 조건부 유일 색인
	 * 둘({@code ux_story_view_member} · {@code ux_story_view_anonymous})이고, 조건까지 옮겨 적으면
	 * 색인 정의와 두 벌이 되므로 {@code ON CONFLICT} 에 대상을 안 적는다.
	 * {@code clearAutomatically} 는 쓰지 않는다 — 비우면 부르는 쪽이 들고 있던 {@code Story} 가
	 * 떨어져 나가 {@code viewCount} 증가가 오류 없이 사라진다.
	 *
	 * @return 오늘 처음이면 1, 이미 셌으면 0
	 */
	@Transactional
	@Modifying(flushAutomatically = true)
	@Query(value = """
			INSERT INTO story_view
			    (story_view_id, story_id, user_id, anonymous_session_id, viewed_on, created_at)
			VALUES (:storyViewId, :storyId, :userId, :anonymousSessionId, :viewedOn, :now)
			ON CONFLICT DO NOTHING
			""", nativeQuery = true)
	int insertIfAbsent(@Param("storyViewId") UUID storyViewId, @Param("storyId") UUID storyId,
			@Param("userId") UUID userId, @Param("anonymousSessionId") UUID anonymousSessionId,
			@Param("viewedOn") LocalDate viewedOn, @Param("now") Instant now);

}
