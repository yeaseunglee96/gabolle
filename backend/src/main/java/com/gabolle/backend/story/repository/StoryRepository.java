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
 * 기록 저장소. 피드 질의는 전부 <b>커서(keyset)</b> 방식이다 — S15P21E201-233.
 *
 * <h2>왜 페이지 번호가 아니라 커서인가</h2>
 * 1페이지를 보는 사이 새 기록이 올라오면 페이지 번호 방식은 항목이 한 칸씩 밀려 2페이지에서 같은
 * 기록을 다시 본다. 커서는 "마지막으로 본 것(공개 시각, 식별자) 보다 뒤" 를 묻기 때문에 새 글이
 * 끼어도 밀리지 않는다. 같은 시각에 올라온 기록이 둘 이상일 수 있어 시각만으로는 부족하고, 식별자를
 * 두 번째 열로 써서 순서를 확정한다. {@code ix_story_feed (publish_at DESC, story_id DESC)} 가 이 정렬
 * 그대로다.
 *
 * <h2>왜 네이티브 SQL 인가</h2>
 * {@code (publish_at, story_id) < (:at, :id)} 같은 행 비교와 UUID 대소 비교는 PostgreSQL 이 잘 하지만
 * JPQL 로는 옮기기 어렵다. 이 저장소는 PostgreSQL 16 하나만 쓰기로 정했다({@code docs/DB-STANDARD.md}).
 *
 * <p>모든 질의가 {@code deleted_at IS NULL} 과 {@code publish_at <= :now} 를 건다 — 지운 기록과 아직
 * 공개 시각이 안 된 기록은 어느 피드에도 없다. 커서가 없을 때는 {@code :cursorAt} 에 아주 먼 미래를,
 * {@code :cursorId} 에 최대 UUID 를 넘긴다 — 호출자({@code StoryFeedService})가 그렇게 한다.
 */
public interface StoryRepository extends JpaRepository<Story, UUID> {

	String FEED_ORDER = " ORDER BY s.publish_at DESC, s.story_id DESC LIMIT :limit";

	/**
	 * 🔴 S15P21E201-254 — {@code moderation_state} 조건이 여기 <b>한 곳</b>에 있다.
	 *
	 * <p>신고된 기록을 감추려면 조회 경로 전부가 그것을 봐야 하고, 하나라도 빠뜨리면 그 화면에만
	 * 계속 보인다. 이 상수가 네 질의(전체 피드·팔로잉 피드·프로필 피드·개수)를 덮으므로 여기에
	 * 넣는 것으로 그 넷이 끝난다. {@code findActiveById}(상세)만 따로 걸어야 한다 — 그쪽은 이
	 * 상수를 쓰지 않는다.
	 *
	 * <p>새 피드 질의를 만들 때 이 상수를 쓰지 않으면 신고된 기록이 그 피드에 보인다.
	 *
	 * <h2>🔴 2026-09-17 — {@code parent_story_id IS NULL} 을 여기 더했다 (S15P21E201-1183)</h2>
	 *
	 * 댓글이 원글과 <b>같은 표</b>에 산다. 그래서 이 조건이 없으면 <b>댓글이 피드에 원글처럼
	 * 올라온다.</b>
	 *
	 * <p>이 상수가 다섯 질의를 덮는다는 것이 댓글을 같은 표에 두기로 한 근거였다 — 다른 표로
	 * 갔다면 신고·숨김·소프트삭제·사진·탈퇴처리를 전부 다시 만들어야 했다. 그 대신 <b>새 피드
	 * 질의를 만들 때 이 상수를 안 쓰면 댓글이 샌다</b>는 위험이 하나 늘었다. 위 문단이 신고된
	 * 기록에 대해 말하는 것과 같은 위험이고, 막는 방법도 같다 — 이 상수를 쓰면 된다.
	 *
	 * <p>🔴 <b>단건 조회({@code findActiveById}·{@code findVisibleById})에는 걸지 않는다.</b>
	 * 댓글도 id 로 열 수 있어야 한다 — 그 댓글에 달린 댓글을 보려면 먼저 그 댓글을 찾아야 한다.
	 */
	String NOT_DELETED_AND_PUBLISHED =
			" s.deleted_at IS NULL AND s.publish_at <= :now AND s.moderation_state = 'VISIBLE' "
					+ " AND s.parent_story_id IS NULL ";

