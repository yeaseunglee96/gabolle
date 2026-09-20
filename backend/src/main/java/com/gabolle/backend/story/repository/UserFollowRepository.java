package com.gabolle.backend.story.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.gabolle.backend.story.domain.UserFollow;

public interface UserFollowRepository extends JpaRepository<UserFollow, UserFollow.Key> {

	boolean existsByKey(UserFollow.Key key);

	/** 이 사람을 팔로우하는 사람의 수. */
	long countByKeyFolloweeUserId(UUID followeeUserId);

	/** 이 사람이 팔로우하는 사람의 수. */
	long countByKeyFollowerUserId(UUID followerUserId);

	// 아래 목록 둘은 커서 방식이 StoryRepository 와 같고, 탈퇴한 사람을 뺀다 — 위 집계는 안 거르므로
	// 목록 길이와 수가 어긋날 수 있다.

	/** 이 사람을 팔로우하는 사람들, 최근에 맺은 순. */
	@Query(value = "SELECT u.user_id AS userId, u.display_name AS displayName, u.avatar_url AS avatarUrl, "
			+ "f.created_at AS relatedAt FROM user_follow f JOIN app_user u ON u.user_id = f.follower_user_id "
			+ "WHERE f.followee_user_id = :userId AND u.deleted_at IS NULL"
			+ " AND (f.created_at, f.follower_user_id) < (CAST(:cursorAt AS timestamptz), CAST(:cursorId AS uuid)) "
			+ "ORDER BY f.created_at DESC, f.follower_user_id DESC LIMIT :limit", nativeQuery = true)
	List<RelationRow> findFollowers(@Param("userId") UUID userId, @Param("cursorAt") Instant cursorAt,
			@Param("cursorId") UUID cursorId, @Param("limit") int limit);

	/** 이 사람이 팔로우하는 사람들, 최근에 맺은 순. */
	@Query(value = "SELECT u.user_id AS userId, u.display_name AS displayName, u.avatar_url AS avatarUrl, "
			+ "f.created_at AS relatedAt FROM user_follow f JOIN app_user u ON u.user_id = f.followee_user_id "
			+ "WHERE f.follower_user_id = :userId AND u.deleted_at IS NULL"
			+ " AND (f.created_at, f.followee_user_id) < (CAST(:cursorAt AS timestamptz), CAST(:cursorId AS uuid)) "
			+ "ORDER BY f.created_at DESC, f.followee_user_id DESC LIMIT :limit", nativeQuery = true)
	List<RelationRow> findFollowing(@Param("userId") UUID userId, @Param("cursorAt") Instant cursorAt,
			@Param("cursorId") UUID cursorId, @Param("limit") int limit);

	/**
	 * {@code candidates} 중 {@code viewer} 가 팔로우하는 사람들의 식별자만. 목록 한 쪽을 한 번에
	 * 물어 사람마다 {@code existsByKey} 를 부르는 N+1 을 막는다.
	 */
	@Query(value = "SELECT followee_user_id FROM user_follow WHERE follower_user_id = :viewer "
			+ "AND followee_user_id IN (:candidates)", nativeQuery = true)
	List<UUID> findFollowedAmong(@Param("viewer") UUID viewer, @Param("candidates") Collection<UUID> candidates);
}
