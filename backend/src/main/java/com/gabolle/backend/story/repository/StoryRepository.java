package com.gabolle.backend.story.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.gabolle.backend.story.domain.Story;

/**
 * 기록 저장소. 피드 질의는 전부 커서(keyset) 방식이다 — 페이지 번호로 자르면 보는 사이에 새 기록이
 * 올라올 때 항목이 밀려 같은 것을 두 번 본다. 같은 시각의 기록이 둘 이상일 수 있어 식별자를 두 번째
 * 정렬 열로 쓰고, 색인 {@code ix_story_feed (publish_at DESC, story_id DESC)} 가 이 정렬 그대로다.
 *
 * <p>네이티브 SQL 인 것은 {@code (publish_at, story_id) < (:at, :id)} 같은 행 비교와 UUID 대소 비교를
 * JPQL 로 옮기기 어렵기 때문이다. 이 저장소는 PostgreSQL 하나만 쓴다({@code docs/DB-STANDARD.md}).
 *
 * <p>커서가 없을 때는 {@code :cursorAt} 에 아주 먼 미래를, {@code :cursorId} 에 최대 UUID 를 넘긴다 —
 * 호출자가 그렇게 한다.
 */
public interface StoryRepository extends JpaRepository<Story, UUID> {

	String FEED_ORDER = " ORDER BY s.publish_at DESC, s.story_id DESC LIMIT :limit";

	/**
	 * 피드 질의 전부(전체·팔로잉·프로필·개수)가 공유하는 조건. 새 피드 질의에서 이 상수를 안 쓰면
	 * 신고로 가려진 기록과 댓글이 그 피드에 샌다 — 댓글이 원글과 같은 표에 살기 때문에
	 * {@code parent_story_id IS NULL} 도 여기 들어 있다.
	 *
	 * <p>단건 조회({@link #findActiveById}·{@link #findVisibleById})에는 걸지 않는다. 댓글도 id 로
	 * 열 수 있어야 그 댓글에 달린 댓글을 찾을 수 있다.
	 */
	String NOT_DELETED_AND_PUBLISHED =
			" s.deleted_at IS NULL AND s.publish_at <= :now AND s.moderation_state = 'VISIBLE' "
					+ " AND s.parent_story_id IS NULL ";

	/**
	 * 나를 차단한 사람의 기록을 목록에서 뺀다. 방향에 주의한다 — 이 저장소에서 차단은 「이 사람에게
	 * 내 것을 안 보여준다」라, 거를 대상은 내가 차단한 사람이 아니라 나를 차단한 사람이다
	 * ({@code blocker = 글쓴이}, {@code blocked = 나}). 반대로 쓰면 아무 오류 없이 정반대로 동작한다.
	 *
	 * <p>목록에서는 조용히 빼기만 한다. 한 사람의 글 때문에 피드 전체가 실패하면 안 되기 때문이고,
	 * 지목해서 여는 경로에서 알리는 것은 {@code BlockService.requireNotBlockedBy} 가 한다.
	 *
	 * <p>{@code :me} 가 있는 질의에만 쓸 수 있다 — 익명 피드에는 안 쓴다.
	 */
	String NOT_BLOCKED_BY_AUTHOR = " AND NOT EXISTS (SELECT 1 FROM user_block b"
			+ " WHERE b.blocker_user_id = s.author_user_id AND b.blocked_user_id = :me) ";

