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
	 * 반응을 넣거나 바꾼다. <b>한 문장이다.</b>
	 *
	 * <h2>🔴 읽고-고치고-쓰지 않는 이유</h2>
	 *
	 * 먼저 조회해서 있으면 고치고 없으면 넣는 모양은 <b>같은 사람이 빠르게 두 번 누르면
	 * 깨진다.</b> 두 요청이 나란히 "없다" 를 읽고 둘 다 넣으려 들면 뒤의 것이 기본 키에 부딪혀
	 * 500 이 된다. 느린 통신에서 사람은 <b>반드시</b> 두 번 누른다.
	 *
	 * <h2>🔴 시각 칸이 둘인 이유</h2>
	 *
	 * {@code created_at} 은 <b>이 사람이 이 글에 처음 손댄 때</b>이고 여기서 안 건드린다.
	 * {@code reacted_at} 은 <b>지금의 반응을 고른 때</b>라 바뀔 때마다 갱신한다. 인기순의
	 * 24시간 창은 <b>{@code reacted_at} 을</b> 자른다.
	 *
	 * <p>한 칸으로 같이 쓰다가 실제로 틀렸다 — 사흘 전에 싫어요를 눌렀던 사람이 오늘
	 * 좋아요로 바꾸면 행은 {@code LIKE} 인데 시각이 사흘 전이라 <b>오늘 눌린 좋아요가 24시간
	 * 집계에서 빠졌다.</b>
	 *
	 * <h2>🔴 {@code IS DISTINCT FROM} 이다 — {@code <>} 가 아니다</h2>
	 *
	 * 취소한 행은 {@code reaction} 이 {@code NULL} 인데, {@code NULL <> 'LIKE'} 는 참이 아니라
	 * <b>{@code NULL}</b> 이다. {@code <>} 를 쓰면 <b>취소한 뒤 다시 누르는 것이 조용히 아무 일도
	 * 안 하고 0 을 돌려준다.</b>
	 *
	 * <p>{@code flushAutomatically} 로 앞선 변경을 먼저 내보내고 {@code clearAutomatically} 로
	 * 영속성 컨텍스트를 비운다 — 네이티브 문장은 그 컨텍스트를 거치지 않으므로, 비우지 않으면
	 * 같은 트랜잭션의 다음 조회가 낡은 객체를 돌려줄 수 있다.
	 *
	 * @return 상태가 실제로 바뀌었으면 1, 같은 값이라 아무것도 안 바뀌었으면 0. 🔴 이 값은
	 *     <b>「표가 바뀌었나」</b>이지 <b>「이벤트를 남길까」</b>가 아니다 — 뒤의 것은
	 *     {@link #markLikeRecorded} 가 답한다
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
	 * 좋아요를 <b>처음</b> 남기는 것이면 표시하고 1 을 돌려준다.
	 *
	 * <h2>🔴 이 한 문장이 이벤트를 (글, 사람, 종류)당 하나로 묶는다</h2>
	 *
	 * 예전에는 취소가 행을 지웠고, 그래서 다음 좋아요는 언제나 「새로 넣은 것」이 되어
	 * 이벤트를 남겼다. <b>하트를 껐다 켰다 5번 하면 {@code story_like} 가 5건 쌓이고 표의
	 * 행은 0개였다.</b> 그 신호를 개인화가 행동 이력으로 읽으므로, 손가락질 몇 번으로
	 * 자기 이력을 임의로 부풀릴 수 있었다.
	 *
	 * <p>{@code WHERE ... = FALSE} 가 판정을 <b>DB 안에서</b> 한다. 같은 순간 두 요청이
	 * 들어와도 1 을 받는 쪽은 하나뿐이라, 읽고-판단하고-쓰는 모양에서 나는 중복이 없다.
	 *
	 * <p>🔴 <b>취소해도 이 칸은 안 내려간다.</b> 내려가면 껐다 켜는 것으로 이벤트를 다시
	 * 만들 수 있고, 그게 바로 고치려는 결함이다. 「좋아요를 눌렀다」는 되풀이되는 사건이
	 * 아니라 사실이다 — 같은 사람이 같은 글을 두 번 좋아한다는 것은 뜻이 없다.
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
	 * 반응을 취소한다 — <b>행은 남기고 종류만 비운다.</b>
	 *
	 * <p>🔴 지우지 않는 이유가 이 파일의 핵심이다. 행이 사라지면
	 * {@code like_recorded}·{@code dislike_recorded} 도 함께 사라지고, 그러면 다시 누르는 것이
	 * 「처음 누른 것」과 구분되지 않아 이벤트가 또 나간다. 껐다 켰다를 반복하면 그만큼 쌓인다.
	 *
	 * <p>비운 행은 집계에 안 잡힌다 — {@code countRecentLikes} 가 {@code reaction = LIKE} 로
	 * 자르므로 {@code NULL} 은 저절로 빠진다.
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

	/** 탈퇴가 이 사람의 행을 지울 때 쓴다 — {@code AccountDeletionService.USER_OWNED_ROWS}. */
	void deleteByIdStoryIdAndIdUserId(UUID storyId, UUID userId);

	/**
	 * 최근 구간에 좋아요를 많이 받은 글 — 「실시간 인기순」이 읽을 자리.
	 *
	 * <p>🔴 <b>{@code reactedAt} 을 자른다.</b> {@code createdAt} 이 아니다 — 그 칸은 처음 손댄
	 * 때라, 마음을 바꾼 사람의 오늘 좋아요가 통째로 빠진다(이 표의 마이그레이션 주석 참고).
	 *
	 * <p>🔴 <b>좋아요만 센다.</b> 싫어요를 빼서 합산하지 않는다. 그건 「인기」가 아니라
	 * 「호감도」이고, 둘은 다른 화면이다. 뺄셈을 넣으면 논쟁적인 글이 조용한 글보다
	 * 아래로 가는데, 그 판단은 제품이 정할 일이지 질의가 몰래 정할 일이 아니다.
	 *
	 * <p>🔴 시각은 부르는 쪽이 준다({@code since}). 여기서 {@code now()} 를 부르면 같은
	 * 요청 안에서도 페이지마다 창이 밀려 목록이 흔들린다.
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
	 * 한 쪽에 실린 글들의 좋아요·싫어요 수를 <b>한 번에</b> 센다 — S15P21E201-1174.
	 *
	 * <h2>🔴 글마다 세지 않는 이유</h2>
	 *
	 * 피드 한 쪽이 최대 50건이다. 글마다 {@link #countByStoryAndReaction} 을 부르면 50번의
	 * 왕복이 되고(N+1), 그건 커뮤니티가 자라는 만큼 그대로 느려진다. {@code StoryResponseAssembler}
	 * 가 사진·작성자·장소를 한 번씩만 읽는 것과 같은 방식이다.
	 *
	 * <p>🔴 <b>취소한 행({@code reaction IS NULL})은 뺀다.</b> 취소해도 행이 남으므로
	 * (S15P21E201-1173 후속) 행을 그냥 세면 <b>취소한 사람까지 들어간다.</b>
	 *
	 * <p>한 글에 종류마다 한 줄씩 나온다 — 좋아요와 싫어요가 둘 다 있으면 두 줄이다.
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
	 *
	 * <p>🔴 위와 같은 이유로 한 번에 읽는다. 그리고 같은 이유로 {@code NULL} 을 뺀다 —
	 * 취소한 것은 <b>안 누른 것</b>이지 「취소를 누른 것」이 아니다.
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

	/** 한 글의 좋아요·싫어요 수 — 상세 화면이 읽는다. 취소한 행({@code NULL})은 저절로 빠진다. */
	@Query("""
			SELECT COUNT(r) FROM StoryReaction r
			 WHERE r.id.storyId = :storyId
			   AND r.reaction = :reaction
			""")
	long countByStoryAndReaction(@Param("storyId") UUID storyId, @Param("reaction") ReactionType reaction);

	/** 한 글의 한 종류에 대한 집계 한 줄. */
	interface StoryReactionCount {

		UUID getStoryId();

		ReactionType getReaction();

		long getCount();
	}

	/** 이 사람이 그 글에 지금 눌러 둔 것. */
	interface StoryViewerReaction {

		UUID getStoryId();

		ReactionType getReaction();
	}

	/** 집계 한 줄. */
	interface StoryLikeCount {

		UUID getStoryId();

		long getLikes();
	}
}
