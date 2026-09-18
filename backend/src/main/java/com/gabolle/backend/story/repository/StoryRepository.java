package com.gabolle.backend.story.repository;

import java.time.Instant;
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
	 */
	String NOT_DELETED_AND_PUBLISHED =
			" s.deleted_at IS NULL AND s.publish_at <= :now AND s.moderation_state = 'VISIBLE' ";

	String BEFORE_CURSOR = " AND (s.publish_at, s.story_id) < (CAST(:cursorAt AS timestamptz), CAST(:cursorId AS uuid)) ";

	/** 전체 피드 — 공개(PUBLIC) 기록, 그리고 내 기록은 범위와 무관하게. */
	@Query(value = "SELECT s.* FROM story s WHERE" + NOT_DELETED_AND_PUBLISHED
			+ " AND (s.visibility = 'PUBLIC' OR s.author_user_id = :me)" + BEFORE_CURSOR + FEED_ORDER,
			nativeQuery = true)
	List<Story> findPublicFeed(@Param("me") UUID me, @Param("now") Instant now, @Param("cursorAt") Instant cursorAt,
			@Param("cursorId") UUID cursorId, @Param("limit") int limit);

	/** 팔로잉 피드 — 내가 팔로우한 사람의 PUBLIC·FOLLOWERS 기록. */
	@Query(value = "SELECT s.* FROM story s WHERE" + NOT_DELETED_AND_PUBLISHED
			+ " AND s.visibility IN ('PUBLIC', 'FOLLOWERS')"
			+ " AND s.author_user_id IN (SELECT f.followee_user_id FROM user_follow f WHERE f.follower_user_id = :me)"
			+ BEFORE_CURSOR + FEED_ORDER, nativeQuery = true)
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
}