	/**
	 * 내가 차단한 사람의 기록도 내 목록에서 뺀다 (S15P21E201-1714 — App Store 가이드라인 1.2: 차단하면 그 사람의
	 * 콘텐츠가 차단한 사람의 피드에서 사라져야 한다).
	 *
	 * <p>{@link #NOT_BLOCKED_BY_AUTHOR} 의 <b>반대 방향</b>이다({@code blocker = 나}, {@code blocked = 글쓴이}). 둘은
	 * 서로를 대신하지 않는다 — 앞의 것은 「차단당한 사람에게 내 글을 안 보여준다」, 이것은 「내가 차단한 사람의 글을 나에게
	 * 안 보여준다」다. 목록에서는 조용히 빼기만 한다. 내 글은 {@code blocker = blocked} 가 될 수 없어(자기 차단 금지) 여기서
	 * 빠지지 않는다.
	 *
	 * <p>{@code :me} 가 있는 질의에만 쓸 수 있다 — 익명 피드에는 안 쓴다.
	 */
	String NOT_BLOCKING_AUTHOR = " AND NOT EXISTS (SELECT 1 FROM user_block bb"
			+ " WHERE bb.blocker_user_id = :me AND bb.blocked_user_id = s.author_user_id) ";

	String BEFORE_CURSOR = " AND (s.publish_at, s.story_id) < (CAST(:cursorAt AS timestamptz), CAST(:cursorId AS uuid)) ";

	/**
	 * 인기순이 쓰는 좋아요 수 — <b>{@code :windowStart} 이후에 받은 것만</b> 센다. 누적 칸이 아니라
	 * 반응 표를 그때그때 센다. {@code story_reaction} 의 PK 가 {@code (story_id, user_id)} 라 한 사람이
	 * 한 번만 세어지고, 취소한 사람은 행이 남되 {@code reaction} 이 바뀌므로 여기서 빠진다.
	 *
	 * <h2>🔴 이 수는 화면에 보이는 좋아요 수가 아니다</h2>
	 *
	 * 화면의 하트 옆 숫자({@code StoryResponse.likeCount})는 기간 제한 없는 <b>누적</b>이다. 여기
	 * 이 수는 <b>정렬에만 쓰는 창 안의 수</b>라 거의 언제나 더 작다. 둘을 같은 것으로 보고
	 * 커서에 화면의 수를 실으면 다음 쪽이 엉뚱한 자리에서 이어진다 —
	 * {@code StoryFeedService.page} 가 이 저장소의 {@link #countLikesSince} 로 따로 세는 이유다.
	 *
	 * <p>시각 열은 {@code created_at} 이다. 색인 {@code ix_story_reaction_recent
	 * (created_at DESC, reaction, story_id)} 이 이 모양 그대로라 «시각으로 먼저 자르고 글별로 센다»
	 * 가 색인만으로 끝난다. 좋아요를 눌렀다가 싫어요로 바꿨다 되돌린 사람은 행이 안 새로 생기므로
	 * 처음 누른 시각으로 남는다 — 「최근 24시간 안에 새로 받은 좋아요」라는 뜻에 그게 맞다.
	 */
	String RECENT_LIKE_COUNT = "(SELECT count(*) FROM story_reaction r WHERE r.story_id = s.story_id"
			+ " AND r.reaction = 'LIKE' AND r.created_at >= :windowStart)";

	/**
	 * 인기순 정렬. 좋아요 수가 같을 때 최신순으로 내려가고 마지막에 식별자로 끊는다.
	 *
	 * <p>뒤의 두 열이 없으면 안 된다. 지금 실서버는 기록 39건에 반응 6건이라 대부분이 0 으로 동점인데,
	 * 동점의 순서를 정하지 않으면 DB 가 주는 대로 나와 새로고침할 때마다 목록이 뒤바뀐다.
	 * 창으로 자르면 동점이 더 늘어난다 — 24시간 밖의 좋아요는 전부 0 이 되므로, 반응이 얇은
	 * 동안 인기순은 사실상 최신순으로 내려앉는다. 그게 이 정렬의 의도된 바닥이다.
	 */
	String POPULAR_ORDER = " ORDER BY " + RECENT_LIKE_COUNT
			+ " DESC, s.publish_at DESC, s.story_id DESC LIMIT :limit";

