package com.gabolle.backend.story.repository;

import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import com.gabolle.backend.story.domain.ReactionType;
import com.gabolle.backend.story.domain.StoryReaction;
import com.gabolle.backend.story.domain.StoryReactionId;

public interface StoryReactionRepository extends JpaRepository<StoryReaction, StoryReactionId> {

	/**
	 * 반응을 넣거나 바꾼다. 읽고-고치고-쓰면 같은 사람이 빠르게 두 번 누를 때 뒤의 것이 기본 키에
	 * 부딪히므로 한 문장으로 한다.
	 *
	 * <p>시각 칸이 둘이다. {@code created_at} 은 이 사람이 이 글에 처음 손댄 때라 여기서 안
	 * 건드리고, {@code reacted_at} 은 지금의 반응을 고른 때라 바뀔 때마다 갱신한다. 인기순의
	 * 24시간 창은 {@code reacted_at} 을 자른다.
	 *
	 * <p>{@code <>} 가 아니라 {@code IS DISTINCT FROM} 이어야 한다 — 취소한 행은
	 * {@code reaction} 이 {@code NULL} 이고 {@code NULL <> 'LIKE'} 는 {@code NULL} 이라,
	 * 취소한 뒤 다시 누르는 것이 조용히 0 을 돌려준다.
	 *
	 * @return 상태가 실제로 바뀌었으면 1, 같은 값이라 아무것도 안 바뀌었으면 0. 「이벤트를
	 *     남길까」는 이 값이 아니라 {@link #markLikeRecorded} 가 답한다
	 */
	@Transactional
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query(value = """
			INSERT INTO story_reaction (story_id, user_id, reaction, created_at, reacted_at, updated_at)
			VALUES (:storyId, :userId, :reaction, :now, :now, :now)
			ON CONFLICT (story_id, user_id) DO UPDATE
			   SET reaction = EXCLUDED.reaction,
			       reacted_at = EXCLUDED.reacted_at,
			       updated_at = EXCLUDED.updated_at
			 WHERE story_reaction.reaction IS DISTINCT FROM EXCLUDED.reaction
			""", nativeQuery = true)
	int upsert(@Param("storyId") UUID storyId, @Param("userId") UUID userId, @Param("reaction") String reaction,
			@Param("now") OffsetDateTime now);

	/**
	 * 좋아요를 처음 남기는 것이면 표시하고 1 을 돌려준다. 이벤트를 (글, 사람, 종류)당 하나로
	 * 묶는 자리다. 판정을 {@code WHERE ... = FALSE} 로 DB 안에서 하므로 같은 순간 두 요청이
	 * 들어와도 1 을 받는 쪽은 하나뿐이다.
	 *
	 * <p>취소해도 이 칸은 안 내려간다 — 내려가면 껐다 켜는 것으로 이벤트를 얼마든지 다시 만들 수
	 * 있고, 개인화가 그 신호를 행동 이력으로 읽는다.
	 *
	 * @return 이번에 처음 남긴 것이면 1, 이미 남긴 적이 있으면 0
	 */
	@Transactional
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query(value = """
			UPDATE story_reaction SET like_recorded = TRUE
			 WHERE story_id = :storyId AND user_id = :userId AND like_recorded = FALSE
			""", nativeQuery = true)
	int markLikeRecorded(@Param("storyId") UUID storyId, @Param("userId") UUID userId);

	/** 싫어요 쪽. 규칙은 {@link #markLikeRecorded} 와 같다. */
	@Transactional
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query(value = """
			UPDATE story_reaction SET dislike_recorded = TRUE
			 WHERE story_id = :storyId AND user_id = :userId AND dislike_recorded = FALSE
			""", nativeQuery = true)
	int markDislikeRecorded(@Param("storyId") UUID storyId, @Param("userId") UUID userId);

