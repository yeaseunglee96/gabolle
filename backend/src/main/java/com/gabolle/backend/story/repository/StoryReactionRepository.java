package com.gabolle.backend.story.repository;

import java.time.OffsetDateTime;
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
	 * <h2>🔴 돌려주는 숫자가 「이벤트를 남길까」를 정한다</h2>
	 *
	 * 마지막 {@code WHERE} 가 핵심이다 — <b>종류가 실제로 달라질 때만</b> 갱신한다. 그래서
	 * 반환값이 새로 넣었거나 마음이 바뀐 경우에만 1 이고, 같은 값을 다시 보낸 재시도에는 0 이다.
	 * 앱의 재시도와 사람이 두 번 마음을 정한 것은 다르고, 그 판정을 <b>DB 가 원자적으로</b> 한다
	 * — {@code SavedPlaceRepository.insertIfAbsent} 가 같은 일을 한다(S15P21E201-1037).
	 *
	 * <p>{@code flushAutomatically} 로 앞선 변경을 먼저 내보내고 {@code clearAutomatically} 로
	 * 영속성 컨텍스트를 비운다 — 네이티브 문장은 그 컨텍스트를 거치지 않으므로, 비우지 않으면
	 * 같은 트랜잭션의 다음 조회가 낡은 객체를 돌려줄 수 있다.
	 *
	 * <p>🔴 {@code created_at} 은 {@code DO UPDATE} 에서 안 건드린다. 좋아요를 싫어요로 바꾼
	 * 것은 <b>처음 누른 시각을 지울 일이 아니고</b>, 인기순이 그 칸으로 24시간 창을 자른다 —
	 * 여기서 갱신하면 마음을 바꾸는 것만으로 창 안으로 다시 들어온다.
	 *
	 * @return 새로 넣었거나 종류가 바뀌었으면 1, 같은 값이라 아무것도 안 바뀌었으면 0
	 */
	@Transactional
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query(value = """
			INSERT INTO story_reaction (story_id, user_id, reaction, created_at, updated_at)
			VALUES (:storyId, :userId, :reaction, :now, :now)
			ON CONFLICT (story_id, user_id) DO UPDATE
			   SET reaction = EXCLUDED.reaction, updated_at = EXCLUDED.updated_at
			 WHERE story_reaction.reaction <> EXCLUDED.reaction
			""", nativeQuery = true)
	int upsert(@Param("storyId") UUID storyId, @Param("userId") UUID userId, @Param("reaction") String reaction,
			@Param("now") OffsetDateTime now);

	@Transactional
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	void deleteByIdStoryIdAndIdUserId(UUID storyId, UUID userId);

	/**
	 * 최근 구간에 좋아요를 많이 받은 글 — 「실시간 인기순」이 읽을 자리.
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
			   AND r.createdAt >= :since
			 GROUP BY r.id.storyId
			 ORDER BY COUNT(r) DESC, r.id.storyId ASC
			""")
	List<StoryLikeCount> countRecentLikes(@Param("since") OffsetDateTime since);

	/** 한 글의 좋아요·싫어요 수 — 상세 화면이 읽는다. */
	@Query("""
			SELECT COUNT(r) FROM StoryReaction r
			 WHERE r.id.storyId = :storyId
			   AND r.reaction = :reaction
			""")
	long countByStoryAndReaction(@Param("storyId") UUID storyId, @Param("reaction") ReactionType reaction);

	/** 집계 한 줄. */
	interface StoryLikeCount {

		UUID getStoryId();

		long getLikes();
	}
}