	/**
	 * 🔴 S15P21E201-990 — 나를 차단한 사람의 기록은 목록에서 빠진다.
	 *
	 * <p><b>방향에 주의한다.</b> 이 저장소에서 차단은 "내가 이 사람을 안 본다" 가 아니라
	 * <b>"이 사람에게 내 것을 안 보여준다"</b> 이다. 그래서 거를 대상은 <b>내가 차단한 사람</b>이
	 * 아니라 <b>나를 차단한 사람</b>이다 — {@code blocker = 글쓴이}, {@code blocked = 나}.
	 * 반대로 쓰면 아무 오류 없이 정반대로 동작한다.
	 *
	 * <p><b>왜 목록에서는 조용히 빼는가.</b> 프로필·상세는 「차단되어 볼 수 없습니다」를 띄우지만
	 * (그쪽은 {@code BlockService.requireNotBlockedBy} 가 막는다), 목록에서 그러면 그 사람 글
	 * 하나 때문에 피드 전체가 실패한다. 목록은 빼고, 지목해서 여는 경로만 알린다.
	 *
	 * <p>🔴 <b>{@code :me} 가 있는 질의에만 쓸 수 있다.</b> 익명 피드
	 * ({@link #findPublicFeedForAnonymous})에는 안 쓴다 — 익명인 사람은 차단할 대상이 아니다.
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
	 * 로그인하지 않은 사람의 전체 피드 — 공개(PUBLIC) 기록만 — S15P21E201-974.
	 *
	 * <p>🔴 <b>왜 {@link #findPublicFeed} 에 {@code null} 을 넣지 않고 질의를 따로 두나.</b>
	 * 거기에 {@code :me = null} 을 넘겨도 지금은 결과가 같다 — SQL 에서
	 * {@code author_user_id = NULL} 은 참이 되지 않으므로 공개 글만 남는다. 그런데 그것은
	 * <b>세 값 논리에 기댄 안전</b>이고, 누군가 그 줄을 {@code COALESCE(:me, …)} 같은 것으로
	 * 고치는 순간 <b>말없이</b> 남의 비공개 기록이 익명에게 나간다. 새는 쪽이 조용한 종류의
	 * 사고라, 조건 자체를 아예 두지 않는 질의를 따로 둔다.
	 *
	 * <p>{@link #NOT_DELETED_AND_PUBLISHED} 를 쓴다 — 이 상수를 안 쓰면 신고된 기록이 이
	 * 피드에만 보인다(위 상수 설명).
	 */
	@Query(value = "SELECT s.* FROM story s WHERE" + NOT_DELETED_AND_PUBLISHED + " AND s.visibility = 'PUBLIC'"
			+ BEFORE_CURSOR + FEED_ORDER, nativeQuery = true)
	List<Story> findPublicFeedForAnonymous(@Param("now") Instant now, @Param("cursorAt") Instant cursorAt,
			@Param("cursorId") UUID cursorId, @Param("limit") int limit);

