package com.gabolle.backend.auth.repository;

import com.gabolle.backend.auth.domain.AuthRefreshToken;
import jakarta.persistence.LockModeType;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

public interface AuthRefreshTokenRepository extends JpaRepository<AuthRefreshToken, UUID> {

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<AuthRefreshToken> findByTokenHash(String tokenHash);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	List<AuthRefreshToken> findAllBySessionTokenFamilyIdAndRevokedAtIsNull(UUID tokenFamilyId);

	@Lock(LockModeType.PESSIMISTIC_WRITE)
	List<AuthRefreshToken> findAllBySessionUserUserIdAndRevokedAtIsNull(UUID userId);
}