	/**
	 * 인기순 커서. 정렬 열이 셋이므로 비교도 셋이다 — 창 안의 좋아요 수가 커서보다 적거나,
	 * 같으면서 {@code (공개 시각, 식별자)} 가 뒤인 것.
	 */
	String BEFORE_POPULAR_CURSOR = " AND (" + RECENT_LIKE_COUNT + " < :cursorLikes OR (" + RECENT_LIKE_COUNT
			+ " = :cursorLikes"
			+ " AND (s.publish_at, s.story_id) < (CAST(:cursorAt AS timestamptz), CAST(:cursorId AS uuid)))) ";

	/** 전체 피드 — 공개(PUBLIC) 기록, 그리고 내 기록은 범위와 무관하게. */
	@Query(value = "SELECT s.* FROM story s WHERE" + NOT_DELETED_AND_PUBLISHED
			+ " AND (s.visibility = 'PUBLIC' OR s.author_user_id = :me)" + NOT_BLOCKED_BY_AUTHOR + NOT_BLOCKING_AUTHOR + BEFORE_CURSOR
			+ FEED_ORDER, nativeQuery = true)
	List<Story> findPublicFeed(@Param("me") UUID me, @Param("now") Instant now, @Param("cursorAt") Instant cursorAt,
			@Param("cursorId") UUID cursorId, @Param("limit") int limit);

	/**
	 * 로그인하지 않은 사람의 전체 피드 — 공개(PUBLIC) 기록만. {@link #findPublicFeed} 에
	 * {@code :me = null} 을 넘겨도 지금은 결과가 같지만 그것은 세 값 논리에 기댄 안전이고, 누가 그
	 * 줄을 {@code COALESCE(:me, …)} 같은 것으로 고치는 순간 말없이 남의 비공개 기록이 익명에게
	 * 나간다. 그래서 조건 자체가 없는 질의를 따로 둔다.
	 */
	@Query(value = "SELECT s.* FROM story s WHERE" + NOT_DELETED_AND_PUBLISHED + " AND s.visibility = 'PUBLIC'"
			+ BEFORE_CURSOR + FEED_ORDER, nativeQuery = true)
	List<Story> findPublicFeedForAnonymous(@Param("now") Instant now, @Param("cursorAt") Instant cursorAt,
			@Param("cursorId") UUID cursorId, @Param("limit") int limit);

	/** 전체 피드, 인기순. {@link #findPublicFeed} 와 조건은 같고 정렬과 커서만 다르다. */
	@Query(value = "SELECT s.* FROM story s WHERE" + NOT_DELETED_AND_PUBLISHED
			+ " AND (s.visibility = 'PUBLIC' OR s.author_user_id = :me)" + NOT_BLOCKED_BY_AUTHOR + NOT_BLOCKING_AUTHOR
			+ BEFORE_POPULAR_CURSOR + POPULAR_ORDER, nativeQuery = true)
	List<Story> findPublicFeedPopular(@Param("me") UUID me, @Param("now") Instant now,
			@Param("cursorAt") Instant cursorAt, @Param("cursorId") UUID cursorId,
			@Param("cursorLikes") int cursorLikes, @Param("windowStart") Instant windowStart,
			@Param("limit") int limit);

	/** 로그인하지 않은 사람의 전체 피드, 인기순. 조건을 따로 두는 이유는 {@link #findPublicFeedForAnonymous} 와 같다. */
	@Query(value = "SELECT s.* FROM story s WHERE" + NOT_DELETED_AND_PUBLISHED + " AND s.visibility = 'PUBLIC'"
			+ BEFORE_POPULAR_CURSOR + POPULAR_ORDER, nativeQuery = true)
	List<Story> findPublicFeedForAnonymousPopular(@Param("now") Instant now, @Param("cursorAt") Instant cursorAt,
			@Param("cursorId") UUID cursorId, @Param("cursorLikes") int cursorLikes,
			@Param("windowStart") Instant windowStart, @Param("limit") int limit);