	/** 팔로잉 피드 — 내가 팔로우한 사람의 PUBLIC·FOLLOWERS 기록. */
	@Query(value = "SELECT s.* FROM story s WHERE" + NOT_DELETED_AND_PUBLISHED
			+ " AND s.visibility IN ('PUBLIC', 'FOLLOWERS')"
			+ " AND s.author_user_id IN (SELECT f.followee_user_id FROM user_follow f WHERE f.follower_user_id = :me)"
			// 🔴 차단하면 팔로우가 양쪽 다 끊기므로(BlockService.block) 이 조건 없이도 안 나오는 것이
			//    "지금은" 맞다. 그래도 건다 — 그 두 동작이 한 트랜잭션에 묶여 있다는 사실에 기대면,
			//    나중에 누가 팔로우 해제를 떼어 내는 순간 차단이 말없이 새기 시작한다.
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
	 * 지운 기록은 없는 기록이다 — 조회·수정·삭제가 전부 이 메서드로 시작한다.
	 *
	 * <p>🔴 검토 상태를 여기서 <b>걸지 않는다.</b> 이 메서드가 수정·삭제의 출발점이기도 해서,
	 * 신고된 기록을 여기서 감추면 작성자가 자기 기록을 지울 수도 없게 된다. 조회에서 감추는 것은
	 * {@link #findVisibleById} 가 한다.
	 */
	@Query("SELECT s FROM Story s WHERE s.storyId = :storyId AND s.deletedAt IS NULL")
	Optional<Story> findActiveById(@Param("storyId") UUID storyId);

	/**
	 * 남에게 보여줄 수 있는 기록만 — 상세 조회가 쓴다 (S15P21E201-254).
	 *
	 * <p>신고를 받으면 <b>상세에서도</b> 즉시 사라져야 한다는 것이 완료 기준이다. 피드에서만
	 * 빠지고 주소를 아는 사람은 계속 볼 수 있으면 감춘 것이 아니다.
	 */
	@Query("""
			SELECT s FROM Story s
			WHERE s.storyId = :storyId AND s.deletedAt IS NULL
			  AND s.moderationState = com.gabolle.backend.moderation.domain.StoryModerationState.VISIBLE
			""")
	Optional<Story> findVisibleById(@Param("storyId") UUID storyId);

	/**
	 * 이 글에 <b>직접</b> 달린 댓글 — S15P21E201-1183. 손자는 안 딸려 온다.
	 *
	 * <h2>🔴 {@link #NOT_DELETED_AND_PUBLISHED} 를 쓰지 않는 이유</h2>
	 *
	 * 그 상수는 이제 {@code parent_story_id IS NULL} 을 함께 건다 — 댓글을 찾는 이 질의에 쓰면
	 * <b>언제나 빈 목록</b>이 나온다. 대신 그 상수가 덮던 나머지 조건은 여기에 그대로 옮겨
	 * 적는다. 지운 댓글과 신고로 가려진 댓글은 안 나가야 하기 때문이다.
	 *
	 * <p>{@code publishAt} 은 안 건다. 댓글은 {@code createdAt} 과 같은 값으로 만들어져
	 * ({@link Story#reply}) 언제나 이미 지난 시각이다 — 안 거는 것이 아니라 걸 것이 없다.
	 *
	 * <h2>오래된 순인 이유</h2>
	 *
	 * 대화는 위에서 아래로 읽는다. 피드는 새 것이 위지만 댓글은 반대다.
	 */
	@Query("""
			SELECT s FROM Story s
			WHERE s.parentStoryId = :parentId AND s.deletedAt IS NULL
			  AND s.moderationState = com.gabolle.backend.moderation.domain.StoryModerationState.VISIBLE
			ORDER BY s.createdAt ASC, s.storyId ASC
			""")
	List<Story> findReplies(@Param("parentId") UUID parentId, Pageable limit);

	/**
	 * 한 여행에 달린 기록 — 추억 지도가 쓴다 (S15P21E201-829).
	 *
	 * <h2>왜 {@link #NOT_DELETED_AND_PUBLISHED} 를 쓰지 않는가</h2>
	 * 그 상수는 {@code publish_at <= :now} 를 함께 건다. 이 경로에서는 그 조건을 SQL 이 아니라
	 * {@code StoryVisibilityPolicy.canView} 가 판정해야 한다 — 작성자·공동 작성자는 공개 시각
	 * 전에도 자기 기록을 본다는 규칙이 그 클래스 한 곳에 있고, 여기서 SQL 로 미리 잘라 내면
	 * 같은 규칙이 두 곳에 갈라진다. <b>대신 검토 상태는 여기서 건다</b> — 그 상수가 덮던 두 조건
	 * 중 감춤에 해당하는 쪽을 잃지 않기 위해서다(신고로 가려진 기록은 참여자에게도 안 보인다는
	 * 것이 이 티켓에서 진미리 님과 합의한 계약이다).
	 *
	 * <h2>왜 {@code created_at} 순인가</h2>
	 * 한 여행의 기록은 {@code publish_at} 기본값이 <b>여행 종료 다음 날 0시로 전부 같다</b>
	 * ({@code StoryService.defaultPublishAt}). 그 열로 정렬하면 순서가 사실상 무작위가 된다.
	 * {@code created_at} 은 기록을 쓴 순서라 화면에 그릴 순서로 쓸 수 있다. 실제 방문 순서와
	 * 합치는 것은 화면이 한다 — {@code GET /api/v1/itineraries/&#123;id&#125;} 를 따로 불러서.
	 *
	 * <p>이어 보기(cursor)를 붙이지 않는다. 한 여행에 사람이 올리는 기록 수에는 현실적인 상한이
	 * 있어서 {@code limit} 한 번으로 끝난다 — 그 상한은 호출자가 준다.
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
	 * 여러 사람의 기록 수를 <b>한 번에</b> — S15P21E201-1317.
	 *
	 * <h2>🔴 왜 한 번에 세나</h2>
	 * 팔로워 목록 한 쪽에 사람이 스무 명이면 {@link #countAuthorStories} 를 스무 번 부르게
	 * 된다. 목록 한 줄을 그리려고 질의를 스무 개 날리는 것이고, 그 값은 <b>줄 옆의 작은
	 * 글씨 하나</b>다. 화면은 멀쩡해 보이고 조금 느릴 뿐이라 눈으로는 절대 안 잡힌다.
	 *
	 * <p>보이는 범위({@code visibilities})는 보는 사람과 그 사람의 관계에 따라 다르므로,
	 * 부르는 쪽이 <b>같은 범위끼리 묶어서</b> 몇 번 나눠 부른다. 쪽 크기와 무관하게 몇 번이다.
	 *
	 * @return {@code [작성자 id, 개수]} 줄들. 기록이 하나도 없는 사람은 <b>안 들어온다</b> —
	 *     부르는 쪽이 0 으로 채운다
	 */
	@Query(value = "SELECT s.author_user_id, count(*) FROM story s WHERE" + NOT_DELETED_AND_PUBLISHED
			+ " AND s.author_user_id IN (:authors) AND s.visibility IN (:visibilities)"
			+ " GROUP BY s.author_user_id", nativeQuery = true)
	List<Object[]> countAuthorStoriesGrouped(@Param("authors") Collection<UUID> authors,
			@Param("visibilities") List<String> visibilities, @Param("now") Instant now);
}
