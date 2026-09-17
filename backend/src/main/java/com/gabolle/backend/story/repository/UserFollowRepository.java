package com.gabolle.backend.story.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.gabolle.backend.story.domain.UserFollow;

public interface UserFollowRepository extends JpaRepository<UserFollow, UserFollow.Key> {

	boolean existsByKey(UserFollow.Key key);

	/** 팔로워 수 — 이 사람을 팔로우하는 사람의 수. */
	long countByKeyFolloweeUserId(UUID followeeUserId);

	/** 팔로잉 수 — 이 사람이 팔로우하는 사람의 수. */
	long countByKeyFollowerUserId(UUID followerUserId);

	// ── 목록 — S15P21E201-1172. 커서 방식은 StoryRepository 와 같다(그쪽 javadoc 참고).
	//    탈퇴한 사람({@code deleted_at} 있음)은 목록에서 뺀다 — 이미 나간 사람이 팔로워 수에는
	//    안 잡히지만(집계 질의가 안 거른다는 뜻이 아니라, 여기서 처음으로 신경 써야 하는 문제라
	//    적어 둔다) 목록에는 유령처럼 남으면 안 된다.

	/** 팔로워 목록 — 이 사람을 팔로우하는 사람들, 최근에 맺은 순. */
	@Query(value = "SELECT u.user_id AS userId, u.display_name AS displayName, u.avatar_url AS avatarUrl, "
			+ "f.created_at AS relatedAt FROM user_follow f JOIN app_user u ON u.user_id = f.follower_user_id "
			+ "WHERE f.followee_user_id = :userId AND u.deleted_at IS NULL"
			+ " AND (f.created_at, f.follower_user_id) < (CAST(:cursorAt AS timestamptz), CAST(:cursorId AS uuid)) "
			+ "ORDER BY f.created_at DESC, f.follower_user_id DESC LIMIT :limit", nativeQuery = true)
	List<RelationRow> findFollowers(@Param("userId") UUID userId, @Param("cursorAt") Instant cursorAt,
			@Param("cursorId") UUID cursorId, @Param("limit") int limit);

	/** 팔로잉 목록 — 이 사람이 팔로우하는 사람들, 최근에 맺은 순. */
	@Query(value = "SELECT u.user_id AS userId, u.display_name AS displayName, u.avatar_url AS avatarUrl, "
			+ "f.created_at AS relatedAt FROM user_follow f JOIN app_user u ON u.user_id = f.followee_user_id "
			+ "WHERE f.follower_user_id = :userId AND u.deleted_at IS NULL"
			+ " AND (f.created_at, f.followee_user_id) < (CAST(:cursorAt AS timestamptz), CAST(:cursorId AS uuid)) "
			+ "ORDER BY f.created_at DESC, f.followee_user_id DESC LIMIT :limit", nativeQuery = true)
	List<RelationRow> findFollowing(@Param("userId") UUID userId, @Param("cursorAt") Instant cursorAt,
			@Param("cursorId") UUID cursorId, @Param("limit") int limit);
}