	/** 팔로잉 피드, 인기순. */
	@Query(value = "SELECT s.* FROM story s WHERE" + NOT_DELETED_AND_PUBLISHED
			+ " AND s.visibility IN ('PUBLIC', 'FOLLOWERS')"
			+ " AND s.author_user_id IN (SELECT f.followee_user_id FROM user_follow f WHERE f.follower_user_id = :me)"
			+ NOT_BLOCKED_BY_AUTHOR + NOT_BLOCKING_AUTHOR + BEFORE_POPULAR_CURSOR + POPULAR_ORDER, nativeQuery = true)
	List<Story> findFollowingFeedPopular(@Param("me") UUID me, @Param("now") Instant now,
			@Param("cursorAt") Instant cursorAt, @Param("cursorId") UUID cursorId,
			@Param("cursorLikes") int cursorLikes, @Param("windowStart") Instant windowStart,
			@Param("limit") int limit);

	/**
	 * 한 글이 창 안에서 받은 좋아요 수 — <b>다음 쪽 커서에 실을 값</b>이다.
	 *
	 * <p>{@link #RECENT_LIKE_COUNT} 와 같은 것을 세지만 글 하나만 본다. 이 메서드가 따로 있는
	 * 이유는 정렬이 쓰는 수와 화면에 보이는 수가 <b>다른 값</b>이기 때문이다 — 커서는 정렬이 쓴
	 * 그 수로 이어져야 하는데, 조립된 응답에는 누적 수밖에 없다.
	 *
	 * <p>쪽마다 한 번씩 더 도는 질의지만 색인 {@code ix_story_reaction_recent} 로 끝나고,
	 * 쪽당 1회다 — 목록을 그리는 질의가 이미 글마다 같은 부질의를 돌고 있다.
	 */
	@Query(value = "SELECT count(*) FROM story_reaction r WHERE r.story_id = :storyId"
			+ " AND r.reaction = 'LIKE' AND r.created_at >= :windowStart", nativeQuery = true)
	int countLikesSince(@Param("storyId") UUID storyId, @Param("windowStart") Instant windowStart);

	/** 팔로잉 피드 — 내가 팔로우한 사람의 PUBLIC·FOLLOWERS 기록. */
	@Query(value = "SELECT s.* FROM story s WHERE" + NOT_DELETED_AND_PUBLISHED
			+ " AND s.visibility IN ('PUBLIC', 'FOLLOWERS')"
			+ " AND s.author_user_id IN (SELECT f.followee_user_id FROM user_follow f WHERE f.follower_user_id = :me)"
			// 차단이 팔로우를 양쪽 다 끊으므로 이 조건 없이도 지금은 안 나온다. 그래도 건다 —
			// 나중에 누가 팔로우 해제를 떼어 내면 차단이 말없이 새기 시작한다.
			+ NOT_BLOCKED_BY_AUTHOR + NOT_BLOCKING_AUTHOR + BEFORE_CURSOR + FEED_ORDER, nativeQuery = true)
	List<Story> findFollowingFeed(@Param("me") UUID me, @Param("now") Instant now,
			@Param("cursorAt") Instant cursorAt, @Param("cursorId") UUID cursorId, @Param("limit") int limit);

	/**
	 * 내가 쓴 댓글·대댓글, 최신순 (S15P21E201-1600). 원글이 지워지거나 가려져도 나온다 — 그것이 이 목록의 이유다.
	 * 그래서 {@link #NOT_DELETED_AND_PUBLISHED} 를 안 쓴다(그 상수에 「원글만」이 들어 있다). 내가 지운 댓글과
	 * 검토로 가려진 내 댓글은 뺀다 — 프로필 목록과 같은 기준이다.
	 */
	@Query(value = "SELECT s.* FROM story s WHERE s.author_user_id = :me AND s.parent_story_id IS NOT NULL"
			+ " AND s.deleted_at IS NULL AND s.publish_at <= :now AND s.moderation_state = 'VISIBLE'"
			+ BEFORE_CURSOR + FEED_ORDER, nativeQuery = true)
	List<Story> findMyReplies(@Param("me") UUID me, @Param("now") Instant now, @Param("cursorAt") Instant cursorAt,
			@Param("cursorId") UUID cursorId, @Param("limit") int limit);

