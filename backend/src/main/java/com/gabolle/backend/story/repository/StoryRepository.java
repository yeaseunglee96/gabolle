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

	String BEFORE_CURSOR = " AND (s.publish_at, s.story_id) < (CAST(:cursorAt AS timestamptz), CAST(:cursorId AS uuid)) ";

	/** 전체 피드 — 공개(PUBLIC) 기록, 그리고 내 기록은 범위와 무관하게. */
	@Query(value = "SELECT s.* FROM story s WHERE" + NOT_DELETED_AND_PUBLISHED
			+ " AND (s.visibility = 'PUBLIC' OR s.author_user_id = :me)" + NOT_BLOCKED_BY_AUTHOR + BEFORE_CURSOR
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

	/** 팔로잉 피드 — 내가 팔로우한 사람의 PUBLIC·FOLLOWERS 기록. */
	@Query(value = "SELECT s.* FROM story s WHERE" + NOT_DELETED_AND_PUBLISHED
			+ " AND s.visibility IN ('PUBLIC', 'FOLLOWERS')"
			+ " AND s.author_user_id IN (SELECT f.followee_user_id FROM user_follow f WHERE f.follower_user_id = :me)"
			// 차단이 팔로우를 양쪽 다 끊으므로 이 조건 없이도 지금은 안 나온다. 그래도 건다 —
			// 나중에 누가 팔로우 해제를 떼어 내면 차단이 말없이 새기 시작한다.
			+ NOT_BLOCKED_BY_AUTHOR + BEFORE_CURSOR + FEED_ORDER, nativeQuery = true)
	List<Story> findFollowingFeed(@Param("me") UUID me, @Param("now") Instant now,
			@Param("cursorAt") Instant cursorAt, @Param("cursorId") UUID cursorId, @Param("limit") int limit);

	/**
	 * 한 사람의 기록(프로필). 본인이면 전부, 팔로워면 PUBLIC·FOLLOWERS, 그 외에는 PUBLIC 만.
	 * 어느 경우인지는 호출자가 {@code visibilities} 로 넘긴다.
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
