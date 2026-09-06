package com.gabolle.backend.story.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

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

	String NOT_DELETED_AND_PUBLISHED = " s.deleted_at IS NULL AND s.publish_at <= :now ";

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

	/** 지운 기록은 없는 기록이다 — 조회·수정·삭제가 전부 이 메서드로 시작한다. */
	@Query("SELECT s FROM Story s WHERE s.storyId = :storyId AND s.deletedAt IS NULL")
	Optional<Story> findActiveById(@Param("storyId") UUID storyId);

	/** 프로필의 공개 기록 수. 본인·팔로워 여부에 따라 세는 범위가 다르다. */
	@Query(value = "SELECT count(*) FROM story s WHERE" + NOT_DELETED_AND_PUBLISHED
			+ " AND s.author_user_id = :author AND s.visibility IN (:visibilities)", nativeQuery = true)
	long countAuthorStories(@Param("author") UUID author, @Param("visibilities") List<String> visibilities,
			@Param("now") Instant now);
}