	/**
	 * 한 사람의 기록(프로필). 본인이면 전부, 팔로워면 PUBLIC·FOLLOWERS, 그 외에는 PUBLIC 만.
	 * 어느 경우인지는 호출자가 {@code visibilities} 로 넘긴다. 🔴 본인이면 {@code :now} 에 먼 미래가 온다 —
	 * 공개 전 기록도 작성자에게는 보인다({@code StoryService#publishedCutoff}, S15P21E201-1737).
	 */
	@Query(value = "SELECT s.* FROM story s WHERE" + NOT_DELETED_AND_PUBLISHED
			+ " AND s.author_user_id = :author AND s.visibility IN (:visibilities)" + BEFORE_CURSOR + FEED_ORDER,
			nativeQuery = true)
	List<Story> findAuthorFeed(@Param("author") UUID author, @Param("visibilities") List<String> visibilities,
			@Param("now") Instant now, @Param("cursorAt") Instant cursorAt, @Param("cursorId") UUID cursorId,
			@Param("limit") int limit);

	/**
	 * 지운 기록은 없는 기록이다 — 조회·수정·삭제가 전부 이 메서드로 시작한다. 검토 상태는 여기서
	 * 걸지 않는다. 수정·삭제의 출발점이기도 해서, 신고된 기록을 여기서 감추면 작성자가 자기 기록을
	 * 지울 수도 없다. 조회에서 감추는 것은 {@link #findVisibleById} 가 한다.
	 */
	@Query("SELECT s FROM Story s WHERE s.storyId = :storyId AND s.deletedAt IS NULL")
	Optional<Story> findActiveById(@Param("storyId") UUID storyId);

	/** 남에게 보여줄 수 있는 기록만 — 상세 조회가 쓴다. 신고를 받으면 주소를 아는 사람에게도 안 보인다. */
	@Query("""
			SELECT s FROM Story s
			WHERE s.storyId = :storyId AND s.deletedAt IS NULL
			  AND s.moderationState = com.gabolle.backend.moderation.domain.StoryModerationState.VISIBLE
			""")
	Optional<Story> findVisibleById(@Param("storyId") UUID storyId);

	/**
	 * 이 글에 직접 달린 댓글. 손자는 안 딸려 온다. {@link #NOT_DELETED_AND_PUBLISHED} 는
	 * {@code parent_story_id IS NULL} 을 걸므로 여기 쓰면 언제나 빈 목록이 나온다 — 그 상수가 덮던
	 * 나머지 조건만 옮겨 적었다. {@code publishAt} 은 걸 것이 없다. 댓글은 {@code createdAt} 과 같은
	 * 값으로 만들어진다({@link Story#reply}). 대화는 위에서 아래로 읽으므로 오래된 순이다.
	 */
	@Query("""
			SELECT s FROM Story s
			WHERE s.parentStoryId = :parentId AND s.deletedAt IS NULL
			  AND s.moderationState = com.gabolle.backend.moderation.domain.StoryModerationState.VISIBLE
			ORDER BY s.createdAt ASC, s.storyId ASC
			""")
	List<Story> findReplies(@Param("parentId") UUID parentId, Pageable limit);

