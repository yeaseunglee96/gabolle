package com.gabolle.backend.story.repository;

import java.util.Collection;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.gabolle.backend.story.domain.UserBlock;

/**
 * 차단 관계. {@link #hasBlocked}(내가 저 사람을 차단했나)와 {@link #isBlockedBy}(저 사람이 나를
 * 차단했나)는 서로 다른 값이다 — 차단은 한쪽만 걸려 있을 수 있다.
 */
public interface UserBlockRepository extends JpaRepository<UserBlock, UserBlock.Key> {

	boolean existsByKey(UserBlock.Key key);

	/** 내가({@code blocker}) 상대를({@code blocked}) 차단했나. */
	default boolean hasBlocked(UUID blocker, UUID blocked) {
		return existsByKey(new UserBlock.Key(blocker, blocked));
	}

	/** 상대가({@code blocker}) 나를({@code me}) 차단했나 — 인자 순서가 위와 반대다. */
	default boolean isBlockedBy(UUID blocker, UUID me) {
		return existsByKey(new UserBlock.Key(blocker, me));
	}

	long countByKeyBlockerUserId(UUID blockerUserId);

	/**
	 * 내가 차단한 사람 목록, 최근에 차단한 순. 탈퇴한 사람은 목록에서 뺀다 — 차단 자체는 그대로
	 * 유효하다.
	 */
	@Query(value = "SELECT u.user_id AS userId, u.display_name AS displayName, u.avatar_url AS avatarUrl, "
			+ "b.created_at AS relatedAt FROM user_block b JOIN app_user u ON u.user_id = b.blocked_user_id "
			+ "WHERE b.blocker_user_id = :userId AND u.deleted_at IS NULL"
			+ " AND (b.created_at, b.blocked_user_id) < (CAST(:cursorAt AS timestamptz), CAST(:cursorId AS uuid)) "
			+ "ORDER BY b.created_at DESC, b.blocked_user_id DESC LIMIT :limit", nativeQuery = true)
	List<RelationRow> findBlocked(@Param("userId") UUID userId, @Param("cursorAt") Instant cursorAt,
			@Param("cursorId") UUID cursorId, @Param("limit") int limit);

	/** {@code candidates} 중 나를 차단한 사람들 — 목록 한 쪽을 한 번에 묻는다(사람마다 {@link #isBlockedBy} 를 부르지 않게). */
	@Query(value = "SELECT blocker_user_id FROM user_block WHERE blocked_user_id = :me AND blocker_user_id IN (:candidates)",
			nativeQuery = true)
	List<UUID> findBlockersAmong(@Param("me") UUID me, @Param("candidates") Collection<UUID> candidates);
}
