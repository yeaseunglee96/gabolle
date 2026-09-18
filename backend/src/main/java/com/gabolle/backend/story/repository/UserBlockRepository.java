package com.gabolle.backend.story.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.gabolle.backend.story.domain.UserBlock;

/**
 * 차단 관계 — S15P21E201-990.
 *
 * <p>🔴 두 물음이 헷갈리기 쉬워 이름으로 갈라 둔다.
 * <ul>
 * <li>{@link #hasBlocked} — <b>내가</b> 이 사람을 차단했나. 버튼이 「차단하기」인지 「차단 해제」인지를 정한다</li>
 * <li>{@link #isBlockedBy} — <b>이 사람이</b> 나를 차단했나. 「차단되어 볼 수 없습니다」를 띄울지 정한다</li>
 * </ul>
 * 둘은 서로 다른 값이다. A 가 B 를 차단해도 B 는 A 를 차단하지 않은 상태일 수 있다.
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

	/** 내가 차단한 사람 수 — 설정의 「차단한 사용자」 목록이 쓸 자리다. */
	long countByKeyBlockerUserId(UUID blockerUserId);

	/**
	 * 내가 차단한 사람 목록, 최근에 차단한 순 — 설정 화면의 「차단된 계정」이 쓴다.
	 *
	 * <p>탈퇴한 사람도 뺀다 — 차단 자체는 여전히 유효하지만(다시 가입해도 다른 계정이다), 이미
	 * 나간 사람을 목록에 보여줄 이유가 없다.
	 */
	@Query(value = "SELECT u.user_id AS userId, u.display_name AS displayName, u.avatar_url AS avatarUrl, "
			+ "b.created_at AS relatedAt FROM user_block b JOIN app_user u ON u.user_id = b.blocked_user_id "
			+ "WHERE b.blocker_user_id = :userId AND u.deleted_at IS NULL"
			+ " AND (b.created_at, b.blocked_user_id) < (CAST(:cursorAt AS timestamptz), CAST(:cursorId AS uuid)) "
			+ "ORDER BY b.created_at DESC, b.blocked_user_id DESC LIMIT :limit", nativeQuery = true)
	List<RelationRow> findBlocked(@Param("userId") UUID userId, @Param("cursorAt") Instant cursorAt,
			@Param("cursorId") UUID cursorId, @Param("limit") int limit);
}