	/**
	 * {@link #findReplies} 에서 <b>내가 차단한 사람의 댓글</b>을 뺀 것 (S15P21E201-1714 — 가이드라인 1.2).
	 * 로그인한 사람만 쓴다 — {@code :viewer} 가 널이면 타입을 못 정해 질의가 실패하므로, 익명은 조건이 없는
	 * {@link #findReplies} 를 부른다({@code findPublicFeedForAnonymous} 가 따로 있는 것과 같은 이유).
	 */
	@Query("""
			SELECT s FROM Story s
			WHERE s.parentStoryId = :parentId AND s.deletedAt IS NULL
			  AND s.moderationState = com.gabolle.backend.moderation.domain.StoryModerationState.VISIBLE
			  AND NOT EXISTS (SELECT 1 FROM UserBlock b
			                  WHERE b.key.blockerUserId = :viewer AND b.key.blockedUserId = s.authorUserId)
			ORDER BY s.createdAt ASC, s.storyId ASC
			""")
	List<Story> findRepliesHidingBlockedBy(@Param("parentId") UUID parentId, @Param("viewer") UUID viewer,
			Pageable limit);

	/**
	 * 한 여행에 달린 기록 — 추억 지도가 쓴다. {@link #NOT_DELETED_AND_PUBLISHED} 를 쓰지 않는 것은
	 * 그 상수의 {@code publish_at <= :now} 를 여기서 걸면 안 되기 때문이다. 작성자·공동 작성자는
	 * 공개 시각 전에도 자기 기록을 본다는 규칙이 {@code StoryVisibilityPolicy.canView} 한 곳에 있고,
	 * SQL 로 미리 자르면 같은 규칙이 두 곳으로 갈라진다. 검토 상태는 대신 여기서 건다 — 신고로
	 * 가려진 기록은 참여자에게도 안 보인다.
	 *
	 * <p>{@code created_at} 순인 것은 한 여행의 기록이 {@code publish_at} 기본값을 여행 종료 다음 날
	 * 0시로 전부 같이 받아 그 열로는 순서가 사실상 무작위이기 때문이다. 실제 방문 순서와 맞추는 것은
	 * 화면이 여정을 따로 불러서 한다. 이어 보기는 붙이지 않는다 — 한 여행의 기록 수에는 현실적인
	 * 상한이 있어 호출자가 주는 {@code limit} 한 번으로 끝난다.
	 */
	@Query("""
			SELECT s FROM Story s
			WHERE s.tripId = :tripId AND s.deletedAt IS NULL
			  AND s.moderationState = com.gabolle.backend.moderation.domain.StoryModerationState.VISIBLE
			ORDER BY s.createdAt ASC, s.storyId ASC
			""")
	List<Story> findTripStories(@Param("tripId") UUID tripId, Pageable limit);

	/** 프로필의 공개 기록 수. 본인·팔로워 여부에 따라 세는 범위가 다르다. */
	@Query(value = "SELECT count(*) FROM story s WHERE" + NOT_DELETED_AND_PUBLISHED
			+ " AND s.author_user_id = :author AND s.visibility IN (:visibilities)", nativeQuery = true)
	long countAuthorStories(@Param("author") UUID author, @Param("visibilities") List<String> visibilities,
			@Param("now") Instant now);

	/**
	 * 여러 사람의 기록 수를 한 번에 센다. 목록 한 쪽을 그리려고 {@link #countAuthorStories} 를
	 * 사람 수만큼 부르지 않기 위해서다. 보이는 범위는 관계마다 다르므로 부르는 쪽이 같은
	 * 범위끼리 묶어 몇 번 나눠 부른다.
	 *
	 * @return {@code [작성자 id, 개수]} 줄들. 기록이 하나도 없는 사람은 안 들어온다 —
	 *     부르는 쪽이 0 으로 채운다
	 */
	@Query(value = "SELECT s.author_user_id, count(*) FROM story s WHERE" + NOT_DELETED_AND_PUBLISHED
			+ " AND s.author_user_id IN (:authors) AND s.visibility IN (:visibilities)"
			+ " GROUP BY s.author_user_id", nativeQuery = true)
	List<Object[]> countAuthorStoriesGrouped(@Param("authors") Collection<UUID> authors,
			@Param("visibilities") List<String> visibilities, @Param("now") Instant now);
}