	/**
	 * 반응을 취소한다 — 행은 남기고 종류만 비운다. 행을 지우면
	 * {@code like_recorded}·{@code dislike_recorded} 도 함께 사라져 다시 누르는 것이 「처음 누른
	 * 것」과 구분되지 않는다. 비운 행은 집계 질의가 {@code reaction} 으로 자르므로 저절로 빠진다.
	 *
	 * @return 실제로 비웠으면 1, 이미 비어 있었거나 누른 적이 없으면 0
	 */
	@Transactional
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query(value = """
			UPDATE story_reaction SET reaction = NULL, updated_at = :now
			 WHERE story_id = :storyId AND user_id = :userId AND reaction IS NOT NULL
			""", nativeQuery = true)
	int clearReaction(@Param("storyId") UUID storyId, @Param("userId") UUID userId,
			@Param("now") OffsetDateTime now);

	/**
	 * 최근 구간에 좋아요를 많이 받은 글 — 「실시간 인기순」이 읽을 자리. 싫어요를 빼서 합산하지
	 * 않는다. 그건 「인기」가 아니라 「호감도」이고 어느 쪽을 쓸지는 제품이 정한다. 시각을 부르는
	 * 쪽에서 받는 것은 여기서 {@code now()} 를 부르면 한 요청 안에서도 페이지마다 창이 밀려
	 * 목록이 흔들리기 때문이다.
	 *
	 * @param since 이 시각 이후에 눌린 것만 (보통 24시간 전)
	 */
	@Query("""
			SELECT r.id.storyId AS storyId, COUNT(r) AS likes
			  FROM StoryReaction r
			 WHERE r.reaction = com.gabolle.backend.story.domain.ReactionType.LIKE
			   AND r.reactedAt >= :since
			 GROUP BY r.id.storyId
			 ORDER BY COUNT(r) DESC, r.id.storyId ASC
			""")
	List<StoryLikeCount> countRecentLikes(@Param("since") OffsetDateTime since);

	/**
	 * 한 쪽에 실린 글들의 좋아요·싫어요 수를 한 번에 센다. 한 글만 세는 메서드를 일부러 두지
	 * 않는다 — 있으면 상세 경로가 그것을 집어 들어 N+1 이 돌아오고, 회귀 테스트는 목록 경로만
	 * 재므로 안 잡힌다. 상세도 이 메서드를 지난다.
	 *
	 * <p>취소한 행은 {@code reaction} 만 비운 채로 남으므로 그냥 세면 취소한 사람까지 들어간다.
	 * 결과는 한 글에 종류마다 한 줄이다 — 좋아요와 싫어요가 둘 다 있으면 두 줄이다.
	 */
	@Query("""
			SELECT r.id.storyId AS storyId, r.reaction AS reaction, COUNT(r) AS count
			  FROM StoryReaction r
			 WHERE r.id.storyId IN :storyIds
			   AND r.reaction IS NOT NULL
			 GROUP BY r.id.storyId, r.reaction
			""")
	List<StoryReactionCount> countByStories(@Param("storyIds") Collection<UUID> storyIds);

	/**
	 * 이 사람이 이 글들에 지금 무엇을 눌러 뒀나 — 화면의 토글이 자기 상태를 그리는 데 쓴다.
	 * 취소한 것은 안 누른 것이므로 결과에 안 들어간다.
	 */
	@Query("""
			SELECT r.id.storyId AS storyId, r.reaction AS reaction
			  FROM StoryReaction r
			 WHERE r.id.storyId IN :storyIds
			   AND r.id.userId = :userId
			   AND r.reaction IS NOT NULL
			""")
	List<StoryViewerReaction> findMineByStories(@Param("storyIds") Collection<UUID> storyIds,
			@Param("userId") UUID userId);

	interface StoryReactionCount {

		UUID getStoryId();

		ReactionType getReaction();

		long getCount();
	}

	interface StoryViewerReaction {

		UUID getStoryId();

		ReactionType getReaction();
	}

	interface StoryLikeCount {

		UUID getStoryId();

		long getLikes();
	}
}
