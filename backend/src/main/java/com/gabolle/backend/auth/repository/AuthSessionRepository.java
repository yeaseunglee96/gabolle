package com.gabolle.backend.auth.repository;

import com.gabolle.backend.auth.domain.AuthSession;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface AuthSessionRepository extends JpaRepository<AuthSession, UUID> {

	Optional<AuthSession> findBySessionIdAndUserUserId(UUID sessionId, UUID userId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<AuthSession> findByRefreshTokenHash(String refreshTokenHash);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	List<AuthSession> findAllByUserUserIdAndRevokedAtIsNull(UUID userId);

	/**
	 * 폐기된 것까지 포함한 그 사용자의 세션 전부.
	 *
	 * <p>계정 삭제가 쓴다. 폐기된 세션도 행은 남아 있어 지우지 않으면 사용자를 가리키는 데이터가 남는다.
	 */
	List<AuthSession> findAllByUserUserId(UUID userId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	List<AuthSession> findAllByTokenFamilyIdAndRevokedAtIsNull(UUID tokenFamilyId);
}
