package com.gabolle.backend.story.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

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
}
